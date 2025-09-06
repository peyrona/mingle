
package com.peyrona.mingle.glue;

import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */
public final class Util
{
    //------------------------------------------------------------------------//
    // ABOUT EXECUTING AND KILLING

    public static Process runGum()
    {
        File fJavaHome = UtilSys.getJavaHome();

        List<String> lstCmd = new ArrayList<>();
                     lstCmd.add( fJavaHome + "/bin/java" );
                     lstCmd.add("-cp");
                     lstCmd.add("gum.jar:lib/*:lib/gum/*");
                     lstCmd.add("-disableassertions");
                     lstCmd.add("-javaagent:lib/lang.jar");
                     lstCmd.add("com.peyrona.mingle.gum.Main");

        ProcessBuilder pb = new ProcessBuilder();
                       pb.directory( UtilSys.fHomeDir );
                       pb.environment().put( "JAVA_HOME", fJavaHome.getAbsolutePath() );
                       pb.command( lstCmd );
                       pb.redirectErrorStream( true );
                     pb.redirectOutput( Redirect.to( new File( UtilSys.fHomeDir, "mierda.txt" ) ) );    // Very useful for debugging
                    // pb.inheritIO(); -> When doing this, process.getInputStream() and process.getErrorStream() will not work

        try
        {
            final Process process = pb.start();

            if( ! process.isAlive() )
                throw new IOException( "Process Gum is not alive" );

            return process;
        }
        catch( IOException ex )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, ex, "Error executing Gum" );
            JTools.error( ex );
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

        List<String> lstCmd = new ArrayList<>();
                     lstCmd.add( fJavaHome + "/bin/java" );
                     lstCmd.add( "-javaagent:"+ UtilSys.fHomeDir + File.separator +"lib"+ File.separator +"lang.jar" );
                     lstCmd.add( "-jar" );
                     lstCmd.add( "stick.jar" );

        if( sFileConfig != null )
            lstCmd.add( "-config="+ sFileConfig );

        if( sFileModel != null )
            lstCmd.add( sFileModel );

        ProcessBuilder pb = new ProcessBuilder();
                       pb.directory( UtilSys.fHomeDir );
                       pb.environment().put( "JAVA_HOME", fJavaHome.getAbsolutePath() );
                       pb.command( lstCmd );
                       pb.redirectErrorStream( true );
                    // pb.redirectOutput( Redirect.to( new File( UtilSys.fHomeDir, "mierda.txt" ) ) );    // Very useful for debugging
                    // pb.inheritIO(); -> When doing this, process.getInputStream() and process.getErrorStream() will not work

        try
        {
            final Process process = pb.start();

            if( ! process.isAlive() )
                throw new IOException( "Process Stick is not alive" );

            return process;
        }
        catch( IOException ex )
        {
            UtilSys.getLogger().log( ILogger.Level.WARNING, ex, "Error executing Stick" );
            JTools.error( ex );
        }

        return null;
    }

    public static void catchOutput( Process process, Consumer<Character> onOutput )
    {
        if( process == null || onOutput == null )
            return;

        UtilSys.execute( Util.class.getName(),
                         () ->
                            {
                                int nIn;

                                try( BufferedReader brIn1 = new BufferedReader( new InputStreamReader( process.getInputStream() ) );
                                     BufferedReader brIn2 = new BufferedReader( new InputStreamReader( process.getErrorStream() ) ) )
                                {
                                    while( process.isAlive() )
                                    {
                                        nIn = brIn1.read();

                                        if( nIn != -1 )
                                            onOutput.accept( (char) nIn );

                                        nIn = brIn2.read();

                                        if( nIn != -1 )
                                            onOutput.accept( (char) nIn );
                                    }
                                }
                                catch( IOException ex )
                                {
                                    // Nothing to do
                                }
                                finally
                                {
                                    onOutput.accept( '\0' );
                                }
                            } );
    }

    public static void killProcess( Process proc )
    {
        if( proc == null )
            return;

        boolean bOK = false;

        if( UtilSys.isLinux() )
        {
            try
            {
                ProcessBuilder builder  = new ProcessBuilder( "kill", "-9", Long.toString( proc.pid() ) );
                Process        process  = builder.start();
                int            exitCode = process.waitFor();

                bOK = (exitCode == 0);
            }
            catch( IOException | SecurityException | InterruptedException exc )
            {
                // JTools.error( exc );    // Best effort was done: nothing else to do
            }
        }

        if( ! bOK )
        {
            UtilSys.execute( Util.class.getName(),
                             () ->
                                {
                                    try
                                    {
                                        proc.destroyForcibly().waitFor();
                                    }    // See API doc for ".waitFor()"
                                    catch( InterruptedException ex )
                                    {
                                        /* Nothing to do */
                                    }
                                } );
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private Util()
    {
    }
}