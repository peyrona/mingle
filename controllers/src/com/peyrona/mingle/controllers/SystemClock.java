
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private volatile ScheduledExecutorService timer = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( final String deviceName, Map<String,Object> deviceInit, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed
        setDeviceConfig( deviceInit );

        Object oInterval = get( KEY );
        long   interval  = (oInterval != null) ? ((Number) oInterval).longValue() : 1000L;

        setBetween( KEY, 10L, interval, Long.MAX_VALUE );

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
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || (! super.start( rt )) )
            return false;

        long interval = ((Number) get( KEY )).longValue();

        timer = Executors.newSingleThreadScheduledExecutor( r ->
                            {
                                Thread t = new Thread( r, getClass().getName() + interval +"ms" );
                                t.setDaemon( true );
                                return t;
                            } );

        timer.scheduleAtFixedRate( this::read, interval, interval, TimeUnit.MILLISECONDS );

        return isValid();
    }

    @Override
    public void stop()
    {
        if( timer != null )
        {
            timer.shutdownNow();
            timer = null;
        }

        super.stop();
    }
}