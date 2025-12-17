package openproject.server.sftp;

import openproject.server.util.HashUtils;

import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.apache.sshd.sftp.server.SftpSubsystemConfigurator;
import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.sftp.common.SftpConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class VecturaSftpSubsystem extends SftpSubsystem {
    private static final Logger LOG = LoggerFactory.getLogger(VecturaSftpSubsystem.class);

    // Nombre oficial
    private static final String EXTENSION_NAME = "check-file-name";

    public VecturaSftpSubsystem(ChannelSession channel, SftpSubsystemConfigurator configurator) {
        super(channel, configurator);
    }

    @Override
    protected void executeExtendedCommand(Buffer buffer, int id, String extension) throws IOException {
        // Procesado de extension personalizada
        if (EXTENSION_NAME.equals(extension)) {
            doCheckFile(id, buffer);
        } else {
            // Si es otra extension pasar a superclase
            super.executeExtendedCommand(buffer, id, extension);
        }
    }

    private void doCheckFile(int id, Buffer buffer) throws IOException {
        String filename = buffer.getString();
        // Vaciado de buffer
        String algs = buffer.getString();
        long offset = buffer.getLong();
        long length = buffer.getLong();

        LOG.info("HASH request received for: {}", filename);

        try {
            Path file = resolveFile(filename);

            if (!Files.exists(file) || Files.isDirectory(file)) {
                sendError(id, SftpConstants.SSH_FX_NO_SUCH_FILE, "File not found");
                return;
            }

            String hash = HashUtils.calculateSha256(file);
            LOG.info("Hash calculado: {}", hash);

            Buffer reply = new ByteArrayBuffer();
            reply.putByte((byte) SftpConstants.SSH_FXP_NAME);
            reply.putInt(id);
            reply.putInt(1);
            reply.putString(hash);
            reply.putString("");
            reply.putString("");

            send(reply);
        } catch (Exception e) {
            LOG.error("Error calculando hash", e);
            // CORRECCIÓN 2: Usamos 'id' directamente
            sendError(id, SftpConstants.SSH_FX_FAILURE, "Error interno: " + e.getMessage());
        }
    }

    // Helper privado
    private void sendError(int id, int substatus, String msg) throws IOException {
        Buffer buffer = new ByteArrayBuffer();
        buffer.putByte((byte) SftpConstants.SSH_FXP_STATUS);
        buffer.putInt(id);
        buffer.putInt(substatus);
        buffer.putString(msg);
        buffer.putString(""); // Language tag (vacio)
        send(buffer);
    }
}
