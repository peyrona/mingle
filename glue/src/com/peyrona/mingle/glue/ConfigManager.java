package com.peyrona.mingle.glue;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized configuration manager for Glue application.
 *
 * This class consolidates all configuration data that was previously stored
 * in separate files into a single JSON configuration file.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ConfigManager
{
    private static final String CONFIG_FILE_NAME = "glue_config.json";
    private static final File   CONFIG_FILE = new File( UtilSys.getEtcDir(), CONFIG_FILE_NAME );
    private static final Object FILE_LOCK   = new Object();
    private static       JsonObject config  = null;

    //------------------------------------------------------------------------//
    // Public interface
    //------------------------------------------------------------------------//

    /**
     * Gets the window bounds for a specific window title.
     *
     * @param title The window title
     * @return JsonObject containing bounds, or null if not found
     */
    public static JsonObject getWindowBounds( String title )
    {
        loadConfig();

        JsonArray bounds = config.get( "bounds" ).asArray();

        for( JsonValue jv : bounds )
        {
            JsonObject obj = jv.asObject();

            if( title.equals( obj.getString( "title", null ) ) )
                return obj.get( "geometry" ).asObject();
        }

        return null;
    }

    /**
     * Sets the window bounds for a specific window title.
     *
     * @param title The window title
     * @param bounds JsonObject containing x, y, width, height
     */
    public static void setWindowBounds( String title, JsonObject bounds )
    {
        loadConfig();

        JsonArray boundsArray = config.get( "bounds" ).asArray();

        // Remove existing entry for this title
        for( int i = boundsArray.size() - 1; i >= 0; i-- )
        {
            if( title.equals( boundsArray.get( i ).asObject().getString( "title", null ) ) )
                boundsArray.remove( i );
        }

        // Add new entry
        boundsArray.add( Json.object().add( "title", title ).add( "geometry", bounds ) );

        saveConfig();
    }

    /**
     * Gets the list of editor files with their caret positions.
     *
     * @return JsonArray of editor file objects
     */
    public static JsonArray getEditorFiles()
    {
        loadConfig();
        return config.get( "editor" ).asArray();
    }

    /**
     * Sets the list of editor files with their caret positions.
     *
     * @param editorFiles JsonArray of editor file objects
     */
    public static void setEditorFiles( JsonArray editorFiles )
    {
        loadConfig();
        config.set( "editor", editorFiles );
        saveConfig();
    }

    /**
     * Gets the list of hidden tips.
     *
     * @return List of tip strings
     */
    public static List<String> getHiddenTips()
    {
        loadConfig();

        JsonArray tips = config.get( "tips" ).asArray();
        List<String> result = new ArrayList<>();

        for( JsonValue jv : tips )
            result.add( jv.asString() );

        return result;
    }

    /**
     * Adds a tip to the hidden tips list.
     *
     * @param tip The tip to hide
     */
    public static void addHiddenTip( String tip )
    {
        loadConfig();

        JsonArray tips = config.get( "tips" ).asArray();
        String cleanedTip = cleanTip( tip );

        // Remove existing entry if present
        for( int i = tips.size() - 1; i >= 0; i-- )
        {
            if( cleanedTip.equals( tips.get( i ).asString() ) )
                tips.remove( i );
        }

        // Add new entry
        tips.add( cleanedTip );

        saveConfig();
    }

    /**
     * Removes all hidden tips.
     */
    public static void clearHiddenTips()
    {
        loadConfig();
        config.set( "tips", new JsonArray() );
        saveConfig();
    }

    /**
     * Gets the last used directory in the editor.
     *
     * @return The last used directory path, or default if not found
     */
    public static String getLastUsedDir()
    {
        loadConfig();
        String dir = config.getString( "lastdir", null );

        if( UtilStr.isEmpty( dir ) )
            return new File( UtilSys.fHomeDir, "examples" ).getAbsolutePath();

        File fDir = new File( dir );
        return fDir.exists() ? fDir.getAbsolutePath() : new File( UtilSys.fHomeDir, "examples" ).getAbsolutePath();
    }

    /**
     * Sets the last used directory in the editor.
     *
     * @param dir The directory path
     */
    public static void setLastUsedDir( String dir )
    {
        loadConfig();
        config.set( "lastdir", dir );
        saveConfig();
    }

    /**
     * Gets the last update check date.
     *
     * @return The last update check date, or null if not found
     */
    public static String getLastUpdateCheck()
    {
        loadConfig();
        return config.getString( "lastupdatecheck", null );
    }

    /**
     * Sets the last update check date to today.
     */
    public static void setLastUpdateCheckToToday()
    {
        loadConfig();
        String todayDate = LocalDate.now().format( DateTimeFormatter.ISO_LOCAL_DATE );
        config.set( "lastupdatecheck", todayDate );
        saveConfig();
    }

    /**
     * Gets the list of connection definitions.
     *
     * @return JsonArray of connection definition objects
     */
    public static JsonArray getConnections()
    {
        loadConfig();
        return config.get( "connections" ).asArray();
    }

    /**
     * Adds or updates a connection definition.
     *
     * @param name The connection label
     * @param configObj The connection configuration object
     */
    public static void setConnection( String name, JsonObject configObj )
    {
        loadConfig();

        JsonArray connections = config.get( "connections" ).asArray();

        // Remove existing entry for this label
        for( int i = connections.size() - 1; i >= 0; i-- )
        {
            if( name.equals( connections.get( i ).asObject().getString( "label", null ) ) )
                connections.remove( i );
        }

        // Add new entry
        connections.add( Json.object().add( "label", name ).add( "config", configObj ) );

        saveConfig();
    }

    /**
     * Removes a connection definition by label.
     *
     * @param name The connection label to remove
     */
    public static void removeConnection( String name )
    {
        loadConfig();

        JsonArray connections = config.get( "connections" ).asArray();

        for( int i = connections.size() - 1; i >= 0; i-- )
        {
            if( name.equals( connections.get( i ).asObject().getString( "label", null ) ) )
                connections.remove( i );
        }

        saveConfig();
    }

    /**
     * Resets all configuration data.
     */
    public static void reset()
    {
        synchronized( FILE_LOCK )
        {
            if( CONFIG_FILE.exists() )
                CONFIG_FILE.delete();

            config = null;
        }
    }

    /**
     * Resets only window bounds configuration.
     */
    public static void resetBounds()
    {
        loadConfig();
        config.set( "bounds", new JsonArray() );
        saveConfig();
    }

    /**
     * Resets only hidden tips configuration.
     */
    public static void resetTips()
    {
        loadConfig();
        config.set( "tips", new JsonArray() );
        saveConfig();
    }

    //------------------------------------------------------------------------//
    // Private methods
    //------------------------------------------------------------------------//

    private static void loadConfig()
    {
        synchronized( FILE_LOCK )
        {
            if( config != null )
                return;

            try
            {
                if( CONFIG_FILE.exists() )
                {
                    String content = UtilIO.getAsText( CONFIG_FILE );
                    config = Json.parse( content ).asObject();
                }
                else
                {
                    // Initialize with default structure
                    config = createDefaultConfig();
                }
            }
            catch( IOException ex )
            {
                JTools.error( ex );
                config = createDefaultConfig();
            }
            catch( Exception ex )
            {
                JTools.error( ex );
                config = createDefaultConfig();
            }

            // Ensure all required sections exist
            ensureConfigStructure();
        }
    }

    private static void saveConfig()
    {
        synchronized( FILE_LOCK )
        {
            try
            {
                UtilIO.newFileWriter()
                      .setFile( CONFIG_FILE )
                      .replace( config.toString() );
            }
            catch( IOException ex )
            {
                JTools.error( ex );
            }
        }
    }

    private static JsonObject createDefaultConfig()
    {
        return Json.object()
                   .add( "bounds", new JsonArray() )
                   .add( "editor", new JsonArray() )
                   .add( "tips", new JsonArray() )
                   .add( "lastdir", "" )
                   .add( "lastupdatecheck", "" )
                   .add( "connections", new JsonArray() );
    }

    private static void ensureConfigStructure()
    {
        boolean needsSave = false;

        if( config.get( "bounds" ) == null )
        {
            config.set( "bounds", new JsonArray() );
            needsSave = true;
        }

        if( config.get( "editor" ) == null )
        {
            config.set( "editor", new JsonArray() );
            needsSave = true;
        }

        if( config.get( "tips" ) == null )
        {
            config.set( "tips", new JsonArray() );
            needsSave = true;
        }

        if( config.get( "lastdir" ) == null )
        {
            config.set( "lastdir", "" );
            needsSave = true;
        }

        if( config.get( "lastupdatecheck" ) == null )
        {
            config.set( "lastupdatecheck", "" );
            needsSave = true;
        }

        if( config.get( "connections" ) == null )
        {
            config.set( "connections", new JsonArray() );
            needsSave = true;
        }

        if( needsSave )
        {
            saveConfig();
        }
    }

    private static String cleanTip( String tip )
    {
        final char[] acIn  = tip.toCharArray();
        final char[] acOut = new char[ acIn.length ];

        for( int n = 0; n < acIn.length; n++ )
            acOut[n] = (acIn[n] <= 32) ? '_' : acIn[n];

        return String.valueOf( acOut );
    }
}