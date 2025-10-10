
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Dispatcher class manages a queue of items which are processed asynchronously.
 *
 * @param <T> the type of task to be processed.
 */
public final class Dispatcher<T>
{
    private final    BlockingQueue<T>    queue     = new LinkedBlockingQueue<>();
    private final    AtomicInteger       pause     = new AtomicInteger( 0 );    // Accumulative calls to ::pause()
    private final    AtomicLong          msgCount  = new AtomicLong( 0 );
    private final    Consumer<T>         consumer;
    private final    String              thrName;
    private final    Consumer<Exception> onError;
    private volatile Thread              thrQueue;

    private final    ReentrantLock       pauseLock = new ReentrantLock();
    private final    Condition           unpaused  = pauseLock.newCondition();

    //------------------------------------------------------------------------//

    /**
     * Constructs a Dispatcher with the specified consumer, error manager and thread name.
     *
     * @param consumer   the consumer to process tasks
     * @param onError    to be invoked in case of the 'consumer' raises an exception.
     * @param threadName the name of the thread (optional)
     */
    public Dispatcher( Consumer<T> consumer, Consumer<Exception> onError, String threadName )
    {
        assert consumer != null && onError != null;

        if( UtilStr.isEmpty( threadName ) )
            threadName = UtilReflect.getCallerClass( 3 ).getSimpleName();    // This can return null

        if( threadName == null )
            threadName = "Unknown";

        this.consumer = consumer;
        this.onError  = onError;
        this.thrName  = getClass().getSimpleName() +':'+ threadName.trim();
    }

    //------------------------------------------------------------------------//

    /**
     * Starts the dispatcher thread if it's not already running.
     *
     * @return this Dispatcher instance
     */
    public synchronized Dispatcher<T> start()
    {
        if( thrQueue == null )
        {
            pause.set( 0 );
            thrQueue = new Thread( this::execute, thrName );
            thrQueue.start();
        }

        return this;
    }

    /**
     * Stops the dispatcher thread, interrupting it if necessary.
     *
     * @return this Dispatcher instance
     */
    public synchronized Dispatcher<T> stop()
    {
        if( thrQueue != null )
        {
            thrQueue.interrupt();
            thrQueue = null;
            pause.set( 0 );
            queue.clear();
        }

        return this;
    }

    /**
     * Returns true if the dispatcher is paused.
     * @return true if the dispatcher is paused.
     */
    public boolean isPaused()
    {
        return pause.get() > 0;
    }

    /**
     * Pauses the dispatcher thread, stopping it from processing tasks.
     *
     * @return this Dispatcher instance
     */
    public Dispatcher<T> pause()
    {
        pauseLock.lock();

        try
        {
            pause.incrementAndGet();
        }
        finally
        {
            pauseLock.unlock();
        }

        return this;
    }

    /**
     * Resumes the dispatcher thread, allowing it to continue processing tasks.
     *
     * @return this Dispatcher instance
     */
    public Dispatcher<T> resume()
    {
        pauseLock.lock();

        try
        {
            if( pause.decrementAndGet() == 0 )
                unpaused.signalAll();
        }
        finally
        {
            pauseLock.unlock();
        }

        return this;
    }

    /**
     * Adds passed item to the internal queue of items.
     *
     * @param item the item to add to the internal queue.
     * @return this Dispatcher instance.
     */
    public Dispatcher<T> add( T item )
    {
        if( item == null )
            onError.accept( new MingleException( "Can not add null to queue" ) );
        else
            queue.offer( item );

        return this;
    }

    /**
     * Returns current message processing speed.
     *
     * @return Current message processing speed.
     */
    public int getSpeed()
    {
        long minutes  = UtilSys.elapsed() / UtilUnit.MINUTE;

        return (int) Math.round( msgCount.get() / Math.max( 1, minutes ) );
    }

    /**
     * Returns the amount of messages waiting in the queue (pending to be processed).
     *
     * @return The amount of messages waiting in the queue (pending to be processed).
     */
    public int getPending()
    {
        return queue.size();
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Executes the dispatcher loop, processing tasks from the queue.<br>
     * This method is intended to be run on a separate thread.
     */
    private void execute()
    {
        Thread tCurrent = Thread.currentThread();

        while( ! tCurrent.isInterrupted() )
        {
            T val = null;

            try
            {
                if( isPaused() )
                {
                    pauseLock.lock();

                    try
                    {
                        while( isPaused() && (! tCurrent.isInterrupted()) )
                            unpaused.await();                                // Sleeps until signaled by resume()
                    }
                    finally
                    {
                        pauseLock.unlock();
                    }
                }

                if( tCurrent.isInterrupted() )
                    break;

                val = queue.take();           // take() blocks until an item is available

                consumer.accept( val );
                msgCount.incrementAndGet();   // It would take too many years until this dispatches more than Long.MAX_VALUE messages
            }
            catch( InterruptedException ie )
            {
                tCurrent.interrupt();
                break;
            }
            catch( Exception exc )     // An Exception was thrown inside Consumer
            {
                String sErr = "Error in " + tCurrent.getName() +", using value: "+
                              (val == null ? "N/A" : val.toString());

                onError.accept( new MingleException( sErr, exc ) );
            }
        }
    }
}