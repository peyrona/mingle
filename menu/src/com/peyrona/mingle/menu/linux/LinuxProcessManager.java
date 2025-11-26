package com.peyrona.mingle.menu.linux;

import com.peyrona.mingle.menu.core.AbstractProcessManager;
import com.peyrona.mingle.menu.core.Orchestrator.ProcessResult;
import com.peyrona.mingle.menu.util.UtilSys;
import com.peyrona.mingle.menu.util.UtilUI;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Linux-specific process manager using pgrep and ps commands.
 */
public class LinuxProcessManager extends AbstractProcessManager
{
    @Override
    public boolean isAvailable()
    {
        try
        {
            return UtilSys.executeAndWait( "pgrep", "--version" ).succeeded() &&
                   UtilSys.executeAndWait( "ps", "--version" ).succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public List<ProcessInfo> list() throws IOException, InterruptedException
    {
        List<ProcessInfo> lstProcs     = new ArrayList<>();
        Map<Long, String> mapJavaProcs = new HashMap<>();

        // Single system call to get ALL relevant process info
        ProcessResult psResult = UtilSys.executeAndWait( "ps", "-eo", "pid,cmd",
                                                         "--sort", "pid" );     // Ensure consistent ordering

        if( ! psResult.succeeded() )
        {
            System.err.println( "Failed to fetch process list." );
            return lstProcs;
        }

        // Parse ALL processes in one pass
        String[] lines = psResult.output.split( "\n" );

        if( lines.length < 2 )
            return lstProcs; // No processes found

        // Skip header line (index 0)
        for( int n = 1; n < lines.length; n++ )
        {
            String line = lines[n].trim();

            if( line.isEmpty() )
                continue;

            // Efficient PID/command extraction
            int firstSpace = line.indexOf( ' ' );

            if( firstSpace <= 0 )
                continue;

            String pidStr = line.substring( 0, firstSpace ).trim();
            String cmd    = line.substring( firstSpace + 1 ).trim();

            // Filter Java processes using efficient case-insensitive check
            if( ! UtilUI.contains( cmd, "java" ) )
                continue;

            try
            {
                long pid = Long.parseLong( pidStr );
                mapJavaProcs.put( pid, cmd );
            }
            catch( NumberFormatException e )
            {
                System.err.println( "Invalid PID format: "+ pidStr );
            }
        }

        // Batch-validate Mingle processes with minimal overhead
        mapJavaProcs.forEach( (pid, cmd) ->
                                {
                                    try
                                    {
                                        if( isMingle( cmd ) )
                                            lstProcs.add( new ProcessInfo( pid, cmd ) );
                                    }
                                    catch( Exception e )
                                    {
                                        System.out.println( "Error checking Mingle status for PID: "+ pid +"\n"+ e.getMessage() );
                                    }
                                } );

        return lstProcs;
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * Retrieves the command line for a process on Linux
     * @param pid the process ID
     * @return the command line string, or null if not found or error occurs
     */
    @Override
    protected String getCommandLine( long pid )
    {
        try
        {
            ProcessResult result = UtilSys.executeAndWait( "cat", "/proc/" + pid + "/cmdline" );

            if( result.succeeded() )
            {
                String cmdline = result.output;
                return cmdline.replace( '\0', ' ' ).trim();
            }
        }
        catch( IOException | InterruptedException exc )
        {
            exc.printStackTrace( System.err );
        }

        return null;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE
}