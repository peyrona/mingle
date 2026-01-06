
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.interfaces.IConfig;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.ExEnComm;
import com.peyrona.mingle.lang.japi.GridNode;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilReflect;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/**
 * This class sends messages to the ExEns that appear under "grid" -> "members" in the configuration file.<br>
 * <br>
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class GridManager
{
            final boolean         isNode;
            final boolean         isDeaf;
    private       Set<Target>     lstTargets = null;   // Not needed to be sync because once populated it is inmutable (not initialized to new ArrayList() to save RAM)
    private       ScheduledFuture executor   = null;
    private final IConfig         config;

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE CONSTRUCTOR

    GridManager( IConfig config )
    {
        this.config = config;
        this.isNode = config.getGridNodes() != null;
        this.isDeaf = isNode && config.isGridDeaf();
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    @Override
    public String toString()
    {
        StringBuilder sbInfo = new StringBuilder( 512 )
                                   .append( "Grid information:\n" );

        if( isNode )
        {
            sbInfo.append( "    reconnect = " ).append( config.getGridReconectInterval() ).append( " milliseconds\n" )
                  .append( "    is deaf   = " ).append( config.isGridDeaf() ).append( '\n' )
                  .append( "    Target nodes:\n" );

            if( lstTargets == null )
            {
                sbInfo.append( "        This ExEn is part of a grid, but has no target nodes (send no messages to others).\n" );
            }
            else
            {
                for( Target target : lstTargets )
                    sbInfo.append( "       * " ).append( target ).append( '\n' );
            }
        }
        else
        {
            sbInfo.append( "    This ExEn does not belong to a Grid.\n" );
        }

        return sbInfo.toString();
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    void broadcast( ExEnComm message )
    {
        if( lstTargets != null )
        {
            UtilSys.execute( null,
                             () ->
                                {
                                    String msg = message.toString();             // Saves CPU in the forEach(...) loop

                                    lstTargets.forEach( t -> t.send( msg ) );    // forEach(...) is thread-safe when applied to a List that is not modified concurrently
                                } );
        }
    }

    synchronized void start()
    {
        assert lstTargets == null;

        List<GridNode> lstNodes = config.getGridNodes();    // Can be empty: it denotes a Grid mute Node (receives messages from others ExEns but send no message to other ExEns).

        if( (lstNodes != null) && validate( lstNodes ) )
        {
            HashSet<Target> lstTmp = new HashSet<>();

            for( GridNode node : lstNodes )
            {
                for( String sConfigAsJSON : node.targets )
                    lstTmp.add( new Target( sConfigAsJSON, node.client, node.URIs ) );
            }

            if( lstTmp.isEmpty() && config.isGridDeaf() )
            {
                error( "Node is mute (can not send messages to other nodes) and is deaf (can not receive messages from other nodes): this node is useless." );
            }

            // Every N secs the Set is traversed and for every Target if( netClient == null ), then an INetClient
            // to its remote ExEn will intended to be created. It can be that the remote ExEn is avilable or not
            // at the the moment (it could be that is not available now but it could be available later).
            //
            // When there is an error in the connection (probably the remote ExEn stopped), the client is closed
            // and fromURI to null, so next iteration of executor will attempt to create it again.

            if( ! lstTmp.isEmpty() )
            {
                lstTargets = lstTmp;

                int nInterval = config.getGridReconectInterval();

                if( nInterval == -1 ) nInterval = 10 * 1000;
                else                  nInterval = Math.max( 1000, nInterval );     // Not less tha 1 second

                executor = UtilSys.executeWithDelay( null, nInterval, nInterval, () -> lstTargets.forEach( t -> t.connect() ) );
            }
        }
    }

    synchronized void stop()
    {
        if( executor != null )
            executor.cancel( true );   // true = may interrupt if running

        if( lstTargets != null )
            lstTargets.forEach( target -> target.disconnect() );

        lstTargets = null;
        executor   = null;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private boolean validate( List<GridNode> list )    // Method is not needed to be synchronized because it is invoked only from constructor
    {
        for( GridNode node : list )
        {
            if( UtilColls.isEmpty( node.targets ) )
                return error( "'targets' array is empty" );

            if( UtilStr.isEmpty( node.client) )
                return error( "'client' is empty" );

            if( UtilColls.isEmpty( node.URIs ) )
                return error( "'uris' array is empty" );

            for( String sConfigAsJSON : node.targets )
            {
                if( sConfigAsJSON == null )
                    return error( "Empty target" );
            }
        }

        return true;   // _Apparently_ members are properly configured: later they can be created
    }

    private boolean error( String sCause )
    {
        if( lstTargets != null )
            lstTargets.clear();

        UtilSys.getLogger().log( ILogger.Level.SEVERE, "Grid became useless.\nCause: "+ sCause );

        return false;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //
    // Having following members definition:
    //
    // "members": [
    //              {
    //                  "nodes" : [{ "host": "192.168.7.3:55886", "ssl": false, "timeout": 5 },
    //                             { "host": "192.168.7.2:55885", "ssl": false, "timeout": 5 }],
    //                  "client": "com.peyrona.mingle.network.socket.SocketClient",
    //                  "uris"  : ["file://{*home.lib*}network.jar"]
    //              }
    //           ]
    //
    // We will obtain 2 instances of Target class, one per each item in the "targets" array
    //------------------------------------------------------------------------//
    private final class Target
    {
        private final String     sConnConf;    // Can be a JSON, a simple string or wahtever: depends on what is expecting INetClient:connect(...)
        private final String     sClientLib;
        private final String[]   asURI;
        private       INetClient netClient;
        private       boolean    isConnecting = false;

        Target( String conf, String client, String[] URIs )
        {
            sConnConf  = conf;
            sClientLib = client;
            asURI      = URIs;
            netClient  = null;
        }

        synchronized void connect()
        {
            if( isConnecting )
                return;            // Avoids reentrance (conneciton timeout can be bigger than the time between two consecutives iterations of ::executor)

            if( (netClient != null) && netClient.isConnected() )
                return;     // Nothing to do: there is already a connection to this Target

            isConnecting = true;

            try
            {
                netClient = UtilReflect.newInstance( INetClient.class, sClientLib, asURI );
                netClient.connect( sConnConf );
            }
            catch( ClassNotFoundException | InstantiationException | NoSuchMethodException | IllegalAccessException |
                   URISyntaxException | IOException | IllegalArgumentException | InvocationTargetException exc )
            {
                netClient = null;     // Just to be sure
            }

            isConnecting = false;
        }

        void disconnect()
        {
            if( netClient != null )
            {
                netClient.disconnect();
                netClient = null;         // Atomic
            }
        }

        void send( String msg )
        {
            if( (netClient != null) && (netClient.isConnected()) )
                netClient.send( msg );
        }

        @Override
        public String toString()
        {
            return "Connect to: "+ sConnConf +", using: "+ sClientLib;
        }
    }
}