/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * Sample extension for the declarative WebAdmin plugin UI (IRT-1422).
 */

package com.mirth.connect.connectors.mocksmtp;

import java.awt.BorderLayout;

import javax.swing.JLabel;

import com.mirth.connect.client.ui.panels.connectors.ConnectorSettingsPanel;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;

/**
 * Minimal Swing panel so the Administrator can open channels using this connector without
 * errors. The connector is configured through the WebAdmin declarative panel; this panel simply
 * preserves whatever properties the channel already carries.
 */
public class MockSmtpConnectorPanel extends ConnectorSettingsPanel {

    private MockSmtpDispatcherProperties properties = new MockSmtpDispatcherProperties();

    public MockSmtpConnectorPanel() {
        setLayout(new BorderLayout());
        add(new JLabel("Mock SMTP Sender is configured through the BridgeLink Web Administrator."), BorderLayout.NORTH);
    }

    @Override
    public String getConnectorName() {
        return new MockSmtpDispatcherProperties().getName();
    }

    @Override
    public ConnectorProperties getProperties() {
        return properties;
    }

    @Override
    public void setProperties(ConnectorProperties properties) {
        this.properties = (MockSmtpDispatcherProperties) properties;
    }

    @Override
    public ConnectorProperties getDefaults() {
        return new MockSmtpDispatcherProperties();
    }

    @Override
    public boolean checkProperties(ConnectorProperties properties, boolean highlight) {
        return true;
    }

    @Override
    public void resetInvalidProperties() {}
}
