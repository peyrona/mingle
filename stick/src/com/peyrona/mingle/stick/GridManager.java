
package com.peyrona.mingle.stick;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.ExEnComm;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.messages.Message;
import com.peyrona.mingle.network.NetworkBuilder;
import com.peyrona.mingle.network.NetworkConfig;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private final Set<Server> lstServers;      // To receive msgs from other nodes in the grid
    private final Set<Client> lstClients;      // To send    msgs to   other nodes in the grid
    private final Supplier<INetServer.IListener> supplier;

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE CONSTRUCTOR

    GridManager( IRuntime rt, Supplier<INetServer.IListener> listenerSupplier )
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
        supplier   = isDeaf()                          ? null : listenerSupplier;   // Used to create listeners for the Servers
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
    //------------------------------------------------------------------------//

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
    //------------------------------------------------------------------------//
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
}