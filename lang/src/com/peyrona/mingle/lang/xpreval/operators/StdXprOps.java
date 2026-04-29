
package com.peyrona.mingle.lang.xpreval.operators;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.xpreval.functions.Operable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Une Standard Expressions Operators.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class StdXprOps
{
    public  static final String sUNARY_MINUS = "ª";     // The easiest way of dealing with the unary minus operator is by providing a different symbol to differentiate it from the minus as subtraction
    private static final Map<String,Operator> map = init();

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    public static Operator get( String sOp )
    {
        return map.get( sOp );
    }

    public static String[] getAll()
    {
        return map.keySet().toArray( String[]::new );
    }

    public static Object eval( String sOp, Object... args )
    {
        for( int n = 0; n < args.length; n++ )    // Fastest loop
            if( args[n] == null )
                return null;

        return map.get( sOp ).eval( args );
    }

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    private static Map<String,Operator> init()
    {
        Map<String,Operator> tmp = new HashMap<>();

        // Arithmetic ---------------------------------------------------------------------------------------

        tmp.put( sUNARY_MINUS, new Operator<Number>( Operator.PRECEDENCE_UNARY, false )                 // Minus unary
                        {
                            @Override
                            protected Number eval( Object... args )
                            {
                                if( args[0] instanceof Integer )
                                    return -(Integer) args[0];

                                if( isL( Number.class, args ) )
                                    return (- getNumL( args ));

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "+" , new Operator<Object>( Operator.PRECEDENCE_ADDITIVE )
                        {   @Override
                            protected Object eval( Object... args )
                            {
                                if( areAll( String.class, args ) )                                      // Concatenate strings
                                    return getStrL(args).concat( getStrR(args) );                       // e.g.: "Hello " + "world" == "Hello world";  e.g.: "12" + "34" == "1234"

                                if( isL( Operable.class, args ) && isR( Number.class, args ) )          // e.g.: (date() + 3)  or  (time() + 60)
                                    return ((Operable) getL( args )).move( getNumR( args ) );           // It does not make sense to add a date to a date or a time to a time

                                if( isR( Operable.class, args ) && isL( Number.class, args ) )          // e.g.: (3 + date())  or  (60 + time())
                                    return ((Operable) getR( args )).move( getNumL( args ) );           // Note: it does not make sense to add a date to a date or a time to a time

                                Object[] nArgs = toNum( args );
                                if( nArgs != null )                                                     // This 'if' has to be the last one  e.g.: 10 + 2   or  "10" + 2 (JS-wise)
                                {
                                    if( areAllIntegers( nArgs ) )
                                        return getIntL( nArgs ) + getIntR( nArgs );
                                    return getNumL( nArgs ) + getNumR( nArgs );
                                }

                                return args[0].toString() + args[1].toString();                         // Can not be null
                            }
                        } );

        tmp.put( "-" , new Operator<Object>( Operator.PRECEDENCE_ADDITIVE )
                        {   @Override
                            protected Object eval( Object... args )
                            {
                                if( areAll( String.class, args ) )                                      // Removes all ocurrences of 2nd str found in 1st str
                                    return getStrL(args).replace( getStrR(args), "" );                  // e.g.: "Caco malo" - "o" == "Cac mal";  e.g.: "1234" - "4" == "123"

                                if( isL( Operable.class, args ) )
                                {
                                    if( isR( Number.class, args ) )
                                        return ((Operable) getL( args )).move( getNumR( args ) * -1 );  // e.g.: (date() - 3)  or  (time() - 60)
                                    else if( isR( Operable.class, args ) )
                                        return ((Operable) getL( args )).duration( getR( args ) );      // e.g.: (date2 - date1)  or  (time2 - time1)
                                }

                                if( isR( Operable.class, args ) )
                                {
                                    if( isL( Number.class, args ) )
                                        return ((Operable) getR( args )).move( getNumL( args ) * -1 );  // e.g.: (3 - date())  or  (60 - time())
                                    else if( isL( Operable.class, args ) )
                                        return ((Operable) getR( args)).duration( getL( args ) );       // e.g.: (date1 - date2)  or  (time1 - time2)
                                }

                                Object[] nArgs = toNum( args );
                                if( nArgs != null )                                                     // This 'if' has to be the last one  e.g.: 10 - 2  or  "10" - 2  (JS-wise)
                                {
                                    if( areAllIntegers( nArgs ) )
                                        return getIntL( nArgs ) - getIntR( nArgs );
                                    return getNumL( nArgs ) - getNumR( nArgs );
                                }

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "*" , new Operator<Number>( Operator.PRECEDENCE_MULTIPLICATIVE )                      // Note: '*' with string and number: (2 * "pe") messes all up
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                Object[] nArgs = toNum( args );
                                if( nArgs != null )                                                    // e.g.: 10 * 2  or  10 * 2 (JS-wise)
                                {
                                    if( areAllIntegers( nArgs ) )
                                        return getIntL( nArgs ) * getIntR( nArgs );

                                    return getNumL( nArgs ) * getNumR( nArgs );
                                }

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "/" , new Operator<Number>( Operator.PRECEDENCE_MULTIPLICATIVE )
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                Object[] nArgs = toNum( args );
                                if( nArgs != null )                                                    // e.g.: 10 / 2  or  "10" / 2 (JS-wise)
                                {
                                    float divisor = getNumR( nArgs );

                                    if( divisor == 0f )
                                        return (getNumL( nArgs ) > 0) ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;

                                    return getNumL( nArgs ) / divisor;
                                }

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "%" , new Operator<Number>( Operator.PRECEDENCE_MULTIPLICATIVE )                      // Arit. percent
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                Object[] nArgs = toNum( args );
                                if( nArgs != null )                                                    // e.g.: 2 % 10.0 or  "2" % 10 (JS-wise)
                                {
                                    float percent = ((Number) nArgs[0]).floatValue();
                                    float total   = ((Number) nArgs[1]).floatValue();
                                    return (percent / 100) * total;
                                }

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "^" , new Operator<Number>( Operator.PRECEDENCE_POWER )                               // Arit. power
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                Object[] nArgs = toNum( args );
                                if( nArgs != null )                                                    // e.g.: 10.0 ** 2   or  "10" ** 2  (JS-wise)
                                    return (float) Math.pow( getNumL(nArgs), getNumR(nArgs) );

                                throw unsupported( args );
                            }
                        } );

        // Relational -------------------------------------------------------------------------------------

        tmp.put( "==", new Operator<Boolean>( Operator.PRECEDENCE_EQUALITY )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Comparable.class, args ) )                                 // Comparable
                                    return compare( args ) == 0;

                                Object[] nArgs = toNum( args );
                                if( nArgs != null )                                                    // e.g.: "10" == 2 == false  (JS-wise)
                                    return Float.compare( getNumL( nArgs ), getNumR( nArgs ) ) == 0;

                                Object[] bArgs = toBool( args );
                                if( bArgs != null )                                                   // e.g.: "true" == true
                                    return (getBolL( bArgs ).equals( getBolR( bArgs ) ));

                                return false;
                            }
                        } );

        tmp.put( "!=", new Operator<Boolean>( Operator.PRECEDENCE_EQUALITY )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Comparable.class, args ) )                                 // Comparable
                                    return compare( args ) != 0;

                                Object[] nArgs = toNum( args );
                                if( nArgs != null )                                                    // e.g.: "10" % 2 == true  (JS-wise)
                                    return Float.compare( getNumL(nArgs), getNumR(nArgs) ) != 0;

                                Object[] bArgs = toBool( args );
                                if( bArgs != null )                                                   // e.g.: "true" == true
                                    return ! (getBolL( bArgs ).equals( getBolR( bArgs ) ));

                                return true;
                            }
                        } );

        tmp.put( ">" , new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Comparable.class, args ) )                                 // Comparable
                                    return compare( args ) > 0;

                                Object[] nArgs = toNum( args );
                                if( nArgs != null )   // e.g.: "10" > 2 == true  (JS-wise)
                                    return Float.compare( getNumL(nArgs), getNumR(nArgs) ) > 0;

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "<" , new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Comparable.class, args ) )                                 // Comparable
                                    return compare( args ) < 0;

                                Object[] nArgs = toNum( args );
                                if( nArgs != null )                                                    // e.g.: "10" < 2 == false  (JS-wise)
                                    return Float.compare( getNumL(nArgs), getNumR(nArgs) ) < 0;

                                throw unsupported( args );
                            }
                        } );

        tmp.put( ">=", new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Comparable.class, args ) )                                 // Comparable
                                    return compare( args ) >= 0;

                                Object[] nArgs = toNum( args );
                                if( nArgs != null )   // e.g.: "10" >= 2 == true  (JS-wise)
                                    return Float.compare( getNumL(nArgs), getNumR(nArgs) ) >= 0;

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "<=", new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Comparable.class, args ) )                                 // Comparable
                                    return compare( args ) <= 0;

                                Object[] nArgs = toNum( args );
                                if( nArgs != null )                                                    // e.g.: "10" <= 2 == false  (JS-wise)
                                    return Float.compare( getNumL(nArgs), getNumR(nArgs) ) <= 0;

                                throw unsupported( args );
                            }
                        } );

        // Conditional ----------------------------------------------------------------------------------------

        tmp.put( "&&", new Operator<Boolean>( Operator.PRECEDENCE_AND )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                return UtilType.isTruthy( args[0] ) && UtilType.isTruthy( args[1] );
                            }
                        } );

        tmp.put( "||", new Operator<Boolean>( Operator.PRECEDENCE_OR )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                return UtilType.isTruthy( args[0] ) || UtilType.isTruthy( args[1] );
                            }
                        } );

        tmp.put( "|&", new Operator<Boolean>( Operator.PRECEDENCE_OR )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                boolean a = UtilType.isTruthy( args[0] );
                                boolean b = UtilType.isTruthy( args[1] );

                                return (a || b) && (!(a && b));
                            }
                        } );

        tmp.put( "!" , new Operator<Boolean>( Operator.PRECEDENCE_NOT, false )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                return ! UtilType.isTruthy( args[0] );
                            }
                        } );

        // Bitwise -----------------------------------------------------------------------------------------

        tmp.put( "&", new Operator<Number>( Operator.PRECEDENCE_BITWISE )          // Bit AND
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                Object[] nArgs = toNum( args );
                                if( nArgs != null )
                                    return ((Number) nArgs[0]).intValue() & ((Number) nArgs[1]).intValue();

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "|" , new Operator<Number>( Operator.PRECEDENCE_BITWISE )          // Bit OR
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                Object[] nArgs = toNum( args );
                                if( nArgs != null )
                                    return ((Number) nArgs[0]).intValue() | ((Number) nArgs[1]).intValue();

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "><" , new Operator<Number>( Operator.PRECEDENCE_BITWISE )         // Bit XOR
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                Object[] nArgs = toNum( args );
                                if( nArgs != null )
                                    return ((Number) nArgs[0]).intValue() ^ ((Number) nArgs[1]).intValue();

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "~" , new Operator<Number>( Operator.PRECEDENCE_BITWISE, false )   // Bit NOT
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                if( isL( Number.class, args ) )
                                    return ~((Number) args[0]).intValue();

                                throw unsupported( args );
                            }
                        } );

        tmp.put( ">>", new Operator<Number>( Operator.PRECEDENCE_BITWISE )          // SHIFT RIGHT
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                Object[] nArgs = toNum( args );
                                if( nArgs != null )
                                    return ((Number) nArgs[0]).intValue() >> ((Number) nArgs[1]).intValue();

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "<<", new Operator<Number>( Operator.PRECEDENCE_BITWISE )          // SHIFT LEFT
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                Object[] nArgs = toNum( args );
                                if( nArgs != null )
                                    return ((Number) nArgs[0]).intValue() << ((Number) nArgs[1]).intValue();

                                throw unsupported( args );
                            }
                        } );

        // Others ----------------------------------------------------------------------------------------

        // Not used: resolved at eval() method. But must exist in order to recognize the operator ':'

        tmp.put( ":" , new Operator<Object>( Operator.PRECEDENCE_SEND )
                        {   @Override
                            protected Object eval( Object... args )
                            {
                                return null;
                            }
                        } );

        // Not used: resolved by Candi:ParseDevice and by CIL:RuleAction

        tmp.put( "=", new Operator<Object>( Operator.PRECEDENCE_ASSIGNMENT )
                        {   @Override
                            protected Object eval( Object... args )
                            {
                                return null;
                            }
                        } );

        // Edge-detection operators (?>, ?<, ?=, ?!=, ?<>) -------------------------------------------------------
        // Note: their eval() bodies are stubs — actual evaluation is handled in ASTNode::evalEdgeOp(),
        // where both the current and previous device value can be accessed from the vars map.
        // These registrations are required so that XprTokenizer recognises the symbols as valid operators.

        tmp.put( "?>",  new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON ) { @Override protected Boolean eval( Object... args ) { return Boolean.FALSE; } } );
        tmp.put( "?<",  new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON ) { @Override protected Boolean eval( Object... args ) { return Boolean.FALSE; } } );
        tmp.put( "?=",  new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON ) { @Override protected Boolean eval( Object... args ) { return Boolean.FALSE; } } );
        tmp.put( "?!=", new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON ) { @Override protected Boolean eval( Object... args ) { return Boolean.FALSE; } } );
        tmp.put( "?<>", new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON ) { @Override protected Boolean eval( Object... args ) { return Boolean.FALSE; } } );

        return Collections.unmodifiableMap( tmp );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE
    // AUXILIARY FUNCTIONS

    private static boolean isL( Class clazz, Object... args )
    {
        return clazz.isAssignableFrom( args[0].getClass() );
    }

    private static boolean isR( Class clazz, Object... args )
    {
        return clazz.isAssignableFrom( args[1].getClass() );
    }

    /**
     *
     * <p>
     * This is a fail fast method.
     *
     * @param clazz
     * @param objs
     * @return
     */
    private static boolean areAll( Class clazz, Object... objs )
    {
        if( clazz == Comparable.class )    // This is a special case because all objects have to be of same class (isAssignableFrom(...) will not work)
        {
            Class cl = objs[0].getClass();

            if( ! (objs[0] instanceof Comparable) )
                return false;

            for( int n = 1; n < objs.length; n++ )
                if( objs[n].getClass() != cl )
                    return false;

            return true;
        }
        else
        {
            for( Object obj : objs )
                if( ! clazz.isAssignableFrom( obj.getClass() ) )
                    return false;

            return true;
        }
    }

    private static Object getL( Object... args )
    {
        return args[0];
    }

    private static Object getR( Object... args )
    {
        return args[1];
    }

    private static float getNumL( Object... args )
    {
        return ((Number) args[0]).floatValue();
    }

    private static float getNumR( Object... args )
    {
        return ((Number) args[1]).floatValue();
    }

    private static int getIntL( Object... args )
    {
        return (Integer) args[0];
    }

    private static int getIntR( Object... args )
    {
        return (Integer) args[1];
    }

    private static String getStrL( Object... args )
    {
        return args[0].toString();
    }

    private static String getStrR( Object... args )
    {
        return args[1].toString();
    }

    private static Boolean getBolL( Object... args )
    {
        return (Boolean) args[0];
    }

    private static Boolean getBolR( Object... args )
    {
        return (Boolean) args[1];
    }

    /**
     * Tries to convert all args to the most specific numeric type, preserving {@code Integer}
     * and {@code Float} as-is and parsing {@code String} values as {@code Integer} first,
     * falling back to {@code Float}.
     * <p>
     * This is a fail-fast method.
     *
     * @param args The arguments to convert.
     * @return Converted args, or {@code null} if any conversion failed.
     */
    private static Object[] toNum( Object... args )
    {
        Object[] newArgs = new Object[args.length];

        try
        {
            if( args.length == 1 )   // Faster
            {
                newArgs[0] = toNumber( args[0] );
            }
            else
            {
                for( int n = 0; n < args.length; n++ )
                    newArgs[n] = toNumber( args[n] );
            }
        }
        catch( MingleException me )
        {
            return null;
        }

        return newArgs;
    }

    /**
     * Converts a single value to the most specific numeric type.
     * <ul>
     *   <li>{@code Integer} is returned as-is.</li>
     *   <li>{@code Float} is returned as-is.</li>
     *   <li>Other {@code Number} subtypes are converted to {@code Float}.</li>
     *   <li>{@code String} is parsed as {@code Integer} (no decimal point) or {@code Float}.</li>
     * </ul>
     *
     * @param n The value to convert.
     * @return An {@code Integer} or {@code Float} representation of the value.
     * @throws MingleException If the value cannot be interpreted as a number.
     */
    private static Number toNumber( Object n )
    {
        if( n instanceof Integer )  return (Integer) n;
        if( n instanceof Float   )  return (Float)   n;
        if( n instanceof Number  )  return ((Number) n).floatValue();

        if( n instanceof String  )
        {
            String s = (String) n;
            try { return Integer.valueOf( s ); }
            catch( NumberFormatException ignored ) { }
            try { return Float.valueOf(   s ); }
            catch( NumberFormatException ignored ) { }
        }

        throw new MingleException( "Invalid number \""+ n +'"' );
    }

    /**
     * Returns {@code true} if every element in {@code args} is an instance of {@code Integer}.
     *
     * @param args The arguments to check.
     * @return {@code true} when all args are {@code Integer}, {@code false} otherwise.
     */
    private static boolean areAllIntegers( Object... args )
    {
        for( Object arg : args )
            if( ! (arg instanceof Integer) )
                return false;

        return true;
    }

    /**
     * Tries to change all instances of String to its Number.
     * * <p>
     * This is a fail fast method.
     *
     * @param args
     * @return Converted args or null if any conversion failed.
     */
    private static Object[] toBool( Object... args )
    {
        Object[] newArgs = new Object[args.length];

        try
        {
            if( args.length == 1 )   // Faster
            {
                newArgs[0] = UtilType.toBoolean( args[0] );
            }
            else
            {
                for( int n = 0; n < args.length; n++ )
                    newArgs[n] = UtilType.toBoolean( args[n] );
            }
        }
        catch( MingleException me )
        {
            return null;
        }

        return newArgs;
    }

    private static int compare( Object[] args )
    {
        Comparable c0 = (Comparable) args[0];
        Comparable c1 = (Comparable) args[1];

        if( areAll( String.class, args ) )
        {
            c0 = args[0].toString().toLowerCase();      // Because Une is
            c1 = args[1].toString().toLowerCase();      // case insensitive
        }

        return c0.compareTo( c1 );
    }

    private static MingleException unsupported( Object... args )
    {
        StringBuilder sb = new StringBuilder( args.length * 16 );
                      sb.append( "Invalid arguments: " );

        for( Object obj : args )
        {
            String s = obj.getClass().getSimpleName().toLowerCase();
            if( "float".equals(s) || "integer".equals(s) )  s = "number";
            sb.append( obj ).append( '[' ).append( s ).append( "], " );
        }

        UtilStr.removeLast( sb, 2 );

        return new MingleException( sb.toString() );
    }
}