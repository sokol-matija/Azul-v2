// File: src/main/java/hr/algebra/azul/controller/LobbyController.java
package hr.algebra.azul.controller;

import hr.algebra.azul.AzulApplication;
import hr.algebra.azul.network.ConnectionStatusHandler;
import hr.algebra.azul.network.GameClient;
import hr.algebra.azul.network.GameState;
import hr.algebra.azul.network.LobbyUpdateHandler;
import hr.algebra.azul.network.lobby.*;
import hr.algebra.azul.network.server.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;

import java.io.IOException;
public class LobbyController implements LobbyUpdateHandler {
    @FXML private ListView<GameLobby> lobbyListView;
    @FXML private ListView<PlayerInfo> playersListView;
    @FXML private Button createLobbyButton;
    @FXML private Button joinLobbyButton;
    @FXML private Button readyButton;
    @FXML private Button startGameButton;
    @FXML private TextField playerNameField;
    @FXML private Label statusLabel;
    @FXML private HBox topContainer;

    private final ObservableList<GameLobby> activeLobbies = FXCollections.observableArrayList();
    private final ObservableList<PlayerInfo> lobbyPlayers = FXCollections.observableArrayList();

    private GameClient gameClient;
    private String playerId;
    private GameLobby currentLobby;
    private Stage stage;
    private AzulApplication mainApp;

