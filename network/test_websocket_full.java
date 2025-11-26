import com.peyrona.mingle.network.websocket.WebSocketServer;
import com.peyrona.mingle.network.websocket.WebSocketClient;
import com.peyrona.mingle.lang.interfaces.network.INetServer;
import com.peyrona.mingle.lang.interfaces.network.INetClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class test_websocket_full {
    public static void main(String[] args) {
        try {
            CountDownLatch latch = new CountDownLatch(2);
            WebSocketServer server = new WebSocketServer();
            WebSocketClient client = new WebSocketClient();
            
            // Add listeners
            server.add(new INetServer.IListener() {
                public void onConnected(INetServer server, INetClient client) {
                    System.out.println("Server: Client connected!");
                    latch.countDown();
                }
                public void onDisconnected(INetServer server, INetClient client) {
                    System.out.println("Server: Client disconnected!");
                }
                public void onMessage(INetServer server, INetClient client, String msg) {
                    System.out.println("Server: message arrived: " + msg);
                    latch.countDown();
                }
                public void onError(INetServer server, INetClient client, Exception e) {
                    System.out.println("Server: Error: " + e);
                }
            });
            
            client.add(new INetClient.IListener() {
                public void onConnected(INetClient client) {
                    System.out.println("Client: Connected to server!");
                    client.send("Hello from WebSocket Client!");
                }
                public void onDisconnected(INetClient client) {
                    System.out.println("Client: Disconnected from server!");
                }
                public void onError(INetClient client, Exception e) {
                    System.out.println("Client: Error: " + e);
                }
                public void onMessage(INetClient client, String message) {
                    System.out.println("Client: message arrived: " + message);
                }
            });
            
            // Start server
            server.start(null);
            System.out.println("Server started");
            
            // Connect client
            client.connect(null);
            System.out.println("Client connecting...");
            
            // Broadcast message
            server.broadcast("Broadcast from WebSocket Server!");
            
            // Wait for events
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            System.out.println("Test completed: " + completed);
            
            // Cleanup
            server.stop();
            System.out.println("Server stopped");
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}