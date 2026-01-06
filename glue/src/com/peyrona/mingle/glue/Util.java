
package com.peyrona.mingle.glue;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Util
{
    //------------------------------------------------------------------------//
    // ABOUT EXECUTING AND KILLING

    public static Process runGum()
    {
        File   fJavaHome   = UtilSys.getJavaHome();
        String javaExecute = UtilSys.isWindows() ? "java.exe" : "java";

        List<String> lstCmd = new ArrayList<>();
                     lstCmd.add( fJavaHome + File.separator + "bin" + File.separator + javaExecute );
                     lstCmd.add( "-cp" );
                     lstCmd.add( "gum.jar" + File.pathSeparator + "lib/*" + File.pathSeparator + "lib/gum/*" );
                     lstCmd.add( "-disableassertions" );
                     lstCmd.add( "-javaagent:lib/lang.jar" );
                     lstCmd.add( "com.peyrona.mingle.gum.Main" );

        ProcessBuilder pb = new ProcessBuilder();
                       pb.directory( UtilSys.fHomeDir );
                       pb.environment().put( "JAVA_HOME", fJavaHome.getAbsolutePath() );
                       pb.command( lstCmd );
                       pb.redirectErrorStream( true );
                    // pb.redirectOutput( Redirect.to( new File( UtilSys.fHomeDir, "gum-inside-glue.out.txt" ) ) );    // Very useful for debugging
                    // pb.inheritIO(); -> When doing this, process.getInputStream() and process.getErrorStream() will not work

        try
        {
            final Process process = pb.start();
            return validateProcessStartup( process, "Gum" );
        }
        catch( IOException ex )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, ex, "Error executing Gum" );
            JTools.error( ex );
        }
        catch( InterruptedException ex )
        {
            Thread.currentThread().interrupt();
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Interrupted while starting Gum process" );
        }

        return null;
    }

    public static Process runStick()
    {
        return runStick( null, null );
    }

    public static Process runStick( File fModel, File fConfig )
    {
        if( (fModel != null) && (! fModel.exists()) )
        {
            JTools.error( "Model \""+ fModel +"\": does not exists." );
            return null;
        }

        if( (fConfig != null) && (! fConfig.exists()) )
        {
            JTools.error( "Config \""+ fConfig +"\": does not exists." );
            return null;
        }

        String sFileModel  = ((fModel  == null) ? null : fModel.getAbsolutePath());
        String sFileConfig = ((fConfig == null) ? null : fConfig.getAbsolutePath());
        File   fJavaHome   = UtilSys.getJavaHome();
        String javaExec    = UtilSys.isWindows() ? "java.exe" : "java";
               javaExec    = fJavaHome + File.separator + "bin" + File.separator + javaExec;
        String javaAgent   = "-javaagent:"+ UtilSys.fHomeDir + File.separator +"lib"+ File.separator +"lang.jar";
        String javaClass   = "stick.jar"+ File.pathSeparator +"lib" + File.separator +"network.jar";

        List<String> lstCmd = new ArrayList<>();
                     lstCmd.add( javaExec     );
                     lstCmd.add( javaAgent    );
                     lstCmd.add( "-classpath" );
                     lstCmd.add( javaClass    );
                     lstCmd.add( "com.peyrona.mingle.stick.Main" );

        if( sFileConfig != null )
            lstCmd.add( "-config="+ sFileConfig );

        if( sFileModel != null )
            lstCmd.add( sFileModel );

        ProcessBuilder pb = new ProcessBuilder();
                       pb.directory( UtilSys.fHomeDir );
                       pb.environment().put( "JAVA_HOME", fJavaHome.getAbsolutePath() );
                       pb.command( lstCmd );
                       pb.redirectErrorStream( true );
                    // pb.redirectOutput( Redirect.to( new File( UtilSys.fHomeDir, "stick-inside-glue.out.txt" ) ) );    // Very useful for debugging
                    // pb.inheritIO(); -> When doing this, process.getInputStream() and process.getErrorStream() will not work

        try
        {
            final Process process = pb.start();
            return validateProcessStartup( process, "Stick" );
        }
        catch( IOException ex )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, ex, "Error executing Stick" );
            JTools.error( ex );
        }
        catch( InterruptedException ex )
        {
            Thread.currentThread().interrupt();
            UtilSys.getLogger().log( ILogger.Level.WARNING, "Interrupted while starting Stick process" );
        }

        return null;
    }

    public static void catchOutput(Process process, Consumer<Character> onOutput)
    {
        if( process == null || onOutput == null )
            return;

        // Cache the line separator to avoid repeated System.lineSeparator() calls
        final String lineSeparator = System.lineSeparator();
        final char[] separatorChars = lineSeparator.toCharArray();

        Thread t1 = new Thread( () ->
        {
            try( BufferedReader brIn1 = new BufferedReader( new InputStreamReader( process.getInputStream(), StandardCharsets.UTF_8 ) ) )
            {
                String line;

                while( !Thread.currentThread().isInterrupted() && (line = brIn1.readLine()) != null )
                {
                    // Process characters efficiently without creating temporary arrays
                    for( int i = 0; i < line.length(); i++ )
                    {
                        onOutput.accept( line.charAt( i ) );
                    }

                    // Send all line separator characters to preserve line endings correctly on all platforms
                    for( char separatorChar : separatorChars )
                    {
                        onOutput.accept( separatorChar );
                    }
                }
            }
            catch( IOException ex )
            {
                // Stream closed or error - normal termination
            }
        } );

        Thread t2 = new Thread( () ->
        {
            try( BufferedReader brIn2 = new BufferedReader( new InputStreamReader( process.getErrorStream(), StandardCharsets.UTF_8 ) ) )
            {
                String line;

                while( ! Thread.currentThread().isInterrupted() && (line = brIn2.readLine()) != null )
                {
                    // Process characters efficiently without creating temporary arrays
                    for( int i = 0; i < line.length(); i++ )
                        onOutput.accept( line.charAt( i ) );

                    // Send all line separator characters to preserve line endings correctly on all platforms
                    for( char separatorChar : separatorChars )
                        onOutput.accept( separatorChar );
                }
            }
            catch( IOException ex )
            {
                // Stream closed or error - normal termination
            }
        } );

        UtilSys.execute( Util.class.getSimpleName() +":catchProcessOutput",
                        () ->
                           {
                               try
                               {
                                   t1.start();
                                   t2.start();

                                   // Wait for process to complete (indefinitely for long-running processes)
                                   process.waitFor();

                                   // After process completes, give streams reasonable time to flush remaining output
                                   t1.join( 5000 );  // 5 seconds timeout for stdout
                                   t2.join( 5000 );  // 5 seconds timeout for stderr
                               }
                               catch( InterruptedException ex )
                               {
                                   // Restore interrupted status and interrupt stream threads
                                   Thread.currentThread().interrupt();
                                   t1.interrupt();
                                   t2.interrupt();
                               }
                           } );
    }

    public static void killProcess( Process proc )
    {
        if( proc == null )
            return;

        UtilSys.execute( Util.class.getSimpleName() +":killProcess",
                         () ->
                            {
                                try
                                {
                                    proc.destroyForcibly().waitFor();
                                }
                                catch( InterruptedException ex )
                                {
                                    Thread.currentThread().interrupt();
                                }
                            } );
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private Util()
    {
    }

    private static Process validateProcessStartup( Process process, String processName ) throws IOException, InterruptedException
    {
        // Give the process a moment to start and potentially fail
        Thread.sleep( 100 );

        if( ! process.isAlive() )
        {
            int exitCode = process.exitValue();
            String errorMsg = String.format( "Process %s failed to start (exit code: %d)", processName, exitCode );

            // Add specific diagnostics
            switch( exitCode )
            {
                case 1:
                    errorMsg += " - Configuration or classpath issue";
                    break;
                case 127:
                    errorMsg += " - Command not found (check Java installation)";
                    break;
                default:
                    if( exitCode > 128 )
                        errorMsg += " - Terminated by signal " + (exitCode - 128);
                    break;
            }

            throw new IOException( errorMsg );
        }

        return process;
    }
}