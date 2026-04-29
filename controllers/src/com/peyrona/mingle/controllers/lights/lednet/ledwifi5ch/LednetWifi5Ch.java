
package com.peyrona.mingle.controllers.lights.lednet.ledwifi5ch;

import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.util.Map;

/**
 * http://www.ledenet.com/products/smart-wifi-led-controller-5-channels-control-4a5ch-cwww-rgb-rgbw-rgbww-led-light-timer-music-group-sync-controller/
 *
 * Smart WiFi LED Controller 5 Channels Control 4A5CH CW/WW RGB RGBW RGBWW LED light.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class LednetWifi5Ch
       extends ControllerBase
{
    private Wifi5ChDevice device = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        String sIpAddr = deviceInit.get( "ip" ).toString();    // This is mandatory

// TODO: quitar este rem
/**
        try
        {
//            if( ! isFaked() )
//                device = new Wifi5ChDevice( sIpAddr, (val) -> sendWriteError( val, new IOException( "Error writing in socket at "+ device.getIP() ) )  );

            set( "ip", sIpAddr );
            setValid( true );
        }
        catch( IOException ioe )
        {
            sendGenericError( ILogger.Level.SEVERE, ioe.getMessage() );
            setValid( false );
        }
*/
// ELIMINAR LAS SIGUIENTES LINEAS -->
        setValid( true );
        set( "ip", sIpAddr );
    }

    @Override
    public void read()
    {
        if( isInvalid() || (device == null) )
            return;

        if( isFaked() )
        {
            sendReaded( "" );
            return;
        }

        if( device != null )
            sendReaded( device.read() );
    }

    @Override
    public void write( Object newValue )
    {
        if( isFaked() || isInvalid() || (device == null) )
            return;

        if( newValue == null )
        {
            sendGenericError( ILogger.Level.SEVERE, "Value to write can not be null" );
            return;
        }

        if( newValue instanceof Boolean )
            newValue = new pair( "power", newValue );

        if( newValue instanceof pair )
        {
            device.write( (pair) newValue );
            sendChanged( (pair) newValue );
        }
        else
        {
            sendGenericError( ILogger.Level.SEVERE, "Value is a "+ newValue.getClass().getSimpleName() +", but should be a 'pair'" );
        }
    }

    @Override
    public void stop()
    {
        if( device != null )
        {
            device.dispose();
            device = null;
        }

        super.stop();
    }
}