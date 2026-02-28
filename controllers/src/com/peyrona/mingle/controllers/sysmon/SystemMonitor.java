
package com.peyrona.mingle.controllers.sysmon;

import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.util.Map;
import java.util.Set;
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

    private ScheduledFuture timer;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String,Object> mapConfig, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        set( KEY_METRIC, ((String) mapConfig.getOrDefault( KEY_METRIC, "cpu" )).toLowerCase() );

        Set<String> validMetrics = Set.of( "speed", "pending", "cpu", "jvmram", "disk" );

        if( ! validMetrics.contains( (String) get( KEY_METRIC ) ) )
        {
            sendIsInvalid( get( KEY_METRIC ) +" is invalid. Valid: "+ validMetrics.toString() );
            set( KEY_METRIC, null );
        }

        set( KEY_MEASURE, ((String) mapConfig.getOrDefault( KEY_MEASURE, "used%" )).toLowerCase() );

        Set<String> validMeasures = Set.of( "used", "used%", "free", "free%" );

        if( ! validMeasures.contains( (String) get( KEY_MEASURE ) ) )
        {
            sendIsInvalid( get( KEY_MEASURE ) +" is invalid. Valid: "+ validMeasures.toString() );
            set( KEY_MEASURE, null );
        }

        long interval = ((Number) mapConfig.getOrDefault( KEY_INTERVAL, 1000f )).longValue();
        setBetween( KEY_INTERVAL, 500L, interval, Long.MAX_VALUE );

        setValid( get( KEY_METRIC ) != null && get( KEY_MEASURE ) != null );
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || (! super.start( rt )) )
            return false;

        // Following check must be after 'super.start( rt )' beacuse 'isDiskWritable(...)' needs 'IRuntime'
        if( "disk".equals( get( KEY_METRIC ) ) && ! isDiskWritable( false ) )
        {
            sendIsInvalid( get( KEY_METRIC ) +" is invalid. Stick is not allowed to use disk." );
        }
        else
        {
            synchronized( this )
            {
                if( timer == null )
                {
                    timer = UtilSys.executor( false )
                                   .name( getClass().getName() )
                                   .delay( 5000L )
                                   .rate( ((Number) get( KEY_INTERVAL )).longValue() )
                                   .execute( () -> read() );
                }
            }
        }

        return isValid();
    }

    @Override
    public void stop()
    {
        synchronized( this )
        {
            if( timer != null )
                timer.cancel( true );
        }

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
                float fUsed = SystemMetrics.getCpuLoad();

                if( Float.isNaN( fUsed ) )    // NaN returned when the value is not available
                {
                    sendReadError( new MingleException( "CPU value not available" ) );
                    break;
                }

                int nUsed = (int) (fUsed * 100);

                if( ((String) get( KEY_MEASURE )).startsWith( "used" ) ) sendReaded(       nUsed );     // "used" and "used%"
                else                                                     sendReaded( 100 - nUsed );

                break;

            case "jvmram":
                long free  = SystemMetrics.getJvmFreeMemory();
                long total = SystemMetrics.getJvmTotalMemory();
                long used  = total - free;

                send( used, free, total );
                break;

            case "disk":
                long free_  = UtilSys.fHomeDir.getFreeSpace();    // Less accurate but much faster than :getUsableSpace()
                long total_ = UtilSys.fHomeDir.getTotalSpace();
                long used_  = total_ - free_;

                send( used_, free_, total_ );
                break;
        }
    }

    @Override
    public void write( Object newValue )
    {
        sendIsNotWritable();
    }

    //------------------------------------------------------------------------//

    private void send( long used, long free, long total )
    {
        switch( ((String) get( KEY_MEASURE )) )
        {
            case "used" : sendReaded( used ); break;
            case "free" : sendReaded( free ); break;
            case "used%": sendReaded(         ((float) used / total) * 100f);  break;
            case "free%": sendReaded( 100f - (((float) used / total) * 100f)); break;
        }
    }
}