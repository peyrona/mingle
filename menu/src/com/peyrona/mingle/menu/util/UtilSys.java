
package com.peyrona.mingle.menu.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 *
 * @author francisco
 */
public class UtilSys
{
    public  static final String  sOS     = System.getProperty( "os.name" ).trim().toLowerCase();
    private static       Boolean isAdmin = null;

    //------------------------------------------------------------------------//

    public static boolean isEmpty( String str )
    {
        return (str == null || str.isEmpty());
    }

    public static boolean isNotEmpty( String str )
    {
        return ! isEmpty( str );
    }

    public static boolean isDevEnv()
    {
        return new File( System.getProperty( "user.home" ), "proyectos/mingle/todeploy" ).exists();
    }

    public static boolean isAdmin()
    {
        if( isAdmin == null )
            isAdmin = UtilSys.sOS.contains( "win" ) ? isWindowsAdmin() : isUnixAdmin();

        return isAdmin;
    }

    public static File getWorkingDir()
    {
        File fDevEnv = new File( System.getProperty( "user.home" ), "proyectos/mingle/todeploy" );

        if( fDevEnv.exists() )
            return fDevEnv;

        return new File( System.getProperty( "user.dir" ) );
    }

    public static String getOwner( File f )
    {
        try{ return Files.getOwner( f.toPath() ).getName(); }
        catch( IOException ioe ) { }

        return null;
    }
    //------------------------------------------------------------------------//
    // EXECUTION
    // Use Execute class for shell command execution with flexible scheduling options:
    //   ProcessResult result = new Execute.Builder("command", "args").build().executeAndWait();
    //   Process process = new Execute.Builder("command", "args").build().execute();
    //   ScheduledFuture future = new Execute.Builder("command", "args").rate(1000).build().schedule();

    //------------------------------------------------------------------------//
    // PRIVATE

    private static boolean isWindowsAdmin()
    {
        Process process = null;

        try
        {
            ProcessBuilder pb = new ProcessBuilder( "whoami", "/groups", "/fo", "csv" );
                           pb.redirectErrorStream( true );

            process = pb.start();

            try( BufferedReader reader = new BufferedReader( new InputStreamReader( process.getInputStream(), StandardCharsets.UTF_8 ) ) )
            {
                return reader.lines().anyMatch( line -> line.contains( "S-1-5-32-544" ) && line.toLowerCase().contains( "administrators" ) );
            }
        }
        catch( IOException ioe )
        {
            ioe.printStackTrace( System.err );
            return false;
        }
        finally     // Ensure process cleanup
        {
            if( process != null && process.isAlive() )
                process.destroyForcibly();
        }
    }

    private static boolean isUnixAdmin()
    {
        Process process = null;

        try
        {
            ProcessBuilder pb = new ProcessBuilder( "id", "-u" );
                           pb.redirectErrorStream( true );

            process = pb.start();

            if( ! process.waitFor( 2, TimeUnit.SECONDS ) )
                return false;

            try( BufferedReader reader = new BufferedReader( new InputStreamReader( process.getInputStream(), StandardCharsets.UTF_8 ) ) )
            {
                String uid = reader.readLine();
                return uid != null && "0".equals( uid.trim() );
            }
        }
        catch( IOException | InterruptedException ioe )
        {
            if( ioe instanceof InterruptedException )
                Thread.currentThread().interrupt(); // Restore interrupt status

            ioe.printStackTrace( System.err );
            return false;
        }
        finally     // Ensure process cleanup
        {
            if( process != null && process.isAlive() )
                process.destroyForcibly();
        }
    }
}
