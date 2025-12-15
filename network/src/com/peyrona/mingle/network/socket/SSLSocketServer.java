
package com.peyrona.mingle.network.socket;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.network.BaseServer4IP;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
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
    @Override
    public synchronized INetServer start( String sCfgAsJSON )
    {
        if( isRunning() )
            return this;

        super.start( sCfgAsJSON );

        CountDownLatch             latch    = new CountDownLatch( 1 );
        AtomicReference<Exception> errorRef = new AtomicReference<>( null );

        return super.run( new ServerThread( latch, errorRef ), latch, errorRef );
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    @Override
    public int getDefaultPort()
    {
        return UtilComm.MINGLE_DEFAULT_SOCKET_PORT_SSL;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Server Socket Thread
    //------------------------------------------------------------------------//

    private final class ServerThread implements Runnable
    {
        private final CountDownLatch             latch;
        private final AtomicReference<Exception> errRef;

        //------------------------------------------------------------------------//

        ServerThread( CountDownLatch latch, AtomicReference<Exception> errRef )
        {
            this.latch  = latch;
            this.errRef = errRef;
        }

        //------------------------------------------------------------------------//

        @Override
        public void run()
        {
            try
            {
                SSLServerSocketFactory ssf = createSSLContext().getServerSocketFactory();

                try( ServerSocket ss = createServerSocket( getPort(), ssf ) )
                {
                    ss.setReuseAddress(true);
                    latch.countDown();

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
                }
            }
            catch( Exception e )

            {
                errRef.set( e );
                latch.countDown();
            }
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
                         newClient.add( newDefaultClientListener() );

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