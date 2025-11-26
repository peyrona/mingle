package com.peyrona.mingle.network.websocket;

import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.network.BaseClient4IP;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

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

    private volatile       WebSocketChannel channel = null;
    private volatile       XnioWorker       worker = null;
    private volatile       AtomicBoolean    isShuttingDown = new AtomicBoolean(false);
    private          final Object           connLock = new Object();

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
        synchronized( connLock )
        {
            if( isConnected() )
                return this;

            if( ! init( sCfgAsJSON, UtilComm.MINGLE_DEFAULT_WEBSOCKET_PORT ) )
                return this;

            for( int attempt = 0; attempt < MAX_RETRIES; attempt++ )
            {
                try
                {
                    Xnio xnio = Xnio.getInstance();
                    worker = xnio.createWorker( null );

                    String wsUri = "ws://" + getHost() + ":" + getPort();
                    URI uri = URI.create( wsUri );

                    CountDownLatch latch = new CountDownLatch(1);
                    AtomicBoolean connected = new AtomicBoolean(false);

                    io.undertow.websockets.client.WebSocketClient.ConnectionBuilder builder =
                        io.undertow.websockets.client.WebSocketClient.connectionBuilder( worker, new DefaultByteBufferPool( false, 1024, 1024, 0 ), uri );

                    IoFuture<WebSocketChannel> future = builder.connect();
                    future.addNotifier( new IoFuture.Notifier<WebSocketChannel, Object>()
                        {
                            @Override
                            public void notify( IoFuture<? extends WebSocketChannel> future, Object attachment )
                            {
                                if( future.getStatus() == IoFuture.Status.DONE )
                                {
                                    try
                                    {
                                        WebSocketChannel connection = future.get();
                                        channel = connection;
                                        connected.set( true );
                                        setupChannel( connection );
                                        forEachListener(l -> ((INetClient.IListener) l).onConnected( WebSocketClient.this ) );
                                    }
                                    catch( Exception e )
                                    {
                                        forEachListener(l -> ((INetClient.IListener) l).onError( WebSocketClient.this, e ) );
                                    }
                                }
                                else
                                {
                                    Throwable throwable = future.getException();
                                    Exception exception = (throwable instanceof Exception) ? (Exception) throwable : new Exception( throwable );
                                    forEachListener(l -> ((INetClient.IListener) l).onError( WebSocketClient.this, exception ) );
                                }
                                latch.countDown();
                            }
                        }, null );

                    latch.await( getTimeout(), TimeUnit.MILLISECONDS );

                    if( connected.get() )
                        return this;
                    else
                        cleanupResources();
                }
                catch( Exception e )
                {
                    cleanupResources();

                    if( attempt == MAX_RETRIES - 1 )
                    {
                        forEachListener(l -> ((INetClient.IListener) l).onError( WebSocketClient.this, e ) );
                        break;
                    }

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
    public synchronized INetClient disconnect()
    {
        isShuttingDown.set( true );
        cleanupResources();
        isShuttingDown.set( false );
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
                forEachListener(l -> ((INetClient.IListener) l).onError( WebSocketClient.this, e ) );
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
    // PROTECTED SCOPE

    /**
     * This method was created by needs of WebSocketServer.
     * @param webSocketChannel
     * @return
     */
    protected synchronized boolean connect( WebSocketChannel webSocketChannel )
    {
        if( isShuttingDown.get() )
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
            forEachListener(l -> ((INetClient.IListener) l).onError( WebSocketClient.this, e ) );
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
                        if( ! isShuttingDown.get() )
                            forEachListener(l -> l.onError( WebSocketClient.this, e ) );
                    }
                }

                @Override
                protected void onError( WebSocketChannel channel, Throwable error )
                {
                    if( ! isShuttingDown.get() )
                    {
                        Exception exception = (error instanceof Exception) ? (Exception) error : new Exception( error );
                        forEachListener(l -> l.onError( WebSocketClient.this, exception ) );
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
                log( "Error closing WebSocket channel: " + e.getMessage() );
            }
            finally
            {
                forEachListener(l -> ((INetClient.IListener) l).onDisconnected( WebSocketClient.this ) );
            }
        }

        if( worker != null )
        {
            try
            {
                worker.shutdownNow();
                if( ! worker.awaitTermination( 2, TimeUnit.SECONDS ) )
                    log( "Worker did not terminate" );
            }
            catch( InterruptedException e )
            {
                Thread.currentThread().interrupt();
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