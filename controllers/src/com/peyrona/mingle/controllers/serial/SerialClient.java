
package com.peyrona.mingle.controllers.serial;

import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mingle controller for serial port (RS-232/RS-485) communication.
 * <p>
 * This controller provides access to serial ports using the nrjavaserial library,
 * enabling communication with devices connected via serial interfaces such as
 * RS-232, RS-485, USB-to-Serial adapters, and similar hardware.
 *
 * <h3>Configuration Parameters:</h3>
 * <table border="1">
 * <tr><th>Parameter</th><th>Required</th><th>Default</th><th>Description</th></tr>
 * <tr><td>port</td><td>Yes</td><td>-</td><td>Serial port name (e.g., "COM3", "/dev/ttyUSB0", "/dev/ttyACM0")</td></tr>
 * <tr><td>baudrate</td><td>No</td><td>9600</td><td>Baud rate: 300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600</td></tr>
 * <tr><td>databits</td><td>No</td><td>8</td><td>Data bits: 5, 6, 7, or 8</td></tr>
 * <tr><td>stopbits</td><td>No</td><td>1</td><td>Stop bits: 1, 1.5, or 2</td></tr>
 * <tr><td>parity</td><td>No</td><td>none</td><td>Parity: "none", "odd", "even", "mark", or "space"</td></tr>
 * <tr><td>flowcontrol</td><td>No</td><td>none</td><td>Flow control: "none", "rtscts", or "xonxoff"</td></tr>
 * <tr><td>encoding</td><td>No</td><td>UTF-8</td><td>Character encoding for text conversion</td></tr>
 * <tr><td>timeout</td><td>No</td><td>5000</td><td>Read timeout in milliseconds</td></tr>
 * <tr><td>terminator</td><td>No</td><td>\n</td><td>Line terminator for reading: "\n", "\r", "\r\n", or custom</td></tr>
 * <tr><td>interval</td><td>No</td><td>0</td><td>Read interval in milliseconds. 0=AUTO mode (continuous), &gt;=500=INTERVAL mode (periodic)</td></tr>
 * </table>
 *
 * <h3>Reading Modes:</h3>
 * <ul>
 *   <li><b>AUTO (default, interval=0 or not specified)</b>: A background thread continuously
 *       reads from the serial port and notifies the device when data arrives. This provides
 *       real-time event-driven communication.</li>
 *   <li><b>INTERVAL (interval&gt;=500)</b>: An internal timer reads from the serial port
 *       at the specified interval in milliseconds. Minimum interval is 500ms to prevent
 *       overloading devices.</li>
 * </ul>
 *
 * <h3>Usage Examples:</h3>
 * <pre>
 * # Basic configuration (default 9600 8N1, AUTO mode)
 * DEVICE SerialSensor
 *     DRIVER SerialDriver
 *         CONFIG
 *             port AS "/dev/ttyUSB0"
 *
 * # Full configuration (AUTO mode)
 * DEVICE ModbusRTU
 *     DRIVER SerialDriver
 *         CONFIG
 *             port        AS "COM3"
 *             baudrate    AS 115200
 *             databits    AS 8
 *             stopbits    AS 1
 *             parity      AS "none"
 *             flowcontrol AS "none"
 *             timeout     AS 3000
 *
 * # INTERVAL mode - read every 1000ms (internal timer)
 * DEVICE SerialSensor
 *     DRIVER SerialDriver
 *         CONFIG
 *             port     AS "/dev/ttyUSB0"
 *             baudrate AS 9600
 *             interval AS 1000
 *
 * # INTERVAL mode - read every 5 seconds
 * DEVICE SerialSensor
 *     DRIVER SerialDriver
 *         CONFIG
 *             port     AS "/dev/ttyUSB0"
 *             interval AS 5000
 * </pre>
 *
 * <h3>Reading Data:</h3>
 * Data received from the serial port is automatically delivered to the device
 * via the listener mechanism. Each complete line (terminated by the configured
 * terminator) triggers a change notification.
 *
 * <h3>Writing Data:</h3>
 * Data written to the device is sent directly to the serial port. String values
 * are encoded using the configured encoding (default UTF-8).
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="https://github.com/NeuronRobotics/nrjavaserial">nrjavaserial library</a>
 * @see <a href="https://github.com/peyrona/mingle">Mingle project</a>
 */
