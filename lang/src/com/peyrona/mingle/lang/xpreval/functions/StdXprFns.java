
package com.peyrona.mingle.lang.xpreval.functions;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilReflect;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.lexer.Language;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Une Standard Expressions Functions.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class StdXprFns
{
    // JUST FEW CACHES
    private static final Map<String,DecimalFormat> mapFormats = new ConcurrentHashMap<>();     // Used by ::format(...)
    private static final Map<MapKey,MethodHandle>  mapMethods = new HashMap<>();               // Only added, never deleted
    private              Object[]                  argsCache  = new Object[0];                 // Cached arrays
    private        final pair                      pairTriggered   = new pair().put( "name" , "" )  // Last device name and value
                                                                               .put( "value", "" );

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE (NON STATIC)

    /**
     * Evaluates a function with certain arguments.
     *
     * @param sFnName Function name to e evaluated.
     * @param aoArgs  Arguments to pass to function: can be null.
     * @return The function's result.
     */
    public Object invoke( String sFnName, Object[] aoArgs )
    {
        // In case of extended types (date, time, list & pair), the class can be in sFnName or in aoArgs[0]
        Object  target     = null;
        Class   clazz      = null;
        Class[] aParamType = null;

        // Speed over clarity  ----------------------

        if( aoArgs == null || aoArgs.length == 0 )
        {
            aoArgs = null;
        }
        else
        {
            target = aoArgs[0];
            clazz  = target.getClass();

            if( (clazz == date.class)  ||   // Lets check if it is an Extended-Type
                (clazz == time.class)  ||
                (clazz == list.class)  ||
                (clazz == pair.class) )
            {
                aoArgs = (aoArgs.length <= 1) ? null : Arrays.copyOfRange( aoArgs, 1, aoArgs.length );
            }
            else
            {
                target = null;
            }

            if( aoArgs != null )     // Needed to check again
            {
                aParamType = new Class[aoArgs.length];
                Arrays.fill( aParamType, Object.class );
            }
        }

        if( target == null )
        {
            target = this;
            clazz  = StdXprFns.class;
        }

        MapKey       mapKey  = new MapKey( clazz, sFnName, aParamType );
        MethodHandle metHdle = mapMethods.get( mapKey );

        if( metHdle == null )
        {
            try
            {
                Method method = UtilReflect.getMethod( clazz, sFnName, aParamType );    // First get the Method using existing reflection logic

                if( method == null )
                    throw new MingleException( '"' + sFnName + "\" does not exist, in: " + toInvocation( clazz, sFnName, aoArgs ) );

                method.setAccessible( true );

                metHdle = MethodHandles.lookup().unreflect( method );    // Convert Method to MethodHandle for better performance

                synchronized( mapMethods )
                {
                    mapMethods.put( mapKey, metHdle );
                }
            }
            catch( IllegalAccessException exc )
            {
                throw new MingleException( "Error creating MethodHandle for " + toInvocation( clazz, sFnName, aoArgs ), exc );
            }
        }

        try
        {
            if( aoArgs == null )      // Simple MethodHandle invocation
                return metHdle.invoke( target );

            switch( aoArgs.length )
            {
                case 1: return metHdle.invoke( target, aoArgs[0] );
                case 2: return metHdle.invoke( target, aoArgs[0], aoArgs[1] );
                case 3: return metHdle.invoke( target, aoArgs[0], aoArgs[1], aoArgs[2] );

                default: // Create arguments array: target + method arguments (for 4 or mor agrs)  (reusing thread-local array)
                        Object[] allArgs    = getCachedArray( aoArgs.length + 1 );
                                 allArgs[0] = target;
                        System.arraycopy( aoArgs, 0, allArgs, 1, aoArgs.length );
                        return metHdle.invokeWithArguments( allArgs );
            }
        }
        catch( Throwable exc ) // MethodHandle.invoke() throws Throwable, not just reflection exceptions
        {
            MingleException me = new MingleException( "Error executing " + toInvocation( clazz, sFnName, aoArgs ), exc.getCause() );

            if( UtilSys.getLogger() == null )  me.printStackTrace( System.err );
            else                               UtilSys.getLogger().log( ILogger.Level.SEVERE, me );

            throw me;
        }
    }

    public void setTriggeredBy( String devName, Object devValue )
    {
        pairTriggered.put( "name" , devName  )
                     .put( "value", devValue );
    }

    //------------------------------------------------------------------------//
    // PUBLIC STATIC METHODS

    public static boolean isBasicType( Object o )       // This method is better here than in YAJER or any other place
    {
        // if( o == null )  return null;   --> Not needed

        return (o instanceof Number ) ||    // By using here
               (o instanceof String ) ||    // instanceof
               (o instanceof Boolean);      // we save CPU
    }

    public static boolean isExtendedType( Object o )    // This method is better here than in NAXE or any other place
    {
        return (o instanceof date) ||
               (o instanceof time) ||
               (o instanceof list) ||
               (o instanceof pair);
    }

    public static boolean isExtendedType( String sFn )
    {
        sFn = sFn.trim();

        if( sFn.length() != 4 )
            return false;

        sFn = sFn.toLowerCase();

        return "date".equals( sFn ) ||
               "time".equals( sFn ) ||
               "list".equals( sFn ) ||
               "pair".equals( sFn );
    }

    public static Class<?> getReturnType( String sFn, int nArgs )    // -1 --> Ignore number of arguments
    {
        Method method = getFunction( sFn, nArgs );    // A function always returns the same type of argument despiting the number of args it receives

        if( method == null )                          // If it is not a method of this class, lets find out if it is a method of an extended type
            method = getMethod( sFn, nArgs );

        if( method != null )
        {
            Class<?> c = method.getReturnType();

                 if( c == boolean.class ) return Boolean.class;    // Primitive types must be transformed into their wrappers
            else if( c == int.class     ) return Integer.class;
            else if( c == float.class   ) return Float.class;
         // else if( c == double.class  ) return Double.class;  --> Not used

            return c;   // None of the above
        }

        return null;
    }

    /**
     * Returns the method that represents any extended Une classes.
     * @param sFn
     * @param nArgs Function number of arguments. Use any value below zero to check only function name.
     * @return This class Method that represents requested function.
     */
    public static Method getMethod( String sFn, int nArgs )
    {
        // If it is not the name of a class (time, date, list, pair)
        // and is not the name of a method of this StdXprFns, it could
        // be a method of the classes: time, date, list, pair.

        Class[] ac = new Class[] { time.class, date.class, list.class, pair.class };

        for( Class c : ac )
        {
            for( Method method : c.getMethods() )    // getMethods() returns only public methods
            {
                if( method.getName().equalsIgnoreCase( sFn ) )
                {
                    if( nArgs < 0 )
                        return method;

                    if( method.isVarArgs() || (method.getParameterCount() == nArgs) )
                        return method;
                }
            }
        }

        return null;
    }

    /**
     * Returns this class Method that represents requested function.
     * @param sFn
     * @param nArgs Function number of arguments. Use any value below zero to check only function name.
     * @return This class Method that represents requested function.
     */
    public static Method getFunction( String sFn, int nArgs )
    {
        for( Method method : StdXprFns.class.getDeclaredMethods() )    // getDeclaredMethods() include even private methods
        {                                                              // (it is not needed to cache it because it is invoked only from Tokenize not from Eval)
            if( method.getName().equalsIgnoreCase( sFn ) )
            {
                if( nArgs < 0 )
                    return method;

                if( method.isVarArgs() || (method.getParameterCount() == nArgs) )
                    return method;
            }
        }

        return null;
    }

    /**
     * Returns all methods in this class that form the MSP standard functions (all private methods).
     *
     * @return All methods in this class that form the MSP standard functions.
     */
    public static String[] getAllFunctions()
    {
        List<String> list = new ArrayList<>();

        for( Method method : StdXprFns.class.getDeclaredMethods() )    // getDeclaredMethods() include even private methods
        {
            if( Modifier.isPrivate( method.getModifiers() ) &&         // Methods managed by the XpreEval in this class are all private
                (method.getName().charAt( 0 ) != '_')       &&         // Methods starting with '_' are for internal use only
                (! list.contains( method.getName() )) )                // Because some methods have same name and different num of params
            {
                list.add( method.getName() );
            }
        }

        String[] as = list.toArray( String[]::new );

        Arrays.sort( as );

        return as;
    }

    /**
     * Returns all extended data types and all their methods.
     *
     * @return All extended data types and all their methods.
     */
    public static Map<String,List<String>> getAllMethods()
    {
        Map<String,List<String>> map = new HashMap<>();

        for( Class c : new Class[] { time.class, date.class, list.class, pair.class } )
        {
            List<String> lst = new ArrayList<>();

            map.put( c.getSimpleName(), lst );

            for( Method method : c.getMethods() )           // getMethods() returns only public methods
                if( ! lst.contains( method.getName() ) )    // Because some methods have same name and different num of params
                    lst.add( method.getName() );

            Collections.sort(lst);
        }

        return map;
    }

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE: used via reflection
    //------------------------------------------------------------------------//

    // All and only the methods in this class that form the MSP standard functions
    // API, are private, so it is easy to find them (::getAllFunctions())

    //------------------------------------------------------------------------//
    // EXTENDED DATA TYPES

    @SuppressWarnings("unused")
    private date date( Object... aoArgs )
    {
        return new date( aoArgs );
    }

    @SuppressWarnings("unused")
    private time time( Object... aoArgs )
    {
        return new time( aoArgs );
    }

    @SuppressWarnings("unused")
    private list list( Object... aoArgs )
    {
        return new list( aoArgs );
    }

    @SuppressWarnings("unused")
    private pair pair( Object... aoArgs )
    {
        return new pair( aoArgs );
    }

    //------------------------------------------------------------------------//
    // NUMERIC RELATED FUNCTIONS

    @SuppressWarnings("unused")
    private int Int( Object o )
    {
        if( o instanceof Number )
            return  ((Number) o).intValue();

        if( ! (o instanceof String) )
            throw new MingleException( "Invalid value: number or string expected, but received: "+ o.getClass().getSimpleName() );

        String sNum = ((String) o).trim();

        if( UtilStr.startsWith( sNum, "0b" ) )
            return Integer.parseInt( sNum.substring( 2 ), 2 );

        if( UtilStr.startsWith( sNum, "0x" ) )
            return Integer.parseInt( sNum.substring( 2 ), 16 );

        return UtilType.toInteger( sNum );
    }

    @SuppressWarnings("unused")
    private float max( Object... objs )
    {
        if( objs == null || objs.length == 0 )
            return Float.MIN_VALUE;

        if( objs.length == 1 )
            return UtilType.toFloat( objs[0] );

        // Optimization for the most frecuent case ------------
        if( objs.length == 2 )
        {
            Float f1 = UtilType.toFloat( objs[0] );
            Float f2 = UtilType.toFloat( objs[1] );

            return (f1 > f2 ? f1 : f2);
        }
        // ----------------------------------------------------

        Float max = UtilType.toFloat( objs[0] );

        for( int n = 1; n < objs.length; n++ )
            max = Math.max( max, UtilType.toFloat( objs[n] ) );

        return max;
    }

    @SuppressWarnings("unused")
    private float min( Object... objs )
    {
        if( objs == null || objs.length == 0 )
            return Float.MAX_VALUE;

        if( objs.length == 1 )
            return UtilType.toFloat( objs[0] );

        // Optimization for the most frecuent case ------------
        if( objs.length == 2 )
        {
            Float f1 = UtilType.toFloat( objs[0] );
            Float f2 = UtilType.toFloat( objs[1] );

            return (f1 < f2 ? f1 : f2);
        }
        // -----------------------------------------------------

        Float max = UtilType.toFloat( objs[0] );

        for( int n = 1; n < objs.length; n++ )
            max = Math.min( max, UtilType.toFloat( objs[n] ) );

        return max;
    }

    /**
     * Returns the largest (closest to positive infinity) double value that is less than or equal
     * to the argument and is equal to a mathematical integer. Special cases:
     * <ul>
     *    <li>If the argument value is already equal to a mathematical integer, then the result is the same as the argument.</li>
     *    <li>If the argument is NaN or an infinity or positive zero or negative zero, then the result is the same as the argument.</li>
     * </ul>
     *
     * @param   number  A value.
     * @return  the largest (closest to positive infinity) floating-point value that less than or equal to the argument
     *          and is equal to a mathematical integer.
     */
    @SuppressWarnings("unused")
    private float floor( Object number )
    {
        return (float) Math.floor( UtilType.toDouble( number ) );
    }

    /**
     * Rounds number down, toward zero, to the nearest multiple of significance.
     *
     * @param number The numeric value you want to round.
     * @param significance  The multiple to which you want to round.
     * @return float number.
     */
    @SuppressWarnings("unused")
    private float floor( Object number, Object significance )
    {
        double nSig = UtilType.toDouble( significance );     // Math.floor(...) requieres double

        if( nSig == 0 )
            throw new IllegalArgumentException( "'significance' cannot be zero" );

        double nNum = UtilType.toDouble( number );           // Math.floor(...) requieres double

        if( nNum > 0 && nSig < 0 )
            throw new IllegalArgumentException( "When 'number' is positive, significance' cannot be zero" );

        return (float) (Math.floor( nNum / nSig ) * nSig);
    }

    /**
     * Returns the smallest (closest to negative infinity) float value that is greater than or equal to the
     * argument and is equal to a mathematical integer. Special cases:
     * <ul>
     *    <li>If the argument value is already equal to a mathematical integer, then the result is the same as the argument.</li>
     *    <li>If the argument is NaN or an infinity or positive zero or negative zero, then the result is the same as the argument.</li>
     *    <li>If the argument value is less than zero but greater than -1.0, then the result is negative zero.</li>
     * </ul>
     * Note that the value of {@code Math.ceil(x)} is exactly the value of <code>floor(-x)</code>.
     *
     * @param   number A value.
     * @return  the smallest (closest to negative infinity)
     *          floating-point value that is greater than or equal to
     *          the argument and is equal to a mathematical integer.
     */
    @SuppressWarnings("unused")
    private float ceiling( Object number )
    {
        return (float) Math.ceil( UtilType.toDouble( number ) );
    }

    /**
     * Returns number rounded up, away from zero, to the nearest multiple of significance.<br>
     * <br>
     * Note: In MS Excel the name is "ceiling" not "ceil" like in Java.
     *
     * @param number The numeric value you want to round.
     * @param significance  The multiple to which you want to round.
     * @return float number.
     */
    @SuppressWarnings("unused")
    private float ceiling( Object number, Object significance )
    {
        float nNum = UtilType.toFloat( number );
        float nSig = UtilType.toFloat( significance );

        if( nSig == 0 )
            throw new IllegalArgumentException( "'significance' cannot be zero" );

        float result = (float) Math.ceil( nNum / nSig ) * nSig;

        if( nNum < 0 )     // Adjust the result if the original number was negative
            result -= nSig;

        return result;
    }

    /**
     * Rounds to the closest integer (from 0.1 to 0.4 rounds down and from 0.5 to 0.9 rounds up).
     *
     * @param number Number to round.
     * @return float number.
     */
    @SuppressWarnings("unused")
    private float round( Object number )
    {
        return (float) Math.round( UtilType.toFloat( number ) );
    }

    /**
     * Rounds a number to a specified number of digits.
     *
     * @param number Number to round.
     * @param decimals Decimal places.
     * @return float number.
     */
    @SuppressWarnings("unused")
    private float round( Object number, Object decimals )
    {
        float nNum = UtilType.toFloat( number );
        int   nDec = UtilType.toInteger( decimals );

        float multiplier = (float) Math.pow( 10, nDec );

        return (float) Math.round( nNum * multiplier ) / multiplier;
    }

    /**
     * Returns the absolute value of a number. The absolute value of a number is the number without its sign.
     *
     * @param number
     * @return Its absolute value.
     */
    @SuppressWarnings("unused")
    private float abs( Object number )
    {
        return Math.abs( UtilType.toFloat( number ) );
    }

    @SuppressWarnings("unused")
    private boolean isBetween( Object val, Object min, Object max )
    {
        return UtilUnit.isBetween( UtilType.toFloat( min ),
                                   UtilType.toFloat( val ),
                                   UtilType.toFloat( max ) );
    }

    @SuppressWarnings("unused")
    private float setBetween( Object val, Object min, Object max )
    {
        return UtilUnit.setBetween( UtilType.toFloat( min ),
                                    UtilType.toFloat( val ),
                                    UtilType.toFloat( max ) );
    }

    /**
     * Returns the remainder after number is divided by divisor. The result has the same sign as divisor.
     *
     * @return The remainder after number is divided by divisor. The result has the same sign as divisor.
     */
    @SuppressWarnings("unused")
    private float mod( Object number, Object divisor )
    {
        float n = UtilType.toFloat( number  );
        float d = UtilType.toFloat( divisor );

        return (n % d);
    }

    /**
     * Returns the next pseudo-random, uniformly distributed float value between lower
     * (inclusive) and upper (exclusive) from the random number generator's sequence.<br>
     * <br>
     * Note: In MS Excel it is named RAND (not random).
     *
     * @param lower Lower limit (minimum number to be returned).
     * @param upper Upper limit (maximum number to be returned).
     * @return the next pseudo-random, uniformly distributed float value between lower
     *         (inclusive) and upper (exclusive).
     */
    @SuppressWarnings("unused")
    private float rand( Object lower, Object upper )
    {
        float min = UtilType.toFloat( lower );
        float max = UtilType.toFloat( upper );

        return new Random().nextFloat() * (max - min) + min;
    }

    /**
     * Formats a number using Java DecimalFormat class.
     *
     * @param number
     * @param pattern
     * @return The number after being formatted.
     */
    @SuppressWarnings("unused")
    private String format( Object o1, Object o2 )
    {
        String        sPattern = String.valueOf(   (o1 instanceof String ? o1 : o2) );
        Float         nNumber  = UtilType.toFloat( (o1 instanceof Number ? o1 : o2) );
        DecimalFormat formater = mapFormats.get( sPattern );

        if( formater == null )
        {
            formater = new DecimalFormat( sPattern );
            mapFormats.put( sPattern, formater );
        }

        synchronized( formater )   // JavaDocs: "Decimal formats are generally not synchronized."
        {
            return formater.format( nNumber );
        }
    }

    //------------------------------------------------------------------------//
    // STRING RELATED FUNCTIONS

    @SuppressWarnings("unused")
    private int size( Object obj )
    {
        return obj.toString().length();
    }

    @SuppressWarnings("unused")
    private int len( Object obj )
    {
        return obj.toString().length();
    }

    @SuppressWarnings("unused")
    private String trim( Object string )
    {
        return string.toString().trim();
    }

    @SuppressWarnings("unused")
    private String reverse( Object string )
    {
        return (new StringBuilder( string.toString() )).reverse().toString();
    }

    @SuppressWarnings("unused")
    private String left( Object string, Object chars )
    {
        return string.toString().substring(0, UtilType.toInteger( chars ) );
    }

    @SuppressWarnings("unused")
    private String right( Object string, Object chars )
    {
        String s = string.toString();

        return s.substring( s.length() - UtilType.toInteger( chars ) );
    }

    @SuppressWarnings("unused")
    private String lower( Object obj )
    {
        return obj.toString().toLowerCase();
    }

    @SuppressWarnings("unused")
    private String upper( Object obj )
    {
        return obj.toString().toUpperCase();
    }

    @SuppressWarnings("unused")
    private String proper( Object obj )       // Excel name for my Capitalize
    {
        return UtilStr.capitalize( obj.toString() );
    }

    @SuppressWarnings("unused")
    private int search( Object find, Object within )
    {
        return search( find, within, 1 );
    }

    /**
    * Converts the given {@code codePoint} object into a {@link String}
    * representing the Unicode character.
    * <p>
    * The input object is first converted to an {@code int} using
    * {@link UtilType#toInteger(Object)}. If the integer does not represent a
    * valid Unicode code point (according to {@link Character#isValidCodePoint(int)}),
    * an empty string is returned.
    * </p>
    * <p>
    * For Basic Multilingual Plane (BMP) code points ({@code U+0000}–{@code U+FFFF}),
    * the resulting string will contain exactly one character.
    * For supplementary characters (above {@code U+FFFF}), the resulting string
    * will contain a surrogate pair (length 2).
    * </p>
    *
    * @param codePoint an {@code Object} representing a numeric Unicode code point;
    *                  must be convertible to {@code int} via {@link UtilType#toInteger(Object)}.
    * @return a {@code String} representing the Unicode character if the code point is valid;
    *         otherwise, an empty {@code String}.
    */
    private String Char(Object codePoint)
    {
        int code = UtilType.toInteger( codePoint );

        if( ! Character.isValidCodePoint( code ) )
            return "";

        return new String( Character.toChars( code ) );
    }

    /**
     * Returns the system-dependent line separator string.<br>
     * On UNIX systems, it returns "\n"; on Microsoft Windows systems it returns "\r\n".
     *
     * @return the system-dependent line separator string.
     */
    private String newLine()
    {
        return System.lineSeparator();
    }

    /**
     * SEARCH: case-insensitive search of a substring in a string.
     *
     * @param find   The substring to search for: wildcards ('?' and '*')are allowed.
     * @param within The text to search within.
     * @param index  The position to start the search (1-based index).
     * @return The 1-based index of the first occurrence of substring in text, or 0 if not found.
     */
    @SuppressWarnings("unused")
    private int search( Object find, Object within, Object index )
    {
        if( UtilStr.isEmpty( find ) || UtilStr.isEmpty( within ) )
            return 0;

        String f = find.toString();
        String w = within.toString();

        if( w.length() < f.length() )    // Saves CPU by not doing toLowerCase()
            return 0;                    // indexes in Une ar 1 based

        int ndx = UtilType.toInteger( index );

        if( ndx < 1 )
            throw new MingleException( "Invalid start \""+ ndx +"\". Min value is 1" );

        if( ndx >= w.length() )
            throw new MingleException( "Invalid start \""+ ndx +"\". String length is "+ w.length() );

        f = f.toLowerCase();
        w = w.toLowerCase();

        // Replace Excel wildcards with Java regex equivalents
        f = f.replace( "?", "." )
             .replace( "*", ".*" );

        // Compile the regex pattern
        Pattern pattern = Pattern.compile( f );
        Matcher matcher = pattern.matcher( w );

        if( matcher.find( ndx - 1 ) )
            return matcher.start() + 1;

        return 0;
    }

    @SuppressWarnings("unused")
    private String substitute( Object source, Object oldText, Object newText )     // substitute is the Excel name
    {
        return substitute( source, oldText, newText, -1 );
    }

    private String substitute( Object source, Object oldText, Object newText, Object occurrence )     // substitute is the Excel name
    {
        String sInput = source.toString();
        String sRegEx = oldText.toString();
        String sNew   = newText.toString();
        int    nIndex = UtilType.toInteger( occurrence ) - 1;

        sRegEx = Language.escape( sRegEx );

        if( nIndex < 0 )
            return sInput.replaceAll( sRegEx, sNew );

        // To replace only nth occurrence

        Pattern      pattern = Pattern.compile( sRegEx );
        Matcher      matcher = pattern.matcher( sInput );
        StringBuffer output  = new StringBuffer();
        int          count   = 0;

        while( matcher.find() )
        {
            if( count++ == nIndex )
                matcher.appendReplacement( output, sNew );                                           // Found the nth occurrence, replace it and append to the output.
            else
                matcher.appendReplacement( output, Matcher.quoteReplacement( matcher.group() ) );    // Append the original match to the output.
        }

        matcher.appendTail( output );    // Append any remaining text after the last match.

        return output.toString();
    }

    @SuppressWarnings("unused")
    private boolean equals( Object... data )     // To check string case-sensitive equality
    {
        if( data == null || data.length == 0 )
            return false;                        // If no data is provided, return false

        Object first = data[0];

        for( int n = 1; n < data.length; n++ )
        {
            if( ! Objects.equals(first, data[n] ) )
                return false;
        }

        return true;
    }

    @SuppressWarnings("unused")
    private String mid( Object target, Object from )      // Like Excel but this is more flexible
    {
        return mid( target, from, null );
    }

    /**
     * Extracts a given specific number of characters from the middle part of a supplied object
     * (previously the object is transformed into its string representation).
     * <p>
     * The starting and ending characters can be defined as numbers or as portions of the where itself.<br>
     * Lets see some examples:
     * <ul>
     *    <li><code>"En un lugar de la mancha...":mid(7,4)</code> returns <code>"lugar"</code></li>
     *    <li><code>"En un lugar de la mancha...":mid(12)</code> returns <code>"de la mancha..."</code></li>
     *    <li><code>"A horse, my kingdom for a horse":mid("my "," for")</code> returns <code>"kingdom"</code></li>
     *    <li><code>"A horse, my kingdom for a horse":mid("my ",4)</code> returns <code>"king"</code></li>
     *    <li><code>"A une passante":mid(0,99)</code> returns <code>""</code></li>
     * </ul>
     * If the start and end points are out of bounds, then an empty string is returned.
     *
     * @param where Any object that internally will be converted into String.
     * @param start Either a number (1 based) or a String
     * @param end Either a number (chars to extract) or a String (if omit, to the end of the target)
     * @return The portion of 'where' defined by 'from' and 'to'.
     */
    private String mid( Object where, Object start, Object end )
    {
        String S = where.toString();
        String s = null;                 // s == S in lower case

        int n1, n2;

        if( start instanceof Number )
        {
            n1 = ((Number) start).intValue() -1;
        }
        else
        {
            if( s == null )
                s = S.toLowerCase();

            String f = start.toString().toLowerCase();

            n1 = S.indexOf( f );   // Makes a case-insensitive search

            if( n1 > -1 )
                n1 += f.length();
        }

        if( end == null )
        {
            n2 = S.length();
        }
        else
        {
            if( end instanceof Number )
            {
                n2 = n1 + ((Number) end).intValue();
            }
            else
            {
                if( s == null )
                    s = S.toLowerCase();

                n2 = s.indexOf( end.toString().toLowerCase(), n1 );   // Makes a case-insensitive search
            }
        }

        if( (n2 > n1) &&
            UtilUnit.isBetween( 0, n1, S.length() ) )
        {
            n2 = Math.min( n2, S.length() );

            return S.substring( n1, n2 );
        }

        return "";
    }

    //------------------------------------------------------------------------//
    // MISCELLANEOUS FUNCTIONS

    /**
     * Returns the current time in milliseconds elapsed since January 1, 1970 UTC.
     * <p>
     * Note that while the unit of time of the return value is a millisecond, the
     * granularity of the value depends on the underlying operating system and may
     * be larger. For example, many operating systems measure time in units of tens
     * of milliseconds.
     */
    @SuppressWarnings("unused")
    private long utc()
    {
        return System.currentTimeMillis();
    }

    /**
     * Returns true if:
     * <ul>
     *    <li>Received parameter is of type string and ii is empty or contains only blank spaces.</li>
     *    <li>Received parameter is of type 'list' and it's ::isEmpty() returns true.</li>
     *    <li>Received parameter is of type 'pair' and it's ::isEmpty() returns true.</li>
     * </ul>
     * Returns false in any other case.
     *
     * @param obj Object to check
     * @return true or false as described.
     * @throws
     */
    @SuppressWarnings("unused")
    private boolean isEmpty( Object obj )
    {
        if( obj instanceof String )
            return obj.toString().trim().isEmpty();

        if( obj instanceof list )
            return ((list) obj).isEmpty();

        if( obj instanceof pair )
            return ((pair) obj).isEmpty();

        // Following are never empty

        if( obj instanceof Number )
            return false;

        if( obj instanceof Boolean )
            return false;

        if( obj instanceof time )
            return false;

        if( obj instanceof date )
            return false;

        throw new MingleException( "Unknown type: "+ obj );
    }

    /**
     * Returns
     * <ul>
     *    <li>"N" if received parameter is of type numeric or a number inside a string. </li>
     *    <li>"B" if received parameter is of type boolean or a boolean inside a string. </li>
     *    <li>"S" in any other case. </li>
     * </ul>
     * @param obj
     * @return "N" or "B" or "S"
     */
    @SuppressWarnings("unused")
    private String type( Object obj )
    {
        if( obj instanceof Number )
            return "N";

        if( obj instanceof Boolean )
            return "B";

        if( obj instanceof String )
        {
            if( Language.isBooleanValue( obj ) )
                return "B";

            if( Language.isNumber( obj.toString() ) )
                return "N";

            return "S";
        }

        // Following lines are not needed because the ::type() method in each class is invoked
        // -->
        // if( lstClasses.contains( obj.getClass() ) )
        //     return obj.getClass().getSimpleName();

        throw new MingleException( "Unknown type: "+ obj );
    }

    /**
     * A function that works like the ternary operator "? :"<br>
     * <br>
     * IIF( window, "closed", "open" )    // device window is of type boolean
     * IIF( celsius ABOVE 25, "hot", IIF( celsius BELOW 17, "cold", "nice" ) )<br>
     * <br>
     * NOTE: IF is a reserved Une word.
     *
     * @param oXprResult Expression result
     * @param onTrue
     * @param onFalse
     * @param mapVars
     * @return 'onTrue' if 'oXpreRes' is TRUE, 'onFalse' in any other case.
     */
    @SuppressWarnings("unused")
    private Object iif( Object oXprResult, Object onTrue, Object onFalse )
    {
        if( oXprResult instanceof Boolean )                                              // This is what will happen 99% of times, so first
            return ((Boolean) oXprResult) ? onTrue : onFalse;

        if( (oXprResult instanceof String) && Language.isBooleanValue( oXprResult ) )    // Following (although stupid) is valid: IIF( "true", 12, 11 )
        {
            oXprResult = UtilType.toBoolean( (String) oXprResult );

            return ((Boolean) oXprResult) ? onTrue : onFalse;
        }

        return onFalse;
    }

    /**
     * Returns a <code>pair</code> with 1 pair:
     * <ul>
     *    <li>"name" : the name of the device.
     *    <li>"value": the value of the device.
     * </ul>
     *
     * Both corresponds with the last device that was changed and therefore the cause of current
     * evaluation of the rule.
     *
     * @return A <code>pair</code> with 2 pairs: name and "value".
     */
    @SuppressWarnings("unused")
    private pair getTriggeredBy()    // This method is needed here so it can be invoked from an expession
    {
        return pairTriggered;
    }

    /**
     * Finish ExEn execution.<br>
     * Returned value is useless.
     *
     * @return ""
     */
    @SuppressWarnings("unused")
    private Object exit()
    {
        System.exit( 0 );
        return "";
    }

    //------------------------------------------------------------------------//
    // Internet/IP related

    /**
     * Test whether that address is reachable. Best effort is made by the implementation
     * to try to reach the host, but firewalls and server configuration may block requests
     * resulting in a unreachable status while some specific ports may be accessible. <br>
     * <br>
     * The timeout value, in milliseconds, indicates the maximum amount of time the try
     * should take. If the operation times out before getting an answer, the host is deemed
     * unreachable. A negative value will be converted into positive (abs).<br>
     * If an internal error occurs, <code>false</code> is returned.
     * @param host
     * @param timeout In millis.
     */
    @SuppressWarnings("unused")
    private boolean isReachable( Object host, Object timeout )
    {
        try
        {
            return InetAddress.getByName( host.toString() )
                              .isReachable( Math.abs( UtilType.toInteger( timeout ) ) );
        }
        catch( IOException e )
        {
            return false;
        }
    }

    @SuppressWarnings("unused")
    private list localIPs()
    {
        list ips = new list();

        try
        {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while( interfaces.hasMoreElements() )
            {
                NetworkInterface iface = interfaces.nextElement();

                if( iface.isLoopback() || (! iface.isUp()) )
                    continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();

                while( addresses.hasMoreElements() )
                    ips.add( addresses.nextElement().getHostAddress() );
            }

            return ips;
        }
        catch( SocketException ex )
        {
            // Nothing to do
        }

        return ips;
    }

    //------------------------------------------------------------------------//
    // Global Variables Map

    @SuppressWarnings("unused")
    private boolean put( Object key, Object value )
    {
        if( key instanceof String )
            key = key.toString().toLowerCase();

        return UtilSys.put( key, value );
    }

    @SuppressWarnings("unused")
    private Object get( Object key )
    {
        return get( key, "" );
    }

    @SuppressWarnings("unused")
    private Object get( Object key, Object def )
    {
        if( key instanceof String )
            key = key.toString().toLowerCase();

        return UtilSys.get( key, def );
    }

    @SuppressWarnings("unused")
    private boolean del( Object key )
    {
        if( key instanceof String )
            key = key.toString().toLowerCase();

        return UtilSys.del( key );
    }

    //------------------------------------------------------------------------//
    // AUXILIARY FUNCTIONS

    private static String toInvocation( Class clazz, String sFn, Object[] aoArgs )
    {
        if( UtilColls.isEmpty( aoArgs ) )
            return sFn + "()";

        if( clazz != null )
            sFn = clazz.getSimpleName() +':'+ sFn;

        StringBuilder sb = new StringBuilder( sFn +'(' );

        for( Object obj : aoArgs )
        {
            if( obj instanceof Number )
            {
                Float floa = ((Number) obj).floatValue();
                int   inte = floa.intValue();

                obj = ((floa - inte) <= 0.00001) ? inte : floa;
            }
            else if( obj instanceof String )
            {
                sb.append( '"' ).append( obj ).append( '"' );
            }
            else
            {
                sb.append( obj );
            }

            sb.append( ',' );
        }

        return UtilStr.removeLast( sb, 1 ).append( ')' ).toString();
    }

    /**
     * Returns an array of 'size' number of items (all are of type Object).<br>
     * <br>
     * size = 1 -> argsCache[size] -> { Object1 }
     * size = 2 -> argsCache[size] -> { Object1, Object2 }
     * size = 3 -> argsCache[size] -> { Object1, Object2, Object3 }
     * .....................
     *
     * @param size The number of items of type Object that the array that this method has to return.
     * @return An array of 'size' number of items
     */
    private Object[] getCachedArray( int size )
    {
        assert size > 0;

        while( argsCache.length < size + 1 )
        {
            Object[] aNewTmp = Arrays.copyOf( argsCache, argsCache.length + 1 );
            Object[] aItem   = new Object[aNewTmp.length - 1];

            for( int n = 0; n < aItem.length; n++ )   // Each array position should have an unique object
                aItem[n] = new Object();

            aNewTmp[ aNewTmp.length-1 ] = aItem;

            synchronized( this )
            {
                argsCache = aNewTmp;
            }
        }

        return (Object[]) argsCache[size];
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    /**
     * Used to index this class functions, making their invocation faster.<br>
     * <br>
     * This is much faster than using a String composed by all these values: I
     * can not imagine why.<br>
     * <br>
     * DO NOT USE THIS: it is much slower than MapKey class -->
     * <code>
     *    private String createMapKey( Class clazz, String fnName, Class[] aParams )
     *    {
     *        StringBuilder sb = new StringBuilder( 256 )
     *                      .append( clazz.getName() )
     *                      .append( fnName );
     *
     *        if( aParams != null )
     *        {
     *            for( int n = 0; n < aParams.length; n++ )
     *                sb.append( aParams );
     *        }
     *
     *        return sb.toString();
     *    }
     * </code>
     */
    private static final class MapKey
    {
        private final int hashCode; // Make final and calculate once

        MapKey(Class clazz, String fnName, Class[] aParams)
        {
            int hash = 7;
                hash = 97 * hash + Objects.hashCode( clazz );
                hash = 97 * hash + Objects.hashCode( fnName );
                hash = 97 * hash + Arrays.hashCode( aParams ); // Use Arrays.hashCode instead of deepHashCode for Class arrays
            this.hashCode = hash;
        }

        @Override
        public int hashCode()
        {
            return hashCode; // Simply return pre-calculated value
        }

        @Override
        public boolean equals(Object obj)
        {
            if( this == obj )
                return true;

            if( obj == null )
                return false;

            if( getClass() != obj.getClass() )
                return false;

            final MapKey other = (MapKey) obj;

            return this.hashCode == other.hashCode;
        }
    }
}