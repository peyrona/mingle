package com.peyrona.mingle.menu.mac;

import com.peyrona.mingle.menu.core.AbstractProcessManager;
import com.peyrona.mingle.menu.core.Orchestrator.ProcessResult;
import com.peyrona.mingle.menu.util.UtilSys;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * macOS-specific process manager using ps and kill commands.
 */
public class MacProcessManager extends AbstractProcessManager
{
    @Override
    public boolean isAvailable()
    {
        try
        {
            return UtilSys.executeAndWait( "ps", "-version" ).succeeded() &&
                   UtilSys.executeAndWait( "kill", "-l"     ).succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public List<ProcessInfo> list() throws IOException, InterruptedException
    {
        List<ProcessInfo> lstProcs       = new ArrayList<>();
        Pattern           javaJarPattern = Pattern.compile( "(?i)java.*\\.jar" ); // Case-insensitive precompiled pattern

        // Single system call to get ALL processes with PID and command
        ProcessResult psResult = UtilSys.executeAndWait( "ps", "-eo", "pid,command" );

        if( ! psResult.succeeded() )
        {
            System.err.println( "Failed to fetch macOS process list." );
            return lstProcs;
        }

        String[] lines = psResult.output.split( "\n" );

        if( lines.length < 2 )
            return lstProcs; // No processes (only header)

        // Pre-compile regex for efficient parsing
        final Pattern linePattern = Pattern.compile( "^\\s*(\\d+)\\s+(.*)$" );

        // First pass: collect candidate PIDs with Java+JAR commands
        Map<Long, String> candidateProcesses = new HashMap<>();

        for( int n = 1; n < lines.length; n++ )    // 1 to skip header line
        {
            String line = lines[n].trim();

            if( line.isEmpty() )
                continue;

            Matcher matcher = linePattern.matcher( line );

            if( matcher.matches() )
            {
                String pidStr  = matcher.group( 1 );
                String command = matcher.group( 2 );

                // Fast pre-filter before expensive isMingle() check
                if( javaJarPattern.matcher( command ).find() )
                {
                    try
                    {
                        candidateProcesses.put(Long.valueOf( pidStr ), command );
                    }
                    catch( NumberFormatException e )
                    {
                        //  "Invalid PID format in line: "+ line
                    }
                }
            }
         // else --> Malformed line
        }

        // Second pass: validate candidates with minimal overhead
        candidateProcesses.forEach( (pid, command) ->
                                    {
                                        try
                                        {
                                            if( isMingle( command ) )
                                                lstProcs.add( new ProcessInfo( pid, command ) );
                                        }
                                        catch( Exception e )
                                        {
                                            // "Error validating Mingle process PID: "+ pid
                                        }
                                    } );

        return lstProcs;
    }

    //------------------------------------------------------------------------//

    /**
     * Retrieves the command line for a process on macOS
     * @param pid the process ID
     * @return the command line string, or null if not found or error occurs
     */
    @Override
    public String getCommandLine( long pid )
    {
        Process process = null;

        try
        {
            process = Runtime.getRuntime().exec( new String[]
            {
                "ps", "-p", String.valueOf( pid ), "-o", "command="
            } );

            try( BufferedReader reader = new BufferedReader( new InputStreamReader( process.getInputStream() ) ) )
            {
                String cmdline = reader.readLine();

                if( UtilSys.isNotEmpty( cmdline ) )
                {
                    return cmdline.trim();
                }
            }

            process.waitFor();
        }
        catch( IOException | InterruptedException exc )
        {
            exc.printStackTrace( System.err );
        }
        finally
        {
            if( process != null )
                process.destroyForcibly();
        }

        return null;
    }
}