package hr.algebra.azul.controller;

import hr.algebra.azul.AzulApplication;
import hr.algebra.azul.model.*;
import hr.algebra.azul.network.*;
import hr.algebra.azul.network.game.*;
import hr.algebra.azul.network.recovery.*;
import hr.algebra.azul.network.scoring.*;
import hr.algebra.azul.network.serialization.*;
import hr.algebra.azul.view.GameResultWindow;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.geometry.Insets;
import javafx.stage.Stage;
import javafx.animation.*;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class GameController implements GameStateUpdateHandler {
    private static final Logger LOGGER = Logger.getLogger(GameController.class.getName());
    private static final Duration TURN_DURATION = Duration.seconds(60);
    private static final Duration WARNING_DURATION = Duration.seconds(10);

    // Game state
    private Game game;
    private TileColor selectedColor;
    private Factory selectedFactory;
    private boolean isSelectingTiles = false;

    // Network components
    private GameClient gameClient;
    private String playerId;
    private boolean isNetworkedGame;

    // Managers
    private GameMoveManager moveManager;
    private TurnManager turnManager;
    private ScoreSynchronizer scoreSynchronizer;
    private NetworkRecoveryManager recoveryManager;
    private GameEndManager endManager;
    private GameStateSerializer stateSerializer;

    // Application components
    private Stage stage;
    private AzulApplication mainApp;

    // UI Components
    @FXML private GridPane factoriesGrid;
    @FXML private FlowPane centralArea;
    @FXML private GridPane playerBoardsGrid;
    @FXML private HBox playerHandArea;
    @FXML private Label currentPlayerLabel;
    @FXML private Button endTurnButton;
    @FXML private Button saveGameButton;
    @FXML private Button loadGameButton;
    @FXML private VBox chatArea;
    @FXML private TextArea chatDisplay;
    @FXML private TextField chatInput;
    @FXML private Button sendChatButton;
    @FXML private ProgressBar turnProgressBar;
    @FXML private Label turnTimerLabel;
    @FXML private Label statusLabel;
    @FXML private VBox connectionStatusPane;
    @FXML private Label connectionStatusLabel;
    @FXML private ProgressBar reconnectionProgress;

    // Animations
    private Timeline turnTimer;
    private Timeline warningTimer;
    private FadeTransition statusFade;

    @FXML
    public void initialize() {
        setupUI();
        setupAnimations();
    }

    private void setupUI() {
        // Initialize UI components
        turnProgressBar.setProgress(0);
        reconnectionProgress.setVisible(false);
        setupFactoriesGrid();
        setupPlayerBoards();
        setupChat();
        setupButtons();
    }

    private void setupAnimations() {
        // Turn timer
        turnTimer = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(turnProgressBar.progressProperty(), 0)),
                new KeyFrame(TURN_DURATION,
                        new KeyValue(turnProgressBar.progressProperty(), 1))
        );
        turnTimer.setOnFinished(e -> handleTurnTimeout());

        // Warning timer
        warningTimer = new Timeline(
                new KeyFrame(WARNING_DURATION, e -> showTurnWarning())
        );

        // Status message fade
        statusFade = new FadeTransition(Duration.seconds(3), statusLabel);
        statusFade.setFromValue(1.0);
        statusFade.setToValue(0.0);
    }

    public void initializeNetworkedGame(GameState gameState, GameClient gameClient, String playerId) {
        this.gameClient = gameClient;
        this.playerId = playerId;
        this.isNetworkedGame = true;
        this.game = gameState.getGame();

        // Initialize managers
        initializeManagers(gameState);

        // Setup network handlers
        setupNetworkHandlers();

        // Start game systems
        startGameSystems();

        // Update view
        updateView();
    }

    private void initializeManagers(GameState gameState) {
        moveManager = new GameMoveManager(gameState.getGameId(), gameClient,
                new GameStateManager(gameClient, playerId));

        turnManager = new TurnManager(gameState.getGameId(), gameClient);
        turnManager.setTurnHandler(new TurnUpdateHandler());

        scoreSynchronizer = new ScoreSynchronizer(gameState.getGameId(), gameClient);
        scoreSynchronizer.setUpdateHandler(new ScoreHandler());

        recoveryManager = new NetworkRecoveryManager(gameClient, playerId,
                new GameStatePersistenceManager(gameState.getGameId(), gameClient));
        recoveryManager.setRecoveryHandler(new RecoveryHandler());

        endManager = new GameEndManager(gameState.getGameId(), gameClient);
        endManager.setEndHandler(new GameEndHandler());

        stateSerializer = new GameStateSerializer();
    }

    private void setupNetworkHandlers() {
        if (gameClient != null) {
            gameClient.setConnectionHandler(new ConnectionHandler());
            gameClient.setGameHandler(this);
        }
    }

    private void startGameSystems() {
        scoreSynchronizer.startSync();
        if (isCurrentPlayer()) {
            startTurn();
        }
    }

    private void startTurn() {
        turnTimer.playFromStart();
        warningTimer.playFromStart();
        enableGameControls();
        showInfo("Your turn");
    }

    private void endTurn() {
        if (!isNetworkedGame || !isCurrentPlayer()) return;

        turnTimer.stop();
        warningTimer.stop();
        disableGameControls();

        GameMoveManager.GameMove endTurnMove =
                GameMoveManager.GameMove.endTurn(playerId);

        if (moveManager.validateAndProcessMove(endTurnMove)) {
            turnManager.endTurn(playerId);
        }
    }

    private void handleTurnTimeout() {
        if (isCurrentPlayer()) {
            showAlert("Turn Timeout", "Your turn has ended");
            endTurn();
        }
    }

    private void showTurnWarning() {
        if (isCurrentPlayer()) {
            showAlert("Turn Warning", "10 seconds remaining!");
        }
    }

    @Override
    public void handleGameAction(GameAction action) {
        if (!isNetworkedGame) {
            super.handleGameAction(action);
            return;
        }

        GameMoveManager.GameMove move = convertActionToMove(action);
        if (move != null) {
            moveManager.validateAndProcessMove(move);
        }
    }

        private GameMoveManager.GameMove convertActionToMove(GameAction action) {
            return switch (action.getType()) {
                case SELECT_TILES -> GameMoveManager.GameMove.selectTiles(
                        action.getPlayerId(),
                        action.getFactoryIndex(),
                        action.getSelectedColor()
                );
                case PLACE_TILES -> GameMoveManager.GameMove.placeTiles(
                        action.getPlayerId(),
                        action.getSelectedColor(),
                        action.getPatternLineIndex()
                );
                case END_TURN -> GameMoveManager.GameMove.endTurn(
                        action.getPlayerId()
                );
            };
        }

    @FXML
    private void handleTileSelection(Rectangle tileRect, int factoryIndex, TileColor color) {
        if (!isNetworkedGame || !isCurrentPlayer() || isSelectingTiles) return;

        isSelectingTiles = true;
        selectedColor = color;
        selectedFactory = factoryIndex >= 0 ?
                game.getFactories().get(factoryIndex) : null;

        GameMoveManager.GameMove move = GameMoveManager.GameMove.selectTiles(
                playerId,
                factoryIndex,
                color
        );

        if (moveManager.validateAndProcessMove(move)) {
            highlightSelectedTiles(tileRect);
        } else {
            isSelectingTiles = false;
            selectedColor = null;
            selectedFactory = null;
            showError("Invalid tile selection");
        }
    }

    @FXML
    private void handlePatternLineSelection(int lineIndex) {
        if (!isNetworkedGame || !isCurrentPlayer() || !isSelectingTiles) return;

        GameMoveManager.GameMove move = GameMoveManager.GameMove.placeTiles(
                playerId,
                selectedColor,
                lineIndex
        );

        if (moveManager.validateAndProcessMove(move)) {
            isSelectingTiles = false;
            selectedColor = null;
            selectedFactory = null;
            clearSelections();

            if (game.getCurrentPlayer().getHand().isEmpty()) {
                endTurn();
            }
        } else {
            showError("Invalid pattern line selection");
        }
    }

    private void highlightSelectedTiles(Rectangle tileRect) {
        tileRect.setStroke(Color.YELLOW);
        tileRect.setStrokeWidth(2);
    }

    private void clearSelections() {
        factoriesGrid.lookupAll(".tile").forEach(node -> {
            if (node instanceof Rectangle) {
                ((Rectangle) node).setStroke(Color.BLACK);
                ((Rectangle) node).setStrokeWidth(1);
            }
        });
    }

    private boolean isCurrentPlayer() {
        return game.getCurrentPlayer().getName().equals(playerId);
    }

    private void enableGameControls() {
        endTurnButton.setDisable(false);
        factoriesGrid.setDisable(false);
        centralArea.setDisable(false);
        playerBoardsGrid.setDisable(false);
    }

    private void disableGameControls() {
        endTurnButton.setDisable(true);
        factoriesGrid.setDisable(true);
        centralArea.setDisable(true);
        playerBoardsGrid.setDisable(true);
    }

    private void showInfo(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #2ecc71;");
        showStatusMessage();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #e74c3c;");
        showStatusMessage();
    }

    private void showStatusMessage() {
        statusFade.stop();
        statusLabel.setOpacity(1.0);
        statusFade.playFromStart();
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();
        });
    }

    @Override
    public void cleanup() {
        if (turnTimer != null) turnTimer.stop();
        if (warningTimer != null) warningTimer.stop();
        if (statusFade != null) statusFade.stop();

        if (moveManager != null) moveManager.clean();
        if (turnManager != null) turnManager.cleanup();
        if (scoreSynchronizer != null) scoreSynchronizer.cleanup();
        if (recoveryManager != null) recoveryManager.cleanup();
        if (endManager != null) endManager.cleanup();
        if (stateSerializer != null) stateSerializer.cleanup();
    }

    // Inner handler classes
        private class TurnUpdateHandler implements TurnManager.TurnUpdateHandler {
            @Override
            public void onTurnStarted(String playerId) {
                Platform.runLater(() -> {
                    updateTurnDisplay(playerId);
                    if (playerId.equals(GameController.this.playerId)) {
                        startTurn();
                    } else {
                        disableGameControls();
                    }
                });
            }

        @Override
        public void onTurnWarning(String playerId) {
            if (playerId.equals(GameController.this.playerId)) {
                showTurnWarning();
            }
        }

        @Override
        public void onTurnResumed(String playerId) {
            Platform.runLater(() -> {
                if (playerId.equals(GameController.this.playerId)) {
                    showInfo("Your turn resumed");
                    startTurn();
                } else {
                    showInfo(playerId + "'s turn resumed");
                }
            });
        }

        @Override
        public void onPlayerRemoved(String playerId) {
            Platform.runLater(() -> {
                showAlert("Player Removed",
                        "Player " + playerId + " has been removed due to inactivity");
                updateView();
            });
        }

        @Override
        public void onTurnError(String message) {
            Platform.runLater(() -> showError(message));
        }
    }

    private class ScoreHandler implements ScoreSynchronizer.ScoreUpdateHandler {
        @Override
        public void onScoreUpdated(String playerId, int newScore,
                                   ScoreSynchronizer.ScoreType type, String reason) {
            Platform.runLater(() -> {
                updatePlayerScore(playerId, newScore);
                showInfo(playerId + " scored points: " + reason);
            });
        }

        @Override
        public void onScoreReconciliation(String playerId) {
            Platform.runLater(() -> {
                showInfo("Reconciling scores for " + playerId);
                updateView();
            });
        }

        @Override
        public void onScoreError(String message) {
            Platform.runLater(() -> showError(message));
        }
    }

    private class RecoveryHandler implements NetworkRecoveryManager.RecoveryHandler {
        @Override
        public void onPlayerDisconnected(String playerId) {
            Platform.runLater(() -> {
                showInfo("Player " + playerId + " disconnected");
                updatePlayerStatus(playerId, false);
            });
        }

        @Override
        public void onPlayerReconnected(String playerId) {
            Platform.runLater(() -> {
                showInfo("Player " + playerId + " reconnected");
                updatePlayerStatus(playerId, true);
                updateView();
            });
        }

        @Override
        public void onPlayerPermanentlyDisconnected(String playerId) {
            Platform.runLater(() -> {
                showAlert("Player Left",
                        "Player " + playerId + " has permanently disconnected");
                handlePermanentDisconnection(playerId);
            });
        }

        @Override
        public void onRecoveryError(String message) {
            Platform.runLater(() -> showError(message));
        }
    }

    private class GameEndHandler implements GameEndManager.GameEndHandler {
        @Override
        public void onGameEnd(GameEndManager.FinalGameResult result) {
            Platform.runLater(() -> showGameResults(result));
        }

        @Override
        public void onGameEndError(String message) {
            Platform.runLater(() -> {
                showError(message);
                mainApp.returnToLobby();
            });
        }
    }

    private class ConnectionHandler implements ConnectionStatusHandler {
        @Override
        public void onConnected() {
            Platform.runLater(() -> {
                connectionStatusLabel.setText("Connected");
                connectionStatusLabel.setStyle("-fx-text-fill: #2ecc71;");
                reconnectionProgress.setVisible(false);
                enableGameControls();
            });
        }

        @Override
        public void onDisconnected(String reason) {
            Platform.runLater(() -> {
                connectionStatusLabel.setText("Disconnected: " + reason);
                connectionStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                reconnectionProgress.setVisible(true);
                disableGameControls();
                handleDisconnection();
            });
        }

        @Override
        public void onConnectionFailed(String reason) {
            Platform.runLater(() -> {
                showError("Connection failed: " + reason);
                handleConnectionFailure(reason);
            });
        }
    }

    private void handleDisconnection() {
        if (isCurrentPlayer()) {
            turnTimer.pause();
            warningTimer.pause();
        }

        recoveryManager.handleDisconnection(playerId)
                .thenAccept(success -> {
                    if (!success) {
                        Platform.runLater(() -> handleConnectionFailure("Recovery failed"));
                    }
                });
    }

    private void handleConnectionFailure(String reason) {
        showAlert("Connection Error",
                "Lost connection to the game: " + reason + "\nReturning to lobby...");
        cleanup();
        mainApp.returnToLobby();
    }

    private void handlePermanentDisconnection(String playerId) {
        removePlayer(playerId);
        if (game.getPlayers().size() < 2) {
            showAlert("Game Ended",
                    "Not enough players to continue. Returning to lobby...");
            cleanup();
            mainApp.returnToLobby();
        } else {
            updateView();
        }
    }

    private void removePlayer(String playerId) {
        game.getPlayers().removeIf(p -> p.getName().equals(playerId));
        updatePlayerBoards();
    }

    private void showGameResults(GameEndManager.FinalGameResult result) {
        GameResultWindow resultWindow = new GameResultWindow(
                result.playerScores().stream()
                        .map(this::createFinalPlayer)
                        .toList()
        );

        resultWindow.setOnNewGameRequest(() -> {
            if (isCurrentPlayer()) {
                requestNewGame();
            } else {
                showInfo("Only the host can start a new game");
            }
        });

        resultWindow.setOnExitRequest(() -> {
            cleanup();
            mainApp.returnToLobby();
        });

        resultWindow.show();
    }

    private Player createFinalPlayer(GameEndManager.PlayerFinalScore score) {
        Player finalPlayer = new Player(score.playerId());
        finalPlayer.setScore(score.score());
        if (score.details() != null) {
            addScoreDetails(finalPlayer, score.details());
        }
        return finalPlayer;
    }

    private void addScoreDetails(Player player, GameEndManager.ScoreDetails details) {
        // Add any additional score details to the player object
        // This could be used for displaying detailed scoring breakdown
    }

    private void requestNewGame() {
        GameMessage newGameRequest = new GameMessage(
                MessageType.NEW_GAME_REQUEST,
                playerId,
                null,
                null
        );
        gameClient.sendGameMessage(newGameRequest);
    }

    @FXML
    private void handleChatSend() {
        if (!chatInput.getText().trim().isEmpty()) {
            sendChatMessage(chatInput.getText().trim());
            chatInput.clear();
        }
    }

    private void sendChatMessage(String message) {
        GameMessage chatMessage = new GameMessage(
                MessageType.CHAT,
                playerId,
                null,
                null,
                message
        );
        gameClient.sendGameMessage(chatMessage);
    }

    @Override
    public void onGameStateUpdate(GameState newState) {
        Platform.runLater(() -> {
            game = newState.getGame();
            updateView();
            endManager.checkGameEnd(game);
        });
    }

    @Override
    public void onGameMove(GameAction action) {
        Platform.runLater(() -> {
            handleGameAction(action);
            updateView();
        });
    }

    @Override
    public void onChatMessage(String senderId, String message) {
        Platform.runLater(() -> {
            if (chatDisplay != null) {
                chatDisplay.appendText(
                        String.format("[%s] %s: %s%n",
                                getFormattedTime(),
                                senderId,
                                message
                        )
                );
                chatDisplay.setScrollTop(Double.MAX_VALUE);
            }
        });
    }

    @Override
    public void onPlayerStatusChange(String playerId, boolean joined) {
        Platform.runLater(() -> {
            String status = joined ? "joined" : "left";
            showInfo("Player " + playerId + " has " + status + " the game");
            updatePlayerStatus(playerId, joined);
        });
    }

    private void updateView() {
        updateFactories();
        updateCentralArea();
        updatePlayerBoards();
        updatePlayerHand();
        updateTurnDisplay(game.getCurrentPlayer().getName());
        updateScores();
    }

    private void updateFactories() {
        factoriesGrid.getChildren().clear();
        List<Factory> factories = game.getFactories();

        int row = 0, col = 0;
        for (int i = 0; i < factories.size(); i++) {
            Factory factory = factories.get(i);
            StackPane factoryPane = createFactoryPane(factory, i);
            factoriesGrid.add(factoryPane, col, row);

            col++;
            if (col > 2) {
                col = 0;
                row++;
            }
        }
    }

    private StackPane createFactoryPane(Factory factory, int index) {
        StackPane pane = new StackPane();
        pane.setPrefSize(100, 100);
        pane.getStyleClass().add("factory-pane");

        FlowPane tilesPane = new FlowPane(5, 5);
        tilesPane.setPrefWrapLength(90);

        for (Tile tile : factory.getTiles()) {
            Rectangle tileRect = createTileRectangle(tile.getColor());
            final int factoryIndex = index;
            tileRect.setOnMouseClicked(e ->
                    handleTileSelection(tileRect, factoryIndex, tile.getColor()));
            tilesPane.getChildren().add(tileRect);
        }

        pane.getChildren().add(tilesPane);
        return pane;
    }

    private Rectangle createTileRectangle(TileColor color) {
        Rectangle rect = new Rectangle(20, 20);
        rect.setFill(getTileColor(color));
        rect.setStroke(Color.BLACK);
        rect.setStrokeWidth(1);
        rect.getStyleClass().add("tile");
        return rect;
    }

    private Color getTileColor(TileColor color) {
        return switch (color) {
            case RED -> Color.RED;
            case BLUE -> Color.BLUE;
            case YELLOW -> Color.YELLOW;
            case BLACK -> Color.BLACK;
            case WHITE -> Color.WHITE;
        };
    }

    private void updateCentralArea() {
        centralArea.getChildren().clear();

        for (Tile tile : game.getCentralArea().getTiles()) {
            Rectangle tileRect = createTileRectangle(tile.getColor());
            tileRect.setOnMouseClicked(e ->
                    handleTileSelection(tileRect, -1, tile.getColor()));
            centralArea.getChildren().add(tileRect);
        }
    }

    private void updatePlayerBoards() {
        playerBoardsGrid.getChildren().clear();
        List<Player> players = game.getPlayers();

        for (int i = 0; i < players.size(); i++) {
            GridPane playerBoard = createPlayerBoard(players.get(i));
            playerBoardsGrid.add(playerBoard, i % 2, i / 2);
        }
    }

    private GridPane createPlayerBoard(Player player) {
        GridPane board = new GridPane();
        board.setHgap(5);
        board.setVgap(5);
        board.setPadding(new Insets(10));
        board.getStyleClass().add("player-board");

        // Player name and score
        Label nameLabel = new Label(player.getName());
        nameLabel.getStyleClass().add("player-name");
        board.add(nameLabel, 0, 0, 2, 1);

        // Pattern lines
        for (int i = 0; i < 5; i++) {
            addPatternLine(board, player, i);
        }

        // Wall
        addWall(board, player.getWall());

        // Negative line
        addNegativeLine(board, player);

        return board;
    }

    private void updatePlayerStatus(String playerId, boolean connected) {
        playerBoardsGrid.getChildren().forEach(node -> {
            if (node instanceof GridPane board) {
                Label nameLabel = (Label) board.lookup(".player-name");
                if (nameLabel != null &&
                        nameLabel.getText().equals(playerId)) {
                    board.setOpacity(connected ? 1.0 : 0.5);
                }
            }
        });
    }

    private String getFormattedTime() {
        return java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    // Setters
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setMainApp(AzulApplication mainApp) {
        this.mainApp = mainApp;
    }

    public void setGame(Game game) {
        this.game = game;
        updateView();
    }
}

