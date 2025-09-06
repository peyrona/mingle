
package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.*;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket bridge that enables web clients to communicate with ExEn servers.
 *
 * <h3>Usage Contract:</h3>
 * <ul>
 *   <li>One CommBridge instance per Undertow server</li>
 *   <li>Browser opens WebSocket to {@code ws://host/gum/bridge/{exen-hash}} where exen-hash identifies the target ExEn</li>
 *   <li>Multiple ExEn sessions can be multiplexed through a single WebSocket connection</li>
 *   <li>Clients must not rely on {@code wasClean} flag due to browser connection sharing behavior</li>
 *   <li>Automatic cleanup occurs when WebSocket closes or ExEn connections fail</li>
 * </ul>
 *
 * <h3>Architecture:</h3>
 * This class acts as a bidirectional message bridge:
 * <pre>
 * Browser WebSocket ←→ CommBridge ←→ ExEn TCP Connection
 * </pre>
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="https://mingle.peyrona.com">Official website</a>
 */
public final class CommBridge implements WebSocketConnectionCallback
{
    /**
     * Maximum allowed message payload size (64 KiB)
     */
    private static final int MAX_PAYLOAD_SIZE = 64 * 1024;

    /**
     * Interval for cleaning up dead WebSocket references (minutes)
     */
    private static final int CLEANUP_INTERVAL_MINUTES = 5;

    /**
     * Message types supported by the bridge. Used instead of string literals for type safety and performance.
     */
    public enum MessageType
    {
        LIST(    "List" ),
        LISTED(  "Listed" ),
        READ(    "Read" ),
        READED(  "Readed" ),
        CHANGE(  "Change" ),
        CHANGED( "Changed" ),
        EXECUTE( "Execute" ),
        ERROR(   "Error" ),
        ADDED(   "Added" ),
        REMOVED( "Removed" );

        private final String value;

        MessageType( String value )
        {
            this.value = value;
        }

        public String getValue()
        {
            return value;
        }

        public static MessageType fromString( String value )
        {
            for( MessageType type : values() )
            {
                if( type.value.equalsIgnoreCase( value ) )
                    return type;
            }

            return null;
        }
    }

    /**
     * WebSocket close codes for different termination scenarios.
     */
    public enum CloseCode
    {
        NORMAL_CLOSURE(    1000, "Normal closure" ),
        GOING_AWAY(        1001, "Server going away" ),
        PROTOCOL_ERROR(    1002, "Protocol error" ),
        UNSUPPORTED_DATA(  1003, "Unsupported data" ),
        INVALID_PAYLOAD(   1007, "Invalid payload data" ),
        POLICY_VIOLATION(  1008, "Policy violation" ),
        MESSAGE_TOO_LARGE( 1009, "Message too large" ),
        INTERNAL_ERROR(    1011, "Internal server error" );

        private final int code;
        private final String reason;

        CloseCode(int code, String reason)
        {
            this.code = code;
            this.reason = reason;
        }

        public int getCode()
        {
            return code;
        }

        public String getReason()
        {
            return reason;
        }
    }

    // Thread-safe map: WebSocket → List of (ExEn client, ExEn address) pairs
    private static final ConcurrentHashMap<WeakReference<WebSocketChannel>, CopyOnWriteArrayList<Pair<INetClient, JsonObject>>> channelToClients = new ConcurrentHashMap<>();

    // Reverse lookup: ExEn client → (WebSocket, ExEn address)
    private static final ConcurrentHashMap<INetClient, Pair<WeakReference<WebSocketChannel>, JsonObject>> clientToChannel = new ConcurrentHashMap<>();

    // Scheduled cleanup of dead WebSocket references
    private static final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor( r ->
                                                                                                                {
                                                                                                                    Thread t = new Thread( r, "CommBridge-Cleanup" );
                                                                                                                    t.setDaemon( true );
                                                                                                                    return t;
                                                                                                                } );

    static    // Start periodic cleanup of dead WebSocket references
    {
        cleanupExecutor.scheduleWithFixedDelay( CommBridge::cleanupDeadReferences,
                                                CLEANUP_INTERVAL_MINUTES,
                                                CLEANUP_INTERVAL_MINUTES,
                                                TimeUnit.MINUTES );
    }

    @Override
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel session)
    {
        // Extract ExEn address from WebSocket path if provided
        String uri  = exchange.getRequestURI();              // Full URI with query string
        String path = uri.split( "\\?" )[0];                 // Remove query parameters

        if( path != null && path.contains( "/bridge/" ) )
        {
            String exenHash = path.substring( path.lastIndexOf( "/" ) + 1 );

            if( !exenHash.isEmpty() && !exenHash.equals( "bridge" ) )
            {
                // Store ExEn hash in channel attachment for later use
                session.setAttribute( "exenHash", exenHash );
            }
        }

        session.getReceiveSetter().set( new WebSocketListener() );
        session.resumeReceives();

        logInfo( "WebSocket connected: " + session + " (path: " + path + ")" );
    }

    /**
     * Adds a new ExEn client connection for the given WebSocket session.
     */
    private static INetClient addExEnClient(WebSocketChannel session, JsonObject exenAddress)
    {
        try
        {
            INetClient client = UtilSys.getConfig().getHttpServerNetClient();
            if( client == null )
            {
                throw new IllegalStateException( "Failed to create ExEn client" );
            }

            client.add( new ExEnListener() );
            client.connect( exenAddress.toString() );

            // Add to both maps atomically
            WeakReference<WebSocketChannel> sessionRef = new WeakReference<>( session );
            Pair<INetClient, JsonObject> clientPair = new Pair<>( client, exenAddress );

            channelToClients.computeIfAbsent( sessionRef, k -> new CopyOnWriteArrayList<>() ).add( clientPair );
            clientToChannel.put( client, new Pair<>( sessionRef, exenAddress ) );

            logInfo( "Added ExEn client for session " + session + " → " + exenAddress );
            return client;

        }
        catch( IllegalStateException e )
        {
            logError( "Failed to create ExEn client for " + exenAddress, e );
            return null;
        }
    }

    /**
     * Finds an existing ExEn client for the given session and address.
     */
    private static INetClient findExEnClient(WebSocketChannel session, JsonObject exenAddress)
    {
        WeakReference<WebSocketChannel> sessionRef = findSessionReference( session );
        if( sessionRef == null )
        {
            return null;
        }

        CopyOnWriteArrayList<Pair<INetClient, JsonObject>> clients = channelToClients.get( sessionRef );
        if( clients == null )
        {
            return null;
        }

        return clients.stream()
                      .filter( pair -> Objects.equals( pair.getValue(), exenAddress ) )
                      .map( Pair::getKey )
                      .filter( INetClient::isConnected )
                      .findFirst()
                      .orElse( null );
    }

    /**
     * Removes an ExEn client connection.
     */
    private static boolean removeExEnClient(WebSocketChannel channel, INetClient client)
    {
        WeakReference<WebSocketChannel> sessionRef = findSessionReference( channel );
        if( sessionRef == null )
        {
            return false;
        }

        CopyOnWriteArrayList<Pair<INetClient, JsonObject>> clients = channelToClients.get( sessionRef );

        if( clients == null )
            return false;

        boolean removed = clients.removeIf( pair -> pair.getKey().equals( client ) );

        if( removed )
        {
            clientToChannel.remove( client );
            try
            {
                client.disconnect();
            }
            catch( Exception e )
            {
                logWarning( "Error disconnecting ExEn client", e );
            }
        }

        return removed;
    }

    /**
     * Finds the WeakReference for a given WebSocket session.
     */
    private static WeakReference<WebSocketChannel> findSessionReference(WebSocketChannel session)
    {
        return channelToClients.keySet()
                               .stream()
                               .filter( ref -> session.equals( ref.get() ) )
                               .findFirst()
                               .orElse( null );
    }

    /**
     * Centralized error handling that logs, notifies client, and cleans up.
     */
    private static void handleError(WebSocketChannel session, Throwable error, String context)
    {
        logWarning( "WebSocket error in " + context + ": " + session, error );

        // Send sanitized error message to client
        try
        {
            JsonObject errorMessage = new JsonObject()
                    .add( MessageType.ERROR.getValue(), sanitizeErrorMessage( error.getMessage() ) );
            sendToWebSocket( session, errorMessage.toString() );
        }
        catch( Exception e )
        {
            logWarning( "Failed to send error message to client", e );
        }

        // Force cleanup
        cleanupSession( session, CloseCode.INTERNAL_ERROR );
    }

    /**
     * Sanitizes error messages to avoid leaking sensitive information.
     */
    private static String sanitizeErrorMessage(String original)
    {
        if( original == null )
            return "Internal server error";

        // Remove stack traces and file paths
        String sanitized = original.replaceAll( "\\s+at\\s+.*", "" )
                                   .replaceAll( "(/[^\\s]+)+", "[path]" );

        return sanitized.length() > 200 ? sanitized.substring( 0, 200 ) + "..." : sanitized;
    }

    /**
 * Sends a message to the WebSocket with proper error handling and back-pressure.
 */
    private static void sendToWebSocket(WebSocketChannel channel, String message)
    {
        if( ! channel.isOpen() )
        {
            logWarning( "Attempted to send to closed WebSocket: " + channel, null );
            return;
        }

        try
        {
            // Send a text message asynchronously using a callback [5]
            WebSockets.sendText( message, channel, new WebSocketCallback<Void>()
            {
                @Override
                public void complete(WebSocketChannel wsc, Void t)
                {
                    // Message sent successfully
                }

                @Override
                public void onError( WebSocketChannel channel, Void context, Throwable throwable )
                {
                    logWarning( "Failed to send WebSocket message", throwable );
                    cleanupSession( channel, CloseCode.INTERNAL_ERROR );
                }
            } );
        }
        catch( Exception e )
        {
            logWarning( "Error initiating WebSocket send", e );
            cleanupSession( channel, CloseCode.INTERNAL_ERROR );
        }
    }

    /**
     * Comprehensive session cleanup that handles all termination scenarios.
     */
    private static void cleanupSession( WebSocketChannel session, CloseCode closeCode )
    {
        try
        {
            WeakReference<WebSocketChannel> sessionRef = findSessionReference( session );

            if( sessionRef == null )
                return; // Already cleaned up

            CopyOnWriteArrayList<Pair<INetClient, JsonObject>> clients = channelToClients.remove( sessionRef );

            if( clients != null )
            {
                logInfo( "Cleaning up session " + session + " with " + clients.size() + " ExEn connection(s)" );

                // Disconnect all ExEn clients
                for( Pair<INetClient, JsonObject> pair : clients )
                {
                    INetClient client = pair.getKey();

                    clientToChannel.remove( client );

                    try
                    {
                        client.disconnect();
                    }
                    catch( Exception e )
                    {
                        logWarning( "Error disconnecting ExEn client during cleanup", e );
                    }
                }
            }

            // Close WebSocket with proper code
            if( session.isOpen() )
            {
                session.setCloseCode(   closeCode.getCode()   );
                session.setCloseReason( closeCode.getReason() );
                session.close();
            }
        }
        catch( IOException ioe )
        {
            logError( "Exception during session cleanup for " + session, ioe );
        }
    }

    /**
     * Periodic cleanup of dead WebSocket references.
     */
    private static void cleanupDeadReferences()
    {
        try
        {
            Iterator<Map.Entry<WeakReference<WebSocketChannel>, CopyOnWriteArrayList<Pair<INetClient, JsonObject>>>> iterator
                    = channelToClients.entrySet().iterator();

            int cleanedCount = 0;
            while( iterator.hasNext() )
            {
                Map.Entry<WeakReference<WebSocketChannel>, CopyOnWriteArrayList<Pair<INetClient, JsonObject>>> entry = iterator.next();
                WebSocketChannel session = entry.getKey().get();

                if( session == null || !session.isOpen() )
                {
                    // Dead reference or closed session
                    CopyOnWriteArrayList<Pair<INetClient, JsonObject>> clients = entry.getValue();
                    for( Pair<INetClient, JsonObject> pair : clients )
                    {
                        clientToChannel.remove( pair.getKey() );
                        try
                        {
                            pair.getKey().disconnect();
                        }
                        catch( Exception e )
                        {
                            // Ignore cleanup errors
                        }
                    }
                    iterator.remove();
                    cleanedCount++;
                }
            }

            if( cleanedCount > 0 )
            {
                logInfo( "Cleaned up " + cleanedCount + " dead WebSocket references" );
            }

        }
        catch( Exception e )
        {
            logError( "Error during periodic cleanup", e );
        }
    }

    // Logging helper methods
    private static void logInfo(String message)
    {
        ILogger logger = UtilSys.getLogger();
        if( logger.isLoggable( ILogger.Level.INFO ) )
        {
            logger.log( ILogger.Level.INFO, message );
        }
    }

    private static void logWarning(String message, Throwable throwable)
    {
        UtilSys.getLogger().log( ILogger.Level.WARNING, throwable, message );
    }

    private static void logError(String message, Throwable throwable)
    {
        UtilSys.getLogger().log( ILogger.Level.SEVERE, throwable, message );
    }

    /**
     * WebSocket message listener that handles incoming messages from web clients.
     */
    private static final class WebSocketListener extends AbstractReceiveListener
    {
        @Override
        protected void onFullTextMessage(WebSocketChannel session, BufferedTextMessage message)
        {
            try
            {
                String data = message.getData();

                if( data.length() > MAX_PAYLOAD_SIZE )    // Fail fast on oversized messages
                {
                    handleError( session,
                                 new IllegalArgumentException( "Message too large: " + data.length() + " bytes" ),
                                 "message size validation" );
                    return;
                }

                if( UtilStr.isEmpty( data ) )
                    return;

                JsonObject messageJson = Json.parse( data ).asObject();
                JsonObject exenAddress = extractExEnAddress( messageJson );

                if( exenAddress == null )
                {
                    handleError( session,
                                 new IllegalArgumentException( "Missing or invalid ExEn address" ),
                                 "ExEn address extraction" );
                    return;
                }

                // Find or create ExEn client
                INetClient client = findExEnClient( session, exenAddress );

                if( client == null )
                    client = addExEnClient( session, exenAddress );

                if( client != null && client.isConnected() )    // Forward message to ExEn
                {
                    String payload = messageJson.get( "msg" ).toString();
                    client.send( payload );
                }
                else
                {
                    handleError( session,
                                 new IllegalStateException( "ExEn client unavailable" ),
                                 "ExEn communication" );
                }
            }
            catch( Exception e )
            {
                handleError( session, e, "message processing" );
            }
        }

        private JsonObject extractExEnAddress(JsonObject messageJson)
        {
            try
            {
                if( messageJson.get( "exen" ) == null )
                    return null;

                if( messageJson.get( "exen" ).isString() )
                    return Json.parse( messageJson.get( "exen" ).asString() ).asObject();

                return messageJson.get( "exen" ).asObject();
            }
            catch( Exception e )
            {
                return null;
            }
        }

        @Override
        protected void onError(WebSocketChannel session, Throwable error)
        {
            handleError( session, error, "WebSocket error" );
        }

        @Override
        protected void onClose(WebSocketChannel session, StreamSourceFrameChannel channel) throws IOException
        {
            cleanupSession( session, CloseCode.NORMAL_CLOSURE );
        }
    }

    /**
     * Listener for ExEn client connections that forwards messages back to WebSocket.
     */
    private static final class ExEnListener implements INetClient.IListener
    {
        @Override
        public void onConnected(INetClient client)
        {
            logInfo( "ExEn client connected: " + client );
        }

        @Override
        public void onDisconnected(INetClient client)
        {
            Pair<WeakReference<WebSocketChannel>, JsonObject> channelInfo = clientToChannel.remove( client );
            if( channelInfo == null )
            {
                return; // Already cleaned up
            }

            WeakReference<WebSocketChannel> sessionRef = channelInfo.getKey();
            WebSocketChannel session = sessionRef.get();

            if( session != null && session.isOpen() )
            {
                removeExEnClient( session, client );

                // Close WebSocket if no more ExEn connections
                CopyOnWriteArrayList<Pair<INetClient, JsonObject>> remainingClients = channelToClients.get( sessionRef );
                if( remainingClients == null || remainingClients.isEmpty() )
                {
                    cleanupSession( session, CloseCode.GOING_AWAY );
                }
            }
        }

        @Override
        public void onMessage(INetClient client, String message)
        {
            if( message == null )
            {
                return;
            }

            try
            {
                Pair<WeakReference<WebSocketChannel>, JsonObject> channelInfo = clientToChannel.get( client );

                if( channelInfo == null )
                {
                    return; // Connection already closed
                }

                WebSocketChannel session = channelInfo.getKey().get();
                if( session == null || !session.isOpen() )
                {
                    return; // WebSocket closed
                }

                JsonObject exenAddress = channelInfo.getValue();
                JsonObject responseMessage = Json.parse( message ).asObject().add( "exen", exenAddress );

                sendToWebSocket( session, responseMessage.toString() );
            }
            catch( Exception e )
            {
                logError( "Error forwarding ExEn message to WebSocket", e );
            }
        }

        @Override
        public void onError(INetClient client, Exception error)
        {
            Pair<WeakReference<WebSocketChannel>, JsonObject> channelInfo = clientToChannel.get( client );
            if( channelInfo == null )
            {
                return; // Already cleaned up
            }

            WeakReference<WebSocketChannel> sessionRef = channelInfo.getKey();
            WebSocketChannel session = sessionRef.get();
            JsonObject exenAddress = channelInfo.getValue();

            if( session != null && session.isOpen() )
            {
                try
                {
                    String errorMessage;
                    if( error.getClass().getPackage().getName().startsWith( "java.net" ) )
                    {
                        errorMessage = "ExEn '" + client + "' cannot be reached. Is it running and accepting connections?";
                    }
                    else
                    {
                        errorMessage = sanitizeErrorMessage( error.getMessage() );
                    }

                    JsonObject errorResponse = new JsonObject()
                            .add( MessageType.ERROR.getValue(), errorMessage )
                            .add( "exen", exenAddress );

                    sendToWebSocket( session, errorResponse.toString() );

                }
                catch( Exception e )
                {
                    logError( "Error sending ExEn error to WebSocket", e );
                }

                // Clean up the failed client
                removeExEnClient( session, client );
            }
        }
    }
}