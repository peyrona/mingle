
package com.peyrona.mingle.lang.messages;

import com.peyrona.mingle.lang.MingleException;

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
public class MsgDeviceReaded extends Message
{
    public MsgDeviceReaded( String device, Object newValue )
    {
        this( device, newValue, true );
    }

    public MsgDeviceReaded( String device, Object newValue, boolean isOwn )
    {
        super( device, check( newValue ), isOwn );
    }

    //------------------------------------------------------------------------//

    private static Object check( Object value )
    {
        if( value == null )
            throw new MingleException( MingleException.INVALID_ARGUMENTS, value );

        return value;
    }
}
