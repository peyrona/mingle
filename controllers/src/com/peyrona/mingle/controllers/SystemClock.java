
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
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   SystemClock
             extends ControllerBase
{
    private static final String KEY = "interval";

    private ScheduledFuture timer = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( final String deviceName, Map<String,Object> deviceInit, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        int interval = ((Number) deviceInit.getOrDefault( KEY, 1000f )).intValue();

        setBetween( KEY, 10, interval, Integer.MAX_VALUE );

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
        if( isInvalid() )
            return;

        super.start( rt );

        synchronized( this )
        {
            timer = UtilSys.executeAtRate( getClass().getName(),
                                           getLong( KEY ),     // 'interval' must also be the initial delay
                                           getLong( KEY ),
                                           () -> read() );
        }
    }

    @Override
    public synchronized void stop()
    {
        if( timer != null )
        {
            timer.cancel( true );
            timer = null;
        }

        super.stop();
    }
}