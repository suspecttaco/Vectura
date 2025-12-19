package com.vectura.client.controller;

import com.vectura.client.model.LocalVecturaFile;
import com.vectura.client.model.VecturaFile;
import com.vectura.client.service.SftpService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class DashboardController {

    // Local
    @FXML private TextField localPathField;
    @FXML private TableView<VecturaFile> localTable;
    @FXML private TableColumn<VecturaFile, String> localNameCol;
    @FXML private TableColumn<VecturaFile, String> localSizeCol;
    @FXML private TableColumn<VecturaFile, String> localDateCol;

    // Remoto
    @FXML private TextField remotePathField;
    @FXML private TableView<VecturaFile> remoteTable;
    @FXML private TableColumn<VecturaFile, String> remoteNameCol;
    @FXML private TableColumn<VecturaFile, String> remoteSizeCol;
    @FXML private TableColumn<VecturaFile, String> remoteDateCol;

    // Cola de transferencias
    @FXML private TableView<?> queueTable;
    @FXML private TableColumn<?, ?> queueNameCol;
    @FXML private TableColumn<?, ?> queueTypeCol; // Subida o Bajada
    @FXML private TableColumn<?, ?> queueStatusCol; // Pendiente, En Progreso...
    @FXML private TableColumn<?, ?> queueProgressCol;

    // General
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;

    private SftpService sftpService;
    private String currentLocalPath = System.getProperty("user.home");
    private String currentRemotePath = "/";

    public void initData(SftpService service) {
        this.sftpService = service;
        statusLabel.setText("Conectado a Vectura Server v1.0");

        setupTables();
        setupContextMenus(); // <--- NUEVO: Clic derecho
        refreshBoth();
    }

    private void setupTables() {
        setupColumns(localNameCol, localSizeCol, localDateCol);
        setupColumns(remoteNameCol, remoteSizeCol, remoteDateCol);

        // Hacer que al dar Enter en la barra de dirección, navegue
        localPathField.setOnAction(e -> {
            currentLocalPath = localPathField.getText();
            loadLocalFiles();
        });

        // Doble clic para entrar en carpetas
        setupDoubleClickHandler(localTable, true);
        setupDoubleClickHandler(remoteTable, false);
    }

    // Configura el doble clic para navegar
    private void setupDoubleClickHandler(TableView<VecturaFile> table, boolean isLocal) {
        table.setRowFactory(tv -> {
            TableRow<VecturaFile> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    VecturaFile rowData = row.getItem();
                    if (rowData.isDirectory()) {
                        if (isLocal) {
                            currentLocalPath = rowData.getPath();
                            loadLocalFiles();
                        } else {
                            // Concatenación simple por ahora, luego usaremos Path
                            if (currentRemotePath.endsWith("/")) currentRemotePath += rowData.getName();
                            else currentRemotePath += "/" + rowData.getName();
                            loadRemoteFiles();
                        }
                    }
                }
            });
            return row ;
        });
    }

    private void setupContextMenus() {
        // Creamos un menú para clic derecho
        ContextMenu contextMenu = new ContextMenu();
        MenuItem uploadItem = new MenuItem("Subir / Bajar");
        MenuItem deleteItem = new MenuItem("Eliminar");
        MenuItem renameItem = new MenuItem("Renombrar");

        uploadItem.setOnAction(e -> handleTransferAction());
        deleteItem.setOnAction(e -> handleDelete());
        renameItem.setOnAction(e -> System.out.println("TODO: Renombrar"));

        contextMenu.getItems().addAll(uploadItem, new SeparatorMenuItem(), renameItem, deleteItem);

        // Asignamos el mismo menu a ambas tablas
        localTable.setContextMenu(contextMenu);
        remoteTable.setContextMenu(contextMenu);
    }

    // Metodo auxiliar para decidir si subimos o bajamos según qué tabla tenga foco
    private void handleTransferAction() {
        if (localTable.isFocused()) handleUpload();
        else if (remoteTable.isFocused()) handleDownload();
    }

    private void setupColumns(TableColumn<VecturaFile, String> nameCol,
                              TableColumn<VecturaFile, String> sizeCol,
                              TableColumn<VecturaFile, String> dateCol) {

        nameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VecturaFile file = getTableView().getItems().get(getIndex());
                    FontIcon icon;
                    if (file.isDirectory()) {
                        icon = new FontIcon(FontAwesomeSolid.FOLDER);
                        icon.setIconColor(javafx.scene.paint.Color.rgb(255, 179, 0));
                    } else {
                        icon = new FontIcon(FontAwesomeSolid.FILE_ALT);
                        icon.setIconColor(javafx.scene.paint.Color.rgb(100, 100, 100));
                    }
                    setText("  " + item);
                    setGraphic(icon);
                }
            }
        });

        sizeCol.setCellValueFactory(cell -> {
            if (cell.getValue().isDirectory()) return new SimpleStringProperty("");
            return new SimpleStringProperty(humanReadableByteCountSI(cell.getValue().getSize()));
        });
        sizeCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        sizeCol.getStyleClass().add("numeric-column");

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        dateCol.setCellValueFactory(cell ->
                new SimpleStringProperty(sdf.format(new Date(cell.getValue().getLastModifiedTime())))
        );
    }

    @FXML
    public void refreshBoth() {
        loadLocalFiles();
        loadRemoteFiles();
    }

    private void loadLocalFiles() {
        try {
            localPathField.setText(currentLocalPath); // Actualizar barra
            File folder = new File(currentLocalPath);
            File[] files = folder.listFiles();

            if (files != null) {
                List<VecturaFile> vFiles = java.util.Arrays.stream(files)
                        .map(f -> (VecturaFile) new LocalVecturaFile(f))
                        .sorted((a, b) -> {
                            if (a.isDirectory() && !b.isDirectory()) return -1;
                            if (!a.isDirectory() && b.isDirectory()) return 1;
                            return a.getName().compareToIgnoreCase(b.getName());
                        })
                        .toList();
                localTable.setItems(FXCollections.observableArrayList(vFiles));
            }
        } catch (Exception e) {
            statusLabel.setText("Error local: " + e.getMessage());
        }
    }

    private void loadRemoteFiles() {
        try {
            remotePathField.setText(currentRemotePath); // Actualizar barra
            List<VecturaFile> files = sftpService.listDirectory(currentRemotePath);

            files.sort((a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            remoteTable.setItems(FXCollections.observableArrayList(files));
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error remoto: " + e.getMessage());
        }
    }

    // --- BOTONES ---

    @FXML public void handleUpload() {
        VecturaFile selected = localTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            System.out.println("TODO: Subir " + selected.getName());
            // Simulamos progreso
            progressBar.setVisible(true);
            progressBar.setProgress(-1); // Indeterminado
        } else {
            statusLabel.setText("Selecciona un archivo local para subir.");
        }
    }

    @FXML public void handleDownload() {
        VecturaFile selected = remoteTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            System.out.println("TODO: Bajar " + selected.getName());
        } else {
            statusLabel.setText("Selecciona un archivo remoto para bajar.");
        }
    }

    @FXML public void handleDelete() {
        System.out.println("TODO: Borrar archivo seleccionado");
    }

    @FXML public void handleMkdir() {
        System.out.println("TODO: Crear carpeta");
    }

    @FXML public void handleDisconnect() {
        sftpService.disconnect();
        // Cerrar ventana actual
        ((Stage) statusLabel.getScene().getWindow()).close();
        System.out.println("Desconectado.");
    }

    // --- NAVEGACIÓN ---

    @FXML public void goLocalUp() {
        File parent = new File(currentLocalPath).getParentFile();
        if (parent != null) {
            currentLocalPath = parent.getAbsolutePath();
            loadLocalFiles();
        }
    }

    @FXML public void goLocalHome() {
        currentLocalPath = System.getProperty("user.home");
        loadLocalFiles();
    }

    @FXML public void goRemoteUp() {
        if (!currentRemotePath.equals("/")) {
            int lastSlash = currentRemotePath.lastIndexOf('/');
            if (lastSlash > 0) currentRemotePath = currentRemotePath.substring(0, lastSlash);
            else currentRemotePath = "/";
            loadRemoteFiles();
        }
    }

    @FXML public void goRemoteHome() {
        currentRemotePath = "/";
        loadRemoteFiles();
    }

    private static String humanReadableByteCountSI(long bytes) {
        if (-1000 < bytes && bytes < 1000) return bytes + " B";
        java.text.CharacterIterator ci = new java.text.StringCharacterIterator("kMGTPE");
        while (bytes <= -999950 || bytes >= 999950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }
}