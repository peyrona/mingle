
package com.peyrona.mingle.network;

import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author francisco
 */
public abstract class   BaseServer4IP
                extends BaseServer
{
    private       String        sHost;
    private       int           nPort;
    private       int           nTimeout;
    private       String        sKeyPath;
    private       short         nAllow;
    private final AtomicBoolean isRunning = new AtomicBoolean( false );

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    public String getHost()
    {
        return sHost;
    }

    public int getPort()
    {
        return nPort;
    }

    public String getKeyPath()
    {
        return sKeyPath;
    }

    public int getTimeout()
    {
        return nTimeout;
    }

    @Override
    public boolean isRunning()
    {
        return isRunning.get();
    }

    @Override
    public int hashCode()
    {
        int hash = 5;
            hash = 83 * hash + Objects.hashCode( this.sHost );
            hash = 83 * hash + this.nPort;
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

        final BaseServer4IP other = (BaseServer4IP) obj;

        return getPort() == other.getPort() &&
               Objects.equals( getHost(), other.getHost() );
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() +" at "+ sHost +":"+ nPort +", Timeout="+ nTimeout +", Allow="+ allow2Str();
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    protected boolean init( String sCfgAsJSON, int nDefPort )    // Can be in the form: {"host":"localhost:65534", "cert_path":null, "key_path":null, "timeout":"5s"|5000}
    {                                                            // or in the form:     {"host":"localhost", "port": 65534, ... }
        if( isRunning() )
            return false;

        final UtilJson uj = new UtilJson( (UtilStr.isEmpty( sCfgAsJSON ) ? "{}" : sCfgAsJSON) );

        sHost  = UtilComm.getHost( uj.getString( "host", null ) );
        nPort  = uj.getInt( "port", -1 );
        nAllow = UtilComm.clientScope( uj.getString( "allow", "intranet" ), null );

        if( sHost == null )
        {
            try
            {
                sHost = InetAddress.getLocalHost().getHostAddress();

                if( sHost.startsWith( "127.0." ) )
                {
                    try
                    {
                        for( InetAddress ia : UtilComm.getLocalIPs() )
                        {
                            if( UtilStr.countChar( ia.getHostAddress(), '.' ) == 3 )
                            {
                                sHost = ia.getHostAddress();
                                break;
                            }
                        }
                    }
                    catch( SocketException ex )
                    {
                        // Nothing to do
                    }
                }
            }
            catch( UnknownHostException uhe )
            {
                sHost = "localhost";
            }
        }

        if( nPort < 0 )      // Then has to be like this: {"host":"localhost", "port": 65534, ... }
        {
            nPort = UtilComm.getPort( uj.getString( "host", null ), -1 );

            if( nPort < 0 )
                nPort = nDefPort;
        }

        if( ! UtilComm.isValidPort( nPort ) )
            throw new IllegalArgumentException( "Invalid port: "+ nPort );

        return true;
    }

    protected boolean isAllowed( InetAddress addr ) throws SocketException
    {
        return UtilComm.isCLientAllowed( nAllow, addr );
    }

    protected BaseServer4IP setRunning( boolean b )
    {
        isRunning.set( b );

        return this;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private String allow2Str()
    {
        if( nAllow == UtilComm.ALLOW_IP_LOCAL )
            return sHost;

        if( nAllow == UtilComm.ALLOW_IP_ANY )
            return "All";

        if( UtilStr.countChar( sHost, '.' ) != 3 )
            return "Intranet";

        String[] groups = sHost.split( "\\." );

        return groups[0] +'.'+ groups[1] +".*.*";
    }
}