/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.server.git;

import com.innovarhealthcare.channelHistory.shared.model.GitSettings;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.util.FS;

import java.nio.charset.StandardCharsets;

public class SshTransportConfig implements TransportConfigCallback {

    private static final Logger logger = LogManager.getLogger(SshTransportConfig.class);
    private static final String SSH_KEY_IDENTITY_NAME = "version-history-ssh-key";

    private final SshSessionFactory factory;

    public SshTransportConfig(GitSettings settings) {
        this.factory = buildFactory(settings);
    }

    @Override
    public void configure(Transport transport) {
        if (transport instanceof SshTransport) {
            ((SshTransport) transport).setSshSessionFactory(factory);
        }
    }

    private SshSessionFactory buildFactory(GitSettings settings) {
        final String sshPrivateKey = settings.getSshPrivateKey();
        final String sshPrivateKeyPath = settings.getSshPrivateKeyPath();

        boolean hasInlineKey = sshPrivateKey != null && !sshPrivateKey.trim().isEmpty();
        boolean hasKeyPath = sshPrivateKeyPath != null && !sshPrivateKeyPath.trim().isEmpty();

        if (!hasInlineKey && !hasKeyPath) {
            throw new IllegalStateException("No SSH private key configured. Provide an inline key or a key file path.");
        }

        return new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host hc, Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch jsch = super.createDefaultJSch(fs);
                try {
                    if (hasInlineKey) {
                        jsch.addIdentity(SSH_KEY_IDENTITY_NAME, sshPrivateKey.getBytes(StandardCharsets.UTF_8), null, null);
                        logger.debug("SSH private key loaded from inline content");
                    } else {
                        jsch.addIdentity(sshPrivateKeyPath.trim());
                        logger.debug("SSH private key loaded from path: {}", sshPrivateKeyPath);
                    }
                } catch (JSchException e) {
                    logger.error("Failed to add SSH private key", e);
                    throw e;
                }
                return jsch;
            }
        };
    }
}
