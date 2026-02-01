
package com.peyrona.mingle.controllers.zigbee;

import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import java.util.List;
import java.util.Map;

/**
 * Mingle controller for Zigbee network communication.
 * <p>
 * This controller provides access to Zigbee networks using the zsmartsystems.zigbee library,
 * enabling communication with Zigbee devices such as lights, sensors, switches, and other
 * Zigbee Home Automation (ZHA) compatible devices.
 *
 * <h3>Configuration Parameters:</h3>
 * <table border="1">
 * <tr><th>Parameter</th><th>Required</th><th>Default</th><th>Description</th></tr>
 * <tr><td>port</td><td>Yes</td><td>-</td><td>Serial port for Zigbee dongle (e.g., "/dev/ttyUSB0", "COM3")</td></tr>
 * <tr><td>dongle</td><td>Yes</td><td>-</td><td>Dongle type: "ember", "cc2531", "conbee", "xbee", "telegesis"</td></tr>
 * <tr><td>channel</td><td>No</td><td>11</td><td>Zigbee channel (11-26)</td></tr>
 * <tr><td>pan_id</td><td>No</td><td>auto</td><td>PAN ID as hex (0x0000-0xFFFF) or "auto"</td></tr>
 * <tr><td>network_key</td><td>No</td><td>auto</td><td>128-bit network key as 32 hex chars or "auto"</td></tr>
 * <tr><td>permit_join</td><td>No</td><td>false</td><td>Allow new devices to join on startup</td></tr>
 * <tr><td>interval</td><td>No</td><td>0</td><td>Polling interval in ms (0 = event-driven, &gt;= 500 = polling)</td></tr>
 * </table>
 *
 * <h3>Supported Dongles:</h3>
 * <ul>
 *   <li><b>ember</b>: Silicon Labs Ember EM35x-based coordinators (most common)</li>
 *   <li><b>cc2531</b>: Texas Instruments CC2531 USB dongle</li>
 *   <li><b>conbee</b>: Dresden Elektronik ConBee/ConBee II/RaspBee</li>
 *   <li><b>xbee</b>: Digi XBee modules</li>
 *   <li><b>telegesis</b>: Telegesis ETRX series</li>
 * </ul>
 *
 * <h3>Reading Modes:</h3>
 * <ul>
 *   <li><b>Event-driven (default, interval=0)</b>: Devices report changes automatically
 *       via Zigbee attribute reporting. Provides real-time updates.</li>
 *   <li><b>Polling (interval &gt;= 500)</b>: Devices are polled at the specified interval.
 *       Use when devices don't support attribute reporting.</li>
 * </ul>
 *
 * <h3>Usage Examples:</h3>
 * <pre>
 * # Basic Zigbee network setup
 * DEVICE ZigbeeNetwork
 *     DRIVER ZigbeeDriver
 *         CONFIG
 *             port   AS "/dev/ttyUSB0"
 *             dongle AS "ember"
 *
 * # Reading temperature sensor
 * DEVICE TempSensor
 *     DRIVER ZigbeeDriver
 *         CONFIG
 *             port   AS "/dev/ttyUSB0"
 *             dongle AS "conbee"
 *             channel AS 15
 *
 * # Controlling a light
 * DEVICE Light1
 *     DRIVER ZigbeeDriver
 *         CONFIG
 *             port   AS "/dev/ttyUSB0"
 *             dongle AS "ember"
 *
 * # Writing commands (in rules):
 * # Turn on: Light1 = pair("ieee", "00:11:22:33:44:55:66:77", "endpoint", 1, "cluster", 6, "value", true)
 * # Set level: Light1 = pair("ieee", "00:11:22:33:44:55:66:77", "endpoint", 1, "cluster", 8, "value", 128)
 * </pre>
 *
 * <h3>Writing Commands:</h3>
 * Commands are sent using a pair object with the following keys:
 * <ul>
 *   <li><b>ieee</b>: Device's IEEE address (64-bit, e.g., "00:11:22:33:44:55:66:77")</li>
 *   <li><b>endpoint</b>: Endpoint number (1-240)</li>
 *   <li><b>cluster</b>: Cluster ID (e.g., 6 for On/Off, 8 for Level Control)</li>
 *   <li><b>value</b>: Value to write (type depends on cluster)</li>
 * </ul>
 *
 * <h3>Special Commands:</h3>
 * <ul>
 *   <li><b>permit_join</b>: pair("command", "permit_join", "duration", 60) - Allow joining for 60 seconds</li>
 *   <li><b>get_devices</b>: pair("command", "get_devices") - Returns list of all devices</li>
 * </ul>
 *
 * @author Francisco Jose Morero Peyrona
 * @see <a href="https://github.com/zsmartsystems/com.zsmartsystems.zigbee">ZSmartSystems Zigbee library</a>
 * @see <a href="https://github.com/peyrona/mingle">Mingle project</a>
 */
