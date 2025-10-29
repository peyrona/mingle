package com.peyrona.mingle.updater;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages backup creation and cleanup for files being updated.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class BackupManager
{
    private static final String BACKUP_SUFFIX = ".backup.";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern( "yyyyMMdd_HHmmss" );
    private        final File backupDir;
    private        final List<File> createdBackups = new ArrayList<>();

    //------------------------------------------------------------------------//

    /**
     * Creates a BackupManager with backup directory in the system temp directory.
     */
    public BackupManager()
    {
        this.backupDir = new File( UtilSys.getTmpDir(), "filehashchecker_backups_" + System.currentTimeMillis() );

        if( ! backupDir.mkdirs() && ! backupDir.exists() )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, "Failed to create backup directory: " + backupDir.getAbsolutePath() );
        }

        UtilSys.getLogger().log( ILogger.Level.INFO, "Backup directory: " + backupDir.getAbsolutePath() );
    }

    /**
     * Creates a backup of the specified file.
     *
     * @param originalFile File to backup
     * @return Backup file if successful, null otherwise
     */
    public File createBackup(File originalFile)
    {
        // Input validation
        if( originalFile == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Original file cannot be null" );
            return null;
        }
        
        if( ! originalFile.exists() || ! originalFile.isFile() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Cannot backup file that does not exist: " + originalFile.getAbsolutePath() );
            return null;
        }

        try
        {
            String timestamp = LocalDateTime.now().format( TIMESTAMP_FORMAT );
            String backupFileName = originalFile.getName() + BACKUP_SUFFIX + timestamp;
            File backupFile = new File( backupDir, backupFileName );

            // Create backup with proper error handling
            try( java.io.InputStream inStream = new java.io.BufferedInputStream( Files.newInputStream( originalFile.toPath() ) );
                 java.io.OutputStream outStream = new java.io.BufferedOutputStream( Files.newOutputStream( backupFile.toPath() ) ) )
            {
                byte[] buffer = new byte[65536]; // 64KB buffer
                int bytesRead;
                while( (bytesRead = inStream.read( buffer )) != -1 )
                {
                    outStream.write( buffer, 0, bytesRead );
                }
            }

            createdBackups.add( backupFile );
            UtilSys.getLogger().log( ILogger.Level.INFO, "Created backup: " + backupFile.getAbsolutePath() );

            return backupFile;

        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Failed to create backup for: " + originalFile.getAbsolutePath() );
            return null;
        }
    }

    /**
     * Restores a file from its backup.
     *
     * @param backupFile   Backup file to restore from
     * @param originalFile Original file location to restore to
     * @return true if restore successful, false otherwise
     */
    public boolean restoreFromBackup(File backupFile, File originalFile)
    {
        // Input validation
        if( backupFile == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Backup file cannot be null" );
            return false;
        }
        
        if( originalFile == null )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Original file cannot be null" );
            return false;
        }
        
        if( !backupFile.exists() || !backupFile.isFile() )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Backup file does not exist: " + backupFile.getAbsolutePath() );
            return false;
        }

        try
        {
            // Create parent directories if they don't exist
            File parentDir = originalFile.getParentFile();
            if( parentDir != null && !parentDir.exists() )
            {
                parentDir.mkdirs();
            }

            // Restore from backup with proper buffering
            try( java.io.InputStream inStream = new java.io.BufferedInputStream( Files.newInputStream( backupFile.toPath() ) );
                 java.io.OutputStream outStream = new java.io.BufferedOutputStream( Files.newOutputStream( originalFile.toPath() ) ) )
            {
                byte[] buffer = new byte[65536]; // 64KB buffer
                int bytesRead;
                while( (bytesRead = inStream.read( buffer )) != -1 )
                {
                    outStream.write( buffer, 0, bytesRead );
                }
            }

            UtilSys.getLogger().log( ILogger.Level.INFO, "Restored file from backup: " + originalFile.getAbsolutePath() );
            return true;

        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Failed to restore from backup: " + backupFile.getAbsolutePath() );
            return false;
        }
    }

    /**
     * Deletes all created backup files and the backup directory.
     *
     * @return true if cleanup successful, false otherwise
     */
    public boolean cleanup()
    {
        boolean success = true;
        int deletedFiles = 0;
        int failedFiles = 0;

        // First, try to delete all backup files
        for( File backupFile : createdBackups )
        {
            if( backupFile != null && backupFile.exists() )
            {
                try
                {
                    boolean deleted = backupFile.delete();
                    if( deleted )
                    {
                        deletedFiles++;
                        UtilSys.getLogger().log( ILogger.Level.INFO, "Deleted backup: " + backupFile.getAbsolutePath() );
                    }
                    else
                    {
                        failedFiles++;
                        UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to delete backup (delete returned false): " + backupFile.getAbsolutePath() );
                        success = false;
                    }
                }
                catch( SecurityException e )
                {
                    failedFiles++;
                    UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Security exception deleting backup: " + backupFile.getAbsolutePath() );
                    success = false;
                }
            }
        }

        // Only attempt to delete directory if all files were deleted successfully
        if( backupDir != null && backupDir.exists() && failedFiles == 0 )
        {
            try
            {
                boolean deleted = backupDir.delete();
                if( deleted )
                {
                    UtilSys.getLogger().log( ILogger.Level.INFO, "Deleted backup directory: " + backupDir.getAbsolutePath() );
                }
                else
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to delete backup directory (delete returned false): " + backupDir.getAbsolutePath() );
                    success = false;
                }
            }
            catch( SecurityException e )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, e, "Security exception deleting backup directory: " + backupDir.getAbsolutePath() );
                success = false;
            }
        }
        else if( failedFiles > 0 )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Skipping backup directory deletion due to " + failedFiles + " failed file deletions" );
        }

        // Log cleanup summary
        UtilSys.getLogger().log( ILogger.Level.INFO,
            String.format( "Cleanup completed: %d files deleted, %d files failed, directory %s",
                deletedFiles, failedFiles, backupDir.exists() ? "not deleted" : "deleted" ) );

        return success;
    }

    /**
     * Gets the backup directory.
     *
     * @return Backup directory
     */
    public File getBackupDir()
    {
        return backupDir;
    }

    /**
     * Gets the number of created backups.
     *
     * @return Number of backup files created
     */
    public int getBackupCount()
    {
        return createdBackups.size();
    }
}
