
package com.peyrona.mingle.lang.interfaces.network;

/**
 * Interface that every communications server must implement.
 *
 * @author Francisco José Morero Peyrona
 *
 * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
 */
public interface INetServer
{
    /**
     * The listener that receives the messages.
     *
     * @author Francisco José Morero Peyrona
     *
     * Official web site at: <a href="https://github.com/peyrona/mingle">https://github.com/peyrona/mingle</a>
     */
    public interface IListener
    {
        void onConnected(    INetServer server, INetClient client );
        void onDisconnected( INetServer server, INetClient client );
        void onMessage(      INetServer server, INetClient client, String msg );
        void onError(        INetServer server, INetClient client, Exception err );
    }

    /**
     * Starts a Socket server with specified configuration.<br>
     * When both 'sCertPath' and 'sKeyFilePath' are passed and are valid, SSL is used,
     * otherwise normal connection is used.
     *
     * @param sCfgAsJSON A JSON object ([key,value] pairs) encoded as string. This is the
     *                   information needed by the controller to properly start the server.
     * @return Itself.
     */
    INetServer start( String sCfgAsJSON );
    INetServer stop();
    INetServer broadcast( String message );
    boolean    isRunning();
    boolean    add( IListener rl );
    boolean    remove( IListener rl );
    boolean    hasClients();
}