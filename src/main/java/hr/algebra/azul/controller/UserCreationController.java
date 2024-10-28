package hr.algebra.azul.controller;

import hr.algebra.azul.AzulApplication;
import hr.algebra.azul.model.User;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class UserCreationController {
    @FXML private TextField usernameField;
    @FXML private Button createButton;
    @FXML private Label errorLabel;

    private AzulApplication mainApp;
    private Stage stage;

    @FXML
    public void initialize() {
        createButton.setDisable(true);

        usernameField.textProperty().addListener((obs, oldValue, newValue) -> {
            createButton.setDisable(newValue.trim().isEmpty());
        });

        createButton.setOnAction(event -> {
            try {
                createUser();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void createUser() throws IOException {
        String username = usernameField.getText().trim();

        if (username.isEmpty()) {
            showError("Username cannot be empty");
            return;
        }

        if (username.length() < 3) {
            showError("Username must be at least 3 characters long");
            return;
        }

        User newUser = new User(username);
        mainApp.setCurrentUser(newUser);
        mainApp.showLobbyScreen();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    public void setMainApp(AzulApplication mainApp) {
        this.mainApp = mainApp;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }
}