
package com.peyrona.mingle.controllers.modbus;

import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import java.util.Map;

/**
 * Mingle controller wrapper for Modbus TCP communication.
 * <p>
 * This controller provides access to Modbus TCP devices with extensive configuration options
 * for compatibility with different Modbus server implementations.
 *
 * <h3>Configuration Parameters:</h3>
 * <table border="1">
 * <tr><th>Parameter</th><th>Required</th><th>Default</th><th>Description</th></tr>
 * <tr><td>uri</td><td>Yes</td><td>-</td><td>Server address as host:port (e.g., "192.168.1.100:502")</td></tr>
 * <tr><td>address</td><td>Yes</td><td>-</td><td>Register/coil address (0-based)</td></tr>
 * <tr><td>type</td><td>Yes</td><td>-</td><td>Data type: "boolean", "int", "long", "float"</td></tr>
 * <tr><td>unitid</td><td>No</td><td>1</td><td>Unit ID / Slave ID (0-255)</td></tr>
 * <tr><td>function</td><td>No</td><td>auto</td><td>Function code: "coils", "discrete", "holding", "input"</td></tr>
 * <tr><td>order</td><td>No</td><td>ABCD</td><td>Byte order for 32-bit values: ABCD, BADC, CDAB, DCBA</td></tr>
 * <tr><td>interval</td><td>No</td><td>5000</td><td>Polling interval in milliseconds</td></tr>
 * <tr><td>timeout</td><td>No</td><td>4000</td><td>Connection/response timeout in milliseconds</td></tr>
 * <tr><td>retries</td><td>No</td><td>2</td><td>Number of retries on failure (0-10)</td></tr>
 * </table>
 *
 * <h3>Function Code Selection:</h3>
 * <ul>
 *   <li><b>coils</b> - FC01 Read Coils / FC05 Write Single Coil (read/write booleans)</li>
 *   <li><b>discrete</b> - FC02 Read Discrete Inputs (read-only booleans)</li>
 *   <li><b>holding</b> - FC03 Read Holding Registers / FC06,FC16 Write Registers (read/write registers)</li>
 *   <li><b>input</b> - FC04 Read Input Registers (read-only registers)</li>
 * </ul>
 * <p>
 * If not specified, the function code is automatically selected based on data type:
 * "boolean" defaults to "coils", numeric types default to "holding".
 *
 * <h3>Byte Order:</h3>
 * Different Modbus devices use different byte orderings for 32-bit values (long, float).
 * Common patterns:
 * <ul>
 *   <li><b>ABCD</b> - Big-endian (most common)</li>
 *   <li><b>CDAB</b> - Word-swapped big-endian</li>
 *   <li><b>BADC</b> - Byte-swapped within words</li>
 *   <li><b>DCBA</b> - Little-endian</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="https://github.com/steveohara/j2mod/">J2mod library</a>
 * @see <a href="https://github.com/peyrona/mingle">Mingle project</a>
 */
public final class ModbusTcpClientWrapper extends ControllerBase
{
    // Configuration keys
    private static final String KEY_URI      = "uri";
    private static final String KEY_ADDRESS  = "address";
    private static final String KEY_TYPE     = "type";
    private static final String KEY_UNITID   = "unitid";
    private static final String KEY_FUNCTION = "function";
    private static final String KEY_ORDER    = "order";
    private static final String KEY_INTERVAL = "interval";
    private static final String KEY_TIMEOUT  = "timeout";
    private static final String KEY_RETRIES  = "retries";

    // Default values
    private static final int    DEFAULT_PORT     = 502;
    private static final int    DEFAULT_UNITID   = 1;
    private static final String DEFAULT_ORDER    = "ABCD";
    private static final int    DEFAULT_INTERVAL = 5000;
    private static final int    DEFAULT_TIMEOUT  = 4000;
    private static final int    DEFAULT_RETRIES  = 2;

    // Valid values
    private static final String VALID_TYPES     = "boolean,float,long,int";
    private static final String VALID_FUNCTIONS = "coils,discrete,holding,input";
    private static final String VALID_ORDERS    = "ABCD,BADC,CDAB,DCBA";

