package com.vectura.server.sftp;

import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

public class VecturaSftpSubsystemFactory extends SftpSubsystemFactory {

    @Override
    public SftpSubsystem createSubsystem(ChannelSession channel) {
        // En lugar de devolver el estándar, devolvemos el nuestro
        return new VecturaSftpSubsystem(channel, this);
    }
}
