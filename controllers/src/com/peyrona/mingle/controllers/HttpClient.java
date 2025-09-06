package com.peyrona.mingle.controllers;

import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.xpreval.functions.list;
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
    private static final java.net.http.HttpClient  client = java.net.http.HttpClient.newHttpClient();    // Only one instance is needed
    private              java.net.http.HttpRequest request;
    private              Map<String, Object>       config;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceConf, Listener listener )
    {
        setName( deviceName );       // Must be 1st
        setListener( listener );     // Must be at begining: in case an error happens, Listener is needed

        config = deviceConf;

        setValid( true );

        if( ! new list( "GET","PUT","POST","DELETE" ).has( method() ) )
            sendIsInvalid( method() +": method not valid." );

        if( headers() != null && headers().isEmpty() )
            sendIsInvalid( headers() +": headers not valid." );
    }

    @Override
    public void start( IRuntime rt )
    {
        super.start( rt );

        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                                                                             .uri( uri() );

        switch( method() )
        {
            case "GET"    : builder = builder.GET()   ; break;
//          case "PUT"    : builder = builder.PUT()   ; break;
//          case "POST"   : builder = builder.POST()  ; break;
            case "DELETE" : builder = builder.DELETE(); break;
        }

        if( headers() != null )
        {
            pair headers = headers();

            for( Object key : headers.keys() )
                builder.header( key.toString(), headers.get( key ).toString() );
        }

        request = builder.build();
    }

    @Override
    public void read()
    {
        if( isInvalid() )
            return;

        client.sendAsync( request, java.net.http.HttpResponse.BodyHandlers.ofString() )
           .thenApply( java.net.http.HttpResponse::body )
           .thenAccept(this::sendReaded );
    }

    @Override
    public void write( Object newValue )
    {
        if( isInvalid() )
            return;
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * "url" is granted to exist because it is REQUIRED and therefore the Transpiler checks it
     * @return
     */
    private URI uri()
    {
        return URI.create( config.get( "uri" ).toString() );
    }

    /**
     * "method" is granted to exist because it is REQUIRED and therefore the Transpiler checks it
     * @return
     */
    private String method()
    {
        return config.getOrDefault( "method", "GET" )
                     .toString()
                     .toUpperCase();
    }

    public pair headers()
    {// TODO: para que las headers funcione hay que hacer que el value de un DRIVER permita instancias de mi clase "pair"
//        if( conf.containsKey( "headers" ) )
//        {
//            try
//            {
//                return new pair( conf.get( "headers" ).toString() );
//            }
//            catch( MingleException me )
//            {
//                return new pair();
//            }
//        }

        return null;
    }
}