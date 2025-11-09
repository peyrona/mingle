package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilCLI;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Main class for checking and updating files from GitHub repository.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Updater
{
    private static final java.util.concurrent.atomic.AtomicBoolean isWorking = new java.util.concurrent.atomic.AtomicBoolean( false );

    //------------------------------------------------------------------------//

    public static void main( String[] args )
    {
        if( args == null )
        {
            System.err.println( "Error: args cannot be null" );
            System.exit( 1 );
        }

        UtilCLI cli = new UtilCLI( args );

        if( (args.length == 0) || cli.hasOption( "help" ) || cli.hasOption( "h" ) )
        {
            System.out.println( "Usage: java Updater <folder> [-dry]" );
            System.out.println( "   folder  : Path to Mingle base directory" );
            System.out.println( "   -dry    : Optional flag to simulate updates without modifying files" );
            System.out.println( "   -help|-h: Show this help" );
            System.exit( (args.length == 0) ? 1 : 0 );
        }

        UtilSys.setLogger( "Updater", UtilSys.getConfig() )
               .setLevel( ILogger.Level.ALL );

        boolean dryRun   = cli.hasOption( "dry" );
        String pathArg   = args[0];

        if( pathArg == null || pathArg.trim().isEmpty() )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, "Error: Base directory path cannot be null or empty" );
            System.exit( 1 );
        }

        File fBaseDir = new File( pathArg );

        updateIfNeeded( fBaseDir, dryRun, () -> { return true; } );

        System.exit( 0 );
    }

    /**
     * Checks if the Updater is currently working (performing its job).
     *
     * @return true if the updater is currently running, false otherwise
     */
    public static boolean isWorking()
    {
        return isWorking.get();
    }

    //------------------------------------------------------------------------//

    /**
     * Checks if updates are needed by comparing catalog.json versions.
     * If versions differ, executes the consumer with the number of files needing updates.
     *
     * @param fMingleDir Base directory path to check
     * @param bDryRun If true, simulate updates without modifying files
     * @param fnExcuteUpdate Receives the number of files that are needed to be updated and returns true if the update method has to be invoked.
     */
    public static void updateIfNeeded( File fMingleDir, boolean bDryRun, Supplier<Boolean> fnExcuteUpdate )
    {
        if( fMingleDir == null )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, "Base directory cannot be null" );
            return;
        }

        if( fnExcuteUpdate == null )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, "Update function cannot be null" );
            return;
        }

        if( ! fMingleDir.exists() || ! fMingleDir.isDirectory() )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, "Base directory does not exist or is not a directory: " + fMingleDir.getAbsolutePath() );
            return;
        }

        File tempRemoteCatalog = null;

        try
        {
            UtilSys.getLogger().log( ILogger.Level.INFO, "Checking for needed updates by comparing catalog versions" );
            UtilSys.getLogger().log( ILogger.Level.INFO, "Base directory: " + fMingleDir.getAbsolutePath() );

            // Get local catalog version
            File localCatalogFile = new File( fMingleDir, "etc/catalog.json" );
            String localVersion = null;

            if( localCatalogFile.exists() && localCatalogFile.isFile() )
            {
                try
                {
                    String localCatalogContent = Files.readString( localCatalogFile.toPath() );

                    if( localCatalogContent != null && ! localCatalogContent.trim().isEmpty() )
                    {
                        localVersion = GitHubFileUpdater.parseVersionFromCatalog( localCatalogContent );
                        UtilSys.getLogger().log( ILogger.Level.INFO, "Local catalog version: " + (localVersion != null ? localVersion : "unknown") );
                    }
                    else
                    {
                        UtilSys.getLogger().log( ILogger.Level.WARNING, "Local catalog.json is empty" );
                    }
                }
                catch( IOException e )
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, "Error reading local catalog.json: " + e.getMessage() );
                }
            }
            else
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "Local catalog.json not found" );
            }

            // Get remote catalog version
            String remoteVersion = null;

            tempRemoteCatalog = File.createTempFile( "remote_catalog", ".json" );

            if( GitHubApiClient.downloadFileFromRoot( "todeploy/etc/catalog.json", tempRemoteCatalog.toPath() ) )
            {
                String remoteCatalogContent = Files.readString( tempRemoteCatalog.toPath() );

                if( remoteCatalogContent != null && ! remoteCatalogContent.trim().isEmpty() )
                {
                    remoteVersion = GitHubFileUpdater.parseVersionFromCatalog( remoteCatalogContent );
                    UtilSys.getLogger().log( ILogger.Level.INFO, "Remote catalog version: " + (remoteVersion != null ? remoteVersion : "unknown") );
                }
                else
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, "Remote catalog.json is empty" );
                }
            }
            else
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to download remote catalog.json" );
                return;
            }

            // Compare versions
            if( remoteVersion == null )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "No remote version found, cannot determine update need" );
            }
            else if( Objects.equals( remoteVersion, localVersion ) )
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "Versions match (local: " + localVersion + "), no update needed" );
            }
            else
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "Version mismatch detected (local: " + localVersion + ", remote: " + remoteVersion + "), update needed" );

                if( fnExcuteUpdate.get() )
                    update( fMingleDir, bDryRun, tempRemoteCatalog );
            }
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, "Error during version check: " + e.getMessage() );
        }
        finally
        {
            if( tempRemoteCatalog != null && tempRemoteCatalog.exists() )
                tempRemoteCatalog.delete();   // Clean up temp file
        }
    }

    /**
     * Updates files from GitHub repository for the specified path.
     *
     * @param fMingleDir Base directory path to check
     * @param bDryRun If true, simulate updates without modifying files
     * @param catalogFile The catalog file to use for the update.
     * @return true if update process completed successfully, false otherwise
     */
    public static boolean update( File fMingleDir, boolean bDryRun, File catalogFile )
    {
        // Input validation
        if( fMingleDir == null )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, "Base directory cannot be null" );
            return false;
        }

        // Set working state to true before starting the update process
        if( ! isWorking.compareAndSet( false, true ) )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Update process is already running" );
            return false;
        }

        try
        {
            if( ! fMingleDir.exists() || ! fMingleDir.isDirectory() )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, "Base directory does not exist or is not a directory: " + fMingleDir.getAbsolutePath() );
                return false;
            }

            UtilSys.getLogger().log( ILogger.Level.INFO, "Starting Updater" + (bDryRun ? " (DRY-RUN MODE)" : "") );
            UtilSys.getLogger().log( ILogger.Level.INFO, "Base directory: " + fMingleDir.getAbsolutePath() );

            GitHubFileUpdater updater = new GitHubFileUpdater( fMingleDir, bDryRun );
            updater.checkAndUpdateFiles(catalogFile);

            UtilSys.getLogger().log( ILogger.Level.INFO, "Updater completed successfully" + (bDryRun ? " (DRY-RUN MODE)" : "") );

            return true;
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, e, "Update failed" );
            return false;
        }
        finally
        {
            isWorking.set( false );   // Always set working state to false when done
        }
    }
}