package com.vectura.server.fs;

import com.vectura.server.auth.CustomDatabaseAuthenticator;

import com.vectura.server.util.UILogManager;
import org.apache.sshd.common.AttributeRepository;
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
    // Flag de encapsulamiento
    private static final AttributeRepository.AttributeKey<Boolean> CAGING_LOGGED_KEY = new AttributeRepository.AttributeKey<>();

    @Override
    public Path getUserHomeDir(SessionContext sessionContext) {
        return null;
    }

    @Override
    public FileSystem createFileSystem(SessionContext sessionContext) throws IOException {
        String homeDirStr = sessionContext.getAttribute(CustomDatabaseAuthenticator.USER_HOME_DIR_KEY);

        if (homeDirStr == null) {
            LOG.error("No home directory found for session {}", sessionContext);
            UILogManager.log(String.format("[FILESYSTEM] No home directory found for session: %s", sessionContext));
            throw new IOException("No home directory defined for session");
        }

        // Convertir ruta a ruta real del s.o.
        Path homePath = Paths.get(homeDirStr).toAbsolutePath().normalize();

        if (!Files.exists(homePath)) {
            LOG.warn("Home directory {} does not exist. Creating...", homePath);
            UILogManager.log(String.format("[FILESYSTEM] Home directory '%s' does not exist. Creating...", homePath));
            Files.createDirectories(homePath);
        }

        if (sessionContext.getAttribute(CAGING_LOGGED_KEY) == null) {
            LOG.info("Caging user home directory at {}", homePath);
            UILogManager.log(String.format("[FILESYSTEM] Caging user home directory at '%s'", homePath));
            sessionContext.setAttribute(CAGING_LOGGED_KEY,true);
        }

        return new RootedFileSystemProvider().newFileSystem(homePath, Collections.emptyMap());
    }
}
