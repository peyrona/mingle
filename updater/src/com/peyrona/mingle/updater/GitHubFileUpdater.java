package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Core logic for checking and updating files from GitHub repository.
 * Simplified version using strategy pattern for better maintainability.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class GitHubFileUpdater
{
    private final File baseDir;
    private final BackupManager backupMgr;
    private final boolean dryRun;
    private       int filesChecked = 0;
    private       int filesUpdated = 0;
    private       int filesWouldUpdate = 0;
    private       int errors = 0;

    //------------------------------------------------------------------------//

    /**
     * Creates a GitHubFileUpdater for the specified base directory.
     *
     * @param baseDir Base directory to check and update files
     * @param dryRun  If true, simulate updates without modifying files
     */
    public GitHubFileUpdater( File baseDir, boolean dryRun )
    {
        this.baseDir   = baseDir;
        this.dryRun    = dryRun;
        this.backupMgr = dryRun ? null : new BackupManager();
    }

    /**
     * Checks and updates files in the base directory.
     */
    public void checkAndUpdateFiles()
    {
        UtilSys.getLogger().log( ILogger.Level.INFO, "Starting file check and update process" );

        try
        {
            File catalogFile = downloadCatalogFromGitHub();

            if( catalogFile == null )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, "Failed to download catalog.json from GitHub - this should not happen" );
                errors++;
                return;
            }

            try
            {
                processFiles( catalogFile, new HashFileComparator(), true );
            }
            finally
            {
                if( catalogFile.exists() )
                    catalogFile.delete();
            }

        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, e, "Error during file update process" );
            errors++;
        }
        finally
        {
            cleanupAndLogSummary();
        }
    }

    /**
     * Checks files using hybrid timestamp + SHA approach (no updates).
     * Optimized for performance by using timestamps as pre-filter.
     *
     * @return Number of files that need updates
     */
    public int checkFilesOnly()
    {
        filesChecked = 0;
        filesWouldUpdate = 0;
        errors = 0;

        UtilSys.getLogger().log( ILogger.Level.INFO, "Checking files for updates using hybrid timestamp + SHA approach" );

        try
        {
            File catalogFile = downloadCatalogFromGitHub();
            if( catalogFile == null )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, "Failed to download catalog.json from GitHub - this should not happen" );
                errors++;
                return filesWouldUpdate;
            }

            try
            {
                processFiles( catalogFile, new HybridFileComparator(), false );
            }
            finally
            {
                if( catalogFile.exists() )
                {
                    catalogFile.delete();
                }
            }

        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, e, "Error during hybrid file check process" );
            errors++;
        }

        return filesWouldUpdate;
    }

    /**
     * Gets the number of files checked during the last operation.
     *
     * @return Number of files checked
     */
    public int getFilesChecked()
    {
        return filesChecked;
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS
    //------------------------------------------------------------------------//

    /**
     * Downloads catalog.json from GitHub todeploy directory.
     */
    private File downloadCatalogFromGitHub()
    {
        File tempCatalog = null;
        try
        {
            UtilSys.getLogger().log( ILogger.Level.INFO, "Downloading catalog.json from GitHub todeploy directory" );

            tempCatalog = File.createTempFile( "catalog", ".json" );
            tempCatalog.deleteOnExit();

            boolean success = GitHubApiClient.downloadFileFromRoot( "todeploy/catalog.json", tempCatalog.toPath() );

            if( success )
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "Successfully downloaded catalog.json from GitHub todeploy directory" );
                return tempCatalog;
            }
            else
            {
                if( tempCatalog != null && tempCatalog.exists() )
                    tempCatalog.delete();

                return null;
            }
        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error creating temporary file for catalog.json" );

            if( tempCatalog != null && tempCatalog.exists() )
                tempCatalog.delete();

            return null;
        }
    }

    /**
     * Processes files using the specified discovery strategy and comparator.
     */
    private void processFiles( File catalogFile, FileComparator comparator, boolean performUpdates )
    {
        FileDiscoveryStrategy discoveryStrategy = createDiscoveryStrategy( catalogFile );
        List<FileDiscoveryStrategy.FileEntry> fileEntries = discoveryStrategy.discoverFiles();

        for( FileDiscoveryStrategy.FileEntry entry : fileEntries )
        {
            processFile( entry, comparator, performUpdates );
        }
    }

    /**
     * Creates appropriate discovery strategy based on catalog availability.
     */
    private FileDiscoveryStrategy createDiscoveryStrategy( File catalogFile )
    {
        if( catalogFile != null && catalogFile.exists() )
        {
            try
            {
                String catalogContent = Files.readString( catalogFile.toPath() );
                List<CatalogParser.CatalogFileEntry> entries = CatalogParser.parseCatalogJson( catalogContent );

                if( ! entries.isEmpty() )
                {
                    UtilSys.getLogger().log( ILogger.Level.INFO, "Using catalog.json for file verification" );
                    return new CatalogFileDiscoveryStrategy( catalogFile );
                }
            }
            catch( IOException e )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error reading catalog.json, falling back to directory traversal" );
            }
        }

        UtilSys.getLogger().log( ILogger.Level.INFO, "Using directory traversal fallback for file verification" );
        return new TraversalFileDiscoveryStrategy( baseDir );
    }

    /**
     * Processes a single file using the specified comparator.
     */
    private void processFile( FileDiscoveryStrategy.FileEntry entry, FileComparator comparator, boolean performUpdates )
    {
        filesChecked++;

        // Log when processing catalog.json to ensure it's last
        if( "catalog.json".equals( entry.path ) )
            UtilSys.getLogger().log( ILogger.Level.INFO, "Processing catalog.json (ensured to be last in update order)" );

        try
        {
            File localFile = new File( baseDir, entry.path );
            GitHubApiClient.GitHubFileResponse githubInfo = getGitHubFileInfo( entry );

            if( githubInfo == null )
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "File not found on GitHub: " + entry.path );
                return;
            }

            FileComparator.ComparisonContext context = new FileComparator.ComparisonContext( entry.path, entry, localFile, githubInfo );
            FileComparator.ComparisonResult result = comparator.compare( context );

            if( result.needsUpdate )  handleFileNeedsUpdate( entry, result, performUpdates );
            else                      UtilSys.getLogger().log( ILogger.Level.INFO, "File is up to date: " + entry.path + " (" + result.reason + ")" );
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error checking file: " + entry.path );
            errors++;
        }
    }

    /**
     * Gets GitHub file information, using cached data from catalog when available.
     */
    private GitHubApiClient.GitHubFileResponse getGitHubFileInfo( FileDiscoveryStrategy.FileEntry entry )
    {
        if( entry.expectedHash != null )
        {
            // Create minimal response object using catalog data
            GitHubApiClient.GitHubFileResponse response = new GitHubApiClient.GitHubFileResponse();
            response.path = entry.path;
            response.sha = entry.expectedHash;
            response.lastModified = 0; // No timestamp when using catalog
            UtilSys.getLogger().log( ILogger.Level.INFO, "Using trusted catalog hash for: " + entry.path );
            return response;
        }
        else
        {
            // Fetch from GitHub API
            return GitHubApiClient.getFileMetadata( entry.path );
        }
    }

    /**
     * Handles files that need updates.
     */
    private void handleFileNeedsUpdate( FileDiscoveryStrategy.FileEntry entry, FileComparator.ComparisonResult result, boolean performUpdates )
    {
        String logPrefix = dryRun ? "DRY-RUN: " : "";

        UtilSys.getLogger().log( ILogger.Level.INFO, String.format( "%sFile needs update: %s (local: %s, remote: %s)",
                logPrefix, entry.path,
                result.localHash != null ? result.localHash : "missing",
                result.remoteHash != null ? result.remoteHash : "missing" ) );

        if( dryRun )
        {
            filesWouldUpdate++;
        }
        else if( performUpdates )
        {
            File localFile = new File( baseDir, entry.path );
            GitHubApiClient.GitHubFileResponse githubInfo = getGitHubFileInfo( entry );

            FileUpdateOperation updateOp = new FileUpdateOperation( backupMgr, entry.path, localFile, githubInfo );

            if( updateOp.execute() )  filesUpdated++;
            else                      errors++;
        }
        else
        {
            filesWouldUpdate++;
        }
    }

    /**
     * Cleans up resources and logs operation summary.
     */
    private void cleanupAndLogSummary()
    {
        // Cleanup backups (only if not dry run)
        if( ! dryRun && backupMgr != null )
            backupMgr.cleanup();

        // Log summary
        String summary = dryRun
            ? String.format( "DRY-RUN completed: %d files checked, %d files would be updated, %d errors", filesChecked, filesWouldUpdate, errors )
            : String.format( "Process completed: %d files checked, %d files updated, %d errors", filesChecked, filesUpdated, errors );

        UtilSys.getLogger().log( ILogger.Level.INFO, summary );
    }

    //------------------------------------------------------------------------//
    // STATIC METHOD
    //------------------------------------------------------------------------//

    /**
     * Extracts version from catalog.json content.
     * Kept for backward compatibility with Updater class.
     *
     * @param catalogContent JSON content
     * @return Version string or null if not found
     */
    public static String parseVersionFromCatalog( String catalogContent )
    {
        return CatalogParser.parseVersionFromCatalog( catalogContent );
    }
}