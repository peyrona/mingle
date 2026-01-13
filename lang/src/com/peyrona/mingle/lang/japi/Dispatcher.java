
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
 * <p>
 * Thread Safety Features:
 * <ul>
 *   <li><b>Reference-counted pause:</b> Multiple threads can call {@link #pause()} and the
 *       dispatcher only resumes when all have called {@link #resume()}.</li>
 *   <li><b>Atomic pause:</b> {@link #pause()} blocks until any in-flight message processing
 *       completes, guaranteeing no concurrent access to shared state.</li>
 * </ul>
 *
 * @param <T> the type of task to be processed.
 */
public final class Dispatcher<T>
{
    // Use ArrayBlockingQueue.
    // 1. It is bounded (prevents OOM).
    // 2. It is backed by a single array (no Node object allocation per item).

    private static final Object          RESIZE_SIGNAL = new Object();

    private volatile BlockingQueue<Object> queue;
    private final    int                 maxCapacity;
    private volatile int                 pauseCount   = 1;                       // Starts paused (count=1), writes under pauseLock, reads can be lock-free
    private volatile boolean             isProcessing = false;                   // True while consumer.accept() is running
    private final    AtomicLong          msgCount     = new AtomicLong( 0 );
    private final    Consumer<T>         consumer;
    private final    String              thrName;
    private final    Consumer<Exception> onError;
    private volatile Thread              thrQueue;

    private final    ReentrantLock       pauseLock     = new ReentrantLock();
    private final    Condition           unpaused      = pauseLock.newCondition();
    private final    Condition           notProcessing = pauseLock.newCondition();

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

    /**
     * Starts the dispatcher.
     * @return Itself.
     */
    public synchronized Dispatcher<T> start()
    {
        if( thrQueue == null )
        {
            pauseLock.lock();
            try
            {
                pauseCount = 0;   // Reset state on start (not paused)
            }
            finally
            {
                pauseLock.unlock();
            }
            thrQueue = new Thread( this::execute, thrName );
            thrQueue.start();
        }

        return this;
    }

    /**
     * Stops the dispatcher.
     * <p>
     * This method interrupts the processing thread and waits for it to terminate
     * before clearing the queue, ensuring no race conditions with a subsequent start().
     *
     * @return Itself.
     */
    public synchronized Dispatcher<T> stop()
    {
        if( thrQueue != null )
        {
            Thread t = thrQueue;
            thrQueue = null;
            t.interrupt();

            // Wait for thread to terminate to avoid race with immediate start()
            try
            {
                t.join( 5000 );   // Wait up to 5 seconds for graceful termination
            }
            catch( InterruptedException e )
            {
                Thread.currentThread().interrupt();
            }

            pauseLock.lock();
            try
            {
                pauseCount = 1;   // Mark as paused
            }
            finally
            {
                pauseLock.unlock();
            }
            queue.clear();
        }

        return this;
    }

    /**
     * Returns true if the dispatcher is paused (pause count > 0).
     *
     * @return true if paused, false otherwise.
     */
    public boolean isPaused()
    {
        return pauseCount > 0;   // volatile read, no lock needed
    }

    /**
     * Pauses the dispatcher thread and waits for any in-flight message to complete.
     * <p>
     * This method is <b>reference-counted</b>: each call to {@code pause()} must be balanced
     * by a corresponding call to {@link #resume()}. The dispatcher only resumes when the
     * pause count reaches zero.
     * <p>
     * This method <b>blocks</b> until any currently processing message completes, ensuring
     * that no message is being processed when this method returns.
     *
     * @return this Dispatcher instance for method chaining.
     */
    public Dispatcher<T> pause()
    {
        pauseLock.lock();

        try
        {
            pauseCount++;

            // Wait for any in-flight message processing to complete
            while( isProcessing )
            {
                try
                {
                    notProcessing.await();
                }
                catch( InterruptedException e )
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        finally
        {
            pauseLock.unlock();
        }

        return this;
    }

    /**
     * Resumes the dispatcher thread by decrementing the pause count.
     * <p>
     * The dispatcher only truly resumes when the pause count reaches zero (i.e., all
     * callers that invoked {@link #pause()} have called {@code resume()}).
     *
     * @return this Dispatcher instance for method chaining.
     */
    public Dispatcher<T> resume()
    {
        pauseLock.lock();

        try
        {
            pauseCount--;

            if( pauseCount < 0 )
                pauseCount = 0;   // Prevent negative count from unbalanced calls

            if( pauseCount == 0 )
                unpaused.signal();   // Only one consumer thread, signal() suffices
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
    public Dispatcher<T> add( T item )
    {
        assert item != null;

        // Fast path: ArrayBlockingQueue.offer() is thread-safe
        if( queue.offer( item ) )
            return this;

        // Slow path: queue is full, need synchronization for potential resize
        synchronized( this )
        {
            checkAndGrow();

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
                BlockingQueue<Object> newQueue = new ArrayBlockingQueue<>( newCapacity );
                BlockingQueue<Object> oldQueue = this.queue;

                synchronized( this )
                {
                    oldQueue.drainTo( newQueue );
                    this.queue = newQueue;
                }

                // If the consumer is blocked waiting for an item in the old queue, we must
                // wake it up so it can switch to the new queue.
                // We do this by inserting a special dummy object.
                oldQueue.offer( RESIZE_SIGNAL );
            }
        }
    }

    private void execute()
    {
        Thread tCurrent = Thread.currentThread();

        while( ! tCurrent.isInterrupted() )
        {
            T val = null;

            try
            {
                // Wait for message from queue
                Object obj = queue.take();

                if( obj == RESIZE_SIGNAL )
                    continue;

                val = (T) obj;

                if( tCurrent.isInterrupted() )
                    break;

                // FAST PATH: If not paused, process without acquiring lock.
                // This is the common case and avoids lock overhead.
                if( pauseCount == 0 )
                {
                    isProcessing = true;   // volatile write

                    try
                    {
                        consumer.accept( val );
                        msgCount.incrementAndGet();
                    }
                    finally
                    {
                        isProcessing = false;   // volatile write

                        // Check if pause() is waiting - only signal if someone might be waiting
                        if( pauseCount > 0 )
                        {
                            pauseLock.lock();
                            try
                            {
                                notProcessing.signal();
                            }
                            finally
                            {
                                pauseLock.unlock();
                            }
                        }
                    }
                }
                else
                {
                    // SLOW PATH: Paused - must use lock-based coordination
                    pauseLock.lock();

                    try
                    {
                        // Wait if paused - must check AFTER getting message to prevent race
                        while( pauseCount > 0 && ! tCurrent.isInterrupted() )
                            unpaused.await();

                        if( tCurrent.isInterrupted() )
                            break;

                        // Mark as processing while still holding the lock
                        isProcessing = true;
                    }
                    finally
                    {
                        pauseLock.unlock();
                    }

                    // Process the message
                    try
                    {
                        consumer.accept( val );
                        msgCount.incrementAndGet();
                    }
                    finally
                    {
                        // Mark as not processing and signal any waiting pause() callers
                        pauseLock.lock();

                        try
                        {
                            isProcessing = false;
                            notProcessing.signal();   // Only one waiter possible
                        }
                        finally
                        {
                            pauseLock.unlock();
                        }
                    }
                }
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

                onError.accept( new MingleException( sErr, exc ) );
            }
        }
    }
}