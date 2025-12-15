
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.ParseException;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilType;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
abstract class ServiceBase
{
    protected final HttpServletRequest  request;
    protected final HttpServletResponse response;

    //------------------------------------------------------------------------//
    // PROTECTED SCOPE

    // CONSTRUCTOR
    protected ServiceBase( HttpServletRequest request, HttpServletResponse response )
    {
        this.request  = request;
        this.response = response;
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
        try
        {
            InetAddress addr = InetAddress.getByName( request.getRemoteAddr() );
            return ((addr != null) && addr.isLoopbackAddress());
        }
        catch( Exception e )
        {
            return false;
        }
    }

    // GET REQUEST PARAMETER

    protected boolean hasParam( String name )
    {
        return request.getParameter( name ) != null;
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
        try
        {
            StringBuilder sb      = new StringBuilder();
            String        line;

            try( BufferedReader reader = request.getReader() )
            {
                while( (line = reader.readLine()) != null )
                {
                    sb.append( line );
                }
            }

            String sData = sb.toString();

            if( UtilStr.isEmpty( sData ) )
            {
                sendError( "Empty request body", HttpServletResponse.SC_BAD_REQUEST );
                return;
            }

            try
            {
                JsonObject json = Json.parse( sData ).asObject();
                onSuccess.accept( json );
                sendOK();
            }
            catch( ParseException e )
            {
                sendError( "Invalid JSON format", HttpServletResponse.SC_BAD_REQUEST );
            }
            catch( Exception exc )
            {
                sendError( "Internal server error", HttpServletResponse.SC_BAD_REQUEST );
            }
            catch( Throwable t )
            {
                sendError( "Unexpected error", HttpServletResponse.SC_INTERNAL_SERVER_ERROR );
            }
        }
        catch( IOException ioe )
        {
            sendError( "Failed to read request body", HttpServletResponse.SC_INTERNAL_SERVER_ERROR );
        }
    }

    //------------------------------------------------------------------------//
    // SEND CONTENT TO CLIENT

    protected ServiceBase sendHTML( String str )
    {
        response.setContentType( "text/html" );
        response.setCharacterEncoding( StandardCharsets.UTF_8.name() );

        return send( str, HttpServletResponse.SC_OK );
    }

    protected ServiceBase sendJSON( String str )
    {
        response.setContentType( "application/json" );
        response.setCharacterEncoding( StandardCharsets.UTF_8.name() );

        return send( str, HttpServletResponse.SC_OK );
    }

    protected ServiceBase sendText( String str )
    {
        response.setContentType( "text/plain" );
        response.setCharacterEncoding( StandardCharsets.UTF_8.name() );

        return send( str, HttpServletResponse.SC_OK );
    }

    protected ServiceBase sendOK()
    {
        response.setStatus( HttpServletResponse.SC_OK );

        return this;
    }

    protected ServiceBase sendError( Exception exc )
    {
        return sendError( exc.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR );
    }

    protected ServiceBase sendError( String msg, int code )
    {
        response.setContentType( "text/plain" );
        response.setCharacterEncoding( StandardCharsets.UTF_8.name() );

        return send( msg, code );
    }

    //------------------------------------------------------------------------//
    // PRIVATE

    private String getParam( String name )
    {
        String value = request.getParameter( name );

        if( value != null )
            return value;

        // Check if it's in the query string separately (in case of POST with query params)
        String queryString = request.getQueryString();

        if( queryString != null )
        {
            for( String param : queryString.split( "&" ) )
            {
                String[] pair = param.split( "=", 2 );

                if( pair.length == 2 && pair[0].equals( name ) )
                {
                    try
                    {
                        return java.net.URLDecoder.decode( pair[1], StandardCharsets.UTF_8.name() );
                    }
                    catch( Exception e )
                    {
                        return pair[1];
                    }
                }
            }
        }

        return null;
    }

    private ServiceBase send( String str, int code )
    {
        try
        {
            response.setStatus( code );
            response.getWriter().write( str );
            response.getWriter().flush();
        }
        catch( IOException e )
        {
            // Log but don't throw - response may already be committed
            try
            {
                if( !response.isCommitted() )
                {
                    response.setStatus( HttpServletResponse.SC_INTERNAL_SERVER_ERROR );
                }
            }
            catch( Exception ex )
            {
                // Ignore - already in error state
            }
        }

        return this;
    }
}