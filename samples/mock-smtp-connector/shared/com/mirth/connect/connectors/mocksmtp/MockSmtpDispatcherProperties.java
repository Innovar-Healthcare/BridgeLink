/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Sample extension for the declarative WebAdmin plugin UI (IRT-1422). The field names below MUST
 * exactly match the field keys declared in webadmin/webadmin.json — the engine's channel
 * deserialization is strict and rejects unknown child elements.
 */

package com.mirth.connect.connectors.mocksmtp;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;

import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorPropertiesInterface;
import com.mirth.connect.donkey.util.DonkeyElement;

public class MockSmtpDispatcherProperties extends ConnectorProperties implements DestinationConnectorPropertiesInterface {

    private DestinationConnectorProperties destinationConnectorProperties;

    private String smtpHost;
    private String smtpPort;
    private String encryption;
    private String timeout;
    private boolean authentication;
    private String username;
    private String password;
    private String to;
    private String from;
    private String subject;
    private boolean html;
    private String body;

    public MockSmtpDispatcherProperties() {
        destinationConnectorProperties = new DestinationConnectorProperties();

        this.smtpHost = "";
        this.smtpPort = "25";
        this.encryption = "none";
        this.timeout = "5000";
        this.authentication = false;
        this.username = "";
        this.password = "";
        this.to = "";
        this.from = "";
        this.subject = "";
        this.html = false;
        this.body = "";
    }

    public MockSmtpDispatcherProperties(MockSmtpDispatcherProperties props) {
        super(props);
        destinationConnectorProperties = new DestinationConnectorProperties(props.getDestinationConnectorProperties());

        smtpHost = props.getSmtpHost();
        smtpPort = props.getSmtpPort();
        encryption = props.getEncryption();
        timeout = props.getTimeout();
        authentication = props.isAuthentication();
        username = props.getUsername();
        password = props.getPassword();
        to = props.getTo();
        from = props.getFrom();
        subject = props.getSubject();
        html = props.isHtml();
        body = props.getBody();
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public String getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(String smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getEncryption() {
        return encryption;
    }

    public void setEncryption(String encryption) {
        this.encryption = encryption;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public boolean isAuthentication() {
        return authentication;
    }

    public void setAuthentication(boolean authentication) {
        this.authentication = authentication;
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

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public boolean isHtml() {
        return html;
    }

    public void setHtml(boolean html) {
        this.html = html;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public String getName() {
        return "Mock SMTP Sender";
    }

    @Override
    public String getProtocol() {
        return "mocksmtp";
    }

    @Override
    public String toFormattedString() {
        StringBuilder builder = new StringBuilder();
        String newLine = "\n";
        builder.append("SMTP HOST: ").append(smtpHost).append(":").append(smtpPort).append(newLine);
        builder.append("TO: ").append(to).append(newLine);
        builder.append("FROM: ").append(from).append(newLine);
        builder.append("SUBJECT: ").append(subject).append(newLine);
        builder.append(newLine);
        builder.append("[CONTENT]").append(newLine);
        builder.append(body);
        return builder.toString();
    }

    @Override
    public DestinationConnectorProperties getDestinationConnectorProperties() {
        return destinationConnectorProperties;
    }

    @Override
    public ConnectorProperties clone() {
        return new MockSmtpDispatcherProperties(this);
    }

    @Override
    public boolean canValidateResponse() {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}// @formatter:on

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purgedProperties = new HashMap<String, Object>();
        purgedProperties.put("destinationConnectorProperties", destinationConnectorProperties.getPurgedProperties());
        return purgedProperties;
    }
}
