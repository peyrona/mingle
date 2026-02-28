
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.OneToMany;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class ServiceBoard extends ServiceBase
{
    private static final    String sMarkStart = "// START MARK TO INSERT getConfig()";
    private static final    String sMarkEnd   = "// END MARK TO INSERT getConfig()";

    private static volatile String                   sMasterPwd        = null;
    private static final    OneToMany<String,String> mapPwd2Boards     = new OneToMany<>();      // key=Pwd, value=Boards[]. Internally, a 'OneToMany' is a: Map<K,List<V>>
    private static final    HmacAuthenticator        hmacAuthenticator = new HmacAuthenticator();

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    ServiceBoard( HttpServletRequest request, HttpServletResponse response )
    {
        super( request, response );
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    @Override
    protected void doGet() throws IOException
    {
        if( ! validateRequest() )
            return;

        if( mapPwd2Boards.isEmpty() )
        {
            synchronized( mapPwd2Boards )
            {
                if( mapPwd2Boards.isEmpty() )
                    initBoardsMap();
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
                    for( List<String> lst : mapPwd2Boards.values() )
                        lstBoards.addAll( lst );
                }
                else if( mapPwd2Boards.containsKey( sPwd ) )
                {
                    lstBoards.addAll( mapPwd2Boards.get( sPwd ) );     // Boards for passed password

                    if( UtilStr.isNotEmpty( sPwd ) )
                        lstBoards.addAll( mapPwd2Boards.get( "" ) );   // Add also boards with no password
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
        if( ! validateRequest() )
            return;

        asJSON( (JsonObject jo) ->
                {
                    UtilJson   uj = new UtilJson( jo );
                    String     sPwdOld    = uj.getString( "pwd_old",    null );
                    String     sPwdNew    = uj.getString( "pwd_new",    null );

                    String     sSecretOld = uj.getString( "secret_old", null );  // To change HMAC shared secret
                    String     sSecretNew = uj.getString( "secret_new", null );
                    String     sMasterPwd = uj.getString( "master_pwd", null );  // Required to authorize a secret change

                    String     sName    = uj.getString( "name", null );      // file name comes already not empty, normalized and != "_template_"
                    JsonObject oRest    = uj.getObject( "rest", null );      // Everything else (board contents)

                    String     sCurrent = uj.getString( "source", null );    // To clone
                    String     sTarget  = uj.getString( "target", null );

                    String     sNameOld = uj.getString( "name_old", null );  // To rename
                    String     sNameNew = uj.getString( "name_new", null );

                    try
                    {
                             if( sPwdOld    != null && sPwdNew    != null )  changeMasterPwd(    sPwdOld,    sPwdNew    );
                        else if( sSecretOld != null && sSecretNew != null )  changeSharedSecret( sMasterPwd, sSecretOld, sSecretNew );
                        else if( sName      != null && oRest      != null )  writeContents( sName, oRest );
                        else if( sCurrent   != null && sTarget    != null )  cloneBoard(  sCurrent, sTarget  );
                        else if( sNameOld   != null && sNameNew   != null )  renameBoard( sNameOld, sNameNew );
                        else                                                 sendError("Invalid parameters", HttpServletResponse.SC_BAD_REQUEST );
                    }
                    catch( IOException exc )
                    {
                        sendError( exc );
                    }
                } );
    }

    @Override
    protected void doPut() throws IOException    // NEW  (used to create a new dashboard)
    {
        if( ! validateRequest() )
            return;

        String sBoardName  = asString( "name"   );
        String sLayoutType = asString( "layout" );
        File   fBoardDir   = getBoardFolder( sBoardName );      // The folder name is the same as board name (".html" is not part of the name)
        File   fBoardImgs  = new File( fBoardDir, "images" );   // The folder inside the '[home]/etc/gum_user_files/dashboards' folder containing the images for this dashboard
        String sErrMsg     = "";

        create( fBoardDir );     // The folder inside the '[home]/etc/gum_user_files/dashboards' folder

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

                File fBoard  = getBoardFile( sBoardName );                        // Board name is the folder's name
                File fTempla = new File( Util.getAppDir(), "_template_.html" );   // "_template_.html" file is one dir above boards folder

                Files.copy( fTempla.toPath(), fBoard.toPath(), StandardCopyOption.REPLACE_EXISTING );

                JsonObject jo = Json.parse( "{\"background\": null, \"exens\": [], \"layout\": {\"type\": \""+ sLayoutType +"\", \"contents\": null}}" ).asObject();

                writeContents( sBoardName, jo );
                mapPwd2Boards.put( "", fBoard.getName() );   // "" -> Empty pwd
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
        if( ! validateRequest() )
            return;

        String name = asString( "name" );

        UtilIO.delete( getBoardFolder( name ) );   // Deletes the folder (containing 'board.html' and any other files/folders (like the images folder)

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
        if( sMasterPwd == null )
            sMasterPwd = UtilSys.getConfig().get( "monitoring", "master_password", "" );

        return sMasterPwd.equals( (pwd == null ? "" : pwd) ) || isFromLocalhost();
    }

    private boolean validateRequest() throws IOException
    {
        String result = hmacAuthenticator.validateRequest( request );

        if( result != null )    // null == valid
            sendError( result, HttpServletResponse.SC_UNAUTHORIZED );

        return result == null;
    }

    private static void initBoardsMap() throws IOException
    {
        List<File> list = UtilIO.listFilesInTree( Util.getBoardsDir(),
                                                  (File f) ->
                                                  {
                                                      return UtilStr.endsWith( f.getName(), ".html" ) &&
                                                             f.getParentFile().getName().equals( UtilIO.getName( f ) );
                                                  } );

        for( File fBoard : list )
        {
            try
            {
                mapPwd2Boards.put( getPassword( fBoard ), fBoard.getName() );
            }
            catch( Exception ex )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING, "Failed to read password from dashboard: " + fBoard.getName() + " — " + ex.getMessage() );
                mapPwd2Boards.put( "", fBoard.getName() );    // Add it with empty password so it still appears in the list
            }
        }
    }

    private void writeContents( String sBoardName, JsonObject oContent ) throws IOException
    {
        File   fBoard = getBoardFile( sBoardName );
        String sBoard = UtilIO.getAsText( fBoard );
        int    nIdx   = sBoard.indexOf( sMarkStart );
        int    nEnd   = sBoard.indexOf( sMarkEnd   );

        if( nIdx == -1 || nEnd == -1 )
            throw new IOException( "Config markers not found in template file" );

        int nStart = nIdx + sMarkStart.length();

        String sReplace = '\n'+
                          "            function getConfig()\n"+
                          "            {\n"+
                          "                return "+ oContent +";\n"+
                          "            }\n"+
                          "           ";    // These spaces are needed to align the // END OF ...

        sBoard = sBoard.substring( 0, nStart ) + sReplace + sBoard.substring( nEnd );

        // Write directly to avoid UtilIO.FileWriter.setFile() overload.
        Files.write( fBoard.toPath(), sBoard.getBytes( StandardCharsets.UTF_8 ) );

        // Every time a dashboard is saved, its password could be changed: it is needed
        // to check if this was the case, and if so, to update the map.
        String sName   = fBoard.getName();
        String sKey    = mapPwd2Boards.getKeyForValue( sName );
        String sNewPwd = oContent.getString( "password", null );

        if( ! Objects.equals( sKey, sNewPwd ) )    // Most part of the time, this 'if' resolves to false
            mapPwd2Boards.moveValue( sKey, sNewPwd, sName );
    }

    private static File getBoardFolder( String name ) throws IOException
    {
        name = Util.sanitizeFileName( name );

        if( UtilStr.endsWith( name, ".html" ) )
            name = UtilStr.removeLast( name, 5 );

        return new File( Util.getBoardsDir(), name );   // Board folder inside 'dashboards' folder
    }

    private static File getBoardFile( String name ) throws IOException
    {
        name = Util.sanitizeFileName( name );

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

    private void changeMasterPwd( String sPwdOld, String sPwdNew ) throws IOException
    {
        if( Objects.equals( sPwdOld, sPwdNew ) )
            return;

        if( isMasterPwd( sPwdOld ) )
        {
            File fConfig = new File( UtilSys.getConfig().getURI() );

            if( fConfig.exists() )
            {
                String sContent = UtilIO.getAsText( fConfig );
                String sEscaped = sPwdNew.replace( "\\", "\\\\" ).replace( "\"", "\\\"" );   // Escape any backslashes and quotes in the new password for JSON embedding
                String sUpdated = sContent.replaceFirst( "(\"master_password\"\\s*:\\s*)(?:\"[^\"]*\"|null)", "$1\"" + sEscaped + "\"" );

                if( sUpdated.equals( sContent ) )
                {
                    sendError( "'master_password' key not found in config file:\n" + fConfig.getPath(), HttpServletResponse.SC_EXPECTATION_FAILED );
                    return;
                }

                UtilIO.newFileWriter()
                      .setFile( fConfig )
                      .replace( sUpdated );

                sMasterPwd = sPwdNew;
            }
            else
            {
                sendError( "Config file not found or not accessible:\n" + fConfig.getPath(),
                           HttpServletResponse.SC_EXPECTATION_FAILED );
            }
        }
        else
        {
            sendError( "Invalid old master password.", HttpServletResponse.SC_UNAUTHORIZED );
        }
    }

    /**
     * Updates the HMAC shared secret in config.json.
     * <p>
     * Only the master user (or localhost) may invoke this. The caller must supply both the current
     * master password ({@code sMasterPwd}) and the current secret ({@code sSecretOld}) to prevent
     * unauthorized changes. Because {@link HmacAuthenticator} reads the secret once at startup,
     * the new value takes effect only after the server is restarted.
     *
     * @param sMasterPwd current master password (used to verify caller is master)
     * @param sSecretOld current shared secret (empty string when HMAC is disabled)
     * @param sSecretNew new shared secret (empty string to disable HMAC)
     * @throws IOException if the config file cannot be read or written
     */
    private void changeSharedSecret( String sMasterPwd, String sSecretOld, String sSecretNew ) throws IOException
    {
        if( ! isFromLocalhost() && ! isFromIntranet() )
        {
            sendError( "Changing the shared secret is only allowed from localhost or the local network (192.168.*.*).",
                       HttpServletResponse.SC_FORBIDDEN );
            return;
        }

        if( Objects.equals( sSecretOld, sSecretNew ) )
            return;

        if( ! isMasterPwd( sMasterPwd ) )
        {
            sendError( "Master access required to change shared secret.", HttpServletResponse.SC_UNAUTHORIZED );
            return;
        }

        File   fConfig   = new File( UtilSys.getConfig().getURI() );
        String sContent  = UtilIO.getAsText( fConfig );

        // Extract the current value so we can validate sSecretOld
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile( "\"shared_secret\"\\s*:\\s*(\"[^\"]*\"|null)" )
                .matcher( sContent );

        if( ! m.find() )
        {
            sendError( "'shared_secret' key not found in config file:\n" + fConfig.getPath(),
                       HttpServletResponse.SC_EXPECTATION_FAILED );
            return;
        }

        String sCurrentRaw = m.group( 1 );   // e.g. "mysecret"  or  null
        String sCurrent    = sCurrentRaw.equals( "null" ) ? "" : sCurrentRaw.substring( 1, sCurrentRaw.length() - 1 );

        if( ! sCurrent.equals( sSecretOld == null ? "" : sSecretOld ) )
        {
            sendError( "Invalid current shared secret.", HttpServletResponse.SC_UNAUTHORIZED );
            return;
        }

        String sReplacement = UtilStr.isEmpty( sSecretNew ) ? "null"
                                                            : "\"" + sSecretNew.replace( "\\", "\\\\" ).replace( "\"", "\\\"" ) + "\"";

        String sUpdated = sContent.replaceFirst( "(\"shared_secret\"\\s*:\\s*)(?:\"[^\"]*\"|null)",
                                                 "$1" + sReplacement );

        UtilIO.newFileWriter()
              .setFile( fConfig )
              .replace( sUpdated );
    }

    private void cloneBoard( String source, String target ) throws IOException
    {
        File fDirSource = getBoardFolder( source );
        File fDirTarget = getBoardFolder( target );

        create( fDirTarget );

        File fileSource = getBoardFile( source );
        File fileTarget = getBoardFile( target );

        UtilIO.copy( fDirSource, fDirTarget );

        Files.move( new File( fDirTarget, fileSource.getName() ).toPath(),
                    new File( fDirTarget, fileTarget.getName() ).toPath(),
                    StandardCopyOption.REPLACE_EXISTING );

        mapPwd2Boards.put( mapPwd2Boards.getKeyForValue( fileSource.getName() ),
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

        mapPwd2Boards.put( sKey, sNameNew +".html" );
    }

    private String removeFileFromList( String sFileName ) throws IOException
    {
        sFileName = Util.sanitizeFileName( sFileName );

        String sKey = mapPwd2Boards.getKeyForValue( sFileName );

        if( sKey == null )
            throw new IOException( "Key for value "+ sFileName +" is null" );

        mapPwd2Boards.remove( sKey, sFileName );

        return sKey;
    }

    // It is done also at client side, but must be done here too (more than one user could invoke this ta the same time)
    private synchronized void create( File file ) throws IOException
    {
        if( file.exists() )
            throw new IOException( '"'+ file.getName() +"\"\n already exists" );

        if( ! UtilIO.mkdirs( file ) )
            throw new IOException( "Error creating: "+ file );
    }

}