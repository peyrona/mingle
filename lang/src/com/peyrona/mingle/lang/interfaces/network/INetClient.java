
package com.peyrona.mingle.lang.interfaces.network;

/**
 * Interface that every communications client must implement.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface INetClient
{
    /**
     * The listener that receives the messages from the channel (the server-side).
     *
     * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
     */
    public interface IListener
    {
        void onConnected(    INetClient origin );
        void onDisconnected( INetClient origin );
        void onMessage(      INetClient origin, String msg );
        void onError(        INetClient origin, Exception exc );
    }

    /**
     * The information needed to start the network channel.
     *
     * @param sCfgAsJSON A JSON object ([key,value] pairs) encoded as string. This is the information
     *                   needed by the controller to initiate the client connection with the server.
     * @return Itself.
     */
    INetClient connect( String sCfgAsJSON );

    /**
     * Disconnects from the remote server and releases resources.
     *
     * @return Itself.
     */
    INetClient disconnect();

    /**
     * Checks if the client is currently connected to the remote server.
     *
     * @return {@code true} if connected, {@code false} otherwise.
     */
    boolean isConnected();

    /**
     * Sends a message to the remote server through the established connection.
     *
     * @param message The message to send (encoded as string).
     * @return Itself.
     */
    INetClient send( String message );

    /**
     * Registers a listener to receive events from this network client.
     * Multiple listeners can be added and will all receive events.
     *
     * @param rl The listener to register.
     * @return {@code true} if the listener was added successfully, {@code false} if it was already registered.
     */
    boolean add( IListener rl );

    /**
     * Removes a previously registered listener from this network client.
     *
     * @param rl The listener to remove.
     * @return {@code true} if the listener was removed successfully, {@code false} if it was not registered.
     */
    boolean remove( IListener rl );
}