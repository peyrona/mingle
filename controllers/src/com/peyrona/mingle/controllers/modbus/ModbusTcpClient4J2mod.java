
package com.peyrona.mingle.controllers.modbus;

import com.ghgande.j2mod.modbus.ModbusException;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.util.BitVector;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Production-ready Modbus TCP client implementation using J2mod library.
 * <p>
 * This class uses the J2mod Facade pattern ({@link ModbusTCPMaster}) for cleaner,
 * more maintainable code that follows library best practices. The facade handles
 * all transaction management, connection lifecycle, and thread safety internally.
 * <p>
 * Supported features:
 * <ul>
 *   <li>Unit ID (slave address) - required for gateways and multi-device networks</li>
 *   <li>Function codes - FC01/FC02 for discrete, FC03/FC04 for registers</li>
 *   <li>Data types - boolean, int (16-bit), long (32-bit), float (32-bit IEEE 754)</li>
 *   <li>Byte ordering - ABCD, BADC, CDAB, DCBA for multi-register values</li>
 *   <li>Retries and timeouts - configurable connection resilience</li>
 * </ul>
 *
 * @author Francisco Jose Morero Peyrona
 * @see <a href="https://github.com/steveohara/j2mod/">J2mod library</a>
 * @see <a href="https://github.com/steveohara/j2mod/wiki/Using-j2mod-to-read-from-a-Slave">J2mod Facade Usage</a>
 * @see <a href="https://github.com/peyrona/mingle">Mingle project</a>
 */
public final class ModbusTcpClient4J2mod implements IModbusClient
{
    /**
     * Function code for reading coils (FC01) - read/write discrete outputs.
     */
    public static final String FC_COILS = "coils";

    /**
     * Function code for reading discrete inputs (FC02) - read-only discrete inputs.
     */
    public static final String FC_DISCRETE_INPUTS = "discrete";

    /**
     * Function code for reading holding registers (FC03) - read/write 16-bit registers.
     */
    public static final String FC_HOLDING_REGISTERS = "holding";

    /**
     * Function code for reading input registers (FC04) - read-only 16-bit registers.
     */
    public static final String FC_INPUT_REGISTERS = "input";

    // Connection parameters (immutable after construction)
    private final String  sHost;
    private final int     nPort;
    private final int     nUnitId;
    private final int     nAddress;
    private final String  sDataType;
    private final String  sFunction;
    private final String  sByteOrder;
    private final int     nTimeout;
    private final int     nRetries;
    private final long    nInterval;
    private final IModbusClient.Listener listener;

    // Runtime state (protected by lock)
    private final ReentrantLock  lock      = new ReentrantLock();
    private ModbusTCPMaster      master    = null;
    private ScheduledFuture<?>   future    = null;
    private volatile boolean     isClosing = false;

    //------------------------------------------------------------------------//
    // CONSTRUCTOR
    //------------------------------------------------------------------------//

    /**
     * Creates a new Modbus TCP client with full configuration options.
     *
     * @param sHost     Modbus server hostname or IP address
     * @param nPort     Modbus server port (typically 502)
     * @param nUnitId   Unit identifier (slave ID), 0-255. Use 0 or 1 for direct TCP connections,
     *                  specific ID for gateways bridging to RTU devices
     * @param nAddress  Register/coil starting address (0-based)
     * @param sDataType Data type: "boolean", "int" (16-bit), "long" (32-bit), "float" (32-bit IEEE 754)
     * @param sFunction Function code type: "coils" (FC01), "discrete" (FC02),
     *                  "holding" (FC03), "input" (FC04)
     * @param sByteOrder Byte order for multi-register values: "ABCD", "BADC", "CDAB", "DCBA"
     * @param nInterval Polling interval in milliseconds (minimum 30ms recommended)
     * @param nTimeout  Connection/response timeout in milliseconds
     * @param nRetries  Number of retries on communication failure (0-10)
     * @param listener  Callback listener for messages and errors
     */
    ModbusTcpClient4J2mod( String sHost, int nPort, int nUnitId, int nAddress,
                           String sDataType, String sFunction, String sByteOrder,
                           int nInterval, int nTimeout, int nRetries,
                           IModbusClient.Listener listener )
    {
        this.sHost      = sHost;
        this.nPort      = nPort;
        this.nUnitId    = nUnitId;
        this.nAddress   = nAddress;
        this.sDataType  = sDataType;
        this.sFunction  = sFunction;
        this.sByteOrder = sByteOrder;
        this.nTimeout   = nTimeout;
        this.nRetries   = nRetries;
        this.nInterval  = nInterval;
        this.listener   = listener;
    }

