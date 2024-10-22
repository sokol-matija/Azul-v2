module hr.algebra.azul {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;

    opens hr.algebra.azul to javafx.fxml;
    opens hr.algebra.azul.controller to javafx.fxml;
    exports hr.algebra.azul;
    exports hr.algebra.azul.controller;
    exports hr.algebra.azul.model;
    exports hr.algebra.azul.network;
    exports hr.algebra.azul.network.lobby;
    exports hr.algebra.azul.network.server;
    exports hr.algebra.azul.view;
}