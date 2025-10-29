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

import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * Instances of this class represents one single Raspberry Pi GPIO Pin.
 * <p>
 * This Controller works only with local RPis (an RPi where the Controller is running
 * on); a different Controller could manage remotely more than one RPi.
 * <p>
 * See note at ControllerBase.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   RPiGpioPin
             extends ControllerBase
{
    private static volatile boolean isBCM = false;

    private ScheduledFuture    timer  = null;
    private IPin               pin    = null;
    private Map<String,Object> mapCfg = null;

    //------------------------------------------------------------------------//

    public RPiGpioPin()
    {
        if( isFaked )
        {
            timer = UtilSys.executeAtRate( getClass().getName(),
                                           5000, 3000,    // After 5 secs (initial delay), every 3 secs
                                           () -> {
                                                    if( pin.isInput() && (new Random().nextInt( 100 ) > 65) )    // 2/3 of times, it does not send
                                                        sendReaded( pin.read() );                               // a new random value
                                                 } );
        }
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    @Override
    public void set( String deviceName, Map<String,Object> mapConfig, IController.Listener listener )
    {
        setListener( listener );    // Must be at begining: in case an error happens, Listener is needed
        setName( deviceName );      // Must be second line to have name of device in case of error
        setValid( true );           // So far this is valid, but start() can change it

        mapCfg = mapConfig;
        set( mapConfig );
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );

        initWiringPi();         // Must be 2nd line because in case of error, the previous line is needed
        createPin( mapCfg );
        setValid( pin != null );
    }

    @Override
    public void stop()
    {
        if( pin != null )
            pin.cleanup();

        if( timer != null )
            timer.cancel( true );

        pin   = null;
        timer = null;

        WiringPi.cleanup();

        super.stop();
    }

    @Override
    public void read()              // isFaked is managed via PinFaked
    {
        if( pin != null )
        {
            try
            {
                sendReaded( pin.read() );
            }
            catch( MingleException ie )
            {
                sendReadError( ie );
            }
        }
    }

    @Override
    public void write( Object newValue )
    {
        if( pin != null )
        {
            try
            {
                if( newValue instanceof Boolean ) pin.write( (boolean) newValue );
                else                              pin.write( UtilType.toInteger( newValue ) );

                sendReaded( newValue );
            }
            catch( MingleException ie )
            {
                sendWriteError( newValue, ie );
            }
        }
    }

    @Override
    public String toString()
    {
        return "RaspberryPi GPIO Controller; faked-mode="+ isFaked +", RPi="+ UtilSys.isRaspberryPi();
    }

    //------------------------------------------------------------------------//

    /**
     * Creates a new Pin and adds it to mapDev2Pin.<br>
     * If received configuration is incorrect, the Pin is not created and not added to the map.
     * <p>
     *  Example:
     *     SENSOR button_1
     *         DRIVER myRPi
     *             CONFIG pin      = 26
     *                    type     = "digital"
     *                    mode     = "input"
     *                    pull     = "down"
     *                    debounce = 95
     *                    invert   = true
     *
     * @param deviceName
     * @param mapConfig
     */
    private void createPin( Map<String,Object> mapConfig )
    {
        // GPIO pin category	      Recommended state
        // -----------------------    ------------------
        // Not being actively used	  Pulled up to 3.3V
        // Being used as input	      Pulled down to 0V
        // Being used as output	      Pulled up to 3.3V when not driven low, pulled down to 0V when driven low

        // NOTE: in received map, the keys had been lowered (are lower-case) by the transpiler. But the values preserve their case.

        boolean bSuccesss = true;
        boolean isDigital = get( mapConfig, "type", "analog", true );              // Everything except "analog" is "digital" (if not in map, true)
        boolean isInput   = get( mapConfig, "mode", "output", true );              // Everything except "output" is "input"   (if not in map, true)
        boolean isInvert  = false;                                                 // Makes true to be false and false to be true
        boolean isButton  = false;
        int     nPull     = (isInput ? WiringPi.PULL_DOWN : WiringPi.PULL_UP);     // Default value
        int     debounce  = 0;                                                     // Default value
        int     address   = UtilType.toInteger( mapConfig.get( "pin" ) );          // Transpiler ensures this value is a number (exists because is REQUIRED)

        if( mapConfig.containsKey( "pull" ) )
        {
            String sPull = mapConfig.get( "pull" ).toString().toLowerCase();

            switch( sPull )
            {
                case "off" : nPull = WiringPi.PULL_OFF;  break;     // Done in this way for cality sake
                case "up"  : nPull = WiringPi.PULL_UP;   break;
                case "down": nPull = WiringPi.PULL_DOWN; break;
                default    : sendIsInvalid( address +": invalid type: "+ mapConfig.get( "pull" ) );
                             bSuccesss = false;
            }
        }

        if( mapConfig.containsKey( "debounce" ) )
            debounce = UtilType.toInteger( mapConfig.get( "debounce" ) );

        if( mapConfig.containsKey( "isbutton" ) )
            isButton = (boolean) mapConfig.get( "isbutton" );              // Transpiler ensures this value is a boolean

        if( mapConfig.containsKey( "invert" ) )
            isInvert = (boolean) mapConfig.get( "invert" );                // Transpiler ensures this value is a boolean

        // Now it is needed to check the consistency of the parameters -------------------------------------------------

        if( nPull == WiringPi.PULL_OFF )
            sendGenericError( ILogger.Level.WARNING, "\"pull\" is \"off\": most applications do not want this" );

        if( isInput && (nPull != WiringPi.PULL_DOWN) )
            sendGenericError( ILogger.Level.WARNING, "When \"mode\" is \"input\", the \"pull\" normally is \"down\", but it is \""+ mapConfig.get( "pull" ) +'\"' );

        if( (address < 0) || (address > 31) )     // Both BMC and WiringPi pins are from 0 to 31
        {
            sendIsInvalid( address +": invalid pin number." );
            bSuccesss = false;
        }

        String msg = " can be applied only when \"type\" is \"digital\" and \"mode\" is \"input\"";

        if( isButton && ((! isInput) || (! isDigital)) )
        {
            sendIsInvalid( "\"isButton\""+ msg );
            bSuccesss = false;          // Do not create this pin
        }

        if( (debounce > 0) && ((! isInput) || (! isDigital)) )
        {
            sendIsInvalid( "\"debounce\""+ msg );
            bSuccesss = false;          // Do not create this pin
        }

        if( debounce < 0 )
        {
            sendGenericError( ILogger.Level.WARNING, "\"debounce\" must be >= 0, but was: "+ debounce +". It is set to default (zero)" );
            debounce = 0;
        }

        if( isInvert && (! isDigital) )
        {
            sendIsInvalid( "\"invert\" can be applied only when \"type\" is \"digital\"" );
            bSuccesss = false;          // Do not create this pin
        }

        if( isButton && (! mapConfig.containsKey( "debounce" )) )
            debounce = 50;

        //------------------------------------------------------------------------//

        if( bSuccesss )
        {
            synchronized( this )
            {
                if( isFaked )
                {
                    pin = new PinFaked( isDigital, isInput );
                }
                else
                {
                    try
                    {
                        Consumer<Object> callback = null;

                        if( isInput )
                            callback = (obj) -> sendReaded( obj );

                        pin = new Pin( address, nPull, debounce, isDigital, isInput, isInvert, isButton, isBCM,
                                       callback, (String str) -> sendGenericError( ILogger.Level.SEVERE, str ));
                    }
                    catch( MingleException ie )
                    {
                        sendIsInvalid( "Error provisioning Pin: "+ ie.getMessage() );      // Not needed to make pin = null, pin is already null
                    }
                }
            }
        }
    }

    /**
     * To initiate WiringPi it is needed to know the Numbering Model and it is set inside config.json or a O.S. environment variable
     */
    private void initWiringPi()
    {
        if( isFaked || WiringPi.isInited() )
            return;

        if( ! UtilSys.isRaspberryPi() )
            failed( "faked-mode=false. Is this a Raspberry Pi compatible?" );

        String sModel = getRuntime().getFromConfig( "common", "RPiNumberingModel", "BCM" );

        isBCM = sModel.equalsIgnoreCase( "BCM" );    // By default (null) it is BCM

        try
        {
            WiringPi.setup( isBCM );
        }
        catch( MingleException me )
        {
            failed( me.getMessage() +"\nBetter not to continue." );
        }
    }

    private boolean get( Map<String,Object> mapConfig, String sKey, String sValue, boolean bDefault )    // Just to make code more clear
    {
        if( mapConfig.containsKey( sKey ) )
            return ! mapConfig.get( sKey ).toString().equalsIgnoreCase( sValue );

        return bDefault;
    }

    private void failed( String msg )
    {
        sendGenericError( ILogger.Level.SEVERE, msg );

        getRuntime().exit( 0 );
    }
}