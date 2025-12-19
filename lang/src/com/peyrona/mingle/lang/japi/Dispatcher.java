
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Dispatcher class manages a queue of items which are processed asynchronously.
 * <p>
 * Improved for Zero-Allocation and Concurrency Safety.
 *
 * @param <T> the type of task to be processed.
 */
public final class Dispatcher<T>
{
    // Use ArrayBlockingQueue.
    // 1. It is bounded (prevents OOM).
    // 2. It is backed by a single array (no Node object allocation per item).

    private volatile BlockingQueue<T>    queue;
    private final    int                 maxCapacity;
    private volatile boolean             paused    = false;
    private final    AtomicLong          msgCount  = new AtomicLong( 0 );
    private final    Consumer<T>         consumer;
    private final    String              thrName;
    private final    Consumer<Exception> onError;
    private volatile Thread              thrQueue;

    private final    ReentrantLock       pauseLock = new ReentrantLock();
    private final    Condition           unpaused  = pauseLock.newCondition();

    //------------------------------------------------------------------------//

    /**
     * Constructs a Dispatcher with specific capacity.
     *
     * @param consumer   the consumer to process tasks
     * @param onError    to be invoked in case of the 'consumer' raises an exception.
     * @param initialCapacity The initial size of the internal buffer.
     * @param maxCapacity The max size of the internal buffer.
     */
    public Dispatcher( Consumer<T> consumer, Consumer<Exception> onError, int initialCapacity, int maxCapacity )
    {
        this( consumer, onError, initialCapacity, maxCapacity, null );
    }

    /**
     * Constructs a Dispatcher with specific capacity.
     *
     * @param consumer   the consumer to process tasks
     * @param onError    to be invoked in case of the 'consumer' raises an exception.
     * @param initialCapacity The initial size of the internal buffer.
     * @param maxCapacity The max size of the internal buffer.
     * @param threadName the name of the thread.
     */
    public Dispatcher( Consumer<T> consumer, Consumer<Exception> onError, int initialCapacity, int maxCapacity, String threadName )
    {
        assert consumer != null && onError != null && initialCapacity > 0 && maxCapacity >= initialCapacity;

        if( UtilStr.isEmpty( threadName ) )
            threadName = UtilReflect.getCallerClass( 3 ).getSimpleName() +":"+ UtilReflect.getCallerMethodName( 3 );

        this.consumer    = consumer;
        this.onError     = onError;
        this.thrName     = threadName;
        this.queue       = new ArrayBlockingQueue<>( initialCapacity );
        this.maxCapacity = maxCapacity;
    }

    //------------------------------------------------------------------------//

    public synchronized Dispatcher<T> start()
    {
        if( thrQueue == null )
        {
            paused = false; // Reset state on start
            thrQueue = new Thread( this::execute, thrName );
            thrQueue.start();
        }

        return this;
    }

    public synchronized Dispatcher<T> stop()
    {
        if( thrQueue != null )
        {
            thrQueue.interrupt();
            thrQueue = null;
            paused = false;
            queue.clear();
        }

        return this;
    }

    /**
     * Returns true if the dispatcher is paused.
     */
    public boolean isPaused()
    {
        return paused;
    }

    /**
     * Pauses the dispatcher thread.
     * Strategy C: Idempotent operation. Calling this multiple times has no side effect.
     */
    public Dispatcher<T> pause()
    {
        this.paused = true;   // No lock needed to set a volatile boolean
        return this;
    }

    /**
     * Resumes the dispatcher thread.
     * Strategy C: Idempotent operation.
     */
    public Dispatcher<T> resume()
    {
        pauseLock.lock();

        try
        {
            this.paused = false;
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
     * The queue grows automatically if it is 85% full.
     * Since we are now using a bounded ArrayBlockingQueue, we must handle the 'full' case.
     *
     * @param item Item to add to the queue.
     * @return Itself.
     */
    public synchronized Dispatcher<T> add( T item )
    {
        assert item != null;

        if( ! queue.offer( item ) )   // If offer fails, it means the queue is full.
        {
            checkAndGrow();           // Tries to increment

            if( ! queue.offer( item ) )
                onError.accept( new MingleException( "Dispatcher queue is at max capacity (" + maxCapacity + "). Event dropped." ) );
        }

        return this;
    }

    /**
     * Returns the approximate amount of messages dispatched per minute.
     *
     * @return The approximate amount of messages dispatched per minute.
     */
    public int getSpeed()
    {
        long minutes  = UtilSys.elapsed() / UtilUnit.MINUTE;
        return (int) Math.round( msgCount.get() / Math.max( 1, minutes ) );
    }

    /**
     * Returns the amount of messages pending to be delivered,
     *
     * @return The amount of messages pending to be delivered,
     */
    public int getPending()
    {
        return queue.size();
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void checkAndGrow()
    {
        int currentCapacity = queue.size() + queue.remainingCapacity();

        if( currentCapacity >= maxCapacity )
            return;

        if( queue.size() >= currentCapacity * 0.85 )   // Grow if queue is at least 85% full
        {
            int newCapacity = (int) Math.ceil( currentCapacity * 1.15 );

            if( newCapacity > maxCapacity )
                newCapacity = maxCapacity;

            if( newCapacity <= currentCapacity )       // Ensure growth for small capacities
                newCapacity = currentCapacity + 1;

            if( newCapacity > maxCapacity )            // Re-check after ensuring growth
                newCapacity = maxCapacity;

            if( newCapacity > currentCapacity )
            {
                BlockingQueue<T> newQueue = new ArrayBlockingQueue<>( newCapacity );
                queue.drainTo( newQueue );
                this.queue = newQueue;
            }
        }
    }

    private void execute()
    {
        Thread tCurrent = Thread.currentThread();

        while( !tCurrent.isInterrupted() )
        {
            T val = null;

            try
            {
                if( paused )
                {
                    pauseLock.lock();

                    try
                    {
                        while( paused && ! tCurrent.isInterrupted() )    // Check paused again inside lock to avoid race conditions
                            unpaused.await();
                    }
                    finally
                    {
                        pauseLock.unlock();
                    }
                }

                if( tCurrent.isInterrupted() )
                    break;

                val = queue.take();    // ArrayBlockingQueuE::take() is cleaner on memory

                consumer.accept( val );
                msgCount.incrementAndGet();
            }
            catch( InterruptedException ie )
            {
                tCurrent.interrupt();
                break;
            }
            catch( Exception exc )
            {
                String sErr = "Error in " + tCurrent.getName() + ", using value: " +
                              (val == null ? "N/A" : val.toString());

                // We must wrap Throwable in an Exception to match the Consumer<Exception> signature
                // or assume MingleException can wrap Throwable.
                onError.accept( new MingleException( sErr, new Exception(exc) ) );
            }
        }
    }
}