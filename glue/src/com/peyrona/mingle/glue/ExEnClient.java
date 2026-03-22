
package com.peyrona.mingle.glue;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ICmdEncDecLib;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.ExEnComm;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.messages.MsgChangeActuator;
import com.peyrona.mingle.network.NetworkBuilder;
import java.net.ConnectException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Opens, closes and send messages to an ExEn using an INetClient.
 * <p>
 * This class holds the communication layer: the INetClient instance that communicates with an ExEn.
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
    // CONSTRUCTORS

    public ExEnClient( JsonObject joConnDef, String sConnName )
    {
        if( joConnDef.get("init") == null || ! joConnDef.get("init").isObject() )
            throw new MingleException( "Invalid or not existing 'init'" );

        this.joConnDef = joConnDef;
        this.sConnName = sConnName;
    }

    /**
     * Use when Stick runs in the same JVM and a direct IRuntime reference is available.
     *
     * @param runtime   The running Stick instance.
     * @param sConnName Display name for this connection.
     */
    public ExEnClient( IRuntime runtime, String sConnName )
    {
        this.joConnDef = null;
        this.sConnName = sConnName;
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    public synchronized boolean connect() throws MingleException
    {
        if( netClient != null && netClient.isConnected() )
            return true;

        netClient = NetworkBuilder.buildClient( joConnDef.toString() );

        // Latch to wait for connection to be established
        CountDownLatch latch = new CountDownLatch( 1 );

        // Needed prior to connect ------------------------
        netClient.add( new ClientListener( latch ) );

        for( INetClient.IListener l : lstPendingListeners )
            netClient.add( l );

        lstPendingListeners.clear();
        // ------------------------------------------------

        try
        {
            netClient.connect( joConnDef.get("init").asObject().toString() );

            boolean started = latch.await( 10, TimeUnit.SECONDS );   // Wait up to 10 seconds for the client to connect to server

            if( ! started || ! netClient.isConnected() )
                throw new MingleException( "" );
        }
        catch( Exception e )
        {
            throw new MingleException( "Client failed to connect with ExEn", e );
        }

        return true;
    }

    public synchronized void disconnect()
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

    public ExEnClient requestCmdList()
    {
        if( netClient != null && netClient.isConnected() )
            netClient.send( new ExEnComm( ExEnComm.Request.List, "true" ).toString() );    // "true" (any not null valid JSON) to force to broadcast all msgs: see ExEnComm class constructor
        else
            JTools.alert( "Can not send List message: target ExEn does not exists or it is disconnected" );

        return this;
    }

    public ExEnClient sendRequest2Add( ICommand cmd2Add )
    {
        return send( ExEnComm.Request.Add, cmd2Add );
    }

    public ExEnClient sendRequest2Del( ICommand cmd2Del )
    {
        return send( ExEnComm.Request.Remove, cmd2Del );
    }

    public ExEnClient sendRequest2Replace( ICommand cmdOld, ICommand cmdNew )
    {
        sendRequest2Del( cmdOld );
        sendRequest2Add( cmdNew );

        return this;
    }

    public ExEnClient sendRequest2Clone( ICommand cmd, String newName )
    {
        ICmdEncDecLib builder = UtilSys.getConfig().newCILBuilder();

        JsonObject jo = Json.parse( builder.unbuild( cmd ) ).asObject();
                   jo.set( "name", newName );

        return send( ExEnComm.Request.Add, builder.build( jo.toString() ) );
    }

    public ExEnClient sendRequest2ChangeActuator( String sActuatorName, Object newValue )
    {
        if( (netClient != null) && netClient.isConnected() )
            netClient.send( new ExEnComm( new MsgChangeActuator( sActuatorName, newValue ) ).toString() );
        else
            JTools.alert( "Can not send Change-Device message: target ExEn does not exists or it is disconnected" );

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
    public ExEnClient sendSetOfCmds( ExEnComm.Request request, ICommand... aCmds )
    {
        if( (netClient != null) && netClient.isConnected() )
            netClient.send( new ExEnComm( request, aCmds ).toString() );   // Can not send cmd one by one: all must be send inside one packet
        else
            JTools.alert( "Can not send Change-Device message: target ExEn does not exists or it is disconnected" );

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
    public boolean equals( Object obj )
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
    // PRIVATE SCOPE

    private ExEnClient send( ExEnComm.Request request, ICommand cmd )
    {
        if( (netClient != null) && netClient.isConnected() )
            netClient.send( new ExEnComm( request, cmd ).toString() );
        else
            JTools.alert( "Can not send "+ request.name() +" message: target ExEn does not exists or it is disconnected" );

        return this;
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    // This listener is in charge of react when connection is started, is ended
    // or an error happens: this is common to all instances of ExEnClient class.
    //------------------------------------------------------------------------//

    private final class ClientListener implements INetClient.IListener
    {
        private final CountDownLatch latch;

        ClientListener( CountDownLatch latch )
        {
            this.latch = latch;
        }

        @Override
        public void onConnected( INetClient origin )
        {
            latch.countDown();   // Signal that connection is established

            ExEnClient.this.requestCmdList();

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
            latch.countDown();   // Signal to unblock the await (even on error)

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