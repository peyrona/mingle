package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;

/**
 * Encapsulates the file update operation: backup, download, and verify.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class FileUpdateOperation
{
    private final BackupManager backupManager;
    private final String relativePath;
    private final File localFile;
    private final GitHubApiClient.GitHubFileResponse githubInfo;

    public FileUpdateOperation( BackupManager backupManager, String relativePath,
                                File localFile, GitHubApiClient.GitHubFileResponse githubInfo )
    {
        if( relativePath == null || relativePath.trim().isEmpty() )
        {
            throw new IllegalArgumentException( "Relative path cannot be null or empty" );
        }
        
        if( localFile == null )
        {
            throw new IllegalArgumentException( "Local file cannot be null" );
        }
        
        if( githubInfo == null )
        {
            throw new IllegalArgumentException( "GitHub info cannot be null" );
        }
        
        this.backupManager = backupManager;
        this.relativePath = relativePath;
        this.localFile = localFile;
        this.githubInfo = githubInfo;
    }

    /**
     * Executes the file update operation.
     *
     * @return true if update successful, false otherwise
     */
    public boolean execute()
    {
        // Create backup
        File backupFile = null;

        if( localFile.exists() )
        {
            if( backupManager != null )
            {
                backupFile = backupManager.createBackup( localFile );

                if( backupFile == null )
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to create backup for: " + relativePath );
                    return false;
                }
            }
            else
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Backup manager is null, skipping backup for: " + relativePath );
            }
        }

        // Download new version
        boolean downloadSuccess = GitHubApiClient.downloadFile( relativePath, localFile.toPath() );

        if( ! downloadSuccess )
        {
            if( backupFile != null )    // Restore from backup if download failed
                backupManager.restoreFromBackup( backupFile, localFile );

            return false;
        }

        // Verify the downloaded file
        return verifyDownloadedFile( backupFile );
    }

    private boolean verifyDownloadedFile( File backupFile )
    {
        String remoteHash = (githubInfo != null) ? githubInfo.sha : null;
        
        if( remoteHash == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Missing remote hash for: " + relativePath );
            if( backupFile != null )
                backupManager.restoreFromBackup( backupFile, localFile );
            return false;
        }

        // Calculate appropriate hash based on remote hash type
        String newHash = HashCalculator.calculateHash( localFile, remoteHash );
        
        if( newHash == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to calculate local hash for: " + relativePath );
            if( backupFile != null )
                backupManager.restoreFromBackup( backupFile, localFile );
            return false;
        }

        if( newHash.equals( remoteHash ) )
        {
            String hashType = HashCalculator.isValidSHA1( remoteHash ) ? "SHA-1" : "SHA-256";
            UtilSys.getLogger().log( ILogger.Level.INFO, "Successfully updated: " + relativePath + " (" + hashType + " verified)" );
            return true;
        }
        else
        {
            // Hash verification failed, restore from backup
            String hashType = HashCalculator.isValidSHA1( remoteHash ) ? "SHA-1" : "SHA-256";
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Hash verification failed for: " + relativePath + " (" + hashType + " mismatch)" );

            if( backupFile != null )
                backupManager.restoreFromBackup( backupFile, localFile );

            return false;
        }
    }
}
