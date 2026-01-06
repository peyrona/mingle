
package com.peyrona.mingle.controllers.modbus;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import java.util.HashMap;
import java.util.Map;

/**
 * Test class for the Modbus TCP controller.
 * <p>
 * This class provides examples and tests for various Modbus configurations.
 * Update the connection parameters to match your Modbus server before running.
 *
 * <h3>Test Modbus Servers:</h3>
 * <ul>
 *   <li>diagslave - Free Modbus simulator: https://www.modbusdriver.com/diagslave.html</li>
 *   <li>ModRSsim2 - Windows Modbus simulator</li>
 *   <li>pyModbusTCP - Python-based simulator</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 */
public class Test
{
    // ========================================================================
    // CONFIGURATION - Update these values to match your Modbus server
    // ========================================================================

    private static final String HOST      = "127.0.0.1";   // Modbus server IP
    private static final int    PORT      = 502;           // Standard Modbus TCP port
    private static final int    UNIT_ID   = 1;             // Slave/Unit ID
    private static final int    TIMEOUT   = 3000;          // Timeout in ms
    private static final int    INTERVAL  = 2000;          // Polling interval in ms

    // ========================================================================
    // MAIN - Run individual tests
    // ========================================================================

    public static void main( String[] args ) throws Exception
    {
        System.out.println( "=".repeat( 70 ) );
        System.out.println( "Modbus TCP Controller Test Suite" );
        System.out.println( "=".repeat( 70 ) );
        System.out.println( "Server: " + HOST + ":" + PORT + " (Unit ID: " + UNIT_ID + ")" );
        System.out.println( "=".repeat( 70 ) );
        System.out.println();

        // Uncomment the test you want to run:

        // testReadHoldingRegisterInt();
        // testReadInputRegisterInt();
        // testReadHoldingRegisterFloat();
        // testReadCoil();
        // testReadDiscreteInput();
        // testWriteHoldingRegister();
        // testWriteCoil();
        // testByteOrders();
        // testWrapperBasic();
        // testWrapperWithAllOptions();
        testWrapperPolling();
    }

    // ========================================================================
    // LOW-LEVEL CLIENT TESTS (ModbusTcpClient4J2mod)
    // ========================================================================

    /**
     * Test reading a 16-bit integer from holding registers (FC03).
     */
    public static void testReadHoldingRegisterInt() throws Exception
    {
        System.out.println( "Test: Read Holding Register (FC03) - Int" );
        System.out.println( "-".repeat( 50 ) );

        ModbusTcpClient4J2mod client = new ModbusTcpClient4J2mod(
            HOST, PORT, UNIT_ID,
            0,                                          // Address
            "int",                                      // Data type
            ModbusTcpClient4J2mod.FC_HOLDING_REGISTERS, // Function code
            "ABCD",                                     // Byte order (N/A for 16-bit)
            INTERVAL, TIMEOUT, 2,                       // Interval, timeout, retries
            null                                        // No listener for sync read
        );

        try
        {
            Object value = client.read();
            System.out.println( "Value at address 0: " + value + " (type: " + value.getClass().getSimpleName() + ")" );
        }
        finally
        {
            client.close();
        }
    }

    /**
     * Test reading a 16-bit integer from input registers (FC04).
     */
    public static void testReadInputRegisterInt() throws Exception
    {
        System.out.println( "Test: Read Input Register (FC04) - Int" );
        System.out.println( "-".repeat( 50 ) );

        ModbusTcpClient4J2mod client = new ModbusTcpClient4J2mod(
            HOST, PORT, UNIT_ID,
            0,                                        // Address
            "int",                                    // Data type
            ModbusTcpClient4J2mod.FC_INPUT_REGISTERS, // Function code (read-only)
            "ABCD",                                   // Byte order
            INTERVAL, TIMEOUT, 2,
            null
        );

        try
        {
            Object value = client.read();
            System.out.println( "Value at address 0: " + value );
        }
        finally
        {
            client.close();
        }
    }

    /**
     * Test reading a 32-bit float from holding registers (FC03).
     */
    public static void testReadHoldingRegisterFloat() throws Exception
    {
        System.out.println( "Test: Read Holding Register (FC03) - Float" );
        System.out.println( "-".repeat( 50 ) );

        ModbusTcpClient4J2mod client = new ModbusTcpClient4J2mod(
            HOST, PORT, UNIT_ID,
            0,                                          // Address (reads 2 registers: 0 and 1)
            "float",                                    // Data type
            ModbusTcpClient4J2mod.FC_HOLDING_REGISTERS,
            "ABCD",                                     // Try different orders if value is wrong
            INTERVAL, TIMEOUT, 2,
            null
        );

        try
        {
            Object value = client.read();
            System.out.printf( "Float value at address 0-1: %.6f%n", (Float) value );
        }
        finally
        {
            client.close();
        }
    }

