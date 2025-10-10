
package com.peyrona.mingle.lang.messages;

/**
 * The Virtual World requests to change an Actuator in Real (physical) World.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class MsgChangeActuator extends MsgAbstractTwo
{
    /**
     * Constructor.
     *
     * @param device Device's deviceName.
     * @param newValue Any valid Une data newValue.
     */
    public MsgChangeActuator( String device, Object newValue )
    {
        super( device, newValue );
    }
}