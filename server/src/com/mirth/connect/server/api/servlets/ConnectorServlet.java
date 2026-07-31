/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) NextGen Healthcare. All rights reserved.
 * https://www.nextgen.com/products-and-services/integration-engine
 *
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.api.servlets;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.apache.commons.lang3.StringUtils;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.client.core.api.RawContent;
import com.mirth.connect.client.core.api.servlets.ConnectorServletInterface;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;
import com.mirth.connect.server.util.ConnectorPropertiesUtil;

public class ConnectorServlet extends MirthServlet implements ConnectorServletInterface {

    private static final ExtensionController extensionController = ControllerFactory.getFactory().createExtensionController();

    public ConnectorServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc);
    }

    @Override
    public Response defaults(String type) {
        if (StringUtils.isBlank(type)) {
            throw badRequest("A connector type is required.");
        }

        // The type is the connector's transport name, e.g. "HTTP Sender" (the same key the desktop
        // client and the extension-scoped webadmin defaults endpoint use).
        ConnectorMetaData connectorMetaData = extensionController.getConnectorMetaDataByTransportName(type);
        if (connectorMetaData == null || !extensionController.isExtensionEnabled(connectorMetaData.getName())) {
            throw new MirthApiException(Status.NOT_FOUND);
        }

        try {
            Class<?> sharedClass = Class.forName(connectorMetaData.getSharedClassName());
            if (!ConnectorProperties.class.isAssignableFrom(sharedClass)) {
                throw new MirthApiException(Status.NOT_FOUND);
            }
            ConnectorProperties instance = (ConnectorProperties) sharedClass.getDeclaredConstructor().newInstance();
            /*
             * The explicit media type pins the response to application/xml even when the client sent
             * Accept: application/json (which the method's Produces admits to avoid a 406).
             */
            RawContent body = new RawContent(ConnectorPropertiesUtil.toConnectorPropertiesXml(instance));
            return Response.ok(body, MediaType.APPLICATION_XML_TYPE).build();
        } catch (MirthApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MirthApiException(e);
        }
    }

    private static MirthApiException badRequest(String message) {
        return new MirthApiException(Response.status(Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity(message).build());
    }
}
