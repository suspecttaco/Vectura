package com.vectura.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class VecturaApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Carga directa del Dashboard (La nueva ventana principal)
        FXMLLoader fxmlLoader = new FXMLLoader(VecturaApp.class.getResource("/view/dashboard.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1000, 700);

        stage.setTitle("Vectura SFTP Client");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}