package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.Cron;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * A Linux Cron style Controller.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class Kronos
       extends ControllerBase
{
    private Cron            cron   = null;
    private ScheduledFuture future = null;
    private long            last   = -1l;      // Last time it was executed: timestamp

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        if( deviceInit.isEmpty() )
            sendGenericError( ILogger.Level.SEVERE, "Driver config is empty" );

        pair pairConfig = new pair();

        for( Map.Entry<String,Object> entry : deviceInit.entrySet() )
            pairConfig.put( entry.getKey(), entry.getValue() );

        try
        {
            cron = new Cron( pairConfig );

            setValid( true );
            set( deviceInit );
        }
        catch( MingleException me )
        {
            for( Object msg : UtilColls.toList( me.getMessage(), UtilColls.cRECORD_SEP ) )
                sendGenericError( ILogger.Level.SEVERE, msg.toString() );

            setValid( false );
        }
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );

        if( cron != null )
            next();
    }

    @Override
    public void stop()
    {
        // Can not make 'cron = null' because it is created at ::set(...), not at ::start(...)

        if( future != null )
        {
            future.cancel( true );
            future = null;
        }

        super.stop();
    }

    @Override
    public void read()
    {
        sendReaded( last );
    }

    @Override
    public void write(Object newValue)
    {
        // Nothing to do.
    }

    //------------------------------------------------------------------------//

    /**
     * Set next iteration (if any)
     */
    private void next()
    {
        if( future != null )
        {
            future.cancel( false );
            future = null;
        }

        long millis = cron.next();

        if( millis > -1l )
        {
            future = UtilSys.execute( getClass().getName(),
                                      millis,
                                      () ->
                                      {
                                          last = System.currentTimeMillis();
                                          read();
                                          next();
                                      }
                                    );
        }
    }
}