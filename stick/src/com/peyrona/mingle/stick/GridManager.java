
package com.peyrona.mingle.stick;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.ParseException;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.interfaces.IXprEval;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.commands.IDriver;
import com.peyrona.mingle.lang.interfaces.commands.ILibrary;
import com.peyrona.mingle.lang.interfaces.commands.IRule;
import com.peyrona.mingle.lang.interfaces.commands.IScript;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.ExEnComm;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.messages.Message;
import com.peyrona.mingle.lang.messages.MsgChangeActuator;
import com.peyrona.mingle.lang.messages.MsgDeviceChanged;
import com.peyrona.mingle.lang.messages.MsgDeviceReaded;
import com.peyrona.mingle.lang.messages.MsgExecute;
import com.peyrona.mingle.lang.messages.MsgReadDevice;
import com.peyrona.mingle.network.NetworkBuilder;
import com.peyrona.mingle.network.NetworkConfig;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * This class sends messages to the ExEns that appear under "grid" -> "members" in the configuration file.<br>
 * <br>
 * A 'deaf' node will not receive messages from other nodes, but will send messages to other nodes. <br>
 * A 'mute' node will receive messages from other nodes, but will not send messages to other nodes.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class GridManager
{
    private       boolean     bErrors = false;
    private final Set<Server> lstServers;                    // To receive msgs from other nodes in the grid. When this list is empty, node is deaf.
    private final Set<Client> lstClients;                    // To send    msgs to   other nodes in the grid. When this list is empty, node is mute.
    private final Supplier<INetServer.IListener> supplier;   // To create listeners for the Servers

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE STATIC METHODS (lifecycle)

    /**
     * Creates and returns a GridManager if the "grid" module is configured,
     * or returns {@code null} if this ExEn does not belong to a grid.
     * <p>
     * When the grid module is absent, the "_GridManager_toString_Message_" system
     * property is set so callers can display informational text later.
     *
     * @param config    The configuration instance.
     * @param rt        The runtime instance.
     * @param onFailure Callback invoked when the configuration is invalid; receives (exception, message).
     * @return A configured GridManager, or {@code null} if the "grid" module is not present.
     */
    static GridManager create( IConfig                      config,
                               IRuntime                     rt,
                               BiConsumer<Exception,String> onFailure )
    {
        if( ! config.isModule( "grid" ) )
        {
            System.setProperty( "_GridManager_toString_Message_",
                                "Grid information:\n    This ExEn does not belong to a Grid.\n" );
            return null;
        }

        GridManager tmp = new GridManager( rt );

        if( ! tmp.isValid() )
        {
            String msg = "Invalid \"grid\" configuration.";

            if( tmp.isMute() && tmp.isDeaf() )
                msg = "Node is 'mute' and is 'deaf': therefore as node-grid it is useless.\n" + msg;

            onFailure.accept( null, msg );   // Expected to terminate the application
        }

        return tmp;
    }

    /**
     * Prepares and starts the grid node, or — when this ExEn does not belong to a
     * grid — validates that the node is useful and starts the keep-alive void thread.
     * <p>
     * When {@code gridMgr} is not {@code null}, all Rule WHEN/IF clauses are scanned
     * to detect devices that reside in remote ExEns; those are registered as remote
     * devices before the grid itself is started.
     *
     * @param gridMgr    The GridManager instance, or {@code null} if not a grid node.
     * @param rt         The runtime instance.
     * @param deviMgr    The DeviceManager instance.
     * @param ruleMgr    The RuleManager instance.
     * @param srptMgr    The ScriptManager instance.
     * @param onFailure  Callback invoked on failure; receives (exception, message).
     * @param voidThread Runnable that starts the keep-alive void thread.
     */
    static void start( GridManager                  gridMgr,
                       IRuntime                     rt,
                       DeviceManager                deviMgr,
                       RuleManager                  ruleMgr,
                       ScriptManager                srptMgr,
                       BiConsumer<Exception,String> onFailure,
                       Runnable                     voidThread )
    {
        if( gridMgr == null )
        {
            if( deviMgr.isEmpty() && ruleMgr.isEmpty() && srptMgr.isEmpty() )
                onFailure.accept( null, "Useless ExEn: no Devices, no Rules, no Scripts and no communications" );

            voidThread.run();

            return;
        }

        // If this is a Grid Node (and it is valid), devices in other nodes (referenced in this Stick) have to be identified.
        // Such devices can be only in Rule's WHEN and IF clauses.

        for( ICommand cmd : rt.all( "rules" ) )
        {
            IXprEval eval4When = rt.newXprEval().build( ((IRule) cmd).getWhen(), (r) -> {}, rt::getGroupMemberNames );

            for( String sName : eval4When.getVars().keySet() )
            {
                if( deviMgr.named( sName ) == null )          // If device manager does not have a device with this name,
                    deviMgr.createRemoteDevice( sName );      // it is because that device exists in another ExEn.
            }

            if( ((IRule) cmd).getIf() != null )
            {
                IXprEval eval4If = rt.newXprEval().build( ((IRule) cmd).getIf(), (r) -> {}, rt::getGroupMemberNames );

                for( String sName : eval4If.getVars().keySet() )
                {
                    if( deviMgr.named( sName ) == null )      // If device manager does not have a device with this name,
                        deviMgr.createRemoteDevice( sName );  // it is because that device exists in another ExEn.
                }
            }
        }

        // After external devices are identified, the grid can be started

        try
        {
            gridMgr.start();
            System.setProperty( "_GridManager_toString_Message_", gridMgr.toString() );
        }
        catch( MingleException me )
        {
            onFailure.accept( null, "One or more communications ports are already in use" );
        }
        catch( Exception exc )
        {
            onFailure.accept( exc, "It looks like there is another ExEn running using same config" );
        }
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    @Override
    public String toString()
    {
        String sMuteInfo = isMute()  ? "true  (Only sends to those that initiated a connection)."
                                     : "false (Sends messages to all defined nodes (ExEns)).";

        String sDeafInfo = (isDeaf() ? "true  (Does not attend"
                                     : "false (Attends") +
                                     " to messages sent by other exens).";

        StringBuilder sbInfo = new StringBuilder( 512 ).append( "Grid information:\n" );

        sbInfo.append( "   * Is mute = " ).append( sMuteInfo ).append( '\n' )
              .append( "   * Is deaf = " ).append( sDeafInfo ).append( '\n' )
              .append( "   * Protocols to receive messages:" );

        if( UtilColls.isEmpty( lstServers ) )
        {
            sbInfo.append( " <none>\n" );
        }
        else
        {
            sbInfo.append( '\n' );

            for( Server server : lstServers )
                sbInfo.append( "         + " ).append( server.toString() ).append( '\n' );
        }

        sbInfo.append( "   * Nodes to send messages to:" );

        if( UtilColls.isEmpty( lstClients ) )
        {
            sbInfo.append( " <none>\n" );
        }
        else
        {
            sbInfo.append( '\n' );

            for( Client client : lstClients )
                sbInfo.append( "         + " ).append( client.toString() ).append( '\n' );
        }

        return sbInfo.toString();
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    boolean isDeaf()
    {
        return UtilColls.isEmpty( lstServers );
    }

    boolean isMute()
    {
        return UtilColls.isEmpty( lstClients );
    }

    boolean isValid()
    {
        if( bErrors )
            return false;

        if( lstServers == null )    // Although Gum does not need this value, Stick needs it.
            return false;

        return ! (isDeaf() && isMute());
    }

    void broadcast( Throwable exc )
    {
        broadcast( ExEnComm.asError( UtilStr.toStringBrief( exc ) ).toString() );
    }

    void broadcast( Message message )
    {
        broadcast( new ExEnComm( message ).toString() );
    }

    synchronized void start()
    {
        if( UtilColls.isNotEmpty( lstServers ) )
            lstServers.forEach( srv -> srv.start( supplier.get() ) );

        if( UtilColls.isNotEmpty( lstClients ) )
            lstClients.forEach( cli -> cli.start() );
    }

    synchronized void stop()
    {
        if( UtilColls.isNotEmpty( lstServers ) )
        {
            lstServers.forEach( srv -> srv.stop() );
            lstServers.clear();
        }

        if( UtilColls.isNotEmpty( lstClients ) )
        {
            lstClients.forEach( cli -> cli.stop() );
            lstClients.clear();
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    // PRIVATE CONSTRUCTOR
    private GridManager( IRuntime rt )
    {
        Set<Server> setTmpSrv = new HashSet<>(  5 );
        Set<Client> setTmpCli = new HashSet<>( 25 );

        // Process all grid nodes: localhost entries become servers, others become clients
        for( JsonValue node : NetworkConfig.getGridNodes() )    // Never returns null
        {
            if( ! node.isObject() )
            {
                bErrors = true;
                continue;
            }

            JsonObject jo   = node.asObject();
            String     host = jo.getString( "host", null );

            if( UtilStr.isEmpty( host ) )
            {
                bErrors = true;
                continue;
            }

            try
            {
                if( isLocalhost( host ) )
                {
                    // Localhost entries: create servers (if empty, this grid node is deaf)
                    if( Server.isValid( node ) )
                        setTmpSrv.add( new Server( node ) );
                    else
                        bErrors = true;
                }
                else
                {
                    // Remote entries: create clients (if empty, this grid node is mute)
                    if( Client.isValid( node ) )
                        setTmpCli.add( new Client( node ) );
                    else
                        bErrors = true;
                }
            }
            catch( MingleException me )
            {
                bErrors = true;
                rt.log( ILogger.Level.SEVERE, me );
            }
        }

        lstServers = (bErrors || setTmpSrv.isEmpty() ) ? null : setTmpSrv;
        lstClients = (bErrors || setTmpCli.isEmpty() ) ? null : setTmpCli;
        supplier   = isDeaf() ? null : () -> new ServerListener( rt );
    }

    private void broadcast( String msg )
    {
        // The following is always true because if there is no clients and no servers, this node does not belong to a grid:
        // lstServers != null || lstClients != null

        UtilSys.executor( true )
               .execute( () ->
                        {
                            if( lstServers != null )
                                lstServers.forEach( srv -> srv.broadcast( msg ) );

                            if( lstClients != null )
                                lstClients.forEach( cli -> cli.send( msg ) );
                        } );

        // forEach(...) is thread-safe when applied to a List that is not modified concurrently
    }

    /**
     * Checks if the given host is localhost (including "127.0.0.1" and "::1").
     */
    private static boolean isLocalhost( String host )
    {
        if( UtilStr.isEmpty( host ) )
            return false;

        // Extract just the host part (without port)
        String hostOnly = host.contains( ":" ) ? host.substring( 0, host.lastIndexOf( ':' ) ) : host;

        return "localhost".equalsIgnoreCase( hostOnly )
            || "127.0.0.1".equals( hostOnly )
            || "::1".equals( hostOnly );
    }

    private static void log( Level level, Object msg )
    {
        String m = msg.toString(); // UtilStr.toString( msg ) );

        if( UtilSys.getLogger() == null )
            System.err.println( "["+ level +"] "+ m );
        else
            UtilSys.getLogger().log( level, msg.toString() );
    }

    //------------------------------------------------------------------------//
    // Nodes (target nodes to inform)

    private static final class Server
    {
        @Override
        public String toString()
        {
            if( server == null )
                return "SocketServer (FAILED TO CREATE - check config)";

            if( server.isRunning() )
                return server.toString();

            // Server exists but not started yet - show config
            String displayPort = "<default>";

            if( config != null )
            {
                try
                {
                    JsonObject jo = Json.parse( config ).asObject();
                    int port = jo.getInt( "port", -1 );

                    if( port > 0 )
                        displayPort = String.valueOf( port );
                }
                catch( Exception e )
                {
                    // Ignore parse errors, use default
                }
            }

            return "SocketServer at: localhost:" + displayPort + ", Timeout=0, Allow=Intranet (not started)";
        }

        /**
         * Validates that the JsonValue contains the required fields for a server node.
         * Expected format: { "host": "localhost", "network": "Plain Socket Server" }
         *
         * @param jv The JsonValue to validate.
         * @return true if the node configuration is valid, false otherwise.
         */
        static boolean isValid( JsonValue jv )
        {
            if( jv == null || ! jv.isObject() )
                return false;

            JsonObject jo    = jv.asObject();
            String     sHost = jo.getString( "host", null );
            JsonValue  jvNet = jo.get( "network" );

            if( UtilStr.isEmpty( sHost ) || jvNet == null || jvNet.isNull() )
                return false;

            // Extract the network name and validate it refers to a server
            String sNetName = jvNet.isString() ? jvNet.asString()
                                               : jvNet.asObject().getString( "name", null );

            if( ! NetworkConfig.isServerNetwork( sNetName ) )
            {
                log( Level.SEVERE, "Grid node with host '"+ sHost +"' must reference a server network: '"+ sNetName +"' is not." );
                return false;
            }

            return true;
        }

        //------------------------------------------------------------------------//

        final INetServer server;
        final String     config;

        /**
         * Creates a Server from a grid node configuration.
         * Parses the network field and builds the INetServer.
         *
         * @param jv The JsonValue containing the node configuration.
         */
        Server( JsonValue jv )
        {
            JsonObject jo = jv.asObject();

            JsonValue jvNetwork = NetworkConfig.getAsNetwork( jo.get( "network" ) );

            INetServer tmpServer = null;
            String     tmpConfig = null;

            if( jvNetwork != null && ! jvNetwork.isNull() )
            {
                Map<INetServer,String> map = NetworkBuilder.buildAllServers( jvNetwork.toString() );

                if( ! map.isEmpty() )
                {
                    Map.Entry<INetServer,String> entry = map.entrySet().iterator().next();
                    tmpServer = entry.getKey();
                    tmpConfig = entry.getValue();
                }
            }

            this.server = tmpServer;
            this.config = tmpConfig;
        }

        void start( INetServer.IListener listener )
        {
            if( server != null && config != null )
            {
                server.add( listener );
                server.start( config );
                log( Level.INFO, toString() +" -> Started");
            }
        }

        void stop()
        {
            if( server != null )
                server.stop();
        }

        void broadcast( String msg )
        {
            if( server != null )
                server.broadcast( msg );
        }
    }

    //------------------------------------------------------------------------//
    // Clients (target nodes to inform to)

    private static final class Client
    {
        @Override
        public String toString()
        {
            return (client != null) ? client.toString() : ("Client to " + host);
        }

        /**
         * Validates that the JsonValue contains the required fields for a grid node.
         * Expected format: { "host": "192.168.1.100:65533", "network": "Plain Socket Client" }
         *
         * @param jv The JsonValue to validate.
         * @return true if the node configuration is valid, false otherwise.
         */
        static boolean isValid( JsonValue jv )
        {
            if( jv == null || ! jv.isObject() )
                return false;

            JsonObject jo    = jv.asObject();
            String     sHost = jo.getString( "host", null );
            JsonValue  jvNet = jo.get( "network" );

            if( UtilStr.isEmpty( sHost ) || jvNet == null || jvNet.isNull() )
                return false;

            // Extract the network name and validate it refers to a client
            String sNetName = jvNet.isString() ? jvNet.asString()
                                               : jvNet.asObject().getString( "name", null );

            if( ! NetworkConfig.isClientNetwork( sNetName ) )
            {
                log( Level.SEVERE, "Grid node with host '"+ sHost +"' must reference a client network, not '"+ sNetName +"'" );
                return false;
            }

            return true;
        }

        //------------------------------------------------------------------------//

        final INetClient client;
        final String     host;       // e.g.: "192.168.7.9" or "192.168.7.9:55880"
        final String     config;     // JSON config for connect()

        /**
         * Creates a Client from a grid node configuration.
         * Parses the host and network fields, builds the INetClient, and prepares the connection config.
         *
         * @param jv The JsonValue containing the node configuration.
         */
        Client( JsonValue jv )
        {
            JsonObject jo = jv.asObject();

            this.host = jo.getString( "host", null );

            JsonValue jvNetwork = NetworkConfig.getAsNetwork( jo.get( "network" ) );

            INetClient tmpClient = null;
            String     tmpConfig = null;

            if( jvNetwork != null && ! jvNetwork.isNull() )
            {
                Map<INetClient,String> map = NetworkBuilder.buildAllClients( jvNetwork.toString() );

                if( ! map.isEmpty() )
                {
                    Map.Entry<INetClient,String> entry = map.entrySet().iterator().next();
                    tmpClient = entry.getKey();

                    // Merge host (and optionally port) into the init config
                    String     sInit  = entry.getValue();
                    JsonObject joInit = UtilStr.isEmpty( sInit )
                                        ? new JsonObject()
                                        : Json.parse( sInit ).asObject();

                    // Parse host:port format
                    String[] parts = this.host.split( ":" );
                    joInit.set( "host", parts[0] );

                    if( parts.length > 1 )
                    {
                        try
                        {
                            joInit.set( "port", Integer.parseInt( parts[1] ) );
                        }
                        catch( NumberFormatException nfe )
                        {
                            // Invalid port format, keep original if present
                        }
                    }

                    tmpConfig = joInit.toString();
                }
            }

            this.client = tmpClient;
            this.config = tmpConfig;
        }

        /**
         * Sends a message to the remote ExEn node.
         * If not connected, attempts to connect first.
         *
         * @param msg The message to send.
         */
        void send( String msg )
        {
            if( client == null )
                return;

            // Try to connect with the other node (it could be that the other was not ready until now or that the connection was lost)
            if( ! client.isConnected() )
                start();

            if( client.isConnected() )
                client.send( msg );
        }

        /**
         * Starts the client by connecting to the remote ExEn node.
         */
        void start()
        {
            if( client != null && config != null )
            {
                client.connect( config );
                log( Level.INFO, toString() +" -> Connected");
            }
        }

        /**
         * Stops the client by disconnecting from the remote ExEn node.
         */
        void stop()
        {
            if( client != null )
                client.disconnect();
        }
    }

    //----------------------------------------------------------------------------//
    // INNER CLASS (used by GridManager and NetworkManager).
    // Passed to the server to process incoming messages from other tools (ExEns, Gum, etc).
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
    private static final class ServerListener implements INetServer.IListener
    {
        private final IRuntime rt;
        private final boolean  isLoggable;

        ServerListener( IRuntime rt )
        {
            this.rt         = rt;
            this.isLoggable = rt.isLoggable( ILogger.Level.INFO );
        }

        //------------------------------------------------------------------------//

        @Override
        public void onConnected( INetServer origin, INetClient client )
        {
            if( isLoggable )
                rt.log( ILogger.Level.INFO, "Server Connected client: "+ origin );
        }

        @Override
        public void onDisconnected( INetServer origin, INetClient client )
        {
            if( isLoggable )
                rt.log( ILogger.Level.INFO, "Server Disconnected client: "+ origin );
        }

        @Override
        public void onError( INetServer origin, INetClient client, Exception exc )
        {
            rt.log( ILogger.Level.SEVERE, exc );

            if( client != null )
                client.send( ExEnComm.asError( "Error in connection:"+ exc.getMessage() ).toString() );
        }

        @Override
        public void onMessage( INetServer origin, INetClient client, String message )
        {
            UtilSys.executor( true )
                   .name( getClass().getSimpleName() +":onMessage:"+ message )
                   .execute( () -> processMsg( origin, client, message ) );
        }

        //------------------------------------------------------------------------//

        private void processMsg( INetServer origin, INetClient client, String message )
        {
            if( isLoggable )
                rt.log( ILogger.Level.INFO, "Arrived message ["+ message +"] received from ["+ origin +"] to ["+ client +']' );

            try
            {
                ExEnComm in = ExEnComm.fromJSON( message );

                switch( in.request )
                {
                    case List:
                        client.send( new ExEnComm( ExEnComm.Request.Listed, rt.all( (String[]) null ) ).toString() );
                        break;

                    case Add:      // Another ExEn or tool is informing that a device was added in a remote ExEn
                        _add_( in, client );
                        break;

                    case Remove:   // Another ExEn or tool is informing that a device was removed from a remote ExEn
                        _remove_( in, client );
                        break;

                    case Read:       // Another ExEn or tool is requesting to read a device's value (JSON -> { "Read": sDeviceName })
                        if( in.getDeviceName() != null )                            // When the request is malformed, this is null
                        {
                            ICommand cmd    = rt.get( in.getDeviceName() );
                            IDevice  device = (cmd instanceof IDevice) ? (IDevice) cmd : null;

                            if( device != null )                                          // If this device belongs to this ExEn.
                            {
                                // Reply immediately with the cached value so the requesting client always receives a Readed response — even when the device value has not changed
                                // since the last driver poll.
                                //
                                // Without this direct reply, the response depended entirely on handleDeviceReaded, which only broadcasts when the value changes.
                                // Stable devices would therefore never respond, leaving GUM dashboards with uninitialised gadget state on page load.
                                //
                                // If cachedValue is null the device has never been read yet (e.g. first startup before any driver poll completes). In that case we skip the
                                // immediate reply and rely on the bus flow below: the first driver read will change the value from unset to actual, which handleDeviceReaded
                                // will broadcast normally.
                                Object value = device.value();

                                if( value != null )
                                    client.send( new ExEnComm( new MsgDeviceReaded( device.name(), value ) ).toString() );

                                // Also trigger a fresh driver read. If the hardware value has changed since the last poll, handleDeviceReaded will broadcast a second Readed
                                // (with the updated value) and evaluate any dependent rules. The client-side stale-detection logic handles the two-message sequence.
                                rt.bus().post( new MsgReadDevice( device.name(), false ) );
                            }
                        }
                        break;

                    case Readed:     // Another ExEn or tool is informing that a driver is reporting a new value
                    case Changed:    // Another ExEn or tool is informing that a device (hosted by that ExEn) changed its state
                    case Change:     // Another ExEn or tool is requesting to change an Actuators state that does not belong to that ExEn (it could be that Actuator belongs to this ExEn)
                    case Execute:    // Another ExEn or tool is requesting to trigger a Rule or a Script (it could or not resides in this ExEn)
                        _postRequest_( in, client );
                        break;

                    case Error:
                        rt.log( ILogger.Level.SEVERE, "Error at ExEn "+ origin.toString() +": "+ in.getErrorMsg() );
                        break;

                    case Exit:
                        rt.exit( 0 );
                        break;

                    default:
                        throw new MingleException( "Unknown request: "+ message );
                }
            }
            catch( NullPointerException | IllegalArgumentException | ParseException | MingleException exc )
            {
                rt.log( ILogger.Level.WARNING, exc );
                client.send( ExEnComm.asError( "Error processing:\n"+ message +'\n'+ exc.getMessage() ).toString() );
            }
        }

        private void _add_( ExEnComm comm, INetClient client )
        {
            List<ICommand> lst = sortByType( comm.getCommands(), false );

            for( ICommand cmd : lst )
            {
                try
                {
                    rt.add( cmd );
                    client.send( new ExEnComm( ExEnComm.Request.Added, cmd ).toString() );    // Confirmation is sent back
                }
                catch( Exception exc )
                {
                    rt.log( ILogger.Level.SEVERE, exc );
                    client.send( ExEnComm.asError( "Cannot add '"+ cmd.name() +"': "+ exc.getMessage() ).toString() );
                }
            }
        }

        private void _remove_( ExEnComm comm, INetClient client )
        {
            List<ICommand> lst = sortByType( comm.getCommands(), true );

            for( ICommand cmd : lst )
            {
                boolean bOK = rt.remove( cmd );

                // rt.remove() returns false for a Driver or Script that was already cascade-removed
                // by a prior step in this batch (e.g. removing a Device auto-removes its empty Driver).
                // That is not a failure: if the command is gone, the intent is achieved.
                if( !bOK && rt.get( cmd.name() ) == null )
                    bOK = true;

                if( bOK )  client.send( new ExEnComm( ExEnComm.Request.Removed, cmd ).toString() );
                else       client.send( ExEnComm.asError( "Cannot remove '"+ cmd.name() +"'" ).toString() );
            }
        }

        private void _postRequest_( ExEnComm comm, INetClient origin )
        {
            ExEnComm.Request    request = comm.request;
            Pair<String,Object> pair    = comm.getChange();

            if( pair == null )
            {
                origin.send( ExEnComm.asError( "Invalid payload for request: "+ request ).toString() );
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

                    if( rt.get( name ) instanceof IDevice )
                        rt.bus().post( new MsgDeviceReaded( name, value, false ) );

                    break;

                case Changed:   // A device hosted in another ExEn changed (it could be that a RULE in this ExEn uses this device).
                    // This always returns null --> deviMgr.named( sDevice ) because the device is hosted in another ExEn.
                    // We could ask the rules if any rule uses the device and if none, do not send the message to the bus,
                    // but it is faster and simpler to directly send the message to the bus: if no rule uses it, it will not be used.
                    // This is not needed --> sDevice = sDevice.trim().toLowerCase();  (transpiler does it).

                    if( rt.get( name ) instanceof IDevice )
                        rt.bus().post( new MsgDeviceChanged( name, value, false ) );

                    break;

                case Change:    // Something (another ExEn or a tool) is requesting to change an Actuator's state (it could resides in this ExEn).
                                //  (JSON -> { "Change": { sDeviceName : deviceValue })
                    if( rt.get( name ) instanceof IDevice )        // If null, this ExEn does not have this Actuator: no error has to be reported because another ExEn could have it.
                        rt.bus().post( new MsgChangeActuator( name, value, false ) );

                    break;

                case Execute:   // Something (another ExEn or a tool) is requesting to execute a Rule or a Script (it could or not resides in this ExEn).
                                //  (JSON -> { "Execute": ruleName })
                    {
                        ICommand cmd = rt.get( name );

                        if( cmd instanceof IRule ||      // It is null when the rule is not in this ExEn (or there was a problem creating the rule)
                            cmd instanceof IScript )     // It is null when the script is not in this ExEn (or there was a problem creating the script)
                        {
                            rt.bus().post( new MsgExecute( name, false, false ) );
                        }
                    }

                    break;
            }

            // There is no need to broadcast, because if the msgs posted into the bus finally affect a
            // device, this device's change will be reported as usual (IEventBus.Listener<MsgDeviceChanged>)
            // (see at beining of this file how these changes are reported).
        }

        //------------------------------------------------------------------------//
        // Sort helpers — same ordering logic as Stick.sortByType / getPrecedence

        private static List<ICommand> sortByType( List<ICommand> list, boolean bReverse )
        {
            if( list.size() > 1 )
            {
                list.sort( (cmd1, cmd2) -> getPrecedence( cmd1 ).compareTo( getPrecedence( cmd2 ) ) );

                if( bReverse )
                    Collections.reverse( list );
            }

            return list;
        }

        private static Integer getPrecedence( ICommand cmd )
        {
                 if( cmd instanceof ILibrary ) return 0;    // Highest precedence: functions must be available before scripts
            else if( cmd instanceof IScript )  return 1;
            else if( cmd instanceof IDriver )  return 2;
            else if( cmd instanceof IDevice )  return 3;
                                               return 4;    // Lowest precedence (IRule)
        }
    }
}