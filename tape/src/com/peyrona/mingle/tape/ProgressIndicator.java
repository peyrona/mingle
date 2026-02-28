package com.peyrona.mingle.tape;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple progress indicator that prints a dot to console every second.
 * <p>
 * Used during long-running operations like transpilation to show activity.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ProgressIndicator
{
    private final Thread        thread;
    private final AtomicBoolean running = new AtomicBoolean( false );

    /**
     * Creates a new ProgressIndicator instance (but does not start it).
     */
    public ProgressIndicator()
    {
        thread = new Thread( this::printDots_, "ProgressIndicator" );
        thread.setDaemon( true );
    }

    /**
     * Starts progress indicator.
     * <p>
     * Begins printing dots to console every second.
     */
    public void start()
    {
        if( running.compareAndSet( false, true ) )
        {
            System.out.print( ">" );
            thread.start();
        }
    }

    /**
     * Stops progress indicator.
     * <p>
     * Waits for thread to finish and prints a newline.
     */
    public void stop()
    {
        if( running.compareAndSet( true, false ) )
        {
            try
            {
                thread.join( 100 );
            }
            catch( InterruptedException ignored )
            { }

            System.out.println( '<' );
        }
    }

    /**
     * Prints dots every second while running flag is true.
     */
    private void printDots_()
    {
        while( running.get() )
        {
            System.out.print( ':' );

            try
            {
                Thread.sleep( 500 );
            }
            catch( InterruptedException ignored )
            {
                break;
            }
        }
    }
}
