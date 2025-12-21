package com.vectura.client.controller;

import com.vectura.client.model.LocalVecturaFile;
import com.vectura.client.model.TransferTask;
import com.vectura.client.model.VecturaFile;
import com.vectura.client.service.SftpService;
import com.vectura.client.service.TransferManager;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;

import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.xfer.TransferListener;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.prefs.Preferences;

public class DashboardController {

    // Host
    @FXML
    private TextField hostField;
    @FXML
    private TextField userField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField portField;
    @FXML
    private Button btnConnect;

    // Local
    @FXML
    private TextField localPathField;
    @FXML
    private TableView<VecturaFile> localTable;
    @FXML
    private TableColumn<VecturaFile, String> localNameCol;
    @FXML
    private TableColumn<VecturaFile, String> localSizeCol;
    @FXML
    private TableColumn<VecturaFile, String> localDateCol;

    // Remoto
    @FXML
    private TextField remotePathField;
    @FXML
    private TableView<VecturaFile> remoteTable;
    @FXML
    private TableColumn<VecturaFile, String> remoteNameCol;
    @FXML
    private TableColumn<VecturaFile, String> remoteSizeCol;
    @FXML
    private TableColumn<VecturaFile, String> remoteDateCol;

    // Cola de transferencias
    @FXML
    private TableView<TransferTask> queueTable;
    @FXML
    private TableColumn<TransferTask, String> queueNameCol;
    @FXML
    private TableColumn<TransferTask, String> queueSizeCol;
    @FXML
    private TableColumn<TransferTask, String> queueTypeCol; // Subida o Bajada
    @FXML
    private TableColumn<TransferTask, String> queueStatusCol; // Pendiente, En Progreso...
    @FXML
    private TableColumn<TransferTask, Double> queueProgressCol;

    // General
    @FXML
    private Label statusLabel;
    @FXML
    private MenuItem menuConnect;

    // Botones
    // Local
    @FXML
    private Button btnLocalBrowse;
    @FXML
    private Button btnLocalUp;
    @FXML
    private Button btnLocalHome;

    // Remoto
    @FXML
    private Button btnRemoteUp;
    @FXML
    private Button btnRemoteHome;

    // Check de conexion
    private boolean isConnected = false;

    // Servicios SFTP
    private SftpService sftpService;
    private TransferManager transferManager;

    // Variables de Path
    private String currentLocalPath = "";
    private String localBaseFolder = null;

    private String currentRemotePath = "/";
    private String remoteBaseFolder = "/";

    // Archivos ocultos
    private boolean showHiddenFiles = false;

    // Preferencias
    private final Preferences preferences = Preferences.userNodeForPackage(DashboardController.class);

    private static final String PREF_HOST = "sftp_host";
    private static final String PREF_USER = "sftp_user";
    private static final String PREF_PORT = "sftp_port";

    @FXML
    public void initialize() {
        setupTables();
        // Deshabilitar tablas para que no se puedan tocar
        localTable.setDisable(true);
        remoteTable.setDisable(true);
        localTable.setPlaceholder(new Label("Conectate para ver archivos locales"));
        remoteTable.setPlaceholder(new Label("Conectate para ver archivos remotos"));

        setupQueueTable();
        setupContextMenus(); // Clic derecho
        updateConnectionState(false);

        // Recuperar datos
        loadSettings();
    }

