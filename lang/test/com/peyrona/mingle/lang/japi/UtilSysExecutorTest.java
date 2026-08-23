package com.peyrona.mingle.lang.japi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

/**
 * Test suite for {@link UtilSys.Executor}.
 * <p>
 * Covers normal builder/scheduling behavior plus two properties that were recently hardened:
 * <ul>
 *   <li>a task submitted while the shared 32-thread worker pool is saturated must still run
 *       (on the caller thread) instead of being silently dropped</li>
 *   <li>a periodic task ({@code rate(...)}) must never have two of its executions running at
 *       the same time, even when a single run takes longer than the rate</li>
 * </ul>
 * <p>
 * NOTE: {@code UtilSys.Executor.scheduler}/{@code workerPool} are static and shared for the
 * lifetime of the JVM, so {@code UtilSys.stop()} (which shuts them down for good) is
 * deliberately never called here — doing so would break every test that runs afterwards.
 */
@RunWith(JUnitPlatform.class)
public class UtilSysExecutorTest
{
    private static final int POOL_SIZE = 32;

    //------------------------------------------------------------------------//
    // NORMAL BEHAVIOR
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Normal Behavior Tests")
    class NormalBehaviorTests
    {
        @Test
        @DisplayName("One-shot pooled task runs exactly once")
        void testOneShotPooledRuns() throws Exception
        {
            AtomicInteger runs = new AtomicInteger( 0 );

            // NOTE: for the pooled path, the returned ScheduledFuture completes as soon as the task
            // is handed off to workerPool.execute() -- NOT when the task itself finishes running
            // there. So waiting on the future is not enough; poll for the actual side effect instead.
            UtilSys.executor( true ).execute( runs::incrementAndGet );

            waitUntil( () -> runs.get() == 1, 3000 );

            assertEquals( 1, runs.get(), "Task should have run exactly once" );
        }

        @Test
        @DisplayName("One-shot non-pooled task runs exactly once")
        void testOneShotNonPooledRuns() throws Exception
        {
            AtomicInteger runs = new AtomicInteger( 0 );

            ScheduledFuture<?> future = UtilSys.executor( false ).execute( runs::incrementAndGet );

            future.get( 5, TimeUnit.SECONDS );

            assertEquals( 1, runs.get(), "Task should have run exactly once" );
        }

        @Test
        @DisplayName("delay() postpones execution by (at least) the requested amount")
        void testDelayIsRespected() throws Exception
        {
            long start = System.currentTimeMillis();
            AtomicReference<Long> ranAt = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch( 1 );

            UtilSys.executor( true )
                   .delay( 300 )
                   .execute( () -> { ranAt.set( System.currentTimeMillis() ); done.countDown(); } );

            assertTrue( done.await( 5, TimeUnit.SECONDS ), "Task should eventually run" );
            assertTrue( (ranAt.get() - start) >= 250, "Task should not run before the requested delay (tolerance applied)" );
        }

        @Test
        @DisplayName("Negative delay is treated as no delay")
        void testNegativeDelayDoesNotThrow() throws Exception
        {
            ScheduledFuture<?> future = UtilSys.executor( true )
                                                .delay( -100 )
                                                .execute( () -> {} );

            assertDoesNotThrow( () -> future.get( 5, TimeUnit.SECONDS ) );
        }

        @Test
        @DisplayName("name() is applied to the executing thread")
        void testNameIsAppliedToThread() throws Exception
        {
            AtomicReference<String> threadName = new AtomicReference<>();

            UtilSys.executor( true )
                   .name( "MyCustomTaskName" )
                   .execute( () -> threadName.set( Thread.currentThread().getName() ) );

            waitUntil( () -> threadName.get() != null, 3000 );

            assertEquals( "MyCustomTaskName", threadName.get() );
        }

        @Test
        @DisplayName("Auto-generated name is used when name() is not called")
        void testAutoNameDoesNotThrowAndIsNotBlank() throws Exception
        {
            AtomicReference<String> threadName = new AtomicReference<>();

            UtilSys.executor( true )
                   .execute( () -> threadName.set( Thread.currentThread().getName() ) );

            waitUntil( () -> threadName.get() != null, 3000 );

            assertNotNull( threadName.get() );
            assertFalse( threadName.get().trim().isEmpty() );
        }

        @Test
        @DisplayName("error() handler receives the thrown exception")
        void testErrorHandlerReceivesException() throws Exception
        {
            AtomicReference<Exception> caught = new AtomicReference<>();
            RuntimeException boom = new RuntimeException( "boom" );

            UtilSys.executor( true )
                   .error( caught::set )
                   .execute( () -> { throw boom; } );

            waitUntil( () -> caught.get() != null, 3000 );

            assertSame( boom, caught.get() );
        }

