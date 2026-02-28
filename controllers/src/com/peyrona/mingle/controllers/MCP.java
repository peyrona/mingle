package com.peyrona.mingle.controllers;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.IController;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.commands.ICommand;
import com.peyrona.mingle.lang.interfaces.commands.IDevice;
import com.peyrona.mingle.lang.interfaces.exen.IRuntime;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.xpreval.functions.list;
import com.peyrona.mingle.lang.xpreval.functions.pair;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * MCP (Model Context Protocol) Controller for LLM communication.
 * <p>
 * This controller provides connectivity to Large Language Model APIs,
 * supporting OpenAI-compatible endpoints (OpenAI, Anthropic, local models, etc.).
 * <p>
 * Configuration parameters:
 * <ul>
 *   <li>uri: API endpoint URL (required). E.g.: "https://api.openai.com/v1/chat/completions"</li>
 *   <li>api_key: API authentication key (required)</li>
 *   <li>model: Model name to use (required). E.g.: "gpt-4", "claude-3-opus-20240229"</li>
 *   <li>timeout: Request timeout in seconds (optional, default: 60)</li>
 *   <li>max_tokens: Maximum tokens in response (optional, default: 1024)</li>
 *   <li>temperature: Sampling temperature 0.0-2.0 (optional, default: 0.7)</li>
 *   <li>system: System prompt/instructions (optional)</li>
 *   <li>context: Name of a DEVICE whose current value is appended to the system prompt on every request (optional)</li>
 * </ul>
 * <p>
 * Write accepts:
 * <ul>
 *   <li>String: Simple user message</li>
 *   <li>pair: Advanced request with keys: "message", "system" (optional), "temperature" (optional)</li>
 *   <li>list: Conversation history as list of pairs with "role" and "content"</li>
 * </ul>
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public final class MCP
       extends ControllerBase
{
    // Configuration keys
    private static final String KEY_URI         = "uri";         // The value of this key is never null because it is declared as REQUIRED in DRIVER declaration
    private static final String KEY_API_KEY     = "api_key";     // The value of this key is never null because it is declared as REQUIRED in DRIVER declaration
    private static final String KEY_MODEL       = "model";       // The value of this key is never null because it is declared as REQUIRED in DRIVER declaration
    private static final String KEY_TIMEOUT     = "timeout";
    private static final String KEY_MAX_TOKENS  = "max_tokens";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_CONTEXT     = "context";     // A device name

    // Default values
    private static final int    DEFAULT_TIMEOUT     = 60;
    private static final int    DEFAULT_MAX_TOKENS  = 1024;
    private static final float  DEFAULT_TEMPERATURE = 0.7f;

    private HttpClient httpClient = null;

    //------------------------------------------------------------------------//

    @Override
    public void set( String deviceName, Map<String, Object> deviceInit, IController.Listener listener )
    {
        setDeviceName( deviceName );
        setListener( listener );        // Must be at beginning: in case an error happens, Listener is needed
        setDeviceConfig( deviceInit );  // Store raw config first, validated values will be stored at the end

        try
        {
            // Optional parameters with defaults

            Object oTimeout = get( KEY_TIMEOUT );
            int timeout = (oTimeout != null) ? ((Number) oTimeout).intValue() : DEFAULT_TIMEOUT;

            Object oMaxTokens = get( KEY_MAX_TOKENS );
            int maxTokens = (oMaxTokens != null) ? ((Number) oMaxTokens).intValue() : DEFAULT_MAX_TOKENS;

            Object oTemperature = get( KEY_TEMPERATURE );
            float temperature = (oTemperature != null) ? ((Number) oTemperature).floatValue() : DEFAULT_TEMPERATURE;

            // Store validated configuration (overwrites raw values with validated ones)

            set( KEY_TIMEOUT    , Math.max( 1, timeout ) );
            set( KEY_MAX_TOKENS , Math.max( 1, maxTokens ) );
            set( KEY_TEMPERATURE, Math.max( 0f, Math.min( 2f, temperature ) ) );

            String context = (String) get( KEY_CONTEXT );

            if( context != null )
                set( KEY_CONTEXT, context );

            setValid( true );
        }
        catch( Exception exc )
        {
            sendIsInvalid( "Error configuring MCP controller: " + UtilStr.toStringBrief( exc ) );
        }
    }

    @Override
    public boolean start( IRuntime rt )
    {
        if( isInvalid() || (! super.start( rt )) )
            return false;

        try
        {
            set( KEY_URI, new URI( (String) get( KEY_URI ) ) );
        }
        catch( URISyntaxException ex )
        {
            sendIsInvalid( "Invalid URI: "+ get( KEY_URI ) );
        }

        if( get( KEY_CONTEXT ) != null )    // A device name
        {
            ICommand dev = getRuntime().get( (String) get( KEY_CONTEXT ) );

                 if( dev == null )                 sendIsInvalid( "Device '"+ get( KEY_CONTEXT ) +"' not found." );
            else if( ! (dev instanceof IDevice) )  sendIsInvalid( "Device '"+ dev.name() +"' is not a DEVICE, but a "+ dev.getClass().getSimpleName() );
        }

        if( isValid() )
        {
            int timeout = (int) get( KEY_TIMEOUT );

            httpClient = HttpClient.newBuilder()
                                   .connectTimeout( Duration.ofSeconds( timeout ) )
                                   .build();
        }

        return isValid();
    }

    @Override
    public void stop()
    {
        httpClient = null;

        super.stop();
    }

    @Override
    public void read()
    {
        // LLM APIs are request/response based, not readable on demand
        // The response is sent via sendChanged() after write()
        sendReaded( "" );
    }

    /**
     * Sends a message to the LLM and receives a response.
     * <p>
     * Accepts:
     * <ul>
     *   <li>String: Simple user message</li>
     *   <li>pair: With keys "message", optionally "system", "temperature"</li>
     *   <li>list: Conversation history as list of pairs with "role" and "content"</li>
     * </ul>
     *
     * @param request The message or conversation to send
     */
    @Override
    public void write( Object request )
    {
        if( isFaked() || isInvalid() || httpClient == null )
            return;

        try
        {
            String jsonBody = buildRequestBody( request );
            sendRequest( jsonBody );
        }
        catch( Exception exc )
        {
            sendWriteError( request, exc );
        }
    }

    //------------------------------------------------------------------------//
    // PRIVATE SCOPE

    /**
     * Builds the JSON request body for the LLM API.
     */
    private String buildRequestBody( Object request ) throws MingleException
    {
        String model        = (String) get( KEY_MODEL );
        int    maxTokens    = (int) get( KEY_MAX_TOKENS );
        float  temperature  = (float) get( KEY_TEMPERATURE );
        String systemPrompt = null;

        JsonObject root = new JsonObject();
                   root.add( "model"      , model );
                   root.add( "max_tokens" , maxTokens );
                   root.add( "temperature", temperature );

        JsonArray messages = new JsonArray();

        // Append context device value to the system prompt when configured
        String contextDeviceName = (String) get( KEY_CONTEXT );

        if( UtilStr.isNotEmpty( contextDeviceName ) )
        {
            Object value = ((IDevice) getRuntime().get( contextDeviceName )).value();

            if( UtilStr.isNotEmpty( value ) )
                systemPrompt = value.toString();
        }

        if( UtilStr.isNotEmpty( systemPrompt ) )
            messages.add( new JsonObject().add( "role", "system" ).add( "content", systemPrompt ) );

        // Process request based on type
        if( request instanceof String )       // Simple String
        {
            messages.add( new JsonObject().add( "role", "user" ).add( "content", (String) request ) );
        }
        else if( request instanceof pair )    // Pair with message and optional parameters
        {
            pair   pReq    = (pair) request;
            Object message = pReq.get( "message" );

            if( message == null )
                throw new MingleException( "Missing 'message' key in pair" );

            messages.add( new JsonObject().add( "role", "user" ).add( "content", message.toString() ) );
        }
        else if( request instanceof list )    // List of messages (conversation history)
        {
            list msgs = (list) request;

            for( int n = 0; n < msgs.size(); n++ )
            {
                Object item = msgs.get( n );

                if( ! (item instanceof pair) )
                    throw new MingleException( "Conversation history must contain pairs with 'role' and 'content'" );

                pair   msg     = (pair) item;
                Object role    = msg.get( "role" );
                Object content = msg.get( "content" );

                if( role == null || content == null )
                    throw new MingleException( "Each message must have 'role' and 'content'" );

                messages.add( new JsonObject().add( "role", role.toString() ).add( "content", content.toString() ) );
            }
        }
        else
        {
            throw new MingleException( "Invalid request type. Expected String, pair, or list." );
        }

        root.add( "messages", messages );

        return root.toString();
    }

    /**
     * Sends the HTTP request to the LLM API asynchronously.
     */
    private void sendRequest( String jsonBody )
    {
        URI    uri    = (URI) get( KEY_URI );
        String apiKey = (String) get( KEY_API_KEY );
        int    timeout = (int) get( KEY_TIMEOUT );

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( uri )
                                         .timeout( Duration.ofSeconds( timeout ) )
                                         .header( "Content-Type", "application/json" )
                                         .header( "Authorization", "Bearer " + apiKey )
                                         .POST( HttpRequest.BodyPublishers.ofString( jsonBody ) )
                                         .build();

        httpClient.sendAsync( request, HttpResponse.BodyHandlers.ofString() )
                  .thenApply( HttpResponse::body )
                  .thenAccept( this::handleResponse )
                  .exceptionally( ex ->
                                    {
                                        sendGenericError( ILogger.Level.SEVERE, "LLM API error: " + ex.getMessage() );
                                        return null;
                                    } );
    }

    /**
     * Handles the response from the LLM API.
     * Extracts the content from OpenAI-compatible response format.
     */
    private void handleResponse( String responseBody )
    {
        try
        {
            // Parse response - looking for content in OpenAI format:
            // {"choices":[{"message":{"content":"..."}}]}
            String content = extractContent( responseBody );

            pair result = new pair();
                 result.put( "response", content );
                 result.put( "raw", responseBody );

            sendChanged( result );
        }
        catch( Exception exc )
        {
            sendGenericError( ILogger.Level.WARNING,
                              "Error parsing LLM response: " + exc.getMessage() + "\nRaw: " + responseBody );

            // Still send the raw response
            pair result = new pair();
                 result.put( "raw", responseBody );
                 result.put( "error", exc.getMessage() );

            sendChanged( result );
        }
    }

    /**
     * Extracts content from OpenAI-compatible API response.
     * Supports both OpenAI and Anthropic response formats.
     */
    private String extractContent( String json )
    {
        try
        {
            JsonObject root = Json.parse( json ).asObject();

            // Try OpenAI format first: {"choices":[{"message":{"content":"..."}}]}
            JsonValue choices = root.get( "choices" );

            if( choices != null && choices.isArray() && !choices.asArray().isEmpty() )
            {
                JsonObject message = choices.asArray().get( 0 ).asObject().get( "message" ).asObject();
                return message.get( "content" ).asString();
            }

            // Try Anthropic format: {"content":[{"text":"..."}]} or {"content": "..."}
            JsonValue content = root.get( "content" );

            if( content != null )
            {
                if( content.isArray() && !content.asArray().isEmpty() )
                {
                   JsonObject item1 = content.asArray().get( 0 ).asObject();
                   JsonValue  text  = item1.get("text");

                   if( text != null )
                       return text.asString();
                }
                else if( content.isString() )
                {
                   return content.asString();
                }
            }
        }
        catch( Exception e )
        {
            // On parse error, return raw JSON to allow debugging
        }

        // Fallback: return raw response
        return json;
    }
}