/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.exen.Cancellable;
import com.peyrona.mingle.lang.interfaces.exen.IEventBus;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.Dispatcher;
import com.peyrona.mingle.lang.messages.Message;
import com.peyrona.mingle.lang.xpreval.functions.ExtraType;
import com.peyrona.mingle.lang.xpreval.functions.list;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for memory-leak fixes in {@link CellSet}.
 * <p>
 * Covers:
 * <ul>
 *   <li>Only one Dispatcher / EventBus listener is created regardless of instance count.</li>
 *   <li>Listener is removed exactly once when the last instance stops.</li>
 *   <li>Map entry is removed cleanly after stop().</li>
 *   <li>PCL is correctly transferred when a new ExtraTypeCollection is assigned via write().</li>
 *   <li>nCount is not corrupted when start() fails formula validation (Bug 1).</li>
 *   <li>instances registry is correctly maintained on start() and stop() (Bug 2).</li>
 *   <li>Circular reference reports exactly one error (double sendIsInvalid fix).</li>
 *   <li>Constant cell value is not overwritten by onDeviceChanged() (else branch fix).</li>
 *   <li>Dispatcher is recreated after full teardown and new start (teardown race fix).</li>
 * </ul>
 *
 * @author Francisco Jose Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class CellSetTest
{
    private static final String KEY_VALUE = "value";

    private MockEventBus  bus;
    private MockRuntime   runtime;
    private TestListener  listener;
    private List<CellSet> created;

    //------------------------------------------------------------------------//

    @BeforeEach
    @SuppressWarnings( "unchecked" )
    void setUp() throws Exception
    {
        // Reset CellSet's static state to ensure each test starts from a clean slate.
        // This guards against cross-test contamination when a previous test
        // fails before stopping all its instances.
        Field mapField = CellSet.class.getDeclaredField( "map" );
        mapField.setAccessible( true );
        ((Map<?,?>) mapField.get( null )).clear();

        Field instancesField = CellSet.class.getDeclaredField( "instances" );
        instancesField.setAccessible( true );
        ((Map<?,?>) instancesField.get( null )).clear();

        Field nCountField = CellSet.class.getDeclaredField( "nCount" );
        nCountField.setAccessible( true );
        ((AtomicInteger) nCountField.get( null )).set( 0 );

        Field disField = CellSet.class.getDeclaredField( "dis" );
        disField.setAccessible( true );
        Dispatcher<?> dis = (Dispatcher<?>) disField.get( null );

        if( dis != null )
            dis.stop();

        disField.set( null, null );

        Field eblField = CellSet.class.getDeclaredField( "ebl" );
        eblField.setAccessible( true );
        eblField.set( null, null );

        // Per-test objects
        bus      = new MockEventBus();
        runtime  = new MockRuntime( bus );
        listener = new TestListener();
        created  = new ArrayList<>();
    }

    @AfterEach
    void tearDown()
    {
        for( CellSet cs : created )
        {
            try { cs.stop(); }
            catch( Exception ignored ) {}
        }

        created.clear();
    }

    //------------------------------------------------------------------------//
    // TESTS
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName( "Listener Registration Tests" )
    class ListenerRegistrationTests
    {
        @Test
        @DisplayName( "Only one EventBus listener is registered regardless of how many instances start" )
        void onlyOneListenerRegisteredAcrossMultipleInstances()
        {
            CellSet a = createCell( "a", "10" );
            CellSet b = createCell( "b", "20" );
            CellSet c = createCell( "c", "30" );

            a.start( runtime );
            b.start( runtime );
            c.start( runtime );

            assertEquals( 1, bus.getAddCount(),
                    "bus.add() must be called exactly once regardless of instance count" );
        }
    }

    @Nested
    @DisplayName( "Lifecycle Tests" )
    class LifecycleTests
    {
        @Test
        @DisplayName( "EventBus listener is removed exactly once when the last instance stops" )
        void listenerRemovedOnLastInstanceStop()
        {
            CellSet a = createCell( "la", "10" );
            CellSet b = createCell( "lb", "20" );

            a.start( runtime );
            b.start( runtime );

            a.stop();
            assertEquals( 0, bus.getRemoveCount(),
                    "Listener must not be removed while other instances are still running" );

            b.stop();
            assertEquals( 1, bus.getRemoveCount(),
                    "Listener must be removed exactly once when the last instance stops" );

            created.clear();    // Already stopped; prevent double-stop in tearDown.
        }

        @Test
        @DisplayName( "No stale map entry remains after stop()" )
        void noStaleMapEntryAfterStop() throws Exception
        {
            CellSet cell = createCell( "staleTest", "42" );
            cell.start( runtime );

            Field mapField = CellSet.class.getDeclaredField( "map" );
            mapField.setAccessible( true );
            Map<?,?> cellMap = (Map<?,?>) mapField.get( null );

            assertTrue( cellMap.containsKey( "staleTest" ),
                    "Map must contain entry after start()" );

            cell.stop();
            created.remove( cell );    // Prevent double-stop in tearDown.

            assertFalse( cellMap.containsKey( "staleTest" ),
                    "Map must not contain entry after stop()" );
        }
    }

    @Nested
    @DisplayName( "Property Change Listener Tests" )
    class PropertyChangeListenerTests
    {
        @Test
        @DisplayName( "PCL is registered for new ExtraTypeCollection assigned via write()" )
        void pclRegisteredForNewExtraTypeCollectionAssignedViaWrite()
        {
            list initialList = new list();
            CellSet cell = createCell( "listCell", initialList );
            cell.start( runtime );

            list newList = new list();
            cell.write( newList );

            listener.resetChangeCalled();    // Discard the notification from write() itself.

            // Mutating newList must propagate through the PCL and trigger sendChanged.
            newList.add( "hello" );

            assertTrue( listener.wasChangeCalled(),
                    "Mutating new ExtraTypeCollection after write() must trigger sendChanged" );
        }
    }

    @Nested
    @DisplayName( "Bug Regression Tests" )
    class BugRegressionTests
    {
        /**
         * Bug 1: when start() fails formula validation (sendIsInvalid called after super.start()
         * already set runtime), calling stop() on that instance must not decrement nCount,
         * and subsequent stop() calls must be safe (isStarted() returns false after first stop()).
         */
        @Test
        @DisplayName( "nCount is not corrupted when start() fails formula validation (Bug 1)" )
        @SuppressWarnings( "unchecked" )
        void nCountNotCorruptedWhenStartValidationFails() throws Exception
        {
            // Provide a runtime that returns an XprEval which reports parse errors.
            // A value starting with '=' triggers formula evaluation inside CellValue.update().
            MockRuntime formulaRuntime = new MockRuntime( bus ).withXprEval( new ErrorXprEval() );

            Field nCountField = CellSet.class.getDeclaredField( "nCount" );
            nCountField.setAccessible( true );
            AtomicInteger nCount = (AtomicInteger) nCountField.get( null );

            CellSet cell = createCell( "formulaCell", "=bad_formula" );
            boolean started = cell.start( formulaRuntime );

            assertFalse( started,        "start() must return false when formula has errors" );
            assertFalse( cell.isValid(), "Cell must be marked invalid" );
            assertEquals( 0, nCount.get(), "nCount must remain 0 when validation fails" );

            // stop() on a never-fully-started cell must be a no-op: nCount stays 0
            // and isStarted() returns false so further calls are also no-ops.
            cell.stop();
            assertEquals( 0, nCount.get(), "nCount must remain 0 after stop() on a failed cell" );
            cell.stop();    // Second call must be safe (isStarted() is now false)
            assertEquals( 0, nCount.get(), "nCount must remain 0 after repeated stop() calls" );

            created.remove( cell );    // Already stopped; prevent double-stop in tearDown.
        }

        /**
         * Bug 2: the dispatcher consumer was tied to the first CellSet instance's listener.
         * The fix introduces an instances registry so each cell dispatches through its own
         * instance. This test verifies the registry is correctly maintained.
         */
        @Test
        @DisplayName( "instances registry is populated on start() and cleaned on stop() (Bug 2)" )
        @SuppressWarnings( "unchecked" )
        void instancesRegistryMaintainedCorrectly() throws Exception
        {
            Field instancesField = CellSet.class.getDeclaredField( "instances" );
            instancesField.setAccessible( true );
            Map<?,?> reg = (Map<?,?>) instancesField.get( null );

            CellSet a = createCell( "regA", "1" );
            CellSet b = createCell( "regB", "2" );

            a.start( runtime );
            b.start( runtime );

            assertTrue( reg.containsKey( "regA" ), "instances must contain 'regA' after start()" );
            assertTrue( reg.containsKey( "regB" ), "instances must contain 'regB' after start()" );

            // Stopping the first instance must not remove the second from the registry.
            a.stop();
            assertFalse( reg.containsKey( "regA" ), "instances must not contain 'regA' after stop()" );
            assertTrue(  reg.containsKey( "regB" ), "'regB' must still be present after 'regA' stops" );

            b.stop();
            assertFalse( reg.containsKey( "regB" ), "instances must not contain 'regB' after stop()" );

            created.clear();    // Already stopped; prevent double-stop in tearDown.
        }

        /**
         * Bug 2 (functional): stopping the first-registered instance while another is still
         * running must not prevent the remaining instance from delivering change notifications
         * through write(), which goes directly through the instance's own sendChanged().
         */
        @Test
        @DisplayName( "Remaining instance still delivers change notifications after first instance stops (Bug 2)" )
        void remainingInstanceDeliversChangesAfterFirstInstanceStops()
        {
            TestListener listenerA = new TestListener();
            TestListener listenerB = new TestListener();

            // Create two cells each with its own listener.
            CellSet a = createCellWithListener( "firstA", "0", listenerA );
            CellSet b = createCellWithListener( "secondB", "0", listenerB );

            a.start( runtime );
            b.start( runtime );

            // Stop the first-started instance (the one whose 'this' was previously captured
            // by the shared dispatcher consumer).
            a.stop();
            created.remove( a );

            // The second instance must still respond to write() correctly.
            listenerB.resetChangeCalled();
            b.write( "updated" );

            assertTrue( listenerB.wasChangeCalled(),
                    "Remaining instance must still fire change notifications after the first stops" );

            b.stop();
            created.remove( b );
        }

        /**
         * Circular reference detection must report exactly one error, not two.
         * Before the fix, hasCircularRef() called sendIsInvalid() internally
         * and start() called it again, producing duplicate error messages.
         */
        @Test
        @DisplayName( "Circular reference reports exactly one error (double sendIsInvalid fix)" )
        void circularReferenceReportsExactlyOneError()
        {
            CountingListener countingListener = new CountingListener();
            MockRuntime selfRefRuntime = new MockRuntime( bus )
                    .withXprEval( new SelfRefXprEval( "selfRef" ) );

            CellSet cell = createCellWithListener( "selfRef", "=selfRef + 1", countingListener );
            boolean started = cell.start( selfRefRuntime );

            assertFalse( started, "start() must return false for circular reference" );
            assertEquals( 1, countingListener.getErrorCount(),
                    "Circular reference must produce exactly one error message" );

            cell.stop();
            created.remove( cell );
        }

        /**
         * A constant (non-formula) cell must not have its value overwritten when
         * onDeviceChanged() is invoked.  Before the fix, the else branch in
         * onDeviceChanged() set value = devValue for non-formula cells.
         */
        @Test
        @DisplayName( "Constant cell value is not overwritten by onDeviceChanged() (else branch fix)" )
        void constantCellValueNotOverwrittenByOnDeviceChanged() throws Exception
        {
            CellSet cell = createCell( "constCell", "42" );
            cell.start( runtime );

            Field mapField = CellSet.class.getDeclaredField( "map" );
            mapField.setAccessible( true );
            @SuppressWarnings( "unchecked" )
            Map<String,?> cellMap = (Map<String,?>) mapField.get( null );
            Object cv = cellMap.get( "constCell" );
            assertNotNull( cv, "CellValue must exist in map after start()" );

            Method m = cv.getClass().getDeclaredMethod( "onDeviceChanged", String.class, Object.class );
            m.setAccessible( true );
            Object result = m.invoke( cv, "someOtherDevice", "unexpected" );

            assertEquals( "42", result,
                    "Constant cell value must not be overwritten by device change events" );
        }

        /**
         * After all instances stop (full teardown) and a new instance starts,
         * the Dispatcher must be recreated.  This validates the double-check
         * pattern in stop() that prevents tearing down while a concurrent
         * start() has re-incremented nCount.
         */
        @Test
        @DisplayName( "Dispatcher is recreated after full teardown and new start (teardown race fix)" )
        void dispatcherRecreatedAfterFullTeardownAndNewStart() throws Exception
        {
            CellSet a = createCell( "tdA", "10" );
            CellSet b = createCell( "tdB", "20" );

            a.start( runtime );
            b.start( runtime );

            a.stop();
            created.remove( a );
            b.stop();
            created.remove( b );

            Field disField = CellSet.class.getDeclaredField( "dis" );
            disField.setAccessible( true );
            assertNull( disField.get( null ), "Dispatcher must be null after full teardown" );

            CellSet c = createCell( "tdC", "30" );
            c.start( runtime );

            assertNotNull( disField.get( null ),
                    "Dispatcher must be recreated when a new instance starts after full teardown" );
        }
    }

    //------------------------------------------------------------------------//
    // HELPERS
    //------------------------------------------------------------------------//

    private CellSet createCell( String name, Object value )
    {
        return createCellWithListener( name, value, listener );
    }

    private CellSet createCellWithListener( String name, Object value, IController.Listener l )
    {
        Map<String,Object> config = new HashMap<>();
        config.put( KEY_VALUE, value );

        CellSet cs = new CellSet();
        cs.set( name, config, l );
        created.add( cs );

        return cs;
    }

    //------------------------------------------------------------------------//
    // TEST LISTENER
    //------------------------------------------------------------------------//

    private static final class TestListener implements IController.Listener
    {
        private final AtomicBoolean           changeCalled = new AtomicBoolean( false );
        private final AtomicReference<Object> lastValue    = new AtomicReference<>();

        @Override
        public void onReaded( String deviceName, Object value )
        {
            lastValue.set( value );
        }

        @Override
        public void onChanged( String deviceName, Object value )
        {
            lastValue.set( value );
            changeCalled.set( true );
        }

        @Override
        public void onError( ILogger.Level level, String message, String device ) {}

        boolean wasChangeCalled() { return changeCalled.get(); }

        void resetChangeCalled()
        {
            changeCalled.set( false );
            lastValue.set( null );
        }
    }

    //------------------------------------------------------------------------//
    // COUNTING LISTENER — tracks the number of onError() calls
    //------------------------------------------------------------------------//

    private static final class CountingListener implements IController.Listener
    {
        private final AtomicInteger errorCount = new AtomicInteger( 0 );

        @Override public void onReaded( String deviceName, Object value )                  {}
        @Override public void onChanged( String deviceName, Object value )                 {}
        @Override public void onError( ILogger.Level level, String message, String device ) { errorCount.incrementAndGet(); }

        int getErrorCount() { return errorCount.get(); }
    }

    //------------------------------------------------------------------------//
    // MOCK XPREVAL — returns one parse error so CellValue treats the formula as invalid
    //------------------------------------------------------------------------//

    private static final class ErrorXprEval implements IXprEval
    {
        private static final List<ICandi.IError> ERRORS = Collections.singletonList(
            new ICandi.IError()
            {
                @Override public String message() { return "mock parse error"; }
                @Override public int    line()    { return 1; }
                @Override public int    column()  { return 1; }
            }
        );

        @Override public IXprEval                     build( String x )                                      { return this; }
        @Override public IXprEval                     build( String x, Consumer<Object> c, Function<String,String[]> f ) { return this; }
        @Override public List<ICandi.IError>          getErrors()                                            { return ERRORS; }
        @Override public boolean                      set( String n, Object v )                              { return false; }
        @Override public Object                       eval()                                                 { return null; }
        @Override public Object                       eval( String n, Object v )                             { return null; }
        @Override public Map<String,Object>           getVars()                                              { return Collections.emptyMap(); }
        @Override public boolean                      isBoolean()                                            { return false; }
        @Override public boolean                      isFuturing()                                           { return false; }
        @Override public IXprEval                     cancel()                                               { return this; }
        @Override public IXprEval                     close()                                                { return this; }
        @Override public boolean                      isBasicDataType( Object v )                            { return false; }
        @Override public boolean                      isExtendedDataType( Object v )                         { return false; }
        @Override public ExtraType<?>                 newExtendedType( String s )                            { return null; }
        @Override public String[]                     getOperators()                                         { return new String[0]; }
        @Override public String[]                     getFunctions()                                         { return new String[0]; }
        @Override public Map<String,List<String>>     getExtendedTypes()                                     { return Collections.emptyMap(); }
        @Override public String                       about()                                                { return "ErrorXprEval"; }
    }

    //------------------------------------------------------------------------//
    // MOCK XPREVAL — valid formula whose getVars() contains the cell's own name (self-reference)
    //------------------------------------------------------------------------//

    private static final class SelfRefXprEval implements IXprEval
    {
        private final Map<String,Object> vars;

        SelfRefXprEval( String selfName )
        {
            vars = new HashMap<>();
            vars.put( selfName, null );
        }

        @Override public IXprEval                     build( String x )                                      { return this; }
        @Override public IXprEval                     build( String x, Consumer<Object> c, Function<String,String[]> f ) { return this; }
        @Override public List<ICandi.IError>          getErrors()                                            { return Collections.emptyList(); }
        @Override public boolean                      set( String n, Object v )                              { return false; }
        @Override public Object                       eval()                                                 { return null; }
        @Override public Object                       eval( String n, Object v )                             { return null; }
        @Override public Map<String,Object>           getVars()                                              { return vars; }
        @Override public boolean                      isBoolean()                                            { return false; }
        @Override public boolean                      isFuturing()                                           { return false; }
        @Override public IXprEval                     cancel()                                               { return this; }
        @Override public IXprEval                     close()                                                { return this; }
        @Override public boolean                      isBasicDataType( Object v )                            { return false; }
        @Override public boolean                      isExtendedDataType( Object v )                         { return false; }
        @Override public ExtraType<?>                 newExtendedType( String s )                            { return null; }
        @Override public String[]                     getOperators()                                         { return new String[0]; }
        @Override public String[]                     getFunctions()                                         { return new String[0]; }
        @Override public Map<String,List<String>>     getExtendedTypes()                                     { return Collections.emptyMap(); }
        @Override public String                       about()                                                { return "SelfRefXprEval"; }
    }

    //------------------------------------------------------------------------//
    // MOCK EVENT BUS
    //------------------------------------------------------------------------//

    private static final class MockEventBus implements IEventBus
    {
        private final AtomicInteger addCount    = new AtomicInteger( 0 );
        private final AtomicInteger removeCount = new AtomicInteger( 0 );

        int getAddCount()    { return addCount.get(); }
        int getRemoveCount() { return removeCount.get(); }

        @Override
        public IEventBus add( Listener<?> listener, Class<? extends Message>... eventTypes )
        {
            addCount.incrementAndGet();
            return this;
        }

        @Override
        public boolean remove( Listener<?> listener )
        {
            removeCount.incrementAndGet();
            return true;
        }

        @Override public IEventBus   post( Message message )                          { return this; }
        @Override public IEventBus   post( Message message, long delay )              { return this; }
        @Override public Cancellable post( Message m, long delay, long interval )     { return () -> {}; }
        @Override public IEventBus   pause()                                          { return this; }
        @Override public IEventBus   resume()                                         { return this; }
        @Override public boolean     isPaused()                                       { return false; }
        @Override public IEventBus   start()                                          { return this; }
        @Override public IEventBus   flush()                                          { return this; }
        @Override public IEventBus   stop()                                           { return this; }
        @Override public IEventBus   add( Listener listener )                         { return this; }
        @Override public int         getSpeed()                                       { return 0; }
        @Override public int         getPending()                                     { return 0; }
    }

    //------------------------------------------------------------------------//
    // MOCK RUNTIME
    //------------------------------------------------------------------------//

    private static final class MockRuntime implements IRuntime
    {
        private final IEventBus bus;
        private       IXprEval  xprEval = null;

        MockRuntime( IEventBus bus ) { this.bus = bus; }

        /** Configures the IXprEval instance returned by newXprEval(). */
        MockRuntime withXprEval( IXprEval eval ) { this.xprEval = eval; return this; }

        @Override
        @SuppressWarnings( "unchecked" )
        public <T> T getFromConfig( String module, String key, T defaultValue ) { return defaultValue; }

        @Override public IEventBus       bus()                           { return bus; }
        @Override public IXprEval        newXprEval()                    { return xprEval; }
        @Override public ICandi.IBuilder newLanguageBuilder()            { return null; }
        @Override public boolean         isGridNode()                    { return false; }
        @Override public IRuntime        log( ILogger.Level l, Object m ){ return this; }
        @Override public boolean         isLoggable( ILogger.Level l )   { return false; }
        @Override public IRuntime        exit( int millis )              { return this; }
        @Override public IRuntime        exit( int m, int c, Object s )  { return this; }
        @Override public ICommand[]      all( String... t )              { return new ICommand[0]; }
        @Override public ICommand        get( String name )              { return null; }
        @Override public void            addModel( String sModelJSON )   {}
        @Override public void            add( ICommand... command )      {}
        @Override public boolean         remove( ICommand... command )   { return false; }
        @Override public boolean         isNameOfGroup( String name )    { return false; }
        @Override public String[]        getGroupMemberNames( String g ) { return new String[0]; }
        @Override public IDevice[]       getMembersOf( String... g )     { return new IDevice[0]; }
        @Override public IDevice[]       getInAnyGroup( String... g )    { return new IDevice[0]; }
        @Override public IDevice[]       getInAllGroups( String... g )   { return new IDevice[0]; }
    }

    //------------------------------------------------------------------------//
    // MAIN METHOD FOR STANDALONE TESTING
    //------------------------------------------------------------------------//

    /**
     * Main method for running tests without JUnit runner.
     * Provides basic test execution and reporting.
     */
    public static void main( String[] args )
    {
        System.out.println( "==================================================" );
        System.out.println( "            CELLSET TEST SUITE                    " );
        System.out.println( "==================================================" );
        System.out.println();

        int passed = 0;
        int failed = 0;
        int total  = 0;

        // Listener Registration Tests
        System.out.println( "--- Listener Registration Tests ---" );
        total++; if( runTest( "Only one listener registered", () -> withSetUp( t -> t.new ListenerRegistrationTests().onlyOneListenerRegisteredAcrossMultipleInstances() ) ) ) passed++; else failed++;
        System.out.println();

        // Lifecycle Tests
        System.out.println( "--- Lifecycle Tests ---" );
        total++; if( runTest( "Listener removed on last stop",   () -> withSetUp( t -> t.new LifecycleTests().listenerRemovedOnLastInstanceStop() ) ) ) passed++; else failed++;
        total++; if( runTest( "No stale map entry after stop",   () -> withSetUp( t -> t.new LifecycleTests().noStaleMapEntryAfterStop() ) ) ) passed++; else failed++;
        System.out.println();

        // Property Change Listener Tests
        System.out.println( "--- Property Change Listener Tests ---" );
        total++; if( runTest( "PCL registered for new collection via write", () -> withSetUp( t -> t.new PropertyChangeListenerTests().pclRegisteredForNewExtraTypeCollectionAssignedViaWrite() ) ) ) passed++; else failed++;
        System.out.println();

        // Bug Regression Tests
        System.out.println( "--- Bug Regression Tests ---" );
        total++; if( runTest( "nCount not corrupted on failed start (Bug 1)",           () -> withSetUp( t -> { try { t.new BugRegressionTests().nCountNotCorruptedWhenStartValidationFails(); } catch( Exception e ) { throw new RuntimeException( e ); } } ) ) ) passed++; else failed++;
        total++; if( runTest( "instances registry maintained (Bug 2)",                  () -> withSetUp( t -> { try { t.new BugRegressionTests().instancesRegistryMaintainedCorrectly(); } catch( Exception e ) { throw new RuntimeException( e ); } } ) ) ) passed++; else failed++;
        total++; if( runTest( "Remaining instance delivers changes after first stops (Bug 2)", () -> withSetUp( t -> t.new BugRegressionTests().remainingInstanceDeliversChangesAfterFirstInstanceStops() ) ) ) passed++; else failed++;
        total++; if( runTest( "Circular reference reports exactly one error",                  () -> withSetUp( t -> t.new BugRegressionTests().circularReferenceReportsExactlyOneError() ) ) ) passed++; else failed++;
        total++; if( runTest( "Constant cell not overwritten by onDeviceChanged",              () -> withSetUp( t -> { try { t.new BugRegressionTests().constantCellValueNotOverwrittenByOnDeviceChanged(); } catch( Exception e ) { throw new RuntimeException( e ); } } ) ) ) passed++; else failed++;
        total++; if( runTest( "Dispatcher recreated after full teardown",                      () -> withSetUp( t -> { try { t.new BugRegressionTests().dispatcherRecreatedAfterFullTeardownAndNewStart(); } catch( Exception e ) { throw new RuntimeException( e ); } } ) ) ) passed++; else failed++;
        System.out.println();

        // Summary
        System.out.println( "==================================================" );
        System.out.println( "                   TEST SUMMARY                    " );
        System.out.println( "==================================================" );
        System.out.println( "Total Tests:  " + total );
        System.out.println( "Passed:       " + passed + " (" + (passed * 100 / total) + "%)" );
        System.out.println( "Failed:       " + failed + " (" + (failed * 100 / total) + "%)" );
        System.out.println( "==================================================" );

        if( failed == 0 )
        {
            System.out.println( "\n✓ ALL TESTS PASSED!" );
            System.exit( 0 );
        }
        else
        {
            System.out.println( "\n✗ SOME TESTS FAILED!" );
            System.exit( 1 );
        }
    }

    /**
     * Runs a single test and returns true if it passes.
     */
    private static boolean runTest( String name, Runnable test )
    {
        try
        {
            test.run();
            System.out.println( "  ✓ " + name );
            return true;
        }
        catch( AssertionError e )
        {
            System.out.println( "  ✗ " + name + ": " + e.getMessage() );
            return false;
        }
        catch( Exception e )
        {
            System.out.println( "  ✗ " + name + ": " + e.getClass().getSimpleName() + " - " + e.getMessage() );
            return false;
        }
    }

    /**
     * Creates a fresh test instance, runs setUp(), executes the given action,
     * then runs tearDown() — mirroring JUnit's lifecycle for standalone use.
     */
    @FunctionalInterface
    private interface TestAction
    {
        void run( CellSetTest t ) throws Exception;
    }

    private static void withSetUp( TestAction action )
    {
        CellSetTest t = new CellSetTest();

        try
        {
            t.setUp();
            action.run( t );
        }
        catch( AssertionError e )
        {
            throw e;
        }
        catch( Exception e )
        {
            throw new RuntimeException( e );
        }
        finally
        {
            t.tearDown();
        }
    }
}
