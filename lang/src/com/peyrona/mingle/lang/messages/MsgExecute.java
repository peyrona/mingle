
package com.peyrona.mingle.lang.messages;

import com.peyrona.mingle.lang.MingleException;

/**
 * Request the execution of an SCRIPT or RULE's actions (unconditionally).
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class MsgExecute extends Message
{
    public MsgExecute( String scriptOrRuleName, boolean bForce )
    {
        this( scriptOrRuleName, bForce, true );
    }

    public MsgExecute( String scriptOrRuleName, boolean bForce, boolean isOwn )
    {
        super( scriptOrRuleName, check( bForce ), isOwn );
    }

    //------------------------------------------------------------------------//

    private static Object check( Object value )
    {
        if( value == null )
            throw new MingleException( MingleException.INVALID_ARGUMENTS, value );

        return value;
    }
}
