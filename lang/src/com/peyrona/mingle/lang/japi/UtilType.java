
package com.peyrona.mingle.lang.japi;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.xpreval.functions.ExtraType;
import com.peyrona.mingle.lang.xpreval.functions.date;
import com.peyrona.mingle.lang.xpreval.functions.list;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import com.peyrona.mingle.lang.xpreval.functions.time;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 *
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UtilType
{
    /**
     * Returns an instance of Double or NaN if received Object is not a number.
     *
     * @param n the object to convert.
     * @return An instance of Double or NaN if received Object is not a number.
     */
    public static Double toDouble( Object n )
    {
        if( n instanceof Double )  return ((Double) n);   // I tested, and this is much faster than: ((Number) n).doubleValue()

        if( n instanceof Number )  return ((Number) n).doubleValue();

        if( n instanceof String )  try{ return Double.valueOf( (String) n ); }
                                   catch( NumberFormatException nfe ) { /* MingleException will be thrown */ }

        throw NaN( n );
    }

    /**
     * Returns an instance of Float or NaN if received Object is not a number.
     *
     * @param n the object to convert.
     * @return An instance of Float or NaN if received Object is not a number.
     */
    public static Float toFloat( Object n )
    {
        if( n instanceof Float  )  return ((Float) n);   // I tested, and this is much faster than: ((Number) n).floatValue()

        if( n instanceof Number )  return ((Number) n).floatValue();

        if( n instanceof String )  try{ return Float.valueOf( (String) n ); }
                                   catch( NumberFormatException nfe ) { /* MingleException will be thrown */ }

        throw NaN( n );
    }

    /**
     * Returns an instance of Long or NaN if received Object is not a number.
     *
     * @param n the object to convert.
     * @return An instance of Long or NaN if received Object is not a number.
     */
    public static Long toLong( Object n )
    {
        if( n instanceof Long   )  return ((Long) n);   // I tested, and this is much faster than: ((Number) n).longValue()

        if( n instanceof Number )  return (Long) ((Number) n).longValue();

        if( n instanceof String )  try{ return Long.valueOf( intPart( (String) n ) ); }
                                   catch( NumberFormatException nfe ) { /* MingleException will be thrown */ }

        throw NaN( n );
    }

    /**
     * Returns an instance of Integer or NaN if received Object is not a number.
     *
     * @param n the object to convert.
     * @return An instance of Integer or NaN if received Object is not a number.
     */
    public static Integer toInteger( Object n )
    {
        if( n instanceof Integer )  return ((Integer) n);   // I tested, and this is much faster than: ((Number) n).intValue()

        if( n instanceof Number  )  return ((Number) n).intValue();

        if( n instanceof String  )  try{ return Integer.valueOf( intPart( (String) n ) ); }
                                    catch( NumberFormatException nfe ) { /* MingleException will be thrown */ }

        throw NaN( n );
    }

    /**
     * Returns true if the string is "true" (ignoring e case), false if it is "false", and
     * null if the string is neither "true" nor "false".<br>
     * <br>
     * Note: do not need to check "yes", "no", etc. because USE command does it
     *
     * @param value String to test.
     * @return true, false o null.
     */
    public static Boolean toBoolean( Object value )
    {
        if( value instanceof Boolean )
            return (Boolean) value;

        if( value instanceof String )
        {
            String sValue = value.toString().trim();

            if( (sValue.length() == 4) || (sValue.length() == 5) )
            {
                sValue = sValue.toLowerCase();

                if( "true".equals( sValue ) )
                    return true;

                if( "false".equals( sValue ) )
                    return false;
            }
        }

        throw new MingleException( "Invalid boolean \""+ value +'\"' );
    }

    /**
     * Determines if a value is "truthy" according to Une semantics.
     * <p>
     * Falsy values:
     * <ul>
     *   <li>null</li>
     *   <li>Boolean FALSE</li>
     *   <li>Number 0 (zero)</li>
     *   <li>Empty String ("")</li>
     *   <li>Empty list</li>
     *   <li>Empty pair</li>
     * </ul>
     * All other values are truthy.
     *
     * @param value The value to test.
     * @return true if value is truthy, false if falsy.
     */
    public static boolean isTruthy( Object value )
    {
        if( value == null )
            return false;

        if( value instanceof Boolean )
            return (Boolean) value;

        if( value instanceof Number )
            return ((Number) value).floatValue() != 0.0f;

        if( value instanceof String )
            return ! ((String) value).isEmpty();

        if( value instanceof list )
            return ! ((list) value).isEmpty();

        if( value instanceof pair )
            return ! ((pair) value).isEmpty();

        return true;    // All other values (date, time, other objects) are truthy
    }

    public static <T> T[] convertArray( Object[] ao, Class<T> type )
    {
        if( ao == null )
            return null;

        T[] result = (T[]) Array.newInstance( type, ao.length );

        for( int n = 0; n < ao.length; n++ )
        {
            if( ao[n] != null && type.isAssignableFrom( ao[n].getClass() ) )
                result[n] = (T) ao[n];
        }

        return result;
    }

    //------------------------------------------------------------------------//
    // UNE CONVERSIONS BACK AND FORTH

    public static JsonValue toJson( String sValue )
    {
        if( UtilStr.isEmpty( sValue ) )
            return Json.NULL;

        if( Language.isBooleanValue( sValue ) )
            return Json.value( UtilType.toBoolean( sValue ) );

        if( Language.isNumber( sValue ) )
        {
            if( sValue.indexOf('.') > -1  )  return Json.value( Float.parseFloat( sValue ) );
                                             return Json.value( Long.parseLong(   sValue ) );
        }

        if( sValue.charAt( 0 ) == '[' || sValue.charAt( 0 ) == '{' )
            return Json.parse( sValue );

        return Json.value( sValue );    // Is a string
    }

    /**
     * From Une type to JSON value.
     *
     * @param oUneValue
     * @return
     */
    public static JsonValue toJson( Object oUneValue )
    {
        if( oUneValue instanceof String  )  return Json.value( (String ) oUneValue );
        if( oUneValue instanceof Boolean )  return Json.value( (Boolean) oUneValue );
        if( oUneValue instanceof Number  )  return Json.value( ((Number) oUneValue).floatValue() );

        if( UtilSys.getConfig().newXprEval().isExtendedDataType( oUneValue ) )
            return (JsonObject) ((ExtraType) oUneValue).serialize();

        throw new IllegalArgumentException( oUneValue +": not Une data" );
    }

    /**
     * From Une type to JSON value.
     *
     * @param lex
     * @return
     */
    public static Object toUne( Lexeme lex )
    {
        if( lex.isBoolean() )  return toBoolean( lex.text() );
        if( lex.isString()  )  return Language.toString( lex.text() );
        if( lex.isNumber()  )  return toFloat( lex.text() );

        if( lex.isExtendedDataType() )
        {
            try
            {
                return toUne( UtilJson.parse( lex.text() ) );
            }
            catch( MingleException ioe ) { /* Nothing to do */ }
        }

        return null;
    }

    /**
     * Returns the proper Java data value based on received String.<br>
     * Note: null is not accepted as parameter value.
     * <p>
     * Returns:
     * <ul>
     *     <li>If is Une keyword   -> returns its String</li>
     *     <li>If is boolean       -> returns its Boolean</li>
     *     <li>If is number        -> returns its Number</li>
     *     <li>If is string        -> returns its String</li>
     *     <li>If is extended type -> returns its instance</li>
     *     <li>If is JSON string   -> returns its value (if valid)</li>
     *     <li>If none of above    -> returns its String</li>
     * </ul>
     *
     * @param text
     * @return The proper data value based on passed string.
     */
    public static Object toUne( String text )
    {
        if( UtilStr.isEmpty( text ) )
            return text;

        String str = text.trim();
        char   ch0 = str.charAt( 0 );

        // Following is done to save CPU

        if( Language.isOperator(    ch0 ) ||
            Language.isParamSep(    ch0 ) ||
            Language.isSendOp(      ch0 ) ||
            Language.isParenthesis( ch0 ) ||
            Language.isCmdWord(     str ) )
        {
            return text;
        }

        Object obj = toUneBasics( str );

        if( ! str.equals( obj ) )
            return obj;

        // It is not a Une basic data type: Boolean, Number or String, lets check if it is a JSON.

        // If is is an expression is has to returned as the incoming string. Because although expressions like this: "left( "Francisco", 4 )",
        // can be resolved statically, others like this: ""Result = "+ floor( rand( 0,99 ) )" can not be resolved now.
        // And it is impossible to distinguish between them.

        // Low probability but it could be (they only 2 valid JSON chars at begin of str are '{' and '[').

        if( ch0 == '{' || ch0 == '[' )
        {
            JsonValue jv = null;

            try
            {
                jv = UtilJson.parse( str );
            }
            catch( MingleException ex )  { /* Nothing to do */ }

            if( (jv != null) && jv.isObject() )
                return toUne( jv );
        }

        return text;    // We have to assume it is either an expression or a String that did
    }                   // not come with its quotes (have to return the original, not the trimmed)

    /**
     * From JSON to Une type.<br>
     * <br>
     * IMPORTANT: this method only works with JSONs generated by ::toJson(...)
     *
     * @param jv
     * @return
     */
    public static Object toUne( JsonValue jv )
    {
        if( jv == null     )  return null;
        if( jv.isNull()    )  return null;
        if( jv.isString()  )  return jv.asString();
        if( jv.isBoolean() )  return jv.asBoolean();
        if( jv.isNumber()  )  return jv.asFloat();

        if( jv.isArray() )
        {
            JsonArray ja  = jv.asArray();
            list      lst = new list();

            for( JsonValue j : ja.values() )
                lst.add( toUne( j ) );

            return lst;
        }

        if( (! jv.isObject()) || jv.asObject().get( "class" ).isNull() )
            throw new MingleException( MingleException.SHOULD_NOT_HAPPEN );

        try
        {
            String sClass = jv.asObject().get( "class" ).asString();

            // It can come as follows: { class: "list", data: [1,2,3] } or as follows: { class: "com.peyrona.mingle.lang.list", data: [1,2,3] }
            // I could force to arrive as the second option, but then I would not be able to move these classes to a different package in the future.
            // This just makes source code more portable.

            if( sClass.length() == 4 )
            {
                switch( sClass )
                {
                    case "date": sClass = date.class.getName(); break;
                    case "time": sClass = time.class.getName(); break;
                    case "list": sClass = list.class.getName(); break;
                    case "pair": sClass = pair.class.getName(); break;
                }
            }

            Class<?>    clazz  = Class.forName( sClass );
            Constructor contru = clazz.getConstructor( Object[].class );    // There can be only one constructor and it must have the following signature: clazz( Object... args )
            Object      instan = contru.newInstance( (Object) null );

            return ((ExtraType) instan).deserialize( jv );
        }
        catch( ClassNotFoundException | IllegalArgumentException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException exc )
        {
            throw new MingleException( jv.toString(), exc );
        }
    }

    /**
     * Returns the proper Java data value based on passed String (which has to be any of Une basic types: Boolean, Number, String).<br>
     * Note: null is not accepted as parameter value.
     * <p>
     * Returns:
     * <ul>
     *     <li>If is boolean    -> returns its Boolean</li>
     *     <li>If is number     -> returns its Number</li>
     *     <li>If is string     -> returns its String</li>
     *     <li>If none of above -> returns its String</li>
     * </ul>
     *
     * @param text
     * @return The proper Java data value based on passed String (which has to be any of Une basic types: Boolean, Number, String).
     */
    public static Object toUneBasics( String text )
    {
        String str = text.trim();

        if( str.length() > 1 )
        {
            if( Language.isString( str ) )                        // Starts and ends with Language.QUOTE?
                return str.substring( 1, str.length() - 1 );

            try
            {
                return toBoolean( str );                          // Before isNumber because this is faster
            }
            catch( MingleException me )
            {
                // Nothiong to do
            }
        }

        if( Language.isNumber( str ) )                            // Numbers can have 1 char length "2"
            return toFloat( str );

        return text;     // We have to assume it is a String that did not come with its quotes (have to return the original, not the trimmed)
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private UtilType()
    {
        // Avoid instances of this class
    }

    private static String intPart( String sNum )
    {
        int x = sNum.indexOf( '.' );

        if( x > -1 )
            sNum = sNum.substring( 0, x );

        return sNum;
    }

    private static MingleException NaN( Object n )
    {
        return new MingleException( "Invalid number \""+ n +'\"' );
    }
}