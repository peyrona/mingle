package com.peyrona.mingle.menu.core;

import com.peyrona.mingle.menu.util.UtilSys;
import java.io.IOException;

public abstract class AbstractServiceManager implements IServiceManager
{
    // Force subclasses to implement this check
    @Override
    public abstract boolean isAvailable();

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * Common implementation for showing service logs with consistent formatting.
     * Subclasses should override getLogContent() to provide platform-specific log retrieval.
     *
     * @param service
     * @param sExtraNoLogMsg
     */
    @Override
    public boolean showLog( String service )
    {
        try
        {
            System.out.println( "===============================================" );
            System.out.println( "           " + service.substring( 0, 1 ).toUpperCase() + service.substring( 1 ) + " Service Log" );
            System.out.println( "===============================================" );

            String logContent = getLogContent( service );

            if( UtilSys.isNotEmpty( logContent ) )  System.out.println( logContent );
            else                                    printNoLogMessage( service );

            return true;
        }
        catch( IOException | InterruptedException e )
        {
            System.err.println( "Error retrieving service logs: " + e.getMessage() );
            return false;
        }
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * Template method for subclasses to implement platform-specific log retrieval.
     *
     * @param service The service name (e.g., "gum", "stick")
     * @return Log content as string, or null/error message if retrieval failed
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    protected abstract String getLogContent( String service ) throws IOException, InterruptedException;

    /**
     * Prints message when no logs are found.
     * @param service
     */
    protected void printNoLogMessage( String service )
    {
        System.out.println( "No log entries found for " + service + " service." );
    }
}