    /**
     * Test reading a coil (FC01).
     */
    public static void testReadCoil() throws Exception
    {
        System.out.println( "Test: Read Coil (FC01) - Boolean" );
        System.out.println( "-".repeat( 50 ) );

        ModbusTcpClient4J2mod client = new ModbusTcpClient4J2mod(
            HOST, PORT, UNIT_ID,
            0,                              // Coil address
            "boolean",
            ModbusTcpClient4J2mod.FC_COILS, // FC01 - Read Coils
            "ABCD",
            INTERVAL, TIMEOUT, 2,
            null
        );

        try
        {
            Object value = client.read();
            System.out.println( "Coil 0 value: " + value );
        }
        finally
        {
            client.close();
        }
    }

    /**
     * Test reading a discrete input (FC02).
     */
    public static void testReadDiscreteInput() throws Exception
    {
        System.out.println( "Test: Read Discrete Input (FC02) - Boolean" );
        System.out.println( "-".repeat( 50 ) );

        ModbusTcpClient4J2mod client = new ModbusTcpClient4J2mod(
            HOST, PORT, UNIT_ID,
            0,
            "boolean",
            ModbusTcpClient4J2mod.FC_DISCRETE_INPUTS, // FC02 - Read Discrete Inputs (read-only)
            "ABCD",
            INTERVAL, TIMEOUT, 2,
            null
        );

        try
        {
            Object value = client.read();
            System.out.println( "Discrete Input 0 value: " + value );
        }
        finally
        {
            client.close();
        }
    }

    /**
     * Test writing to a holding register (FC06).
     */
    public static void testWriteHoldingRegister() throws Exception
    {
        System.out.println( "Test: Write Holding Register (FC06) - Int" );
        System.out.println( "-".repeat( 50 ) );

        ModbusTcpClient4J2mod client = new ModbusTcpClient4J2mod(
            HOST, PORT, UNIT_ID,
            0,
            "int",
            ModbusTcpClient4J2mod.FC_HOLDING_REGISTERS,
            "ABCD",
            INTERVAL, TIMEOUT, 2,
            null
        );

        try
        {
            // Read current value
            Object before = client.read();
            System.out.println( "Before: " + before );

            // Write new value
            int newValue = 12345;
            Object result = client.write( newValue );
            System.out.println( "Wrote: " + newValue + ", Response: " + result );

            // Read back
            Object after = client.read();
            System.out.println( "After: " + after );
        }
        finally
        {
            client.close();
        }
    }

    /**
     * Test writing to a coil (FC05).
     */
    public static void testWriteCoil() throws Exception
    {
        System.out.println( "Test: Write Coil (FC05) - Boolean" );
        System.out.println( "-".repeat( 50 ) );

        ModbusTcpClient4J2mod client = new ModbusTcpClient4J2mod(
            HOST, PORT, UNIT_ID,
            0,
            "boolean",
            ModbusTcpClient4J2mod.FC_COILS,
            "ABCD",
            INTERVAL, TIMEOUT, 2,
            null
        );

        try
        {
            // Read current value
            Object before = client.read();
            System.out.println( "Before: " + before );

            // Toggle the coil
            boolean newValue = !((Boolean) before);
            Object result = client.write( newValue );
            System.out.println( "Wrote: " + newValue + ", Response: " + result );

            // Read back
            Object after = client.read();
            System.out.println( "After: " + after );
        }
        finally
        {
            client.close();
        }
    }

