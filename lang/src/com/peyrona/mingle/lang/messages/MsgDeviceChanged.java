
package com.peyrona.mingle.lang.messages;

/**
 * A Controller or another grid node sent this message: if it was sent by another node,
 * the device effectively got a new value. But if the device belongs to current node,
 * it is needed to check if the new value will produce a change in the device's value
 * (delta and others considerations) and act consequently.
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
        super( device, newValue, true );
    }

    public MsgDeviceChanged( String device, Object newValue, boolean isOwn )
    {
        super( device, newValue, isOwn );
    }
}