    //------------------------------------------------------------------------//
    // IModbusClient IMPLEMENTATION
    //------------------------------------------------------------------------//

    @Override
    public void open()
    {
        isClosing = false;

        // Initial delay allows slow devices to become ready
        long initialDelay = Math.max( 2000, nTimeout );

        this.future = UtilSys.executeAtRate(
            getClass().getName() + "-" + sHost + ":" + nPort + "/" + nAddress,
            initialDelay,
            nInterval,
            () ->
            {
                if( isClosing )
                    return;

                try
                {
                    Object value = read();

                    if( listener != null && !isClosing )
                        listener.onMessage( value );
                }
                catch( Exception ex )
                {
                    if( listener != null && !isClosing )
                        listener.onError( ex );
                }
            } );
    }

    @Override
    public void close()
    {
        isClosing = true;

        if( future != null )
        {
            future.cancel( false );   // Don't interrupt if running
            future = null;
        }

        lock.lock();

        try
        {
            disconnectMaster();
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public Object read() throws Exception
    {
        lock.lock();

        try
        {
            ensureConnected();

            switch( sDataType )
            {
                case "boolean": return readBoolean();
                case "int":     return readInt();
                case "long":    return readLong();
                case "float":   return readFloat();
                default:
                    throw new IllegalStateException( "Unknown data type: " + sDataType );
            }
        }
        catch( Exception ex )
        {
            // On error, disconnect so next call will reconnect fresh
            handleConnectionError( ex );
            throw ex;
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public Object write( Object value ) throws Exception
    {
        lock.lock();

        try
        {
            ensureConnected();
            validateWriteValue( value );

            switch( sDataType )
            {
                case "boolean": return writeBoolean( (Boolean) value );
                case "int":     return writeInt( ((Number) value).intValue() );
                case "long":    return writeLong( ((Number) value).longValue() );
                case "float":   return writeFloat( ((Number) value).floatValue() );
                default:
                    throw new IllegalStateException( "Unknown data type: " + sDataType );
            }
        }
        catch( Exception ex )
        {
            handleConnectionError( ex );
            throw ex;
        }
        finally
        {
            lock.unlock();
        }
    }

    //------------------------------------------------------------------------//
    // CONNECTION MANAGEMENT
    //------------------------------------------------------------------------//

    /**
     * Ensures the master is connected, creating and connecting if needed.
     * <p>
     * Uses reconnecting=false for better polling performance (keeps connection open).
     * Connection failures are handled by disconnecting and retrying on next call.
     */
    private void ensureConnected() throws Exception
    {
        if( master == null )
        {
            master = new ModbusTCPMaster( sHost, nPort );
            master.setTimeout( nTimeout );
            master.setRetries( nRetries );
            master.setReconnecting( false );   // Keep connection open for polling efficiency (reconnect manually on error)
        }

        if( ! master.isConnected() )
            master.connect();

        if( ! master.isConnected() )
            throw new IOException( "Failed to connect to Modbus server at " + sHost + ":" + nPort );
    }

    /**
     * Handles connection errors by disconnecting so the next call will reconnect.
     */
    private void handleConnectionError( Exception exc )
    {
        // Disconnect on error so next attempt will try fresh connection
        disconnectMaster();

        if( UtilSys.getLogger() != null )
            UtilSys.getLogger().log( ILogger.Level.WARNING, exc );
    }

    /**
     * Safely disconnects and nullifies the master connection.
     */
    private void disconnectMaster()
    {
        if( master != null )
        {
            try
            {
                master.disconnect();
            }
            catch( Exception ignored )
            {
                // Ignore disconnect errors
            }

            master = null;
        }
    }

    //------------------------------------------------------------------------//
    // READ OPERATIONS (using Facade methods)
    //------------------------------------------------------------------------//

    /**
     * Reads a boolean value using the appropriate function code.
     * Uses facade methods: readCoils() or readInputDiscretes().
     */
    private Boolean readBoolean() throws ModbusException
    {
        if( FC_DISCRETE_INPUTS.equals( sFunction ) )
        {
            // FC02 - Read Discrete Inputs (read-only)
            BitVector result = master.readInputDiscretes( nUnitId, nAddress, 1 );
            return result.getBit( 0 );
        }
        else
        {
            // FC01 - Read Coils (read/write) - default for boolean
            BitVector result = master.readCoils( nUnitId, nAddress, 1 );
            return result.getBit( 0 );
        }
    }

    /**
     * Reads a 16-bit integer value from registers.
     * Uses facade method: readMultipleRegisters() or readInputRegisters().
     */
    private Integer readInt() throws ModbusException
    {
        InputRegister[] registers = readRegisters( 1 );
        return registers[0].toUnsignedShort();
    }

    /**
     * Reads a 32-bit long value from two consecutive registers.
     * Applies configured byte order transformation.
     */
    private Long readLong() throws ModbusException
    {
        InputRegister[] registers = readRegisters( 2 );
        byte[]          bytes = registersToBytes( registers );
                        bytes = applyByteOrder( bytes );
        return bytesToUnsignedInt( bytes );
    }

    /**
     * Reads a 32-bit IEEE 754 float value from two consecutive registers.
     * Applies configured byte order transformation.
     */
    private Float readFloat() throws ModbusException
    {
        InputRegister[] registers = readRegisters( 2 );
        byte[]          bytes     = registersToBytes( registers );
                        bytes     = applyByteOrder( bytes );
        return ByteBuffer.wrap( bytes ).order( ByteOrder.BIG_ENDIAN ).getFloat();
    }

    /**
     * Reads registers using the appropriate function code.
     * Uses facade methods: readMultipleRegisters() or readInputRegisters().
     * Both return InputRegister[] (Register extends InputRegister).
     */
    private InputRegister[] readRegisters( int count ) throws ModbusException
    {
        if( FC_INPUT_REGISTERS.equals( sFunction ) )
        {
            // FC04 - Read Input Registers (read-only)
            return master.readInputRegisters( nUnitId, nAddress, count );
        }
        else
        {
            // FC03 - Read Holding Registers (read/write) - default for registers
            return master.readMultipleRegisters( nUnitId, nAddress, count );
        }
    }

    //------------------------------------------------------------------------//
    // WRITE OPERATIONS (using Facade methods)
    //------------------------------------------------------------------------//

    /**
     * Writes a boolean value to a coil.
     * Uses facade method: writeCoil().
     */
    private Boolean writeBoolean( boolean value ) throws ModbusException
    {
        if( FC_DISCRETE_INPUTS.equals( sFunction ) )
        {
            throw new UnsupportedOperationException(
                "Cannot write to discrete inputs (FC02). Use 'coils' function for writable booleans." );
        }

        // FC05 - Write Single Coil
        boolean result = master.writeCoil( nUnitId, nAddress, value );
        return result;
    }

    /**
     * Writes a 16-bit integer value to a single register.
     * Uses facade method: writeSingleRegister().
     */
    private Integer writeInt( int value ) throws ModbusException
    {
        if( FC_INPUT_REGISTERS.equals( sFunction ) )
        {
            throw new UnsupportedOperationException(
                "Cannot write to input registers (FC04). Use 'holding' function for writable registers." );
        }

        // FC06 - Write Single Register
        // Modbus registers are 16-bit unsigned, mask to prevent sign extension
        Register register = new SimpleRegister( value & 0xFFFF );

        // writeSingleRegister returns the register value written
        int written = master.writeSingleRegister( nUnitId, nAddress, register );

        // Return the value that was written
        return written;
    }

    /**
     * Writes a 32-bit long value to two consecutive registers.
     * Uses facade method: writeMultipleRegisters().
     */
    private Long writeLong( long value ) throws ModbusException
    {
        if( FC_INPUT_REGISTERS.equals( sFunction ) )
        {
            throw new UnsupportedOperationException(
                "Cannot write to input registers (FC04). Use 'holding' function for writable registers." );
        }

        // FC16 - Write Multiple Registers (2 registers for 32-bit value)
        byte[] bytes = longToBytes( value );
               bytes = applyByteOrder( bytes );
        Register[] registers = bytesToRegisters( bytes );

        master.writeMultipleRegisters( nUnitId, nAddress, registers );

        // Read back the written value to confirm
        return readLong();
    }

    /**
     * Writes a 32-bit IEEE 754 float value to two consecutive registers.
     * Uses facade method: writeMultipleRegisters().
     */
    private Float writeFloat( float value ) throws ModbusException
    {
        if( FC_INPUT_REGISTERS.equals( sFunction ) )
        {
            throw new UnsupportedOperationException(
                "Cannot write to input registers (FC04). Use 'holding' function for writable registers." );
        }

        // FC16 - Write Multiple Registers (2 registers for 32-bit float)
        byte[] bytes = floatToBytes( value );
               bytes = applyByteOrder( bytes );
        Register[] registers = bytesToRegisters( bytes );

        master.writeMultipleRegisters( nUnitId, nAddress, registers );

        // Read back the written value to confirm
        return readFloat();
    }

    //------------------------------------------------------------------------//
    // BYTE CONVERSION UTILITIES
    //------------------------------------------------------------------------//

    /**
     * Converts registers to a byte array in big-endian order.
     */
    private byte[] registersToBytes( InputRegister[] registers )
    {
        byte[] bytes = new byte[registers.length * 2];

        for( int n = 0; n < registers.length; n++ )
        {
            byte[] regBytes = registers[n].toBytes();
            bytes[n * 2]     = regBytes[0];
            bytes[n * 2 + 1] = regBytes[1];
        }

        return bytes;
    }

    /**
     * Converts a byte array to registers (2 bytes per register).
     */
    private Register[] bytesToRegisters( byte[] bytes )
    {
        Register[] registers = new Register[bytes.length / 2];

        for( int n = 0; n < registers.length; n++ )
        {
            registers[n] = new SimpleRegister( bytes[n * 2], bytes[n * 2 + 1] );
        }

        return registers;
    }

    /**
     * Converts a 32-bit long value to 4 bytes in big-endian order.
     * Note: Modbus "long" is 32-bit (2 registers), not Java's 64-bit long.
     */
    private byte[] longToBytes( long value )
    {
        return new byte[] {
            (byte) ((value >> 24) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }

    /**
     * Converts 4 bytes to an unsigned 32-bit integer (returned as long).
     */
    private long bytesToUnsignedInt( byte[] bytes )
    {
        return ((long) (bytes[0] & 0xFF) << 24) |
               ((long) (bytes[1] & 0xFF) << 16) |
               ((long) (bytes[2] & 0xFF) << 8)  |
               ((long) (bytes[3] & 0xFF));
    }

    /**
     * Converts a float to 4 bytes in IEEE 754 big-endian format.
     */
    private byte[] floatToBytes( float value )
    {
        int bits = Float.floatToIntBits( value );

        return new byte[] {
            (byte) ((bits >> 24) & 0xFF),
            (byte) ((bits >> 16) & 0xFF),
            (byte) ((bits >> 8) & 0xFF),
            (byte) (bits & 0xFF)
        };
    }

    //------------------------------------------------------------------------//
    // BYTE ORDER HANDLING
    //------------------------------------------------------------------------//

    /**
     * Applies the configured byte order transformation to a 4-byte array.
     * <p>
     * Modbus does not define a standard byte order for 32-bit values spanning
     * multiple registers. Different manufacturers use different conventions:
     * <ul>
     *   <li>ABCD - Big-endian (most common, also called "high word first")</li>
     *   <li>DCBA - Little-endian (also called "low word first")</li>
     *   <li>BADC - Big-endian with byte swap within words</li>
     *   <li>CDAB - Little-endian with byte swap within words (also called "mid-big endian")</li>
     * </ul>
     *
     * @param in Input bytes in ABCD order (big-endian)
     * @return Bytes reordered according to the configured byte order
     */
    private byte[] applyByteOrder( byte[] in )
    {
        if( in.length != 4 )
            return in;   // Only applies to 32-bit values

        byte[] out = new byte[4];

        switch( sByteOrder )
        {
            case "ABCD":   // Big-endian (no change)
                out[0] = in[0];
                out[1] = in[1];
                out[2] = in[2];
                out[3] = in[3];
                break;

            case "BADC":   // Big-endian, byte-swapped within words
                out[0] = in[1];
                out[1] = in[0];
                out[2] = in[3];
                out[3] = in[2];
                break;

            case "CDAB":   // Little-endian word order, big-endian bytes
                out[0] = in[2];
                out[1] = in[3];
                out[2] = in[0];
                out[3] = in[1];
                break;

            case "DCBA":   // Full little-endian
                out[0] = in[3];
                out[1] = in[2];
                out[2] = in[1];
                out[3] = in[0];
                break;

            default:
                throw new IllegalArgumentException( "Invalid byte order: " + sByteOrder
                    + ". Must be ABCD, BADC, CDAB, or DCBA." );
        }

        return out;
    }

    //------------------------------------------------------------------------//
    // VALIDATION
    //------------------------------------------------------------------------//

    /**
     * Validates that the write value matches the expected data type.
     */
    private void validateWriteValue( Object value )
    {
        if( value == null )
            throw new IllegalArgumentException( "Write value cannot be null" );

        if( "boolean".equals( sDataType ) )
        {
            if( !(value instanceof Boolean) )
                throw new IllegalArgumentException( "Expected Boolean for data type 'boolean', got: " + value.getClass().getSimpleName() );
        }
        else
        {
            if( !(value instanceof Number) )
                throw new IllegalArgumentException( "Expected Number for data type '" + sDataType + "', got: " + value.getClass().getSimpleName() );
        }
    }
}