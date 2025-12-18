package com.vectura.client.model;

import java.io.File;

public class LocalVecturaFile implements VecturaFile {
    private final File file;

    public LocalVecturaFile(File file) {
        this.file = file;
    }

    public LocalVecturaFile(String path) {
        this.file = new File(path);
    }


    @Override
    public String getName() {
        String name = file.getName();
        return name.isEmpty() ? file.getPath() : name;
    }

    @Override
    public String getPath() {
        return file.getAbsolutePath();
    }

    @Override
    public long getSize() {
        return file.length();
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public long getLastModifiedTime() {
        return file.lastModified();
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public String getParentPath() {
        return file.getParent();
    }

    public File getOriginalFile() {
        return file;
    }
}
