
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * Mingle controller for HTTP client operations.
 * <p>
 * This controller provides HTTP/HTTPS communication capabilities, supporting
 * GET, POST, PUT, PATCH, DELETE, HEAD, and OPTIONS methods. It can be used
 * for REST API integration, web service calls, and general HTTP communication.
 *
 * <h3>Configuration Parameters:</h3>
 * <table border="1">
 * <tr><th>Parameter</th><th>Required</th><th>Default</th><th>Description</th></tr>
 * <tr><td>uri</td><td>Yes</td><td>-</td><td>Target URL (e.g., "https://api.example.com/data")</td></tr>
 * <tr><td>interval</td><td>No</td><td>0</td><td>Polling interval in seconds. 0 = no polling (read once). Min: 1s if &gt; 0</td></tr>
 * <tr><td>timeout</td><td>No</td><td>30</td><td>Request timeout in seconds (1-300)</td></tr>
 * <tr><td>headers</td><td>No</td><td>-</td><td>Custom headers as "name:value;name2:value2" string</td></tr>
 * </table>
 *
 * <h3>Usage Examples:</h3>
 * <pre>
 * # Simple GET request (read once)
 * DEVICE my_api
 *     DRIVER HttpClientDriver
 *         CONFIG
 *             uri AS "https://api.example.com/status"
 *
 * # Polling every 30 seconds
 * DEVICE weather_api
 *     DRIVER HttpClientDriver
 *         CONFIG
 *             uri      AS "https://api.weather.com/current"
 *             interval AS 30
 *             timeout  AS 10
 *
 * # With custom headers (e.g., API key)
 * DEVICE secure_api
 *     DRIVER HttpClientDriver
 *         CONFIG
 *             uri     AS "https://api.example.com/data"
 *             headers AS "Authorization:Bearer token123;X-API-Key:mykey"
 * </pre>
 *
 * <h3>Reading Data (GET):</h3>
 * Reading from the device performs a GET request. If an interval is configured,
 * the device automatically polls at that interval. The response body is delivered
 * as the device's value.
 *
 * <h3>Writing Data (POST/PUT/PATCH/DELETE):</h3>
 * Writing to the device sends an HTTP request. The value must be a {@code pair}
 * containing at minimum a "method" key:
 * <pre>
 * # POST with JSON body
 * my_api = pair():put("method", "POST"):put("body", "{\"name\":\"value\"}")
 *
 * # PUT request
 * my_api = pair():put("method", "PUT"):put("body", "{\"status\":\"active\"}")
 *
 * # DELETE request
 * my_api = pair():put("method", "DELETE")
 *
 * # PATCH request
 * my_api = pair():put("method", "PATCH"):put("body", "{\"field\":\"newvalue\"}")
 * </pre>
 *
 * <h3>Supported HTTP Methods:</h3>
 * <ul>
 *   <li><b>GET</b> - Retrieve data (via read() or automatic polling)</li>
 *   <li><b>POST</b> - Create new resource</li>
 *   <li><b>PUT</b> - Update/replace resource</li>
 *   <li><b>PATCH</b> - Partial update</li>
 *   <li><b>DELETE</b> - Remove resource</li>
 *   <li><b>HEAD</b> - Get headers only (no body)</li>
 *   <li><b>OPTIONS</b> - Get supported methods</li>
 * </ul>
 *
 * @author Francisco Jose Morero Peyrona
 * @see <a href="https://github.com/peyrona/mingle">Mingle project</a>
 */
public final class HttpClient extends ControllerBase
{
    // Configuration keys
    private static final String KEY_URI     = "uri";
    private static final String KEY_TIME    = "interval";
    private static final String KEY_TIMEOUT = "timeout";
    private static final String KEY_HEADERS = "headers";

    // Default values
    private static final int DEFAULT_TIMEOUT_SECS = 30;
    private static final int MIN_TIMEOUT_SECS     = 1;
    private static final int MAX_TIMEOUT_SECS     = 300;
    private static final int MIN_INTERVAL_SECS    = 1;

    // Shared HTTP client (thread-safe, reusable)
    private static final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .followRedirects( java.net.http.HttpClient.Redirect.NORMAL )
            .build();

    // Instance state
    private final Object lock = new Object();
    private ScheduledFuture<?> pollingTimer = null;
    private Duration requestTimeout = Duration.ofSeconds( DEFAULT_TIMEOUT_SECS );
    private String[] customHeaders = null;

