
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Authentication filter that validates user sessions before allowing access.
 * Must be added to each ServletContextHandler that requires authentication.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class AuthenticationFilter implements javax.servlet.Filter
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
                HttpServer.logger.log( ILogger.Level.WARNING, "Session access failed: " + e.getMessage() );
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
            else if( session != null && session.getAttribute( HttpServer.KEY_USER_ID ) != null )
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
                if( HttpServer.ServerConfig.isTlsAvailable() && ! httpRequest.isSecure() )
                {
                    isAllowed = true;
                }
                else
                {
                    try
                    {
                        session = httpRequest.getSession( true );
                        session.setAttribute( HttpServer.KEY_USER_ID, "user_local" );
                        session.setAttribute( "authenticated", Boolean.TRUE );  // Trusted by IP scope — also covers HmacAuthenticator fast-path
                        isAllowed = true;
                    }
                    catch( IllegalStateException e )
                    {
                        HttpServer.logger.log( ILogger.Level.WARNING, "Session creation failed: " + e.getMessage() );
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
            //
            // Same reachability gate as the master-password login (ServletLogin): the signed request
            // carries a credential just as sensitive as that password, so it must never be accepted in
            // the clear off-LAN either — only from the trusted LAN, or over a secure (HTTPS) connection.
            if( ! isAllowed && privatePath && isHmacEnabled() && (onLan || httpRequest.isSecure()) )
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
                    HttpServer.logger.log( ILogger.Level.WARNING, e, "Remote-access grant failed to record on-LAN presence" );
                }
            }

            if( isAllowed )
            {
                if( HttpServer.logger.isLoggable( ILogger.Level.INFO ) )
                {
                    HttpServer.logger.log( Level.INFO, String.format( "Access granted: %s %s from %s",
                                                           httpRequest.getMethod(), target, httpRequest.getRemoteAddr() ) );
                }

                chain.doFilter( request, response ); // Continue to servlet
            }
            else
            {
                if( HttpServer.logger.isLoggable( ILogger.Level.WARNING ) )
                {
                    HttpServer.logger.log( ILogger.Level.WARNING, String.format( "Unauthorized access attempt: %s %s from %s",
                                                                      httpRequest.getMethod(), target, httpRequest.getRemoteAddr() ) );
                }

                sendUnauthorizedResponse( httpResponse, "Authentication required" );
            }
        }
        catch( Exception exc )
        {
            HttpServer.logger.log( ILogger.Level.SEVERE, exc, "Error in authentication filter" );

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
            return UtilComm.isClientAllowed( HttpServer.ServerConfig.getClientIPScope(), ip );
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
            HttpServer.logger.log( ILogger.Level.WARNING, e, "Remote-access grant failed; denying access" );

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
