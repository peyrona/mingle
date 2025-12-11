package com.peyrona.mingle.network.websocket;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.network.BaseServer4IP;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.WebSocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AtomicBoolean             isStopping  = new AtomicBoolean( false );
    private final AtomicReference<Undertow> undertowRef = new AtomicReference<>( null );
    private       String path;

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
    public synchronized INetServer start( String sCfgAsJSON )
    {
        if( isRunning() )
            return this;

        if( ! init( sCfgAsJSON, UtilComm.MINGLE_DEFAULT_WEBSOCKET_PORT ) )
            return this;

        // Latch to enforce deterministic startup wait
        CountDownLatch             startupLatch = new CountDownLatch( 1 );
        AtomicReference<Exception> startupError = new AtomicReference<>( null );

        try
        {
            // We use a Runnable instead of extending Thread
            ServerRunner runner = new ServerRunner( startupLatch, startupError );

            // Execute the runner. We don't need to keep the future because
            // the runner finishes as soon as Undertow.start() returns (non-blocking).
            // The Undertow instance itself is what keeps the app alive.
            UtilSys.execute( getClass().getSimpleName() + ":boot", runner );

            // Wait up to 5 seconds for the server to actually boot
            boolean started = startupLatch.await( 5, TimeUnit.SECONDS );

            if( startupError.get() != null )
                throw startupError.get();

            if( ! started )
                throw new MingleException( "Server failed to initialize within timeout period" );

            // Validate that the reference was actually set
            if( undertowRef.get() == null )
                throw new MingleException( "Server initialization failed (Instance null)" );

        }
        catch( Exception e )
        {
            MingleException me = new MingleException( "Failed to start server", e );
            stop(); // Ensure cleanup
            log( me );
            notifyError( (INetClient) null, me );
            throw me;
        }

        return super.start( sCfgAsJSON );
    }

    /**
     * Stops the WebSocket server gracefully.
     * This method closes all active client connections and shuts down the
     * underlying Undertow server.
     *
     * @return The server instance.
     */
    @Override
    public synchronized INetServer stop()
    {
        isStopping.set( true );

        try
        {
            // 1. Stop the generic IP server components
            super.stop();

            // 2. Explicitly stop Undertow to release ports and threads
            Undertow server = undertowRef.getAndSet( null );
            if( server != null )
            {
                server.stop();
                log( Level.INFO, "Undertow server stopped" );
            }
        }
        catch( Exception e )
        {
            log( new MingleException( "Error stopping Undertow server", e ) );
        }
        finally
        {
            isStopping.set( false );
        }

        return this;
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

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Server Runner
    //------------------------------------------------------------------------//
    private final class ServerRunner implements Runnable
    {
        private final CountDownLatch             startupLatch;
        private final AtomicReference<Exception> errorRef;

        ServerRunner( CountDownLatch latch, AtomicReference<Exception> errorRef )
        {
            this.startupLatch = latch;
            this.errorRef     = errorRef;
        }

        @Override
        public void run()
        {
            try
            {
                log( Level.INFO,
                     String.format( "Starting WebSocket server on %s:%d%s (SSL: %s)",
                                    getHost(), getPort(), path, getSSLCert() != null ) );

                WebSocketProtocolHandshakeHandler wsphh = Handlers.websocket( (exchange, channel) -> handleNewConnection( channel ) );

                Undertow.Builder builder = Undertow.builder()
                        .setServerOption( Options.BACKLOG, 10000 )
                        .setHandler( new PathHandler().addPrefixPath( path, wsphh ) );

                if( getSSLCert() == null )
                {
                    builder.addHttpListener( getPort(), getHost() );
                    log( Level.INFO, "Plain WebSocket because the Certificate file was not provided" );
                }
                else
                {
                    builder.addHttpsListener( getPort(), getHost(), createSSLContext() );
                    log( Level.INFO, "SSL WebSocket initialized" );
                }

                Undertow server = builder.build();

                // Critical: Assign to the AtomicReference so stop() can access it
                undertowRef.set( server );

                server.start();
                log( Level.INFO, "WebSocket server started successfully" );
            }
            catch( Exception e )
            {
                cleanupClients( true );
                errorRef.set( e );
            }
            finally
            {
                // Always count down so the main thread stops waiting
                startupLatch.countDown();
            }
        }

        //------------------------------------------------------------------------//

        private void handleNewConnection( WebSocketChannel channel )
        {
            WebSocketClient client = null;

            try
            {
                if( isAllowed( channel.getSourceAddress().getAddress() ) )
                {
                    client = new WebSocketClient();
                    client.add( newClientListener() );

                    // Note: Assuming WebSocketClient.connect maps to a boolean success/fail
                    if( client.connect( channel ) )
                    {
                        add( client );
                    }
                    else
                    {
                        // Explicitly close if connect logic failed but threw no exception
                        closeChannel( channel );
                    }
                }
                else
                {
                    MingleException me = new MingleException( channel.getSourceAddress().getAddress() + ": address not allowed" );

                    notifyError( client, me );
                    closeChannel( channel );
                }
            }
            catch( Exception exc )
            {
                if( ! isStopping.get() )
                    handleConnectionError( client, channel, exc );
            }
        }

        private void handleConnectionError( WebSocketClient client, WebSocketChannel channel, Exception exc )
        {
            if( client != null )
                client.disconnect();

            del( client );
            notifyError( client, exc );
            closeChannel( channel );
        }

        private void closeChannel( WebSocketChannel channel )
        {
            if( channel != null && channel.isOpen() )
            {
                try
                {
                    channel.sendClose();
                }
                catch( Exception e )
                {
                    // Ignore close errors, but log trace if needed for debugging
                }
            }
        }
    }
}