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
        log("[SYSTEM] Dashboard inicializado.");

        // Conectar BD al inicio
        try {
            dbManager = new DatabaseManager();
            dbManager.init();
            log("[DB] Base de datos conectada.");
        } catch (Exception e) {
            logError("Error CRÍTICO BD: " + e.getMessage());
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
            logError("No se pudo abrir la ventana de usuarios: " + e.getMessage());
            e.printStackTrace();
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
            sshd.setPort(port);
            File hostKeyFile = new File("hostkey.ser");
            SimpleGeneratorHostKeyProvider provider = new SimpleGeneratorHostKeyProvider(hostKeyFile.toPath());
            provider.setAlgorithm("RSA");

            sshd.setKeyPairProvider(provider);

            // Factory y Listener
            VecturaSftpSubsystemFactory sftpFactory = new VecturaSftpSubsystemFactory(this::log);
            sshd.setSubsystemFactories(Collections.singletonList(sftpFactory));

            // Session Listener
            sshd.addSessionListener(new SessionListener() {
                @Override
                public void sessionCreated(Session session) {}
                @Override
                public void sessionEvent(Session session, Event event) {
                    if (event == Event.Authenticated) {
                        log("[" + session.getUsername() + "] [LOGIN] Acceso exitoso (" + session.getRemoteAddress() + ")");
                    }
                }
                @Override
                public void sessionClosed(Session session) {
                    if(session.getUsername() != null) {
                        log("[" + session.getUsername() + "] [LOGOUT] Desconectado");
                    }
                }
            });

            sshd.setPasswordAuthenticator(new CustomDatabaseAuthenticator(dbManager));
            sshd.setFileSystemFactory(new VecturaFileSystemFactory());
            sshd.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);

            sshd.start();
            isRunning = true;
            updateStatusUI(true, port);
            log("[SERVER] Servidor INICIADO en puerto " + port);

        } catch (Exception e) {
            logError("Fallo al iniciar: " + e.getMessage());
        }
    }

    private void stopServer() {
        try {
            if (sshd != null) sshd.stop();
            isRunning = false;
            updateStatusUI(false, 0);
            log("[SERVER] Servidor DETENIDO");
        } catch (Exception e) {
            logError(e.getMessage());
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

    private void log(String msg) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() -> logArea.appendText(time + " " + msg + "\n"));
    }
    private void logError(String msg) { log("[ERROR] " + msg); }
    public void shutdown() { stopServer(); }

    @FXML
    public void handleOpenRootFolder() {
        try {
            // Buscamos la carpeta raiz.
            File rootDir = new File("./sftp_root");
            if (!rootDir.exists()) {
                rootDir.mkdirs();
            }

            // Abrir con el explorador nativo del sistema operativo
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(rootDir);
                log("[SYSTEM] Carpeta raíz abierta en explorador.");
            } else {
                logError("La función de escritorio no está soportada en este sistema.");
            }
        } catch (IOException e) {
            logError("No se pudo abrir la carpeta: " + e.getMessage());
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
                log("[SYSTEM] Logs exportados correctamente a: " + file.getName());
            } catch (IOException e) {
                logError("Error al guardar logs: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleClearLogs() {
        logArea.clear();
        log("[SYSTEM] Terminal limpiada por el usuario.");
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

                log("[SYSTEM] Iniciando respaldo en caliente...");

                // Backup H2
                stmt.execute("BACKUP TO '" + destFile.getAbsolutePath() + "'");

                log("[SYSTEM] Respaldo guardado exitosamente: " + destFile.getName());

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Respaldo Exitoso");
                alert.setHeaderText(null);
                alert.setContentText("La base de datos se ha exportado correctamente a:\n" + destFile.getAbsolutePath());
                alert.showAndWait();

            } catch (Exception e) {
                logError("Error crítico al generar respaldo: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Acerca de Vectura Server");
        alert.setHeaderText("Vectura SFTP Server v1.0");
        alert.setContentText("Sistema de Gestión de Archivos Seguro.\n\n" +
                "Tecnologías: JavaFX, Apache SSHD, H2 Database.\n" +
                "Desarrollado para entorno de producción.\n" +
                "2025 © Ibhar Gomez");
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
        confirm.setContentText("Se configurarán las reglas de red para permitir conexiones.\n" +
                "Se solicitarán permisos de Administrador de Windows.\n\n" +
                "¿Desea continuar?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                // Obtener puerto actual de la caja de texto
                int port = Integer.parseInt(txtPort.getText());

                // Llamar al Manager
                boolean launched = FirewallManager.configureFirewall(port);

                if (launched) {
                    log("[SYSTEM] Solicitud de firewall enviada al sistema.");
                }
            } catch (NumberFormatException e) {
                logError("El puerto debe ser un número válido.");
            }
        }
    }
}