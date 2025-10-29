
package com.peyrona.mingle.network;

import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 *
 * @author francisco
 */
public abstract class BaseClient4IP extends BaseClient
{
    private String sHost;
    private int    nPort;
    private int    nTimeout;

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

    public int getTimeout()
    {
        return nTimeout;
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

        final BaseClient4IP other = (BaseClient4IP) obj;

        return getPort() == other.getPort() &&
               Objects.equals( getHost(), other.getHost() );
    }

    @Override
    public String toString()
    {
        String str = getClass().getSimpleName() +" at "+ sHost +":"+ nPort +", Timeout="+ nTimeout;

        return str;
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    protected boolean init( String sCfgAsJSON, int nDefPort )    // Can be in the form: {"host":"localhost:65534", "cert_path":null, "key_path":null, "timeout":"5s"|5000}
    {                                                            // or in the form:     {"host":"localhost", "port": 65534, ... }
        if( isConnected() )
            return false;

        final UtilJson uj   = new UtilJson( (UtilStr.isEmpty( sCfgAsJSON ) ? "{}" : sCfgAsJSON) );
        final int      time = (int) (uj.getInt( "timeout", 0 ) * 1000);    // From seconds to millis

        sHost     = UtilComm.getHost( uj.getString( BaseServer4IP.KEY_HOST, null ) );
        nPort     = uj.getInt( BaseServer4IP.KEY_PORT, -1 );
        nTimeout  = UtilUnit.setBetween( 0, time, Integer.MAX_VALUE );

        if( sHost == null )
        {
            try
            {
                sHost = uj.getString( BaseServer4IP.KEY_HOST, InetAddress.getLocalHost().getHostAddress() );
            }
            catch( UnknownHostException uhe )
            {
                sHost = "localhost";
            }
        }

        if( nPort < 0 )      // Then has to be like this: {"host":"localhost", "port": 65534, ... }
        {
            nPort = UtilComm.getPort( uj.getString( BaseServer4IP.KEY_HOST, null ), -1 );

            if( nPort < 0 )
                nPort = nDefPort;
        }

        if( ! UtilComm.isValidPort( nPort ) )
            throw new IllegalArgumentException( "Invalid port: "+ nPort );

        return true;
    }
}