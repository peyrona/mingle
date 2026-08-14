
package com.peyrona.mingle.gum;

import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.websocket.api.Session;

/**
 * The read/write view of an incoming request that the {@link RemoteGrant} off-LAN policy needs, abstracted
 * over the two sources Gum serves it from: a servlet HTTP request (in
 * {@link HttpServer.AuthenticationFilter}) and a WebSocket upgrade request (in {@link CommBridge}).
 * This lets one policy instance serve both decision points without depending on either transport.
 * <p>
 * Client-IP resolution uses the socket peer address only ({@code X-Forwarded-For} is intentionally
 * ignored, since Gum runs without a trusted reverse proxy). Cookie emission
 * ({@link #setCookie(String, String, int)}) is only meaningful for the HTTP request/response pair;
 * the WebSocket view is read-only and silently ignores it.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
interface AccessContext
{
    /** Path all Gum auth cookies are scoped to. */
    String COOKIE_PATH = "/gum";

    /**
     * The client's resolved IP address, taken from the socket peer only ({@code X-Forwarded-For}
     * is intentionally ignored — Gum runs without a trusted reverse proxy).
     *
     * @return the address, or {@code null} if it cannot be resolved.
     */
    InetAddress clientIp();

    /**
     * Returns the value of the named request cookie.
     *
     * @param name the cookie name.
     * @return the value, or {@code null} if the cookie is absent.
     */
    String cookie( String name );

    /**
     * Emits a response cookie (a no-op on read-only contexts, e.g. WebSocket). The cookie is scoped to
     * {@link #COOKIE_PATH}, {@code HttpOnly}, {@code SameSite=Lax}, and {@code Secure} on HTTPS requests.
     *
     * @param name         the cookie name.
     * @param value        the cookie value; if {@code null}, nothing is written.
     * @param maxAgeSeconds the cookie's {@code Max-Age} in seconds, or a negative value to omit
     *                      {@code Max-Age} entirely and let the browser treat it as a session cookie
     *                      (discarded when the browser — not just the tab — closes).
     */
    void setCookie( String name, String value, int maxAgeSeconds );

    //------------------------------------------------------------------------//

    /** Wraps a servlet HTTP request/response pair (read + write). */
    static AccessContext of( HttpServletRequest request, HttpServletResponse response )
    {
        return new ServletCtx( request, response );
    }

    /** Wraps a WebSocket session's HTTP upgrade request (read-only). */
    static AccessContext of( Session session )
    {
        return new WsCtx( session );
    }

    //------------------------------------------------------------------------//
    // IMPLEMENTATIONS

    /** {@link AccessContext} backed by a servlet HTTP request/response pair. */
    final class ServletCtx implements AccessContext
    {
        private final HttpServletRequest  request;
        private final HttpServletResponse response;

        private ServletCtx( HttpServletRequest request, HttpServletResponse response )
        {
            this.request  = request;
            this.response = response;
        }

        @Override
        public InetAddress clientIp()
        {
            // Resolve from the socket peer only. X-Forwarded-For is deliberately NOT honoured: Gum is
            // exposed directly (no trusted reverse proxy), so honouring it would let any client spoof
            // an on-LAN address and defeat the IP-scope trust that gates access.
            try
            {
                return InetAddress.getByName( request.getRemoteAddr() );
            }
            catch( UnknownHostException ex )
            {
                return null;
            }
        }

        @Override
        public String cookie( String name )
        {
            Cookie[] cookies = request.getCookies();

            if( cookies != null )
            {
                for( Cookie c : cookies )
                {
                    if( name.equals( c.getName() ) )
                        return c.getValue();
                }
            }

            return null;
        }

        @Override
        public void setCookie( String name, String value, int maxAgeSeconds )
        {
            if( value == null )
                return;

            StringBuilder sb = new StringBuilder( 128 );
            sb.append( name ).append( '=' ).append( value )
              .append( "; Path=" ).append( COOKIE_PATH );

            if( maxAgeSeconds >= 0 )
                sb.append( "; Max-Age=" ).append( maxAgeSeconds );

            sb.append( "; HttpOnly" )
              .append( "; SameSite=Lax" );

            if( request.isSecure() )
                sb.append( "; Secure" );

            response.addHeader( "Set-Cookie", sb.toString() );
        }
    }

    //------------------------------------------------------------------------//

    /** Read-only {@link AccessContext} backed by a WebSocket session's HTTP upgrade request. */
    final class WsCtx implements AccessContext
    {
        private final Session session;

        private WsCtx( Session session )
        {
            this.session = session;
        }

        @Override
        public InetAddress clientIp()
        {
            // Socket peer only; X-Forwarded-For is deliberately NOT honoured (see ServletCtx.clientIp).
            try
            {
                if( session.getRemoteAddress() instanceof InetSocketAddress )
                    return ((InetSocketAddress) session.getRemoteAddress()).getAddress();
            }
            catch( Exception e )
            {
                // Address not resolvable: treat as unknown (off-LAN)
            }

            return null;
        }

        @Override
        public String cookie( String name )
        {
            try
            {
                List<HttpCookie> cookies = session.getUpgradeRequest().getCookies();

                if( cookies != null )
                {
                    for( HttpCookie c : cookies )
                    {
                        if( name.equals( c.getName() ) )
                            return c.getValue();
                    }
                }
            }
            catch( Exception e )
            {
                // Upgrade request / cookies not available: treat as no cookie
            }

            return null;
        }

        @Override
        public void setCookie( String name, String value, int maxAgeSeconds )
        {
            // Read-only context: cookies cannot be set on a WebSocket upgrade response.
        }
    }
}
