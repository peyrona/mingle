
package com.peyrona.mingle.lang;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public class MingleException extends RuntimeException
{
    public static final short SHOULD_NOT_HAPPEN = 0;
    public static final short INVALID_ARGUMENTS = 1;

    private static final String[] asMSG = new String[] { "This should not happen", "Invalid arguments" };

    //------------------------------------------------------------------------//

    public MingleException()
    {
        super( "" );
    }

    public MingleException( short msg )
    {
        super( asMSG[msg] );
    }

    public MingleException( String message )
    {
        super( message );
    }

    public MingleException( Throwable cause )
    {
        super( cause );
    }

    public MingleException( String message, Throwable cause )
    {
        super( message, cause );
    }
}