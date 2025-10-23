package com.peyrona.mingle.network.plain;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.network.BaseServer4IP;

/**
 *
 * @author francisco
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