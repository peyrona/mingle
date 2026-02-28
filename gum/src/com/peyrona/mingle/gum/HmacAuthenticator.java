
package com.peyrona.mingle.gum;

import com.peyrona.mingle.lang.japi.UtilSys;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpSession;

/**
 * Provides HMAC-based authentication for HTTP requests in the Gum monitoring server.
 * <p>
 * This authenticator validates incoming requests using HMAC-SHA256 signatures to ensure
 * request integrity and authenticity. It supports timestamp validation to prevent replay attacks
 * and maintains session-based authentication to avoid re-validating each request.
 * <p>
 * Authentication parameters:
 * <ul>
 *   <li>{@code action} - The action being performed</li>
 *   <li>{@code data} - The request payload data</li>
 *   <li>{@code timestamp} - Unix timestamp in seconds</li>
 *   <li>{@code hmac} - HMAC-SHA256 signature of (action + timestamp + data)</li>
 * </ul>
 * <p>
 * Configuration parameters (from config.json):
 * <ul>
 *   <li>{@code monitoring.shared_secret} - Secret key for HMAC computation</li>
 *   <li>{@code monitoring.hmac_tolerance} - Max acceptable timestamp difference in seconds (default: 60)</li>
 * </ul>
 */
final class HmacAuthenticator
{
    private static final String HMAC_SHA256       = "HmacSHA256";
    private static final int    DEFAULT_TOLERANCE = 60;   // In seconds

    private final String secretKey;
    private final int    tolerance;   // In seconds
    private final Mac    mac;

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    /**
     * Constructs a new HmacAuthenticator and initializes it with configuration values.
     * <p>
     * Reads the secret key and timestamp tolerance from the configuration. If no
     * shared secret is configured, authentication will be effectively disabled.
     */
    HmacAuthenticator()
    {
        secretKey = UtilSys.getConfig().get( "monitoring", "shared_secret", "" );
        tolerance = UtilSys.getConfig().get( "monitoring", "hmac_tolerance", DEFAULT_TOLERANCE );
        mac       = createMac();
    }

    /**
     * Validates an HTTP request using HMAC-SHA256 signature authentication.
     * <p>
     * This method performs the following validation steps:
     * <ol>
     *   <li>Checks if authentication is enabled (returns null if disabled)</li>
     *   <li>Verifies if the session is already authenticated (returns null if true)</li>
     *   <li>Validates the timestamp is within the tolerance window</li>
     *   <li>Computes the HMAC of (action + timestamp + data) and compares with received HMAC</li>
     * </ol>
     * <p>
     * If validation succeeds, marks the session as authenticated for future requests.
     *
     * @param request the HTTP request to validate, must contain parameters: action, data, timestamp, hmac
     * @return an error message if validation fails, or null if validation succeeds or authentication is disabled
     * @throws java.io.IOException if an I/O error occurs during request processing
     */
    String validateRequest( javax.servlet.http.HttpServletRequest request ) throws java.io.IOException
    {
        if( ! isAuthenticationEnabled() )
            return null;

        String receivedHmac = asString( request, "hmac" );

        // No hmac param → fast path: rely on existing session (normal subsequent calls)
        if( receivedHmac == null )
        {
            HttpSession session       = request.getSession( false );
            Boolean     authenticated = (session != null) ? (Boolean) session.getAttribute( "authenticated" ) : null;

            return (authenticated != null && authenticated) ? null : "Missing authentication parameters";
        }

        // hmac param present → always validate from scratch, ignoring any cached session
        String action       = asString( request, "action" );
        String data         = asString( request, "data" );
        String timestampStr = asString( request, "timestamp" );

        if( action == null || data == null || timestampStr == null )
            return "Missing authentication parameters";

        String timeResult = validateTimestamp( timestampStr );
        if( timeResult != null )
            return timeResult;

        String computedHmac = computeHmac( action + timestampStr + data );

        if( ! computedHmac.equals( receivedHmac ) )
            return "Invalid HMAC";

        // Validation succeeded — mark session as authenticated for subsequent requests
        request.getSession( true ).setAttribute( "authenticated", Boolean.TRUE );

        return null;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Creates and initializes a Mac instance for HMAC-SHA256 computation.
     * <p>
     * If authentication is not enabled (no secret key configured), returns null.
     * Otherwise, creates a Mac instance initialized with the secret key.
     *
     * @return the initialized Mac instance, or null if authentication is disabled
     * @throws RuntimeException if HMAC-SHA256 algorithm is not available or key is invalid
     */
    private Mac createMac()
    {
        try
        {
            if( isAuthenticationEnabled() )
            {
                Mac           macInstance = Mac.getInstance( HMAC_SHA256 );
                SecretKeySpec _secretKey_ = new SecretKeySpec( secretKey.getBytes( StandardCharsets.UTF_8 ), HMAC_SHA256 );

                macInstance.init( _secretKey_ );
                return macInstance;
            }
            return null;
        }
        catch( NoSuchAlgorithmException | InvalidKeyException e )
        {
            throw new RuntimeException( "Failed to initialize HMAC: " + e.getMessage(), e );
        }
    }

    /**
     * Computes the HMAC-SHA256 hash of the given message.
     * <p>
     * The message is encoded to UTF-8, the HMAC is computed, and the result is
     * converted to a hexadecimal string.
     * <p>
     * Synchronized because {@link Mac#doFinal} is not thread-safe when called
     * concurrently on the same instance.
     *
     * @param message the message to hash
     * @return the hexadecimal HMAC-SHA256 hash, or null if authentication is disabled (mac is null)
     */
    private synchronized String computeHmac( String message )
    {
        if( mac == null )
            return null;

        byte[] hmacBytes = mac.doFinal( message.getBytes( StandardCharsets.UTF_8 ) );
        StringBuilder sb = new StringBuilder( hmacBytes.length * 2 );
        for( byte b : hmacBytes )
            sb.append( String.format( "%02x", b ) );
        return sb.toString();
    }

    /**
     * Validates that the provided timestamp is within the acceptable time window.
     * <p>
     * The timestamp must be a Unix timestamp in seconds and must not differ from
     * the current time by more than the configured tolerance (default 60 seconds).
     * This prevents replay attacks by rejecting old timestamps.
     *
     * @param timestampStr the timestamp as a string (Unix timestamp in seconds)
     * @return an error message if validation fails, or null if validation succeeds
     */
    private String validateTimestamp( String timestampStr )
    {
        try
        {
            long timestamp   = Long.parseLong( timestampStr );
            long currentTime = System.currentTimeMillis() / 1000;
            long diff        = Math.abs( currentTime - timestamp );

            if( diff > tolerance )
                return "Timestamp expired: difference is " + diff + " seconds (max " + tolerance + ")";

            return null;
        }
        catch( NumberFormatException e )
        {
            return "Invalid timestamp format";
        }
    }

    /**
     * Retrieves a parameter value from the HTTP request.
     * <p>
     * Returns the parameter value if present, or null if the parameter does not exist.
     *
     * @param request the HTTP request to get the parameter from
     * @param paramName the name of the parameter to retrieve
     * @return the parameter value as a string, or null if not present
     */
    private String asString( javax.servlet.http.HttpServletRequest request, String paramName )
    {
        String value = request.getParameter( paramName );
        return (value != null) ? value : null;
    }

    /**
     * Checks if authentication is enabled based on the configured secret key.
     * <p>
     * Authentication is considered enabled if a non-empty secret key is configured.
     *
     * @return true if a secret key is configured, false otherwise
     */
    private boolean isAuthenticationEnabled()
    {
        return secretKey != null && ! secretKey.isEmpty();
    }
}
