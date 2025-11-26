
package com.peyrona.mingle.network.socket;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.network.BaseServer4IP;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
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
    private       ServerThread    server = null;
    private       ExecutorService exec   = null;
    private final Object          locker = new Object();
    private final AtomicBoolean   isStopping = new AtomicBoolean(false);

    //------------------------------------------------------------------------//

    @Override
    public INetServer start( String sCfgAsJSON )
    {
        synchronized( locker )
        {
            if( isRunning() )
                return this;

            if( ! init( sCfgAsJSON, UtilComm.MINGLE_DEFAULT_SOCKET_PORT_SSL ) )
                return this;

            if( getSSLCert() == null || getSSLKey() == null )
                throw new MingleException( "SSL certificate and key files are required for SSL server" );

            startServerSSL();
        }

        return this;
    }

    @Override
    public INetServer stop()
    {
        synchronized( locker )
        {
            isStopping.set( true );
            cleanup();
            setRunning( false );
            isStopping.set( false );
        }

        return this;
    }

    @Override
    public INetServer broadcast( String message )
    {
        if( ! isRunning() )
            forEachListener( l -> l.onError( SSLSocketServer.this, null, new MingleException( "Attempting to use a closed server-socket" ) ) );

        if( server != null )
            server.broadcast( message );

        return this;
    }

    @Override
    public boolean hasClients()
    {
        return ((server != null) && (! server.lstClients.isEmpty()));
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void cleanup()
    {
        if( exec != null )
        {
            exec.shutdownNow();

            try
            {
                exec.awaitTermination( 5, TimeUnit.SECONDS );
            }
            catch( InterruptedException e )
            {
                MingleException me = new MingleException( "Attempting to use a closed server-socket" );

                log( me );
                Thread.currentThread().interrupt();
                forEachListener( l -> l.onError( SSLSocketServer.this, null, me ) );
            }
        }

        if( server != null )
            server.clean();

        exec   = null;
        server = null;
    }

    private void startServerSSL()
    {
        try
        {
            SSLContext sslContext = createSSLContext();
            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();

            server = new ServerThread(getPort(), ssf);
            exec   = Executors.newCachedThreadPool();
            exec.submit(server);

            setRunning(true);
        }
        catch( IOException | RejectedExecutionException | GeneralSecurityException e )
        {
            MingleException me = new MingleException( "Failed to start SSL server", e );

            cleanup();
            log( me );
            throw me;
        }
    }

    private SSLContext createSSLContext() throws GeneralSecurityException, IOException
    {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password   = getSSLPassword();

        try( FileInputStream fis = new FileInputStream( getSSLCert() ) )
        {
            keyStore.load( fis, password );
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
        kmf.init( keyStore, password );

        sslContext.init( kmf.getKeyManagers(), null, null );
        return sslContext;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Server Socket Thread
    //------------------------------------------------------------------------//
    private class ServerThread extends Thread
    {
        protected final ServerSocket ss;
        private   final Queue<SocketClient> lstClients = new ConcurrentLinkedQueue<>();
        private   final ScheduledExecutorService maintenanceExecutor = Executors.newSingleThreadScheduledExecutor();

        //------------------------------------------------------------------------//

        ServerThread( int nPort, SSLServerSocketFactory sslSocketFactory ) throws IOException
        {
            super( SSLSocketServer.class.getSimpleName() +":Server" );

            ss = createServerSocket( nPort, sslSocketFactory );
            ss.setReuseAddress(true);

            maintenanceExecutor.scheduleAtFixedRate( this::performMaintenance, 30, 30, TimeUnit.SECONDS );
        }

        protected ServerSocket createServerSocket( int nPort, SSLServerSocketFactory sslSocketFactory ) throws IOException
        {
            SSLServerSocket sslServerSocket = (SSLServerSocket) sslSocketFactory.createServerSocket(nPort);
            sslServerSocket.setReuseAddress(true);
            sslServerSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            return sslServerSocket;
        }

        //------------------------------------------------------------------------//

        @Override
        public void run()
        {
            while( (! isStopping.get()) && (! Thread.currentThread().isInterrupted()) )
            {
                Socket                             socket = null;
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

                        forEachListener( l -> l.onError( SSLSocketServer.this, client.get(), me ) );
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

            clean();
        }

        //------------------------------------------------------------------------//

        synchronized void broadcast(String message)
        {
            cleanupDisconnectedClients();

            if( lstClients.isEmpty() )
                return;

            Iterator<SocketClient>   iterator = lstClients.iterator();
            List<CompletableFuture<Void>> futures  = new ArrayList<>();

            while( iterator.hasNext() )
            {
                SocketClient client = iterator.next();

                CompletableFuture<Void> future = CompletableFuture.runAsync( () ->
                                                    {
                                                        try
                                                        {
                                                            client.send( message );
                                                        }
                                                        catch( Exception e )
                                                        {
                                                            forEachListener( l -> l.onError( SSLSocketServer.this, client, e ) );
                                                            iterator.remove();
                                                        }
                                                    }, exec );

                futures.add( future );
            }

            try
            {
                CompletableFuture.allOf( futures.toArray( CompletableFuture[]::new ) ).get( 5000, TimeUnit.MILLISECONDS );
            }
            catch( Exception e )
            {
                forEachListener( l -> l.onError( SSLSocketServer.this, null, e ) );
            }
        }

        void clean()
        {
            maintenanceExecutor.shutdownNow();

            for( Iterator<SocketClient> itera = lstClients.iterator(); itera.hasNext(); )
            {
                SocketClient client = itera.next();

                forEachListener( l -> l.onDisconnected( SSLSocketServer.this, client ) );

                if( client.isConnected() )
                    client.disconnect();

                itera.remove();
            }

            try
            {
                if( ! ss.isClosed() )
                    ss.close();
            }
            catch( IOException ioe )
            {
                // Ignore close errors
            }
        }

        //------------------------------------------------------------------------//

        private void handleNewConnection( Socket socket, AtomicReference<SocketClient> clientRef ) throws IOException
        {
            SocketClient newClient = new SocketClient();
                              newClient.add( new ClientListener() );

            if( newClient.connect( socket ) )
            {
                clientRef.set( newClient );
                lstClients.add( newClient );
                forEachListener( l -> l.onConnected( SSLSocketServer.this, newClient ) );
            }
        }

        private void handleConnectionError( SocketClient client, Socket socket, Exception exc )
        {
            if( client != null )
                client.disconnect();

            forEachListener( l -> l.onError( SSLSocketServer.this, client, exc ) );
            closeSocket( socket );
        }

        private void removeClient( SocketClient client )
        {
            lstClients.remove( client );
            forEachListener( l -> l.onDisconnected( SSLSocketServer.this, client ) );
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

        private void cleanupDisconnectedClients()
        {
            lstClients.removeIf( client -> {
                if( ! client.isConnected() )
                {
                    removeClient( client );
                    return true;
                }
                return false;
            });
        }

        private void performMaintenance()
        {
            cleanupDisconnectedClients();
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private final class ClientListener implements INetClient.IListener
    {
        @Override
        public void onConnected(INetClient origin)
        {
        }

        @Override
        public void onDisconnected(INetClient origin)
        {
        }

        @Override
        public void onMessage( INetClient origin, String msg )
        {
            forEachListener( l -> l.onMessage( SSLSocketServer.this, origin, msg ) );
        }

        @Override
        public void onError(INetClient origin, Exception exc)
        {
        }
    }
}