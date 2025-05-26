import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.Random;

public class Client {
    private final String serverHost;
    private final int serverPort;
    private final ProxyServerUI ui;
    private final Random random = new Random();

    public Client(String serverHost, int serverPort, ProxyServerUI ui) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.ui = ui;
    }

    // Modified runTest method to generate a specific number of requests within a duration, repeated for loops
    public void runTest(int totalRequestsToGenerate, int durationPerLoopSeconds, int numLoops, boolean isSkewed) {
        ui.resetMetrics(); // Reset metrics at the beginning of the entire test
        
        // Define a thread pool for concurrently sending requests
        ExecutorService executor = Executors.newFixedThreadPool(50); // Increased thread pool for potentially higher concurrent requests

        for (int loop = 0; loop < numLoops; loop++) {
            ui.addLog("CLIENT", "Starting Loop " + (loop + 1) + " of " + numLoops, "SYSTEM", "INFO");
            long loopStartTime = System.currentTimeMillis();
            long loopEndTime = loopStartTime + (durationPerLoopSeconds * 1000L); // End time for the current loop
            
            // Calculate target delay per request to achieve the desired rate
            // This is an average target; actual timing will vary due to network/server response
            long targetDelayPerRequestMillis = (durationPerLoopSeconds * 1000L) / totalRequestsToGenerate;
            
            for (int i = 0; i < totalRequestsToGenerate; i++) {
                // Check if the loop time has expired before sending the next request
                if (System.currentTimeMillis() >= loopEndTime) {
                    ui.addLog("CLIENT", "Loop " + (loop + 1) + " time expired, stopping early.", "SYSTEM", "WARNING");
                    break; 
                }

                final int clientId = random.nextInt(10000); // Use random client IDs
                executor.execute(() -> {
                    try (Socket socket = new Socket(serverHost, serverPort);
                         PrintWriter toSocket = new PrintWriter(socket.getOutputStream(), true);
                         BufferedReader fromSocket = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                        String request = generateRequest(clientId, isSkewed);
                        String path = extractPath(request);
                        
                        ui.addLog("CLIENT", path, "Client-" + clientId, "SENDING REQUEST");
                        
                        toSocket.println(request);

                        // Read response
                        StringBuilder response = new StringBuilder();
                        String line;
                        // Read until the end of the response or a reasonable timeout
                        long responseReadStartTime = System.currentTimeMillis();
                        while ((line = fromSocket.readLine()) != null) {
                            response.append(line).append("\n");
                            // Basic heuristic for end of headers. For robust parsing,
                            // Content-Length or Transfer-Encoding headers would be used.
                            if (line.isEmpty()) { 
                                break; 
                            }
                            if (System.currentTimeMillis() - responseReadStartTime > 5000) { // 5 second timeout for reading response
                                ui.addLog("CLIENT WARNING", "Response read timeout", "Client-" + clientId, "WARNING");
                                break;
                            }
                        }

                        ui.addLog("CLIENT", path, "Client-" + clientId, "RECEIVED RESPONSE");

                    } catch (ConnectException e) {
                        ui.addLog("CLIENT ERROR", "Connection refused", "Client-" + clientId, "CONNECTION FAILED: " + e.getMessage());
                    } catch (SocketException e) {
                        if (e.getMessage().contains("Connection reset") || e.getMessage().contains("Broken pipe")) {
                            ui.addLog("CLIENT WARNING", "Connection closed prematurely", "Client-" + clientId, "WARNING: " + e.getMessage());
                        } else {
                            ui.addLog("CLIENT ERROR", "Socket Error", "Client-" + clientId, "ERROR: " + e.getMessage());
                        }
                    } catch (IOException e) {
                        ui.addLog("CLIENT ERROR", "IO Error", "Client-" + clientId, "ERROR: " + e.getMessage());
                    }
                });
                
                // Introduce a delay to pace the requests for the current loop
                long elapsedInLoop = System.currentTimeMillis() - loopStartTime;
                long expectedElapsedForCurrentRequest = (long) (i + 1) * targetDelayPerRequestMillis;
                long sleepTime = expectedElapsedForCurrentRequest - elapsedInLoop;

                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        ui.addLog("CLIENT ERROR", "Client interrupted during sleep", "SYSTEM", "ERROR: " + e.getMessage());
                        return; // Exit if interrupted
                    }
                }
            }

            // Ensure the loop runs for at least `durationPerLoopSeconds` if not all requests were sent or if requests finished early
            long remainingLoopTime = loopEndTime - System.currentTimeMillis();
            if (remainingLoopTime > 0) {
                try {
                    ui.addLog("CLIENT", "Waiting for loop " + (loop + 1) + " to complete its duration.", "SYSTEM", "INFO");
                    Thread.sleep(remainingLoopTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    ui.addLog("CLIENT ERROR", "Client interrupted during loop wait", "SYSTEM", "ERROR: " + e.getMessage());
                    return;
                }
            }
            ui.addLog("CLIENT", "Finished Loop " + (loop + 1) + " of " + numLoops, "SYSTEM", "INFO");
        }
        
        executor.shutdown();
        try {
            // Give a generous timeout for all pending tasks to complete
            if (!executor.awaitTermination(300, TimeUnit.SECONDS)) { 
                executor.shutdownNow(); // Forcefully shut down if not terminated
                ui.addLog("CLIENT WARNING", "Executor did not terminate within timeout", "SYSTEM", "Some client tasks may not have finished.");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt(); // Restore the interrupted status
            ui.addLog("CLIENT ERROR", "Executor termination interrupted", "SYSTEM", "ERROR: " + e.getMessage());
        }
        ui.addLog("SYSTEM", "Client test run completed for all loops.", "SYSTEM", "INFO");
    }

    private String generateRequest(int clientId, boolean isSkewed) {
        int requestType;
        
        if (isSkewed) {
            // 80/20 distribution - 80% of requests go to 20% of types (types 0 and 1)
            if (random.nextDouble() < 0.8) {
                requestType = random.nextInt(2); // 0 or 1
            } else {
                requestType = 2 + random.nextInt(2); // 2 or 3
            }
        } else {
            // Uniform distribution
            requestType = random.nextInt(4);
        }
        
        switch (requestType) {
            case 0:
                return "GET /index.html HTTP/1.1\nHost: " + serverHost + "\n\n";
            case 1:
                return "GET /api/data?id=" + clientId + " HTTP/1.1\nHost: " + serverHost + "\n\n";
            case 2:
                return "POST /api/data HTTP/1.1\nHost: " + serverHost + "\nContent-Length: 12\n\n{\"id\":" + clientId + "}";
            case 3:
                return "GET /static/image" + (clientId % 5) + ".jpg HTTP/1.1\nHost: " + serverHost + "\n\n";
            default:
                return "GET / HTTP/1.1\nHost: " + serverHost + "\n\n";
        }
    }
    
    private String extractPath(String request) {
        String[] lines = request.split("\n");
        if (lines.length > 0) {
            String[] parts = lines[0].split(" ");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return "Unknown";
    }
}