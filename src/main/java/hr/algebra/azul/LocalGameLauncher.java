package hr.algebra.azul;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import hr.algebra.azul.controller.GameController;

public class LocalGameLauncher extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // Load the game view
        FXMLLoader loader = new FXMLLoader(getClass().getResource("game-view.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 800);

        // Get and initialize the controller
        GameController gameController = loader.getController();
        gameController.setStage(stage);

        // Initialize local game
        gameController.initializeGame();

        // Set up the stage
        stage.setTitle("Azul - Local Game");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}