package com.vectura.server.controller;

import com.vectura.server.db.DatabaseManager;
import com.vectura.server.db.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserManagementController {

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> colUser;
    @FXML private TableColumn<User, String> colHome;
    @FXML private TableColumn<User, Boolean> colActive;

    @FXML private TextField txtNewUser;
    @FXML private PasswordField txtNewPass;
    @FXML private TextField txtHomeDir;
    @FXML private CheckBox chkActive;

    @FXML private Button btnAdd;
    @FXML private Button btnUpdate;
    @FXML private Button btnDelete;

    private DatabaseManager dbManager;
    private final ObservableList<User> usersList = FXCollections.observableArrayList();

    public void setDatabaseManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        loadUsers();
    }

    @FXML
    public void initialize() {
        // Configuracion de columnas
        colUser.setCellValueFactory(cell -> cell.getValue().usernameProperty());
        colHome.setCellValueFactory(cell -> cell.getValue().homeDirProperty());
        colActive.setCellValueFactory(cell -> cell.getValue().activeProperty());
        usersTable.setItems(usersList);

        // Listener de cambio de seleccion
        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // MODO EDICION
                txtNewUser.setText(newVal.getUsername());
                txtNewUser.setDisable(true); // PK Bloqueada
                txtHomeDir.setText(newVal.getHomeDir());
                chkActive.setSelected(newVal.isActive());
                txtNewPass.clear();

                btnAdd.setDisable(true);
                btnUpdate.setDisable(false);
                btnDelete.setDisable(false);
            } else {
                resetForm();
            }
        });

        usersTable.setOnMouseClicked(e -> {
            // Verificar si el objetivo del clic es un nodo valido
            if (e.getTarget() instanceof javafx.scene.Node) {
                javafx.scene.Node node = (javafx.scene.Node) e.getTarget();

                javafx.scene.control.TableRow<?> row = null;
                while (node != null && node != usersTable) {
                    if (node instanceof javafx.scene.control.TableRow) {
                        row = (javafx.scene.control.TableRow<?>) node;
                        break;
                    }
                    node = node.getParent();
                }

                // Si no es fila -> Limpiar
                if (row == null || row.isEmpty()) {
                    handleClearSelection();
                }
            }
        });

        resetForm();
    }

    @FXML
    public void handleClearSelection() {
        resetForm();
    }

    private void resetForm() {
        usersTable.getSelectionModel().clearSelection();

        txtNewUser.setDisable(false); // Habilitado solo para crear nuevos
        txtNewUser.clear();
        txtNewPass.clear();
        txtHomeDir.clear();
        chkActive.setSelected(true);

        btnAdd.setDisable(false);
        btnUpdate.setDisable(true);
        btnDelete.setDisable(true);
    }

    private void loadUsers() {
        if (dbManager == null) return;
        usersList.clear();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                usersList.add(new User(
                        rs.getString("username"),
                        rs.getString("home_dir"),
                        rs.getBoolean("is_active")
                ));
            }
        } catch (Exception e) {
            showAlert("Error", "Error cargando usuarios: " + e.getMessage());
        }
    }

    @FXML
    public void handleAddUser() {
        String user = txtNewUser.getText();
        String pass = txtNewPass.getText();
        String home = txtHomeDir.getText();
        boolean active = chkActive.isSelected();

        if (user.isEmpty() || pass.isEmpty()) {
            showAlert("Validación", "Usuario y contraseña son obligatorios.");
            return;
        }
        if (home.isEmpty()) home = "./sftp_root/" + user;

        try (Connection conn = dbManager.getConnection()) {
            String sql = "INSERT INTO users (username, password_hash, home_dir, is_active) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, user);
                stmt.setString(2, BCrypt.hashpw(pass, BCrypt.gensalt()));
                stmt.setString(3, home);
                stmt.setBoolean(4, active);
                stmt.executeUpdate();
                loadUsers();
                resetForm();
            }
        } catch (Exception e) {
            showAlert("Error", "No se pudo crear (¿Usuario duplicado?): " + e.getMessage());
        }
    }

    @FXML
    public void handleUpdateUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String newPass = txtNewPass.getText();
        String newHome = txtHomeDir.getText();
        boolean active = chkActive.isSelected();

        try (Connection conn = dbManager.getConnection()) {
            boolean updatePass = !newPass.isEmpty();

            String sql = updatePass
                    ? "UPDATE users SET password_hash = ?, home_dir = ?, is_active = ? WHERE username = ?"
                    : "UPDATE users SET home_dir = ?, is_active = ? WHERE username = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int idx = 1;
                if (updatePass) stmt.setString(idx++, BCrypt.hashpw(newPass, BCrypt.gensalt()));

                stmt.setString(idx++, newHome);
                stmt.setBoolean(idx++, active);
                stmt.setString(idx, selected.getUsername()); // WHERE username = OLD

                stmt.executeUpdate();
                loadUsers();
                resetForm();
            }
        } catch (Exception e) {
            showAlert("Error", "Error al actualizar: " + e.getMessage());
        }
    }

    @FXML
    public void handleDeleteUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "¿Borrar usuario " + selected.getUsername() + "?", ButtonType.YES, ButtonType.NO);
        if (alert.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
                stmt.setString(1, selected.getUsername());
                stmt.executeUpdate();
            }
            loadUsers();
            resetForm();
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML
    public void handleBrowseDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        File f = chooser.showDialog(txtNewUser.getScene().getWindow());
        if (f != null) txtHomeDir.setText(f.getAbsolutePath());
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.initOwner(txtNewUser.getScene().getWindow());
        alert.showAndWait();
    }
}