
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
        setDeviceName( deviceName );
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed
        setDeviceConfig( deviceInit );   // Store raw config first, validated values will be stored at the end

        // Parse boolean values with proper handling (could be Boolean or String "true"/"false")
        Object  oAppend   = get( KEY_APEND );
        Object  oAutoFeed = get( KEY_AUTO_FEED );
        boolean bAppend   = (oAppend == null)   || Boolean.parseBoolean( oAppend.toString() );
        boolean bAutoFeed = (oAutoFeed == null) || Boolean.parseBoolean( oAutoFeed.toString() );

        set( KEY_APEND    , bAppend );
        set( KEY_AUTO_FEED, bAutoFeed );

        Object oCharset = get( KEY_CHARSET );

        if( oCharset != null )
        {
            try
            {
                set( KEY_CHARSET, Charset.forName( oCharset.toString() ) );
            }
            catch( IllegalCharsetNameException | UnsupportedCharsetException exc )
            {
                sendIsInvalid( exc.getMessage() +"; using default one: "+ Charset.defaultCharset() );
            }
        }

        try
        {
            String sURI = (String) get( "file" );    // This is REQUIRED
            set( "file", sURI );
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
            // Parse interval with proper Number handling
            Object oInterval = get( KEY_INTERVAL );
            long   nInterval = (oInterval != null) ? ((Number) oInterval).longValue() : -1L;

            if( nInterval > -1L )
            {
                nInterval = bLocal ? Math.max(   99L, nInterval )    // Min rate for Local  file
                                   : Math.max( 3000L, nInterval );   // Min rate for Remote file
            }

            // Store validated interval
            set( KEY_INTERVAL, nInterval );
        }
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || (! super.start( rt )) )
            return false;

        if( bLocal && ! isDiskWritable( true ) )
            return true;

        file = new java.io.File( uri );

        long interval = (long) get( KEY_INTERVAL );

        if( (future == null) && (interval > -1L) )
            future = UtilSys.executeWithDelay( getClass().getName(), interval, interval, () -> read() );

        return isValid();
    }

    @Override
    public void stop()
    {
        if( future != null )
        {
            future.cancel( true );
            future = null;
        }

        file = null;

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

                sendChanged( value );
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