package com.peyrona.mingle.menu.mac;

import com.peyrona.mingle.menu.core.AbstractServiceManager;
import com.peyrona.mingle.menu.util.UtilJVM;
import com.peyrona.mingle.menu.util.UtilSys;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * macOS-specific service manager using launchd.
 */
public class MacServiceManager extends AbstractServiceManager
{
    private static final String LAUNCH_AGENTS_DIR = System.getProperty( "user.home" ) + "/Library/LaunchAgents";
    private static final String BUNDLE_ID_PREFIX = "com.mingle.";

    //------------------------------------------------------------------------//

    @Override
    public boolean isAvailable()
    {
        try    // Check if launchctl is available (always available on macOS)
        {
            return UtilSys.executeAndWait( "launchctl", "version" ).succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public boolean exists( String service )
    {
        return Files.exists( Paths.get( getPlistFilePath( service ) ) );
    }

    @Override
    public boolean create( String jarName, List<String> lstOptions, String... args )
    {
        String plistFile = getPlistFilePath( jarName );

        try
        {
            // Create LaunchAgents directory if it doesn't exist
            Files.createDirectories( Paths.get( LAUNCH_AGENTS_DIR ) );

            // Build plist content
            String plistContent = buildPlistContent( jarName, lstOptions, args );

            // Write plist file
            Files.write( Paths.get( plistFile ), plistContent.getBytes( StandardCharsets.UTF_8 ) );

            // Load the service
            UtilSys.executeAndWait( "launchctl", "load", plistFile );

            return true;
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public boolean start( String service )
    {
        try
        {
            return UtilSys.executeAndWait( "launchctl", "start", getBundleId( service ) ).succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public boolean stop( String service )
    {
        try
        {
            return UtilSys.executeAndWait( "launchctl", "stop", getBundleId( service ) ).succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public boolean restart( String service )
    {
        // launchd doesn't have a direct restart command, so stop then start
        return stop( service ) && start( service );
    }

    @Override
    public String getStatus( String service )
    {
        try
        {
            String output = UtilSys.executeAndWait( "launchctl", "list", getBundleId( service ) )
                                    .output;

            if( output.trim().isEmpty() )
                return "Service not found: " + getBundleId( service );

            return output;
        }
        catch( IOException | InterruptedException e )
        {
            return "Error getting service status: " + e.getMessage();
        }
    }

    @Override
    public boolean isRunning( String service )
    {
        try
        {
            String output = UtilSys.executeAndWait( "launchctl", "list", getBundleId( service ) )
                                    .output;

            // In launchctl list output, first column is PID (0 means not running)
            String result = output.trim();

            if( result.isEmpty() )
                return false;

            String[] parts = result.split( "\\s+" );
            return parts.length > 0 && !parts[0].equals( "0" ) && !parts[0].equals( "-" );
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public boolean delete( String service )
    {
        try
        {
            String bundleId = getBundleId( service );
            String plistFile = getPlistFilePath( service );

            // Stop and unload the service first
            UtilSys.executeAndWait( "launchctl", "stop", bundleId );
            UtilSys.executeAndWait( "launchctl", "unload", plistFile );

            // Delete the plist file
            Path plistPath = Paths.get( plistFile );

            if( Files.exists( plistPath ) )
                Files.delete( plistPath );

            return true;
        }
        catch( IOException | InterruptedException e )
        {
            System.err.println( "Failed to delete service: " + e.getMessage() );
            return false;
        }
    }

    @Override
    protected String getLogContent( String service ) throws IOException
    {
        String logPath = buildLogPath( service );
        File   logFile = new File( logPath );

        if( logFile.exists() && logFile.canRead() )
        {
            // Read last 50 lines using tail-like functionality
            List<String> lines = Files.readAllLines( logFile.toPath() );
            int startIdx = Math.max( 0, lines.size() - 50 );

            StringBuilder content = new StringBuilder();
            for( int i = startIdx; i < lines.size(); i++ )
            {
                content.append( lines.get( i ) ).append( "\n" );
            }
            return content.toString();
        }
        else
        {
            return null; // Will trigger "no log entries" message
        }
    }

    @Override
    protected void printNoLogMessage( String service )
    {
        String logPath = buildLogPath( service );
        System.out.println( "No log file found for " + service + " service." );
        System.out.println( "Expected location: " + logPath );
    }

    //------------------------------------------------------------------------//
    // Private helper methods

    private String getBundleId( String jarName )
    {
        return BUNDLE_ID_PREFIX + jarName;
    }

    private String getPlistFilePath( String jarName )
    {
        return LAUNCH_AGENTS_DIR + "/" + getBundleId( jarName ) + ".plist";
    }

    private String buildLogPath( String jarName )    // macOS needs this file
    {
        return UtilSys.getWorkingDir().getAbsolutePath() + "/log/" + jarName.replace( ".jar", "" ) + ".service.txt";
    }

    private String buildPlistContent( String jarName, List<String> lstOptions, String... args )
    {
        final String scriptDir = UtilSys.getWorkingDir().getAbsolutePath();

        StringBuilder content = new StringBuilder()
                            .append( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" )
                            .append( "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" )
                            .append( "<plist version=\"1.0\">\n" )
                            .append( "<dict>\n" )
                            .append( "    <key>Label</key>\n" )
                            .append( "    <string>" ).append( getBundleId( jarName ) ).append( "</string>\n" )
                            .append( "    <key>ProgramArguments</key>\n" )
                            .append( "    <array>\n" );

        // Build Java command directly instead of calling menu.sh
        String javaCmd = UtilJVM.javaCmdToString(UtilJVM.buildJavaCmd( jarName, lstOptions, args ) );

        // Split the command into array elements for plist
        String[] commandParts = javaCmd.split( " " );

        for( String part : commandParts )
        {
            if( ! part.trim().isEmpty() )
                content.append( "        <string>" ).append( part ).append( "</string>\n" );
        }

        content.append( "    </array>\n" )
               .append( "    <key>WorkingDirectory</key>\n" )
               .append( "    <string>" ).append( scriptDir ).append( "</string>\n" )
               .append( "    <key>RunAtLoad</key>\n" )
               .append( "    <false/>\n" )
               .append( "    <key>KeepAlive</key>\n" )
               .append( "    <false/>\n" )
               .append( "    <key>StandardOutPath</key>\n" )
               .append( "    <string>" ).append( buildLogPath( jarName ) ).append( "</string>\n" )
               .append( "    <key>StandardErrorPath</key>\n" )
               .append( "    <string>" ).append( buildLogPath( jarName ) ).append( "</string>\n" )
               .append( "</dict>\n" )
               .append( "</plist>\n" );

        return content.toString();
    }
}