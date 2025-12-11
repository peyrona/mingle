package com.peyrona.mingle.network.websocket;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.network.BaseClient4IP;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.ssl.JsseXnioSsl;

/**
 * This class exposes a WebSocket service (port is configurable) that can be used
 * to communicate with the ExEn and is used by the ExEn to communicate back to
 * the clients.
 * <p>
 * It is mainly used to send requests to the ExEn or to retrieve the status of
 * a device.
 * <p>
 * Used protocol is WebSocket with text messages.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class WebSocketClient
             extends BaseClient4IP
{
    private static final int  MAX_RETRIES  = 3;
    private static final long RETRY_DELAY  = 1000L;

    private volatile WebSocketChannel channel    = null;
    private volatile XnioWorker       worker     = null;
    private volatile AtomicBoolean    isStopping = new AtomicBoolean(false);


    //------------------------------------------------------------------------//

    /**
     * Connects to WebSocket server using received JSON string configuration.
     *
     * @param sCfgAsJSON
     * @return Itself
     */
    @Override
    public INetClient connect( String sCfgAsJSON )
    {
        if( isConnected() )
            return this;

        if( ! init( sCfgAsJSON, UtilComm.MINGLE_DEFAULT_WEBSOCKET_PORT ) )
            return this;

        try
        {
            // The XnioWorker is created once for all retry attempts
            worker = Xnio.getInstance().createWorker( OptionMap.EMPTY );
        }
        catch( Exception e )
        {
            sendError( e );
            return this;
        }

        for( int attempt = 0; attempt < MAX_RETRIES; attempt++ )
        {
            try
            {
                String scheme = ( isSSLEnabled() ? "wss://" : "ws://" );

                URI uri = URI.create( scheme + getHost() + ":" + getPort() );

                io.undertow.websockets.client.WebSocketClient.ConnectionBuilder builder =
                    io.undertow.websockets.client.WebSocketClient.connectionBuilder( worker,
                                                                                     new DefaultByteBufferPool( false, 1024, 1024, 0 ),
                                                                                     uri );

                if( isSSLEnabled() )    // Wrap the standard SSLContext into an XnioSsl instance
                    builder.setSsl( new JsseXnioSsl( worker.getXnio(), OptionMap.EMPTY, createSSLContext() ) );

                IoFuture<WebSocketChannel> future = builder.connect();

                // Synchronously wait for the connection to complete or time out
                if( future.await( getTimeout(), TimeUnit.MILLISECONDS ) != IoFuture.Status.DONE )
                {
                    future.cancel(); // Cancel the pending attempt
                    throw new IOException( "Connection timed out after " + getTimeout() + "ms" );
                }

                // If we get here, the future is DONE. Check if it was successful.
                this.channel = future.get(); // This will throw an exception if the connection failed
                setupChannel( this.channel );

                forEachListener( l -> l.onConnected( this ) );

                return this;    // Success! Exit the method.
            }
            catch( Exception e )
            {
                if( attempt == MAX_RETRIES - 1 ) // This was the last attempt
                {
                    log( "Connection with server failed", e );
                    sendError( e ); // Report the final error
                    break;          // Exit loop to perform final cleanup
                }
                else
                {
                    log( Level.INFO, "Connection attempt " + (attempt + 1) + " failed: " + e.getMessage() );
                }

                try
                {
                    Thread.sleep( RETRY_DELAY );
                }
                catch( InterruptedException ie )
                {
                    Thread.currentThread().interrupt();
                    sendError(new MingleException("Connection retry interrupted", ie));
                    break; // Exit loop if interrupted
                }
            }
        }

        // If we exit the loop, all retries have failed or were interrupted.
        // Now, we perform a full cleanup, including shutting down the worker.
        cleanupResources();
        return this;
    }

    @Override
    public synchronized INetClient disconnect()
    {
        isStopping.set( true );
        cleanupResources();
        isStopping.set( false );
        return this;
    }

    @Override
    public synchronized INetClient send( String message )
    {
        if( message != null && channel != null && channel.isOpen() )
        {
            try
            {
                WebSockets.sendText( message, channel, null );
            }
            catch( Exception e )
            {
                sendError( e );
            }
        }

        return this;
    }

    @Override
    public boolean isConnected()
    {
        return (channel != null) && channel.isOpen();
    }

    @Override
    public String toString()
    {
        return super.toString() +", RemoteAddress="+ ((channel == null) ? "unknown" : channel.getPeerAddress());
    }

    // hashCode() and equals(...)  are defined at super (parent class)

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    /**
     * This method was created by needs of WebSocketServer.
     * @param webSocketChannel
     * @return
     */
    synchronized boolean connect( WebSocketChannel webSocketChannel )
    {
        if( isStopping.get() )
            return false;

        try
        {
            channel = webSocketChannel;
            setupChannel( channel );
            forEachListener(l -> ((INetClient.IListener) l).onConnected( WebSocketClient.this ) );
            return true;
        }
        catch( Exception e )
        {
            cleanupResources();
            sendError( e );
            return false;
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void setupChannel( WebSocketChannel channel )
    {
        channel.getReceiveSetter().set( new AbstractReceiveListener()
            {
                @Override
                protected void onFullTextMessage( WebSocketChannel channel, BufferedTextMessage message )
                {
                    try
                    {
                        String data = message.getData();
                        forEachListener(l -> l.onMessage( WebSocketClient.this, data ) );
                    }
                    catch( Exception e )
                    {
                        if( ! isStopping.get() )
                            sendError( e );
                    }
                }

                @Override
                protected void onClose( WebSocketChannel channel, StreamSourceFrameChannel sourceChannel )
                {
                    if( ! isStopping.get() )
                        cleanupResources();
                }

                @Override
                protected void onError( WebSocketChannel channel, Throwable error )
                {
                    if( ! isStopping.get() )
                    {
                        Exception exception = (error instanceof Exception) ? (Exception) error : new Exception( error );
                        log( exception );
                        sendError( exception );
                    }
                }
            } );

        channel.resumeReceives();
    }

    private void cleanupResources()
    {
        if( channel != null )
        {
            try
            {
                IoUtils.safeClose( channel );
            }
            catch( Exception e )
            {
                log( "Error closing WebSocket channel: ", e);
            }
            finally
            {
                forEachListener( l -> ((INetClient.IListener) l).onDisconnected( WebSocketClient.this ) );
            }
        }

        if( worker != null )
        {
            try
            {
                worker.shutdownNow();

                if( ! worker.awaitTermination( 10, TimeUnit.SECONDS ) )
                {
                    log( Level.INFO, "Worker did not terminate gracefully, forcing cleanup." );
                    worker = null;   // Force cleanup by setting to null
                }
            }
            catch( InterruptedException e )
            {
                Thread.currentThread().interrupt();
                log( Level.INFO, "Shutdown did not terminate gracefully" );
            }
        }

        clearListenersList();

        synchronized( this )
        {
            channel = null;
            worker = null;
        }
    }
}