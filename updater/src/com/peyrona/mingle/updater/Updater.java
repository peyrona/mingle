
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
    private static volatile boolean isWorking = false;

    //------------------------------------------------------------------------//

    public static void main( String[] args )
    {
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
        File    fBaseDir = new File( args[0] );

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
        return isWorking;
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
        if( ! fMingleDir.exists() || ! fMingleDir.isDirectory() )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, "Base directory does not exist or is not a directory: " + fMingleDir.getAbsolutePath() );
            return;
        }

        try
        {
            UtilSys.getLogger().log( ILogger.Level.INFO, "Checking for needed updates by comparing catalog versions" );
            UtilSys.getLogger().log( ILogger.Level.INFO, "Base directory: " + fMingleDir.getAbsolutePath() );

            // Get local catalog version
            File localCatalogFile = new File( fMingleDir, "catalog.json" );
            String localVersion = null;

            if( localCatalogFile.exists() && localCatalogFile.isFile() )
            {
                try
                {
                    String localCatalogContent = Files.readString( localCatalogFile.toPath() );
                    localVersion = GitHubFileUpdater.parseVersionFromCatalog( localCatalogContent );
                    UtilSys.getLogger().log( ILogger.Level.INFO, "Local catalog version: " + (localVersion != null ? localVersion : "unknown") );
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
            String remoteVersion;
            File   tempRemoteCatalog;

            try
            {
                tempRemoteCatalog = File.createTempFile( "remote_catalog", ".json" );
                tempRemoteCatalog.deleteOnExit();

                if( GitHubApiClient.downloadFileFromRoot( "todeploy/catalog.json", tempRemoteCatalog.toPath() ) )
                {
                    String remoteCatalogContent = Files.readString( tempRemoteCatalog.toPath() );
                    remoteVersion = GitHubFileUpdater.parseVersionFromCatalog( remoteCatalogContent );
                    UtilSys.getLogger().log( ILogger.Level.INFO, "Remote catalog version: " + (remoteVersion != null ? remoteVersion : "unknown") );
                }
                else
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to download remote catalog.json" );
                    return;
                }
            }
            catch( IOException e )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Error downloading remote catalog.json: " + e.getMessage() );
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
                    update( fMingleDir, bDryRun );
            }
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, "Error during version check: " + e.getMessage() );
        }
    }

    /**
     * Updates files from GitHub repository for the specified path.
     *
     * @param fMingleDir Base directory path to check
     * @param bDryRun If true, simulate updates without modifying files
     * @return true if update process completed successfully, false otherwise
     */
    public static boolean update( File fMingleDir, boolean bDryRun )
    {
        try
        {
            // Set working state to true before starting the update process
            isWorking = true;

            if( ! fMingleDir.exists() || ! fMingleDir.isDirectory() )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, "Base directory does not exist or is not a directory: " + fMingleDir.getAbsolutePath() );
                return false;
            }

            UtilSys.getLogger().log( ILogger.Level.INFO, "Starting Updater" + (bDryRun ? " (DRY-RUN MODE)" : "") );
            UtilSys.getLogger().log( ILogger.Level.INFO, "Base directory: " + fMingleDir.getAbsolutePath() );

            GitHubFileUpdater updater = new GitHubFileUpdater( fMingleDir, bDryRun );
            updater.checkAndUpdateFiles();

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
            isWorking = false;   // Always set working state to false when done
        }
    }
}