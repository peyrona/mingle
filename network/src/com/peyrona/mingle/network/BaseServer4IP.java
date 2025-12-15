
package com.peyrona.mingle.network;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilJson;
import com.peyrona.mingle.lang.japi.UtilStr;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * Base class for IP-based network servers.
 * Extends BaseServer with IP-specific functionality including SSL support and client access control.
 *
 * @author francisco
 */
public abstract class   BaseServer4IP
                extends BaseServer
{
    /** Configuration key for host address. */
    public static final String KEY_HOST      = "host";
    /** Configuration key for port number. */
    public static final String KEY_PORT      = "port";
    /** Configuration key for SSL certificate file. */
    public static final String KEY_CERT_FILE = "certFile";
    /** Configuration key for SSL key file. */
    public static final String KEY_KEY_FILE  = "keyFile";
    /** Configuration key for SSL password. */
    public static final String KEY_PASSWORD  = "password";

    //------------------------------------------------------------------------

    private String sHost;
    private int    nPort;
    private int    nTimeout;
    private short  nAllow;
    private File   fCert;    // File containing SSL certificate
    private File   fKey;     // File containing SSL key
    private char[] acPwd;    // SSL Certificate password

    private   final ExecutorService serverExec = Executors.newSingleThreadExecutor();
    protected final AtomicBoolean   isStopping = new AtomicBoolean(false);

    //------------------------------------------------------------------------//
    // PUBLIC SCOPE

    /**
     * Gets the default port for this server type.
     * Subclasses must implement this method to provide their default port.
     *
     * @return the default port number
     */
    public abstract int getDefaultPort();

    /**
     * Starts the server with the given configuration.
     * Initializes server settings and handles startup failures.
     *
     * @param sCfgAsJson the server configuration as JSON string
     * @return this server instance for method chaining
     */
    @Override
    public INetServer start( String sCfgAsJson )
    {
        super.start( sCfgAsJson );

        try
        {
            init( sCfgAsJson );
        }
        catch( Exception exc )
        {
            onServerFailedToStart( new MingleException( "Failed to start server: "+ sCfgAsJson, exc ) );
        }

        return this;
    }

    /**
     * Stops the server and cleans up all resources.
     * Shuts down the server executor and performs cleanup.
     *
     * @return this server instance for method chaining
     */
    @Override
    public INetServer stop()
    {
        isStopping.set( true );

        try
        {
            if( ! serverExec.awaitTermination( 10, TimeUnit.SECONDS ) )
                serverExec.shutdownNow();

            log( ILogger.Level.INFO, "WebSocket server stopped" );
        }
        catch( InterruptedException ie )
        {
            serverExec.shutdownNow();
            log( ILogger.Level.INFO, "WebSocket server stop interrupted" );
        }
        finally
        {
            super.stop();
            isStopping.set( false );
        }

        return this;
    }

    /**
     * Gets the host address this server is bound to.
     *
     * @return the host address
     */
    public String getHost()
    {
        return sHost;
    }

    /**
     * Gets the port number this server is listening on.
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
        return acPwd;
    }

    /**
     * Generates a hash code for this server based on host and port.
     *
     * @return hash code
     */
    @Override
    public int hashCode()
    {
        int hash = 5;
            hash = 83 * hash + Objects.hashCode( this.sHost );
            hash = 83 * hash + this.nPort;
        return hash;
    }

    /**
     * Compares this server to another object for equality.
     * Two servers are considered equal if they have the same host and port.
     *
     * @param obj the object to compare with
     * @return true if the objects are equal, false otherwise
     */
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

    /**
     * Returns a string representation of this server including host, port, timeout, and access control.
     *
     * @return string representation
     */
    @Override
    public String toString()
    {
        return getClass().getSimpleName() +" at "+ sHost +":"+ nPort +", Timeout="+ nTimeout +", Allow="+ allow2Str();
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * Starts the server task and waits for it to signal readiness.
     * @param serverTask   The logic to run (e.g., the loop accepting connections).
     * @param startupLatch A latch initialized to 1 by the caller. The task MUST count this down.
     * @param startupError A reference to hold any exception thrown during startup.
     * @return this instance for chaining.
     */
    protected BaseServer4IP run( Runnable serverTask, CountDownLatch startupLatch, AtomicReference<Exception> startupError )
    {
        // 1. Wrap the task to sets the thread name dynamically
        // (This replaces serverThread.setName)
        Runnable wrappedTask = () ->
        {
            Thread.currentThread().setName( getClass().getSimpleName() + ":waiting4client" );

            try
            {
                serverTask.run();
            }
            catch( Exception e )
            {
                if( startupLatch.getCount() > 0 )    // Catch runtime exceptions that might escape the task
                {
                    startupError.set( e );
                    startupLatch.countDown();
                }
            }
        };

        try
        {
            // 2. Submit the task to the executor
            serverExec.execute( wrappedTask );

            // 3. Wait for the server to bind the port (Blocking wait)
            // Adjust timeout (e.g., 5 seconds) as needed for your environment.
            boolean started = startupLatch.await( 5, TimeUnit.SECONDS );

            // 4. Check for startup errors
            Exception error = startupError.get();

            if( error != null )
                throw error;

            // 5. Check for timeout
            if( ! started )
                throw new MingleException( "Server failed to initialize within timeout period" );
        }
        catch( Exception exc )
        {
            onServerFailedToStart( exc );

            throw new MingleException( "Server startup failed", exc );
        }

        return this;
    }

    /**
     * Checks if a client address is allowed to connect to this server.
     *
     * @param addr the client address to check
     * @return true if the address is allowed, false otherwise
     * @throws SocketException if there's an error checking the address
     */
    protected boolean isAllowed( InetAddress addr ) throws SocketException
    {
        return UtilComm.isCLientAllowed( nAllow, addr );
    }

    /**
     * Creates an SSL context for secure server connections.
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

    /**
     * Handles server startup failures.
     * Creates appropriate error messages based on exception type and notifies listeners.
     *
     * @param exc the exception that caused the startup failure
     * @throws MingleException always throws to propagate the failure
     */
    protected void onServerFailedToStart( Exception exc )
    {
        String msg = "Failed to start server at "+ getClass().getSimpleName();

        if( exc instanceof BindException )
            msg += ": apparently the port "+ getPort() +" is already in use.";

        MingleException me = new MingleException( msg, exc );

        notifyError( (INetClient) null, me );
        log( me );
        stop();
        throw me;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private String allow2Str()
    {
        if( nAllow == UtilComm.ALLOW_IP_LOCAL )
            return sHost != null ? sHost : "unknown";

        if( nAllow == UtilComm.ALLOW_IP_ANY )
            return "All";

        if( sHost == null || UtilStr.countChar( sHost, '.' ) != 3 )
            return "Intranet";

        String[] groups = sHost.split( "\\." );

        if( groups.length < 2 )   // Additional safety check
            return "Intranet";

        return groups[0] +'.'+ groups[1] +".*.*";
    }

    private void init( String sCfgAsJSON )    // Can be in the form: {"host":"localhost:65534", "cert_path":null, "key_path":null, "timeout":"5s"|5000}
            throws UnknownHostException       // or in the form:     {"host":"localhost", "port": 65534, ... }
    {
        // Can not do this --> if( isRunning() ) return;

        final UtilJson uj = new UtilJson( (UtilStr.isEmpty( sCfgAsJSON ) ? "{}" : sCfgAsJSON) );

        sHost  = UtilComm.getHost( uj.getString( KEY_HOST, "localhost" ) );
        nPort  = uj.getInt( KEY_PORT, -1 );                                          // Then has to be like this: {"host":"localhost", "port": 65534, ... }
        nAllow = UtilComm.clientScope( uj.getString( "allow", "intranet" ), null );

        if( nPort < 0 )    // Then has to be like this: {"host":"localhost:65534", ... }
        {
            nPort = UtilComm.getPort( sHost, -1 );

            if( nPort < 0 )
                nPort = getDefaultPort();
        }

        if( ! UtilComm.isValidPort( nPort ) )
            throw new IllegalArgumentException( "Invalid port: "+ nPort );

        // SSL related --------------------------------------------
        String sCertFile  = uj.getAsString( KEY_CERT_FILE, null );
        String sKeyFile   = uj.getAsString( KEY_KEY_FILE , null );
        String sPassword  = uj.getAsString( KEY_PASSWORD , null );

        fCert = (sCertFile == null) ? null : new File( sCertFile );
        fKey  = (sKeyFile  == null) ? null : new File( sKeyFile  );
        acPwd = (sPassword == null) ? null : sPassword.toCharArray();

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
    }
}