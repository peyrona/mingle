
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.Config;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilCLI;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

/**
 * Class to run an stand-alone instance of Stick.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Main
{
    public static void main( String[] as ) throws IOException
    {
        UtilCLI cli = new UtilCLI( as );

        if( cli.hasOption( "help" ) || cli.hasOption( "h" ))
        {
            System.out.println( UtilIO.getAsText( Main.class.getResourceAsStream( "help.txt" ),   // Done using a .txt file to save RAM
                                                  StandardCharsets.UTF_8 ) );
        }
        else
        {
            Stick stick = null;

            try
            {
                String   sCfgURI = cli.getValue( "config", null );
                IConfig  config  = new Config().load( sCfgURI ).setCliArgs( as );
                String[] asModel = getModels( cli, config );

                compareAndUpdateModels( config, asModel );                       // One-time startup check (refreshes the local copy of HTTP/S models)

                String[] asLoad = localizeModelsForLoading( config, asModel );   // When polling is on, load (and thus watch) the local copy of HTTP/S models

                Pair<String,String> pair  = loadFirstValidModel( asLoad );
                String              sName = pair == null ? null : pair.getKey();
                String              sJSON = pair == null ? null : pair.getValue();

                stick = new Stick( sJSON, config ).start( sName );

                scheduleModelUpdateChecks( config, asModel );                    // Periodically re-check HTTP/S models; a download overwrites the local file, which the watcher hot-reloads
            }
            catch( Exception exc )
            {
                String msg = exc.getMessage() +"\nStick can not continue.\n"+ UtilStr.toString( exc );

                if( stick != null )  stick.log( ILogger.Level.SEVERE, msg  );
                else                 System.err.println( msg );
            }
        }
    }

    //-----------------------------------------------------------------------//

    // URI extension ".model" is not mandatory
    // 'model' can be null because Stick can run without a model: it can be built later

    private static String[] getModels( UtilCLI cli, IConfig config ) throws IOException, URISyntaxException
    {
        String[]     asURI    = cli.getNoOptions();
        List<String> lstModel = new ArrayList<>();    // Config file can be empty, or can have one file or an array of files

        // First look among CLI args

        if( UtilColls.isNotEmpty( asURI ) )           // CLI args have precedence over config file
        {
            if( asURI.length > 1 )
            {
                System.err.println( "One and only one Model file needed.\nReceived: '"+ UtilColls.toString( asURI ) +'\'' );
                System.exit( 1 );
            }

            if( UtilStr.isNotEmpty( asURI[0] ) )     // Could be ""
                lstModel.add( asURI[0].trim() );
        }

        // Now look in config file (it can be one file or an array of files)

        try
        {
            String[] as = config.get( "exen", "model", new String[0] );   // First we try considering it is an array (an exception is thrown if is not an array)

            Arrays.asList( as ).forEach( sModel ->
                                         {
                                             if( ! UtilStr.isMeaningless( sModel ) )
                                                 lstModel.add( sModel );
                                         } );

            // UtilIO.expandPath(...) is not needed because it is done later by UtilIO.getAsText(...)
        }
        catch( Exception ex )    // If this is not the case, lets assume it is one single item
        {
            String sModel = config.get( "exen", "model", "" );

            if( ! UtilStr.isMeaningless( sModel ) )
                lstModel.add( sModel );
        }

        for( ListIterator<String> itera = lstModel.listIterator(); itera.hasNext(); )
        {
            String sModel = itera.next();

            if( UtilStr.isLastChar( sModel, '?' ) ||
                UtilStr.isLastChar( sModel, '*' ) )
                continue;

            if( UtilIO.hasExtension( sModel, "une" ) )
                sModel = UtilStr.removeLast( sModel, 3 );

            if( ! UtilIO.hasExtension( sModel, "model" ) )
                itera.set( sModel + (UtilStr.endsWith( sModel, "." ) ? "" : ".") + "model" );
        }

        return lstModel.toArray( String[]::new );
    }

    private static Pair<String,String> loadFirstValidModel( String[] asModel ) throws IOException
    {
        // Can not log because logger is not initialized

        if( UtilColls.isEmpty( asModel ) )
            return null;                   // 'model' can be null because Stick can run without a model: it can be built later

        for( String sName : asModel )
        {
            try
            {
                return new Pair( sName, UtilIO.getAsText( sName ) );
            }
            catch( IOException ioe )
            {
                // Nothing to do: try with the next one
            }
        }

        String msg = "None of the proposed models can be loaded:\n";

        if( msg.toLowerCase().endsWith( "une.model" ) )
            msg += "(Suspiciously the passed model file ends with '.une')\n";

        msg += Arrays.toString( asModel );

        throw new IOException( msg );
    }

    //-----------------------------------------------------------------------//
    // HTTP vs LOCAL MODEL COMPARISON AND UPDATE
    //-----------------------------------------------------------------------//

    /**
     * Compares remote HTTP/S models with local versions and updates the local
     * files if the remote ones are newer.
     * <p>
     * This process is triggered by the {@code exen.update_models} configuration
     * flag. It iterates through the provided model URIs, and for each HTTP/S
     * URI, it checks if the remote model is newer and, if so, downloads it
     * to replace the local version.
     *
     * @param config The application configuration.
     * @param asModelNames An array of model URIs to check for updates.
     */
    private static void compareAndUpdateModels( IConfig config, String[] asModelNames )
    {
        if( ! UtilSys.isFsWritable )    // Faster first
            return;

        if( UtilColls.isEmpty( asModelNames ) )
            return;

        if( ! config.get( "exen", "update_models", false ) )
            return;

        for( String sModelName : asModelNames )
        {
            try
            {
                if( isHttpUri( sModelName ) )
                {
                    String sLocalPath     = getLocalPathFromHttpUri( sModelName );
                    String sRemoteContent = UtilIO.getAsText( sModelName );        // Fetched once and reused for both the age check and the write

                    if( (sRemoteContent != null) && isRemoteNewer( sRemoteContent, sLocalPath ) )
                    {
                        UtilIO.newFileWriter()
                              .setFile( sLocalPath )
                              .replace( sRemoteContent );

                        System.err.println( "Updated local model: "+ sLocalPath +" (from "+ sModelName +")" );
                    }
                }
            }
            catch( Exception exc )
            {
                exc.printStackTrace( System.err );
            }
        }
    }

    //-----------------------------------------------------------------------//
    // PERIODIC HTTP/S MODEL UPDATE CHECK
    //
    // Local model files are hot-reloaded by Stick's file watcher. These helpers add the remote
    // (HTTP/S) counterpart: periodically download a newer version over the local model file, which
    // the watcher then hot-reloads. Enabled by "exen.update_models_interval" (minutes; 0 = startup only).
    //-----------------------------------------------------------------------//

    /**
     * Whether periodic HTTP/S model-update polling is enabled (and possible).
     *
     * @param config The application configuration.
     * @return {@code true} when the file system is writable, {@code exen.update_models} is on and
     *         {@code exen.update_models_interval} is greater than zero.
     */
    private static boolean isModelPollingEnabled( IConfig config )
    {
        return UtilSys.isFsWritable
            && config.get( "exen", "update_models", false )
            && updateIntervalMinutes( config ) > 0;
    }

    /**
     * Returns the configured interval, in minutes, between HTTP/S model-update checks.
     *
     * @param config The application configuration.
     * @return The interval in minutes (0 means "only at startup").
     */
    private static int updateIntervalMinutes( IConfig config )
    {
        return config.get( "exen", "update_models_interval", 0 );
    }

    /**
     * When polling is enabled, replaces every HTTP/S model URI by its already-downloaded local copy
     * path (when that copy exists), so Stick loads -- and therefore watches -- the local file. The
     * periodic check later overwrites that file and the watcher hot-reloads it.
     * <p>
     * When polling is disabled the array is returned unchanged, preserving the default behaviour of
     * loading HTTP/S models live from their URL.
     *
     * @param config  The application configuration.
     * @param asModel The resolved model URIs.
     * @return The model URIs to actually load (a copy; the input array is not modified).
     */
    private static String[] localizeModelsForLoading( IConfig config, String[] asModel )
    {
        if( ! isModelPollingEnabled( config ) )
            return asModel;

        String[] asLoad = asModel.clone();

        for( int i = 0; i < asLoad.length; i++ )
        {
            try
            {
                if( isHttpUri( asLoad[i] ) )
                {
                    String sLocal = getLocalPathFromHttpUri( asLoad[i] );

                    if( new File( sLocal ).exists() )    // Downloaded by compareAndUpdateModels (now or in a previous run)
                        asLoad[i] = sLocal;
                }
            }
            catch( Exception exc )
            {
                // Keep the original URI for this entry
            }
        }

        return asLoad;
    }

    /**
     * When polling is enabled and at least one model is an HTTP/S URI, schedules a recurring task that
     * re-runs {@link #compareAndUpdateModels(IConfig, String[])}. Each run downloads a newer version
     * over the local model file; Stick's file watcher then hot-reloads it.
     *
     * @param config  The application configuration.
     * @param asModel The original (HTTP/S) model URIs to check -- NOT the localized ones.
     */
    private static void scheduleModelUpdateChecks( IConfig config, String[] asModel )
    {
        if( ! isModelPollingEnabled( config ) )
            return;

        boolean bHasHttp = false;

        for( String sModel : asModel )
        {
            try                                   { bHasHttp |= isHttpUri( sModel ); }
            catch( Exception exc )                { /* not an HTTP/S URI */ }
        }

        if( ! bHasHttp )    // Nothing remote to poll: avoid a useless recurring task
            return;

        long millis = updateIntervalMinutes( config ) * 60_000L;

        UtilSys.executor( false )   // dedicated, not the shared pool: compareAndUpdateModels() does an unbounded openStream(); called once at startup, so no restart-leak risk
               .name( "ModelUpdateChecker" )
               .delay( millis )
               .rate(  millis )
               .execute( () -> compareAndUpdateModels( config, asModel ) );
    }

    /**
     * Checks if a given URI string uses the HTTP or HTTPS protocol.
     *
     * @param uri The URI string to check.
     * @return {@code true} if the URI is for HTTP or HTTPS, {@code false} otherwise.
     */
    private static boolean isHttpUri( String uri )
    {
        UtilComm.Protocol protocol = UtilComm.getFileProtocol( uri );
        return protocol == UtilComm.Protocol.http || protocol == UtilComm.Protocol.https;
    }

    /**
     * Constructs a local file path for a model given its HTTP URI.
     * <p>
     * The local file is placed in the application's current working directory
     * ({@code user.dir}). The filename is extracted from the URI's path. If the
     * extracted filename does not end with ".model", the extension is added.
     *
     * @param httpUri The full HTTP or HTTPS URI of the model.
     * @return The absolute local file path.
     */
    private static String getLocalPathFromHttpUri( String httpUri ) throws URISyntaxException
    {
        URI    uri       = URI.create( httpUri );
        Path   path      = Paths.get( uri.getPath() );
        String sFileName = (path.getFileName() == null) ? null : path.getFileName().toString();

        if( sFileName.isEmpty() )
            throw new IllegalArgumentException( "Cannot determine filename from HTTP URI: " + httpUri );

        // If the filename doesn't end with .model, add the extension.
        if( ! UtilStr.endsWith( sFileName, ".model" ) )
            sFileName = sFileName + ".model";

        // Store in Java application directory
        return UtilSys.fHomeDir + java.io.File.separator + sFileName;
    }

    /**
     * Extracts the "generated" timestamp string from a model's JSON content.
     *
     * @param jsonContent The JSON content of the model file.
     * @return The timestamp string (expected in ISO-8601 format), or {@code null}
     *         if the "generated" field is not found, the content is invalid,
     *         or an error occurs.
     */
    private static String extractModelTimestamp( String jsonContent )
    {
        if( UtilStr.isEmpty( jsonContent ) )
            return null;

        UtilJson uj = new UtilJson( jsonContent );

        return uj.getString( "generated", null );
    }

    /**
     * Compares the "generated" timestamp of an already-downloaded remote model
     * with a local model file to determine if the remote one is newer.
     * <p>
     * The remote content is received already fetched (by the caller), so the
     * remote model is downloaded only once per check.
     *
     * @param sRemoteContent The already-downloaded content of the remote model.
     * @param localPath The file path to the local model for comparison.
     * @return {@code true} if the remote model is newer, the local file does
     *         not exist, or the local file has no valid timestamp. Returns
     *         {@code false} if they are the same age, the local is newer, or
     *         if a comparison cannot be made.
     */
    private static boolean isRemoteNewer( String sRemoteContent, String localPath )
    {
        try
        {
            File localFile = new java.io.File( localPath );

            if( ! localFile.exists() )
                return true;              // Local doesn't exist, download remote

            String sRemoteGenerated = extractModelTimestamp( sRemoteContent );

            if( sRemoteGenerated == null )
                return false;             // Can't compare, don't update

            String sLocalContent   = UtilIO.getAsText( localPath );
            String sLocalGenerated = extractModelTimestamp( sLocalContent );

            if( sLocalGenerated == null )
                return true;              // Local has no timestamp, prefer remote

            // Compare timestamps
            Instant remoteTime = Instant.parse( sRemoteGenerated );
            Instant localTime  = Instant.parse( sLocalGenerated  );

            return remoteTime.isAfter( localTime );
        }
        catch( Exception exc )
        {
            return false;                 // Any error, don't update
        }
    }
}