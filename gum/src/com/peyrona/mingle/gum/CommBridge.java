package com.peyrona.mingle.gum;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.ILogger;
import com.peyrona.mingle.lang.interfaces.ILogger.Level;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.japi.Pair;
import com.peyrona.mingle.lang.japi.UtilReflect;
import com.peyrona.mingle.lang.japi.UtilStr;
import com.peyrona.mingle.lang.japi.UtilSys;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.WriteCallback;

/**
 * WebSocket bridge that enables web clients to communicate with ExEn servers.
 *
 * <h3>Usage Contract:</h3>
 * <ul>
 * <li>One CommBridge instance per Jetty server</li>
 * <li>Browser opens WebSocket to {@code ws://host/gum/bridge/{exen-hash}} where exen-hash identifies the target ExEn</li>
 * <li>Multiple ExEn sessions can be multiplexed through a single WebSocket connection</li>
 * <li>Clients must not rely on {@code wasClean} flag due to browser connection sharing behavior</li>
 * <li>Automatic cleanup occurs when WebSocket closes or ExEn connections fail</li>
 * </ul>
 *
 * <h3>Architecture:</h3>
 * This class acts as a bidirectional message bridge:
 * <pre>
 * Browser WebSocket ↔↔ CommBridge ↔↔ ExEn TCP Connection
 * </pre>
 *
 * @author Francisco José Morero Peyrona
 * @see <a href="https://github.com/peyrona/mingle">Official website</a>
 */
