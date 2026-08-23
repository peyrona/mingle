package com.peyrona.mingle.lang.japi;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

/**
 * Test suite for {@link Dispatcher}.
 * <p>
 * Covers normal start/stop/pause/add/flush behavior plus the "never drop an item" guarantee
 * that {@link Dispatcher#add(Object)} was recently hardened to provide:
 * <ul>
 *   <li>an external caller is throttled (blocking backpressure) when the queue is genuinely full</li>
 *   <li>a reentrant {@code add()} from the consumer thread itself grows the queue past its
 *       configured max instead of deadlocking or dropping the item</li>
 *   <li>{@code add()} on a dispatcher that was never started (or was stopped) also grows past
 *       max instead of blocking forever</li>
 *   <li>a caller throttled at the moment {@code stop()} runs still gets its item preserved</li>
 * </ul>
 * Each of these paths also reports the condition via the configured {@code onError} consumer,
 * which every test captures to confirm it fires.
 */
@RunWith(JUnitPlatform.class)
public class DispatcherTest
{
    //------------------------------------------------------------------------//
    // NORMAL BEHAVIOR
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Normal Behavior Tests")
    class NormalBehaviorTests
    {
        @Test
        @DisplayName("Items are delivered to the consumer, in order")
        void testItemsDeliveredInOrder() throws Exception
        {
            List<Integer> processed = new CopyOnWriteArrayList<>();
            List<Exception> errors  = new CopyOnWriteArrayList<>();

            Dispatcher<Integer> d = new Dispatcher<>( processed::add, errors::add, 4, 8, "test-order" );
            d.start();

            for( int i = 1; i <= 5; i++ )
                d.add( i );

            waitUntil( () -> processed.size() == 5, 3000 );

            assertEquals( List.of( 1, 2, 3, 4, 5 ), processed );
            assertTrue( errors.isEmpty(), "No errors expected in the normal path: " + errors );

            d.stop();
        }

        @Test
        @DisplayName("stop() then start() again resumes normal delivery")
        void testStopThenStartAgain() throws Exception
        {
            List<Integer> processed = new CopyOnWriteArrayList<>();
            Dispatcher<Integer> d = new Dispatcher<>( processed::add, e -> {}, 4, 8, "test-restart" );

            d.start();
            d.add( 1 );
            waitUntil( () -> processed.size() == 1, 2000 );

            d.stop();
            d.start();
            d.add( 2 );
            waitUntil( () -> processed.size() == 2, 2000 );

            assertEquals( List.of( 1, 2 ), processed );

            d.stop();
        }

        @Test
        @DisplayName("stop() discards whatever is still queued")
        void testStopClearsPendingQueue() throws Exception
        {
            List<Integer> processed = new CopyOnWriteArrayList<>();
            CountDownLatch blockFirst  = new CountDownLatch( 1 );
            CountDownLatch firstEntered = new CountDownLatch( 1 );

            Dispatcher<Integer> d = new Dispatcher<>( v ->
            {
                if( v == 1 )
                {
                    firstEntered.countDown();
                    await( blockFirst, 5000 );
                }
                processed.add( v );
            }, e -> {}, 4, 8, "test-clear" );

            d.start();
            d.add( 1 );
            assertTrue( firstEntered.await( 2, TimeUnit.SECONDS ) );

            d.add( 2 );
            d.add( 3 );
            assertEquals( 2, d.getPending(), "Items 2 and 3 should still be queued" );

            d.stop();   // interrupt() unblocks item 1's wait, then join() + clear() empty the queue

            assertEquals( 0, d.getPending(), "Queue must be empty right after stop()" );
        }

