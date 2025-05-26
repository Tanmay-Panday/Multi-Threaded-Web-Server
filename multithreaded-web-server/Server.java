import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 8010;
    private static final int THREAD_POOL_SIZE = 10;

    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Main Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }

    private static void handleClient(Socket socket) {
        long startTime = System.currentTimeMillis();

        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
        ) {
            StringBuilder request = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null && !line.isEmpty()) {
                request.append(line).append("\n");
            }

            System.out.println("Received Request:\n" + request);

            // Plain text response
            String responseBody = "Hello from Server";
            String httpResponse = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: " + responseBody.length() + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n"
                    + responseBody;

            out.write(httpResponse);
            out.flush();

        } catch (IOException e) {
            System.err.println("Client handling error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("Handled in " + duration + " ms");
        }
    }
}
