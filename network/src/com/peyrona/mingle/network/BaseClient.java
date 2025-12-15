
package com.peyrona.mingle.network;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.ListenerWise;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;

/**
 * Base class for all network client implementations.
 * Provides common functionality for logging, error handling, and listener management.
 *
 * @author francisco
 */
public abstract
       class      BaseClient
       extends    ListenerWise<INetClient.IListener>
       implements INetClient
{
    /**
     * Returns a string representation of this client.
     *
     * @return string representation
     */
    @Override
    public String toString()
    {
        return UtilStr.toString( this );
    }

    //------------------------------------------------------------------------//

    /**
     * Logs a message with the specified log level.
     *
     * @param levcl the log level
     * @param msg the message to log
     * @return this client instance for method chaining
     */
    protected INetClient log( ILogger.Level levcl, String msg )
    {
        return log( msg, null );
    }

    /**
     * Logs an exception.
     *
     * @param th the exception to log
     * @return this client instance for method chaining
     */
    protected INetClient log( Throwable th )
    {
        return log( null, th );
    }

    /**
     * Logs a message and/or exception.
     * Uses system error output if no logger is available, otherwise uses the configured logger.
     *
     * @param msg the message to log (can be null)
     * @param th the exception to log (can be null)
     * @return this client instance for method chaining
     */
    protected INetClient log( String msg, Throwable th )
    {
        UtilSys.getLogger().log( ILogger.Level.SEVERE, th, msg );
        return this;
    }

    /**
     * Sends an error notification to all registered listeners.
     *
     * @param exc the exception to notify listeners about (can be null)
     * @return this client instance for method chaining
     */
    protected INetClient sendError( Exception exc )
    {
        if( exc != null )
            forEachListener( (l) -> l.onError( this, exc ) );

        return this;
    }
}