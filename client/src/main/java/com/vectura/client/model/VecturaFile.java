package com.vectura.client.model;

public interface VecturaFile {
    // Datos del archivo
    String getName();
    String getPath();
    long getSize();
    boolean isDirectory();
    long getLastModifiedTime();

    // Metodo para diferenciar de local o remoto
    boolean isRemote();

    // Para navegacion
    String getParentPath();

}
