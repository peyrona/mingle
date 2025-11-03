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
        try
        {
            this.backupDir = Files.createTempDirectory( "filehashchecker_backups_" ).toFile();
            UtilSys.getLogger().log( ILogger.Level.INFO, "Backup directory: " + backupDir.getAbsolutePath() );
        }
        catch( IOException e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, e, "Failed to create backup directory" );
            throw new RuntimeException( "Failed to create backup directory", e );
        }
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
    
            if( backupDir != null && backupDir.exists() && backupDir.isDirectory() )
            {
                // Delete all files in the backup directory
                File[] files = backupDir.listFiles();
                if( files != null )
                {
                    for( File file : files )
                    {
                        if( file.delete() )
                        {
                            UtilSys.getLogger().log( ILogger.Level.INFO, "Deleted backup file: " + file.getAbsolutePath() );
                        }
                        else
                        {
                            UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to delete backup file: " + file.getAbsolutePath() );
                            success = false;
                        }
                    }
                }
    
                // Now, try to delete the directory itself
                if( backupDir.delete() )
                {
                    UtilSys.getLogger().log( ILogger.Level.INFO, "Deleted backup directory: " + backupDir.getAbsolutePath() );
                }
                else
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to delete backup directory (it might not be empty): " + backupDir.getAbsolutePath() );
                    success = false;
                }
            }
    
            createdBackups.clear();
    
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
