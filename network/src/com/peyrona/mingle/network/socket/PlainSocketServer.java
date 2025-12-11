
package com.peyrona.mingle.network.socket;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.network.BaseServer4IP;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plain socket server implementation for non-secure communication.
 * This class provides basic socket functionality without SSL/TLS encryption.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class PlainSocketServer
      extends BaseServer4IP
{
    private       ServerThread    server = null;
    private       ScheduledFuture future = null;
    private final AtomicBoolean   isStopping = new AtomicBoolean(false);

    //------------------------------------------------------------------------//

    @Override
    public synchronized INetServer start( String sCfgAsJSON )
    {
        if( isRunning() )
            return this;

        if( ! init( sCfgAsJSON, UtilComm.MINGLE_DEFAULT_SOCKET_PORT ) )
            return this;

        startServerPlain();

        return super.start( sCfgAsJSON );
    }

    @Override
    public synchronized INetServer stop()
    {
        isStopping.set( true );
        super.stop();

        if( future != null )
            future.cancel( true );

        future = null;
        server = null;

        isStopping.set( false );

        return this;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void startServerPlain()
    {
        try
        {
            future = UtilSys.execute( getClass().getSimpleName() +":server", new ServerThread( getPort() ) );
        }
        catch( Exception exc )
        {
            String msg = "Failed to start server";

            if( exc instanceof BindException )
                msg += ": apparently the port "+ getPort() +" is already in use.";

            MingleException me = new MingleException( msg, exc );

            log( me );
            notifyError( (INetClient) null, me );
            throw me;
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Server Socket Thread
    //------------------------------------------------------------------------//
    private final class ServerThread extends Thread
    {
        private final ServerSocket ss;

        //------------------------------------------------------------------------//

        ServerThread( int nPort ) throws IOException
        {
            super( PlainSocketServer.class.getSimpleName() +":Server" );

            ss = createServerSocket( nPort );
            ss.setReuseAddress(true);
         // ss.setSoTimeout( SERVER_TIMEOUT ); --> Can not use it because a client can last days in attempting to connect
        }

        protected ServerSocket createServerSocket( int nPort ) throws IOException
        {
            return new ServerSocket( nPort );
        }

        //------------------------------------------------------------------------//

        @Override
        public void run()
        {
            while( (! isStopping.get()) && (! Thread.currentThread().isInterrupted()) )
            {
                Socket       socket = null;
                SocketClient client = null;

                try
                {
                    socket = ss.accept();

                    if( isAllowed( socket.getInetAddress() ) )
                    {
                        client = new SocketClient();
                        client.add( newClientListener() );

                        if( client.connect( socket ) )
                            add( client );
                    }
                    else
                    {
                        MingleException me = new MingleException( socket.getInetAddress() + ": address not allowed" );

                        notifyError( client, me );
                        closeSocket( socket );
                    }
                }
                catch( SocketTimeoutException ste )
                {
                    // Normal timeout, continue listening
                }
                catch( IOException exc )
                {
                    if( ! isStopping.get() )
                        handleConnectionError( client, socket, exc );
                }
            }

            cleanupClients( true );
        }

        //------------------------------------------------------------------------//

        private void handleConnectionError( SocketClient client, Socket socket, Exception exc )
        {
            if( client != null )
                client.disconnect();

            del( client );
            notifyError( client, exc );
            closeSocket( socket );
        }

        private void closeSocket( Socket socket )
        {
            if( socket != null )
            {
                try
                {
                    socket.close();
                }
                catch( IOException ioe )
                {
                    // Ignore close errors
                }
            }
        }
    }
}