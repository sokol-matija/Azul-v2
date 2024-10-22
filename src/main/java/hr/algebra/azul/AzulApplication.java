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
import java.util.logging.Logger;

public class AzulApplication extends Application {
    private static final Logger LOGGER = Logger.getLogger(AzulApplication.class.getName());

    private Stage primaryStage;
    private GameClient gameClient;
    private GameController gameController;
    private LobbyController lobbyController;
    private Scene lobbyScene;
    private Scene gameScene;

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
                    showAlert("Connection Status",
                            "Connected to server",
                            Alert.AlertType.INFORMATION);
                });
            }

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> {
                    LOGGER.warning("Disconnected: " + reason);
                    showAlert("Connection Status",
                            "Disconnected: " + reason,
                            Alert.AlertType.WARNING);
                    handleDisconnection();
                });
            }

            @Override
            public void onConnectionFailed(String reason) {
                Platform.runLater(() -> {
                    LOGGER.severe("Connection failed: " + reason);
                    showAlert("Connection Status",
                            "Connection failed: " + reason,
                            Alert.AlertType.ERROR);
                });
            }
        });

        // Connect to server
        // TODO: This is a bug
        // Connected to the target VM, address: '127.0.0.1:1660', transport: 'socket'
        //lis 22, 2024 12:47:22 PM hr.algebra.azul.AzulApplication lambda$initializeNetworkConnection$1
        //INFO: Successfully connected to server
        //lis 22, 2024 12:47:23 PM hr.algebra.azul.AzulApplication$1 lambda$onConnected$0
        //INFO: Connected to server
        //lis 22, 2024 12:47:26 PM hr.algebra.azul.AzulApplication$1 lambda$onDisconnected$1
        //WARNING: Disconnected: Connection lost: Read timed out
        gameClient.connect().thenAccept(success -> {
            Platform.runLater(() -> {
                if (success) {
                    LOGGER.info("Successfully connected to server");
                    showAlert("Connection Status",
                            "Successfully connected to server",
                            Alert.AlertType.INFORMATION);
                } else {
                    LOGGER.severe("Failed to connect to server");
                    showAlert("Connection Status",
                            "Failed to connect to server",
                            Alert.AlertType.ERROR);
                }
            });
        });
    }

    private void showLobbyScreen() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("lobby-view.fxml"));
        lobbyScene = new Scene(loader.load(), 800, 600);

        // Get and initialize the lobby controller
        lobbyController = loader.getController();
        lobbyController.setMainApp(this);
        lobbyController.setStage(primaryStage);
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
        gameController.setStage(primaryStage);
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
            //gameController.handleDisconnection();
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