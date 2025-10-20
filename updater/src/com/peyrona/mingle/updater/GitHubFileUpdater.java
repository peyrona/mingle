
package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Core logic for checking and updating files from GitHub repository.
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
            // Download catalog.json from GitHub repository root (always expected to exist)
            File catalogFile = downloadCatalogFromGitHub();

            if( catalogFile == null )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, "Failed to download catalog.json from GitHub - this should not happen" );
                errors++;
                return;
            }

            try
            {
                checkAndUpdateByCatalog( catalogFile );
            }
            finally
            {
                // Clean up temporary catalog file
                if( catalogFile.exists() )
                {
                    catalogFile.delete();
                }
            }

        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, e, "Error during file update process" );
            errors++;
        }
        finally
        {
            // Cleanup backups (only if not dry run)
            if( !dryRun && backupMgr != null )
            {
                backupMgr.cleanup();
            }

            // Log summary
            String summary = dryRun ? String.format( "DRY-RUN completed: %d files checked, %d files would be updated, %d errors", filesChecked, filesWouldUpdate, errors )
                                    : String.format( "Process completed: %d files checked, %d files updated, %d errors", filesChecked, filesUpdated, errors );

            UtilSys.getLogger().log( ILogger.Level.INFO, summary );
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
            // Download catalog.json from GitHub repository root (always expected to exist)
            File catalogFile = downloadCatalogFromGitHub();
            if( catalogFile == null )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, "Failed to download catalog.json from GitHub - this should not happen" );
                errors++;
                return filesWouldUpdate;
            }

            try
            {
                checkFilesOnlyHybridByCatalog( catalogFile );
            }
            finally
            {
                // Clean up temporary catalog file
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

    /**
     * Downloads catalog.json from GitHub repository root.
     *
     * @return File object containing the downloaded catalog, or null if download failed
     */
    private File downloadCatalogFromGitHub()
    {
        File tempCatalog = null;
        try
        {
            UtilSys.getLogger().log( ILogger.Level.INFO, "Downloading catalog.json from GitHub repository root" );

            // Create temporary file for catalog
            tempCatalog = File.createTempFile( "catalog", ".json" );
            tempCatalog.deleteOnExit();

            // Download catalog.json from repository root (not from todeploy directory)
            boolean success = GitHubApiClient.downloadFileFromRoot( "catalog.json", tempCatalog.toPath() );

            if( success )
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "Successfully downloaded catalog.json from GitHub" );
                return tempCatalog;
            }
            else
            {
                // Cleanup on failure
                if( tempCatalog != null && tempCatalog.exists() )
                    tempCatalog.delete();
                return null;
            }
        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error creating temporary file for catalog.json" );

            // Cleanup on exception
            if( tempCatalog != null && tempCatalog.exists() )
                tempCatalog.delete();

            return null;
        }
    }

    /**
     * Checks and updates files based on catalog.json.
     */
    private void checkAndUpdateByCatalog(File catalogFile)
    {
        UtilSys.getLogger().log( ILogger.Level.INFO, "Using catalog.json for file verification" );

        try
        {
            String                 catalogContent = Files.readString( catalogFile.toPath() );
            List<CatalogFileEntry> catalogEntries = parseCatalogJson( catalogContent );

            for( CatalogFileEntry entry : catalogEntries )
                checkAndUpdateFile( entry.path, entry.hash );
        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error reading catalog.json, falling back to directory traversal" );
            checkAndUpdateByTraversal();
        }
    }

    /**
     * Checks files only based on catalog.json (no updates).
     */
    private void checkFilesOnlyByCatalog(File catalogFile)
    {
        UtilSys.getLogger().log( ILogger.Level.INFO, "Using catalog.json for file verification (check only)" );

        try
        {
            String catalogContent = Files.readString( catalogFile.toPath() );
            List<CatalogFileEntry> catalogEntries = parseCatalogJson( catalogContent );

            for( CatalogFileEntry entry : catalogEntries )
                checkFileOnly( entry.path, entry.hash );
        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error reading catalog.json, falling back to directory traversal" );
            checkFilesOnlyByTraversal();
        }
    }

    /**
     * Checks files using hybrid approach based on catalog.json (no updates).
     */
    private void checkFilesOnlyHybridByCatalog(File catalogFile)
    {
        UtilSys.getLogger().log( ILogger.Level.INFO, "Using catalog.json for hybrid file verification" );

        try
        {
            String catalogContent = Files.readString( catalogFile.toPath() );
            List<CatalogFileEntry> catalogEntries = parseCatalogJson( catalogContent );

            for( CatalogFileEntry entry : catalogEntries )
                checkFileOnlyHybrid( entry.path, entry.hash );
        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error reading catalog.json, falling back to hybrid directory traversal" );
            checkFilesOnlyHybridByTraversal();
        }
    }

    /**
     * Checks and updates a single file.
     *
     * @param relativePath Relative path from base directory
     * @param expectedHash Expected hash (from catalog), or null to fetch from GitHub
     */
    private void checkAndUpdateFile(String relativePath, String expectedHash)
    {
        filesChecked++;

        try
        {
            File localFile = new File( baseDir, relativePath );

            // Get file metadata from GitHub
            GitHubApiClient.GitHubFileResponse githubInfo = GitHubApiClient.getFileMetadata( relativePath );

            if( githubInfo == null )
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "File not found on GitHub: " + relativePath );
                return;
            }

            // Calculate local file hash
            String localHash = HashCalculator.calculateSHA256( localFile );

            // Determine which hash to use for comparison
            String remoteHash = githubInfo.sha;

            // If we have expected hash from catalog, use it for validation
            if( expectedHash != null && ! expectedHash.equals( remoteHash ) )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, String.format( "Hash mismatch for %s - catalog: %s, GitHub: %s", relativePath, expectedHash, remoteHash ) );
                // Use catalog hash for validation as it's the trusted source
                remoteHash = expectedHash;
            }

            // Compare hashes and update if necessary
            if( localHash == null || remoteHash == null || ! localHash.equals( remoteHash ) )
            {
                String logPrefix = dryRun ? "DRY-RUN: " : "";
                UtilSys.getLogger().log( ILogger.Level.INFO, String.format( "%sFile needs update: %s (local: %s, remote: %s)",
                        logPrefix,
                        relativePath,
                        localHash != null ? localHash : "missing",
                        remoteHash != null ? remoteHash : "missing" ) );

                if( dryRun )
                {
                    filesWouldUpdate++;
                }
                else
                {
                    if( updateFile( relativePath, localFile, githubInfo ) )  filesUpdated++;
                    else                                                     errors++;
                }
            }
            else
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "File is up to date: " + relativePath );
            }

        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error checking file: " + relativePath );
            errors++;
        }
    }

    /**
     * Checks a single file only (no updates).
     *
     * @param relativePath Relative path from base directory
     * @param expectedHash Expected hash (from catalog), or null to fetch from GitHub
     */
    private void checkFileOnly(String relativePath, String expectedHash)
    {
        filesChecked++;

        try
        {
            File localFile = new File( baseDir, relativePath );

            // Get file metadata from GitHub
            GitHubApiClient.GitHubFileResponse githubInfo = GitHubApiClient.getFileMetadata( relativePath );

            if( githubInfo == null )
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "File not found on GitHub: " + relativePath );
                return;
            }

            // Calculate local file hash
            String localHash = HashCalculator.calculateSHA256( localFile );

            // Determine which hash to use for comparison
            String remoteHash = githubInfo.sha;

            // If we have expected hash from catalog, use it for validation
            if( expectedHash != null && !expectedHash.equals( remoteHash ) )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, String.format( "Hash mismatch for %s - catalog: %s, GitHub: %s",
                        relativePath, expectedHash, remoteHash ) );
                // Use catalog hash for validation as it's the trusted source
                remoteHash = expectedHash;
            }

            // Compare hashes and count if update needed
            if( localHash == null || remoteHash == null || !localHash.equals( remoteHash ) )
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, String.format( "File needs update: %s (local: %s, remote: %s)",
                                                                        relativePath,
                                                                        localHash != null ? localHash : "missing",
                                                                        remoteHash != null ? remoteHash : "missing" ) );

                filesWouldUpdate++;
            }
            else
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "File is up to date: " + relativePath );
            }

        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error checking file: " + relativePath );
            errors++;
        }
    }

    /**
     * Checks a single file using hybrid timestamp + SHA approach (no updates).
     *
     * @param relativePath Relative path from base directory
     * @param expectedHash Expected hash (from catalog), or null to fetch from GitHub
     */
    private void checkFileOnlyHybrid(String relativePath, String expectedHash)
    {
        filesChecked++;

        try
        {
            File localFile = new File( baseDir, relativePath );

            // Get file metadata from GitHub
            GitHubApiClient.GitHubFileResponse githubInfo = GitHubApiClient.getFileMetadata( relativePath );

            if( githubInfo == null )
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "File not found on GitHub: " + relativePath );
                return;
            }

            // Determine which hash to use for comparison
            String remoteHash = githubInfo.sha;
            // If we have expected hash from catalog, use it for validation
            if( expectedHash != null && !expectedHash.equals( remoteHash ) )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, String.format( "Hash mismatch for %s - catalog: %s, GitHub: %s",
                        relativePath, expectedHash, remoteHash ) );
                // Use catalog hash for validation as it's the trusted source
                remoteHash = expectedHash;
            }

            // Hybrid approach: First check timestamps, then SHA if needed
            boolean needsUpdate = false;
            String reason;

            if( !localFile.exists() )
            {
                needsUpdate = true;
                reason = "missing file";
            }
            else if( githubInfo.lastModified > 0 )
            {
                // Compare timestamps
                long localLastModified = localFile.lastModified();

                if( localLastModified < githubInfo.lastModified )
                {
                    // Remote file is newer, verify with SHA
                    String localHash = HashCalculator.calculateSHA256( localFile );

                    if( localHash == null || remoteHash == null || !localHash.equals( remoteHash ) )
                    {
                        needsUpdate = true;
                        reason = "newer remote file (timestamp + SHA verified)";
                    }
                    else
                    {
                        reason = "same content despite newer timestamp";
                    }
                }
                else if( localLastModified > githubInfo.lastModified )
                {
                    // Local file is newer, but still verify with SHA for safety
                    String localHash = HashCalculator.calculateSHA256( localFile );

                    if( localHash == null || remoteHash == null || !localHash.equals( remoteHash ) )
                    {
                        needsUpdate = true;
                        reason = "different content (local newer but SHA mismatch)";
                    }
                    else
                    {
                        reason = "same content (local newer)";
                    }
                }
                else
                {
                    // Timestamps are equal, assume up-to-date
                    reason = "same timestamp";
                }
            }
            else
            {
                // No timestamp info from GitHub, fall back to SHA only
                String localHash = HashCalculator.calculateSHA256( localFile );

                if( localHash == null || remoteHash == null || !localHash.equals( remoteHash ) )
                {
                    needsUpdate = true;
                    reason = "SHA verification (no timestamp available)";
                }
                else
                {
                    reason = "SHA verified (no timestamp available)";
                }
            }

            if( needsUpdate )
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, String.format( "File needs update: %s (%s)", relativePath, reason ) );
                filesWouldUpdate++;
            }
            else
            {
                UtilSys.getLogger().log( ILogger.Level.INFO, "File is up to date: " + relativePath + " (" + reason + ")" );
            }

        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Error checking file: " + relativePath );
            errors++;
        }
    }

    /**
     * Fallback method: Checks and updates files by traversing the base directory.
     * Used when catalog.json is not available.
     */
    private void checkAndUpdateByTraversal()
    {
        UtilSys.getLogger().log( ILogger.Level.INFO, "Using directory traversal fallback for file verification" );

        traverseAndUpdateDirectory( baseDir, "" );
    }

    /**
     * Fallback method: Checks files only by traversing the base directory.
     * Used when catalog.json is not available.
     */
    private void checkFilesOnlyByTraversal()
    {
        UtilSys.getLogger().log( ILogger.Level.INFO, "Using directory traversal fallback for file checking" );

        traverseAndCheckDirectory( baseDir, "" );
    }

    /**
     * Fallback method: Checks files using hybrid approach by traversing the base directory.
     * Used when catalog.json is not available.
     */
    private void checkFilesOnlyHybridByTraversal()
    {
        UtilSys.getLogger().log( ILogger.Level.INFO, "Using hybrid directory traversal fallback for file checking" );

        traverseAndCheckHybridDirectory( baseDir, "" );
    }

    /**
     * Recursively traverses directory and checks/updates files.
     */
    private void traverseAndUpdateDirectory(File dir, String relativePath)
    {
        File[] files = dir.listFiles();
        if( files == null )
        {
            return;
        }

        for( File file : files )
        {
            if( file.isDirectory() )
            {
                traverseAndUpdateDirectory( file, relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName() );
            }
            else if( file.isFile() )
            {
                String filePath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                checkAndUpdateFile( filePath, null );
            }
        }
    }

    /**
     * Recursively traverses directory and checks files only.
     */
    private void traverseAndCheckDirectory(File dir, String relativePath)
    {
        File[] files = dir.listFiles();
        if( files == null )
        {
            return;
        }

        for( File file : files )
        {
            if( file.isDirectory() )
            {
                traverseAndCheckDirectory( file, relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName() );
            }
            else if( file.isFile() )
            {
                String filePath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                checkFileOnly( filePath, null );
            }
        }
    }

    /**
     * Recursively traverses directory and checks files using hybrid approach.
     */
    private void traverseAndCheckHybridDirectory(File dir, String relativePath)
    {
        File[] files = dir.listFiles();

        if( files == null )
            return;

        for( File file : files )
        {
            if( file.isDirectory() )
            {
                traverseAndCheckHybridDirectory( file, relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName() );
            }
            else if( file.isFile() )
            {
                String filePath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                checkFileOnlyHybrid( filePath, null );
            }
        }
    }

    /**
     * Parses catalog.json content.
     *
     * @param catalogContent JSON content
     * @return List of file entries
     */
    private boolean updateFile(String relativePath, File localFile, GitHubApiClient.GitHubFileResponse githubInfo)
    {
        // Create backup
        File backupFile = null;

        if( localFile.exists() )
        {
            backupFile = backupMgr.createBackup( localFile );
            if( backupFile == null )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to create backup for: " + relativePath );
                return false;
            }
        }

        // Download new version
        Path targetPath = localFile.toPath();
        boolean downloadSuccess = GitHubApiClient.downloadFile( relativePath, targetPath );

        if( !downloadSuccess )
        {
            // Restore from backup if download failed
            if( backupFile != null )
                backupMgr.restoreFromBackup( backupFile, localFile );

            return false;
        }

        // Verify the downloaded file
        String newHash = HashCalculator.calculateSHA256( localFile );

        // Use the cached remote hash instead of making another API call
        String remoteHash = (githubInfo != null) ? githubInfo.sha : null;

        if( remoteHash != null && newHash != null && newHash.equals( remoteHash ) )
        {
            UtilSys.getLogger().log( ILogger.Level.INFO, "Successfully updated: " + relativePath );
            return true;
        }
        else
        {
            // Hash verification failed, restore from backup
            String failureReason = remoteHash == null ? "missing remote hash" :
                                  newHash == null ? "missing local hash" :
                                  "hash mismatch";
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Hash verification failed for: " + relativePath + " (" + failureReason + ")" );
            if( backupFile != null )
                backupMgr.restoreFromBackup( backupFile, localFile );

            return false;
        }
    }

    //------------------------------------------------------------------------//

    /**
     * Parses catalog.json content.
     *
     * @param catalogContent JSON content
     * @return List of file entries
     */
    private static final String CATALOG_FILE_PATTERN = "\\{\\s*\"path\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"hash\"\\s*:\\s*\"([a-fA-F0-9]+)\"\\s*\\}";
    private static final java.util.regex.Pattern CATALOG_PATTERN = java.util.regex.Pattern.compile( CATALOG_FILE_PATTERN, java.util.regex.Pattern.CASE_INSENSITIVE );

    /**
     * Pattern to extract version from catalog.json.
     */
    private static final String VERSION_PATTERN = "\"version\"\\s*:\\s*\"([^\"]+)\"";
    private static final java.util.regex.Pattern VERSION_REGEX = java.util.regex.Pattern.compile( VERSION_PATTERN );

    private List<CatalogFileEntry> parseCatalogJson(String catalogContent)
    {
        List<CatalogFileEntry> entries = new ArrayList<>();

        // Simple JSON parsing (avoiding external dependencies)
        java.util.regex.Matcher matcher = CATALOG_PATTERN.matcher( catalogContent );

        while( matcher.find() )
        {
            CatalogFileEntry entry = new CatalogFileEntry();
            entry.path = matcher.group( 1 );
            entry.hash = matcher.group( 2 );
            entries.add( entry );
        }

        UtilSys.getLogger().log( ILogger.Level.INFO, "Parsed " + entries.size() + " file entries from catalog.json" );
        return entries;
    }

    /**
     * Extracts version from catalog.json content.
     *
     * @param catalogContent JSON content
     * @return Version string or null if not found
     */
    public static String parseVersionFromCatalog(String catalogContent)
    {
        java.util.regex.Matcher matcher = VERSION_REGEX.matcher( catalogContent );

        if( matcher.find() )
        {
            return matcher.group( 1 );
        }

        return null;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    /**
     * Represents a file entry from catalog.json.
     */
    private static class CatalogFileEntry
    {
        String path;
        String hash;
    }
}