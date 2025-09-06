
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * This Controller periodically informs about the time elapsed since ExEn started.
 * <p>
 * See note at ControllerBase.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class   SystemClock
             extends ControllerBase
{
    private ScheduledFuture timer = null;
    private int             interval;

    //------------------------------------------------------------------------//

    @Override
    public void set( final String deviceName, Map<String,Object> deviceInit, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        interval = ((Number) deviceInit.getOrDefault( "interval", 1000f )).intValue();
        interval = setBetween( "interval", 10, interval, Integer.MAX_VALUE );

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
    public void start( IRuntime rt )
    {
        super.start( rt );

        synchronized( this )
        {
            if( timer == null )
                timer = UtilSys.executeAtRate( getClass().getName(), interval, interval, () -> read() );     // 'interval' must also be the initial delay
        }
    }

    @Override
    public void stop()
    {
        if( timer != null )
        {
            timer.cancel( true );
            timer = null;
        }

        super.stop();
    }
}