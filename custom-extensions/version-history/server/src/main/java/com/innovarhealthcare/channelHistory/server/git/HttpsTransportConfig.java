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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class HttpsTransportConfig implements TransportConfigCallback {

    private static final Logger logger = LogManager.getLogger(HttpsTransportConfig.class);

    private final CredentialsProvider credentials;

    public HttpsTransportConfig(GitSettings settings) {
        this.credentials = buildCredentials(settings);
    }

    @Override
    public void configure(Transport transport) {
        transport.setCredentialsProvider(credentials);
    }

    private CredentialsProvider buildCredentials(GitSettings settings) {
        String username = settings.getHttpsUsername();
        String password = settings.getHttpsPassword();
        String credentialsPath = settings.getHttpsCredentialsPath();

        boolean hasInline = username != null && !username.trim().isEmpty()
                && password != null && !password.trim().isEmpty();
        boolean hasFilePath = credentialsPath != null && !credentialsPath.trim().isEmpty();

        if (hasInline) {
            logger.debug("HTTPS credentials loaded from inline configuration");
            return new UsernamePasswordCredentialsProvider(username.trim(), password.trim());
        }

        if (hasFilePath) {
            return loadFromFile(credentialsPath.trim());
        }

        throw new IllegalStateException("No HTTPS credentials configured. Provide username/PAT or a credentials file path.");
    }

    /**
     * Reads credentials from a file. Expected format: two lines — username on line 1, PAT on line 2.
     */
    private CredentialsProvider loadFromFile(String path) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(path));
            if (lines.size() < 2) {
                throw new IllegalArgumentException("Credentials file must contain two lines: username and PAT");
            }
            String username = lines.get(0).trim();
            String password = lines.get(1).trim();
            if (username.isEmpty() || password.isEmpty()) {
                throw new IllegalArgumentException("Credentials file contains empty username or PAT on line 1/2");
            }
            logger.debug("HTTPS credentials loaded from file: {}", path);
            return new UsernamePasswordCredentialsProvider(username, password);
        } catch (IOException e) {
            logger.error("Failed to read HTTPS credentials file: {}", path, e);
            throw new IllegalStateException("Cannot read HTTPS credentials from file: " + path, e);
        }
    }
}
