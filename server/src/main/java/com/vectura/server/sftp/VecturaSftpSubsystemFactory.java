package com.vectura.server.sftp;

import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

public class VecturaSftpSubsystemFactory extends SftpSubsystemFactory {

    // Constructor que exige el logger
    public VecturaSftpSubsystemFactory() {
    }

    @Override
    public SftpSubsystem createSubsystem(ChannelSession channel) {
        VecturaSftpSubsystem subsystem = new VecturaSftpSubsystem(channel, this);

        // Asignar listener
        subsystem.addSftpEventListener(new VecturaSftpEventListener());

        return subsystem;
    }
}