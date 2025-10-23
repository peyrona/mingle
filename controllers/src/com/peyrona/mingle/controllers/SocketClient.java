
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.io.IOException;
import java.util.Map;

/**
 * A controller to manage client plain-old Sockets and WebSockets.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class SocketClient
       extends ControllerBase
{
    private INetClient client = null;
    private boolean    bWS;
    private String     sHost;
    private int        nPort;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        boolean bOK   = true;
        String  sURL  = (String) deviceInit.get( "url" );    // This is REQUIRED

        sHost = UtilComm.getHost( sURL );
        nPort = UtilComm.getPort( sURL, UtilComm.MINGLE_DEFAULT_SOCKET_PORT );

        if( UtilStr.isEmpty( sHost ) )
        {
            sendIsInvalid( "URL is empty" );
            bOK = false;
        }

        if( nPort < 1 || nPort > 65535 )
        {
            sendIsInvalid( "Invalid port = "+ nPort );
            bOK = false;
        }

        if( nPort <= 1024 )
            sendGenericError( ILogger.Level.WARNING, "It is not recommeded to use a port <= 1024. Using: "+ nPort );

        if( ! bOK )
            return;

        setValid( true );

        bWS = deviceInit.getOrDefault( "websocket", "false" ).equals( "true" );
    }

    @Override
    public void start( IRuntime rt )
    {
        if( ! isValid() )
            return;

        super.start( rt );

        if( bWS )
        {
            // TODO: implementar los WebSockets con la lib Undertow

//            NettyWebSocketClient wsc = new NettyWebSocketClient();
//                                 wsc.add( new MyListener() );
//                                 wsc.connect( "{\"host\":"+ sHost +", \"port\":"+ nPort +'}' );
//            client = wsc;
            System.out.println( "WebSocket -> Option not yet implemeted" );
            System.exit( 1 );
        }
        else
        {
            com.peyrona.mingle.network.plain.SocketClient psc = new com.peyrona.mingle.network.plain.SocketClient();
                              psc.add( new MyListener() );
                              psc.connect( "{\"host\":"+ sHost +", \"port\":"+ nPort +'}' );
            client = psc;
        }
    }

    @Override
    public void stop()
    {
        if( client != null )
        {
            client.disconnect();
            client = null;
        }

        super.stop();
    }

    @Override
    public void read()
    {
        // This method is empty, because the interface "INetClient" does not have a "read()" method.
        // And it does not have it, because reads are done in aseparated Thread using a Listener.
    }

    @Override
    public void write( Object value )
    {
        if( isFaked || isInvalid() )
            return;

        if( ! client.isConnected() )
        {
            sendWriteError( value, new IOException( "Not connected" ) );
            return;
        }

        if( value != null )
            client.send( value.toString() );

        sendReaded( value );
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private class MyListener implements INetClient.IListener
    {
        @Override
        public void onConnected( INetClient origin )
        {
        }

        @Override
        public void onDisconnected( INetClient origin )
        {
        }

        @Override
        public void onMessage( INetClient origin, String msg )
        {
            sendReaded( msg );
        }

        @Override
        public void onError( INetClient origin, Exception exc )
        {
            sendGenericError( ILogger.Level.SEVERE, exc.getMessage() );
        }
    }
}