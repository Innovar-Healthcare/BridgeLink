/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.ws;

import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.donkey.model.event.ConnectionStatusEventType;
import com.mirth.connect.donkey.server.event.ConnectionStatusEvent;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

/*
 * Log the whole SOAP message
 */
public class LoggingSOAPHandler implements SOAPHandler<SOAPMessageContext> {

    private Logger logger = LogManager.getLogger(this.getClass());
    private EventController eventController = ControllerFactory.getFactory().createEventController();

    private WebServiceReceiver webServiceReceiver;

    public LoggingSOAPHandler(WebServiceReceiver webServiceReceiver) {
        this.webServiceReceiver = webServiceReceiver;
    }

    public Set<QName> getHeaders() {
        return null;
    }

    public void close(MessageContext mc) {
        logger.debug("Web Service connection closed.");
        eventController.dispatchEvent(new ConnectionStatusEvent(webServiceReceiver.getChannelId(), webServiceReceiver.getMetaDataId(), webServiceReceiver.getSourceName(), ConnectionStatusEventType.IDLE));
    }

    public boolean handleFault(SOAPMessageContext smc) {
        return true;
    }

    public boolean handleMessage(SOAPMessageContext smc) {
        try {
            Boolean outbound = (Boolean) smc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
            if (!outbound) {
                logger.debug("Web Service message received.");
                eventController.dispatchEvent(new ConnectionStatusEvent(webServiceReceiver.getChannelId(), webServiceReceiver.getMetaDataId(), webServiceReceiver.getSourceName(), ConnectionStatusEventType.CONNECTED));
            } else {
                logger.debug("Web Service returning response.");
            }
        } catch (Exception e) {
            // IRT-2428 follow-up (D-04): a pure-logging handler must never reverse jaxws-rt
            // message direction. Returning false here makes jaxws-rt reverse direction and echo
            // the client's own request back as a 200, with the channel never seeing the message.
            logger.error("Error handling SOAP message", e);
        }
        return true;
    }

}
