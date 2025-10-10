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

import com.peyrona.mingle.lang.MingleException;
import com.sun.jna.Callback;
import com.sun.jna.Native;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * This class uses JNA to access natively needed C functions in WiringPi library.<br>
 * <br>
 * To install the latest unofficial build of WiringPi:<br>
 *       1. Download from https://github.com/WiringPi/WiringPi: "wiringpi-{version}-armhf.deb" or "wiringpi-{version}-arm64.deb"
 *       3. Uninstall current version (if any): sudo apt-get remove wiringpi -y
 *       4. Install the provided version: sudo dpkg -i {file}.deb
 *       5. Check this file exists: '/usr/lib/libwiringPi.so'
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class WiringPi
{
    //------------------------------------------------------------------------//
    // COPIED FROM wiringPi.h

    // Pin modes
    static final int INPUT            = 0;
    static final int OUTPUT           = 1;
//  static final int PWM_OUTPUT       = 2;
//  static final int GPIO_CLOCK       = 3;
//  static final int SOFT_PWM_OUTPUT  = 4;
//  static final int SOFT_TONE_OUTPUT = 5;
//  static final int PWM_TONE_OUTPUT  = 6;

    static final int LOW  = 0;
    static final int HIGH = 1;

    // PWM
//  static final int PWM_MODE_MS  = 0;
//  static final int PWM_MODE_BAL = 1;

    // Interrupt levels (for wiringPiISR(...))
//  static final int INT_EDGE_SETUP	= 0;      // This level configures the ISR without actually activating it. It's useful for setting up the interrupt behavior but not triggering the ISR immediately.
//  static final int INT_EDGE_FALLING = 1;    // This level triggers the ISR when the GPIO pin transitions from high (1) to low (0). This is often referred to as a "falling edge."
//  static final int INT_EDGE_RISING  = 2;    // This level triggers the ISR when the GPIO pin transitions from low (0) to high (1). This is often referred to as a "rising edge."
    static final int INT_EDGE_BOTH    = 3;    // This level triggers the ISR on both falling and rising edges of the GPIO pin, effectively capturing both transitions.

    // Pull modes (up/down/none)
//  private static final int PUD_OFF  = 0;    // For clarity, it is better to use PULL_OFF
//  private static final int PUD_DOWN = 1;    // For clarity, it is better to use PULL_DOWN
//  private static final int PUD_UP   = 2;    // For clarity, it is better to use PULL_UP

    //------------------------------------------------------------------------//
    // ADDED BY ME

    static final int PULL_OFF  = 0;   // PUD_OFF;
    static final int PULL_DOWN = 1;   // PUD_DOWN;
    static final int PULL_UP   = 2;   // PUD_UP;

    //------------------------------------------------------------------------//

    interface PinCallback extends Callback
    {
        void onChanged();
    }

    //------------------------------------------------------------------------//

    private static volatile boolean bInited = false;
    private static final Object INIT_LOCK = new Object();
    private static final Map<Integer,PinCallback> callbackMap = Collections.synchronizedMap( new WeakHashMap<>() );    // Keep strong references to callbacks to prevent garbage collection

    static boolean isInited()
    {
        return bInited;
    }

    /**
     * Set up RPi.
     * <p>
     * @param bUseWPN If true, then uses WiringPi numbering, else uses Broadcom (BCM) numbering.
     * @throws MingleException if something failed.
     */
    static void setup( boolean bUseBCM ) throws MingleException
    {
        if( ! bInited )
        {
            synchronized( INIT_LOCK )
            {
                if( ! bInited )
                {
                    initializeNative( bUseBCM );
                    bInited = true;
                }
            }
        }
    }

    /**
     * Cleanup resources and callbacks.
     */
    static synchronized void cleanup()
    {
        callbackMap.clear();

        // Cant' do: initialized = false
        // because once the RPi is initialized, this can not be undone.
    }

    /**
     * Thread-safe interrupt registration with callback protection.
     */
    public static synchronized int setCallBack( int pin, int edgeType, PinCallback callback )
    {
        callbackMap.put( pin, callback );    // Store callback to prevent garbage collection

        return wiringPiISR( pin, edgeType, callback );
    }

    //------------------------------------------------------------------------//

    /**
     * Core Functions</p>
     *
     * <p>
     * This sets the mode of a pin to either INPUT, OUTPUT, PWM_OUTPUT or GPIO_CLOCK. Note that only wiringPi pin 1
     * (BCM_GPIO 18) supports PWM output and only wiringPi pin 7 (BCM_GPIO 4) supports CLOCK output modes.
     * </p>
     *
     * <p> <b><i>This function has no effect when in Sys mode.</i></b></p>
     *
     * @see #INPUT
     * @see #OUTPUT
     * @see #PWM_OUTPUT
     * @see <a
     *      href="http://wiringpi.com/reference/core-functions/">http://wiringpi.com/reference/core-functions/</a>
     * @param pin The GPIO pin number. <i>(Depending on how wiringPi was initialized, this may
     *            be the wiringPi pin number, the Broadcom GPIO pin number, or the board header pin number.)</i>
     * @param mode  Pin mode/direction to apply to the selected pin.The following constants are
     *            provided for use with this parameter:
     *            <ul>
     *            <li>INPUT</li>
     *            <li>OUTPUT</li>
     *            <li>PWM_OUTPUT</li>
     *            <li>GPIO_CLOCK</li>
     *            </ul>
     */
    public static native void pinMode( int pin, int mode );

    /**
     * Core Functions</p>
     *
     * This sets the pull-up or pull-down resistor mode on the given pin, which should be set as an
     * input. Unlike the Arduino, the BCM2835 has both pull-up an down internal resistors. The
     * parameter pud should be; PULL_OFF, (no pull up/down), PULL_DOWN (pull to ground) or PULL_UP
     * (pull to 3.3v)
     *
     * This function has no effect when in Sys mode (see above) If you need to activate a
     * pull-up/pull-down, then you can do it with the gpio program in a script before you start your
     * program.
     *
     * @see #PULL_OFF
     * @see #PULL_DOWN
     * @see #PULL_UP
     * @see <a
     *      href="http://wiringpi.com/reference/core-functions/">http://wiringpi.com/reference/core-functions/</a>
     * @param pin The GPIO pin number. <i>(Depending on how wiringPi was initialized, this may
     *            be the wiringPi pin number or the Broadcom GPIO pin number.)</i>
     * @param pud Pull Up/Down internal pin resistance.The following constants are provided for
     *            use with this parameter:
     *            <ul>
     *            <li>PULL_OFF</li>
     *            <li>PULL_DOWN</li>
     *            <li>PULL_UP</li>
     *            </ul>
     */
    public static native void pullUpDnControl( int pin, int pud );

    /**
     * Core Functions</p>
     * <p>
     * Writes the value HIGH or LOW (1 or 0) to the given pin which must have been previously set as
     * an output. WiringPi treats any non-zero number as HIGH, however 0 is the only representation of LOW.
     * </p>
     *
     * @see #HIGH
     * @see #LOW
     * @see <a
     *      href="http://wiringpi.com/reference/core-functions/">http://wiringpi.com/reference/core-functions/</a>
     * @param pin The GPIO pin number. <i>(Depending on how wiringPi was initialized, this may
     *            be the wiringPi pin number or the Broadcom GPIO pin number.)</i>
     * @param value The pin state to write to the selected pin.The following constants are
     *            provided for use with this parameter:
     *            <ul>
     *            <li>HIGH</li>
     *            <li>LOW</li>
     *            </ul>
     */
    public static native void digitalWrite( int pin, int value );

    /**
     * Core Functions</p>
     *
     * <p>
     * This function returns the value read at the given pin. It will be HIGH or LOW (1 or 0)
     * depending on the logic level at the pin.
     * </p>
     *
     * @see <a
     *      href="http://wiringpi.com/reference/core-functions/">http://wiringpi.com/reference/core-functions/</a>
     * @param pin The GPIO pin number. <i>(Depending on how wiringPi was initialized, this may
     *            be the wiringPi pin number or the Broadcom GPIO pin number.)</i>
     * @return If the selected GPIO pin is HIGH, then a value of '1' is returned; else of the pin is
     *         LOW, then a value of '0' is returned.
     */
    public static native int  digitalRead( int pin );

    /**
     * Core Functions</p>
     *
     * <p>
     * This returns the value read on the supplied analog input pin. You will need to register additional analog
     * modules to enable this function for devices such as the Gertboard, quick2Wire analog board, etc.
     * </p>
     *
     * @see <a
     *      href="http://wiringpi.com/reference/core-functions/">http://wiringpi.com/reference/core-functions/</a>
     * @param pin The GPIO pin number. <i>(Depending on how wiringPi was initialized, this may
     *            be the wiringPi pin number or the Broadcom GPIO pin number.)</i>
     * @return Analog value of selected pin.
     */
    public static native int  analogRead( int pin );

    /**
     * Core Functions</p>
     *
     * <p>
     * This writes the given value to the supplied analog pin. You will need to register additional analog modules to
     * enable this function for devices such as the Gertboard.
     * </p>
     *
     * @see <a
     *      href="http://wiringpi.com/reference/core-functions/">http://wiringpi.com/reference/core-functions/</a>
     * @param pin The GPIO pin number. <i>(Depending on how wiringPi was initialized, this may
     *            be the wiringPi pin number or the Broadcom GPIO pin number.)</i>
     * @param value The analog value to assign to the selected pin number.
     */
    public static native void analogWrite( int pin, int value);

    //------------------------------------------------------------------------//

    /**
     * Priority, Interrupt and Thread Functions</p>
     *
     * <p>
     * This function registers a function to received interrupts on the specified pin. The edgeType parameter is either
     * INT_EDGE_FALLING, INT_EDGE_RISING, INT_EDGE_BOTH or INT_EDGE_SETUP. If it is INT_EDGE_SETUP then no
     * initialisation of the pin will happen – it’s assumed that you have already setup the pin elsewhere
     * (e.g. with the gpio program), but if you specify one of the other types, then the pin will be exported and
     * initialised as specified. This is accomplished via a suitable call to the gpio utility program, so it need to
     * be available
     * </p>
     *
     * <p>
     * The pin number is supplied in the current mode – native wiringPi, BCM_GPIO, physical or Sys modes.
     * </p>
     *
     * <p>
     * This function will work in any mode, and does not need root privileges to work.
     * </p>
     *
     * <p>
     * The function will be called when the interrupt triggers. When it is triggered, it’s cleared in the dispatcher
     * before calling your function, so if a subsequent interrupt fires before you finish your handler, then it won’t
     * be missed. (However it can only track one more interrupt, if more than one interrupt fires while one is being
     * handled then they will be ignored)
     * </p>
     *
     * <p>
     * This function is run at a high priority (if the program is run using sudo, or as root) and executes
     * concurrently with the main program. It has full access to all the global variables, open file handles
     * and so on.
     * </p>
     *
     * @see <a
     *      href="http://wiringpi.com/reference/priority-interrupts-and-threads/">http://wiringpi.com/reference/priority-interrupts-and-threads/</a>
     * @param pin The GPIO pin number. <i>(Depending on how wiringPi was initialized, this may
     *            be the wiringPi pin number or the Broadcom GPIO pin number.)</i>
     * @param edgeType The type of pin edge event to watch for: INT_EDGE_FALLING, INT_EDGE_RISING, INT_EDGE_BOTH or INT_EDGE_SETUP.
     * @param callback The callback interface implemented by the consumer.  The 'callback' method of this interface
     *                 will be invoked when the wiringPiISR issues a callback signal.
     * @return The return value is -1 if an error occurred (and errno will be set appropriately), 0
     *         if it timed out, or 1 on a successful interrupt event.
     */
    private static native int  wiringPiISR( int pin, int edgeType, PinCallback callback );

    /**
     * Setup Functions</p>
     *
     * <p>
     * This initializes the wiringPi system and assumes that the calling program is going to be
     * using the wiringPi pin numbering scheme.
     * </p>
     *
     * @see <a href="http://wiringpi.com/reference/setup/">http://wiringpi.com/reference/setup/</a>
     * @return If this function returns a value of '-1' then an error has occurred and the
     *         initialization of the GPIO has failed. A return value of '0' indicates a successful
     *         GPIO initialization.
     */
    private static native int  wiringPiSetup();

    /**
     * Setup Functions</p>
     *
     * <p>
     * This initializes the wiringPi system and assumes that the calling program is going to be
     * using the Broadcom pin numbering scheme.
     * </p>
     *
     * @see <a href="http://wiringpi.com/reference/setup/">http://wiringpi.com/reference/setup/</a>
     * @return If this function returns a value of '-1' then an error has occurred and the
     *         initialization of the GPIO has failed. A return value of '0' indicates a successful
     *         GPIO initialization.
     */
    private static native int  wiringPiSetupGpio();

    private static void initializeNative(boolean bUseBCM) throws MingleException
    {
        File fLib = new File( "/usr/lib/libwiringPi.so" );

        if( ! fLib.exists() )
            throw new MingleException( fLib +": file does not exist. Is WiringPi library installed?" );

        try
        {
            Native.register( "wiringPi" );   // In most cases, JNA will automatically manage the library's lifecycle.
        }
        catch( UnsatisfiedLinkError ule )
        {
            throw new MingleException( ule );
        }

        int nResult = ((! bUseBCM) ? wiringPiSetup()         // Initializes the WiringPi library using the WiringPi pin numbering scheme.
                                   : wiringPiSetupGpio());   // Initializes the WiringPi library using the BCM GPIO numbering scheme.

        if( nResult != 0 )
            throw new MingleException( "Error on WiringPi setup. Result="+ nResult );
    }


// ESTE ES EL OTRO MODO DE HACER LO MISMO (ES MÁS LENTO PERO PERMITE MÁS TIPOS DE OPERACIONES): A MI NO ME
// HACE FALTA PORQUE LAS OPERACIONES QUE YO USO SON MUY BASICAS Y ESTÁN TODAS SOPORTADAS EN EL MODO MÁS RÁPIDO
//
//    interface CWiringPi extends Library
//    {
//        CWiringPi INSTANCE = (CWiringPi)
//            Native.load( "wiringPi", CWiringPi.class );
//
//        void pinMode( int pin, int mode );
//
//        void pullUpDnControl( int pin, int pud );
//
//        void digitalWrite( int pin, int value );
//
//        int  digitalRead( int pin );
//
//        int  analogRead( int pin );
//
//        void analogWrite( int pin, int value);
//
//        int  wiringPiISR( int pin, int edgeType, PinCallback callback );
//
//        int  wiringPiSetup();
//    }
//
//    static synchronized int setup( boolean bUseWPN )
//    {
//        if( nSetupResult != 0 )
//        {
//            nSetupResult = (bUseWPN ? wiringPiSetup() : wiringPiSetupGpio());
//
//            cleanup();
//        }
//
//        return nSetupResult;
//    }
//
//    static void pinMode( int pin, int mode )          { CWiringPi.INSTANCE.pinMode( pin, mode ); }
//
//    static void pullUpDnControl( int pin, int pud )   { CWiringPi.INSTANCE.pullUpDnControl( pin, pud ); }
//
//    static void digitalWrite( int pin, int value )    { CWiringPi.INSTANCE.digitalWrite( pin, value ); }
//
//    static int  digitalRead( int pin )                { return CWiringPi.INSTANCE.digitalRead( pin ); }
//
//    static int  analogRead( int pin )                 { return CWiringPi.INSTANCE.analogRead( pin ); }
//
//    static void analogWrite( int pin, int value)      { CWiringPi.INSTANCE.analogWrite( pin, value ); }
//
//    static int  wiringPiISR( int pin, int edgeType, PinCallback callback ) { return CWiringPi.INSTANCE.wiringPiISR( pin, edgeType, callback ); }
}