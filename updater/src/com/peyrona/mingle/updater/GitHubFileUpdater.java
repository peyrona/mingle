package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core logic for checking and updating files from GitHub repository.
 * Uses catalog.json for file discovery and SHA hash comparison to detect stale files.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class GitHubFileUpdater
{
    private final File          baseDir;
    private final BackupManager backupMgr;
    private final boolean       dryRun;
    private final AtomicBoolean abortRequested;
    private       int filesChecked     = 0;
    private       int filesUpdated     = 0;
    private       int filesWouldUpdate = 0;
    private       int filesSkipped     = 0;
    private       int errors           = 0;

    //------------------------------------------------------------------------//

    /**
     * Creates a GitHubFileUpdater for the specified base directory.
     *
     * @param baseDir        Base directory to check and update files
     * @param dryRun         If true, simulate updates without modifying files
     * @param abortRequested Shared flag; when set to {@code true} the update loop stops after
     *                       the current file finishes. May be {@code null} (abort disabled).
     */
    public GitHubFileUpdater( File baseDir, boolean dryRun, AtomicBoolean abortRequested )
    {
        if( baseDir == null )
        {
            throw new IllegalArgumentException( "Base directory cannot be null" );
        }

        this.baseDir        = baseDir;
        this.dryRun         = dryRun;
        this.backupMgr      = dryRun ? null : new BackupManager();
        this.abortRequested = (abortRequested != null) ? abortRequested : new AtomicBoolean( false );
    }

    /**
     * Creates a GitHubFileUpdater for the specified base directory (abort disabled).
     *
     * @param baseDir Base directory to check and update files
     * @param dryRun  If true, simulate updates without modifying files
     */
    public GitHubFileUpdater( File baseDir, boolean dryRun )
    {
        this( baseDir, dryRun, null );
    }

    /**
     * Checks and updates files in the base directory.
     *
     * @param catalogFile Catalog file listing expected file paths and hashes
     */
    public void checkAndUpdateFiles( File catalogFile )
    {
        UtilSys.getLogger().log( ILogger.Level.INFO, "Starting file check and update process" );

        try
        {
            if( catalogFile == null || ! catalogFile.exists() )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, "Catalog file not provided or does not exist." );
                errors++;
                return;
            }

            processFiles( catalogFile, true );
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

    //------------------------------------------------------------------------//
    // PRIVATE METHODS
    //------------------------------------------------------------------------//

    /**
     * Processes files using the discovery strategy derived from the catalog.
     */
    private void processFiles( File catalogFile, boolean performUpdates )
    {
        FileDiscoveryStrategy discoveryStrategy = createDiscoveryStrategy( catalogFile );
        List<FileDiscoveryStrategy.FileEntry> fileEntries = discoveryStrategy.discoverFiles();

        for( FileDiscoveryStrategy.FileEntry entry : fileEntries )
        {
            if( abortRequested.get() )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Update aborted by request" );
                break;
            }

            processFile( entry, performUpdates );
        }
    }

    /**
     * Creates the appropriate discovery strategy based on catalog availability.
     */
    private FileDiscoveryStrategy createDiscoveryStrategy( File catalogFile )
    {
        if( catalogFile != null && catalogFile.exists() )
        {
            try
            {
                String catalogContent = Files.readString( catalogFile.toPath(), StandardCharsets.UTF_8 );
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
     * Processes a single file: fetches remote hash, compares with local, updates if needed.
     */
    private void processFile( FileDiscoveryStrategy.FileEntry entry, boolean performUpdates )
    {
        if( entry == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "File entry is null, skipping" );
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

            String remoteHash = githubInfo.sha;

            if( remoteHash == null )
            {
                handleFileNeedsUpdate( entry, null, null, githubInfo, performUpdates );
                return;
            }

            String  localHash   = HashCalculator.calculateHash( localFile, remoteHash );
            boolean needsUpdate = (localHash == null || ! localHash.equals( remoteHash ));

            if( ! needsUpdate )
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "File is up to date: " + entry.path + " (hashes match)" );
            }
            else if( entry.isProtected && localFile.exists() )
            {
                // File differs from remote but belongs to the user — never overwrite
                String logPrefix = dryRun ? "DRY-RUN: " : "";
                UtilSys.getLogger().log( ILogger.Level.WARNING,
                    logPrefix + "Skipping protected file (local has been modified): " + entry.path );
                filesSkipped++;
            }
            else
            {
                handleFileNeedsUpdate( entry, localHash, remoteHash, githubInfo, performUpdates );
            }
        }
        catch( RuntimeException e )
        {
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
     * Gets GitHub file information, using the trusted catalog hash when available
     * to avoid an API round-trip.
     */
    private GitHubApiClient.GitHubFileResponse getGitHubFileInfo( FileDiscoveryStrategy.FileEntry entry )
    {
        if( entry.expectedHash != null )
        {
            GitHubApiClient.GitHubFileResponse response = new GitHubApiClient.GitHubFileResponse();
            response.path = entry.path;
            response.sha  = entry.expectedHash;
            UtilSys.getLogger().log( ILogger.Level.INFO, "Using trusted catalog hash for: " + entry.path );
            return response;
        }

        return GitHubApiClient.getFileMetadata( entry.path );
    }

    /**
     * Handles a file that needs updating.
     */
    private void handleFileNeedsUpdate( FileDiscoveryStrategy.FileEntry entry, String localHash, String remoteHash,
                                        GitHubApiClient.GitHubFileResponse githubInfo, boolean performUpdates )
    {
        String logPrefix = dryRun ? "DRY-RUN: " : "";

        UtilSys.getLogger().log( ILogger.Level.INFO, String.format( "%sFile needs update: %s (local: %s, remote: %s)",
                logPrefix, entry.path,
                localHash  != null ? localHash  : "missing",
                remoteHash != null ? remoteHash : "missing" ) );

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
     * Cleans up backup resources and logs the operation summary.
     */
    private void cleanupAndLogSummary()
    {
        if( ! dryRun && backupMgr != null )
            backupMgr.cleanup();

        boolean aborted = abortRequested.get();
        String  summary = dryRun
            ? String.format( "DRY-RUN %s: %d files checked, %d would be updated, %d protected skipped, %d errors",
                             aborted ? "aborted" : "completed", filesChecked, filesWouldUpdate, filesSkipped, errors )
            : String.format( "Process %s: %d files checked, %d updated, %d protected skipped, %d errors",
                             aborted ? "aborted" : "completed", filesChecked, filesUpdated, filesSkipped, errors );

        UtilSys.getLogger().log( ILogger.Level.INFO, summary );
        GitHubApiClient.logCacheStatistics();
    }
}