    /**
     * Test different byte orders for 32-bit float values.
     * Useful for determining which byte order your device uses.
     */
    public static void testByteOrders() throws Exception
    {
        System.out.println( "Test: Byte Orders for Float" );
        System.out.println( "-".repeat( 50 ) );
        System.out.println( "Reading float from address 0-1 with different byte orders:" );
        System.out.println();

        String[] orders = { "ABCD", "BADC", "CDAB", "DCBA" };

        for( String order : orders )
        {
            ModbusTcpClient4J2mod client = new ModbusTcpClient4J2mod(
                HOST, PORT, UNIT_ID,
                0,
                "float",
                ModbusTcpClient4J2mod.FC_HOLDING_REGISTERS,
                order,
                INTERVAL, TIMEOUT, 2,
                null
            );

            try
            {
                Object value = client.read();
                System.out.printf( "  %s: %.6f%n", order, (Float) value );
            }
            catch( Exception e )
            {
                System.out.printf( "  %s: ERROR - %s%n", order, e.getMessage() );
            }
            finally
            {
                client.close();
            }
        }

        System.out.println();
        System.out.println( "Note: Only one byte order will give you the correct value." );
        System.out.println( "Common orders by manufacturer:" );
        System.out.println( "  - ABCD (Big-endian): Most common, ABB, Schneider" );
        System.out.println( "  - CDAB (Word-swap): Modicon, some Siemens" );
        System.out.println( "  - DCBA (Little-endian): Some Asian manufacturers" );
        System.out.println( "  - BADC (Byte-swap): Less common" );
    }

    // ========================================================================
    // HIGH-LEVEL WRAPPER TESTS (ModbusTcpClientWrapper)
    // ========================================================================

    /**
     * Test the wrapper with minimal configuration.
     */
    public static void testWrapperBasic() throws Exception
    {
        System.out.println( "Test: Wrapper - Basic Configuration" );
        System.out.println( "-".repeat( 50 ) );

        Map<String, Object> config = new HashMap<>();
        config.put( "uri",     HOST + ":" + PORT );
        config.put( "address", 0 );
        config.put( "type",    "int" );

        ModbusTcpClientWrapper wrapper = new ModbusTcpClientWrapper();
        wrapper.set( "test_device", config, createListener() );

        // Read will report errors via the listener if configuration is invalid
        wrapper.read();
    }

    /**
     * Test the wrapper with all configuration options.
     */
    public static void testWrapperWithAllOptions() throws Exception
    {
        System.out.println( "Test: Wrapper - Full Configuration" );
        System.out.println( "-".repeat( 50 ) );

        Map<String, Object> config = new HashMap<>();
        config.put( "uri",      HOST + ":" + PORT );  // Required: server address
        config.put( "address",  0 );                   // Required: register address
        config.put( "type",     "float" );             // Required: data type
        config.put( "unitid",   UNIT_ID );             // Optional: slave ID (default: 1)
        config.put( "function", "holding" );           // Optional: FC03 (default: auto)
        config.put( "order",    "ABCD" );              // Optional: byte order (default: ABCD)
        config.put( "interval", INTERVAL );            // Optional: polling interval ms (default: 5000)
        config.put( "timeout",  TIMEOUT );             // Optional: timeout ms (default: 4000)
        config.put( "retries",  3 );                   // Optional: retry count (default: 2)

        ModbusTcpClientWrapper wrapper = new ModbusTcpClientWrapper();
        wrapper.set( "sensor_temperature", config, createListener() );

        System.out.println( "Configuration set. Reading..." );
        // Read will report errors via the listener if configuration is invalid
        wrapper.read();
    }

    /**
     * Test the wrapper with automatic polling.
     */
    public static void testWrapperPolling() throws Exception
    {
        System.out.println( "Test: Wrapper - Automatic Polling" );
        System.out.println( "-".repeat( 50 ) );
        System.out.println( "Starting polling (press Ctrl+C to stop)..." );
        System.out.println();

        Map<String, Object> config = new HashMap<>();
        config.put( "uri",      HOST + ":" + PORT );
        config.put( "address",  0 );
        config.put( "type",     "int" );
        config.put( "unitid",   UNIT_ID );
        config.put( "interval", 2000 );  // Poll every 2 seconds
        config.put( "timeout",  3000 );

        ModbusTcpClientWrapper wrapper = new ModbusTcpClientWrapper();
        wrapper.set( "poll_test", config, createListener() );

        // Start polling - errors will be reported via the listener
        wrapper.start( null );

        // Keep running for a while
        try
        {
            Thread.sleep( 30000 );  // Run for 30 seconds
        }
        finally
        {
            wrapper.stop();
            System.out.println( "Polling stopped." );
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Creates a listener that prints events to the console.
     */
    private static IController.Listener createListener()
    {
        return new IController.Listener()
        {
            @Override
            public void onReaded( String deviceName, Object newValue )
            {
                System.out.println( "[READ]    " + deviceName + " = " + newValue );
            }

            @Override
            public void onChanged( String deviceName, Object newValue )
            {
                System.out.println( "[CHANGED] " + deviceName + " = " + newValue );
            }

            @Override
            public void onError( ILogger.Level level, String message, String device )
            {
                System.out.println( "[" + level + "] " + device + ": " + message );
            }
        };
    }
}
