package com.peyrona.mingle.network.websocket;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.network.BaseServer4IP;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.websockets.core.WebSocketChannel;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.xnio.Options;

/**
 * WebSocket server supporting both plain and SSL connections.
 * This class is a concrete implementation of a Mingle network server that uses
 * the WebSocket protocol, leveraging the Undertow library for handling the
 * underlying connections.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class WebSocketServer
             extends BaseServer4IP
{
    private       ServerThread    server = null;
    private       ExecutorService exec   = null;
    private final Object          locker = new Object();
    private final AtomicBoolean   isStopping = new AtomicBoolean(false);
    private       String          path;

    //------------------------------------------------------------------------//

    /**
     * Starts the WebSocket server with the specified configuration.
     * The configuration is provided as a JSON string, which can include
     * settings for the host, port, path, and SSL.
     *
     * @param sCfgAsJSON A JSON string containing the server configuration.
     * @return The server instance.
     * @throws MingleException if the server is already running or fails to start.
     */
    @Override
    public INetServer start( String sCfgAsJSON )
    {
        synchronized( locker )
        {
            if( isRunning() )
                return this;

            if( ! init( sCfgAsJSON, UtilComm.MINGLE_DEFAULT_WEBSOCKET_PORT ) )
                return this;

            startServer();
        }

        return this;
    }

    /**
     * Stops the WebSocket server gracefully.
     * This method closes all active client connections and shuts down the
     * underlying Undertow server.
     *
     * @return The server instance.
     */
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

    /**
     * Broadcasts a message to all connected clients.
     *
     * @param message The message to be sent.
     * @return The server instance.
     * @throws IllegalStateException if the server is not running.
     */
    @Override
    public INetServer broadcast( String message )
    {
        if( ! isRunning() )
            forEachListener(l -> l.onError(WebSocketServer.this, null, new MingleException( "Attempting to use a closed server-socket" ) ) );

        if( server != null )
            server.broadcast( message );

        return this;
    }

    /**
     * Checks if the server has any active client connections.
     *
     * @return true if there is at least one connected client, false otherwise.
     */
    @Override
    public boolean hasClients()
    {
        return ((server != null) && (! server.lstClients.isEmpty()));
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    @Override
    protected boolean init( String sCfgAsJSON, int nDefPort )
    {
        if( ! super.init( sCfgAsJSON, nDefPort ) )
            return false;

        UtilJson uj = new UtilJson( (sCfgAsJSON == null || sCfgAsJSON.isEmpty()) ? "{}" : sCfgAsJSON );
        path = uj.getString( "path", "/" );

        return true;
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
                forEachListener(l -> l.onError(WebSocketServer.this, null, me ) );    // Last to in case it throws an exception
            }
        }

        if( server != null )
            server.clean();

        exec   = null;
        server = null;
    }

    private void startServer()
    {
        try
        {
            server = new ServerThread();
            exec   = Executors.newCachedThreadPool();
            exec.submit( server );

            setRunning( true );
        }
        catch( Exception e )
        {
            MingleException me = new MingleException( "Failed to start server", e );

            cleanup();
            log( me );
            throw me;
        }
    }

    private SSLContext createSSLContext() throws Exception
    {
        if( getSSLCert() == null )
            throw new MingleException( "SSL certificate file is required for SSL server" );

        KeyStore keyStore = KeyStore.getInstance( "PKCS12" );
        char[] password = getSSLPassword();

        try( InputStream is = new FileInputStream( getSSLCert() ) )
        {
            keyStore.load( is, password );
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm() );
        keyManagerFactory.init( keyStore, password );

        SSLContext sslContext = SSLContext.getInstance( "TLS" );
        sslContext.init(
                keyManagerFactory.getKeyManagers(),
                null,
                null
        );

        return sslContext;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Server Thread
    //------------------------------------------------------------------------//
    private class ServerThread extends Thread
    {
        protected final Undertow server;
        private   final Set<WebSocketClient> lstClients = ConcurrentHashMap.newKeySet();
        private   final ScheduledExecutorService maintenanceExecutor = Executors.newSingleThreadScheduledExecutor();

        //------------------------------------------------------------------------//

        ServerThread() throws IOException
        {
            super( WebSocketServer.class.getSimpleName() +":Server" );

            log( String.format( "Starting WebSocket server on %s:%d%s (SSL: %s)",
                                getHost(), getPort(), path, getSSLCert() != null ) );

            Undertow.Builder builder = Undertow.builder()
                    .setServerOption( Options.BACKLOG, 10000 )
                    .setHandler( new PathHandler()
                            .addPrefixPath( path, Handlers.websocket(
                                    ( exchange, channel ) ->
                                    {
                                        handleNewConnection( channel );
                                    }
                            ) ) );

            if( getSSLCert() != null )
            {
                try
                {
                    SSLContext sslContext = createSSLContext();
                    builder.addHttpsListener( getPort(), getHost(), sslContext );
                }
                catch( Exception e )
                {
                    throw new IOException( "Failed to create SSL context", e );
                }
            }
            else
            {
                builder.addHttpListener( getPort(), getHost() );
            }

            this.server = builder.build();

            // Schedule periodic maintenance task
            maintenanceExecutor.scheduleAtFixedRate( this::performMaintenance, 30, 30, TimeUnit.SECONDS );
        }

        //------------------------------------------------------------------------//

        @Override
        public void run()
        {
            try
            {
                server.start();
                log( "WebSocket server started successfully" );
            }
            catch( Exception e )
            {
                MingleException me = new MingleException( "Failed to start WebSocket server", e );
                log( me );
                forEachListener( l -> l.onError( WebSocketServer.this, null, me ) );
            }
        }

        //------------------------------------------------------------------------//

        void broadcast(String message)
        {
            // First cleanup any disconnected clients
            cleanupDisconnectedClients();

            if( lstClients.isEmpty() )
                return;

            // Create a snapshot of clients to avoid concurrent modification
            List<WebSocketClient>         clientSnapshot = new ArrayList<>( lstClients );
            List<CompletableFuture<Void>> futures        = new ArrayList<>();
            List<WebSocketClient>         failedClients  = new ArrayList<>();

            for( WebSocketClient client : clientSnapshot )
            {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                {
                    try
                    {
                        if( client.isConnected() )
                        {
                            client.send( message );
                        }
                        else
                        {
                            synchronized( failedClients )
                            {
                                failedClients.add( client );
                            }
                        }
                    }
                    catch( Exception e )
                    {
                        forEachListener( l -> l.onError( WebSocketServer.this, client, e ) );

                        synchronized( failedClients )
                        {
                            failedClients.add( client );
                        }
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
                forEachListener( l -> l.onError( WebSocketServer.this, null, e ) );
            }

            // Remove failed clients after all async operations complete
            if( ! failedClients.isEmpty() )
            {
                for( WebSocketClient client : failedClients )
                {
                    removeClient( client );
                }
            }
        }

        void clean()
        {
            maintenanceExecutor.shutdownNow();

            List<WebSocketClient> clientsToClose = new ArrayList<>( lstClients );

            for( WebSocketClient client : clientsToClose )
            {
                forEachListener( l -> l.onDisconnected( WebSocketServer.this, client ) );

                if( client.isConnected() )
                    client.disconnect();
            }

            lstClients.clear();

            if( server != null )
            {
                try
                {
                    server.stop();
                }
                catch( Exception e )
                {
                    // Ignore stop errors
                }
            }
        }

        //------------------------------------------------------------------------//

        private void handleNewConnection( WebSocketChannel channel )
        {
            AtomicReference<WebSocketClient> clientRef = new AtomicReference<>( null );

            try
            {
                if( isAllowed( channel.getSourceAddress().getAddress() ) )
                {
                    WebSocketClient newClient = new WebSocketClient();
                                      newClient.add( new ClientListener() );

                    if( newClient.connect( channel ) )
                    {
                        clientRef.set( newClient );
                        lstClients.add( newClient );
                        forEachListener( l -> l.onConnected( WebSocketServer.this, newClient ) );
                    }
                }
                else
                {
                    MingleException me = new MingleException( channel.getSourceAddress().getAddress() + ": address not allowed" );

                    forEachListener( l -> l.onError( WebSocketServer.this, clientRef.get(), me ) );
                    closeChannel( channel );
                }
            }
            catch( Exception exc )
            {
                if( ! isStopping.get() )
                    handleConnectionError( clientRef.get(), channel, exc );
            }
        }

        private void handleConnectionError( WebSocketClient client, WebSocketChannel channel, Exception exc )
        {
            if( client != null )
                client.disconnect();

            forEachListener( l -> l.onError( WebSocketServer.this, client, exc ) );
            closeChannel( channel );
        }

        private void removeClient( WebSocketClient client )
        {
            if( lstClients.remove( client ) )
                forEachListener( l -> l.onDisconnected( WebSocketServer.this, client ) );
        }

        private void closeChannel( WebSocketChannel channel )
        {
            if( channel != null )
            {
                try
                {
                    channel.sendClose();
                }
                catch( Exception e )
                {
                    // Ignore close errors
                }
            }
        }

        private void cleanupDisconnectedClients()
        {
            lstClients.removeIf( client -> ! client.isConnected() );
        }

        private void performMaintenance()
        {
            List<WebSocketClient> disconnectedClients = new ArrayList<>();

            for( WebSocketClient client : lstClients )
            {
                if( ! client.isConnected() )
                    disconnectedClients.add( client );
            }

            for( WebSocketClient client : disconnectedClients )
                removeClient( client );
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
            ServerThread currentServer = server;

            if( currentServer != null )
            {
                currentServer.lstClients.remove( origin );
                forEachListener( l -> l.onDisconnected( WebSocketServer.this, origin ) );
            }
        }

        @Override
        public void onMessage( INetClient origin, String msg )
        {
            forEachListener( l -> l.onMessage( WebSocketServer.this, origin, msg ) );
        }

        @Override
        public void onError(INetClient origin, Exception exc)
        {
            ServerThread currentServer = server;

            if( currentServer != null )
                currentServer.removeClient( (WebSocketClient) origin );
        }
    }
}