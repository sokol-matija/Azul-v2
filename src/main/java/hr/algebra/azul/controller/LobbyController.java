package hr.algebra.azul.controller;

import hr.algebra.azul.AzulApplication;
import hr.algebra.azul.network.*;
import hr.algebra.azul.network.lobby.*;
import hr.algebra.azul.network.server.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.animation.*;
import javafx.util.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class LobbyController implements LobbyUpdateHandler {
    private static final Logger LOGGER = Logger.getLogger(LobbyController.class.getName());
    private static final Duration STATUS_FADE_DURATION = Duration.seconds(3);
    private static final int MAX_PLAYER_NAME_LENGTH = 20;
    private static final int MIN_PLAYER_NAME_LENGTH = 3;

    // FXML Components
    @FXML
    private ListView<GameLobby> lobbyListView;
    @FXML
    private ListView<PlayerInfo> playersListView;
    @FXML
    private Button createLobbyButton;
    @FXML
    private Button joinLobbyButton;
    @FXML
    private Button readyButton;
    @FXML
    private Button startGameButton;
    @FXML
    private TextField playerNameField;
    @FXML
    private Label statusLabel;
    @FXML
    private VBox connectionStatusPane;
    @FXML
    private Label connectionStatusLabel;
    @FXML
    private ProgressBar connectionProgress;
    @FXML
    private HBox topContainer;

    // Observable Collections
    private final ObservableList<GameLobby> activeLobbies = FXCollections.observableArrayList();
    private final ObservableList<PlayerInfo> lobbyPlayers = FXCollections.observableArrayList();

    // State Management
    private GameClient gameClient;
    private String playerId;
    private GameLobby currentLobby;
    private final Map<String, PlayerReadyState> playerReadyStates = new ConcurrentHashMap<>();
    private volatile boolean isConnecting = false;
    private volatile boolean isJoiningLobby = false;

    // UI Components
    private FadeTransition statusFade;
    private Timeline connectionTimeout;

    // Application Components
    private AzulApplication mainApp;

    @FXML
    public void initialize() {
        setupUI();
        setupListViews();
        setupEventHandlers();
        setupAnimations();
    }

    private void setupUI() {
        connectionProgress.setVisible(false);
        disableGameControls();
        setupStyles();
    }

    private void setupStyles() {
        // Apply CSS styles
        String buttonStyle = """
            
                -fx-background-radius: 5;
            -fx-padding: 8 15;
            -fx-font-size: 14px;
            """;

        createLobbyButton.setStyle(buttonStyle + "-fx-background-color: #4CAF50; -fx-text-fill: white;");
        joinLobbyButton.setStyle(buttonStyle + "-fx-background-color: #2196F3; -fx-text-fill: white;");
        readyButton.setStyle(buttonStyle + "-fx-background-color: #FFC107; -fx-text-fill: black;");
        startGameButton.setStyle(buttonStyle + "-fx-background-color: #4CAF50; -fx-text-fill: white;");

        playerNameField.setStyle("-fx-padding: 5 10; -fx-background-radius: 3;");
        statusLabel.setStyle("-fx-font-size: 14px;");
    }

    private void setupListViews() {
        lobbyListView.setItems(activeLobbies);
        lobbyListView.setCellFactory(lv -> new LobbyListCell());

        playersListView.setItems(lobbyPlayers);
        playersListView.setCellFactory(lv -> new PlayerListCell());
    }

    private void setupEventHandlers() {
        createLobbyButton.setOnAction(e -> handleCreateLobby());
        joinLobbyButton.setOnAction(e -> handleJoinLobby());
        readyButton.setOnAction(e -> handleReadyToggle());
        startGameButton.setOnAction(e -> handleStartGame());

        playerNameField.textProperty().addListener((obs, oldVal, newVal) -> {
            validatePlayerName(newVal);
            updateButtonStates();
        });

        lobbyListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> updateButtonStates());
    }

    private void setupAnimations() {
        statusFade = new FadeTransition(STATUS_FADE_DURATION, statusLabel);
        statusFade.setFromValue(1.0);
        statusFade.setToValue(0.0);

        connectionTimeout = new Timeline(
                new KeyFrame(Duration.seconds(10), e -> handleConnectionTimeout())
        );
        connectionTimeout.setCycleCount(1);
    }

    public void initializeWithClient(GameClient client) {
        this.gameClient = client;
        this.playerId = client.getClientId();

        setupNetworkHandlers();
        requestInitialLobbyState();
    }

    private void setupNetworkHandlers() {
        if (gameClient == null) return;

        gameClient.setConnectionHandler(new ConnectionHandler());
        gameClient.setLobbyHandler(this);
    }

    private void requestInitialLobbyState() {
        LobbyMessage request = new LobbyMessage.Builder(LobbyMessageType.LOBBY_LIST_UPDATE)
                .playerId(playerId)
                .build();
        gameClient.sendLobbyMessage(request);
    }

    private void handleCreateLobby() {
        if (!validatePlayerName(playerNameField.getText())) return;

        isJoiningLobby = true;
        showLoading("Creating lobby...");

        GameLobby newLobby = new GameLobby(playerId);
        newLobby.addPlayer(playerId, playerNameField.getText());

        LobbyMessage createMessage = new LobbyMessage.Builder(LobbyMessageType.LOBBY_CREATE)
                .playerId(playerId)
                .lobby(newLobby)
                .build();

        gameClient.sendLobbyMessage(createMessage);
    }

    private void handleJoinLobby() {
        if (!validatePlayerName(playerNameField.getText())) return;

        GameLobby selected = lobbyListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a lobby to join");
            return;
        }

        isJoiningLobby = true;
        showLoading("Joining lobby...");

        LobbyMessage joinMessage = new LobbyMessage.Builder(LobbyMessageType.PLAYER_JOINED)
                .playerId(playerId)
                .lobby(selected)
                .build();

        gameClient.sendLobbyMessage(joinMessage);
    }

    private void handleReadyToggle() {
        if (currentLobby == null) return;

        PlayerReadyState readyState = playerReadyStates.computeIfAbsent(
                playerId,
                id -> new PlayerReadyState()
        );

        boolean newReadyState = !readyState.isReady();
        readyState.setReady(newReadyState);

        LobbyMessage readyMessage = new LobbyMessage.Builder(LobbyMessageType.PLAYER_READY)
                .playerId(playerId)
                .lobby(currentLobby)
                .playerReady(newReadyState)
                .build();

        gameClient.sendLobbyMessage(readyMessage);
        updateReadyButton(newReadyState);
    }

    private void handleStartGame() {
        if (currentLobby == null || !currentLobby.isHost(playerId)) return;

        if (currentLobby.canStart()) {
            showLoading("Starting game...");

            LobbyMessage startMessage = new LobbyMessage.Builder(LobbyMessageType.GAME_START)
                    .playerId(playerId)
                    .lobby(currentLobby)
                    .build();

            gameClient.sendLobbyMessage(startMessage);
        } else {
            showError("Cannot start game: Not all players are ready");
        }
    }

    private boolean validatePlayerName(String name) {
        if (name == null || name.trim().isEmpty()) {
            showError("Player name cannot be empty");
            return false;
        }

        if (name.length() < MIN_PLAYER_NAME_LENGTH) {
            showError("Player name must be at least 3 characters");
            return false;
        }

        if (name.length() > MAX_PLAYER_NAME_LENGTH) {
            showError("Player name must be less than 20 characters");
            return false;
        }

        return true;
    }

    private void updateButtonStates() {
        boolean hasValidName = validatePlayerName(playerNameField.getText());
        boolean isInLobby = currentLobby != null;
        boolean isHost = isInLobby && currentLobby.isHost(playerId);
        boolean hasSelectedLobby = lobbyListView.getSelectionModel().getSelectedItem() != null;

        createLobbyButton.setDisable(!hasValidName || isInLobby);
        joinLobbyButton.setDisable(!hasValidName || isInLobby || !hasSelectedLobby);
        readyButton.setDisable(!isInLobby);
        startGameButton.setDisable(!isInLobby || !isHost || !currentLobby.canStart());
    }

    private void updateReadyButton(boolean isReady) {
        readyButton.setText(isReady ? "Not Ready" : "Ready");
        readyButton.setStyle(readyButton.getStyle() +
                (isReady ? "-fx-background-color: #e74c3c;" : "-fx-background-color: #2ecc71;"));
    }

    private void showLoading(String message) {
        connectionProgress.setVisible(true);
        statusLabel.setText(message);
        connectionTimeout.playFromStart();
    }

    private void hideLoading() {
        connectionProgress.setVisible(false);
        connectionTimeout.stop();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #e74c3c;");
        showStatusMessage();
    }

    private void showInfo(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #2ecc71;");
        showStatusMessage();
    }

    private void showStatusMessage() {
        statusFade.stop();
        statusLabel.setOpacity(1.0);
        statusFade.playFromStart();
    }

    private void handleConnectionTimeout() {
        if (isConnecting || isJoiningLobby) {
            showError("Operation timed out");
            hideLoading();
            isConnecting = false;
            isJoiningLobby = false;
        }
    }

    @Override
    public void onLobbyUpdate(GameLobby lobby) {
        Platform.runLater(() -> {
            hideLoading();

            if (currentLobby != null &&
                    currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                handleCurrentLobbyUpdate(lobby);
            } else {
                updateLobbyList(lobby);
            }

            updateButtonStates();
        });
    }

    private void handleCurrentLobbyUpdate(GameLobby lobby) {
        currentLobby = lobby;
        lobbyPlayers.clear();
        lobbyPlayers.addAll(lobby.getPlayers().values());

        PlayerInfo currentPlayer = lobby.getPlayers().get(playerId);
        if (currentPlayer != null) {
            updateReadyButton(currentPlayer.isReady());
        }
    }

    private void updateLobbyList(GameLobby lobby) {
        int index = -1;
        for (int i = 0; i < activeLobbies.size(); i++) {
            if (activeLobbies.get(i).getLobbyId().equals(lobby.getLobbyId())) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            activeLobbies.set(index, lobby);
        } else {
            activeLobbies.add(lobby);
        }
    }

    @Override
    public void onGameStart(GameLobby lobby, GameState gameState) {
        Platform.runLater(() -> {
            hideLoading();
            try {
                mainApp.switchToGame(gameState);
            } catch (Exception e) {
                showError("Failed to start game: " + e.getMessage());
                LOGGER.severe("Error starting game: " + e.getMessage());
            }
        });
    }

    @Override
    public void onLobbyClosed(GameLobby lobby) {
        Platform.runLater(() -> {
            if (currentLobby != null &&
                    currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                currentLobby = null;
                lobbyPlayers.clear();
                showInfo("Lobby has been closed");
            }
            activeLobbies.removeIf(l -> l.getLobbyId().equals(lobby.getLobbyId()));
            updateButtonStates();
        });
    }

    private class ConnectionHandler implements ConnectionStatusHandler {
        @Override
        public void onConnected() {
            Platform.runLater(() -> {
                connectionStatusLabel.setText("Connected");
                connectionStatusLabel.setStyle("-fx-text-fill: #2ecc71;");
                hideLoading();
                enableGameControls();
                isConnecting = false;
            });
        }

        @Override
        public void onDisconnected(String reason) {
            Platform.runLater(() -> {
                connectionStatusLabel.setText("Disconnected: " + reason);
                connectionStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                disableGameControls();
                showError("Lost connection to server");
            });
        }

        @Override
        public void onConnectionFailed(String reason) {
            Platform.runLater(() -> {
                showError("Connection failed: " + reason);
                hideLoading();
                isConnecting = false;
            });
        }
    }

    private void enableGameControls() {
        createLobbyButton.setDisable(false);
        joinLobbyButton.setDisable(false);
        playerNameField.setDisable(false);
        updateButtonStates();
    }

    private void disableGameControls() {
        createLobbyButton.setDisable(true);
        joinLobbyButton.setDisable(true);
        readyButton.setDisable(true);
        startGameButton.setDisable(true);
        playerNameField.setDisable(true);
    }

    private static class PlayerReadyState {
        private volatile boolean ready;

        public boolean isReady() {
            return ready;
        }

        public void setReady(boolean ready) {
            this.ready = ready;
        }
    }

    // Setters for dependencies
    public void setMainApp(AzulApplication mainApp) {
        this.mainApp = mainApp;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void refreshLobbyView() {
        if (gameClient != null && gameClient.getCurrentLobby() != null) {
            handleCurrentLobbyUpdate(gameClient.getCurrentLobby());
            updateButtonStates();
        }
    }

    private class LobbyListCell extends ListCell<GameLobby> {
        private final VBox container;
        private final Label hostLabel;
        private final Label playersLabel;
        private final Label statusLabel;
        private final HBox statusContainer;
        private final ProgressBar fillBar;

        public LobbyListCell() {
            container = new VBox(5);
            container.getStyleClass().add("lobby-cell");
            container.setPadding(new javafx.geometry.Insets(10));

            hostLabel = new Label();
            hostLabel.getStyleClass().add("host-label");

            playersLabel = new Label();
            playersLabel.getStyleClass().add("players-label");

            statusLabel = new Label();
            statusLabel.getStyleClass().add("status-label");

            fillBar = new ProgressBar(0);
            fillBar.getStyleClass().add("fill-bar");
            fillBar.setMaxWidth(Double.MAX_VALUE);

            statusContainer = new HBox(10);
            statusContainer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            statusContainer.getChildren().addAll(playersLabel, statusLabel);

            container.getChildren().addAll(hostLabel, statusContainer, fillBar);

            setupStyles();
        }

        private void setupStyles() {
            container.setStyle("""
                -fx-background-color: white;
                -fx-border-color: #e0e0e0;
                -fx-border-radius: 5;
                -fx-background-radius: 5;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 0);
                """);

            hostLabel.setStyle("""
                -fx-font-weight: bold;
                -fx-font-size: 14px;
                """);

            playersLabel.setStyle("""
                -fx-font-size: 12px;
                -fx-text-fill: #666666;
                """);

            statusLabel.setStyle("""
                -fx-font-size: 12px;
                -fx-padding: 3 8;
                -fx-background-radius: 3;
                """);

            fillBar.setStyle("""
                -fx-accent: #4CAF50;
                """);
        }

        @Override
        protected void updateItem(GameLobby lobby, boolean empty) {
            super.updateItem(lobby, empty);

            if (empty || lobby == null) {
                setGraphic(null);
                return;
            }

            // Update host info
            PlayerInfo host = lobby.getPlayers().get(lobby.getHostId());
            hostLabel.setText("Host: " + (host != null ? host.getPlayerName() : "Unknown"));

            // Update player count and progress
            int playerCount = lobby.getPlayers().size();
            int maxPlayers = lobby.getSettings().getMaxPlayers();
            playersLabel.setText(String.format("Players: %d/%d", playerCount, maxPlayers));
            fillBar.setProgress((double) playerCount / maxPlayers);

            // Update status
            updateLobbyStatus(lobby);

            // Highlight if current lobby
            if (currentLobby != null &&
                    currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                container.setStyle(container.getStyle() + """
                    -fx-border-color: #2196F3;
                    -fx-effect: dropshadow(gaussian, rgba(33,150,243,0.2), 5, 0, 0, 0);
                    """);
            }

            setGraphic(container);
        }

        private void updateLobbyStatus(GameLobby lobby) {
            switch (lobby.getStatus()) {
                case WAITING -> {
                    statusLabel.setText("Waiting");
                    statusLabel.setStyle(statusLabel.getStyle() +
                            "-fx-background-color: #fff3cd; -fx-text-fill: #856404;");
                }
                case STARTING -> {
                    statusLabel.setText("Starting");
                    statusLabel.setStyle(statusLabel.getStyle() +
                            "-fx-background-color: #d4edda; -fx-text-fill: #155724;");
                }
                case IN_PROGRESS -> {
                    statusLabel.setText("In Progress");
                    statusLabel.setStyle(statusLabel.getStyle() +
                            "-fx-background-color: #cce5ff; -fx-text-fill: #004085;");
                }
                case FINISHED -> {
                    statusLabel.setText("Finished");
                    statusLabel.setStyle(statusLabel.getStyle() +
                            "-fx-background-color: #f8d7da; -fx-text-fill: #721c24;");
                }
            }
        }
    }

    private class PlayerListCell extends ListCell<PlayerInfo> {
        private final HBox container;
        private final Label nameLabel;
        private final Label readyLabel;
        private final Label hostLabel;
        private final Region spacer;

        public PlayerListCell() {
            container = new HBox(10);
            container.getStyleClass().add("player-cell");
            container.setPadding(new javafx.geometry.Insets(8));
            container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            nameLabel = new Label();
            nameLabel.getStyleClass().add("player-name");

            readyLabel = new Label();
            readyLabel.getStyleClass().add("ready-status");

            hostLabel = new Label("(Host)");
            hostLabel.getStyleClass().add("host-indicator");
            hostLabel.setVisible(false);

            spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

            container.getChildren().addAll(nameLabel, spacer, readyLabel, hostLabel);

            setupStyles();
        }

        private void setupStyles() {
            container.setStyle("""
                -fx-background-color: white;
                -fx-border-color: #e0e0e0;
                -fx-border-radius: 3;
                -fx-background-radius: 3;
                """);

            nameLabel.setStyle("""
                -fx-font-size: 13px;
                -fx-font-weight: bold;
                """);

            readyLabel.setStyle("""
                -fx-padding: 3 8;
                -fx-background-radius: 3;
                -fx-font-size: 12px;
                """);

            hostLabel.setStyle("""
                -fx-font-size: 12px;
                -fx-text-fill: #7f8c8d;
                -fx-font-style: italic;
                """);
        }

        @Override
        protected void updateItem(PlayerInfo player, boolean empty) {
            super.updateItem(player, empty);

            if (empty || player == null) {
                setGraphic(null);
                return;
            }

            nameLabel.setText(player.getPlayerName());

            // Update ready status
            readyLabel.setText(player.isReady() ? "Ready" : "Not Ready");
            if (player.isReady()) {
                readyLabel.setStyle(readyLabel.getStyle() + """
                    -fx-background-color: #d4edda;
                    -fx-text-fill: #155724;
                    """);
            } else {
                readyLabel.setStyle(readyLabel.getStyle() + """
                    -fx-background-color: #f8d7da;
                    -fx-text-fill: #721c24;
                    """);
            }

            // Show host indicator
            boolean isHost = currentLobby != null &&
                    currentLobby.isHost(player.getPlayerId());
            hostLabel.setVisible(isHost);

            // Highlight current player
            if (player.getPlayerId().equals(playerId)) {
                container.setStyle(container.getStyle() + """
                    -fx-background-color: #f8f9fa;
                    -fx-border-color: #2196F3;
                    """);
            }

            setGraphic(container);
        }
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public void cleanup() {
        if (connectionTimeout != null) {
            connectionTimeout.stop();
        }
        if (statusFade != null) {
            statusFade.stop();
        }
        activeLobbies.clear();
        lobbyPlayers.clear();
        playerReadyStates.clear();
    }
}