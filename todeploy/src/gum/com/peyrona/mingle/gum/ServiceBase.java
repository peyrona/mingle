
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.ParseException;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.function.Consumer;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
abstract class ServiceBase
{
    protected final HttpServerExchange xchg;

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    // CONSTRUCTOR
    protected ServiceBase( HttpServerExchange xchg )
    {
        this.xchg = xchg;
    }

    protected void dispatch( String method )
    {
        try
        {
            switch( method )
            {
                case "GET"   : doGet();    break;
                case "POST"  : doPost();   break;
                case "PUT"   : doPut();    break;
                case "DELETE": doDelete(); break;
                default      : sendError( new IllegalArgumentException( method +": method not yet implemented" ) );
            }
        }
        catch( Exception exc )
        {
            sendError( exc );
        }
    }

    protected void doGet()     throws Exception {}    // Returns one or more entities
    protected void doPost()    throws Exception {}    // Updates an existing entity
    protected void doPut()     throws Exception {}    // Adds a new (not existing previously) entity
    protected void doDelete()  throws Exception {}    // Deletes an existing entity

    protected boolean isFromLocalhost()
    {
        InetAddress addr = xchg.getSourceAddress().getAddress();

        return ((addr != null) && addr.isLoopbackAddress());
    }

    // GET REQUEST PARAMETER

    protected boolean hasParam( String name )
    {
        return xchg.getQueryParameters().get( name ) != null;
    }

    /**
     * Returns value for passed parameter as a String.<br>
     * Because default value is not an option, it means that the key must exists.
     *
     * @param name Parameter name which value has to be retrieved.
     * @return The value for the parameter.
     */
    protected String asString( String name )
    {
        String value = getParam( name );

        if( value == null )
            throw new MingleException( "Invalid request: '"+ name +"' not received" );

        return value;
    }

    /**
     * Returns value for passed parameter as a String.
     *
     * @param name Parameter name which value has to be retrieved.
     * @param defValue Value to be returned if param does not exist (di not came with the request).
     * @return The value for the parameter.
     */
    protected String asString( String name, String defValue )
    {
        String value = getParam( name );

        return ((value == null) ? defValue : value.trim());
    }

    /**
     * Returns value for passed parameter as a Long.<br>
     * Because default value is not an option, it means that the key must exists.
     *
     * @param name Parameter name which value has to be retrieved.
     * @return The value for the parameter.
     */
    protected Long asLong( String name )
    {
        String value = getParam( name );

        if( value == null )
            throw new MingleException( "Invalid request: '"+ name +"' not received" );

        try
        {   // Most probably this will work and it is faster than checking if paramValue is a valid number
            return UtilType.toLong( value );
        }
        catch( NumberFormatException nfe )
        {
            throw new MingleException( "Invalid request: '"+ name +"' is not a number" );
        }
    }

    /**
     * Returns value for passed parameter as a Long.
     *
     * @param name Parameter name which value has to be retrieved.
     * @param defValue Value to be returned if param does not exist (di not came with the request).
     * @return The value for the parameter.
     */
    protected Long asLong( String name, long defValue )
    {
        String value = getParam( name );

        try
        {   // Most probably this will work and it is faster than checking if paramValue is a valid number
            return (UtilStr.isNotEmpty( value ) ? UtilType.toLong( value ) : defValue);
        }
        catch( NumberFormatException nfe )
        {
            return defValue;
        }
    }

    /**
     * After parsing as JSON received data, executes 'onSuccess' passing the JSON.
     */
    protected void asJSON( final Consumer<JsonObject> onSuccess )
    {
        xchg.getRequestReceiver().receiveFullBytes((reqEx, data) ->
            {
                try
                {
                    String sData = new String( data, StandardCharsets.UTF_8 );   // ALWAYS specify charset for byte[] to String conversion
                    onSuccess.accept( Json.parse( sData ).asObject() );
                    sendOK();
                }
                catch( ParseException e )
                {
                    sendError( "Invalid JSON format", StatusCodes.BAD_REQUEST );
                }
                catch( Exception exc )
                {
                    sendError("Internal server error", StatusCodes.BAD_REQUEST );
                }
            } );
    }

    //------------------------------------------------------------------------//
    // SEND CONTENT TO CLIENT

    protected ServiceBase sendHTML( String str )
    {
        xchg.getResponseHeaders()
            .put( HttpString.tryFromString( "Content-Type" ), "text/html" );

        return send( str, StatusCodes.OK );
    }

    protected ServiceBase sendJSON( String str )
    {
        xchg.getResponseHeaders()
            .put( HttpString.tryFromString( "Content-Type" ), "application/json" );

        return send( str, StatusCodes.OK );
    }

    protected ServiceBase sendText( String str )
    {
        xchg.getResponseHeaders()
            .put( HttpString.tryFromString( "Content-Type" ), "text/plain" );

        return send( str, StatusCodes.OK );
    }

    protected ServiceBase sendOK()
    {
        xchg.setStatusCode( StatusCodes.OK );
        xchg.endExchange(); // End the exchange without sending any response body

        return this;
    }

    protected ServiceBase sendError( Exception exc )
    {
        return sendError( exc.getMessage(), StatusCodes.INTERNAL_SERVER_ERROR );
    }

    protected ServiceBase sendError( String msg, int code )
    {
        xchg.getResponseHeaders()
            .put( HttpString.tryFromString( "Content-Type" ), "text/plain" );

        return send( msg, code );
    }

    //------------------------------------------------------------------------//
    // PRIVATE

    String getParam( String name )
    {
        Deque<String> queue = xchg.getQueryParameters().get( name );

        if( (queue != null) && (! queue.isEmpty()) )
            return queue.getFirst();

        // If parameter is not in Query, lets seach inside the 'request body'

        FormDataParser parser = FormParserFactory.builder().build().createParser( xchg );

        if( parser != null )
        {
            try
            {
                FormData.FormValue fv = parser.parseBlocking().getFirst( name );

                if( fv != null )
                    return fv.getValue();
            }
            catch( IOException ex )
            {
                // Nothing to do
            }
        }

        return null;
    }

    private ServiceBase send( String str, int code )
    {
        xchg.setStatusCode( code )
            .getResponseSender()
            .send( str );

        return this;
    }
}