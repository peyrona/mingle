package com.peyrona.mingle.menu.core;

import com.peyrona.mingle.menu.util.UtilJVM;
import com.peyrona.mingle.menu.util.UtilSys;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public abstract class AbstractProcessManager implements IProcessManager
{
    @Override
    public boolean isAvailable()
    {
        return true;
    }

    /**
     * Executes a command in the background: the process survives menu app termination.
     *
     * @param jarName
     * @param lstOptions
     * @param args
     * @return The Process object for the background task.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public Process execJar( String jarName, List<String> lstOptions, String... args ) throws IOException
    {
        File workDir = UtilSys.getWorkingDir();
        File fLogDir = new File( workDir, "log" );

        ProcessBuilder pb = new ProcessBuilder( UtilJVM.buildJavaCmd( jarName, lstOptions, args ) )
                                    .directory( workDir );

        if( ! fLogDir.exists() )
        {
            System.err.println( "'log' folder does not exists. Can not redirect output to a file." );
        }
        else if( ! fLogDir.canWrite() )
        {
            System.err.println( "'log' folder is read-only. Can not redirect output to a file." );
        }
        else
        {
            File fLogFile = new File( fLogDir, jarName.replace( ".jar", "" ) + ".out.txt" );

            pb.redirectErrorStream( true );
            pb.redirectOutput( fLogFile );
        }

        return pb.start();
    }

    @Override
    public boolean kill( long pid, boolean forceful )
    {
        try
        {
            Optional<ProcessHandle> handle = ProcessHandle.of( pid );

            if( ! handle.isPresent() )
            {
                System.err.println( "Process with PID " + pid + " not found." );
                return false;
            }

            ProcessHandle processHandle = handle.get();

            if( forceful )  processHandle.destroyForcibly();
            else            processHandle.destroy();

            // Wait for process to actually die
            ProcessHandle process = processHandle.onExit().orTimeout( 3, TimeUnit.SECONDS ).join();

            return process != null && ! process.isAlive();
        }
        catch( Exception e )
        {
            System.err.println( "Error killing process " + pid + ": " + e.getMessage() );
            return false;
        }
    }

    //------------------------------------------------------------------------//

    /**
     * Returns the command line that launched the process associated with passed id.
     *
     * @param pid
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    protected abstract String getCommandLine( long pid ) throws IOException, InterruptedException;


    protected boolean isMingle( String cmd )
    {
        try
        {
            if( UtilSys.isEmpty( cmd ) )
                return false;

            cmd = cmd.trim().toLowerCase();

            if( cmd.contains( "com.peyrona.mingle.menu.main" ) )
                return false;                                    // We do not want to show the menu itself

            if( cmd.contains( "com.peyrona.mingle" ) )           // Check for Mingle main classes
                return true;

            return cmd.contains( "-javaagent:lib/lang.jar" );    // Check for Mingle Java agent
        }
        catch( Exception e )     // Log error for debugging but don't throw
        {
            System.err.println( "Error checking process " + cmd + ": " + e.getMessage() );
            return false;
        }
    }
}