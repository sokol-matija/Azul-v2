package hr.algebra.azul.controller;

import hr.algebra.azul.model.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
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
                    tileRect.setFill(Color.WHITE);
                    tileRect.setStroke(Color.BLACK);
                }
                playerBoard.add(tileRect, j, i + 1);
            }
        }

        // Add wall
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                Rectangle tileRect = new Rectangle(20, 20);
                if (player.getWall().hasTile(i, j)) {
                    tileRect.setFill(getTileColor(player.getWall().getTileColor(i, j)));
                } else {
                    tileRect.setFill(Color.LIGHTGRAY);
                    tileRect.setStroke(Color.BLACK);
                }
                playerBoard.add(tileRect, j + 6, i + 1);
            }
        }

        // Add floor line
        FlowPane floorLine = new FlowPane();
        floorLine.setHgap(2);
        for (Tile tile : player.getFloorLine()) {
            Rectangle tileRect = createTileRectangle(tile.getColor());
            floorLine.getChildren().add(tileRect);
        }
        playerBoard.add(floorLine, 0, 6, 11, 1);

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
        Factory selectedFactory = factoryIndex == -1 ? null : game.getFactories().get(factoryIndex);

        int patternLineIndex = promptForPatternLineSelection(currentPlayer, color);

        boolean turnTaken = game.takeTurn(currentPlayer, selectedFactory, color, patternLineIndex);

        if (!turnTaken) {
            showAlert("Invalid move", "This move is not allowed. Please try again.");
        }

        updateView();
    }

    private int promptForPatternLineSelection(Player player, TileColor color) {
        List<Integer> validLines = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (player.canAddTilesToPatternLine(color, i)) {
                validLines.add(i);
            }
        }
        validLines.add(-1); // Option for floor line

        ChoiceDialog<Integer> dialog = new ChoiceDialog<>(-1, validLines);
        dialog.setTitle("Select Pattern Line");
        dialog.setHeaderText("Choose a pattern line to place the tiles");
        dialog.setContentText("Pattern line:");

        Optional<Integer> result = dialog.showAndWait();
        return result.orElse(-1);
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
        updateView();
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
}