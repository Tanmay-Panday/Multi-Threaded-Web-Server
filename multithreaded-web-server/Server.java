import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Server {
    private final int port;
    private final ExecutorService threadPool;
    private volatile boolean isRunning = true;
    private ServerSocket serverSocket;
    private final ProxyServerUI ui; // Add UI reference

    public Server(int port, ProxyServerUI ui) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(10); // Use a fixed thread pool size
        this.ui = ui;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            ui.addLog("SERVER", "Server started on port " + port, "SYSTEM", "SUCCESS"); // Log to UI

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(() -> handleClient(clientSocket));
                } catch (SocketException e) {
                    if (!isRunning) {
                        // Expected exception when stopping the server
                        break;
                    } else {
                        ui.addLog("SERVER ERROR", "Socket error", "SYSTEM", "ERROR: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if (isRunning) { // Only log if not intentionally stopped
                ui.addLog("SERVER ERROR", "Failed to start server", "SYSTEM", "ERROR: " + e.getMessage());
            }
        } finally {
            stop(); // Ensure resources are cleaned up if start loop exits
        }
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                ui.addLog("SERVER", "Server stopped", "SYSTEM", "INFO"); // Log to UI
            }
        } catch (IOException e) {
            ui.addLog("SERVER ERROR", "Error stopping server", "SYSTEM", "ERROR: " + e.getMessage());
        } finally {
            threadPool.shutdownNow(); // Force shutdown of all running tasks
            ui.addLog("SERVER", "Server thread pool shut down", "SYSTEM", "INFO");
        }
    }

    private void handleClient(Socket socket) {
        long startTime = System.currentTimeMillis();
        String clientIP = socket.getInetAddress().getHostAddress();

        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
        ) {
            StringBuilder request = new StringBuilder();
            String line;

            // Read HTTP request headers
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                request.append(line).append("\n");
            }

            String requestLine = request.toString().split("\n")[0];
            String method = requestLine.split(" ")[0];
            String path = requestLine.split(" ")[1];

            ui.addLog("SERVER", path, clientIP, "RECEIVED REQUEST: " + method);

            String responseBody;
            if (path.startsWith("/api/data")) {
                if (method.equals("GET")) {
                    String id = path.contains("id=") ? path.split("id=")[1] : "unknown";
                    responseBody = "{\"data\":\"Response for ID " + id + "\"}";
                } else if (method.equals("POST")) {
                    responseBody = "{\"status\":\"success\",\"message\":\"Data received\"}";
                } else {
                    responseBody = "{\"error\":\"Unsupported method\"}";
                }
            } else if (path.startsWith("/static/")) {
                String filename = path.substring("/static/".length());
                responseBody = "<html><body><h1>Static file: " + filename + "</h1></body></html>";
            } else {
                responseBody = "<html><body><h1>Hello from Server</h1><p>Path: " + path + "</p></body></html>";
            }

             String httpResponse = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + (path.endsWith(".json") || path.contains("/api") ? "application/json" : "text/html") + "\r\n"
                + "Content-Length: " + responseBody.length() + "\r\n"
                + "Cache-Control: max-age=60\r\n" // Add cache control header
                + "Connection: close\r\n"
                + "\r\n"
                + responseBody;
                
            out.write(httpResponse);
            out.flush();
            ui.addLog("SERVER", path, clientIP, "SENT RESPONSE");

        } catch (IOException e) {
            ui.addLog("SERVER ERROR", "Client handling error", clientIP, "ERROR: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
            long duration = System.currentTimeMillis() - startTime;
            // System.out.println("Handled in " + duration + " ms"); // Removed direct console print
        }
    }
}