        @Test
        @DisplayName("Task exception without an error() handler does not propagate nor break the pool")
        void testDefaultErrorHandlingDoesNotPropagateOrBreakPool() throws Exception
        {
            AtomicBoolean ranDespiteThrowing = new AtomicBoolean( false );

            assertDoesNotThrow( () ->
                UtilSys.executor( true ).execute( () ->
                {
                    ranDespiteThrowing.set( true );
                    throw new RuntimeException( "no handler" );
                } ) );

            waitUntil( () -> ranDespiteThrowing.get(), 3000 );

            // Pool must still be usable afterwards
            AtomicInteger runs = new AtomicInteger( 0 );
            UtilSys.executor( true ).execute( runs::incrementAndGet );

            waitUntil( () -> runs.get() == 1, 3000 );
            assertEquals( 1, runs.get() );
        }

        @Test
        @DisplayName("Pooled periodic task fires more than once")
        void testPooledPeriodicFiresMultipleTimes() throws Exception
        {
            AtomicInteger runs = new AtomicInteger( 0 );
            CountDownLatch threeRuns = new CountDownLatch( 3 );

            ScheduledFuture<?> future = UtilSys.executor( true )
                                                .delay( 0 )
                                                .rate( 50 )
                                                .execute( () -> { runs.incrementAndGet(); threeRuns.countDown(); } );
            try
            {
                assertTrue( threeRuns.await( 5, TimeUnit.SECONDS ), "Periodic task should fire at least 3 times" );
            }
            finally
            {
                future.cancel( false );
            }
        }

        @Test
        @DisplayName("Non-pooled periodic task fires more than once")
        void testNonPooledPeriodicFiresMultipleTimes() throws Exception
        {
            CountDownLatch threeRuns = new CountDownLatch( 3 );

            ScheduledFuture<?> future = UtilSys.executor( false )
                                                .delay( 0 )
                                                .rate( 50 )
                                                .execute( threeRuns::countDown );
            try
            {
                assertTrue( threeRuns.await( 5, TimeUnit.SECONDS ), "Periodic task should fire at least 3 times" );
            }
            finally
            {
                future.cancel( false );
            }
        }

        @Test
        @DisplayName("Without rate(), the task never repeats")
        void testNoRateMeansOneShotOnly() throws Exception
        {
            AtomicInteger runs = new AtomicInteger( 0 );

            UtilSys.executor( true ).execute( runs::incrementAndGet ).get( 5, TimeUnit.SECONDS );

            Thread.sleep( 300 );   // give a (hypothetical) repeat a chance to fire

            assertEquals( 1, runs.get(), "Task without rate() must run exactly once" );
        }

        @Test
        @DisplayName("cancel() stops further periodic executions")
        void testCancelStopsPeriodicExecution() throws Exception
        {
            AtomicInteger runs = new AtomicInteger( 0 );

            ScheduledFuture<?> future = UtilSys.executor( true )
                                                .delay( 0 )
                                                .rate( 40 )
                                                .execute( runs::incrementAndGet );

            Thread.sleep( 250 );
            future.cancel( false );
            int countAtCancel = runs.get();

            Thread.sleep( 300 );

            assertEquals( countAtCancel, runs.get(), "No more executions should happen after cancel()" );
        }
    }

    //------------------------------------------------------------------------//
    // #2 — SATURATED POOL MUST RUN THE TASK, NEVER SILENTLY DROP IT
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Worker Pool Saturation Tests")
    class WorkerPoolSaturationTests
    {
        @Test
        @DisplayName("Task submitted while all 32 worker threads are busy still runs (caller-runs), not dropped")
        void testSaturatedPoolRunsInsteadOfDropping() throws Exception
        {
            CountDownLatch allBlockersStarted = new CountDownLatch( POOL_SIZE );
            CountDownLatch releaseBlockers    = new CountDownLatch( 1 );
            CountDownLatch allBlockersDone    = new CountDownLatch( POOL_SIZE );

            try
            {
                for( int i = 0; i < POOL_SIZE; i++ )
                {
                    UtilSys.executor( true ).execute( () ->
                    {
                        allBlockersStarted.countDown();

                        try { releaseBlockers.await( 15, TimeUnit.SECONDS ); }
                        catch( InterruptedException ignored ) {}

                        allBlockersDone.countDown();
                    } );
                }

                assertTrue( allBlockersStarted.await( 5, TimeUnit.SECONDS ),
                            "All "+ POOL_SIZE +" worker-pool threads should become busy" );

                AtomicBoolean overflowRan = new AtomicBoolean( false );

                UtilSys.executor( true ).execute( () -> overflowRan.set( true ) );

                // The overflow task must run right away, WHILE every pool thread is still
                // blocked — proving it ran on the caller thread instead of waiting for a slot.
                long deadline = System.currentTimeMillis() + 3000;

                while( (! overflowRan.get()) && (System.currentTimeMillis() < deadline) )
                    Thread.sleep( 10 );

                assertTrue( overflowRan.get(),
                            "A task submitted while the pool is saturated must still run instead of being silently dropped" );
            }
            finally
            {
                releaseBlockers.countDown();
                assertTrue( allBlockersDone.await( 10, TimeUnit.SECONDS ), "All blocking tasks should complete once released" );
            }
        }
    }

