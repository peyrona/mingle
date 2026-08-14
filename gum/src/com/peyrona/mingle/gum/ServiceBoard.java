
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
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
import java.util.concurrent.ConcurrentHashMap;
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
    // NOTE: API authentication (IP scope / remote-access grant / HMAC) is enforced centrally by
    //       HttpServer.AuthenticationFilter before any request reaches this service.

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
        ensureBoardsMapLoaded();

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

            case "public_url":   // Returns the base URL Gum is reached by from outside (may be empty);
                                 // used by the dashboard manager to build a viewer link that works from
                                 // wherever it is opened, not just from the host the admin happens to be on
                sendText( RemoteGrant.get().publicUrl() );
                break;
        }
    }

    @Override
    protected void doPost() throws IOException    // UPDATE  (used to update master password and also to update dashboard contents)
    {
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
        String name = asString( "name" );

        UtilIO.delete( getBoardFolder( name ) );   // Deletes the folder (containing 'board.html' and any other files/folders (like the images folder)

        removeFileFromList( name );                // After file was properly deleted, we can remove the board from the Map
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE
    // Used by RemoteGrant / HttpServer.ServletLogin to resolve and confine board-viewer grants — see
    // gum/src/com/peyrona/mingle/gum/RemoteGrant.java's "Viewer" role.

    /**
     * The dashboards a given password unlocks, normalized to a bare board name (no {@code ".html"}
     * extension) — the same form {@link RemoteGrant} path-matches against
     * ({@code /gum/board/<name>/...}).
     * <p>
     * An empty password IS looked up by the caller ({@code /gum/login?board=}): a board left without
     * a password is, by the admin's own choice when leaving it blank in the dashboard editor, meant
     * to be open to whoever asks — the same access anyone on the LAN or holding an admin/HMAC session
     * already has via {@link #doGet}'s "list" action, which has always treated {@code ""} as a real
     * key. This method itself does not special-case an empty password; it simply returns whatever
     * boards match it.
     *
     * @param pwd the submitted password.
     * @return the board names that password unlocks; empty when none match.
     * @throws IOException if the boards map has not been loaded yet and cannot be read.
     */
    static String[] boardsForPassword( String pwd ) throws IOException
    {
        ensureBoardsMapLoaded();

        // mapPwd2Boards is a ConcurrentHashMap under the hood, which throws on a null key rather than
        // just missing it — and unlike ServiceBase.asString(name), a JSON body's "password" key can be
        // absent entirely (not just empty), so null is a real input here, not a theoretical one.
        List<String> matches = mapPwd2Boards.get( pwd == null ? "" : pwd );

        if( (matches == null) || matches.isEmpty() )
            return new String[0];

        String[] result = new String[ matches.size() ];

        for( int n = 0; n < matches.size(); n++ )
            result[n] = stripHtmlExtension( matches.get( n ) );

        return result;
    }

    /**
     * The {@code "host:port"} of every ExEn a dashboard's own configuration references — the same
     * format {@link CommBridge} normalizes an incoming bridge message's target to, so the two can be
     * compared directly. Used by {@link RemoteGrant#mayReach} to confine a board-scoped viewer grant to
     * exactly the ExEns that dashboard actually talks to.
     *
     * @param boardName the dashboard, with or without the ".html" extension.
     * @return the "host:port" (or bare "host", if a board's exen has no port) tags found in its
     *         "exens" array; never {@code null}.
     * @throws IOException if the dashboard file cannot be read.
     */
    static String[] boardExens( String boardName ) throws IOException
    {
        JsonArray    jaExens = cachedBoardConfig( boardName ).getArray( "exens", Json.array() );
        List<String> list    = new ArrayList<>( jaExens.size() );

        for( JsonValue jv : jaExens )
        {
            if( ! jv.isObject() )
                continue;

            JsonObject joExen = jv.asObject();
            String     sHost  = joExen.getString( "host", null );
            int        nPort  = joExen.getInt( "port", -1 );

            if( UtilStr.isNotEmpty( sHost ) )
                list.add( (nPort > 0) ? (sHost +':'+ nPort) : sHost );
        }

        return list.toArray( new String[0] );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE
    // AUXILIARY METHODS

    /**
     * Returns true if passed is master pwd (or the file does not exists: master pwd was not set yet).
     * <p>
     * Also true, without any password at all, for a request already carrying a valid RemoteGrant
     * ADMIN cookie: minting that cookie ({@code /gum/login}) already required knowing the master
     * password once, so asking for it a second time here — via the dashboard manager's own "Password
     * for dashboards" field — would be pure friction, not an extra security boundary. A GUEST or
     * VIEWER grant, or plain on-LAN trust with no grant at all, does NOT qualify: being reachable on
     * the network was never proof of knowing the master password, which is exactly why this per-board
     * credential exists in the first place.
     *
     * @param pwd
     * @return true if passed is master pwd.
     * @throws IOException
     */
    private boolean isMasterPwd( String pwd ) throws IOException
    {
        if( sMasterPwd == null )
            sMasterPwd = UtilSys.getConfig().get( "monitoring", "master_password", "" );

        if( sMasterPwd.equals( (pwd == null ? "" : pwd) ) || isFromLocalhost() )
            return true;

        RemoteGrant.Grant grant = RemoteGrant.get().admit( AccessContext.of( request, response ) );

        return (grant != null) && grant.isAdmin();
    }

    /**
     * Loads {@link #mapPwd2Boards} on first use — shared by {@link #doGet} and by
     * {@link #boardsForPassword}, which may be the very first entry point to touch it when a viewer
     * signs in at {@code /gum/login?board=} before anyone has ever opened the dashboard manager.
     */
    private static void ensureBoardsMapLoaded() throws IOException
    {
        if( mapPwd2Boards.isEmpty() )
        {
            synchronized( mapPwd2Boards )
            {
                if( mapPwd2Boards.isEmpty() )
                    initBoardsMap();
            }
        }
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

        // JSON escaping does not cover '<', so a string value containing "</script>" would close the
        // block this is spliced into and let the rest run as markup on Gum's own origin (where the
        // session and the access grant live). Escaping the slash is invalid nowhere: inside a JSON
        // string "\/" is just "/", so the value the dashboard reads back is unchanged.
        String sConfig = oContent.toString().replace( "</", "<\\/" );

        String sReplace = '\n'+
                          "            function getConfig()\n"+
                          "            {\n"+
                          "                return "+ sConfig +";\n"+
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

    /**
     * Sanitizes a caller-supplied board name and strips a trailing ".html" if present, so any caller
     * can pass either form and always land on the same canonical name. Shared by {@link #getBoardFolder}
     * and {@link #getBoardFile} (each normalizes exactly once now, instead of {@link #getBoardFile}
     * re-sanitizing the already-sanitized name a second time via the old {@code getBoardFolder} call),
     * and used as the cache key in {@link #cachedBoardConfig}.
     */
    private static String normalizeBoardName( String name ) throws IOException
    {
        name = Util.sanitizeFileName( name );

        return UtilStr.endsWith( name, ".html" ) ? UtilStr.removeLast( name, 5 ) : name;
    }

    private static File getBoardFolder( String name ) throws IOException
    {
        return new File( Util.getBoardsDir(), normalizeBoardName( name ) );   // Board folder inside 'dashboards' folder
    }

    private static File getBoardFile( String name ) throws IOException
    {
        String sName = normalizeBoardName( name );

        return new File( new File( Util.getBoardsDir(), sName ),
                         UtilIO.addExtension( sName, ".html" ) );
    }

    private static String getPassword( File fBoard ) throws IOException
    {
        return getBoardConfig( fBoard ).getString( "password", "" );
    }

    /**
     * Per-board cache: the resolved {@link File}, built once per board name, plus the config last
     * parsed from it and the file's mtime at that time. Backs {@link #cachedBoardConfig}, which
     * {@link #boardExens} uses — a check {@link RemoteGrant#mayReach} runs on every inbound WebSocket
     * bridge message from a viewer session, so re-resolving the path and re-reading/re-parsing the file
     * on every single message would be wasted work under a busy session.
     */
    private static final ConcurrentHashMap<String, BoardEntry> boardCache = new ConcurrentHashMap<>();

    private static final class BoardEntry
    {
        final    File     file;
        volatile long     mtime = -1;   // -1: never parsed yet
        volatile UtilJson config;

        BoardEntry( File file ) { this.file = file; }
    }

    /**
     * Same as {@link #getBoardConfig(File)}, but resolves the board's {@link File} only once per name
     * and reuses it — together with the config last parsed from it — for as long as the file's own
     * last-modified time has not changed, instead of rebuilding the path and re-reading/re-parsing the
     * file on every call.
     *
     * @param boardName the dashboard, with or without the ".html" extension.
     * @return the parsed configuration.
     * @throws IOException if the file cannot be read or the markers are not found.
     */
    private static UtilJson cachedBoardConfig( String boardName ) throws IOException
    {
        String     sName = normalizeBoardName( boardName );
        BoardEntry entry = boardCache.get( sName );

        if( entry == null )
        {
            entry = new BoardEntry( getBoardFile( sName ) );

            BoardEntry raced = boardCache.putIfAbsent( sName, entry );

            if( raced != null )
                entry = raced;
        }

        long mtime = entry.file.lastModified();

        if( (entry.config == null) || (mtime != entry.mtime) )
        {
            synchronized( entry )
            {
                mtime = entry.file.lastModified();   // May have changed again while waiting for the lock

                if( (entry.config == null) || (mtime != entry.mtime) )
                {
                    entry.config = getBoardConfig( entry.file );
                    entry.mtime  = mtime;
                }
            }
        }

        return entry.config;
    }

    /**
     * Parses a dashboard file's embedded {@code getConfig()} — the same object
     * {@link #writeContents} writes and the board's own player reads on load. Shared by
     * {@link #getPassword} and (via {@link #cachedBoardConfig}) {@link #boardExens}, so both read the
     * exact same object instead of two independent parses of the same markers drifting apart.
     *
     * @param fBoard the dashboard's {@code .html} file.
     * @return the parsed configuration.
     * @throws IOException if the file cannot be read or the markers are not found.
     */
    private static UtilJson getBoardConfig( File fBoard ) throws IOException
    {
        String sBoard = UtilIO.getAsText( fBoard );
        int    nStart = sBoard.indexOf( sMarkStart ) + sMarkStart.length();
        int    nEnd   = sBoard.indexOf( sMarkEnd   );
        String sFunc  = sBoard.substring( nStart, nEnd );    // function{ return {......}; }

        sFunc = sFunc.substring( sFunc.indexOf( "return" ) + "return".length() );
        sFunc = sFunc.substring( 0, sFunc.lastIndexOf( ';' ) ).trim();

        return new UtilJson( sFunc );
    }

    /**
     * @param sName a board's file name, with or without the ".html" extension.
     * @return the name without it.
     */
    private static String stripHtmlExtension( String sName )
    {
        return UtilStr.endsWith( sName, ".html" ) ? UtilStr.removeLast( sName, 5 ) : sName;
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