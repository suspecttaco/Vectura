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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransferManager {
    private final Logger LOG = LoggerFactory.getLogger(TransferManager.class);
    private final SFTPClient sftpClient;

    public TransferManager(SFTPClient sftpClient) {
        this.sftpClient = sftpClient;
    }

    // Subida
    public void upload(File localItem, String remotePath) throws IOException {
        LOG.info("Uploading: {} -> {}", localItem.getName(), remotePath);

        if (localItem.isDirectory()) {
            uploadFolder(localItem, remotePath);
        } else {
            uploadFile(localItem, remotePath);
        }
    }

    // Subida de carpetas
    private void uploadFolder(File localFolder, String remoteParentPath) throws IOException {
        String newRemotePath = remoteParentPath + "/" + localFolder.getName();

        // Crear remoto si no existe
        try {
            sftpClient.stat(newRemotePath);
        } catch (SFTPException e) {

            if (e.getStatusCode() == Response.StatusCode.NO_SUCH_FILE) {
                sftpClient.mkdir(newRemotePath);
            } else {
                throw e;
            }
        }

        // Subir recursivo
        File[] children = localFolder.listFiles();
        if (children != null) {
            for (File child : children) {
                upload(child, newRemotePath);
            }
        }
    }

    // Subida de archivos
    private void uploadFile(File localFile, String remotePath) throws IOException {
        sftpClient.put(localFile.getAbsolutePath(), remotePath);
    }

    // Bajada
    public void download(String remotePath, File localDestFolder) throws IOException {
        // Obtener informacion del archivo remoto
        FileAttributes attributes = sftpClient.stat(remotePath);
        String name = new File(remotePath).getName();
        File newLocalItem = new File(localDestFolder, name);

        LOG.info("Downloading: {} -> {}", remotePath, newLocalItem.getAbsolutePath());

        if (attributes.getMode().getType() == FileMode.Type.DIRECTORY) {
            downloadFolder(remotePath, newLocalItem);
        } else {
            downloadFile(remotePath, newLocalItem);
        }
    }

    private void downloadFolder(String remotePath, File localFolder) throws IOException {
        // Crear carpeta local si no existe
        if (!localFolder.exists()) {
            if (!localFolder.mkdirs()) {
                throw new IOException("Can't create local directory: " + localFolder.getAbsolutePath());
            }
        }

        // Listar contenido remoto y bajar recursivamente
        for (RemoteResourceInfo remoteChild : sftpClient.ls(remotePath)) {
            // Saltar . y ..
            if (remoteChild.getName().equals(".") || remoteChild.getName().equals("..")) continue;

            // Recursividad
            download(remoteChild.getPath(), localFolder);
        }
    }

    private void downloadFile(String remotePath, File localFile) throws IOException {
        sftpClient.get(remotePath, localFile.getAbsolutePath());
    }
}
