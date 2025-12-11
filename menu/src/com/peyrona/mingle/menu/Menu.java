package com.peyrona.mingle.menu;

import com.peyrona.mingle.menu.core.IProcessManager.ProcessInfo;
import com.peyrona.mingle.menu.core.Orchestrator;
import com.peyrona.mingle.menu.util.UtilSys;
import com.peyrona.mingle.menu.util.UtilUI;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

/**
 * Text-based menu for Mingle components in headless environments.
 * Provides the same functionality as the GUI version but using console interface.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class Menu
{
    private final Orchestrator orchestrator = new Orchestrator();
    private       boolean      running     = true;

    //------------------------------------------------------------------------//

    void run( String... args )
    {
        String option = "";

        if( args.length > 0 )
        {
            option = args[0].trim().toLowerCase();   // 1st option -> what to do
            args   = delItem0( args );               // The shortcut itself (for the tool to be launched) has to be removed from CLI passed args: it is consumed by the menu
        }

        while( running )
        {
            if( option.isBlank() )
            {
                showMainMenu();
                option = UtilUI.readInput( " Select option and press [Enter]: " );
            }

            System.out.println( "" );

            switch( option )
            {
                case "g":
                    launchGlue( args );
                    break;
                case "u":
                    launchGum( args );
                    break;
                case "s":
                    launchStick( "default", args );
                    break;
                case "t":
                    launchStick( "lowram", args );
                    break;
                case "i":
                    launchStick( "debug", args );
                    break;
                case "c":
                    launchStick( "profile", args );
                    break;
                case "k":
                    launchStick( "resident", args );
                    break;
                case "a":
                    launchTape( args );
                    break;
                case "l":
                    if( isInteractive() )
                        manageProcesses();
                    break;
                case "o":
                    showSystemInfo();
                    break;
                case "e":
                    if( isInteractive() )
                        manageServices();
                    break;
                case "h":
                    showHelp();
                    break;
                case "x":
                    if( isInteractive() )
                    {
                        askToKill();
                        running = false;
                        System.out.println( "Mingle console menu finished." );
                    }
                    break;

                default:
                    System.out.println( "Invalid command line option: " + option );
                    UtilUI.pause();
                    break;
            }

            if( ! Main.isInteractive() )    // Only executes one action
                break;

            option = "";   // Needed to reset
        }
    }

    //------------------------------------------------------------------------//
    // LAUNCHER FUNCTIONS
    //------------------------------------------------------------------------//

    private void launchGlue( String... args )
    {
        if( GraphicsEnvironment.isHeadless() )
        {
            System.out.println( "Graphics hardware not found: can not launch 'Glue' (Mingle's IDE)." );
            UtilUI.pause();
            return;
        }

        launchTools( "glue.jar", null, args );
    }

    private void launchGum( String... args )
    {
        launchTools( "gum.jar", null, args );
    }

    private void launchTape( String... args )
    {
        if( isFileNeeded( args ) )    // The file could already be in 'args'
        {
            if( Main.isInteractive() )
            {
                String fileName = UtilUI.readFileName( "Une file(s) to compile ('*' and '?' allowed) or [Enter] to cancel:" );

                if( fileName.isEmpty() )
                    return;

                args = append( args, fileName );
            }
            else
            {
                throw new IllegalArgumentException( "Requested to run 'Tape' but no file(s) provided." );
            }
        }

        launchTools( "tape.jar", null, args );
    }

    private void launchStick( String mode, String... args )
    {
        if( Main.isInteractive() && isFileNeeded( args ) )
        {
            System.out.println( "'.model' can be provided or not. Can be taken also from 'config.json'. See help for more info." );
            String fileName = UtilUI.readFileName( "Provide '.model' file, or just [Enter] for no model: " );

            if( ! fileName.isEmpty() )
                args = append( args, fileName );
        }

        List<String> lstOpts = new ArrayList<>();

        switch( mode )
        {
            case "lowram":
                lstOpts.add( "-XX:+UseG1GC" );
                lstOpts.add( "-XX:+UseStringDeduplication" );
                lstOpts.add( "-XX:+UseCompressedOops"      );
                break;

            case "debug":
                lstOpts.add( "-XX:+UseG1GC" );
                lstOpts.add( "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=0.0.0.0:8800" );
                break;

            case "profile":
                lstOpts.add( "-XX:+UseG1GC" );
                lstOpts.add( "-Dcom.sun.management.jmxremote"                    );
                lstOpts.add( "-Dcom.sun.management.jmxremote.port=1099"          );
                lstOpts.add( "-Dcom.sun.management.jmxremote.ssl=false"          );
                lstOpts.add( "-Dcom.sun.management.jmxremote.authenticate=false" );
                lstOpts.add( "-Dcom.sun.management.jmxremote.local.only=false"   );
                lstOpts.add( "-XX:+HeapDumpOnOutOfMemoryError"                   );
                break;

            case "resident":    // Configure for long-running background service
                lstOpts.add( "-XX:+UseG1GC" );
                lstOpts.add( "-Xms16m" );                           // Initial heap
                lstOpts.add( "-Xmx512m" );                          // Max heap
                lstOpts.add( "-XX:+UseG1GC" );                      // Modern GC
                lstOpts.add( "-XX:MaxGCPauseMillis=200" );          // Responsive GC
                lstOpts.add( "-XX:+UseStringDeduplication" );       // Memory efficiency
                lstOpts.add( "-Djava.awt.headless=true" );          // No GUI components
                break;

            default:     // default mode
                break;
        }

        launchTools( "stick.jar", lstOpts, args );
        UtilUI.pause();
    }

    /**
     * Launches all Mingle tools except Stick.
     *
     * @param mode
     * @param tool
     * @param args
     */
    private Orchestrator.LaunchResult launchTools( String tool, List<String> lstOpts, String... args )
    {
        if( lstOpts == null )
            lstOpts = new ArrayList<>();

        Orchestrator.LaunchResult lr = orchestrator.execJar( tool, lstOpts, args );
        System.out.println( lr.getMessage() );

        return lr;
    }

    //------------------------------------------------------------------------//
    // SERVICE MANAGEMENT
    //------------------------------------------------------------------------//

    private void manageServices()
    {
        boolean gumSelected = false;
        boolean stickSelected = true;  // Stick is selected by default

        while( true )
        {
            if( ! showServiceMenu( gumSelected, stickSelected ) )
                break;

            String choice = UtilUI.readInput( "Select an option: " );

            if( choice.isBlank() )
                break;

            if( choice.equals( "0" ) )
            {
                // Toggle selection
                gumSelected   = ! gumSelected;
                stickSelected = ! stickSelected;
                continue;
            }

            String selectedService = stickSelected ? "stick" : "gum";
            String[] command;

            switch( choice )
            {
                case "1": command = new String[] { "status" , selectedService }; break;
                case "2": command = new String[] { "start"  , selectedService }; break;
                case "3": command = new String[] { "stop"   , selectedService }; break;
                case "4": command = new String[] { "restart", selectedService }; break;
                case "5": command = new String[] { "showlog", selectedService }; break;
                case "6": command = new String[] { "showfile", selectedService }; break;
                case "9": command = new String[] { "delete" , selectedService }; break;
                default:
                    System.out.println( "Invalid option." );
                    UtilUI.pause();
                    continue;
            }

            Orchestrator.ServiceOperationResult result = orchestrator.executeServiceCommand( command[0], command[1] );

            if( result.isStatusOperation() )
            {
                System.out.println();
                System.out.println( command[1].substring( 0, 1 ).toUpperCase() + command[1].substring( 1 ) + " Service Status:" );
                System.out.println( "--------------------------------------------------" );
                System.out.println( result.getMessage() );
                System.out.println();
                UtilUI.pause();
            }
            else
            {
                if( result.isSuccess() )  System.out.println( "SUCCESS: " + result.getMessage() );
                else                      System.out.println( "ERROR: "   + result.getMessage() );

                UtilUI.pause();
            }
        }
    }

    //------------------------------------------------------------------------//
    // PROCESS MANAGEMENT
    //------------------------------------------------------------------------//

    private void manageProcesses()
    {
        while( true )
        {
            String choice;
            List<ProcessInfo> lstProcs = listProcesses( null );

            if( lstProcs.isEmpty() )
            {
                UtilUI.pause();
                break;
            }
            else
            {
                choice = UtilUI.readInput( "Provide an ID (A,B,C,...) to kill it, 0 to refresh or [Enter] to return: " );

                if( choice.isEmpty() )
                    break;

                if( ! "0".equals( choice ) )    // If "0" -> jumps to while( true )
                {
                    int ndx = choice.toUpperCase().charAt( 0 ) - 'A';

                    if( ndx < 0 || ndx > lstProcs.size() - 1 )
                        System.out.println( "Invalid process ID." );
                    else
                        killProcess( lstProcs.get( ndx ).pid );
                }
            }
        }
    }

    private List<ProcessInfo> listProcesses( List<ProcessInfo> lstProcs )
    {
        if( lstProcs == null || lstProcs.isEmpty() )
            lstProcs = orchestrator.listProcesses();

        if( lstProcs.isEmpty() )
        {
            System.out.println( "No Mingle tools currently running (besides this Menu)." );
        }
        else
        {
            System.out.println( "--------------------------------------------------------------------------------" );
            System.out.println( "ID Tool    PID      Command" );
            System.out.println( "--------------------------------------------------------------------------------" );

            for( int n = 0; n < lstProcs.size(); n++ )
            {
                ProcessInfo proc = lstProcs.get( n );
                String      cmd = (proc.command.length() <= 60) ? proc.command : (proc.command.substring( 0, 57 ) + "...");

                System.out.println( (char)('A' + n) +". "+
                                    UtilUI.rightPad( proc.jar   , ' ', 8 ) +
                                    UtilUI.rightPad( proc.pid+"", ' ', 9 ) + cmd );
            }

            System.out.println( "--------------------------------------------------------------------------------" );
        }

        return lstProcs;
    }

    private void killProcess( long nPID )
    {
        String choice = UtilUI.readInput( "Kill [F]orcefully or [G]racefully?  ([Enter] to go back): " ).toLowerCase();

        if( choice.isBlank() )
        {
            System.out.println( "Kill aborted by user." );
        }
        else
        {
            boolean forceful = "f".equals( choice );

            try
            {
                Orchestrator.KillResult result = orchestrator.killProcess( nPID, forceful );

                System.out.println( result.getMessage() );
            }
            catch( NumberFormatException e )
            {
                System.err.println( "Invalid PID: " + nPID );
            }
        }

        UtilUI.pause();
    }

    //------------------------------------------------------------------------//
    // INFORMATION FUNCTIONS
    //------------------------------------------------------------------------//

    private void showSystemInfo()
    {
        UtilUI.clearScreen();
        System.out.println( "-----------------------------------------------" );
        System.out.println( "            ::: System Info :::                " );
        System.out.println( "-----------------------------------------------" );
        System.out.println( orchestrator.getSystemInfo() );
        System.out.println();
        UtilUI.pause();
    }

    private void showHelp()
    {
        UtilUI.clearScreen();

        System.out.println( "-----------------------------------------------\n" +
                            "                 ::: Help :::                  \n" +
                            "-----------------------------------------------\n" +
                            "       https://github.com/peyrona/mingle       \n" +
                            "-----------------------------------------------\n" +
                            "\n" +
                            "PURPOSE\n" +
                            "-----------------\n" +
                            "   * Launch Mingle tools (autonomous mode).\n"+
                            "   * Manage Stick and Gum tools as System Services (interactive mode).\n"+
                            "   * List and optionally kill Mingle running tools (interactive mode).\n"+
                            "\n" +
                            "MINGLE COMPONENTS:\n" +
                            "-----------------\n" +
                            "G - Glue  : Mission Control IDE for Mingle development\n" +
                            "U - Gum   : Dashboard server and file management system\n" +
                            "S - Stick : Execution Environment (default mode)\n" +
                            "T -       : Execution Environment (low memory mode)\n" +
                            "I -       : Execution Environment (debug mode on port 8800)\n" +
                            "C -       : Execution Environment (profiling mode for VisualVM)\n" +
                            "K -       : Execution Environment (resident/background mode)\n" +
                            "A - Tape  : Une language transpiler\n" +
                            "\n" +
                            "MANAGEMENT OPTIONS:\n" +
                            "------------------\n" +
                            "L - List/Kill : View and terminate running Mingle processes\n" +
                            "E - Services  : Manage Mingle tools as system services\n" +
                            "O - Info      : Display system and Java information\n" +
                            "\n" +
                            "OTHER OPTIONS:\n" +
                            "--------------\n" +
                            "H - Help      : Show this help screen\n" +
                            "X - Exit      : Exit the menu (with process cleanup option)\n" +
                            "\n" +
                            "PLATFORM SUPPORT:\n" +
                            "----------------\n" +
                            "Fully tested on Linux. Basic support for macOS and Windows.\n" +
                            "Service management requires systemd (Linux), launchd (macOS), or Windows Services.\n" +
                            "\n" +
                            "USAGE EXAMPLES:\n" +
                            "---------------\n" +
                            "menu g              # Launch Glue IDE\n" +
                            "menu u              # Start Gum dashboard server\n" +
                            "menu s file.model   # Run Stick in default mode\n" +
                            "menu i              # Run Stick with remote debugging enabled\n" +
                            "menu a file.une     # Compile Une file with Tape" );

        UtilUI.pause();
    }

    //------------------------------------------------------------------------//
    // UTILITY FUNCTIONS
    //------------------------------------------------------------------------//

    private boolean isInteractive()
    {
        if( Main.isInteractive() )
            return true;

        System.out.println( "This option is only available in interactive mode." );
        UtilUI.pause();

        return false;
    }

    private boolean isFileNeeded( String[] args )
    {
        for( String arg : args )
        {
            if( (! arg.isEmpty()) && arg.charAt( 0 ) != '-' )
                return false;
        }

        return true;
    }

    private String[] append( String[] originalArray, String newItem )
    {
        String[] newArray = new String[originalArray.length + 1];

        System.arraycopy( originalArray, 0, newArray, 0, originalArray.length );

        newArray[originalArray.length] = newItem;

        return newArray;
    }

    private String[] delItem0( String[] array )
    {
        if( array.length == 0 )
            return array;

        String[] aNew = new String[array.length - 1];
        System.arraycopy( array, 1, aNew, 0, aNew.length );

        return aNew;
    }

    private void askToKill()
    {
        List<ProcessInfo> lstProcs= orchestrator.listProcesses();

        if( ! lstProcs.isEmpty() )
        {
            System.out.println( "These are the currently running Mingle components." );

            listProcesses( lstProcs );

            boolean kill = UtilUI.confirm( "Do you want to terminate all now?" );

            if( kill )
            {
                for( ProcessInfo proc : lstProcs )
                    orchestrator.killProcess( proc.pid, true );

                System.out.println( "All processes terminated." );
            }
            else
            {
                System.out.println( "Processes will continue running." );
            }
        }
    }

    //------------------------------------------------------------------------//
    // MENU DISPLAY
    //------------------------------------------------------------------------//

    private void showMainMenu()
    {
        UtilUI.clearScreen();
        System.out.println( "===============================================" );
        System.out.println( "            ::: Mingle Menu :::" );
        System.out.println( "===============================================" );

        if( orchestrator.isProcessManagerAvailable() )
        {
            System.out.println( " G - Glue.......Mission Control (IDE)" );
            System.out.println( " U - Gum........Dashboards and file-server" );
            System.out.println( " S - Stick......ExEn (default)" );
            System.out.println( " T - Stick..........Low memory mode" );
            System.out.println( " I - Stick..........Debug mode (JPDA port 8800)" );
            System.out.println( " C - Stick..........Profiling mode (VisualVM)" );
            System.out.println( " K - Stick..........Resident mode (nohup)" );
            System.out.println( " A - Tape ......Transpiler" );
            System.out.println( " L - List/Kill..Manage JVM processes" );
        }
        else
        {
            System.out.println( " Service to launch tools not avaibale in "+ UtilSys.sOS );
            System.out.println( " Some Menu options are not available," );
            System.out.println( "-----------------------------------------------" );
        }

        System.out.println( " O - Info.......System information" );
        System.out.println( " E - Services...Service Manager" );
        System.out.println( "-----------------------------------------------" );
        System.out.println( " H - Help" );
        System.out.println( " X - Exit" );
        System.out.println( "===============================================" );
    }

    private boolean showServiceMenu( boolean gumSelected, boolean stickSelected )
    {
        boolean bSuccess = true;

        UtilUI.clearScreen();
        System.out.println( "---------------------------------" );
        System.out.println( "     ::: Service Manager :::" );
        System.out.println( "---------------------------------" );

        if( orchestrator.isServiceManagerAvailable() )
        {
            if( UtilSys.isAdmin() || UtilSys.isDevEnv() )
            {
                String gumCheckbox   = gumSelected   ? "[X]" : "[ ]";
                String stickCheckbox = stickSelected ? "[X]" : "[ ]";

                System.out.println( " " + gumCheckbox + " Gum      " + stickCheckbox + " Stick" );
                System.out.println();
                System.out.println( " 1 - Service Status" );
                System.out.println( " 2 - Service Start" );
                System.out.println( " 3 - Service Stop" );
                System.out.println( " 4 - Service Restart" );
                System.out.println( " 5 - Service show log" );
                System.out.println( " 6 - Service show file contents" );
                System.out.println( " 9 - Delete service file" );
                System.out.println( "---------------------------------" );
                System.out.println( " 0   + [Enter] to change tool"     );
                System.out.println( " 1-9 + [Enter] to execute a task"  );
                System.out.println( " Only  [Enter] to go back"         );
                System.out.println( "---------------------------------" );
            }
            else
            {
                System.out.println( "Service management is not available because this script was not launched by an Admin." );
                UtilUI.pause();
                bSuccess = false;
            }
        }
        else
        {
            System.out.println( "Service management is not supported on "+ UtilSys.sOS );
            UtilUI.pause();
            bSuccess = false;
        }

        return bSuccess;
    }
}