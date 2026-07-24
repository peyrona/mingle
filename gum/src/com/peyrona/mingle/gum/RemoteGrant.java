
package com.peyrona.mingle.gum;

import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Off-LAN remote-access grant that lets a client recently seen on the trusted LAN keep accessing Gum
 * from outside that LAN for a limited time, without sharing a permanent password. It stays dormant
 * unless given a positive {@code remote_grant_ttl} and a non-empty {@code shared_secret}.
 * <p>
 * <b>How it works.</b> While a client's IP is inside the configured trusted scope (see
 * {@code monitoring.allow}), {@link #onTrusted(AccessContext)} issues (or refreshes) a signed grant
 * cookie. The cookie carries the {@code /24} subnet the client was seen on plus an absolute expiry,
 * signed with HMAC-SHA256. When a later request arrives from outside the trusted scope,
 * {@link #isAdmitted(AccessContext)} validates the cookie: a correct signature and a non-expired
 * timestamp grant access. This proves the device was physically on the trusted LAN within the last
 * {@code remote_grant_ttl} hours.
 * <p>
 * Cookie value format (all fields are cookie-safe, ':' is the separator):
 * <pre>
 *   &lt;subnetTag&gt;:&lt;expiryEpochSeconds&gt;:&lt;hexHmacSha256(subnetTag + ":" + expiry)&gt;
 * </pre>
 * <p>
 * Configuration parameters (from config.json, under {@code "monitoring"}):
 * <ul>
 *   <li>{@code remote_grant_ttl} - grant lifetime in hours. {@code 0} or absent disables the feature.</li>
 *   <li>{@code remote_grant_bind_subnet} - when {@code true}, a granted client may only reach ExEns
 *       located on the same subnet where its presence was proven (enforced via {@link #mayReach}).</li>
 *   <li>{@code shared_secret} - HMAC signing key (reused from the existing API authentication setting).
 *       If empty, the feature stays disabled because grants cannot be signed safely.</li>
 * </ul>
 * <p>
 * The design deliberately omits replay nonces, rate limiting and server-side revocation: the
 * intended threat is a former, non-technical user reusing a stale bookmark, not a determined
 * attacker. HMAC signing is kept only so a hand-edited cookie (e.g. a forged expiry) is rejected.
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

    private final int     ttlSeconds;     // Grant lifetime in seconds (0 == feature off)
    private final boolean bindSubnet;     // Restrict a granted client to the subnet it was seen on
    private final String  secret;         // HMAC signing key (empty == feature off)
    private final short   allowScope;     // Trusted-LAN scope (same "monitoring.allow" used by the HTTP filter)
    private final Mac     mac;            // null when the feature is disabled

    /** The single shared instance; reads its configuration once, when the class is first used. */
    private static final RemoteGrant INSTANCE = new RemoteGrant();

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
        int    ttlHours = UtilSys.getConfig().get( "monitoring", "remote_grant_ttl"        , 0     );
        String sAllow   = UtilSys.getConfig().get( "monitoring", "allow"                   , "intranet" );

        this.bindSubnet = UtilSys.getConfig().get( "monitoring", "remote_grant_bind_subnet", false );
        this.secret     = UtilSys.getConfig().get( "monitoring", "shared_secret"           , ""    );
        this.ttlSeconds = Math.max( 0, ttlHours ) * 3600;
        this.allowScope = UtilComm.clientScope( UtilStr.isEmpty( sAllow ) ? "intranet" : sAllow );
        this.mac        = createMac();

        if( ttlHours > 0 && UtilStr.isEmpty( secret ) )
            UtilSys.getLogger().log( com.peyrona.mingle.lang.interfaces.ILogger.Level.WARNING,
                                     "Config 'monitoring.remote_grant_ttl' is set but 'monitoring.shared_secret' is empty: remote access grants are disabled." );
    }

    //------------------------------------------------------------------------//
    // OFF-LAN GRANT HOOKS (called by HttpServer.AuthenticationFilter and CommBridge)

    /**
     * Returns {@code true} when the feature is usable: a positive TTL and a non-empty signing secret.
     */
    boolean isEnabled()
    {
        return (ttlSeconds > 0) && UtilStr.isNotEmpty( secret ) && (mac != null);
    }

    /**
     * Issues (or refreshes) the grant cookie for a client currently on the trusted LAN, using the
     * {@code /24} subnet of its IP. No-op when the feature is disabled or the IP is not IPv4.
     *
     * @param ctx the request/response context.
     */
    void onTrusted( AccessContext ctx )
    {
        String subnet = subnetTag( ctx.clientIp() );

        if( subnet != null )
            ctx.setCookie( COOKIE_NAME, issue( subnet ), ttlSeconds );
    }

    /**
     * Admits an off-LAN request iff it carries a valid, unexpired grant cookie. The caller persists no
     * session for this path, so the grant's expiry is effectively re-checked on every request.
     *
     * @param ctx the request context.
     * @return {@code true} when the grant cookie is present, correctly signed and not expired.
     */
    boolean isAdmitted( AccessContext ctx )
    {
        return subnetIfValid( ctx.cookie( COOKIE_NAME ) ) != null;
    }

    /**
     * When subnet binding is enabled, confines an off-LAN granted client to ExEns on the subnet its
     * grant authorizes. On-LAN clients (allowed broadly by IP, e.g. multi-room admin monitoring) and
     * clients without a valid grant are never restricted here.
     *
     * @param ctx          the request context.
     * @param exenHostPort the requested ExEn as a {@code "host[:port]"} string.
     * @return {@code true} if the target is allowed.
     */
    boolean mayReach( AccessContext ctx, String exenHostPort )
    {
        if( ! (isEnabled() && bindSubnet) )
            return true;

        // On-LAN clients (incl. multi-room admin monitoring) are allowed broadly by IP, never bound by subnet.
        if( isOnTrustedLan( ctx.clientIp() ) )
            return true;

        String allowedSubnet = subnetIfValid( ctx.cookie( COOKIE_NAME ) );

        if( allowedSubnet == null )
            return true;       // No/invalid grant: access is already governed by the HTTP auth filter

        return allowedSubnet.equals( subnetTag( exenHostPort ) );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

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
     * Builds a fresh signed grant value for the given subnet, expiring {@code remote_grant_ttl}
     * hours from now.
     *
     * @param subnetTag The client's {@code /24} subnet (e.g. {@code "192.168.6"}).
     * @return The signed cookie value, or {@code null} if the feature is disabled or the tag is empty.
     */
    private String issue( String subnetTag )
    {
        if( ! isEnabled() || UtilStr.isEmpty( subnetTag ) )
            return null;

        long   expiry  = (System.currentTimeMillis() / 1000) + ttlSeconds;
        String payload = subnetTag + SEPARATOR + expiry;

        return payload + SEPARATOR + computeHmac( payload );
    }

    /**
     * Validates a grant cookie value and returns the subnet it authorizes.
     *
     * @param cookieValue The raw cookie value, may be {@code null}.
     * @return The authorized subnet tag (e.g. {@code "192.168.6"}) when the signature is valid and
     *         the grant has not expired; {@code null} otherwise.
     */
    private String subnetIfValid( String cookieValue )
    {
        if( ! isEnabled() || UtilStr.isEmpty( cookieValue ) )
            return null;

        String[] parts = cookieValue.split( SEPARATOR );

        if( parts.length != 3 )
            return null;

        String subnetTag    = parts[0];
        String expiryStr    = parts[1];
        String receivedHmac = parts[2];

        String expectedHmac = computeHmac( subnetTag + SEPARATOR + expiryStr );

        if( expectedHmac == null || ! constantTimeEquals( expectedHmac, receivedHmac ) )
            return null;

        try
        {
            long expiry = Long.parseLong( expiryStr );

            if( (System.currentTimeMillis() / 1000) > expiry )
                return null;       // Expired
        }
        catch( NumberFormatException e )
        {
            return null;
        }

        return subnetTag;
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

    private Mac createMac()
    {
        try
        {
            if( (ttlSeconds > 0) && UtilStr.isNotEmpty( secret ) )
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
}
