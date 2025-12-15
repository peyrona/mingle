package com.peyrona.mingle.network.websocket;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.slf4j.SLF4JBridge;
import com.peyrona.mingle.network.BaseClient4IP;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Production-hardened WebSocket client.
 *
 * @author Francisco José Morero Peyrona
 */
public final class WebSocketClient extends BaseClient4IP
{
    private static final int  MAX_RETRIES  = 3;
    private static final long RETRY_DELAY  = 1000L;

    /** * The raw WebSocket interface.
     * Volatile ensures visibility across threads without always needing a lock for read access.
     */
    private volatile WebSocket      webSocket  = null;
    private volatile MingleWSClient wsClient   = null;
    private final    AtomicBoolean  isStopping = new AtomicBoolean( false );

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public WebSocketClient()
    {
        if( UtilSys.getLogger() == null )
            UtilSys.setLogger( "websocket-client", UtilSys.getConfig() );

        SLF4JBridge.setup( UtilSys.getLogger(), ILogger.Level.INFO);
    }

    //------------------------------------------------------------------------//

    @Override
    public INetClient connect( String sCfgAsJSON )
    {
        // Avoid re-connecting if already connected
        if( isConnected() ) return this;

        if( ! init( sCfgAsJSON, UtilComm.MINGLE_DEFAULT_WEBSOCKET_PORT ) )
            return this;

        for( int attempt = 0; attempt < MAX_RETRIES; attempt++ )
        {
            if( isStopping.get() )
                break;

            try
            {
                String scheme = ( isSSLEnabled() ? "wss://" : "ws://" );
                URI uri = URI.create( scheme + getHost() + ":" + getPort() );

                wsClient = new MingleWSClient( uri );

                if( isSSLEnabled() )
                {
                    SSLContext sslCtx = createSSLContext();
                    if( sslCtx == null )
                        throw new MingleException( "Failed to create SSL Context (returned null)" );

                    wsClient.setSocketFactory( sslCtx.getSocketFactory() );
                }

                // connectBlocking returns true if connection is successful
                if( wsClient.connectBlocking( getTimeout(), TimeUnit.MILLISECONDS ) )
                {
                    // Success: Assign to the interface reference
                    synchronized( this )
                    {
                        this.webSocket = wsClient;
                    }

                    // Notify listeners
                    forEachListener( l -> l.onConnected( this ) );
                    return this;
                }
                else
                {
                    throw new IOException( "Connection failed (timeout or refused)" );
                }
            }
            catch( Exception e )
            {
                // Clean up any partial state from this failed attempt
                cleanupResources();

                if( attempt == MAX_RETRIES - 1 ) // Last attempt
                {
                    log( "Final connection attempt failed", e );
                    sendError( e );
                    break;
                }
                else
                {
                    log( Level.INFO, "Connection attempt " + (attempt + 1) + " failed: " + e.getMessage() );
                }

                // Smart sleep: wait only if we aren't stopping
                if( ! isStopping.get() )
                {
                    try
                    {
                        Thread.sleep( RETRY_DELAY );
                    }
                    catch( InterruptedException ie )
                    {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return this;
    }

    @Override
    public INetClient disconnect()
    {
        isStopping.set( true );
        cleanupResources();
        isStopping.set( false );
        return this;
    }

    @Override
    public synchronized INetClient send( String message )
    {
        // Simple check before trying to send
        if( message != null && isConnected() )
        {
            try
            {
                webSocket.send( message );
            }
            catch( Exception e )
            {
                sendError( e );
                // If sending fails, we might consider the connection broken?
                // For now, just reporting the error is safer.
            }
        }
        return this;
    }

    @Override
    public boolean isConnected()
    {
        WebSocket ws = this.webSocket;
        return (ws != null) && ws.isOpen();
    }

    @Override
    public String toString()
    {
        WebSocket ws = this.webSocket;
        return super.toString() +", RemoteAddress="+ ((ws == null) ? "unknown" : ws.getRemoteSocketAddress());
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE - Methods used by WebSocketServer
    //------------------------------------------------------------------------//

    synchronized boolean connect( WebSocket webSocket )
    {
        if( isStopping.get() ) return false;

        try
        {
            this.webSocket = webSocket;
            forEachListener( l -> l.onConnected( WebSocketClient.this ) );
            return true;
        }
        catch( Exception e )
        {
            cleanupResources();
            sendError( e );
            return false;
        }
    }

    void dispatchMessage( String message )
    {
        if( isStopping.get() ) return;
        try
        {
            forEachListener( l -> l.onMessage( this, message ) );
        }
        catch( Exception e )
        {
            sendError( e );
        }
    }

    void dispatchClose()
    {
        if( ! isStopping.get() )
            cleanupResources();
    }

    void dispatchError( Exception ex )
    {
        if( ! isStopping.get() )
            sendError( ex );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE
    //------------------------------------------------------------------------//

    /**
     * Safely cleans up resources without causing deadlocks.
     * 1. Nullify references inside lock.
     * 2. Close physical sockets outside lock.
     * 3. Do NOT clear listeners (allows retry/reconnect).
     */
    private void cleanupResources()
    {
        WebSocket wsToClose;
        MingleWSClient clientToClose;

        // 1. Atomically detach resources
        synchronized( this )
        {
            wsToClose = this.webSocket;
            clientToClose = this.wsClient;

            this.webSocket = null;
            this.wsClient = null;
        }

        // 2. Notify disconnection
        // We do this before physical close to ensure app knows we are logically down.
        forEachListener( l -> l.onDisconnected( WebSocketClient.this ) );

        // 3. Physical cleanup (Outside synchronized block to prevent deadlocks)
        try
        {
            if( clientToClose != null )
            {
                clientToClose.close();
            }
            else if( wsToClose != null && wsToClose.isOpen() )
            {
                wsToClose.close();
            }
        }
        catch( Exception e )
        {
            log( "Error closing WebSocket: ", e );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------
    private final class MingleWSClient extends org.java_websocket.client.WebSocketClient
    {
        MingleWSClient( URI serverUri )
        {
            super( serverUri );
        }

        @Override
        public void onOpen( ServerHandshake handshake )
        {
            // Logic handled by connectBlocking
        }

        @Override
        public void onMessage( String message )
        {
            dispatchMessage( message );
        }

        @Override
        public void onClose( int code, String reason, boolean remote )
        {
            dispatchClose();
        }

        @Override
        public void onError( Exception ex )
        {
            dispatchError( ex );
        }
    }
}