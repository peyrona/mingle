package com.peyrona.mingle.menu.win;

import com.peyrona.mingle.menu.core.AbstractProcessManager;
import com.peyrona.mingle.menu.core.Orchestrator.ProcessResult;
import com.peyrona.mingle.menu.util.UtilSys;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Windows-specific process manager using tasklist and taskkill commands.
 */
public class WinProcessManager extends AbstractProcessManager
{
    @Override
    public boolean isAvailable()
    {
        try
        {
            return UtilSys.executeAndWait( "tasklist", "/?" ).succeeded() &&
                   UtilSys.executeAndWait( "taskkill", "/?" ).succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public List<ProcessInfo> list() throws IOException, InterruptedException
    {
        final String WMIC_ERROR_PREFIX = "WMIC command failed: ";

        List<ProcessInfo> lstProcs = new ArrayList<>();

        // Single WMIC call to get ALL Java processes with PID and command line
        ProcessResult result = UtilSys.executeAndWait( "wmic", "process", "where", "name='java.exe'",
                                                       "get", "processid,commandline", "/format:csv" );

        if( ! result.succeeded() )
            throw new IOException( WMIC_ERROR_PREFIX + " error accessing internal processes lsit" );

        String output = result.output;

        if( UtilSys.isEmpty( output ) )
            return lstProcs;

        // Efficient CSV parsing (WMIC /format:csv outputs true CSV)
        String[] lines = output.split( "\\r?\\n" );

        if( lines.length < 2 )
            return lstProcs; // No data rows (only header)

        // Pre-compile patterns for validation
        final Pattern minglePattern = Pattern.compile( "(?i)(?:mingle|automation).*\\.jar" );
        final int PID_COL = 1;  // CSV column index for ProcessId
        final int CMD_COL = 2;  // CSV column index for CommandLine

        // Process all lines in single pass
        for( int n = 1; n < lines.length; n++ )    // 1 --> skip CSV header
        {
            String line = lines[n].trim();

            if( line.isEmpty() || line.startsWith( "Node," ) )
                continue; // Skip empty/summary lines

            String[] cols = line.split( "\",\"", -1 ); // Handle quoted fields with commas

            if( cols.length <= CMD_COL )
                continue;

            // Extract and clean PID
            String pidStr = cols[PID_COL].replace( "\"", "" ).trim();

            if( pidStr.isEmpty() )
                continue;

            // Extract and clean command line
            String command = cols[CMD_COL]
                    .replace( "\"", "" )
                    .replace( "\\\\", "\\" ) // Fix escaped backslashes
                    .trim();

            // Pre-filter using command line before expensive checks
            if( ! minglePattern.matcher( command ).find() )
                continue;

            try
            {
                if( isMingle( command ) )    // Validate with isMingle only after command line pre-filter
                    lstProcs.add( new ProcessInfo( Long.parseLong( pidStr ), command ) );
            }
            catch( NumberFormatException e )
            {
                //  "Invalid PID format [{}] in line: {}", pidStr, line );
            }
            catch( Exception e )
            {
                System.out.println( "Error processing PID:"+ pidStr );
            }
        }

        return lstProcs;
    }

    //------------------------------------------------------------------------//

    /**
     * Retrieves the command line for a process on Windows
     * @param pid the process ID
     * @return the command line string, or null if not found or error occurs
     */
    @Override
    public String getCommandLine( long pid )
    {
        Process process = null;

        try
        {
            process = Runtime.getRuntime().exec( new String[] { "wmic", "process", "where", "ProcessId=" + pid, "get", "CommandLine", "/format:list" } );

            try( BufferedReader reader = new BufferedReader( new InputStreamReader( process.getInputStream() ) ) )
            {
                String line;
                while( (line = reader.readLine()) != null )
                {
                    if( line.startsWith( "CommandLine=" ) )
                    {
                        String cmdline = line.substring( "CommandLine=".length() ).trim();

                        if( ! cmdline.isEmpty() )
                            return cmdline;
                    }
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