    private IModbusClient client = null;

    //------------------------------------------------------------------------//
    // IController IMPLEMENTATION
    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );     // Must be at beginning: in case an error happens, Listener is needed

        // Parse and validate URI (required)
        String sURI = (String) deviceInit.get( KEY_URI );
        if( UtilStr.isEmpty( sURI ) )
        {
            sendIsInvalid( "URI is required (e.g., '192.168.1.100:502')" );
            return;
        }

        String sHost = UtilComm.getHost( sURI );
        int    nPort = UtilComm.getPort( sURI, DEFAULT_PORT );

        if( UtilStr.isEmpty( sHost ) )
        {
            sendIsInvalid( "Invalid URI: host is empty" );
            return;
        }

        if( nPort < UtilComm.TCP_PORT_MIN_ALLOWED || nPort > UtilComm.TCP_PORT_MAX_ALLOWED )
        {
            sendIsInvalid( "Invalid port: " + nPort + ". Must be between "
                + UtilComm.TCP_PORT_MIN_ALLOWED + " and " + UtilComm.TCP_PORT_MAX_ALLOWED );
            return;
        }

        // Parse and validate address (required)
        Object oAddr = deviceInit.get( KEY_ADDRESS );
        if( oAddr == null )
        {
            sendIsInvalid( "Address is required" );
            return;
        }

        int nAddress = ((Number) oAddr).intValue();
        if( nAddress < 0 || nAddress > 65535 )
        {
            sendIsInvalid( "Invalid address: " + nAddress + ". Must be 0-65535" );
            return;
        }

        // Parse and validate data type (required)
        Object oType = deviceInit.get( KEY_TYPE );
        if( oType == null )
        {
            sendIsInvalid( "Type is required. Must be: " + VALID_TYPES );
            return;
        }

        String sType = oType.toString().toLowerCase();
        if( !VALID_TYPES.contains( sType ) )
        {
            sendIsInvalid( "Invalid type: " + sType + ". Must be: " + VALID_TYPES );
            return;
        }

        // Parse and validate Unit ID (optional, default 1)
        int nUnitId = DEFAULT_UNITID;
        Object oUnitId = deviceInit.get( KEY_UNITID );
        if( oUnitId != null )
        {
            nUnitId = ((Number) oUnitId).intValue();
            if( nUnitId < 0 || nUnitId > 255 )
            {
                sendIsInvalid( "Invalid unitid: " + nUnitId + ". Must be 0-255" );
                return;
            }
        }

        // Parse and validate function code (optional, auto-detect based on type)
        String sFunction = getDefaultFunction( sType );
        Object oFunction = deviceInit.get( KEY_FUNCTION );
        if( oFunction != null )
        {
            sFunction = oFunction.toString().toLowerCase();
            if( !VALID_FUNCTIONS.contains( sFunction ) )
            {
                sendIsInvalid( "Invalid function: " + sFunction + ". Must be: " + VALID_FUNCTIONS );
                return;
            }

            // Validate function/type compatibility
            if( !isValidFunctionForType( sFunction, sType ) )
            {
                sendIsInvalid( "Function '" + sFunction + "' is not compatible with type '" + sType + "'" );
                return;
            }
        }

        // Parse and validate byte order (optional, default ABCD)
        String sOrder = DEFAULT_ORDER;
        Object oOrder = deviceInit.get( KEY_ORDER );
        if( oOrder != null )
        {
            sOrder = oOrder.toString().toUpperCase();
            if( !VALID_ORDERS.contains( sOrder ) )
            {
                sendIsInvalid( "Invalid order: " + sOrder + ". Must be: " + VALID_ORDERS );
                return;
            }
        }

        // Parse interval (optional, default 5000ms)
        int nInterval = DEFAULT_INTERVAL;
        Object oInterval = deviceInit.get( KEY_INTERVAL );
        if( oInterval != null )
        {
            nInterval = ((Number) oInterval).intValue();
            if( nInterval < 30 )
            {
                sendGenericError( ILogger.Level.WARNING,
                    "Interval " + nInterval + "ms is very low. Minimum recommended is 30ms. Using 30ms." );
                nInterval = 30;
            }
        }

        // Parse timeout (optional, default 4000ms)
        int nTimeout = DEFAULT_TIMEOUT;
        Object oTimeout = deviceInit.get( KEY_TIMEOUT );
        if( oTimeout != null )
        {
            nTimeout = ((Number) oTimeout).intValue();
            if( nTimeout < 100 )
            {
                sendGenericError( ILogger.Level.WARNING,
                    "Timeout " + nTimeout + "ms is very low. Minimum recommended is 100ms. Using 100ms." );
                nTimeout = 100;
            }
        }

        // Parse retries (optional, default 2)
        int nRetries = DEFAULT_RETRIES;
        Object oRetries = deviceInit.get( KEY_RETRIES );
        if( oRetries != null )
        {
            nRetries = ((Number) oRetries).intValue();
            if( nRetries < 0 || nRetries > 10 )
            {
                sendGenericError( ILogger.Level.WARNING,
                    "Retries " + nRetries + " out of range 0-10. Using default: " + DEFAULT_RETRIES );
                nRetries = DEFAULT_RETRIES;
            }
        }

        // All validation passed - create the client
        setValid( true );

        client = new ModbusTcpClient4J2mod(
            sHost, nPort, nUnitId, nAddress,
            sType, sFunction, sOrder,
            nInterval, nTimeout, nRetries,
            new ModbusListener() );

        set( deviceInit );
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );

        if( client != null )
        {
            client.open();
        }
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
        if( isFaked() || isInvalid() || client == null )
            return;

        try
        {
            sendReaded( client.read() );
        }
        catch( Exception ex )
        {
            sendReadError( ex );
        }
    }

    @Override
    public void write( Object newValue )
    {
        if( isFaked() || isInvalid() || client == null )
            return;

        // Convert string values to appropriate types
        if( newValue instanceof String )
        {
            newValue = UtilType.toUne( newValue.toString() );
        }

        try
        {
            sendChanged( client.write( newValue ) );
        }
        catch( UnsupportedOperationException ex )
        {
            sendGenericError( ILogger.Level.SEVERE, "Modbus write not supported: " + ex.getMessage() );
        }
        catch( Exception ex )
        {
            sendGenericError( ILogger.Level.SEVERE, "Modbus write error: " + ex.getMessage() );
        }
    }

    //------------------------------------------------------------------------//
    // HELPER METHODS
    //------------------------------------------------------------------------//

    /**
     * Returns the default function code for a given data type.
     */
    private String getDefaultFunction( String type )
    {
        if( "boolean".equals( type ) )
        {
            return ModbusTcpClient4J2mod.FC_COILS;  // FC01 - Read/Write
        }
        else
        {
            return ModbusTcpClient4J2mod.FC_HOLDING_REGISTERS;  // FC03 - Read/Write
        }
    }

    /**
     * Validates that the function code is compatible with the data type.
     */
    private boolean isValidFunctionForType( String function, String type )
    {
        if( "boolean".equals( type ) )
        {
            // Boolean can only use coils (FC01) or discrete inputs (FC02)
            return ModbusTcpClient4J2mod.FC_COILS.equals( function )
                || ModbusTcpClient4J2mod.FC_DISCRETE_INPUTS.equals( function );
        }
        else
        {
            // Numeric types can only use holding (FC03) or input registers (FC04)
            return ModbusTcpClient4J2mod.FC_HOLDING_REGISTERS.equals( function )
                || ModbusTcpClient4J2mod.FC_INPUT_REGISTERS.equals( function );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS - Modbus Listener
    //------------------------------------------------------------------------//

    /**
     * Listener that bridges Modbus client events to the controller's listener.
     */
    private final class ModbusListener implements IModbusClient.Listener
    {
        @Override
        public void onMessage( Object msg )
        {
            sendChanged( msg );
        }

        @Override
        public void onError( Exception exc )
        {
            String message = exc.getMessage();
            if( message == null || message.isEmpty() )
            {
                message = exc.getClass().getSimpleName();
            }
            sendGenericError( ILogger.Level.SEVERE, "Modbus error: " + message );
        }
    }
}
