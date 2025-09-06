
package com.peyrona.mingle.network.plain;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.network.BaseServer4IP;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
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

/**
 * This class exposes a socket service (port is configurable) that can be used
 * to communicate with the ExEn and is used by the ExEn to communicate back to
 * the clients.
 * <p>
 * It is mainly used to send requests to the ExEn or to retrieve the status of a
 * device.
 * <p>
 * Used protocol is defined by SocketProtocol class.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class PlainSocketServer
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

            if( ! init( sCfgAsJSON, UtilComm.MINGLE_DEFAULT_SOCKET_PORT ) )
                return this;

            try
            {
                server = new ServerThread( getPort() );
                exec   = Executors.newCachedThreadPool();
                exec.submit( server );

                setRunning( true );
            }
            catch( IOException | RejectedExecutionException e )
            {
                MingleException me = new MingleException( "Failed to start server", e );

                cleanup();
                log( me );
                throw me;
            }
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
            forEachListener( l -> l.onError( PlainSocketServer.this, null, new MingleException( "Attempting to use a closed server-socket" ) ) );

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
                forEachListener( l -> l.onError( PlainSocketServer.this, null, me ) );    // Last to in case it throws an exception
            }
        }

        if( server != null )
            server.clean();

        exec   = null;
        server = null;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Server Socket Thread
    //------------------------------------------------------------------------//
    private final class ServerThread extends Thread
    {
        private final ServerSocket ss;
        private final Queue<PlainSocketClient> lstClients = new ConcurrentLinkedQueue<>();
        private final ScheduledExecutorService maintenanceExecutor = Executors.newSingleThreadScheduledExecutor();

        //------------------------------------------------------------------------//

        ServerThread( int nPort ) throws IOException
        {
            super( PlainSocketServer.class.getSimpleName() +":Server" );

            ss = new ServerSocket( nPort );
            ss.setReuseAddress(true);
         // ss.setSoTimeout( SERVER_TIMEOUT ); --> Can not use it because a client can last days in attempting to connect

            // Schedule periodic maintenance task
            maintenanceExecutor.scheduleAtFixedRate( this::performMaintenance, 30, 30, TimeUnit.SECONDS );
        }

        //------------------------------------------------------------------------//

        @Override
        public void run()
        {
            while( (! isStopping.get()) && (! Thread.currentThread().isInterrupted()) )
            {
                Socket                             socket = null;
                AtomicReference<PlainSocketClient> client = new AtomicReference<>( null );

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

                        forEachListener( l -> l.onError( PlainSocketServer.this, client.get(), me ) );
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
            if( ! hasClients() )
                return;

            Iterator<PlainSocketClient>   iterator = lstClients.iterator();
            List<CompletableFuture<Void>> futures  = new ArrayList<>();

            while( iterator.hasNext() )
            {
                PlainSocketClient client = iterator.next();

                if( ! client.isConnected() )
                {
                    iterator.remove();    // Remove disconnected clients safely
                }
                else
                {
                    CompletableFuture<Void> future = CompletableFuture.runAsync( () ->
                                                        {
                                                            try
                                                            {
                                                                client.send( message );
                                                            }
                                                            catch( Exception e )
                                                            {
                                                                forEachListener( l -> l.onError( PlainSocketServer.this, client, e ) );
                                                                iterator.remove();
                                                            }
                                                        }, exec );

                    futures.add( future );
                }
            }

            try
            {
                CompletableFuture.allOf( futures.toArray( CompletableFuture[]::new ) ).get( 5000, TimeUnit.MILLISECONDS );
            }
            catch( Exception e )
            {
                forEachListener( l -> l.onError( PlainSocketServer.this, null, e ) );
            }
        }

        void clean()
        {
            maintenanceExecutor.shutdownNow();

            for( Iterator<PlainSocketClient> itera = lstClients.iterator(); itera.hasNext(); )
            {
                PlainSocketClient client = itera.next();

                forEachListener( l -> l.onDisconnected( PlainSocketServer.this, client ) );

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

        private void handleNewConnection( Socket socket, AtomicReference<PlainSocketClient> clientRef ) throws IOException
        {
            PlainSocketClient newClient = new PlainSocketClient();
                              newClient.add( new ClientListener() );

            if( newClient.connect( socket ) )
            {
                clientRef.set( newClient );
                lstClients.add( newClient );
                forEachListener( l -> l.onConnected( PlainSocketServer.this, newClient ) );
            }
        }

        private void handleConnectionError( PlainSocketClient client, Socket socket, Exception exc )
        {
            if( client != null )
                client.disconnect();

            forEachListener( l -> l.onError( PlainSocketServer.this, client, exc ) );
            closeSocket( socket );
        }

        private void removeClient( PlainSocketClient client )
        {
            lstClients.remove( client );
            forEachListener( l -> l.onDisconnected( PlainSocketServer.this, client ) );
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

        private void performMaintenance()
        {
            Iterator<PlainSocketClient> iterator = lstClients.iterator();

            while( iterator.hasNext() )
            {
                PlainSocketClient client = iterator.next();

                if( ! client.isConnected() )
                {
                    removeClient(client);
                    iterator.remove();
                }
            }
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
            forEachListener( l -> l.onMessage( PlainSocketServer.this, origin, msg ) );
        }

        @Override
        public void onError(INetClient origin, Exception exc)
        {
        }
    }
}