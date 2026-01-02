package com.vectura.server.sftp;

import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import java.util.function.Consumer;

public class VecturaSftpSubsystemFactory extends SftpSubsystemFactory {

    private final Consumer<String> logCallback;

    // Constructor que exige el logger
    public VecturaSftpSubsystemFactory(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    @Override
    public SftpSubsystem createSubsystem(ChannelSession channel) {
        VecturaSftpSubsystem subsystem = new VecturaSftpSubsystem(channel, this);

        // Asignar listener
        subsystem.addSftpEventListener(new VecturaSftpEventListener(logCallback));

        return subsystem;
    }
}