import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ProxyServerUI extends JFrame {
    // Configuration components
    private JSpinner totalRequestsSpinner; // Changed to totalRequestsSpinner
    private JSpinner numLoopsSpinner;      // Added numLoopsSpinner
    private JSpinner proxyPortSpinner;
    private JSpinner serverPortSpinner;
    private JComboBox<String> distributionCombo;

    // Control buttons
    private JButton startProxyBtn;
    private JButton stopProxyBtn;
    private JButton startServerBtn;
    private JButton stopServerBtn;
    private JButton runTestBtn;
    private JButton clearLogsBtn;

    // Status labels
    private JLabel proxyStatusLabel;
    private JLabel serverStatusLabel;

    // Metrics components
    private JLabel throughputLabel;
    private JLabel cacheHitRateLabel;
    private JLabel totalRequestsLabel;
    private JLabel cacheHitsLabel;
    private JLabel cacheMissesLabel;

    // Logs table
    private DefaultTableModel logsTableModel;
    private JTable logsTable;
    private JScrollPane logsScrollPane;

    // Server instances
    private ProxyServer proxyServer;
    private Server mainServer;
    private Thread proxyThread;
    private Thread serverThread;

    // Metrics
    private AtomicInteger totalRequests = new AtomicInteger(0);
    private AtomicInteger cacheHits = new AtomicInteger(0);
    private AtomicInteger cacheMisses = new AtomicInteger(0);
    private AtomicLong startTime = new AtomicLong(0);
    private Timer metricsTimer;

    public ProxyServerUI() {
        initializeUI();
        setupEventListeners();
        resetMetrics();
        
        // Start metrics update timer (every second)
        metricsTimer = new Timer(1000, e -> updateMetrics());
        metricsTimer.start();
    }

    private void initializeUI() {
        setTitle("Proxy Server Monitor & Control");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(1000, 700));

        // Create main panels
        JPanel topPanel = createConfigurationPanel();
        JPanel centerPanel = createControlAndMetricsPanel();
        JPanel bottomPanel = createLogsPanel();

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createConfigurationPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Total Requests to Generate
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Total Requests to Generate:"), gbc);
        gbc.gridx = 1;
        totalRequestsSpinner = new JSpinner(new SpinnerNumberModel(1000, 1, 100000, 100)); // Default 1000, max 100000
        panel.add(totalRequestsSpinner, gbc);

        // Number of Loops
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Number of Loops:"), gbc);
        gbc.gridx = 1;
        numLoopsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1)); // Default 1, max 10 loops
        panel.add(numLoopsSpinner, gbc);

        // Proxy Port
        gbc.gridx = 2; gbc.gridy = 0;
        panel.add(new JLabel("Proxy Port:"), gbc);
        gbc.gridx = 3;
        proxyPortSpinner = new JSpinner(new SpinnerNumberModel(9000, 1024, 65535, 1));
        panel.add(proxyPortSpinner, gbc);

        // Server Port
        gbc.gridx = 4; gbc.gridy = 0;
        panel.add(new JLabel("Server Port:"), gbc);
        gbc.gridx = 5;
        serverPortSpinner = new JSpinner(new SpinnerNumberModel(8010, 1024, 65535, 1));
        panel.add(serverPortSpinner, gbc);

        // Distribution
        gbc.gridx = 2; gbc.gridy = 1; gbc.gridwidth = 4; // Adjusted gridwidth
        panel.add(new JLabel("Request Distribution:"), gbc);
        gbc.gridx = 4; gbc.gridy = 1; gbc.gridwidth = 2;
        distributionCombo = new JComboBox<>(new String[]{"Uniform", "Skewed (80/20)"});
        panel.add(distributionCombo, gbc);
        
        return panel;
    }

    private JPanel createControlAndMetricsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Control Panel
        JPanel controlPanel = new JPanel(new GridLayout(2, 3, 10, 10));
        controlPanel.setBorder(new TitledBorder("Server Control"));

        startProxyBtn = new JButton("Start Proxy");
        stopProxyBtn = new JButton("Stop Proxy");
        stopProxyBtn.setEnabled(false);

        startServerBtn = new JButton("Start Server");
        stopServerBtn = new JButton("Stop Server");
        stopServerBtn.setEnabled(false);

        runTestBtn = new JButton("Run Performance Test");

        proxyStatusLabel = new JLabel("Proxy: Stopped", JLabel.CENTER);
        proxyStatusLabel.setForeground(Color.RED);
        serverStatusLabel = new JLabel("Server: Stopped", JLabel.CENTER);
        serverStatusLabel.setForeground(Color.RED);

        controlPanel.add(startProxyBtn);
        controlPanel.add(stopProxyBtn);
        controlPanel.add(proxyStatusLabel);
        controlPanel.add(startServerBtn);
        controlPanel.add(stopServerBtn);
        controlPanel.add(serverStatusLabel);

        // Metrics Panel
        JPanel metricsPanel = new JPanel(new GridLayout(5, 1, 10, 10));
        metricsPanel.setBorder(new TitledBorder("Performance Metrics"));

        throughputLabel = new JLabel("Throughput: 0 req/s");
        cacheHitRateLabel = new JLabel("Cache Hit Rate: 0%");
        totalRequestsLabel = new JLabel("Total Requests: 0");
        cacheHitsLabel = new JLabel("Cache Hits: 0");
        cacheMissesLabel = new JLabel("Cache Misses: 0");

        metricsPanel.add(throughputLabel);
        metricsPanel.add(cacheHitRateLabel);
        metricsPanel.add(totalRequestsLabel);
        metricsPanel.add(cacheHitsLabel);
        metricsPanel.add(cacheMissesLabel);

        panel.add(controlPanel, BorderLayout.NORTH);
        panel.add(metricsPanel, BorderLayout.CENTER);
        panel.add(runTestBtn, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Real-Time Logs"));
        panel.setPreferredSize(new Dimension(800, 250));

        String[] columnNames = {"Timestamp", "Type", "URL/Path", "Source", "Status"};
        logsTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        logsTable = new JTable(logsTableModel);
        logsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        logsScrollPane = new JScrollPane(logsTable);
        logsScrollPane.setPreferredSize(new Dimension(800, 200));

        clearLogsBtn = new JButton("Clear Logs");
        clearLogsBtn.addActionListener(e -> clearLogs());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(clearLogsBtn);

        panel.add(logsScrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void setupEventListeners() {
        startProxyBtn.addActionListener(e -> startProxy());
        stopProxyBtn.addActionListener(e -> stopProxy());
        startServerBtn.addActionListener(e -> startServer());
        stopServerBtn.addActionListener(e -> stopServer());
        runTestBtn.addActionListener(e -> runTest());
    }

    private void startProxy() {
        try {
            int proxyPort = (Integer) proxyPortSpinner.getValue();
            int serverPort = (Integer) serverPortSpinner.getValue();

            proxyServer = new ProxyServer(proxyPort, "localhost", serverPort, 1000, this);
            proxyThread = new Thread(() -> proxyServer.start());
            proxyThread.start();

            startProxyBtn.setEnabled(false);
            stopProxyBtn.setEnabled(true);
            proxyStatusLabel.setText("Proxy: Running on port " + proxyPort);
            proxyStatusLabel.setForeground(new Color(0, 150, 0)); // Dark green

            addLog("SYSTEM", "Proxy started on port " + proxyPort, "SYSTEM", "SUCCESS");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to start proxy: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
            addLog("SYSTEM ERROR", "Proxy failed to start", "SYSTEM", "ERROR: " + e.getMessage());
        }
    }

    private void stopProxy() {
        if (proxyServer != null) {
            proxyServer.stop();
        }

        startProxyBtn.setEnabled(true);
        stopProxyBtn.setEnabled(false);
        proxyStatusLabel.setText("Proxy: Stopped");
        proxyStatusLabel.setForeground(Color.RED);

        addLog("SYSTEM", "Proxy stopped", "SYSTEM", "INFO");
    }

    private void startServer() {
        try {
            int serverPort = (Integer) serverPortSpinner.getValue();

            mainServer = new Server(serverPort, this);
            serverThread = new Thread(() -> mainServer.start());
            serverThread.start();

            startServerBtn.setEnabled(false);
            stopServerBtn.setEnabled(true);
            serverStatusLabel.setText("Server: Running on port " + serverPort);
            serverStatusLabel.setForeground(new Color(0, 150, 0)); // Dark green

            addLog("SYSTEM", "Server started on port " + serverPort, "SYSTEM", "SUCCESS");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to start server: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
            addLog("SYSTEM ERROR", "Server failed to start", "SYSTEM", "ERROR: " + e.getMessage());
        }
    }

    private void stopServer() {
        if (mainServer != null) {
            mainServer.stop();
        }

        startServerBtn.setEnabled(true);
        stopServerBtn.setEnabled(false);
        serverStatusLabel.setText("Server: Stopped");
        serverStatusLabel.setForeground(Color.RED);

        addLog("SYSTEM", "Server stopped", "SYSTEM", "INFO");
    }

    // Corrected runTest method in ProxyServerUI.java
    private void runTest() {
        if (proxyServer == null || mainServer == null) {
            JOptionPane.showMessageDialog(this,
                "Please start both proxy and server before running tests!",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        resetMetrics(); 
        
        int totalRequestsToGenerate = (Integer) totalRequestsSpinner.getValue(); // Get total requests
        int numLoops = (Integer) numLoopsSpinner.getValue(); // Get number of loops

        int proxyPort = (Integer) proxyPortSpinner.getValue();
        boolean isSkewed = distributionCombo.getSelectedIndex() == 1;

        ExecutorService testExecutor = Executors.newSingleThreadExecutor();
        testExecutor.execute(() -> {
            Client client = new Client("localhost", proxyPort, this);
            // Pass the new parameters to Client.runTest
            client.runTest(totalRequestsToGenerate, 60, numLoops, isSkewed); // Assuming 60 seconds duration for requests
                                                                             // This Client.runTest signature needs to be implemented

            SwingUtilities.invokeLater(() -> {
                addLog("SYSTEM", "Test completed: " + totalRequestsToGenerate + " requests per 60s for " + numLoops + " loops",
                    "SYSTEM", "INFO");
            });
        });
        testExecutor.shutdown();
        
        addLog("SYSTEM", "Starting test: " + totalRequestsToGenerate + " requests per 60s for " + numLoops + " loops (" +
            (isSkewed ? "skewed" : "uniform") + " distribution)", "SYSTEM", "INFO");
    }

    public void resetMetrics() {
        totalRequests.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        startTime.set(System.currentTimeMillis());
        updateMetrics(); // Call updateMetrics to refresh the UI immediately
    }

    private void clearLogs() {
        logsTableModel.setRowCount(0);
        addLog("SYSTEM", "Logs cleared", "SYSTEM", "INFO");
    }

    public void addLog(String type, String url, String source, String status) {
        SwingUtilities.invokeLater(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            String timestamp = sdf.format(new Date());
            logsTableModel.addRow(new Object[]{timestamp, type, url, source, status});

            // Auto-scroll to bottom
            int rowCount = logsTableModel.getRowCount();
            if (rowCount > 0) {
                logsTable.scrollRectToVisible(logsTable.getCellRect(rowCount - 1, 0, true));
            }

            // Limit log entries
            if (rowCount > 1000) {
                logsTableModel.removeRow(0);
            }
        });
    }

    public void incrementTotalRequests() {
        totalRequests.incrementAndGet();
    }

    public void incrementCacheHits() {
        cacheHits.incrementAndGet();
    }

    public void incrementCacheMisses() {
        cacheMisses.incrementAndGet();
    }

    private void updateMetrics() {
        SwingUtilities.invokeLater(() -> {
            int total = totalRequests.get();
            int hits = cacheHits.get();
            int misses = cacheMisses.get();
            long elapsed = Math.max(1, System.currentTimeMillis() - startTime.get());

            // Calculate metrics
            double hitRate = total > 0 ? (hits * 100.0 / total) : 0;
            double throughput = total * 1000.0 / elapsed;

            // Update UI
            totalRequestsLabel.setText("Total Requests: " + total);
            cacheHitsLabel.setText("Cache Hits: " + hits);
            cacheMissesLabel.setText("Cache Misses: " + misses);
            cacheHitRateLabel.setText(String.format("Cache Hit Rate: %.1f%%", hitRate));
            throughputLabel.setText(String.format("Throughput: %.1f req/s", throughput));
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new ProxyServerUI().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}