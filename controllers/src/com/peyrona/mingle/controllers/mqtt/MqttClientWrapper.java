
package com.peyrona.mingle.controllers.mqtt;

import com.peyrona.mingle.controllers.ControllerBase;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.lexer.Language;
import com.peyrona.mingle.lang.xpreval.functions.list;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.io.IOException;
import java.util.Map;

/**
 * This is the class that is used in Une: all others in this package are accessory
 * classes (needed to make things easy to read and manage and more reusable).
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class MqttClientWrapper
       extends ControllerBase
{
    private IMqttClient        client = null;
    private Map<String,Object> mapConfig;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String,Object> mapConfig, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        this.mapConfig = mapConfig;

        try
        {
            client = new MqttClient4Paho();
            client.add( new MyListener() );    // Must be done before: client.connect(...)

            setValid( true );
        }
        catch( Exception exc )
        {
            client = null;
            sendIsInvalid( "Error creating MQTT client.\nCause: "+ UtilStr.toStringBrief( exc ) );
        }
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );

        String sURI  = (String) mapConfig.get( "uri"      );   // This is REQUIRED
        String sUID  = (String) mapConfig.get( "name"     );
        String sUser = (String) mapConfig.get( "user"     );
        String sPwd  = (String) mapConfig.get( "password" );

        try
        {
            client.connect( sURI, sUID, sUser, sPwd );

            if( mapConfig.containsKey( "subscribe" ) )  doSubscribe( client, mapConfig.get( "subscribe" ) );    // In another method just for clarity
            else                                        client.subscribe( "#", 2 );                             // All messages with QoS == 2

            if( mapConfig.containsKey( "initial" ) )
                write( (String) mapConfig.get( "initial" ) );
        }
        catch( Exception exc )
        {
            client = null;
            sendIsInvalid( "Error creating MQTT client.\nCause: "+ UtilStr.toStringBrief( exc ) );
        }
    }

    @Override
    public void stop()
    {
        if( client != null )
        {
            client.close();
            client = null;
        }

        super.stop();
    }

    @Override
    public void read()
    {
        // Nothing to do: MQTT can not be actively readed.
        // MQTT brokers inform by broadcasting messages.
        // DO NOT DO THIS --> sendIsNotReadable();
    }

    /**
     * Sends a MQTT Message to the broker.
     * <pre>
     * A string as follows: "{ topic    = "sTopic",
     *                         payload  = xPayload,
     *                         qos      = nQoS,
     *                         retained = bRetained }"
     * </pre>
     * Where QoS (default is 2) and Retained (default is false) are optional.
     *
     * @param deviceName The name of the device.
     * @param request The message to be sent.
     */
    @Override
    public void write( Object request )
    {
        if( isFaked || isInvalid() || (client == null) )
            return;

        if( ! client.isConnected() )
        {
            sendWriteError( request, new IOException( "Not connected" ) );
            return;
        }

        if( ! (request instanceof pair) )
        {
            sendWriteError(request, new MingleException( "Type 'pair' expected" ) );
            return;
        }

        pair pReq  = (pair) request;
        list lKeys = pReq.keys();

        if( lKeys.has( "topic" ) && lKeys.has( "payload" ) )
        {
            Float   nQoS      = (Float)   pReq.get( "qos"     , 2f    );
            Boolean bRetained = (Boolean) pReq.get( "retained", false );

            try
            {
                client.send( pReq.get( "topic" ).toString(),
                             pReq.get( "payload" ).toString().getBytes(),
                             setBetween( "qos", 0, nQoS.intValue(), 2 ),
                             bRetained );

             // sendReaded( getName(), request ); --> This does not make sense because user can subscribe to whatever he/she wants
            }
            catch( Exception exc )
            {
                sendWriteError( request, exc );
            }
        }
        else
        {
            sendWriteError( request, new IllegalArgumentException( "Malformed message: can not be sent." ) );
        }
    }

    //------------------------------------------------------------------------//

    private void doSubscribe( IMqttClient client, Object oTopics ) throws Exception
    {
        String[] asTopics = oTopics.toString().split( ";" );
        String   sErrMsg  = "Malformed 'subscribe': "+ oTopics.toString();

        // Note: following is not needed bacause: "".split(";").length == 1
        // if( asTopics.length == 0 )
        //     throw new IllegalArgumentException( sErrMsg );

        for( String s : asTopics )
        {
            String[] asPair = s.split( "," );

            if( asPair.length != 2 )
                throw new IllegalArgumentException( sErrMsg );

            String sTopic = asPair[0].trim();   // trim() needed
            String sQoS   = asPair[1].trim();   // trim() needed

            if( sTopic.isEmpty() )
                throw new IllegalArgumentException( sErrMsg + ". Cause: empty topic.");

            if( ! Language.isNumber( sQoS ) )
                throw new IllegalArgumentException( sErrMsg + ". Cause: not a number for QoS: "+ sQoS );

            int nQoS = UtilType.toFloat( sQoS ).intValue();    // Une accepts float values

            client.subscribe( sTopic, UtilUnit.setBetween( 0, nQoS, 2 ) );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // MQTT Listener
    //------------------------------------------------------------------------//
    private final class MyListener implements IMqttClient.Listener
    {
        @Override
        public void onMessage( String topic, IMqttClient.Message msg )
        {
            sendReaded( new pair().split( msg ).put( "topic", topic ) );
        }

        @Override
        public void onError( Exception exc )
        {
            sendGenericError( ILogger.Level.SEVERE, exc.getMessage() );
        }
    }
}
