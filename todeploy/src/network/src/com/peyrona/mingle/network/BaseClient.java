
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

    protected void log( String msg )
    {
        log( msg, null );
    }

    protected void log( Throwable th )
    {
        log( null, th );
    }

    protected void log( String msg, Throwable th )
    {
        if( UtilSys.getLogger() == null )
        {
            if( msg != null )
                System.err.println( msg );

            if( th != null )
                th.printStackTrace( System.err );

            return;
        }

        if( msg != null )
            UtilSys.getLogger().log( ILogger.Level.SEVERE, th, msg );
    }
}