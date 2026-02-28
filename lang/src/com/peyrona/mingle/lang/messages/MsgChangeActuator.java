
package com.peyrona.mingle.lang.messages;

import com.peyrona.mingle.lang.MingleException;

/**
 * The Virtual World requests to change an Actuator in Real (physical) World.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class MsgChangeActuator extends Message
{
    public MsgChangeActuator( String device, Object newValue )
    {
        this( device, newValue, true );
    }

    /**
     * Constructor.
     *
     * @param device   Device's deviceName.
     * @param newValue Any valid Une data newValue.
     * @param isOwn    {@code true} when the message is generated in this ExEn.
     */
    public MsgChangeActuator( String device, Object newValue, boolean isOwn )
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
