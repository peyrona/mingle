
package com.peyrona.mingle.controllers.mqtt;

/**
 * Mingle MQTT clients are always asynchronous.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface IMqttClient
{
    public static interface Listener
    {
        void onMessage( String topic, IMqttClient.Message msg );     // Message arrived
        void onError( Exception exc );
    }

    public static interface Message
    {
        String  getPayload();
        boolean isDuplicate();
        boolean isRetained();
        int     getQoS();
    }

    void    connect( String sURI, String sUID, String sUser, String sPwd ) throws Exception;
    boolean isConnected();
    void    close();
    void    send( String topic, byte[] payload, int qos, boolean isRetained ) throws Exception;
    void    subscribe( String topic, int qos ) throws Exception;
    boolean add( Listener l );
    boolean remove( Listener l );
}