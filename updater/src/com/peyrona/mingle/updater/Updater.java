
package com.peyrona.mingle.updater;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * This class downloads from a remote server those files that are newer (more recent) than local files.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class Updater
{
    private static final String sBASE_URL    = "https://mingle.peyrona.com/";
    private static final File   fLastSuccess = new File( UtilSys.getEtcDir(), "updater.last.success.txt" );
    private static final File   fWorking     = new File( UtilSys.getEtcDir(), "updater.working.txt" );

    //------------------------------------------------------------------------//

    public static synchronized boolean isWorking()
    {
        return fWorking.exists();
    }

    public static LocalDate lastSuccess()
    {
        return fLastSuccess.exists() ? UtilSys.toLocalDate( fLastSuccess.lastModified() )
                                     : null;
    }

    public static synchronized boolean isNeeded()
    {
        if( isWorking() )
            return false;

        LocalDate dLocalFile = lastSuccess();

        if( dLocalFile == null )
            return true;

        if( dLocalFile.until( LocalDate.now(), ChronoUnit.DAYS ) <= 7 )   // Do not access server more than once per week (to save traffic)
            return false;

        try
        {
            long stamp = Long.parseLong( download( Util.sFILE_LAST_NAME ) );    // Can throw IO, NumberFormat, Null... exceptions

            return dLocalFile.isBefore( UtilSys.toLocalDate( stamp ) );
        }
        catch( Exception ex )
        {
            // Nothing to do: this method returns false (this is what is needed)
        }

        return false;  // Flow arrives here when there were an error accessing the server or accessing the file sFILE_LIST_NAME.
    }                  // In both cases, the update process can not be accomplished.

    public static void update()
    {
        update( (sMsg) -> System.out.println( sMsg ),
                (sErr) -> System.err.println( sErr ),
                true );
    }

    /**
     * 1. Download the file that contains the list of all files and their timestamp.
     * 2. Compare  every file in the list with its correspondent local file.
     * 3. Download all files that are newer than local files into a temporal folder.
     * 4. Move     all files from the temporal folder to MSP home.
     *
     * @param onMessage
     * @param onError
     * @param bDryRun
     */
    public static void update( Consumer<String> onMessage, Consumer<String> onError, boolean bDryRun )
    {
        if( isWorking() )
            return;

        JsonArray jaRemote = null;

        setWorking( true );

        if( ! isNeeded() )
            setWorking( false );

        if( onMessage == null )
            onMessage = (str) -> {};

        if( onError == null )
            onError = (str) -> {};

        onMessage.accept( "Downloading files list..." );

        try
        {
            jaRemote = getRemoteFileListContents();
        }
        catch( IOException exc )
        {
            throw new MingleException( "Update aborted because unsatisfied prerequisites.", exc );
        }
        finally
        {
            setWorking( false );
        }

        boolean    bSuccess = true;
        List<File> lstLocal = new ArrayList<>();
        File       fTmpDir  = new File( UtilSys.getTmpDir(), UtilIO.UUFileName() );     // After all files are downloaded without errors, the entire folder is moved to UtilSys.fHome

        try
        {
            UtilIO.mkdirs( fTmpDir );

            onMessage.accept( "Processing files list..." );

            lstLocal = new ArrayList<>( Util.getLocalDistroFiles().values() );

            for( int n = 0; n < jaRemote.size(); n++ )
            {
                JsonObject joRemote = jaRemote.get( n ).asObject();
                File       fLocal   = getLocalTwinFile( joRemote, lstLocal );

                if( (fLocal == null) || isRemoteNewer( joRemote, fLocal ) )    // null == does not exist locally
                {
                    String  sRemote = joRemote.get( Util.sJSON_NAME   ).asString();
                    boolean isDir   = joRemote.get( Util.sJSON_FOLDER ).asBoolean();
                    File    file    = new File( fTmpDir, sRemote );

                    try
                    {
                        if( isDir && ! file.exists() )
                        {
                            onMessage.accept( "Creating folder: "+ file );

                            if( bDryRun )  onMessage.accept( "Dry-run: "+ file );
                            else           UtilIO.mkdirs( file );
                        }
                        else
                        {
                            onMessage.accept( "Downloading file: "+ file );

                            if( bDryRun )  onMessage.accept( "Dry-run: "+ file );
                            else           download( sRemote, file );
                        }
                    }
                    catch( IOException ioe )
                    {
                        bSuccess = false;
                        onError.accept( "Updater error processing: "+ fLocal + UtilStr.toStringBrief( ioe ) );
                    }
                }
            }
        }
        catch( IOException ioe )
        {
            bSuccess = false;
            onError.accept( "Error updating MSP files: "+ ioe.getMessage() );

            try
            {
                if( bDryRun )
                    onMessage.accept( "Removing temporal folder" );

                UtilIO.delete( fTmpDir );
            }
            catch( IOException exc )
            {
                onError.accept( "Error deleting folder: ["+ fTmpDir +"]\nYou should delete it manually.\n"+ UtilStr.toStringBrief( exc ) );
            }
        }

        if( bSuccess )
        {
            onMessage.accept( "Deleting unnecesary local files" );

            // Remaining local files (in lstLocal) do not exist remotely: can be deleted.
            // It is not important if deleting fails: next update there will be another opportunity to delete the files.

            for( File fLocal : lstLocal )
            {
                try
                {
                    onMessage.accept( "Delete "+ (fLocal.isDirectory() ? "folder: " : "file: ") + fLocal );

                    if( ! fLocal.isDirectory() &&                 // Better not to remove dirs: user coud create
                        UtilIO.hasExtension( fLocal, "jar" ) )    // Bettor delete only JARs, user could create many files: props, html, js, css, txt...
                    {
                        if( bDryRun )  onMessage.accept( "Dry-run: "+ fLocal );
                        else           UtilIO.delete( fLocal );
                    }
                }
                catch( IOException ioe )
                {
                    if( bDryRun )
                        onMessage.accept( "Dry-run: "+ ioe.getMessage() );
                 // else --> Nothing to do (not need to inform the user): next update there will be another opportunity to delete the files.
                 // Do not need to set bSuccess to false
                }
            }
        }

        if( bSuccess )
        {
            try
            {
                // Time to move temporal folder to UtilSys.fHomeDir

                onMessage.accept( "Moving temporal folder-tree from ["+ fTmpDir +"]  --->  ["+ UtilSys.fHomeDir +']' );

                UtilIO.move( fTmpDir, UtilSys.fHomeDir );

                // Updates the file that is used as flag

                onMessage.accept( "Updating last successful operation flag file ["+ fLastSuccess +']' );

                UtilIO.newFileWriter()
                      .setFile( fLastSuccess )
                      .replace( "" );           // File is empty: I am only insterested in the time-stamp of this file
            }
            catch( IOException ioe )
            {
                bSuccess = false;

                if( fTmpDir.exists() )
                    onError.accept( "Error deleting folder: "+ fTmpDir +"\nYou should delete it manually." );
            }
            finally
            {
                setWorking( false );
            }
        }

        onMessage.accept( "Update process finished " + (bSuccess ? "successfully" : "with errors") );
    }

    //----------------------------------------------------------------------------//
    // PRIVATE

    private static synchronized void setWorking( boolean start )
    {
        if( start )
        {
            if( ! fWorking.exists() )
            {
                try
                {
                    if( fWorking.createNewFile() )
                        fWorking.deleteOnExit();      // Do not really needed, just to be sure
                }
                catch( IOException ex )
                {
                    throw new MingleException( "Can not create file "+ fWorking );
                }
            }
        }
        else    // stop
        {
            if( ! fWorking.delete() )
                throw new MingleException(  fWorking +": still exists, it has to be removed manually." );
        }
    }

    private static File getLocalTwinFile( JsonObject joRemote, List<File> lstLocal ) throws IOException
    {
        String sRemote = joRemote.get( Util.sJSON_NAME ).asString();

        for( File f: lstLocal )
            if( f.getCanonicalPath().endsWith( sRemote ) )
                return f;

        return null;
    }

    private static boolean isRemoteNewer( JsonObject joRemote, File fLocal )
    {
        long nTime = joRemote.get( Util.sJSON_MODIFIED ).asLong();

        return (nTime > fLocal.lastModified());
    }

    private static JsonArray getRemoteFileListContents() throws IOException
    {
        JsonArray ja = null;

        try
        {
            String sContent = download( Util.sFILE_LIST_NAME ).trim();

            if( sContent.isEmpty() )
                throw new IOException( Util.sFILE_LIST_NAME +": remote file does not exists or is empty" );

            ja = Json.parse( sContent ).asArray();

            if( (ja == null) || ja.isNull() || (! ja.isArray()) )
                throw new IOException( "Malformed JSON file: "+ Util.sFILE_LIST_NAME );
        }
        catch( Exception exc )
        {
            throw new IOException( exc );
        }

        return ja;
    }

    private static String download( String sRemote ) throws IOException, URISyntaxException
    {
        URI uri = new URL( sBASE_URL + sRemote ).toURI();

             if( Util.isBinary( sRemote ) ) return UtilIO.getAsText( uri );
        else if( Util.isASCII(  sRemote ) ) return UtilIO.getAsText( uri );

        throw new IOException( UtilIO.getExtension( sRemote ) +" extension not recognized" );
    }

    private static void download( String sRemote, File fLocalFile ) throws IOException
    {
        URL url = new URL( sBASE_URL + sRemote );

        UtilIO.copy( new BufferedInputStream( url.openStream() ),
                     new FileOutputStream( fLocalFile ) );
    }
}
