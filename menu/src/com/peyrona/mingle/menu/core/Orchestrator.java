package com.peyrona.mingle.menu.core;

import com.peyrona.mingle.menu.core.IProcessManager.ProcessInfo;
import com.peyrona.mingle.menu.util.UtilSys;
import com.peyrona.mingle.menu.util.UtilUI;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Core business logic for Mingle Menu application.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Orchestrator
{
    private final IProcessManager processManager;
    private final IServiceManager serviceManager;

    //------------------------------------------------------------------------//

    public Orchestrator()
    {
        this.processManager = ProcessManagerFactory.createProcessManager();
        this.serviceManager = ServiceManagerFactory.createServiceManager();
    }

    public boolean isProcessManagerAvailable()
    {
        return processManager.isAvailable();
    }

    public boolean isServiceManagerAvailable()
    {
        return serviceManager.isAvailable();
    }

    //------------------------------------------------------------------------//
    // LAUNCHER FUNCTIONS
    //------------------------------------------------------------------------//

    public Orchestrator.LaunchResult execJar( String jarName, List<String> lstOptions, String... args )
    {
        if( ! processManager.isAvailable() )
            return new LaunchResult( false, "Process Manager is not available for "+ UtilSys.sOS, null );

        try
        {
            Process      proc = processManager.execJar( jarName, lstOptions, args );
            LaunchResult resu = new LaunchResult( isRunning( proc ), jarName + (proc.isAlive() ? " is running." : " failed to start."), proc );

            return resu;
        }
        catch( IOException ioe )
        {
            return new LaunchResult( false, "Error executing "+ jarName + ": "+ ioe.getMessage(), null );
        }
    }

    public List<ProcessInfo> listProcesses()
    {
        try
        {
            return processManager.list();
        }
        catch( IOException | InterruptedException exc )
        {
            System.err.println( "Error listing Mingle running processes: "+ exc.getMessage() );
        }

        return new ArrayList<>();
    }

    //------------------------------------------------------------------------//
    // SERVICE MANAGEMENT
    //------------------------------------------------------------------------//

    public ServiceOperationResult executeServiceCommand( String action, String component )
    {
        if( ! serviceManager.exists( component ) )
        {
            if( UtilUI.confirm( "Service does not exists in this computer.\nDo you want to create it?" ) )
            {
                System.out.println( "Creating..." );

                if( serviceManager.create( component, null, (String[]) null ) )
                    System.out.println( "Service successfully created." );
                else
                    return new ServiceOperationResult( false, "Error creating the Service.", null );
            }
        }

        System.out.println( "Wait..." );

        boolean success;
        String  message;

        try
        {
            switch( action )
            {
                case "status":
                    message = serviceManager.getStatus( component );
                    return new ServiceOperationResult( true, message, "status" );
                case "start":
                    success = serviceManager.start( component );
                    message = component + " service started successfully.";
                    break;
                case "stop":
                    success = serviceManager.stop( component );
                    message = component + " service stopped successfully.";
                    break;
                case "restart":
                    success = serviceManager.restart( component );
                    message = component + " service restarted successfully.";
                    break;
                case "showlog":
                    UtilUI.clearScreen();
                    success = serviceManager.showLog( component );
                    message = component + " last log entries shown.";
                    break;
                case "delete":
                    boolean confirmed = UtilUI.confirm( "Confirm to delete" );
                    success = confirmed ? serviceManager.delete( component ) : false;
                    message = component + " service file " + (success ? "deleted successfully." : "not deleted.");
                    break;
                default:
                    return new ServiceOperationResult( false, "Unknown service action: " + action, null );
            }

            return new ServiceOperationResult( success, message, action );
        }
        catch( Exception ex )
        {
            return new ServiceOperationResult( false, "Error executing service command: " + ex.getMessage(), action );
        }
    }

    //------------------------------------------------------------------------//
    // PROCESS MANAGEMENT
    //------------------------------------------------------------------------//

    public KillResult killProcess( long pid, boolean forceful )
    {
        try
        {
            for( ProcessInfo info : processManager.list() )
            {
                if( info.pid == pid )
                {
                    boolean success = processManager.kill( pid, forceful );

                    String  message = success ? ("Process " + pid + " killed " + (forceful ? "forcefully" : "gracefully"))
                                              : ("Failed to kill process " + pid + ": termination may be pending or may require elevated privileges.");

                    return new KillResult( success, message );
                }
            }

            return new KillResult( false, "Error killing process, PID: " + pid + " not found." );
        }
        catch( Exception ex )
        {
            return new KillResult( false, "Error killing process: " + ex.getMessage() );
        }
    }

    //------------------------------------------------------------------------//
    // SYSTEM INFORMATION
    //------------------------------------------------------------------------//

    public String getSystemInfo()
    {
        StringBuilder info = new StringBuilder();

        info.append( "JAVA\n-----\n" );
        info.append( "Java Home: " ).append( System.getProperty( "java.home" ) ).append( "\n" );
        info.append( "Java Version: " ).append( System.getProperty( "java.version" ) )
            .append( " by " ).append( System.getProperty( "java.vendor" ) ).append( "\n\n" );

        info.append( "SYSTEM\n------\n" );
        info.append( "OS: " ).append( UtilSys.sOS )
            .append( " (" ).append( System.getProperty( "os.version" ) ).append( ")\n" );
        info.append( "Architecture: " ).append( System.getProperty( "os.arch" ) ).append( "\n" );
        info.append( "User: " ).append( System.getProperty( "user.name" ) ).append( "\n" );
        info.append( "Home: " ).append( System.getProperty( "user.home" ) ).append( "\n\n" );

        if( serviceManager != null )
        {
            info.append( "SERVICES\n---------\n" );

            info.append( "Gum Service Status:\n" );
            info.append( serviceManager.getStatus( "gum" ) ).append( "\n\n" );

            info.append( "Stick Service Status:\n" );
            info.append( serviceManager.getStatus( "stick" ) );
        }
        else
        {
            info.append( "SERVICES\n---------\n" );
            info.append( "Service management is not supported on " ).append( UtilSys.sOS ).append( ".\n" );
        }

        return info.toString();
    }

    //------------------------------------------------------------------------//
    // UTILITY FUNCTIONS
    //------------------------------------------------------------------------//

    private boolean isRunning( Process process )
    {
        if( process == null )
            return false;

        try
        {
            for( int n = 0; n < 15; n++ )   // Wait for the process to stabilize
            {
                Thread.sleep( 100 );

                if( process.isAlive() )
                    return true;
            }
        }
        catch( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            return process.isAlive();
        }

        return process.isAlive();
    }

    //------------------------------------------------------------------------//
    // RESULT CLASSES
    //------------------------------------------------------------------------//

    public static final class ProcessResult
    {
        public final String  command;
        public final Process process;
        public final String  output;

        public ProcessResult( String[] cmd, Process proc, String out )
        {
            command = Arrays.toString( cmd );
            process = proc;
            output  = (out == null ? "" : out);
        }

        public boolean succeeded()  { return (process != null && process.exitValue() == 0); }
    }

    public static final class LaunchResult
    {
        private final boolean success;
        private final String message;
        private final Process process;

        public LaunchResult( boolean success, String message, Process process )
        {
            this.success = success;
            this.message = message;
            this.process = process;
        }

        public boolean isSuccess() { return success; }
        public String  getMessage() { return message; }
        public Process getProcess() { return process; }
    }

    public static final class ServiceOperationResult
    {
        private final boolean success;
        private final String message;
        private final String action;

        public ServiceOperationResult( boolean success, String message, String action )
        {
            this.success = success;
            this.message = message;
            this.action = action;
        }

        public boolean isSuccess() { return success; }
        public String  getMessage() { return message; }
        public String  getAction() { return action; }
        public boolean isStatusOperation() { return "status".equals( action ); }
    }

    public static final class KillResult
    {
        private final boolean success;
        private final String  message;

        public KillResult( boolean success, String message )
        {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String  getMessage() { return message; }
    }
}