    //------------------------------------------------------------------------//
    // #3 — PERIODIC EXECUTIONS MUST NEVER OVERLAP
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName("Periodic Non-Overlap Tests")
    class PeriodicNonOverlapTests
    {
        @Test
        @DisplayName("scheduleWithFixedDelay-style periodic task never overlaps itself, even when a run outlasts the rate")
        void testFixedDelayStyleNeverOverlaps() throws Exception
        {
            assertNeverOverlaps( false );
        }

        @Test
        @DisplayName("scheduleAtFixedRate-style periodic task never overlaps itself, even when a run outlasts the rate")
        void testFixedRateStyleNeverOverlaps() throws Exception
        {
            assertNeverOverlaps( true );
        }

        @Test
        @DisplayName("A slow run causes the next tick to be skipped, not queued up behind it")
        void testSlowRunSkipsNextTickRatherThanQueueing() throws Exception
        {
            AtomicInteger    concurrent    = new AtomicInteger( 0 );
            AtomicInteger    maxConcurrent = new AtomicInteger( 0 );
            AtomicInteger    startedCount  = new AtomicInteger( 0 );
            CountDownLatch   twoRuns       = new CountDownLatch( 2 );

            // rate is much shorter than the run itself, so several ticks are expected to be skipped.
            ScheduledFuture<?> future = UtilSys.executor( true )
                                                .delay( 0 )
                                                .rate( 30 )
                                                .execute( () ->
                                                {
                                                    int c = concurrent.incrementAndGet();
                                                    maxConcurrent.updateAndGet( m -> Math.max( m, c ) );
                                                    startedCount.incrementAndGet();

                                                    try { Thread.sleep( 250 ); }
                                                    catch( InterruptedException ignored ) {}

                                                    concurrent.decrementAndGet();
                                                    twoRuns.countDown();
                                                } );
            try
            {
                assertTrue( twoRuns.await( 5, TimeUnit.SECONDS ), "At least 2 runs should complete" );
                assertEquals( 1, maxConcurrent.get(), "Runs must never overlap" );

                // ~250ms per run, observed for >= 2 runs: elapsed time is dominated by run duration,
                // not by (rate * ticks-that-would-have-queued) — i.e. skipped ticks are discarded.
                Thread.sleep( 100 );
                assertTrue( startedCount.get() < 6, "Skipped ticks should be discarded, not queued up and burst-executed later" );
            }
            finally
            {
                future.cancel( false );
            }
        }

        private void assertNeverOverlaps( boolean fixedRate ) throws Exception
        {
            AtomicInteger  concurrent    = new AtomicInteger( 0 );
            AtomicInteger  maxConcurrent = new AtomicInteger( 0 );
            CountDownLatch threeRuns     = new CountDownLatch( 3 );

            ScheduledFuture<?> future = UtilSys.executor( true )
                                                .delay( 0 )
                                                .rate( 60 )
                                                .fixedRate( fixedRate )
                                                .execute( () ->
                                                {
                                                    int c = concurrent.incrementAndGet();
                                                    maxConcurrent.updateAndGet( m -> Math.max( m, c ) );

                                                    try { Thread.sleep( 200 ); }   // outlasts the 60ms rate on purpose
                                                    catch( InterruptedException ignored ) {}

                                                    concurrent.decrementAndGet();
                                                    threeRuns.countDown();
                                                } );
            try
            {
                assertTrue( threeRuns.await( 8, TimeUnit.SECONDS ), "Should still complete several runs despite skipped ticks" );
                assertEquals( 1, maxConcurrent.get(), "Periodic runs must never execute concurrently with themselves" );
            }
            finally
            {
                future.cancel( false );
            }
        }
    }

    //------------------------------------------------------------------------//
    // HELPERS
    //------------------------------------------------------------------------//

    private static void waitUntil( java.util.function.BooleanSupplier condition, long timeoutMs ) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while( (! condition.getAsBoolean()) && System.currentTimeMillis() < deadline )
            Thread.sleep( 10 );
    }
}
