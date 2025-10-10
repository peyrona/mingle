
package com.peyrona.mingle.controllers.daikin.emura;

import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.RateMonitor;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Locale;
import java.util.Map;

/**
 * Change Daikin Emura AC state.
 * <p>
 * Each time a command is sent, the state of the machine is read back.
 * On the other hand, the status of the machine should only be changed
 * using this class: if other ways are used (like using the remote
 * control or the Daikin app) at the same time, the machine's status,
 * the results can be chaotic.
 * <p>
 * See note at ControllerBase.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class   Control
             extends ControllerBase
{
    private final static String[] as = { "power","mode","fan","wings","t_target","h_target"};
    private final static short POWER     = 0;
    private final static short MODE      = 1;
    private final static short FAN       = 2;
    private final static short WINGS     = 3;
    private final static short T_TARGET  = 4;
    private final static short H_TARGET  = 5;

    private       Talker      talker  = null;
    private       pair        faked   = null;    // Used only when using Faked drivers
    private       String      sIpAddr = null;
    private final RateMonitor monitor = new RateMonitor( 5, 3000 );

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String,Object> deviceInit, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed
        sIpAddr = deviceInit.get( "address" ).toString();    // This is mandatory
        setValid( true );
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );

        if( isFaked )
        {
            faked = new pair().put( as[POWER]   , false )
                              .put( as[MODE]    , Const.Mode.Fan.toString() )
                              .put( as[FAN]     , Const.Fan.Minimum.toString() )
                              .put( as[WINGS]   , Const.Wings.None.toString() )
                              .put( as[T_TARGET], 22 )
                              .put( as[H_TARGET], 0 );
        }
        else
        {
            try
            {
                talker = new Talker( sIpAddr );
            }
            catch( MalformedURLException mue )
            {
                sendIsInvalid( mue );
            }
        }
    }

    @Override
    public void stop()
    {
        talker = null;
        super.stop();
    }

    @Override
    public void read()
    {
        if( isInvalid() )
            return;

        if( isFaked )
        {
            sendReaded( faked );
        }
        else
        {
            try
            {
                sendReaded( decode( talker.read() ) );
            }
            catch( IOException ise )
            {
                sendReadError( ise );
            }
        }
    }

    @Override
    public void write( Object oRequest )
    {
        if( isInvalid() )
            return;

        if( isFaked )
        {
            for( Object key : ((pair) oRequest).keys() )
                faked.put( key, ((pair) oRequest).get( key ) );

            sendReaded( faked );
        }
        else
        {
            if( checkOfClass( oRequest, pair.class ) )
            {
                if( monitor.notifyTooFast() )
                {
                    sendWriteError( oRequest, new MingleException( "Too many write requests per second" ) );
                }
                else
                {
                    try
                    {
                        String sNewState = talker.write( encode( (pair) oRequest ) );

                        sendReaded( decode( sNewState ) );
                    }
                    catch( IOException ioe )
                    {
                        sendWriteError( oRequest, ioe );
                    }
                }
            }
        }
    }

    /**
     * From machine to human.
     *
     * @param sState
     * @return
     * @throws IOException
     */
    private pair decode( String sState ) throws IOException
    {
        Map<String,String> map = UtilColls.toMap( sState );

        String sPower = Const.Power.key2enum( map.get( Const.What.Power.key ) ).toString();
        String sMode  = Const.Mode.key2enum(  map.get( Const.What.Mode.key  ) ).toString();
        String sFan   = Const.Fan.key2enum(   map.get( Const.What.Fan.key   ) ).toString();
        String sWings = Const.Wings.key2enum( map.get( Const.What.Wings.key ) ).toString();
        String sTempe = map.get( Const.What.TargetTemperature.key );
        String sHumid = map.get( Const.What.TargetHumidity.key );
        Float  nTempe = Language.isNumber( sTempe ) ? UtilType.toFloat( sTempe ) : -99f;
        Float  nHumid = Language.isNumber( sHumid ) ? UtilType.toFloat( sHumid ) : -99f;    // Even if this is an integer, Une only uses float

        return new pair()
                    .put( as[POWER]   , sPower.equalsIgnoreCase( "ON" ) )
                    .put( as[MODE]    , sMode  )
                    .put( as[FAN]     , sFan   )
                    .put( as[WINGS]   , sWings )
                    .put( as[T_TARGET], nTempe )
                    .put( as[H_TARGET], nHumid );
    }

    /**
     * From human to machine
     *
     * @param pRequest
     * @return
     * @throws IOException
     */
    private String encode( pair pRequest ) throws IOException     // This 'pair' has the keys used by this driver: "power", "t_target", etc.
    {
        pair now = decode( talker.read() );    // The A.C. state now

        Boolean power = (Boolean) (pRequest.hasKey( as[POWER]    ) ? pRequest.get( as[POWER]    ) : now.get( as[POWER]    ));
        String  mode  = (String)  (pRequest.hasKey( as[MODE]     ) ? pRequest.get( as[MODE]     ) : now.get( as[MODE]     ));
        String  fan   = (String)  (pRequest.hasKey( as[FAN]      ) ? pRequest.get( as[FAN]      ) : now.get( as[FAN]      ));
        String  wings = (String)  (pRequest.hasKey( as[WINGS]    ) ? pRequest.get( as[WINGS]    ) : now.get( as[WINGS]    ));
        Float   t_tar = (Float)   (pRequest.hasKey( as[T_TARGET] ) ? pRequest.get( as[T_TARGET] ) : now.get( as[T_TARGET] ));
        Float   h_tar = (Float)   (pRequest.hasKey( as[H_TARGET] ) ? pRequest.get( as[H_TARGET] ) : now.get( as[H_TARGET] ));

        t_tar = UtilUnit.setBetween( 17f, t_tar, 34f );    // Must be Locale.US to ensure '.' as separator
        h_tar = UtilUnit.setBetween(  0f, h_tar, 90f );    // 0 humid == do not use the dehumidifier

        return new StringBuilder( pRequest.size() * 32 )
                    .append( Const.What.Power.key )
                    .append( '=' )
                    .append( Const.Power.enum2key( power ) )
                    .append( '&' )
                    .append( Const.What.Mode.key )
                    .append( '=' )
                    .append( Const.Mode.enum2key( mode ) )
                    .append( '&' )
                    .append( Const.What.Fan.key )
                    .append( '=' )
                    .append( Const.Fan.enum2key( fan ) )
                    .append( '&' )
                    .append( Const.What.Wings.key )
                    .append( '=' )
                    .append( Const.Wings.enum2key( wings ) )
                    .append( '&' )
                    .append( Const.What.TargetTemperature.key )
                    .append( '=' )
                    .append( String.format( Locale.US, "%.1f", t_tar ) )    // Must have only 1 decimal place
                    .append( '&' )
                    .append( Const.What.TargetHumidity.key )
                    .append( '=' )
                    .append( String.valueOf( h_tar.intValue() ) )           // Must be an int (if 'float' is used, Emura returns error)
                    .toString();
    }

    //----------------------------------------------------------------------------//
    // FOR TESTING PURPOSES

