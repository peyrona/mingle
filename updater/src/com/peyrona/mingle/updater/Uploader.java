
package com.peyrona.mingle.updater;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

/**
 * This class do following:
 * <ol>
 *     <li>Generates the file containing the file-list.</li>
 *     <li>Uploads files newer than remote ones (including the file-list).</li>
 * </ol>
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class Uploader
{
    // He creado el usuario de FTP "mingle". Al conectarse, la raíz del FTP pasa a ser la carpeta "/public_html/peyrona/mingle"

    private static final String sREMOTE_UPDATER_FOLDER  = "/updater";      // Remote Folder Path for Upload  (single files)
    private static final String sREMOTE_DOWNLOAD_FOLDER = "/downloads";    // Remote Folder Path for Updater (historic MSP ZIP files)

    private static final boolean   bUseDebug = false;
    private static final boolean   isVerbose = true;
    private static       FTPClient ftpClient = null;

    //------------------------------------------------------------------------//

    /**
     * Uploads to server via FTP only new and changed files.<br>
     * If no FTP password is provided, I dry-run will be performed.
     *
     * @param sPwd GFGTP password.
     */
    public static synchronized void upload( String sPwd )
    {
        if( ftpClient != null )
            return;            // upload is already working

        try
        {
            ftpClient = createFtpClient( sPwd );

            createAndUploadSourcesZip();
            createAndUploadMSPZip();
            synchronizeMSP();
            createAndUploadFileList();
        }
        catch( Exception exc )
        {
            exc.printStackTrace( System.err );
        }
        finally
        {
            if( ftpClient != null )
            {
                try
                {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
                catch( IOException i )
                {
                    // Nothing to do
                }
                finally
                {
                    ftpClient = null;
                }
            }
        }
    }

    public static boolean isDryRun()
    {
        return ftpClient == null;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private static void createAndUploadSourcesZip() throws IOException
    {
        System.out.println( "Creating Mingle MSP sources zip file ..." );

        String[] asMod = { "candi", "cil", "controllers", "glue", "gum", "lang", "network", "stick", "tape", "updater" };    // MSP modules
        Zip      zip   = new Zip( "sources" );

        for( String sMod : asMod )
        {
            String sSrcHome = UtilSys.fHomeDir.getParentFile().getAbsolutePath();
            File   fDirSrc  = new File( sSrcHome +"/"+ sMod +"/src/" );

            if( ! fDirSrc.exists() )
                throw new IOException( fDirSrc +" does not exist and it should" );

            zip.add( fDirSrc,
                     (File f) -> { return f.getAbsolutePath().substring( sSrcHome.length() + 1 ); } );   // +1 to remove '/'

            File fDirWeb = new File( sSrcHome +"/"+ sMod + "/web");    // Gum has "web" dir, others does not have it

            if( fDirWeb.exists() )
            {
                zip.add( fDirWeb,
                         (File f) -> { return f.getAbsolutePath().substring( sSrcHome.length() + 1 ); } );   // +1 to remove '/'
            }
        }

        zip.close();

        System.out.println( "Uploading "+ zip.getFile() +" to ftp:/"+ sREMOTE_DOWNLOAD_FOLDER +" ..." );

        if( ! isDryRun() )
        {
            ftpClient.makeDirectory( sREMOTE_DOWNLOAD_FOLDER );    // NO pasa nada si da error: es que no se pudo crear pq ya existe
            uploadFile( sREMOTE_DOWNLOAD_FOLDER, zip.getFile() );
        }
    }

    private static void createAndUploadMSPZip() throws IOException
    {
        System.out.println( "Creating Mingle MSP zip file ..." );

        Zip zip = new Zip( "mingle_standard_platform" );
            zip.add( Util.getLocalDistroFiles().values(),
                     (file) -> { return Util.getFilePathFromHomeDir( file ); } );
            zip.close();

        System.out.println( "Uploading "+ zip.getFile() +" to ftp:/"+ sREMOTE_DOWNLOAD_FOLDER +" ..." );

        if( ! isDryRun() )
        {
            ftpClient.makeDirectory( sREMOTE_DOWNLOAD_FOLDER );    // NO pasa nada si da error: es que no se pudo crear pq ya existe
            uploadFile( sREMOTE_DOWNLOAD_FOLDER, zip.getFile() );
        }
    }

    /**
     * Synchronizes local "toDeploy/*" with remote "/updater/*"
     *
     * @param ftpClient
     * @throws IOException
     */
    private static void synchronizeMSP() throws IOException
    {
        if( ! isDryRun() )
            ftpClient.makeDirectory( sREMOTE_UPDATER_FOLDER );

        System.out.println( "Uploading files to "+ sREMOTE_UPDATER_FOLDER +" ..." );

        Map<String,File> mapLocalFiles = Util.getLocalDistroFiles();

        for( File fLocal : mapLocalFiles.values() )
        {
            if( isDryRun() || isVerbose )
                System.out.println( "Uploaded from ["+ fLocal +"]   --->   ["+ sREMOTE_UPDATER_FOLDER +'/'+ fLocal.getName() +']' );

            if( ! isDryRun() )
                uploadFile( sREMOTE_UPDATER_FOLDER, fLocal );
        }

        System.out.println( "Deleting unneeded files in "+ sREMOTE_UPDATER_FOLDER +" ..." );

        Map<String,FTPFile> mapRemoteFiles = new HashMap<>();

        if( ! isDryRun() )
            mapRemoteFiles = getRemoteFolderFiles( new HashMap<>(), sREMOTE_UPDATER_FOLDER );   // This method is recursive

        for( Map.Entry<String,FTPFile> entry : mapRemoteFiles.entrySet() )
        {
            String sRemotePath = entry.getKey();

            if( ! mapLocalFiles.containsKey( sRemotePath ) )
            {
                if( isDryRun() || isVerbose )
                    System.out.println( "Deleted ("+ (entry.getValue().isDirectory() ? "folder" : "file  ") +") --> "+ sRemotePath );

                if( ! isDryRun() )
                {
                    if( entry.getValue().isDirectory() )
                        deleteRemoteFolder( sRemotePath );
                    else
                        ftpClient.deleteFile( sRemotePath );    // No problem if can not be deleted
                }
            }
        }
    }

    // After all files were uploaded without errors, we can upload the "file list".
    // Generates and uploads a file containing all existing MSP files at the moment on invoking this method.
    private static void createAndUploadFileList() throws IOException
    {
        System.out.println( "Creating and uploading "+ Util.sFILE_LIST_NAME +"..." );

        long      newst = -1;              // Time stamp for the newst file
        JsonArray ja    = new JsonArray();

        for( Map.Entry<String,File> entry : Util.getLocalDistroFiles().entrySet() )
        {
            String sFilePathFromHome = entry.getKey();
            File   file              = entry.getValue();
            long   modifed           = file.lastModified();

            if( newst < modifed )
                newst = modifed;

            JsonObject jo = new JsonObject()
                                .add( Util.sJSON_NAME    , sFilePathFromHome  )
                                .add( Util.sJSON_FOLDER  , file.isDirectory() )
                                .add( Util.sJSON_MODIFIED, modifed );
            ja.add( jo );
        }

        //------------------------------------------------------------------------//

        File fList = new File( UtilSys.getTmpDir(), Util.sFILE_LIST_NAME );
             fList.deleteOnExit();

        UtilIO.newFileWriter()
              .setFile( fList )
              .replace( ja.toString() );

        if( isDryRun() || isVerbose )
            System.out.println( "Created at "+ fList );

        if( ! isDryRun() )
            uploadFile( "/", fList );

        //------------------------------------------------------------------------//

        System.out.println( "Creating and uploading "+ Util.sFILE_LAST_NAME +"..." );

        File fLast = new File( UtilSys.getTmpDir(), Util.sFILE_LAST_NAME );
             fLast.deleteOnExit();

        UtilIO.newFileWriter()
              .setFile( fLast )
              .replace( String.valueOf( newst ) );

        if( isDryRun() || isVerbose )
            System.out.println( "Created at "+ fLast );

        if( ! isDryRun() )
            uploadFile( "/", fLast );
    }

    //------------------------------------------------------------------------//
    // ACCESSORY METHODS: USED BY PRIVATE METHODS IN THIS CLASS

    /**
     * If file is a regular file, it is uploaded if remote does not exists or local
     * is newer than remote: all needed parent folders are also created.<br>
     * <br>
     * If files is a folder, it is created if it does not exists remotely.
     */
    private static void uploadFile( String sRemoteDir, final File fLocal ) throws IOException
    {
        File fLocalDir = (fLocal.isDirectory() ? fLocal : fLocal.getParentFile());

        sRemoteDir = sRemoteDir +
                     (UtilStr.isLastChar( sRemoteDir, '/' ) ? "" : "/") +
                     Util.getFilePathFromHomeDir( fLocalDir ) +
                     (UtilStr.isLastChar( sRemoteDir, '/' ) ? "" : "/");

        // Creates all folders needed to store the file --------------------------------------

        if( ! existFolderInRemote( fLocalDir ) )
        {
            if( ! ftpClient.changeWorkingDirectory( "/" ) )
                throw new IOException( "Error changing to remote root folder" );

            if( isVerbose )
                System.out.print( "Creating folder(s) in remote (if needed) "+ sRemoteDir );

            for( String dir : UtilIO.splitByFolder( sRemoteDir ) )
            {
                if( UtilStr.isNotEmpty( dir ) )
                {
                    ftpClient.makeDirectory( dir );

                    if( ! ftpClient.changeWorkingDirectory( dir ) )    // Needed
                        throw new IOException( "Error creating remote folder "+ dir +" in "+ sRemoteDir );
                }
            }
        }
        else
        {
            if( ! ftpClient.changeWorkingDirectory( sRemoteDir ) )
                throw new IOException( "Error changing to remote folder "+ sRemoteDir );
        }

        // ----------------------------------------------------------------------------------

        if( ! fLocal.isDirectory() )     // If it was a folder, everything is created by now
        {
            if( ! ftpClient.changeWorkingDirectory( sRemoteDir ) )
                throw new IOException( "Can not set default remote folder to: "+ UtilIO.getPath( fLocal ) );

            FTPFile fRemote = getRemoteFile( fLocal );

            if( (fRemote == null) || (fLocal.lastModified() > fRemote.getTimestamp().getTimeInMillis()) )
            {
                     if( Util.isBinary( fLocal ) ) ftpClient.setFileType( FTP.BINARY_FILE_TYPE );
                else if( Util.isASCII(  fLocal ) ) ftpClient.setFileType( FTP.ASCII_FILE_TYPE  );
                else throw new IOException( UtilIO.getExtension( fLocal ) +" extension not recognized" );

                try( FileInputStream inputStream = new FileInputStream( fLocal ) )
                {
                    if( ! ftpClient.storeFile( sRemoteDir + fLocal.getName(), inputStream ) )      // No path needed because we did ftpClient.changeWorkingDirectory(...)
                        throw new IOException( "Error uploading file: "+ fLocal );
                }

                if( isVerbose )
                    System.out.println( "Uploaded file (existed: "+ (fRemote != null) +") --> "+ fLocal );    // If existed and is uploading is because local is file is newer
            }
            else if( isVerbose )
            {
                System.out.println( "Unneeded to upload "+ (fLocal.isDirectory() ? "folder" : "file  ") +" (existed: "+ (fRemote != null) +") --> "+ fLocal );    // If existed and is uploading is because local is file is newer
            }
        }
    }

    private static Map<String,FTPFile> getRemoteFolderFiles( Map<String,FTPFile> mapRemoteFilePaths, String remoteDirPath ) throws IOException
    {
        FTPFile[] files = ftpClient.listFiles( remoteDirPath );

        for( FTPFile file : files )
        {
            if( file.isDirectory() )
            {
                String subDirPath = remoteDirPath + "/" + file.getName();
                String path       = subDirPath.substring( sREMOTE_UPDATER_FOLDER.length() + 1 );

                mapRemoteFilePaths.put( path, file );
                getRemoteFolderFiles( mapRemoteFilePaths, subDirPath );
            }
            else
            {
                String filePath = remoteDirPath + "/" + file.getName();
                String path     = filePath.substring( sREMOTE_UPDATER_FOLDER.length() + 1 );

                mapRemoteFilePaths.put( path, file );
            }
        }

        return mapRemoteFilePaths;
    }

    private static void deleteRemoteFolder( String path ) throws IOException
    {
        FTPFile[] aFiles = ftpClient.listFiles( path );

        if( aFiles != null )
        {
            for( FTPFile ftpFile : aFiles )
            {
                if( ftpFile.isDirectory() )
                {
                    deleteRemoteFolder( path + (UtilStr.isLastChar( path, '/' ) ? "" : "/") + ftpFile.getName() );
                }
                else
                {
                    if( ! UtilStr.endsWith( ftpFile.getName(), ".php" ) )    // endsWith(...) ignores case. Tengo ficheros .php por varios sitios y no se pueden borrar
                    {
                        String sFile = path + (UtilStr.isLastChar( path, '/' ) ? "" : "/") + ftpFile.getName();

                        if( ! ftpClient.deleteFile( sFile ) )
                            System.out.println( "Error deleting remote file: "+ sFile );     // Es mejor no reportar un error
                    }
                }
            }
        }

        if( ! ftpClient.removeDirectory( path ) )
            System.out.println( "Error deleting remote folder: "+ path );     // Es mejor no reportar un error

        // NO poner esto pq este método es recursivo -->
        // System.out.println( "Finalizado el borrado de ficheros en "+ path );
    }

    private static FTPFile getRemoteFile( File fLocal ) throws IOException
    {
        if( ! fLocal.isFile() )
            throw new IOException( fLocal +" is not a local entry of type file" );

        try
        {
            FTPFile[] aFiles = ftpClient.listFiles( sREMOTE_UPDATER_FOLDER +'/'+ Util.getFilePathFromHomeDir( fLocal ) );

            return (UtilColls.isEmpty( aFiles ) ? null : aFiles[0]);
        }
        catch( IOException ioe )
        {
            return null;
        }
    }

    private static boolean existFolderInRemote( File fLocal ) throws IOException
    {
        if( ! fLocal.isDirectory() )
            throw new IOException( fLocal +" is not a local entry of type directory" );

        try
        {
            String  sCurrent = ftpClient.printWorkingDirectory();
            boolean bExist   = ftpClient.changeWorkingDirectory( sREMOTE_UPDATER_FOLDER +'/'+ Util.getFilePathFromHomeDir( fLocal ) );

            ftpClient.changeWorkingDirectory( sCurrent );

            return bExist;
        }
        catch( IOException ioe )
        {
            return false;
        }
    }

    private static FTPClient createFtpClient( String sPwd ) throws IOException
    {
        if( UtilStr.isEmpty( sPwd ) )
            return null;

        FTPClient ftp = new FTPClient();
                  ftp.setConnectTimeout( 9 * 1000 );
                  ftp.enterLocalPassiveMode();
               // ftp.setKeepAlive( true ); --> Si lo uso, da error
                  ftp.connect( "ftp.peyrona.com", 21 );

        int nCode = ftp.getReplyCode();

        if( ! FTPReply.isPositiveCompletion( nCode ) )
            throw new IOException( "Failed to connect to the FTP server. FTP server reply: " + nCode );

        if( ! ftp.login( "mingle", sPwd ) )
            throw new IOException( "Failed to log in to the FTP server." );

        if( bUseDebug )
            ftpClient.addProtocolCommandListener(  new PrintCommandListener( new PrintWriter( System.out ) ) );

        return ftp;
    }
}