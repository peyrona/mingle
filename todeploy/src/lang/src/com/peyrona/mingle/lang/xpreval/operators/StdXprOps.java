
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
    private static final Map<String,Operator> map = new StdXprOps().init();

    //------------------------------------------------------------------------//
    // PUBLIC INTERFACE

    public Operator get( String sOp )
    {
        return map.get( sOp );
    }

    public String[] getAll()
    {
        return map.keySet().toArray( String[]::new );
    }

    public Object eval( String sOp, Object... args )
    {
        for( int n = 0; n < args.length; n++ )    // Fastest loop
            if( args[n] == null )
                return null;

        return map.get( sOp ).eval( args );
    }

    //------------------------------------------------------------------------//
    // PRIVATE INTERFACE

    private Map<String,Operator> init()
    {
        Map<String,Operator> tmp = new HashMap<>();

        // Arithmetic ---------------------------------------------------------------------------------------

        tmp.put( sUNARY_MINUS, new Operator<Number>( Operator.PRECEDENCE_UNARY, false )                 // Minus unary
                        {
                            @Override
                            protected Number eval( Object... args )
                            {
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

                                if( toNum( args ) )                                                     // e.g.: 10 + 2   or  "10" + 2 (JS-wise)
                                    return getNumL( args ) + getNumR( args );                           // This 'if' has to be the last one

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

                                if( toNum( args ) )                                                     // e.g.: 10 - 2  or  "10" - 2  (JS-wise)
                                    return getNumL( args ) - getNumR( args );                           // This 'if' has to be the last one

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "*" , new Operator<Number>( Operator.PRECEDENCE_MULTIPLICATIVE )                      // Note: '*' with string and number: (2 * "pe") messes all up
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                if( toNum( args ) )                                                    // e.g.: 10 * 2  or  10 * 2 (JS-wise)
                                    return getNumL( args ) * getNumR( args );

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "/" , new Operator<Number>( Operator.PRECEDENCE_MULTIPLICATIVE )
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                if( toNum( args ) )                                                    // e.g.: 10 / 2  or  "10" / 2 (JS-wise)
                                {
                                    Float result = getNumL( args ) / getNumR( args );

                                    return (Float.isInfinite( result ) ? Float.NaN : result);
                                }

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "%" , new Operator<Number>( Operator.PRECEDENCE_MULTIPLICATIVE )                      // Arit. percent
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                if( toNum( args ) )                                                    // e.g.: 2 % 10.0 or  "2" % 10 (JS-wise)
                                {
                                    float percent = ((Number) args[0]).floatValue();
                                    float total   = ((Number) args[1]).floatValue();
                                    return (percent / 100) * total;
                                }

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "^" , new Operator<Number>( Operator.PRECEDENCE_POWER )                               // Arit. power
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                if( toNum( args ) )                                                    // e.g.: 10.0 ** 2   or  "10" ** 2  (JS-wise)
                                    return (float) Math.pow( getNumL(args), getNumR(args) );

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

                                if( toNum( args ) )                                                    // e.g.: "10" == 2 == false  (JS-wise)
                                    return Float.compare( getNumL( args ), getNumR( args ) ) == 0;

                                if( toBool( args ) )                                                   // e.g.: "true" == true
                                    return (getBolL( args ).equals( getBolR( args ) ));

                                return false;
                            }
                        } );

        tmp.put( "!=", new Operator<Boolean>( Operator.PRECEDENCE_EQUALITY )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Comparable.class, args ) )                                 // Comparable
                                    return compare( args ) != 0;

                                if( toNum( args ) )                                                    // e.g.: "10" % 2 == true  (JS-wise)
                                    return Float.compare( getNumL(args), getNumR(args) ) != 0;

                                if( toBool( args ) )                                                   // e.g.: "true" == true
                                    return ! (getBolL( args ).equals( getBolR( args ) ));

                                return true;
                            }
                        } );

        tmp.put( ">" , new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Comparable.class, args ) )                                 // Comparable
                                    return compare( args ) > 0;

                                if( toNum( args ) )   // e.g.: "10" > 2 == true  (JS-wise)
                                    return Float.compare( getNumL(args), getNumR(args) ) > 0;

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "<" , new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Comparable.class, args ) )                                 // Comparable
                                    return compare( args ) < 0;

                                if( toNum( args ) )                                                    // e.g.: "10" < 2 == false  (JS-wise)
                                    return Float.compare( getNumL(args), getNumR(args) ) < 0;

                                throw unsupported( args );
                            }
                        } );

        tmp.put( ">=", new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Comparable.class, args ) )                                 // Comparable
                                    return compare( args ) >= 0;

                                if( toNum( args ) )   // e.g.: "10" >= 2 == true  (JS-wise)
                                    return Float.compare( getNumL(args), getNumR(args) ) >= 0;

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "<=", new Operator<Boolean>( Operator.PRECEDENCE_COMPARISON )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Comparable.class, args ) )                                 // Comparable
                                    return compare( args ) <= 0;

                                if( toNum( args ) )                                                    // e.g.: "10" <= 2 == false  (JS-wise)
                                    return Float.compare( getNumL(args), getNumR(args) ) <= 0;

                                throw unsupported( args );
                            }
                        } );

        // Conditional ----------------------------------------------------------------------------------------

        tmp.put( "&&", new Operator<Boolean>( Operator.PRECEDENCE_AND )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Boolean.class, args ) )
                                    return getBolL(args) && getBolR(args);

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "||", new Operator<Boolean>( Operator.PRECEDENCE_OR )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Boolean.class, args ) )
                                    return getBolL(args) || getBolR(args);

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "|&", new Operator<Boolean>( Operator.PRECEDENCE_OR )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( areAll( Boolean.class, args ) )
                                {
                                    Boolean a = getBolL(args);
                                    Boolean b = getBolR(args);

                                    return (a || b) && (!(a && b));
                                }

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "!" , new Operator<Boolean>( Operator.PRECEDENCE_NOT, false )
                        {   @Override
                            protected Boolean eval( Object... args )
                            {
                                if( isL( Boolean.class, args ) )
                                    return (! getBolL( args ));

                                throw unsupported( args );
                            }
                        } );

        // Bitwise -----------------------------------------------------------------------------------------

        tmp.put( "&", new Operator<Number>( Operator.PRECEDENCE_BITWISE )          // Bit AND
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                if( toNum( args ) )
                                    return (float) ( ((Float) getNumL( args )).intValue() & ((Float) getNumR( args )).intValue() );

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "|" , new Operator<Number>( Operator.PRECEDENCE_BITWISE )          // Bit OR
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                if( toNum( args ) )
                                    return (float) ( ((Float) getNumL( args )).intValue() | ((Float) getNumR( args )).intValue() );

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "><" , new Operator<Number>( Operator.PRECEDENCE_BITWISE )         // Bit XOR
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                if( toNum( args ) )
                                    return (float) ( ((Float) getNumL( args )).intValue() ^ ((Float) getNumR( args )).intValue() );

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "~" , new Operator<Number>( Operator.PRECEDENCE_BITWISE, false )   // Bit NOT
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                if( isL( Number.class, args ) )
                                    return (float) ( ~ ((Float) getNumL( args )).intValue() );

                                throw unsupported( args );
                            }
                        } );

        tmp.put( ">>", new Operator<Number>( Operator.PRECEDENCE_BITWISE )          // SHIFT RIGHT
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                if( toNum( args ) )
                                    return (float) ( ((Float) getNumL( args )).intValue() >> ((Float) getNumR( args )).intValue() );

                                throw unsupported( args );
                            }
                        } );

        tmp.put( "<<", new Operator<Number>( Operator.PRECEDENCE_BITWISE )          // SHIFT LEFT
                        {   @Override
                            protected Number eval( Object... args )
                            {
                                if( toNum( args ) )
                                    return (float) ( ((Float) getNumL( args )).intValue() << ((Float) getNumR( args )).intValue() );

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

        return Collections.unmodifiableMap( tmp );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE
    // AUXILIARY FUNCTIONS

    private boolean isL( Class clazz, Object... args )
    {
        return clazz.isAssignableFrom( args[0].getClass() );
    }

    private boolean isR( Class clazz, Object... args )
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
    private boolean areAll( Class clazz, Object... objs )
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

    private Object getL( Object... args )
    {
        return args[0];
    }

    private Object getR( Object... args )
    {
        return args[1];
    }

    private float getNumL( Object... args )
    {
        return ((Number) args[0]).floatValue();
    }

    private float getNumR( Object... args )
    {
        return ((Number) args[1]).floatValue();
    }

    private String getStrL( Object... args )
    {
        return args[0].toString();
    }

    private String getStrR( Object... args )
    {
        return args[1].toString();
    }

    private Boolean getBolL( Object... args )
    {
        return (Boolean) args[0];
    }

    private Boolean getBolR( Object... args )
    {
        return (Boolean) args[1];
    }

    /**
     * Tries to change all instances of String to its Number.
     * <p>
     * This is a fail fast method.
     *
     * @param args
     * @return true if all Strings were successfully converted into their Number.
     */
    private boolean toNum( Object... args )
    {
        for( int n = 0; n < args.length; n++ )
        {
            try
            {
                args[n] = UtilType.toFloat( args[n] );    // This takes care of '_' in case the arg is an string (sse this class doc)
            }
            catch( MingleException me )
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Tries to change all instances of String to its Number.
     * * <p>
     * This is a fail fast method.
     *
     * @param args
     * @return true if all Strings were successfully converted into their Number.
     */
    private boolean toBool( Object... args )
    {
        for( int n = 0; n < args.length; n++ )
        {
            try
            {
                args[n] = UtilType.toBoolean( args[n] );
            }
            catch( MingleException me )
            {
                return false;
            }
        }

        return true;
    }

    private int compare( Object[] args )
    {
        if( areAll( String.class, args ) )
        {
            args[0] = args[0].toString().toLowerCase();      // Because Une is
            args[1] = args[1].toString().toLowerCase();      // case insensitive
        }

        return ((Comparable) args[0]).compareTo( (Comparable) args[1] );
    }

    private MingleException unsupported( Object... args )
    {
        StringBuilder sb = new StringBuilder( args.length * 16 );
                      sb.append( "Invalid arguments: " );

        for( Object obj : args )
        {
            String s = obj.getClass().getSimpleName().toLowerCase();
                   s = "float".equals(s) ? "number" : s;
            sb.append( obj ).append( '[' ).append( s ).append( "], " );
        }

        UtilStr.removeLast( sb, 2 );

        return new MingleException( sb.toString() );
    }
}