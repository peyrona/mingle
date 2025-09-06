
package com.peyrona.mingle.controllers.lights.lednet.ledwifi5ch;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;

// Los sockets q tengo en 'network' envían sólo Strings y aquí necesito
// enviar bytes. Podría haber hecho una clase padre en 'network' y heredar
// de ella 2 clases: una para enviar Strings y otra para enviar bytes, o 2
// métodos en la misma clase, o cualquier otra cosa, pero eso me llevaría
// mucho trabajo y no tengo ahora mismo tiempo para ello.
// Por eso he hecho esta clase ad-hoc.

final class Wifi5ChSocket
{
    private       Socket       socket = null;
    private       OutputStream writer = null;
    private       byte[]       abLast = null;
    private final String       ip;

    //----------------------------------------------------------------------------//

    Wifi5ChSocket( String ip ) throws IOException
    {
        this.ip = ip;
    }

    //----------------------------------------------------------------------------//

    void write( byte[] bytes ) throws IOException
    {
        if( bytes == null || bytes.length == 0 )
            throw new IllegalArgumentException( "Value is null or empty" );

        if( Arrays.equals( bytes, abLast ) )
            return;

        try
        {
            createSocket( false );
        }
        catch( IOException ioe )
        {
            try
            {
                createSocket( true );
            }
            catch( IOException e )
            {
                // Nothing to do
            }
        }

        if( socket != null && writer != null )
        {
            writer.write( bytes );
            writer.flush();

            synchronized( this )
            {
                abLast = Arrays.copyOf( bytes, bytes.length );
            }
        }
    }

    synchronized void close()
    {
        try
        {
            if( socket != null )
                socket.close();     // Also closes out and in streams
        }
        catch( IOException ioe )
        {
            // Nothing to do
        }
        finally
        {
            socket = null;
            writer = null;
        }
    }

    InetAddress getIP()
    {
        return (socket == null ? null : socket.getInetAddress());
    }

    //------------------------------------------------------------------------//

    private synchronized void createSocket( boolean bForce ) throws IOException
    {
        if( bForce || (socket == null) )
        {
            close();

            socket = new Socket( ip, 5577 );
            writer = socket.getOutputStream();
        }
    }
}