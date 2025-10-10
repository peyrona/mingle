
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
    INetClient disconnect();
    boolean    isConnected();
    INetClient send( String message );
    boolean    add( IListener rl );
    boolean    remove( IListener rl );
}