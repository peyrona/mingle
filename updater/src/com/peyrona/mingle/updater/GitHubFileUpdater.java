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
        if( baseDir == null )
        {
            throw new IllegalArgumentException( "Base directory cannot be null" );
        }
        
        this.baseDir   = baseDir;
        this.dryRun    = dryRun;
        this.backupMgr = dryRun ? null : new BackupManager();
    }

    /**
     * Checks and updates files in the base directory.
     */
    public void checkAndUpdateFiles(File catalogFile)
    {
        UtilSys.getLogger().log( ILogger.Level.INFO, "Starting file check and update process" );

        try
        {
            if (catalogFile == null || !catalogFile.exists()) {
                UtilSys.getLogger().log(ILogger.Level.SEVERE, "Catalog file not provided or does not exist.");
                errors++;
                return;
            }
            processFiles( catalogFile, new HashFileComparator(), true );
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
    public int checkFilesOnly(File catalogFile)
    {
        filesChecked = 0;
        filesWouldUpdate = 0;
        errors = 0;

        UtilSys.getLogger().log( ILogger.Level.INFO, "Checking files for updates using hybrid timestamp + SHA approach" );

        try
        {
            if (catalogFile == null || !catalogFile.exists()) {
                UtilSys.getLogger().log(ILogger.Level.SEVERE, "Catalog file not provided or does not exist.");
                errors++;
                return filesWouldUpdate;
            }
            processFiles( catalogFile, new HashFileComparator(), false );
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
                    return new CatalogFileDiscoveryStrategy( catalogContent );
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
        if( entry == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File entry is null, skipping" );
            errors++;
            return;
        }
        
        if( comparator == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File comparator is null for: " + entry.path );
            errors++;
            return;
        }
        
        if( entry.path == null || entry.path.trim().isEmpty() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File path is null or empty, skipping" );
            errors++;
            return;
        }
        
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

            if( result == null )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Comparison result is null for: " + entry.path );
                errors++;
                return;
            }

            if( result.needsUpdate )  handleFileNeedsUpdate( entry, result, githubInfo, performUpdates );
            else                      UtilSys.getLogger().log( ILogger.Level.INFO, "File is up to date: " + entry.path + " (" + result.reason + ")" );
        }
        catch( RuntimeException e )
        {
            // Re-throw runtime exceptions to indicate serious issues
            UtilSys.getLogger().log( ILogger.Level.SEVERE, e, "Runtime error checking file: " + entry.path );
            errors++;
            throw e;
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
    private void handleFileNeedsUpdate( FileDiscoveryStrategy.FileEntry entry, FileComparator.ComparisonResult result, GitHubApiClient.GitHubFileResponse githubInfo, boolean performUpdates )
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
        
        // Log cache statistics
        GitHubApiClient.logCacheStatistics();
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
