
package com.peyrona.mingle.network.socket;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.network.BaseClient4IP;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

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
public final class SocketClient
             extends BaseClient4IP
{
    private static final int  MAX_RETRIES  = 3;
    private static final long RETRY_DELAY  = 1000L;

    private volatile Socket          client = null;
    private volatile BufferedReader  input  = null;
    private volatile PrintWriter     output = null;
    private volatile ScheduledFuture future   = null;
    private volatile AtomicBoolean   isStopping = new AtomicBoolean( false );

    //------------------------------------------------------------------------//

    /**
     * Connects to server using received JSON string configuration.
     *
     * @param sCfgAsJSON
     * @return Itself
     */
    @Override
    public synchronized INetClient connect( String sCfgAsJSON )
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
                socket.setTcpNoDelay( true );           // Disable Nagle's algorithm
                socket.connect( addr, getTimeout() );   // Throws an IOException if timeout: connection is blocked until connected or timeout
             // socket.setSoTimeout( READ_TIMEOUT );    -> Can not use this because a client can be days without requesting inf. from server

                if( connect( socket ) )
                    return this;
            }
            catch( IOException ioe )
            {
                if( attempt == MAX_RETRIES - 1 )
                {
                    sendError( ioe );
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

        return this;
    }

    @Override
    public synchronized INetClient disconnect()
    {
        isStopping.set( true );
        cleanupResources();
        isStopping.set( false );
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
    public synchronized boolean isConnected()
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
        if( isStopping.get() )
            return false;

        try
        {
            client = socket;
            input  = new BufferedReader( new InputStreamReader( client.getInputStream(), StandardCharsets.UTF_8 ) );
            output = new PrintWriter( client.getOutputStream(), true );
            future = UtilSys.execute( "network:socketclient-reader", new ThreadReader() );

            forEachListener( l -> ((INetClient.IListener) l).onConnected( SocketClient.this ) );
            return true;
        }
        catch( IOException ioe )
        {
            cleanupResources();
            sendError( ioe );
            return false;
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    private void cleanupResources()    // Invoked from a synchronized method
    {
        // Close streams before socket to prevent resource leaks
        if( input != null )
        {
            try
            {
                input.close();
            }
            catch( IOException e )
            {
                log( "Error closing input stream: ", e );
            }
        }

        if( output != null )
        {
            try
            {
                output.close();
            }
            catch( Exception e )
            {
                log( "Error closing output stream: ", e );
            }
        }

        // Shutdown executor service first
        if( future != null )
            future.cancel( true );

        // Close socket last
        if( client != null )
        {
            try
            {
                client.close();
            }
            catch( IOException ioe )
            {
                log( "Error closing socket: ", ioe );
            }
            finally
            {
                forEachListener(l -> ((INetClient.IListener) l).onDisconnected( SocketClient.this ) );
            }
        }

        // Do not clear listeners: they will be needed if socket is reconnected.

        synchronized( this )
        {
            client = null;
            input  = null;
            output = null;
            future   = null;
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
            Thread.currentThread().setName(SocketClient.class.getSimpleName() +"_Reader-" + client.getRemoteSocketAddress() );

            while( (! isStopping.get()) && isConnected() && (! Thread.currentThread().isInterrupted()) )
            {
                try
                {
                    String message = input.readLine();

                    if( message == null )    // 'message' is null when thread is interrupted or when the communication channel is closed
                    {
                        if( ! Thread.interrupted() && ! isConnected() && ! isStopping.get() )
                            sendError( new MingleException( "Server was closed cleanly" ) );

                        break;
                    }
                    else
                    {
                        forEachListener(l -> l.onMessage(SocketClient.this, message ) );
                    }
                }
                catch( IOException exc )
                {
                    // A "Connection reset" or "Broken pipe" is a common exception when the client abruptly closes the connection.
                    // We don't need to treat it as a server-side error. We'll just break the loop,
                    // and the disconnect() call below will handle the cleanup.

                    boolean isCommonDisconnect = exc instanceof java.net.SocketException &&
                                                 UtilStr.contains( exc.getMessage(), "Connection reset", "Broken pipe" );

                    if( ! isStopping.get() && ! isCommonDisconnect )
                        sendError( exc );

                    break;   // For any IOException (reset or other), the connection is no longer viable, so we break the loop.
                }
                catch( Throwable th )
                {
                    UtilSys.getLogger().log( Level.SEVERE, th );
                }
            }

            if( ! isStopping.get() )
                disconnect();
        }
    }
}