public final class ZigbeeClient extends ControllerBase
{
    // Configuration keys
    private static final String KEY_PORT        = "port";
    private static final String KEY_DONGLE      = "dongle";
    private static final String KEY_CHANNEL     = "channel";
    private static final String KEY_PAN_ID      = "pan_id";
    private static final String KEY_NETWORK_KEY = "network_key";
    private static final String KEY_PERMIT_JOIN = "permit_join";
    private static final String KEY_INTERVAL    = "interval";
    private static final String KEY_TIMEOUT     = "timeout";

    // Command keys for pair objects
    private static final String CMD_COMMAND     = "command";
    private static final String CMD_IEEE        = "ieee";
    private static final String CMD_ENDPOINT    = "endpoint";
    private static final String CMD_CLUSTER     = "cluster";
    private static final String CMD_VALUE       = "value";
    private static final String CMD_DURATION    = "duration";

    // Command types
    private static final String CMD_PERMIT_JOIN = "permit_join";
    private static final String CMD_GET_DEVICES = "get_devices";

    // Zigbee client instance (volatile for thread-safe access)
    private volatile IZigbeeClient client = null;

    //------------------------------------------------------------------------//
    // IController IMPLEMENTATION
    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceConf, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );     // Must be at beginning: in case an error happens, Listener is needed
        setDeviceConfig( deviceConf );

        // Validate port (required)
        String sPort = (String) get( KEY_PORT );

        if( UtilStr.isEmpty( sPort ) )
        {
            sendIsInvalid( "Port is required (e.g., '/dev/ttyUSB0' or 'COM3')" );
            return;
        }

        // Validate dongle (required)
        String sDongle = (String) get( KEY_DONGLE );

        if( UtilStr.isEmpty( sDongle ) )
        {
            sendIsInvalid( "Dongle type is required. Valid types: " + IZigbeeClient.Config.VALID_DONGLES );
            return;
        }

        sDongle = sDongle.toLowerCase();

        if( ! IZigbeeClient.Config.isValidDongle( sDongle ) )
        {
            sendIsInvalid( "Invalid dongle: " + sDongle + ". Valid types: " + IZigbeeClient.Config.VALID_DONGLES );
            return;
        }

        // Validate channel (optional, with default)
        int nChannel;

        try
        {
            nChannel = UtilType.toInteger( get( KEY_CHANNEL, IZigbeeClient.Config.DEFAULT_CHANNEL ) );
        }
        catch( NumberFormatException ex )
        {
            sendIsInvalid( "Invalid channel format: " + get( KEY_CHANNEL ) );
            return;
        }

        if( ! IZigbeeClient.Config.isValidChannel( nChannel ) )
        {
            sendIsInvalid( "Invalid channel: " + nChannel + ". Valid range: " +
                           IZigbeeClient.Config.CHANNEL_MIN + "-" + IZigbeeClient.Config.CHANNEL_MAX );
            return;
        }

        // Validate PAN ID (optional, with default)
        String sPanId = get( KEY_PAN_ID, IZigbeeClient.Config.DEFAULT_PAN_ID ).toString();

        if( ! IZigbeeClient.Config.isValidPanId( sPanId ) )
        {
            sendIsInvalid( "Invalid pan_id: " + sPanId + ". Use 'auto' or 1-4 hex digits (e.g., '1234')" );
            return;
        }

        // Validate network key (optional, with default)
        String sNetworkKey = get( KEY_NETWORK_KEY, IZigbeeClient.Config.DEFAULT_NETWORK_KEY ).toString();

        if( ! IZigbeeClient.Config.isValidNetworkKey( sNetworkKey ) )
        {
            sendIsInvalid( "Invalid network_key: " + sNetworkKey + ". Use 'auto' or 32 hex digits" );
            return;
        }

        // Validate permit_join (optional, with default)
        boolean bPermitJoin = false;
        Object oPermitJoin = get( KEY_PERMIT_JOIN );

        if( oPermitJoin != null )
        {
            if( oPermitJoin instanceof Boolean )
                bPermitJoin = (Boolean) oPermitJoin;
            else
                bPermitJoin = Boolean.parseBoolean( oPermitJoin.toString() );
        }

        // Validate interval (optional, with default)
        int nInterval;

        try
        {
            nInterval = UtilType.toInteger( get( KEY_INTERVAL, IZigbeeClient.Config.DEFAULT_INTERVAL ) );

            if( nInterval > 0 && nInterval < IZigbeeClient.Config.MIN_INTERVAL )
            {
                sendGenericError( ILogger.Level.WARNING, "Interval " + nInterval + "ms is below minimum (" +
                                  IZigbeeClient.Config.MIN_INTERVAL + "ms). Using minimum." );
                nInterval = IZigbeeClient.Config.MIN_INTERVAL;
            }
        }
        catch( NumberFormatException ex )
        {
            sendGenericError( ILogger.Level.WARNING, "Invalid interval format: " + get( KEY_INTERVAL ) +
                              ". Using event-driven mode (interval=0)." );
            nInterval = IZigbeeClient.Config.DEFAULT_INTERVAL;
        }

        // Validate timeout (optional, with default)
        int nTimeout;

        try
        {
            nTimeout = UtilType.toInteger( get( KEY_TIMEOUT, IZigbeeClient.Config.DEFAULT_TIMEOUT ) );

            if( nTimeout < IZigbeeClient.Config.MIN_TIMEOUT )
            {
                sendGenericError( ILogger.Level.WARNING, "Timeout " + nTimeout + "ms is below minimum (" +
                                  IZigbeeClient.Config.MIN_TIMEOUT + "ms). Using minimum." );
                nTimeout = IZigbeeClient.Config.MIN_TIMEOUT;
            }
        }
        catch( NumberFormatException ex )
        {
            sendGenericError( ILogger.Level.WARNING, "Invalid timeout format: " + get( KEY_TIMEOUT ) +
                              ". Using default." );
            nTimeout = IZigbeeClient.Config.DEFAULT_TIMEOUT;
        }

        // Store all validated values with correct types
        set( KEY_PORT,        sPort );
        set( KEY_DONGLE,      sDongle );
        set( KEY_CHANNEL,     nChannel );
        set( KEY_PAN_ID,      sPanId );
        set( KEY_NETWORK_KEY, sNetworkKey );
        set( KEY_PERMIT_JOIN, bPermitJoin );
        set( KEY_INTERVAL,    nInterval );
        set( KEY_TIMEOUT,     nTimeout );

        setValid( true );
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || (! super.start( rt )) )
            return false;

        if( isFaked() )
            return isValid();

        // Build configuration using the Builder
        IZigbeeClient.Config config = new IZigbeeClient.Config.Builder(
                (String) get( KEY_PORT ),
                (String) get( KEY_DONGLE ) )
            .channel( (int) get( KEY_CHANNEL ) )
            .panId( (String) get( KEY_PAN_ID ) )
            .networkKey( (String) get( KEY_NETWORK_KEY ) )
            .permitJoin( (boolean) get( KEY_PERMIT_JOIN ) )
            .interval( (int) get( KEY_INTERVAL ) )
            .timeout( (int) get( KEY_TIMEOUT ) )
            .build();

        try
        {
            // Create and open the Zigbee client
            client = new ZigbeeClient4ZSmartSystems( config, new ZigbeeListener() );
            client.open();
        }
        catch( Exception ex )
        {
            sendGenericError( ILogger.Level.SEVERE, "Failed to open Zigbee network: " + ex.getMessage() );
        }

        return isValid();
    }

    @Override
    public void stop()
    {
        if( client != null )
        {
            client.close();
            client = null;
        }

        super.stop();
    }

    @Override
    public void read()
    {
        if( isInvalid() )
            return;

        if( isFaked() )   // Generate random but congruent data for faked mode
        {
            // Return a sample device list
            sendChanged( "[{\"ieee\":\"00:11:22:33:44:55:66:77\",\"type\":\"end_device\",\"endpoints\":[1]}]" );
            return;
        }

        if( client == null )
            return;

        int nInterval = (int) get( KEY_INTERVAL );

        if( nInterval <= 0 )
        {
            // In event-driven mode, return list of devices
            List<Map<String, Object>> devices = client.getDevices();
            sendChanged( devices );
        }
        else
        {
            sendIsNotReadable();    // In polling mode, data arrives via the listener
        }
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public void write( Object newValue )
    {
        if( isInvalid() )
            return;

        if( isFaked() )
        {
            sendChanged( newValue );   // Echo back in faked mode
            return;
        }

        if( client == null )
            return;

        // Parse the command from the pair object
        if( ! (newValue instanceof Map) )
        {
            sendGenericError( ILogger.Level.SEVERE,
                              "Invalid write value. Expected pair with: ieee, endpoint, cluster, value" );
            return;
        }

        Map<String, Object> cmd = (Map<String, Object>) newValue;

        // Check for special commands
        String command = (String) cmd.get( CMD_COMMAND );

        if( command != null )
        {
            executeSpecialCommand( command, cmd );
            return;
        }

        // Regular device write command
        String ieee = (String) cmd.get( CMD_IEEE );

        if( UtilStr.isEmpty( ieee ) )
        {
            sendGenericError( ILogger.Level.SEVERE, "Missing 'ieee' address in write command" );
            return;
        }

        Object oEndpoint = cmd.get( CMD_ENDPOINT );

        if( oEndpoint == null )
        {
            sendGenericError( ILogger.Level.SEVERE, "Missing 'endpoint' in write command" );
            return;
        }

        int endpoint = ((Number) oEndpoint).intValue();

        Object oCluster = cmd.get( CMD_CLUSTER );

        if( oCluster == null )
        {
            sendGenericError( ILogger.Level.SEVERE, "Missing 'cluster' in write command" );
            return;
        }

        int cluster = ((Number) oCluster).intValue();
        Object value = cmd.get( CMD_VALUE );

        try
        {
            client.write( ieee, endpoint, cluster, value );
            sendChanged( newValue );
        }
        catch( Exception ex )
        {
            sendWriteError( newValue, ex );
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE HELPER METHODS
    //------------------------------------------------------------------------//

    /**
     * Executes a special command (permit_join, get_devices, etc.).
     */
    private void executeSpecialCommand( String command, Map<String, Object> params )
    {
        try
        {
            switch( command.toLowerCase() )
            {
                case CMD_PERMIT_JOIN:
                    int duration = 60;  // Default 60 seconds
                    Object oDuration = params.get( CMD_DURATION );

                    if( oDuration instanceof Number )
                        duration = ((Number) oDuration).intValue();

                    client.permitJoin( duration );
                    sendChanged( "permit_join enabled for " + duration + " seconds" );
                    break;

                case CMD_GET_DEVICES:
                    List<Map<String, Object>> devices = client.getDevices();
                    sendChanged( devices );
                    break;

                default:
                    sendGenericError( ILogger.Level.WARNING, "Unknown command: " + command );
                    break;
            }
        }
        catch( Exception ex )
        {
            sendGenericError( ILogger.Level.SEVERE, "Command '" + command + "' failed: " + ex.getMessage() );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS - Zigbee Listener
    //------------------------------------------------------------------------//

    /**
     * Listener that bridges Zigbee client events to the controller's listener.
     */
    private final class ZigbeeListener implements IZigbeeClient.Listener
    {
        @Override
        public void onMessage( String ieeeAddress, int endpoint, int clusterId, Object value )
        {
            // Format the message as a structured object
            Map<String, Object> msg = new java.util.HashMap<>();
            msg.put( "ieee",     ieeeAddress );
            msg.put( "endpoint", endpoint );
            msg.put( "cluster",  clusterId );
            msg.put( "value",    value );

            sendChanged( msg );
        }

        @Override
        public void onError( Exception exc )
        {
            String message = exc.getMessage();

            if( message == null || message.isEmpty() )
                message = exc.getClass().getSimpleName();

            sendGenericError( ILogger.Level.SEVERE, "Zigbee error: " + message );
        }

        @Override
        public void onDeviceJoined( String ieeeAddress )
        {
            Map<String, Object> event = new java.util.HashMap<>();
            event.put( "event", "device_joined" );
            event.put( "ieee",  ieeeAddress );

            sendChanged( event );
        }

        @Override
        public void onDeviceLeft( String ieeeAddress )
        {
            Map<String, Object> event = new java.util.HashMap<>();
            event.put( "event", "device_left" );
            event.put( "ieee",  ieeeAddress );

            sendChanged( event );
        }

        @Override
        public void onNetworkStarted()
        {
            Map<String, Object> event = new java.util.HashMap<>();
            event.put( "event", "network_started" );

            sendChanged( event );
        }

        @Override
        public void onNetworkStopped()
        {
            Map<String, Object> event = new java.util.HashMap<>();
            event.put( "event", "network_stopped" );

            sendChanged( event );
        }
    }
}