public class CommBridge extends WebSocketAdapter
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
    private enum MessageType
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
    private enum CloseCode
    {
        NORMAL_CLOSURE(    StatusCode.NORMAL,      "Normal closure" ),
        GOING_AWAY(        StatusCode.SHUTDOWN,    "Server going away" ),
        PROTOCOL_ERROR(    StatusCode.PROTOCOL,    "Protocol error" ),
        UNSUPPORTED_DATA(  StatusCode.BAD_DATA,    "Unsupported data" ),
        INVALID_PAYLOAD(   StatusCode.BAD_PAYLOAD, "Invalid payload data" ),
        POLICY_VIOLATION(  StatusCode.POLICY_VIOLATION, "Policy violation" ),
        MESSAGE_TOO_LARGE( StatusCode.MESSAGE_TOO_LARGE, "Message too large" ),
        INTERNAL_ERROR(    StatusCode.SERVER_ERROR, "Internal server error" );

        private final int code;
        private final String reason;

        CloseCode( int code, String reason )
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

    // Thread-safe map: WebSocket Session → List of (ExEn client, ExEn address)
    private static final ConcurrentHashMap<Session,
                                           CopyOnWriteArrayList<Pair<INetClient,JsonObject>>> sessionToClients = new ConcurrentHashMap<>();

    // Reverse lookup: ExEn client → (WebSocket Session, ExEn address)
    private static final ConcurrentHashMap<INetClient, Pair<Session, JsonObject>> clientToSession = new ConcurrentHashMap<>();

    // Scheduled cleanup of dead WebSocket references
    private static final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor( r ->
                                                                                                                {
                                                                                                                    Thread t = new Thread( r, "CommBridge-Cleanup" );
                                                                                                                           t.setDaemon( true );
                                                                                                                    return t;
                                                                                                                } );

    private static final ILogger logger = UtilSys.getLogger();

    //------------------------------------------------------------------------//

    static    // Start periodic cleanup of dead WebSocket references
    {
        cleanupExecutor.scheduleWithFixedDelay( CommBridge::cleanupDeadReferences,
                                                CLEANUP_INTERVAL_MINUTES,
                                                CLEANUP_INTERVAL_MINUTES,
                                                TimeUnit.MINUTES );
    }

    //------------------------------------------------------------------------//

    @Override
    public void onWebSocketConnect( Session session )
    {
        super.onWebSocketConnect( session );

        if( logger.isLoggable( ILogger.Level.INFO ) )
            logger.log( Level.INFO, "WebSocket connected: " + session );
    }

    @Override
    public void onWebSocketText( String message )
    {
        Session session = getSession();

        if( session == null || ! session.isOpen() )
            return;

        try
        {
            if( message.length() > MAX_PAYLOAD_SIZE )    // Fail fast on oversized messages
            {
                handleError( session,
                             new IllegalArgumentException( "Message too large: " + message.length() + " bytes" ),
                             "message size validation" );
                return;
            }

            if( UtilStr.isEmpty( message ) )
                return;

            JsonObject messageJson = Json.parse( message ).asObject();
            JsonObject exenAddress = extractExEnAddress( messageJson );

            if( exenAddress == null )
            {
                handleError( session,
                             new IllegalArgumentException( "Missing or invalid ExEn address:\n"+ session ),
                             "ExEn address extraction" );
                return;
            }

            // Find or create ExEn client
            INetClient client = findExEnClient( session, exenAddress );

            if( client == null )
            {
                // Attempt to create and connect
                client = addExEnClient( session, exenAddress );
            }

            if( client != null && client.isConnected() )    // Forward message to ExEn
            {
                String payload = messageJson.get( "msg" ).toString();
                client.send( payload );
            }
            else
            {
                handleError( session,
                             new IllegalStateException( "ExEn client unavailable:\n"+ session ),
                             "ExEn communication" );
            }
        }
        catch( Exception e )
        {
            handleError( session, e, "message processing" );
        }
    }

    @Override
    public void onWebSocketClose( int statusCode, String reason )
    {
        Session session = getSession();

        if( session != null )
        {
            cleanupSession( session, CloseCode.NORMAL_CLOSURE );
        }

        super.onWebSocketClose( statusCode, reason );
    }

    @Override
    public void onWebSocketError( Throwable cause )
    {
        Session session = getSession();

        if( session != null )
        {
            handleError( session, cause, "WebSocket error" );
        }

        super.onWebSocketError( cause );
    }

    //------------------------------------------------------------------------//
    // PRIVATE STATIC METHODS

    /**
     * Adds a new ExEn client connection for the given WebSocket session.
     */
    private static INetClient addExEnClient( Session session, JsonObject exenAddress )
    {
        INetClient client = null;

        try
        {
            // 1. Prepare and connect the client (Heavy operation outside locks)
            client = getNetClient();
            client.add( new ExEnListener() );
            client.connect( exenAddress.toString() ); // May block or fail
        }
        catch( Exception me )
        {
            logError( "Failed to create/connect ExEn client for " + exenAddress, me );
            // Clean up the partially created client if it exists
            if( client != null )
            {
                try { client.disconnect(); } catch( Exception ignored ) {}
            }
            return null;
        }

        // 2. Add to maps safely (Quick atomic operation)
        synchronized( session )
        {
            if( ! session.isOpen() )
            {
                try { client.disconnect(); } catch( Exception ignored ) {}
                return null;
            }

            Pair<INetClient, JsonObject> clientPair = new Pair<>( client, exenAddress );

            sessionToClients.computeIfAbsent( session, k -> new CopyOnWriteArrayList<>() ).add( clientPair );
            clientToSession.put( client, new Pair<>( session, exenAddress ) );
        }

        if( logger.isLoggable( ILogger.Level.INFO ) )
            logger.log( Level.INFO, "Added ExEn client for session " + session + " → " + exenAddress );

        return client;
    }

    private static INetClient getNetClient()
    {
        String   sClass = UtilSys.getConfig().get( "monitoring", "client", "com.peyrona.mingle.network.socket.SocketClient" );
        String[] asURIs = UtilSys.getConfig().get( "monitoring", "uris"  , new String[] { "file://{*home.lib*}network.jar" } );

        try
        {
            return UtilReflect.newInstance( INetClient.class, sClass, asURIs );
        }
        catch( ClassNotFoundException | InstantiationException | IllegalAccessException   | NoSuchMethodException |
               URISyntaxException     | IOException            | IllegalArgumentException | InvocationTargetException exc )
        {
            throw new MingleException( exc );
        }
    }

    /**
     * Finds an existing ExEn client for the given session and address.
     * Optimized to O(1) for session lookup + list iteration.
     */
    private static INetClient findExEnClient( Session session, JsonObject exenAddress )
    {
        CopyOnWriteArrayList<Pair<INetClient, JsonObject>> clients = sessionToClients.get( session );

        if( clients == null )
            return null;

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
    private static boolean removeExEnClient( Session session, INetClient client )
    {
        boolean removed = false;

        // Synchronize on session to ensure atomic removal from list
        synchronized( session )
        {
            CopyOnWriteArrayList<Pair<INetClient, JsonObject>> clients = sessionToClients.get( session );

            if( clients != null )
            {
                removed = clients.removeIf( pair -> pair.getKey().equals( client ) );

                // Cleanup map entry if empty to save memory
                if( clients.isEmpty() )
                {
                    sessionToClients.remove( session );
                }
            }
        }

        if( removed )
        {
            clientToSession.remove( client );

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
     * Centralized error handling that logs, notifies client, and cleans up.
     */
    private static void handleError( Session session, Throwable error, String context )
    {
        logWarning( "WebSocket error in " + context + ": " + session, error );

        try    // Send sanitized error message to client
        {
            JsonObject errorMessage = new JsonObject().add( MessageType.ERROR.getValue(), sanitizeErrorMessage( error.getMessage() ) );
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
    private static String sanitizeErrorMessage( String original )
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
    private static void sendToWebSocket( Session session, String message )
    {
        if( session == null || !session.isOpen() )
        {
            logWarning( "Attempted to send to closed WebSocket: " + session, null );
            return;
        }

        try
        {
            // Send a text message asynchronously using a callback
            session.getRemote().sendString( message, new WriteCallback()
            {
                @Override
                public void writeSuccess()
                {
                    // Message sent successfully
                }

                @Override
                public void writeFailed( Throwable throwable )
                {
                    logWarning( "Failed to send WebSocket message", throwable );
                    cleanupSession( session, CloseCode.INTERNAL_ERROR );
                }
            } );
        }
        catch( Exception e )
        {
            logWarning( "Error initiating WebSocket send", e );
            cleanupSession( session, CloseCode.INTERNAL_ERROR );
        }
    }

    /**
     * Comprehensive session cleanup that handles all termination scenarios.
     */
    private static void cleanupSession( Session session, CloseCode closeCode )
    {
        try
        {
            CopyOnWriteArrayList<Pair<INetClient, JsonObject>> clients;

            // Atomically remove the session mapping
            synchronized( session )
            {
                clients = sessionToClients.remove( session );
            }

            if( clients != null )
            {
                if( logger.isLoggable( ILogger.Level.INFO ) )
                    logger.log( Level.INFO,  "Cleaning up session " + session + " with " + clients.size() + " ExEn connection(s)" );

                // Disconnect all ExEn clients
                for( Pair<INetClient, JsonObject> pair : clients )
                {
                    INetClient client = pair.getKey();

                    clientToSession.remove( client );

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
                session.close( closeCode.getCode(), closeCode.getReason() );
            }
        }
        catch( Exception e )
        {
            logError( "Exception during session cleanup for " + session, e );
        }
    }

    /**
     * Periodic cleanup of dead WebSocket references.
     */
    private static void cleanupDeadReferences()
    {
        try
        {
            Iterator<Map.Entry<Session, CopyOnWriteArrayList<Pair<INetClient, JsonObject>>>> iterator = sessionToClients.entrySet().iterator();

            int cleanedCount = 0;

            while( iterator.hasNext() )
            {
                Map.Entry<Session, CopyOnWriteArrayList<Pair<INetClient, JsonObject>>> entry   = iterator.next();
                Session                                                                session = entry.getKey();

                // If session is closed but still in map (leak), force cleanup
                if( session == null || !session.isOpen() )
                {
                    cleanupSession( session, CloseCode.GOING_AWAY );
                    cleanedCount++;
                }
            }

            if( cleanedCount > 0 && logger.isLoggable( ILogger.Level.INFO ) )
                logger.log( Level.INFO, "Cleaned up " + cleanedCount + " dead WebSocket references" );
        }
        catch( Exception e )
        {
            logError( "Error during periodic cleanup", e );
        }
    }

    private static JsonObject extractExEnAddress( JsonObject messageJson )
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

    private static void logWarning( String message, Throwable throwable )
    {
        logger.log( ILogger.Level.WARNING, throwable, message );
    }

    private static void logError( String message, Throwable throwable )
    {
        logger.log( ILogger.Level.SEVERE, throwable, message );
    }

    /**
     * Shuts down the CommBridge and cleans up all resources.
     * This method should be called during application shutdown to prevent thread leaks.
     */
    public static void shutdown()
    {
        try
        {
            cleanupExecutor.shutdown();

            if( ! cleanupExecutor.awaitTermination( 5, TimeUnit.SECONDS ) )
            {
                cleanupExecutor.shutdownNow();

                if( ! cleanupExecutor.awaitTermination( 2, TimeUnit.SECONDS ) )
                    logError( "ScheduledExecutorService did not terminate gracefully", null );
            }
        }
        catch( InterruptedException e )
        {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            logError( "Interrupted while waiting for ScheduledExecutorService termination", e );
        }
    }

    //------------------------------------------------------------------------//
    // INNER CLASS
    //------------------------------------------------------------------------//

    /**
     * Listener for ExEn client connections that forwards messages back to WebSocket.
     */
    private static final class ExEnListener implements INetClient.IListener
    {
        @Override
        public void onConnected( INetClient client )
        {
            if( logger.isLoggable( ILogger.Level.INFO ) )
                logger.log( Level.INFO, "ExEn client connected: " + client );
        }

        @Override
        public void onDisconnected( INetClient client )
        {
            Pair<Session, JsonObject> sessionInfo = clientToSession.remove( client );

            if( sessionInfo == null )
                return;     // Already cleaned up

            Session session = sessionInfo.getKey();

            if( session != null && session.isOpen() )
            {
                removeExEnClient( session, client );

                // Close WebSocket if no more ExEn connections
                CopyOnWriteArrayList<Pair<INetClient, JsonObject>> remainingClients = sessionToClients.get( session );

                if( remainingClients == null || remainingClients.isEmpty() )
                    cleanupSession( session, CloseCode.GOING_AWAY );
            }
        }

        @Override
        public void onMessage( INetClient client, String message )
        {
            if( message == null )
                return;

            try
            {
                Pair<Session, JsonObject> sessionInfo = clientToSession.get( client );

                if( sessionInfo == null )
                    return; // Connection already closed

                Session session = sessionInfo.getKey();

                if( session == null || !session.isOpen() )
                    return; // WebSocket closed

                JsonObject exenAddress     = sessionInfo.getValue();
                JsonObject responseMessage = Json.parse( message ).asObject().add( "exen", exenAddress );

                sendToWebSocket( session, responseMessage.toString() );
            }
            catch( Exception e )
            {
                logError( "Error forwarding ExEn message to WebSocket", e );
            }
        }

        @Override
        public void onError( INetClient client, Exception error )
        {
            Pair<Session, JsonObject> sessionInfo = clientToSession.get( client );

            if( sessionInfo == null )
                return;    // Already cleaned up

            Session    session     = sessionInfo.getKey();
            JsonObject exenAddress = sessionInfo.getValue();

            if( session != null && session.isOpen() )
            {
                try
                {
                    String errorMessage;

                    if( error.getClass().getPackage().getName().startsWith( "java.net" ) )  errorMessage = "ExEn '" + client + "' cannot be reached. Is it running and accepting connections?";
                    else                                                                    errorMessage = sanitizeErrorMessage( error.getMessage() );

                    JsonObject errorResponse = new JsonObject()
                            .add( MessageType.ERROR.getValue(), errorMessage )
                            .add( "exen", exenAddress );

                    sendToWebSocket( session, errorResponse.toString() );

                }
                catch( Exception e )
                {
                    logError( "Error sending ExEn error to WebSocket", e );
                }

                removeExEnClient( session, client );   // Clean up the failed client
            }
        }
    }
}