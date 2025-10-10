
package com.peyrona.mingle.lang.messages;

/**
 * A Device received a message of type MsgDeviceReaded, and if the device effectively
 * changed its value (delta and others considerations) then, the device will send a
 * message of this type to the bus.
 *
 * @see MsgDeviceReaded
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class MsgDeviceChanged extends MsgAbstractTwo
{
    public MsgDeviceChanged( String device, Object newValue )
    {
        super( device, newValue );
    }
}