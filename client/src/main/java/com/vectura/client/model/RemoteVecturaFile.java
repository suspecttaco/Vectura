package com.vectura.client.model;

import net.schmizz.sshj.sftp.RemoteResourceInfo;

public class RemoteVecturaFile implements VecturaFile {
    private final RemoteResourceInfo remoteResourceInfo;
    private final String parentPath;

    public RemoteVecturaFile(RemoteResourceInfo remoteResourceInfo, String parentPath) {
        this.remoteResourceInfo = remoteResourceInfo;
        this.parentPath = parentPath;
    }


    @Override
    public String getName() {
        return remoteResourceInfo.getName();
    }

    @Override
    public String getPath() {
        return remoteResourceInfo.getPath();
    }

    @Override
    public long getSize() {
        return remoteResourceInfo.getAttributes().getSize();
    }

    @Override
    public boolean isDirectory() {
        return remoteResourceInfo.isDirectory();
    }

    @Override
    public long getLastModifiedTime() {
        // Convertir segundos Unix a milisegundos Java
        return remoteResourceInfo.getAttributes().getMtime() * 1000L;
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public String getParentPath() {
        return parentPath;
    }
}

