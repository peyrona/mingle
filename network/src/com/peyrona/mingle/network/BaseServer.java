
package com.peyrona.mingle.network;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.ListenerWise;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 *
 * @author francisco
 */
public abstract
       class      BaseServer
       extends    ListenerWise<INetServer.IListener>
       implements INetServer
{
    private volatile boolean         isRunning   = false;
    private volatile boolean         wasStopped  = false;
    private final    Set<INetClient> lstClients  = ConcurrentHashMap.newKeySet();
    private final    Broadcaster     broadcaster = new Broadcaster();
    private final    ScheduledFuture maintenance = Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay( () -> cleanupClients( false ),
                                                                                                                        10,10, TimeUnit.SECONDS );
    private final    ExecutorService ioExecutor  = new ThreadPoolExecutor( numOfThreads(),                                 // Core threads
                                                                           200,                                            // Max threads
                                                                           30, TimeUnit.SECONDS,                           // Keep-alive
                                                                           new ArrayBlockingQueue<>(500),                  // Bounded queue
                                                                           createThreadFactory( "network-server-", true ), // Custom factory
                                                                           new ThreadPoolExecutor.CallerRunsPolicy() );    // Rejection policy (to provide backpressure)

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    @Override
    public boolean isRunning()
    {
        return isRunning;
    }

    @Override
    public synchronized INetServer start( String sCfgAsJSON )
    {
        if( wasStopped )
            throw new MingleException( "Once a server is stopped it can not be re-started" );

        isRunning = true;
        return this;
    }

    @Override
    public synchronized INetServer stop()
    {
        wasStopped = true;

        maintenance.cancel( true );

        cleanupClients( true );

        broadcaster.shutdown();
        ioExecutor.shutdown();

        try
        {
            if( ! ioExecutor.awaitTermination( 10, TimeUnit.SECONDS ) )
                ioExecutor.shutdownNow();
        }
        catch( InterruptedException ie )
        {
            ioExecutor.shutdownNow();
        }

        isRunning = false;
        return this;
    }

    @Override
    public boolean add( INetClient client )
    {
        return lstClients.add( client );
    }

    @Override
    public boolean del( INetClient client )
    {
        return lstClients.remove( client );
    }

    @Override
    public boolean hasClients()
    {
        return ! lstClients.isEmpty();
    }

    @Override
    public Stream<INetClient> getClients()
    {
        synchronized( lstClients )
        {
            cleanupClients( false );
            return new ArrayList<>( lstClients ).stream();
        }
    }

    @Override
    public INetServer broadcast( String message )
    {
        if( ! hasClients() || ! isRunning() )
        {
            if( ! isRunning() )
                notifyError( (INetClient) null, new MingleException( "Attempting to use a closed " + getClass().getSimpleName() ) );

            return this;
        }

        broadcaster.broadcast( message, lstClients, this::del, this::notifyError );

        return this;
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    // If getClients() returns Collection<INetClient>
    protected void cleanupClients( boolean bAll )
    {
        if( ! lstClients.isEmpty() )
            lstClients.removeIf( client -> bAll || (! client.isConnected()) );
    }

    /**
     * Notify all listeners: INetServer.IListener.onConnected.
     *
     * @param client Involved client. Can be null.
     * @return Itself.
     */
    protected INetServer notifyConnected( INetClient client )
    {
        forEachListener( (l) -> l.onConnected( this, client ) );
        return this;
    }

    /**
     * Notify all listeners: INetServer.IListener.onDisconnected.
     *
     * @param client Involved client. Can be null.
     * @return Itself.
     */
    protected INetServer notifyDisconnected( INetClient client )
    {
        if( client != null )
            del( client );

        forEachListener( (l) -> l.onDisconnected( this, client ) );
        return this;
    }

    /**
     * Notify all listeners: INetServer.IListener.onMessage.
     *
     * @param client Involved client. Can be null.
     * @param msg
     * @return Itself.
     */
    protected INetServer notifyMessage( INetClient client, String msg )
    {
        forEachListener( (l) -> l.onMessage( this, client, msg ) );
        return this;
    }

    /**
     * Notify all listeners: INetServer.IListener.onError.
     *
     * @param client Involved client. Can be null.
     * @param exc
     * @return Itself.
     */
    protected INetServer notifyError( INetClient client, Exception exc )
    {
        if( client != null )
            del( client );

        forEachListener( (l) ->  l.onError( this, client, exc ) );

        return this;
    }

    protected INetServer log( ILogger.Level level, String msg )
    {
        if( UtilSys.getLogger() == null )  System.err.println( "["+ level +"] "+ msg );
        else                               UtilSys.getLogger().log( level, msg );

        return this;
    }

    protected INetServer log( Throwable th )
    {
        return log( null, th );
    }

    protected INetServer log( String msg, Throwable th )
    {
        if( UtilSys.getLogger() == null )
        {
            if( msg != null )
                System.err.println( msg );

            if( th != null )
                th.printStackTrace( System.err );
        }
        else if( msg != null )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, th, msg );
        }

        return this;
    }

    protected INetClient.IListener newClientListener()
    {
        return new INetClient.IListener()
                    {
                        @Override
                        public void onConnected( INetClient origin )
                        {
                            notifyConnected( origin );
                        }

                        @Override
                        public void onDisconnected( INetClient origin )
                        {
                            notifyDisconnected( origin );
                        }

                        @Override
                        public void onMessage( INetClient origin, String msg )
                        {
                            notifyMessage( origin, msg );
                        }

                        @Override
                        public void onError( INetClient origin, Exception exc )
                        {
                            notifyError( origin, exc );
                        }
                    };
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    // Helper method to create thread factory
    private static ThreadFactory createThreadFactory( String namePrefix, boolean daemon )
    {
        return new ThreadFactory()
        {
            private final AtomicInteger threadNumber = new AtomicInteger( 1 );

            @Override
            public Thread newThread( Runnable r )
            {
                Thread t = new Thread( r, namePrefix + threadNumber.getAndIncrement() );
                t.setDaemon( daemon );   // Critical for JVM shutdown
                return t;
            }
        };
    }

    private static int numOfThreads()
    {
        return Math.min( 4, Runtime.getRuntime().availableProcessors() );
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // Non-blocking parallel broadcaster
    //------------------------------------------------------------------------//

    private final class Broadcaster
    {
        // Using ThreadPoolExecutor with a bounded queue to prevent OOM.
        // Core=Max threads (fixed size), Bounded Queue=1000 (adjust as needed), Policy=CallerRuns (backpressure)
        private final ExecutorService executor = new ThreadPoolExecutor( numOfThreads(),                                      // Core threads
                                                                         numOfThreads(),                                      // Max threads
                                                                         0L, TimeUnit.MILLISECONDS,                           // Keep-alive (0 beacuse is irrelevant when Core==Max)
                                                                         new ArrayBlockingQueue<>( 512 ),                     // Bounded queue limits pending broadcasts (512 should be enough)
                                                                         createThreadFactory( "network-broadcaster-", true ), // Factory
                                                                         new ThreadPoolExecutor.CallerRunsPolicy() );         // Rejection policy: Run in caller thread if full

        //------------------------------------------------------------------------//

        public void broadcast( String                            message,
                               Set<INetClient>                   clients,
                               Consumer<INetClient>              clientRemover,
                               BiConsumer<INetClient, Exception> errorHandler )
        {
            if( clients.isEmpty() )
                return;

            // A thread-safe set to collect clients that need to be removed after the broadcast.
            final Set<INetClient> clientsToRemove = ConcurrentHashMap.newKeySet();

            // 1. Create a stream of CompletableFuture tasks, one for each client.

            CompletableFuture<?>[] futures =
                 clients.stream()
                        .map( client -> CompletableFuture.runAsync( () ->
                            {
                                if( client != null )
                                {
                                    try
                                    {
                                        if( client.isConnected() )  client.send( message );
                                        else                        clientsToRemove.add( client );
                                    }
                                    catch( Exception exc )   // If send() fails, mark for removal and handle the error.
                                    {
                                        clientsToRemove.add( client );
                                        errorHandler.accept( client, exc );
                                    }
                                }
                            }, executor ) )
                        .toArray( CompletableFuture<?>[]::new );

            // 2. After all send operations are complete, remove all the clients that failed.
            //    This action is triggered asynchronously when all futures are done.

            CompletableFuture.allOf( futures )
                             .thenRun( () -> clientsToRemove.forEach( clientRemover ) );
        }

        public void shutdown()
        {
            executor.shutdown();

            try
            {
                if( ! executor.awaitTermination( 5, TimeUnit.SECONDS ) )
                    executor.shutdownNow();
            }
            catch( InterruptedException e )
            {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}