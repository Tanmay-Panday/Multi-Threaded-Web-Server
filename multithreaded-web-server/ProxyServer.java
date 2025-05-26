import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProxyServer {
    private final ExecutorService threadPool;
    private volatile boolean isRunning = true;
    private ServerSocket serverSocket;
    private final int proxyPort;
    private final String targetHost;
    private final int targetPort;
    private final LRUCache cache;
    private final ProxyServerUI ui;

    private static class LRUCache extends LinkedHashMap<String, String> {
        private final int capacity;

        public LRUCache(int capacity) {
            super(capacity, 0.75f, true);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > capacity;
        }
    }

    public ProxyServer(int proxyPort, String targetHost, int targetPort, int cacheCapacity, ProxyServerUI ui) {
        this.threadPool = Executors.newFixedThreadPool(10);
        this.proxyPort = proxyPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.cache = new LRUCache(cacheCapacity);
        this.ui = ui;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(proxyPort);
            ui.addLog("PROXY", "Proxy started on port " + proxyPort, "SYSTEM", "SUCCESS");

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(() -> handleClient(clientSocket));
                } catch (SocketException e) {
                    if (!isRunning) break;
                    ui.addLog("PROXY ERROR", "Socket error", "SYSTEM", "ERROR: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                ui.addLog("PROXY ERROR", "Failed to start proxy", "SYSTEM", "ERROR: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        String clientIP = clientSocket.getInetAddress().getHostAddress();
        
        try (
            BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter clientOut = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            // Read request line
            String requestLine = clientIn.readLine();
            if (requestLine == null) return;
            
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) return;
            
            String method = requestParts[0];
            String path = requestParts[1];
            
            // Read headers
            StringBuilder headers = new StringBuilder();
            String line;
            while ((line = clientIn.readLine()) != null && !line.isEmpty()) {
                headers.append(line).append("\n");
            }
            
            // Update total requests
            ui.incrementTotalRequests();
            
            // Create cache key
            String cacheKey = method + " " + path;
            
            // Check cache for GET requests
            if (method.equalsIgnoreCase("GET")) {
                String cachedResponse = cache.get(cacheKey);
                if (cachedResponse != null) {
                    ui.incrementCacheHits();
                    ui.addLog("CACHE HIT", path, clientIP, "SERVED FROM CACHE");
                    clientOut.println(cachedResponse);
                    clientOut.flush();
                    return;
                }
            }
            
            ui.incrementCacheMisses();
            ui.addLog("CACHE MISS", path, clientIP, "FORWARDING TO SERVER");

            // Forward request to server
            try (Socket targetSocket = new Socket(targetHost, targetPort);
                 PrintWriter targetOut = new PrintWriter(targetSocket.getOutputStream(), true);
                 BufferedReader targetIn = new BufferedReader(new InputStreamReader(targetSocket.getInputStream()))) {

                // Forward request
                targetOut.println(requestLine);
                targetOut.println(headers.toString());
                targetOut.println();
                targetOut.flush();

                // Read response
                StringBuilder response = new StringBuilder();
                String responseLine = targetIn.readLine();
                if (responseLine == null) return;
                
                response.append(responseLine).append("\n");
                
                // Read headers
                while ((line = targetIn.readLine()) != null && !line.isEmpty()) {
                    response.append(line).append("\n");
                }
                
                // Read body
                response.append("\n");
                while ((line = targetIn.readLine()) != null) {
                    response.append(line).append("\n");
                }

                String responseStr = response.toString();
                
                // Cache GET responses
                if (method.equalsIgnoreCase("GET") && responseLine.contains("200 OK")) {
                    cache.put(cacheKey, responseStr);
                    ui.addLog("FORWARDED", path, clientIP, "RESPONSE CACHED");
                }
                
                // Send response to client
                clientOut.println(responseStr);
                clientOut.flush();
            }
        } catch (Exception e) {
            ui.addLog("PROXY ERROR", "Error handling client", clientIP, "ERROR: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                ui.addLog("PROXY", "Proxy stopped", "SYSTEM", "INFO");
            }
        } catch (IOException e) {
            ui.addLog("PROXY ERROR", "Error stopping proxy", "SYSTEM", "ERROR: " + e.getMessage());
        }
        threadPool.shutdown();
    }
}