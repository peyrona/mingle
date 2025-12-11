
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.lexer.Language;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.form.EagerFormParsingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * This is a basic HTTP 2.0 Server using: https://undertow.io/
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class HttpServer
{
    private static final String         KEY_USER_ID     = "mingle_user_id";
    private static final String[]       asPrivatePaths  = { "/gum/bridge", "/gum/upload", "/gum/ws", "/gum/board", Util.appendFileMgrCtxTo("/gum/"), Util.appendUserFilesCtxTo("/gum/") };
    private static final String[]       asPublicFileExt = { "model", "html", "js", "css", "png", "jpg", "jpeg", "svg", "ico" };    // Models have to be always available to avoid ExEns to be logged-in. JSON files can not be available because "users.json"
    private static final SessionConfig  sessionCfg      = new SessionCookieConfig().setCookieName( "_Mingle::Gum_" );
    private        final Undertow       server;
    private        final SessionManager sessionMgr;
    private        final short          nClientAllow;

    //------------------------------------------------------------------------//

// UN-COMMENT TO DEBUGG
//    static
//    {
//        java.util.logging.Level level = java.util.logging.Level.INFO;
//
//        try
//        {
//            // Set JBoss Logging to use Java Logger
//            System.setProperty( "org.jboss.logging.provider", "jdk" );
//
//            // Root logger setup
//            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger( "" );
//                                     rootLogger.setLevel( level );
//
//            // Configure ConsoleHandler to show all logs
//            java.util.logging.ConsoleHandler consoleHandler = new java.util.logging.ConsoleHandler();
//            consoleHandler.setLevel( level );
//            consoleHandler.setFormatter( new java.util.logging.SimpleFormatter() ); // Use simple formatting
//            rootLogger.addHandler( consoleHandler );
//
//            // Optionally, log to a file
//         // java.util.logging.FileHandler fileHandler = new java.util.logging.FileHandler( "undertow_server.log", true ); // Append to log file
//         // fileHandler.setLevel( level );
//         // fileHandler.setFormatter( new java.util.logging.SimpleFormatter() );
//         // rootLogger.addHandler( fileHandler );
//
//            // Configure Undertow-specific loggers
//            java.util.logging.Logger.getLogger( "io.undertow" ).setLevel( level );
//            java.util.logging.Logger.getLogger( "org.xnio" ).setLevel( level );
//
//        }
//        catch( Exception e )
//        {
//            e.printStackTrace();
//        }
//    }

    //----------------------------------------------------------------------------------------------------------------------------------------//

    /**
     * Class constructor.
     *
     * @param host Host address
     * @param httpPort HTTP port number
     * @param httpsPort HTTPS port number
     * @param keystorePath Path to the keystore file
     * @param keystorePassword Password for the keystore
     * @throws Exception if there's an error setting up the server
     */
    HttpServer( String host, int httpPort, int maxSessions, String allowed, int httpsPort, String keystorePath, String keystorePassword ) throws Exception
    {
        sessionMgr = new InMemorySessionManager( "MINGLE_SESSION_MANAGER", maxSessions, true );
        sessionMgr.setDefaultSessionTimeout( (int) ServiceUtil.getSessionTimeout() );    // In secs, not in millis

        nClientAllow = UtilComm.clientScope( allowed, null );

        // Create the main handlers for different paths
        ResourceManager rmGum = new FileResourceManager( Util.getAppDir() );
        ResourceHandler rhGum = new ResourceHandler( rmGum ).setWelcomeFiles( "index.html" );

        ResourceManager rmBoard = new FileResourceManager( Util.getBoardsDir() );
        ResourceHandler rhBoard = new ResourceHandler( rmBoard );

        boolean bFollow = UtilSys.isDevEnv;    // Follow Symbolic Links?
        ResourceManager rmStatic = new PathResourceManager( Util.getServedFilesDir().toPath(), Long.MAX_VALUE, bFollow, new String[] {} );
        ResourceHandler rhStatic = Handlers.resource( rmStatic ).setDirectoryListingEnabled( true );

        // Create the path handler for different endpoints
        String sUserFilesCtx = UtilStr.removeLast( Util.appendUserFilesCtxTo("/gum/"), 1 );    // This ends with '/' because Undertow's PathHandler treats paths with and without trailing slashes as distinct paths by default

        PathHandler pathHandler = new PathHandler()
                                        .addPrefixPath( "/gum/bridge", Handlers.websocket( new CommBridge() ) )
                                        .addPrefixPath( "/gum/upload", newUploader() )
                                        .addPrefixPath( "/gum/ws"    , new WsHandler() )
                                        .addPrefixPath( "/gum/board" , rhBoard )
                                        .addPrefixPath( "/gum/login" , new LoginHandler() )
                                        .addPrefixPath( "/gum/logout", newLogoutHandler() )
                                        .addPrefixPath( "/gum"       , rhGum )
                                        .addPrefixPath( sUserFilesCtx, rhStatic );

        // Wrap the authenticated handler with session management
        SessionAttachmentHandler sah = new SessionAttachmentHandler( sessionMgr, sessionCfg )
                                            .setNext( newAuthHandler( pathHandler ) );

        host     = UtilStr.isEmpty( host ) ? "localhost" : host.toLowerCase();
        httpPort = UtilUnit.setBetween( 1, httpPort , 65535 );

        Undertow.Builder builder = Undertow.builder()
                                           .addHttpListener( httpPort, host );    // Regular HTTP

        if( ! "localhost".equals( host ) )
            builder.addHttpListener( httpPort, "localhost" );                     // Regular HTTP on localhost

        if( (httpsPort > 0) && (keystorePath != null) && (keystorePassword != null) )
        {
            httpsPort = UtilUnit.setBetween( 1, httpsPort, 65535 );

            SSLContext sslContext = createSSLContext( keystorePath, keystorePassword );

            builder.addHttpsListener( httpsPort, host, sslContext );              // HTTPS

            if( ! "localhost".equals( host ) )
                builder.addHttpsListener( httpsPort, "localhost", sslContext );   // HTTPS on localhost
        }

        server = builder.setHandler( newErrorHandler( sah ) )
                        .build();

        // Shows GUM configuration ----------------------------------------

        String sServer = "http://" + host +':'+ httpPort +"/gum/";

        String sMsg = "Dashboards editor and player + File Manager + Server for static content.\n\n" +
                      "Dashboards manager : "+ sServer + "index.html    (also 'localhost')\n" +
                      "Dashboard's folder : "+ Util.getBoardsDir().getCanonicalPath()      +"/\n" +
                      "Serving files from : "+ Util.getServedFilesDir().getCanonicalPath() +"/\n" +
                      "    * at context   : "+ Util.appendUserFilesCtxTo( sServer )        +'\n' +
                      "    * UI manager   : "+ Util.appendFileMgrCtxTo(   sServer ) + "index.html\n";

        UtilSys.getLogger().say( sMsg );

        if( (httpsPort > 0) && (keystorePath != null) && (keystorePassword != null) )
            UtilSys.getLogger().say( "\nSame services are also available via HTTPS at port "+ httpsPort );
    }

    HttpServer start()
    {
        server.start();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );
        String msg = "["+ LocalDateTime.now().format(formatter) +"] Gum started...";
        UtilSys.getLogger().say( msg );

        return this;
    }

    HttpServer stop()
    {
        try
        {
            sessionMgr.getAllSessions()
                      .forEach( (name) -> sessionMgr.getSession( name ).invalidate( null ) );    // Passing null because we don't need an exchange

            server.stop();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );
            String msg = "["+ LocalDateTime.now().format(formatter) +"] Gum stopped.";
            UtilSys.getLogger().say( msg );
        }
        catch( Exception e )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, "Error during server stop: " + e.getMessage() );
        }

        return this;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Create an authorization handler that checks for session existence.
     *
     * @param pathHandler
     * @return
     */
    private HttpHandler newAuthHandler( PathHandler pathHandler )
    {
        return (HttpServerExchange xchg) ->
                {
                    Session session = sessionMgr.getSession( xchg, sessionCfg );
                    boolean isValid = (session != null) && UtilStr.isNotEmpty( session.getAttribute( KEY_USER_ID ) );    // Is user is already authenticated?

                    if( isValid || isPublicPath( xchg.getRequestPath() ) )       // This is the 2nd fastest and has to be before the "websocket"
                    {
                        pathHandler.handleRequest( xchg );
                    }
                    else if( session == null )                                   // Creates a session to speed-up next requests
                    {
                        session = sessionMgr.createSession( xchg, sessionCfg );

                        if( isClientAllowed( xchg ) )
                        {
                            session.setAttribute( KEY_USER_ID, "local_client" );
                        }
                        else
                        {
                            xchg.setStatusCode( StatusCodes.UNAUTHORIZED );
                            xchg.endExchange();
                        }
                    }
                };
    }

    private HttpHandler newLogoutHandler()
    {
        return exchange ->
                {
                    Session session = sessionMgr.getSession( exchange, sessionCfg );

                    if( session != null )
                        session.invalidate( exchange );

                    exchange.endExchange();
                };
    }

    private boolean isClientAllowed( HttpServerExchange xchg )
    {
        HeaderMap   headers       = xchg.getRequestHeaders();
        String      xForwardedFor = headers.getFirst( "X-Forwarded-For" );    // Check for the X-Forwarded-For header
        InetAddress clientIP      = null;

        if( (xForwardedFor != null) && (! xForwardedFor.isEmpty()) )
        {
            try
            {
                String s = xForwardedFor.split( "," )[0].trim();
                clientIP = Inet4Address.getByName( s );                       // The header can contain multiple IPs, the first one is the original client
            }
            catch( UnknownHostException ex )
            {
                // Nothing to do: clientIP is already null
            }
        }
        else
        {
            clientIP = xchg.getSourceAddress().getAddress();                  // Fallback to the source address if the header is not present
        }

        try
        {
            return UtilComm.isCLientAllowed( nClientAllow, clientIP );
        }
        catch( SocketException ex )
        {
            // Nothing to do
        }

        return false;
    }

    //------------------------------------------------------------------------//
    // PRIVATE STATIC INTERFACE

    /**
     * Creates an SSL context from a keystore file
     *
     * @param keystorePath Path to the keystore file
     * @param keystorePassword Password for the keystore
     * @return Configured SSLContext
     * @throws Exception if there's an error loading the keystore
     */
    private static SSLContext createSSLContext(String keystorePath, String keystorePassword) throws Exception
    {
        KeyStore keyStore = KeyStore.getInstance( "JKS" );

        try( FileInputStream is = new FileInputStream( keystorePath ) )
        {
            keyStore.load( is, keystorePassword.toCharArray() );
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
                          keyManagerFactory.init( keyStore, keystorePassword.toCharArray() );

        KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

        SSLContext sslContext = SSLContext.getInstance( "TLS" );
                   sslContext.init( keyManagers, null, new SecureRandom() );

        return sslContext;
    }

    /**
     * Check if the requested path is in the public paths list
     *
     * @param path The request path
     * @return true if the path is public (no auth needed)
     */
    private static boolean isPublicPath( String path )
    {
        if( path == null )
            return false;

        if( ! path.startsWith( "/gum/" ) )
            return false;

        if( path.contains( ".." ) || path.contains( "//" ) )   // Prevent directory traversal
            return false;

        if( UtilColls.contains( asPublicFileExt, UtilIO.getExtension( path ).toLowerCase() ) )
            return true;

        for( String p : asPrivatePaths )
        {
            if( path.startsWith( p ) )
                return false;
        }

        return true;
    }

    private static HttpHandler newErrorHandler( HttpHandler next )
    {
        return (xchg) ->
                {
                    try
                    {
                        next.handleRequest( xchg );    // Delegate to the next handler
                    }
                    catch( Exception exc )
                    {
                        sendErrAndLogIt( xchg, exc );
                    }
                };
    }

    private static EagerFormParsingHandler newUploader()
    {
        return new EagerFormParsingHandler()
                .setNext( new HttpHandler()
                {
                    @Override
                    public void handleRequest( final HttpServerExchange xchg ) throws IOException
                    {
                        if( xchg.isInIoThread() )
                        {
                            xchg.dispatch( this );
                            return;
                        }

                        FormData formData = xchg.getAttachment( FormDataParser.FORM_DATA );

                        if( formData == null )
                            return;

                        String sTarget = formData.getFirst( "target" ).getValue();  // "board_images" --> used by dashboards
                                                                                    // "user_files"   --> used by file manager
                        for( String name : formData )
                        {
                            if( "file".equals( name ) )     // The file input field is "files"
                            {
                                for( FormData.FormValue fileValue : formData.get( name ) )
                                {
                                    if( fileValue.isFileItem() )
                                    {
                                        switch( sTarget )
                                        {
                                            case "user_files"  : saveUserFile(  formData, fileValue ); break;
                                            case "board_images": saveImg4Board( formData, fileValue ); break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } );
    }

    /**
     * Image files associated with a certain dashboard.<br>
     * Stored at: {*home*}/lib/gum/web/dashboards/<dashboard_name>/images/
     *
     * @param formData
     * @param fileValue
     * @throws IOException
     */
    private static void saveImg4Board( FormData formData, FormData.FormValue fileValue ) throws IOException
    {
        String fileName = fileValue.getFileName();

        if( UtilStr.isEmpty( fileName ) )
            throw new IOException( "Empty file name" );

        String sBoardName = formData.getFirst( "board" ).getValue();

        try( InputStream is = fileValue.getFileItem().getInputStream() )
        {
            File fDestination = ServiceImages.getImageFile( sBoardName, fileName );

            try( FileOutputStream os = new FileOutputStream( fDestination ) )
            {
                byte[] buffer = new byte[1024 * 4];
                int    nRead;

                while( (nRead = is.read( buffer )) != -1 )
                {
                    os.write( buffer, 0, nRead );
                }
            }
        }
    }

    /**
     * User files are files of any type that exists in the server folder pointed by:
     * File( UtilSys.getEtcFolder(), "gum_user_files" ).
     *
     * @param formData
     * @param fileValue
     * @throws IOException
     */
    private static void saveUserFile( FormData formData, FormData.FormValue fileValue ) throws IOException
    {
        String fBaseDir = formData.getFirst( "basedir" ).getValue();
        String fileName = fileValue.getFileName();

        if( UtilStr.isEmpty( fileName ) )
        {
            return;
        }

        try( InputStream is = fileValue.getFileItem().getInputStream() )
        {
            File fDestination = new File( new File( Util.getServedFilesDir(), fBaseDir ), fileName );

            try( FileOutputStream os = new FileOutputStream( fDestination ) )
            {
                byte[] buffer = new byte[1024*4];
                int    nRead;

                while( (nRead = is.read( buffer )) != -1 )
                {
                    os.write( buffer, 0, nRead );
                }
            }
        }
    }

    private static void sendErrAndLogIt( HttpServerExchange xchg, Exception exc )
    {
        UtilSys.getLogger().log( ILogger.Level.SEVERE, exc );

        if( xchg != null && xchg.isResponseChannelAvailable() )
        {
            xchg.getResponseHeaders().put( Headers.CONTENT_TYPE, "application/json" );
            xchg.setStatusCode( StatusCodes.INTERNAL_SERVER_ERROR );

            // Don't expose internal error details to client
            String msg = (exc instanceof MingleException) ? exc.getMessage()
                                                          : "An internal server error occurred";

            xchg.getResponseSender().send( "{\"error\":\""+ Json.value( msg ).toString() + "\"}" );
            xchg.endExchange();
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    /**
     * Login handler for processing login requests
     */
    private final class LoginHandler implements HttpHandler
    {
        @Override
        public void handleRequest( HttpServerExchange xchg ) throws Exception
        {
            String sMethod = xchg.getRequestMethod().toString();

            if( ! sMethod.equals( "POST" ) )
                throw new IOException( "Bad use of MSP login framework" );

            Runnable run = () ->
                            {
                                try
                                {
                                    handleLoginPost( xchg );
                                }
                                catch( IOException exc )
                                {
                                    HttpServer.sendErrAndLogIt( xchg, exc );
                                }
                            };

            if( xchg.isInIoThread() )    // Check if we're on the IO thread
            {
                xchg.dispatch( run );    // Dispatch to a worker thread for blocking operations
            }
            else                         // Already on a worker thread, proceed with handling the request
            {
                run.run();               // Inmediately executed in the current thread
            }
        }

        //------------------------------------------------------------------------//

        private void handleLoginPost( HttpServerExchange xchg ) throws IOException
        {
            xchg.startBlocking();

            if( xchg.getRequestHeaders().getFirst( Headers.CONTENT_TYPE ).startsWith( "application/json" ) )
            {
                // JSON login (for REST API/AJAX)
                int    length = Integer.parseInt( xchg.getRequestHeaders().getFirst( Headers.CONTENT_LENGTH ) );
                byte[] buffer = new byte[length];

                // Set read timeout to prevent indefinite blocking
                xchg.getConnection();

                int bytesRead = 0;
                int totalRead = 0;

                while( totalRead < length && bytesRead != -1 )
                {
                    bytesRead = xchg.getInputStream().read( buffer, totalRead, length - totalRead );
                    if( bytesRead > 0 )
                        totalRead += bytesRead;
                }

                if( totalRead != length )
                    throw new IOException( "Incomplete request data received" );

                String   sj   = new String( buffer, StandardCharsets.UTF_8 );
                UtilJson uj   = new UtilJson( sj );
                String   user = uj.getString( "username", null );
                String   pwd  = uj.getString( "password", null );

                processLogin( xchg, user, pwd );
            }
            else
            {
                xchg.setStatusCode( StatusCodes.BAD_REQUEST );
                xchg.getResponseHeaders().put( HttpString.tryFromString( "Content-Type" ), "application/json" );
                xchg.getResponseSender().send( "{\"error\":\"Missing form data\"}" );
            }
        }

        private void processLogin( HttpServerExchange xchg, String username, String password ) throws IOException
        {
            String  requestedWith = xchg.getRequestHeaders().getFirst( HttpString.tryFromString( "X-Requested-With" ) );
            boolean isAJAXRequest = "XMLHttpRequest".equals( requestedWith );

            if( isAJAXRequest && isAdmin( username, password ) )
            {
                sessionMgr.getSession( xchg, sessionCfg )
                          .setAttribute( KEY_USER_ID, username );

                // Return JSON response for AJAX
                xchg.setStatusCode( StatusCodes.OK );
                xchg.getResponseHeaders().put( HttpString.tryFromString( "Content-Type" ), "application/json" );
                xchg.getResponseSender().send( "{\"success\":true}" );
            }
            else
            {
                xchg.setStatusCode( StatusCodes.UNAUTHORIZED );
                xchg.getResponseSender().send( "Unauthorized: Access denied" );
            }
        }

        private boolean isAdmin( String username, String password ) throws IOException
        {
            return "admin".equalsIgnoreCase( authenticateUser( username, password ) );
        }

        /**
         * Authenticate a user.
         *
         * @param username The username
         * @param password The password
         * @return The user role or null if file or user does not exists.
         */
        private String authenticateUser( String username, String password ) throws IOException
        {
            File fUsers = new File( Util.getServedFilesDir(), "users.json" );

            if( UtilIO.canRead( fUsers ) != null )
                return null;                       // File can not be read

            String    sJSON  = UtilStr.removeComments( UtilIO.getAsText( fUsers ) );
            JsonArray jArray = Json.parse( sJSON ).asArray();

            for( int n = 0; n < jArray.size(); n++ )
            {
                JsonObject jObj = jArray.get( n ).asObject();
                String     name = jObj.getString( "user", "" );
                String     pwd  = jObj.getString( "pwd" , "" );

                if( Language.hasMacro( pwd ) )
                {
                    LocalDateTime ldt = LocalDateTime.now();

                    pwd = UtilStr.replaceAll( pwd, Language.buildMacro( "d" ), String.valueOf( ldt.getDayOfMonth() ) );
                    pwd = UtilStr.replaceAll( pwd, Language.buildMacro( "h" ), String.valueOf( ldt.getHour()       ) );
                }

                if( name.equalsIgnoreCase( username ) && pwd.equals( password ) )
                    return jObj.getString( "role", "" );
            }

            return null;
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private class WsHandler implements HttpHandler
    {
        @Override
        public void handleRequest( HttpServerExchange xchg ) throws IOException
        {
            String      sPath   = xchg.getRequestPath();
            ServiceBase service = null;

                 if( sPath.contains( "ws/util"   ) )  service = new ServiceUtil(    xchg );    // Used by Balata and useful for others
            else if( sPath.contains( "ws/board"  ) )  service = new ServiceBoard(   xchg );
            else if( sPath.contains( "ws/db"     ) )  service = new ServiceDB(      xchg );
            else if( sPath.contains( "ws/images" ) )  service = new ServiceImages(  xchg );
            else if( sPath.contains( "ws/files"  ) )  service = new ServiceFileMgr( xchg );

            if( service == null )  throw new IOException( "Invalid path: " + sPath );
            else                   service.dispatch( xchg.getRequestMethod().toString() );
        }
    }
}