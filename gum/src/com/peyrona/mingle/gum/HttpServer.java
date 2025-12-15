
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.lexer.Language;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 * This is a basic HTTP 2.0 Server using: https://www.eclipse.org/jetty/
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class HttpServer
{
    public static class GumWebSocketServlet extends WebSocketServlet
    {
        @Override
        public void configure( WebSocketServletFactory factory )
        {
            factory.register( CommBridge.class );
            factory.getPolicy().setMaxTextMessageSize( 64 * 1024 );
        }
    }

    //------------------------------------------------------------------------//

    private static final String   KEY_USER_ID     = "mingle_user_id";
    private static final String[] asPrivatePaths  = { "/gum/bridge", "/gum/upload", "/gum/ws", "/gum/board", "/gum/file_mgr", "/gum/user-files" };
    private static final String[] asPublicFileExt = { "model", "html", "js", "css", "png", "jpg", "jpeg", "svg", "ico" };
    private        final Server   server;
    private        final short    nClientAllow;
    private        final int      timeout;

    //------------------------------------------------------------------------//

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
    HttpServer( String host, int httpPort, int maxSessions, String allowed, int timeout, int httpsPort, String keystorePath, String keystorePassword ) throws Exception
    {
        this.nClientAllow = UtilComm.clientScope( allowed, null );
        this.timeout = timeout;

        host     = UtilStr.isEmpty( host ) ? "localhost" : host.toLowerCase();
        httpPort = UtilUnit.setBetween( 1, httpPort , 65535 );

        server = new Server();

        // HTTP Connector
        ServerConnector httpConnector = new ServerConnector( server );
                        httpConnector.setHost( host );
                        httpConnector.setPort( httpPort );
        server.addConnector( httpConnector );

        // Additional localhost connector if host is not localhost
        if( ! "localhost".equals( host ) )
        {
            ServerConnector localhostConnector = new ServerConnector( server );
                            localhostConnector.setHost( "localhost" );
                            localhostConnector.setPort( httpPort );
            server.addConnector( localhostConnector );
        }

        // HTTPS Connector (if configured)
        if( (httpsPort > 0) && (keystorePath != null) && (keystorePassword != null) )
        {
            httpsPort = UtilUnit.setBetween( 1, httpsPort, 65535 );

            SslContextFactory.Server sslContextFactory = createSslContextFactory( keystorePath, keystorePassword );

            ServerConnector httpsConnector = new ServerConnector( server, sslContextFactory );
                            httpsConnector.setHost( host );
                            httpsConnector.setPort( httpsPort );
            server.addConnector( httpsConnector );

            if( ! "localhost".equals( host ) )
            {
                ServerConnector httpsLocalhostConnector = new ServerConnector( server, sslContextFactory );
                                httpsLocalhostConnector.setHost( "localhost" );
                                httpsLocalhostConnector.setPort( httpsPort );
                server.addConnector( httpsLocalhostConnector );
            }

            UtilSys.getLogger().say( "\nHTTPS services available at port "+ httpsPort );
        }

        // Setup handlers - ORDER MATTERS: Most specific paths FIRST
        ContextHandlerCollection contexts = new ContextHandlerCollection();

        // CRITICAL: Add handlers from MOST specific to LEAST specific paths
        // More specific paths (/gum/file_mgr) MUST come before less specific (/gum)

        // WebSocket handler for /gum/bridge
        contexts.addHandler( createWebSocketHandler() );

        // Upload handler for /gum/upload (remove individual session handler)
        contexts.addHandler( createUploadHandler() );

        // Web services handler for /gum/ws (remove individual session handler)
        contexts.addHandler( createWsHandler() );

        // Board resources handler for /gum/board
        contexts.addHandler( createBoardHandler() );

        // Login handler for /gum/login (remove individual session handler)
        contexts.addHandler( createLoginHandler() );

        // Logout handler for /gum/logout (remove individual session handler)
        contexts.addHandler( createLogoutHandler() );

        // User files handler for /gum/user-files
        contexts.addHandler( createUserFilesHandler() );

        // File Manager UI handler for /gum/file_mgr
        contexts.addHandler( createFileMgrHandler() );

        // Main GUM handler for /gum (MUST be LAST - catches all remaining /gum/* requests)
        contexts.addHandler( createGumHandler() );

        // Create auth wrapper - directly wrap the contexts (no SessionHandler in between)
        HandlerWrapper authWrapper = createAuthWrapper();
        authWrapper.setHandler( contexts );

        server.setHandler( authWrapper );

        // Custom error handler
        server.setErrorHandler( new CustomErrorHandler() );

        // Shows GUM configuration
        String sServer = "http://" + host +':'+ httpPort +"/gum/";

        String sMsg = "Dashboards editor and player + File Manager + HTTP/S Server for static content.\n\n" +
                      "Dashboards manager : "+ sServer + "index.html    (also 'localhost')\n" +
                      "Dashboard's folder : "+ Util.getBoardsDir().getCanonicalPath()      +"/\n" +
                      "Serving files from : "+ Util.getServedFilesDir().getCanonicalPath() +"/\n" +
                      "    * at context   : "+ Util.appendUserFilesCtxTo( sServer )        +'\n' +
                      "    * UI manager   : "+ Util.appendFileMgrCtxTo(   sServer ) + "index.html\n";

        UtilSys.getLogger().say( sMsg );
    }

    HttpServer start() throws Exception
    {
        server.start();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );
        String msg = "["+ LocalDateTime.now().format(formatter) +"] Gum started...";
        UtilSys.getLogger().say( msg );

        server.join();

        return this;
    }

    HttpServer stop() throws Exception
    {
        try
        {
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
    // PRIVATE SCOPE - Handler Creation

    /**
 * Creates an authentication wrapper that validates user sessions before allowing access.
 * Public paths (static resources) and requests from allowed clients bypass authentication.
 */
    private HandlerWrapper createAuthWrapper()
    {
        return new HandlerWrapper()
        {
            @Override
            public void handle( String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response )
                    throws IOException, ServletException
            {
                // Skip if already handled by another handler
                if( baseRequest.isHandled() )
                    return;

                Handler handler = getHandler();

                // Fail fast if no handler is configured (should never happen)
                if( handler == null )
                {
                    UtilSys.getLogger().log( ILogger.Level.SEVERE, "No handler configured in auth wrapper for: " + target );
                    sendUnauthorizedResponse( response, baseRequest, "Server configuration error" );
                    return;
                }

                try
                {
                    boolean isAllowed = false;

                    HttpSession session = request.getSession( false );

                    if( session != null && session.getAttribute( KEY_USER_ID ) != null )    // Check 1: Valid session exists
                    {
                        isAllowed = true;
                    }
                    else if( isPublicPath( target ) )                                       // Check 2: Public resource (static files, login page, etc.)
                    {
                        isAllowed = true;
                    }
                    else if( isClientAllowed( request ) )                                   // Check 3: Request from allowed client (localhost, configured IPs)
                    {   // FIXME: activarlo -->
                        // session = request.getSession( true );
                        // session.setAttribute( KEY_USER_ID, "local_client" );
                        isAllowed = true;
                    }

                    if( isAllowed )
                    {
                        if( UtilSys.getLogger() != null && UtilSys.getLogger().isLoggable( ILogger.Level.INFO ) )
                        {
                            UtilSys.getLogger().log( Level.INFO, String.format(
                                                                                "Access granted: %s %s from %s",
                                                                                request.getMethod(),
                                                                                target,
                                                                                request.getRemoteAddr() ) );
                        }

                        handler.handle( target, baseRequest, request, response );   // Delegate to the next handler in the chain
                    }
                    else    // Access denied - log security event
                    {
                        if( UtilSys.getLogger() != null && UtilSys.getLogger().isLoggable( ILogger.Level.WARNING ) )
                        {
                            UtilSys.getLogger().log( ILogger.Level.WARNING, String.format(
                                                                                "Unauthorized access attempt: %s %s from %s",
                                                                                request.getMethod(),
                                                                                target,
                                                                                request.getRemoteAddr() ) );
                        }

                        sendUnauthorizedResponse( response, baseRequest, "Authentication required" );
                    }
                }
                catch( Exception exc )
                {
                    if( UtilSys.getLogger() != null )
                        UtilSys.getLogger().log( ILogger.Level.SEVERE, exc, "Error in authentication handler" );

                    if( ! response.isCommitted() )
                    {
                        response.setStatus( HttpServletResponse.SC_INTERNAL_SERVER_ERROR );
                        response.setContentType( "application/json" );
                        response.getWriter().write( "{\"error\":\"Authentication error\"}" );
                    }

                    baseRequest.setHandled( true );
                }
            }

            /**
             * Sends a standardized 401 Unauthorized response with proper headers and body.
             */
            private void sendUnauthorizedResponse( HttpServletResponse response, Request baseRequest, String message )
                    throws IOException
            {
                if( response.isCommitted() )
                    return;

                response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
                response.setContentType( "application/json; charset=utf-8" );
                response.setHeader( "Cache-Control", "no-store, no-cache, must-revalidate" );
                response.setHeader( "WWW-Authenticate", "FormBased" );

                JsonObject jsonResponse = Json.object()
                                              .add( "error", message )
                                              .add( "status", 401 )
                                              .add( "loginUrl", "/gum/login.html" );

                response.getWriter().write( jsonResponse.toString() );
                baseRequest.setHandled( true );
            }
        };
    }

    private SessionHandler createSessionHandler( int timeout )
    {
        SessionHandler sessionHandler = new SessionHandler();
        sessionHandler.setMaxInactiveInterval( timeout > 0 ? timeout : -1 );
        sessionHandler.getSessionCookieConfig().setName( "_Mingle__Gum_" );

        return sessionHandler;
    }

    private ContextHandler createWebSocketHandler()
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/bridge" );
                              context.addServlet( new ServletHolder( new GumWebSocketServlet() ), "/*" );

        return context;
    }

    private ContextHandler createUploadHandler()
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/upload" );
                              context.setSessionHandler( createSessionHandler( timeout ) );

        ServletHolder holder = new ServletHolder( new UploadServlet() );
        holder.getRegistration().setMultipartConfig( new MultipartConfigElement( System.getProperty( "java.io.tmpdir" ) ) );

        context.addServlet( holder, "/*" );

        return context;
    }

    private ContextHandler createWsHandler()
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/ws" );
                              context.addServlet( new ServletHolder( new WsServlet() ), "/*" );
                              context.setSessionHandler( createSessionHandler( timeout ) );

        return context;
    }

    private ContextHandler createBoardHandler() throws IOException
    {
        ContextHandler context = new ContextHandler( "/gum/board" );

        ResourceHandler resourceHandler = new ResourceHandler();
                        resourceHandler.setResourceBase( Util.getBoardsDir().getAbsolutePath() );
                        resourceHandler.setDirectoriesListed( false );

        context.setHandler( resourceHandler );

        return context;
    }

    private ContextHandler createLoginHandler()
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/login" );
                              context.setSessionHandler( createSessionHandler( timeout ) );

        ServletHolder holder = new ServletHolder( new LoginServlet() );

        context.addServlet( new ServletHolder( new LoginServlet() ), "/*" );

        return context;
    }

    private ContextHandler createLogoutHandler()
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/logout" );
                              context.addServlet( new ServletHolder( new LogoutServlet() ), "/*" );
                              context.setSessionHandler( createSessionHandler( timeout ) );

        return context;
    }

    private ContextHandler createUserFilesHandler() throws IOException
    {
        ContextHandler context = new ContextHandler( "/gum/user-files" );
        ResourceHandler resourceHandler = new ResourceHandler();
                        resourceHandler.setResourceBase( Util.getServedFilesDir().getAbsolutePath() );
                        resourceHandler.setDirectoriesListed( true );

        boolean bFollow = UtilSys.isDevEnv;

        if( bFollow )
            resourceHandler.setAcceptRanges( true );

        context.setHandler( resourceHandler );

        return context;
    }

    private ContextHandler createFileMgrHandler() throws IOException
    {
        ContextHandler context = new ContextHandler("/gum/file_mgr");

        ResourceHandler resourceHandler = new ResourceHandler();
                        resourceHandler.setResourceBase( new File( Util.getAppDir(), "file_mgr" ).getAbsolutePath() );
                        resourceHandler.setDirectoriesListed( false );
                        resourceHandler.setWelcomeFiles( new String[] { "index.html" } );

        context.setHandler(resourceHandler);

        return context;
    }

    private ContextHandler createGumHandler()
    {
        ContextHandler context = new ContextHandler("/gum");

        ResourceHandler resourceHandler = new ResourceHandler();
                        resourceHandler.setResourceBase(Util.getAppDir().getAbsolutePath());
                        resourceHandler.setDirectoriesListed(false);
                        resourceHandler.setWelcomeFiles(new String[]{"index.html"});

        context.setHandler(resourceHandler);

        return context;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE - Utility Methods

    private boolean isClientAllowed( HttpServletRequest request )
    {
        String      xForwardedFor = request.getHeader( "X-Forwarded-For" );
        InetAddress clientIP      = null;

        if( (xForwardedFor != null) && (! xForwardedFor.isEmpty()) )
        {
            try
            {
                String s = xForwardedFor.split( "," )[0].trim();
                clientIP = Inet4Address.getByName( s );
            }
            catch( UnknownHostException ex )
            {
                // Nothing to do: clientIP is already null
            }
        }
        else
        {
            try
            {
                clientIP = InetAddress.getByName( request.getRemoteAddr() );
            }
            catch( UnknownHostException ex )
            {
                // Nothing to do
            }
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
    // PRIVATE STATIC METHODS

    private static SslContextFactory.Server createSslContextFactory( String keystorePath, String keystorePassword )
            throws Exception
    {
        KeyStore keyStore = KeyStore.getInstance( "JKS" );

        try( FileInputStream is = new FileInputStream( keystorePath ) )
        {
            keyStore.load( is, keystorePassword.toCharArray() );
        }

        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStore( keyStore );
        sslContextFactory.setKeyStorePassword( keystorePassword );

        return sslContextFactory;
    }

    private static boolean isPublicPath( String path )
    {
        if( path == null )
            return false;

        if( ! path.startsWith( "/gum/" ) )
            return false;

        if( path.contains( ".." ) || path.contains( "//" ) )
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

    private static void sendErrAndLogIt( HttpServletResponse response, Exception exc )
    {
        UtilSys.getLogger().log( ILogger.Level.SEVERE, exc );

        if( response != null && !response.isCommitted() )
        {
            try
            {
                response.setContentType( "application/json" );
                response.setStatus( HttpServletResponse.SC_INTERNAL_SERVER_ERROR );

                String msg = (exc instanceof MingleException) ? exc.getMessage()
                                                              : "An internal server error occurred";

                response.getWriter().write( "{\"error\":\""+ Json.value( msg ).toString() + "\"}" );
            }
            catch( IOException e )
            {
                UtilSys.getLogger().log( ILogger.Level.SEVERE, e, "Failed to send error response" );
            }
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASSES - Servlets
    //------------------------------------------------------------------------//

    private class LoginServlet extends HttpServlet
    {
        @Override
        protected void doPost( HttpServletRequest request, HttpServletResponse response )
                throws ServletException, IOException
        {
            try
            {
                String contentType = request.getContentType();

                if( contentType != null && contentType.startsWith( "application/json" ) )
                {
                    int    length = request.getContentLength();
                    byte[] buffer = new byte[length];

                    try( InputStream is = request.getInputStream() )
                    {
                        int bytesRead = 0;
                        int totalRead = 0;

                        while( totalRead < length && bytesRead != -1 )
                        {
                            bytesRead = is.read( buffer, totalRead, length - totalRead );
                            if( bytesRead > 0 )
                                totalRead += bytesRead;
                        }

                        if( totalRead != length )
                            throw new IOException( "Incomplete request data received" );
                    }

                    String   sj   = new String( buffer, StandardCharsets.UTF_8 );
                    UtilJson uj   = new UtilJson( sj );
                    String   user = uj.getString( "username", null );
                    String   pwd  = uj.getString( "password", null );

                    processLogin( request, response, user, pwd );
                }
                else
                {
                    response.setStatus( HttpServletResponse.SC_BAD_REQUEST );
                    response.setContentType( "application/json" );
                    response.getWriter().write( "{\"error\":\"Missing form data\"}" );
                }
            }
            catch( Exception exc )
            {
                sendErrAndLogIt( response, exc );
            }
        }

        private void processLogin( HttpServletRequest request, HttpServletResponse response,
                                  String username, String password ) throws IOException
        {
            String  requestedWith = request.getHeader( "X-Requested-With" );
            boolean isAJAXRequest = "XMLHttpRequest".equals( requestedWith );

            if( isAJAXRequest && isAdmin( username, password ) )
            {
                HttpSession session = request.getSession( true );
                session.setAttribute( KEY_USER_ID, username );

                response.setStatus( HttpServletResponse.SC_OK );
                response.setContentType( "application/json" );
                response.getWriter().write( "{\"success\":true}" );
            }
            else
            {
                response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
                response.getWriter().write( "Unauthorized: Access denied" );
            }
        }

        private boolean isAdmin( String username, String password ) throws IOException
        {
            return "admin".equalsIgnoreCase( authenticateUser( username, password ) );
        }

        private String authenticateUser( String username, String password ) throws IOException
        {
            File fUsers = new File( Util.getServedFilesDir(), "users.json" );

            if( UtilIO.canRead( fUsers ) != null )
                return null;

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

    private class LogoutServlet extends HttpServlet
    {
        @Override
        protected void doGet( HttpServletRequest request, HttpServletResponse response )
                throws ServletException, IOException
        {
            HttpSession session = request.getSession( false );

            if( session != null )
                session.invalidate();

            response.setStatus( HttpServletResponse.SC_OK );
        }

        @Override
        protected void doPost( HttpServletRequest request, HttpServletResponse response )
                throws ServletException, IOException
        {
            doGet( request, response );
        }
    }

    //------------------------------------------------------------------------//

    private class UploadServlet extends HttpServlet
    {
        @Override
        protected void doPost( HttpServletRequest request, HttpServletResponse response )
                throws ServletException, IOException
        {
            try
            {
                String sTarget = request.getParameter( "target" );

                for( Part part : request.getParts() )
                {
                    if( "file".equals( part.getName() ) )
                    {
                        String fileName = getFileName( part );

                        if( UtilStr.isNotEmpty( fileName ) )
                        {
                            switch( sTarget )
                            {
                                case "user_files"  : saveUserFile(  request, part, fileName ); break;
                                case "board_images": saveImg4Board( request, part, fileName ); break;
                            }
                        }
                    }
                }

                response.setStatus( HttpServletResponse.SC_OK );
            }
            catch( Exception exc )
            {
                sendErrAndLogIt( response, exc );
            }
        }

        private String getFileName( Part part )
        {
            String contentDisposition = part.getHeader( "content-disposition" );

            if( contentDisposition != null )
            {
                for( String token : contentDisposition.split( ";" ) )
                {
                    if( token.trim().startsWith( "filename" ) )
                    {
                        return token.substring( token.indexOf( '=' ) + 1 ).trim().replace( "\"", "" );
                    }
                }
            }

            return null;
        }

        private void saveImg4Board( HttpServletRequest request, Part part, String fileName ) throws IOException
        {
            String sBoardName = request.getParameter( "board" );

            try( InputStream is = part.getInputStream() )
            {
                File fDestination = ServiceImages.getImageFile( sBoardName, fileName );

                try( FileOutputStream os = new FileOutputStream( fDestination ) )
                {
                    copyStream( is, os );
                }
            }
        }

        private void saveUserFile( HttpServletRequest request, Part part, String fileName ) throws IOException
        {
            String fBaseDir = request.getParameter( "basedir" );

            try( InputStream is = part.getInputStream() )
            {
                File fDestination = new File( new File( Util.getServedFilesDir(), fBaseDir ), fileName );

                try( FileOutputStream os = new FileOutputStream( fDestination ) )
                {
                    copyStream( is, os );
                }
            }
        }

        private void copyStream( InputStream is, OutputStream os ) throws IOException
        {
            byte[] buffer = new byte[1024 * 4];
            int    nRead;

            while( (nRead = is.read( buffer )) != -1 )
            {
                os.write( buffer, 0, nRead );
            }
        }
    }

    //------------------------------------------------------------------------//

    private class WsServlet extends HttpServlet
    {
        @Override
        protected void service( HttpServletRequest request, HttpServletResponse response )
                throws ServletException, IOException
        {
            try
            {
                String      sPath   = request.getPathInfo();
                ServiceBase service = null;

                     if( sPath.contains( "/board"  ) )  service = new ServiceBoard(   request, response );
                else if( sPath.contains( "/db"     ) )  service = new ServiceDB(      request, response );
                else if( sPath.contains( "/images" ) )  service = new ServiceImages(  request, response );
                else if( sPath.contains( "/files"  ) )  service = new ServiceFileMgr( request, response );

                if( service == null )   throw new IOException( "Invalid path: " + sPath );
                else                    service.dispatch( request.getMethod() );
            }
            catch( Exception exc )
            {
                sendErrAndLogIt( response, exc );
            }
        }
    }

    //------------------------------------------------------------------------//

    private static class CustomErrorHandler extends ErrorHandler
    {
        @Override
        public void handle( String target, Request baseRequest, HttpServletRequest request,
                           HttpServletResponse response ) throws IOException
        {
            if( !response.isCommitted() )
            {
                response.setContentType( "application/json" );

                String msg = "An error occurred";
                response.getWriter().write( "{\"error\":\""+ Json.value( msg ).toString() + "\"}" );
            }

            baseRequest.setHandled( true );
        }
    }
}