        @Test
        @DisplayName("pause() blocks until in-flight processing completes, resume() lets it continue")
        void testPauseWaitsForInFlightThenResume() throws Exception
        {
            List<Integer> processed = new CopyOnWriteArrayList<>();
            CountDownLatch enteredProcessing = new CountDownLatch( 1 );
            CountDownLatch releaseProcessing = new CountDownLatch( 1 );

            Dispatcher<Integer> d = new Dispatcher<>( v ->
            {
                enteredProcessing.countDown();
                await( releaseProcessing, 5000 );
                processed.add( v );
            }, e -> {}, 4, 8, "test-pause" );

            d.start();
            d.add( 1 );
            assertTrue( enteredProcessing.await( 2, TimeUnit.SECONDS ) );

            AtomicBoolean pauseReturned = new AtomicBoolean( false );
            Thread pauser = new Thread( () -> { d.pause(); pauseReturned.set( true ); } );
            pauser.start();

            Thread.sleep( 200 );
            assertFalse( pauseReturned.get(), "pause() must block while item 1 is still processing" );

            releaseProcessing.countDown();
            pauser.join( 3000 );

            assertTrue( pauseReturned.get(), "pause() should return once in-flight processing completes" );
            assertTrue( d.isPaused() );

            d.add( 2 );
            Thread.sleep( 200 );
            assertEquals( List.of( 1 ), processed, "Item 2 must not be processed while paused" );

            d.resume();
            waitUntil( () -> processed.size() == 2, 2000 );
            assertEquals( List.of( 1, 2 ), processed );

            d.stop();
        }

        @Test
        @DisplayName("pause() is reference-counted: needs one resume() per pause()")
        void testPauseIsReferenceCounted() throws Exception
        {
            List<Integer> processed = new CopyOnWriteArrayList<>();
            Dispatcher<Integer> d = new Dispatcher<>( processed::add, e -> {}, 4, 8, "test-refcount" );
            d.start();

            d.pause();
            d.pause();
            assertTrue( d.isPaused() );

            d.resume();
            assertTrue( d.isPaused(), "Still paused: only one of two pause() calls was resumed" );

            d.add( 1 );
            Thread.sleep( 200 );
            assertTrue( processed.isEmpty() );

            d.resume();
            assertFalse( d.isPaused() );
            waitUntil( () -> processed.size() == 1, 2000 );

            d.stop();
        }

        @Test
        @DisplayName("flush() blocks until the queue is drained")
        void testFlushWaitsForQueueToDrain() throws Exception
        {
            List<Integer> processed = new CopyOnWriteArrayList<>();
            Dispatcher<Integer> d = new Dispatcher<>( v ->
            {
                try { Thread.sleep( 50 ); } catch( InterruptedException ignored ) {}
                processed.add( v );
            }, e -> {}, 4, 8, "test-flush" );

            d.start();
            d.add( 1 ).add( 2 ).add( 3 );

            d.flush();

            assertEquals( 3, processed.size() );
            assertEquals( 0, d.getPending() );

            d.stop();
        }

        @Test
        @DisplayName("flush() called from the consumer thread itself throws IllegalStateException")
        void testFlushFromConsumerThreadThrows() throws Exception
        {
            AtomicReference<Dispatcher<Integer>> self  = new AtomicReference<>();
            AtomicReference<Exception>           caught = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch( 1 );

            Dispatcher<Integer> d = new Dispatcher<>( v ->
            {
                try { self.get().flush(); }
                catch( Exception e ) { caught.set( e ); }
                finally { done.countDown(); }
            }, e -> {}, 4, 8, "test-flush-reentrant" );

            self.set( d );
            d.start();
            d.add( 1 );

            assertTrue( done.await( 3, TimeUnit.SECONDS ) );
            assertNotNull( caught.get() );
            assertInstanceOf( IllegalStateException.class, caught.get() );

            d.stop();
        }

        @Test
        @DisplayName("getPending() and getSpeed() reflect basic activity")
        void testGetPendingAndGetSpeed() throws Exception
        {
            CountDownLatch started = new CountDownLatch( 1 );
            CountDownLatch hold    = new CountDownLatch( 1 );
            Dispatcher<Integer> d  = new Dispatcher<>( v -> { started.countDown(); await( hold, 5000 ); }, e -> {}, 4, 8, "test-metrics" );

            d.start();
            d.add( 1 );   // dequeued for processing as soon as the consumer thread picks it up
            assertTrue( started.await( 2, TimeUnit.SECONDS ), "Item 1 should have been dequeued into processing" );

            d.add( 2 );
            d.add( 3 );

            assertEquals( 2, d.getPending() );
            assertTrue( d.getSpeed() >= 0 );

            hold.countDown();
            d.stop();
        }

