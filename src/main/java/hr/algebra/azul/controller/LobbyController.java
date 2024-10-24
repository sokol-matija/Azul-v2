package hr.algebra.azul.controller;

import hr.algebra.azul.AzulApplication;
import hr.algebra.azul.network.ConnectionStatusHandler;
import hr.algebra.azul.network.GameClient;
import hr.algebra.azul.network.GameState;
import hr.algebra.azul.network.LobbyUpdateHandler;
import hr.algebra.azul.network.lobby.*;
import hr.algebra.azul.network.server.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Controller for the game lobby view.
 * Handles lobby creation, joining, and player management.
 */
public class LobbyController implements LobbyUpdateHandler {

    // region FXML Injected Fields
    @FXML private ListView<GameLobby> lobbyListView;
    @FXML private ListView<PlayerInfo> playersListView;
    @FXML private Button createLobbyButton;
    @FXML private Button joinLobbyButton;
    @FXML private Button readyButton;
    @FXML private Button startGameButton;
    @FXML private TextField playerNameField;
    @FXML private Label statusLabel;
    @FXML private HBox topContainer;
    // endregion

    // region Class Fields
    private final ObservableList<GameLobby> activeLobbies = FXCollections.observableArrayList();
    private final ObservableList<PlayerInfo> lobbyPlayers = FXCollections.observableArrayList();
    private GameClient gameClient;
    private String playerId;
    private GameLobby currentLobby;
    private Stage stage;
    private AzulApplication mainApp;
    private Circle connectionIndicator;
    private Label connectionLabel;
    // endregion

    // region Initialization Methods
    @FXML
    public void initialize() {
        initializeListViews();
        setupConnectionStatus();
        setupButtons();
        setupEventListeners();
    }

    private void initializeListViews() {
        lobbyListView.setItems(activeLobbies);
        playersListView.setItems(lobbyPlayers);
        configureListViewCells();
    }

    private void setupConnectionStatus() {
        HBox statusBox = new HBox(10);
        connectionIndicator = new Circle(5);
        connectionIndicator.setId("connectionIndicator");
        connectionLabel = new Label("Disconnected");

        connectionIndicator.setStyle("-fx-fill: #e74c3c;"); // Initial disconnected state
        statusBox.setStyle("-fx-padding: 5;");

        statusBox.getChildren().addAll(connectionIndicator, connectionLabel);

        VBox statusContainer = new VBox(10);
        statusContainer.getChildren().add(statusBox);

        if (statusLabel.getParent() instanceof Pane parent) {
            parent.getChildren().add(0, statusContainer);
        }

        if (gameClient != null) {
            setupConnectionHandler();
        }
    }

    private void setupButtons() {
        configureButtonStyles();
        configureButtonTooltips();
        updateButtonStates();
    }

