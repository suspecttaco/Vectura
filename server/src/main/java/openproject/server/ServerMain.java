package openproject.server;

import openproject.server.auth.CustomDatabaseAuthenticator;
import openproject.server.db.DatabaseManager;
import openproject.server.fs.VecturaFileSystemFactory;

import openproject.server.sftp.VecturaSftpSubsystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.apache.sshd.core.CoreModuleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
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
            sshd.setSubsystemFactories(Collections.singletonList(new VecturaSftpSubsystemFactory()));

            // Credenciales temporales
            sshd.setPasswordAuthenticator(new CustomDatabaseAuthenticator(dbManager));

            // Enjaular usuario
            sshd.setFileSystemFactory(new VecturaFileSystemFactory());

            // Deshabilitar shell
            sshd.setShellFactory(null);

            // Deshabilitar comandos exec
            sshd.setCommandFactory(null);

            // Deshabilitar pport forwarding
            sshd.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);

            // Timeout de 10s para autenticar
            CoreModuleProperties.AUTH_TIMEOUT.set(sshd, Duration.ofSeconds(10));

            // Inactividad de 5 min.
            CoreModuleProperties.IDLE_TIMEOUT.set(sshd, Duration.ofMinutes(5));

            // Limite de conexiones
            CoreModuleProperties.MAX_CONCURRENT_SESSIONS.set(sshd, 10);

            // Iniciar
            sshd.start();
            LOG.info("Server started in port: {}", sshd.getPort());

            // Mantener vivo
            Thread.sleep(Long.MAX_VALUE);
        }

    }
}
