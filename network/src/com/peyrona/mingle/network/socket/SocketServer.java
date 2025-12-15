package com.peyrona.mingle.network.socket;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.network.BaseServer4IP;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * The Server functionality is divided into two classes using the Strategy Pattern and Factory Pattern
 * design principles: SocketServer (Factory/Facade): Acts as a public facade that decides which
 * implementation to use (Plain or SSL).
 *
 * @author francisco
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class SocketServer
             extends BaseServer4IP
{
    private final AtomicBoolean isStopping = new AtomicBoolean( false );
    private       BaseServer4IP impl       = null;

    //------------------------------------------------------------------------//

    @Override
    public synchronized INetServer start( String sCfgAsJSON )
    {
        if( isRunning() )
            return this;

        super.start( sCfgAsJSON );

        if( getSSLCert() == null && getSSLKey() == null )
        {
            impl = new PlainSocketServer();
        }
        else
        {
            if( getSSLCert() == null || getSSLKey() == null )
            {
                String          cause = "SSL "+ ((getSSLCert() == null) ? "Certificate" : "Key");
                MingleException me    = new MingleException( "SSL Server can not be started because '"+ cause +"' was not provided." );
                log( me );
                notifyError( (INetClient) null, me );
                throw me;
            }

            impl = new SSLSocketServer();
        }

        // We must transfer all listeners from the facade to the implementation
        forEachListener( listener -> impl.add( listener ) );

        impl.start( sCfgAsJSON );   // Start implementation first, then set running state

        // The 'super.start()' call is not needed here, as the 'impl' handles everything.
        // The isRunning state will be managed by the implementation.
        return this;
    }

    @Override
    public synchronized INetServer stop()
    {
        isStopping.set( true );

        if( impl != null )
            impl.stop();

        isStopping.set( false );
        return this;
    }

    // --- DELEGATED METHODS ---

    @Override
    public int getDefaultPort()
    {
        return impl.getDefaultPort();
    }
    
    @Override
    public boolean isRunning()
    {
        return (impl != null) && impl.isRunning();
    }

    @Override
    public boolean add( INetClient client )
    {
        if( impl != null )
            return impl.add( client );
        return false;
    }

    @Override
    public boolean del( INetClient client )
    {
        if( impl != null )
            return impl.del( client );
        return false;
    }

    @Override
    public boolean hasClients()
    {
        if( impl != null )
            return impl.hasClients();
        return false;
    }

    @Override
    public INetServer broadcast( String message )
    {
        if( impl != null )
            return impl.broadcast( message );
        return this;
    }

    @Override
    public Stream<INetClient> getClients()
    {
        if( impl != null )
            return impl.getClients();

        return super.getClients();
    }

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE
}