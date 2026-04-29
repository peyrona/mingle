
package com.peyrona.mingle.controllers.lights.shelly;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.controllers.lights.LightCtrlBase;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Controller for Shelly RGBW LED controllers — supports both Gen1 (RGBW2) and
 * Gen2+ (Plus RGBW PM) devices.
 * <p>
 * The device generation is auto-detected at startup by querying the {@code /shelly}
 * endpoint. Gen1 devices use plain REST ({@code GET /color/{channel}}), while Gen2+
 * devices use JSON-RPC ({@code POST /rpc}).
 * <p>
 * <b>Status notifications:</b>
 * <ul>
 *   <li><b>Gen2+</b>: A persistent WebSocket connection ({@code ws://{ip}/rpc}) receives
 *       real-time {@code NotifyStatus} events whenever the device state changes. The {@code read()}
 *       method is still available for on-demand status queries.</li>
 *   <li><b>Gen1</b>: No push mechanism is available for color/brightness changes (Gen1 webhooks
 *       only fire on on/off transitions). Status is obtained via polling through {@code read()}.</li>
 * </ul>
 * <p>
 * Configuration parameters:
 * <ul>
 *   <li>ip     : IP address of the Shelly device (required). E.g.: "192.168.1.100"</li>
 *   <li>channel: Channel ID, usually 0 (optional, default: 0)</li>
 *   <li>mode   : Device mode: "rgbw", "rgb" or "white". By default "rgbw"</li>
 *   <li>ramp   : Default transition duration in milliseconds for writes that don't specify a transition (optional, default: 0)</li>
 * </ul>
 * <p>
 * Write accepts:
 * <ul>
 *   <li>String "on" or "off": Simple on/off control</li>
 *   <li>Number 0-100: Set brightness (turns on if > 0, off if 0)</li>
 *   <li>pair: Advanced control with keys:
 *     <ul>
 *       <li>"on" (boolean): Turn on/off</li>
 *       <li>"brightness" (0-100): Brightness percentage</li>
 *       <li>"red" (0-100): Red channel percentage</li>
 *       <li>"green" (0-100): Green channel percentage</li>
 *       <li>"blue" (0-100): Blue channel percentage</li>
 *       <li>"white" (0-100): White channel percentage</li>
 *       <li>"transition" (seconds): Per-write fade duration (overrides default ramp)</li>
 *     </ul>
 *   </li>
 * </ul>
 * <p>
 * Read returns a pair with current status:
 * <ul>
 *   <li>"on": boolean - current power state</li>
 *   <li>"brightness": number - current brightness 0-100</li>
 *   <li>"red", "green", "blue", "white": number 0-100 - current color percentages</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ShellyRGBWPM
       extends LightCtrlBase
{
    // Configuration keys
    private static final String KEY_IP      = "ip";
    private static final String KEY_CHANNEL = "channel";
    private static final String KEY_MODE    = "mode";
    private static final String KEY_RAMP    = "ramp";

    // Device generation constants
    private static final int GEN1 = 1;
    private static final int GEN2 = 2;

    // Device types (configured via "mode" config key)
    private static final String TYPE_RGBW  = "rgbw";
    private static final String TYPE_RGB   = "rgb";
    private static final String TYPE_WHITE = "white";

    // Hardware modes
    private static final String MODE_COLOR = "color";
    private static final String MODE_WHITE = "white";

    // WebSocket source ID used to register for Gen2 notifications
    private static final String WS_SOURCE = "mingle";

    private volatile HttpClient httpClient      = null;
    private volatile Thread    reconnectThread = null;
    private URI        rpcUri     = null;
    private String     baseUrl    = null;        // Base URL without path (e.g. "http://192.168.1.100")
    private volatile int     deviceGen  = GEN2;       // Default assumption
    private volatile String  deviceType = TYPE_RGBW;  // Mingle device type
    private volatile String  deviceMode = MODE_COLOR; // Hardware operating mode
    private pair       fakeState  = null;
    private volatile WebSocket webSocket  = null;     // Gen2 WebSocket for push notifications

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceConf, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );

        String ip = (String) deviceConf.get( KEY_IP );   // Guarrantee to exists because it is REQUIRED

        try
        {
            // Build base URL
            baseUrl = ip.startsWith( "http" ) ? ip : "http://" + ip;

            // Remove trailing slash if present
            if( baseUrl.endsWith( "/" ) )
                baseUrl = baseUrl.substring( 0, baseUrl.length() - 1 );

            // Remove /rpc suffix if present (we'll add it when needed)
            if( baseUrl.endsWith( "/rpc" ) )
                baseUrl = baseUrl.substring( 0, baseUrl.length() - 4 );

            // Store configuration
            set( KEY_IP, ip );

            int channel = ((Number) deviceConf.getOrDefault( KEY_CHANNEL, 0f )).intValue();
            set( KEY_CHANNEL, Math.max( 0, channel ) );

            deviceType = deviceConf.getOrDefault( KEY_MODE, TYPE_RGBW ).toString().toLowerCase();
            set( KEY_MODE, deviceType );

            int ramp = ((Number) deviceConf.getOrDefault( KEY_RAMP, 0 )).intValue();
            set( KEY_RAMP, Math.max( 0, ramp ) );

            setValid( true );
        }
        catch( Exception exc )
        {
            sendIsInvalid( "Error configuring Shelly RGBW PM: " + UtilStr.toStringBrief( exc ) );
        }
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( ! super.start( rt ) )
            return false;

        if( isFaked() )
            return true;

        if( getHttpClient() == null )
            sendGenericError( ILogger.Level.SEVERE, "No route to: " + getClass().getSimpleName() + ", at: " + get( KEY_IP ) );

        return true;
    }

    @Override
    public void stop()
    {
        Thread t = reconnectThread;
        if( t != null )
        {
            reconnectThread = null;
            t.interrupt();
        }
        closeWebSocket();
        httpClient = null;
        super.stop();
    }

    @Override
    public void read()
    {
        if( isInvalid() )
            return;

        if( isFaked() )
        {
            sendReaded( generateFakedValues() );
            return;
        }

        if( getHttpClient() == null )
            return;

        int channel = (int) get( KEY_CHANNEL );

        if( MODE_WHITE.equals( deviceMode ) )
        {
            if( deviceGen == GEN1 )
            {
                sendGen1Get( "/white/" + channel, false );
            }
            else
            {
                JsonObject request = new JsonObject();
                           request.add( "id",     1 );
                           request.add( "src",    WS_SOURCE );
                           request.add( "method", "Light.GetStatus" );
                           request.add( "params", new JsonObject().add( "id", channel ) );

                sendRpcRequest( request.toString(), false );
            }
        }
        else // MODE_COLOR
        {
            if( deviceGen == GEN1 )
            {
                sendGen1Get( "/color/0", false );
            }
            else
            {
                JsonObject request = new JsonObject();
                           request.add( "id",     1 );
                           request.add( "src",    WS_SOURCE );
                           request.add( "method", getRpcNamespace() + ".GetStatus" );
                           request.add( "params", new JsonObject().add( "id", 0 ) );

                sendRpcRequest( request.toString(), false );
            }
        }
    }

    @Override
    public void write( Object newValue )
    {
        if( isInvalid() )
            return;

        if( isFaked() )
        {
            generateFakedValues();
            fakeState.putAll( normalizeValue( newValue ) );
            sendReaded( generateFakedValues() );
            return;
        }

        if( getHttpClient() == null )
            return;

        try
        {
            if( deviceGen == GEN1 )
            {
                String url = buildGen1WriteUrl( newValue );
                sendGen1Get( url, true );
            }
            else
            {
                sendRpcRequest( buildSetCommand( newValue ), true );
            }
        }
        catch( Exception exc )
        {
            sendWriteError( newValue, exc );
        }
    }

    //------------------------------------------------------------------------//
    // FROM LightCtrlBase

    @Override
    public boolean isDimmable()
    {
        return true;
    }

    @Override
    public boolean isRGB()
    {
        return ! TYPE_WHITE.equals( deviceType );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE — RPC helpers

    /**
     * Returns the Gen2 RPC namespace for the current device type/profile.
     * <p>
     * Maps Mingle device types to Shelly Gen2 component namespaces:
     * <ul>
     *   <li>{@code "white"} → {@code "Light"} (independent white channels)</li>
     *   <li>{@code "rgb"}   → {@code "RGB"} (red, green, blue — no white)</li>
     *   <li>{@code "rgbw"}  → {@code "RGBW"} (red, green, blue, white)</li>
     * </ul>
     *
     * @return The RPC namespace string (e.g. "RGBW.Set", "Light.GetStatus").
     */
    private String getRpcNamespace()
    {
        if( TYPE_WHITE.equals( deviceType ) )
            return "Light";
        else if( TYPE_RGB.equals( deviceType ) )
            return "RGB";
        else
            return "RGBW";
    }

    /**
     * Returns the Gen2 component key used in {@code NotifyStatus} params and
     * {@code Shelly.GetStatus} result to identify the relevant component.
     * <p>
     * Examples: {@code "light:0"}, {@code "rgb:0"}, {@code "rgbw:0"}.
     *
     * @return The component key string.
     */
    private String getComponentKey()
    {
        int channel = (int) get( KEY_CHANNEL );

        if( TYPE_WHITE.equals( deviceType ) )
            return "light:" + channel;
        else if( TYPE_RGB.equals( deviceType ) )
            return "rgb:0";
        else
            return "rgbw:0";
    }

    /**
     * Returns the component ID to use in RPC params for the current device type.
     * <p>
     * For {@code Light} components the channel is configurable (0-3), while
     * {@code RGB} and {@code RGBW} components always use id 0.
     *
     * @return The numeric component ID.
     */
    private int getComponentId()
    {
        return TYPE_WHITE.equals( deviceType ) ? (int) get( KEY_CHANNEL ) : 0;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE — Auxiliary functions

    private HttpClient getHttpClient()
    {
        if( httpClient == null )
        {
            boolean created;

            synchronized( this )
            {
                created = (httpClient == null);

                if( created )
                    httpClient = HttpClient.newBuilder()
                                           .connectTimeout( Duration.ofSeconds( 7 ) )
                                           .build();
            }

            // Post-construction init runs only in the thread that created the client,
            // preventing duplicate detectGeneration()/connectWebSocket() calls.
            if( created )
            {
                if( UtilComm.isReachable( (String) get( KEY_IP ) ) )
                {
                    detectGeneration();

                    if( deviceGen == GEN2 )
                        connectWebSocket();
                }
                else
                {
                    synchronized( this )
                    {
                        httpClient = null;
                    }
                }
            }
        }

        return httpClient;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE — Generation detection

    /**
     * Detects the device generation and operating mode by querying the device.
     * <p>
     * Queries {@code /shelly} and inspects the {@code gen} field:
     * <ul>
     *   <li>{@code gen >= 2} (as number or string) → Gen2+ (JSON-RPC)</li>
     *   <li>Otherwise → Gen1 (plain REST)</li>
     * </ul>
     * If the request fails entirely, defaults to Gen2 as a safe fallback since
     * most modern Shelly devices are Gen2+.
     */
    private void detectGeneration()
    {
        try
        {
            URI        shellyUri = new URI( baseUrl + "/shelly" );
            HttpRequest request  = HttpRequest.newBuilder()
                                              .uri( shellyUri )
                                              .timeout( Duration.ofSeconds( 5 ) )
                                              .GET()
                                              .build();

            HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
            String     body = response.body();
            JsonObject root = Json.parse( body ).asObject();

            JsonValue gen = root.get( "gen" );

            if( gen != null )
            {
                // Gen2+ may report gen as a number (2) or as a string ("2")
                int genNum = -1;

                if( gen.isNumber() )
                    genNum = gen.asInt();
                else if( gen.isString() )
                {
                    try { genNum = Integer.parseInt( gen.asString() ); }
                    catch( NumberFormatException ignored ) { /* keep -1 */ }
                }

                if( genNum >= 2 )
                {
                    deviceGen = GEN2;
                    rpcUri    = new URI( baseUrl + "/rpc" );
                    detectGen2Mode();
                    return;
                }
            }

            // Gen1 or unknown gen value
            deviceGen = GEN1;
            rpcUri    = null;
            detectGen1Mode();
        }
        catch( Exception exc )
        {
            deviceGen = GEN2;
            deviceMode = MODE_COLOR;

            try { rpcUri = new URI( baseUrl + "/rpc" ); }
            catch( Exception ignored ) { /* baseUrl was already validated in set() */ }

            sendGenericError( ILogger.Level.WARNING, "Could not detect Shelly generation (defaulting to Gen2 Color): " + exc.getMessage() );
        }
    }

    /**
     * Detects Gen1 mode by querying /settings.
     */
    private void detectGen1Mode()
    {
        try
        {
            URI         uri     = new URI( baseUrl + "/settings" );
            HttpRequest request = HttpRequest.newBuilder().uri( uri ).timeout( Duration.ofSeconds( 5 ) ).GET().build();
            HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );

            JsonObject root = Json.parse( response.body() ).asObject();
            JsonValue  mode = root.get( "mode" );

            if( mode != null )
                deviceMode = mode.asString().toLowerCase();
        }
        catch( Exception exc )
        {
            deviceMode = MODE_COLOR; // Default
        }
    }

    /**
     * Detects Gen2 profile by checking available components in Shelly.GetStatus.
     * <p>
     * The Shelly Plus RGBW PM exposes different components depending on its profile:
     * <ul>
     *   <li>{@code rgbw:0} present → rgbw profile</li>
     *   <li>{@code rgb:0} present → rgb profile</li>
     *   <li>{@code light:0} present → light profile (4 independent white channels)</li>
     * </ul>
     * If no known component is found, falls back to the user-configured {@code deviceType}.
     */
    private void detectGen2Mode()
    {
        try
        {
            URI         uri     = new URI( baseUrl + "/rpc/Shelly.GetStatus" );
            HttpRequest request = HttpRequest.newBuilder().uri( uri ).timeout( Duration.ofSeconds( 5 ) ).GET().build();
            HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );

            JsonObject root   = Json.parse( response.body() ).asObject();
            JsonObject result = root.get( "result" ).asObject();

            if( result.get( "rgbw:0" ) != null )
            {
                deviceMode = MODE_COLOR;
            }
            else if( result.get( "rgb:0" ) != null )
            {
                deviceMode = MODE_COLOR;
            }
            else if( result.get( "light:0" ) != null )
            {
                deviceMode = MODE_WHITE;
            }
            else
            {
                // Fallback to user-configured mode
                deviceMode = TYPE_WHITE.equals( deviceType ) ? MODE_WHITE : MODE_COLOR;
            }
        }
        catch( Exception exc )
        {
            // Fallback to user-configured mode
            deviceMode = TYPE_WHITE.equals( deviceType ) ? MODE_WHITE : MODE_COLOR;
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE — Gen2 WebSocket

    /**
     * Opens a persistent WebSocket connection to the Gen2 device for receiving
     * real-time {@code NotifyStatus} events.
     * <p>
     * The WebSocket URI is {@code ws://{ip}/rpc}. After connecting, an initial
     * {@code RGBW.GetStatus} request is sent with a {@code src} field so that the
     * device knows where to route notifications.
     */
    private void connectWebSocket()
    {
        try
        {
            String wsUrl = baseUrl.replaceFirst( "^http", "ws" ) + "/rpc";
            URI    wsUri = new URI( wsUrl );

            httpClient.newWebSocketBuilder()
                      .connectTimeout( Duration.ofSeconds( 7 ) )
                      .buildAsync( wsUri, new ShellyWebSocketListener() )
                      .thenAccept( ws ->
                                     {
                                         webSocket = ws;
                                         // Send initial request with 'src' to register for notifications
                                         JsonObject req = new JsonObject();
                                                    req.add( "id",     1 );
                                                    req.add( "src",    WS_SOURCE );
                                                    req.add( "method", getRpcNamespace() + ".GetStatus" );
                                                    req.add( "params", new JsonObject().add( "id", getComponentId() ) );
                                         ws.sendText( req.toString(), true );
                                     } )
                      .exceptionally( ex ->
                                        {
                                            sendGenericError( ILogger.Level.WARNING,
                                                              "Could not open WebSocket to Shelly (falling back to polling): " + ex.getMessage() );
                                            return null;
                                        } );
        }
        catch( Exception exc )
        {
            sendGenericError( ILogger.Level.WARNING,
                              "WebSocket URI error (falling back to polling): " + exc.getMessage() );
        }
    }

    /**
     * Closes the Gen2 WebSocket connection if it is open.
     */
    private void closeWebSocket()
    {
        if( webSocket != null )
        {
            try
            {
                webSocket.sendClose( WebSocket.NORMAL_CLOSURE, "shutdown" );
            }
            catch( Exception ignored ) { /* best-effort close */ }

            webSocket = null;
        }
    }

    /**
     * Handles a {@code NotifyStatus} message received via WebSocket.
     * <p>
     * The message contains a {@code params} object with a key like {@code "rgbw:0"}
     * holding the changed status fields. Only fields that actually changed are present.
     *
     * @param json The raw JSON notification string.
     */
    private void handleNotifyStatus( String json )
    {
        try
        {
            JsonObject root   = Json.parse( json ).asObject();
            JsonValue  params = root.get( "params" );

            if( params == null || ! params.isObject() )
                return;

            String     key     = getComponentKey();
            JsonValue  status  = params.asObject().get( key );

            if( status == null || ! status.isObject() )
                return;

            pair result = parseGen2StatusObject( status.asObject() );

            if( ! result.isEmpty() )
                sendReaded( result );
        }
        catch( Exception exc )
        {
            sendGenericError( ILogger.Level.WARNING, "Error parsing Shelly WebSocket notification: " + exc.getMessage() );
        }
    }

    /**
     * Parses a Gen2 status object (used by both RPC responses and WebSocket notifications).
     * <p>
     * The Gen2 API returns color data as an {@code rgb} array {@code [r, g, b]} where each
     * value is in the range 0-255. This method converts those to 0-100 percentages.
     * Brightness is already returned as a percentage (0-100) by the device.
     * White (for RGBW) is returned as 0-255 and also converted to percentage.
     *
     * @param res The JSON object containing RGBW/RGB/Light status fields.
     * @return A pair with normalized status values (all percentages 0-100).
     */
    private pair parseGen2StatusObject( JsonObject res )
    {
        pair result = new pair();

        JsonValue output = res.get( "output" );
        if( output != null )
            result.put( "on", output.asBoolean() );

        // Brightness is returned as a percentage (0-100) by all Gen2 components (Light, RGB, RGBW).
        JsonValue brightness = res.get( "brightness" );
        if( brightness != null )
            result.put( "brightness", brightness.asFloat() );

        // Color channels: only present in RGB and RGBW components (not Light).
        if( ! TYPE_WHITE.equals( deviceType ) )
        {
            // Gen2 returns colors as an "rgb" array [r, g, b] with values 0-255.
            JsonValue rgb = res.get( "rgb" );
            if( rgb != null && rgb.isArray() )
            {
                JsonArray rgbArr = rgb.asArray();
                if( rgbArr.size() >= 1 )
                    result.put( "red",   deviceToPercent( rgbArr.get( 0 ).asFloat() ) );
                if( rgbArr.size() >= 2 )
                    result.put( "green", deviceToPercent( rgbArr.get( 1 ).asFloat() ) );
                if( rgbArr.size() >= 3 )
                    result.put( "blue",  deviceToPercent( rgbArr.get( 2 ).asFloat() ) );
            }

            // White channel (RGBW only): returned as 0-255, convert to percentage.
            if( TYPE_RGBW.equals( deviceType ) )
            {
                JsonValue white = res.get( "white" );
                if( white != null )
                    result.put( "white", deviceToPercent( white.asFloat() ) );
            }
        }

        JsonValue apower = res.get( "apower" );
        if( apower != null )
            result.put( "power", apower.asFloat() );

        return result;
    }

    /**
     * WebSocket listener that receives Gen2 notifications and handles reconnection.
     * <p>
     * Accumulates text frames (which may arrive in multiple parts), then dispatches
     * complete messages. On close or error, attempts to reconnect after a short delay.
     */
    private class ShellyWebSocketListener implements WebSocket.Listener
    {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText( WebSocket ws, CharSequence data, boolean last )
        {
            buffer.append( data );

            if( last )
            {
                String message = buffer.toString();
                buffer.setLength( 0 );
                processWebSocketMessage( message );
            }

            ws.request( 1 );
            return null;
        }

        @Override
        public CompletionStage<?> onClose( WebSocket ws, int statusCode, String reason )
        {
            webSocket = null;
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError( WebSocket ws, Throwable error )
        {
            sendGenericError( ILogger.Level.WARNING, "Shelly WebSocket error: " + error.getMessage() );
            webSocket = null;
            scheduleReconnect();
        }

        /**
         * Processes a complete WebSocket JSON message.
         * <p>
         * Dispatches {@code NotifyStatus} events to {@link #handleNotifyStatus(String)}.
         * Regular RPC responses (with {@code "result"}) are parsed as read responses.
         */
        private void processWebSocketMessage( String json )
        {
            try
            {
                JsonObject root   = Json.parse( json ).asObject();
                JsonValue  method = root.get( "method" );

                if( method != null && "NotifyStatus".equals( method.asString() ) )
                {
                    handleNotifyStatus( json );
                }
                else
                {
                    // Regular RPC response (e.g. from our initial GetStatus request)
                    JsonValue resultVal = root.get( "result" );

                    if( resultVal != null && resultVal.isObject() )
                    {
                        pair result = parseGen2StatusObject( resultVal.asObject() );

                        if( ! result.isEmpty() )
                            sendReaded( result );
                    }
                }
            }
            catch( Exception exc )
            {
                sendGenericError( ILogger.Level.WARNING, "Error processing WebSocket message: " + exc.getMessage() );
            }
        }

        /**
         * Schedules a WebSocket reconnection attempt after a 5-second delay.
         * Only reconnects if the controller is still started and not stopped.
         */
        private void scheduleReconnect()
        {
            if( httpClient == null )
                return;   // Controller has been stopped

            Thread t = new Thread( () ->
            {
                try { Thread.sleep( 5000 ); } catch( InterruptedException ignored ) { Thread.currentThread().interrupt(); return; }

                if( httpClient != null && webSocket == null )
                    connectWebSocket();
            }, "Shelly-WS-Reconnect-" + getDeviceName() );

            reconnectThread = t;
            t.start();
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE — Value normalization and conversion

    /**
     * Parses and normalizes a write value into a pair with validated entries.
     * <p>
     * Color values (red, green, blue, white) are accepted as percentages (0-100).
     * <p>
     * Handles three input types:
     * <ul>
     *   <li>String "on"/"off": Sets the "on" key to true/false</li>
     *   <li>Number: Clamps to 0-100, sets "on" to true if > 0, false if 0</li>
     *   <li>pair: Extracts and validates individual keys (on, brightness, red, green, blue, white, transition)</li>
     * </ul>
     *
     * @param value The input value to normalize.
     * @return A sparse pair with only the keys present in the input.
     *         Returns an empty pair if the value type is not recognized.
     */
    private pair normalizeValue( Object value )
    {
        pair result = new pair();

        if( value instanceof String )
        {
            String sVal = ((String) value).toLowerCase().trim();

                 if( "on".equals(  sVal ) )  result.put( "on", true );
            else if( "off".equals( sVal ) )  result.put( "on", false );
        }
        else if( value instanceof Boolean )
        {
            result.put( "on", (boolean) value );
        }
        else if( value instanceof Number )
        {
            int brightness = UtilUnit.setBetween( 0, ((Number) value).intValue(), 100 );
            result.put( "on", brightness > 0 );
            result.put( "brightness", brightness );
        }
        else if( value instanceof pair )
        {
            pair p = (pair) value;

            if( p.hasKey( "on" ) )
            {
                Object on = p.get( "on" );
                boolean bOn = on instanceof Boolean ? (Boolean) on
                                                    : "true".equalsIgnoreCase( on.toString() );
                result.put( "on", bOn );
            }

            if( p.hasKey( "brightness" ) )
            {
                int brightness = UtilUnit.setBetween( 0, ((Number) p.get( "brightness" )).intValue(), 100 );
                result.put( "brightness", brightness );

                if( brightness == 0 )
                    result.put( "on", false );
            }

            if( p.hasKey( "red" ) )
                result.put( "red", UtilUnit.setBetween( 0, ((Number) p.get( "red" )).intValue(), 100 ) );

            if( p.hasKey( "green" ) )
                result.put( "green", UtilUnit.setBetween( 0, ((Number) p.get( "green" )).intValue(), 100 ) );

            if( p.hasKey( "blue" ) )
                result.put( "blue", UtilUnit.setBetween( 0, ((Number) p.get( "blue" )).intValue(), 100 ) );

            if( p.hasKey( "white" ) )
                result.put( "white", UtilUnit.setBetween( 0, ((Number) p.get( "white" )).intValue(), 100 ) );

            if( p.hasKey( "transition" ) )
                result.put( "transition", Math.max( 0f, ((Number) p.get( "transition" )).floatValue() ) );
        }

        return result;
    }

    /**
     * Converts a color percentage (0-100) to a device value (0-255).
     *
     * @param percent The percentage value (0-100).
     * @return The device value (0-255).
     */
    private int percentToDevice( int percent )
    {
        return (int) (UtilUnit.setBetween( 0, percent, 100 ) * 255 / 100);
    }

    /**
     * Converts a device color value (0-255) to a percentage (0-100).
     *
     * @param deviceValue The device value (0-255).
     * @return The percentage value (0-100).
     */
    private float deviceToPercent( float deviceValue )
    {
        return (float) Math.round( deviceValue * 100f / 255f );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE — Gen2 HTTP RPC

    /**
     * Builds the appropriate Gen2 JSON-RPC Set command from the input value.
     * <p>
     * The method name is determined by {@link #getRpcNamespace()}:
     * {@code Light.Set}, {@code RGB.Set}, or {@code RGBW.Set}.
     * <p>
     * Color values are sent as a single {@code rgb} array {@code [r, g, b]} with values
     * in the device range 0-255 (converted from the Mingle 0-100 percentage range).
     */
    private String buildSetCommand( Object value ) throws IllegalArgumentException
    {
        pair parsed = normalizeValue( value );

        if( parsed.isEmpty() )
            throw new IllegalArgumentException( "Invalid value type. Expected String, Number, or pair." );

        JsonObject params = new JsonObject();
                   params.add( "id", getComponentId() );

        if( parsed.hasKey( "on" ) )
            params.add( "on", (boolean) parsed.get( "on" ) );

        if( TYPE_WHITE.equals( deviceType ) )
        {
            if( parsed.hasKey( "brightness" ) )
                params.add( "brightness", ((Number) parsed.get( "brightness" )).intValue() );
        }
        else
        {
            if( parsed.hasKey( "brightness" ) )
                params.add( "brightness", ((Number) parsed.get( "brightness" )).intValue() );

            // Gen2 API expects colors as an "rgb" array [r, g, b] with values 0-255.
            boolean hasAnyColor = parsed.hasKey( "red" ) || parsed.hasKey( "green" ) || parsed.hasKey( "blue" );
            if( hasAnyColor )
            {
                int r = parsed.hasKey( "red" )   ? percentToDevice( ((Number) parsed.get( "red" )).intValue() )   : 0;
                int g = parsed.hasKey( "green" ) ? percentToDevice( ((Number) parsed.get( "green" )).intValue() ) : 0;
                int b = parsed.hasKey( "blue" )  ? percentToDevice( ((Number) parsed.get( "blue" )).intValue() )  : 0;
                params.add( "rgb", new JsonArray().add( r ).add( g ).add( b ) );
            }

            if( TYPE_RGBW.equals( deviceType ) && parsed.hasKey( "white" ) )
                params.add( "white", percentToDevice( ((Number) parsed.get( "white" )).intValue() ) );
        }

        // Transition: per-write "transition" (seconds) takes precedence over default "ramp" (millis)
        float transitionSecs;
        if( parsed.hasKey( "transition" ) )
        {
            transitionSecs = ((Number) parsed.get( "transition" )).floatValue();
        }
        else
        {
            int ramp = ((Number) get( KEY_RAMP )).intValue();
            transitionSecs = ramp / 1000f;  // from millis to secs
        }

        if( transitionSecs > 0 )
            params.add( "transition_duration", transitionSecs );

        JsonObject request = new JsonObject();
                   request.add( "id",     1 );
                   request.add( "src",    WS_SOURCE );
                   request.add( "method", getRpcNamespace() + ".Set" );
                   request.add( "params", params );

        return request.toString();
    }

    /**
     * Sends a Gen2 JSON-RPC request to the Shelly device via HTTP.
     */
    private void sendRpcRequest( String jsonBody, boolean isWrite )
    {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( rpcUri )
                                         .timeout( Duration.ofSeconds( 7 ) )
                                         .header( "Content-Type", "application/json" )
                                         .POST( HttpRequest.BodyPublishers.ofString( jsonBody ) )
                                         .build();

        httpClient.sendAsync( request, HttpResponse.BodyHandlers.ofString() )
                  .thenApply( HttpResponse::body )
                  .thenAccept( response -> handleResponse( response, isWrite ) )
                  .exceptionally( ex ->
                                    {
                                        sendGenericError( ILogger.Level.SEVERE, "Shelly RGBW PM communication error: " + ex.getMessage() );
                                        return null;
                                    } );
    }

    /**
     * Handles the Gen2 JSON-RPC response from the Shelly device (HTTP path).
     */
    private void handleResponse( String responseBody, boolean isWrite )
    {
        try
        {
            pair result = parseResponse( responseBody );

            if( isWrite )  sendChanged( result );
            else           sendReaded(  result );
        }
        catch( Exception exc )
        {
            sendGenericError( ILogger.Level.WARNING, "Error parsing Shelly response: " + exc.getMessage() );
        }
    }

    /**
     * Parses a Gen2 JSON-RPC response (HTTP) and extracts status values.
     * <p>
     * Color values are converted from device range (0-255) to percentages (0-100).
     */
    private pair parseResponse( String json )
    {
        pair result = new pair();

        try
        {
            JsonObject root = Json.parse( json ).asObject();

            // Check for error
            JsonValue error = root.get( "error" );

            if( error != null && error.isObject() )
            {
                JsonValue message = error.asObject().get( "message" );

                if( message != null )
                    result.put( "error", message.asString() );

                return result;
            }

            // Extract "result" object
            JsonValue resultVal = root.get( "result" );

            if( resultVal == null || ! resultVal.isObject() )
                return result;

            result = parseGen2StatusObject( resultVal.asObject() );
        }
        catch( Exception e )
        {
            result.put( "error", "Parse error: " + e.getMessage() );
        }

        return result;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE — Gen1 HTTP REST

    /**
     * Builds the Gen1 write URL with query parameters for {@code /color/{channel}}.
     * <p>
     * Maps normalized values to Gen1 parameters:
     * {@code turn=on/off}, {@code gain=0-100}, {@code red/green/blue/white=0-255},
     * {@code transition=milliseconds}.
     * <p>
     * <b>Important:</b> On Gen1 devices, {@code gain} is the master brightness multiplier
     * for all color channels. If colors (red/green/blue/white) are set without specifying
     * brightness, {@code gain=100} is sent as a default — without it the LED output would
     * remain dark if the device's current gain was 0.
     *
     * @param value The write value (String, Number, or pair).
     * @return The URL path with query string (e.g. "/color/0?turn=on&red=255&gain=50").
     * @throws IllegalArgumentException If the value cannot be normalized.
     */
    private String buildGen1WriteUrl( Object value ) throws IllegalArgumentException
    {
        pair parsed = normalizeValue( value );

        if( parsed.isEmpty() )
            throw new IllegalArgumentException( "Invalid value type. Expected String, Number, or pair." );

        int           channel = (int) get( KEY_CHANNEL );
        StringBuilder sb      = new StringBuilder();
        boolean       first   = true;

        if( MODE_WHITE.equals( deviceMode ) )
        {
            sb.append( "/white/" ).append( channel ).append( '?' );

            Object on = parsed.get( "on", null );
            if( on != null )
            {
                sb.append( "turn=" ).append( (boolean) on ? "on" : "off" );
                first = false;
            }

            Object brightness = parsed.get( "brightness", null );
            if( brightness != null )
            {
                if( ! first ) sb.append( '&' );
                sb.append( "brightness=" ).append( ((Number) brightness).intValue() );
                first = false;
            }
        }
        else // MODE_COLOR
        {
            sb.append( "/color/0?" );

            Object on = parsed.get( "on", null );
            if( on != null )
            {
                sb.append( "turn=" ).append( (boolean) on ? "on" : "off" );
                first = false;
            }

            if( TYPE_WHITE.equals( deviceType ) )
            {
                Object brightness = parsed.get( "brightness", null );
                if( brightness != null )
                {
                    if( ! first ) sb.append( '&' );
                    sb.append( "white=" ).append( percentToDevice( ((Number) brightness).intValue() ) );
                    first = false;
                }
            }
            else
            {
                Object brightness = parsed.get( "brightness", null );
                if( brightness != null )
                {
                    if( ! first ) sb.append( '&' );
                    sb.append( "gain=" ).append( ((Number) brightness).intValue() );
                    first = false;
                }

                boolean hasAnyColor = parsed.hasKey( "red" ) || parsed.hasKey( "green" )
                                   || parsed.hasKey( "blue" ) || parsed.hasKey( "white" );

                // gain is the master brightness multiplier on Gen1 /color/0 endpoint.
                // If colors are being set but no gain was specified, default to 100
                // to ensure the LED strip is actually visible.
                if( hasAnyColor && ! parsed.hasKey( "brightness" ) )
                {
                    if( ! first ) sb.append( '&' );
                    sb.append( "gain=100" );
                    first = false;
                }

                Object red = parsed.get( "red", null );
                if( red != null )
                {
                    if( ! first ) sb.append( '&' );
                    sb.append( "red=" ).append( percentToDevice( ((Number) red).intValue() ) );
                    first = false;
                }

                Object green = parsed.get( "green", null );
                if( green != null )
                {
                    if( ! first ) sb.append( '&' );
                    sb.append( "green=" ).append( percentToDevice( ((Number) green).intValue() ) );
                    first = false;
                }

                Object blue = parsed.get( "blue", null );
                if( blue != null )
                {
                    if( ! first ) sb.append( '&' );
                    sb.append( "blue=" ).append( percentToDevice( ((Number) blue).intValue() ) );
                    first = false;
                }

                Object white = parsed.get( "white", null );
                if( TYPE_RGBW.equals( deviceType ) && white != null )
                {
                    if( ! first ) sb.append( '&' );
                    sb.append( "white=" ).append( percentToDevice( ((Number) white).intValue() ) );
                    first = false;
                }
            }
        }

        Object oRamp = parsed.get( "transition", null );
        int    nRamp = (oRamp instanceof Number) ? ((Number) parsed.get( "transition" )).intValue()
                                                 : ((Number) get( KEY_RAMP )).intValue();
        if( nRamp > 0 )
        {
            if( ! first )
                sb.append( '&' );

            sb.append( "transition=" ).append( nRamp );
        }

        return sb.toString();
    }

    /**
     * Sends an async GET request to the Gen1 Shelly device and handles the response.
     *
     * @param path    The URL path (e.g. "/color/0" or "/color/0?turn=on&red=255").
     * @param isWrite {@code true} if this is a write operation, {@code false} for read.
     */
    private void sendGen1Get( String path, boolean isWrite )
    {
        try
        {
            URI        uri     = new URI( baseUrl + path );
            HttpRequest request = HttpRequest.newBuilder()
                                             .uri( uri )
                                             .timeout( Duration.ofSeconds( 7 ) )
                                             .GET()
                                             .build();

            httpClient.sendAsync( request, HttpResponse.BodyHandlers.ofString() )
                      .thenApply( HttpResponse::body )
                      .thenAccept( response -> handleGen1Response( response, isWrite ) )
                      .exceptionally( ex ->
                                        {
                                            sendGenericError( ILogger.Level.SEVERE, "Shelly RGBW (Gen1) communication error: " + ex.getMessage() );
                                            return null;
                                        } );
        }
        catch( Exception exc )
        {
            sendGenericError( ILogger.Level.SEVERE, "Shelly RGBW (Gen1) URI error: " + exc.getMessage() );
        }
    }

    /**
     * Handles a Gen1 response (flat JSON, no "result" wrapper).
     */
    private void handleGen1Response( String responseBody, boolean isWrite )
    {
        try
        {
            pair result = parseGen1Response( responseBody );

            if( isWrite )  sendChanged( result );
            else           sendReaded(  result );
        }
        catch( Exception exc )
        {
            sendGenericError( ILogger.Level.WARNING, "Error parsing Shelly Gen1 response: " + exc.getMessage()
                             + " — raw: " + responseBody.substring( 0, Math.min( responseBody.length(), 200 ) ) );
        }
    }

    /**
     * Parses a Gen1 flat JSON response into a normalized pair.
     * <p>
     * Maps Gen1 fields to standard keys:
     * {@code "ison" → "on"}, {@code "gain" → "brightness"},
     * and converts color values from 0-255 to 0-100 percentages.
     * <p>
     * Also handles Gen2 backward-compatible responses from the {@code /color/0} endpoint,
     * which uses {@code "brightness"} instead of {@code "gain"} and returns colors as
     * individual 0-255 fields (same as Gen1).
     *
     * @param json The raw JSON response string.
     * @return A pair with normalized status values.
     */
    private pair parseGen1Response( String json )
    {
        pair result = new pair();

        try
        {
            JsonObject root = Json.parse( json ).asObject();

            // Power state: Gen1 uses "ison"
            JsonValue ison = root.get( "ison" );
            if( ison != null )
                result.put( "on", ison.asBoolean() );

            if( MODE_WHITE.equals( deviceMode ) || TYPE_WHITE.equals( deviceType ) )
            {
                // In Gen1 White mode, intensity is "brightness".
                // In Gen1 Color mode, White channel intensity is "white".
                JsonValue val = root.get( MODE_WHITE.equals( deviceMode ) ? "brightness" : "white" );

                if( val != null )
                {
                    float fVal = val.asFloat();
                    result.put( "brightness", MODE_WHITE.equals( deviceMode ) ? fVal : deviceToPercent( fVal ) );
                }
            }
            else
            {
                // Brightness: Gen1 uses "gain" (0-100).
                // Gen2 /color/0 compat endpoint uses "brightness" (0-100).
                // Prefer "gain" for true Gen1, fall back to "brightness" for Gen2 compat.
                JsonValue gain = root.get( "gain" );
                if( gain != null )
                {
                    result.put( "brightness", gain.asFloat() );
                }
                else
                {
                    JsonValue brightness = root.get( "brightness" );
                    if( brightness != null )
                        result.put( "brightness", brightness.asFloat() );
                }

                // Color values: both Gen1 and Gen2 /color/0 return 0-255, convert to percentage
                JsonValue red = root.get( "red" );
                if( red != null )
                    result.put( "red", deviceToPercent( red.asFloat() ) );

                JsonValue green = root.get( "green" );
                if( green != null )
                    result.put( "green", deviceToPercent( green.asFloat() ) );

                JsonValue blue = root.get( "blue" );
                if( blue != null )
                    result.put( "blue", deviceToPercent( blue.asFloat() ) );

                if( TYPE_RGBW.equals( deviceType ) )
                {
                    JsonValue white = root.get( "white" );
                    if( white != null )
                        result.put( "white", deviceToPercent( white.asFloat() ) );
                }
            }

            // Power consumption: Gen1 uses "power", Gen2 uses "apower"
            JsonValue power = root.get( "power" );
            if( power == null )
                power = root.get( "apower" );  // Gen2 compat fallback
            if( power != null )
                result.put( "power", power.asFloat() );
        }
        catch( Exception e )
        {
            result.put( "error", "Parse error: " + e.getMessage() );
        }

        return result;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE — Faked mode

    /**
     * Generates faked device values for testing without a real device.
     */
    private pair generateFakedValues()
    {
        if( fakeState == null )
        {
            fakeState = new pair();
            fakeState.put( "on",         Math.random() < 0.5 );
            fakeState.put( "brightness", (float) (Math.random() * 100) );
            fakeState.put( "red",        (float) (Math.random() * 100) );
            fakeState.put( "green",      (float) (Math.random() * 100) );
            fakeState.put( "blue",       (float) (Math.random() * 100) );
            fakeState.put( "white",      (float) (Math.random() * 100) );
        }

        return new pair()
                    .put( "on",         fakeState.get( "on" ) )
                    .put( "brightness", fakeState.get( "brightness" ) )
                    .put( "red",        fakeState.get( "red" ) )
                    .put( "green",      fakeState.get( "green" ) )
                    .put( "blue",       fakeState.get( "blue" ) )
                    .put( "white",      fakeState.get( "white" ) );
    }
}