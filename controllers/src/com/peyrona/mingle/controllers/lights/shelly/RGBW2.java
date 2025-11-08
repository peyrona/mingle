
package com.peyrona.mingle.controllers.lights.shelly;

import com.peyrona.mingle.controllers.lights.LightCtrlBase;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.util.Map;

/**
 * This Controller
 * <p>
 * See note at ControllerBase.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   RGBW2
             extends LightCtrlBase
{
    //------------------------------------------------------------------------//
    // FROM Controller Base

    @Override
    public void set( final String deviceName, Map<String,Object> deviceInit, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        int interval = ((Number) deviceInit.getOrDefault( "interval", 1000f )).intValue();
            interval = Math.max( 10, interval );


        setValid( true );
        set( "interval", interval );
    }

    @Override
    public void read()
    {
        // bFaked and isValid are not needed to be checked in this Controller

        sendReaded( UtilSys.elapsed() );    // Drivers just place the new value into the bus
    }

    @Override
    public void write( Object newValue)
    {
        sendIsNotWritable();
    }

    //------------------------------------------------------------------------//
    // FROM LightCtrlBase

    @Override
    public boolean isDimmable()
    {
        return true;
    }

    @Override
    public boolean isRGB()
    {
        return true;
    }
}