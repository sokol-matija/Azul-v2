package hr.algebra.azul;

import hr.algebra.azul.controller.GameController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class AzulApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(AzulApplication.class.getResource("game-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        stage.setTitle("Azul Game");
        stage.setScene(scene);

        GameController controller = fxmlLoader.getController();
        controller.initializeGame();

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}