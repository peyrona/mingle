/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.peyrona.mingle.lang.japi;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.lexer.Lexeme;
import com.peyrona.mingle.lang.xpreval.functions.ExtraType;
import com.peyrona.mingle.lang.xpreval.functions.list;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.lang.reflect.Array;

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

        if( oUneValue instanceof Number  )
        {
            if( oUneValue instanceof Integer ) return Json.value( ((Number) oUneValue).intValue()  );
            if( oUneValue instanceof Long    ) return Json.value( ((Number) oUneValue).longValue() );

            return Json.value( ((Number) oUneValue).floatValue() );
        }

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
        if( lex.isExtendedDataType() )
        {
            try
            {
                return UtilJson.toUneType( UtilJson.parse( lex.text() ) );
            }
            catch( MingleException ioe ) { /* Nothing to do */ }
        }
        else
        {
            return toUneBasics( lex.text() );
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

        if( str.isEmpty() )    // e.g.: text contains only escape chars like '\n', '\t', '\r'
            return text;

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

            if( jv != null )
                return UtilJson.toUneType( jv );
        }

        return text;    // We have to assume it is either an expression or a String that did
    }                   // not come with its quotes (have to return the original, not the trimmed)

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
                return Language.unescapeString( str.substring( 1, str.length() - 1 ) );

            if( Language.isBooleanValue( str ) )
                return toBoolean( str );                          // Before isNumber because this is faster
        }

        if( Language.isNumber( str ) )                            // Numbers can have 1 char length
        {
            try
            {
                if( str.indexOf( '.' ) > -1 )
                    return Float.valueOf( str );

                return Integer.valueOf( str );
            }
            catch( NumberFormatException nfe )
            {
                try  { return Long.valueOf( str ); }
                catch( NumberFormatException e2 ) { throw NaN( str ); }
            }
        }

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