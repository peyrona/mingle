
package com.peyrona.mingle.lang.interfaces.network;

import java.util.stream.Stream;

/**
 * The interface that every communications server must implement.
 * <p>
 * This interface provides a comprehensive contract for network server implementations,
 * including client management, message broadcasting, and error handling capabilities.
 * <p>
 * Implementations must handle client lifecycle, message routing, and error
 * notification in a thread-safe manner.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface INetServer
{
    /**
     * The listener that receives server events and client messages.
     * <p>
     * Implementations of this interface receive notifications about client connections,
     * disconnections, messages, and errors from the server.
     *
     * @author Francisco José Morero Peyrona
     *
     * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
     */
    public interface IListener
    {
        /**
         * Called when a new client connects to the server.
         *
         * @param server The server instance that received the connection
         * @param client The newly connected client
         */
        void onConnected( INetServer server, INetClient client );

        /**
         * Called when a client disconnects from the server.
         *
         * @param server The server instance that lost the connection
         * @param client The disconnected client
         */
        void onDisconnected( INetServer server, INetClient client );

        /**
         * Called when a message is received from a client.
         *
         * @param server The server instance that received the message
         * @param client The client that sent the message
         * @param msg The message content (may be null or empty)
         */
        void onMessage( INetServer server, INetClient client, String msg );

        /**
         * Called when an error occurs related to a specific client.
         *
         * @param server The server instance where the error occurred
         * @param client The client associated with the error (may be null for server-wide errors)
         * @param err The exception that occurred
         */
        void onError( INetServer server, INetClient client, Exception err );
    }

    //------------------------------------------------------------------------//
    // SERVER LIFECYCLE METHODS
    //------------------------------------------------------------------------//

    /**
     * Starts the server with specified configuration.
     * <p>
     * The configuration is provided as a JSON string containing settings such as
     * host, port, path, SSL certificates, and timeouts. When SSL certificates
     * are provided and valid, the server will use secure connections; otherwise,
     * normal HTTP connections are used.
     *
     * @param sCfgAsJSON A JSON object ([key,value] pairs) encoded as string. This is the
     *                   information needed by the controller to properly start the server.
     *                   Expected keys include: "host", "port", "path", "cert_path", "key_path", "timeout".
     * @return The server instance for method chaining.
     * @throws IllegalArgumentException if the configuration is invalid
     * @throws IllegalStateException if the server is already running
     */
    INetServer start( String sCfgAsJSON );

    /**
     * Stops the server gracefully.
     * <p>
     * This method should close all active client connections, release resources,
     * and stop accepting new connections. The shutdown should be clean and allow
     * for proper restart.
     *
     * @return The server instance for method chaining.
     */
    INetServer stop();

    /**
     * Checks if the server is currently running and accepting connections.
     *
     * @return true if the server is running, false otherwise.
     */
    boolean isRunning();

    //------------------------------------------------------------------------//
    // MESSAGE BROADCASTING METHODS
    //------------------------------------------------------------------------//

    /**
     * Broadcasts a message to all connected clients.
     * <p>
     * This method should send the message to every currently connected client.
     * The operation should be thread-safe and should not fail if no clients
     * are connected.
     *
     * @param message The message to broadcast to all clients
     * @return The server instance for method chaining.
     */
    INetServer broadcast( String message );

    //------------------------------------------------------------------------//
    // CLIENT MANAGEMENT METHODS
    //------------------------------------------------------------------------//

    /**
     * Adds a client to the server's client collection.
     * <p>
     * This method is typically called internally when a new client successfully
     * connects to the server.
     *
     * @param client The client to add to the server
     * @return true if operation succeeded.
     */
    boolean add( INetClient client );

    /**
     * Removes a client from the server's client collection.
     * <p>
     * This method is typically called when a client disconnects or when the
     * server needs to forcefully remove a client.
     *
     * @param client The client to remove from the server
     * @return true if operation succeeded.
     */
    boolean del( INetClient client );

    /**
     * Checks if the server has any active client connections.
     *
     * @return true if there is at least one connected client, false otherwise.
     */
    boolean hasClients();

    /**
     * Returns a Stream with all connected clients (a copy of leaving clients).
     * @return A Stream with all connected clients.
     */
    Stream<INetClient> getClients();

    //------------------------------------------------------------------------//
    // LISTENER MANAGEMENT METHODS
    //------------------------------------------------------------------------//

    /**
     * Adds a server event listener to receive notifications.
     *
     * @param rl The listener to add for server events
     * @return true if the listener was added successfully, false if already present
     */
    boolean add( IListener rl );

    /**
     * Removes a server event listener.
     *
     * @param rl The listener to remove
     * @return true if the listener was removed successfully, false if not found
     */
    boolean remove( IListener rl );
}