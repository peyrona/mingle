package com.peyrona.mingle.network.socket;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
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
        if (impl != null && impl.isRunning()) {
            return this;
        }

        init(sCfgAsJSON, 0); // Port is determined by Plain or SSL server

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

        // Proxy listeners to the implementation
        impl.add(new INetServer.IListener() {
            @Override
            public void onConnected(INetServer server, INetClient client) {
                forEachListener(l -> l.onConnected(SocketServer.this, client));
            }

            @Override
            public void onDisconnected(INetServer server, INetClient client) {
                forEachListener(l -> l.onDisconnected(SocketServer.this, client));
            }

            @Override
            public void onMessage(INetServer server, INetClient client, String msg) {
                forEachListener(l -> l.onMessage(SocketServer.this, client, msg));
            }

            @Override
            public void onError(INetServer server, INetClient client, Exception e) {
                forEachListener(l -> l.onError(SocketServer.this, client, e));
            }
        });

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
        return (impl != null) && impl.hasClients();
    }

    @Override
    public boolean isRunning() {
        return (impl != null) && impl.isRunning();
    }
}