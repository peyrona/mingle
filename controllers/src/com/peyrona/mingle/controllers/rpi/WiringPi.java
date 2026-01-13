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
import com.peyrona.mingle.lang.japi.UtilStr;
import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.ptr.IntByReference;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    // CALLBACK INTERFACE AND STATUS STRUCTURE
    //------------------------------------------------------------------------//

    /**
     * Callback interface for WiringPi 3.x interrupt handling.
     * <p>
     * The callback receives interrupt status details including pin number,
     * edge type (RISING/FALLING), and microsecond timestamp.
     */
    interface PinCallback extends Callback
    {
        void invoke( WPIWfiStatus status, Pointer userdata );
    }

    /**
     * Maps the C struct WPIWfiStatus from WiringPi 3.x.
     * <p>
     * This structure is passed to callbacks registered with wiringPiISR().
     */
    @FieldOrder({ "statusOK", "pinBCM", "edge", "timeStamp_us" })
    public static class WPIWfiStatus extends Structure
    {
        /** Status code: -1 = error, 0 = timeout, 1 = IRQ processed successfully */
        public int  statusOK;

        /** GPIO pin number in BCM format */
        public int  pinBCM;

        /** Edge type: INT_EDGE_FALLING (1) or INT_EDGE_RISING (2) */
        public int  edge;

        /** Timestamp of the interrupt in microseconds */
        public long timeStamp_us;

        public WPIWfiStatus() { super(); }

        public WPIWfiStatus( Pointer p )
        {
            super( p );
            read();
        }
    }

    //------------------------------------------------------------------------//

    private static volatile boolean bInited = false;
    private static final    Object INIT_LOCK = new Object();
    private static final    Map<Integer,PinCallback> callbackMap = Collections.synchronizedMap( new WeakHashMap<>() );

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
                    bInited = true;               // Before init(...) because init(...) can be called only
                    initializeNative( bUseBCM );  // once and if it fails one time, it will fail next times
                }
            }
        }
    }

    /**
     * Cleanup resources and callbacks.
     * <p>
     * Properly stops all registered ISR handlers and releases GPIO resources.
     */
    static synchronized void cleanup()
    {
        for( Integer pin : callbackMap.keySet() )
        {
            try
            {
                wiringPiISRStop( pin );
            }
            catch( Exception e )
            {
                // Ignore errors during cleanup
            }
        }

        callbackMap.clear();

        // Can't do: bInited = false
        // because once the RPi is initialized, this can not be undone.
    }

    /**
     * Thread-safe interrupt registration with enhanced callback and debounce.
     * <p>
     * Uses WiringPi 3.x wiringPiISR2() which provides:
     * <ul>
     *   <li>Interrupt status information (pin, edge type, timestamp)</li>
     *   <li>Built-in debounce support</li>
     *   <li>Proper cleanup via wiringPiISRStop()</li>
     * </ul>
     *
     * @param pin        The GPIO pin number (WiringPi or BCM depending on setup)
     * @param edgeType   Edge type: INT_EDGE_FALLING, INT_EDGE_RISING, or INT_EDGE_BOTH
     * @param callback   The callback to invoke on interrupt
     * @param debounceUs Debounce period in microseconds (0 to disable)
     * @return 0 on success, -1 on error
     */
    public static synchronized int setCallBack( int pin, int edgeType, PinCallback callback, long debounceUs )
    {
        if( callbackMap.containsKey( pin ) )   // Stop any existing ISR on this pin first
            wiringPiISRStop( pin );

        callbackMap.put( pin, callback );      // Store callback to prevent garbage collection

        return wiringPiISR2( pin, edgeType, callback, debounceUs, null );
    }

    /**
     * Stops the ISR handler for a specific pin.
     * <p>
     * Properly releases the GPIO resources held by the interrupt handler.
     *
     * @param pin The GPIO pin number to stop ISR on
     */
    public static synchronized void stopCallBack( int pin )
    {
        wiringPiISRStop( pin );
        callbackMap.remove( pin );
    }

    //------------------------------------------------------------------------//
    // SETUP FUNCIONS

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

    private static native void wiringPiVersion( IntByReference major, IntByReference minor );

    //------------------------------------------------------------------------//
    // CORE FUNCTIONS

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
    // INTERRUPT FUNCTIONS (WiringPi 3.x)

    /**
     * Registers a callback for interrupts with WiringPi 3.x enhanced features.
     * <p>
     * This function uses wiringPiISR2() which provides:
     * <ul>
     *   <li>Status information passed to callback (pin, edge, timestamp)</li>
     *   <li>Built-in debounce support (microsecond precision)</li>
     *   <li>User data pointer for context</li>
     * </ul>
     *
     * @param pin                The GPIO pin number
     * @param edgeMode           Edge type: INT_EDGE_FALLING, INT_EDGE_RISING, or INT_EDGE_BOTH
     * @param callback           The callback to invoke on interrupt (receives WPIWfiStatus)
     * @param debounce_period_us Debounce period in microseconds (0 to disable)
     * @param userdata           User data pointer passed to callback (can be null)
     * @return 0 on success, -1 on error
     */
    private static native int wiringPiISR2( int pin, int edgeMode, PinCallback callback,
                                            long debounce_period_us, Pointer userdata );

    /**
     * Stops the ISR handler for a specific pin.
     * <p>
     * Deregisters the interrupt handler and releases GPIO resources.
     * Must be called before re-registering an ISR on the same pin.
     *
     * @param pin The GPIO pin number to stop ISR on
     * @return 0 on success, -1 on error
     */
    public static native int wiringPiISRStop( int pin );

    //------------------------------------------------------------------------//

    private static void initializeNative( boolean bUseBCM ) throws MingleException
    {
        checkPiModel();
        checkWiringPiLibFile();

        try
        {
            Native.register( "wiringPi" );   // In most cases, JNA will automatically manage the library's lifecycle.
        }
        catch( UnsatisfiedLinkError ule )
        {
            throw new MingleException( ule );
        }

        int nResult = (bUseBCM ? wiringPiSetupGpio()   // Initializes the WiringPi library using the BCM GPIO numbering scheme.
                               : wiringPiSetup());     // Initializes the WiringPi library using the WiringPi pin numbering scheme.

        if( nResult != 0 )
            throw new MingleException( "Error on WiringPi setup. Result="+ nResult );

        checkWiringPiLibVersion();    // Must be after lib registration
    }

    private static void checkPiModel()
    {
        String model = null;

        try
        {
            String modelPath = "/sys/firmware/devicetree/base/model";    // Modern method: Read device tree model (works on Pi 2+)

            if( Files.exists( Paths.get( modelPath ) ) )
            {
                model = new String( Files.readAllBytes( Paths.get( modelPath ) ) ).trim();
                model = model.replaceAll( "\u0000.*", "" );   // Remove trailing null bytes (common in device tree strings)
            }
            else    // Fallback: Parse /proc/cpuinfo for older Pis
            {
                String   cpuInfo = new String( Files.readAllBytes( Paths.get( "/proc/cpuinfo" ) ) );
                String[] lines   = cpuInfo.split( "\n" );

                for( String line : lines )
                {
                    if( line.startsWith( "Model" ) || line.startsWith( "Hardware" ) )
                    {
                        model = line.split( ":" )[1].trim();

                        if( model.contains( "Raspberry Pi" ) )
                            break;
                        else
                            model = null;
                    }
                }
            }
        }
        catch( IOException e )
        {
            model = null;
        }

        if( model != null )     // When null we have to assume it is fine: there is nothing that can be done
        {
            if( UtilStr.contains( model, "Pi 3", "Pi 4" ) )
                return;

            throw new MingleException( model +" is not supported. Only Pi 3 and 4 are" );
        }
    }

    private static void checkWiringPiLibFile()
    {
        // Check multiple paths where WiringPi library might be installed
        // (matches the paths checked by wiringpi.sh script)
        String[] libDirs = {"/usr/lib",
                            "/usr/local/lib",
                            "/lib",
                            "/usr/lib/arm-linux-gnueabihf",
                            "/usr/lib/aarch64-linux-gnu",
                            "/lib/arm-linux-gnueabihf",
                            "/lib/aarch64-linux-gnu" };

        boolean found = false;

        for( String dir : libDirs )
        {
            File dirFile = new File( dir );

            if( dirFile.isDirectory() )
            {
                // Check for libwiringPi.so or versioned variants (e.g., libwiringPi.so.3.16)
                File[] matches = dirFile.listFiles( (d, name) -> name.startsWith( "libwiringPi.so" ) );

                if( matches != null && matches.length > 0 )
                {
                    found = true;
                    break;
                }
            }
        }

        if( ! found )
            throw new MingleException( "WiringPi file does not exist in any standard location. Is WiringPi library installed?" );
    }

    private static void checkWiringPiLibVersion()
    {
        IntByReference majorRef = new IntByReference();
        IntByReference minorRef = new IntByReference();

        wiringPiVersion( majorRef, minorRef );

        int major = majorRef.getValue();
        int minor = minorRef.getValue();

        if( major != 3 )
            throw new MingleException( "Incorrect WiringPi version: expected=3.x, found="+ major +'.'+ minor );
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