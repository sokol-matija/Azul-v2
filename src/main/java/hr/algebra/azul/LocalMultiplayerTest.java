// File: src/test/java/hr/algebra/azul/LocalMultiplayerTest.java
package hr.algebra.azul;

import hr.algebra.azul.network.GameServer;
import javafx.application.Application;
import javafx.stage.Stage;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class LocalMultiplayerTest extends Application {
    private static final Logger LOGGER = Logger.getLogger(LocalMultiplayerTest.class.getName());
    private static final CountDownLatch serverStartLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        // Start the server in a separate thread
        Thread serverThread = new Thread(() -> {
            GameServer server = new GameServer();
            serverStartLatch.countDown(); // Signal that server is ready
            server.start();
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Launch multiple client instances
        Thread[] clientThreads = new Thread[2]; // Launch 2 clients
        for (int i = 0; i < clientThreads.length; i++) {
            final int clientIndex = i;
            clientThreads[i] = new Thread(() -> {
                try {
                    // Wait for server to start
                    serverStartLatch.await();
                    // Add some delay between client launches to prevent UI conflicts
                    Thread.sleep(1000 * clientIndex);
                    // Launch client
                    launch(LocalMultiplayerTest.class,
                            "--client-index=" + clientIndex);
                } catch (Exception e) {
                    LOGGER.severe("Error launching client " + clientIndex + ": " + e.getMessage());
                }
            });
            clientThreads[i].setDaemon(true);
        }

        // Start clients
        for (Thread clientThread : clientThreads) {
            clientThread.start();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        // Extract client index from parameters
        Parameters params = getParameters();
        String clientIndex = params.getNamed().getOrDefault("client-index", "0");

        // Create and start the AzulApplication
        AzulApplication client = new AzulApplication();
        try {
            // Set window title to distinguish different clients
            primaryStage.setTitle("Azul Client " + clientIndex);
            // Position windows side by side
            primaryStage.setX(Integer.parseInt(clientIndex) * 820); // Width + 20px margin
            primaryStage.setY(50);

            client.start(primaryStage);
        } catch (Exception e) {
            LOGGER.severe("Error starting client " + clientIndex + ": " + e.getMessage());
        }
    }
}