public final class SerialClient extends ControllerBase
{
    // Configuration keys
    private static final String KEY_PORT        = "port";
    private static final String KEY_BAUDRATE    = "baudrate";
    private static final String KEY_DATABITS    = "databits";
    private static final String KEY_STOPBITS    = "stopbits";
    private static final String KEY_PARITY      = "parity";
    private static final String KEY_FLOWCONTROL = "flowcontrol";
    private static final String KEY_ENCODING    = "encoding";
    private static final String KEY_TIMEOUT     = "timeout";
    private static final String KEY_TERMINATOR  = "terminator";
    private static final String KEY_INTERVAL    = "interval";
    private static final String KEY_RECONNECT   = "reconnect";
    private static final String KEY_MAX_RETRIES = "max_retries";

    // Default encoding (Mingle-specific, not serial-port specific)
    private static final String DEFAULT_ENCODING = "UTF-8";

    // Serial client instance (volatile for thread-safe access)
    private volatile ISerialClient client = null;

    // Interval timer for INTERVAL mode (reads at fixed intervals)
    private volatile ScheduledExecutorService timer = null;

    //------------------------------------------------------------------------//
    // IController IMPLEMENTATION
    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String,Object> deviceConf, IController.Listener listener )
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

        // Port availability check is made at start() where isFaked() is initialized

        // Validate baud rate (optional, with default)
        int nBaudrate;

        try
        {
            nBaudrate = UtilType.toInteger( get( KEY_BAUDRATE, ISerialClient.Config.DEFAULT_BAUDRATE ) );
        }
        catch( NumberFormatException ex )
        {
            sendIsInvalid( "Invalid baudrate format: " + get( KEY_BAUDRATE ) );
            return;
        }

        if( ! ISerialClient.Config.isValidBaudrate( nBaudrate ) )
        {
            sendIsInvalid( "Invalid baudrate: " + nBaudrate + ". Valid values: 300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600" );
            return;
        }

        // Validate data bits (optional, with default)
        int nDatabits;

        try
        {
            nDatabits = UtilType.toInteger( get( KEY_DATABITS, ISerialClient.Config.DEFAULT_DATABITS ) );
        }
        catch( NumberFormatException ex )
        {
            sendIsInvalid( "Invalid databits format: " + get( KEY_DATABITS ) );
            return;
        }

        if( ! ISerialClient.Config.isValidDatabits( nDatabits ) )
        {
            sendIsInvalid( "Invalid databits: " + nDatabits + ". Valid values: 5, 6, 7, 8" );
            return;
        }

        // Validate stop bits (optional, with default)
        double dStopbits;

        try
        {
            dStopbits = UtilType.toDouble( get( KEY_STOPBITS, ISerialClient.Config.DEFAULT_STOPBITS ) );
        }
        catch( NumberFormatException ex )
        {
            sendIsInvalid( "Invalid stopbits format: " + get( KEY_STOPBITS ) );
            return;
        }

        if( ! ISerialClient.Config.isValidStopbits( dStopbits ) )
        {
            sendIsInvalid( "Invalid stopbits: " + dStopbits + ". Valid values: 1, 1.5, 2" );
            return;
        }

        // Validate parity (optional, with default)
        String sParity = get( KEY_PARITY, "none" ).toString().toLowerCase();

        if( ! ISerialClient.Config.isValidParity( sParity ) )
        {
            sendIsInvalid( "Invalid parity: " + sParity + ". Valid values: " + ISerialClient.Config.VALID_PARITIES );
            return;
        }

        // Validate flow control (optional, with default)
        String sFlowControl = get( KEY_FLOWCONTROL, "none" ).toString().toLowerCase();

        if( ! ISerialClient.Config.isValidFlowcontrol( sFlowControl ) )
        {
            sendIsInvalid( "Invalid flowcontrol: " + sFlowControl + ". Valid values: " + ISerialClient.Config.VALID_FLOWCONTROLS );
            return;
        }

        // Validate encoding (optional, with default)
        String sEncoding = get( KEY_ENCODING, DEFAULT_ENCODING ).toString();

        try
        {
            Charset.forName( sEncoding );
        }
        catch( Exception ex )
        {
            sendIsInvalid( "Invalid encoding: " + sEncoding + ". Use standard charset names (e.g., UTF-8, ISO-8859-1)" );
            return;
        }

        // Validate timeout (optional, with default)
        int nTimeout;

        try
        {
            nTimeout = UtilType.toInteger( get( KEY_TIMEOUT, ISerialClient.Config.DEFAULT_TIMEOUT ) );
        }
        catch( NumberFormatException ex )
        {
            sendIsInvalid( "Invalid timeout format: " + get( KEY_TIMEOUT ) );
            return;
        }

        if( nTimeout < ISerialClient.Config.MIN_TIMEOUT )
        {
            sendGenericError( ILogger.Level.WARNING, "Timeout " + nTimeout + "ms is very low. Minimum is " + ISerialClient.Config.MIN_TIMEOUT + "ms." );
        }

        // Parse terminator (optional, with default)
        Object oTerminator = get( KEY_TERMINATOR );
        String sTerminator = (oTerminator != null)
                           ? ISerialClient.Config.expandTerminator( oTerminator.toString() )
                           : ISerialClient.Config.DEFAULT_TERMINATOR;

        // Validate interval (optional, with default)
        int nInterval;

        try
        {
            nInterval = UtilType.toInteger( get( KEY_INTERVAL, ISerialClient.Config.DEFAULT_INTERVAL ) );

            if( nInterval > 0 && nInterval < ISerialClient.Config.MIN_INTERVAL )
            {
                sendGenericError( ILogger.Level.WARNING, "Interval " + nInterval + "ms is below minimum (" +
                                                          ISerialClient.Config.MIN_INTERVAL + "ms). Using minimum." );

                nInterval = ISerialClient.Config.MIN_INTERVAL;
            }
        }
        catch( NumberFormatException ex )
        {
            sendGenericError( ILogger.Level.WARNING, "Invalid interval format: " + get( KEY_INTERVAL ) + ". Using AUTO mode (interval=0)." );
            nInterval = ISerialClient.Config.DEFAULT_INTERVAL;
        }

        // Validate reconnection settings (optional, with defaults)
        boolean bReconnect = Boolean.parseBoolean( get( KEY_RECONNECT, ISerialClient.Config.DEFAULT_RECONNECT ).toString() );

        int nMaxRetries;

        try
        {
            nMaxRetries = UtilType.toInteger( get( KEY_MAX_RETRIES, ISerialClient.Config.DEFAULT_MAX_RETRIES ) );
        }
        catch( NumberFormatException ex )
        {
            sendGenericError( ILogger.Level.WARNING, "Invalid max_retries: " + get( KEY_MAX_RETRIES ) + ". Using default." );
            nMaxRetries = ISerialClient.Config.DEFAULT_MAX_RETRIES;
        }

        // Store all validated values with correct types
        set( KEY_BAUDRATE,    nBaudrate );
        set( KEY_DATABITS,    nDatabits );
        set( KEY_STOPBITS,    dStopbits );
        set( KEY_PARITY,      sParity );
        set( KEY_FLOWCONTROL, sFlowControl );
        set( KEY_ENCODING,    sEncoding );
        set( KEY_TIMEOUT,     nTimeout );
        set( KEY_TERMINATOR,  sTerminator );
        set( KEY_INTERVAL,    nInterval );
        set( KEY_RECONNECT,   bReconnect );
        set( KEY_MAX_RETRIES, nMaxRetries );

        setValid( true );
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || (! super.start( rt )) )
            return false;

        if( ! isFaked() )    // Validate port exists (warning only - port might appear later)
        {
            String        sPort          = (String) get( KEY_PORT );
            Set<String>   availablePorts = ISerialClient.getAvailablePorts();

            if( ! availablePorts.contains( sPort ) )
                sendGenericError( ILogger.Level.WARNING, "Port '" + sPort + "' not currently available. Available ports: " + availablePorts );
        }

        int nInterval = (int) get( KEY_INTERVAL );

        // Build configuration using the Builder
        ISerialClient.Config config = new ISerialClient.Config.Builder( (String) get( KEY_PORT ) )
                                                              .baudrate( (int) get( KEY_BAUDRATE ) )
                                                              .databits( (int) get( KEY_DATABITS ) )
                                                              .stopbits( (double) get( KEY_STOPBITS ) )
                                                              .parity( (String) get( KEY_PARITY ) )
                                                              .flowcontrol( (String) get( KEY_FLOWCONTROL ) )
                                                              .terminator( (String) get( KEY_TERMINATOR ) )
                                                              .timeout( (int) get( KEY_TIMEOUT ) )
                                                              .interval( nInterval )
                                                              .encoding( (String) get( KEY_ENCODING ) )
                                                              .reconnect( (boolean) get( KEY_RECONNECT ) )
                                                              .maxRetries( (int) get( KEY_MAX_RETRIES ) )
                                                              .build();

        // Start internal timer for INTERVAL mode
        if( config.isIntervalMode() )
        {
            timer = Executors.newSingleThreadScheduledExecutor( r ->
                                {
                                    Thread t = new Thread( r, "SerialInterval-" + config.getPort() );
                                    t.setDaemon( true );
                                    return t;
                                } );

            timer.scheduleAtFixedRate( this::read, nInterval, nInterval, TimeUnit.MILLISECONDS );
        }

        if( isFaked() )
            return isValid();

        try
        {   // Create and open the serial client
            client = new SerialPort4NRJavaSerial( config, new SerialListener() );
            client.open();
        }
        catch( Exception ex )
        {
            sendGenericError( ILogger.Level.SEVERE, "Failed to open serial port: " + ex.getMessage() );
            // Failed to start - ensure resources are cleaned up
            stop();
            return false;
        }

        return isValid();
    }

    @Override
    public void stop()
    {
        // Stop interval timer first
        if( timer != null )
        {
            timer.shutdownNow();
            timer = null;
        }

        // Close serial client
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
            double value = Math.random() * 100;
            sendChanged( String.format( "%.2f", value ) );
            return;
        }

        if( client == null )
            return;

        int nInterval = (int) get( KEY_INTERVAL );

        if( nInterval <= 0 )
        {
            sendIsNotReadable();    // In AUTO mode, data arrives asynchronously via the listener
        }
        else
        {
            UtilSys.execute( null, () ->
                            {
                                try
                                {
                                    client.readOnce();
                                }
                                catch( IOException ex )
                                {
                                    sendReadError( ex );
                                }
                            } );
        }
    }

    @Override
    public void write( Object newValue )
    {
        if( isInvalid() )
            return;

        if( isFaked() )
        {
            sendChanged( newValue );   // Echo back the same value in faked mode
            return;
        }

        if( client == null )
            return;

        UtilSys.execute( null, () ->
                        {
                            try
                            {
                                String sValue    = (newValue == null) ? "" : newValue.toString();
                                String sEncoding = (String) get( KEY_ENCODING );
                                byte[] bytes     = sValue.getBytes( Charset.forName( sEncoding ) );

                                client.write( bytes );
                                sendChanged( newValue );
                            }
                            catch( IOException ex )
                            {
                                sendWriteError( newValue, ex );
                            }
                        } );
    }

    //------------------------------------------------------------------------//
    // UTILITY METHODS
    //------------------------------------------------------------------------//

    /**
     * Returns a set of available serial port names on this system.
     * <p>
     * This static method can be used to discover available ports before
     * configuring the controller.
     *
     * @return Set of available port names (e.g., "/dev/ttyUSB0", "COM3").
     */
    public static Set<String> getAvailablePorts()
    {
        return ISerialClient.getAvailablePorts();
    }

    //------------------------------------------------------------------------//
    // INNER CLASS - Serial Listener
    //------------------------------------------------------------------------//

    /**
     * Listener that bridges serial client events to the controller's listener.
     */
    private final class SerialListener implements ISerialClient.Listener
    {
        @Override
        public void onMessage( String message )
        {
            sendChanged( message );
        }

        @Override
        public void onError( Exception exc )
        {
            String message = exc.getMessage();

            if( message == null || message.isEmpty() )
                message = exc.getClass().getSimpleName();

            sendGenericError( ILogger.Level.SEVERE, "Serial port error: " + message );
        }

        @Override
        public void onConnected()
        {
            // Connection established - nothing special to do
        }

        @Override
        public void onDisconnected()
        {
            sendGenericError( ILogger.Level.INFO, "Serial port disconnected" );
        }
    }
}