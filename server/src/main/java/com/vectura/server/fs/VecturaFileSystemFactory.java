package com.vectura.server.fs;

import com.vectura.server.auth.CustomDatabaseAuthenticator;

import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.file.root.RootedFileSystemProvider;
import org.apache.sshd.common.session.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class VecturaFileSystemFactory implements FileSystemFactory {
    private static final Logger LOG = LoggerFactory.getLogger(VecturaFileSystemFactory.class);

    @Override
    public Path getUserHomeDir(SessionContext sessionContext) throws IOException {
        return null;
    }

    @Override
    public FileSystem createFileSystem(SessionContext sessionContext) throws IOException {
        String homeDirStr = sessionContext.getAttribute(CustomDatabaseAuthenticator.USER_HOME_DIR_KEY);

        if (homeDirStr == null) {
            LOG.error("No home directory found for session {}", sessionContext);
            throw new IOException("No home directory defined for session");
        }

        // Convertir ruta a ruta real del s.o.
        Path homePath = Paths.get(homeDirStr).toAbsolutePath().normalize();

        if (!Files.exists(homePath)) {
            LOG.warn("Home directory {} does not exist. Creating...", homePath);
            Files.createDirectories(homePath);
        }

        LOG.info("Caging user home directory at {}", homePath);

        return new RootedFileSystemProvider().newFileSystem(homePath, Collections.emptyMap());
    }
}