    public void setMainApp(AzulApplication mainApp) {
        this.mainApp = mainApp;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void initializeWithClient(GameClient client) {
        this.gameClient = client;
        this.playerId = client.getClientId();
        setupNetworking();
        refreshLobbyView();
    }

    private void setupNetworking() {
        if (gameClient == null) return;

        // Set up network handlers
        gameClient.setLobbyHandler(this);

        // Request initial lobby list
        LobbyMessage requestLobbies = new LobbyMessage(
                LobbyMessageType.LOBBY_UPDATE,
                null
        );
        gameClient.sendLobbyMessage(requestLobbies);

        // Update connection status
        updateConnectionStatus("Connected to server");

        // Enable lobby interactions
        createLobbyButton.setDisable(false);
        joinLobbyButton.setDisable(false);

        // Reset any existing lobby state
        currentLobby = null;
        activeLobbies.clear();
        lobbyPlayers.clear();
        updateButtonStates();
    }

    private void updateConnectionStatus(String status) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(status);
                statusLabel.setStyle("-fx-text-fill: green;");
            }
        });
    }

    public void refreshLobbyView() {
        // Refresh the lobby list and player list
        if (gameClient != null && gameClient.getCurrentLobby() != null) {
            updateLobbyDisplay(gameClient.getCurrentLobby());
        }
        updateButtonStates();
    }

    public String getPlayerId() {
        return playerId;
    }

    @FXML
    public void initialize() {
        lobbyListView.setItems(activeLobbies);
        playersListView.setItems(lobbyPlayers);

        // Create connection status indicator
        HBox statusBox = new HBox(10);
        Circle connectionIndicator = new Circle(5);
        connectionIndicator.setId("connectionIndicator");
        Label connectionLabel = new Label("Disconnected");

        // Add CSS styles
        String css = """
            .connected { -fx-fill: #2ecc71; }
            .disconnected { -fx-fill: #e74c3c; }
        """;
        connectionIndicator.getStyleClass().add("disconnected");
        statusBox.getStylesheets().add(css);

        statusBox.getChildren().addAll(connectionIndicator, connectionLabel);

        // Add the status box to the existing layout
        // First, wrap the status box in a VBox for proper alignment
        VBox statusContainer = new VBox(10);
        statusContainer.getChildren().add(statusBox);

        // Get the parent of the statusLabel and add our status container
        if (statusLabel.getParent() instanceof Pane) {
            Pane parent = (Pane) statusLabel.getParent();
            parent.getChildren().add(0, statusContainer);
        }

        // Set up connection handler if client exists
        if (gameClient != null) {
            setupConnectionHandler(connectionIndicator, connectionLabel);
        }

        // Configure list views
        setupListViews();

        // Initial button states
        setupButtons();

        // Set up event listeners
        setupListeners();
    }

    private void setupListViews() {
        // Configure the lobby list view
        lobbyListView.setCellFactory(lv -> new ListCell<GameLobby>() {
            @Override
            protected void updateItem(GameLobby lobby, boolean empty) {
                super.updateItem(lobby, empty);
                if (empty || lobby == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Create a container for lobby information
                    VBox container = new VBox(5);
                    container.getStyleClass().add("lobby-cell");

                    // Host information
                    PlayerInfo host = lobby.getPlayers().get(lobby.getHostId());
                    Label hostLabel = new Label("Host: " + (host != null ? host.getPlayerName() : "Unknown"));
                    hostLabel.getStyleClass().add("lobby-host-label");

                    // Player count
                    Label playerCount = new Label(String.format("Players: %d/%d",
                            lobby.getPlayers().size(),
                            lobby.getSettings().getMaxPlayers()));
                    playerCount.getStyleClass().add("lobby-count-label");

                    // Status indicator
                    Label statusLabel = new Label("Status: " + lobby.getStatus());
                    statusLabel.getStyleClass().add("lobby-status-label");

                    // Add all components to the container
                    container.getChildren().addAll(hostLabel, playerCount, statusLabel);

                    // Style based on whether this is the current lobby
                    if (currentLobby != null && currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                        container.getStyleClass().add("current-lobby");
                    }

                    setGraphic(container);
                }
            }
        });

        // Configure the players list view
        playersListView.setCellFactory(lv -> new ListCell<PlayerInfo>() {
            @Override
            protected void updateItem(PlayerInfo player, boolean empty) {
                super.updateItem(player, empty);
                if (empty || player == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Create a container for player information
                    HBox container = new HBox(10);
                    container.getStyleClass().add("player-cell");

                    // Player name with host indicator if applicable
                    String nameText = player.getPlayerName();
                    if (currentLobby != null && currentLobby.isHost(player.getPlayerId())) {
                        nameText += " (Host)";
                    }
                    Label nameLabel = new Label(nameText);
                    nameLabel.getStyleClass().add("player-name-label");

                    // Ready status indicator
                    Label readyLabel = new Label(player.isReady() ? "Ready" : "Not Ready");
                    readyLabel.getStyleClass().add(player.isReady() ? "ready-status" : "not-ready-status");

                    // Add components to container
                    container.getChildren().addAll(nameLabel, readyLabel);

                    // Highlight current player
                    if (player.getPlayerId().equals(playerId)) {
                        container.getStyleClass().add("current-player");
                    }

                    setGraphic(container);
                }
            }
        });

        // Add CSS styles for the list views
        String listViewCss = """
        .lobby-cell, .player-cell {
            -fx-padding: 5;
            -fx-background-radius: 5;
        }
        
        .current-lobby {
            -fx-background-color: #e3f2fd;
        }
        
        .lobby-host-label {
            -fx-font-weight: bold;
        }
        
        .ready-status {
            -fx-text-fill: #2ecc71;
            -fx-font-weight: bold;
        }
        
        .not-ready-status {
            -fx-text-fill: #e74c3c;
        }
        
        .current-player {
            -fx-background-color: #f5f5f5;
        }
    """;

        lobbyListView.getStylesheets().add(listViewCss);
        playersListView.getStylesheets().add(listViewCss);
    }

    private void setupButtons() {
        // Initially disable all game-related buttons
        startGameButton.setDisable(true);
        readyButton.setDisable(true);

        // Create lobby button styling
        createLobbyButton.getStyleClass().add("primary-button");
        createLobbyButton.setStyle("""
        -fx-background-color: #4CAF50;
        -fx-text-fill: white;
        -fx-font-weight: bold;
    """);

        // Join lobby button styling
        joinLobbyButton.getStyleClass().add("secondary-button");
        joinLobbyButton.setStyle("""
        -fx-background-color: #2196F3;
        -fx-text-fill: white;
    """);

        // Ready button styling
        readyButton.getStyleClass().add("ready-button");
        readyButton.setStyle("""
        -fx-background-color: #FFC107;
        -fx-text-fill: #333333;
    """);

        // Start game button styling
        startGameButton.getStyleClass().add("start-game-button");
        startGameButton.setStyle("""
        -fx-background-color: #4CAF50;
        -fx-text-fill: white;
        -fx-font-weight: bold;
    """);

        // Add hover effects
        String buttonHoverCss = """
        .primary-button:hover, .start-game-button:hover {
            -fx-background-color: #45a049;
        }
        
        .secondary-button:hover {
            -fx-background-color: #1976D2;
        }
        
        .ready-button:hover {
            -fx-background-color: #FFA000;
        }
    """;

        createLobbyButton.getStylesheets().add(buttonHoverCss);
        joinLobbyButton.getStylesheets().add(buttonHoverCss);
        readyButton.getStylesheets().add(buttonHoverCss);
        startGameButton.getStylesheets().add(buttonHoverCss);

        // Add tooltips
        createLobbyButton.setTooltip(new Tooltip("Create a new game lobby"));
        joinLobbyButton.setTooltip(new Tooltip("Join the selected lobby"));
        readyButton.setTooltip(new Tooltip("Toggle ready status"));
        startGameButton.setTooltip(new Tooltip("Start the game when all players are ready"));

        // Update button states based on client/lobby status
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasPlayerName = playerNameField != null && !playerNameField.getText().trim().isEmpty();
        boolean isInLobby = currentLobby != null;
        boolean isHost = isInLobby && currentLobby.isHost(playerId);
        boolean canStartGame = isHost && currentLobby.canStart() &&
                currentLobby.getPlayers().values().stream().allMatch(PlayerInfo::isReady);

        // Enable/disable lobby creation and joining
        createLobbyButton.setDisable(!hasPlayerName || isInLobby);
        joinLobbyButton.setDisable(!hasPlayerName || isInLobby ||
                lobbyListView.getSelectionModel().getSelectedItem() == null);

        // Enable/disable ready and start buttons
        readyButton.setDisable(!isInLobby || isHost);
        startGameButton.setDisable(!isHost || !canStartGame);

        // Update button text based on state
        if (isInLobby) {
            PlayerInfo player = currentLobby.getPlayers().get(playerId);
            if (player != null) {
                readyButton.setText(player.isReady() ? "Not Ready" : "Ready");
            }
        }
    }

    private void setupConnectionHandler(Circle indicator, Label label) {
        gameClient.setConnectionHandler(new ConnectionStatusHandler() {
            @Override
            public void onConnected() {
                Platform.runLater(() -> {
                    indicator.getStyleClass().remove("disconnected");
                    indicator.getStyleClass().add("connected");
                    label.setText("Connected");
                });
            }

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> {
                    indicator.getStyleClass().remove("connected");
                    indicator.getStyleClass().add("disconnected");
                    label.setText("Disconnected: " + reason);
                });
            }

            @Override
            public void onConnectionFailed(String reason) {
                Platform.runLater(() -> {
                    indicator.getStyleClass().remove("connected");
                    indicator.getStyleClass().add("disconnected");
                    label.setText("Connection Failed: " + reason);
                });
            }
        });
    }

    private void setupListeners() {
        createLobbyButton.setOnAction(e -> createLobby());
        joinLobbyButton.setOnAction(e -> joinSelectedLobby());
        readyButton.setOnAction(e -> toggleReady());
        startGameButton.setOnAction(e -> startGame());
    }

    private void createLobby() {
        String playerName = playerNameField.getText().trim();
        if (playerName.isEmpty()) {
            showError("Please enter a player name");
            return;
        }

        // Create a new lobby with current player as host
        GameLobby newLobby = new GameLobby(playerId);
        PlayerInfo hostInfo = new PlayerInfo(playerId, playerName);
        hostInfo.setReady(true); // Host is automatically ready
        newLobby.getPlayers().put(playerId, hostInfo);

        // Send lobby creation message
        LobbyMessage createMessage = new LobbyMessage(
                LobbyMessageType.LOBBY_UPDATE,
                newLobby
        );

        gameClient.sendLobbyMessage(createMessage);

        // Update local state
        currentLobby = newLobby;
        updateButtonStates();
        updatePlayersList();

        // Show success message
        Platform.runLater(() -> {
            statusLabel.setText("Lobby created successfully!");
            statusLabel.setStyle("-fx-text-fill: green;");
        });
    }

    private void joinSelectedLobby() {
        GameLobby selected = lobbyListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a lobby to join");
            return;
        }

        String playerName = playerNameField.getText().trim();
        if (playerName.isEmpty()) {
            showError("Please enter a player name");
            return;
        }

        gameClient.sendMessage(new LobbyMessage(
                LobbyMessageType.PLAYER_JOINED,
                selected
        ));
    }

    private void toggleReady() {
        if (currentLobby != null) {
            PlayerInfo playerInfo = currentLobby.getPlayers().get(playerId);
            if (playerInfo != null) {
                playerInfo.setReady(!playerInfo.isReady());
                gameClient.sendMessage(new LobbyMessage(
                        LobbyMessageType.LOBBY_UPDATE,
                        currentLobby
                ));
            }
        }
    }

    private void startGame() {
        if (currentLobby != null && currentLobby.isHost(playerId) && currentLobby.canStart()) {
            gameClient.sendMessage(new LobbyMessage(
                    LobbyMessageType.GAME_START,
                    currentLobby
            ));
        }
    }

    public void handleLobbyUpdate(LobbyMessage message) {
        Platform.runLater(() -> {
            switch (message.getType()) {
                case LOBBY_UPDATE:
                    updateLobbyDisplay(message.getLobby());
                    break;
                case GAME_START:
                    handleGameStart(message);
                    break;
                case LOBBY_CLOSED:
                    handleLobbyClosed(message.getLobby());
                    break;
            }
        });
    }


    private void updateLobbyDisplay(GameLobby lobby) {
        if (lobby.getLobbyId().equals(currentLobby != null ? currentLobby.getLobbyId() : null)) {
            // Update current lobby
            currentLobby = lobby;
            updatePlayersList();
            updateButtonStates();
        } else {
            // Update lobby list
            int index = findLobbyIndex(lobby.getLobbyId());
            if (index >= 0) {
                activeLobbies.set(index, lobby);
            } else {
                activeLobbies.add(lobby);
            }
        }
    }

    private void handleGameStart(LobbyMessage message) {
        if (message.getGameState() != null) {
            // Switch to game view
            Platform.runLater(() -> {
                try {
                    GameController gameController = switchToGameView();
                    gameController.initializeNetworkedGame(
                            message.getGameState(),
                            gameClient,
                            playerId
                    );
                } catch (Exception e) {
                    showError("Failed to start game: " + e.getMessage());
                }
            });
        }
    }

    private GameController switchToGameView() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/hr/algebra/azul/game-view.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 800);

        GameController gameController = loader.getController();
        gameController.setStage(stage);
        gameController.setMainApp(mainApp);

        stage.setScene(scene);
        return gameController;
    }

    private void handleLobbyClosed(GameLobby lobby) {
        Platform.runLater(() -> {
            activeLobbies.removeIf(l -> l.getLobbyId().equals(lobby.getLobbyId()));
            if (currentLobby != null && currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                currentLobby = null;
                updatePlayersList();
                updateButtonStates();
                showError("Lobby has been closed by the host");
            }
        });
    }

    private void updatePlayersList() {
        lobbyPlayers.clear();
        if (currentLobby != null) {
            lobbyPlayers.addAll(currentLobby.getPlayers().values());
        }
    }



    private int findLobbyIndex(String lobbyId) {
        for (int i = 0; i < activeLobbies.size(); i++) {
            if (activeLobbies.get(i).getLobbyId().equals(lobbyId)) {
                return i;
            }
        }
        return -1;
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: red;");
        });
    }



    @Override
    public void onLobbyUpdate(GameLobby lobby) {
        Platform.runLater(() -> {
            // Update the lobby list
            int existingIndex = -1;
            for (int i = 0; i < activeLobbies.size(); i++) {
                if (activeLobbies.get(i).getLobbyId().equals(lobby.getLobbyId())) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0) {
                activeLobbies.set(existingIndex, lobby);
            } else {
                activeLobbies.add(lobby);
            }

            // If this is our current lobby, update it
            if (currentLobby != null &&
                    currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                currentLobby = lobby;
                updatePlayersList();
            }

            updateButtonStates();

            // Log for debugging
            System.out.println("Active lobbies: " + activeLobbies.size());
            for (GameLobby l : activeLobbies) {
                System.out.println(" - Lobby " + l.getLobbyId() +
                        " (Host: " + l.getHostId() + ")");
            }
        });
    }

    @Override
    public void onGameStart(GameLobby lobby, GameState gameState) {
        Platform.runLater(() -> {
            try {
                mainApp.switchToGame(gameState);
            } catch (IOException e) {
                showError("Failed to start game: " + e.getMessage());
            }
        });
    }


    @Override
    public void onLobbyClosed(GameLobby lobby) {
        Platform.runLater(() -> {
            if (currentLobby != null && currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                currentLobby = null;
                updatePlayersList();
                updateButtonStates();
                showError("Lobby has been closed");
            }
        });
    }

    // Custom ListCell for lobbies
    private static class LobbyListCell extends ListCell<GameLobby> {
        @Override
        protected void updateItem(GameLobby lobby, boolean empty) {
            super.updateItem(lobby, empty);
            if (empty || lobby == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox container = new VBox(5);
                Label nameLabel = new Label("Host: " + lobby.getPlayers().get(lobby.getHostId()).getPlayerName());
                Label playersLabel = new Label(String.format("Players: %d/%d",
                        lobby.getPlayers().size(),
                        lobby.getSettings().getMaxPlayers()));
                Label statusLabel = new Label("Status: " + lobby.getStatus());

                container.getChildren().addAll(nameLabel, playersLabel, statusLabel);
                setGraphic(container);
            }
        }
    }

    // Custom ListCell for players
    private static class PlayerListCell extends ListCell<PlayerInfo> {
        @Override
        protected void updateItem(PlayerInfo player, boolean empty) {
            super.updateItem(player, empty);
            if (empty || player == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox container = new HBox(10);
                Label nameLabel = new Label(player.getPlayerName());
                Label readyLabel = new Label(player.isReady() ? "Ready" : "Not Ready");
                readyLabel.setStyle(player.isReady() ?
                        "-fx-text-fill: green;" : "-fx-text-fill: red;");

                container.getChildren().addAll(nameLabel, readyLabel);
                setGraphic(container);
            }
        }
    }
}