    private void setupTables() {
        setupColumns(localNameCol, localSizeCol, localDateCol);
        setupColumns(remoteNameCol, remoteSizeCol, remoteDateCol);

        // Enter para ir a direccion
        localPathField.setOnAction(e -> {
            currentLocalPath = localPathField.getText();
            loadLocalFiles();
        });

        // Doble clic para entrar en carpetas
        setupDoubleClickHandler(localTable, true);
        setupDoubleClickHandler(remoteTable, false);

        localTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F2) {
                handleRename();
                e.consume();
            }
        });

        remoteTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F2) {
                handleRename();
                e.consume();
            }
        });
    }

    private void setupQueueTable() {
        // Vincular columnas
        queueNameCol.setCellValueFactory(cell -> cell.getValue().fileNameProperty());
        queueTypeCol.setCellValueFactory(cell -> cell.getValue().typeProperty());
        queueStatusCol.setCellValueFactory(cell -> cell.getValue().statusProperty());
        queueSizeCol.setCellValueFactory(cell -> cell.getValue().sizeProperty());
        queueSizeCol.getStyleClass().add("numeric-column");

        // Columna de progreso
        queueProgressCol.setCellValueFactory(cell -> cell.getValue().progressProperty().asObject());

        // Renderizar ProgressBar en celda
        queueProgressCol.setCellFactory(column -> new TableCell<>() {
            private final ProgressBar progressBar = new ProgressBar();
            private final Label progressLabel = new Label();
            private final StackPane stackPane = new StackPane();

            {
                // Configuracion inicial
                progressBar.setMaxWidth(Double.MAX_VALUE);
                progressBar.setPrefHeight(20);

                // Estilo del texto
                progressLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #333; -fx-font-size: 11px;");

                // Apilamos: Barra abajo, Texto arriba
                stackPane.getChildren().addAll(progressBar, progressLabel);
            }

            @Override
            protected void updateItem(Double progress, boolean empty) {
                super.updateItem(progress, empty);

                if (empty || progress == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    progressBar.setProgress(progress);

                    if (progress < 0) {
                        progressLabel.setText("Calculando...");
                    } else {
                        progressLabel.setText(String.format("%.0f%%", progress * 100));
                    }

                    if (progress >= 1.0) {
                        progressBar.setStyle("-fx-accent: #28a745;"); // Verde exito
                        progressLabel.setText("Completado");
                        progressLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-effect: dropshadow(one-pass-box, black, 2, 0, 0, 0);");
                    } else {
                        progressBar.setStyle("-fx-accent: #007bff;"); // Azul normal
                        progressLabel.setStyle("-fx-text-fill: #333; -fx-font-weight: bold;");
                    }

                    setGraphic(stackPane);
                }
            }
        });
    }

    // Doble clic (Navegar o Transferir)
    private void setupDoubleClickHandler(TableView<VecturaFile> table, boolean isLocal) {

        table.setRowFactory(tv -> {
            TableRow<VecturaFile> row = new TableRow<>();

            row.setOnMouseClicked(event -> {
                // Solo en fila con datos
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    VecturaFile rowData = row.getItem();

                    if (rowData.isDirectory()) {
                        // Carpeta -> Navegar
                        if (isLocal) {
                            currentLocalPath = rowData.getPath();
                            loadLocalFiles();
                        } else {
                            if (currentRemotePath.endsWith("/")) currentRemotePath += rowData.getName();
                            else currentRemotePath += "/" + rowData.getName();
                            loadRemoteFiles();
                        }
                    } else {
                        // Archivo -> Transferir
                        if (isLocal) {
                            // Local = SUBIR
                            handleUpload();
                        } else {
                            // Remoto = BAJAR
                            handleDownload();
                        }
                    }
                }
            });
            return row;
        });
    }

    private void setupContextMenus() {
        // Local
        ContextMenu localMenu = new ContextMenu();

        // Subir
        MenuItem uploadItem = new MenuItem("Subir al Servidor");
        uploadItem.setOnAction(e -> handleUpload());
        uploadItem.setGraphic(new FontIcon("fas-cloud-upload-alt"));

        // Actualizar
        MenuItem refreshItemL = new MenuItem("Actualizar");
        refreshItemL.setOnAction(e -> loadLocalFiles());

        // Nueva carpeta local
        MenuItem mkdirItemL = new MenuItem("Nueva Carpeta");
        mkdirItemL.setOnAction(e -> handleMkdir(true)); // true = local

        // Renombrar local
        MenuItem renameItemL = new MenuItem("Renombrar");
        renameItemL.setOnAction(e -> renameLocal());

        // Eliminar
        MenuItem deleteItemL = new MenuItem("Eliminar");
        deleteItemL.setOnAction(e -> handleDelete(true)); // true = local
        deleteItemL.setStyle("-fx-text-fill: red;");

        localMenu.getItems().addAll(uploadItem, new SeparatorMenuItem(), mkdirItemL, refreshItemL, renameItemL, new SeparatorMenuItem(), deleteItemL);
        localTable.setContextMenu(localMenu);


        // Remoto
        ContextMenu remoteMenu = new ContextMenu();

        // Descargar
        MenuItem downloadItem = new MenuItem("Descargar a mi PC");
        downloadItem.setOnAction(e -> handleDownload());
        downloadItem.setGraphic(new FontIcon("fas-cloud-download-alt"));

        //Actualizar
        MenuItem refreshItemR = new MenuItem("Actualizar");
        refreshItemR.setOnAction(e -> loadRemoteFiles());

        // Nueva carpeta
        MenuItem mkdirItemR = new MenuItem("Nueva Carpeta");
        mkdirItemR.setOnAction(e -> handleMkdir(false)); // false = remoto

        // Renombrar remoto
        MenuItem renameItemR = new MenuItem("Renombrar");
        renameItemR.setOnAction(e -> renameRemote());

        // Eliminar
        MenuItem deleteItemR = new MenuItem("Eliminar");
        deleteItemR.setOnAction(e -> handleDelete(false)); // false = remoto
        deleteItemR.setStyle("-fx-text-fill: red;");

        remoteMenu.getItems().addAll(downloadItem, new SeparatorMenuItem(), mkdirItemR, refreshItemR, renameItemR, new SeparatorMenuItem(), deleteItemR);
        remoteTable.setContextMenu(remoteMenu);
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
                        .filter(f -> showHiddenFiles || !f.isHidden())
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

            // Filtro archivos ocultos
            if (!showHiddenFiles) {
                files.removeIf(f -> f.getName().startsWith("."));
            }

            files.sort((a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            remoteTable.setItems(FXCollections.observableArrayList(files));
        } catch (Exception e) {
            statusLabel.setText("Error remoto: " + e.getMessage());
        }
    }

    @FXML
    public void handleUpload() {
        VecturaFile selected = localTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Selecciona un archivo local.");
            return;
        }

        TransferTask rowItem = new TransferTask(selected.getName(), "Subida", selected.getSize());
        queueTable.getItems().add(rowItem);

        javafx.concurrent.Task<Void> backgroundWorker = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Iniciando...");

                File localFile = new File(selected.getPath());
                String remoteDest = currentRemotePath.endsWith("/") ?
                        currentRemotePath + selected.getName() :
                        currentRemotePath + "/" + selected.getName();

                // Revisar existencia
                if (sftpService.getSftpClient().statExistence(remoteDest) != null) {

                    java.util.concurrent.FutureTask<String> query = new java.util.concurrent.FutureTask<>(() ->
                            askResolution(selected.getName())
                    );
                    javafx.application.Platform.runLater(query);

                    String decision = query.get();

                    if ("SKIP".equals(decision)) {
                        updateMessage("Cancelado por el usuario.");
                        throw new Exception("Cancelado por el usuario.");
                    } else if ("RENAME".equals(decision)) {
                        // Renombrado
                        String originalName = selected.getName();
                        String nameNoExt = originalName.contains(".") ? originalName.substring(0, originalName.lastIndexOf('.')) : originalName;
                        String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";

                        int i = 1;
                        // Calcular nuevo nombre
                        String newName = nameNoExt + " (" + i + ")" + ext;
                        String newPath = currentRemotePath.endsWith("/") ? currentRemotePath + newName : currentRemotePath + "/" + newName;

                        // Buscar hueco libre en el servidor
                        while (sftpService.getSftpClient().statExistence(newPath) != null) {
                            i++;
                            newName = nameNoExt + " (" + i + ")" + ext;
                            newPath = currentRemotePath.endsWith("/") ? currentRemotePath + newName : currentRemotePath + "/" + newName;
                        }

                        // Asignar el nuevo destino final
                        remoteDest = newPath;
                        updateMessage("Renombrando a: " + newName);
                    }
                }

                // Sensor de progreso
                TransferListener progressSensor = new TransferListener() {

                    // Devolver 'this' para seguir usando este mismo listener dentro de las carpetas.
                    @Override
                    public TransferListener directory(String name) {
                        updateMessage("Entrando a carpeta: " + name);
                        return this;
                    }

                    // Devolver Listener
                    @Override
                    public StreamCopier.Listener file(String name, long size) {
                        updateMessage("Subiendo archivo: " + name);

                        // Listener con calculo de velocidad
                        return new StreamCopier.Listener() {
                            // Variables para recordar el estado anterior
                            private long lastTime = System.currentTimeMillis();
                            private long lastBytes = 0;
                            private String speedStr = "Calculando...";

                            @Override
                            public void reportProgress(long transferred) {
                                long total = selected.getSize();
                                if (total <= 0) total = 1;

                                // Control de tiempo (Throttling)
                                long now = System.currentTimeMillis();
                                long timeDiff = now - lastTime;

                                // Actualizar velocidad cada 500ms para que no parpadee
                                if (timeDiff >= 500) {
                                    long bytesDiff = transferred - lastBytes;

                                    // Velocidad = bytes / segundos
                                    double speedBps = (double) bytesDiff / timeDiff * 1000.0;
                                    speedStr = humanReadableByteCountSI((long) speedBps) + "/s";

                                    // Resetear contadores para el siguiente ciclo
                                    lastTime = now;
                                    lastBytes = transferred;
                                }

                                updateProgress(transferred, total);

                                // Mensaje con velocidad
                                String statusText = String.format("Subiendo... %s / %s (%s)",
                                        humanReadableByteCountSI(transferred),
                                        humanReadableByteCountSI(total),
                                        speedStr);

                                updateMessage(statusText);
                            }
                        };
                    }
                };

                transferManager.upload(localFile, remoteDest, progressSensor);
                return null;
            }
        };

        // Bindings
        rowItem.progressProperty().bind(backgroundWorker.progressProperty());
        backgroundWorker.messageProperty().addListener((obs, old, msg) -> rowItem.setStatus(msg));

        backgroundWorker.setOnSucceeded(e -> {
            rowItem.progressProperty().unbind();
            rowItem.setProgress(1.0);
            rowItem.setStatus("Completado");
            refreshBoth();
            statusLabel.setText("Subida exitosa: " + selected.getName());
        });

        backgroundWorker.setOnFailed(e -> {
            rowItem.progressProperty().unbind();
            rowItem.setProgress(0.0);
            rowItem.setStatus("Error: " + backgroundWorker.getException().getMessage());
            System.out.println(backgroundWorker.getException().getMessage());
        });

        new Thread(backgroundWorker).start();
    }

    @FXML
    public void handleDownload() {
        VecturaFile selected = remoteTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Selecciona un archivo remoto.");
            return;
        }

        TransferTask rowItem = new TransferTask(selected.getName(), "Bajada", selected.getSize());
        queueTable.getItems().add(rowItem);

        javafx.concurrent.Task<Void> backgroundWorker = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Iniciando conexion...");

                String remotePath = currentRemotePath.endsWith("/") ?
                        currentRemotePath + selected.getName() :
                        currentRemotePath + "/" + selected.getName();

                File localDestFolder = new File(currentLocalPath);

                // Revisar existencia
                File targetFile = new File(localDestFolder, selected.getName());

                if (targetFile.exists()) {
                    java.util.concurrent.FutureTask<String> query = new java.util.concurrent.FutureTask<>(() ->
                            askResolution(selected.getName())
                    );
                    javafx.application.Platform.runLater(query);

                    String decision = query.get();

                    if ("SKIP".equals(decision)) {
                        updateMessage("Cancelado.");
                        throw new Exception("Cancelado.");
                    } else if ("RENAME".equals(decision)) {
                        // Renombrado local
                        String originalName = selected.getName();
                        String nameNoExt = originalName.contains(".") ? originalName.substring(0, originalName.lastIndexOf('.')) : originalName;
                        String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";

                        int i = 1;
                        File newFile = new File(localDestFolder, nameNoExt + " (" + i + ")" + ext);

                        // Buscar hueco libre localmente
                        while (newFile.exists()) {
                            i++;
                            newFile = new File(localDestFolder, nameNoExt + " (" + i + ")" + ext);
                        }

                        // Cambiar el targetFile al nuevo nombre libre
                        targetFile = newFile;
                        updateMessage("Guardando como: " + newFile.getName());
                    }
                }

                // Sensor de progreso
                TransferListener progressSensor = new TransferListener() {

                    // Retorna 'this'
                    @Override
                    public TransferListener directory(String name) {
                        updateMessage("Creando carpeta local: " + name);
                        return this;
                    }

                    // Retorna un nuevo StreamCopier.Listener
                    @Override
                    public StreamCopier.Listener file(String name, long size) {
                        updateMessage("Bajando archivo: " + name);

                        return new StreamCopier.Listener() {
                            // Variables de estado
                            private long lastTime = System.currentTimeMillis();
                            private long lastBytes = 0;
                            private String speedStr = "Calculando...";

                            @Override
                            public void reportProgress(long transferred) {
                                long total = selected.getSize();
                                if (total <= 0) total = 1;

                                // Calculo de velocidad (cada 500ms)
                                long now = System.currentTimeMillis();
                                long timeDiff = now - lastTime;

                                if (timeDiff >= 500) {
                                    long bytesDiff = transferred - lastBytes;
                                    double speedBps = (double) bytesDiff / timeDiff * 1000.0;

                                    speedStr = humanReadableByteCountSI((long) speedBps) + "/s";

                                    lastTime = now;
                                    lastBytes = transferred;
                                }

                                updateProgress(transferred, total);

                                // Actualizar mensaje
                                String statusText = String.format("Bajando... %s / %s (%s)",
                                        humanReadableByteCountSI(transferred),
                                        humanReadableByteCountSI(total),
                                        speedStr);

                                updateMessage(statusText);
                            }
                        };
                    }
                };

                transferManager.download(remotePath, targetFile, progressSensor);
                return null;
            }
        };

        // Bindings
        rowItem.progressProperty().bind(backgroundWorker.progressProperty());
        backgroundWorker.messageProperty().addListener((obs, old, msg) -> rowItem.setStatus(msg));

        backgroundWorker.setOnSucceeded(e -> {
            rowItem.progressProperty().unbind();
            rowItem.setProgress(1.0);
            rowItem.setStatus("Completado");
            refreshBoth();
            statusLabel.setText("Descarga exitosa: " + selected.getName());
        });

        backgroundWorker.setOnFailed(e -> {
            rowItem.progressProperty().unbind();
            rowItem.setProgress(0.0);
            rowItem.setStatus("Error: " + backgroundWorker.getException().getMessage());
            System.out.println(backgroundWorker.getException().getMessage());
        });

        new Thread(backgroundWorker).start();
    }

    private void handleDelete(boolean isLocal) {
        // Identificar que borrar
        VecturaFile selected = isLocal ? localTable.getSelectionModel().getSelectedItem()
                : remoteTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            statusLabel.setText("Selecciona algo para eliminar.");
            return;
        }

        // Confirmacion
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Eliminacion");
        alert.setHeaderText("¿Eliminar permanentemente '" + selected.getName() + "'?");
        alert.setContentText("Si es una carpeta, se borrara TODO su contenido. Esta accion no se puede deshacer.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {

                statusLabel.setText("🗑️ Eliminando...");

                // Ejecutar en segundo plano
                new Thread(() -> {
                    try {
                        if (isLocal) {
                            // Borrado Local Recursivo
                            transferManager.deleteLocal(new File(selected.getPath()));
                        } else {
                            // Borrado Remoto Recursivo
                            String remotePath = currentRemotePath.endsWith("/") ?
                                    currentRemotePath + selected.getName() :
                                    currentRemotePath + "/" + selected.getName();

                            transferManager.deleteRemote(remotePath);
                        }

                        // Actualizar UI al terminar
                        javafx.application.Platform.runLater(() -> {
                            refreshBoth();
                            statusLabel.setText("Eliminado: " + selected.getName());
                        });

                    } catch (Exception e) {
                        javafx.application.Platform.runLater(() -> {
                            statusLabel.setText("Error al eliminar: " + e.getMessage());

                            // Mostrar alerta de error detallada
                            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                            errorAlert.setTitle("Error");
                            errorAlert.setHeaderText("No se pudo eliminar");
                            errorAlert.setContentText(e.getMessage());
                            errorAlert.show();
                        });
                    }
                }).start();
            }
        });
    }

    private void handleMkdir(boolean isLocal) {
        // Dialogo para carpeta
        TextInputDialog dialog = new TextInputDialog("Nueva Carpeta");
        dialog.setTitle("Crear Carpeta");
        dialog.setHeaderText(isLocal ? "Crear carpeta en MI PC" : "Crear carpeta en SERVIDOR");
        dialog.setContentText("Nombre:");

        dialog.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) return;

            try {
                if (isLocal) {
                    // Logica Local
                    File newDir = new File(currentLocalPath, name);
                    if (!newDir.mkdir()) throw new IOException("No se pudo crear la carpeta");
                } else {
                    // Logica Remota (SSHJ)
                    String path = currentRemotePath.endsWith("/") ? currentRemotePath + name : currentRemotePath + "/" + name;
                    sftpService.getSftpClient().mkdir(path);
                }
                refreshBoth();
                statusLabel.setText("Carpeta creada: " + name);
            } catch (Exception e) {
                statusLabel.setText("Error: " + e.getMessage());
            }
        });
    }

    // Navegacion
    @FXML
    public void handleLocalUp() {
        if (currentLocalPath == null || currentLocalPath.isEmpty() || currentLocalPath.equals(localBaseFolder)) return;

        File parent = new File(currentLocalPath).getParentFile();
        if (parent != null) {
            currentLocalPath = parent.getAbsolutePath();
            loadLocalFiles();
        }
    }

    @FXML
    public void handleLocalHome() {
        // Si no hay carpeta base forza a seleccionar
        if (localBaseFolder == null) {
            handleLocalBrowse();
            return;
        }

        // Si hay base volver a ella
        currentLocalPath = localBaseFolder;
        loadLocalFiles();
    }

    @FXML
    public void handleRemoteUp() {
        if (!isConnected || currentRemotePath.equals("/")) return;

        int lastSlash = currentRemotePath.lastIndexOf('/');
        if (lastSlash > 0) {
            currentRemotePath = currentRemotePath.substring(0, lastSlash);
        } else {
            currentRemotePath = "/";
        }

        loadRemoteFiles();
    }

    @FXML
    public void handleRemoteHome() {
        if (!isConnected) return;
        currentRemotePath = remoteBaseFolder;
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

    private void doConnect() {
        // Leer datos de la UI
        String host = hostField.getText().trim();
        String user = userField.getText().trim();
        String pass = passwordField.getText().trim();
        String portStr = portField.getText().trim();

        // Validaciones basicas
        if (host.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            statusLabel.setText("Por favor llena todos los campos.");
            return;
        }

        int port;

        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            statusLabel.setText("El puerto debe ser un numero.");
            return;
        }

        // Bloqueo visual
        btnConnect.setDisable(true);
        statusLabel.setText("Conectando a " + host + "...");

        final int finalPort = port;

        // Tarea en segundo plano
        javafx.concurrent.Task<Void> connectTask = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {

                sftpService = new SftpService();
                sftpService.connect(host, finalPort, user, pass);

                transferManager = sftpService.getTransferManager();

                currentRemotePath = sftpService.getSftpClient().canonicalize(".");

                return null;
            }
        };

        // Manejo de resultados
        connectTask.setOnSucceeded(e -> {
            isConnected = true;
            updateConnectionState(true);
            statusLabel.setText("Conectado a " + host);

            // Guardar datos
            saveSettings();

            // Seleccionar ruta
            currentLocalPath = null;
            localBaseFolder = null;
            localPathField.clear();
            localTable.setPlaceholder(new Label("Seleccione una carpeta local."));

            // Obtener ruta del servidor
            try {
                String initialPath = sftpService.getSftpClient().canonicalize(".");

                currentRemotePath = initialPath;
                remoteBaseFolder = initialPath;
            } catch (Exception ex) {
                currentRemotePath = "/";
                remoteBaseFolder = "/";
            }

            loadRemoteFiles();

            btnConnect.setDisable(false);
        });

        connectTask.setOnFailed(e -> {
            isConnected = false;
            updateConnectionState(false);
            btnConnect.setDisable(false);

            Throwable error = connectTask.getException();
            statusLabel.setText("Error: " + error.getMessage());

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error de Conexion");
            alert.setHeaderText("No se pudo conectar");
            alert.setContentText(error.getMessage());
            alert.show();
        });

        new Thread(connectTask).start();
    }

    private void doDisconnect() {
        // Intentar cerrar la sesion SSH
        if (sftpService != null) {
            try {
                sftpService.disconnect();
            } catch (Exception e) {
                statusLabel.setText(e.getMessage());
            }
        }

        // Limpieza

        // Borrar contenido visual
        remoteTable.getItems().clear();
        localTable.getItems().clear();
        queueTable.getItems().clear();

        // Resetear rutas
        currentRemotePath = "";
        currentLocalPath = "";
        localPathField.clear();
        remotePathField.clear();

        statusLabel.setText("Desconectado");
        updateConnectionState(false);
    }

    @FXML
    public void handleToggleConnect() {
        if (isConnected) {

            if (thereAreActiveTransfers()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Transferencias en curso");
                alert.setHeaderText("Hay transferencias activas");
                alert.setContentText("Si te desconectas ahora, se cancelaran las subidas/bajadas actuales.\n\n¿Estas seguro de querer desconectar?");

                // Personalizar botones
                ButtonType btnYes = new ButtonType("Si, desconectar", ButtonBar.ButtonData.OK_DONE);
                ButtonType btnNo = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

                alert.getButtonTypes().setAll(btnYes, btnNo);

                // Esperar respuesta
                java.util.Optional<ButtonType> result = alert.showAndWait();

                // Si dice que NO (o cierra la ventana), abortamos la desconexion
                if (result.isEmpty() || result.get() != btnYes) {
                    return;
                }
            }

            doDisconnect();
        } else {
            doConnect();
        }
    }

    // Alternar boton
    private void updateConnectionState(boolean connected) {
        this.isConnected = connected;

        // setDisabled = !connected
        boolean disableNav = !connected;

        // Controlar TABLAS
        localTable.setDisable(disableNav);
        remoteTable.setDisable(disableNav);

        // Controlar botones de navegacion
        if (btnLocalBrowse != null) {
            btnLocalBrowse.setDisable(disableNav);
            btnLocalUp.setDisable(disableNav);
            btnLocalHome.setDisable(disableNav);

            btnRemoteUp.setDisable(disableNav);
            btnRemoteHome.setDisable(disableNav);
        }

        // Controlar campos de conexion
        hostField.setDisable(connected);
        userField.setDisable(connected);
        passwordField.setDisable(connected);
        portField.setDisable(connected);

        // Transformar boton principal
        if (connected) {
            btnConnect.setText("Desconectar");
            btnConnect.setStyle("-fx-background-color: #d9534f; -fx-text-fill: white; -fx-font-weight: bold;");
            btnConnect.setGraphic(new FontIcon("fas-times"));
        } else {
            btnConnect.setText("Conexion Rapida");
            btnConnect.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
            btnConnect.setGraphic(new FontIcon("fas-plug"));
        }

        if (menuConnect != null) {
            menuConnect.setText(connected ? "Desconectar" : "Conectar");
        }
    }

    @FXML
    public void handleLocalBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Seleccionar carpeta de trabajo");

        // Si ya hay una ruta, empezamos ahi. Si no, el chooser abre por defecto en Documentos/PC
        if (currentLocalPath != null && !currentLocalPath.isEmpty()) {
            File currentCtx = new File(currentLocalPath);
            if (currentCtx.exists() && currentCtx.isDirectory()) {
                chooser.setInitialDirectory(currentCtx);
            }
        }

        File selectedDirectory = chooser.showDialog(localTable.getScene().getWindow());

        if (selectedDirectory != null) {
            String newPath = selectedDirectory.getAbsolutePath();

            // Establecer la ruta actual
            currentLocalPath = newPath;
            localBaseFolder = newPath;

            // Cargar
            loadLocalFiles();
        }
    }

    private void saveSettings() {
        // Guardar preferencias
        preferences.put(PREF_HOST, hostField.getText().trim());
        preferences.put(PREF_USER, userField.getText().trim());
        preferences.put(PREF_PORT, portField.getText().trim());
    }

    private void loadSettings() {
        // Recuperar preferencias
        hostField.setText(preferences.get(PREF_HOST, ""));
        userField.setText(preferences.get(PREF_USER, ""));
        portField.setText(preferences.get(PREF_PORT, ""));
    }

    @FXML
    public void handleExit() {
        // Verificar si estamos conectados y hay actividad
        if (isConnected && thereAreActiveTransfers()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmar Salida");
            alert.setHeaderText("Transferencias en curso");
            alert.setContentText("Hay archivos transfiriendose. Si sales, se perderan.\n¿Salir de todos modos?");

            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return; // El usuario se arrepintio
            }
        }

        // Desconexion limpia
        if (isConnected) {
            doDisconnect();
        }

        // Matar la aplicacion
        javafx.application.Platform.exit();
        System.exit(0);
    }

    @FXML
    public void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Acerca de");
        alert.setHeaderText("Vectura SFTP Client v1.0");
        alert.setContentText("""
                Desarrollado con JavaFX y SSHJ.
                
                Una herramienta simple y segura para transferencia de archivos.
                2025 © Ibhar Gomez""");

        alert.showAndWait();
    }

    @FXML
    public void handleToggleHidden() {
        // Invertir estado
        showHiddenFiles = !showHiddenFiles;
        // Recargar
        refreshBoth();
    }

    @FXML
    public void handleClearQueue() {
        // Si la lista esta vacia, no hacemos nada
        if (queueTable.getItems().isEmpty()) {
            return;
        }

        // removeIf: Borra el elemento si devuelve TRUE
        boolean deleted = queueTable.getItems().removeIf(task -> {
            String status = task.getStatus();

            // Proteccion contra nulos
            if (status == null) return true;

            // CRITERIO: Borramos solo si termino (Bien o Mal)
            return status.equals("Completado") || status.startsWith("Error");
        });

        if (deleted) {
            statusLabel.setText("Cola limpiada (Activos conservados).");
        } else {
            statusLabel.setText("No hay tareas finalizadas para limpiar.");
        }
    }

    @FXML
    public void handleOpenExplorer() {
        if (currentLocalPath == null || currentLocalPath.isEmpty()) return;

        try {
            java.awt.Desktop.getDesktop().open(new File(currentLocalPath));
        } catch (IOException e) {
            statusLabel.setText("No se pudo abrir el explorador.");
        }
    }

    @FXML
    public void handleOpenTerminal() {
        if (currentLocalPath == null || currentLocalPath.isEmpty()) return;

        try {
            File dir = new File(currentLocalPath);
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Comando para Windows (abre CMD en esa ruta)
                new ProcessBuilder("cmd", "/c", "start", "cmd.exe", "/K", "cd /d \"" + currentLocalPath + "\"")
                        .directory(dir)
                        .start();
            } else if (os.contains("mac")) {
                // Comando para Mac
                new ProcessBuilder("open", "-a", "Terminal", currentLocalPath)
                        .directory(dir)
                        .start();
            } else if (os.contains("nix") || os.contains("nux")) {
                // Intento generico para Linux (gnome-terminal)
                new ProcessBuilder("gnome-terminal", "--working-directory=" + currentLocalPath)
                        .directory(dir)
                        .start();
            }
        } catch (Exception e) {
            statusLabel.setText("No se pudo abrir la terminal: " + e.getMessage());
        }
    }

    private boolean thereAreActiveTransfers() {
        // Recorre la cola
        for (TransferTask task : queueTable.getItems()) {
            String status = task.getStatus();

            // Si es null ignorar
            if (status == null) continue;

            // Si no esta completado o no hay error es activo
            if (!status.equals("Completado") && !status.startsWith("Error")) return true;
        }

        return false;
    }

    @FXML
    public void handleRename() {
        // Detectar foco
        if (localTable.isFocused() || !localTable.getSelectionModel().isEmpty()) {
            renameLocal();
        } else if (remoteTable.isFocused() || !remoteTable.getSelectionModel().isEmpty()) {
            renameRemote();
        } else {
            statusLabel.setText("Seleccione un archivo para renombrar.");
        }
    }

    private void renameLocal() {
        VecturaFile selected = localTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String oldName = selected.getName();
        String newName = promptForName("Renombrar en LOCAL", "Nombre actual: " + oldName, oldName);

        if (newName != null && !newName.equals(oldName)) {
            File oldFile = new File(selected.getPath());
            File newFile = new File(oldFile.getParent(), newName);

            if (oldFile.renameTo(newFile)) {
                statusLabel.setText("Renombrado local: " + newName);
                loadLocalFiles();
            } else {
                statusLabel.setText("Error: no se pudo renombrar el archvio local.");
            }
        }
    }

    private void renameRemote() {
        VecturaFile selected = remoteTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String oldName = selected.getName();
        String newName = promptForName("Renombrar en SERVIDOR", "Nombre actual: " + oldName, oldName);

        if (newName != null && !newName.equals(oldName)) {
            // Hilo para no congelar UI
            new Thread(() -> {
                try {
                    String path = currentRemotePath.endsWith("/") ? currentRemotePath : currentRemotePath + "/";
                    String oldPath = path + oldName;
                    String newPath = path + newName;

                    sftpService.getSftpClient().rename(oldPath, newPath);

                    Platform.runLater(() -> {
                        statusLabel.setText("Renombrado remoto: " + newName);
                        loadRemoteFiles();
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> statusLabel.setText("Error remoto: " + e.getMessage()));
                }
            }).start();
        }
    }

    // Dialogo para obtener nuevo nombre
    private String promptForName(String title, String header, String currentName) {
        TextInputDialog dialog = new TextInputDialog(currentName);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText("Nuevo nombre: ");

        dialog.getDialogPane().setGraphic(new FontIcon("fas-edit"));

        return dialog.showAndWait()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    // Dialogo de conflictos
    private String askResolution(String fileName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Conflicto de Archivos");
        alert.setHeaderText("El archivo ya existe: " + fileName);
        alert.setContentText("¿Qué deseas hacer?");

        ButtonType btnOverwrite = new ButtonType("Sobreescribir");
        ButtonType btnRename = new ButtonType("Renombrar (Auto)"); // <--- NUEVO
        ButtonType btnSkip = new ButtonType("Saltar (Cancelar)", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(btnOverwrite, btnRename, btnSkip);

        java.util.Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent()) {
            if (result.get() == btnOverwrite) return "OVERWRITE";
            if (result.get() == btnRename) return "RENAME";
        }
        return "SKIP";
    }
}