
package com.peyrona.mingle.lang.messages;

/**
 * A Device changed in real world (or in a different ExEn).<br>
 * Now, either a Driver receives this message from the Device's Controller or an ExEn
 * receives it from another ExEn. In both cases, this message indicates the receiver
 * will post a message of this class (MsgDeviceReaded) into the bus.
 * <p>
 * Although these messages can be read by anyone (as all message types), the only
 * natural receiver for this type of messages are Devices. When the device receives
 * this message, it updates its internal value (if it is appropriate (e.g.: delta
 * has to be taken in consideration) and if it proceeds, it sends a message of
 * type MsgDeviceChanged to the bus.
 *
 * @see MsgDeviceChanged
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class MsgDeviceReaded extends MsgAbstractTwo
{
    public MsgDeviceReaded( String device, Object newValue )
    {
        super( device, newValue );
    }
}