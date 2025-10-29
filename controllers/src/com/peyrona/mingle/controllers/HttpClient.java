package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.net.URI;
import java.util.Map;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 */
public final class HttpClient
        extends ControllerBase
{
    private static final String KEY_URI = "uri";

    private static final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();    // Only one instance is needed

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceConf, Listener listener )
    {
        setName( deviceName );       // Must be 1st
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        setValid( true );

        String uri = deviceConf.get( KEY_URI ).toString();

        set( KEY_URI, URI.create( uri ) );
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );
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

        executeRequestAsync( request );
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
                executeRequestAsync( request );
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
    private void executeRequestAsync( java.net.http.HttpRequest request )
    {
        client.sendAsync( request, java.net.http.HttpResponse.BodyHandlers.ofString() )
              .thenApply( java.net.http.HttpResponse::body )
              .thenAccept( this::sendReaded )
              .exceptionally( ex -> { sendReadError( (Exception) ex ); return null; } );
    }
}