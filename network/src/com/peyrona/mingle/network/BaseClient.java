
package com.peyrona.mingle.network;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.ListenerWise;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;

/**
 *
 * @author francisco
 */
public abstract
       class      BaseClient
       extends    ListenerWise<INetClient.IListener>
       implements INetClient
{
    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//

    protected INetClient log( ILogger.Level levcl, String msg )
    {
        return log( msg, null );
    }

    protected INetClient log( Throwable th )
    {
        return log( null, th );
    }

    protected INetClient log( String msg, Throwable th )
    {
        if( UtilSys.getLogger() == null )
        {
            if( msg != null )
                System.err.println( msg );

            if( th != null )
                th.printStackTrace( System.err );
        }
        else if( msg != null )
        {
            UtilSys.getLogger().log( ILogger.Level.SEVERE, th, msg );
        }

        return this;
    }

    protected INetClient sendError( Exception exc )
    {
        if( exc != null )
            forEachListener( (l) -> l.onError( this, exc ) );

        return this;
    }
}