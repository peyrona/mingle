package com.peyrona.mingle.tape;

import java.io.PrintWriter;
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
    private final PrintWriter   console;
    private final Thread        thread;
    private final AtomicBoolean running = new AtomicBoolean( false );

    /**
     * Creates a new ProgressIndicator instance (but does not start it).
     * @param console
     */
    public ProgressIndicator( PrintWriter console )
    {
        this.console = console;
        this.thread  = new Thread( this::printDots_, "ProgressIndicator" );
        this.thread.setDaemon( true );
    }

    /**
     * Starts progress indicator.
     * <p>
     * Begins printing dots to console every second.
     */
    public void start()
    {
        if( running.compareAndSet( false, true ) )
            thread.start();
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
            try { thread.join( 100 ); }
            catch( InterruptedException ignored ) { }
        }
    }

    /**
     * Prints dots every second while running flag is true.
     */
    private void printDots_()
    {
        try { thread.sleep( 1000 ); }              // 1 sec initial delay because most part of Une
        catch( InterruptedException ignored ) { }  // scripts can be transpiled in less than 1 sec

        if( ! running.get() )                      // finished in ≤1 sec: show nothing
            return;

        console.print( ">" );

        while( running.get() )
        {
            console.print( ':' );

            try
            {
                Thread.sleep( 500 );
            }
            catch( InterruptedException ignored )
            {
                break;
            }
        }

        console.println( '<' );
        console.flush();
    }
}