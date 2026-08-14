
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed access grants that give Gum two roles — <b>guest</b> and <b>admin</b> — without a user
 * database, using one HMAC-signed cookie. It stays dormant unless given a non-empty
 * {@code shared_secret}.
 * <p>
 * <b>Guest.</b> While a client's IP is inside the configured trusted scope (see
 * {@code monitoring.allow}), {@link #onTrusted(AccessContext)} issues (or refreshes) a grant cookie
 * carrying the {@code /24} subnets the device was seen on plus an absolute expiry. When a later
 * request arrives from outside that scope, {@link #admit(AccessContext)} validates the cookie, so
 * the device keeps working for {@code guest_ttl} hours after it leaves the premises. Because
 * the cookie is refreshed on every on-LAN request, the countdown only starts once the device is
 * actually gone. A guest is confined to {@code guest_paths} and — when
 * {@code bind_subnet} is set — to the ExEns of the subnets it proved presence on.
 * <p>
 * Subnets accumulate rather than replace (up to {@value #MAX_SUBNETS}, newest kept): a visitor who
 * uses both a bedroom and a common-area WiFi keeps reaching both, but never a subnet their device
 * was never on.
 * <p>
 * <b>Admin.</b> A successful master-password login ({@link HttpServer.ServletLogin}) mints a grant
 * cookie exactly once — {@link #issueAdmin(AccessContext, boolean)} — and it is deliberately never
 * refreshed afterwards, here or anywhere ({@link #onTrusted} and {@link #admit} both leave an
 * existing admin cookie untouched): the login itself is the only moment that re-proves the password,
 * so the expiry it sets is the one that holds. Two modes, chosen by "keep me signed in" at login: the
 * default is {@code monitoring.timeout} seconds (session inactivity, same value used elsewhere) in a
 * plain browser-session cookie — meant for a device that is not "mine"; checking the box instead issues
 * a real persistent cookie, {@code admin_ttl} long, that survives a closed browser. The whole point of
 * the admin role is reaching every room in the house and from outside without repeating the login
 * dance, which only the persistent form actually delivers — see the class's own history: an earlier
 * version of this method made "keep me signed in" widen the signed expiry only, while the cookie itself
 * stayed session-only regardless, which quietly broke exactly that goal.
 * <p>
 * Admin has no use for {@link #issueBridgeToken(AccessContext)}: cookies are host-only, and rather
 * than carry one between host names, an admin simply signs in again under whichever one is in use —
 * login is accepted from the trusted LAN or over TLS (never in the clear from outside), and
 * {@link LoginRateLimiter} is what makes typing the password from an internet-facing host safe enough
 * to allow without a subnet check first. That is also why the bridge below stays guest-only.
 * <p>
 * <b>Viewer.</b> {@link #issueViewer(AccessContext, String[])} mints a grant confined to specific
 * dashboards, read-only, valid for {@code view_ttl} hours. It is issued by {@link HttpServer.ServletLogin}
 * ({@code /gum/login?board=<name>}) after a dashboard's own password checks out — a credential wholly separate from
 * {@code master_password}, meant to be handed to someone who never sets foot on the trusted LAN. Unlike
 * a guest, a viewer gets no blanket exception for the WebSocket bridge ({@link #isGuestPath}); instead
 * {@link #mayReach} confines its bridge traffic to exactly the ExEns its dashboards reference, and
 * {@link CommBridge} separately refuses any {@code Change}/{@code Execute} message on its behalf — a
 * viewer may watch a dashboard update, never drive it.
 * <p>
 * <b>Guests already indoors.</b> {@code guest_subnets} lists the {@code /24}s that visitor devices
 * live on. A request arriving from one of them is treated as a guest <i>even while on the LAN</i>,
 * so "their page and nothing else" holds inside the house too, not merely after they leave.
 * <p>
 * <b>The cookie bridge.</b> Cookies are host-only, so one obtained at {@code http://192.168.1.5:8080}
 * never travels to the public host name — and a router that hairpins with source NAT rewrites the
 * origin of an in-house request to that name, so Gum sees it as off-LAN and would never issue one
 * there either. {@link #issueBridgeToken(AccessContext)} breaks that deadlock: a device on the LAN
 * gets a token attesting its presence, {@code HttpServer.ServletLogin.handleBridge()} redirects it to
 * the same endpoint under {@code public_url}, and {@link #consumeBridgeToken(String)} +
 * {@link #issueFrom(AccessContext, Grant)} turn it into the cookie, now stored under the name the
 * device will use from outside. The token — not the IP — is what proves the earlier presence, which
 * is why it lives only {@value #BRIDGE_TTL_SECONDS} seconds and is signed in its own context, so a
 * token and a cookie can never stand in for one another.
 * <p>
 * Cookie value format (all fields are cookie-safe; {@code ':'} separates them, {@code '_'} separates
 * a guest's subnets and {@code '|'} a viewer's board names — {@code ','} and {@code ';'} are cookie
 * separators and {@code '.'} is already used inside a subnet tag, which rules out both for a list
 * separator; {@code '|'} is additionally guaranteed absent from a board name, which {@code '_'} is not
 * — {@code Util.sanitizeFileName} turns spaces into {@code '_'}, so "Living Room" is a board literally
 * named {@code "Living_Room"} on disk):
 * <pre>
 *   &lt;role&gt;:&lt;subnets-or-boards&gt;:&lt;expiryEpochSeconds&gt;:&lt;hexHmacSha256(role + ":" + subnets + ":" + expiry)&gt;
 *
 *   guest:  "g:192.168.7_192.168.1:1737050000:9f3c…"
 *   admin:  "a:*:1744820000:2b71…"
 *   viewer: "v:Living_Room|Kitchen:1752300000:7ad0…"
 * </pre>
 * <p>
 * Configuration lives under {@code "monitoring"}, in the {@code "remote_access"} sub-object:
 * <ul>
 *   <li>{@code guest_ttl} - guest grant lifetime in hours. {@code 0} or absent disables off-LAN
 *       guest access.</li>
 *   <li>{@code admin_ttl} - admin grant lifetime in hours, used two ways (see {@link #issueAdmin}): as
 *       the persistent cookie's {@code Max-Age} when "keep me signed in" is checked at login, and — at
 *       any nonzero value, regardless of that checkbox — as what enables master-password login at all.
 *       {@code 0} disables master-password login entirely. Also requires a non-empty
 *       {@code monitoring.master_password}.</li>
 *   <li>{@code view_ttl} - viewer grant lifetime in hours (default {@value #ADMIN_TTL_DEFAULT}, same
 *       default as admin: a shared dashboard link is meant to be bookmarked and keep working, not
 *       re-entered often). {@code 0} disables board-viewer sign-in entirely. The real gate is each
 *       dashboard's own password, set from the dashboard manager — this only bounds how long a
 *       successful sign-in is remembered.</li>
 *   <li>{@code bind_subnet} - when {@code true}, a guest may only reach ExEns located on a subnet
 *       it proved presence on (enforced via {@link #mayReach}; a viewer is confined to its dashboards'
 *       own ExEns unconditionally, independently of this flag — see {@link #mayReach}).</li>
 *   <li>{@code guest_paths} - path prefixes a guest may request, on top of the WebSocket bridge,
 *       which is always reachable. Absent/empty means a guest is not path-restricted, which
 *       preserves the behaviour of earlier releases.</li>
 *   <li>{@code guest_subnets} - {@code /24}s whose devices are guests even while on the LAN.
 *       Absent/empty means every on-LAN device is trusted, as before.</li>
 *   <li>{@code public_url} - the {@code scheme://host[:port]} Gum is reached by from outside, e.g.
 *       {@code "http://my.host.org:8080"}. Absent/empty disables the cookie bridge.</li>
 * </ul>
 * Two keys stay directly under {@code "monitoring"} because they are not exclusive to remote access:
 * {@code shared_secret} (also the API authentication key; if empty, no grant can be signed and the
 * whole mechanism stays off) and {@code master_password} (also gates dashboards and the file manager).
 * <p>
 * The design deliberately omits replay nonces and server-side revocation for the guest/viewer
 * cookies: the intended threat there is a former, non-technical user reusing a stale bookmark, not a
 * determined attacker. The master password is a different story — it grants full control, and the
 * login endpoint is internet-reachable once HTTPS is on — so {@link LoginRateLimiter} throttles that
 * one credential specifically; see its own class doc. HMAC signing is kept only so a hand-edited
 * cookie (e.g. a forged expiry or a promotion
 * from guest to admin) is rejected.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class RemoteGrant
{
    private static final String COOKIE_NAME = "mingle_remote_grant";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SEPARATOR   = ":";
    private static final String SUBNET_SEP  = "_";
    private static final String ROLE_GUEST  = "g";
    private static final String ROLE_ADMIN  = "a";
    private static final String ROLE_VIEW   = "v";
    private static final String ANY_SUBNET  = "*";

    /**
     * Separator for the board-name list a viewer grant carries. Deliberately NOT {@link #SUBNET_SEP}:
     * {@code Util.sanitizeFileName} turns spaces (and other characters) in a board name into {@code '_'},
     * so a board legitimately named e.g. "Living Room" would collide with it. {@code '|'} is one of the
     * characters that sanitisation strips from board names, so it can never appear inside one.
     */
    private static final String BOARD_SEP   = "|";

    /** The WebSocket bridge: always within a guest's reach, so {@code guest_paths} need not list it. */
    private static final String BRIDGE_PATH = "/gum/bridge";

    /** Where a dashboard's own static content is served from — see {@link #isViewerPath}. */
    private static final String BOARD_PATH  = "/gum/board/";

    /**
     * Signing context for grant cookies. Empty on purpose: it is what keeps cookies issued by earlier
     * releases valid.
     */
    private static final String COOKIE_CTX = "";

    /**
     * Signing context for bridge tokens. Cookies and tokens share a value format, so signing them apart
     * is what stops either from being replayed as the other.
     */
    private static final String BRIDGE_CTX = "b" + SEPARATOR;

    /** Most subnets a single guest grant remembers; oldest is dropped when a new one is added. */
    static final int MAX_SUBNETS = 4;

    /** Default admin grant lifetime, in hours (90 days). */
    static final int ADMIN_TTL_DEFAULT = 2160;

    /**
     * Bridge token lifetime, in seconds. It is the whole defence against replay: a token is only ever
     * handed out on the trusted LAN and is worthless two minutes later, so no nonce or server-side
     * state is kept.
     */
    static final int BRIDGE_TTL_SECONDS = 120;

    private final int      ttlSeconds;        // Guest grant lifetime in seconds (0 == off-LAN guest access off)
    private final int      adminTtlSeconds;   // 0 == admin grants off; else persistent-cookie lifetime when "keep me signed in" IS checked
    private final int      adminShortSeconds; // Admin cookie lifetime, in seconds, when "keep me signed in" was NOT checked
    private final int      viewTtlSeconds;    // Viewer grant lifetime in seconds (0 == board-viewer sign-in off)
    private final boolean  bindSubnet;        // Restrict a guest to the subnets it was seen on
    private final String   secret;            // HMAC signing key (empty == whole mechanism off)
    private final String   publicUrl;         // Base URL Gum is reached by from outside (empty == cookie bridge off)
    private final short    allowScope;        // Trusted-LAN scope (same "monitoring.allow" used by the HTTP filter)
    private final String[] asGuestPaths;      // Path prefixes a guest may request (empty == unrestricted)
    private final String[] asGuestSubnets;    // Subnets whose devices are guests even while on the LAN
    private final Mac      mac;               // null when no secret is configured

    /** The single shared instance; reads its configuration once, when the class is first used. */
    private static final RemoteGrant INSTANCE = new RemoteGrant();

    //------------------------------------------------------------------------//
    // INNER CLASS

    /**
     * The decoded, verified contents of a grant cookie: who the holder is and, for a guest, which
     * subnets it proved presence on. Instances are created only by {@link #validate(String)}, so
     * holding one means the signature checked out and the grant had not expired.
     */
    static final class Grant
    {
        private final boolean  isAdmin;
        private final boolean  isViewer;
        private final String[] asSubnets;     // For a viewer grant, board names instead of subnet tags
        private final long     expiry;        // Absolute, in epoch seconds

        private Grant( boolean isAdmin, boolean isViewer, String[] asSubnets, long expiry )
        {
            this.isAdmin   = isAdmin;
            this.isViewer  = isViewer;
            this.asSubnets = asSubnets;
            this.expiry    = expiry;
        }

        /**
         * @return {@code true} when this grant belongs to the administrator (unrestricted).
         */
        boolean isAdmin()
        {
            return isAdmin;
        }

        /**
         * @return {@code true} when this grant is confined to specific dashboards, read-only.
         */
        boolean isViewer()
        {
            return isViewer;
        }

        /**
         * @param tag A {@code /24} subnet tag (guest grant) or a board name (viewer grant); may be
         *            {@code null}.
         * @return {@code true} when this grant carries the passed tag.
         */
        boolean has( String tag )
        {
            if( tag == null )
                return false;

            for( String s : asSubnets )
            {
                if( s.equals( tag ) )
                    return true;
            }

            return false;
        }
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    /**
     * @return the single shared {@code RemoteGrant} instance.
     */
    static RemoteGrant get()
    {
        return INSTANCE;
    }

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    RemoteGrant()
    {
        // Everything specific to reaching Gum from outside lives in one sub-object; "shared_secret" and
        // "master_password" stay at module level because they are used outside remote access too.
        JsonObject joRemote = UtilSys.getConfig().get( "monitoring", "remote_access", Json.object() );

        // Each value found in the sub-object is handed to Config as the DEFAULT, not read directly: that
        // way Config still applies its CLI > system property > environment lookup on the bare key name
        // (e.g. -guest_ttl=24) and only falls back to the configured value. Config performs that lookup
        // for top-level keys only, and this is all it takes to keep the nested ones overridable too.
        String sAllow        = UtilSys.getConfig().get( "monitoring", "allow"        , "intranet" );
        String sPublicUrl    = UtilSys.getConfig().get( "monitoring", "public_url"   , asString( joRemote, "public_url" ) );
        int    ttlHours      = UtilSys.getConfig().get( "monitoring", "guest_ttl"    , joRemote.getInt( "guest_ttl", 0 ) );
        int    adminTtlHours = UtilSys.getConfig().get( "monitoring", "admin_ttl"    , joRemote.getInt( "admin_ttl", ADMIN_TTL_DEFAULT ) );
        int    viewTtlHours  = UtilSys.getConfig().get( "monitoring", "view_ttl"     , joRemote.getInt( "view_ttl", ADMIN_TTL_DEFAULT ) );

        // Same value used elsewhere for plain session inactivity — reused here as the admin cookie's
        // own default lifetime, since it is the same "how long before you are asked again" idea.
        int    sessionTimeout = UtilSys.getConfig().get( "monitoring", "timeout", 1800 );

        this.bindSubnet         = UtilSys.getConfig().get( "monitoring", "bind_subnet"  , joRemote.getBoolean( "bind_subnet", false ) );
        this.secret             = UtilSys.getConfig().get( "monitoring", "shared_secret", "" );
        this.asGuestPaths       = toArray( UtilSys.getConfig().get( "monitoring", "guest_paths"  , asArray( joRemote, "guest_paths"   ) ), false );
        this.asGuestSubnets     = toArray( UtilSys.getConfig().get( "monitoring", "guest_subnets", asArray( joRemote, "guest_subnets" ) ), true  );
        this.publicUrl          = normalizeUrl( sPublicUrl );
        this.ttlSeconds         = Math.max( 0, ttlHours      ) * 3600;
        this.adminTtlSeconds    = Math.max( 0, adminTtlHours ) * 3600;
        this.adminShortSeconds  = Math.max( 1, sessionTimeout );
        this.viewTtlSeconds     = Math.max( 0, viewTtlHours  ) * 3600;
        this.allowScope         = UtilComm.clientScope( UtilStr.isEmpty( sAllow ) ? "intranet" : sAllow );
        this.mac                = createMac();

        warnIfInconsistent( ttlHours, adminTtlHours, sPublicUrl );
    }

    //------------------------------------------------------------------------//
    // GRANT HOOKS (called by HttpServer.AuthenticationFilter and CommBridge)

    /**
     * Returns {@code true} when the mechanism can do anything at all: a signing secret plus at least
     * one of the two grant kinds enabled.
     */
    boolean isEnabled()
    {
        return canSign() && ((ttlSeconds > 0) || (adminTtlSeconds > 0) || (viewTtlSeconds > 0));
    }

    /**
     * Tells whether the passed address belongs to a device that must be treated as a guest even while
     * it sits on the trusted LAN, per {@code monitoring.guest_subnets}.
     *
     * @param ip The client address, may be {@code null}.
     * @return {@code true} when the address' {@code /24} is listed as a guest subnet.
     */
    boolean isGuestDevice( InetAddress ip )
    {
        if( asGuestSubnets.length == 0 )
            return false;

        String tag = subnetTag( ip );

        if( tag == null )
            return false;

        for( String s : asGuestSubnets )
        {
            if( s.equals( tag ) )
                return true;
        }

        return false;
    }

    /**
     * Issues (or refreshes) the grant cookie for a client currently on the trusted LAN, adding the
     * {@code /24} subnet of its IP to the set the cookie already carries. No-op when guest grants are
     * disabled or the IP is not IPv4.
     * <p>
     * An existing admin or viewer cookie is left untouched here, never refreshed and never overwritten
     * by the guest logic below — being on the LAN must never demote either one to a guest grant. An
     * admin cookie is deliberately never renewed anywhere (see the class doc), so it simply rides out
     * the expiry set at login; a viewer cookie is scoped to specific dashboards and would be corrupted
     * by being merged into the guest cookie's subnet-tag format.
     *
     * @param ctx the request/response context.
     */
    void onTrusted( AccessContext ctx )
    {
        Grant current = validate( ctx.cookie( COOKIE_NAME ) );

        if( (current != null) && (current.isAdmin() || current.isViewer()) )
            return;

        if( ! isGuestEnabled() )
            return;

        String subnet = subnetTag( ctx.clientIp() );

        if( subnet == null )
            return;

        String[] asSubnets = merge( (current == null) ? null : current.asSubnets, subnet );

        ctx.setCookie( COOKIE_NAME, issue( COOKIE_CTX, ROLE_GUEST, join( asSubnets ), ttlSeconds ), ttlSeconds );
    }

    /**
     * Mints the admin grant cookie after a successful master-password login. Called once, by
     * {@link HttpServer.ServletLogin#processLogin}, and never refreshed afterwards — see the class doc.
     * <p>
     * "Keep me signed in" is the real distinction it appears to be: checked, the cookie is issued for
     * the full {@code admin_ttl} (weeks/months — the whole point being to reach every room in the house
     * and from outside without repeating this dance) and carries an actual {@code Max-Age}, so it
     * survives a closed browser and a rebooted phone. Unchecked, it is a plain browser-session cookie
     * (no {@code Max-Age}) good for {@code monitoring.timeout} seconds — meant for a device this isn't
     * "mine", e.g. borrowing someone else's laptop to check something once.
     *
     * @param ctx           the request/response context.
     * @param keepSignedIn  whether "keep me signed in" was checked at login.
     */
    void issueAdmin( AccessContext ctx, boolean keepSignedIn )
    {
        if( ! isAdminEnabled() )
            return;

        int ttl          = keepSignedIn ? adminTtlSeconds : adminShortSeconds;
        int cookieMaxAge = keepSignedIn ? adminTtlSeconds : -1;   // -1 == browser-session cookie, no Max-Age

        ctx.setCookie( COOKIE_NAME, issue( COOKIE_CTX, ROLE_ADMIN, ANY_SUBNET, ttl ), cookieMaxAge );
    }

    /**
     * Mints a grant confined to the passed dashboards, read-only. Called by
     * {@link HttpServer.ServletLogin} after a submitted password matched one or more dashboards.
     * <p>
     * No-op unless {@link #isViewerEnabled()} and at least one board name is passed — minting a
     * grant that authorizes nothing would be pointless and, worse, {@link #issue} would reject an
     * empty payload anyway.
     *
     * @param ctx        the request/response context.
     * @param boardNames the dashboards this grant authorizes.
     */
    void issueViewer( AccessContext ctx, String[] boardNames )
    {
        if( isViewerEnabled() && (boardNames != null) && (boardNames.length > 0) )
            ctx.setCookie( COOKIE_NAME, issue( COOKIE_CTX, ROLE_VIEW, joinBoards( boardNames ), viewTtlSeconds ), viewTtlSeconds );
    }

    /**
     * Drops whatever grant the client holds, by expiring the cookie. Called on logout: invalidating the
     * session alone would leave a long-lived administrator grant in place.
     *
     * @param ctx the request/response context.
     */
    void revoke( AccessContext ctx )
    {
        ctx.setCookie( COOKIE_NAME, "", 0 );
    }

    //------------------------------------------------------------------------//
    // COOKIE BRIDGE (called by HttpServer.ServletLogin.handleBridge())

    /**
     * Tells whether the cookie bridge can operate: a signing secret, somewhere to send the client to,
     * and guest grants enabled — the only role that still uses it (admin re-authenticates directly
     * under whichever host is in use instead; see the class doc).
     *
     * @return {@code true} when the bridge (folded into {@code /gum/login}) can do its job.
     */
    boolean isBridgeEnabled()
    {
        return canSign() && UtilStr.isNotEmpty( publicUrl ) && isGuestEnabled();
    }

    /**
     * @return the public base URL, with no trailing slash, or {@code ""} when unset or malformed.
     */
    String publicUrl()
    {
        return publicUrl;
    }

    /**
     * Mints the short-lived token that carries a client's presence on the trusted LAN over to the public
     * host name. The caller must have already established that the client really is on the LAN.
     * <p>
     * Guest-only: admin has no use for the bridge (see the class doc — it re-authenticates directly
     * under whichever host name is in use instead), so this always mints a guest token, never an admin
     * one — even if the caller happens to be carrying an admin cookie, which is ignored here rather
     * than propagated into the bridge.
     *
     * @param ctx the request/response context.
     * @return the token, or {@code null} when no guest grant can be given to this client.
     */
    String issueBridgeToken( AccessContext ctx )
    {
        if( ! isGuestEnabled() )
            return null;

        String subnet = subnetTag( ctx.clientIp() );

        if( subnet == null )
            return null;

        // Carry over whatever subnets the LAN-side cookie had already accumulated, exactly as
        // onTrusted() does: a visitor who uses both a bedroom and a common-area WiFi must not end up
        // confined to whichever of the two they happened to bridge from. A pre-existing admin cookie
        // (its "subnets" field is the ANY_SUBNET sentinel, not a real subnet list) or viewer cookie
        // (its "subnets" field is a dashboard-name list, not a subnet list either) is deliberately not
        // consulted here — neither ever applies to a guest token.
        Grant    current  = validate( ctx.cookie( COOKIE_NAME ) );
        String[] existing = ((current != null) && ! current.isAdmin() && ! current.isViewer()) ? current.asSubnets : null;
        String[] asSubnets = merge( existing, subnet );

        return issue( BRIDGE_CTX, ROLE_GUEST, join( asSubnets ), BRIDGE_TTL_SECONDS );
    }

    /**
     * Validates a bridge token: signature, role and the {@value #BRIDGE_TTL_SECONDS}-second expiry it
     * carries. Holding the result means the client stood on the trusted LAN moments ago.
     *
     * @param token the token read from the {@code t} query parameter, may be {@code null}.
     * @return the {@link Grant} it stands for, or {@code null} when it is absent, forged or expired.
     */
    Grant consumeBridgeToken( String token )
    {
        return validate( BRIDGE_CTX, token );
    }

    /**
     * Emits the grant cookie a redeemed bridge token stands for. A guest cookie is issued for the full
     * {@code guest_ttl}, since the countdown starts here: requests reaching Gum under the public name
     * never look like they come from the LAN, so {@link #onTrusted(AccessContext)} will not refresh it.
     * <p>
     * Guest-only, matching {@link #issueBridgeToken}: {@code grant.isAdmin()} is rejected rather than
     * honoured, purely as a defensive fallback in case a token minted by a not-yet-updated Gum is ever
     * redeemed here — {@link #issueBridgeToken} itself never produces one any more.
     *
     * @param ctx   the request/response context.
     * @param grant the grant returned by {@link #consumeBridgeToken(String)}, may be {@code null}.
     * @return {@code true} when a cookie was written.
     */
    boolean issueFrom( AccessContext ctx, Grant grant )
    {
        if( (grant == null) || grant.isAdmin() )
            return false;

        String value = isGuestEnabled() ? issue( COOKIE_CTX, ROLE_GUEST, join( grant.asSubnets ), ttlSeconds )
                                        : null;

        if( value == null )
            return false;

        ctx.setCookie( COOKIE_NAME, value, ttlSeconds );

        return true;
    }

    /**
     * @return the page a guest should land on once its cookie is in place — the first {@code guest_paths}
     *         entry — or {@code null} when guests are not path-restricted.
     */
    String guestLandingPath()
    {
        return (asGuestPaths.length == 0) ? null : asGuestPaths[0];
    }

    //------------------------------------------------------------------------//
    // GRANT CHECKS (called by HttpServer.AuthenticationFilter and CommBridge)

    /**
     * Validates the grant cookie carried by the request and returns what it authorizes.
     * <p>
     * A viewer grant past half its life is transparently refreshed, so a bookmarked dashboard link
     * never expires, without writing a {@code Set-Cookie} header on every single request. A guest
     * grant is deliberately <b>never</b> refreshed here: it may only be extended by being on the
     * trusted LAN (see {@link #onTrusted(AccessContext)}), which is the whole point of it. An admin
     * grant is never refreshed either, here or anywhere — see the class doc — so a pre-existing one
     * simply rides out its original expiry.
     *
     * @param ctx the request context.
     * @return the {@link Grant}, or {@code null} when the cookie is absent, forged or expired.
     */
    Grant admit( AccessContext ctx )
    {
        Grant grant = validate( ctx.cookie( COOKIE_NAME ) );

        if( (grant != null) && grant.isViewer() && ((grant.expiry - now()) < (viewTtlSeconds / 2)) )
            issueViewer( ctx, grant.asSubnets );   // Same boards the cookie already carried

        return grant;
    }

    /**
     * Tells whether the passed grant authorizes the passed request path.
     *
     * @param grant the validated grant, may be {@code null}.
     * @param path  the requested URI path.
     * @return {@code true} when the grant is an admin one, a guest one whose scope covers the path, or
     *         a viewer one naming the requested dashboard.
     */
    boolean mayAccess( Grant grant, String path )
    {
        if( grant == null )
            return false;

        if( grant.isAdmin() )
            return true;

        // A viewer's bridge CONNECTION is let through here, exactly like a guest's — refusing it would
        // leave every dashboard "loads but does nothing" (see isViewerPath). What travels once it is
        // open is a different question, answered per message by mayReach() and by CommBridge itself.
        if( grant.isViewer() )
            return isViewerPath( path, grant ) || (path != null && path.startsWith( BRIDGE_PATH ));

        return isGuestPath( path );
    }

    /**
     * Tells whether a guest may request the passed path, per {@code guest_paths}. Used both for
     * off-LAN grant holders and for on-LAN devices living on a {@code guest_subnets} subnet.
     * <p>
     * The WebSocket bridge is always reachable and need not be listed: a guest page that cannot open
     * it would load but do nothing, which is a confusing failure and the one mistake this list invites.
     * Nothing is given away by that — what a guest may actually drive through the bridge is decided by
     * {@link #mayReach}, which confines them to the ExEns of their own subnets.
     *
     * @param path the requested URI path.
     * @return {@code true} when the path is within the guest scope (always {@code true} when no scope
     *         is configured).
     */
    boolean isGuestPath( String path )
    {
        if( path == null )
            return false;

        if( path.startsWith( BRIDGE_PATH ) )
            return true;

        if( asGuestPaths.length == 0 )
            return true;      // Not configured: guests are not path-restricted (as in earlier releases)

        for( String p : asGuestPaths )
        {
            if( path.startsWith( p ) )
                return true;
        }

        return false;
    }

    /**
     * Tells whether a viewer grant may request the passed path: only the dashboards it names, and
     * only their own static content under {@link #BOARD_PATH}. The WebSocket bridge is not decided
     * here — {@link #mayAccess} lets a viewer's bridge connection through the same way a guest's is,
     * because refusing it would leave every dashboard "loads but does nothing". What is NOT the same
     * as a guest is what travels once that connection is open: {@link #mayReach} confines it to the
     * dashboards' own ExEns, and {@link CommBridge} separately refuses any write on a viewer's behalf,
     * because the connection's own authentication runs once, at connect time, and cannot by itself
     * distinguish "watch this dashboard" from "control this house".
     *
     * @param path  the requested URI path.
     * @param grant the viewer grant.
     * @return {@code true} when the path names one of the grant's dashboards.
     */
    private static boolean isViewerPath( String path, Grant grant )
    {
        if( (path == null) || ! path.startsWith( BOARD_PATH ) )
            return false;

        String rest  = path.substring( BOARD_PATH.length() );
        int    slash = rest.indexOf( '/' );
        String board = (slash < 0) ? rest : rest.substring( 0, slash );

        return grant.has( board );
    }

    /**
     * When subnet binding is enabled, confines a guest to the ExEns of the subnets its grant
     * authorizes — whether it is off-LAN holding a cookie, or indoors on a {@code guest_subnets}
     * subnet. An admin grant is always unrestricted (see the class doc: "the whole point of the admin
     * role is reaching every room in the house"), regardless of which subnet its own device sits on.
     * Any unlisted on-LAN host with no grant at all is likewise never restricted here.
     *
     * @param ctx          the request context.
     * @param exenHostPort the requested ExEn as a {@code "host[:port]"} string.
     * @return {@code true} if the target is allowed.
     */
    boolean mayReach( AccessContext ctx, String exenHostPort )
    {
        // Viewer and admin are both checked unconditionally, BEFORE the "bind_subnet" gate below
        // (a GUEST-only opt-in) and before any IP-based check — neither role's access should ever
        // hinge on which subnet the holder's own device happens to be sitting on. A viewer is
        // confined to its dashboards' own ExEns; an admin is confined to nothing.
        Grant grant = validate( ctx.cookie( COOKIE_NAME ) );

        if( (grant != null) && grant.isViewer() )
            return isExenAuthorizedForBoards( grant.asSubnets, exenHostPort );

        if( (grant != null) && grant.isAdmin() )
            return true;

        if( ! bindSubnet )
            return true;

        InetAddress ip = ctx.clientIp();

        // A guest device sitting on the house LAN is bound to its own subnet exactly as it would be
        // from outside: being indoors must not widen the set of ExEns it can drive.
        if( isGuestDevice( ip ) )
        {
            String tag = subnetTag( ip );

            return (tag != null) && tag.equals( subnetTag( exenHostPort ) );
        }

        if( isOnTrustedLan( ip ) )
            return true;

        if( grant == null )
        {
            // A cookie that was never presented is genuinely covered by the HTTP auth filter's
            // one-time check. But a cookie that WAS presented and no longer validates (typically:
            // it has since expired) must not fall back to that same "already governed elsewhere"
            // assumption — for a persistent WebSocket the filter ran once, at connect, and is never
            // re-run for the life of the socket, so a grant expiring mid-connection would otherwise
            // silently regain unrestricted ExEn reach.
            return ctx.cookie( COOKIE_NAME ) == null;
        }

        // Only a GUEST grant reaches here: viewer returned above, admin returned above, and a null
        // grant was just handled.
        return grant.has( subnetTag( exenHostPort ) );
    }

    /**
     * Tells whether the request currently carries a viewer grant. Used by {@link CommBridge} to decide
     * whether an inbound bridge message needs the extra {@code Change}/{@code Execute} check that
     * {@link #mayReach} cannot make on its own — it only ever sees one ExEn address, never a
     * message's own kind.
     *
     * @param ctx the request context.
     * @return {@code true} when the grant carried is a viewer one.
     */
    boolean isViewerSession( AccessContext ctx )
    {
        String cookieValue = ctx.cookie( COOKIE_NAME );
        Grant  grant       = validate( cookieValue );

        if( grant != null )
            return grant.isViewer();

        // A cookie that was never presented genuinely isn't a viewer session. But a cookie that WAS
        // presented and no longer validates (typically: it has since expired) must not be read the
        // same way — for a persistent WebSocket the viewer role established at connect is never
        // re-checked elsewhere, so losing viewer status here would silently lift CommBridge's
        // Change/Execute write-block exactly when the credential should have locked it in place.
        return cookieValue != null;
    }

    /**
     * Tells whether the passed dashboards' own configuration references the given ExEn. Failing to
     * read a dashboard is treated as "no" — see {@link #mayReach}, which fails closed on any
     * doubt — rather than as an exception that would need a caller-side fallback.
     *
     * @param boardNames the viewer grant's dashboards.
     * @param exenHostPort the requested ExEn, as {@code "host[:port]"}.
     * @return {@code true} when at least one of the dashboards references that ExEn.
     */
    private static boolean isExenAuthorizedForBoards( String[] boardNames, String exenHostPort )
    {
        if( UtilStr.isEmpty( exenHostPort ) )
            return false;

        for( String board : boardNames )
        {
            try
            {
                for( String exen : ServiceBoard.boardExens( board ) )
                {
                    if( exen.equalsIgnoreCase( exenHostPort ) )
                        return true;
                }
            }
            catch( java.io.IOException e )
            {
                UtilSys.getLogger().log( ILogger.Level.WARNING,
                                         "Could not read dashboard '"+ board +"' while checking a viewer grant's bridge access: "+
                                         e.getMessage() );
            }
        }

        return false;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * @return {@code true} when a secret is configured and the {@link Mac} could be initialised.
     */
    private boolean canSign()
    {
        return UtilStr.isNotEmpty( secret ) && (mac != null);
    }

    /**
     * @return {@code true} when off-LAN guest grants may be issued.
     */
    private boolean isGuestEnabled()
    {
        return canSign() && (ttlSeconds > 0);
    }

    /**
     * Admin grants additionally require a master password: without one, {@code ServletLogin.isMaster}
     * accepts anything, so minting a long-lived full-access cookie would hand one to every device on
     * the LAN.
     * <p>
     * Read from the configuration on each call so it stays in step with the value that servlet checks.
     * Both therefore see the same thing — but note that {@code Config} is a <b>start-up snapshot</b>
     * with no reload: a password changed at runtime through {@code ServiceBoard} is written to
     * {@code config.json} and to that service's own cache, and neither this method nor
     * {@code ServletLogin} will see it until Gum restarts.
     *
     * @return {@code true} when admin grants may be issued.
     */
    boolean isAdminEnabled()
    {
        return canSign()
               && (adminTtlSeconds > 0)
               && UtilStr.isNotEmpty( UtilSys.getConfig().get( "monitoring", "master_password", "" ) );
    }

    /**
     * Viewer grants need no master password: the credential is each dashboard's own, checked by
     * {@link HttpServer.ServletLogin} against {@link ServiceBoard#boardsForPassword}. Package-scoped,
     * not private, for that same servlet to decide upfront whether a board-scoped {@code /gum/login}
     * can do anything at all — mirrors {@link #isAdminEnabled()}'s visibility for the same reason.
     *
     * @return {@code true} when board-viewer grants may be issued.
     */
    boolean isViewerEnabled()
    {
        return canSign() && (viewTtlSeconds > 0);
    }

    /**
     * Tells whether the given address is on the trusted LAN, per the same {@code monitoring.allow}
     * scope the HTTP authentication filter uses.
     *
     * @param ip the client address, may be {@code null}
     * @return {@code true} if the address falls inside the trusted scope
     */
    private boolean isOnTrustedLan( InetAddress ip )
    {
        if( ip == null )
            return false;

        try
        {
            return UtilComm.isClientAllowed( allowScope, ip );
        }
        catch( SocketException e )
        {
            return false;
        }
    }

    /**
     * Builds a fresh signed grant value.
     *
     * @param signCtx    {@link #COOKIE_CTX} or {@link #BRIDGE_CTX}: prefixed to the signed message so a
     *                   value minted for one purpose cannot be presented as the other.
     * @param role       {@link #ROLE_GUEST} or {@link #ROLE_ADMIN}.
     * @param subnets    The subnet field: a {@link #SUBNET_SEP}-joined list, or {@link #ANY_SUBNET}.
     * @param ttlSeconds Lifetime, in seconds, counted from now.
     * @return The signed value, or {@code null} if no secret is configured.
     */
    private String issue( String signCtx, String role, String subnets, int ttlSeconds )
    {
        if( ! canSign() || UtilStr.isEmpty( subnets ) )
            return null;

        long   expiry  = now() + ttlSeconds;
        String payload = role + SEPARATOR + subnets + SEPARATOR + expiry;

        return payload + SEPARATOR + computeHmac( signCtx + payload );
    }

    /**
     * Validates a grant cookie value and returns what it authorizes.
     *
     * @param cookieValue The raw cookie value, may be {@code null}.
     * @return The {@link Grant} when the signature is valid, the role is enabled and the grant has
     *         not expired; {@code null} otherwise.
     */
    private Grant validate( String cookieValue )
    {
        return validate( COOKIE_CTX, cookieValue );
    }

    /**
     * Validates a signed grant value and returns what it authorizes.
     *
     * @param signCtx The signing context the value is expected to have been minted with.
     * @param value   The raw value, may be {@code null}.
     * @return The {@link Grant} when the signature is valid, the role is enabled and the grant has
     *         not expired; {@code null} otherwise.
     */
    private Grant validate( String signCtx, String value )
    {
        if( ! canSign() || UtilStr.isEmpty( value ) )
            return null;

        String[] parts = value.split( SEPARATOR );

        if( parts.length != 4 )
            return null;

        String role         = parts[0];
        String subnets      = parts[1];
        String expiryStr    = parts[2];
        String receivedHmac = parts[3];

        String expectedHmac = computeHmac( signCtx + role + SEPARATOR + subnets + SEPARATOR + expiryStr );

        if( (expectedHmac == null) || ! constantTimeEquals( expectedHmac, receivedHmac ) )
            return null;

        boolean isAdmin  = ROLE_ADMIN.equals( role );
        boolean isViewer = ROLE_VIEW.equals( role );

        if( ! (isAdmin || isViewer || ROLE_GUEST.equals( role )) )
            return null;

        // A role that is switched off in the configuration must stop honouring cookies already out there
        if( isAdmin ? (adminTtlSeconds == 0) : (isViewer ? (viewTtlSeconds == 0) : (ttlSeconds == 0)) )
            return null;

        try
        {
            long expiry = Long.parseLong( expiryStr );

            if( now() > expiry )
                return null;       // Expired

            String[] asTags = isAdmin  ? new String[0]
                             : isViewer ? subnets.split( java.util.regex.Pattern.quote( BOARD_SEP ) )
                                        : subnets.split( SUBNET_SEP );

            return new Grant( isAdmin, isViewer, asTags, expiry );
        }
        catch( NumberFormatException e )
        {
            return null;
        }
    }

    /**
     * Adds a subnet to a guest's set, newest last, keeping at most {@link #MAX_SUBNETS} entries.
     * Re-adding an already known subnet moves it to the end, so the subnets in daily use are the
     * ones that survive trimming.
     *
     * @param asExisting The subnets already in the cookie, may be {@code null}.
     * @param subnetTag  The subnet to add.
     * @return The resulting set.
     */
    private static String[] merge( String[] asExisting, String subnetTag )
    {
        List<String> list = new ArrayList<>( MAX_SUBNETS + 1 );

        if( asExisting != null )
            list.addAll( Arrays.asList( asExisting ) );

        list.remove( subnetTag );
        list.add(    subnetTag );

        while( list.size() > MAX_SUBNETS )
            list.remove( 0 );

        return list.toArray( new String[0] );
    }

    /**
     * Joins subnet tags into the cookie's subnet field.
     */
    private static String join( String[] asSubnets )
    {
        return String.join( SUBNET_SEP, asSubnets );
    }

    /**
     * Joins board names into a viewer cookie's payload field. See {@link #BOARD_SEP} for why this is
     * not {@link #join(String[])}.
     */
    private static String joinBoards( String[] asBoards )
    {
        return String.join( BOARD_SEP, asBoards );
    }

    /**
     * Reads an array-valued key out of the {@code "remote_access"} sub-object, so it can be handed to
     * {@code Config.get} as its default — which that method requires to be non-null, and which is what
     * keeps these nested keys overridable by CLI argument, system property or environment variable.
     *
     * @param jo  The sub-object.
     * @param key The key to read.
     * @return The array, or an empty one when the key is absent or does not hold an array.
     */
    private static JsonArray asArray( JsonObject jo, String key )
    {
        JsonValue jv = jo.get( key );

        return ((jv != null) && jv.isArray()) ? jv.asArray() : Json.array();
    }

    /**
     * Reads a string-valued key out of the {@code "remote_access"} sub-object, so it can be handed to
     * {@code Config.get} as its default. Read defensively rather than with {@code getString(key,def)}
     * because the surrounding configuration uses {@code null} to mean "unset", and that method throws on
     * a JSON null instead of falling back — inside a static initialiser, which would stop Gum from
     * starting at all.
     *
     * @param jo  The sub-object.
     * @param key The key to read.
     * @return The value, or {@code ""} when the key is absent or does not hold a string.
     */
    private static String asString( JsonObject jo, String key )
    {
        JsonValue jv = jo.get( key );

        return ((jv != null) && jv.isString()) ? jv.asString() : "";
    }

    /**
     * Normalises the configured public base URL: trimmed and with no trailing slash.
     * <p>
     * Anything that is not a plain absolute {@code http(s)} origin is rejected outright, because this
     * value is written verbatim into a {@code Location} header: a stray control character there is a
     * response-splitting bug, and a relative or malformed one is a redirect that silently goes nowhere.
     *
     * @param url The configured value, may be {@code null}.
     * @return The normalised URL, or {@code ""} when unset or unusable.
     */
    private static String normalizeUrl( String url )
    {
        if( UtilStr.isEmpty( url ) )
            return "";

        url = url.trim();

        while( url.endsWith( "/" ) )
            url = url.substring( 0, url.length() - 1 );

        if( ! (url.startsWith( "http://" ) || url.startsWith( "https://" )) )
            return "";

        for( int n = 0; n < url.length(); n++ )
        {
            if( url.charAt( n ) <= ' ' )
                return "";
        }

        return url;
    }

    /**
     * Reads a JSON array of strings into a trimmed array, dropping empty entries.
     *
     * @param jaValue   The configured value; anything that is not an array yields an empty result.
     * @param asSubnets When {@code true}, every entry is normalised to a bare {@code /24} tag.
     * @return The resulting array, never {@code null}.
     */
    private static String[] toArray( JsonValue jaValue, boolean asSubnets )
    {
        List<String> list = new ArrayList<>();

        if( (jaValue != null) && jaValue.isArray() )
        {
            JsonArray ja = jaValue.asArray();

            for( JsonValue jv : ja )
            {
                String s = jv.isString() ? jv.asString().trim() : null;

                if( UtilStr.isEmpty( s ) )
                    continue;

                if( asSubnets )
                    s = normalizeSubnetTag( s );

                if( UtilStr.isNotEmpty( s ) )
                    list.add( s );
            }
        }

        return list.toArray( new String[0] );
    }

    /**
     * Normalises a configured subnet entry to a bare {@code /24} tag, accepting the forms a user is
     * likely to write: {@code "192.168.7"}, {@code "192.168.7.*"}, {@code "192.168.7.0"} and
     * {@code "192.168.7.0/24"}.
     *
     * @param s The configured entry.
     * @return The tag (e.g. {@code "192.168.7"}), or {@code null} when it cannot be understood.
     */
    private static String normalizeSubnetTag( String s )
    {
        int slash = s.indexOf( '/' );

        if( slash > 0 )
            s = s.substring( 0, slash );

        String[] octets = s.split( "\\." );

        if( octets.length < 3 )
            return null;

        return octets[0] +'.'+ octets[1] +'.'+ octets[2];
    }

    /**
     * Derives the {@code /24} subnet tag (first three octets) of an IPv4 address.
     *
     * @param ip The address, may be {@code null}.
     * @return A tag like {@code "192.168.6"}, or {@code null} for null / non-IPv4 addresses.
     */
    private static String subnetTag( InetAddress ip )
    {
        if( ! (ip instanceof Inet4Address) )
            return null;

        byte[] o = ip.getAddress();

        return (o[0] & 0xFF) + "." + (o[1] & 0xFF) + "." + (o[2] & 0xFF);
    }

    /**
     * Derives the {@code /24} subnet tag from a host string that may be an IPv4 literal,
     * optionally including a port (e.g. {@code "192.168.6.9:55880"}).
     *
     * @param host The host string, may be {@code null}.
     * @return A tag like {@code "192.168.6"}, or {@code null} when it is not a dotted IPv4 literal.
     */
    private static String subnetTag( String host )
    {
        if( UtilStr.isEmpty( host ) )
            return null;

        int colon = host.indexOf( ':' );

        if( colon >= 0 )
            host = host.substring( 0, colon );

        String[] octets = host.split( "\\." );

        if( octets.length != 4 )
            return null;

        return octets[0] + "." + octets[1] + "." + octets[2];
    }

    /**
     * @return the current time in epoch seconds.
     */
    private static long now()
    {
        return System.currentTimeMillis() / 1000;
    }

    private Mac createMac()
    {
        try
        {
            if( UtilStr.isNotEmpty( secret ) )
            {
                Mac           instance = Mac.getInstance( HMAC_SHA256 );
                SecretKeySpec keySpec  = new SecretKeySpec( secret.getBytes( StandardCharsets.UTF_8 ), HMAC_SHA256 );

                instance.init( keySpec );

                return instance;
            }

            return null;
        }
        catch( NoSuchAlgorithmException | InvalidKeyException e )
        {
            throw new RuntimeException( "Failed to initialize HMAC for remote grants: " + e.getMessage(), e );
        }
    }

    /**
     * Computes the hexadecimal HMAC-SHA256 of the message.
     * <p>
     * Synchronized because {@link Mac#doFinal} is not thread-safe on a shared instance.
     */
    private synchronized String computeHmac( String message )
    {
        if( mac == null )
            return null;

        byte[]        bytes = mac.doFinal( message.getBytes( StandardCharsets.UTF_8 ) );
        StringBuilder sb    = new StringBuilder( bytes.length * 2 );

        for( byte b : bytes )
            sb.append( String.format( "%02x", b ) );

        return sb.toString();
    }

    /**
     * Length-constant string comparison, to avoid leaking the signature via timing.
     */
    private static boolean constantTimeEquals( String a, String b )
    {
        if( a == null || b == null || a.length() != b.length() )
            return false;

        int diff = 0;

        for( int i = 0; i < a.length(); i++ )
            diff |= a.charAt( i ) ^ b.charAt( i );

        return diff == 0;
    }

    /**
     * Reports, at start-up, the configurations that silently do less than they appear to.
     *
     * @param ttlHours      The configured guest grant lifetime, in hours.
     * @param adminTtlHours The configured admin grant lifetime, in hours.
     * @param sPublicUrl    The public base URL as configured, before normalisation.
     */
    private void warnIfInconsistent( int ttlHours, int adminTtlHours, String sPublicUrl )
    {
        ILogger logger = UtilSys.getLogger();

        if( UtilStr.isNotEmpty( sPublicUrl ) && UtilStr.isEmpty( publicUrl ) )
            logger.log( ILogger.Level.WARNING,
                        "Config 'monitoring.remote_access.public_url' is not a plain http(s) URL "+
                        "(e.g. \"http://my.host.org:8080\"): the guest cookie bridge (/gum/login?net=...) is disabled." );

        if( UtilStr.isEmpty( secret ) )
        {
            if( ttlHours > 0 || asGuestSubnets.length > 0 || (viewTtlSeconds > 0) )
                logger.log( ILogger.Level.WARNING,
                            "Config 'monitoring.shared_secret' is empty: remote access grants are disabled." );

            return;
        }

        if( (adminTtlHours > 0) && UtilStr.isEmpty( UtilSys.getConfig().get( "monitoring", "master_password", "" ) ) )
            logger.log( ILogger.Level.WARNING,
                        "Config 'monitoring.remote_access.admin_ttl' is set but 'monitoring.master_password' is empty: "+
                        "admin grants are disabled (any password would be accepted)." );

        if( ((ttlHours > 0) || (asGuestSubnets.length > 0)) && (asGuestPaths.length == 0) )
            logger.log( ILogger.Level.WARNING,
                        "Config 'monitoring.remote_access.guest_paths' is empty: guests are not restricted to any page." );

        if( (asGuestSubnets.length > 0) && ! bindSubnet )
            logger.log( ILogger.Level.WARNING,
                        "Config 'monitoring.remote_access.guest_subnets' is set but '...bind_subnet' is false: "+
                        "guests may drive ExEns outside their own subnet." );

        // Admin is deliberately excluded here: it no longer uses the bridge at all (it signs in
        // directly at /gum/login under whichever host is in use — see the class doc), so an empty
        // public_url has no effect on admin access, only on guests.
        if( UtilStr.isEmpty( publicUrl ) && isGuestEnabled() )
            logger.log( ILogger.Level.WARNING,
                        "Config 'monitoring.remote_access.public_url' is empty: the guest cookie bridge "+
                        "(/gum/login?net=...) is off, so a guest grant is only ever stored under the host name used "+
                        "to reach Gum on the LAN — which a browser will not send to the public one." );
    }
}
