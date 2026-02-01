package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilCLI;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final AtomicBoolean isWorking = new AtomicBoolean( false );

    //------------------------------------------------------------------------//

    public static void main( String[] args )
    {
        if( args == null )
        {
            System.err.println( "Error: args cannot be null" );
            System.exit( 1 );
        }

        UtilCLI cli = new UtilCLI( args );

        if( cli.hasOption( "help" ) || cli.hasOption( "h" ) )
        {
            System.out.println( "Usage: java Updater [-dry]" );
            System.out.println( "   -dry    : Optional flag to simulate updates without modifying files" );
            System.out.println( "   -help|-h: Show this help" );
            System.exit( 0 );
        }

        UtilSys.setLogger( "Updater", UtilSys.getConfig() )
               .setLevel( ILogger.Level.ALL );

        boolean dryRun  = cli.hasOption( "dry" );

        updateIfNeeded( dryRun, () -> { return true; } );

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

    /**
     * Checks if updates are needed by comparing catalog.json versions.
     * If versions differ, executes the consumer with the number of files needing updates.
     *
     * @param bDryRun If true, simulate updates without modifying files
     * @param fnExcuteUpdate Receives the number of files that are needed to be updated and returns true if the update method has to be invoked.
     */
    public static void updateIfNeeded( boolean bDryRun, Supplier<Boolean> fnExcuteUpdate )
    {
        if( fnExcuteUpdate == null )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, "Update function cannot be null" );
            return;
        }

        File tempRemoteCatalog = null;

        try
        {
            UtilSys.getLogger().log( ILogger.Level.INFO, "Checking for needed updates by comparing catalog versions" );
            UtilSys.getLogger().log( ILogger.Level.INFO, "Base directory: " + UtilSys.fHomeDir.getAbsolutePath() );

            // Get local catalog version
            File   localCatalogFile = new File( UtilSys.getEtcDir(), "catalog.json" );
            String localVersion     = null;

            if( localCatalogFile.exists() && localCatalogFile.isFile() )
            {
                try
                {
                    String localCatalogContent = Files.readString( localCatalogFile.toPath(), StandardCharsets.UTF_8 );

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

            if( GitHubApiClient.downloadFileFromRoot( "catalog.json", tempRemoteCatalog.toPath() ) )
            {
                String remoteCatalogContent = Files.readString( tempRemoteCatalog.toPath(), StandardCharsets.UTF_8 );

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
                {
                    if( update( bDryRun, tempRemoteCatalog ) && ! bDryRun )
                    {
                        try
                        {
                            Files.copy( tempRemoteCatalog.toPath(), localCatalogFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING );
                            UtilSys.getLogger().log( ILogger.Level.INFO, "Updated local catalog.json" );
                        }
                        catch( Exception e )
                        {
                            UtilSys.getLogger().log( ILogger.Level.SEVERE, e, "Failed to update local catalog.json" );
                        }
                    }
                }
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
     * @param bDryRun If true, simulate updates without modifying files
     * @param catalogFile The catalog file to use for the update.
     * @return true if update process completed successfully, false otherwise
     */
    public static boolean update( boolean bDryRun, File catalogFile )
    {
        // Set working state to true before starting the update process
        if( ! isWorking.compareAndSet( false, true ) )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Update process is already running" );
            return false;
        }

        try
        {
            if( ! UtilSys.fHomeDir.exists() || ! UtilSys.fHomeDir.isDirectory() )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, "Base directory does not exist or is not a directory: " + UtilSys.fHomeDir.getAbsolutePath() );
                return false;
            }

            UtilSys.getLogger().log( ILogger.Level.INFO, "Starting Updater" + (bDryRun ? " (DRY-RUN MODE)" : "") );
            UtilSys.getLogger().log( ILogger.Level.INFO, "Base directory: " + UtilSys.fHomeDir.getAbsolutePath() );

            GitHubFileUpdater updater = new GitHubFileUpdater( UtilSys.fHomeDir, bDryRun );
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