    private void setupEventListeners() {
        createLobbyButton.setOnAction(e -> createLobby());
        joinLobbyButton.setOnAction(e -> joinSelectedLobby());
        readyButton.setOnAction(e -> toggleReady());
        startGameButton.setOnAction(e -> startGame());

        // Add listener for player name changes
        playerNameField.textProperty().addListener((obs, oldVal, newVal) ->
                updateButtonStates());

        // Add listener for lobby selection
        lobbyListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                updateButtonStates());
    }
    // endregion

    // region Network Setup
    public void initializeWithClient(GameClient client) {
        this.gameClient = client;
        this.playerId = client.getClientId();
        setupNetworking();
        refreshLobbyView();
    }

    private void setupNetworking() {
        if (gameClient == null) return;

        gameClient.setLobbyHandler(this);
        requestInitialLobbies();
        updateConnectionStatus("Connected to server");
        enableLobbyInteractions();
        resetLobbyState();
    }

    private void setupConnectionHandler() {
        gameClient.setConnectionHandler(new ConnectionStatusHandler() {
            @Override
            public void onConnected() {
                Platform.runLater(() -> {
                    connectionIndicator.setStyle("-fx-fill: #2ecc71;");
                    connectionLabel.setText("Connected");
                });
            }

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> {
                    connectionIndicator.setStyle("-fx-fill: #e74c3c;");
                    connectionLabel.setText("Disconnected: " + reason);
                });
            }

            @Override
            public void onConnectionFailed(String reason) {
                Platform.runLater(() -> {
                    connectionIndicator.setStyle("-fx-fill: #e74c3c;");
                    connectionLabel.setText("Connection Failed: " + reason);
                });
            }
        });
    }

    private void requestInitialLobbies() {
        LobbyMessage requestLobbies = new LobbyMessage(
                LobbyMessageType.LOBBY_UPDATE,
                null
        );
        gameClient.sendLobbyMessage(requestLobbies);
    }

    private void enableLobbyInteractions() {
        createLobbyButton.setDisable(false);
        joinLobbyButton.setDisable(false);
    }

    private void resetLobbyState() {
        currentLobby = null;
        activeLobbies.clear();
        lobbyPlayers.clear();
        updateButtonStates();
    }
    // endregion

    // region UI Configuration Methods
    private void configureListViewCells() {
        lobbyListView.setCellFactory(lv -> new LobbyListCell());
        playersListView.setCellFactory(lv -> new PlayerListCell());
    }

    private void configureButtonStyles() {
        // Initial states
        startGameButton.setDisable(true);
        readyButton.setDisable(true);

        // Style buttons
        createLobbyButton.setStyle("""
            -fx-background-color: #4CAF50;
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-padding: 8 16;
            -fx-background-radius: 4;
        """);

        joinLobbyButton.setStyle("""
            -fx-background-color: #2196F3;
            -fx-text-fill: white;
            -fx-padding: 8 16;
            -fx-background-radius: 4;
        """);

        readyButton.setStyle("""
            -fx-background-color: #FFC107;
            -fx-text-fill: #333333;
            -fx-padding: 8 16;
            -fx-background-radius: 4;
        """);

        startGameButton.setStyle("""
            -fx-background-color: #4CAF50;
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-padding: 8 16;
            -fx-background-radius: 4;
        """);

        // Add hover effects
        addButtonHoverEffect(createLobbyButton, "#4CAF50", "#45a049");
        addButtonHoverEffect(joinLobbyButton, "#2196F3", "#1976D2");
        addButtonHoverEffect(readyButton, "#FFC107", "#FFA000");
        addButtonHoverEffect(startGameButton, "#4CAF50", "#45a049");
    }

    private void configureButtonTooltips() {
        createLobbyButton.setTooltip(new Tooltip("Create a new game lobby"));
        joinLobbyButton.setTooltip(new Tooltip("Join the selected lobby"));
        readyButton.setTooltip(new Tooltip("Toggle ready status"));
        startGameButton.setTooltip(new Tooltip("Start the game when all players are ready"));
    }

    private void addButtonHoverEffect(Button button, String normalColor, String hoverColor) {
        button.setOnMouseEntered(e ->
                button.setStyle(button.getStyle().replace(normalColor, hoverColor))
        );
        button.setOnMouseExited(e ->
                button.setStyle(button.getStyle().replace(hoverColor, normalColor))
        );
    }
    // endregion

    // region Action Handlers
    private void createLobby() {
        String playerName = playerNameField.getText().trim();
        if (playerName.isEmpty()) {
            showError("Please enter a player name");
            return;
        }

        // Create a new lobby with current player as host
        GameLobby newLobby = new GameLobby(playerId);
        // Use the addPlayer method instead of directly accessing the map
        newLobby.addPlayer(playerId, playerName);

        // Set the host as ready
        PlayerInfo hostInfo = newLobby.getPlayers().get(playerId);
        if (hostInfo != null) {
            hostInfo.setReady(true);
        }

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
        showSuccess("Lobby created successfully!");
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

        try {
            // Create a copy of the selected lobby to modify
            GameLobby updatedLobby = new GameLobby(selected.getHostId());
            updatedLobby.setStatus(selected.getStatus());
            
            // Copy existing players
            for (PlayerInfo player : selected.getPlayers().values()) {
                updatedLobby.addPlayer(player.getPlayerId(), player.getPlayerName());
                if (player.isReady()) {
                    updatedLobby.getPlayers().get(player.getPlayerId()).setReady(true);
                }
            }
            
            // Add the new player and set them as ready
            updatedLobby.addPlayer(playerId, playerName);
            PlayerInfo newPlayer = updatedLobby.getPlayers().get(playerId);
            if (newPlayer != null) {
                newPlayer.setReady(true);
            }

            // Send join message with updated lobby
            LobbyMessage joinMessage = new LobbyMessage(
                LobbyMessageType.PLAYER_JOINED,
                updatedLobby
            );
            gameClient.sendLobbyMessage(joinMessage);

            // Update local state
            currentLobby = updatedLobby;
            updateButtonStates();
            updatePlayersList();
            showSuccess("Successfully joined lobby!");
        } catch (IllegalStateException e) {
            showError("Cannot join lobby: " + e.getMessage());
        }
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
        if (currentLobby != null && currentLobby.isHost(playerId) &&
                currentLobby.canStart()) {
            gameClient.sendMessage(new LobbyMessage(
                    LobbyMessageType.GAME_START,
                    currentLobby
            ));
        }
    }
    // endregion

    // region Update Methods
    private void updateButtonStates() {
        boolean hasPlayerName = !playerNameField.getText().trim().isEmpty();
        boolean isInLobby = currentLobby != null;
        boolean isHost = isInLobby && currentLobby.isHost(playerId);
        boolean canStartGame = isHost && currentLobby.canStart() &&
                currentLobby.getPlayers().values().stream()
                        .allMatch(PlayerInfo::isReady);

        createLobbyButton.setDisable(!hasPlayerName || isInLobby);
        joinLobbyButton.setDisable(!hasPlayerName || isInLobby ||
                lobbyListView.getSelectionModel().getSelectedItem() == null);
        readyButton.setDisable(!isInLobby || isHost);
        startGameButton.setDisable(!isHost || !canStartGame);

        if (isInLobby) {
            PlayerInfo player = currentLobby.getPlayers().get(playerId);
            if (player != null) {
                readyButton.setText(player.isReady() ? "Not Ready" : "Ready");
            }
        }
    }

    private void updatePlayersList() {
        lobbyPlayers.clear();
        if (currentLobby != null) {
            lobbyPlayers.addAll(currentLobby.getPlayers().values());
        }
    }

    private void updateLobbyDisplay(GameLobby lobby) {
        if (lobby.getLobbyId().equals(currentLobby != null ?
                currentLobby.getLobbyId() : null)) {
            currentLobby = lobby;
            updatePlayersList();
            updateButtonStates();
        } else {
            int index = findLobbyIndex(lobby.getLobbyId());
            if (index >= 0) {
                activeLobbies.set(index, lobby);
            } else {
                activeLobbies.add(lobby);
            }
        }
    }
    // endregion

    // region LobbyUpdateHandler Implementation
    @Override
    public void onLobbyUpdate(GameLobby lobby) {
        Platform.runLater(() -> {
            int existingIndex = findLobbyIndex(lobby.getLobbyId());

            if (existingIndex >= 0) {
                activeLobbies.set(existingIndex, lobby);
            } else {
                activeLobbies.add(lobby);
            }

            if (currentLobby != null &&
                    currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                currentLobby = lobby;
                updatePlayersList();
            }

            updateButtonStates();
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
            if (currentLobby != null &&
                    currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                currentLobby = null;
                updatePlayersList();
                updateButtonStates();
                showError("Lobby has been closed");
            }
        });
    }
    // endregion

    // region Helper Methods
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

    private void showSuccess(String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: green;");
        });
    }

    private void updateConnectionStatus(String status) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(status);
                statusLabel.setStyle("-fx-text-fill: green;");
            }
        });
    }
    // region Setter/Getter Methods
    public void setMainApp(AzulApplication mainApp) {
        this.mainApp = mainApp;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void refreshLobbyView() {
        if (gameClient != null && gameClient.getCurrentLobby() != null) {
            updateLobbyDisplay(gameClient.getCurrentLobby());
        }
        updateButtonStates();
    }

    // region Custom Cell Classes
    private class LobbyListCell extends ListCell<GameLobby> {
        @Override
        protected void updateItem(GameLobby lobby, boolean empty) {
            super.updateItem(lobby, empty);
            if (empty || lobby == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox container = new VBox(5);
                container.setStyle("-fx-padding: 5; -fx-background-radius: 5;");

                // Host information
                PlayerInfo host = lobby.getPlayers().get(lobby.getHostId());
                Label hostLabel = new Label("Host: " +
                        (host != null ? host.getPlayerName() : "Unknown"));
                hostLabel.setStyle("-fx-font-weight: bold;");

                // Player count
                Label playersLabel = new Label(String.format("Players: %d/%d",
                        lobby.getPlayers().size(),
                        lobby.getSettings().getMaxPlayers()));

                // Status
                Label statusLabel = new Label("Status: " + lobby.getStatus());

                container.getChildren().addAll(hostLabel, playersLabel, statusLabel);

                // Highlight current lobby
                if (currentLobby != null &&
                        currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                    container.setStyle(container.getStyle() +
                            "; -fx-background-color: #e3f2fd;");
                }

                setGraphic(container);
            }
        }
    }

    private class PlayerListCell extends ListCell<PlayerInfo> {
        @Override
        protected void updateItem(PlayerInfo player, boolean empty) {
            super.updateItem(player, empty);
            if (empty || player == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox container = new HBox(10);
                container.setStyle("-fx-padding: 5; -fx-background-radius: 5;");

                // Player name with host indicator
                String nameText = player.getPlayerName();
                if (currentLobby != null &&
                        currentLobby.isHost(player.getPlayerId())) {
                    nameText += " (Host)";
                }
                Label nameLabel = new Label(nameText);

                // Ready status
                Label readyLabel = new Label(player.isReady() ? "Ready" : "Not Ready");
                readyLabel.setStyle(player.isReady() ?
                        "-fx-text-fill: #2ecc71; -fx-font-weight: bold;" :
                        "-fx-text-fill: #e74c3c;");

                container.getChildren().addAll(nameLabel, readyLabel);

                // Highlight current player
                if (player.getPlayerId().equals(playerId)) {
                    container.setStyle(container.getStyle() +
                            "; -fx-background-color: #f5f5f5;");
                }

                setGraphic(container);
            }
        }
    }
}
