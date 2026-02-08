package com.peyrona.mingle.menu.win;

import com.peyrona.mingle.menu.core.AbstractServiceManager;
import com.peyrona.mingle.menu.util.Execute.ProcessResult;
import com.peyrona.mingle.menu.util.Execute;
import com.peyrona.mingle.menu.util.UtilJVM;
import com.peyrona.mingle.menu.util.UtilSys;
import java.io.IOException;
import java.util.List;

/**
 * Windows-specific service manager using Windows Services.
 */
public class WinServiceManager extends AbstractServiceManager
{
    private static final String SERVICE_PREFIX = "Mingle";

    //------------------------------------------------------------------------//

    @Override
    public boolean isAvailable()
    {
        try    // Check if sc.exe is available (Windows service control utility)
        {
            return new Execute.Builder( "sc", "query" ).build().executeAndWait().succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public boolean exists( String service )
    {
        try
        {
            return new Execute.Builder( "sc", "query", getServiceName( service ) ).build().executeAndWait().succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public boolean create( String service, List<String> lstOptions, String... args )
    {
        String serviceName = getServiceName( service );

        try
        {
            // Build service command
            String serviceCommand = buildServiceCommand( service, lstOptions, args );

            // Create service using sc.exe
            ProcessBuilder pb = new ProcessBuilder(
                                                    "sc", "create", serviceName,
                                                    "binPath=", serviceCommand,
                                                    "start=", "auto",
                                                    "DisplayName=", "Mingle " + service.substring( 0, 1 ).toUpperCase() + service.substring( 1 )
                                                  );

            Process process = pb.start();

            if( process.waitFor() != 0 )
                return false;

            // Configure service to not restart on failure
            ProcessBuilder failureConfig  = new ProcessBuilder( "sc", "failure", serviceName, "reset=", "0", "actions=", "" );
            Process        failureProcess = failureConfig.start();

            return failureProcess.waitFor() == 0;
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
            return new Execute.Builder( "sc", "start", getServiceName( service ) ).build().executeAndWait().succeeded();
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
            return new Execute.Builder( "sc", "stop", getServiceName( service ) ).build().executeAndWait().succeeded();
        }
        catch( IOException | InterruptedException e )
        {
            return false;
        }
    }

    @Override
    public boolean restart( String service )
    {
        // Windows services don't have a direct restart command in sc.exe
        return stop( service ) && start( service );
    }

    @Override
    public String getStatus( String service )
    {
        try
        {
            return new Execute.Builder( "sc", "query", getServiceName( service ) )
                           .build().executeAndWait().output;
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
            String output  = new Execute.Builder( "sc", "query", getServiceName( service ) )
                                     .build().executeAndWait().output;

            return output.toUpperCase().contains( "RUNNING" );
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

            // Stop the service first
            new Execute.Builder( "sc", "stop", serviceName ).build().executeAndWait();

            // Delete the service
            return new Execute.Builder( "sc", "delete", serviceName ).build().executeAndWait().succeeded();
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
            String serviceName = getServiceName( service );

            // Use sc.exe to query the service configuration
            ProcessResult result = new Execute.Builder( "sc", "qc", serviceName ).build().executeAndWait();

            if( ! result.succeeded() )
            {
                System.out.println( "Service configuration not found for " + service + "." );
                return false;
            }

            System.out.println( "===============================================" );
            System.out.println( "      " + service.substring( 0, 1 ).toUpperCase() + service.substring( 1 ) + " Service Configuration" );
            System.out.println( "===============================================" );
            System.out.println( "Service Name: " + serviceName );
            System.out.println();
            System.out.println( result.output );
            System.out.println( "===============================================" );

            return true;
        }
        catch( IOException | InterruptedException e )
        {
            System.err.println( "Failed to query service configuration: " + e.getMessage() );
            return false;
        }
    }

    @Override
    protected String getLogContent( String service ) throws IOException, InterruptedException
    {
        String serviceName = getServiceName( service );

        // Use PowerShell to get recent Windows Event Log entries
        String command = "Get-WinEvent -LogName Application -Source \"" + serviceName + "\" -MaxEvents 50 | Format-Table TimeCreated, LevelDisplayName, Message -Wrap";
        ProcessResult result = new Execute.Builder( "powershell", "-Command", command ).build().executeAndWait();

        if( result.succeeded() && !result.output.trim().isEmpty() )
        {
            return result.output;
        }
        else
        {
            return null; // Will trigger "no log entries" message
        }
    }

    @Override
    protected void printNoLogMessage( String service )
    {
        System.out.println( "No Event Log entries found for " + service + " service." );
        System.out.println( "You can also check Windows Event Viewer for detailed logs." );
    }

    //------------------------------------------------------------------------//
    // Private helper methods

    private String getServiceName( String service )
    {
        return SERVICE_PREFIX + service.substring( 0, 1 ).toUpperCase() + service.substring( 1 );
    }

    private String buildServiceCommand( String service, List<String> lstOptions, String... args )
    {
        final String scriptDir = UtilSys.getWorkingDir().getAbsolutePath();

        StringBuilder command = new StringBuilder();

        // Use PowerShell to executeAndWait Java command directly
        command.append( "powershell.exe -Command \"" );
        command.append( "cd '" ).append( scriptDir ).append( "'; " );

        // Build Java command directly instead of calling menu.ps1
        command.append( UtilJVM.javaCmdToString( UtilJVM.buildJavaCmd( service, lstOptions, args ) ) );
        command.append( "\"" );

        return command.toString();
    }
}