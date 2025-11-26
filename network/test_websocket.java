import com.peyrona.mingle.network.websocket.WebSocketServer;
import com.peyrona.mingle.lang.interfaces.network.INetServer;

public class test_websocket {
    public static void main(String[] args) {
        try {
            WebSocketServer server = new WebSocketServer();
            System.out.println("WebSocket server created successfully");
            
            INetServer started = server.start(null);
            System.out.println("WebSocket server started: " + (started != null));
            
            Thread.sleep(1000);
            
            server.stop();
            System.out.println("WebSocket server stopped successfully");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}