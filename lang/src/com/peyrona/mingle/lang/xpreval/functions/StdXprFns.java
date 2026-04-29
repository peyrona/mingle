package com.peyrona.mingle.lang.xpreval.functions;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IRule;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilReflect;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.lexer.Language;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Une Standard Expressions Functions.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class StdXprFns
{
    // JUST FEW CACHES
    private static final Map<String,DecimalFormat> mapFormats  = new ConcurrentHashMap<>();   // Used by ::format(...)
    private static final Map<MapKey,MethodHandle>  mapMethods  = new ConcurrentHashMap<>();
    private static final Map<String,Pattern>       mapPatterns = new ConcurrentHashMap<>();

    // Pre-allocated arrays to avoid repeated allocations
    private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
    private static final Class[] ONE_CLASS_ARRAY   = { Object.class };
    private static final Class[] TWO_CLASS_ARRAY   = { Object.class, Object.class };
    private static final Class[] THREE_CLASS_ARRAY = { Object.class, Object.class, Object.class };

    // ThreadLocal for rule-scoped triggered device info (WHEN sets it, THEN reads it on same thread)
    private static final ThreadLocal<pair>              threadTriggered = ThreadLocal.withInitial( () -> new pair() );

    // ThreadLocal for previous device values: populated by EvalByAST.set() so that prev() is available
    // in both WHEN and THEN clause evaluators running on the same thread.
    private static final ThreadLocal<Map<String,Object>> threadPrev     = ThreadLocal.withInitial( HashMap::new );

    // Single instance for MethodHandle invocations (protected methods are instance methods)
    private static final StdXprFns INSTANCE = new StdXprFns();

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE (NON STATIC)

    /**
     * Evaluates a function with certain arguments.
     *
     * @param sFnName Function name to be evaluated.
     * @param aoArgs  Arguments to pass to function: can be null.
     * @return The function's result.
     */
    public static Object invoke( String sFnName, Object... aoArgs )
    {
        if( aoArgs == null || aoArgs.length == 0 )    // Fast path for null/empty args
            return invokeNoArgs( sFnName );

        // Check for extended types (date, time, list & pair)
        Class<?> clazz;
        Class[]  aParamType;
        Object   target        = null;
        Object[] actualArgs    = aoArgs;
        Object   firstArg      = aoArgs[0];
        Class<?> firstArgClass = firstArg.getClass();

        if( isExtendedType( firstArgClass ) )
        {
            target = firstArg;

            // Create view without first element instead of copying when possible
            if( aoArgs.length > 1 )
            {
                actualArgs = Arrays.copyOfRange( aoArgs, 1, aoArgs.length );
            }
            else
            {
                return invokeNoArgsOnTarget( sFnName, target );
            }
        }

        // Use pre-allocated parameter type arrays
        int argCount = (actualArgs != null) ? actualArgs.length : 0;

        switch( argCount )
        {
            case 0: aParamType = EMPTY_CLASS_ARRAY; break;
            case 1: aParamType = ONE_CLASS_ARRAY;   break;
            case 2: aParamType = TWO_CLASS_ARRAY;   break;
            case 3: aParamType = THREE_CLASS_ARRAY; break;
            default:
                aParamType = new Class[argCount];
                Arrays.fill( aParamType, Object.class );
                break;
        }

        if( target == null )
        {
            target = INSTANCE;
            clazz = StdXprFns.class;
        }
        else
        {
            clazz = target.getClass();
        }

        MethodHandle metHdle = getMethod( clazz, sFnName, aParamType, actualArgs );

        try
        {
            if( actualArgs == null )
                return metHdle.invoke( target );

            // Fast path for common argument counts
            switch( actualArgs.length )
            {
                case 1: return metHdle.invoke( target, actualArgs[0] );
                case 2: return metHdle.invoke( target, actualArgs[0], actualArgs[1] );
                case 3: return metHdle.invoke( target, actualArgs[0], actualArgs[1], actualArgs[2] );

                default: // For 4+ args (rare), create array
                        Object[] allArgs = new Object[actualArgs.length + 1];
                                 allArgs[0] = target;
                        System.arraycopy( actualArgs, 0, allArgs, 1, actualArgs.length );
                        return metHdle.invokeWithArguments( allArgs );
            }
        }
        catch( Throwable exc )
        {
            MingleException me = new MingleException( "Error executing " + toInvocation( clazz, sFnName, actualArgs ), exc.getCause() );

            UtilSys.getLogger().log( ILogger.Level.SEVERE, me );

            throw me;
        }
    }

    // Fast path methods for common cases
    private static Object invokeNoArgs( String sFnName )
    {
        MethodHandle metHdle = getMethod( StdXprFns.class, sFnName, EMPTY_CLASS_ARRAY, null );

        try
        {
            return metHdle.invoke( INSTANCE );
        }
        catch( Throwable exc )
        {
            MingleException me = new MingleException( "Error executing " + sFnName + "()", exc.getCause() );

            UtilSys.getLogger().log( ILogger.Level.SEVERE, me );

            throw me;
        }
    }

    private static Object invokeNoArgsOnTarget( String sFnName, Object target )
    {
        MethodHandle metHdle = getMethod( target.getClass(), sFnName, EMPTY_CLASS_ARRAY, null );

        try
        {
            return metHdle.invoke( target );
        }
        catch( Throwable exc )
        {
            MingleException me = new MingleException( "Error executing " + target.getClass().getSimpleName() + ":" + sFnName + "()", exc.getCause() );

            UtilSys.getLogger().log( ILogger.Level.SEVERE, me );

            throw me;
        }
    }

    private static MethodHandle getMethod( Class clazz, String sFnName, Class[] aParamType, Object[] actualArgs )
    {
        MapKey mapKey = new MapKey( clazz, sFnName, aParamType );

        return mapMethods.computeIfAbsent( mapKey, k ->
        {
            try
            {
                Method method = UtilReflect.getMethod( clazz, sFnName, aParamType );

                if( method == null )
                    throw new MingleException( '"' + sFnName + "\" does not exist, in: \"" + toInvocation( clazz, sFnName, actualArgs ) +'"' );

                method.setAccessible( true );
                return MethodHandles.lookup().unreflect( method );
            }
            catch( IllegalAccessException exc )
            {
                throw new MingleException( "Error creating MethodHandle for " + toInvocation( clazz, sFnName, actualArgs ), exc );
            }
        });
    }

    //------------------------------------------------------------------------//
    // PUBLIC METHODS

    /**
     * Sets the device information that triggered the current rule evaluation.
     * This information is stored in a {@link ThreadLocal} to ensure thread-safety.
     *
     * @param devName  The name of the device that triggered the rule.
     * @param devValue The current value of the device.
     */
    public static void setTriggeredBy( String devName, Object devValue )
    {
        threadTriggered.get().put( "name" , devName  )
                             .put( "value", devValue );
    }

    /**
     * Records the previous value of a device so that the {@code prev()} function can
     * return it during the evaluation of WHEN and THEN clauses on the same thread.
     * <p>
     * Called by {@code EvalByAST.set()} before the new value overwrites the old one.
     *
     * @param devName   The device name.
     * @param prevValue The value the device held before the current update (may be {@code null}
     *                  on the first evaluation — cold-start).
     */
    public static void setPreviousValue( String devName, Object prevValue )
    {
        threadPrev.get().put( devName, prevValue );
    }

    /**
     * Returns the previous value of the named device as recorded by the most recent
     * {@link com.peyrona.mingle.lang.xpreval.EvalByAST#set} call on the current thread.
     * Used by edge-detection operators in {@code ASTNode.evalEdgeOp()}.
     *
     * @param devName The device name.
     * @return The previous value, or {@code null} on cold start (no prior value recorded yet).
     */
    public static Object getPreviousValue( String devName )
    {
        return threadPrev.get().get( devName );
    }

    //------------------------------------------------------------------------//
    // PUBLIC STATIC METHODS

    /**
     * Checks if the given object is of a basic Une type (Number, String, or Boolean).
     *
     * @param o The object to check.
     * @return {@code true} if the object is a basic type, {@code false} otherwise.
     */
    public static boolean isBasicType( Object o )       // This method is better here than in YAJER or any other place
    {
        // if( o == null )  return null;   --> Not needed

        return (o instanceof Number ) ||    // By using here
               (o instanceof String ) ||    // instanceof
               (o instanceof Boolean);      // we save CPU
    }

    /**
     * Checks if the given object is of an extended Une type (date, time, list, or pair).
     *
     * @param o The object to check.
     * @return {@code true} if the object is an extended type, {@code false} otherwise.
     */
    public static boolean isExtendedType( Object o )    // This method is better here than in NAXE or any other place
    {
        if( o == null )
            return false;

        // Fast path: use calss '==' (works if same classloader)
        if( isExtendedType( o.getClass() ) )
            return true;

        // Fallback: check class name (works across classloaders when types are loaded from different JARs)
        return isExtendedType( o.getClass().getSimpleName() );
    }

    /**
     * Checks if the given class represents an extended Une type (date, time, list, or pair).
     * Fast class-based check to avoid {@code instanceof} overhead.
     *
     * @param clazz The class to check.
     * @return {@code true} if the class is an extended type, {@code false} otherwise.
     */
    public static boolean isExtendedType( Class<?> clazz )
    {
        if( clazz == null )
            return false;

        return (clazz == date.class) ||
               (clazz == time.class) ||
               (clazz == list.class) ||
               (clazz == pair.class);
    }

    /** Maps short type names to their corresponding extended type classes. */
    private static final Map<String, Class<? extends ExtraType>> EXT_TYPES = Map.of(
        "date", date.class,
        "time", time.class,
        "list", list.class,
        "pair", pair.class
    );

    /**
     * Creates a new empty instance of an extended data type given its name.
     * Accepts both short names ("date", "time", "list", "pair") and
     * fully-qualified class names.
     *
     * @param sName The type name (short or fully-qualified).
     * @return A new empty ExtraType instance, or {@code null} if the name is not recognized.
     */
    public static ExtraType<?> newExtendedType( String sName )
    {
        sName = sName.trim().toLowerCase();

        Class<? extends ExtraType> clazz = EXT_TYPES.get( sName );

        if( clazz == null )
        {
            // Try FQCN: extract simple name from fully-qualified
            int dot = sName.lastIndexOf( '.' );

            if( dot >= 0 )
                clazz = EXT_TYPES.get( sName.substring( dot + 1 ) );
        }

        if( clazz == null )
            return null;

        try
        {
            return clazz.getDeclaredConstructor( Object[].class )
                         .newInstance( (Object) new Object[0] );
        }
        catch( Exception e )
        {
            return null;
        }
    }

    /**
     * Checks if the given string name matches one of the extended Une types.
     *
     * @param sFn The name to check (e.g., "date", "time", "list", "pair").
     * @return {@code true} if the name matches an extended type, {@code false} otherwise.
     */
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

    /**
     * Returns the return type of the specified function.
     *
     * @param sFn   The function name.
     * @param nArgs The number of arguments the function receives, or -1 to ignore the number of arguments.
     * @return The {@link Class} representing the return type, or {@code null} if the function is not found.
     */
    public static Class<?> getReturnType( String sFn, int nArgs )    // -1 --> Ignore number of arguments
    {
        return getReturnType( sFn, nArgs, false );
    }

    /**
     * Returns the return type of the specified function or method.
     *
     * @param sFn      The function or method name.
     * @param nArgs    The number of arguments the function/method receives, or -1 to ignore the number of arguments.
     * @param isMethod True if searching for a method of an extended type, false if searching for a global function.
     * @return The {@link Class} representing the return type, or {@code null} if not found.
     */
    public static Class<?> getReturnType( String sFn, int nArgs, boolean isMethod )
    {
        Method method = isMethod ? getMethod( sFn, nArgs )
                                 : getFunction( sFn, nArgs );

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
     *
     * @param sFn   Function name.
     * @param nArgs Function number of arguments. Use any value below zero to check only function name.
     * @return This class {@link Method} that represents requested function.
     */
    public static Method getMethod( String sFn, int nArgs )
    {
        // If it is not the name of a class (time, date, list, pair)
        // and is not the name of a method of this StdXprFns, it could
        // be a method of the classes: time, date, list, pair.

        Class[] ac = new Class[] { time.class, date.class, list.class, pair.class };
        Method fallback = null;

        for( Class c : ac )
        {
            for( Method method : c.getMethods() )    // getMethods() returns only public methods
            {
                if( ! method.getDeclaringClass().equals( Object.class ) &&     // Method is not declared in Object class (wait, notify, etc), although if
                    method.getName().equalsIgnoreCase( sFn ) )                 // it is overwritten by my classes (date, time, etc) it will be included.
                {
                    if( nArgs < 0 )
                    {
                        if( method.getParameterCount() == 0 )
                            return method;
                        if( fallback == null )
                            fallback = method;
                    }
                    else if( method.isVarArgs() || (method.getParameterCount() == nArgs) )
                    {
                        return method;
                    }
                }
            }
        }

        return fallback;
    }

    /**
     * Returns this class {@link Method} that represents requested function.
     *
     * @param sFn   Function name.
     * @param nArgs Function number of arguments. Use any value below zero to check only function name.
     * @return This class {@link Method} that represents requested function.
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
     * Returns all methods in this class that form the MSP standard functions (all protected methods).
     *
     * @return All methods in this class that form the MSP standard functions.
     */
    public static String[] getAllFunctions()
    {
        List<String> list = new ArrayList<>();

        for( Method method : StdXprFns.class.getDeclaredMethods() )    // getDeclaredMethods() include even private methods
        {
            if( Modifier.isProtected( method.getModifiers() ) &&       // Methods managed by the XpreEval in this class are all private
                (method.getName().charAt( 0 ) != '_')         &&       // Methods starting with '_' are for internal use only
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
     * @return A map where keys are extended data type names and values are lists of their method names.
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

    /*--------------------------------------------------------------------------------------------
     * PROTECTED INTERFACE: used via reflection
     * All and only the methods in this class that form the MSP standard functions
     * API, are private, so it is easy to find them (::getAllFunctions())
     *
     * IMPORTANT:
     * It is better to keep the protected methods as instance methods. Here's why:
     *
     * The MethodHandle invocation pattern
     *
     * The current code handles both:
     * 1. Methods on StdXprFns (like max(), floor(), date())
     * 2. Methods on extended types (date, time, list, pair instances)
     *
     * For instance methods, MethodHandle invocation works like:
     * metHdle.invoke(target, arg1, arg2)  // target is the object to call the method on
     *
     * For static methods, it would be:
     * metHdle.invoke(arg1, arg2)  // no target needed
     *
     * Making them static would add complexity
     *
     * If you made StdXprFns methods static, you'd need to:
     *    1. Detect whether you're calling a static method (StdXprFns) vs instance method (date/time/list/pair)
     *    2. Use different invocation patterns for each case
     *    3. Add branching logic throughout invoke(), invokeNoArgs(), etc.
    ---------------------------------------------------------------------------------------------*/

    //------------------------------------------------------------------------//
    // EXTENDED DATA TYPES

    /**
     * Creates a new Une {@link date} instance.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>date()</code> returns current date (e.g. 2023-10-27)</li>
     *    <li><code>date("2023-12-31")</code> returns date object for Dec 31, 2023</li>
     *    <li><code>date(2023, 12, 31)</code> returns date object for Dec 31, 2023</li>
     * </ul>
     *
     * @param aoArgs Optional arguments for the date constructor.
     * @return A new date object.
     */
    @SuppressWarnings("unused")
    protected date date( Object... aoArgs )
    {
        return new date( aoArgs );
    }

    /**
     * Creates a new Une {@link time} instance.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>time()</code> returns current time (e.g. 14:30:00)</li>
     *    <li><code>time("14:30")</code> returns time object for 14:30</li>
     *    <li><code>time(14, 30, 0)</code> returns time object for 14:30:00</li>
     * </ul>
     *
     * @param aoArgs Optional arguments for the time constructor.
     * @return A new time object.
     */
    @SuppressWarnings("unused")
    protected time time( Object... aoArgs )
    {
        return new time( aoArgs );
    }

    /**
     * Creates a new Une {@link list} instance.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>list()</code> returns an empty list <code>[]</code></li>
     *    <li><code>list(1, 2, 3)</code> returns <code>[1, 2, 3]</code></li>
     *    <li><code>list("a", "b")</code> returns <code>["a", "b"]</code></li>
     * </ul>
     *
     * @param aoArgs Optional arguments for the list constructor.
     * @return A new list object.
     */
    @SuppressWarnings("unused")
    protected list list( Object... aoArgs )
    {
        return new list( aoArgs );
    }

    /**
     * Creates a new Une {@link pair} instance.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>pair()</code> returns an empty pair/map <code>{}</code></li>
     *    <li><code>pair("name", "John", "age", 30)</code> returns <code>{"name":"John", "age":30}</code></li>
     * </ul>
     *
     * @param aoArgs Optional arguments for the pair constructor.
     * @return A new pair object.
     */
    @SuppressWarnings("unused")
    protected pair pair( Object... aoArgs )
    {
        return new pair( aoArgs );
    }

    //------------------------------------------------------------------------//
    // NUMERIC RELATED FUNCTIONS

    /**
     * Converts an object to an integer. Supports decimal, binary (0b prefix),
     * and hexadecimal (0x prefix) string representations.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>Int(12.34)</code> returns <code>12</code></li>
     *    <li><code>Int("123")</code> returns <code>123</code></li>
     *    <li><code>Int("0xFF")</code> returns <code>255</code></li>
     *    <li><code>Int("0b101")</code> returns <code>5</code></li>
     * </ul>
     *
     * @param number The object to convert (typically a Number or String).
     * @return The integer value.
     * @throws MingleException If the input is not a number or string.
     */
    @SuppressWarnings("unused")
    protected int Int( Object number )
    {
        if( number instanceof Number )
            return  ((Number) number).intValue();

        if( ! (number instanceof String) )
            throw new MingleException( "Invalid value: number or string expected, but received: "+ number.getClass().getSimpleName() );

        String sNum = ((String) number).trim();

        if( UtilStr.startsWith( sNum, "0b" ) )
            return Integer.parseInt( sNum.substring( 2 ), 2 );

        if( UtilStr.startsWith( sNum, "0x" ) )
            return Integer.parseInt( sNum.substring( 2 ), 16 );

        return UtilType.toInteger( sNum );
    }

    /**
     * Returns the maximum value from a list of arguments.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>max(1, 5, 2)</code> returns <code>5.0</code></li>
     *    <li><code>max(-1, -5)</code> returns <code>-1.0</code></li>
     * </ul>
     *
     * @param numbers One or more numeric values to compare.
     * @return The maximum value as a float.
     */
    @SuppressWarnings("unused")
    protected float max( Object... numbers )
    {
        if( numbers == null || numbers.length == 0 )
            return Float.MIN_VALUE;

        if( numbers.length == 1 )
            return UtilType.toFloat( numbers[0] );

        // Optimization for the most frecuent case ------------
        if( numbers.length == 2 )
        {
            Float f1 = UtilType.toFloat( numbers[0] );
            Float f2 = UtilType.toFloat( numbers[1] );

            return (f1 > f2 ? f1 : f2);
        }
        // ----------------------------------------------------

        Float max = UtilType.toFloat( numbers[0] );

        for( int n = 1; n < numbers.length; n++ )
            max = Math.max( max, UtilType.toFloat( numbers[n] ) );

        return max;
    }

    /**
     * Returns the minimum value from a list of arguments.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>min(1, 5, 2)</code> returns <code>1.0</code></li>
     *    <li><code>min(-1, -5)</code> returns <code>-5.0</code></li>
     * </ul>
     *
     * @param numbers The numeric values to compare.
     * @return The minimum value as a float.
     */
    @SuppressWarnings("unused")
    protected float min( Object... numbers )
    {
        if( numbers == null || numbers.length == 0 )
            return Float.MAX_VALUE;

        if( numbers.length == 1 )
            return UtilType.toFloat( numbers[0] );

        // Optimization for the most frecuent case ------------
        if( numbers.length == 2 )
        {
            Float f1 = UtilType.toFloat( numbers[0] );
            Float f2 = UtilType.toFloat( numbers[1] );

            return (f1 < f2 ? f1 : f2);
        }
        // -----------------------------------------------------

        Float max = UtilType.toFloat( numbers[0] );

        for( int n = 1; n < numbers.length; n++ )
            max = Math.min( max, UtilType.toFloat( numbers[n] ) );

        return max;
    }

    /**
     * Returns the largest (closest to positive infinity) double value that is less than or equal
     * to the argument and is equal to a mathematical integer. Special cases:
     * <ul>
     *    <li>If the argument value is already equal to a mathematical integer, then the result is the same as the argument.</li>
     *    <li>If the argument is NaN or an infinity or positive zero or negative zero, then the result is the same as the argument.</li>
     * </ul>
     * <p>
     * Examples:
     * <ul>
     *    <li><code>floor(3.14)</code> returns <code>3.0</code></li>
     *    <li><code>floor(-3.14)</code> returns <code>-4.0</code></li>
     * </ul>
     *
     * @param   number  A value.
     * @return  the largest (closest to positive infinity) floating-point value that less than or equal to the argument
     *          and is equal to a mathematical integer.
     */
    @SuppressWarnings("unused")
    protected float floor( Object number )
    {
        return (float) Math.floor( UtilType.toDouble( number ) );
    }

    /**
     * Rounds number down, toward zero, to the nearest multiple of significance.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>floor(158, 10)</code> returns <code>150.0</code> (nearest multiple of 10)</li>
     *    <li><code>floor(0.56, 0.1)</code> returns <code>0.5</code></li>
     * </ul>
     *
     * @param number The numeric value you want to round.
     * @param significance  The multiple to which you want to round.
     * @return float number.
     */
    @SuppressWarnings("unused")
    protected float floor( Object number, Object significance )
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
     * <p>
     * Examples:
     * <ul>
     *    <li><code>ceiling(3.14)</code> returns <code>4.0</code></li>
     *    <li><code>ceiling(-3.14)</code> returns <code>-3.0</code></li>
     * </ul>
     *
     * @param   number A value.
     * @return  the smallest (closest to negative infinity)
     *          floating-point value that is greater than or equal to
     *          the argument and is equal to a mathematical integer.
     */
    @SuppressWarnings("unused")
    protected float ceiling( Object number )
    {
        return (float) Math.ceil( UtilType.toDouble( number ) );
    }

    /**
     * Returns number rounded up, away from zero, to the nearest multiple of significance.<br>
     * <br>
     * Note: In MS Excel the name is "ceiling" not "ceil" like in Java.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>ceiling(152, 10)</code> returns <code>160.0</code></li>
     *    <li><code>ceiling(0.51, 0.1)</code> returns <code>0.6</code></li>
     * </ul>
     *
     * @param number The numeric value you want to round.
     * @param significance  The multiple to which you want to round.
     * @return float number.
     */
    @SuppressWarnings("unused")
    protected float ceiling( Object number, Object significance )
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
     * <p>
     * Examples:
     * <ul>
     *    <li><code>round(3.4)</code> returns <code>3.0</code></li>
     *    <li><code>round(3.6)</code> returns <code>4.0</code></li>
     * </ul>
     *
     * @param number Number to round.
     * @return float number.
     */
    @SuppressWarnings("unused")
    protected float round( Object number )
    {
        return (float) Math.round( UtilType.toFloat( number ) );
    }

    /**
     * Rounds a number to a specified number of digits.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>round(3.14159, 2)</code> returns <code>3.14</code></li>
     *    <li><code>round(3.14159, 3)</code> returns <code>3.142</code></li>
     * </ul>
     *
     * @param number Number to round.
     * @param decimals Decimal places.
     * @return float number.
     */
    @SuppressWarnings("unused")
    protected float round( Object number, Object decimals )
    {
        float nNum = UtilType.toFloat( number );
        int   nDec = UtilType.toInteger( decimals );

        float multiplier = (float) Math.pow( 10, nDec );

        return (float) Math.round( nNum * multiplier ) / multiplier;
    }

    /**
     * Returns the absolute value of a number. The absolute value of a number is the number without its sign.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>abs(-5)</code> returns <code>5.0</code></li>
     *    <li><code>abs(3)</code> returns <code>3.0</code></li>
     * </ul>
     *
     * @param number
     * @return Its absolute value.
     */
    @SuppressWarnings("unused")
    protected float abs( Object number )
    {
        return Math.abs( UtilType.toFloat( number ) );
    }

    /**
     * Checks if a value is within a specified range (inclusive).
     * <p>
     * Examples:
     * <ul>
     *    <li><code>isBetween(5, 1, 10)</code> returns <code>true</code></li>
     *    <li><code>isBetween(0, 1, 10)</code> returns <code>false</code></li>
     * </ul>
     *
     * @param value The value to check.
     * @param min The minimum value of the range.
     * @param max The maximum value of the range.
     * @return {@code true} if val is between min and max, {@code false} otherwise.
     */
    @SuppressWarnings("unused")
    protected boolean isBetween( Object value, Object min, Object max )
    {
        return UtilUnit.isBetween( UtilType.toFloat( min   ),
                                   UtilType.toFloat( value ),
                                   UtilType.toFloat( max ) );
    }

    /**
     * Constraints a value within a specified range.
     * If the value is less than min, returns min.
     * If the value is greater than max, returns max.
     * Otherwise, returns the value itself.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>setBetween(5, 1, 10)</code> returns <code>5.0</code></li>
     *    <li><code>setBetween(-5, 1, 10)</code> returns <code>1.0</code></li>
     *    <li><code>setBetween(50, 1, 10)</code> returns <code>10.0</code></li>
     * </ul>
     *
     * @param value The value to constraint.
     * @param min The minimum allowed value.
     * @param max The maximum allowed value.
     * @return The constrained value.
     */
    @SuppressWarnings("unused")
    protected float setBetween( Object value, Object min, Object max )
    {
        return UtilUnit.setBetween( UtilType.toFloat( min   ),
                                    UtilType.toFloat( value ),
                                    UtilType.toFloat( max ) );
    }

    /**
     * Returns the remainder after number is divided by divisor.
     * <p>
     * The result has the same sign as the divisor (mathematical modulo).
     * This differs from Java's {@code %} operator which returns a result
     * with the same sign as the dividend.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>mod(10, 3)</code> returns <code>1.0</code></li>
     *    <li><code>mod(10, 5)</code> returns <code>0.0</code></li>
     *    <li><code>mod(-10, 3)</code> returns <code>2.0</code> (not -1.0)</li>
     *    <li><code>mod(10, -3)</code> returns <code>-2.0</code> (not 1.0)</li>
     * </ul>
     *
     * @param number The dividend.
     * @param divisor The divisor (cannot be zero).
     * @return The remainder after number is divided by divisor, with the same sign as divisor.
     */
    @SuppressWarnings("unused")
    protected float mod( Object number, Object divisor )
    {
        float n = UtilType.toFloat( number  );
        float d = UtilType.toFloat( divisor );

        // Mathematical modulo: result has same sign as divisor
        // This handles negative numbers correctly
        float result = n % d;

        if( (result != 0) && ((result < 0) != (d < 0)) )
            result += d;

        return result;
    }

    /**
     * Returns the next pseudo-random, uniformly distributed float value between lower
     * (inclusive) and upper (exclusive) from the random number generator's sequence.<br>
     * <br>
     * Note: In MS Excel it is named RAND (not random).
     * <p>
     * Examples:
     * <ul>
     *    <li><code>rand(1, 10)</code> returns a float between 1.0 and 10.0</li>
     * </ul>
     *
     * @param lower Lower limit (minimum number to be returned).
     * @param upper Upper limit (maximum number to be returned).
     * @return the next pseudo-random, uniformly distributed float value between lower
     *         (inclusive) and upper (exclusive).
     */
    @SuppressWarnings("unused")
    protected float rand( Object lower, Object upper )
    {
        float min = UtilType.toFloat( lower );
        float max = UtilType.toFloat( upper );

        return (float) (ThreadLocalRandom.current().nextFloat() * (max - min) + min);
    }

    /**
     * Formats a number using Java DecimalFormat class.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>format(1234.567, "#.##")</code> returns <code>"1234.57"</code></li>
     *    <li><code>format(0.25, "#%")</code> returns <code>"25%"</code></li>
     * </ul>
     *
     * @param num_str The numeric value or the pattern string.
     * @param format The numeric value or the pattern string.
     * @return The number after being formatted.
     */
    @SuppressWarnings("unused")
    protected String format( Object num_str, Object format )
    {
        String        sPattern = String.valueOf(   (num_str instanceof String ? num_str : format) );
        Float         nNumber  = UtilType.toFloat( (num_str instanceof Number ? num_str : format) );
        DecimalFormat formater = mapFormats.computeIfAbsent( sPattern, DecimalFormat::new );

        synchronized( formater )   // JavaDocs: "Decimal formats are generally not synchronized."
        {
            return formater.format( nNumber );
        }
    }

    //------------------------------------------------------------------------//
    // STRING RELATED FUNCTIONS

    /**
     * Returns the length of the string representation of an object.
     * Equivalent to {@link #len(Object)}.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>size("hello")</code> returns <code>5</code></li>
     *    <li><code>size(12345)</code> returns <code>5</code></li>
     * </ul>
     *
     * @param string The object to measure.
     * @return The number of characters.
     */
    @SuppressWarnings("unused")
    protected int size( Object string )
    {
        return string.toString().length();
    }

    /**
     * Returns the length of the string representation of an object.
     * Equivalent to {@link #size(Object)}.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>len("hello")</code> returns <code>5</code></li>
     * </ul>
     *
     * @param string The object to measure.
     * @return The number of characters.
     */
    @SuppressWarnings("unused")
    protected int len( Object string )
    {
        return string.toString().length();
    }

    /**
     * Removes leading and trailing whitespace from the string representation of an object.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>trim("  hello  ")</code> returns <code>"hello"</code></li>
     * </ul>
     *
     * @param string The object to trim.
     * @return The trimmed string.
     */
    @SuppressWarnings("unused")
    protected String trim( Object string )
    {
        return UtilStr.trim( string.toString() );
    }

    /**
     * Reverses the string representation of an object.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>reverse("abc")</code> returns <code>"cba"</code></li>
     * </ul>
     *
     * @param string The object to reverse.
     * @return The reversed string.
     */
    @SuppressWarnings("unused")
    protected String reverse( Object string )
    {
        return (new StringBuilder( string.toString() )).reverse().toString();
    }

    /**
     * Returns a specified number of characters from the left side of a string.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>left("abcdef", 3)</code> returns <code>"abc"</code></li>
     * </ul>
     *
     * @param string The source object.
     * @param numOfChars  The number of characters to extract.
     * @return The extracted substring.
     */
    @SuppressWarnings("unused")
    protected String left( Object string, Object numOfChars )
    {
        return string.toString().substring(0, UtilType.toInteger( numOfChars ) );
    }

    /**
     * Returns a specified number of characters from the right side of a string.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>right("abcdef", 2)</code> returns <code>"ef"</code></li>
     * </ul>
     *
     * @param string The source object.
     * @param numOfChars  The number of characters to extract.
     * @return The extracted substring.
     */
    @SuppressWarnings("unused")
    protected String right( Object string, Object numOfChars )
    {
        String s = string.toString();

        return s.substring( s.length() - UtilType.toInteger( numOfChars ) );
    }

    /**
     * Converts the string representation of an object to lowercase.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>lower("HeLLo")</code> returns <code>"hello"</code></li>
     * </ul>
     *
     * @param string The object to convert.
     * @return The lowercase string.
     */
    @SuppressWarnings("unused")
    protected String lower( Object string )
    {
        return string.toString().toLowerCase();
    }

    /**
     * Converts the string representation of an object to uppercase.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>upper("hello")</code> returns <code>"HELLO"</code></li>
     * </ul>
     *
     * @param string The object to convert.
     * @return The uppercase string.
     */
    @SuppressWarnings("unused")
    protected String upper( Object string )
    {
        return string.toString().toUpperCase();
    }

    /**
     * Capitalizes the first letter of each word in the string representation of an object.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>proper("mr. smith")</code> returns <code>"Mr. Smith"</code></li>
     * </ul>
     *
     * @param string The object to capitalize.
     * @return The capitalized string.
     */
    @SuppressWarnings("unused")
    protected String proper( Object string )       // Excel name for my Capitalize
    {
        return UtilStr.capitalize( string.toString() );
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
    * <p>
    * Examples:
    * <ul>
    *    <li><code>Char(65)</code> returns <code>"A"</code></li>
    *    <li><code>Char(8364)</code> returns <code>"€"</code></li>
    * </ul>
    *
    * @param codePoint an {@code Object} representing a numeric Unicode code point;
    *                  must be convertible to {@code int} via {@link UtilType#toInteger(Object)}.
    * @return a {@code String} representing the Unicode character if the code point is valid;
    *         otherwise, an empty {@code String}.
    */
    @SuppressWarnings("unused")
    protected String unichar( Object codePoint )
    {
        int code = UtilType.toInteger( codePoint );

        if( ! Character.isValidCodePoint( code ) )
            return "";

        return new String( Character.toChars( code ) );
    }

    /**
     * Returns the Unicode code point of the first character in the given string.
     * This method serves as a Java equivalent to Excel's UNICODE function.
     *
     * <p> The method extracts the first character (or Unicode code point) from the input string.
     * If the first character is a high surrogate (part of a supplementary Unicode character),
     * it will combine with the following low surrogate to return the full code point.
     *
     * <p> Key features:
     * <ul>
     *   <li>Correctly handles supplementary Unicode characters (those requiring two {@code char}
     *       values, like emoji or ancient scripts)</li>
     *   <li>Validates input to ensure it is not null or empty</li>
     *   <li>Returns the code point as an {@code int} in the range 0 to 0x10FFFF</li>
     * </ul>
     *
     * <p> Example usage:
     * <pre>{@code
     * unicode("A");           // Returns: 65
     * unicode("€");           // Returns: 8364
     * unicode("😀");          // Returns: 128512 (U+1F600)
     * unicode("你");          // Returns: 20320 (U+4F60)
     * unicode("Hello");       // Returns: 72 (only first character 'H' is considered)
     * }</pre>
     *
     * <p> Comparison with Excel's UNICODE:
     * <table border="1" cellpadding="3" cellspacing="0" style="margin-top: 0.5em">
     * <caption>Excel UNICODE vs Java unicode()</caption>
     * <tr>
     *   <th>Aspect</th>
     *   <th>Excel UNICODE</th>
     *   <th>Java unicode()</th>
     * </tr>
     * <tr>
     *   <td><b>Input</b></td>
     *   <td>A text string (can be longer than one character)</td>
     *   <td>A String (any length)</td>
     * </tr>
     * <tr>
     *   <td><b>Output</b></td>
     *   <td>Unicode code point of the first character</td>
     *   <td>Unicode code point of the first character</td>
     * </tr>
     * <tr>
     *   <td><b>Supplementary characters</b></td>
     *   <td>Returns correct code point (e.g., 😀 → 128512)</td>
     *   <td>Returns correct code point using surrogate pair decoding</td>
     * </tr>
     * <tr>
     *   <td><b>Empty input</b></td>
     *   <td>Returns #VALUE! error</td>
     *   <td>Throws IllegalArgumentException</td>
     * </tr>
     * </table>
     *
     * @param str the input string (must not be null or empty)
     * @return the Unicode code point (as an int) of the first character in the string
     * @throws IllegalArgumentException if the input string is null or empty
     *
     * @see Character#codePointAt(CharSequence, int)
     * @see Character#isSurrogate(char)
     * @see Character#toChars(int)
     *
     * @since 1.0
     */
    protected int unicode( Object str )
    {
        if( str == null || str.toString().isEmpty() )
            throw new IllegalArgumentException( "Input string must not be null or empty" );

        return str.toString().codePointAt( 0 );   // codePointAt handles surrogate pairs automatically
    }

    /**
     * Performs a case-insensitive search for a substring within a string, starting from the first character.
     * Supports wildcards: '?' for any single character and '*' for any sequence of characters.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>search("l", "Hello")</code> returns <code>3</code></li>
     *    <li><code>search("H?l", "Hello")</code> returns <code>1</code></li>
     *    <li><code>search("e*", "Hello")</code> returns <code>2</code></li>
     * </ul>
     *
     * @param find   The substring to search for.
     * @param within The text to search within.
     * @return The 1-based index of the first occurrence, or 0 if not found.
     */
    @SuppressWarnings("unused")
    protected int search( Object find, Object within )
    {
        return search( find, within, 1 );
    }

    /**
     * SEARCH: case-insensitive search of a substring in a string.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>search("l", "Hello", 4)</code> returns <code>4</code></li>
     * </ul>
     *
     * @param find   The substring to search for: wildcards ('?' and '*')are allowed.
     * @param within The text to search within.
     * @param index  The position to start the search (1-based index).
     * @return The 1-based index of the first occurrence of substring in text, or 0 if not found.
     */
    @SuppressWarnings("unused")
    protected int search( Object find, Object within, Object index )
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
        String finalF = f.replace( "?", "." )
                         .replace( "*", ".*" );

        // Compile the regex pattern
        Pattern pattern = mapPatterns.computeIfAbsent( finalF, Pattern::compile );
        Matcher matcher = pattern.matcher( w );

        if( matcher.find( ndx - 1 ) )
            return matcher.start() + 1;

        return 0;
    }

    /**
     * Replaces occurrences of a substring within a string with another substring.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>substitute("Sales Data", "Sales", "Cost")</code> returns <code>"Cost Data"</code></li>
     * </ul>
     *
     * @param source  The source string.
     * @param oldText The text to be replaced.
     * @param newText The replacement text.
     * @return The resulting string.
     */
    @SuppressWarnings("unused")
    protected String substitute( Object source, Object oldText, Object newText )     // substitute is the Excel name
    {
        return substitute( source, oldText, newText, -1 );
    }

    /**
     * Replaces a specific occurrence of a substring within a string with another substring.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>substitute("one, one, one", "one", "two", 2)</code> returns <code>"one, two, one"</code></li>
     * </ul>
     *
     * @param source     The source string.
     * @param oldText    The text to be replaced.
     * @param newText    The replacement text.
     * @param occurrence The 1-based index of the occurrence to replace (if &lt; 1, all occurrences are replaced).
     * @return The resulting string.
     */
    protected String substitute( Object source, Object oldText, Object newText, Object occurrence )     // substitute is the Excel name
    {
        String sInput = source.toString();
        String sRegEx = oldText.toString();
        String sNew   = newText.toString();
        int    nIndex = UtilType.toInteger( occurrence ) - 1;

        sRegEx = Language.escape( sRegEx );

        if( nIndex < 0 )
            return mapPatterns.computeIfAbsent( sRegEx, Pattern::compile ).matcher( sInput ).replaceAll( sNew );

        // To replace only nth occurrence

        Pattern      pattern = mapPatterns.computeIfAbsent( sRegEx, Pattern::compile );
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

    /**
     * Checks for case-sensitive equality between multiple objects.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>equals("a", "a", "a")</code> returns <code>true</code></li>
     *    <li><code>equals("a", "A")</code> returns <code>false</code></li>
     * </ul>
     *
     * @param data The objects to compare.
     * @return {@code true} if all objects are equal, {@code false} otherwise.
     */
    @SuppressWarnings("unused")
    protected boolean equals( Object... data )     // To check string case-sensitive equality
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

    /**
     * Extracts a substring from the specified position to the end of the string.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>mid("Fluid Flow", 7)</code> returns <code>"Flow"</code></li>
     *    <li><code>mid("Fluid Flow", " ")</code> returns <code>"Flow"</code> (starts after the space)</li>
     * </ul>
     *
     * @param target The source object.
     * @param from   The 1-based starting index or a substring to search for.
     * @return The extracted substring.
     */
    @SuppressWarnings("unused")
    protected String mid( Object target, Object from )      // Like Excel but this is more flexible
    {
        return mid( target, from, null );
    }

    /**
     * Extracts a given specific number of characters from the middle part of a supplied object
     * (previously the object is transformed into its string representation).
     * <p>
     * The starting and ending characters can be defined as numbers or as portions of the where itself.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>mid("En un lugar de la mancha...", 7, 4)</code> returns <code>"lugar"</code></li>
     *    <li><code>mid("A horse, my kingdom for a horse", "my ", " for")</code> returns <code>"kingdom"</code></li>
     * </ul>
     * If the start and end points are out of bounds, then an empty string is returned.
     *
     * @param where Any object: internally it will be converted into a string.
     * @param start Either a number (1 based) or a string.
     * @param end Either a number (chars to extract) or a string (if omitted, to the end).
     * @return The portion of 'where' defined by 'from' and 'to'.
     */
    protected String mid( Object where, Object start, Object end )
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
     * Returns the previous value of the named device, i.e. the value it held immediately before
     * the current evaluation cycle began.
     * <p>
     * On the first evaluation after startup (cold start), there is no prior value and this
     * function returns {@code null}. User expressions that call {@code prev()} should guard
     * against {@code null} if a cold-start result matters.
     * <p>
     * Example:
     * <ul>
     *    <li><code>prev(temperature)</code> returns the temperature reading from the previous cycle</li>
     * </ul>
     *
     * @param devName The device name (passed as a string or variable reference).
     * @return The previous value, or {@code null} if no previous value exists yet.
     */
    @SuppressWarnings("unused")
    protected Object prev( Object devName )
    {
        return threadPrev.get().get( devName.toString() );
    }

    /**
     * Returns the current time in milliseconds elapsed since January 1, 1970 UTC.
     * <p>
     * Note that while the unit of time of the return value is a millisecond, the
     * granularity of the value depends on the underlying operating system and may
     * be larger. For example, many operating systems measure time in units of tens
     * of milliseconds.
     * <p>
     * Example:
     * <ul>
     *    <li><code>utc()</code> returns something like <code>1698412345678</code></li>
     * </ul>
     *
     * @return Current time in milliseconds.
     */
    @SuppressWarnings("unused")
    protected long utc()
    {
        return System.currentTimeMillis();
    }

    /**
     * Returns true if:
     * <ul>
     *    <li>Received parameter is of type string and it is empty or contains only blank spaces.</li>
     *    <li>Received parameter is of type 'list' and it's ::isEmpty() returns true.</li>
     *    <li>Received parameter is of type 'pair' and it's ::isEmpty() returns true.</li>
     * </ul>
     * Returns false in any other case.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>isEmpty("")</code> returns <code>true</code></li>
     *    <li><code>isEmpty("   ")</code> returns <code>true</code></li>
     *    <li><code>isEmpty(list())</code> returns <code>true</code></li>
     *    <li><code>isEmpty(0)</code> returns <code>false</code></li>
     * </ul>
     *
     * @param obj Object to check
     * @return true or false as described.
     */
    @SuppressWarnings("unused")
    protected boolean isEmpty( Object obj )
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
     * <p>
     * Examples:
     * <ul>
     *    <li><code>type(123)</code> returns <code>"N"</code></li>
     *    <li><code>type("123")</code> returns <code>"N"</code></li>
     *    <li><code>type(true)</code> returns <code>"B"</code></li>
     *    <li><code>type("Hello")</code> returns <code>"S"</code></li>
     * </ul>
     *
     * @param obj The object to check.
     * @return "N" or "B" or "S"
     */
    @SuppressWarnings("unused")
    protected String type( Object obj )
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
     * Examples:
     * <ul>
     *    <li><code>iif(true, "Yes", "No")</code> returns <code>"Yes"</code></li>
     *    <li><code>iif(window, "Closed", "Open")</code> (if window is boolean)</li>
     * </ul>
     * <br>
     * NOTE: IF is a reserved Une word.
     *
     * @param oXprResult Expression result.
     * @param onTrue     Value to return if true.
     * @param onFalse    Value to return if false.
     * @return 'onTrue' if 'oXpreRes' is TRUE, 'onFalse' in any other case.
     */
    @SuppressWarnings("unused")
    protected Object iif( Object oXprResult, Object onTrue, Object onFalse )
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
     * Returns a <code>pair</code> with 2 entries:
     * <ul>
     *    <li>"name" : the name of the device.
     *    <li>"value": the value of the device.
     * </ul>
     *
     * Both correspond with the last device that was changed and therefore the cause of current
     * evaluation of the rule.
     * <p>
     * Example:
     * <ul>
     *    <li><code>getTriggeredBy().get("name")</code> returns the name of the triggering device.</li>
     * </ul>
     *
     * @return A <code>pair</code> with 2 entries: "name" and "value".
     */
    @SuppressWarnings("unused")
    protected pair getTriggeredBy()    // This method is needed here so it can be invoked from an expession
    {                                  // (although it is not commonly used).
        if( threadTriggered.get().isEmpty() )
            threadTriggered.get().put( "name", "" ).put( "value", "" );

        return threadTriggered.get();
    }

    /**
     * Requests to the ExEn to terminate the execution.
     * <p>
     * Example:
     * <ul>
     *    <li><code>exit()</code> - Stops the application.</li>
     * </ul>
     *
     * @return Nothing.
     */
    @SuppressWarnings("unused")
    protected Object exit()
    {
        UtilSys.getRuntime().exit( 0 );
        return "";
    }

    //------------------------------------------------------------------------//
    // Internet/IP related

    /**
     * Tests whether a specific TCP port on the given host is reachable within the
     * specified timeout. Firewalls and server configuration may block requests,
     * resulting in an unreachable status while some specific ports may be accessible.<br>
     * <br>
     * The timeout value, in milliseconds, indicates the maximum amount of time the try
     * should take. If the operation times out before getting an answer, the host is deemed
     * unreachable. A negative value will be converted into positive (abs).<br>
     * If an internal error occurs, <code>false</code> is returned.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>isReachable("google.com" , 443, 1000)</code></li>
     *    <li><code>isReachable("192.168.1.1",  80,  500)</code></li>
     * </ul>
     *
     * @param host    The host to test.
     * @param port    The TCP port to test.
     * @param timeout Timeout in milliseconds.
     * @return {@code true} if reachable, {@code false} otherwise.
     */
    protected boolean isReachable( Object host, Object port, Object timeout )
    {
        int nPort  = Math.abs( UtilType.toInteger( port ) );
        int millis = Math.max( 300, Math.abs( UtilType.toInteger( timeout ) ) );

        if( ! UtilComm.isValidPort( nPort ) )
            throw new MingleException( MingleException.INVALID_ARGUMENTS, nPort );

        try( java.net.Socket socket = new java.net.Socket() )
        {
            socket.connect( new java.net.InetSocketAddress( host.toString(), nPort ), millis );
            return true;
        }
        catch( Exception e )
        {
            return false;
        }
    }

    /**
     * Returns a list of all local non-loopback IP addresses.
     * <p>
     * Example:
     * <ul>
     *    <li><code>localIPs()</code> returns <code>["192.168.1.50"]</code></li>
     * </ul>
     *
     * @return A {@link list} of IP address strings.
     */
    @SuppressWarnings("unused")
    protected list localIPs()
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

    /**
     * Stores a value in the global variables map.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>put("myVar", 100)</code> stores 100 in "myVar"</li>
     * </ul>
     *
     * @param key   The variable name (case-insensitive).
     * @param value The value to store.
     * @return {@code true} if the operation was successful.
     */
    @SuppressWarnings("unused")
    protected boolean put( Object key, Object value )
    {
        if( key instanceof String )
            key = key.toString().toLowerCase();

        return UtilSys.put( key, value );
    }

    /**
     * Retrieves a value from the global variables map. Returns an empty string if not found.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>get("myVar")</code> returns <code>100</code> (if previously set)</li>
     * </ul>
     *
     * @param key The variable name (case-insensitive).
     * @return The stored value, or an empty string if not found.
     */
    @SuppressWarnings("unused")
    protected Object get( Object key )
    {
        return get( key, "" );
    }

    /**
     * Retrieves a value from the global variables map, with a default value if not found.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>get("missingVar", 0)</code> returns <code>0</code></li>
     * </ul>
     *
     * @param key The variable name (case-insensitive).
     * @param def The default value to return if the key is not found.
     * @return The stored value, or the default value.
     */
    @SuppressWarnings("unused")
    protected Object get( Object key, Object def )
    {
        if( key instanceof String )
            key = key.toString().toLowerCase();

        return UtilSys.get( key, def );
    }

    /**
     * Deletes a value from the global variables map.
     * <p>
     * Examples:
     * <ul>
     *    <li><code>del("myVar")</code> removes "myVar" from the map</li>
     * </ul>
     *
     * @param key The variable name (case-insensitive).
     * @return {@code true} if the key was removed.
     */
    @SuppressWarnings("unused")
    protected boolean del( Object key )
    {
        if( key instanceof String )
            key = key.toString().toLowerCase();

        return UtilSys.del( key );
    }

    /**
     * Enables the RULE with the given name so it reacts to device changes.
     * <p>
     * This function is intended to be used in a THEN clause:
     * <pre>THEN enable("my_rule")</pre>
     *
     * @param ruleName The name of the RULE to enable (case-insensitive).
     * @return {@code true} if the rule was found and enabled, {@code false} otherwise.
     */
    @SuppressWarnings("unused")
    protected boolean enable( Object ruleName )
    {
        IRule rule = getRule( ruleName );

        if( rule != null )
            rule.enable();

        return (rule != null);
    }

    /**
     * Disables the RULE with the given name so it ignores device changes without removing it.
     * <p>
     * This function is intended to be used in a THEN clause:
     * <pre>THEN disable("my_rule")</pre>
     *
     * @param ruleName The name of the RULE to disable (case-insensitive).
     * @return {@code true} if the rule was found and disabled, {@code false} otherwise.
     */
    @SuppressWarnings("unused")
    protected boolean disable( Object ruleName )
    {
        IRule rule = getRule( ruleName );

        if( rule != null )
            rule.disable();

        return (rule != null);
    }

    private static IRule getRule( Object ruleName )
    {
        if( ruleName == null || UtilSys.getRuntime() == null )
            return null;

        ICommand cmd = UtilSys.getRuntime().get( ruleName.toString() );

        return (cmd instanceof IRule) ? (IRule) cmd : null;
    }

    //------------------------------------------------------------------------//
    // PRIVATE CONSTRUCTOR

    private StdXprFns()
    {
        // To avoid instances creation
    }

    //------------------------------------------------------------------------//
    // AUXILIARY FUNCTIONS

    private static String toInvocation( Class clazz, String sFn, Object[] aoArgs )
    {
        if( UtilColls.isEmpty( aoArgs ) )
            return sFn + "()";

        if( clazz != null )
            sFn = clazz.getSimpleName() +':'+ sFn;

        // Pre-size StringBuilder: function name + parentheses + estimated arg size
        int estimatedSize = sFn.length() + 2 + (aoArgs.length * 12);
        StringBuilder sb = new StringBuilder( estimatedSize );
        sb.append( sFn ).append( '(' );

        for( Object obj : aoArgs )
        {
            if( obj instanceof Number )
            {
                // Format numbers nicely: use integer format if no decimal part
                float floatVal = ((Number) obj).floatValue();
                int   intVal   = (int) floatVal;

                if( Math.abs( floatVal - intVal ) <= 0.00001f )
                    sb.append( intVal );
                else
                    sb.append( floatVal );
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
        private final Class   clazz;
        private final String  fnName;
        private final Class[] aParams;
        private final int     hashCode; // Make final and calculate once

        MapKey(Class clazz, String fnName, Class[] aParams)
        {
            this.clazz   = clazz;
            this.fnName  = fnName;
            this.aParams = aParams;

            int hash = 7;
                hash = 97 * hash + Objects.hashCode( clazz );
                hash = 97 * hash + Objects.hashCode( fnName );
                hash = 97 * hash + Arrays.hashCode( aParams );  // Uses Arrays.hashCode instead of deepHashCode for Class arrays
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

            if( obj == null || getClass() != obj.getClass() )
                return false;

            final MapKey other = (MapKey) obj;

            return this.hashCode == other.hashCode               &&
                   Objects.equals( this.clazz  , other.clazz   ) &&
                   Objects.equals( this.fnName , other.fnName  ) &&
                   Arrays.equals(  this.aParams, other.aParams );
        }
    }
}