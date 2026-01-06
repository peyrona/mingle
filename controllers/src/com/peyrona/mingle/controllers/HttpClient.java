package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilSys;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 */
public final class HttpClient
        extends ControllerBase
{
    private static final String KEY_URI  = "uri";       // Mandatory
    private static final String KEY_TIME = "interval";  // In seconds, optional, default = 1

    private static final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();    // Only one instance is needed
    private              ScheduledFuture timer = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, Listener listener )
    {
        setDeviceName( deviceName );       // Must be 1st
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        String uri = deviceInit.get( KEY_URI ).toString();

        try
        {
            int interval = ((Number) deviceInit.getOrDefault( KEY_TIME, 0f )).intValue();

            if( interval < 0 )
                interval = 0;

            if( interval > 0 )
                setBetween( KEY_TIME, 1000, interval, Integer.MAX_VALUE );

            set( KEY_URI, new URI( uri ) );
            setValid( true );
        }
        catch( Exception exc )
        {
            sendIsInvalid( exc );
        }
    }

    @Override
    public void start( IRuntime rt )
    {
        if( isInvalid() )
            return;

        super.start( rt );

        synchronized( this )
        {
            if( (int) get( KEY_TIME ) >= 1000 )
            {
                timer = UtilSys.executeWithDelay( getClass().getName(),
                                                  getLong( KEY_TIME ),     // 'interval' must also be the initial delay
                                                  getLong( KEY_TIME ),
                                                  () -> read() );
            }
        }
    }

    @Override
    public void read()
    {
        if( isInvalid() )
            return;

        java.net.http.HttpRequest request =
        java.net.http.HttpRequest.newBuilder()
                                 .uri( (URI) get( KEY_URI ) )
                                 .GET()
                                 .header( "Accept", "application/json" ) // Add appropriate headers
                                 .build();

        executeRequestAsync( request, false );
    }

    @Override
    public void write( Object newValue )
    {
        if( isInvalid() )
            return;

        try
        {
            pair data = (pair) newValue;    // Can throw a ClassCastException

            String method = (String) data.get( "method" );    // Returns "" if not found
            String body   = (String) data.get( "body"   );    // Returns "" if not found
            java.net.http.HttpRequest request = null;

            switch( method )
            {
                case "PUT":
                    request = java.net.http.HttpRequest.newBuilder()
                                    .uri( (URI) get( KEY_URI ) )
                                    .header( "Content-Type", "application/json" )
                                    .PUT( java.net.http.HttpRequest.BodyPublishers.ofString( body ) )
                                    .build();
                    break;

                case "POST":
                    request = java.net.http.HttpRequest.newBuilder()
                                    .uri( (URI) get( KEY_URI ) )
                                    .header( "Content-Type", "application/json" )
                                    .POST( java.net.http.HttpRequest.BodyPublishers.ofString( body ) )
                                    .build();

                    break;

                case "DELETE":
                    request = java.net.http.HttpRequest.newBuilder()
                                    .uri( (URI) get( KEY_URI ) )
                                    .DELETE()
                                    .build();
                    break;

                default:
                    sendWriteError( method, new MingleException( "Invalid method" ) );
            }

            if( request != null )
                executeRequestAsync( request, true );
        }
        catch( ClassCastException cce )
        {
            sendWriteError( newValue, new MingleException( "Must be an instance of 'pair'" ) );
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
 * Executes an HTTP request asynchronously and handles the response
 * @param request The HttpRequest to execute
 */
    private void executeRequestAsync( java.net.http.HttpRequest request, boolean bChanged )
    {
        if( bChanged )
        {
            client.sendAsync( request, java.net.http.HttpResponse.BodyHandlers.ofString() )
                  .thenApply( java.net.http.HttpResponse::body )
                  .thenAccept( this::sendChanged )
                  .exceptionally( ex -> { sendReadError( (Exception) ex ); return null; } );
        }
        else
        {
            client.sendAsync( request, java.net.http.HttpResponse.BodyHandlers.ofString() )
                  .thenApply( java.net.http.HttpResponse::body )
                  .thenAccept( this::sendReaded )
                  .exceptionally( ex -> { sendReadError( (Exception) ex ); return null; } );
        }
    }
}