
package com.peyrona.mingle.network;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.io.File;
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
    public static String KEY_HOST      = "host";
    public static String KEY_PORT      = "port";
    public static String KEY_CERT_FILE = "certFile";
    public static String KEY_KEY_FILE  = "keyFile";
    public static String KEY_PASSWORD  = "password";

    //------------------------------------------------------------------------//

    private       String        sHost;
    private       int           nPort;
    private       int           nTimeout;
    private       String        sKeyPath;
    private       short         nAllow;
    private       File          fCert;    // File containing SSL certificate
    private       File          fKey;     // File containing SSL key
    private       char[]        acPass;   // SSL Certificate password
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

    public File getSSLCert()
    {
        return fCert;
    }

    public File getSSLKey()
    {
        return fKey;
    }

    public char[] getSSLPassword()
    {
        return acPass;
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

        sHost  = UtilComm.getHost( uj.getString( KEY_HOST, null ) );
        nPort  = uj.getInt( KEY_PORT, -1 );
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
            nPort = UtilComm.getPort( sHost, -1 );

            if( nPort < 0 )
                nPort = nDefPort;
        }

        if( ! UtilComm.isValidPort( nPort ) )
            throw new IllegalArgumentException( "Invalid port: "+ nPort );

        // SSL related --------------------------------------------
        String sCertFile  = uj.getAsString( KEY_CERT_FILE, null );
        String sKeyFile   = uj.getAsString( KEY_KEY_FILE , null );
        String sPassword  = uj.getAsString( KEY_PASSWORD , null );

        fCert  = (sCertFile == null) ? null : new File( sCertFile );
        fKey   = (sKeyFile  == null) ? null : new File( sKeyFile  );
        acPass = (sPassword == null) ? null : sPassword.toCharArray();

        // Only validate SSL files if they are provided
        if( fCert != null || fKey != null )
        {
            if( UtilIO.canRead( fCert ) == null )    // null == fCert exists and can be read
            {                                        // fCert exists and can be read: lets now check fKey
                if( UtilIO.canRead( fKey ) != null )
                    throw new MingleException( "SSL can not be activated: "+ UtilIO.canRead( fKey ) );
            }
            else
            {
                throw new MingleException( "SSL can not be activated: "+ UtilIO.canRead( fCert ) );
            }
        }
        // --------------------------------------------------------

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