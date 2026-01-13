/*
 * Copyright Francisco José Morero Peyrona.
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

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class Pin implements IPin
{
    private static final int CLICK_LAPSE        = 400;     // Max interval of millisecs between ON (pushed) and OFF (released) to consider it as click event
    private static final int DOUBLE_CLICK_LAPSE = 800;     // Max interval of millisecs between two consecutive clicks to consider it as double-click event

    private final int     nWhich;
    private final boolean isInput;
    private final int     nDebounce;
    private final boolean bInvert;
    private final boolean isButton;                                   // Considers this input as being a push-button and launches button events: "pressed", "released", "clicked" and "doubleclicked"
    private final WiringPi.PinCallback callback;                      // Callback reference (kept to avoid GC)
    private final Consumer<String> onError;                           // Function "pointer" to report errors
    private final AtomicLong    nWhenPressed  = new AtomicLong( 0 );  // TimeStamp when button was pressed (to mesure the time between pressed and released) (Can not be volatile because 32 bits systems)
    private final AtomicLong    nWhenClicked  = new AtomicLong( 0 );  // TimeStamp when button was clicked (to mesure the time between 2 consecutive clicks) (Can not be volatile because 32 bits systems)
    private final AtomicBoolean isDispatching = new AtomicBoolean( false );

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    /**
     * Creates an instance to manage a physical RPi GPIO pin.
     *
     * @param nWhich     0 >= x <= 31
     * @param nPull      PULL_UP, PULL_DOWN or PULL_OFF
     * @param nDebounce  In milliseconds: min == 0; default == 0.
     * @param isDigital
     * @param isInput
     * @param isInverted
     * @param isButton
     * @param isBCM      Numbering model: BCM or WiringPi
     * @param callback
     * @param onError
     */
    Pin( int     nWhich,
         int     nPull,
         int     nDebounce,
         boolean isInput,
         boolean isInverted,
         boolean isButton,
         boolean isBCM,
         Consumer<Object> callback,
         Consumer<String> onError )
    {
        // This constructor does not need to check the consistency of the parameters because it is done at RPiGpioPin class

        this.isInput   = isInput;
        this.nDebounce = ((nDebounce < 0) ? 0 : nDebounce);
        this.nWhich    = createNativePin( isBCM, nWhich, nPull, isInput );
        this.bInvert   = isInverted;
        this.isButton  = isButton;
        this.onError   = onError;

        WiringPi.PinCallback cb = null;

        if( callback != null )
        {
            cb = createCallback( callback );

            // Use WiringPi 3.x API with built-in debounce (convert ms to microseconds)
            long debounceUs = this.nDebounce * 1000L;
            int  nErrCode   = WiringPi.setCallBack( nWhich, WiringPi.INT_EDGE_BOTH, cb, debounceUs );

            if( nErrCode != 0 )
            {
                cb = null;
                throw new MingleException( "Can not set Callback for pin "+ nWhich +". Error: "+ nErrCode );
            }
        }

        this.callback = cb;
    }

    //------------------------------------------------------------------------//

    @Override
    public boolean isInput()
    {
        return isInput;
    }

    @Override
    public Object read()
    {
        // Not needed -->  if( ! isOutput() ) { ... }
        // because you can still use GPIO.input() for a pin setted up as an OUTPUT.
        // Seen here: https://www.raspberrypi.org/forums/viewtopic.php?t=136104

        boolean bValue = (WiringPi.digitalRead( nWhich ) == WiringPi.HIGH);

        return (bInvert ? (! bValue) : bValue);
    }

    @Override
    public void write( boolean value )
    {
        // As bButton is only for Sensors, it has no sense to consider it in this method

            if( isInput )
            throw new MingleException( "Pin "+ nWhich +" is for input: can not write." );

        if( bInvert )
            value = ! value;

        WiringPi.digitalWrite( nWhich, (value ? WiringPi.HIGH : WiringPi.LOW) );
    }

    @Override
    public void cleanup()
    {
        if( callback != null )                                // WiringPi 3.x: Properly stop the ISR handler to release GPIO resources
            WiringPi.wiringPiISRStop( nWhich );

        if( ! isInput )
            WiringPi.digitalWrite( nWhich, WiringPi.LOW );    // Needed because pins can keep their state from one use to next use

        WiringPi.pinMode( nWhich, WiringPi.INPUT );           // It is a good idea to set used pins back to input
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
            hash = 97 * hash + this.nWhich;
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final Pin other = (Pin) obj;

        return this.nWhich == other.nWhich;
    }

    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//

    private int createNativePin( boolean isBCM, int pinNumber, int nPull, boolean bInput )
    {
        // WiringPi.PULL_OFF can cause pin to become unstable and unpredictable (and even physical damage).
        // This is generally not desired behavior for most applications.

        // SDA.1 pin has a physical pull-up resistor -> WiringPi #  8,  BCM # 2
        // SDC.1 pin has a physical pull-up resistor -> WiringPi #  9,  BCM # 3
        // SDA.0 pin has a physical pull-up resistor -> WiringPi # 30,  BCM # 0
        // SDC.0 pin has a physical pull-up resistor -> WiringPi # 31,  BCM # 1

        Integer[] aPins = (isBCM ? new Integer[] { 0, 1, 2, 3 }
                                 : new Integer[] { 8, 9, 30, 31 });

        if( ! UtilColls.contains( aPins, pinNumber ) )
            WiringPi.pullUpDnControl( pinNumber, nPull );

        WiringPi.pinMode( pinNumber, (bInput ? WiringPi.INPUT : WiringPi.OUTPUT) );

        return pinNumber;
    }

    /**
     * Creates a callback that wraps the Consumer.
     * <p>
     * The callback receives WPIWfiStatus with interrupt details:
     * <ul>
     *   <li>statusOK: 1 = success, 0 = timeout, -1 = error</li>
     *   <li>pinBCM: GPIO pin number in BCM format</li>
     *   <li>edge: INT_EDGE_FALLING (1) or INT_EDGE_RISING (2)</li>
     *   <li>timeStamp_us: Microsecond timestamp of interrupt</li>
     * </ul>
     */
    private WiringPi.PinCallback createCallback( final Consumer<Object> callback )
    {
        return  ( status, userdata ) ->
                {
                    if( status.statusOK != 1 )   // Check if interrupt was processed successfully
                        return;

                    if( isDispatching.get() )    // To avoid re-entrance
                        return;

                    isDispatching.set( true );

                    try
                    {
                        if( isButton )  actAsButton( (boolean) read(), callback );
                        else            callback.accept( read() );                  // Normal mode: emit the raw value (true/false)
                    }
                    catch( Exception exc )
                    {
                        onError.accept( UtilStr.toStringBrief( exc ) );
                    }
                    finally
                    {
                        isDispatching.set( false );   // It is crucial to ensure this is false
                    }
                };
    }

    private void actAsButton( final boolean bON, final Consumer<Object> callback )
    {
        long now = System.currentTimeMillis();

        if( bON )
        {
            nWhenPressed.set( now );
            callback.accept( "pressed" );
        }
        else
        {
            callback.accept( "released" );

            if( (now - nWhenPressed.get()) <= CLICK_LAPSE )
            {
                callback.accept( "clicked" );

                if( (now - nWhenClicked.get()) <= DOUBLE_CLICK_LAPSE )
                {
                    callback.accept( "doubleclicked" );

                    nWhenClicked.set( 0 );     // It is needed to be reseted
                }
                else
                {
                    nWhenClicked.set( now );
                }
            }
        }
    }
}
