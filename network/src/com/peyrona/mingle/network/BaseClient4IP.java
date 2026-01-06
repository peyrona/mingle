
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
 * Base class for IP-based network clients.
 * Extends BaseClient with IP-specific functionality including SSL support.
 *
 * @author francisco
 */
public abstract class BaseClient4IP extends BaseClient
{
    private volatile String  sHost;
    private volatile int     nPort;
    private volatile int     nTimeout;
    private volatile File    fCert;    // SSL certificate file
    private volatile File    fKey;     // SSL key file
    private volatile char[]  acPass;   // SSL certificate password
    private volatile boolean bSSL = false;

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    /**
     * Gets the host address this client is configured to connect to.
     *
     * @return the host address
     */
    public String getHost()
    {
        return sHost;
    }

    /**
     * Gets the port number this client is configured to connect to.
     *
     * @return the port number
     */
    public int getPort()
    {
        return nPort;
    }

    /**
     * Gets the connection timeout in milliseconds.
     *
     * @return the timeout value
     */
    public int getTimeout()
    {
        return nTimeout;
    }

    /**
     * Gets the SSL certificate file for secure connections.
     *
     * @return the SSL certificate file or null if not configured
     */
    public File getSSLCert()
    {
        return fCert;
    }

    /**
     * Gets the SSL private key file for secure connections.
     *
     * @return the SSL key file or null if not configured
     */
    public File getSSLKey()
    {
        return fKey;
    }

    /**
     * Gets the password for the SSL certificate.
     *
     * @return the SSL certificate password or null if not configured
     */
    public char[] getSSLPassword()
    {
        return acPass;
    }

    /**
     * Checks if SSL/TLS is enabled for this client.
     *
     * @return true if SSL is enabled, false otherwise
     */
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

    /**
     * Returns a string representation of this client including host, port, and timeout.
     *
     * @return string representation
     */
    @Override
    public String toString()
    {
        return getClass().getSimpleName() +" at "+ sHost +":"+ nPort +", Timeout="+ nTimeout;
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * Initializes the client configuration from JSON parameters.
     * Can be in the form: {"host":"localhost:65534", "cert_path":null, "key_path":null, "timeout":"5s"|5000}
     * or in the form:     {"host":"localhost", "port": 65534, ... }
     *
     * @param sCfgAsJSON the JSON configuration string
     * @param nDefPort the default port to use if not specified in configuration
     * @return true if initialization was successful, false if client is already connected
     * @throws IllegalArgumentException if port is invalid
     * @throws MingleException if SSL configuration is invalid
     */
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

    /**
     * Creates an SSL context for secure connections.
     * Loads SSL certificate and key, validates certificate expiration, and initializes the SSL context.
     *
     * @return configured SSL context
     * @throws GeneralSecurityException if SSL context creation fails
     * @throws IOException if certificate file cannot be read
     * @throws SecurityException if certificate is expired or invalid
     * @throws IllegalArgumentException if key store access fails
     */
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