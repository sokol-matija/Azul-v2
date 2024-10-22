package hr.algebra.azul;

import javafx.application.Application;
import javafx.stage.Stage;

public class LocalGameClient extends Application {
    private static int clientIndex = 0;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Create and start the AzulApplication
        AzulApplication client = new AzulApplication();

        // Set window title and position
        primaryStage.setTitle("Azul Client " + clientIndex);
        primaryStage.setX(clientIndex + 820); // Width + 20px margin
        primaryStage.setY(50);

        client.start(primaryStage);
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            clientIndex = Integer.parseInt(args[0]);
        }
        launch(args);
    }
}