package com.peyrona.mingle.network.websocket;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.slf4j.SLF4JBridge;
import com.peyrona.mingle.network.BaseServer4IP;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;

/**
 * WebSocket server.
 *
 * @author Francisco José Morero Peyrona
 */
public final class WebSocketServer
             extends BaseServer4IP
{
    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public WebSocketServer()
    {
        if( UtilSys.getLogger() == null )
            UtilSys.setLogger( "websocket-server", UtilSys.getConfig() );

        SLF4JBridge.setup( UtilSys.getLogger(), ILogger.Level.INFO);
    }

    //------------------------------------------------------------------------//

    @Override
    public synchronized INetServer start( String sCfgAsJSON )
    {
        if( isRunning() )
            return this;

        super.start( sCfgAsJSON );

        // 1. Create synchronization objects
        CountDownLatch             latch    = new CountDownLatch( 1 );
        AtomicReference<Exception> errorRef = new AtomicReference<>( null );

        // 2. Create the runner (Pass the latch/errorRef to it)
        // Note: The runner MUST call latch.countDown() when onStart() fires!
        ServerRunner runner = new ServerRunner( latch, errorRef );

        // 3. Delegate execution and waiting to BaseServer4IP
        return super.run( runner, latch, errorRef );
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    @Override
    public int getDefaultPort()
    {
        return UtilComm.MINGLE_DEFAULT_WEBSOCKET_PORT;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS: Mingle WebSocket Server
    //------------------------------------------------------------------------//
    private final class MingleWebSocketServer extends org.java_websocket.server.WebSocketServer
    {
        // Map raw WebSocket -> Our Wrapper
        private final Map<WebSocket, WebSocketClient> connMap = new ConcurrentHashMap<>();
        private final CountDownLatch startupLatch;

        MingleWebSocketServer( InetSocketAddress address, CountDownLatch latch )
        {
            super( address );
            this.startupLatch = latch;
        }

        /**
         * Explicitly closes all underlying sockets.
         * This triggers onClose() for each, ensuring proper cleanup.
         */
        void closeAllConnections()
        {
            for( WebSocket ws : connMap.keySet() )
                if( ws.isOpen() )
                    ws.close();
        }

        @Override
        public void onStart()
        {
            if( startupLatch != null )
                startupLatch.countDown();

            log( Level.INFO, "WebSocket server started successfully" );
        }

        @Override
        public void onOpen( WebSocket conn, ClientHandshake handshake )
        {
            if( isStopping.get() )
            {
                conn.close();
                return;
            }

            try
            {
                if( isAllowed( conn.getRemoteSocketAddress().getAddress() ) )
                {
                    WebSocketClient client = new WebSocketClient();
                    client.add( newDefaultClientListener() ); // Hook into BaseServer listeners

                    if( client.connect( conn ) )
                    {
                        connMap.put( conn, client );
                        // Add to BaseServer's generic client list
                        add( client );
                    }
                    else
                    {
                        conn.close();
                    }
                }
                else
                {
                    log( Level.WARNING, "Connection denied: " + conn.getRemoteSocketAddress() );
                    conn.close();
                }
            }
            catch( Exception exc )
            {
                log( "Error handling new connection", exc );
                conn.close();
            }
        }

        @Override
        public void onClose( WebSocket conn, int code, String reason, boolean remote )
        {
            // Remove from our local map
            WebSocketClient client = connMap.remove( conn );

            if( client != null )
            {
                // 1. Notify wrapper to clean up resources (listeners, etc.)
                client.dispatchClose();

                // 2. Ensure removal from BaseServer's generic list
                // (Assuming BaseServer has a method for this, or newDefaultClientListener handles it via onDisconnected)
                // If BaseServer.newDefaultClientListener() handles removal on 'onDisconnected', we are good.
                // If not, we might need: remove(client);
            }
        }

        @Override
        public void onMessage( WebSocket conn, String message )
        {
            WebSocketClient client = connMap.get( conn );
            if( client != null )
            {
                client.dispatchMessage( message );
            }
        }

        @Override
        public void onError( WebSocket conn, Exception ex )
        {
            if( isStopping.get() )
                return;

            if( conn != null )
            {
                WebSocketClient client = connMap.get( conn );

                if( client != null )
                {
                    client.dispatchError( ex );
                    return;
                }
            }

            log( "WebSocket server error", ex );
        }
    }

    //------------------------------------------------------------------------//
    // Server Runner
    //------------------------------------------------------------------------//
    private final class ServerRunner implements Runnable
    {
        private final CountDownLatch             latch;
        private final AtomicReference<Exception> errRef;

        ServerRunner( CountDownLatch startupLatch, AtomicReference<Exception> errorRef )
        {
            this.latch  = startupLatch;
            this.errRef = errorRef;
        }

        @Override
        public void run()
        {
            try
            {
                InetSocketAddress     address = new InetSocketAddress( getHost(), getPort() );
                MingleWebSocketServer server  = new MingleWebSocketServer( address, latch );

                if( getSSLCert() != null )
                {
                    // createSSLContext() could throw or return null in some implementations
                    // assuming BaseServer4IP handles the throw, here we just pass context.
                    server.setWebSocketFactory( new DefaultSSLWebSocketServerFactory( createSSLContext() ) );
                }

                server.start();
            }
            catch( Exception e )
            {
                errRef.set( e );

                // Ensure main thread doesn't hang
                if( latch != null )
                    latch.countDown();
            }
        }
    }
}