
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
 * Official web site at: <a href="https://mingle.peyrona.com">https://mingle.peyrona.com</a>
 */

public final class File
       extends ControllerBase
{
    private URI               uri  = null;
    private boolean           bAutoNL;
    private boolean           bLocal;            // Local or Remote file
    private boolean           bAppend;
    private java.io.File      file    = null;
    private UtilIO.FileWriter writer  = null;
    private Charset           charset = null;
    private ScheduledFuture   future  = null;
    private int               interval;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        bAppend = (Boolean) deviceInit.getOrDefault( "append"  , Boolean.TRUE );
        bAutoNL = (Boolean) deviceInit.getOrDefault( "autofeed", Boolean.TRUE );

        if( deviceInit.get( "charset" ) != null )
        {
            try
            {
                charset = Charset.forName( (String) deviceInit.get( "charset" ) );
            }
            catch( IllegalCharsetNameException | UnsupportedCharsetException exc )
            {
                sendIsInvalid( exc.getMessage() +"; using default one: "+ Charset.defaultCharset() );
            }
        }

        try
        {
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
            interval = ((Number) deviceInit.getOrDefault( "interval", -1f )).intValue();

            if( interval > -1 )
            {
                int n = bLocal ? Math.max(   99, interval )    // Min rate for Local  file
                               : Math.max( 3000, interval );   // Min rate for Remote file

                interval = setBetween( "interval", n, interval, Integer.MAX_VALUE );
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

        if( (future == null) && (interval > -1) )
            future = UtilSys.executeAtRate( getClass().getName(), interval, interval, () -> read() );
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

            sendReaded( UtilIO.getAsText( uri, charset ) );
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

            if( bAutoNL )
                s += UtilStr.sEoL;

            try
            {
                if( (writer == null) || (! file.exists()) )
                {
                    writer = UtilIO.newFileWriter()
                                   .setFile( file )
                                   .setCharset( charset );
                }

                if( bAppend ) writer.append(  s );
                else          writer.replace( s );

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