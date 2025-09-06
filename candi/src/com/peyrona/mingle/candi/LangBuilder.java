
package com.peyrona.mingle.candi;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilReflect;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 This method is invoked via reflection (fromJson Config.java).
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class LangBuilder implements ICandi.IBuilder
{
    private static final Map<String,ICandi.ILanguage> map = new HashMap<>();

    //------------------------------------------------------------------------//

    @Override
    public ICandi.ILanguage build( String sLangName )
    {
        sLangName = sLangName.toLowerCase();

        if( map.containsKey( sLangName ) )    // Here too (it is also in ::buildUsing) to save CPU
            return map.get( sLangName );

        try
        {
            JsonObject[] value = UtilSys.getConfig().get( "exen", "languages", new JsonObject[] {} );

            if( UtilColls.isEmpty( value ) )
                value = getDefault();

            for( JsonValue jv : value )
            {
                JsonObject jo = jv.asObject();

                if( sLangName.equalsIgnoreCase( jo.get( "name" ).asString() ) )
                    return buildUsing( jo );
            }
        }
        catch( MingleException me )
        {
            throw me;
        }
        catch( Exception exc )
        {
            throw new MingleException( exc );
        }

        return null;
    }

    @Override
    public ICandi.ILanguage buildUsing( String sJSON_LangDef )
    {
        try
        {
            JsonObject jo = Json.parse( sJSON_LangDef ).asObject();

            return buildUsing( jo );
        }
        catch( Exception exc )
        {
            throw new MingleException( exc );
        }
    }

    //------------------------------------------------------------------------//

    private ICandi.ILanguage buildUsing( JsonObject jo ) throws Exception
    {
        String name = jo.get( "name" ).asString().toLowerCase();    // null to provoke a NullPointerException if JSON is malformed (does not have "name" property)

        if( ! map.containsKey( name ) )
        {
            Object[] aoURIs = UtilJson.toArray( jo.get( "uris" ) );

            map.put( name,
                     UtilReflect.newInstance( ICandi.ILanguage.class,
                                              jo.get( "class" ).asString(),
                                              UtilType.convertArray( aoURIs, String.class ) ) );
        }

        return map.get( name );
    }

    private static JsonObject[] getDefault()
    {
        JsonObject[] ret = new JsonObject[ 4 ];

        ret[0] = Json.parse( "{" +
                             "     \"name\" : \"Une\"," +
                             "     \"class\": \"com.peyrona.mingle.candi.unescript.UneScriptRT\"," +
                             "     \"uris\" : [\"file://{*home.lib*}candi.jar\"]" +
                             "}" ).asObject();

        ret[1] = Json.parse( "{" +
                             "     \"name\" : \"Java\"," +
                             "     \"class\": \"com.peyrona.mingle.candi.javac.JavaRT\"," +
                             "     \"uris\" : [\"file://{*home.lib*}candi.jar\"]" +
                             "}" ).asObject();

        ret[2] = Json.parse( "{" +
                             "     \"name\" : \"JavaScript\"," +
                             "     \"class\": \"com.peyrona.mingle.candi.javascript.NashornRT\"," +
                             "     \"uris\" : [\"file://{*home.lib*}candi.jar\"]" +
                             "}" ).asObject();

        ret[3] = Json.parse( "{" +
                             "     \"name\" : \"Python\"," +
                             "     \"class\": \"com.peyrona.mingle.candi.python.JythonRT\"," +
                             "     \"uris\" : [\"file://{*home.lib*}candi.jar\"," +
                             "                 \"file://{*home.lib*}jython-standalone-2.7.2.jar\"]" +
                             "}" ).asObject();

        return ret;
    }
}