package com.vectura.client.controller;

import com.vectura.client.service.SftpService;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import net.synedra.validatorfx.Validator;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.io.IOException;

public class LoginController {

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField userField;
    @FXML private PasswordField passField;
    @FXML private Button connectButton;
    @FXML private Label statusLabel;
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;

    private final SftpService sftpService = new SftpService();
    private final Validator validator = new Validator();

    private final BooleanProperty isConnecting = new SimpleBooleanProperty(false);

    @FXML
    public void initialize() {
        setuValidation();
    }

    private void setuValidation() {
        // Validar host
        validator.createCheck()
                .dependsOn("host", hostField.textProperty())
                .withMethod(c -> {
                    String text = c.get("host");
                    if (text == null || text.trim().isEmpty()) {
                        c.error("La IP es requerida");
                    }
                })
                .decorates(hostField)
                .immediate();

        // Validar puerto
        validator.createCheck()
                .dependsOn("port", portField.textProperty())
                .withMethod(c -> {
                    String text = c.get("port");
                    if (text == null || !text.matches("\\d+")) {
                        c.error("Debe ser un numero");
                        return;
                    }

                    int port = Integer.parseInt(text);
                    if (port < 1 || port > 65535) {
                        c.error("Puerto invalido (1-65535");
                    }
                })
                .decorates(portField)
                .immediate();

        // Validar usuario
        validator.createCheck()
                .dependsOn("user", userField.textProperty())
                .withMethod(c -> {
                    if (c.get("user") == null || c.get("user").toString().trim().isEmpty()) {
                        c.error("Usuario requerido");
                    }
                })
                .decorates(userField)
                .immediate();

        // Validar contraseña
        validator.createCheck()
                .dependsOn("pass", passField.textProperty())
                .withMethod(c -> {
                    if (c.get("pass") == null || c.get("pass").toString().trim().isEmpty()) {
                        c.error("Contraseña requerida");
                    }
                })
                .decorates(passField)
                .immediate();

        // Conectar boton
        connectButton.disableProperty().bind(validator.containsErrorsProperty().or(isConnecting));

        validator.validate();
    }

    @FXML
    public void handleLogin() {
        // Bloqueo de boton para evitar doble clic
        isConnecting.set(true);
        statusLabel.setText("Conectando...");
        statusLabel.setStyle("-fx-text-fill: blue;");

        String host = hostField.getText();
        String user = userField.getText();
        String pass = passField.getText();

        String portText = portField.getText();
        int port;

        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            statusLabel.setText("Error: El puerto debe ser un número");
            statusLabel.setStyle("-fx-text-fill: red;");
            isConnecting.set(false);
            return;
        }

        // Port limpio
        final int finalPort = port;
        // Intentar conexion con hilo separado
        new Thread(() -> {
           try {
               sftpService.connect(host, finalPort, user, pass);

               // Si se llega aqui, hay conexion.
               javafx.application.Platform.runLater(() -> {
                   try {
                       // Cargar FXML
                       FXMLLoader loader = new FXMLLoader(
                               getClass().getResource("/view/dashboard.fxml")
                       );

                       Parent root = loader.load();

                       // Obtener controlador y pasar conexion
                       DashboardController dashboardController = loader.getController();
                       dashboardController.initData(sftpService);

                       // Cambiar escena
                       Stage stage = (Stage) connectButton.getScene().getWindow();
                       stage.setScene(new Scene(root));
                       stage.setTitle("Explorador de Archivos");
                       stage.centerOnScreen();
                   } catch (Exception e) {
                       e.printStackTrace();
                       statusLabel.setText("Error cargando dashboard: " + e.getMessage());
                   }
               });
           } catch (Exception e) {
               // Si falla
               javafx.application.Platform.runLater(() -> {
                   statusLabel.setText("Error: " + e.getMessage());
                   statusLabel.setStyle("-fx-text-fill: red;");
                   isConnecting.set(false);
               });
           }
        }).start();
    }
}
