package hr.algebra.azul.view;

import hr.algebra.azul.model.Player;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

public class GameResultWindow extends Stage {
    private static final double WINDOW_WIDTH = 600;
    private static final double WINDOW_HEIGHT = 400;
    private final VBox root;
    private Runnable onNewGameRequest;
    private Runnable onExitRequest;

    public GameResultWindow(List<Player> players) {
        // Configure the window
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.DECORATED);
        setTitle("Game Results");
        setResizable(false);

        // Create main container
        root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: white;");

        // Add title
        Label titleLabel = new Label("Game Over!");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        root.getChildren().add(titleLabel);

        // Add winner announcement
        Player winner = players.stream()
                .max(Comparator.comparingInt(Player::getScore))
                .orElseThrow();

        HBox winnerBox = createWinnerBox(winner);
        root.getChildren().add(winnerBox);

        // Add player results
        List<Player> sortedPlayers = new ArrayList<>(players);
        sortedPlayers.sort((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));

        VBox resultsBox = createResultsBox(sortedPlayers);
        root.getChildren().add(resultsBox);

        // Add buttons
        HBox buttonBox = createButtonBox();
        root.getChildren().add(buttonBox);

        // Set up the scene
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        setScene(scene);
    }

    private HBox createWinnerBox(Player winner) {
        HBox winnerBox = new HBox(10);
        winnerBox.setAlignment(Pos.CENTER);
        winnerBox.setPadding(new Insets(10));
        winnerBox.setStyle("-fx-background-color: #fef3c7; -fx-background-radius: 5; -fx-border-color: #fde68a; -fx-border-radius: 5;");

        Label trophyLabel = new Label("🏆"); // Unicode trophy
        trophyLabel.setFont(Font.font("System", 20));

        Label winnerLabel = new Label(String.format("%s wins with %d points!",
                winner.getName(), winner.getScore()));
        winnerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        winnerBox.getChildren().addAll(trophyLabel, winnerLabel);
        return winnerBox;
    }

    private VBox createResultsBox(List<Player> sortedPlayers) {
        VBox resultsBox = new VBox(10);
        resultsBox.setAlignment(Pos.CENTER);

        for (int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);
            VBox playerCard = createPlayerCard(player, i + 1);
            resultsBox.getChildren().add(playerCard);
        }

        return resultsBox;
    }

    private VBox createPlayerCard(Player player, int rank) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5; -fx-border-color: #e9ecef; -fx-border-radius: 5;");

        // Player name and rank
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label rankLabel = new Label(getRankSymbol(rank));
        rankLabel.setFont(Font.font("System", 18));

        Label nameLabel = new Label(player.getName());
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        Label scoreLabel = new Label(player.getScore() + " points");
        scoreLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        HBox.setHgrow(scoreLabel, Priority.ALWAYS);
        scoreLabel.setAlignment(Pos.CENTER_RIGHT);

        headerBox.getChildren().addAll(rankLabel, nameLabel, scoreLabel);

        // Score breakdown
        GridPane scoreBreakdown = new GridPane();
        scoreBreakdown.setHgap(10);
        scoreBreakdown.setVgap(5);

        // Calculate score components from the wall
        int rowPoints = player.getWall().calculateScore(); // This should be broken down further
        int negativePoints = player.calculateNegativeLinePenalty();

        addScoreRow(scoreBreakdown, "Wall Points:", rowPoints, 0);
        addScoreRow(scoreBreakdown, "Penalties:", negativePoints, 1);

        card.getChildren().addAll(headerBox, new Region(), scoreBreakdown);
        return card;
    }

    private void addScoreRow(GridPane grid, String label, int value, int row) {
        Label nameLabel = new Label(label);
        Label valueLabel = new Label(String.valueOf(value));
        valueLabel.setTextFill(value < 0 ? Color.RED : Color.BLACK);

        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private String getRankSymbol(int rank) {
        return switch (rank) {
            case 1 -> "🏆";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "🎮";
        };
    }

    private HBox createButtonBox() {
        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER);

        Button newGameButton = new Button("New Game");
        newGameButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        newGameButton.setOnAction(e -> {
            if (onNewGameRequest != null) {
                onNewGameRequest.run();
            }
            close();
        });

        Button exitButton = new Button("Exit to Menu");
        exitButton.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6;");
        exitButton.setOnAction(e -> {
            if (onExitRequest != null) {
                onExitRequest.run();
            }
            close();
        });

        buttonBox.getChildren().addAll(newGameButton, exitButton);
        return buttonBox;
    }

    public void setOnNewGameRequest(Runnable handler) {
        this.onNewGameRequest = handler;
    }

    public void setOnExitRequest(Runnable handler) {
        this.onExitRequest = handler;
    }
}