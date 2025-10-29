
package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilComm;
import com.peyrona.mingle.lang.japi.UtilIO;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */

public final class File
       extends ControllerBase
{
    private static final String KEY_INTERVAL  = "interval";
    private static final String KEY_APEND     = "append";
    private static final String KEY_AUTO_FEED = "autofeed";
    private static final String KEY_CHARSET   = "charset";

    private URI               uri  = null;
    private boolean           bLocal;            // Local or Remote file
    private java.io.File      file    = null;
    private UtilIO.FileWriter writer  = null;
    private ScheduledFuture   future  = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        set( KEY_APEND    , (Boolean) deviceInit.getOrDefault( KEY_APEND    , Boolean.TRUE ) );
        set( KEY_AUTO_FEED, (Boolean) deviceInit.getOrDefault( KEY_AUTO_FEED, Boolean.TRUE ) );

        if( deviceInit.get( KEY_CHARSET ) != null )
        {
            try
            {
                set( KEY_CHARSET, Charset.forName( (String) deviceInit.get( KEY_CHARSET ) ) );
            }
            catch( IllegalCharsetNameException | UnsupportedCharsetException exc )
            {
                sendIsInvalid( exc.getMessage() +"; using default one: "+ Charset.defaultCharset() );
            }
        }

        try
        {
            set( "file", (String) deviceInit.get( "file" ) );

            String    sURI   = (String) deviceInit.get( "file" );    // This is REQUIRED
            List<URI> lstURI = UtilIO.expandPath( sURI );

            if( lstURI.size() != 1 )
            {
                sendIsInvalid( "Invalid URL: "+ sURI );
            }
            else
            {
                bLocal = UtilComm.getFileProtocol( lstURI.get( 0 ).toString() ) == UtilComm.Protocol.file;
                uri    = lstURI.get( 0 );

                setValid( true );
            }
        }
        catch( IOException | URISyntaxException exc )
        {
            sendIsInvalid( exc );
        }

        if( isValid() )
        {
            set( KEY_INTERVAL, ((Number) deviceInit.getOrDefault( KEY_INTERVAL, -1f )).intValue() );

            if( (int) get( KEY_INTERVAL ) > -1 )
            {
                int n = bLocal ? Math.max(   99, (int) get( KEY_INTERVAL ) )    // Min rate for Local  file
                               : Math.max( 3000, (int) get( KEY_INTERVAL ) );   // Min rate for Remote file

                setBetween( KEY_INTERVAL, n, (int) get( KEY_INTERVAL ), Integer.MAX_VALUE );
            }
        }
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );

        if( bLocal )
        {
            String use_disk = "use_disk";

            if( ! getRuntime().getFromConfig( "exen", use_disk, true ) )
            {
                sendIsInvalid( use_disk +" flag is off: can not use File System" );
                return;
            }
        }

        file = new java.io.File( uri );

        if( (future == null) && ((int) get( KEY_INTERVAL ) > -1) )
            future = UtilSys.executeAtRate( getClass().getName(), (int) get( KEY_INTERVAL ), (int) get( KEY_INTERVAL ), () -> read() );
    }

    @Override
    public void stop()
    {
        if( future != null )
            future.cancel( true );

        file   = null;
        future = null;

        super.stop();
    }

    @Override
    public void read()
    {
        if( isInvalid() )
            return;

        try
        {
            if( bLocal &&    // file is not null when it is a local (not remote) file
                (! file.exists()) )
            {
                return;              // Nothing to do: can not throw an Exception because it does not exist until
            }                        // first values are sent to be written. And user can delete it when he wants

            sendReaded( UtilIO.getAsText( uri, (Charset) get( KEY_CHARSET ) ) );
        }
        catch( IOException ex )
        {
            sendReadError( ex );
        }
    }

    @Override
    public synchronized void write( Object value )
    {
        if( isInvalid() )
            return;

        if( bLocal )
        {
            String s = value.toString();

            if( (Boolean) get( KEY_AUTO_FEED ) )
                s += UtilStr.sEoL;

            try
            {
                if( (writer == null) || (! file.exists()) )
                {
                    writer = UtilIO.newFileWriter()
                                   .setFile( file )
                                   .setCharset( (Charset) get( KEY_CHARSET ) );
                }

                if( (Boolean) get( KEY_APEND ) ) writer.append(  s );
                else                             writer.replace( s );

                sendReaded( value );
            }
            catch( IOException exc )
            {
                sendWriteError( value, exc );
            }
        }
        else
        {
            sendWriteError( value, new IOException( "Can not write a remote file" ) );
        }
    }
}