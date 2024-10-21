module hr.algebra.azul {
    requires javafx.controls;
    requires javafx.fxml;


    opens hr.algebra.azul to javafx.fxml;
    exports hr.algebra.azul;
}