    //------------------------------------------------------------------------//
    // IController IMPLEMENTATION
    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String,Object> deviceConf, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );     // Must be at beginning: in case an error happens, Listener is needed
        setDeviceConfig( deviceConf );   // Store raw config first, validated values will be stored at the end

        // Parse and validate URI (required)
        Object oUri = get( KEY_URI );

        if( oUri == null || UtilStr.isEmpty( oUri.toString() ) )
        {
            sendIsInvalid( "URI is required (e.g., 'https://api.example.com/data')" );
            return;
        }

        URI uri;
        try
        {
            uri = new URI( oUri.toString().trim() );

            // Validate scheme
            String scheme = uri.getScheme();
            if( scheme == null || (!scheme.equalsIgnoreCase( "http" ) && !scheme.equalsIgnoreCase( "https" )) )
            {
                sendIsInvalid( "URI must use http:// or https:// scheme: " + oUri );
                return;
            }
        }
        catch( Exception ex )
        {
            sendIsInvalid( "Invalid URI format: " + oUri + " (" + ex.getMessage() + ")" );
            return;
        }

        // Parse interval (optional, default 0 = no polling)
        int nInterval = 0;
        Object oInterval = get( KEY_TIME );

        if( oInterval != null )
        {
            nInterval = ((Number) oInterval).intValue();

            if( nInterval < 0 )
            {
                nInterval = 0;
            }
            else if( nInterval > 0 && nInterval < MIN_INTERVAL_SECS )
            {
                sendGenericError( ILogger.Level.WARNING,
                    "Interval " + nInterval + "s is too low. Adjusted to minimum: " + MIN_INTERVAL_SECS + "s" );
                nInterval = MIN_INTERVAL_SECS;
            }
        }

        // Parse timeout (optional, default 30s)
        int nTimeout = DEFAULT_TIMEOUT_SECS;
        Object oTimeout = get( KEY_TIMEOUT );

        if( oTimeout != null )
        {
            nTimeout = ((Number) oTimeout).intValue();

            if( nTimeout < MIN_TIMEOUT_SECS )
            {
                sendGenericError( ILogger.Level.WARNING,
                    "Timeout " + nTimeout + "s is too low. Adjusted to minimum: " + MIN_TIMEOUT_SECS + "s" );
                nTimeout = MIN_TIMEOUT_SECS;
            }
            else if( nTimeout > MAX_TIMEOUT_SECS )
            {
                sendGenericError( ILogger.Level.WARNING,
                    "Timeout " + nTimeout + "s is too high. Adjusted to maximum: " + MAX_TIMEOUT_SECS + "s" );
                nTimeout = MAX_TIMEOUT_SECS;
            }
        }

        requestTimeout = Duration.ofSeconds( nTimeout );

        // Parse custom headers (optional)
        Object oHeaders = get( KEY_HEADERS );

        if( oHeaders != null && !UtilStr.isEmpty( oHeaders.toString() ) )
        {
            customHeaders = parseHeaders( oHeaders.toString() );
        }

        // Store validated configuration (overwrites raw values with validated ones)
        set( KEY_URI, uri );
        set( KEY_TIME, nInterval * 1000 );     // Store in milliseconds
        set( KEY_TIMEOUT, nTimeout );

        setValid( true );
    }

    @Override
    public boolean start( IRuntime rt )
    {
         if( isInvalid() || (! super.start( rt )) )
            return false;

        synchronized( lock )
        {
            int intervalMs = (int) get( KEY_TIME );

            if( intervalMs >= 1000 )
            {
                // Schedule periodic polling
                pollingTimer = UtilSys.executeWithDelay( getClass().getName() + "-" + getDeviceName(),
                                                         intervalMs,        // Initial delay
                                                         intervalMs,        // Period
                                                         () -> read() );

                sendGenericError( ILogger.Level.INFO,
                    "HTTP polling started: " + get( KEY_URI ) + " every " + (intervalMs / 1000) + "s" );
            }
            else
            {
                // Single read on start
                read();
            }
        }

        return isValid();
    }

    @Override
    public void stop()
    {
        synchronized( lock )
        {
            if( pollingTimer != null )
            {
                pollingTimer.cancel( false );
                pollingTimer = null;
            }
        }

        super.stop();
    }

    @Override
    public void read()
    {
        if( isFaked() || isInvalid() )
            return;

        try
        {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri( (URI) get( KEY_URI ) )
                    .timeout( requestTimeout )
                    .GET()
                    .header( "Accept", "application/json, text/plain, */*" )
                    .header( "User-Agent", "Mingle-HttpClient/1.0" );

            // Add custom headers
            addCustomHeaders( builder );

            HttpRequest request = builder.build();

            httpClient.sendAsync( request, HttpResponse.BodyHandlers.ofString() )
                      .thenApply( HttpResponse::body )
                      .thenAccept( this::sendReaded )
                      .exceptionally( ex -> { handleError( "GET", ex ); return null; } );
        }
        catch( Exception ex )
        {
            sendReadError( ex );
        }
    }

    @Override
    public void write( Object newValue )
    {
        if( isFaked() || isInvalid() )
            return;

        if( newValue == null )
        {
            sendWriteError( null, new MingleException( "Value cannot be null" ) );
            return;
        }

        // Value must be a pair containing at least "method"
        if( !(newValue instanceof pair) )
        {
            sendWriteError( newValue, new MingleException(
                "Value must be a pair with 'method' key. Example: pair():put(\"method\", \"POST\"):put(\"body\", \"data\")" ) );
            return;
        }

        UtilSys.execute( null, () -> executeWrite( (pair) newValue ) );
    }

    //------------------------------------------------------------------------//
    // PRIVATE METHODS
    //------------------------------------------------------------------------//

    /**
     * Executes a write operation (POST, PUT, PATCH, DELETE, HEAD, OPTIONS).
     *
     * @param data Pair containing method and optional body
     */
    private void executeWrite( pair data )
    {
        String rawMethod = (String) data.get( "method" );
        String body      = (String) data.get( "body" );

        if( UtilStr.isEmpty( rawMethod ) )
        {
            sendWriteError( data, new MingleException( "Missing 'method' key in pair" ) );
            return;
        }

        final String method = rawMethod.trim().toUpperCase();

        try
        {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                                                     .uri( (URI) get( KEY_URI ) )
                                                     .timeout( requestTimeout )
                                                     .header( "User-Agent", "Mingle-HttpClient/1.0" );

            // Add custom headers
            addCustomHeaders( builder );

            // Build request based on method
            switch( method )
            {
                case "POST":
                    builder.header( "Content-Type", "application/json" )
                           .POST( HttpRequest.BodyPublishers.ofString( body != null ? body : "" ) );
                    break;

                case "PUT":
                    builder.header( "Content-Type", "application/json" )
                           .PUT( HttpRequest.BodyPublishers.ofString( body != null ? body : "" ) );
                    break;

                case "PATCH":
                    builder.header( "Content-Type", "application/json" )
                           .method( "PATCH", HttpRequest.BodyPublishers.ofString( body != null ? body : "" ) );
                    break;

                case "DELETE":
                    builder.DELETE();
                    break;

                case "HEAD":
                    builder.method( "HEAD", HttpRequest.BodyPublishers.noBody() );
                    break;

                case "OPTIONS":
                    builder.method( "OPTIONS", HttpRequest.BodyPublishers.noBody() );
                    break;

                case "GET":
                    builder.GET();
                    break;

                default:
                    sendWriteError( method, new MingleException( "Unsupported HTTP method: " + method + ". Valid: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS" ) );
                    return;
            }

            HttpRequest request = builder.build();

            httpClient.sendAsync( request, HttpResponse.BodyHandlers.ofString() )
                      .thenApply( this::processResponse )
                      .thenAccept( this::sendChanged )
                      .exceptionally( ex -> { handleError( method, ex ); return null; } );
        }
        catch( Exception ex )
        {
            sendWriteError( data, ex instanceof Exception ? (Exception) ex : new MingleException( ex.getMessage() ) );
        }
    }

    /**
     * Processes HTTP response, returning body or status for bodyless responses.
     *
     * @param response The HTTP response
     * @return Response body or status description
     */
    private String processResponse( HttpResponse<String> response )
    {
        String body = response.body();

        // For HEAD/OPTIONS or empty responses, return status info
        if( body == null || body.isEmpty() )
        {
            return "status=" + response.statusCode();
        }

        return body;
    }

    /**
     * Handles async errors and reports them appropriately.
     *
     * @param method HTTP method that failed
     * @param ex     The exception
     */
    private void handleError( String method, Throwable ex )
    {
        String message = ex.getMessage();

        if( message == null || message.isEmpty() )
        {
            message = ex.getClass().getSimpleName();
        }

        // Unwrap CompletionException if needed
        if( ex.getCause() != null )
        {
            message = ex.getCause().getMessage();
        }

        sendGenericError( ILogger.Level.SEVERE, "HTTP " + method + " failed: " + message );
    }

    /**
     * Parses header string into array of name-value pairs.
     * <p>
     * Format: "name1:value1;name2:value2"
     *
     * @param headerStr Header configuration string
     * @return Array of header strings for HttpRequest.headers()
     */
    private String[] parseHeaders( String headerStr )
    {
        if( UtilStr.isEmpty( headerStr ) )
            return null;

        String[] pairs = headerStr.split( ";" );
        String[] result = new String[pairs.length * 2];
        int idx = 0;

        for( String pair : pairs )
        {
            String[] parts = pair.split( ":", 2 );

            if( parts.length == 2 )
            {
                result[idx++] = parts[0].trim();
                result[idx++] = parts[1].trim();
            }
        }

        // Resize if some pairs were invalid
        if( idx < result.length )
        {
            String[] trimmed = new String[idx];
            System.arraycopy( result, 0, trimmed, 0, idx );
            return trimmed;
        }

        return result;
    }

    /**
     * Adds custom headers to the request builder.
     *
     * @param builder HttpRequest builder
     */
    private void addCustomHeaders( HttpRequest.Builder builder )
    {
        if( customHeaders != null )
        {
            for( int i = 0; i < customHeaders.length - 1; i += 2 )
            {
                builder.header( customHeaders[i], customHeaders[i + 1] );
            }
        }
    }
}
