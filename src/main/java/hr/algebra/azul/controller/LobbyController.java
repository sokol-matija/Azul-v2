package hr.algebra.azul.controller;

import hr.algebra.azul.AzulApplication;
import hr.algebra.azul.model.User;
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
    @FXML private ListView<User> playersListView;
    @FXML private Button createLobbyButton;
    @FXML private Button joinLobbyButton;
    @FXML private Button readyButton;
    @FXML private Button startGameButton;
    @FXML private Label statusLabel;
    @FXML private HBox topContainer;
    // endregion


    // region Class Fields
    private final ObservableList<GameLobby> activeLobbies = FXCollections.observableArrayList();
    private final ObservableList<User> lobbyPlayers = FXCollections.observableArrayList();
    private GameClient gameClient;
    private String playerId;
    private GameLobby currentLobby;
    private AzulApplication mainApp;
    private Circle connectionIndicator;
    private Label connectionLabel;
    // endregion

    // region Initialization Methods
    @FXML
    public void initialize() {
        initializeListViews();
        setupConnectionStatus();
        setupEventListeners();
    }

    private void initializeListViews() {
        lobbyListView.setItems(activeLobbies);
        playersListView.setItems(lobbyPlayers);
        configureListViewCells();
    }

    public void initializeController(AzulApplication mainApp) {
        this.mainApp = mainApp;
        setupButtons();
        updateButtonStates();
    }

    private void setupConnectionStatus() {
        HBox statusBox = new HBox(10);
        connectionIndicator = new Circle(5);
        connectionLabel = new Label("Disconnected");

        connectionIndicator.setStyle("-fx-fill: #e74c3c;");
        statusBox.setStyle("-fx-padding: 5;");

        statusBox.getChildren().addAll(connectionIndicator, connectionLabel);
        topContainer.getChildren().add(0, statusBox);

        if (gameClient != null) {
            setupConnectionHandler();
        }
    }

    private void setupButtons() {
        if (mainApp == null) return; // Guard against null mainApp

        // Initial states
        startGameButton.setDisable(true);
        readyButton.setDisable(true);

        configureButtonStyles();
        configureButtonTooltips();
        updateButtonStates();
    }

    private void setupEventListeners() {
        createLobbyButton.setOnAction(e -> createLobby());
        joinLobbyButton.setOnAction(e -> joinSelectedLobby());
        readyButton.setOnAction(e -> toggleReady());
        startGameButton.setOnAction(e -> startGame());

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
        try {
            User currentUser = mainApp.getCurrentUser();
            if (currentUser == null) {
                showError("Not logged in");
                return;
            }

            GameLobby newLobby = new GameLobby(currentUser);
            // Add the host as first player
            newLobby.addPlayer(currentUser);

            LobbyMessage createMessage = new LobbyMessage(
                    LobbyMessageType.LOBBY_UPDATE,
                    newLobby
            );
            gameClient.sendLobbyMessage(createMessage);

            // Don't update local state until we get server confirmation
            showSuccess("Creating new lobby...");
        } catch (Exception e) {
            showError("Failed to create lobby: " + e.getMessage());
        }
    }




    private void toggleReady() {
        User currentUser = mainApp.getCurrentUser();
        if (currentLobby != null && currentUser != null) {
            User player = currentLobby.getPlayers().get(currentUser.getId());
            if (player != null) {
                player.setReady(!player.isReady());
                gameClient.sendMessage(new LobbyMessage(
                        LobbyMessageType.LOBBY_UPDATE,
                        currentLobby
                ));
            }
        }
    }

    private void startGame() {
        User currentUser = mainApp.getCurrentUser();
        if (currentLobby != null &&
                currentUser != null &&
                currentLobby.isHost(currentUser.getId()) &&
                currentLobby.canStart()) {
            gameClient.sendMessage(new LobbyMessage(
                    LobbyMessageType.GAME_START,
                    currentLobby
            ));
        }
    }
    // endregion

    // region Update Methods
    private void joinSelectedLobby() {
        GameLobby selected = lobbyListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a lobby to join");
            return;
        }

        User currentUser = mainApp.getCurrentUser();
        if (currentUser == null) {
            showError("User not logged in");
            return;
        }

        // Disable join button while processing
        joinLobbyButton.setDisable(true);

        try {
            // Create join message with the SELECTED lobby's ID
            LobbyMessage joinMessage = new LobbyMessage(
                    LobbyMessageType.PLAYER_JOINED,
                    selected  // Use the selected lobby object directly
            );
            gameClient.sendLobbyMessage(joinMessage);

            // Don't update local state yet - wait for server confirmation
            showSuccess("Join request sent to lobby: " + selected.getHost().getUsername() + "'s game");

        } catch (Exception e) {
            showError("Failed to join lobby: " + e.getMessage());
            joinLobbyButton.setDisable(false);
        }
    }

    // Add this helper method to verify lobby exists
    private boolean verifyLobbyExists(String lobbyId) {
        return lobbyListView.getItems().stream()
                .anyMatch(lobby -> lobby.getLobbyId().equals(lobbyId));
    }


    public void debugPrintPlayersList() {
        System.out.println("Current players in lobby:");
        if (currentLobby != null) {
            currentLobby.getPlayers().forEach((id, user) ->
                    System.out.println("ID: " + id + ", Name: " + user.getUsername()));
        }
        System.out.println("Players in observable list:");
        lobbyPlayers.forEach(user ->
                System.out.println("ID: " + user.getId() + ", Name: " + user.getUsername()));
    }

    private void updatePlayersList() {
        lobbyPlayers.clear();
        if (currentLobby != null) {
            // Make sure we're using the values from the players map
            lobbyPlayers.addAll(currentLobby.getPlayers().values());

            // Debug logging
            System.out.println("Updating players list. Count: " + lobbyPlayers.size());
            for (User player : lobbyPlayers) {
                System.out.println("Player: " + player.getUsername() + " (ID: " + player.getId() + ")");
            }
        }
    }

    private void updateButtonStates() {
        if (mainApp == null) return; // Guard against null mainApp

        User currentUser = mainApp.getCurrentUser();
        if (currentUser == null) return; // Guard against null user

        boolean isInLobby = currentLobby != null;
        boolean isHost = isInLobby && currentLobby.getHost().getId().equals(currentUser.getId());
        boolean canStartGame = isHost && currentLobby.canStart();

        // Update button states based on user state
        createLobbyButton.setDisable(isInLobby);
        joinLobbyButton.setDisable(isInLobby ||
                lobbyListView.getSelectionModel().getSelectedItem() == null);
        readyButton.setDisable(!isInLobby || isHost);
        startGameButton.setDisable(!isHost || !canStartGame);

        // Update ready button text
        if (isInLobby) {
            User player = currentLobby.getPlayers().get(currentUser.getId());
            if (player != null) {
                readyButton.setText(player.isReady() ? "Not Ready" : "Ready");
                readyButton.setStyle(player.isReady() ?
                        "-fx-background-color: #FF9800;" :
                        "-fx-background-color: #FFC107;");
            }
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
            if (lobby != null) {
                // Update the lobby in the list view if it exists, or add it if it's new
                int existingIndex = findLobbyIndex(lobby.getLobbyId());
                if (existingIndex >= 0) {
                    activeLobbies.set(existingIndex, lobby);
                } else {
                    activeLobbies.add(lobby);
                }

                // If this is our current lobby, update it
                if (currentLobby != null && currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                    currentLobby = lobby;
                    updatePlayersList();
                }

                // If we're in this lobby but it's not marked as current, update our state
                if (mainApp.getCurrentUser() != null &&
                        lobby.getPlayers().containsKey(mainApp.getCurrentUser().getId()) &&
                        (currentLobby == null || !currentLobby.getLobbyId().equals(lobby.getLobbyId()))) {
                    currentLobby = lobby;
                    updatePlayersList();
                }

                updateButtonStates();
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
        initializeController(mainApp); // Call initialization after setting mainApp
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
                container.setPadding(new Insets(5));

                Label hostLabel = new Label("Host: " + lobby.getHost().getUsername());
                hostLabel.setStyle("-fx-font-weight: bold;");

                Label playersLabel = new Label(String.format("Players: %d/%d",
                        lobby.getPlayers().size(),
                        lobby.getSettings().getMaxPlayers()));

                Label statusLabel = new Label("Status: " + lobby.getStatus());

                container.getChildren().addAll(hostLabel, playersLabel, statusLabel);

                if (currentLobby != null &&
                        currentLobby.getLobbyId().equals(lobby.getLobbyId())) {
                    container.setStyle("-fx-background-color: #e3f2fd;");
                }

                setGraphic(container);
            }
        }
    }

    private class PlayerListCell extends ListCell<User> {
        @Override
        protected void updateItem(User user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox container = new HBox(10);
                container.setPadding(new Insets(5));
                container.setAlignment(Pos.CENTER_LEFT);

                // Add host crown if player is host
                if (currentLobby != null &&
                        currentLobby.getHost().getId().equals(user.getId())) {
                    Label crownLabel = new Label("👑");
                    crownLabel.setStyle("-fx-font-size: 14px;");
                    container.getChildren().add(crownLabel);
                }

                // Player name
                Label nameLabel = new Label(user.getUsername());
                nameLabel.setStyle("-fx-font-weight: bold;");

                // Ready status
                Label readyLabel = new Label(user.isReady() ? "Ready" : "Not Ready");
                readyLabel.setStyle(user.isReady() ?
                        "-fx-text-fill: #2ecc71; -fx-font-weight: bold;" :
                        "-fx-text-fill: #e74c3c; -fx-font-weight: bold;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                container.getChildren().addAll(nameLabel, spacer, readyLabel);

                // Highlight current user
                if (mainApp != null &&
                        mainApp.getCurrentUser() != null &&
                        user.getId().equals(mainApp.getCurrentUser().getId())) {
                    container.setStyle("-fx-background-color: #e8f5e9; -fx-background-radius: 4;");
                }

                setGraphic(container);
            }
        }
    }
}