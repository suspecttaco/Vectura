package openproject.server;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import java.io.File;
import java.util.Collections;

public class ServerMain {

    public static void main(String[] args) throws Exception {
        // Instancia base
        try (SshServer sshd = SshServer.setUpDefaultServer()) {

            // Puerto
            sshd.setPort(2222);

            // Generar llave simple si no existe
            sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(new File("hostkey.ser").toPath()));

            // Habilitar protocolo de transferencia de archivos
            sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));

            // Credenciales temporales
            sshd.setPasswordAuthenticator((username, password, session) ->
                    "admin".equals(username) && "admin".equals(password));

            // Iniciar
            sshd.start();
            System.out.println("Server Started in port: " + sshd.getPort());
        }

        // Mantener vivo
        Thread.sleep(Long.MAX_VALUE);
    }
}
