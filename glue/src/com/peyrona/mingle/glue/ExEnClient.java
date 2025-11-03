
package com.peyrona.mingle.glue;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICmdEncDecLib;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.ExEnComm;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.messages.MsgChangeActuator;
import com.peyrona.mingle.network.NetworkBuilder;
import java.net.ConnectException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Opens, closes and send messages to an ExEn using an INetClient.
 * <p>
 This class holds the communication layer: the INetClient instance that communicates with an ExEn.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class ExEnClient
{
    private final    JsonObject joConnDef;
    private final    String     sConnName;
    private volatile INetClient netClient = null;
    private final    Set<INetClient.IListener> lstPendingListeners = Collections.synchronizedSet( new HashSet<>() );  // Sync is enought because listeners are only added, never removed

    //------------------------------------------------------------------------//
    // CONSTRUCTOR

    public ExEnClient( JsonObject joConnDef, String sConnName )
    {
        if( joConnDef.get("init") == null || ! joConnDef.get("init").isObject() )
            throw new MingleException( "Invalid or not existing 'init'" );

        this.joConnDef = joConnDef;
        this.sConnName = sConnName;
    }

    //------------------------------------------------------------------------//

    public synchronized void connect() throws MingleException
    {
        if( netClient != null && netClient.isConnected() )
            return;

        netClient = NetworkBuilder.buildClient( joConnDef.toString() );

        // Needed prior to connect ------------------------
        netClient.add( new ClientListener() );

        for( INetClient.IListener l : lstPendingListeners )
            netClient.add( l );

        lstPendingListeners.clear();
        // ------------------------------------------------

        netClient.connect( joConnDef.get("init").asObject().toString() );
    }

    public void disconnect()
    {
        if( netClient != null )
        {
            netClient.disconnect();
            netClient = null;
        }
    }

    public void add( INetClient.IListener l )
    {
        if( netClient != null )
            netClient.add( l );
        else
            lstPendingListeners.add( l );
    }

    public void remove( INetClient.IListener l )
    {
        if( netClient != null )
            netClient.remove( l );
        else
            lstPendingListeners.remove( l );
    }

    public ExEnClient sendList()
    {
        if( netClient != null )
        {
            if( netClient.isConnected() )
            {
                netClient.send( new ExEnComm( ExEnComm.Request.List, "true" ).toString() );    // "true" (any not null valid JSON) to force to broadcast all msgs: see ExEnComm class constructor
            }
            else
            {
                JTools.alert( "Client appears as disconnected" );
            }
        }

        return this;
    }

    public ExEnClient sendAdd( ICommand cmd2Add )
    {
        return send( ExEnComm.Request.Add, cmd2Add );
    }

    public ExEnClient sendDel( ICommand cmd2Del )
    {
        return send( ExEnComm.Request.Remove, cmd2Del );
    }

    public ExEnClient sendReplace( ICommand cmdOld, ICommand cmdNew )
    {
        sendDel( cmdOld );
        sendAdd( cmdNew );

        return this;
    }

    public ExEnClient sendClone( ICommand cmd, String newName )
    {
        ICmdEncDecLib builder = UtilSys.getConfig().newCILBuilder();

        JsonObject jo = Json.parse( builder.unbuild( cmd ) ).asObject();
                   jo.set( "name", newName );

        return send( ExEnComm.Request.Add, builder.build( jo.toString() ) );
    }

    public ExEnClient sendChangeActuator( String sActuatorName, Object newValue )
    {
        if( (netClient != null) && netClient.isConnected() )
            netClient.send( new ExEnComm( new MsgChangeActuator( sActuatorName, newValue ) ).toString() );

        return this;
    }

    /**
     * Sends a set of commands in one single ExEnComm message: this method can be
     * used when the commands has to follow certain order (v.g. Add or Del).
     *
     * @param request
     * @param lstCmds
     * @return Itself.
     */
    public ExEnClient sendSetOfCmds( ExEnComm.Request request, List<ICommand> lstCmds )
    {
        if( (netClient != null) && netClient.isConnected() )
        {
            ICommand[] aCmds = lstCmds.toArray( ICommand[]::new );

            netClient.send( new ExEnComm( request, aCmds ).toString() );   // Can not send cmd one by one: all must be send inside one packet
        }

        return this;
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
            hash = 59 * hash + Objects.hashCode( this.netClient );
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if( this == obj )
            return true;

        if( obj == null )
            return false;

        if( getClass() != obj.getClass() )
            return false;

        final ExEnClient other = (ExEnClient) obj;

        return Objects.equals( this.netClient, other.netClient );
    }

    //------------------------------------------------------------------------//

    private ExEnClient send( ExEnComm.Request request, ICommand cmd )
    {
        if( (netClient != null) && netClient.isConnected() )
            netClient.send( new ExEnComm( request, cmd ).toString() );

        return this;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // This listener is in charge of react when connection is started, is ended
    // or an error happens: this is common to all instances of ExEnClient class.
    //------------------------------------------------------------------------//

    private final class ClientListener implements INetClient.IListener
    {
        @Override
        public void onConnected( INetClient origin )
        {
            if( (netClient != null) && (! netClient.isConnected()) )
            {
                JTools.alert( "Error: client shoud be connected but it is not" );
                return;
            }

            ExEnClient.this.sendList();

            UtilSys.getLogger().log( ILogger.Level.INFO, getClass().getSimpleName() +" connected to "+ sConnName );

            JTools.hideWaitFrame();
        }

        @Override
        public void onDisconnected( INetClient origin )
        {
            UtilSys.getLogger().log( ILogger.Level.INFO, getClass().getSimpleName() +" disconnected from "+ sConnName );
            JTools.hideWaitFrame();     // No harm if it was already closed
        }

        @Override
        public void onError( INetClient origin, Exception exc )
        {
            JTools.hideWaitFrame();     // No harm if it was already closed

            if( exc != null)
            {
                String msg = (exc.getClass().equals( ConnectException.class ) ? "It looks like there is no ExEn running at specified URL and Port.\nPlease check it and try again."
                                                                              : "Error connecting: "+ exc.getMessage());
                JTools.alert( msg );
            }

            UtilSys.getLogger().log( ILogger.Level.WARNING, exc );
        }

        @Override
        public void onMessage( INetClient origin, String sJSON )
        {
            // Nothing to do
        }
    }
}