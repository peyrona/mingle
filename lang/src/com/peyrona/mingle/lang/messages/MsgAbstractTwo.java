
package com.peyrona.mingle.lang.messages;

/**
 * The Virtual World requests to change an Actuator in Real (physical) World.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public abstract class MsgAbstractTwo extends MsgAbstractOne
{
    public final Object value;

    //------------------------------------------------------------------------//

    /**
     * Constructor.
     *
     * @param name Device's name.
     * @param value  Any valid Une data value.
     */
    public MsgAbstractTwo( String name, Object value )
    {
        this( name, value, true );
    }

    public MsgAbstractTwo( String name, Object value, boolean isOwn )
    {
        super( name, isOwn );

        this.value = value;
    }
}