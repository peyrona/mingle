
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.Map;
import javax.servlet.MultipartConfigElement;
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
    HttpServer( String host, int httpPort, String allowed, int timeout, int httpsPort, String keystorePath, String keystorePassword ) throws Exception
    {
        ServerConfig.setClientIPScope( allowed );    // Store in shared configuration
        RemoteGrant.get();                           // Initialise the off-LAN remote-access grant (warns at startup if misconfigured)

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

            // Recorded before the contexts are built, because it decides whether their session cookie
            // is marked "Secure" (see newSessionHandler) and it must be known by then.
            ServerConfig.setTlsAvailable( true );
        }

        ContextHandlerCollection contexts = new ContextHandlerCollection();

        // Add Handlers to the collection
        contexts.addHandler( createLoginHandler( timeout ) );        // /gum/login        - NO AUTH FILTER
        contexts.addHandler( createRemoteHandler() );                // /gum/remote       - NO AUTH FILTER (redirects to /gum/login)
        contexts.addHandler( createWsHandler( timeout ) );           // /gum/ws/*         - WITH AUTH FILTER
        contexts.addHandler( createBridgeHandler( timeout ) );       // /gum/bridge/*     - WITH AUTH FILTER
        contexts.addHandler( createUploadHandler( timeout ) );       // /gum/upload       - WITH AUTH FILTER
        contexts.addHandler( createFileManagerHandler( timeout ) );  // /gum/user-files/_mgr/* - WITH AUTH FILTER
        contexts.addHandler( createServedFilesHandler( timeout ) );  // /gum/user-files/*      - WITH AUTH FILTER
        contexts.addHandler( createBoardHandler( timeout ) );        // /gum/board/*      - WITH AUTH FILTER
        contexts.addHandler( createGumHandler( timeout ) );          // /gum              - WITH AUTH FILTER
        contexts.addHandler( createLogoutHandler( timeout ) );       // /gum/logout       - WITH AUTH FILTER

        // Reverse-proxy handlers — optional, driven by "monitoring.proxies" in config.json
        JsonArray proxies = UtilSys.getConfig().get( "monitoring", "proxies", Json.array() );

        for( JsonValue entry : proxies )
        {
            JsonObject proxy  = entry.asObject();
            String     path   = proxy.getString( "path",   null );
            String     target = proxy.getString( "target", null );

            if( UtilStr.isNotEmpty( path ) && UtilStr.isNotEmpty( target ) )
                contexts.addHandler( createProxyHandler( path, target, timeout ) );
        }

        // Set the Root Handler (NO SessionHandler wrapper, NO AuthWrapper!)
        server.setHandler( contexts );
        server.setErrorHandler( new CustomErrorHandler() );

        // Show GUM configuration
        String sServer = "http://" + resolveDisplayHost( host ) + ':' + httpPort + "/gum/";

        String sMsg = "Dashboards manager : " + sServer + "index.html\n" +
                      "Dashboard's folder : " + Util.getBoardsDir().getCanonicalPath() + "/\n" +
                      "Serving files from : " + Util.getServedFilesDir().getCanonicalPath() + "/\n" +
                      "    * at context   : " + sServer + "user-files/\n" +
                      "    * UI manager   : " + sServer + "user-files/_mgr/index.html\n";

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
        String            sNow      = '[' + LocalDateTime.now().format( formatter ) +']';

        Runtime runtime   = Runtime.getRuntime();
        long    nMaxRAM   = runtime.maxMemory()   / UtilUnit.MEGA_BYTE;
        long    nTotRAM   = runtime.totalMemory() / UtilUnit.MEGA_BYTE;
        long    nUsedRAM  = (runtime.totalMemory() - runtime.freeMemory()) / UtilUnit.MEGA_BYTE;

        String  msg = sNow + " JVM RAM: "+ nUsedRAM +" Mb used of "+ nTotRAM +" Mb (Max to alloc: "+ nMaxRAM +" Mb)\n"+
                      sNow + " Gum started...";

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
        SessionHandler sessionHandler = newSessionHandler( timeout );

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
        SessionHandler sessionHandler = newSessionHandler( timeout );

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
        SessionHandler sessionHandler = newSessionHandler( timeout );

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
        SessionHandler sessionHandler = newSessionHandler( timeout );

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
        SessionHandler sessionHandler = newSessionHandler( timeout );

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
        SessionHandler sessionHandler = newSessionHandler( timeout );

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

        // Accept a POST to the bare context path "/gum/upload" (no trailing slash). By default Jetty
        // answers such a request with a 302 redirect to "/gum/upload/" at the context scope, before the
        // servlet runs; a client following that redirect on a POST drops the multipart body, so the
        // upload silently fails. Enabling this makes both "/gum/upload" and "/gum/upload/" reach the servlet.
        context.setAllowNullPathInfo( true );

        // Configure session management
        SessionHandler sessionHandler = newSessionHandler( timeout );

        context.setSessionHandler( sessionHandler );

        // Add authentication filter
        context.addFilter( AuthenticationFilter.class, "/*", java.util.EnumSet.of( javax.servlet.DispatcherType.REQUEST ) );

        // Add servlet with multipart configuration for file uploads
        ServletHolder uploadHolder = new ServletHolder( new ServletUpload() );

        // IMPORTANT: Add servlet FIRST, then configure multipart
        // In Jetty, getRegistration() requires the servlet to be registered first
        context.addServlet( uploadHolder, "/*" );

        uploadHolder.getRegistration().setMultipartConfig(
            new MultipartConfigElement( System.getProperty( "java.io.tmpdir" ),
                                        100 * UtilUnit.MEGA_BYTE,
                                        150 * UtilUnit.MEGA_BYTE,
                                          1 * UtilUnit.MEGA_BYTE ) );

        return context;
    }

    private ContextHandler createLoginHandler( int timeout )
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/login" );

        // Accept a POST to the bare context path "/gum/login" (no trailing slash). Otherwise Jetty answers
        // with a 302 to "/gum/login/" before the servlet runs; a client following that on a POST turns it
        // into a GET and drops the JSON body, so login silently fails. (Same fix as "/gum/upload".)
        context.setAllowNullPathInfo( true );

        // Configure session management
        SessionHandler sessionHandler = newSessionHandler( timeout );

        context.setSessionHandler( sessionHandler );

        // NO authentication filter here - login must be accessible without auth.
        context.addServlet( new ServletHolder( new ServletLogin() ), "/*" );

        return context;
    }

    /**
     * Creates the {@code /gum/remote} context — a bare redirect to {@code /gum/login}, kept only for
     * links and QR codes printed before the cookie bridge moved there. See {@link ServletRemote}.
     *
     * @return A configured {@link ContextHandler} ready to be added to the server.
     */
    private ContextHandler createRemoteHandler()
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.NO_SESSIONS );
                              context.setContextPath( "/gum/remote" );

        // Accept the bare context path "/gum/remote": that is the link handed to guests (often as a QR
        // code), and Jetty would otherwise answer it with a 302 to "/gum/remote/" before the servlet runs.
        context.setAllowNullPathInfo( true );

        context.addServlet( new ServletHolder( new ServletRemote() ), "/*" );

        return context;
    }


    private ContextHandler createLogoutHandler( int timeout )
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( "/gum/logout" );

        // Configure session management
        SessionHandler sessionHandler = newSessionHandler( timeout );

        context.setSessionHandler( sessionHandler );

        // Add authentication filter (must be logged in to logout)
        context.addFilter( AuthenticationFilter.class, "/*", java.util.EnumSet.of( javax.servlet.DispatcherType.REQUEST ) );

        // Add servlet
        context.addServlet( new ServletHolder( new ServletLogout() ), "/*" );

        return context;
    }

    /**
     * Creates a reverse-proxy context handler that forwards all requests under
     * {@code contextPath} to the given {@code target} base URL.
     *
     * @param contextPath Local context path (e.g. {@code /gum/proxy/rpi}).
     * @param target      Base URL of the downstream server (e.g. {@code http://192.168.7.9:8080}).
     * @param timeout     Session timeout in seconds; {@code 0} means no expiry.
     * @return A configured {@link ContextHandler} ready to be added to the server.
     */
    private ContextHandler createProxyHandler( String contextPath, String target, int timeout )
    {
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
                              context.setContextPath( contextPath );

        // Configure session management
        SessionHandler sessionHandler = newSessionHandler( timeout );

        context.setSessionHandler( sessionHandler );

        // Add authentication filter (proxied targets are protected the same way as other Gum routes)
        context.addFilter( AuthenticationFilter.class, "/*", java.util.EnumSet.of( javax.servlet.DispatcherType.REQUEST ) );

        // Add the proxy servlet
        context.addServlet( new ServletHolder( new ProxyServlet( target, contextPath ) ), "/*" );

        return context;
    }

    //------------------------------------------------------------------------//
    // PRIVATE STATIC METHODS

    /**
     * Builds a {@link SessionHandler} with a consistently hardened session cookie: fixed name,
     * {@code HttpOnly}, {@code SameSite=Lax} and — once a TLS connector exists — {@code Secure}.
     * <p>
     * {@code SameSite} is the practical defence against a malicious page riding an authenticated
     * session (CSRF). {@code Secure} is applied only when {@code monitoring.ssl} is configured: adding
     * it to a deployment that speaks plain HTTP alone would stop the browser from ever returning the
     * cookie. Note that enabling TLS does not switch HTTP off — the plain connector is always added —
     * so from then on the session travels over HTTPS only, and on-LAN HTTP clients simply fall back to
     * being trusted by IP scope on every request.
     *
     * @param timeout Session inactivity timeout in seconds; {@code 0} (or less) means never expire.
     * @return A configured session handler.
     */
    private static SessionHandler newSessionHandler( int timeout )
    {
        SessionHandler handler = new SessionHandler();
                       handler.setMaxInactiveInterval( timeout > 0 ? timeout : -1 );

        javax.servlet.SessionCookieConfig cfg = handler.getSessionCookieConfig();
                                          cfg.setName( "_Mingle_Gum_" );
                                          cfg.setHttpOnly( true );
                                          cfg.setSecure( ServerConfig.isTlsAvailable() );
                                          cfg.setComment( org.eclipse.jetty.http.HttpCookie.SAME_SITE_LAX_COMMENT );   // Jetty 9.4 reads this magic comment to emit "SameSite=Lax"

        return handler;
    }

    /**
     * Whether the request's real socket peer is inside the trusted {@code monitoring.allow} scope.
     * Uses {@link HttpServletRequest#getRemoteAddr()} only — never {@code X-Forwarded-For} — so it
     * cannot be spoofed by a header.
     *
     * @param request The incoming request.
     * @return {@code true} if the real client address is on the trusted LAN (localhost included).
     */
    private static boolean isRealClientOnLan( HttpServletRequest request )
    {
        try
        {
            return UtilComm.isClientAllowed( ServerConfig.getClientIPScope(),
                                             InetAddress.getByName( request.getRemoteAddr() ) );
        }
        catch( Exception e )
        {
            return false;
        }
    }

    /**
     * Resolves the host address to display in startup messages.
     * <p>
     * When the server binds to {@code 0.0.0.0} (all interfaces), that address is meaningless
     * to the user. This method iterates network interfaces directly so it can filter virtual
     * and container interfaces (docker, veth, virbr, br-) and prefer common LAN ranges
     * ({@code 192.168.x.x} first, then {@code 10.x.x.x}, then {@code 172.16–31.x.x}).
     * Falls back to {@code localhost} if no suitable address is found.
     *
     * @param bindHost The host address the server is bound to.
     * @return A human-friendly host string suitable for display in URLs.
     */
    private static String resolveDisplayHost( String bindHost )
    {
        if( !"0.0.0.0".equals( bindHost ) )
            return bindHost;

        String best      = null;
        int    bestScore = Integer.MAX_VALUE;

        try
        {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();

            if( ifaces != null )
            {
                while( ifaces.hasMoreElements() )
                {
                    NetworkInterface iface = ifaces.nextElement();
                    String           name  = iface.getName().toLowerCase();

                    if( name.startsWith( "docker" ) || name.startsWith( "veth" ) ||
                        name.startsWith( "virbr"  ) || name.startsWith( "br-"  ) )
                        continue;

                    Enumeration<InetAddress> addrs = iface.getInetAddresses();

                    while( addrs.hasMoreElements() )
                    {
                        InetAddress addr = addrs.nextElement();

                        if( addr.isLoopbackAddress() || !(addr instanceof Inet4Address) )
                            continue;

                        if( !addr.isSiteLocalAddress() )
                            continue;

                        int score = scoreAddress( addr );

                        if( score < bestScore )
                        {
                            best      = addr.getHostAddress();
                            bestScore = score;
                        }
                    }
                }
            }
        }
        catch( SocketException ignored )
        {
            // Fall through to default
        }

        return (best != null) ? best : "localhost";
    }

    /**
     * Returns a priority score for a LAN IPv4 address (lower = preferred).
     * <ul>
     *   <li>1 – {@code 192.168.x.x} (most common home/office LAN)</li>
     *   <li>2 – {@code 10.x.x.x}</li>
     *   <li>3 – {@code 172.16–31.x.x} or other site-local</li>
     * </ul>
     *
     * @param addr A site-local IPv4 address.
     * @return Priority score.
     */
    private static int scoreAddress( InetAddress addr )
    {
        byte[] b      = addr.getAddress();
        int    first  = b[0] & 0xFF;
        int    second = b[1] & 0xFF;

        if( first == 192 && second == 168 )  return 1;
        if( first == 10 )                    return 2;
        return 3;
    }

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

    /**
     * Answers with a plain-text message. Used by the endpoints a human reaches by typing (or scanning) a
     * URL rather than through the SPA, where a JSON body would be shown to the user verbatim.
     *
     * @param response The response to write to.
     * @param status   The HTTP status code.
     * @param message  The message, meant to be read by whoever made the request.
     * @throws IOException if the response cannot be written.
     */
    private static void sendPlain( HttpServletResponse response, int status, String message ) throws IOException
    {
        response.setStatus( status );
        response.setContentType( "text/plain; charset=utf-8" );
        response.setHeader( "Cache-Control", "no-store" );
        response.getWriter().write( message );
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
        /** Where {@link #buildSignInPage} loads its page template from — see that file's own header comment. */
        private static final String LOGIN_TEMPLATE = "{*home*}lib/gum/templates/login.html";

        @Override
        protected void doPost( HttpServletRequest request, HttpServletResponse response )
                throws ServletException, IOException
        {
            try
            {
                AccessContext ctx = AccessContext.of( request, response );

                // Password login accepts a plaintext credential, so it must never travel off-LAN in the
                // clear. Over TLS that objection disappears, and admitting it is what spares the owner
                // the trip home: they sign in from wherever they are and the grant is stored straight
                // away under the public name. On plain HTTP the LAN remains the only accepted origin;
                // this same endpoint's cookie bridge (see handleBridge()) is how a GUEST's grant reaches
                // the public name instead — an admin has no equivalent need for it (see the class doc).
                if( ! (isRealClientOnLan( request ) || request.isSecure()) )
                {
                    response.setStatus( HttpServletResponse.SC_FORBIDDEN );
                    response.setContentType( "application/json" );
                    response.getWriter().write( "{\"error\":\"Login is only available from the local network, or over a secure (HTTPS) connection\"}" );
                    return;
                }

                // Guessing the master password grants full control of the house, and this endpoint is
                // reachable from the open internet whenever HTTPS is configured — a guest-subnet block
                // alone would stop the least likely attacker (a paying guest) while leaving the more
                // likely one (anyone on the internet) with unlimited tries. LoginRateLimiter throttles
                // the credential itself, uniformly, regardless of where the request comes from.
                long secondsLocked = LoginRateLimiter.tryAttempt( ctx.clientIp() );

                if( secondsLocked > 0 )
                {
                    response.setStatus( 429 );   // Too Many Requests (not in HttpServletResponse's SC_* set)
                    response.setContentType( "application/json" );
                    response.getWriter().write( "{\"error\":\"Too many attempts. Try again in "+ secondsLocked +" second(s).\"}" );
                    return;
                }

                String contentType = request.getContentType();

                if( contentType != null && contentType.startsWith( "application/json" ) )
                {
                    int    length = request.getContentLength();    // getContentLength() returns -1 if the length is unknown
                    byte[] buffer;

                    if( length > 0 )
                    {
                        buffer = new byte[length];

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
                                throw new IOException( "Incomplete request data received: expected " + length + ", got " + totalRead );
                        }
                    }
                    else    // Unknown/chunked content - read all available
                    {
                        try( InputStream is = request.getInputStream();
                             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream() )
                        {
                            byte[] temp = new byte[4096];
                            int    bytesRead;

                            while( (bytesRead = is.read( temp )) != -1 )
                                baos.write( temp, 0, bytesRead );

                            buffer = baos.toByteArray();
                        }

                        if( buffer.length == 0 )
                            throw new IOException( "No request data received" );
                    }

                    String   sj   = new String( buffer, StandardCharsets.UTF_8 );
                    UtilJson uj   = new UtilJson( sj );
                    String   pwd  = uj.getString( "password", null );
                    boolean  keep = uj.getBoolean( "keep", false );

                    processLogin( request, response, ctx, pwd, keep );
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

        /**
         * Serves the one sign-in page for both credentials Gum knows: the master password and a
         * dashboard's own password. It exists because <b>no other page in Gum ever posts here</b>. The
         * dashboard manager has its own dialog, but that one validates against
         * {@code GET /gum/ws/board} with an HMAC-signed payload and never calls {@code /gum/login}, so
         * it cannot mint a grant. Without this page neither the admin grant nor a board-viewer one —
         * the whole point of both being to work later, from outside — could ever be obtained.
         * <p>
         * One field, no username: the server tells the two credentials apart itself in
         * {@link #processLogin}, by which one matches. This used to be two separate pages — one for the
         * master password, one ({@code /gum/view}) for a dashboard's own — before realising the split
         * bought nothing: both need the exact same reachability gate below, both mint through the same
         * {@link RemoteGrant}, and the only real difference (what to do once signed in) is decided
         * server-side anyway, not by which door was used to get there.
         * <p>
         * Reachable from the trusted LAN, or from anywhere over TLS (same reasoning as {@link #doPost}) —
         * including a guest-subnet device: the owner must still be able to reach this page while standing
         * in their own guest room. What actually guards the credential is {@link LoginRateLimiter}, not
         * where the request comes from.
         * <p>
         * A third, credential-less path also lives here: a guest device proves presence on the LAN
         * instead of typing anything, via a {@code t} or {@code net} query parameter — see
         * {@link #handleBridge}. It used to be a separate endpoint, {@code /gum/remote}; folded in here
         * so every human path into Gum starts at one URL, {@code /gum/login} — {@code /gum/remote} is
         * kept alive only as a redirect to this one, for links already printed or saved as a QR code.
         */
        @Override
        protected void doGet( HttpServletRequest request, HttpServletResponse response )
                throws ServletException, IOException
        {
            // A bare 't' or 'net' parameter means this is the cookie bridge, not a human asking for the
            // sign-in form — see handleBridge(). Checked first and returns unconditionally: the bridge
            // has its own reachability rules (LAN on the first hop, a signed token on the second) that
            // must not fall through to the plain-sign-in-page gate below.
            if( (request.getParameter( "t" ) != null) || (request.getParameter( "net" ) != null) )
            {
                try
                {
                    handleBridge( request, response, request.getParameter( "t" ) );
                }
                catch( Exception exc )
                {
                    sendErrAndLogIt( response, exc );
                }
                return;
            }

            // Viewing the form is harmless from anywhere (guest room included — the owner must still be
            // able to reach it while standing in their own house); it is the credential attempt itself,
            // gated below in doPost/processLogin by LoginRateLimiter, that needs guarding. This check is
            // purely about the password never travelling in the clear over plain HTTP from off-LAN.
            if( ! (isRealClientOnLan( request ) || request.isSecure()) )
            {
                sendPlain( response, HttpServletResponse.SC_FORBIDDEN,
                           "Sign-in is only available from the local network, or over a secure (HTTPS) connection." );
                return;
            }

            response.setStatus( HttpServletResponse.SC_OK );
            response.setContentType( "text/html; charset=utf-8" );
            response.setHeader( "Cache-Control", "no-store" );
            response.getWriter().write( buildSignInPage( request.getParameter( "board" ) ) );
        }

        /**
         * Builds the sign-in page.
         *
         * @param boardHint the {@code board} query parameter, if the link named one — shown only as a
         *                  courtesy so whoever opens a shared link knows what they are signing in to see;
         *                  it authorizes nothing by itself, the submitted password is what decides that.
         * @return a complete HTML document.
         */
        private String buildSignInPage( String boardHint )
        {
            RemoteGrant grant = RemoteGrant.get();

            if( ! grant.isAdminEnabled() && ! grant.isViewerEnabled() )
            {
                return """
                       <!DOCTYPE html><html lang="en"><head><meta charset="utf-8">
                       <title>Gum - Sign in</title></head><body>
                       <p>Remote access is not available: set a non-empty <code>monitoring.shared_secret</code> and,
                       for full access, <code>monitoring.master_password</code> with a non-zero
                       <code>monitoring.remote_access.admin_ttl</code>; or, for a dashboard's own password, a
                       non-zero <code>monitoring.remote_access.view_ttl</code>. Then restart Gum.</p>
                       </body></html>
                       """;
            }

            String hint = UtilStr.isEmpty( boardHint ) ? ""
                        : "<p class=\"hint\">Signing in to see: <b>"+ escapeHtml( boardHint ) +"</b></p>";

            try
            {
                return UtilIO.getAsText( LOGIN_TEMPLATE ).replace( "__HINT__", hint );
            }
            catch( IOException ioe )
            {
                throw new UncheckedIOException( "Could not read the sign-in page template at '"+ LOGIN_TEMPLATE +"'", ioe );
            }
        }

        /**
         * Escapes text taken from the query string before it is written into the page — the
         * {@code board} hint is display-only, never trusted for anything, but it still must not be able
         * to break out of the markup it is spliced into.
         */
        private String escapeHtml( String s )
        {
            return s.replace( "&", "&amp;" ).replace( "<", "&lt;" ).replace( ">", "&gt;" ).replace( "\"", "&quot;" );
        }

        /**
         * Tries the master password first, then — if that does not match — every dashboard's own
         * password, and mints whichever grant fits. The two are tried in this order and reported with
         * the same "wrong password" wording on failure either way: telling them apart would let this
         * endpoint be used to probe whether a master password is even set.
         */
        private void processLogin( HttpServletRequest request, HttpServletResponse response, AccessContext ctx, String password, boolean keepSignedIn ) throws IOException
        {
            String      requestedWith = request.getHeader( "X-Requested-With" );
            boolean     isAJAXRequest = "XMLHttpRequest".equals( requestedWith );
            RemoteGrant grant         = RemoteGrant.get();

            if( ! isAJAXRequest )
            {
                response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
                response.getWriter().write( "Unauthorized: Access denied" );
                return;
            }

            if( grant.isAdminEnabled() && isMaster( password ) )
            {
                LoginRateLimiter.onSuccess( ctx.clientIp() );

                grant.issueAdmin( ctx, keepSignedIn );

                response.setStatus( HttpServletResponse.SC_OK );
                response.setContentType( "application/json" );
                response.getWriter().write( "{\"role\":\"admin\"}" );
                return;
            }

            // Empty password IS looked up: a board with no password is, by the admin's own choice when
            // leaving it blank in the dashboard editor, meant to be open to whoever asks — same as it
            // already is for anyone on the LAN or holding an admin/HMAC session (ServiceBoard.doGet's
            // "list" action has always included the "" key for them). This just extends that same
            // choice to someone who is never on the LAN at all.
            String[] boards = grant.isViewerEnabled() ? ServiceBoard.boardsForPassword( password )
                                                       : new String[0];

            if( boards.length > 0 )
            {
                LoginRateLimiter.onSuccess( ctx.clientIp() );

                grant.issueViewer( ctx, boards );

                response.setStatus( HttpServletResponse.SC_OK );
                response.setContentType( "application/json" );
                response.getWriter().write( "{\"role\":\"viewer\",\"boards\":"+ UtilJson.toJSON( boards ).toString() +"}" );
                return;
            }

            // No onFailure() call here: LoginRateLimiter.tryAttempt(), above, already counted this
            // request as a tentative failure atomically with the lock check, so this attempt is
            // already reflected in the throttle regardless of how it turns out.
            response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
            response.getWriter().write( "Unauthorized: Access denied" );
        }

        private boolean isMaster( String password ) throws IOException
        {
            String sMasterPwd = UtilSys.getConfig().get( "monitoring", "master_password", "" );

            // isAdminEnabled() (checked by every caller of this method) already guarantees sMasterPwd is
            // non-empty, so this is a plain equality check — no "empty master password accepts anything"
            // fallback here, unlike ServiceBoard.isMasterPwd(): that one also gates localhost/on-LAN board
            // editing, where a not-yet-configured master password must not lock the owner out entirely.
            // This endpoint has no such need — isAdminEnabled() already refuses to mint anything without
            // one — so nothing here should accept "any password" ever.
            return sMasterPwd.equals( password );
        }

        //------------------------------------------------------------------------//
        // COOKIE BRIDGE — folded in from the former /gum/remote endpoint.
        //
        // Hands a device that is provably on the trusted LAN a grant cookie already stored under the
        // PUBLIC host name. It exists because cookies are host-only. A grant obtained at
        // http://192.168.1.5:8080 is never sent to http://my.host.org:8080, and Gum cannot simply issue
        // one there instead: a router that hairpins with source NAT rewrites the origin of an in-house
        // request to the public name, so Gum sees it arrive from the WAN address and refuses to trust
        // it. The two halves of the problem cancel out if the proof of presence travels in the URL
        // rather than in the source address:
        //   1. On the room's WiFi, the client opens  http://192.168.1.5:8080/gum/login?net=7
        //   2. Gum trusts the source address, mints a 120-second token and redirects to
        //          http://my.host.org:8080/gum/login?net=7&t=<role>:<subnets>:<expiry>:<hmac>
        //   3. That request leaves and comes back through the router, arriving with the public address.
        //      The token — not the address — is what proves the client was just on the LAN.
        //   4. Gum validates it, sets the cookie UNDER THE PUBLIC NAME and redirects to the guest page.
        //   5. The client bookmarks that page. It now works both at home and away.
        // Guest-only: an admin re-authenticates directly under whichever host name is in use instead
        // (see doPost/doGet above) now that plain password login is accepted over TLS from anywhere —
        // so this never mints or honours an admin token, only ever a guest one.

        /**
         * Entry point for both hops of the bridge (see above). {@code token} is {@code null}/empty on
         * the first hop (LAN) and the signed token on the second (public host name).
         */
        private void handleBridge( HttpServletRequest request, HttpServletResponse response, String token ) throws IOException
        {
            RemoteGrant   grant = RemoteGrant.get();
            AccessContext ctx   = AccessContext.of( request, response );

            if( ! grant.isBridgeEnabled() )
            {
                sendPlain( response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           "Remote access is not available: set 'monitoring.remote_access.public_url', a non-empty "+
                           "'monitoring.shared_secret' and a non-zero 'monitoring.remote_access.guest_ttl' in "+
                           "config.json, then restart Gum." );
                return;
            }

            if( UtilStr.isEmpty( token ) )  issueToken(  request, response, ctx, grant );
            else                            redeemToken( request, response, ctx, grant, token );
        }

        /**
         * First hop, reached under the LAN address: attests the client's presence and sends it to the
         * public host name carrying that attestation.
         */
        private void issueToken( HttpServletRequest request, HttpServletResponse response,
                                 AccessContext ctx, RemoteGrant grant ) throws IOException
        {
            // Presence on the trusted LAN is precisely what is being attested here, so it is checked
            // against the real socket peer only (isRealClientOnLan never honours X-Forwarded-For). A
            // guest has no other way in — this IS their credential. An administrator has a real one
            // (the master password) that works regardless of location, so rather than a dead end this
            // sends them to the very same sign-in form every other admin entry point uses, already
            // aimed at this room: correct password in, and RemoteGrant.mayAccess()/mayReach() wave an
            // admin grant past guest_paths and bind_subnet unconditionally, no bridge involved.
            if( ! isRealClientOnLan( request ) )
            {
                String landing = grant.guestLandingPath();

                if( landing == null )
                    landing = Util.getDashboardManagerContext() + "/index.html";

                String query = carriedQuery( request );   // still carries 'net' — 't' is never present here

                if( ! query.isEmpty() )
                    landing += (landing.indexOf( '?' ) < 0 ? "?" : "&") + query;

                response.setHeader( "Cache-Control", "no-store" );
                response.sendRedirect( "/gum/login?return=" + URLEncoder.encode( landing, StandardCharsets.UTF_8 ) );
                return;
            }

            String token = grant.issueBridgeToken( ctx );

            if( token == null )
            {
                sendPlain( response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           "No remote access can be granted to this device: guest access is disabled "+
                           "('monitoring.remote_access.guest_ttl' is 0), or this is not a plain IPv4 connection." );
                return;
            }

            String query = carriedQuery( request );

            response.setHeader( "Cache-Control", "no-store" );
            response.sendRedirect( grant.publicUrl() + "/gum/login?t=" + URLEncoder.encode( token, StandardCharsets.UTF_8 ) +
                                   (query.isEmpty() ? "" : "&" + query) );
        }

        /**
         * Second hop, reached under the public host name: turns a valid token into the cookie it stands
         * for and sends the client on to the page it may use, carrying the original query across.
         */
        private void redeemToken( HttpServletRequest request, HttpServletResponse response,
                                  AccessContext ctx, RemoteGrant grant, String token ) throws IOException
        {
            RemoteGrant.Grant granted = grant.consumeBridgeToken( token );

            if( ! grant.issueFrom( ctx, granted ) )
            {
                sendPlain( response, HttpServletResponse.SC_FORBIDDEN,
                           "This remote-access link is not valid any more. Open it again while connected to the local network." );
                return;
            }

            // Never a destination taken from the request: the landing page is Gum's own, so this redirect
            // cannot be pointed at somebody else's site. Only the query is carried over, re-encoded.
            String landing = granted.isAdmin() ? null : grant.guestLandingPath();

            if( landing == null )
                landing = Util.getDashboardManagerContext() + "/index.html";

            String query = carriedQuery( request );

            if( ! query.isEmpty() )
                landing += (landing.indexOf( '?' ) < 0 ? "?" : "&") + query;

            response.setHeader( "Cache-Control", "no-store" );
            response.sendRedirect( landing );
        }

        /**
         * Rebuilds the request's query string, dropping the bridge's own {@code t} parameter, so that
         * whatever the caller put there survives both hops of the redirect.
         * <p>
         * This is what makes a per-room link such as {@code /gum/login?net=7} work: the guest page
         * reads {@code net} to know which room's ExEn to talk to, and without carrying it across, a
         * guest arriving under the public host name would land on a page unable to identify its room.
         * Names and values are re-encoded rather than copied verbatim, since the result goes into a
         * {@code Location} header.
         *
         * @param request the incoming request.
         * @return the re-encoded query, with no leading separator; {@code ""} when there is nothing to carry.
         */
        private String carriedQuery( HttpServletRequest request )
        {
            StringBuilder sb = new StringBuilder( 64 );

            for( Map.Entry<String,String[]> entry : request.getParameterMap().entrySet() )
            {
                if( "t".equals( entry.getKey() ) )
                    continue;

                for( String value : entry.getValue() )
                {
                    if( sb.length() > 0 )
                        sb.append( '&' );

                    sb.append( URLEncoder.encode( entry.getKey(), StandardCharsets.UTF_8 ) )
                      .append( '=' )
                      .append( URLEncoder.encode( value, StandardCharsets.UTF_8 ) );
                }
            }

            return sb.toString();
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS

    /**
     * Backward-compatible alias for the cookie bridge, which now lives at {@code /gum/login} (see
     * {@code ServletLogin.handleBridge}) so every human-facing entry point uses one URL. Kept only so a
     * link or QR code already printed for a guest room (pointing at {@code /gum/remote?net=N}) keeps
     * working: it does nothing but redirect to the equivalent {@code /gum/login} URL, query string and
     * all. New links should be generated as {@code /gum/login?net=N} directly — the dashboard manager
     * and any future guest-link generator never need to know {@code /gum/remote} still exists.
     */
    private class ServletRemote extends HttpServlet
    {
        @Override
        protected void doGet( HttpServletRequest request, HttpServletResponse response ) throws IOException
        {
            String query = request.getQueryString();

            response.setHeader( "Cache-Control", "no-store" );
            response.sendRedirect( "/gum/login" + ((query == null) ? "" : "?" + query) );
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

            // Dropping the session alone would leave a long-lived administrator grant in place, so
            // logging out would not actually log anybody out.
            RemoteGrant.get().revoke( AccessContext.of( request, response ) );

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
        private static final String[] asPrivatePaths  = { "/gum/bridge", "/gum/upload", "/gum/ws", "/gum/board", "/gum/user-files/_mgr", "/gum/user-files", "/gum/proxy" };

        // Gum's own bundled UI that lives *under* a private prefix and must still load without a session
        // (its own operations authenticate separately). Only the File-Manager UI qualifies. The trailing
        // slash is required so an uploaded file merely named "_mgr..." cannot borrow the exemption.
        private static final String[] asPublicUiSubPaths = { "/gum/user-files/_mgr/" };

        /** Single shared validator: HMAC is verified here, at the choke point, for every private path. */
        private static final HmacAuthenticator hmacAuth = new HmacAuthenticator();

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

                AccessContext       ctx    = AccessContext.of( httpRequest, httpResponse );
                RemoteGrant         grant = RemoteGrant.get();

                try
                {
                    session = httpRequest.getSession( false );
                }
                catch( IllegalStateException e )
                {
                    logger.log( ILogger.Level.WARNING, "Session access failed: " + e.getMessage() );
                }

                // Whether the client is on the trusted LAN right now. Evaluated only for protected paths;
                // public static files short-circuit below and never need this check.
                boolean privatePath  = ! isPublicPath( target );
                boolean onLan        = privatePath && isClientAllowed( ctx.clientIp() );
                boolean isGuestOnLan = onLan && grant.isGuestDevice( ctx.clientIp() );

                // The validated grant cookie, read only when it can decide something: for off-LAN clients,
                // and for visitor devices indoors (whose admin cookie, if any, outranks the subnet they
                // happen to be sitting on).
                RemoteGrant.Grant granted = (privatePath && grant.isEnabled() && (isGuestOnLan || ! onLan))
                                            ? admittedGrant( grant, ctx )
                                            : null;

                if( ! privatePath )
                {
                    isAllowed = true;     // Public resource (html/js/css/images/...)
                }
                else if( isGuestOnLan )
                {
                    // Visitor device on the house LAN: confined exactly as it would be from outside, so
                    // "their page and nothing else" holds indoors too. Checked BEFORE the session branches
                    // and never pinned to a session, so a session obtained by any other means cannot widen
                    // it: the decision is re-made on every request. Uses the same role-aware mayAccess()
                    // as the off-LAN branch below, so a Viewer grant is confined to its own dashboard here
                    // too, instead of falling through to the guest rules.
                    isAllowed = grant.mayAccess( granted, target );
                }
                else if( session != null && session.getAttribute( KEY_USER_ID ) != null )
                {
                    isAllowed = true;
                }
                else if( session != null && Boolean.TRUE.equals( session.getAttribute( "authenticated" ) ) )
                {
                    // Session was authenticated by HmacAuthenticator (used by external clients)
                    isAllowed = true;
                }
                else if( onLan )
                {
                    // A session is only worth creating when its cookie can actually reach the client on a
                    // later request. Once HTTPS is configured, the session cookie is marked Secure (see
                    // newSessionHandler) and a browser will never return it over plain HTTP — so for an
                    // on-LAN client on this still-active HTTP connector, allocating one here would just be
                    // abandoned on every single request. Access is granted either way, re-derived from IP
                    // scope on each request exactly as the class doc for newSessionHandler describes.
                    if( ServerConfig.isTlsAvailable() && ! httpRequest.isSecure() )
                    {
                        isAllowed = true;
                    }
                    else
                    {
                        try
                        {
                            session = httpRequest.getSession( true );
                            session.setAttribute( KEY_USER_ID, "user_local" );
                            session.setAttribute( "authenticated", Boolean.TRUE );  // Trusted by IP scope — also covers HmacAuthenticator fast-path
                            isAllowed = true;
                        }
                        catch( IllegalStateException e )
                        {
                            logger.log( ILogger.Level.WARNING, "Session creation failed: " + e.getMessage() );
                        }
                    }
                }
                else if( grant.mayAccess( granted, target ) )
                {
                    // Off-LAN client admitted by its grant (an administrator, or a visitor proven to have
                    // been on the trusted LAN recently and asking for a path within the guest scope). No
                    // session is persisted, so the grant is re-checked on every request.
                    isAllowed = true;
                }

                // HMAC configured: the request itself may carry a valid signature (used by the off-LAN
                // File Manager and by the deploy script). This is the single API-authentication choke
                // point — the downstream servlets no longer re-validate, so every private path is covered
                // here. Tried last, so it remains available to whoever holds the secret even when a
                // narrower credential (e.g. a stale guest cookie) has already been rejected above.
                if( ! isAllowed && privatePath && isHmacEnabled() )
                    isAllowed = (hmacAuth.validateRequest( httpRequest ) == null);

                // Let the policy record on-LAN presence (e.g. issue/refresh a grant) ONLY while the client is
                // genuinely on the trusted LAN right now — never from a lingering off-LAN session — so any
                // issued grant lapses the configured time after the client truly leaves the LAN.
                if( onLan && grant.isEnabled() )
                {
                    try
                    {
                        grant.onTrusted( ctx );
                    }
                    catch( Exception e )
                    {
                        logger.log( ILogger.Level.WARNING, e, "Remote-access grant failed to record on-LAN presence" );
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

            // Gum's own bundled File-Manager UI lives under the /gum/user-files private prefix but is app
            // code, not user content, so it stays publicly loadable (its file operations authenticate via HMAC).
            for( String p : asPublicUiSubPaths )
            {
                if( path.startsWith( p ) )
                    return true;
            }

            // User-content areas (served files, dashboards) and the API/WS endpoints require authentication
            // — even for .html/.js/.svg. This is what stops an *uploaded* page from being served anonymously
            // (and running as script) from Gum's own origin. On-LAN / granted / HMAC clients still reach them.
            for( String p : asPrivatePaths )
            {
                if( path.startsWith( p ) )
                    return false;
            }

            return true;   // Gum's own static UI assets under /gum/
        }

        private boolean isClientAllowed( InetAddress ip )
        {
            try
            {
                return UtilComm.isClientAllowed( ServerConfig.getClientIPScope(), ip );
            }
            catch( SocketException ex )
            {
                // Nothing to do
            }

            return false;
        }

        /**
         * Reads and validates the request's grant cookie, failing closed: any exception thrown by the
         * grant is logged and treated as "no grant".
         *
         * @param grant the shared remote-access grant
         * @param ctx   the request context
         * @return the validated grant, or {@code null} when there is none (or it is forged or expired)
         */
        private RemoteGrant.Grant admittedGrant( RemoteGrant grant, AccessContext ctx )
        {
            try
            {
                return grant.admit( ctx );
            }
            catch( Exception e )
            {
                logger.log( ILogger.Level.WARNING, e, "Remote-access grant failed; denying access" );

                return null;
            }
        }

        private boolean isHmacEnabled()
        {
            String secret = UtilSys.getConfig().get( "monitoring", "shared_secret", "" );

            return UtilStr.isNotEmpty( secret );
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
        private static volatile short   clientIPScope = -1;
        private static volatile boolean tlsAvailable  = false;

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

        /**
         * Records that an HTTPS connector was created. Set once, while the server is being built.
         */
        public static void setTlsAvailable( boolean b )
        {
            tlsAvailable = b;
        }

        /**
         * @return {@code true} when this server has a TLS connector, so requests may arrive encrypted.
         */
        public static boolean isTlsAvailable()
        {
            return tlsAvailable;
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

            // Ignore common non-error 404s
            if( sURI.endsWith( ".js.map"     ) ||    // Ignore JS debugging file
                sURI.endsWith( "favicon.ico" ) )     // Ignore favicon requests
                return;

            Throwable cause  = (Throwable) request.getAttribute( "javax.servlet.error.exception"   );
            Integer   status = (Integer)   request.getAttribute( "javax.servlet.error.status_code" );

            // Determine log level based on status code
            Level logLevel = (status != null && status == 404) ? Level.WARNING : Level.SEVERE;

            if( logLevel == Level.WARNING && target == null )
                return;

            logger.log( logLevel, "=== ERROR HANDLER TRIGGERED ===\n"+
                                  "Target:  "+ target  +'\n'+
                                  "Status:  "+ status  +'\n'+
                                  "Request: "+ request +'\n' );

            if( cause != null )
                logger.log( logLevel, cause, "Exception: " + cause.getClass().getName() );

            cause = (Throwable) request.getAttribute( "javax.servlet.error.exception" );

            if( cause != null )  logger.log( logLevel, cause, "ERROR:\n" );
            else                 logger.log( logLevel, "Server error with no exception attached. Target: " + target +", Status: "+ status );

            if( ! response.isCommitted() )
            {
                response.setContentType( "application/json" );
                String errorMsg = (cause != null) ? cause.getMessage() : "Unknown error on target: " + target +", Status: "+ status;
                response.getWriter().write( "{\"error\":"+ Json.value( errorMsg ).toString() + "}" );
            }

            baseRequest.setHandled( true );
        }
    }
}