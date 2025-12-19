package com.vectura.client.service;

import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPException;
import net.schmizz.sshj.sftp.Response;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.TransferListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransferManager {
    private final Logger LOG = LoggerFactory.getLogger(TransferManager.class);
    private final SFTPClient sftpClient;

    public TransferManager(SFTPClient sftpClient) {
        this.sftpClient = sftpClient;
    }

    // Subida
    public void upload(File localItem, String remotePath, TransferListener listener) throws IOException {
        LOG.info("Uploading: {} -> {}", localItem.getName(), remotePath);

        sftpClient.getFileTransfer().setTransferListener(listener);

        sftpClient.getFileTransfer().upload(new FileSystemFile(localItem), remotePath);
    }

    // Bajada
    public void download(String remotePath, File localDestFolder, TransferListener listener) throws IOException {
        LOG.info("Downloading: {} -> {}", remotePath, localDestFolder.getAbsolutePath());

        sftpClient.getFileTransfer().setTransferListener(listener);

        sftpClient.getFileTransfer().download(remotePath, new FileSystemFile(localDestFolder));
    }

    // Borrar remoto
    public void deleteRemote(String path) throws IOException {
        // Obtener atributos
        if (sftpClient.stat(path).getType() == FileMode.Type.DIRECTORY) {

            // Listar contenido
            List<RemoteResourceInfo> children = sftpClient.ls(path);

            for (RemoteResourceInfo child : children) {
                // Saltar "." y ".."
                if (child.getName().equals(".") || child.getName().equals("..")) continue;

                // Recursividad para borrar hijo
                deleteRemote(child.getPath());
            }
            // Borrar carpeta una vez este vacia
            sftpClient.rmdir(path);
        } else {
            // Si es archivo borrar directo
            sftpClient.rm(path);
        }
    }

    public void deleteLocal(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();

            if (children != null) {
                for (File child : children) {
                    deleteLocal(child);
                }
            }
        }

        // Borrar archivo o carpeta vacia
        if (!file.delete()) {
            throw new IOException("No se pudo borrar: " + file.getAbsolutePath());
        }
    }
}
