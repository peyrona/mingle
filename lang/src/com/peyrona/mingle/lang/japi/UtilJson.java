
package com.peyrona.mingle.lang.japi;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.ParseException;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.xpreval.functions.list;
import com.peyrona.mingle.lang.xpreval.functions.pair;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class UtilJson
{
    private final JsonObject jo;

    //------------------------------------------------------------------------//
    // STATICS

    /**
     * Parses JSON string into JsonValue.
     *
     * @param sJSON JSON string to parse
     * @return Parsed JsonValue, or null if sJSON is empty
     * @throws MingleException if JSON is malformed
     */
    public static JsonValue parse( String sJSON )
    {
        if( UtilStr.isEmpty( sJSON ) )
            return null;

        try
        {
            return Json.parse( sJSON );
        }
        catch( ParseException pe )
        {
            throw new MingleException( pe );
        }
    }

    //------------------------------------------------------------------------//

    /**
     * Converts JSON string to Une data type.
     * <p>
     * Converts JSON values to equivalent Une types (Pair, List, primitives).
     *
     * @param s JSON string to convert
     * @return Une-compatible object (Pair for objects, List for arrays, or primitives)
     */
    public static Object toUneType( String s )
    {
        return convertJsonValue( Json.parse(s) );
    }

    /**
     * Converts JsonValue array to Object array.
     *
     * @param jv JsonValue to convert (should be array type)
     * @return Object array with converted elements
     */
    public static Object[] toArray(JsonValue jv)
    {
        Object[]  array = null;
        JsonArray ja    = jv.isArray() ? (JsonArray) jv : jv.asArray();

        if( ! ja.isNull() )
        {
            array = new Object[ ja.size() ];

            for( int n = 0; n < array.length; n++ )
            {
                JsonValue value = ja.get( n );

                     if( value.isNull()    )  array[n] = null;
                else if( value.isBoolean() )  array[n] = value.asBoolean();
                else if( value.isNumber()  )  array[n] = value.asDouble();
                else if( value.isString()  )  array[n] = value.asString();
                else if( value.isArray()   )  array[n] = toArray( value );
                else if( value.isObject()  )  array[n] = value;            // Stores the value (a JsonOPbject instance) itself
            }
        }

        return array;
    }

    /**
     * Returns a JSON array where each item is mapped to received array of Strings.
     *
     * @param as
     * @return A JSON array where each item is mapped to received array of Strings.
     */
    public static JsonArray toJSON( String[] as )
    {
        JsonArray ja = new JsonArray();

        if( UtilColls.isEmpty( as ) )
            return ja;

        for( String s : as )
            ja.add( s );

        return ja;
    }

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public UtilJson( String sJSON )
    {
        this( parse( sJSON ) );
    }

    public UtilJson( JsonValue jv )
    {
        if( ! jv.isObject() )
            throw new MingleException( "Not a JSON Object: "+ jv.toString() );

        this.jo = jv.asObject();
    }

    //------------------------------------------------------------------------//

    @Override
    public String toString()
    {
        return (jo == null) ? "" : jo.toString();
    }

    //------------------------------------------------------------------------//
    // PUBLIC

    public JsonObject getObject( String key )
    {
        return getObject( key, null, true );
    }

    public JsonObject getObject( String key, JsonObject defVal )
    {
        return getObject( key, defVal, false );
    }

    public JsonArray getArray( String key )
    {
        return getArray( key, null, true );
    }

    public JsonArray getArray( String key, JsonArray defVal )
    {
        return getArray( key, defVal, false );
    }

    public Boolean getBoolean( String key )
    {
        return getBoolean( key, null, true );
    }

    public Boolean getBoolean( String key, boolean defVal )
    {
        return getBoolean( key, defVal, false );
    }

    public Integer getInt( String key )
    {
        return getInt( key, null, true );
    }

    public Integer getInt( String key, int defVal )
    {
        return getInt( key, defVal, false );
    }

    public Long getLong( String key )
    {
        return getLong( key, null, true );
    }

    public Long getLong( String key, long defVal )
    {
        return getLong( key, defVal, false );
    }

    public Float getFloat( String key )
    {
        return getFloat( key, null, true );
    }

    public Float getFloat( String key, float defVal )
    {
        return getFloat( key, defVal, false );
    }

    public String getString( String key )
    {
        return getString( key, null, true, false );
    }

    public String getString( String key, String defVal )
    {
        return getString( key, defVal, false, false );
    }

    public String getAsString( String key )
    {
        return getString( key, null, true, true );
    }

    public String getAsString( String key, String defVal )
    {
        return getString( key, defVal, false, true );
    }

    /**
     * Add as new if key did not existed or replaces existing key by the new value.
     *
     * @param key
     * @param value
     * @return Itself
     */
    public UtilJson add( String key, JsonValue value )
    {
        if( jo != null )
            remove( key ).jo.add( key, value );

        return this;
    }

    /**
     * Removes the key and it contents (if existed).
     *
     * @param key
     * @return
     */
    public UtilJson remove( String key )
    {
        if( jo != null )
            jo.remove( key );

        return this;
    }

    //------------------------------------------------------------------------//
    // PRIVATE

    private JsonObject getObject( String key, JsonObject defVal, boolean bMustExist )
    {
        if( jo == null )
            return null;

        JsonValue val = jo.get( key );          // returns null if the value of the key is null, but returns Json.NULL if the object does not contain a member with that name

        if( val == null )                       // key does not exis: not frecuent
        {
            if( bMustExist )
                throw new MingleException( key +": not found" );
            else
                return defVal;
        }

        if( val.isNull() )                      // key exists and its value is null
            return defVal;

        if( val.isObject() )
            return val.asObject();

        throw new MingleException( "Not an Object: "+ val.toString() );
    }

    private JsonArray getArray( String key, JsonArray defVal, boolean bMustExist )
    {
        if( jo == null )
            return null;

        JsonValue val = jo.get( key );          // returns null if the value of the key is null, but returns Json.NULL if the object does not contain a member with that name

        if( val == null )                       // key does not exis: not frecuent
        {
            if( bMustExist )
                throw new MingleException( key +": not found" );
            else
                return defVal;
        }

        if( val.isNull() )                      // key exists and its value is null
            return defVal;

        if( val.isArray() )
            return val.asArray();

        throw new MingleException( "Not an Array: "+ val.toString() );
    }

    private Boolean getBoolean( String key, Boolean defVal, boolean bMustExist )
    {
        if( jo == null )
            return null;

        JsonValue val = jo.get( key );          // returns null if the value of the key is null, but returns Json.NULL if the object does not contain a member with that name

        if( val == null )                       // key does not exis: not frecuent
        {
            if( bMustExist )
                throw new MingleException( key +": not found" );
            else
                return defVal;
        }

        if( val.isNull() )                      // key exists and its value is null
            return defVal;

        if( val.isBoolean() )
            return val.asBoolean();

        throw new MingleException( "Not a Boolean: "+ val.toString() );
    }

    private Integer getInt( String key, Integer defVal, boolean bMustExist )
    {
        if( jo == null )
            return null;

        JsonValue val = jo.get( key );          // returns null if the value of the key is null, but returns Json.NULL if the object does not contain a member with that name

        if( val == null )                       // key does not exis: not frecuent
        {
            if( bMustExist )
                throw new MingleException( key +": not found" );
            else
                return defVal;
        }

        if( val.isNull() )                      // key exists and its value is null
            return defVal;

        if( val.isNumber() )
            return val.asInt();

        throw new MingleException( "Not an Integer: "+ val.toString() );
    }

    private Long getLong( String key, Long defVal, boolean bMustExist )
    {
        if( jo == null )
            return null;

        JsonValue val = jo.get( key );          // returns null if the value of the key is null, but returns Json.NULL if the object does not contain a member with that name

        if( val == null )                       // key does not exis: not frecuent
        {
            if( bMustExist )
                throw new MingleException( key +": not found" );
            else
                return defVal;
        }

        if( val.isNull() )                      // key exists and its value is null
            return defVal;

        if( val.isNumber() )
            return val.asLong();

        throw new MingleException( "Not a Long: "+ val.toString() );
    }

    private Float getFloat( String key, Float defVal, boolean bMustExist )
    {
        if( jo == null )
            return null;

        JsonValue val = jo.get( key );          // returns null if the value of the key is null, but returns Json.NULL if the object does not contain a member with that name

        if( val == null )                       // key does not exis: not frecuent
        {
            if( bMustExist )
                throw new MingleException( key +": not found" );
            else
                return defVal;
        }

        if( val.isNull() )                      // key exists and its value is null
            return defVal;

        if( val.isNumber() )
            return val.asFloat();

        throw new MingleException( "Not an Float: "+ val.toString() );
    }

    private String getString( String key, String defVal, boolean bMustExist, boolean bConvert2Str )
    {
        if( jo == null )
            return null;

        JsonValue val = jo.get( key );          // returns null if the value of the key is null, but returns Json.NULL if the object does not contain a member with that name

        if( val == null )                       // key does not exis: not frecuent
        {
            if( bMustExist )
                throw new MingleException( key +": not found" );
            else
                return defVal;
        }

        if( val.isNull() )                      // key exists and its value is null
            return defVal;

        if( val.isString() )
            return val.asString();

        if( bConvert2Str )
            return val.toString();

        throw new MingleException( "Not an String: "+ val.toString() );
    }

    //------------------------------------------------------------------------//
    // AUN FUNCS FOR ::toUneType
    //------------------------------------------------------------------------//

    private static Object convertJsonValue( JsonValue jv )
    {
             if( jv.isObject()  )  return convertJsonObject( jv.asObject() );
        else if( jv.isArray()   )  return convertJsonArray( jv.asArray() );
        else if( jv.isString()  )  return jv.asString();
        else if( jv.isNumber()  )  return jv.asFloat();
        else if( jv.isBoolean() )  return jv.asBoolean();
        else                       return "";              // Handle JSON null (in Une null does not exists)
    }

    private static pair convertJsonObject( JsonObject jo )
    {
        pair p = new pair();

        for( JsonObject.Member member : jo )
            p.put( member.getName(), convertJsonValue( member.getValue() ) );

        return p;
    }

    private static list convertJsonArray( JsonArray ja )
    {
        list l = new list();

        for( JsonValue value : ja )
            l.add( convertJsonValue( value ) );

        return l;
    }
}