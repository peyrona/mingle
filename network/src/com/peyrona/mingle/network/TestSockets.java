package com.peyrona.mingle.network;

import com.peyrona.mingle.lang.MingleException;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.network.socket.SocketClient;
import com.peyrona.mingle.network.socket.SocketServer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Netty examples:
// https://github.com/netty/netty/tree/4.1/example/src/main/java/io/netty/example

final class TestSockets
{
    public static void main(String[] args)
    {
        try
        {
            runTests( "PlainSocket - MSP" );
            System.out.println( "\n============================================================\n" );
            runTests( "PlainSocket - Netty" );
            System.out.println( "\n============================================================\n" );
            runTests( "WebSocket   - Netty" );
        }
        catch(Exception e)
        {
            System.out.println( "Test execution failed: "+ e );
            System.exit(1);
        }
        finally
        {
            System.exit(0);
        }
    }

    private static void runTests( String type )
    {
        CountDownLatch latch = new CountDownLatch(2);
        INetServer server;
        INetClient client;

        if( type.contains( "MSP" ) )
        {
            server = new SocketServer();
            client = new SocketClient();
        }
        else
        {
            throw new MingleException( "Unknown socket type" );
        }

        server.add( createServerListener(latch, type ) );
        client.add( createClientListener(latch, type ) );

        try
        {
            server.start( null );
            client.connect( null );

            server.broadcast("Broadacst from "+ type +" Server!");

            // Wait for connection and message events
            boolean completed = latch.await( 5, TimeUnit.SECONDS );

            if(! completed)
                System.out.println( type +" test did not complete within expected time");
        }
        catch(Exception e)
        {
            throw new MingleException("WebSocket test failed: "+ e);
        }
    }

    // Reusable server listener creator
    private static INetServer.IListener createServerListener(CountDownLatch latch, String type)
    {
        return new INetServer.IListener()
        {
            @Override
            public void onConnected(INetServer server, INetClient client)
            {
                System.out.println( " Server "+ type +": Client connected!");
                latch.countDown();
            }

            @Override
            public void onDisconnected(INetServer server, INetClient client)
            {
                System.out.println( " Server "+ type +": Client disconnected!");
            }

            @Override
            public void onMessage(INetServer server, INetClient client, String msg)
            {
                System.out.println( " Server "+ type +": message arrived: " + msg);
                latch.countDown();
            }

            @Override
            public void onError(INetServer server, INetClient client, Exception e)
            {
                System.out.println( " Server "+ type +": Error: "+ e);
            }
        };
    }

    // Reusable client listener creator
    private static INetClient.IListener createClientListener(CountDownLatch latch, String type)
    {
        return new INetClient.IListener()
        {
            @Override
            public void onConnected(INetClient client)
            {
                System.out.println( " Client "+ type +": Connected to server!");
                client.send("Hello, msg sent from " + type + " Client!");
            }

            @Override
            public void onDisconnected(INetClient client)
            {
                System.out.println( " Client "+ type +": Disconnected from server!");
            }

            @Override
            public void onError(INetClient client, Exception e)
            {
                System.out.println( " Client "+ type +": Error: "+ e);
            }

            @Override
            public void onMessage(INetClient client, String message)
            {
                System.out.println( " Client "+ type +": message arrived: " + message);
            }
        };
    }
}