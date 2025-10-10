
package com.peyrona.mingle.controllers.mqtt;

import com.peyrona.mingle.lang.japi.ListenerWise;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class MqttClient4Paho
      extends ListenerWise<IMqttClient.Listener>
      implements IMqttClient
{
    private org.eclipse.paho.client.mqttv3.IMqttClient client = null;

    //------------------------------------------------------------------------//

    @Override
    public boolean isConnected()
    {
        return (client != null) && client.isConnected();
    }

    @Override
    public void connect( String sURI, String sUID, String sUser, String sPwd ) throws Exception
    {
        if( isConnected() )
            return;

        synchronized( this )
        {
            if( sUID == null )
                sUID = "Mingle_MQTT["+ UUID.randomUUID().toString() +']';

            MqttConnectOptions co = new MqttConnectOptions();
                               co.setAutomaticReconnect( true );
                               co.setCleanSession( true );

            if( sUser != null )
                co.setUserName( sUser );

            if( sPwd != null )
                co.setPassword( sPwd.toCharArray() );

            // NEXT: añadir las SSL Props
            //       co.setSSLProperties( cp.getSSLProperties() );

            org.eclipse.paho.client.mqttv3.IMqttClient cliente = new org.eclipse.paho.client.mqttv3.MqttClient( sURI, sUID );
                                                       cliente.setCallback( new Receiver() );
                                                       cliente.connect( co );

            this.client = cliente;    // Doing this, if there was an error during connect(...), this.client is still null
        }
    }

    @Override
    public void close()
    {
        try
        {
            client.close();
        }
        catch( MqttException ex )
        {
            forEachListener( l -> l.onError( ex ) );
        }

        synchronized( this )
        {
            client = null;
        }
    }

    @Override
    public void send( String topic, byte[] payload, int qos, boolean isRetained ) throws Exception
    {
        client.publish( topic, payload, qos, isRetained );
    }

    @Override
    public void subscribe( String topic, int qos ) throws Exception
    {
        client.subscribe( topic, qos );
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private final class Receiver implements MqttCallback
    {
        @Override
        public void messageArrived( final String topic, final org.eclipse.paho.client.mqttv3.MqttMessage message ) throws Exception
        {
            IMqttClient.Message msg = new MqttMessage( new String( message.getPayload(), StandardCharsets.UTF_8 ),
                                                       message.getQos(),
                                                       message.isDuplicate(),
                                                       message.isRetained() );

            forEachListener( l -> l.onMessage( topic, msg ) );
        }

        @Override
        public void connectionLost( Throwable th )
        {
            forEachListener( l -> l.onError( new MqttException( th ) ) );
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken imdt)
        {
        }
    }
}
