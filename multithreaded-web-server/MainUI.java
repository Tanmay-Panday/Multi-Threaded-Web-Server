import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.OutputStream;
import java.io.PrintStream;

public class MainUI extends JFrame {
    private JTextArea logArea;
    private JTextField clientCountField;

    public MainUI() {
        setTitle("Client-Proxy-Server Controller");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new FlowLayout());

        JButton startServerButton = new JButton("Start Server");
        JButton startProxyButton = new JButton("Start Proxy");
        JButton startClientButton = new JButton("Start Clients");
        clientCountField = new JTextField("100", 5);

        controlPanel.add(startServerButton);
        controlPanel.add(startProxyButton);
        controlPanel.add(new JLabel("Client Count:"));
        controlPanel.add(clientCountField);
        controlPanel.add(startClientButton);

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        add(mainPanel);

        // Redirect console output to logArea
        PrintStream printStream = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append(String.valueOf((char) b));
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            }
        });
        System.setOut(printStream);
        System.setErr(printStream);

        // Action Listeners
        startServerButton.addActionListener((ActionEvent e) -> runInThread(() -> Server.main(new String[]{})));

        startProxyButton.addActionListener((ActionEvent e) -> runInThread(() -> ProxyServer.main(new String[]{})));

        startClientButton.addActionListener((ActionEvent e) -> {
            runInThread(() -> {
                try {
                    int numClients = Integer.parseInt(clientCountField.getText().trim());
                    for (int i = 0; i < numClients; i++) {
                        int clientId = i;
                        new Thread(new Client().getClientRunnable(clientId)).start();
                    }
                } catch (NumberFormatException ex) {
                    System.err.println("Invalid number of clients.");
                }
            });
        });

        setVisible(true);
    }

    private void runInThread(Runnable runnable) {
        new Thread(runnable).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainUI::new);
    }
}
