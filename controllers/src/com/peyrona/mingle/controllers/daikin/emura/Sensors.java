
package com.peyrona.mingle.controllers.daikin.emura;

import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.xpreval.functions.StdXprFns;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * Manage Sensors providing following current values: temperature inside, temperature
 * outside and humidity inside.
 * <p>
 * Each time a command is sent, the machine status is read back, and the machine status
 * should only be changed using this class: if other means (using the remote control or
 * the Daikin app) are used at the same time, the results can be chaotic.
 * <p>
 * See note at ControllerBase.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   Sensors
             extends ControllerBase
{
    private static final String KEY_ADDRESS  = "address";
    private static final String KEY_INTERVAL = "interval";

    private Talker          talker = null;
    private ScheduledFuture timer  = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( final String deviceName, Map<String,Object> deviceInit, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );     // Must be at beginning: in case an error happens, Listener is needed

        String sIpAddr   = deviceInit.get( KEY_ADDRESS ).toString();    // This is mandatory
        long   nInterval = ((Number) deviceInit.getOrDefault( KEY_INTERVAL, (5 * UtilUnit.MINUTE) )).longValue();

        if( ! UtilSys.isDevEnv )     // When under development, any value is accepted
            nInterval = UtilUnit.setBetween( 1 * UtilUnit.MINUTE, nInterval, 50 * UtilUnit.HOUR );

        setValid( true );

        set( KEY_ADDRESS , sIpAddr   );
        set( KEY_INTERVAL, nInterval );
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || (! super.start( rt )) )
            return false;

        if( ! isFaked() )   // isFaked() is initialized by super:start(...)
        {
            try
            {
                talker = new Talker( get( KEY_ADDRESS ).toString() );
            }
            catch( IOException ioe )    // MalformedURLException extends IOException
            {
                sendIsInvalid( ioe.getMessage() );
            }
        }

        timer = UtilSys.executor( false )
                       .delay( 5000L )                      // Initial delay
                       .rate( ((Number) get( KEY_INTERVAL )).longValue() )
                       .execute( () -> read() );

        return isValid();
    }

    @Override
    public void stop()
    {
        if( timer != null )
        {
            timer.cancel( true );
            timer = null;
        }

        talker = null;

        super.stop();
    }

    @Override
    public void read()
    {
        if( isInvalid() )
            return;

        if( isFaked() )
        {
            sendReaded( new pair().put( "t_inside" , StdXprFns.invoke( "rand",  5, 44 ))
                                  .put( "t_outside", StdXprFns.invoke( "rand", -9, 44 )) );
        }
        else
        {
            try
            {
                Map<String,String> map = UtilColls.toMap( talker.get() );

                if( UtilColls.isNotEmpty( map ) )
                {
                    map.remove( "ret" );     // Not needed any more
                    map.remove( "err" );     // Not needed any more

                    pair   pair2ret = new pair();
                    String sValue;

                    sValue = map.get( Const.What.InsideTemperature.key );
                    pair2ret.put( "t_inside" , (Language.isNumber( sValue ) ? UtilType.toFloat( sValue ) : -99f) );

                    sValue = map.get( Const.What.OutsideTemperature.key );
                    pair2ret.put( "t_outside", (Language.isNumber( sValue ) ? UtilType.toFloat( sValue ) : -99f) );

                    sendReaded( pair2ret );
                }
            }
            catch( IOException ise )
            {
                sendReadError( ise );     // The error message has the original xpr
            }
        }
    }

    @Override
    public void write( Object newValue )
    {
        sendIsNotWritable();
    }
}