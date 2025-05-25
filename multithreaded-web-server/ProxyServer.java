import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ProxyServer {
    private final ExecutorService threadPool;
    private volatile boolean isRunning = true;
    private ServerSocket serverSocket;
    private final int proxyPort;
    private final String targetHost;
    private final int targetPort;

    public ProxyServer(int proxyPort, String targetHost, int targetPort) {
        this.threadPool = Executors.newFixedThreadPool(10);
        this.proxyPort = proxyPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(proxyPort);
            System.out.println("Proxy Server started on port " + proxyPort);
            System.out.println("Forwarding requests to " + targetHost + ":" + targetPort);

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
            Socket targetSocket = new Socket(targetHost, targetPort);
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();
            InputStream targetIn = targetSocket.getInputStream();
            OutputStream targetOut = targetSocket.getOutputStream()
        ) {
            Thread forwardClient = new Thread(() -> forwardData(clientIn, targetOut, "Client -> Server"));
            Thread forwardServer = new Thread(() -> forwardData(targetIn, clientOut, "Server -> Client"));
            forwardClient.start();
            forwardServer.start();
            forwardClient.join();
            forwardServer.join();
        } catch (Exception e) {
            System.out.println("[Proxy Error] " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private void forwardData(InputStream in, OutputStream out, String direction) {
        try {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                String data = new String(buffer, 0, bytesRead);
                if (!data.trim().isEmpty()) {
                    System.out.println(direction + ": " + data);
                }
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (IOException e) {
            System.out.println(direction + " connection closed.");
        }
    }

    public static void main(String[] args) {
        int proxyPort = 9000;
        String targetHost = "localhost";
        int targetPort = 8010;
        new ProxyServer(proxyPort, targetHost, targetPort).start();
    }
}
