
package com.peyrona.mingle.lang.messages;

/**
 * Request the execution of an SCRIPT or RULE's actions (unconditionally).
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class MsgExecute extends MsgAbstractTwo
{
    public MsgExecute( String scriptOrRuleName, boolean bForce )
    {
        super( scriptOrRuleName, bForce, true );
    }

    public MsgExecute( String scriptOrRuleName, boolean bForce, boolean isOwn )
    {
        super( scriptOrRuleName, bForce, isOwn );
    }
}