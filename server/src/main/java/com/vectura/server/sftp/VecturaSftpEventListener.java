package com.vectura.server.sftp;

import com.vectura.server.util.UILogManager;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.Handle;
import org.apache.sshd.sftp.server.SftpEventListener;

import java.nio.file.CopyOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VecturaSftpEventListener implements SftpEventListener {

    private final Map<String, Boolean> uploadTracker = new ConcurrentHashMap<>();

    public VecturaSftpEventListener() {
    }

    @Override
    public void initialized(ServerSession session, int version) {
        // Descartado log
    }

    @Override
    public void destroying(ServerSession session) {
        // Fin de subsistema
    }

    @Override
    public void open(ServerSession session, String remoteHandle, Handle localHandle) {
        // Al abrir, asumir lectura (bajada) por defecto
        uploadTracker.put(remoteHandle, false);
    }

    @Override
    public void writing(ServerSession session, String remoteHandle, FileHandle localHandle, long offset, byte[] data, int dataOffset, int dataLen) {
        // Si hay escritura, marcar como subida
        if (uploadTracker.containsKey(remoteHandle)) {
            uploadTracker.put(remoteHandle, true);
        }
    }

    @Override
    public void closed(ServerSession session, String remoteHandle, Handle localHandle, Throwable thrown) {
        Boolean isUpload = uploadTracker.remove(remoteHandle);

        if (localHandle instanceof FileHandle) {
            Path path = localHandle.getFile();
            String name = path.getFileName().toString();

            if (thrown != null) {
                log(session, "[ERROR] Failed on file '" + name + "': " + thrown.getMessage());
                return;
            }

            if (isUpload != null && isUpload) {
                log(session, "[UPLOAD] Upload completed: " + name);
            } else {
                log(session, "[DOWNLOAD] Download/Read completed: " + name);
            }
        }
    }

    @Override
    public void removed(ServerSession session, Path path, boolean isDirectory, Throwable thrown) {
        if (thrown == null) {
            log(session, "[DELETE] Deleted: " + path.getFileName());
        }
    }

    @Override
    public void moved(ServerSession session, Path srcPath, Path dstPath, Collection<CopyOption> opts, Throwable thrown) {
        if (thrown == null) {
            log(session, "[RENAME] Renamed: " + srcPath.getFileName() + " -> " + dstPath.getFileName());
        }
    }

    @Override
    public void created(ServerSession session, Path path, Map<String, ?> attrs, Throwable thrown) {
        if (thrown == null && java.nio.file.Files.isDirectory(path)) {
            log(session, "[MKDIR] Directory created: " + path.getFileName());
        }
    }

    private void log(ServerSession session, String msg) {
        String user = (session != null && session.getUsername() != null) ? session.getUsername() : "UNKNOWN";
        UILogManager.log("[EVENT] [" + user + "] " + msg);
    }
}