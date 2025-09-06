
package com.peyrona.mingle.controllers.mqtt;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
final class MqttMessage implements IMqttClient.Message
{
    private final String  payload;
    private final int     qos;
    private final boolean duplicate;
    private final boolean retained;

    //------------------------------------------------------------------------//

    MqttMessage( String payload, int qos, boolean isDuplicate, boolean isRetained )
    {
        this.payload   = payload;
        this.qos       = qos;
        this.duplicate = isDuplicate;
        this.retained  = isRetained;
    }

    @Override
    public String getPayload()
    {
        return payload;
    }

    @Override
    public int getQoS()
    {
        return qos;
    }

    @Override
    public boolean isDuplicate()
    {
        return duplicate;
    }

    @Override
    public boolean isRetained()
    {
        return retained;
    }

    @Override
    public String toString()
    {
        return "payload=" + payload + ", qos=" + qos + ", duplicate=" + duplicate + ", retained=" + retained;
    }
}