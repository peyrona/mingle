
package com.peyrona.mingle.lang.messages;

/**
 * The Virtual World requests to change an Actuator in Real (physical) World.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public abstract class MsgAbstractOne extends Message
{
    public final String  name;
    public final boolean isOwn;

    //------------------------------------------------------------------------//

    /**
     * Constructor.
     *
     * @param name Device's name.
     */
    public MsgAbstractOne( String name )
    {
        this( name, true );
    }

    /**
     * Constructor.
     *
     * @param name Device's name.
     * @param isOwn true when the message is generated in this ExEn, false when comes for another (via network).
     */
    public MsgAbstractOne( String name, boolean isOwn )
    {
        this.name  = name;    // 'name' validity is checked by the transpiler. If someone call here from an Script, he/she must do it properly.
        this.isOwn = isOwn;
    }
}