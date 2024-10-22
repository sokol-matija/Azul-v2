package hr.algebra.azul;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import hr.algebra.azul.controller.GameController;
import hr.algebra.azul.model.Game;
import java.util.logging.Logger;

public class LocalGameLauncher extends Application {
    private static final Logger LOGGER = Logger.getLogger(LocalGameLauncher.class.getName());

    @Override
    public void start(Stage stage) {
        try {
            // Load the game view FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("game-view.fxml"));
            Scene scene = new Scene(loader.load(), 1200, 800);

            // Get and initialize the controller
            GameController gameController = loader.getController();
            gameController.setStage(stage);

            // Create a new local game
            Game game = new Game(2); // Start with 2 players
            game.startGame();
            gameController.setGame(game);

            // Set up local game initialization
            gameController.initializeGame();

            // Configure the stage
            stage.setTitle("Azul - Local Game");
            stage.setScene(scene);
            stage.setOnCloseRequest(event -> {
                // Clean up resources when closing
                gameController.cleanup();
            });

            // Show the stage
            stage.show();

            LOGGER.info("Local game started successfully");
        } catch (Exception e) {
            LOGGER.severe("Error starting local game: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}