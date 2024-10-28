// File: src/main/java/hr/algebra/azul/AzulApplication.java
package hr.algebra.azul;

import hr.algebra.azul.controller.GameController;
import hr.algebra.azul.controller.LobbyController;
import hr.algebra.azul.model.User;
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
import java.util.Random;
import java.util.logging.Logger;

public class AzulApplication extends Application {
    private static final Logger LOGGER = Logger.getLogger(AzulApplication.class.getName());
    private static final String[] RANDOM_ADJECTIVES = {
            "Happy", "Lucky", "Clever", "Swift", "Bright", "Nimble", "Quick", "Sharp", "Smart", "Wise"
    };
    private static final String[] RANDOM_NOUNS = {
            "Player", "Gamer", "Master", "Champion", "Warrior", "Knight", "Hero", "Legend", "Star", "Winner"
    };


    private Stage primaryStage;
    private GameClient gameClient;
    private GameController gameController;
    private LobbyController lobbyController;
    private Scene lobbyScene;
    private Scene gameScene;
    private User currentUser;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            handleApplicationClose();
        });

        // Create random user before initializing network
        createRandomUser();

        initializeNetworkConnection();
        showLobbyScreen();
    }

    private void createRandomUser() {
        Random random = new Random();
        String adjective = RANDOM_ADJECTIVES[random.nextInt(RANDOM_ADJECTIVES.length)];
        String noun = RANDOM_NOUNS[random.nextInt(RANDOM_NOUNS.length)];
        String randomNumber = String.format("%03d", random.nextInt(1000));

        String username = adjective + noun + randomNumber;
        this.currentUser = new User(username);

        LOGGER.info("Created new user: " + username);
    }

    private void initializeNetworkConnection() {
        gameClient = new GameClient(NetworkConfig.DEFAULT_HOST, NetworkConfig.DEFAULT_PORT);

        // Set the current user in the game client
        gameClient.setCurrentUser(currentUser);

        gameClient.setConnectionHandler(new ConnectionStatusHandler() {
            @Override
            public void onConnected() {
                Platform.runLater(() -> {
                    LOGGER.info("Connected to server as: " + currentUser.getUsername());
                    showAlert("Connection Status",
                            "Connected to server as: " + currentUser.getUsername(),
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

        gameClient.connect().thenAccept(success -> {
            Platform.runLater(() -> {
                if (success) {
                    LOGGER.info("Successfully connected to server as: " + currentUser.getUsername());
                    showAlert("Connection Status",
                            "Successfully connected as: " + currentUser.getUsername(),
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

    public void showLobbyScreen() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("lobby-view.fxml"));
        Scene scene = new Scene(loader.load(), 800, 600);

        LobbyController controller = loader.getController();
        controller.setMainApp(this); // This will now trigger proper initialization
        controller.initializeWithClient(gameClient);

        primaryStage.setTitle("Azul Lobby - " + currentUser.getUsername());
        primaryStage.setScene(scene);
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

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public static void main(String[] args) {
        launch();
    }
}