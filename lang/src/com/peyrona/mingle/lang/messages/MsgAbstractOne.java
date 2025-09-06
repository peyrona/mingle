
package com.peyrona.mingle.lang.messages;

/**
 * The Virtual World requests to change an Actuator in Real (physical) World.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public abstract class MsgAbstractOne extends Message
{
    public final String name;

    //------------------------------------------------------------------------//

    /**
     * Constructor.
     *
     * @param name Device's name.
     */
    public MsgAbstractOne( String name )
    {
        this.name = name;    // 'name' validity is checked by the transpiler. If someone call here from an Script, he/she must do it properly.
    }
}