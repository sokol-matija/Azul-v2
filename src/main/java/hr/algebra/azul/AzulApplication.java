// File: src/main/java/hr/algebra/azul/AzulApplication.java
package hr.algebra.azul;

import hr.algebra.azul.controller.GameController;
import hr.algebra.azul.controller.LobbyController;
import hr.algebra.azul.network.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class AzulApplication extends Application {
    private static final Logger LOGGER = Logger.getLogger(AzulApplication.class.getName());
    private static final long CONNECTION_TIMEOUT = 10000; // 10 seconds

    private Stage primaryStage;
    private GameClient gameClient;
    private GameController gameController;
    private LobbyController lobbyController;
    private Scene lobbyScene;
    private Scene gameScene;
    private boolean isConnecting = false;
    private CompletableFuture<Boolean> connectionFuture;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            handleApplicationClose();
        });

        initializeNetworkConnection();
        showLobbyScreen();
    }

    private void initializeNetworkConnection() {
        // Create client instance
        gameClient = new GameClient(NetworkConfig.DEFAULT_HOST, NetworkConfig.DEFAULT_PORT);

        // Set up connection handler
        gameClient.setConnectionHandler(new ConnectionStatusHandler() {
            @Override
            public void onConnected() {
                Platform.runLater(() -> {
                    LOGGER.info("Connected to server");
                    if (isConnecting && connectionFuture != null && !connectionFuture.isDone()) {
                        connectionFuture.complete(true);
                    }
                });
            }

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> {
                    LOGGER.warning("Disconnected: " + reason);
                    if (!reason.equals("Client disconnected")) {
                        showAlert("Connection Status",
                                "Disconnected: " + reason,
                                Alert.AlertType.WARNING);
                    }
                    handleDisconnection();
                });
            }

            @Override
            public void onConnectionFailed(String reason) {
                Platform.runLater(() -> {
                    LOGGER.severe("Connection failed: " + reason);
                    if (isConnecting && connectionFuture != null && !connectionFuture.isDone()) {
                        connectionFuture.complete(false);
                    }
                });
            }
        });

        // Connect to server with timeout
        isConnecting = true;
        connectionFuture = new CompletableFuture<>();
        
        gameClient.connect()
            .orTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
            .exceptionally(throwable -> {
                Platform.runLater(() -> {
                    String errorMsg = throwable.getCause() instanceof java.util.concurrent.TimeoutException ?
                            "Connection timed out" : throwable.getMessage();
                    LOGGER.severe("Connection error: " + errorMsg);
                    showAlert("Connection Error",
                            "Failed to establish connection: " + errorMsg,
                            Alert.AlertType.ERROR);
                });
                return false;
            })
            .thenAccept(success -> {
                isConnecting = false;
                if (!success) {
                    Platform.runLater(() -> {
                        showAlert("Connection Failed",
                                "Could not connect to server",
                                Alert.AlertType.ERROR);
                    });
                }
            });
    }

    private void showLobbyScreen() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("lobby-view.fxml"));
        lobbyScene = new Scene(loader.load(), 800, 600);

        // Get and initialize the lobby controller
        lobbyController = loader.getController();
        lobbyController.setMainApp(this);
        gameClient.setLobbyHandler(lobbyController);
        lobbyController.initializeWithClient(gameClient);

        primaryStage.setTitle("Azul - Game Lobby");
        primaryStage.setScene(lobbyScene);
        primaryStage.show();
    }

    public void switchToGame(GameState gameState) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("game-view.fxml"));
        gameScene = new Scene(loader.load(), 1200, 800);

        // Get and initialize the game controller
        gameController = loader.getController();
        gameController.setMainApp(this);
        gameClient.setGameHandler(gameController);
        gameController.initializeNetworkedGame(gameState, gameClient, lobbyController.getPlayerId());

        primaryStage.setTitle("Azul - In Game");
        primaryStage.setScene(gameScene);
    }

    public void returnToLobby() {
        if (lobbyScene != null) {
            primaryStage.setScene(lobbyScene);
            primaryStage.setTitle("Azul - Game Lobby");
            if (lobbyController != null) {
                lobbyController.refreshLobbyView();
            }
        }
    }

    private void handleDisconnection() {
        if (gameController != null) {
            gameController.cleanup();
        }
        returnToLobby();
    }

    private void handleApplicationClose() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Exit");
        alert.setHeaderText("Are you sure you want to exit?");
        alert.setContentText("Any ongoing game progress will be lost.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            cleanup();
            Platform.exit();
        }
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }

    private void cleanup() {
        if (gameController != null) {
            gameController.cleanup();
        }
        if (gameClient != null) {
            gameClient.disconnect();
        }
        if (connectionFuture != null && !connectionFuture.isDone()) {
            connectionFuture.cancel(true);
        }
    }

    @Override
    public void stop() {
        cleanup();
    }

    // Getters
    public GameClient getGameClient() {
        return gameClient;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch();
    }
}
