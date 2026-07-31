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

package com.mirth.connect.server.util;

import java.io.StringWriter;
import java.util.HashSet;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.mirth.connect.donkey.model.channel.ConnectorPluginProperties;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorPropertiesInterface;
import com.mirth.connect.donkey.model.channel.SourceConnectorProperties;
import com.mirth.connect.donkey.model.channel.SourceConnectorPropertiesInterface;
import com.mirth.connect.donkey.server.Constants;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.controllers.ControllerFactory;

/**
 * Serializes freshly instantiated connector properties into the standalone {@code <properties>} form
 * used inside saved channel XML. Extracted from ExtensionServlet so both the extension-scoped
 * webadmin defaults endpoint and the built-in connector defaults endpoint share one implementation.
 */
public class ConnectorPropertiesUtil {

    private ConnectorPropertiesUtil() {}

    /**
     * Serializes freshly instantiated connector properties into the same form they take when
     * embedded in a channel: a root <properties> element carrying class and version attributes.
     */
    public static String toConnectorPropertiesXml(ConnectorProperties properties) throws Exception {
        /*
         * Client-created channels always carry a pluginProperties element (the Swing client sets
         * an empty set); serialize the same form so defaults match saved channel XML.
         */
        if (properties.getPluginProperties() == null) {
            properties.setPluginProperties(new HashSet<ConnectorPluginProperties>());
        }

        /*
         * The Swing client replaces a zero queue buffer size with the server's configured default
         * before displaying defaults (ConnectorPanel); do the same so served defaults match.
         */
        if (properties instanceof SourceConnectorPropertiesInterface) {
            SourceConnectorProperties sourceProperties = ((SourceConnectorPropertiesInterface) properties).getSourceConnectorProperties();
            if (sourceProperties != null && sourceProperties.getQueueBufferSize() <= 0) {
                sourceProperties.setQueueBufferSize(getDefaultQueueBufferSize());
            }
        }
        if (properties instanceof DestinationConnectorPropertiesInterface) {
            DestinationConnectorProperties destinationProperties = ((DestinationConnectorPropertiesInterface) properties).getDestinationConnectorProperties();
            if (destinationProperties != null && destinationProperties.getQueueBufferSize() <= 0) {
                destinationProperties.setQueueBufferSize(getDefaultQueueBufferSize());
            }
        }

        String xml = ObjectXMLSerializer.getInstance().serialize(properties);
        DonkeyElement element = new DonkeyElement(xml);
        // The standalone root node name is exactly what XStream emits as the class attribute
        element.setAttribute("class", element.getNodeName());
        element.setNodeName("properties");
        stripStructuralWhitespace(element.getElement());

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(element.getElement()), new StreamResult(writer));
        return writer.toString();
    }

    private static int getDefaultQueueBufferSize() {
        try {
            Integer queueBufferSize = ControllerFactory.getFactory().createConfigurationController().getServerSettings().getQueueBufferSize();
            if (queueBufferSize != null && queueBufferSize > 0) {
                return queueBufferSize;
            }
        } catch (Exception e) {
            // Fall through to the donkey default
        }
        return Constants.DEFAULT_QUEUE_BUFFER_SIZE;
    }

    private static void stripStructuralWhitespace(Node node) {
        boolean hasElementChild = false;
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                hasElementChild = true;
                break;
            }
        }

        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && hasElementChild && StringUtils.isBlank(child.getNodeValue())) {
                node.removeChild(child);
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                stripStructuralWhitespace(child);
            }
        }
    }
}
