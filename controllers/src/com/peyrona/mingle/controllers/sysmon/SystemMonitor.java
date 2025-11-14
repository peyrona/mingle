
package com.peyrona.mingle.controllers.sysmon;

import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class SystemMonitor
             extends ControllerBase
{
    private static final String KEY_INTERVAL = "interval";
    private static final String KEY_METRIC   = "metric";
    private static final String KEY_MEASURE  = "measure";

    private static final File file = new File( "." );

    private ScheduledFuture timer;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String,Object> mapConfig, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        set( KEY_METRIC, ((String) mapConfig.getOrDefault( KEY_METRIC, "cpu" )).toLowerCase() );

        if( ! "speed,pending,cpu,jvmram,disk".contains( (String) get( KEY_METRIC ) ) )
        {
            sendIsInvalid( get( KEY_METRIC ) +" is invalid. Valids: speed, pending, cpu, jvmram, disk" );
            set( KEY_METRIC, null );
        }

        set( KEY_MEASURE, ((String) mapConfig.getOrDefault( KEY_MEASURE, "used%" )).toLowerCase() );

        if( ! "used,used%,free,free%".contains( (String) get( KEY_MEASURE ) ) )
        {
            sendIsInvalid( get( KEY_MEASURE ) +" is invalid. Valids: used, used%, free, free%" );
            set( KEY_MEASURE, null );
        }

        int interval = ((Number) mapConfig.getOrDefault( KEY_INTERVAL, 1000f )).intValue();
        setBetween( KEY_INTERVAL, 500, interval, Integer.MAX_VALUE );

        setValid( get( KEY_METRIC ) != null && get( KEY_MEASURE ) != null );
    }

    @Override
    public void start( IRuntime rt )
    {
        if( isInvalid() )
            return;

        super.start( rt );

        if( "disk".equals( get( KEY_METRIC ) ) && ! useDisk( true ) )
        {
            sendIsInvalid( get( KEY_METRIC ) +" is invalid. Stick is not allowed to use disk." );
            return;
        }

        if( timer == null )
            timer = UtilSys.executeAtRate( getClass().getName(), 5000, (int) get( KEY_INTERVAL ), () -> read() );
    }

    @Override
    public void stop()
    {
        timer.cancel( true );
        super.stop();
    }

    @Override
    public void read()
    {
        if( isInvalid() )      // bFaked is ignored by this Controller
            return;

        switch( (String) get( KEY_METRIC ) )
        {
            case "speed" :     // Amount of messages dispatched per minute
                sendReaded( getRuntime().bus().getSpeed() );
                break;

            case "pending" :   // Amount of messages pending to be processed
                sendReaded( getRuntime().bus().getPending() );
                break;

            case "cpu" :
            case "cpu%":
                float fUsed = SystemMetrics.getCpuLoad() * 100;

                if( ((String) get( KEY_MEASURE )).startsWith( "used" ) ) sendReaded(        fUsed );     // "used" and "used%"
                else                                                     sendReaded( 100f - fUsed );

                break;

            case "jvmram":
                long free  = SystemMetrics.getJvmFreeMemory();
                long total = SystemMetrics.getJvmTotalMemory();
                long used  = total - free;

                switch( ((String) get( KEY_MEASURE )) )
                {
                    case "used" : sendReaded( used ); break;
                    case "free" : sendReaded( free ); break;
                    case "used%": sendReaded(         ((float) used / total) * 100f);  break;
                    case "free%": sendReaded( 100f - (((float) used / total) * 100f)); break;
                }

                break;

            case "disk":
                long free_  = file.getFreeSpace();    // Less accurate but much faster than :getUsableSpace()
                long total_ = file.getTotalSpace();
                long used_  = total_ - free_;

                switch( ((String) get( KEY_MEASURE )) )
                {
                    case "used" : sendReaded( used_ ); break;
                    case "free" : sendReaded( free_ ); break;
                    case "used%": sendReaded(         ((float) used_ / total_) * 100f);  break;
                    case "free%": sendReaded( 100f - (((float) used_ / total_) * 100f)); break;
                }

                break;
        }
    }

    @Override
    public void write( Object newValue )
    {
        sendIsNotWritable();
    }
}