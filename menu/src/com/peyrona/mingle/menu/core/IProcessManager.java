package com.peyrona.mingle.menu.core;

import com.peyrona.mingle.menu.util.UtilUI;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interface for platform-specific process management.
 * Each platform (Linux, macOS, Windows) will implement this interface
 * to provide process listing and management functionality.
 */
public interface IProcessManager
{
    //------------------------------------------------------------------------//
    // INNER CLASS

    public static final class ProcessInfo
    {
        public final long   pid;
        public final String jar;
        public final String command;

        public ProcessInfo( long pid, String cmd )
        {
            this.pid     = pid;
            this.command = cmd;
            this.jar     = getJar( cmd );
        }

        private static String getJar( String cmd )
        {
            // First: check main class pattern (e.g. "com.peyrona.mingle.glue.Main")
            // This is more reliable than checking JAR names in classpath

            int ndx1 = cmd.indexOf( "com.peyrona.mingle." );

            if( ndx1 > -1 )
            {
                    ndx1 += "com.peyrona.mingle.".length();
                int ndx2  = cmd.indexOf( '.', ndx1 );

                if( ndx2 > -1 )
                    return UtilUI.capitalize( cmd.substring( ndx1, ndx2 ) );
            }

            // Second: check for -jar execution (e.g. "-jar lib/glue.jar")

            Pattern jarPattern = Pattern.compile( "-jar\\s+(?:lib/)?(\\w+)\\.jar" );
            Matcher jarMatcher = jarPattern.matcher( cmd );

            if( jarMatcher.find() )
                return UtilUI.capitalize( jarMatcher.group( 1 ) );

            // Third: check classpath for last JAR entry

            Pattern classpathPattern = Pattern.compile("-classpath\\s+([^\"\\s]+?)(?:\\s+-\\w+|\\s*$)");
            Matcher matcher = classpathPattern.matcher( cmd );

            if( matcher.find() )
            {
                String   classpath = matcher.group( 1 ).trim();
                String[] entries   = classpath.split( ":" );

                // Iterate BACKWARDS to find the last JAR entry

                for( int n = entries.length - 1; n >= 0; n-- )
                {
                    String entry = entries[n].trim();

                    if( entry.isEmpty() || ! entry.endsWith( ".jar" ) )
                        continue;    // Skip empty entries and non-JAR files

                    return UtilUI.capitalize( new File( entry ).getName() );
                }
            }

            return "unknown"; // Fallback
        }
    }

    //------------------------------------------------------------------------//

    Process execJar( String jarName, List<String> lstOptions, String... args ) throws IOException;

    /**
     * Scans for all running Mingle processes on the system
     * @return List of Process objects representing Mingle processes
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    List<ProcessInfo> list() throws IOException, InterruptedException;

    /**
     * Kills a process.
     *
     * @param proc The process to kill.
     * @param forceful If true, use SIGKILL (forceful), if false, use SIGTERM (graceful).
     * @return The received process.
     */
    boolean kill( long pid, boolean forceful );

    /**
     * Checks if the process manager is available on the current platform.
     * @return true if the process manager is available, false otherwise
     */
    boolean isAvailable();
}