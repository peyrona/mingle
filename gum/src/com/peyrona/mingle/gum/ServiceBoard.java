
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.japi.OneToMany;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ServiceBoard class extends ServiceBase to provide operations related to boards
 * through HTTP requests. This class handles GET, POST, PUT, and DELETE requests
 * to manage board files.
 *
 * <p>
 * HTTP methods and parameters:
 * <ul>
 * <li>GET: Lists all board files.
 * <li>POST: Updates a specific board file with new configuration.
 *   <ul>
 *   <li>name: Name of the board file (required)
 *   <li>rest: New configuration data to be inserted (required)
 *   </ul>
 * <li>PUT: Creates a new board directory and copies the common images to it.
 *   <ul>
 *   <li>name: Name of the new board directory (required)
 *   </ul>
 * <li>DELETE: Deletes a specific board directory.
 *   <ul>
 *   <li>name: Name of the board directory to delete (required)
 *   </ul>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class ServiceBoard extends ServiceBase
{
    private static final String sMarkStart = "// START MARK TO INSERT getConfig()";
    private static final String sMarkEnd   = "// END MARK TO INSERT getConfig()";
                                // Pwd   , Boards
    private static final OneToMany<String,String> mapPwdBoard = new OneToMany<>();    // Internally, a 'OneToMany' this is a: Map<K,List<V>>

    private static       String sMasterPwd = null;    // There is only one for all users

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    ServiceBoard( HttpServerExchange xchg )
    {
        super( xchg );
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    @Override
    protected void doGet() throws IOException
    {
        if( mapPwdBoard.isEmpty() )
        {
            synchronized( mapPwdBoard )
            {
                if( mapPwdBoard.isEmpty() )
                    initMap();
            }
        }

        String sAction = asString( "action"   );
        String sPwd    = asString( "password" );

        switch( sAction )
        {
            case "list":     // Returns all boards that can be managed by received password
                List<String> lstBoards = new ArrayList<>( 64 );

                if( isMasterPwd( sPwd ) )
                {
                    for( List<String> lst : mapPwdBoard.values() )
                        lstBoards.addAll( lst );
                }
                else if( mapPwdBoard.containsKey( sPwd ) )
                {
                    lstBoards.addAll( mapPwdBoard.get( sPwd ) );     // Boards for passed password

                    if( UtilStr.isNotEmpty( sPwd ) )
                        lstBoards.addAll( mapPwdBoard.get( "" ) );   // Add also boards with no password
                }

                sendJSON( UtilJson.toJSON( lstBoards.toArray( String[]::new ) ).toString() );

                break;

            case "check":    // Returns "true" if received password is master password
                sendText( (isMasterPwd( sPwd ) ? "true" : "false") );
                break;
        }
    }

    @Override
    protected void doPost() throws IOException    // UPDATE  (used to update master password and also to update dashboard contents)
    {
        asJSON( (JsonObject jo) ->
                {
                    UtilJson   uj = new UtilJson( jo );
                    String     sPwdOld  = uj.getString( "pwd_old", null );
                    String     sPwdNew  = uj.getString( "pwd_new", null );

                    String     sName    = uj.getString( "name", null );      // file name comes already not empty, normalized and != "_template_"
                    JsonObject oRest    = uj.getObject( "rest", null );      // Everything else (board contents)

                    String     sCurrent = uj.getString( "source", null );    // To clone
                    String     sTarget  = uj.getString( "target", null );

                    String     sNameOld = uj.getString( "name_old", null );  // To rename
                    String     sNameNew = uj.getString( "name_new", null );

                    try
                    {
                             if( sPwdOld  != null && sPwdNew  != null )  changeMasterPwd( sPwdOld, sPwdNew );
                        else if( sName    != null && oRest    != null )  writeContents( getBoardFile( sName ), oRest );
                        else if( sCurrent != null && sTarget  != null )  cloneBoard(  sCurrent, sTarget  );
                        else if( sNameOld != null && sNameNew != null )  renameBoard( sNameOld, sNameNew );
                        else                                             sendError("Invalid parameters", StatusCodes.BAD_REQUEST );
                    }
                    catch( IOException ioe )
                    {
                        sendError( ioe );
                    }
                } );
    }

    @Override
    protected void doPut() throws IOException    // NEW  (used to create a new dashboard)
    {
        String sBoardName  = asString( "name"   );
        String sLayoutType = asString( "layout" );
        File   fBoardDir   = new File( Util.getBoardsDir(), sBoardName );    // The folder name is the same as board name (".html" is not part of the name)
        File   fBoardImgs  = new File( fBoardDir, "images" );     // The folder inside the '[home]/etc/gum_user_files/dashboards' folder containing the images for this dashboard
        String sErrMsg     = "";

        createIfNotExist( fBoardDir );     // The folder inside the '[home]/etc/gum_user_files/dashboards' folder

        if( ! UtilIO.mkdirs( fBoardImgs ) )
        {
            sErrMsg = "Can not create '"+ fBoardDir +'\'';
        }
        else
        {
            File fCommonImages = new File( Util.getAppDir(), "images/common" );

            try
            {
                UtilIO.copy( fCommonImages, fBoardImgs );

                File fBoard  = new File( fBoardDir, sBoardName +".html" );     // Board name is the folder's name
                File fTempla = new File( Util.getAppDir(), "_template_.html" );   // "_template_.html" file is one dir above boards folder

                Files.copy( fTempla.toPath(), fBoard.toPath(), StandardCopyOption.REPLACE_EXISTING );

                JsonObject jo = Json.parse( "{\"background\": null, \"exens\": [], \"layout\": {\"type\": \""+ sLayoutType +"\", \"contents\": null}}" ).asObject();

                writeContents( fBoard, jo );
                mapPwdBoard.put( "", fBoard.getName() );
            }
            catch( IOException ioe )
            {
                sErrMsg = ioe.getMessage();
            }
        }

        if( ! sErrMsg.isEmpty() )
        {
            sendError( new IOException( sErrMsg ) );
            UtilIO.delete( fBoardDir );
        }
    }

    @Override
    protected void doDelete() throws IOException   // Client takes care that only users with master password can invoke this
    {
        String name = asString( "name" );

        UtilIO.delete( getBoardFolder( name ) );   // Deletes the folder (cotaining 'board.html' and any other files/folders (like the images folder)

        removeFileFromList( name );                // After file was properly deleted, we can remove the board from the Map
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE
    // AUXILIARY METHODS

    /**
     * Returns true if passed is master pwd (or the file does not exists: master pwd was not set yet).
     *
     * @param pwd
     * @return true if passed is master pwd.
     * @throws IOException
     */
    private boolean isMasterPwd( String pwd ) throws IOException
    {
        if( (sMasterPwd == null) && getFile4MasterPwd().exists() )
            sMasterPwd = UtilIO.getAsText( getFile4MasterPwd() );

        return sMasterPwd == null || sMasterPwd.equals( pwd ) || isFromLocalhost();
    }

    private static void initMap() throws IOException
    {
        List<File> list = UtilIO.listFilesInTree( Util.getBoardsDir(),
                                                  (File f) ->
                                                  {
                                                      return "html".equalsIgnoreCase( UtilIO.getExtension( f ) ) &&
                                                             f.getParentFile().getName().equals( UtilIO.getName( f ) );
                                                  } );

        for( File fBoard : list )
            mapPwdBoard.put( getPassword( fBoard ), fBoard.getName() );
    }

    private void writeContents( File fBoard, JsonObject oContent ) throws IOException
    {
        String sBoard   = UtilIO.getAsText( fBoard );
        int    nStart   = sBoard.indexOf( sMarkStart ) + sMarkStart.length();
        int    nEnd     = sBoard.indexOf( sMarkEnd   );
        String sReplace = '\n'+
                          "            function getConfig()\n"+
                          "            {\n"+
                          "                return "+ oContent +";\n"+
                          "            }\n"+
                          "           ";    // These spaces are needed to align the // END OF ...

        sBoard = sBoard.substring( 0, nStart ) + sReplace + sBoard.substring( nEnd );

        UtilIO.newFileWriter()
              .setFile( fBoard )
              .replace( sBoard );

        // Every time a dashboard is saved, its password could be changed: it is needed
        // to check if this was the case, and if so, to update the map.

        String sName   = fBoard.getName();
        String sKey    = mapPwdBoard.getKeyForValue( sName );
        String sNewPwd = oContent.getString( "password", null );

        if( ! Objects.equals( sKey, sNewPwd ) )    // Most part of the time, this 'if' resolves to false
        {
            if( sKey != null )
                mapPwdBoard.remove( sKey, sName );

            mapPwdBoard.put( sNewPwd, sName );
        }
    }

    private static File getBoardFolder( String name ) throws IOException
    {
        if( UtilStr.endsWith( name, ".html" ) )
            name = UtilStr.removeLast( name, 5 );

        return new File( Util.getBoardsDir(), name );   // Board folder inside 'dashboards' folder
    }

    private static File getBoardFile( String name ) throws IOException
    {
        return new File( getBoardFolder( name ),
                         UtilIO.addExtension( name, ".html" ) );
    }

    private static String getPassword( File fBoard ) throws IOException
    {
        String sBoard = UtilIO.getAsText( fBoard );
        int    nStart = sBoard.indexOf( sMarkStart ) + sMarkStart.length();
        int    nEnd   = sBoard.indexOf( sMarkEnd   );
        String sFunc  = sBoard.substring( nStart, nEnd );    // function{ return {......}; }

        sFunc = sFunc.substring( sFunc.indexOf( "return" ) + "return".length() );
        sFunc = sFunc.substring( 0, sFunc.lastIndexOf( ';' ) ).trim();

        return new UtilJson( sFunc ).getString( "password", "" );
    }

    private static File getFile4MasterPwd() throws IOException
    {
        return new File( Util.getBoardsDir(), "master_password.txt" );
    }

    private void changeMasterPwd( String sPwdOld, String sPwdNew ) throws IOException
    {
        if( isMasterPwd( sPwdOld ) )
        {
            UtilIO.newFileWriter()
                  .setFile( getFile4MasterPwd() )
                  .replace( sPwdNew );
        }

        sMasterPwd = sPwdNew;    // After successfully saved the file
    }

    private void cloneBoard( String source, String target ) throws IOException
    {
        File fDirSource = getBoardFolder( source );
        File fDirTarget = getBoardFolder( target );

        createIfNotExist( fDirTarget );

        File fileSource = getBoardFile( source );
        File fileTarget = getBoardFile( target );

        UtilIO.copy( fDirSource, fDirTarget );

        Files.move( new File( fDirTarget, fileSource.getName() ).toPath(),
                    new File( fDirTarget, fileTarget.getName() ).toPath(),
                    StandardCopyOption.REPLACE_EXISTING );

        mapPwdBoard.put( mapPwdBoard.getKeyForValue( fileSource.getName() ),
                         fileTarget.getName() );
    }

    /**
     *
     * @param sNameOld With or without '.html' extension
     * @param sNameNew With or without '.html' extension
     * @throws IOException
     */
    private void renameBoard( String sNameOld, String sNameNew ) throws IOException
    {
        sNameOld = UtilStr.endsWith( sNameOld, ".html" ) ? UtilStr.removeLast( sNameOld, 5 ) : sNameOld;
        sNameNew = UtilStr.endsWith( sNameNew, ".html" ) ? UtilStr.removeLast( sNameNew, 5 ) : sNameNew;

        File fBoardOld  = getBoardFile( sNameOld );
        File fBoardNew  = new File( fBoardOld.getParentFile(), sNameNew +".html" );   // Same path as old but with new name
        File fFolderOld = fBoardOld.getParentFile();
        File fFolderNew = new File( fFolderOld.getParent(), sNameNew );

        if( fFolderNew.exists() )
            throw new IOException( '"'+ fFolderNew.getName() +"\"\n already exists" );

        Files.move( fBoardOld.toPath() , fBoardNew.toPath() , StandardCopyOption.REPLACE_EXISTING );
        Files.move( fFolderOld.toPath(), fFolderNew.toPath(), StandardCopyOption.REPLACE_EXISTING );

        String sKey = removeFileFromList( fBoardOld.getName() );

        mapPwdBoard.put( sKey, sNameNew +".html" );
    }

    private String removeFileFromList( String sFileName ) throws IOException
    {
        String sKey = mapPwdBoard.getKeyForValue( sFileName );

        if( sKey == null )
            throw new IOException( "Key for value "+ sFileName +" is null" );

        mapPwdBoard.remove( sKey, sFileName );

        return sKey;
    }

    // It is done also at client side, but must be done here too (more than one user could invoke this ta the same time)
    private synchronized void createIfNotExist( File file ) throws IOException
    {
        if( file.exists() )
            throw new IOException( '"'+ file.getName() +"\"\n already exists" );

        if( ! UtilIO.mkdirs( file ) )
            throw new IOException( "Error creating: "+ file );
    }
}