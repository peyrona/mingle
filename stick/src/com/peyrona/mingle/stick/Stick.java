
package com.peyrona.mingle.stick;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.ParseException;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICandi;
import com.peyrona.mingle.lang.interfaces.ICmdEncDecLib;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.commands.IDriver;
import com.peyrona.mingle.lang.interfaces.commands.IRule;
import com.peyrona.mingle.lang.interfaces.commands.IScript;
import com.peyrona.mingle.lang.interfaces.exen.IEventBus;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.Config;
import com.peyrona.mingle.lang.japi.ExEnComm;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.messages.Message;
import com.peyrona.mingle.lang.messages.MsgChangeActuator;
import com.peyrona.mingle.lang.messages.MsgDeviceChanged;
import com.peyrona.mingle.lang.messages.MsgDeviceReaded;
import com.peyrona.mingle.lang.messages.MsgReadDevice;
import com.peyrona.mingle.lang.messages.MsgTrigger;
import com.peyrona.mingle.lang.xpreval.functions.StdXprFns;
import com.peyrona.mingle.lang.xpreval.functions.list;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * This class provides the execution environment where the transpiled Une code is executed.
 * <p>
 * Although it is not frequent, several ExEn instances could coexists even in the same
 * JVM: it is responsibility of the application that creates the instances to properly
 * manage them.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class Stick
             implements IRuntime
{
    private final IConfig        config;     // Can not be sat as UtilSys.setConfig(...) because each instance of Stick can have its own instance of IConfig
    private final IEventBus      eventBus;
    private final NetworkManager netwMgr;    // Receives requests (from other ExEns) and send changes to them
    private final GridManager    gridMgr;
    private final ScriptManager  srptMgr;
    private final DriverManager  drvrMgr;
    private final DeviceManager  deviMgr;
    private final RuleManager    ruleMgr;
    private       boolean        bExited  = false;
    private       boolean        bSendAll = false;     // Broadcast all msgs or only changes and errors?

    //----------------------------------------------------------------------------//
    // CONSTRUCTOR

    // Exceptions thrown inside the constructor (and methods it calls) will stop Stick: this is the intention.
    // But once Stick is running, exceptions thrown should do not stop Stick.

    /**
     * Starts Stick: the MSP Environment Executor (ExEn).
     *
     * @param sModelJSON  An string containing a valid JSON: this is the transpiled Une code to be executed. It can be empty.
     * @param config      An instance holding the ".json" configuration that will be used; can be null.
     */
    public Stick( String sModelJSON, IConfig config )
    {
        try
        {
            config = ((config == null) ? new Config().load( null ) : config);
        }
        catch( IOException ioe )
        {
            failed( ioe, null );
        }

        this.config = config;

        // These are also nedded here (besides Main.java) because this class
        // can be instatiated directly (without passing it by Main.java)

        UtilSys.setLogger( "stick", config );

        // Creates and assigns a SLF4J Provider to redirect SLF4J logs to Mingle default Logger (I also removed the SLF4J JAR from the Stick project)
        // SLF4J_Adapter.init( logger );
        // System.setProperty( "slf4j.provider", SLF4J_Provider.class.getName() );

        // After Logger is initialized, the rest of modules can be initialized too

        eventBus = new EventBus();
        srptMgr  = new ScriptManager( this );
        drvrMgr  = new DriverManager( this );
        deviMgr  = new DeviceManager( this );
        ruleMgr  = new RuleManager(   this );
        netwMgr  = new NetworkManager();
        gridMgr  = new GridManager( this.config );

        // Loads transpiled code (if any) and adds commands to their managers

        if( UtilStr.isNotEmpty( sModelJSON ) )
        {
            try
            {
                List<ICommand> lstCmds = new ArrayList<>();
                ICmdEncDecLib  builder = this.config.newCILBuilder();
                JsonObject     joModel = Json.parse( sModelJSON ).asObject();
                String         version = joModel.getString( "code-version", "unknown" );

                if( ! version.equals( "1.0" ) )
                    failed( null, "Invalid transpiled code version: "+ version );

                joModel.get( "commands" )
                       .asArray()
                       .forEach( (JsonValue jv) -> lstCmds.add( builder.build( jv.toString() ) ) );

                if( lstCmds.isEmpty() )
                {
                    UtilSys.getLogger().log( ILogger.Level.WARNING, "There are no commmands in script: this is valid, but it is strange." );
                }
                else
                {
                    for( ICommand cmd : sortByType( lstCmds, false ) )
                    {
                        if( ! add( cmd ) )
                            failed( null, "Error adding: "+ cmd );    // Fail fast
                    }
                }
            }
            catch( ParseException exc )
            {
                failed( exc, null );
            }
        }

        // By using a hook we maximize the possibilities the finalization code will be
        // invoked: even if INTERRUPT signal (Ctrl-C) is used, the JVM will invoke this hook.
        // Even when System.exit(...) is used, the JVM will invoke this hook.

        Runtime.getRuntime()
               .addShutdownHook( new Thread( () -> stop() ) );
    }

    //------------------------------------------------------------------------//

    /**
     * Starts stick with the parameters passed at class constructor.
     *
     * @return Itself.
     */
    public Stick start()
    {
        return start( null );
    }

    /**
     * Starts stick with the parameters passed at class constructor.
     * @param sModelName When not null, it is used only to be displayed in welcome screen.
     *
     * @return Itself.
     */
    public Stick start( String sModelName )
    {
        if( UtilStr.isMeaningless( sModelName ) )
            sModelName = deviMgr.isEmpty() ? "None" : "Unknown";    // "None" means that no model was provided. "Unknown" means that a model was provided but its name is unknown
        else
            sModelName = sModelName.trim();

        srptMgr.start();    // First one to trigger SCRIPTs ONSTART before anything is started
        gridMgr.start();    // GridManager instance is needed before creating the Commands because Rule (perhaps also others) use ::isGridNode
        drvrMgr.start();    // Drivers has to be inited before devices because devices ask for their values to drivers
        deviMgr.start();    // After starting every device, it requests its value and generates a Device Changes message
        ruleMgr.start();

        // If this is a Grid Node, devices in other nodes (referenced in this Stick) have to be identified.
        // Such devices can be only in Rule's WHEN and IF clauses.

        if( gridMgr.isNode )
        {
            for( ICommand cmd : all( "rules" ) )
            {
                IXprEval eval4When = newXprEval().build( ((IRule) cmd).getWhen(), (r) -> {}, newGroupWiseFn() );

                for( String sName : eval4When.getVars().keySet() )
                {
                    if( deviMgr.named( sName ) == null )            // If device manager does not have a device with this name,
                        deviMgr.createRemoteDevice( sName );        // it is because that device exists in another ExEn.
                }

                IXprEval eval4If = newXprEval().build( ((IRule) cmd).getIf(), (r) -> {}, newGroupWiseFn() );

                if( eval4If != null )
                {
                    for( String sName : eval4If.getVars().keySet() )
                    {
                        if( deviMgr.named( sName ) == null )        // If device manager does not have a device with this name,
                            deviMgr.createRemoteDevice( sName );    // it is because that device exists in another ExEn.
                    }
                }
            }
        }

        // Starts network servers -------------------------------------------------------

        try
        {
            netwMgr.start( new ServerListener(), config.getNetworkServersOutline() );

            // If NetworkManager is not empty (even if transpiled code is empty) we can not exit because
            // NetworkManager can receive requests to create Scripts, Drivers, Devices and Rules.

            if( netwMgr.isEmpty() )
            {
                if( deviMgr.isEmpty() && ruleMgr.isEmpty() && srptMgr.isEmpty() )
                    failed( null, "Useless ExEn: no Devices, no Rules, no Scripts and no communications" );

                runVoidThread();     // Java neededs at least one non-daemon thread to not exit (NetworkManager provides a Thread when it is not empty)
            }
        }
        catch( MingleException me )
        {
            failed( null, "Communications ports are already in use:\n"+ netwMgr.toString() );
        }
        catch( Exception exc )
        {
            failed( exc, "It looks like there is another ExEn running using same config" );
        }

        //------------------------------------------------------------------------//
        boolean bUseDisk  = config.get( "exen", "use_disk"     , true  );
        boolean bFakeDrvs = config.get( "exen", "faked_drivers", false );
        boolean bDocker   = UtilSys.isDocker();

        String sInfo = new StringBuilder()
                .append( '\n' )
                .append( "Stick: An 'Execution Environment' (ExEn) for the 'Mingle Standard Platform' (MSP).\n" )
                .append( "       Version "    ).append( UtilSys.getVersion( getClass() ) ).append( '\n' ).append( '\n' )
                .append( "Model   = "         ).append( sModelName ).append( '\n' )
                .append( "Config  = "         ).append( config.getURI() ).append( '\n' )
                .append( "Home    = "         ).append( UtilSys.fHomeDir ).append( '\n' )
                .append( "Log     = "         ).append( new File( UtilSys.getLogDir(), UtilSys.getLogger().getName() ) ).append( ", Level=" ).append( UtilSys.getLogger().getLevel() ).append( '\n' )
                .append( "XprEval = "         ).append( config.newXprEval().about() ).append( '\n' )
                .append( "Cmd Lib = "         ).append( config.newCILBuilder().about() ).append( '\n' )
                .append( "UseDisk = "         ).append( bUseDisk ).append( " -> Local storage is used to read" ).append( bUseDisk ? " and to write" : "-only").append( '\n' )
                .append( "Faked   = "         ).append( bFakeDrvs ).append( bFakeDrvs ? " -> Using faked drivers" : " -> Using real drivers" ).append( '\n' )
                .append( "User    = "         ).append( System.getProperty( "user.name" ) ).append( ", home: ").append( System.getProperty( "user.home" ) ).append( '\n' )
                .append( "Java    = version " ).append( System.getProperty( "java.version" ) ).append( " (" ).append( System.getProperty( "java.vendor" ) ).append( ")\n" )
                .append( "JVM     = "         ).append( System.getProperty( "java.vm.name" ) ).append( " v." ).append( System.getProperty( "java.vm.version" ) ).append( '\n' )
                .append( "JRE     = "         ).append( System.getProperty( "java.runtime.name" ) ).append( ". Version: " ).append( System.getProperty( "java.runtime.version" ) ).append( '\n' )
                .append( "Locale  = "         ).append( Locale.getDefault() ).append( '\n' )
                .append( "OS      = "         ).append( System.getProperty( "os.name" ) ).append( ". Version: " ).append( System.getProperty( "os.version" ) ).append( ". Architecture: " ).append( System.getProperty( "os.arch" ) ).append( '\n' )
                .append( "IP(s)   = "         ).append( (new StdXprFns()).invoke( "localIPs", null ).toString() ).append( '\n' )
                .append( "Docker  = "         ).append( bDocker ).append( " -> Apparently Stick is " ).append( bDocker ? "" : "not" ).append( " running inside a docker" ).append ( '\n' ).append( '\n' )
                .append( netwMgr.toString()   ).append( '\n' )
                .append( gridMgr.toString()   ).append( '\n' )
                .append( '[' ).append( LocalDateTime.now().format( DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" ) ) ).append( "] Stick started..." ).append('\n')
                .toString();

        UtilSys.getLogger().say( sInfo );

        //------------------------------------------------------------------------//
        // Prepare and start the Event Bus
        // When ExEn Grid, those ExEns connecting later will loose events launched by those ExEns that started earlier: this is the expected behaviour.

        eventBus.add( new BusListener() )
                .start();

        //------------------------------------------------------------------------//
        // Collect devices downtimed (those that apparently do not work any more)

        long   nDownTime   = config.get( "exen", "downtimed_interval"     , 0 ) * 1000;
        String sDownDevice = config.get( "exen", "downtimed_report_device", "" );

        if( (nDownTime > 0) && UtilStr.isNotEmpty( sDownDevice ) )
        {
            nDownTime = (nDownTime < 1000 ? 1000 : nDownTime);

            if( deviMgr.named( sDownDevice ) != null )
                UtilSys.executeAtFixed( getClass().getName(), nDownTime, nDownTime, () -> checkDowntimedDevices( sDownDevice ) );
            else
                log( ILogger.Level.WARNING, '"'+ sDownDevice +"\" device does not exist; downtimed task not initiated" );
        }

        return this;
    }

    @Override
    public String toString()
    {
        return "Stick version "+ UtilSys.getVersion( getClass() );
    }

    // equals and hascode are not needed: the default Java implementation for these methods is OK

    //------------------------------------------------------------------------//
    // BY IMPLEMENTING IRuntime

    @Override
    public ICommand[] all( String... asClass )
    {
        List<ICommand> list      = new ArrayList<>();
        StringBuilder  sbClasses = new StringBuilder( 1024 * 8 );    // Proven: 8K is a good value

        if( UtilColls.isEmpty( asClass ) )
        {
            asClass = null;
        }
        else
        {
            for( String sClas : asClass )
            {
                sbClasses.append( sClas.trim().toLowerCase() );

                if( UtilStr.isLastChar( sbClasses, 's' ) )
                    UtilStr.removeLast( sbClasses, 1 );

                sbClasses.append( ',' );
            }
        }

        if( (asClass == null) || sbClasses.indexOf( "device" ) > -1 )  deviMgr.forEach( item -> list.add( item ) );    // My method
        if( (asClass == null) || sbClasses.indexOf( "driver" ) > -1 )  drvrMgr.forEach( item -> list.add( item ) );    // forEach( ... )
        if( (asClass == null) || sbClasses.indexOf( "script" ) > -1 )  srptMgr.forEach( item -> list.add( item ) );    // is already
        if( (asClass == null) || sbClasses.indexOf( "rule"   ) > -1 )  ruleMgr.forEach( item -> list.add( item ) );    // synchronized

        return list.toArray( ICommand[]::new );
    }

    @Override
    public ICommand get( String sName )
    {
        if( UtilStr.isEmpty( sName ) )
            return null;

        sName = sName.trim().toLowerCase();

        ICommand cmd;

        cmd = deviMgr.named( sName );  if( cmd != null ) return cmd;
        cmd = ruleMgr.named( sName );  if( cmd != null ) return cmd;
        cmd = srptMgr.named( sName );  if( cmd != null ) return cmd;
        cmd = drvrMgr.named( sName );  return cmd;                     // Either the command or null
    }

    @Override
    public boolean add( ICommand command )
    {
        if( command == null )
        {
            log( ILogger.Level.SEVERE, "Attempting to add 'null'" );
            return false;
        }

        if( command instanceof IDriver )  return drvrMgr.add( (IDriver) command );
        if( command instanceof IScript )  return srptMgr.add( (IScript) command );
        if( command instanceof IRule   )  return ruleMgr.add( (IRule  ) command );

        eventBus.pause();

        IDevice device = (IDevice) command;

        boolean bOK = drvrMgr.add( device ) &&
                      deviMgr.add( device );

        eventBus.resume();

        return bOK;

        // When a new Device is added on the fly, all its dependencies must exist previously: Device's Driver and Script.

        // srptMgr.clean( drvrMgr.clean() ) is not invoked here because prior to add a Device, its Driver and Script
        // must exist, or a not OK is returned by DriverManager::add( IDevice )

        // Next time ::remove(...) will be invoked, all unneeded Drivers and Scripts will be removed.
    }

    @Override
    public boolean remove( ICommand command )
    {
        if( command == null )
        {
            log( ILogger.Level.SEVERE, "Removing 'null'" );
            return false;
        }

        eventBus.pause();

        boolean bOK;

             if( command instanceof IDriver )  bOK = drvrMgr.remove( (IDriver) command );
        else if( command instanceof IScript )  bOK = srptMgr.remove( (IScript) command );
        else if( command instanceof IRule   )  bOK = ruleMgr.remove( (IRule  ) command );
        else
        {
            IDevice device = (IDevice) command;

            bOK = deviMgr.remove( device ) &&
                  drvrMgr.remove( device );     // This method removes the driver if it is not needed anymore (has no more devices)
        }

        if( bOK )
        {
            srptMgr.clean();
            drvrMgr.clean();
        }

        eventBus.resume();

        return bOK;
    }

    @Override
    public boolean isNameOfGroup( String name )
    {
        return deviMgr.isGroup( name );
    }

    @Override
    public IDevice[] getMembersOf( String... group )
    {
        return deviMgr.getMembersOf( group ).toArray( IDevice[]::new );
    }

    @Override
    public IDevice[] getInAnyGroup( String... group )
    {
        return deviMgr.getInAnyGroup( group ).toArray( IDevice[]::new );
    }

    @Override
    public IDevice[] getInAllGroups( String... group )
    {
        return deviMgr.getInAllGroups( group ).toArray( IDevice[]::new );
    }

    @Override
    public Function<String,String[]> newGroupWiseFn()
    {
        // Better to create a new instance each time to avoid thread-saftey issues

        return (gn) ->    // Group Name
                    {
                        return Arrays.stream( getMembersOf( gn ) )
                                     .map( IDevice::name )
                                     .toArray( String[]::new );
                    };
    }

    @Override
    public IXprEval newXprEval()
    {
        return config.newXprEval();
    }

    @Override
    public ICandi.IBuilder newLanguageBuilder()
    {
        return config.newLanguageBuilder();
    }

    @Override
    public IEventBus bus()
    {
        return eventBus;
    }

    @Override
    public <T> T getFromConfig( String module, String varName, T defValue )
    {
        return config.get( module, varName, defValue );
    }

    @Override
    public boolean isGridNode()
    {
        return gridMgr.isNode;
    }

    @Override
    public Stick log( ILogger.Level level, Object msg )
    {
        ILogger logger = UtilSys.getLogger();

        if( logger == null )
        {
            System.err.println( msg );
        }
        else if( isLoggable( level ) )
        {
            String sMsg;

            if( msg instanceof Throwable )
                sMsg = UtilStr.toString( msg );
            else
                sMsg = ((msg == null) ? "Error has no description" : msg.toString());    // Will be null only by mistake, but has to be checked

            logger.log( level, sMsg );

            if( ! netwMgr.isEmpty() &&                          // Saves CPU: creating ExEnComm and the JSON.
                logger.isLoggable( ILogger.Level.WARNING ) )    // Only WARNING and SEVERE are broadcasted (if WARNING is loggable, SEVERE will be too).
            {
                netwMgr.broadcast( ExEnComm.asError( sMsg ) );
            }
        }

        return this;
    }

    @Override
    public boolean isLoggable( ILogger.Level level )
    {
        return UtilSys.getLogger().isLoggable( level );
    }

    @Override
    public IRuntime exit( int millis )
    {
        synchronized( this )
        {
            if( bExited )       // To avoid more than one call to ::exit(...)
                return this;

            bExited = true;
        }

        millis = ((millis <= 0) ? 500 : millis);    // I set a minium delay time of 500 to allow any pending task to be accomplished

        // NEXT: --> Do not call 'System.exit( 0 )' to allow to run more than one instace of Stick in same JVM

        UtilSys.execute( getClass().getName(), millis, () -> System.exit( 0 ) );   // Don't need to ::stop(), it is always invoked by a System hook

        return this;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Informs to a device about the down-timed devices.
     *
     * @param sReporterDevName The name of the device to be informed: the one that receives the list of down-timed devices.
     */
    private void checkDowntimedDevices( final String sReporterDevName )
    {
        IDevice dev2Report = deviMgr.named( sReporterDevName );    // The device used to store the list of downtimed devices

        if( dev2Report != null )    // Because devices can be deleted at runtime
        {
            list lst = new list();

            deviMgr.forEach( dev ->
                             {
                                if( dev.isDowntimed() )
                                    lst.add( dev.name() );
                             } );

            if( ! lst.isEmpty() )
                dev2Report.value( lst );
        }
    }

    private void failed( Exception exc, String msg )
    {
        if( msg == null )
            msg = "";

        msg += "\nCan not continue. Check logs.\n\n"+ UtilStr.toString( exc );

        log( ILogger.Level.SEVERE, msg );

        exit( 0 );    // ::stop() is always invoked by a System hook
    }

    private Stick stop()
    {
        eventBus.stop();
        ruleMgr.stop();
        drvrMgr.stop();
        deviMgr.stop();
        gridMgr.stop();
        netwMgr.stop();
        srptMgr.stop();    // Last one to trigger SCRIPTs ONSTOP after all is stopped
        String bye = "<<<<<<< Stick finished >>>>>>>";
        System.out.println( bye );

        if( UtilSys.getLogger() != null )
            UtilSys.getLogger().say( bye );

        return this;
    }

    /**
     * Sorts passed list in the needed order to be added (or removed): Scripts, Drivers, Devices and Rules.
     *
     * @param commands List to be sorted.
     * @param bReverse true when the resulting list is needed to remove commands instead of adding them.
     * @return Passed list after being sorted
     */
    private List<ICommand> sortByType( List<ICommand> list, boolean bReverse )
    {
        if( list.size() > 1 )
        {
            list.sort( (cmd1, cmd2) -> getPrecedence( cmd1 ).compareTo( getPrecedence( cmd2 ) ) );

            if( bReverse )
                Collections.reverse( list );
        }

        return list;
    }

    private Integer getPrecedence( ICommand cmd )
    {
             if( cmd instanceof IScript )  return 0;    // Highest precedence
        else if( cmd instanceof IDriver )  return 1;
        else if( cmd instanceof IDevice )  return 2;
                                           return 3;    // Lowest precedence (IRule)
    }

    /**
     * JVM needed at least one daemon thread to not exit: this approach (to create a void
     * thread) is as good as any other approach.
     * <p>
     * The thread lives in the JVM and there is no need to have a reference to this thread:
     * the only case this reference would be needed is to stop this thread, but this is done
     * only when Stick is going to be stopped, and it is simpler to invoke System.exit(0):
     * this stops all threads, invokes the shutdown-hooks and exists.
     */
    private void runVoidThread()
    {
        Thread voidTh = new Thread( () ->
                                    {
                                        while( true )
                                        {
                                            synchronized( Thread.currentThread() )
                                            {
                                                try { Thread.currentThread().wait(); }
                                                catch( InterruptedException ex ) { break; }
                                            }
                                        }
                                    } );
        voidTh.setDaemon( true );
        voidTh.start();
    }

    //----------------------------------------------------------------------------//
    // INNER CLASS
    // A Bus Listener
    //---------------------------------------------------------------------------//

    /**
     * This is just an interface needed by the BusListener class.
     */
    @FunctionalInterface
    private interface MessageHandler
    {
        void handle( Message message );
    }

    /**
     * BusListener: this class is the messages orchestrator for the bus.
     *
     * When arrived to destinations, messages are processed by the inner class
     * ::ClientListener. In other words, from the point of the view of clients
     * connected to an ExEn, ::BusListener is the one that sends messages to
     * the clients and ::ClientListener the one that receives messages from them.
     *
     * An ExEn sends only messages produced by itself, therefore an ExEn can not
     * resend messages received from another ExEn or tool (like Glue or Gum), but
     * messages to be sent are sent to every connected entity (using
     * ::NetworkManager).
     */
    private final class BusListener implements IEventBus.Listener<com.peyrona.mingle.lang.messages.Message>
    {
        private final Map<Class<?>, MessageHandler> msgHandler;       // Pre-computed dispatch map for O(1) message routing
        private final Map<String  , IDriver>        driver4device;    // Driver associated with a device (by its name)

        @Override
        public void onMessage( Message message )
        {
            msgHandler.get( message.getClass() )
                      .handle( message );    // If there is no handler for the messsage, a null point exc is triggered: this is intended
        }

        //------------------------------------------------------------------------//
        // PRIVATE INTERFACE

        private BusListener()
        {
            driver4device = new ConcurrentHashMap<>( 5 );   // Cached driver for every device (5 to start small: will grow as needed)

            msgHandler = Map.of( MsgDeviceChanged.class , this::handleDeviceChanged,
                                 MsgDeviceReaded.class  , this::handleDeviceReaded,
                                 MsgChangeActuator.class, this::handleChangeActuator,
                                 MsgTrigger.class       , this::handleExecute,
                                 MsgReadDevice.class    , this::handleReadDevice );
        }

        private void handleDeviceChanged(Message message)
        {
            final MsgDeviceChanged msg = (MsgDeviceChanged) message;
            final boolean          own = deviMgr.named( msg.name ) != null;

            if( own || gridMgr.isNode )
                ruleMgr.forEach( rule -> rule.eval( msg.name, msg.value ) );

            broadcast( message, own );
        }

        private void handleDeviceReaded( Message message )
        {
            final MsgDeviceReaded msg = (MsgDeviceReaded) message;
            final boolean         own = deviMgr.named( msg.name ) != null;

            if( own )
                deviMgr.named( msg.name ).value( msg.value );

            if( bSendAll )
                broadcast( message, own );
        }

        private void handleChangeActuator( Message message )
        {
            final MsgChangeActuator msg    = (MsgChangeActuator) message;
            final IDriver           driver = getDriver4Device( msg.name );

            if( driver != null )
                driver.write( msg.name, msg.value );

            if( bSendAll )
                broadcast( message, driver != null );
        }

        private void handleExecute( Message message )
        {
            final MsgTrigger msg = (MsgTrigger) message;
            boolean          own = true;

            final IRule rule = ruleMgr.named( msg.name );

            if( rule != null )
            {
                rule.trigger( (boolean) msg.value );
            }
            else
            {
                final IScript script = srptMgr.named( msg.name );

                if( script != null )  script.execute();
                else                  own = false;
            }

            if( bSendAll )
                broadcast( message, own );
        }

        private void handleReadDevice( Message message )
        {
            final MsgReadDevice msg    = (MsgReadDevice) message;
            final IDriver       driver = getDriver4Device( msg.name );

            if( driver != null )
                driver.read( msg.name );

            if( bSendAll )
                broadcast( message, driver != null );
        }

        private void broadcast( Message message, boolean owned )
        {
            ExEnComm msg = null;

            if( ! netwMgr.isEmpty() )                      // Saves CPU: avoids to create the ExEnComm and the JSON
            {
                msg = new ExEnComm( message );
                netwMgr.broadcast( msg );                  // Internally uses a thread
            }

            if( gridMgr.isNode )
            {
                Class clazz = message.getClass();

                if( (! owned) || (clazz == MsgDeviceChanged.class) )    // It is needed to be sent only if this condition is satisfied
                {
                    if( msg == null )                      // Saves CPU (if netwMgr.isEmpty() is false, 'msg' was built previously)
                        msg = new ExEnComm( message );

                    gridMgr.broadcast( msg );              // Internally uses a thread
                }
            }
        }

        private IDriver getDriver4Device( String name )
        {
            IDriver driver = driver4device.get( name );

            if( driver == null )
            {
                driver = drvrMgr.first( cmd -> cmd.has( name ) );

                if( driver != null )
                    driver4device.put( name, driver );
                else
                    Stick.this.failed( new MingleException(), "No driver for "+ name );
            }

            return driver;
        }
    }

    //----------------------------------------------------------------------------//
    // INNER CLASS
    // This is passed to the Communcations Manager to process incoming requests.
    //---------------------------------------------------------------------------//

    /**
     * Processes incoming requests (v.g. from other ExEn or external tool (an IDE)).
     * Here are all commands (requests) that an ExEn can receive.
     * This class is used by NetworkManager and GridManager.
     * <pre>
     * Note 1: NetworkManager uses all ExEnComm.Request types, GridManager does not
     *         uses: List, Add, Remove, neither Exit. But is simpler to use only
     *         one one class for both: NetworkManager and GridManager.
     *
     * Note 2: An ExEn sends only messages produced by itself, therefore an ExEn
     *         can not resend messages received from another ExEn or tool (like
     *         Glue or Gum).
     *
     * Note 3: devices in different ExEns can have same name (makes things easier
     *         for developers).
     * </pre>
     */
    private final class ServerListener implements INetServer.IListener
    {
        private final boolean isInfoLoggable = isLoggable( ILogger.Level.INFO );

        @Override
        public void onConnected( INetServer origin, INetClient client )
        {
            if( gridMgr.isDeaf )
                return;

            if( isInfoLoggable )
                log( ILogger.Level.INFO, "Server Connected client: "+ origin );
        }

        @Override
        public void onDisconnected( INetServer origin, INetClient client )
        {
            if( gridMgr.isDeaf )
                return;

            if( isInfoLoggable )
                log( ILogger.Level.INFO, "Server Disconnected client: "+ origin );
        }

        @Override
        public void onError( INetServer origin, INetClient client, Exception exc )
        {
            if( gridMgr.isNode && gridMgr.isDeaf )
                return;

            log( ILogger.Level.SEVERE, exc );

            if( client != null )
                client.send( ExEnComm.asError( "Error in connection:"+ exc.getMessage() ).toString() );
        }

        @Override
        public void onMessage( INetServer origin, INetClient client, String message )
        {
            if( gridMgr.isDeaf )
                return;

            if( isInfoLoggable )
                log( ILogger.Level.INFO, "Arrived message ["+ message +"] received from ["+ origin +"] to ["+ client +']' );

            try
            {
                ExEnComm in = ExEnComm.fromJSON( message );

                switch( in.request )
                {
                    case List:
                        bSendAll = ! in.payload.isNull();    // To force to broadcast all messages instead of only DeviceChanged, send List with a non-null valid JSON value.
                        client.send( new ExEnComm( ExEnComm.Request.Listed, all( (String[]) null ) ).toString() );

                        break;

                    case Add:
                        _add_( in, client );
                        break;

                    case Remove:
                        _remove_( in, client );
                        break;

                    case Read:       // Another ExEn or tool is requesting to read a device's value (JSON -> { "Read": sDeviceName })
                        if( in.getDeviceName() != null )                            // When the request is malformed, this is null
                        {
                            IDevice device = deviMgr.named( in.getDeviceName() );
                            if( device != null )                                    // If this device belongs to this ExEn.
                                bus().post( new MsgReadDevice( device.name() ) );   // Following the ExEn logic, a message is posted into the Bus, the Driver will
                        }                                                           // receive it and it will read current Device's value posting back a 'Readed' msg.
                        break;

                    case Readed:     // Another ExEn or tool is informing that a driver is reporting a new value
                    case Changed:    // Another ExEn or tool is informing that a device (hosted by that ExEn) changed its state
                    case Change:     // Another ExEn or tool is requesting to change an Actuators state that does not belong to that ExEn (it could be that Actuator belongs to this ExEn)
                    case Execute:    // Another ExEn or tool is requesting to trigger a Rule or a Script (it could or not resides in this ExEn)
                        _postRequest_( in, client );
                        break;

                    case Error:
                    case ErrorAdding:
                    case ErrorDeleting:
                        _error_( in, client );
                        break;

                    case Exit:
                        exit( 0 );
                        break;

                    default:
                        throw new MingleException( "Unknown request: "+ message );
                }
            }
            catch( NullPointerException | IllegalArgumentException | ParseException | MingleException exc )
            {
                log( ILogger.Level.WARNING, exc );
                client.send( ExEnComm.asError( "Error processing:\n"+ message +'\n'+ exc.getMessage() ).toString() );
            }
        }

        private void _add_( ExEnComm comm, INetClient origin )
        {
            List<ICommand> lstAdded = new ArrayList<>();
            List<ICommand> lstError = new ArrayList<>();
            List<ICommand> lstAll   = comm.getCommands();

            for( ICommand cmd : sortByType( lstAll, false ) )    // Java compiler optimizes for-each loops by transforming them into equivalent loops using iterators ('sortByType' is invoked only once)
            {
                if( add( cmd ) )  lstAdded.add( cmd );
                else              lstError.add( cmd );
            }

            netwMgr.broadcast( new ExEnComm( ExEnComm.Request.Added, lstAdded.toArray( ICommand[]::new ) ) );

            if( ! lstError.isEmpty() )
            {
                origin.send( new ExEnComm( ExEnComm.Request.ErrorAdding, lstError.toArray( ICommand[]::new ) ).toString() );          // Only to the requester
            }
        }

        private void _remove_( ExEnComm comm, INetClient origin )
        {
            List<ICommand> lstDeleted = new ArrayList<>();
            List<ICommand> lstErrors  = new ArrayList<>();

            for( ICommand cmd : sortByType( comm.getCommands(), true ) )
            {
                if( remove( cmd ) )  lstDeleted.add( cmd );                   // Stick:remove(...) pauses the bus
                else                 lstErrors.add(  cmd );
            }

            netwMgr.broadcast( new ExEnComm( ExEnComm.Request.Removed, lstDeleted.toArray( ICommand[]::new ) ) );

            if( ! lstErrors.isEmpty() )
            {
                origin.send( new ExEnComm( ExEnComm.Request.ErrorDeleting, lstErrors.toArray( ICommand[]::new ) ).toString() );       // Only to the requester
            }
        }

        private void _postRequest_( ExEnComm comm, INetClient origin )
        {
            ExEnComm.Request    request = comm.request;
            Pair<String,Object> pair    = comm.getChange();

            if( "_error_".equals( pair.getKey() ) )
            {
                origin.send( ExEnComm.asError( pair.getValue().toString() ).toString() );
                return;
            }

            String name  = pair.getKey();
            Object value = pair.getValue();

            switch( request )
            {
                case Readed:    // Another ExEn is reporting a new value for a device (it could be that a RULE in this ExEn uses this device).
                    // This message will be used only if the device exists in this ExEn: so it could produce (delta) a change in
                    // the device, otherwise this message is useless.
                    // This is not needed --> sDevice = sDevice.trim().toLowerCase(); (transpiler does it).

                    if( deviMgr.named( name ) != null )
                        bus().post( new MsgDeviceReaded( name, value ) );

                    break;

                case Changed:   // A device hosted in another ExEn changed (it could be that a RULE in this ExEn uses this device).
                    // This always returns null --> deviMgr.named( sDevice ) because the device is hosted in another ExEn.
                    // We could ask the rules if any rule uses the device and if none, do not send the message to the bus,
                    // but it is faster and simpler to directly send the message to the bus: if no rule uses it, it will be not used.
                    // This is not needed --> sDevice = sDevice.trim().toLowerCase();  (transpiler does it).

                    IDevice device = deviMgr.named( name );

                    if( device != null )
                    {
                        device.value( value );      // Needed beacuse this device (is hosted in another ExEn) has to keep its value: an expr can have more
                                                    // than one device involved, but device's values are updated once at a time by different messages.
                        bus().post( new MsgDeviceChanged( name, value ) );
                    }

                    break;

                case Change:    // Something (another ExEn or a tool) is requesting to change an Actuator's state (it could resides in this ExEn).
                                //  (JSON -> { "Change": { sDeviceName : deviceValue })
                    if( deviMgr.named( name ) != null )        // If null, this ExEn does not have this Actuator: no error has to be reported because another ExEn could have it.
                        bus().post( new MsgChangeActuator( name, value ) );

                    break;

                case Execute:   // Something (another ExEn or a tool) is requesting to trigger a Rule or a Script (it could or not resides in this ExEn).
                                //  (JSON -> { "Execute": ruleName })
                    if( ruleMgr.named( name ) != null ||    // It is null when the rule is not in this ExEn (or there was a problem creating the rule)
                        srptMgr.named( name ) != null )     // It is null when the script is not in this ExEn (or there was a problem creating the script)
                    {
                        bus().post( new MsgTrigger( name, false ) );
                    }

                    break;
            }

            // There is no need to broadcast, because if the msgs posted into the bus finally affect a
            // device, this device's change will be reported as usual (IEventBus.Listener<MsgDeviceChanged>)
            // (see at beining of this file how these changes are reported).
        }

        private void _error_( ExEnComm comm, INetClient origin )
        {
            switch( comm.request )
            {
                case Error:
                    log( ILogger.Level.SEVERE, "Error at ExEn "+ origin.toString() +": "+ comm.getErrorMsg() );
                    break;

                case ErrorAdding:
                case ErrorDeleting:
                    String sAction = (comm.request == ExEnComm.Request.ErrorAdding) ? "add" : "delete";

                    log( ILogger.Level.SEVERE, "Error at ExEn "+ origin.toString() +", can not "+ sAction +" following: "+ comm.getCommands().toString() );
                    break;
            }
        }
    }
}