package hr.algebra.azul.controller;

import hr.algebra.azul.model.*;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;

import java.util.List;

public class GameController {
    private Game game;

    @FXML private Label currentPlayerLabel;
    @FXML private GridPane factoriesGrid;
    @FXML private FlowPane centralArea;
    @FXML private GridPane playerBoardsGrid;

    public void initializeGame() {
        game = new Game(2); // Start with 2 players for now
        game.startGame();
        updateView();
    }

    private void updateView() {
        updateCurrentPlayerLabel();
        updateFactories();
        updateCentralArea();
        updatePlayerBoards();
    }

    private void updateCurrentPlayerLabel() {
        currentPlayerLabel.setText(game.getCurrentPlayer().getName());
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
            tileRect.setOnMouseClicked(event -> onCentralAreaTileSelected(tile.getColor()));
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

        // Add pattern lines
        for (int i = 0; i < 5; i++) {
            List<Tile> patternLine = player.getPatternLines().getLine(i);
            for (int j = 0; j <= i; j++) {
                Rectangle tileRect = new Rectangle(20, 20);
                if (j < patternLine.size()) {
                    tileRect.setFill(getTileColor(patternLine.get(j).getColor()));
                } else {
                    tileRect.setFill(Color.WHITE);
                    tileRect.setStroke(Color.BLACK);
                }
                playerBoard.add(tileRect, j, i);
            }
        }

        // Add wall (simplified for now)
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                Rectangle tileRect = new Rectangle(20, 20, Color.LIGHTGRAY);
                tileRect.setStroke(Color.BLACK);
                playerBoard.add(tileRect, j + 6, i);
            }
        }

        return playerBoard;
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
        Factory selectedFactory = game.getFactories().get(factoryIndex);

        // For simplicity, we're using the first available pattern line.
        // In a real game, you'd want to let the player choose the pattern line.
        int patternLineIndex = getFirstAvailablePatternLine(currentPlayer, color);

        game.takeTurn(currentPlayer, selectedFactory, color, patternLineIndex);
        updateView();
    }

    @FXML
    private void onCentralAreaTileSelected(TileColor color) {
        Player currentPlayer = game.getCurrentPlayer();

        // Again, using the first available pattern line for simplicity
        int patternLineIndex = getFirstAvailablePatternLine(currentPlayer, color);

        game.takeTurnFromCentralArea(currentPlayer, color, patternLineIndex);
        updateView();
    }

    @FXML
    private void onEndTurn() {
        game.endTurn();
        updateView();
    }

    private int getFirstAvailablePatternLine(Player player, TileColor color) {
        PatternLines patternLines = player.getPatternLines();
        for (int i = 0; i < 5; i++) {
            if (patternLines.canAddTiles(color, i)) {
                return i;
            }
        }
        return -1; // Floor line
    }

    @FXML
    private void onSaveGame() {
        // TODO: Implement save game functionality
    }

    @FXML
    private void onLoadGame() {
        // TODO: Implement load game functionality
    }
}