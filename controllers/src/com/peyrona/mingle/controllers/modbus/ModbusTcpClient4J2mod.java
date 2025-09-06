
package com.peyrona.mingle.controllers.modbus;

import com.ghgande.j2mod.modbus.ModbusException;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.io.ModbusTransaction;
import com.ghgande.j2mod.modbus.msg.ModbusRequest;
import com.ghgande.j2mod.modbus.msg.ModbusResponse;
import com.ghgande.j2mod.modbus.msg.ReadCoilsRequest;
import com.ghgande.j2mod.modbus.msg.ReadCoilsResponse;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersResponse;
import com.ghgande.j2mod.modbus.msg.WriteCoilRequest;
import com.ghgande.j2mod.modbus.msg.WriteMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.WriteSingleRegisterRequest;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.util.ModbusUtil;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;

/**
 * This class uses the J2mod lib by Steve O'Hara to manage a ModBus.
 * https://github.com/steveohara/j2mod/
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class ModbusTcpClient4J2mod
       implements IModbusClient
{
    private final ModbusTCPMaster        master;
    private final int                    nAddress;
    private final String                 sType;
    private final String                 sOrder;
    private final int                    nTimeout;
    private final int                    nInterval;
    private final IModbusClient.Listener listener;
    private       ScheduledFuture        future = null;

    //------------------------------------------------------------------------//

    ModbusTcpClient4J2mod( String sHost, int nPort, int nAddress, String sType, String sOrder, int nInterval, int nTimeout, IModbusClient.Listener listener )
    {
        // Parameters are checked to be valid before arriving here

        this.nAddress  = nAddress;
        this.sType     = sType;
        this.sOrder    = sOrder;
        this.nTimeout  = nTimeout;
        this.nInterval = nInterval;
        this.listener  = listener;

        master = new ModbusTCPMaster( sHost, nPort );
        master.setReconnecting( true );
        master.setTimeout( nTimeout );
    }

    //------------------------------------------------------------------------//
    // BY IMPLEMENTING IModbusClient

    @Override
    public void open()
    {
        this.future = UtilSys.executeAtRate( getClass().getName(),
                                             Math.max( 2000, nTimeout ),     // 2000 is the recommended time in Modbus to start accessing devices because some of them could need a lot of time to be available
                                             nInterval,
                                             () ->
                                             {
                                                 try { listener.onMessage( read() ); }
                                                 catch( Exception ex ) { listener.onError( ex ); }
                                             } );
    }

    @Override
    public void close()
    {
        if( future != null )
        {
            future.cancel( true );
            future = null;
        }

        master.disconnect();
    }

    @Override
    public Object read() throws Exception
    {
        connect();

        ModbusRequest              request;
        ReadCoilsResponse          rcr;
        ReadInputRegistersResponse rirr;

        switch( sType )
        {
            case "boolean":
                request = new ReadCoilsRequest( nAddress, 1 );
                rcr     = ((ReadCoilsResponse) getResponse( master, request ));
                return toBoolean( rcr );

            case "int":
                request = new ReadInputRegistersRequest( nAddress, 1 );
                rirr    = (ReadInputRegistersResponse) getResponse( master, request );
                return toInt( rirr );

            case "long":
                request = new ReadInputRegistersRequest( nAddress, 2 );
                rirr    = (ReadInputRegistersResponse) getResponse( master, request );
                return toLong( rirr );

            case "float":
                request = new ReadInputRegistersRequest( nAddress, 2 );
                rirr    = (ReadInputRegistersResponse) getResponse( master, request );
                return toFloat( rirr );
        }

        return null;    // Never will happen
    }

    @Override
    public Object write( Object value ) throws Exception
    {
        connect();

        ModbusRequest              request;
        ReadCoilsResponse          rcr;
        ReadInputRegistersResponse rirr;

        checkType( sType, value );

        switch( sType )
        {
            case "boolean":
                request = new WriteCoilRequest( nAddress, (Boolean) value );
                rcr     = ((ReadCoilsResponse) getResponse( master, request ));
                return toBoolean( rcr );

            case "int":
                request = new WriteSingleRegisterRequest( nAddress, new SimpleRegister( ((Number) value).intValue() ) );
                rirr    = (ReadInputRegistersResponse) getResponse( master, request );
                return toInt( rirr );

            case "long":
                request = new WriteMultipleRegistersRequest( nAddress, long2Registers( (Long) value ) );
                rirr    = (ReadInputRegistersResponse) getResponse( master, request );
                return toLong( rirr );

            case "float":
                request = new WriteMultipleRegistersRequest( nAddress, float2Registers( (Float) value ) );
                rirr    = (ReadInputRegistersResponse) getResponse( master, request );
                return toFloat( rirr );
        }

        return null;    // Never will happen
    }

    //------------------------------------------------------------------------//

    private void connect() throws Exception
    {
        if( ! master.isConnected() )
            master.connect();

//        while( master.isReconnecting() )
//        {
//            try
//            {
//                Thread.sleep( 300 );
//            }
//            catch( InterruptedException ie )
//            {
//                break;
//            }
//        }

        if( ! master.isConnected() )
            throw new IOException( "Not connected: "+ master );
    }

    private ModbusResponse getResponse( ModbusTCPMaster master, ModbusRequest request ) throws ModbusException
    {
        ModbusTransaction transaction = master.getTransport().createTransaction();
                          transaction.setRequest( request );
                          transaction.setRetries( 2 );
                          transaction.execute();

        return transaction.getResponse();
    }

    private void checkType( String sType, Object value )
    {
        boolean bErr = ("boolean".equals( sType ) && (! (value instanceof Boolean) ))
                       ||
                       (! (value instanceof Number) );

        if( bErr )
            throw new IllegalArgumentException( "'value' must be a "+ ("boolean".equals( sType ) ? sType : "number") +", but found ["+ value +']' );
    }

    private Boolean toBoolean( ReadCoilsResponse rcr )
    {
        return rcr.getCoilStatus( 0 );
    }

    private Short toInt( ReadInputRegistersResponse rirr )
    {
        return ModbusUtil.registerToShort( Arrays.copyOfRange( rirr.getMessage(), 1, 3 ) );
    }

    private Integer toLong( ReadInputRegistersResponse rirr )
    {
        return ModbusUtil.registersToInt( Arrays.copyOfRange( rirr.getMessage(), 1, 5 ) );
    }

    private Float toFloat( ReadInputRegistersResponse rirr )
    {
        byte[] ab = Arrays.copyOfRange( rirr.getMessage(), 1, 5 );

        return ByteBuffer.wrap( order( ab ) ).order( ByteOrder.LITTLE_ENDIAN ).getFloat();
    }

    Register[] long2Registers( Long l )
    {
        byte[] ab = order( long2Bytes( l ) );

        return new Register[]
                   {
                        new SimpleRegister( ab[0], ab[1] ),
                        new SimpleRegister( ab[2], ab[3] ),
                   };
    }

    // Modbus long is 4 bytes; Java long is 8 bytes
    byte[] long2Bytes( Long l )
    {
        return new byte[] { (byte) ((l >> 24) & 0xff),
                            (byte) ((l >> 16) & 0xff),
                            (byte) ((l >>  8) & 0xff),
                            (byte) ((l      ) & 0xff) };
    }

    Register[] float2Registers( Float f )
    {
        byte[] ab = order( float2Bytes( f ) );

        return new Register[]
                   {
                        new SimpleRegister( ab[0], ab[1] ),
                        new SimpleRegister( ab[2], ab[3] ),
                   };
    }

    byte[] float2Bytes( Float f )
    {
        int ib = Float.floatToIntBits( f );

        return new byte[] { (byte) (ib >> 24),
                            (byte) (ib >> 16),
                            (byte) (ib >>  8),
                            (byte) (ib) };
    }

    private byte[] order( byte[] in )
    {
        byte[] out = new byte[in.length];

        switch( sOrder )
        {
            case "ABCD":
                out = in;
                break;

            case "BADC":
                out[0] = in[1];
                out[1] = in[0];
                if( in.length < 4 ) break;
                out[2] = in[3];
                out[3] = in[2];
                break;

            case "CDAB":
                out[0] = in[2];
                out[1] = in[3];
                if( in.length < 4 ) break;
                out[2] = in[0];
                out[3] = in[1];
                break;

            case "DCBA":
                out[0] = in[3];
                out[1] = in[2];
                if( in.length < 4 ) break;
                out[2] = in[1];
                out[3] = in[0];
                break;

            default:
                throw new IllegalArgumentException( sOrder +": invalid" );
        }

        return out;
    }
}