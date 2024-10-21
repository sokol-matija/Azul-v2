package hr.algebra.azul.controller;

import hr.algebra.azul.model.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.geometry.Insets;
import java.util.*;

public class GameController {
    private Game game;

    @FXML private Label currentPlayerLabel;
    @FXML private GridPane factoriesGrid;
    @FXML private FlowPane centralArea;
    @FXML private GridPane playerBoardsGrid;
    @FXML private HBox playerHandArea;
    @FXML private Button endTurnButton;
    @FXML private Button saveGameButton;
    @FXML private Button loadGameButton;

    public void initializeGame() {
        game = new Game(2); // Start with 2 players
        game.startGame();
        updateView();
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
            List<Tile> patternLine = player.getPatternLines().getLine(i);
            for (int j = 0; j <= i; j++) {
                Rectangle tileRect = new Rectangle(20, 20);
                if (j < patternLine.size()) {
                    tileRect.setFill(getTileColor(patternLine.get(j).getColor()));
                } else {
                    tileRect.setFill(Color.WHITE.deriveColor(0, 1, 0.5, 1)); // 50% darker white for empty pattern line slots
                }
                int finalI = i;
                tileRect.setOnMouseClicked(event -> onPatternLineClicked(player, finalI));
                playerBoard.add(tileRect, j, i + 1);
            }
        }

        // Add wall
        Wall wall = player.getWall();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                Rectangle tileRect = new Rectangle(20, 20);
                if (wall.hasTile(i, j)) {
                    tileRect.setFill(getTileColor(wall.getTileColor(i, j)));
                } else {
                    TileColor wallPatternColor = Wall.getWallPatternColor(i, j);
                    Color emptyColor = getTileColor(wallPatternColor).deriveColor(0, 1, 0.5, 0.5);
                    tileRect.setFill(emptyColor);
                }
                playerBoard.add(tileRect, j + 6, i + 1);
            }
        }

        // Add negative line
        FlowPane negativeLine = new FlowPane();
        negativeLine.setHgap(2);
        for (Tile tile : player.getNegativeLine()) {
            Rectangle tileRect = createTileRectangle(tile.getColor());
            negativeLine.getChildren().add(tileRect);
        }
        playerBoard.add(negativeLine, 0, 6, 11, 1);

        // Add negative line label
        Label negativeLineLabel = new Label("Negative Line: " + player.getNegativeLine().size() + " tiles");
        playerBoard.add(negativeLineLabel, 0, 7, 11, 1);

        return playerBoard;
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
        switch (color) {
            case RED: return Color.RED;
            case BLUE: return Color.BLUE;
            case YELLOW: return Color.YELLOW;
            case BLACK: return Color.BLACK;
            case WHITE: return Color.WHITE;
            default: return Color.GRAY;
        }
    }

    @FXML
    private void onTileSelected(int factoryIndex, TileColor color) {
        Player currentPlayer = game.getCurrentPlayer();
        Factory selectedFactory = factoryIndex == -1 ? null : game.getFactories().get(factoryIndex);

        List<Tile> selectedTiles;
        if (selectedFactory == null) {
            selectedTiles = game.getCentralArea().takeTiles(color);
        } else {
            selectedTiles = selectedFactory.takeTiles(color);
            game.getCentralArea().addTiles(selectedFactory.getRemainingTiles());
        }

        currentPlayer.addTilesToHand(selectedTiles);

        updateView();
    }

    private void onPatternLineClicked(Player player, int lineIndex) {
        if (player != game.getCurrentPlayer()) {
            showAlert("Invalid move", "It's not your turn!");
            return;
        }

        Map<TileColor, Integer> hand = player.getHand();
        if (hand.isEmpty()) {
            showAlert("Invalid move", "You don't have any tiles in your hand!");
            return;
        }

        TileColor colorToPlace = hand.keySet().iterator().next(); // Get the first color in hand

        // Check if the color is completed in the wall
        if (player.getWall().isColorCompleted(colorToPlace)) {
            showAlert("Invalid move", "This color is already completed in your wall!");
            return;
        }

        // Check if the color can be placed in this row of the wall
        if (!player.getWall().canPlaceTile(colorToPlace, lineIndex)) {
            showAlert("Invalid move", "You can't place this color in this row!");
            return;
        }

        // Check if the pattern line already contains a different color
        TileColor existingColor = player.getPatternLines().getLineColor(lineIndex);
        if (existingColor != null && existingColor != colorToPlace) {
            showAlert("Invalid move", "This line already contains tiles of a different color!");
            return;
        }

        boolean placed = player.placeTilesFromHand(colorToPlace, lineIndex);

        if (!placed) {
            showAlert("Invalid move", "You can't place these tiles in this line!");
        } else {
            updateView();
            if (player.getHand().isEmpty()) {
                game.endTurn();
                updateView();
            }
        }
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
        game.endTurn();
        if (game.isRoundEnd()) {
            game.endRound();
        }
        updateView();
        checkGameEnd();
    }

    @FXML
    private void onSaveGame() {
        // TODO: Implement save game functionality
        showAlert("Save Game", "Game saved successfully!");
    }

    @FXML
    private void onLoadGame() {
        // TODO: Implement load game functionality
        showAlert("Load Game", "Game loaded successfully!");
        updateView();
    }

    public void setGame(Game game) {
        this.game = game;
        updateView();
    }

    private void checkGameEnd() {
        if (game.isGameEnded()) {
            Player winner = game.getWinner();
            showAlert("Game Over", "The game has ended. " + winner.getName() + " wins with " + winner.getScore() + " points!");
            // TODO: Implement any post-game actions (e.g., resetting the game, showing final scores)
        }
    }

    public void onTurnEnd() {
        updateView();
        checkGameEnd();
    }
}