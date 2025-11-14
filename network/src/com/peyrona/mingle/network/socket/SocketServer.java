package com.peyrona.mingle.network.socket;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.network.BaseServer4IP;

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
    private BaseServer4IP impl = null;

    //------------------------------------------------------------------------//

    @Override
    public INetServer start( String sCfgAsJSON )
    {
        if( getSSLCert() == null && getSSLKey() == null )
        {
            impl = new PlainSocketServer();
        }
        else
        {
            if( getSSLCert() == null || getSSLKey() == null )
            {
                String cause = "SSL "+ ((getSSLCert() == null) ? "Certificate" : "Key");
                MingleException me = new MingleException( "SSL Server can not be started because '"+ cause +"' was not provided." );

                log( me );
                throw me;
            }

            impl = new SSLSocketServer();
        }

        if( impl != null )
            impl.start( sCfgAsJSON );

        return this;
    }

    @Override
    public INetServer stop()
    {
        if( impl != null )
            impl.stop();

        return this;
    }

    @Override
    public INetServer broadcast( String message )
    {
        if( impl != null )
            impl.broadcast( message );

        return this;
    }

    @Override
    public boolean hasClients()
    {
        return (impl == null) ? false : impl.hasClients();
    }
}