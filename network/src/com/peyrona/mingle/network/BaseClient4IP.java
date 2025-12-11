
package com.peyrona.mingle.network;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilUnit;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 *
 * @author francisco
 */
public abstract class BaseClient4IP extends BaseClient
{
    private String  sHost;
    private int     nPort;
    private int     nTimeout;
    private File    fCert;    // SSL certificate file
    private File    fKey;     // SSL key file
    private char[]  acPass;   // SSL certificate password
    private boolean bSSL = false;

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

    public boolean isSSLEnabled()
    {
        return bSSL;
    }

// Perhaps it is better for this class not implementing these -->
//    @Override
//    public int hashCode()
//    {
//        int hash = 5;
//            hash = 83 * hash + Objects.hashCode( this.sHost );
//            hash = 83 * hash + this.nPort;
//        return hash;
//    }
//
//    @Override
//    public boolean equals(Object obj)
//    {
//        if( this == obj )
//            return true;
//
//        if( obj == null )
//            return false;
//
//        if( getClass() != obj.getClass() )
//            return false;
//
//        final BaseClient4IP other = (BaseClient4IP) obj;
//
//        return getPort() == other.getPort() &&
//               Objects.equals( getHost(), other.getHost() );
//    }

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

        boolean ssl = uj.getBoolean( "ssl", false );
        bSSL = ssl;

        if( bSSL )
        {
            String sCertFile = uj.getAsString( "certFile", null );
            String sKeyFile  = uj.getAsString( "keyFile" , null );
            String sPassword = uj.getAsString( "password", null );

            fCert  = (sCertFile == null) ? null : new File( sCertFile );
            fKey   = (sKeyFile == null)  ? null : new File( sKeyFile );
            acPass = (sPassword == null) ? null : sPassword.toCharArray();

            // Only validate SSL files if they are provided -----------
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
        }
        return true;
    }

    protected SSLContext createSSLContext() throws GeneralSecurityException, IOException
    {
        SSLContext sslContext = SSLContext.getInstance( "TLS" );
        KeyStore   keyStore   = KeyStore.getInstance( "PKCS12" );
        char[]     password   = getSSLPassword();

        try( FileInputStream fis = new FileInputStream( getSSLCert() ) )
        {
            keyStore.load( fis, password );
        }

        // Add certificate validation
        keyStore.aliases().asIterator().forEachRemaining( alias ->
        {
            try
            {
                Certificate cert = keyStore.getCertificate( alias );

                if( cert instanceof java.security.cert.X509Certificate )
                     ((java.security.cert.X509Certificate) cert).checkValidity();
            }
            catch( KeyStoreException e )
            {
                throw new IllegalArgumentException( "KeyStore access error for alias: " + alias, e );
            }
            catch( java.security.cert.CertificateExpiredException e )
            {
                throw new SecurityException( "Certificate expired for alias: " + alias, e );
            }
            catch( java.security.cert.CertificateNotYetValidException e )
            {
                throw new SecurityException( "Certificate not yet valid for alias: " + alias, e );
            }
            catch( Exception e )
            {
                throw new SecurityException( "Unexpected error validating certificate: " + alias, e );
            }
        } );

        KeyManagerFactory kmf = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
                          kmf.init( keyStore, password );

        // Use stronger SSL configuration
        sslContext.init( kmf.getKeyManagers(), null, null );

        return sslContext;
    }
}