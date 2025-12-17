package openproject.server;

import openproject.server.auth.CustomDatabaseAuthenticator;
import openproject.server.db.DatabaseManager;
import openproject.server.fs.VecturaFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;

public class ServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) throws Exception {
        // Base de datos
        DatabaseManager dbManager = new DatabaseManager();
        dbManager.init();
        // Instancia base
        try (SshServer sshd = SshServer.setUpDefaultServer()) {

            // Puerto
            sshd.setPort(2222);

            // Generar llave simple si no existe
            sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File("hostkey.ser").toPath()));

            // Habilitar protocolo de transferencia de archivos
            sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));

            // Credenciales temporales
            sshd.setPasswordAuthenticator(new CustomDatabaseAuthenticator(dbManager));

            // Enjaular usuario
            sshd.setFileSystemFactory(new VecturaFileSystemFactory());

            // Iniciar
            sshd.start();
            LOG.info("Server started in port: {}", sshd.getPort());

            // Mantener vivo
            Thread.sleep(Long.MAX_VALUE);
        }

    }
}
