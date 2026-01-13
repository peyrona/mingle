
package com.peyrona.mingle.controllers.lights.shelly;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.controllers.lights.LightCtrlBase;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * Controller for Shelly Plus RGBW PM - a Wi-Fi LED controller.
 * <p>
 * This controller uses the Shelly Gen2 HTTP RPC API to control RGBW LED strips.
 * It supports setting colors, brightness, white channel, and transitions.
 * <p>
 * Configuration parameters:
 * <ul>
 *   <li>address: IP address of the Shelly device (required). E.g.: "192.168.1.100"</li>
 *   <li>channel: Channel ID, usually 0 (optional, default: 0)</li>
 *   <li>interval: Polling interval in milliseconds for status updates (optional, default: 5000)</li>
 *   <li>timeout: HTTP request timeout in seconds (optional, default: 10)</li>
 * </ul>
 * <p>
 * Write accepts:
 * <ul>
 *   <li>String "on" or "off": Simple on/off control</li>
 *   <li>Number 0-100: Set brightness (turns on if off)</li>
 *   <li>pair: Advanced control with keys:
 *     <ul>
 *       <li>"on" (boolean): Turn on/off</li>
 *       <li>"brightness" (0-100): Brightness percentage</li>
 *       <li>"red" (0-255): Red channel value</li>
 *       <li>"green" (0-255): Green channel value</li>
 *       <li>"blue" (0-255): Blue channel value</li>
 *       <li>"white" (0-255): White channel value</li>
 *       <li>"transition" (seconds): Transition duration</li>
 *     </ul>
 *   </li>
 * </ul>
 * <p>
 * Read returns a pair with current status:
 * <ul>
 *   <li>"on": boolean - current power state</li>
 *   <li>"brightness": number - current brightness 0-100</li>
 *   <li>"red", "green", "blue", "white": number 0-255 - current color values</li>
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
    private static final String KEY_ADDRESS  = "address";
    private static final String KEY_CHANNEL  = "channel";
    private static final String KEY_INTERVAL = "interval";
    private static final String KEY_TIMEOUT  = "timeout";

    // Default values
    private static final int DEFAULT_CHANNEL  = 0;
    private static final int DEFAULT_INTERVAL = 5000;
    private static final int DEFAULT_TIMEOUT  = 10;

    private HttpClient       httpClient = null;
    private ScheduledFuture  timer      = null;
    private URI              rpcUri     = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );

        String address = (String) deviceInit.get( KEY_ADDRESS );

        if( UtilStr.isEmpty( address ) )
        {
            sendIsInvalid( "Missing required parameter: 'address'" );
            return;
        }

        try
        {
            // Build RPC endpoint URI
            String uriStr = address.startsWith( "http" ) ? address : "http://" + address;

            if( ! uriStr.endsWith( "/rpc" ) )
                uriStr += "/rpc";

            rpcUri = new URI( uriStr );

            // Store configuration
            set( KEY_ADDRESS, address );

            int channel = ((Number) deviceInit.getOrDefault( KEY_CHANNEL, (float) DEFAULT_CHANNEL )).intValue();
            set( KEY_CHANNEL, Math.max( 0, channel ) );

            int interval = ((Number) deviceInit.getOrDefault( KEY_INTERVAL, (float) DEFAULT_INTERVAL )).intValue();
            set( KEY_INTERVAL, Math.max( 1000, interval ) );

            int timeout = ((Number) deviceInit.getOrDefault( KEY_TIMEOUT, (float) DEFAULT_TIMEOUT )).intValue();
            set( KEY_TIMEOUT, Math.max( 1, timeout ) );

            setValid( true );
        }
        catch( Exception exc )
        {
            sendIsInvalid( "Error configuring Shelly RGBW PM: " + UtilStr.toStringBrief( exc ) );
        }
    }

    @Override
    public void start( IRuntime rt )
    {
        if( isInvalid() )
            return;

        super.start( rt );

        int timeout = (int) get( KEY_TIMEOUT );

        httpClient = HttpClient.newBuilder()
                               .connectTimeout( Duration.ofSeconds( timeout ) )
                               .build();

// FIXME: activarlo cuando vaya a probar esta clase
httpClient = null;
//-------------------------------------------------

        // Start periodic status polling
        int interval = (int) get( KEY_INTERVAL );

        timer = UtilSys.executeWithDelay( getClass().getName(),
                                          1000,      // Initial delay
                                          interval,
                                          this::read );
    }

    @Override
    public void stop()
    {
        if( timer != null )
        {
            timer.cancel( false );
            timer = null;
        }

        httpClient = null;

        super.stop();
    }

    @Override
    public void read()
    {
        if( isFaked() || isInvalid() || httpClient == null )
            return;

        int channel = (int) get( KEY_CHANNEL );

        JsonObject request = new JsonObject();
                   request.add( "id", 1 );
                   request.add( "method", "RGBW.GetStatus" );
                   request.add( "params", new JsonObject().add( "id", channel ) );

        sendRpcRequest( request.toString(), false );
    }

    @Override
    public void write( Object newValue )
    {
        if( isFaked() || isInvalid() || httpClient == null )
            return;

        try
        {
            String jsonBody = buildSetCommand( newValue );
            sendRpcRequest( jsonBody, true );
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
        return true;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Builds the RGBW.Set JSON-RPC command from the input value.
     */
    private String buildSetCommand( Object value ) throws IllegalArgumentException
    {
        int        channel = (int) get( KEY_CHANNEL );
        JsonObject params  = new JsonObject();
                   params.add( "id", channel );

        if( value instanceof String )
        {
            String sVal = ((String) value).toLowerCase().trim();

            if( "on".equals( sVal ) )
            {
                params.add( "on", true );
            }
            else if( "off".equals( sVal ) )
            {
                params.add( "on", false );
            }
            else
            {
                throw new IllegalArgumentException( "String value must be 'on' or 'off'" );
            }
        }
        else if( value instanceof Number )
        {
            int brightness = UtilUnit.setBetween( 0, ((Number) value).intValue(), 100 );
            params.add( "on", true );
            params.add( "brightness", brightness );
        }
        else if( value instanceof pair )
        {
            pair p = (pair) value;

            // On/Off
            Object on = p.get( "on" );

            if( on != null )
            {
                boolean bOn = on instanceof Boolean ? (Boolean) on
                                                    : "true".equalsIgnoreCase( on.toString() );
                params.add( "on", bOn );
            }

            // Brightness
            Object brightness = p.get( "brightness" );

            if( brightness != null )
                params.add( "brightness", UtilUnit.setBetween( 0, ((Number) brightness).intValue(), 100 ) );

            // RGB values
            Object red = p.get( "red" );

            if( red != null )
                params.add( "red", UtilUnit.setBetween( 0, ((Number) red).intValue(), 255 ) );

            Object green = p.get( "green" );

            if( green != null )
                params.add( "green", UtilUnit.setBetween( 0, ((Number) green).intValue(), 255 ) );

            Object blue = p.get( "blue" );

            if( blue != null )
                params.add( "blue", UtilUnit.setBetween( 0, ((Number) blue).intValue(), 255 ) );

            // White channel
            Object white = p.get( "white" );

            if( white != null )
                params.add( "white", UtilUnit.setBetween( 0, ((Number) white).intValue(), 255 ) );

            // Transition duration
            Object transition = p.get( "transition" );

            if( transition != null )
                params.add( "transition_duration", Math.max( 0f, ((Number) transition).floatValue() ) );
        }
        else
        {
            throw new IllegalArgumentException( "Invalid value type. Expected String, Number, or pair." );
        }

        JsonObject request = new JsonObject();
                   request.add( "id", 1 );
                   request.add( "method", "RGBW.Set" );
                   request.add( "params", params );

        return request.toString();
    }

    /**
     * Sends an RPC request to the Shelly device.
     */
    private void sendRpcRequest( String jsonBody, boolean isWrite )
    {
        int timeout = (int) get( KEY_TIMEOUT );

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( rpcUri )
                                         .timeout( Duration.ofSeconds( timeout ) )
                                         .header( "Content-Type", "application/json" )
                                         .POST( HttpRequest.BodyPublishers.ofString( jsonBody ) )
                                         .build();

        httpClient.sendAsync( request, HttpResponse.BodyHandlers.ofString() )
                  .thenApply( HttpResponse::body )
                  .thenAccept( response -> handleResponse( response, isWrite ) )
                  .exceptionally( ex ->
                  {
                      sendGenericError( ILogger.Level.SEVERE,
                                        "Shelly RGBW PM communication error: " + ex.getMessage() );
                      return null;
                  });
    }

    /**
     * Handles the response from the Shelly device.
     */
    private void handleResponse( String responseBody, boolean isWrite )
    {
        try
        {
            pair result = parseResponse( responseBody );

            if( isWrite )
                sendChanged( result );
            else
                sendReaded( result );
        }
        catch( Exception exc )
        {
            sendGenericError( ILogger.Level.WARNING,
                              "Error parsing Shelly response: " + exc.getMessage() );
        }
    }

    /**
     * Parses the JSON response from Shelly and extracts status values.
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

            JsonObject res = resultVal.asObject();

            // Extract power state (check both "ison" and "output")
            JsonValue ison = res.get( "ison" );

            if( ison != null )
                result.put( "on", ison.asBoolean() );
            else
            {
                JsonValue output = res.get( "output" );

                if( output != null )
                    result.put( "on", output.asBoolean() );
            }

            // Extract brightness
            JsonValue brightness = res.get( "brightness" );

            if( brightness != null )
                result.put( "brightness", brightness.asFloat() );

            // Extract RGB values
            JsonValue red = res.get( "red" );

            if( red != null )
                result.put( "red", red.asFloat() );

            JsonValue green = res.get( "green" );

            if( green != null )
                result.put( "green", green.asFloat() );

            JsonValue blue = res.get( "blue" );

            if( blue != null )
                result.put( "blue", blue.asFloat() );

            JsonValue white = res.get( "white" );

            if( white != null )
                result.put( "white", white.asFloat() );

            // Extract power consumption if available
            JsonValue apower = res.get( "apower" );

            if( apower != null )
                result.put( "power", apower.asFloat() );
        }
        catch( Exception e )
        {
            result.put( "error", "Parse error: " + e.getMessage() );
        }

        return result;
    }
}
