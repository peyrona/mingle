
package com.peyrona.mingle.controllers.modbus;

import com.ghgande.j2mod.modbus.ModbusException;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.msg.ExceptionResponse;
import com.ghgande.j2mod.modbus.msg.ModbusRequest;
import com.ghgande.j2mod.modbus.msg.ModbusResponse;
import com.ghgande.j2mod.modbus.msg.ReadCoilsRequest;
import com.ghgande.j2mod.modbus.msg.ReadCoilsResponse;
import com.ghgande.j2mod.modbus.msg.ReadInputDiscretesRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputDiscretesResponse;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersResponse;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersResponse;
import com.ghgande.j2mod.modbus.msg.WriteCoilRequest;
import com.ghgande.j2mod.modbus.msg.WriteCoilResponse;
import com.ghgande.j2mod.modbus.msg.WriteMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.WriteMultipleRegistersResponse;
import com.ghgande.j2mod.modbus.msg.WriteSingleRegisterRequest;
import com.ghgande.j2mod.modbus.msg.WriteSingleRegisterResponse;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Production-ready Modbus TCP client implementation using J2mod library.
 * <p>
 * This class provides full support for Modbus TCP communication with configurable:
 * <ul>
 *   <li>Unit ID (slave address) - required for gateways and multi-device networks</li>
 *   <li>Function codes - supports FC01/FC02 for discrete, FC03/FC04 for registers</li>
 *   <li>Data types - boolean, int (16-bit), long (32-bit), float (32-bit IEEE 754)</li>
 *   <li>Byte ordering - ABCD, BADC, CDAB, DCBA for multi-register values</li>
 *   <li>Retries and timeouts - configurable connection resilience</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="https://github.com/steveohara/j2mod/">J2mod library</a>
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

    // Connection parameters
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

    // Runtime state
    private ModbusTCPMaster  master   = null;
    private ScheduledFuture  future   = null;
    private final ReentrantLock lock  = new ReentrantLock();
    private volatile boolean isClosing = false;

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
        this.sHost     = sHost;
        this.nPort     = nPort;
        this.nUnitId   = nUnitId;
        this.nAddress  = nAddress;
        this.sDataType = sDataType;
        this.sFunction = sFunction;
        this.sByteOrder = sByteOrder;
        this.nTimeout  = nTimeout;
        this.nRetries  = nRetries;
        this.nInterval = nInterval;
        this.listener  = listener;
    }

    //------------------------------------------------------------------------//
    // IModbusClient IMPLEMENTATION
    //------------------------------------------------------------------------//

    @Override
    public void open()
    {
        isClosing = false;

        // Initial delay allows slow devices to become ready (Modbus recommendation: 2000ms)
        long initialDelay = Math.max( 2000, nTimeout );

        this.future = UtilSys.executeAtRate(
            getClass().getName() + "-" + sHost + ":" + nPort + "/" + nAddress,
            initialDelay,
            nInterval,
            () ->
            {
                if( isClosing ) return;

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
            future.cancel( false );  // Don't interrupt if running
            future = null;
        }

        lock.lock();
        try
        {
            if( master != null )
            {
                try { master.disconnect(); }
                catch( Exception ignored ) { }
                master = null;
            }
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
                case "boolean":
                    return readBoolean();

                case "int":
                    return readInt();

                case "long":
                    return readLong();

                case "float":
                    return readFloat();

                default:
                    throw new IllegalStateException( "Unknown data type: " + sDataType );
            }
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
                case "boolean":
                    return writeBoolean( (Boolean) value );

                case "int":
                    return writeInt( ((Number) value).intValue() );

                case "long":
                    return writeLong( ((Number) value).longValue() );

                case "float":
                    return writeFloat( ((Number) value).floatValue() );

                default:
                    throw new IllegalStateException( "Unknown data type: " + sDataType );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    //------------------------------------------------------------------------//
    // CONNECTION MANAGEMENT
    //------------------------------------------------------------------------//

    private void ensureConnected() throws Exception
    {
        if( master == null )
        {
            master = new ModbusTCPMaster( sHost, nPort );
            master.setTimeout( nTimeout );
            master.setRetries( nRetries );
            master.setReconnecting( true );
        }

        if( !master.isConnected() )
        {
            master.connect();
        }

        if( !master.isConnected() )
        {
            throw new IOException( "Failed to connect to Modbus server at " + sHost + ":" + nPort );
        }
    }

    //------------------------------------------------------------------------//
    // READ OPERATIONS
    //------------------------------------------------------------------------//

    private Boolean readBoolean() throws ModbusException
    {
        ModbusRequest request;
        ModbusResponse response;

        if( FC_DISCRETE_INPUTS.equals( sFunction ) )
        {
            // FC02 - Read Discrete Inputs (read-only)
            request = new ReadInputDiscretesRequest( nAddress, 1 );
            request.setUnitID( nUnitId );
            response = executeTransaction( request );
            return ((ReadInputDiscretesResponse) response).getDiscreteStatus( 0 );
        }
        else
        {
            // FC01 - Read Coils (read/write) - default for boolean
            request = new ReadCoilsRequest( nAddress, 1 );
            request.setUnitID( nUnitId );
            response = executeTransaction( request );
            return ((ReadCoilsResponse) response).getCoilStatus( 0 );
        }
    }

    private Integer readInt() throws ModbusException
    {
        InputRegister[] registers = readRegisters( 1 );
        return registers[0].toUnsignedShort();
    }

    private Long readLong() throws ModbusException
    {
        InputRegister[] registers = readRegisters( 2 );
        byte[] bytes = registersToBytes( registers );
        bytes = applyByteOrder( bytes );
        return bytesToUnsignedInt( bytes );
    }

    private Float readFloat() throws ModbusException
    {
        InputRegister[] registers = readRegisters( 2 );
        byte[] bytes = registersToBytes( registers );
        bytes = applyByteOrder( bytes );
        return ByteBuffer.wrap( bytes ).order( ByteOrder.BIG_ENDIAN ).getFloat();
    }

    private InputRegister[] readRegisters( int count ) throws ModbusException
    {
        ModbusRequest request;
        ModbusResponse response;

        if( FC_INPUT_REGISTERS.equals( sFunction ) )
        {
            // FC04 - Read Input Registers (read-only)
            request = new ReadInputRegistersRequest( nAddress, count );
            request.setUnitID( nUnitId );
            response = executeTransaction( request );
            ReadInputRegistersResponse rirr = (ReadInputRegistersResponse) response;
            return rirr.getRegisters();
        }
        else
        {
            // FC03 - Read Holding Registers (read/write) - default for registers
            request = new ReadMultipleRegistersRequest( nAddress, count );
            request.setUnitID( nUnitId );
            response = executeTransaction( request );
            ReadMultipleRegistersResponse rmrr = (ReadMultipleRegistersResponse) response;
            return rmrr.getRegisters();
        }
    }

    //------------------------------------------------------------------------//
    // WRITE OPERATIONS
    //------------------------------------------------------------------------//

    private Boolean writeBoolean( boolean value ) throws ModbusException
    {
        if( FC_DISCRETE_INPUTS.equals( sFunction ) )
        {
            throw new UnsupportedOperationException(
                "Cannot write to discrete inputs (FC02). Use 'coils' function for writable booleans." );
        }

        // FC05 - Write Single Coil
        WriteCoilRequest request = new WriteCoilRequest( nAddress, value );
        request.setUnitID( nUnitId );
        WriteCoilResponse response = (WriteCoilResponse) executeTransaction( request );
        return response.getCoil();
    }

    private Integer writeInt( int value ) throws ModbusException
    {
        if( FC_INPUT_REGISTERS.equals( sFunction ) )
        {
            throw new UnsupportedOperationException(
                "Cannot write to input registers (FC04). Use 'holding' function for writable registers." );
        }

        // FC06 - Write Single Register
        // Modbus registers are 16-bit unsigned, so we mask to prevent sign extension issues
        Register register = new SimpleRegister( value & 0xFFFF );
        WriteSingleRegisterRequest request = new WriteSingleRegisterRequest( nAddress, register );
        request.setUnitID( nUnitId );
        WriteSingleRegisterResponse response = (WriteSingleRegisterResponse) executeTransaction( request );
        return response.getRegisterValue();
    }

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

        WriteMultipleRegistersRequest request = new WriteMultipleRegistersRequest( nAddress, registers );
        request.setUnitID( nUnitId );
        WriteMultipleRegistersResponse response = (WriteMultipleRegistersResponse) executeTransaction( request );

        // Read back the written value to confirm
        return readLong();
    }

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

        WriteMultipleRegistersRequest request = new WriteMultipleRegistersRequest( nAddress, registers );
        request.setUnitID( nUnitId );
        WriteMultipleRegistersResponse response = (WriteMultipleRegistersResponse) executeTransaction( request );

        // Read back the written value to confirm
        return readFloat();
    }

    //------------------------------------------------------------------------//
    // TRANSACTION EXECUTION
    //------------------------------------------------------------------------//

    private ModbusResponse executeTransaction( ModbusRequest request ) throws ModbusException
    {
        var transaction = master.getTransport().createTransaction();
        transaction.setRequest( request );
        transaction.setRetries( nRetries );
        transaction.execute();

        ModbusResponse response = transaction.getResponse();

        if( response == null )
        {
            throw new ModbusException( "No response received from server" );
        }

        // Check for Modbus exception response
        if( response instanceof ExceptionResponse )
        {
            ExceptionResponse exResponse = (ExceptionResponse) response;
            int exceptionCode = exResponse.getExceptionCode();
            throw new ModbusException( "Modbus exception code: " + exceptionCode
                + " - " + getExceptionMessage( exceptionCode ) );
        }

        return response;
    }

    //------------------------------------------------------------------------//
    // BYTE CONVERSION UTILITIES
    //------------------------------------------------------------------------//

    /**
     * Converts registers to a byte array in big-endian order.
     */
    private byte[] registersToBytes( InputRegister[] registers )
    {
        byte[] bytes = new byte[ registers.length * 2 ];
        for( int i = 0; i < registers.length; i++ )
        {
            byte[] regBytes = registers[i].toBytes();
            bytes[i * 2]     = regBytes[0];
            bytes[i * 2 + 1] = regBytes[1];
        }
        return bytes;
    }

    /**
     * Converts a byte array to registers (2 bytes per register).
     */
    private Register[] bytesToRegisters( byte[] bytes )
    {
        Register[] registers = new Register[ bytes.length / 2 ];
        for( int i = 0; i < registers.length; i++ )
        {
            registers[i] = new SimpleRegister( bytes[i * 2], bytes[i * 2 + 1] );
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
        return ((long)(bytes[0] & 0xFF) << 24) |
               ((long)(bytes[1] & 0xFF) << 16) |
               ((long)(bytes[2] & 0xFF) << 8) |
               ((long)(bytes[3] & 0xFF));
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
            return in;  // Only applies to 32-bit values

        byte[] out = new byte[4];

        switch( sByteOrder )
        {
            case "ABCD":  // Big-endian (no change)
                out[0] = in[0];
                out[1] = in[1];
                out[2] = in[2];
                out[3] = in[3];
                break;

            case "BADC":  // Big-endian, byte-swapped within words
                out[0] = in[1];
                out[1] = in[0];
                out[2] = in[3];
                out[3] = in[2];
                break;

            case "CDAB":  // Little-endian word order, big-endian bytes
                out[0] = in[2];
                out[1] = in[3];
                out[2] = in[0];
                out[3] = in[1];
                break;

            case "DCBA":  // Full little-endian
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

    private void validateWriteValue( Object value )
    {
        if( value == null )
        {
            throw new IllegalArgumentException( "Write value cannot be null" );
        }

        if( "boolean".equals( sDataType ) )
        {
            if( !(value instanceof Boolean) )
            {
                throw new IllegalArgumentException(
                    "Expected Boolean for data type 'boolean', got: " + value.getClass().getSimpleName() );
            }
        }
        else
        {
            if( !(value instanceof Number) )
            {
                throw new IllegalArgumentException(
                    "Expected Number for data type '" + sDataType + "', got: " + value.getClass().getSimpleName() );
            }
        }
    }

    //------------------------------------------------------------------------//
    // ERROR MESSAGES
    //------------------------------------------------------------------------//

    /**
     * Returns a human-readable message for Modbus exception codes.
     */
    private String getExceptionMessage( int code )
    {
        switch( code )
        {
            case 1:  return "Illegal Function - The function code is not supported";
            case 2:  return "Illegal Data Address - The address is not valid";
            case 3:  return "Illegal Data Value - The value is not valid";
            case 4:  return "Server Device Failure - An unrecoverable error occurred";
            case 5:  return "Acknowledge - Request accepted, processing in progress";
            case 6:  return "Server Device Busy - The server is busy";
            case 8:  return "Memory Parity Error - Memory parity error detected";
            case 10: return "Gateway Path Unavailable - Gateway path not available";
            case 11: return "Gateway Target Device Failed to Respond - No response from target";
            default: return "Unknown exception code";
        }
    }
}
