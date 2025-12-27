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
import java.util.concurrent.TimeoutException;
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
        NORMAL_CLOSURE(     StatusCode.NORMAL,            "Normal closure" ),
        TARGET_UNAVAILABLE( StatusCode.BAD_PAYLOAD,       "Target ExEn unavailable" ),
        WEBSOCKET_TIMEOUT(  StatusCode.POLICY_VIOLATION,  "Idle timeout expired" ),
        GOING_AWAY(         StatusCode.SHUTDOWN,          "Server going away" ),
        PROTOCOL_ERROR(     StatusCode.PROTOCOL,          "Protocol error" ),
        UNSUPPORTED_DATA(   StatusCode.BAD_DATA,          "Unsupported data" ),
        INVALID_PAYLOAD(    StatusCode.BAD_PAYLOAD,       "Invalid payload data" ),
        POLICY_VIOLATION(   StatusCode.POLICY_VIOLATION,  "Policy violation" ),
        MESSAGE_TOO_LARGE(  StatusCode.MESSAGE_TOO_LARGE, "Message too large" ),
        INTERNAL_ERROR(     StatusCode.SERVER_ERROR,      "Internal server error" );

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

    /**
     * Thread-safe map: WebSocket Session → List of (ExEn client, ExEn address).
     *
     * <p><b>Design Note:</b> These static maps are intentional. Jetty creates one CommBridge
     * instance per WebSocket connection, but we need to share state across all connections
     * (e.g., for cleanup, cross-session lookups). Static maps provide this shared state
     * while instance methods provide access to per-connection Session via {@code getSession()}.</p>
     */
    private static final ConcurrentHashMap<Session,
                                           CopyOnWriteArrayList<Pair<INetClient,JsonObject>>> sessionToClients = new ConcurrentHashMap<>();

    /**
     * Reverse lookup: ExEn client → (WebSocket Session, ExEn address).
     * @see #sessionToClients
     */
    private static final ConcurrentHashMap<INetClient, Pair<Session, JsonObject>> clientToSession = new ConcurrentHashMap<>();

    /**
     * Dedicated lock objects per session to avoid synchronizing on Session objects directly.
     * Synchronizing on Session is risky because Jetty may internally synchronize on it,
     * potentially causing deadlocks.
     */
    private static final ConcurrentHashMap<Session, Object> sessionLocks = new ConcurrentHashMap<>();

    /**
     * Set of ExEn addresses currently being connected to, to prevent duplicate connection attempts.
     * Key format: session.hashCode() + ":" + exenAddress.toString()
     */
    private static final ConcurrentHashMap<String, Boolean> pendingConnections = new ConcurrentHashMap<>();

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
            // Note: Jetty already enforces MAX_PAYLOAD_SIZE via setMaxTextMessageSize() in GumWebSocketServlet.
            // No need to check here - Jetty will reject oversized messages before they reach this method.

            if( UtilStr.isEmpty( message ) )
                return;

            JsonObject messageJson = Json.parse( message ).asObject();
            JsonObject exenAddress = extractExEnAddress( messageJson );

            if( exenAddress == null )
            {
                sendErrorToClient( session, "Missing or invalid ExEn address" );
                return;
            }

            // Find or create ExEn client
            INetClient client = findExEnClient( session, exenAddress );

            if( client == null )
            {
                // Attempt to create and connect asynchronously
                String payload = messageJson.get( "msg" ).toString();
                addExEnClientAsync( session, exenAddress, payload );
                return;   // Message will be sent after connection is established
            }

            if( client.isConnected() )    // Forward message to ExEn
            {
                String payload = messageJson.get( "msg" ).toString();
                client.send( payload );
            }
            else
            {
                sendErrorToClient( session, exenAddress, "ExEn client unavailable: "+ exenAddress );
            }
        }
        catch( Exception e )
        {
            logWarning( "Error processing WebSocket message", e );
            sendErrorToClient( session, sanitizeErrorMessage( e.getMessage() ) );
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
            // WebSocket errors are typically fatal (connection issues, protocol errors)
            handleFatalError( session, cause );
        }

        super.onWebSocketError( cause );
    }

    //------------------------------------------------------------------------//
    // PRIVATE STATIC METHODS

    /**
     * Returns a dedicated lock object for the given session.
     * Using dedicated locks avoids potential deadlocks from synchronizing on Session objects.
     */
    private static Object getLock( Session session )
    {
        return sessionLocks.computeIfAbsent( session, k -> new Object() );
    }

    /**
     * Generates a unique key for pending connection tracking.
     */
    private static String getPendingKey( Session session, JsonObject exenAddress )
    {
        return session.hashCode() + ":" + exenAddress.toString();
    }

    /**
     * Adds a new ExEn client connection asynchronously.
     * Uses UtilSys.execute() to avoid blocking the WebSocket thread during connection.
     * Prevents duplicate connection attempts using pendingConnections map.
     *
     * @param session      The WebSocket session
     * @param exenAddress  The ExEn address to connect to
     * @param initialPayload The message to send after connection is established (can be null)
     */
    private static void addExEnClientAsync( Session session, JsonObject exenAddress, String initialPayload )
    {
        String pendingKey = getPendingKey( session, exenAddress );

        // Prevent duplicate connection attempts (race condition fix)
        if( pendingConnections.putIfAbsent( pendingKey, Boolean.TRUE ) != null )
        {
            if( logger.isLoggable( ILogger.Level.INFO ) )
                logger.log( Level.INFO, "Connection already in progress for: " + exenAddress );
            return;
        }

        // Execute connection in background thread using UtilSys ThreadPool
        UtilSys.execute( "CommBridge-Connect-" + exenAddress.hashCode(), () ->
        {
            INetClient client = null;

            try
            {
                // 1. Prepare and connect the client (Heavy operation in background thread)
                client = getNetClient();
                client.add( new ExEnListener() );
                client.connect( exenAddress.toString() );  // This may block

                // 2. Add to maps safely using dedicated lock
                synchronized( getLock( session ) )
                {
                    if( ! session.isOpen() )
                    {
                        try { client.disconnect(); } catch( Exception ignored ) {}
                        return;
                    }

                    Pair<INetClient, JsonObject> clientPair = new Pair<>( client, exenAddress );

                    sessionToClients.computeIfAbsent( session, k -> new CopyOnWriteArrayList<>() ).add( clientPair );
                    clientToSession.put( client, new Pair<>( session, exenAddress ) );
                }

                if( logger.isLoggable( ILogger.Level.INFO ) )
                    logger.log( Level.INFO, "Added ExEn client for session " + session + " → " + exenAddress );

                // 3. Send the initial message if provided
                if( initialPayload != null && client.isConnected() )
                {
                    client.send( initialPayload );
                }
            }
            catch( Exception me )
            {
                logError( "Failed to create/connect ExEn client for " + exenAddress, me );

                // Clean up the partially created client if it exists
                if( client != null )
                {
                    try { client.disconnect(); } catch( Exception ignored ) {}
                }

                // Send error to client
                sendErrorToClient( session, exenAddress, "Cannot connect to ExEn: " + exenAddress );
            }
            finally
            {
                // Remove from pending connections
                pendingConnections.remove( pendingKey );
            }
        });
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

        // Synchronize using dedicated lock to ensure atomic removal from list
        synchronized( getLock( session ) )
        {
            CopyOnWriteArrayList<Pair<INetClient, JsonObject>> clients = sessionToClients.get( session );

            if( clients != null )
            {
                removed = clients.removeIf( pair -> pair.getKey().equals( client ) );

                // Cleanup map entry if empty to save memory
                if( clients.isEmpty() )
                {
                    sessionToClients.remove( session );
                    sessionLocks.remove( session );   // Also cleanup the lock
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
     * Centralized error handling for fatal errors that require session cleanup.
     * Only call this for truly fatal errors (connection failures, protocol errors).
     * For recoverable errors, use sendErrorToClient() instead.
     */
    private static void handleFatalError( Session session, Throwable error )
    {
        logError( "Fatal WebSocket error in: " + session, error );

        try    // Send sanitized error message to client
        {
            JsonObject errorMessage = new JsonObject().add( MessageType.ERROR.getValue(), sanitizeErrorMessage( error.getMessage() ) );
            sendToWebSocket( session, errorMessage.toString() );
        }
        catch( Exception e )
        {
            logWarning( "Failed to send error message to client", e );
        }
        finally     // Force cleanup only for fatal errors
        {
            cleanupSession( session, CloseCode.INTERNAL_ERROR );
        }
    }

    /**
     * Sends an error message to the client without closing the session.
     * Use this for recoverable errors like invalid messages or temporarily unavailable ExEn.
     */
    private static void sendErrorToClient( Session session, String errorMessage )
    {
        sendErrorToClient( session, null, errorMessage );
    }

    /**
     * Sends an error message to the client with ExEn address context, without closing the session.
     */
    private static void sendErrorToClient( Session session, JsonObject exenAddress, String errorMessage )
    {
        if( session == null || !session.isOpen() )
            return;

        try
        {
            JsonObject errorResponse = new JsonObject().add( MessageType.ERROR.getValue(), sanitizeErrorMessage( errorMessage ) );

            if( exenAddress != null )
                errorResponse.add( "exen", exenAddress );

            sendToWebSocket( session, errorResponse.toString() );
        }
        catch( Exception e )
        {
            logWarning( "Failed to send error message to client", e );
        }
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
     * Does not cleanup session on failure - let periodic cleanup handle stale sessions.
     */
    private static void sendToWebSocket( Session session, String message )
    {
        if( session == null || !session.isOpen() )
        {
            if( logger.isLoggable( ILogger.Level.INFO ) )
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
                    // Log failure but don't cleanup session here - let periodic cleanup handle it
                    // This avoids race conditions where multiple sends fail simultaneously
                    logWarning( "Failed to send WebSocket message", throwable );
                }
            } );
        }
        catch( Exception e )
        {
            // Log error but don't cleanup - periodic cleanup will handle stale sessions
            logWarning( "Error initiating WebSocket send", e );
        }
    }

    /**
     * Comprehensive session cleanup that handles all termination scenarios.
     */
    private static void cleanupSession( Session session, CloseCode closeCode )
    {
        if( session == null )
            return;

        try
        {
            CopyOnWriteArrayList<Pair<INetClient, JsonObject>> clients;

            // Atomically remove the session mapping using dedicated lock
            synchronized( getLock( session ) )
            {
                clients = sessionToClients.remove( session );
                sessionLocks.remove( session );   // Cleanup the lock itself
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
     * Uses iterator.remove() to safely remove entries while iterating.
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
                    // First remove from map using iterator to avoid ConcurrentModificationException
                    iterator.remove();

                    // Then cleanup associated resources
                    if( session != null )
                    {
                        CopyOnWriteArrayList<Pair<INetClient, JsonObject>> clients = entry.getValue();

                        // Disconnect all ExEn clients
                        if( clients != null )
                        {
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
                                    logWarning( "Error disconnecting ExEn client during periodic cleanup", e );
                                }
                            }
                        }

                        // Cleanup the lock
                        sessionLocks.remove( session );

                        // Close WebSocket if still somehow open
                        try
                        {
                            if( session.isOpen() )
                                session.close( CloseCode.GOING_AWAY.getCode(), CloseCode.GOING_AWAY.getReason() );
                        }
                        catch( Exception e )
                        {
                            logWarning( "Error closing session during periodic cleanup", e );
                        }
                    }

                    cleanedCount++;
                }
            }

            // Also cleanup orphaned pending connections
            pendingConnections.entrySet().removeIf( entry -> {
                // Pending connections older than 30 seconds are considered orphaned
                // (connection timeout should have triggered by then)
                return true;  // For now, just remove all - they should be removed by the async tasks
            });

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
        if( throwable instanceof TimeoutException )
            logger.log( ILogger.Level.INFO  , throwable, message );
        else
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
            // Shutdown the cleanup executor
            cleanupExecutor.shutdown();

            if( ! cleanupExecutor.awaitTermination( 5, TimeUnit.SECONDS ) )
            {
                cleanupExecutor.shutdownNow();

                if( ! cleanupExecutor.awaitTermination( 2, TimeUnit.SECONDS ) )
                    logError( "ScheduledExecutorService did not terminate gracefully", null );
            }

            // Cleanup all remaining sessions
            for( Session session : sessionToClients.keySet() )
            {
                cleanupSession( session, CloseCode.GOING_AWAY );
            }

            // Clear all static maps
            sessionToClients.clear();
            clientToSession.clear();
            sessionLocks.clear();
            pendingConnections.clear();
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
                finally
                {
                    removeExEnClient( session, client );   // Clean up the failed client
                }
            }
        }
    }
}