
package com.peyrona.mingle.lang.messages;

import com.peyrona.mingle.lang.japi.UtilStr;

/**
 * An error happened (most part of the time at Controllers that are who deal with physical world).
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public class MsgError extends Message
{
    public final String message;
    public final String device;

    //------------------------------------------------------------------------//
// TODO: usar esta clase para utilizar la clausla onERROR en las RULE
//       CUIDADO, esto altera muchas cosas en muchos sitios
    public MsgError( String msg )
    {
        this( msg, null );
    }

    public MsgError( Exception exc )
    {
        this( exc, null );
    }

    public MsgError( Exception exc, String device )
    {
        this( UtilStr.toString( exc ), device );
    }

    public MsgError( String message, String device )
    {
        this.message = message;
        this.device  = device;
    }
}