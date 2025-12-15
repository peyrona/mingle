
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
        return UtilComm.MINGLE_DEFAULT_SOCKET_PORT;
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
            try( ServerSocket ss = new ServerSocket( getPort() ) )
            {
                ss.setReuseAddress(true);

                latch.countDown();

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
                            client.add( newDefaultClientListener() );

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
            }
            catch( Exception e )
            {
                errRef.set( e );
                latch.countDown();
            }
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