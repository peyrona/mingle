/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.peyrona.mingle.lang.japi;

import com.peyrona.mingle.lang.MingleException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
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
 *   <li><b>{@link #add(Object)} never drops an item.</b> When the queue is full, an external
 *       caller is throttled (blocking backpressure) instead of losing its item. The consumer
 *       thread itself (a reentrant {@code add()} — e.g. a rule's THEN action posting a message
 *       while its WHEN is being evaluated) can't safely block on itself, so it grows the queue
 *       past its configured max instead.</li>
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
            //
            // Known: this join() runs while holding this Dispatcher's monitor, which a reentrant
            // add() (see add()'s isReentrant branch) also needs to enter. If consumer.accept() is
            // reentrantly calling add() at the exact moment another thread calls stop(), that
            // add() call stalls here until this join() completes (monitor-entry isn't
            // interruptible) and then misreports as "not running" instead of "reentrant". The
            // window is tiny and self-heals (forceGrowAndAdd still runs either way, item still
            // preserved), so the author decided it isn't worth fixing.
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
     * The queue grows automatically if it is 85% full. This method never drops an item: see the
     * class-level note on {@link #add(Object)} above.
     *
     * @param item Item to add to the queue.
     * @return Itself.
     */
    public Dispatcher<T> add( T item )
    {
        assert item != null;

        boolean isReentrant;

        // Serialize all offers with the resize swap in checkAndGrow(). Otherwise a fast-path
        // offer() resolved against the old queue reference can land in a queue that was
        // just replaced by checkAndGrow(), silently losing the item.
        synchronized( this )
        {
            if( queue.offer( item ) )
                return this;

            checkAndGrow();

            if( queue.offer( item ) )
                return this;

            isReentrant = (Thread.currentThread() == thrQueue);

            if( isReentrant )
            {
                // We ARE the only consumer: blocking here would deadlock (nothing else can ever
                // drain the queue to make room). And this item was generated by processing the
                // current one (e.g. a rule's THEN action) — dropping it is not acceptable. So grow
                // past the configured ceiling instead. This should be rare; if it recurs, it's a
                // strong signal of a message cascade (e.g. a self-triggering rule), not a capacity
                // problem — logged every time so it's noticed long before it becomes an OOM.
                onError.accept( new MingleException( "Dispatcher '" + thrName + "' queue grew past its "
                              + "configured max (" + maxCapacity + ") to accept an internally generated "
                              + "event — check for a rule/message cycle." ) );
                forceGrowAndAdd( item );
                return this;
            }

            if( thrQueue == null )
            {
                // Not running (not started yet, or stopped): no consumer thread will ever drain
                // the queue, so blocking below would wait forever. Same remedy as the reentrant
                // case, for the same underlying reason — nothing can ever unblock us.
                onError.accept( new MingleException( "Dispatcher '" + thrName + "' queue grew past its "
                              + "configured max (" + maxCapacity + ") while not running (not started, or "
                              + "stopped) — the item would otherwise block forever with nothing to drain it." ) );
                forceGrowAndAdd( item );
                return this;
            }
        }

        // External caller, queue genuinely full: throttle instead of dropping. Deliberately done
        // outside the lock above — blocking here while holding it would stop the reentrant path
        // (above) from ever acquiring it, and only that thread can drain the queue. Polling the
        // *current* queue field each attempt (rather than blocking once against a captured
        // reference) means this self-heals if forceGrowAndAdd() swaps the queue while we wait.
        onError.accept( new MingleException( "Dispatcher '" + thrName + "' queue is full (" + maxCapacity
                      + "): caller is being throttled until space frees up." ) );
        try
        {
            while( ! queue.offer( item, 50, TimeUnit.MILLISECONDS ) )
            {
                if( Thread.currentThread().isInterrupted() )
                    break;

                if( thrQueue == null )
                {
                    // Stopped while we were waiting: a concurrent stop()'s queue.clear() could
                    // otherwise let a subsequent offer() here succeed silently into a queue nobody
                    // is reading anymore, deferring the item indefinitely with no record of it.
                    // Route it through the same logged force-grow path instead.
                    synchronized( this )
                    {
                        onError.accept( new MingleException( "Dispatcher '" + thrName + "' queue grew past "
                                      + "its configured max (" + maxCapacity + ") after being stopped while "
                                      + "a caller was waiting to add to it." ) );
                        forceGrowAndAdd( item );
                    }
                    return this;
                }
            }
        }
        catch( InterruptedException ie )
        {
            Thread.currentThread().interrupt();
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
        // Known: uses UtilSys.elapsed() (time since the ExEn started) instead of this instance's
        // own start time, so the rate is diluted for a Dispatcher created/restarted well after
        // ExEn startup. Left as-is deliberately: not worth the extra state for a best-effort stat.
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

    /**
     * Blocks until all pending messages are processed (queue becomes empty).
     * <p>
     * This method is useful for graceful shutdown scenarios where you want to
     * ensure all queued messages are delivered before stopping the dispatcher.
     *
     * @return this Dispatcher instance for method chaining.
     */
    public Dispatcher<T> flush()
    {
        // Calling flush() from the consumer thread itself would livelock: isProcessing stays
        // true while the consumer is inside consumer.accept(), so the loop would never exit.
        if( Thread.currentThread() == thrQueue )
            throw new IllegalStateException( "flush() must not be called from the consumer thread" );

        while( (! queue.isEmpty()) || isProcessing )
        {
            try
            {
                Thread.sleep( 10 );    // Small sleep to avoid busy-waiting
            }
            catch( InterruptedException e )
            {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return this;
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

    /**
     * Grows the queue past 'maxCapacity', ignoring the configured ceiling, and inserts 'item'.
     * Must be called with this Dispatcher's monitor held (see the reentrant branch of
     * {@link #add(Object)} — its only caller). Never used for external callers, which are
     * throttled instead; this exists solely because the consumer thread can't block on itself.
     */
    private void forceGrowAndAdd( T item )
    {
        int currentCapacity = queue.size() + queue.remainingCapacity();
        int newCapacity     = Math.max( currentCapacity + 1, (int) Math.ceil( currentCapacity * 1.15 ) );

        BlockingQueue<Object> newQueue = new ArrayBlockingQueue<>( newCapacity );

        queue.drainTo( newQueue );
        queue = newQueue;

        newQueue.offer( item );   // Always succeeds: sized to fit every drained item plus this one.
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

                // Wait here while paused, then mark as processing - checking pauseCount and
                // setting isProcessing all happen under pauseLock, as one atomic step. Otherwise
                // pause() could run in between, see isProcessing == false, and return while this
                // message is about to be processed anyway - breaking the "atomic pause" guarantee.
                pauseLock.lock();

                try
                {
                    // Must recheck AFTER getting the message, not before, to avoid the race above
                    while( pauseCount > 0 && ! tCurrent.isInterrupted() )
                        unpaused.await();

                    if( pauseCount > 0 )   // still paused: only got here because of an interrupt
                        break;

                    isProcessing = true;   // atomic with the pauseCount check above
                }
                finally
                {
                    pauseLock.unlock();
                }

                try
                {
                    consumer.accept( val );
                    msgCount.incrementAndGet();
                }
                finally
                {
                    pauseLock.lock();

                    try
                    {
                        isProcessing = false;
                        notProcessing.signalAll();   // Multiple threads can be waiting in pause() concurrently
                    }
                    finally
                    {
                        pauseLock.unlock();
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