
package com.peyrona.mingle.gum;

import com.peyrona.mingle.lang.japi.UtilStr;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * COOKIE BRIDGE — folded in from the former /gum/remote endpoint.
 * <p>
 * Hands a device that is provably on the trusted LAN a grant cookie already stored under the
 * PUBLIC host name. It exists because cookies are host-only. A grant obtained at
 * http://192.168.1.5:8080 is never sent to http://my.host.org:8080, and Gum cannot simply issue
 * one there instead: a router that hairpins with source NAT rewrites the origin of an in-house
 * request to the public name, so Gum sees it arrive from the WAN address and refuses to trust
 * it. The two halves of the problem cancel out if the proof of presence travels in the URL
 * rather than in the source address:
 * <ol>
 *   <li>On the room's WiFi, the client opens  http://192.168.1.5:8080/gum/login?net=7</li>
 *   <li>Gum trusts the source address, mints a 120-second token and redirects to
 *       http://my.host.org:8080/gum/login?net=7&amp;t=&lt;role&gt;:&lt;subnets&gt;:&lt;expiry&gt;:&lt;hmac&gt;</li>
 *   <li>That request leaves and comes back through the router, arriving with the public address.
 *       The token — not the address — is what proves the client was just on the LAN.</li>
 *   <li>Gum validates it, sets the cookie UNDER THE PUBLIC NAME and redirects to the guest page.</li>
 *   <li>The client bookmarks that page. It now works both at home and away.</li>
 * </ol>
 * Guest-only: an admin re-authenticates directly under whichever host name is in use instead
 * (see {@code ServletLogin.doPost}/{@code doGet}) now that plain password login is accepted over TLS
 * from anywhere — so this never mints or honours an admin token, only ever a guest one.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class CookieBridge
{
    //------------------------------------------------------------------------//
    private CookieBridge() {}  // Avoid creating instances of this class
    //------------------------------------------------------------------------//

    /**
     * Entry point for both hops of the bridge (see above). {@code token} is {@code null}/empty on
     * the first hop (LAN) and the signed token on the second (public host name).
     */
    static void handleBridge( HttpServletRequest request, HttpServletResponse response, String token ) throws IOException
    {
        RemoteGrant   grant = RemoteGrant.get();
        AccessContext ctx   = AccessContext.of( request, response );

        if( ! grant.isBridgeEnabled() )
        {
            HttpServer.sendPlain( response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
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
    private static void issueToken( HttpServletRequest request, HttpServletResponse response,
                             AccessContext ctx, RemoteGrant grant ) throws IOException
    {
        // Presence on the trusted LAN is precisely what is being attested here, so it is checked
        // against the real socket peer only (isRealClientOnLan never honours X-Forwarded-For). A
        // guest has no other way in — this IS their credential. An administrator has a real one
        // (the master password) that works regardless of location, so rather than a dead end this
        // sends them to the very same sign-in form every other admin entry point uses, already
        // aimed at this room: correct password in, and RemoteGrant.mayAccess()/mayReach() wave an
        // admin grant past guest_paths and bind_subnet unconditionally, no bridge involved.
        if( ! HttpServer.isRealClientOnLan( request ) )
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
            HttpServer.sendPlain( response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
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
    private static void redeemToken( HttpServletRequest request, HttpServletResponse response,
                              AccessContext ctx, RemoteGrant grant, String token ) throws IOException
    {
        RemoteGrant.Grant granted = grant.consumeBridgeToken( token );

        if( ! grant.issueFrom( ctx, granted ) )
        {
            HttpServer.sendPlain( response, HttpServletResponse.SC_FORBIDDEN,
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
    private static String carriedQuery( HttpServletRequest request )
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
