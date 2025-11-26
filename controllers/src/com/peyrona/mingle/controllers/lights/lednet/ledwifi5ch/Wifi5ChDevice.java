
package com.peyrona.mingle.controllers.lights.lednet.ledwifi5ch;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.japi.UtilType;
import com.peyrona.mingle.lang.japi.UtilUnit;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * http://www.ledenet.com/products/smart-wifi-led-controller-5-channels-control-4a5ch-cwww-rgb-rgbw-rgbww-led-light-timer-music-group-sync-controller/
 *
 * Estos cacharros tienen 5 canales: Red, Green, Blue, ColdWhite y WarmWhite.
 * Una tira LED ocupa los 3 RGB, pero aún quedan los otros 2 para otras 2 tiras
 * monocromas. O bien se pueden conectar 5 tiras monocromas a un solo cacharro.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
final class Wifi5ChDevice
{
    private final static byte[] POWER_ON  = { (byte) 0x71, (byte) 0x23, (byte) 0x0F, (byte) 0xA3 };
    private final static byte[] POWER_OFF = { (byte) 0x71, (byte) 0x24, (byte) 0x0F, (byte) 0xA4 };

 // private final static int    SET_COLOR = 0x31;
 // private final static int    TRUE      = 0xF0;
 // private final static int    FALSE     = 0x0F;

    private final byte[] anCmd = { (byte) 0x31, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                                   (byte) 0x00, (byte) 0x00, (byte) 0xF0, (byte) 0x0F, (byte) 0x00 };

    // Estas son las posiciones de los colores en el array a enviar (see anCmd in ::changeChannel(...))
    private final static int NDX_RED   = 1;
    private final static int NDX_GREEN = 2;
    private final static int NDX_BLUE  = 3;
    private final static int NDX_WARM  = 4;
    private final static int NDX_COLD  = 5;
    private final static int NDX_CHECK = 8;     // CheckSum

    //----------------------------------------------------------------------------//

    // If this device receives too many messages in a row, it gets blocked. That's why I use a dispatcher:
    // this ensures that messages are delivered at a suitable pace so it can process them without being overwhelmed.

    private volatile pair                lastSend = null;     // Since this device doesn't allow reading its state, I store the last one successfully sent.
    private final    BlockingQueue<pair> pending  = new LinkedBlockingQueue<>();
    private final    ScheduledFuture     future;
    private final    Wifi5ChSocket       socket;
    private final    Consumer<Exception> onError;

    //----------------------------------------------------------------------------//

    Wifi5ChDevice( String ip, Consumer<Exception> onErr ) throws IOException
    {
        socket  = new Wifi5ChSocket( ip );
        future  = UtilSys.executeAtRate( getClass().getName(), 0l, 25l, () -> _send_() );   // 25 millis == 50 fps
        onError = onErr;
    }

    //----------------------------------------------------------------------------//

    /**
     * Devuelve el valor actual del actuador.
     *
     * @return El valor actual del actuador.
     */
    pair read()
    {
       return lastSend;
    }

    void write( final pair request )
    {
        pending.offer( request );
    }

    void dispose()
    {
        try
        {
            socket.write( POWER_OFF );
        }
        catch( IOException ex )
        {
            // Nothing to do
        }

        pending.clear();
        socket.close();
    }

    String getIP()
    {
        InetAddress ia = (socket == null ? null : socket.getIP());

        return (ia == null ? "unknown IP" : ia.toString());
    }

    //----------------------------------------------------------------------------//

    /**
     * Este método es llamado desde la clase DispatcherFIFO para despachar el
     * siguiente mensaje.
     * <p>
     * Los mensajes llegan con el delay defindo en DispatcherFIFO. El delay máx
     * admisible es de 40ms (24 fps) q es lo q el ojo humano podría empezar a
     * percibir como sucesos independientes.
     * He comprobado q si se envían muchos mensajes muy rápido, el cacharro se
     * ¿"atasca"? y deja de responder, aunque si le envío un mensaje de OFF lo
     * ejecuta (¿?).
     * Sé q se pueden enviar 255 msgs a intervalos de 30ms y sé q eso mismo a
     * intervalos de 20ms, (¿atascan?) al cacharro.
     *
     * @param dispatcher
     * @param lumina
     */
    private void _send_()
    {
        try
        {
            boolean bSend   = false;
            pair    request = pending.take();    // take() blocks until an item is available

            for( Object key : request.keys() )
            {
                String sKey = key.toString().toLowerCase();

                if( "power".equals( sKey ) )
                {
                    if( request.get(key) instanceof Boolean )  socket.write( ((Boolean) request.get(key) ? POWER_ON : POWER_OFF) );
                    else                                       onError.accept( new MingleException( "'power' needs a boolean value" ) );
                }
                else
                {
                    switch( sKey )
                    {
                        case "red"  : anCmd[NDX_RED  ] = value( request.get( sKey ) ); bSend = true; break;
                        case "green": anCmd[NDX_GREEN] = value( request.get( sKey ) ); bSend = true; break;
                        case "blue" : anCmd[NDX_BLUE ] = value( request.get( sKey ) ); bSend = true; break;
                        case "warm" : anCmd[NDX_WARM ] = value( request.get( sKey ) ); bSend = true; break;
                        case "cold" : anCmd[NDX_COLD ] = value( request.get( sKey ) ); bSend = true; break;
                    }
                }
            }

            if( bSend )
            {
                anCmd[NDX_CHECK] = checkSum( anCmd );
                socket.write( anCmd );
            }

            lastSend = request;    // Atomic: it is a reference
        }
        catch( IOException ioe )
        {
            onError.accept( ioe );
        }
        catch( InterruptedException exc )
        {
            future.cancel( true );
        }
    }

    private byte value( Object oValue ) throws IOException
    {
        try
        {
            int nValue = UtilType.toInteger( oValue );

            return (byte) UtilUnit.percent2abs( nValue, 0, 255 );
        }
        catch( MingleException me )
        {
            throw new IOException( "Number expected, found: '"+ oValue +'\'' );
        }
    }

    private byte checkSum( byte[] bytes )
    {
        int sum = 0;

        for( int n = 0; n <= 7; n++ )     // 7 == todos menos el último item del array
        {
            sum += (bytes[n] & 0xFF);
        }

        return (byte) (sum & 0xFF);
    }

    //----------------------------------------------------------------------------//

//    public static void main( String[] args ) throws IOException, InterruptedException
//    {
//        Consumer<Exception> onErr = (exc) -> System.out.println( exc );
//
//        Wifi5ChDevice w5s = new Wifi5ChDevice( "192.168.7.102", onErr );
//
//        w5s.write( new pair().put( "power", false ) );
//        Thread.sleep( 7000 );
//
//        w5s.write( new pair().put( "power", true ) );
//        Thread.sleep( 1000 );
//
//        for( int n = 0; n < 3; n++ )
//        {
//            w5s.write( new pair().put( "red", 100 ).put( "green", 0 ).put( "blue", 0 ) );
//            Thread.sleep( 1000 );
//
//            w5s.write( new pair().put( "red", 0 ).put( "green", 100 ).put( "blue", 0 ) );
//            Thread.sleep( 1000 );
//
//            w5s.write( new pair().put( "red", 0 ).put( "green", 0 ).put( "blue", 100 ) );
//            Thread.sleep( 1000 );
//        }
//
//        w5s.write( new pair().put( "power", false ) );
//        System.exit( 0 );
//    }
}