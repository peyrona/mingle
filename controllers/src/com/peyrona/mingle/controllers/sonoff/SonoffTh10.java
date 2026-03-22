
package com.peyrona.mingle.controllers.sonoff;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Read-only controller for the Sonoff TH10/TH16 temperature and humidity monitor.
 * <p>
 * Retrieves sensor readings from a Sonoff device running Tasmota firmware via its
 * HTTP API ({@code GET /cm?cmnd=Status%208}). The device is expected to have a
 * DS18B20 temperature probe connected.
 * <p>
 * Configuration parameters:
 * <ul>
 *   <li>{@code ip}: IP address of the Sonoff device (required). E.g.: "192.168.1.50"</li>
 *   <li>{@code interval}: Polling interval in seconds (optional, default: 60, minimum: 1)</li>
 * </ul>
 * <p>
 * Read returns a current value in Celsius.
 * <p>
 * This controller is read-only; {@link #write(Object)} always reports not writable.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class SonoffTh10
       extends ControllerBase
{
    private static final String KEY_IP           = "ip";
    private static final String KEY_INTERVAL     = "interval";
    private static final int    DEFAULT_INTERVAL = 60 * 1000;  // 1 minute
    private static final int    MAX_BACKOFF      = 8;          // max multiplier for backoff

    private HttpClient      httpClient    = null;
    private ScheduledFuture timer         = null;
    private int             failureCount  = 0;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );

        try
        {
            set( KEY_IP, (String) deviceInit.get( KEY_IP ) );   // This will always exists: marked as 'REQUIRED' in DEVICE command

            if( ! UtilComm.isValidIP( (String) get( KEY_IP ) ) )
            {
                setValid( false );
                return;
            }

            long nInterval = deviceInit.containsKey( KEY_INTERVAL ) ? ((Number) deviceInit.get( KEY_INTERVAL )).longValue()
                                                                    : DEFAULT_INTERVAL;

            if( nInterval > Integer.MAX_VALUE )
                sendIsInvalid( "Interval is above max: "+ nInterval +" > "+ Integer.MAX_VALUE );

            setBetween( KEY_INTERVAL, 1, (int) nInterval, Integer.MAX_VALUE );
            setValid( true );
        }
        catch( Exception exc )
        {
            sendIsInvalid( "Error configuring SonoffTh10: " + UtilStr.toStringBrief( exc ) );
        }
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || ! super.start( rt ) )
            return false;

        if( ! isFaked() )
        {
            httpClient = HttpClient.newBuilder()
                                   .connectTimeout( Duration.ofSeconds( 5 ) )
                                   .build();
        }

        int interval = ((Number) get( KEY_INTERVAL )).intValue();

        timer = UtilSys.executor( false )
                       .delay( interval )
                       .rate( interval )
                       .execute( () -> read() );

        return true;
    }

    @Override
    public void stop()
    {
        if( timer != null )
        {
            timer.cancel( true );
            timer = null;
        }

        httpClient = null;
        super.stop();
    }

    @Override
    public void write( Object request )
    {
        sendIsNotWritable();
    }

    @Override
    public void read()
    {
        if( isInvalid() )
            return;

        if( isFaked() )
        {
            float fCelsius = ThreadLocalRandom.current().nextFloat() * 75 + 15;
            sendReaded( fCelsius );
        }
        else
        {
            if( httpClient == null )
                return;

            String ip = (String) get( KEY_IP );

            if( ! UtilComm.isReachable( ip ) )
            {
                onFailure( "SonoffTh10: device unreachable at " + ip );
                return;
            }

            try
            {
                HttpRequest request = HttpRequest.newBuilder()
                                                 .uri( URI.create( "http://" + ip + "/cm?cmnd=Status%208" ) )
                                                 .timeout( Duration.ofSeconds( 5 ) )
                                                 .GET()
                                                 .build();

                httpClient.sendAsync( request, HttpResponse.BodyHandlers.ofString() )
                          .thenApply( HttpResponse::body )
                          .thenAccept( body -> processResponse( body ) )
                          .exceptionally( ex ->
                                            {
                                                onFailure( "SonoffTh10 communication error: " + ex.getMessage() );
                                                return null;
                                            } );
            }
            catch( Exception exc )
            {
                sendReadError( exc );
            }
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Parses the Tasmota {@code Status 8} JSON response and sends the temperature reading.
     * <p>
     * Expected response structure:
     * <pre>{@code
     * {
     *   "StatusSNS": {
     *     "DS18B20": {
     *       "Temperature": 23.5,
     *       "TempUnit": "C"
     *     }
     *   }
     * }
     * }</pre>
     *
     * @param json The raw JSON response body.
     */
    private void processResponse( String json )
    {
        try
        {
            JsonValue jv = UtilJson.parse( json );

            if( jv == null || ! jv.isObject() )
            {
                sendGenericError( ILogger.Level.WARNING, "SonoffTh10: malformed JSON response" );
                return;
            }

            JsonObject root      = jv.asObject();
            JsonObject statusSNS = getNested( root, "StatusSNS" );

            if( statusSNS == null )
            {
                sendGenericError( ILogger.Level.WARNING, "SonoffTh10: missing 'StatusSNS' in response" );
                return;
            }

            JsonObject ds18b20 = getNested( statusSNS, "DS18B20" );

            if( ds18b20 == null )
            {
                sendGenericError( ILogger.Level.WARNING, "SonoffTh10: missing 'DS18B20' in StatusSNS" );
                return;
            }

            JsonValue jvTemp = ds18b20.get( "Temperature" );

            if( jvTemp == null || ! jvTemp.isNumber() )
            {
                sendGenericError( ILogger.Level.WARNING, "SonoffTh10: invalid or missing 'Temperature'" );
                return;
            }

            float fVal = jvTemp.asFloat();

            JsonValue jvUnit = ds18b20.get( "TempUnit" );

            String sUnit = (jvUnit != null && jvUnit.isString()) ? jvUnit.asString() : "";
            char   cUnit = sUnit.isEmpty() ? 'C' : sUnit.charAt( 0 );

            if( cUnit == 'f' || cUnit == 'F' )
                fVal = UtilUnit.fahrenheit2celsius( fVal );

            onSuccess();
            sendReaded( fVal );
        }
        catch( Exception exc )
        {
            sendReadError( exc );
        }
    }

    /**
     * Handles a successful read: resets the failure counter and restores the
     * normal polling interval if it was previously backed off.
     */
    private void onSuccess()
    {
        if( failureCount > 0 )
        {
            failureCount = 0;
            reschedule();
        }
    }

    /**
     * Handles a read failure: logs a warning, increments the failure counter,
     * and backs off the polling interval (doubling each time, up to {@value #MAX_BACKOFF}x).
     *
     * @param message The warning message to log.
     */
    private void onFailure( String message )
    {
        failureCount++;

        int multiplier = Math.min( 1 << failureCount, MAX_BACKOFF );   // 2, 4, 8, 8, 8...

        sendGenericError( ILogger.Level.WARNING, message + " (retry x" + multiplier + ")" );
        reschedule();
    }

    /**
     * Cancels the current polling timer and creates a new one with an interval
     * adjusted by the current backoff multiplier.
     */
    private void reschedule()
    {
        if( timer != null )
            timer.cancel( false );

        int base       = ((Number) get( KEY_INTERVAL )).intValue();
        int multiplier = (failureCount == 0) ? 1 : Math.min( 1 << failureCount, MAX_BACKOFF );
        int interval   = base * multiplier;

        timer = UtilSys.executor( false )
                       .delay( interval )
                       .rate( interval )
                       .execute( () -> read() );
    }

    /**
     * Extracts a nested JSON object from a parent object by key.
     *
     * @param parent The parent JSON object.
     * @param key    The key to look up.
     * @return The nested object, or {@code null} if absent or not an object.
     */
    private JsonObject getNested( JsonObject parent, String key )
    {
        JsonValue jv = parent.get( key );

        return (jv != null && jv.isObject()) ? jv.asObject() : null;
    }
}
