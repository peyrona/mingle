
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
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
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
    private static final String  KEY_USER_ID = "mingle_user_id";
    private static final ILogger logger      = UtilSys.getLogger();
    private        final Server  server;

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

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
        ServerConfig.setClientIPScope( allowed );    // Store in shared configuration

        host     = UtilStr.isEmpty( host ) ? "localhost" : host.toLowerCase();
        httpPort = UtilUnit.setBetween( 1, httpPort , 65535 );

        // Configure the Server and it connectors
        this.server = new Server();

        // HTTP Connector
        ServerConnector httpConnector = new ServerConnector( server );
                        httpConnector.setHost( host );
                        httpConnector.setPort( httpPort );
        server.addConnector( httpConnector );

        // HTTPS Connector (if configured)
        if( (httpsPort > 0) && (keystorePath != null) && (keystorePassword != null) )
        {
            httpsPort = UtilUnit.setBetween( 1, httpsPort, 65535 );

            SslContextFactory.Server sslContextFactory = createSslContextFactory( keystorePath, keystorePassword );

            ServerConnector httpsConnector = new ServerConnector( server, sslContextFactory );
                            httpsConnector.setHost( host );
                            httpsConnector.setPort( httpsPort );
            server.addConnector( httpsConnector );
        }

        ContextHandlerCollection contexts = new ContextHandlerCollection();

        // Add Handlers to the collection
        contexts.addHandler( createLoginHandler( timeout ) );        // /gum/login        - NO AUTH FILTER
        contexts.addHandler( createWsHandler( timeout ) );           // /gum/ws/*         - WITH AUTH FILTER
        contexts.addHandler( createBridgeHandler( timeout ) );       // /gum/bridge/*     - WITH AUTH FILTER
        contexts.addHandler( createUploadHandler( timeout ) );       // /gum/upload       - WITH AUTH FILTER
        contexts.addHandler( createFileManagerHandler( timeout ) );  // /gum/file_mgr/*   - WITH AUTH FILTER
        contexts.addHandler( createServedFilesHandler( timeout ) );  // /gum/user-files/* - WITH AUTH FILTER
        contexts.addHandler( createBoardHandler( timeout ) );        // /gum/board/*      - WITH AUTH FILTER
        contexts.addHandler( createGumHandler( timeout ) );          // /gum              - WITH AUTH FILTER
        contexts.addHandler( createLogoutHandler( timeout ) );       // /gum/logout       - WITH AUTH FILTER

        // Set the Root Handler (NO SessionHandler wrapper, NO AuthWrapper!)
        server.setHandler( contexts );
        server.setErrorHandler( new CustomErrorHandler() );

        // Show GUM configuration
        String sServer = "http://" + host + ':' + httpPort + "/gum/";

        String sMsg = "Dashboards editor and player + File Manager + HTTP/S Server for static content.\n\n" +
                      "Dashboards manager : " + sServer + "index.html\n" +
                      "Dashboard's folder : " + Util.getBoardsDir().getCanonicalPath() + "/\n" +
                      "Serving files from : " + Util.getServedFilesDir().getCanonicalPath() + "/\n" +
                      "    * at context   : " + sServer + "user-files/\n" +
                      "    * UI manager   : " + sServer + "file_mgr/index.html\n";

        if( (httpsPort > 0) && (keystorePath != null) && (keystorePassword != null) )
            logger.say( "HTTPS services available at port " + httpsPort + '\n' );

        logger.say( sMsg );
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    HttpServer start() throws Exception
    {
        server.start();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );
        String msg = "["+ LocalDateTime.now().format( formatter ) +"] Gum started...";
        logger.say( msg );

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
            logger.say( msg );
        }
        catch( Exception e )
        {
            logger.log( ILogger.Level.SEVERE, "Error during server stop: " + e.getMessage() );
        }

        return this;
    }

    //------------------------------------------------------------------------//
    //  STATIC CONTENT HANDLERS (Refactored to use DefaultServlet)            //
    //------------------------------------------------------------------------//

    private ContextHandler createGumHandler( int timeout ) throws IOException
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( Util.getDashboardManagerContext() );
                              context.setBaseResource( Resource.newResource( Util.getDashboardManagerDir() ) );
                              context.addAliasCheck( new ContextHandler.ApproveAliases() );

        // Configure session management for this context
        SessionHandler sessionHandler = new SessionHandler();
                       sessionHandler.setMaxInactiveInterval( timeout > 0 ? timeout : -1 );
                       sessionHandler.getSessionCookieConfig().setName( "_Mingle_Gum_" );

        context.setSessionHandler( sessionHandler );

        // Add authentication filter (runs before servlet)
        context.addFilter( AuthenticationFilter.class, "/*", java.util.EnumSet.of( javax.servlet.DispatcherType.REQUEST ) );

        // Add servlet
        ServletHolder holder = new ServletHolder( "default", DefaultServlet.class );
                      holder.setInitParameter( "dirAllowed", "false" );

        context.addServlet( holder, "/" );

        return context;
    }

    private ContextHandler createBoardHandler( int timeout ) throws IOException
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/board" );
                              context.setBaseResource( Resource.newResource( Util.getBoardsDir() ) );
                              context.addAliasCheck( new ContextHandler.ApproveAliases() );

        // Configure session management for this context
        SessionHandler sessionHandler = new SessionHandler();
                       sessionHandler.setMaxInactiveInterval( timeout > 0 ? timeout : -1 );
                       sessionHandler.getSessionCookieConfig().setName( "_Mingle_Gum_" );

        context.setSessionHandler( sessionHandler );

        // Add authentication filter (runs before servlet)
        context.addFilter( AuthenticationFilter.class, "/*", java.util.EnumSet.of( javax.servlet.DispatcherType.REQUEST ) );

        // Add servlet
        ServletHolder holder = new ServletHolder( "default", DefaultServlet.class );
                      holder.setInitParameter( "dirAllowed", "false" );

        context.addServlet( holder, "/" );

        return context;
    }

    private ContextHandler createFileManagerHandler( int timeout ) throws IOException
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( Util.getFileManagerContext() );
                              context.setBaseResource( Resource.newResource( Util.getdFileManagerDir() ) );
                              context.addAliasCheck( new ContextHandler.ApproveAliases() );

        // Configure session management for this context
        SessionHandler sessionHandler = new SessionHandler();
                       sessionHandler.setMaxInactiveInterval( timeout > 0 ? timeout : -1 );
                       sessionHandler.getSessionCookieConfig().setName( "_Mingle_Gum_" );

        context.setSessionHandler( sessionHandler );

        // Add authentication filter
        context.addFilter( AuthenticationFilter.class, "/*", java.util.EnumSet.of( javax.servlet.DispatcherType.REQUEST ) );

        // Add servlet
        ServletHolder holder = new ServletHolder( "default", DefaultServlet.class );
                      holder.setInitParameter( "dirAllowed", "false" );

        context.addServlet( holder, "/" );

        return context;
    }

    private ContextHandler createServedFilesHandler( int timeout ) throws IOException
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( Util.getServedFilesContext() );
                              context.setBaseResource( Resource.newResource( Util.getServedFilesDir() ) );
                              context.addAliasCheck( new ContextHandler.ApproveAliases() );

        // Configure session management for this context
        SessionHandler sessionHandler = new SessionHandler();
                       sessionHandler.setMaxInactiveInterval( timeout > 0 ? timeout : -1 );
                       sessionHandler.getSessionCookieConfig().setName( "_Mingle_Gum_" );

        context.setSessionHandler( sessionHandler );

        // Add authentication filter
        context.addFilter( AuthenticationFilter.class, "/*", java.util.EnumSet.of( javax.servlet.DispatcherType.REQUEST ) );

        // Add servlet
        ServletHolder holder = new ServletHolder( "default", DefaultServlet.class );
                      holder.setInitParameter( "dirAllowed", "true" );

        context.addServlet( holder, "/" );

        return context;
    }

    //------------------------------------------------------------------------//
    //  DYNAMIC HANDLERS (WebSockets / API)                                   //
    //------------------------------------------------------------------------//

    private ContextHandler createWsHandler( int timeout )
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/ws" );

        // Configure session management
        SessionHandler sessionHandler = new SessionHandler();
                       sessionHandler.setMaxInactiveInterval( timeout > 0 ? timeout : -1 );
                       sessionHandler.getSessionCookieConfig().setName( "_Mingle_Gum_" );

        context.setSessionHandler( sessionHandler );

        // Add authentication filter
        context.addFilter( AuthenticationFilter.class, "/*", java.util.EnumSet.of( javax.servlet.DispatcherType.REQUEST ) );

        // Add servlet
        context.addServlet( new ServletHolder( new ServletWS() ), "/*" );

        return context;
    }

    private ContextHandler createBridgeHandler( int timeout )
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/bridge" );

        // Configure session management
        SessionHandler sessionHandler = new SessionHandler();
                       sessionHandler.setMaxInactiveInterval( timeout > 0 ? timeout : -1 );
                       sessionHandler.getSessionCookieConfig().setName( "_Mingle_Gum_" );

        context.setSessionHandler( sessionHandler );

        // Add authentication filter
        context.addFilter( AuthenticationFilter.class, "/*", java.util.EnumSet.of( javax.servlet.DispatcherType.REQUEST ) );

        // Add WebSocket servlet
        context.addServlet( new ServletHolder( new GumWebSocketServlet() ), "/*" );

        return context;
    }

    private ContextHandler createUploadHandler( int timeout )
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/upload" );

        // Configure session management
        SessionHandler sessionHandler = new SessionHandler();
                       sessionHandler.setMaxInactiveInterval( timeout > 0 ? timeout : -1 );
                       sessionHandler.getSessionCookieConfig().setName( "_Mingle_Gum_" );

        context.setSessionHandler( sessionHandler );

        // Add authentication filter
        context.addFilter( AuthenticationFilter.class, "/*", java.util.EnumSet.of( javax.servlet.DispatcherType.REQUEST ) );

        // Add servlet
        context.addServlet( new ServletHolder( new ServletUpload() ), "/*" );

        return context;
    }

    private ContextHandler createLoginHandler( int timeout )
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/login" );

        // Configure session management
        SessionHandler sessionHandler = new SessionHandler();
                       sessionHandler.setMaxInactiveInterval( timeout > 0 ? timeout : -1 );
                       sessionHandler.getSessionCookieConfig().setName( "_Mingle_Gum_" );

        context.setSessionHandler( sessionHandler );

        // NO authentication filter here - login must be accessible without auth.
        context.addServlet( new ServletHolder( new ServletLogin() ), "/*" );

        return context;
    }

    private ContextHandler createLogoutHandler( int timeout )
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/logout" );

        // Configure session management
        SessionHandler sessionHandler = new SessionHandler();
                       sessionHandler.setMaxInactiveInterval( timeout > 0 ? timeout : -1 );
                       sessionHandler.getSessionCookieConfig().setName( "_Mingle_Gum_" );

        context.setSessionHandler( sessionHandler );

        // Add authentication filter (must be logged in to logout)
        context.addFilter( AuthenticationFilter.class, "/*", java.util.EnumSet.of( javax.servlet.DispatcherType.REQUEST ) );

        // Add servlet
        context.addServlet( new ServletHolder( new ServletLogout() ), "/*" );

        return context;
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

    private static void sendErrAndLogIt( HttpServletResponse response, Exception exc )
    {
        logger.log( ILogger.Level.SEVERE, exc );

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
                logger.log( ILogger.Level.SEVERE, e, "Failed to send error response" );
            }
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASSES - Servlets
    //------------------------------------------------------------------------//

    private class ServletLogin extends HttpServlet
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
                    int    length = request.getContentLength();    // getContentLength() returns -1 if the length is unknown
                    byte[] buffer = new byte[Math.max( length, 1024)];

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

                        if( length > -1 && totalRead != length )
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
    // INNER CLASS

    private class ServletLogout extends HttpServlet
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
    // INNER CLASS
    //------------------------------------------------------------------------//

    private class ServletUpload extends HttpServlet
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
            // Validate basedir parameter
            String fBaseDir = request.getParameter( "basedir" );

            if( fBaseDir == null || fBaseDir.trim().isEmpty() )
                fBaseDir = "";

            // Validate and sanitize basedir
            if( fBaseDir.contains( ".." ) || fBaseDir.contains( "//" ) || fBaseDir.contains( "\\" ) )
                throw new IOException( "Invalid base directory: " + fBaseDir );

            // Validate filename
            if( fileName == null || fileName.trim().isEmpty() )
                throw new IOException( "Filename cannot be empty" );

            // Remove path components from filename
            fileName = new File( fileName ).getName();

            if( fileName.contains( ".." ) || fileName.contains( "/" ) || fileName.contains( "\\" ) )
                throw new IOException( "Invalid filename: " + fileName );

            File baseDir = new File( Util.getServedFilesDir(), fBaseDir );
            File destDir = baseDir.getCanonicalFile();
            File rootDir = Util.getServedFilesDir().getCanonicalFile();

            // Ensure destination is within allowed directory
            if( ! destDir.getPath().startsWith( rootDir.getPath() ) )
                throw new IOException( "Access denied: " + fBaseDir );

            File fDestination = new File( destDir, fileName ).getCanonicalFile();

            // Final safety check
            if( ! fDestination.getPath().startsWith( rootDir.getPath() ) )
                throw new IOException( "Access denied" );

            // Add file size limit
            long maxSize = 100 * 1024 * 1024; // 100MB
            long totalBytes = 0;

            try( InputStream is = part.getInputStream() )
            {
                byte[] buffer = new byte[8192];
                int bytesRead;

                try( FileOutputStream os = new FileOutputStream( fDestination ) )
                {
                    while( (bytesRead = is.read( buffer )) != -1 )
                    {
                        totalBytes += bytesRead;

                        if( totalBytes > maxSize )
                        {
                            fDestination.delete(); // Clean up partial file
                            throw new IOException( "File too large" );
                        }

                        os.write( buffer, 0, bytesRead );
                    }
                }
            }
        }

        private void copyStream( InputStream is, OutputStream os ) throws IOException
        {
            byte[] buffer = new byte[1024 * 4];
            int    nRead;

            while( (nRead = is.read( buffer )) != -1 )
                os.write( buffer, 0, nRead );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    private class ServletWS extends HttpServlet
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
    // INNER CLASS
    //------------------------------------------------------------------------//

    public static class GumWebSocketServlet extends WebSocketServlet
    {
        /**
         * WebSocket idle timeout in milliseconds (5 minutes).
         * Jetty will automatically send ping frames to keep the connection alive.
         */
        private static final long WS_IDLE_TIMEOUT_MS = 5 * 60 * 1000;

        @Override
        public void configure( WebSocketServletFactory factory )
        {
            factory.register( CommBridge.class );
            factory.getPolicy().setMaxTextMessageSize( 64 * 1024 );

            // Enable idle timeout - Jetty handles ping/pong automatically when this is set.
            // If no activity occurs within this timeout, Jetty sends a ping frame.
            // If no pong is received, the connection is closed.
            factory.getPolicy().setIdleTimeout( WS_IDLE_TIMEOUT_MS );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Authentication Filter
    //------------------------------------------------------------------------//

    /**
 * Authentication filter that validates user sessions before allowing access.
 * Must be added to each ServletContextHandler that requires authentication.
 */
    public static class AuthenticationFilter implements javax.servlet.Filter
    {
        private static final String[] asPrivatePaths  = { "/gum/bridge", "/gum/upload", "/gum/ws", "/gum/board", "/gum/file_mgr", "/gum/user-files" };
        private static final String[] asPublicFileExt = { "model", "html", "js", "css", "png", "jpg", "jpeg", "svg", "ico", "map" };

        //------------------------------------------------------------------------//

        @Override
        public void init( javax.servlet.FilterConfig filterConfig ) throws ServletException
        {
            // Nothing to init?
        }

        @Override
        public void doFilter( javax.servlet.ServletRequest request,
                              javax.servlet.ServletResponse response,
                              javax.servlet.FilterChain chain )
                throws IOException, ServletException
        {
            HttpServletRequest  httpRequest  = (HttpServletRequest)  request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            String target = httpRequest.getRequestURI();

            try
            {
                boolean     isAllowed = false;
                HttpSession session   = null;

                try
                {
                    session = httpRequest.getSession( false );
                }
                catch( IllegalStateException e )
                {
                    logger.log( ILogger.Level.WARNING, "Session access failed: " + e.getMessage() );
                }

                if( session != null && session.getAttribute( KEY_USER_ID ) != null )
                {
                    isAllowed = true;
                }
                else if( isPublicPath( target ) )
                {
                    isAllowed = true;
                }
                else if( isClientAllowed( httpRequest ) )
                {
                    try
                    {
                        session = httpRequest.getSession( true );
                        session.setAttribute( KEY_USER_ID, "local_client" );
                        isAllowed = true;
                    }
                    catch( IllegalStateException e )
                    {
                        logger.log( ILogger.Level.WARNING, "Session creation failed: " + e.getMessage() );
                    }
                }

                if( isAllowed )
                {
                    if( logger.isLoggable( ILogger.Level.INFO ) )
                    {
                        logger.log( Level.INFO, String.format( "Access granted: %s %s from %s",
                                                               httpRequest.getMethod(), target, httpRequest.getRemoteAddr() ) );
                    }

                    chain.doFilter( request, response ); // Continue to servlet
                }
                else
                {
                    if( logger.isLoggable( ILogger.Level.WARNING ) )
                    {
                        logger.log( ILogger.Level.WARNING, String.format( "Unauthorized access attempt: %s %s from %s",
                                                                          httpRequest.getMethod(), target, httpRequest.getRemoteAddr() ) );
                    }

                    sendUnauthorizedResponse( httpResponse, "Authentication required" );
                }
            }
            catch( Exception exc )
            {
                logger.log( ILogger.Level.SEVERE, exc, "Error in authentication filter" );

                if( ! httpResponse.isCommitted() )
                {
                    httpResponse.setStatus( HttpServletResponse.SC_INTERNAL_SERVER_ERROR );
                    httpResponse.setContentType( "application/json" );
                    httpResponse.getWriter().write( "{\"error\":\"Authentication error\"}" );
                }
            }
        }

        @Override
        public void destroy()
        {
            // Nothing to cleanup?
        }

        //------------------------------------------------------------------------//

        private boolean isPublicPath( String path )
        {
            if( path == null )
                return false;

            if( ! path.startsWith( "/gum/" ) )
                return false;

            if( path.contains( ".." ) || path.contains( "//" ) )
                return false;

            if( UtilColls.contains( asPublicFileExt, UtilIO.getExtension( path ).toLowerCase() ) )   // Returns just the extension (not including) the '.')
                return true;

            for( String p : asPrivatePaths )
            {
                if( path.startsWith( p ) )
                    return false;
            }

            return true;
        }

        private boolean isClientAllowed( HttpServletRequest request )
        {
            String      xForwardedFor = request.getHeader( "X-Forwarded-For" );
            InetAddress clientIP      = null;

            if( (xForwardedFor != null) && (! xForwardedFor.isEmpty()) )
            {
                try
                {
                    String s = xForwardedFor.split( "," )[0].trim();
                    clientIP = InetAddress.getByName( s );
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
                return UtilComm.isClientAllowed( ServerConfig.getClientIPScope(), clientIP );
            }
            catch( SocketException ex )
            {
                // Nothing to do
            }

            return false;
        }

        private void sendUnauthorizedResponse( HttpServletResponse response, String message )
                throws IOException
        {
            if( response.isCommitted() )
            {
                return;
            }

            response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
            response.setContentType( "application/json; charset=utf-8" );
            response.setHeader( "Cache-Control", "no-store, no-cache, must-revalidate" );
            response.setHeader( "WWW-Authenticate", "FormBased" );

            JsonObject jsonResponse = Json.object()
                    .add( "error", message )
                    .add( "status", 401 )
                    .add( "loginUrl", "/gum/login.html" );

            response.getWriter().write( jsonResponse.toString() );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    /**
 * Centralized server configuration shared between HttpServer and components.
 * Thread-safe singleton pattern for managing server-wide settings.
 */
    private static final class ServerConfig
    {
        private static volatile short clientIPScope = -1;

        public static void setClientIPScope( String scope )
        {
            clientIPScope = UtilComm.clientScope( scope );
        }

        public static short getClientIPScope()
        {
            if( clientIPScope == -1 )
                throw new IllegalStateException();

            return clientIPScope;
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    private static class CustomErrorHandler extends ErrorHandler
    {
        @Override
        public void handle( String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response ) throws IOException
        {
            String sURI = request.getRequestURI();

            if( sURI.endsWith( ".js.map" ) ||      // Ignore JS debugging file,
                sURI.endsWith( "favicon.ico" ) )   // also this stupidity.
                return;

            Throwable cause  = (Throwable) request.getAttribute( "javax.servlet.error.exception"   );
            Integer   status = (Integer)   request.getAttribute( "javax.servlet.error.status_code" );

            logger.log( Level.SEVERE, "=== ERROR HANDLER TRIGGERED ===\n"+
                                      "Target: " + target    +'\n'+
                                      "Status: " + status    +'\n'+
                                      "Request URI: " + sURI +'\n' );

            if( cause != null )
                logger.log( Level.SEVERE, cause, "Exception: " + cause.getClass().getName() );

            cause = (Throwable) request.getAttribute( "javax.servlet.error.exception" );

            if( cause != null )  logger.log( Level.SEVERE, cause, "CRITICAL SERVER ERROR:\n" );
            else                 logger.log( Level.SEVERE, "Server error with no exception attached. Target: " + target +", Status: "+ status );

            if( ! response.isCommitted() )
            {
                response.setContentType( "application/json" );

                String errorMsg = (cause != null) ? cause.getMessage() : "Unknown error on target: " + target +", Status: "+ status;

                response.getWriter().write( "{\"error\":"+ Json.value( errorMsg ).toString() + "}" );   // Safe JSON quoting
            }

            baseRequest.setHandled( true );
        }
    }
}