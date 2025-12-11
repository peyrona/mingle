package com.peyrona.mingle.menu.linux;

import com.peyrona.mingle.menu.core.AbstractServiceManager;
import com.peyrona.mingle.menu.core.Orchestrator.ProcessResult;
import com.peyrona.mingle.menu.util.UtilJVM;
import com.peyrona.mingle.menu.util.UtilSys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Linux-specific service manager using systemd.
 * Note: Requires Root privileges to write to /etc/systemd/system/
 */
public class LinuxServiceManager extends AbstractServiceManager
{
    private static final String SERVICE_PREFIX = "mingle_";
    private static final String SERVICE_SUFFIX = ".service";
    private static final String SYSTEMD_DIR    = "/etc/systemd/system/";

    //------------------------------------------------------------------------//

    @Override
    public boolean isAvailable()
    {
        try    // Ensure we have systemctl
        {
            return UtilSys.executeAndWait( "systemctl", "--version" ).succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public boolean exists( String service )
    {
        return Files.exists( Paths.get( getServiceFilePath( service ) ) );
    }

    @Override
    public boolean create( String service, List<String> lstOptions, String... args )
    {
        Path servicePath = Paths.get( getServiceFilePath( service ) );

        try
        {
            // Build service file content
            String execStart      = UtilJVM.javaCmdToString( UtilJVM.buildJavaCmd( service, lstOptions, args ) );
            String serviceContent = buildServiceFileContent( service, execStart );

            // Write service file (May throw AccessDeniedException if not root)
            Files.write( servicePath, serviceContent.getBytes( StandardCharsets.UTF_8 ) );

            // Reload systemd daemon and enable service
            UtilSys.executeAndWait( "systemctl", "daemon-reload" );
            UtilSys.executeAndWait( "systemctl", "enable", getServiceName( service ) );

            return true;
        }
        catch( IOException | InterruptedException e )
        {
            System.err.println( "Failed to create service. Ensure you are running as root/sudo: " + e.getMessage() );
            return false;
        }
    }

    @Override
    public boolean start( String service )
    {
        try
        {
            return UtilSys.executeAndWait( "systemctl", "start", getServiceName( service ) ).succeeded();
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
            return UtilSys.executeAndWait( "systemctl", "stop", getServiceName( service ) ).succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public boolean restart( String service )
    {
        try
        {
            return UtilSys.executeAndWait( "systemctl", "restart", getServiceName( service ) ).succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public String getStatus( String service )
    {
        try
        {
            return UtilSys.executeAndWait( "systemctl", "status", getServiceName( service ), "--no-pager" )
                          .output;
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
            return UtilSys.executeAndWait( "systemctl", "is-active", "--quiet", getServiceName( service ) ).succeeded();
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
            String serviceName = getServiceName( service );

            // Stop and disable the service first
            UtilSys.executeAndWait( "systemctl", "stop", serviceName );
            UtilSys.executeAndWait( "systemctl", "disable", serviceName );

            // Delete the service file
            Path servicePath = Paths.get( getServiceFilePath( service ) );
            if( Files.exists( servicePath ) )
            {
                Files.delete( servicePath );
            }

            // Reload systemd daemon
            UtilSys.executeAndWait( "systemctl", "daemon-reload" );

            return true;
        }
        catch( IOException | InterruptedException e )
        {
            System.err.println( "Failed to delete service: " + e.getMessage() );
            return false;
        }
    }

    @Override
    public boolean showFile( String service )
    {
        try
        {
            Path servicePath = Paths.get( getServiceFilePath( service ) );

            if( ! Files.exists( servicePath ) )
            {
                System.out.println( "Service file does not exist for " + service + "." );
                return false;
            }

            String content = Files.readString( servicePath );

            System.out.println( "===============================================" );
            System.out.println( "      " + service.substring( 0, 1 ).toUpperCase() + service.substring( 1 ) + " Service File Contents" );
            System.out.println( "===============================================" );
            System.out.println( "File: " + servicePath.toString() );
            System.out.println();
            System.out.println( content );
            System.out.println( "===============================================" );

            return true;
        }
        catch( IOException e )
        {
            System.err.println( "Failed to read service file: " + e.getMessage() );
            return false;
        }
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    @Override
    protected String getLogContent( String service ) throws IOException, InterruptedException
    {
        String serviceName = getServiceName( service );

        // Use journalctl to show service logs
        ProcessResult result = UtilSys.executeAndWait( "journalctl", "-u", serviceName, "--no-pager", "-n", "50" );

        if( result.succeeded() )
        {
            String output = result.output;

            if( UtilSys.isNotEmpty( output ) )  return output;
            else                                return null;   // Will trigger "no log entries" message
        }
        else
        {
            return "Failed to retrieve logs for " + service + " service.\nError: " + result.output;
        }
    }

    //------------------------------------------------------------------------//
    // Private helper methods

    private String getServiceName( String componentName )
    {
        return SERVICE_PREFIX + componentName + SERVICE_SUFFIX;
    }

    private String getServiceFilePath( String componentName )
    {
        return SYSTEMD_DIR + getServiceName( componentName );
    }

    private String buildServiceFileContent( String componentName, String execStart )
    {
        final String scriptDir = UtilSys.getWorkingDir().getAbsolutePath();

        return "[Unit]\n"
            + "Description=Mingle " + componentName.substring( 0, 1 ).toUpperCase() + componentName.substring( 1 ) + "\n"
            + "After=network.target\n"
            + "StartLimitBurst=5\n"
            + "StartLimitIntervalSec=300\n"
            + "\n"
            + "[Service]\n"
            + "Type=simple\n"
            + "WorkingDirectory=" + scriptDir + "\n"
            + "ExecStart=" + execStart + "\n"
            + "Restart=on-failure\n"
            + "RestartSec=15\n"         // Wait 15 seconds before restart
            + "TimeoutStartSec=300\n"   // 5-minute timeout for start
            + "TimeoutStopSec=30\n"     // 30-second timeout for stop
            + "StandardOutput=journal\n"
            + "StandardError=journal\n"
            + "\n"
            + "[Install]\n"
            + "WantedBy=multi-user.target\n";
    }
}