        @Test
        @DisplayName("Queue grows automatically as it fills, up to maxCapacity, without dropping items")
        void testAutomaticGrowthWithinMax() throws Exception
        {
            List<Integer> processed = new CopyOnWriteArrayList<>();
            CountDownLatch hold = new CountDownLatch( 1 );
            AtomicBoolean first = new AtomicBoolean( true );

            Dispatcher<Integer> d = new Dispatcher<>( v ->
            {
                if( first.compareAndSet( true, false ) )
                    await( hold, 5000 );   // keep the consumer busy so items pile up in the queue
                processed.add( v );
            }, e -> {}, 2, 50, "test-grow" );

            d.start();

            for( int i = 1; i <= 40; i++ )
                d.add( i );

            assertTrue( d.getPending() <= 50, "Queue must never exceed maxCapacity via ordinary growth" );

            hold.countDown();
            waitUntil( () -> processed.size() == 40, 5000 );

            assertEquals( 40, processed.size(), "No item should be lost while the queue is growing" );

            d.stop();
        }
    }

    //------------------------------------------------------------------------//
    // add() NEVER DROPS AN ITEM
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Never-Drop add() Tests")
    class NeverDropTests
    {
        @Test
        @DisplayName("External caller is throttled (blocked), not dropped, when the queue is full")
        void testExternalCallerIsThrottledNotDropped() throws Exception
        {
            List<Integer> processed = new CopyOnWriteArrayList<>();
            List<Exception> errors  = new CopyOnWriteArrayList<>();
            CountDownLatch started = new CountDownLatch( 1 );
            CountDownLatch release = new CountDownLatch( 1 );

            Dispatcher<Integer> d = new Dispatcher<>( v ->
            {
                processed.add( v );
                if( v == 1 )
                {
                    started.countDown();
                    await( release, 5000 );
                }
            }, errors::add, 2, 2, "test-throttle" );   // maxCapacity == initialCapacity: no ordinary growth possible

            d.start();
            d.add( 1 );                       // dequeued immediately, consumer now blocked inside accept(1)
            assertTrue( started.await( 2, TimeUnit.SECONDS ) );

            d.add( 2 );                       // queue: [2]
            d.add( 3 );                       // queue: [2,3] -- FULL (capacity 2)

            AtomicBoolean externalAddReturned = new AtomicBoolean( false );
            Thread external = new Thread( () ->
            {
                d.add( 4 );                   // must block here until space frees up
                externalAddReturned.set( true );
            } );
            external.start();

            Thread.sleep( 300 );
            assertFalse( externalAddReturned.get(), "add() must block (throttle) while the queue is genuinely full, not drop the item" );

            release.countDown();              // let item 1 finish, unblocking the consumer to drain the queue
            external.join( 5000 );

            assertTrue( externalAddReturned.get(), "Throttled add() must eventually return once space frees up" );
            waitUntil( () -> processed.size() == 4, 3000 );
            assertEquals( List.of( 1, 2, 3, 4 ), processed, "Every item must be delivered, in order, none dropped" );
            assertTrue( errors.stream().anyMatch( e -> e.getMessage().contains( "throttled" ) ),
                        "onError should report that a caller was throttled" );

            d.stop();
        }

        @Test
        @DisplayName("Reentrant add() from the consumer thread grows past maxCapacity instead of deadlocking or dropping")
        void testReentrantAddGrowsPastMax() throws Exception
        {
            List<Integer> processed = new CopyOnWriteArrayList<>();
            List<Exception> errors  = new CopyOnWriteArrayList<>();
            AtomicReference<Dispatcher<Integer>> self = new AtomicReference<>();

            Dispatcher<Integer> d = new Dispatcher<>( v ->
            {
                processed.add( v );

                if( v == 1 )
                {
                    // Simulates a rule's THEN action posting more messages while THIS message is
                    // being processed: reentrant call, on the very thread that would otherwise be
                    // the only one able to drain the queue.
                    self.get().add( 2 );   // queue empty at this point -> succeeds normally
                    self.get().add( 3 );   // queue now full (max=1) -> must grow, not drop/deadlock
                }
            }, errors::add, 1, 1, "test-reentrant" );   // maxCapacity == 1: trivial to fill

            self.set( d );

            d.add( 1 );   // queue: [1], full (max=1), dispatcher not started yet
            d.start();    // consumer immediately dequeues 1 and starts the cascade above

            waitUntil( () -> processed.size() == 3, 3000 );

            assertEquals( List.of( 1, 2, 3 ), processed, "No item should be dropped despite exceeding maxCapacity" );
            assertTrue( errors.stream().anyMatch( e -> e.getMessage().contains( "grew past its configured max" )
                                                     && e.getMessage().contains( "internally generated" ) ),
                        "onError should flag the reentrant overflow: " + errors );

            d.stop();
        }

