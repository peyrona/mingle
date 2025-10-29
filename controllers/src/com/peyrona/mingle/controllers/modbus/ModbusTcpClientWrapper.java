
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
 * https://sourceforge.net/projects/jamod/
 *
 * https://github.com/steveohara/j2mod
 * https://github.com/steveohara/j2mod/wiki/Using-j2mod-to-send-from-a-Slave
 *
 * https://sourceforge.net/projects/jlibmodbus/
 *
 * https://github.com/digitalpetri/modbus
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ModbusTcpClientWrapper
       extends ControllerBase
{
    private static final String KEY_TIMEOUT  = "timeout";
    private static final String KEY_INTERVAL = "interval";

    private IModbusClient client = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        boolean bOK   = true;
        String  sURI  = (String) deviceInit.get( "uri" );    // This is REQUIRED
        String  sHost = UtilComm.getHost( sURI );
        int     nPort = UtilComm.getPort( sURI, 552 );

        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        if( UtilStr.isEmpty( sHost ) )
        {
            sendIsInvalid( "URI is empty" );
            bOK = false;
        }

        if( nPort < UtilComm.TCP_PORT_MIN_ALLOWED || nPort > UtilComm.TCP_PORT_MAX_ALLOWED )
        {
            sendIsInvalid( "Invalid port = "+ nPort );
            bOK = false;
        }

        int nAddr = ((Number) deviceInit.get( "address" )).intValue();      // This is REQUIRED

        if( nAddr < 0 )
        {
            sendIsInvalid( "Invalid address: "+ nAddr );
            bOK = false;
        }

        String sSize = ((String) deviceInit.get( "type" )).toLowerCase();   // This is REQUIRED

        if( ! "boolean,float,long,int".contains( sSize ) )
        {
            sendIsInvalid( "Invalid type: "+ nAddr +". Must be: 'boolean' or 'int' or 'long' or 'float'" );
            bOK = false;
        }

        String sOrder = ((String) deviceInit.getOrDefault( "order", "ABCD" )).toUpperCase();

        for( int n = 0; n < sOrder.length(); n++ )
        {
            if( (sOrder.charAt( n ) < 'A') || (sOrder.charAt( n ) > 'D') )
            {
                sendGenericError( ILogger.Level.SEVERE, "Invalid Order letter: "+ sOrder.charAt( n ) +". Must be: A,B,C,D" );
                bOK = false;
            }
        }

        int nTimeout = ((Number) deviceInit.getOrDefault( KEY_TIMEOUT, 4000f )).intValue();
            setBetween( KEY_TIMEOUT, 50, nTimeout, Integer.MAX_VALUE );

        if( ! bOK )
            return;    // Client is null

        setValid( true );

        int nInterval = ((Number) deviceInit.getOrDefault( "interval", 5000f )).intValue();   // Default is 5s
            setBetween( KEY_INTERVAL, 30, nInterval, Integer.MAX_VALUE );           // I've estimated 30ms could be a good minimum interval for this old protocol

        client = new ModbusTcpClient4J2mod( sHost, nPort, nAddr, sSize, sOrder, nInterval, nTimeout, new MyListener() );
        set( deviceInit );
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );

        client.open();
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
        if( isFaked || isInvalid() || (client == null) )
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
        if( isFaked || isInvalid() || (client == null) )
            return;

        if( newValue instanceof String )
            newValue = UtilType.toUne( newValue.toString() );

        try
        {
            sendReaded( client.write( newValue ) );
        }
        catch( Exception exc )
        {
            sendGenericError( ILogger.Level.SEVERE, "Modbus error: "+ exc.getMessage() );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Modbus Listener
    //------------------------------------------------------------------------//
    private final class MyListener implements IModbusClient.Listener
    {
        @Override
        public void onMessage( Object msg )
        {
            sendReaded( msg );
        }

        @Override
        public void onError( Exception exc )
        {
            sendGenericError( ILogger.Level.SEVERE, exc.getMessage() );
        }
    }
}