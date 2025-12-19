package com.vectura.client.model;


import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

public class TransferTask {

    // Properties
    private final StringProperty fileName;
    private final StringProperty type;
    private final StringProperty status;
    private final DoubleProperty progress;
    private final StringProperty size;

    public TransferTask(String filename, String type, long sizeBytes) {
        this.fileName = new SimpleStringProperty(filename);
        this.type = new SimpleStringProperty(type);
        this.status = new SimpleStringProperty("Pendiente");
        this.progress = new SimpleDoubleProperty(0.0);
        this.size = new SimpleStringProperty(formatSize(sizeBytes));
    }

    // Formatear tamaño
    private String formatSize(long bytes) {
        if (-1000 < bytes && bytes < 1000) return bytes + " B";

        CharacterIterator ci = new StringCharacterIterator("kMGTPE");

        while (bytes <= -999950 || bytes >= 999950) {
            bytes /= 1000;
            ci.next();
        }

        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }

    // Getters de propiedades
    public StringProperty fileNameProperty() { return fileName; }
    public StringProperty typeProperty() { return type; }
    public StringProperty statusProperty() { return status; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty sizeProperty() { return size; }

    // Getters y Setters
    public String getFileName() { return fileName.get(); }
    public String getType() { return type.get(); }

    public void setStatus(String status) { this.status.set(status); }
    public String getStatus() { return status.get(); }

    public void setProgress(double progress) { this.progress.set(progress); }
    public double getProgress() { return progress.get(); }
}
