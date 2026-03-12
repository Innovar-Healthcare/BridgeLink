/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.util.MirthSSLUtil;

public class ServerSMTPConnection {
    private static final Logger logger = LogManager.getLogger(ServerSMTPConnection.class);

    private String host;
    private String port;
    private boolean useAuthentication;
    private String secure;
    private String username;
    private String password;
    private String from;
    private int socketTimeout = 5000;

    // OAuth fields — populated only when authType is "OAUTH"
    private String authType;
    private String oAuthClientId;
    private String oAuthClientSecret;
    private String oAuthTokenEndpointUrl;
    private String oAuthScope;

    public ServerSMTPConnection(String host, String port, int socketTimeout, boolean useAuthentication, String secure, String username, String password, String from) {
        this.host = host;
        this.port = port;
        this.socketTimeout = socketTimeout;
        this.useAuthentication = useAuthentication;
        this.secure = secure;
        this.username = username;
        this.password = password;
        this.from = from;
        this.authType = useAuthentication ? "BASIC" : "NONE";
    }

    public ServerSMTPConnection(String host, String port, boolean useAuthentication, String secure, String username, String password, String from) {
        this(host, port, 5000, useAuthentication, secure, username, password, from);
    }

    /**
     * OAuth-capable constructor. Use this when {@code authType} is {@code "OAUTH"}.
     */
    public ServerSMTPConnection(String host, String port, int socketTimeout, String authType, String secure,
            String username, String oAuthClientId, String oAuthClientSecret,
            String oAuthTokenEndpointUrl, String oAuthScope, String from) {
        this.host = host;
        this.port = port;
        this.socketTimeout = socketTimeout;
        this.authType = authType;
        this.secure = secure;
        this.username = username;
        this.useAuthentication = "BASIC".equals(authType);
        this.oAuthClientId = oAuthClientId;
        this.oAuthClientSecret = oAuthClientSecret;
        this.oAuthTokenEndpointUrl = oAuthTokenEndpointUrl;
        this.oAuthScope = oAuthScope;
        this.from = from;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public boolean isUseAuthentication() {
        return useAuthentication;
    }

    public void setUseAuthentication(boolean useAuthentication) {
        this.useAuthentication = useAuthentication;
    }

    public String getSecure() {
        return secure;
    }

    public void setSecure(String secure) {
        this.secure = secure;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public void send(String toList, String ccList, String from, String subject, String body, String charset) throws EmailException {
        Email email = new SimpleEmail();

        // Set the charset if it was specified. Otherwise use the system's default.
        if (StringUtils.isNotBlank(charset)) {
            email.setCharset(charset);
        }

        email.setHostName(host);
        email.setSmtpPort(Integer.parseInt(port));
        email.setSocketConnectionTimeout(socketTimeout);

        if ("OAUTH".equals(authType)) {
            OAuthTokenManager tokenManager = new OAuthTokenManager(oAuthTokenEndpointUrl, oAuthClientId, oAuthClientSecret, oAuthScope);
            try {
                String accessToken = tokenManager.getAccessToken();
                email.setAuthentication(username, accessToken);
            } catch (Exception e) {
                throw new EmailException("Failed to obtain OAuth access token for SMTP authentication: " + e.getMessage(), e);
            } finally {
                tokenManager.shutdown();
            }
        } else if (useAuthentication) {
            email.setAuthentication(username, password);
        }

        if (StringUtils.equalsIgnoreCase(secure, "TLS")) {
            email.setStartTLSEnabled(true);
        } else if (StringUtils.equalsIgnoreCase(secure, "SSL")) {
            email.setSSLOnConnect(true);
            email.setSslSmtpPort(port);
        }

        // These have to be set after the authenticator, so that a new mail session isn't created
        ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
        Properties mailProperties = email.getMailSession().getProperties();
        mailProperties.setProperty("mail.smtp.ssl.protocols", StringUtils.join(MirthSSLUtil.getEnabledHttpsProtocols(configurationController.getHttpsClientProtocols()), ' '));
        mailProperties.setProperty("mail.smtp.ssl.ciphersuites", StringUtils.join(MirthSSLUtil.getEnabledHttpsCipherSuites(configurationController.getHttpsCipherSuites()), ' '));
        if ("OAUTH".equals(authType)) {
            mailProperties.setProperty("mail.smtp.auth.mechanisms", "XOAUTH2");
            mailProperties.setProperty("mail.smtp.sasl.enable", "true");
        }

        for (String to : StringUtils.split(toList, ",")) {
            email.addTo(to);
        }

        if (StringUtils.isNotEmpty(ccList)) {
            for (String cc : StringUtils.split(ccList, ",")) {
                email.addCc(cc);
            }
        }

        email.setFrom(from);
        email.setSubject(subject);
        email.setMsg(body);
        email.send();
    }

    public void send(String toList, String ccList, String from, String subject, String body) throws EmailException {
        send(toList, ccList, from, subject, body, null);
    }

    public void send(String toList, String ccList, String subject, String body) throws EmailException {
        send(toList, ccList, from, subject, body);
    }
}
