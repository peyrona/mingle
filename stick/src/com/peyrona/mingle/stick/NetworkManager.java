
package com.peyrona.mingle.stick;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.ExEnComm;
import com.peyrona.mingle.lang.japi.UtilColls;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.network.NetworkBuilder;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This Manager does not extends BaseManager because this is not like other
 * Managers: this one does not manage instances of ICommand, although this
 * class has the suffix "Manager" in its name for consistency.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class NetworkManager
{
    private Set<INetServer> lstServers = null;    // Once added all items (at ::start()), the list is immutable (until ::stop())

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE CONSTRUCTOR

    NetworkManager()
    {
    }

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    @Override
    public String toString()
    {
        StringBuilder sbInfo = new StringBuilder()
                                   .append( "Communication channels:\n" );

        if( UtilColls.isNotEmpty( lstServers ) )
        {
            for( INetServer provider : lstServers )
                sbInfo.append( "    * " ).append( provider.toString() ).append( '\n' );
        }
        else
        {
            sbInfo.append( "    No channels were defined.\n" );
        }

        return sbInfo.toString();
    }

    //------------------------------------------------------------------------//
    // PACKAGE SCOPE

    /**
     * Creates all protocols defined in configuration file.
     */
    synchronized void start( INetServer.IListener listener, String sChannelsJSON )
    {
        if( sChannelsJSON == null )
            return;

        try
        {
            Map<INetServer,String> map = NetworkBuilder.buildAllServers( sChannelsJSON );

            if( ! map.isEmpty() )
            {
                lstServers = new HashSet<>( map.size() );

                for( Map.Entry<INetServer,String> entry : map.entrySet() )
                {
                    entry.getKey().add( listener );
                }

                // Now all server hav been created and have their listener

                for( Map.Entry<INetServer,String> entry : map.entrySet() )
                {
                    try
                    {
                        entry.getKey().start( entry.getValue() );
                        lstServers.add( entry.getKey() );
                    }
                    catch( Exception exc )
                    {
                        // Nothing to do
                    }
                }

                if( lstServers.isEmpty() )    // It is empty when all servers failed at ::start(...)
                    lstServers = null;
            }
        }
        catch( MingleException exc )    // NetworkBuilder only throws MingleException
        {
            UtilSys.getLogger().log( Level.SEVERE, new MingleException( "Error creating Network Server", exc ) );
        }
    }

    synchronized void stop()
    {
        if( lstServers != null )
        {
            lstServers.forEach( ps -> ps.stop() );
            lstServers = null;
        }
    }

    /**
     * Returns true when there is no INetworkServer defined.
     * <p>
 In other words, when there is no way to communicate with this ExEn.
     *
     * @return true when there is no INetServer defined.
     */
    boolean isEmpty()
    {
        return (lstServers == null);
    }

    void broadcast( ExEnComm comm )
    {
        if( lstServers != null )
        {
            String msg = comm.toString();    // Saves CPU in the forEach loop

            UtilSys.execute( getClass().getName(),
                             () -> lstServers.forEach( (INetServer server) ->
                                                       server.broadcast( msg ) ) );
        }
    }
}
