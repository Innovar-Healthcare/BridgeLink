/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * Sample extension for the declarative WebAdmin plugin UI (IRT-1422).
 */

package com.mirth.connect.connectors.mocksmtp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.event.ConnectionStatusEventType;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.DestinationConnector;
import com.mirth.connect.donkey.server.event.ConnectionStatusEvent;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.util.TemplateValueReplacer;

/**
 * Mock destination connector: deployable and startable like a real connector, but send() only
 * logs and always succeeds. No network I/O is performed.
 */
public class MockSmtpDispatcher extends DestinationConnector {

    private MockSmtpDispatcherProperties connectorProperties;
    private TemplateValueReplacer replacer = new TemplateValueReplacer();
    private EventController eventController = ControllerFactory.getFactory().createEventController();
    private Logger logger = LogManager.getLogger(getClass());

    @Override
    public void onDeploy() throws ConnectorTaskException {
        this.connectorProperties = (MockSmtpDispatcherProperties) getConnectorProperties();
    }

    @Override
    public void onUndeploy() throws ConnectorTaskException {}

    @Override
    public void onStart() throws ConnectorTaskException {
        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getDestinationName(), ConnectionStatusEventType.IDLE));
    }

    @Override
    public void onStop() throws ConnectorTaskException {
        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getDestinationName(), ConnectionStatusEventType.DISCONNECTED));
    }

    @Override
    public void onHalt() throws ConnectorTaskException {
        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getDestinationName(), ConnectionStatusEventType.DISCONNECTED));
    }

    @Override
    public void replaceConnectorProperties(ConnectorProperties connectorProperties, ConnectorMessage connectorMessage) {
        MockSmtpDispatcherProperties props = (MockSmtpDispatcherProperties) connectorProperties;

        props.setSmtpHost(replacer.replaceValues(props.getSmtpHost(), connectorMessage));
        props.setTo(replacer.replaceValues(props.getTo(), connectorMessage));
        props.setFrom(replacer.replaceValues(props.getFrom(), connectorMessage));
        props.setSubject(replacer.replaceValues(props.getSubject(), connectorMessage));
        props.setBody(replacer.replaceValues(props.getBody(), connectorMessage));
    }

    @Override
    public Response send(ConnectorProperties connectorProperties, ConnectorMessage message) {
        MockSmtpDispatcherProperties props = (MockSmtpDispatcherProperties) connectorProperties;

        eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getDestinationName(), ConnectionStatusEventType.SENDING, "Mock SMTP: " + props.getSmtpHost() + ":" + props.getSmtpPort()));

        try {
            logger.info("Mock SMTP send (channel " + getChannelId() + "): to=" + props.getTo() + ", from=" + props.getFrom() + ", subject=" + props.getSubject());
            return new Response(Status.SENT, null, "Mock SMTP: message accepted by " + props.getSmtpHost() + ":" + props.getSmtpPort());
        } finally {
            eventController.dispatchEvent(new ConnectionStatusEvent(getChannelId(), getMetaDataId(), getDestinationName(), ConnectionStatusEventType.IDLE));
        }
    }
}
