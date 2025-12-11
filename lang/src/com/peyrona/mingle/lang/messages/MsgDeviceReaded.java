
package com.peyrona.mingle.lang.messages;

/**
 * A Device changed in real world (or in a different grid node).<br>
 * When changed in real world, the Driver receives this message from the Device's Controller.<br>
 * When changed in another ExEn, the message is received from this ExEn.<br>
 *
 * @see MsgDeviceChanged
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class MsgDeviceReaded extends MsgAbstractTwo
{
    public MsgDeviceReaded( String device, Object newValue )
    {
        super( device, newValue, true );
    }

    public MsgDeviceReaded( String device, Object newValue, boolean isOWn )
    {
        super( device, newValue, isOWn );
    }
}