//    private pair fakeState( Object oRequest )
//    {
//        if( faked == null )
//        {
//            faked = new pair().put( as[POWER]   , false )
//                              .put( as[MODE]    , Const.Mode.Fan.toString() )
//                              .put( as[FAN]     , Const.Fan.Minimum.toString() )
//                              .put( as[WINGS]   , Const.Wings.None.toString() )
//                              .put( as[T_TARGET], 22 )
//                              .put( as[H_TARGET], 0 );
//        }
//
//        pair request = (pair) oRequest;
//
//        for( Object o : request.keys() )
//            faked.put( o, request.get(o) );
//
//        return faked;
//    }


//    public static void main( String[] as ) throws MalformedURLException, IOException
//    {
//        IController.Listener listener = new Listener()
//                                        {
//                                            @Override
//                                            public void onChanged(String deviceName, Object newValue)
//                                            {
//                                                System.out.println( newValue );
//                                            }
//
//                                            @Override
//                                            public void onError(ILogger.Level level, String deviceName, String message)
//                                            {
//                                                System.out.println( message );
//                                            }
//                                        };
//
//        Map<String,Object> map = new HashMap<>();
//                           map.put( "address", "192.168.7.246" );
//
//        Control control = new Control();
//                control.set( "A.C.", map, listener );
//
//        pair p = new pair( "power", false );
//
//        control.write( p );
//
////        state.put( "power"   , false )
////             .put( "mode"    , Const.Mode.Fan.toString() )
////             .put( "fan"     , Const.Fan.Minimum.toString() )
////             .put( "wings"   , Const.Wings.None.toString() )
////             .put( "t_target", 22 )
////             .put( "h_target", 75 );
//    }
}