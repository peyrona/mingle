/*
 * Copyright Francisco Morero Peyrona.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.peyrona.mingle.controllers.rpi;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test class for RPiGpioPin controller.
 * <p>
 * Tests include:
 * - Faked mode tests (run on any machine)
 * - Real hardware tests (run only on Raspberry Pi with LED on pin 5)
 * <p>
 * Hardware setup for real tests:
 * - LED connected to WiringPi pin 5 (BCM pin 24) with appropriate resistor
 * - LED anode (+) to pin, cathode (-) to GND through resistor (330-470 ohm)
 *
 * @author Francisco Jose Morero Peyrona
 */
public class RPiGpioPinTest
{
    private static final int LED_PIN = 5;   // WiringPi numbering

    private RPiGpioPin      controller;
    private TestListener    listener;
    private MockRuntime     runtime;

    //------------------------------------------------------------------------//

    @BeforeEach
    void setUp()
    {
        controller = new RPiGpioPin();
        listener   = new TestListener();
        runtime    = new MockRuntime();
    }

    @AfterEach
    void tearDown()
    {
        if( controller != null )
            controller.stop();
    }

    //------------------------------------------------------------------------//
    // FAKED MODE TESTS (run on any machine)
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName( "Faked Mode Tests" )
    class FakedModeTests
    {
        @BeforeEach
        void setFakedMode()
        {
            // Ensure faked mode is enabled for these tests
            System.setProperty( "mingle.faked", "true" );
        }

        @AfterEach
        void clearFakedMode()
        {
            System.clearProperty( "mingle.faked" );
        }

        @Test
        @DisplayName( "Should create controller in faked mode" )
        void testControllerCreation()
        {
            Map<String,Object> config = createLedConfig();

            controller.set( "test_led", config, listener );
            controller.start( runtime );

            assertTrue( controller.isValid(), "Controller should be valid" );
            assertFalse( listener.hasError(), "Should not have errors: " + listener.getLastError() );
        }

        @Test
        @DisplayName( "Should write true (LED ON) in faked mode" )
        void testWriteTrue()
        {
            Map<String,Object> config = createLedConfig();

            controller.set( "test_led", config, listener );
            controller.start( runtime );

            controller.write( true );

            assertTrue( listener.wasChangeCalled(), "Change callback should be called" );
            assertEquals( true, listener.getLastValue(), "Last value should be true" );
        }

        @Test
        @DisplayName( "Should write false (LED OFF) in faked mode" )
        void testWriteFalse()
        {
            Map<String,Object> config = createLedConfig();

            controller.set( "test_led", config, listener );
            controller.start( runtime );

            controller.write( false );

            assertTrue( listener.wasChangeCalled(), "Change callback should be called" );
            assertEquals( false, listener.getLastValue(), "Last value should be false" );
        }

        @Test
        @DisplayName( "Should toggle LED on/off in faked mode" )
        void testToggle()
        {
            Map<String,Object> config = createLedConfig();

            controller.set( "test_led", config, listener );
            controller.start( runtime );

            // Turn ON
            controller.write( true );
            assertEquals( true, listener.getLastValue(), "LED should be ON" );

            // Turn OFF
            controller.write( false );
            assertEquals( false, listener.getLastValue(), "LED should be OFF" );

            // Turn ON again
            controller.write( true );
            assertEquals( true, listener.getLastValue(), "LED should be ON again" );
        }

        @Test
        @DisplayName( "Should reject non-boolean values" )
        void testInvalidValue()
        {
            Map<String,Object> config = createLedConfig();

            controller.set( "test_led", config, listener );
            controller.start( runtime );

            controller.write( "invalid" );

            assertTrue( listener.hasWriteError(), "Should have write error for non-boolean value" );
        }

        @Test
        @DisplayName( "Should reject invalid pin number" )
        void testInvalidPin()
        {
            Map<String,Object> config = new HashMap<>();
            config.put( "pin", 99 );        // Invalid pin number (must be 0-31)
            config.put( "mode", "output" );

            controller.set( "test_led", config, listener );
            controller.start( runtime );

            assertFalse( controller.isValid(), "Controller should be invalid for pin 99" );
        }

        @Test
        @DisplayName( "Should handle stop and cleanup" )
        void testStopCleanup()
        {
            Map<String,Object> config = createLedConfig();

            controller.set( "test_led", config, listener );
            controller.start( runtime );
            controller.write( true );

            controller.stop();

            // Controller should handle stop gracefully
            assertFalse( listener.hasError(), "Should not have errors during stop" );
        }
    }

    //------------------------------------------------------------------------//
    // REAL HARDWARE TESTS (run only on Raspberry Pi)
    //------------------------------------------------------------------------//

    @Nested
    @DisplayName( "Real Hardware Tests (LED on pin 5)" )
    class RealHardwareTests
    {
        @Test
        @DisplayName( "Should turn LED ON" )
        void testLedOn() throws InterruptedException
        {
            Map<String,Object> config = createLedConfig();

            controller.set( "led_pin5", config, listener );
            controller.start( runtime );

            assertTrue( controller.isValid(), "Controller should be valid on RPi" );

            controller.write( true );
            System.out.println( "LED should be ON - verify visually" );

            Thread.sleep( 1000 );   // Wait 1 second to observe

            assertFalse( listener.hasError(), "Should not have errors" );
        }

