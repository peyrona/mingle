
package com.peyrona.mingle.candi;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilReflect;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 This method is invoked via reflection (fromJson Config.java).
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
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
            downloadIfMissing( jo );

            Object[] aoURIs = UtilJson.toArray( jo.get( "uris" ) );

            map.put( name,
                     UtilReflect.newInstance( ICandi.ILanguage.class,
                                              jo.get( "class" ).asString(),
                                              UtilType.convertArray( aoURIs, String.class ) ) );
        }

        return map.get( name );
    }

    /**
     * Downloads language-specific JARs that are listed in the optional {@code "download"} array
     * of the language JSON object but are not yet present in the local {@code lib/} directory.
     * <p>
     * Each entry in the {@code "download"} array must be a JSON object with:
     * <ul>
     *   <li>{@code "local"}  – a {@code file://} URI (macros allowed) for the target local JAR.</li>
     *   <li>{@code "remote"} – the HTTP/HTTPS URL to fetch the JAR from when the local file is absent.</li>
     * </ul>
     * <p>
     * This method is called <em>on demand</em>, just before the language runtime is instantiated,
     * so optional large JARs (e.g. {@code graalpython}) are not fetched unless the language is
     * actually used in a Une programme.
     * <p>
     * Partial downloads caused by network failures are removed before the exception propagates.
     *
     * @param jo The JSON object describing the language configuration.
     * @throws Exception if a download fails or the local path cannot be resolved.
     */
    private static void downloadIfMissing( JsonObject jo ) throws Exception
    {
        JsonValue jvDownload = jo.get( "download" );

        if( jvDownload == null || !jvDownload.isArray() )
            return;

        for( JsonValue jv : jvDownload.asArray() )
        {
            JsonObject joEntry = jv.asObject();
            String     sLocal  = UtilIO.replaceFileMacros( joEntry.get( "local"  ).asString() );
            String     sRemote = joEntry.get( "remote" ).asString();
            File       fLocal  = new File( new URI( sLocal ).getPath() );

            if( fLocal.exists() )
                continue;

            UtilSys.getLogger().say( "Language JAR '"+ fLocal.getName()
                                   + "' not found locally. Downloading from:\n  "+ sRemote );

            Path dest = fLocal.toPath();

            try( InputStream in = URI.create( sRemote ).toURL().openStream() )
            {
                Files.copy( in, dest );
            }
            catch( IOException ex )
            {
                Files.deleteIfExists( dest );   // Remove any partial download
                throw new IOException( "Failed to download '"+ fLocal.getName()
                                     + "' from '"+ sRemote +"': "+ ex.getMessage(), ex );
            }

            UtilSys.getLogger().say( "Download complete: "+ fLocal.getName() );
        }
    }

    private static JsonObject[] getDefault()
    {
        JsonObject[] ret = new JsonObject[ 6 ];

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
                             "     \"class\": \"com.peyrona.mingle.candi.graalvm.GraalJsRT\"," +
                             "     \"uris\" : [\"file://{*home.lib*}candi.jar\"," +
                             "                 \"file://{*home.lib*}graal-sdk-23.0.11.jar\"," +
                             "                 \"file://{*home.lib*}truffle-api-23.0.11.jar\"," +
                             "                 \"file://{*home.lib*}js-23.0.11.jar\"," +
                             "                 \"file://{*home.lib*}regex-23.0.11.jar\"]" +
                             "}" ).asObject();

        ret[3] = Json.parse( "{" +
                             "     \"name\" : \"Python\"," +
                             "     \"class\": \"com.peyrona.mingle.candi.graalvm.GraalPyRT\"," +
                             "     \"uris\" : [\"file://{*home.lib*}candi.jar\"," +
                             "                 \"file://{*home.lib*}graal-sdk-23.0.11.jar\"," +
                             "                 \"file://{*home.lib*}truffle-api-23.0.11.jar\"," +
                             "                 \"file://{*home.lib*}graalpython-23.0.11.jar\"]," +
                             "     \"download\": [{\"local\" : \"file://{*home.lib*}graalpython-23.0.11.jar\"," +
                             "                     \"remote\": \"https://repo1.maven.org/maven2/org/graalvm/tools/graalpython/23.0.11/graalpython-23.0.11.jar\"}]" +
                             "}" ).asObject();

        ret[4] = Json.parse( "{" +
                             "     \"name\" : \"Ruby\"," +
                             "     \"class\": \"com.peyrona.mingle.candi.graalvm.GraalRubyRT\"," +
                             "     \"uris\" : [\"file://{*home.lib*}candi.jar\"," +
                             "                 \"file://{*home.lib*}graal-sdk-23.0.11.jar\"," +
                             "                 \"file://{*home.lib*}truffle-api-23.0.11.jar\"," +
                             "                 \"file://{*home.lib*}ruby-23.0.11.jar\"]," +
                             "     \"download\": [{\"local\" : \"file://{*home.lib*}ruby-23.0.11.jar\"," +
                             "                     \"remote\": \"https://repo1.maven.org/maven2/org/graalvm/ruby/ruby/23.0.11/ruby-23.0.11.jar\"}]" +
                             "}" ).asObject();

        ret[5] = Json.parse( "{" +
                             "     \"name\" : \"R\"," +
                             "     \"class\": \"com.peyrona.mingle.candi.graalvm.GraalRRT\"," +
                             "     \"uris\" : [\"file://{*home.lib*}candi.jar\"," +
                             "                 \"file://{*home.lib*}graal-sdk-23.0.11.jar\"," +
                             "                 \"file://{*home.lib*}truffle-api-23.0.11.jar\"," +
                             "                 \"file://{*home.lib*}r-23.0.11.jar\"]," +
                             "     \"download\": [{\"local\" : \"file://{*home.lib*}r-23.0.11.jar\"," +
                             "                     \"remote\": \"https://repo1.maven.org/maven2/org/graalvm/r/r/23.0.11/r-23.0.11.jar\"}]" +
                             "}" ).asObject();

        return ret;
    }
}