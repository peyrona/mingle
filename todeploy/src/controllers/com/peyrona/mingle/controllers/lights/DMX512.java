
package com.peyrona.mingle.controllers.lights;

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
public final class   DMX512
             extends LightCtrlBase
{

// https://blog.ledbox.es/que-es-control-dmx512/   --> Teoría
// https://github.com/corentin59/ArtNetStack       --> Art-Net is DMX512 over Ethernet TCP/IP UDP
// https://github.com/trevordavies095/DmxJava      --> Java Controller for USB - DMX devices

    //------------------------------------------------------------------------//

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

    //------------------------------------------------------------------------//

    @Override
    public void set( final String deviceName, Map<String,Object> deviceInit, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        int interval = ((Number) deviceInit.getOrDefault( "interval", 1000f )).intValue();
            interval = Math.max( 10, interval );


        setValid( true );
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

    @Override
    public void stop()
    {


        super.stop();
    }
}