        @Test
        @DisplayName( "Should turn LED OFF" )
        void testLedOff() throws InterruptedException
        {
            Map<String,Object> config = createLedConfig();

            controller.set( "led_pin5", config, listener );
            controller.start( runtime );

            controller.write( false );
            System.out.println( "LED should be OFF - verify visually" );

            Thread.sleep( 1000 );

            assertFalse( listener.hasError(), "Should not have errors" );
        }

        @Test
        @DisplayName( "Should blink LED 5 times" )
        void testBlinkLed() throws InterruptedException
        {
            Map<String,Object> config = createLedConfig();

            controller.set( "led_pin5", config, listener );
            controller.start( runtime );

            System.out.println( "LED should blink 5 times..." );

            for( int i = 0; i < 5; i++ )
            {
                controller.write( true );
                Thread.sleep( 300 );
                controller.write( false );
                Thread.sleep( 300 );
            }

            assertFalse( listener.hasError(), "Should not have errors during blink" );
            System.out.println( "Blink test completed" );
        }
    }

    //------------------------------------------------------------------------//
    // HELPER METHODS
    //------------------------------------------------------------------------//

    private Map<String,Object> createLedConfig()
    {
        Map<String,Object> config = new HashMap<>();
        config.put( "pin", LED_PIN );
        config.put( "mode", "output" );
        // pull defaults to "up" for output mode
        return config;
    }

    static boolean isRaspberryPi()
    {
        return UtilSys.isRaspberryPi();
    }

    //------------------------------------------------------------------------//
    // TEST LISTENER (Mock)
    //------------------------------------------------------------------------//

    private static class TestListener implements IController.Listener
    {
        private final AtomicReference<Object>  lastValue    = new AtomicReference<>();
        private final AtomicReference<String>  lastError    = new AtomicReference<>();
        private final AtomicBoolean            changeCalled = new AtomicBoolean( false );
        private final AtomicBoolean            writeError   = new AtomicBoolean( false );

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

        public void onInvalid( String deviceName, String message )
        {
            lastError.set( "INVALID: " + message );
        }

        public void onReadError( String deviceName, Throwable error )
        {
            lastError.set( "READ_ERROR: " + error.getMessage() );
        }

        public void onWriteError( String deviceName, Object attemptedValue, Throwable error )
        {
            lastError.set( "WRITE_ERROR: " + error.getMessage() );
            writeError.set( true );
        }

        Object  getLastValue()      { return lastValue.get(); }
        String  getLastError()      { return lastError.get(); }
        boolean hasError()          { return lastError.get() != null; }
        boolean hasWriteError()     { return writeError.get(); }
        boolean wasChangeCalled()   { return changeCalled.get(); }

        void reset()
        {
            lastValue.set( null );
            lastError.set( null );
            changeCalled.set( false );
            writeError.set( false );
        }

        @Override
        public void onError( ILogger.Level level, String message, String device )
        {
            // Warnings are not errors
            if( level == ILogger.Level.SEVERE )
                lastError.set( "GENERIC_ERROR: " + message );
        }
    }

    //------------------------------------------------------------------------//
    // MOCK RUNTIME
    //------------------------------------------------------------------------//

    private static class MockRuntime implements IRuntime
    {
        @Override
        @SuppressWarnings( "unchecked" )
        public <T> T getFromConfig( String section, String key, T defaultValue )
        {
            // Return WiringPi numbering model (not BCM)
            if( "common".equals( section ) && "RPiNumberingModel".equals( key ) )
                return (T) "WiringPi";

            return defaultValue;
        }

        @Override
        public IRuntime exit( int millis )
        {
            throw new RuntimeException( "Runtime exit called with millis: " + millis );
        }

        // Implement other IRuntime methods with defaults or no-op

        @Override public com.peyrona.mingle.lang.interfaces.exen.IEventBus bus()  { return null; }
        @Override public java.util.function.Function<String,String[]> newGroupWiseFn() { return (s) -> new String[0]; }
        @Override public com.peyrona.mingle.lang.interfaces.IXprEval newXprEval() { return null; }
        @Override public com.peyrona.mingle.lang.interfaces.ICandi.IBuilder newLanguageBuilder() { return null; }
        @Override public boolean isGridNode() { return false; }
        @Override public IRuntime log( ILogger.Level level, Object message ) { System.out.println( "[" + level + "] " + message ); return this; }
        @Override public boolean isLoggable( ILogger.Level level ) { return true; }
        @Override public com.peyrona.mingle.lang.interfaces.commands.ICommand[] all( String... sCommandType ) { return new com.peyrona.mingle.lang.interfaces.commands.ICommand[0]; }
        @Override public com.peyrona.mingle.lang.interfaces.commands.ICommand get( String name ) { return null; }
        @Override public void add( com.peyrona.mingle.lang.interfaces.commands.ICommand command ) { /* no-op */ }
        @Override public boolean remove( com.peyrona.mingle.lang.interfaces.commands.ICommand command ) { return false; }
        @Override public boolean isNameOfGroup( String name ) { return false; }
        @Override public com.peyrona.mingle.lang.interfaces.commands.IDevice[] getMembersOf( String... group ) { return new com.peyrona.mingle.lang.interfaces.commands.IDevice[0]; }
        @Override public com.peyrona.mingle.lang.interfaces.commands.IDevice[] getInAnyGroup( String... group ) { return new com.peyrona.mingle.lang.interfaces.commands.IDevice[0]; }
        @Override public com.peyrona.mingle.lang.interfaces.commands.IDevice[] getInAllGroups( String... group ) { return new com.peyrona.mingle.lang.interfaces.commands.IDevice[0]; }
    }
}
