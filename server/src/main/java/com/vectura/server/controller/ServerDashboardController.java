package com.vectura.server.controller;

import com.vectura.server.auth.CustomDatabaseAuthenticator;
import com.vectura.server.db.DatabaseManager;
import com.vectura.server.fs.VecturaFileSystemFactory;
import com.vectura.server.sftp.VecturaSftpSubsystemFactory;
import com.vectura.server.util.FirewallManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static com.vectura.server.util.UILogManager.*;

public class ServerDashboardController {

    // UI Elements
    @FXML private FontIcon statusIcon;
    @FXML private Label statusLabel;
    @FXML private Label ipLabel;
    @FXML private Button btnToggleServer;
    @FXML private TextField txtPort;
    @FXML private TextArea logArea;

    // Logic
    private SshServer sshd;
    private DatabaseManager dbManager;
    private boolean isRunning = false;

    @FXML
    public void initialize() {
        setup(logArea);

        log("[SYSTEM] Dashboard initialized.");

        // Conectar BD al inicio
        try {
            dbManager = new DatabaseManager();
            dbManager.init();
            log("[DB] Database connected.");
        } catch (Exception e) {
            log("[DB] Database error: " + e.getMessage());
        }

        // DETECTAR IP
        ipLabel.setText("IP LAN: " + getRealNetworkIP());
    }

    @FXML
    public void handleOpenUserManagement() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user_management.fxml"));
            Parent root = loader.load();

            // Pasar la instancia de BD
            UserManagementController userController = loader.getController();
            userController.setDatabaseManager(this.dbManager);

            Stage stage = new Stage();
            stage.setTitle("Gestión de Usuarios - Vectura");
            stage.setScene(new Scene(root));

            // Bloquea la ventana padre hasta que esta se cierre
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.initOwner(txtPort.getScene().getWindow());

