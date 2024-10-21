module hr.algebra.azul {
    requires javafx.controls;
    requires javafx.fxml;


    opens hr.algebra.azul to javafx.fxml;
    exports hr.algebra.azul;
    exports hr.algebra.azul.controller;
    opens hr.algebra.azul.controller to javafx.fxml;
}