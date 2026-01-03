package com.vectura.client.service;

import com.vectura.client.model.RemoteVecturaFile;
import com.vectura.client.model.VecturaFile;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SftpService {
    private static final Logger LOG = LoggerFactory.getLogger(SftpService.class);
    private SSHClient sshClient;
    private SFTPClient sftpClient;

    // Conectar y autenticar
    public void connect(String host, int port, String user, String password) throws IOException {
        if (isConnected()) {
            disconnect();
        }

        sshClient = new SSHClient();

        sshClient.addHostKeyVerifier(new PromiscuousVerifier());

        sshClient.connect(host, port);
        sshClient.authPassword(user, password);

        // Abrir sftp
        sftpClient = sshClient.newSFTPClient();
        LOG.info("SFTP connection established with {}", host);
    }

    // Listar archivos
    public List<VecturaFile> listDirectory(String remotePath) throws IOException {
        ensureConnected();

        List<RemoteResourceInfo> rawFiles = sftpClient.ls(remotePath);
        List<VecturaFile> result = new ArrayList<>();

        for (RemoteResourceInfo info : rawFiles) {
            // Saltar . y ..
            if (info.getName().equals(".") || info.getName().equals("..")) {
                continue;
            }

            // Envolver archivo crudo
            result.add(new RemoteVecturaFile(info, remotePath));
        }

        return result;
    }

    // Desconectar limpiamente
    public void disconnect() {
        try {
            if (sftpClient != null) sftpClient.close();
            if (sshClient != null) sshClient.disconnect();
        } catch (IOException e) {
            LOG.error(e.getMessage());
        } finally {
            sftpClient = null;
            sshClient = null;
        }
    }

    public boolean isConnected() {
        return sshClient != null && sshClient.isConnected() && sftpClient != null;
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            throw new IOException("Client is not connected.");
        }
    }

    public TransferManager getTransferManager() throws IOException {
        ensureConnected();

        SFTPClient isolatedClient = sshClient.newSFTPClient();

        return new TransferManager(isolatedClient);
    }

    public SFTPClient getSftpClient() {
        return this.sftpClient;
    }
}
