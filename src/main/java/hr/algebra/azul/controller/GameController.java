// File: src/main/java/hr/algebra/azul/controller/GameController.java
package hr.algebra.azul.controller;

import hr.algebra.azul.AzulApplication;
import hr.algebra.azul.model.*;
import hr.algebra.azul.network.*;
import hr.algebra.azul.view.GameResultWindow;
import hr.algebra.azul.network.server.LobbyMessageType;
import hr.algebra.azul.network.GameMessage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.geometry.Insets;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.*;

public class GameController implements GameStateUpdateHandler {
    private Game game;
    private static final double WALL_TILE_BORDER_WIDTH = 2.5;
    private static final double REGULAR_TILE_BORDER_WIDTH = 0.5;

    // Network-related fields
    private Stage stage;
    private AzulApplication mainApp;
    private GameClient gameClient;
    private String playerId;
    private boolean isNetworkedGame = false;
    private TurnManager turnManager;
    private NetworkGameState networkGameState;
    private MultiplayerGameManager gameManager;

    @FXML private Label turnTimerLabel;
    @FXML private Label currentPlayerLabel;
    @FXML private GridPane factoriesGrid;
    @FXML private FlowPane centralArea;
    @FXML private GridPane playerBoardsGrid;
    @FXML private HBox playerHandArea;
    @FXML private Button endTurnButton;
    @FXML private Button saveGameButton;
    @FXML private Button loadGameButton;
    @FXML private Button newRoundButton;
    @FXML private VBox chatArea;
    @FXML private TextArea chatDisplay;
    @FXML private TextField chatInput;
    @FXML private Button sendChatButton;

    private void initializeTurnBasedGame() {
        turnManager = new TurnManager(
                unused -> handleTurnTimeout(),
                remainingTime -> updateTurnTimer(remainingTime)
        );

        // Initialize turn timer label
        turnTimerLabel = new Label();
        turnTimerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        // Add turnTimerLabel to your UI (update your FXML)

        if (isNetworkedGame && gameClient != null) {
            networkGameState = new NetworkGameState(new GameState(game));
            if (isCurrentPlayer()) {
                startPlayerTurn();
            }
        }
    }

    private void startPlayerTurn() {
        if (!isNetworkedGame) return;

        Platform.runLater(() -> {
            turnManager.startTurn();
            showAlert("Your Turn", "You have 30 seconds to make your move!");
        });
    }

    private void handleTurnTimeout() {
        if (!isNetworkedGame) return;

        // Force end the current turn
        GameAction timeoutAction = new GameAction(
                GameAction.ActionType.END_TURN,
                -1,
                null,
                -1
        );
        sendGameAction(timeoutAction);
        endTurnInternal();
    }

    private void updateTurnTimer(int remainingSeconds) {
        Platform.runLater(() -> {
            turnTimerLabel.setText(String.format("Time remaining: %ds", remainingSeconds));
            if (remainingSeconds <= 5) {
                turnTimerLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px; -fx-font-weight: bold;");
            }
        });
    }

    private void endTurnInternal() {
        turnManager.endTurn();
        game.endTurn();
        if (game.isRoundEnd()) {
            game.endRound();
        }
        updateView();

        // Send updated game state to other players
        if (isNetworkedGame) {
            GameState newState = new GameState(game);
            GameMessage syncMessage = new GameMessage(
                    MessageType.SYNC,
                    playerId,
                    null,
                    newState
            );
            gameClient.sendGameMessage(syncMessage);
        }
    }


    // Setters for stage and main application
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setMainApp(AzulApplication mainApp) {
        this.mainApp = mainApp;
    }

    public void initializeGame() {
        game = new Game(2); // Start with 2 players for local game
        game.startGame();
        setupNewRoundButton();
        updateView();
    }

