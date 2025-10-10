
package com.peyrona.mingle.network.plain;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.network.BaseClient4IP;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This class exposes a socket service (port is configurable) that can be used
 * to communicate with the ExEn and is used by the ExEn to communicate back to
 * the clients.
 * <p>
 * It is mainly used to send requests to the ExEn or to retrieve the status of
 * a device.
 * <p>
 * Used protocol is defined by SocketProtocol class.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class PlainSocketClient
             extends BaseClient4IP
{
    private static final int  MAX_RETRIES  = 3;
    private static final long RETRY_DELAY  = 1000L;

    private volatile Socket          client = null;
    private volatile BufferedReader  input  = null;
    private volatile PrintWriter     output = null;
    private volatile ExecutorService exec   = null;
    private volatile boolean isShuttingDown = false;
    private final    Object  connectionLock = new Object();

    //------------------------------------------------------------------------//

    /**
     * Connects to server using received JSON string configuration.
     *
     * @param sCfgAsJSON
     * @return Itself
     */
    @Override
    public INetClient connect( String sCfgAsJSON )
    {
        synchronized( connectionLock )
        {
            if( isConnected() )
                return this;

            if( ! init( sCfgAsJSON, UtilComm.MINGLE_DEFAULT_SOCKET_PORT ) )
                return this;

            for( int attempt = 0; attempt < MAX_RETRIES; attempt++ )
            {
                try
                {
                    Socket            socket = new Socket();
                    InetSocketAddress addr   = new InetSocketAddress( getHost(), getPort() );

                    socket.setKeepAlive( true );
                    socket.setTcpNoDelay( true );             // Disable Nagle's algorithm
                    socket.connect( addr, getTimeout() );     // Throws an IOException if timeout: connection is blocked until connected or timeout
                 // socket.setSoTimeout( READ_TIMEOUT );      -> Can not use this because a client can be days without requesting inf. from server

                    if( connect( socket ) )
                        return this;
                }
                catch( IOException ioe )
                {
                    if( attempt == MAX_RETRIES - 1 )
                    {
                        forEachListener( l -> ((INetClient.IListener) l).onError( PlainSocketClient.this, ioe ) );
                        break;
                    }

                    try
                    {
                        Thread.sleep( RETRY_DELAY );
                    }
                    catch( InterruptedException ie )
                    {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return this;
    }

    @Override
    public synchronized INetClient disconnect()
    {
        isShuttingDown = true;
        cleanupResources();
        isShuttingDown = false;
        return this;
    }


    @Override
    public synchronized INetClient send( String message )
    {
        if( message != null && output != null )
        {
            output.println( message );
            output.flush();
        }

        return this;
    }

    @Override
    public boolean isConnected()
    {
        return (client != null) && (! client.isClosed());   // client.isConnected() returns always true after being connected, same with .isBound()
    }

    @Override
    public String toString()
    {
        return super.toString() +", RemoteAddress="+ ((client == null) ? "unknown" : client.getRemoteSocketAddress());
    }

    // hashCode() and equals(...)  are defined at super (parent class)

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    /**
     * This method was created by needs of PlainSocketServer.
     * @param socket
     * @return
     */
    protected synchronized boolean connect( Socket socket )
    {
        if( isShuttingDown )
            return false;

        try
        {
            client = socket;
            input  = new BufferedReader( new InputStreamReader( client.getInputStream() ) );
            output = new PrintWriter( client.getOutputStream(), true );
            exec   = Executors.newCachedThreadPool();
            exec.submit( new ThreadReader() );

            forEachListener( l -> ((INetClient.IListener) l).onConnected( PlainSocketClient.this ) );
            return true;
        }
        catch( IOException ioe )
        {
            cleanupResources();
            forEachListener( l -> ((INetClient.IListener) l).onError( PlainSocketClient.this, ioe ) );
            return false;
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void cleanupResources()    // Invoked from a synchronized method
    {
        if( exec != null )
        {
            exec.shutdownNow();

            try
            {
                if( ! exec.awaitTermination( 2, TimeUnit.SECONDS ) )
                    log( "Thread pool did not terminate" );
            }
            catch( InterruptedException e )
            {
                Thread.currentThread().interrupt();
            }
        }

        if( client != null )
        {
            try
            {
                client.close();
            }
            catch( IOException ioe )
            {
                log( "Error closing socket: " + ioe.getMessage() );    // Log error but don't throw
            }

            forEachListener( l -> ((INetClient.IListener) l).onDisconnected( PlainSocketClient.this ) );
        }

        synchronized( this )
        {
            client = null;
            input  = null;
            output = null;
            exec   = null;
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//
    private final class ThreadReader extends Thread
    {
        @Override
        public void run()
        {
            Thread.currentThread().setName( PlainSocketClient.class.getSimpleName() +"_Reader-" + client.getRemoteSocketAddress() );

            while( (! isShuttingDown) && isConnected() && (! Thread.currentThread().isInterrupted()) )
            {
                try
                {
                    String message = input.readLine();

                    if( message == null )    // 'message' is null when thread is interrupted or when the communication channel is closed
                    {
                        if( ! Thread.interrupted() && ! isConnected() && ! isShuttingDown )
                            forEachListener( l -> l.onError( PlainSocketClient.this, new MingleException( "Server was closed cleanly" ) ) );

                        break;
                    }
                    else
                    {
                        forEachListener( l -> l.onMessage(PlainSocketClient.this, message ) );
                    }
                }
                catch( IOException exc )
                {
                    if( ! isShuttingDown )
                        forEachListener( l -> l.onError( PlainSocketClient.this, exc ) );
                }
                catch( Throwable th )
                {
                    UtilSys.getLogger().log( Level.SEVERE, "Cannot suppress a null exception." );
                }
            }

            if( ! isShuttingDown )
                disconnect();
        }
    }
}