            stage.showAndWait(); // Esperar a que se cierre

        } catch (IOException e) {
            log("[UI] Can't open user manager: " + e.getMessage());
        }
    }

    @FXML
    public void handleExit() {
        if (isRunning) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmar Salida");
            alert.setHeaderText("El servidor está corriendo.");
            alert.setContentText("¿Estás seguro de que deseas detener el servidor y salir?");

            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return; // Cancelar salida
            }
        }

        shutdown();
        Platform.exit();
        System.exit(0);
    }

    @FXML
    public void handleToggleServer() {
        if (isRunning) stopServer();
        else startServer();
    }

    private void startServer() {
        try {
            int port = Integer.parseInt(txtPort.getText());
            sshd = SshServer.setUpDefaultServer();
            sshd.getProperties().put("idle-timeout", java.time.Duration.ZERO);
            sshd.setPort(port);
            File hostKeyFile = new File("hostkey.ser");
            SimpleGeneratorHostKeyProvider provider = new SimpleGeneratorHostKeyProvider(hostKeyFile.toPath());
            provider.setAlgorithm("RSA");

            sshd.setKeyPairProvider(provider);

            // Factory y Listener
            VecturaSftpSubsystemFactory sftpFactory = new VecturaSftpSubsystemFactory();
            sshd.setSubsystemFactories(Collections.singletonList(sftpFactory));

            // Session Listener
            sshd.addSessionListener(new SessionListener() {
                @Override
                public void sessionCreated(Session session) {}
                @Override
                public void sessionEvent(Session session, Event event) {
                    if (event == Event.Authenticated) {
                        log("[LOGIN] [" + session.getUsername() + "] Successful access (" + session.getRemoteAddress() + ")");
                    }
                }
                @Override
                public void sessionClosed(Session session) {
                    if(session.getUsername() != null) {
                        log("[LOGOUT] [" + session.getUsername() + "] Disconnected");
                    }
                }
            });

            sshd.setPasswordAuthenticator(new CustomDatabaseAuthenticator(dbManager));
            sshd.setFileSystemFactory(new VecturaFileSystemFactory());
            sshd.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);

            sshd.start();
            isRunning = true;
            updateStatusUI(true, port);
            log("[SERVER] Server STARTED on port " + port);

        } catch (Exception e) {
            log("[SERVER] Failed to start server: " + e.getMessage());
        }
    }

    private void stopServer() {
        try {
            if (sshd != null) sshd.stop();
            isRunning = false;
            updateStatusUI(false, 0);
            log("[SERVER] Server STOPPED");
        } catch (Exception e) {
            log(e.getMessage());
        }
    }

    private void updateStatusUI(boolean running, int port) {
        if (running) {
            statusLabel.setText("ONLINE (Puerto " + port + ")");
            statusLabel.setStyle("-fx-text-fill: #2ecc71;");
            statusIcon.setIconColor(javafx.scene.paint.Color.web("#2ecc71"));
            btnToggleServer.setText("DETENER");
            btnToggleServer.getStyleClass().add("button-danger");
            txtPort.setDisable(true);
        } else {
            statusLabel.setText("OFFLINE");
            statusLabel.setStyle("-fx-text-fill: #7f8c8d;");
            statusIcon.setIconColor(javafx.scene.paint.Color.web("#e74c3c"));
            btnToggleServer.setText("INICIAR");
            btnToggleServer.getStyleClass().removeAll("button-danger");
            txtPort.setDisable(false);
        }
    }


    public void shutdown() { stopServer(); }

    @FXML
    public void handleOpenRootFolder() {
        try {
            // Buscamos la carpeta raiz.
            File rootDir = new File("./sftp_root");
            if (!rootDir.exists()) {
                if (rootDir.mkdirs()) {
                    log("Root folder created");
                }
            }

            // Abrir con el explorador nativo del sistema operativo
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(rootDir);
                log("[SYSTEM] Root folder opened in explorer.");
            } else {
                log("[SYSTEM] Desktop functionality is not supported on this system.");
            }
        } catch (IOException e) {
            log("[SYSTEM] Could not open folder: " + e.getMessage());
        }
    }

    @FXML
    public void handleSaveLogs() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Guardar Logs del Servidor");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Archivos de Texto", "*.txt"));

        // Nombre por defecto con fecha
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        fileChooser.setInitialFileName("vectura_logs_" + datePart + ".txt");

        File file = fileChooser.showSaveDialog(logArea.getScene().getWindow());
        if (file != null) {
            try {
                java.nio.file.Files.writeString(file.toPath(), logArea.getText());
                log("[SYSTEM] Logs exported successfully to: " + file.getName());
            } catch (IOException e) {
                log("[SYSTEM] Error saving logs: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleClearLogs() {
        logArea.clear();
        log("[SYSTEM] Terminal cleared by user.");
    }

    @FXML
    public void handleBackupDatabase() {
        // Configurar para guardar ZIP
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Generar Respaldo de Base de Datos");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Archivo ZIP", "*.zip"));

        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        fileChooser.setInitialFileName("vectura_backup_" + datePart + ".zip");

        File destFile = fileChooser.showSaveDialog(logArea.getScene().getWindow());

        if (destFile != null) {
            // Usar conexion de BD
            try (Connection conn = dbManager.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {

                log("[SYSTEM] Starting hot backup...");

                // Backup H2
                stmt.execute("BACKUP TO '" + destFile.getAbsolutePath() + "'");

                log("[SYSTEM] Backup saved successfully: " + destFile.getName());

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Respaldo Exitoso");
                alert.setHeaderText(null);
                alert.setContentText("La base de datos se ha exportado correctamente a:\n" + destFile.getAbsolutePath());
                alert.showAndWait();

            } catch (Exception e) {
                log("[SYSTEM] Critical error generating backup: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Acerca de Vectura Server");
        alert.setHeaderText("Vectura SFTP Server v1.0");
        alert.setContentText("""
                Sistema de Gestión de Archivos Seguro.
                
                Tecnologías: JavaFX, Apache SSHD, H2 Database.
                Desarrollado para entorno de producción.
                2025 © Ibhar Gomez""");
        alert.showAndWait();
    }

    private String getRealNetworkIP() {
        // Preguntar al OS la ruta de salida
        try (final DatagramSocket socket = new DatagramSocket()) {

            // Ping a google para testeo
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {

            // Iterar filtrando nombres de adaptadores virtuales comunes
            try {
                java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    java.net.NetworkInterface iface = interfaces.nextElement();
                    String name = iface.getDisplayName().toLowerCase();

                    // Filtros estrictos anti-virtuales
                    if (iface.isLoopback() || !iface.isUp() ||
                            name.contains("virtual") || name.contains("vmware") ||
                            name.contains("virtualbox") || name.contains("host-only") ||
                            name.contains("hyper-v") || name.contains("pseudo")) {
                        continue;
                    }

                    java.util.Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof java.net.Inet4Address && addr.isSiteLocalAddress()) {
                            return addr.getHostAddress();
                        }
                    }
                }
            } catch (Exception ex) {
                return "127.0.0.1";
            }
        }
        return "127.0.0.1";
    }

    @FXML
    public void handleConfigureFirewall() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Configuración de Firewall");
        confirm.setHeaderText("Configuración Automática");
        confirm.setContentText("""
                Se configurarán las reglas de red para permitir conexiones.
                Se solicitarán permisos de Administrador de Windows.
                
                ¿Desea continuar?""");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                // Obtener puerto actual de la caja de texto
                int port = Integer.parseInt(txtPort.getText());

                // Llamar al Manager
                boolean launched = FirewallManager.configureFirewall(port);

                if (launched) {
                    log("[SYSTEM] Firewall request sent to system.");
                }
            } catch (NumberFormatException e) {
                log("[SYSTEM] The port must be a valid number.");
            }
        }
    }
}