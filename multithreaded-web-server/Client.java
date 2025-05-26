import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Client {
    private static final int NUM_CLIENTS = 100;
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9000; // Connect to proxy

    public Runnable getClientRunnable(int clientId) {
        return () -> {
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 PrintWriter toSocket = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader fromSocket = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                toSocket.println("GET / HTTP/1.1");
                toSocket.println("Host: " + SERVER_HOST);
                toSocket.println();

                String line;
                while ((line = fromSocket.readLine()) != null) {
                    // Suppress or selectively log
                    if (line.contains("200 OK")) {
                        System.out.println("Client " + clientId + " received status: " + line);
                    }
                }

            } catch (ConnectException e) {
                System.err.println("Connection refused for Client " + clientId);
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
    }

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        Client client = new Client();
        for (int i = 0; i < NUM_CLIENTS; i++) {
            executor.execute(client.getClientRunnable(i));
        }
        executor.shutdown();
    }
}
