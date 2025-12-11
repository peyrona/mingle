
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
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * SSL-enabled socket server implementation.
 * This class provides secure socket communication using SSL/TLS.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class SSLSocketServer
      extends BaseServer4IP
{
    private       ScheduledFuture future = null;
    private final AtomicBoolean   isStopping = new AtomicBoolean( false );

    //------------------------------------------------------------------------//

    @Override
    public synchronized INetServer start( String sCfgAsJSON )
    {
        if( isRunning() )
            return this;

        if( ! init( sCfgAsJSON, UtilComm.MINGLE_DEFAULT_SOCKET_PORT_SSL ) )
            return this;

        if( getSSLCert() == null || getSSLKey() == null )
            throw new MingleException( "SSL certificate and key files are required for SSL server" );

        startServerSSL();

        return this;
    }

    @Override
    public INetServer stop()
    {
        isStopping.set( true );

        if( future != null )
            future.cancel( true );

        future = null;

        isStopping.set( false );

        return this;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void startServerSSL()
    {
        try
        {
            SSLServerSocketFactory ssf = createSSLContext().getServerSocketFactory();

            future = UtilSys.execute( getClass().getSimpleName() +":server", new ServerThread( getPort(), ssf ) );
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

        ServerThread( int nPort, SSLServerSocketFactory sslSocketFactory ) throws IOException
        {
            super( SSLSocketServer.class.getSimpleName() +":Server" );

            ss = createServerSocket( nPort, sslSocketFactory );
            ss.setReuseAddress(true);
        }

        //------------------------------------------------------------------------//

        @Override
        public void run()
        {
            while( (! isStopping.get()) && (! Thread.currentThread().isInterrupted()) )
            {
                Socket                        socket = null;
                AtomicReference<SocketClient> client = new AtomicReference<>( null );

                try
                {
                    socket = ss.accept();

                    if( isAllowed( socket.getInetAddress() ) )
                    {
                        handleNewConnection( socket, client );
                    }
                    else
                    {
                        MingleException me = new MingleException( socket.getInetAddress() + ": address not allowed" );

                        notifyError( client.get(), me );
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
                        handleConnectionError( client.get(), socket, exc );
                }
            }

            cleanupClients( true );
        }

        //------------------------------------------------------------------------//

        private ServerSocket createServerSocket( int nPort, SSLServerSocketFactory sslSocketFactory ) throws IOException
        {
            SSLServerSocket sslServerSocket = (SSLServerSocket) sslSocketFactory.createServerSocket(nPort);
            sslServerSocket.setReuseAddress(true);
            sslServerSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});

            return sslServerSocket;
        }

        private void handleNewConnection( Socket socket, AtomicReference<SocketClient> clientRef ) throws IOException
        {
            SocketClient newClient = new SocketClient();
                         newClient.add( newClientListener() );

            if( newClient.connect( socket ) )
            {
                clientRef.set( newClient );
                add( newClient );
                notifyConnected( newClient );
            }
        }

        private void handleConnectionError( SocketClient client, Socket socket, Exception exc )
        {
            if( client != null )
                client.disconnect();

            del( client );
            notifyError( client, exc );
            closeSocket( socket );
        }

        private void closeSocket(Socket socket)
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