        @Test
        @DisplayName("add() on a dispatcher that was never started grows past maxCapacity instead of blocking forever")
        void testAddWhenNeverStartedGrowsPastMax() throws Exception
        {
            List<Exception> errors = new CopyOnWriteArrayList<>();
            Dispatcher<Integer> d = new Dispatcher<>( v -> {}, errors::add, 1, 1, "test-not-started" );

            d.add( 1 );   // fills the queue (max=1)

            AtomicBoolean returned = new AtomicBoolean( false );
            Thread t = new Thread( () -> { d.add( 2 ); returned.set( true ); } );
            t.start();
            t.join( 3000 );

            assertTrue( returned.get(), "add() must not block forever on a dispatcher with no consumer thread" );
            assertEquals( 2, d.getPending(), "Both items must be retained, none dropped" );
            assertTrue( errors.stream().anyMatch( e -> e.getMessage().contains( "while not running" ) ),
                        "onError should flag that the dispatcher isn't running: " + errors );

            // Confirm both survive into normal delivery once actually started.
            List<Integer> processed = new CopyOnWriteArrayList<>();
            Dispatcher<Integer> withCapture = new Dispatcher<>( processed::add, e -> {}, 1, 1, "test-not-started-2" );
            withCapture.add( 1 );
            withCapture.add( 2 );
            withCapture.start();

            waitUntil( () -> processed.size() == 2, 3000 );
            assertEquals( List.of( 1, 2 ), processed );

            withCapture.stop();
        }

        @Test
        @DisplayName("A caller throttled at the moment stop() runs still gets its item preserved")
        void testThrottledCallerSurvivesConcurrentStop() throws Exception
        {
            List<Integer> processed = new CopyOnWriteArrayList<>();
            List<Exception> errors  = new CopyOnWriteArrayList<>();
            CountDownLatch started       = new CountDownLatch( 1 );
            CountDownLatch neverReleased = new CountDownLatch( 1 );   // only stop()'s interrupt() ever unblocks this

            Dispatcher<Integer> d = new Dispatcher<>( v ->
            {
                processed.add( v );
                if( v == 1 )
                {
                    started.countDown();
                    await( neverReleased, 10_000 );
                }
            }, errors::add, 1, 1, "test-stop-race" );

            d.start();
            d.add( 1 );                        // dequeued immediately, consumer blocks inside accept(1)
            assertTrue( started.await( 2, TimeUnit.SECONDS ) );

            d.add( 2 );                        // queue: [2] -- FULL (max=1), and never dequeued from here on

            AtomicBoolean externalReturned = new AtomicBoolean( false );
            Thread external = new Thread( () ->
            {
                d.add( 3 );                    // will throttle: queue full, consumer stuck on item 1
                externalReturned.set( true );
            } );
            external.start();
            Thread.sleep( 200 );               // let it enter the throttle loop
            assertFalse( externalReturned.get() );

            d.stop();                          // its own interrupt() unblocks item 1; races with 'external' polling for item 3

            external.join( 8000 );

            assertTrue( externalReturned.get(), "The throttled add() must return once stop() releases it, not hang forever" );
            assertEquals( 1, d.getPending(), "Item 3, added during the stop() race, must be the only item retained (item 2 was cleared by stop())" );

            d.start();
            waitUntil( () -> processed.contains( 3 ), 3000 );
            assertTrue( processed.contains( 3 ), "Item 3 must eventually be delivered" );

            d.stop();
        }
    }

    //------------------------------------------------------------------------//
    // HELPERS
    //------------------------------------------------------------------------//

    private static void await( CountDownLatch latch, long timeoutMs )
    {
        try { latch.await( timeoutMs, TimeUnit.MILLISECONDS ); }
        catch( InterruptedException e ) { Thread.currentThread().interrupt(); }
    }

    private static void waitUntil( java.util.function.BooleanSupplier condition, long timeoutMs ) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while( (! condition.getAsBoolean()) && System.currentTimeMillis() < deadline )
            Thread.sleep( 10 );
    }

}