    public void initializeNetworkedGame(GameState gameState, GameClient gameClient, String playerId) {
        this.gameClient = gameClient;
        this.playerId = playerId;
        this.isNetworkedGame = true;
        this.game = gameState.getGame();

        // Initialize multiplayer manager
        gameManager = new MultiplayerGameManager(
                gameClient,
                playerId,
                gameState.getConnectedPlayers()
        );

        // Set up callbacks
        gameManager.setOnStateUpdate(this::handleGameStateUpdate);
        gameManager.setOnPlayerTimeout(this::handlePlayerTimeout);
        gameManager.setOnTurnTick(this::updateTurnTimer);
        gameManager.setOnTurnTimeout(unused -> handleTurnTimeout());

        // Start multiplayer management
        gameManager.start();

        // Initialize UI
        setupNetworkHandlers();
        setupChatHandlers();
        updateView();
    }



    private void setupNetworkHandlers() {
        if (!isNetworkedGame) return;

        // Already implementing GameStateUpdateHandler interface/////////////////////////////////////////////////////////////////
        gameClient.setGameHandler(this);
    }////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void setupChatHandlers() {
        if (!isNetworkedGame) {
            chatArea.setVisible(false);
            return;
        }

        sendChatButton.setOnAction(e -> sendChatMessage());
        chatInput.setOnAction(e -> sendChatMessage());
    }

    private void sendChatMessage() {
        if (!chatInput.getText().trim().isEmpty()) {
            GameMessage chatMessage = new GameMessage(
                    MessageType.CHAT,
                    playerId,
                    null,
                    null,
                    chatInput.getText()
            );
            gameClient.sendGameMessage(chatMessage);
            chatInput.clear();
        }
    }

    private void setupNewRoundButton() {
        newRoundButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        newRoundButton.setOnAction(event -> startNewRound());
    }

    private void startNewRound() {
        game.endRound();
        for (Player player : game.getPlayers()) {
            player.startNewTurn();
        }
        updateView();
        showAlert("New Round", "A new round has been started!");
    }

    private void updateView() {
        updateCurrentPlayerLabel();
        updateFactories();
        updateCentralArea();
        updatePlayerBoards();
        updatePlayerHand();
    }

    private void updateCurrentPlayerLabel() {
        currentPlayerLabel.setText("Current Player: " + game.getCurrentPlayer().getName());
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

    private StackPane createFactoryPane(Factory factory, int factoryIndex) {
        StackPane factoryPane = new StackPane();
        factoryPane.setPrefSize(100, 100);
        Rectangle background = new Rectangle(100, 100, Color.LIGHTGRAY);
        factoryPane.getChildren().add(background);

        FlowPane tilesPane = new FlowPane();
        tilesPane.setHgap(5);
        tilesPane.setVgap(5);

        for (Tile tile : factory.getTiles()) {
            Rectangle tileRect = createTileRectangle(tile.getColor());
            int finalFactoryIndex = factoryIndex;
            tileRect.setOnMouseClicked(event -> onTileSelected(finalFactoryIndex, tile.getColor()));
            tilesPane.getChildren().add(tileRect);
        }

        factoryPane.getChildren().add(tilesPane);
        return factoryPane;
    }

    private void updateCentralArea() {
        centralArea.getChildren().clear();
        for (Tile tile : game.getCentralArea().getTiles()) {
            Rectangle tileRect = createTileRectangle(tile.getColor());
            tileRect.setOnMouseClicked(event -> onTileSelected(-1, tile.getColor()));
            centralArea.getChildren().add(tileRect);
        }
    }

    private void updatePlayerBoards() {
        playerBoardsGrid.getChildren().clear();
        List<Player> players = game.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            GridPane playerBoard = createPlayerBoard(player);
            playerBoardsGrid.add(playerBoard, i % 2, i / 2);
        }
    }

    private GridPane createPlayerBoard(Player player) {
        GridPane playerBoard = new GridPane();
        playerBoard.setHgap(5);
        playerBoard.setVgap(5);
        playerBoard.setPadding(new Insets(10));

        // Add player name and score
        Label nameLabel = new Label(player.getName() + " - Score: " + player.getScore());
        playerBoard.add(nameLabel, 0, 0, 6, 1);

        // Add pattern lines
        for (int i = 0; i < 5; i++) {
            addPatternLine(playerBoard, player, i);
        }

        // Add wall
        addWall(playerBoard, player.getWall());

        // Add negative line
        addNegativeLine(playerBoard, player);

        return playerBoard;
    }

    private void addPatternLine(GridPane playerBoard, Player player, int row) {
        List<Tile> patternLine = player.getPatternLines().getLine(row);
        for (int j = 0; j <= row; j++) {
            Rectangle tileRect = new Rectangle(20, 20);
            if (j < patternLine.size()) {
                tileRect.setFill(getTileColor(patternLine.get(j).getColor()));
                tileRect.setStroke(Color.BLACK);
                tileRect.setStrokeWidth(REGULAR_TILE_BORDER_WIDTH);
            } else {
                tileRect.setFill(Color.WHITE.deriveColor(0, 1, 0.5, 1));
                tileRect.setStroke(Color.GRAY);
                tileRect.setStrokeWidth(REGULAR_TILE_BORDER_WIDTH);
            }
            int finalRow = row;
            tileRect.setOnMouseClicked(event -> onPatternLineClicked(player, finalRow));
            playerBoard.add(tileRect, j, row + 1);
        }
    }

    private void addWall(GridPane playerBoard, Wall wall) {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                Rectangle tileRect = new Rectangle(20, 20);
                if (wall.hasTile(i, j)) {
                    tileRect.setFill(getTileColor(wall.getTileColor(i, j)));
                    tileRect.setStroke(Color.BLACK);
                    tileRect.setStrokeWidth(WALL_TILE_BORDER_WIDTH);
                    tileRect.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 2, 0, 0, 1);");
                } else {
                    TileColor wallPatternColor = Wall.getWallPatternColor(i, j);
                    Color emptyColor = getTileColor(wallPatternColor).deriveColor(0, 1, 0.5, 0.3);
                    tileRect.setFill(emptyColor);
                    tileRect.setStroke(Color.GRAY);
                    tileRect.setStrokeWidth(REGULAR_TILE_BORDER_WIDTH);
                }
                playerBoard.add(tileRect, j + 6, i + 1);
            }
        }
    }

    private void addNegativeLine(GridPane playerBoard, Player player) {
        FlowPane negativeLine = new FlowPane();
        negativeLine.setHgap(2);
        for (Tile tile : player.getNegativeLine()) {
            Rectangle tileRect = createTileRectangle(tile.getColor());
            negativeLine.getChildren().add(tileRect);
        }
        playerBoard.add(negativeLine, 0, 6, 11, 1);

        Label negativeLineLabel = new Label("Negative Line: " + player.getNegativeLine().size() + " tiles");
        playerBoard.add(negativeLineLabel, 0, 7, 11, 1);
    }

    private void updatePlayerHand() {
        playerHandArea.getChildren().clear();
        Map<TileColor, Integer> hand = game.getCurrentPlayer().getHand();
        for (Map.Entry<TileColor, Integer> entry : hand.entrySet()) {
            TileColor color = entry.getKey();
            int count = entry.getValue();
            StackPane tileStack = new StackPane();
            Rectangle tileRect = createTileRectangle(color);
            Label countLabel = new Label(String.valueOf(count));
            countLabel.setStyle("-fx-font-weight: bold;");
            tileStack.getChildren().addAll(tileRect, countLabel);
            playerHandArea.getChildren().add(tileStack);
        }
    }

    private Rectangle createTileRectangle(TileColor color) {
        Rectangle tileRect = new Rectangle(20, 20);
        tileRect.setFill(getTileColor(color));
        tileRect.setStroke(Color.BLACK);
        return tileRect;
    }

    private Color getTileColor(TileColor color) {
        return switch (color) {
            case RED -> Color.RED;
            case BLUE -> Color.BLUE;
            case YELLOW -> Color.YELLOW;
            case BLACK -> Color.BLACK;
            case WHITE -> Color.WHITE;
            default -> Color.GRAY;
        };
    }

    private void onTileSelected(int factoryIndex, TileColor color) {
        if (isNetworkedGame && !isCurrentPlayer()) {
            showAlert("Not Your Turn", "Please wait for your turn");
            return;
        }

        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer.hasSelectedThisTurn()) {
            showAlert("Invalid move", "You have already selected tiles this turn!");
            return;
        }

        Factory selectedFactory = factoryIndex == -1 ? null : game.getFactories().get(factoryIndex);
        boolean success = game.takeTurn(currentPlayer, selectedFactory, color, -1);

        if (!success) {
            showAlert("Invalid move", "Unable to select tiles!");
            return;
        }

        if (isNetworkedGame) {
            // Send move to other players
            GameAction action = new GameAction(
                    GameAction.ActionType.SELECT_TILES,
                    factoryIndex,
                    color,
                    -1
            );
            sendGameAction(action);
        }

        updateView();
    }

    private void onPatternLineClicked(Player player, int lineIndex) {
        if (isNetworkedGame && !isCurrentPlayer()) {
            showAlert("Not Your Turn", "Please wait for your turn");
            return;
        }

        if (player != game.getCurrentPlayer()) {
            showAlert("Invalid move", "It's not your turn!");
            return;
        }

        Map<TileColor, Integer> hand = player.getHand();
        if (hand.isEmpty()) {
            showAlert("Invalid move", "You don't have any tiles in your hand!");
            return;
        }

        TileColor colorToPlace = hand.keySet().iterator().next();

        if (player.getWall().isColorCompleted(colorToPlace)) {
            showAlert("Invalid move", "This color is already completed in your wall!");
            return;
        }

        if (!player.getWall().canPlaceTile(colorToPlace, lineIndex)) {
            showAlert("Invalid move", "You can't place this color in this row!");
            return;
        }

        boolean placed = player.placeTilesFromHand(colorToPlace, lineIndex);

        if (!placed) {
            showAlert("Invalid move", "You can't place these tiles in this line!");
            return;
        }

        if (isNetworkedGame) {
            // Send move to other players
            GameAction action = new GameAction(
                    GameAction.ActionType.PLACE_TILES,
                    -1,
                    colorToPlace,
                    lineIndex
            );
            sendGameAction(action);
        }

        updateView();
        if (player.getHand().isEmpty()) {
            game.endTurn();
            updateView();
            checkGameEnd();
        }
    }

    private void sendGameAction(GameAction action) {
        GameMessage message = new GameMessage(
                MessageType.MOVE,
                playerId,
                action,
                new GameState(game)
        );
        gameClient.sendGameMessage(message);
    }

    private boolean isCurrentPlayer() {
        return game.getCurrentPlayer().getName().equals(playerId);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void onEndTurn() {
        if (isNetworkedGame && !isCurrentPlayer()) {
            showAlert("Not Your Turn", "Please wait for your turn");
            return;
        }

        game.endTurn();
        if (game.isRoundEnd()) {
            game.endRound();
        }

        if (isNetworkedGame) {
            GameAction action = new GameAction(
                    GameAction.ActionType.END_TURN,
                    -1,
                    null,
                    -1
            );
            sendGameAction(action);
        }

        updateView();
        checkGameEnd();
    }

    @FXML
    private void onSaveGame() {
        // TODO: Implement save game functionality with network state
        if (isNetworkedGame) {
            showAlert("Save Game", "Saving networked games is not yet implemented!");
            return;
        }
        showAlert("Save Game", "Game saved successfully!");
    }

    @FXML
    private void onLoadGame() {
        // TODO: Implement load game functionality with network state
        if (isNetworkedGame) {
            showAlert("Load Game", "Loading networked games is not yet implemented!");
            return;
        }
        showAlert("Load Game", "Game loaded successfully!");
        updateView();
    }

    private void checkGameEnd() {
        if (game.isGameEnded()) {
            GameResultWindow resultWindow = new GameResultWindow(game.getPlayers());

            resultWindow.setOnNewGameRequest(() -> {
                if (isNetworkedGame) {
                    // Handle networked new game request
                    showAlert("New Game", "Starting a new networked game...");
                    // TODO: Implement networked new game logic
                } else {
                    initializeGame();
                    updateView();
                }
            });

            resultWindow.setOnExitRequest(() -> {
                if (isNetworkedGame) {
                    // Clean up network resources
                    cleanup();
                }
//                // Return to main menu or lobby
//                try {
//                    //mainApp.showLobbyScreen();
////                } catch (IOException e) {
////                    showAlert("Error", "Failed to return to lobby: " + e.getMessage());
////                }
            });

            resultWindow.show();
        }
    }

    // GameStateUpdateHandler implementation
    @Override
    public void onGameStateUpdate(GameState newState) {
        Platform.runLater(() -> {
            game = newState.getGame();
            if (isCurrentPlayer()) {
                startPlayerTurn();
            }
            updateView();
        });
    }

    @Override
    public void onGameMove(GameAction action) {
        Platform.runLater(() -> {
            processNetworkMove(action);
            updateView();
        });
    }

    @Override
    public void onChatMessage(String senderId, String message) {
        Platform.runLater(() -> {
            if (chatDisplay != null) {
                chatDisplay.appendText(String.format("%s: %s\n", senderId, message));
            }
        });
    }

    @Override
    public void onPlayerStatusChange(String playerId, boolean joined) {
        Platform.runLater(() -> {
            String message = joined ? "joined" : "left";
            showAlert("Player Status", "Player " + playerId + " has " + message + " the game");
            if (!joined) {
                handlePlayerDisconnection(playerId);
            }
        });
    }

    @Override
    public String getCurrentPlayerId() {
        return playerId;
    }

    private void processNetworkMove(GameAction action) {
        if (action == null) return;

        switch (action.getType()) {
            case SELECT_TILES:
                handleNetworkTileSelection(action);
                break;
            case PLACE_TILES:
                handleNetworkTilePlacement(action);
                break;
            case END_TURN:
                handleNetworkEndTurn();
                turnManager.endTurn();
                break;
        }
    }

    private void handleNetworkTileSelection(GameAction action) {
        Player currentPlayer = game.getCurrentPlayer();
        Factory selectedFactory = action.getFactoryIndex() == -1 ?
                null : game.getFactories().get(action.getFactoryIndex());
        game.takeTurn(currentPlayer, selectedFactory, action.getSelectedColor(), -1);
    }

    private void handleNetworkTilePlacement(GameAction action) {
        Player currentPlayer = game.getCurrentPlayer();
        currentPlayer.placeTilesFromHand(action.getSelectedColor(), action.getPatternLineIndex());
    }

    private void handleNetworkEndTurn() {
        game.endTurn();
        if (game.isRoundEnd()) {
            game.endRound();
        }
    }

    private void handlePlayerDisconnection(String disconnectedPlayerId) {
        // Handle player disconnection
        // For now, just show an alert
        showAlert("Player Disconnected",
                "Player " + disconnectedPlayerId + " has disconnected from the game");

        // In a more sophisticated implementation, you might want to:
        // 1. Pause the game
        // 2. Wait for reconnection
        // 3. Save the game state
        // 4. Return to lobby if too many players disconnect
    }



    public void cleanup() {
        if (isNetworkedGame && gameClient != null) {
            // Send a leave message before disconnecting
            GameMessage leaveMessage = new GameMessage(
                    MessageType.LEAVE,
                    playerId,
                    null,
                    null
            );
            gameClient.sendGameMessage(leaveMessage);
        }
    }

    public void setGame(Game game) {
        this.game = game;
        updateView();
    }

    public void onTurnEnd() {
        updateView();
        checkGameEnd();
    }
}