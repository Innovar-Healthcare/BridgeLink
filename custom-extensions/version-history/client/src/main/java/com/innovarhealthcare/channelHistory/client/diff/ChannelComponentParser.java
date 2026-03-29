/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.diff;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;

/**
 * Parses a Mirth Connect channel XML into its structural components for diff display.
 *
 * Extracts:
 *   - Channel Info: direct-child &lt;name&gt;, &lt;description&gt;, &lt;revision&gt;
 *   - Source Connector: &lt;sourceConnector&gt; block + &lt;transportName&gt;
 *   - Destination Connectors: each &lt;connector&gt; under &lt;destinationConnectors&gt;,
 *       keyed by &lt;name&gt; with &lt;transportName&gt; as display label
 */
public class ChannelComponentParser {

    public static ChannelComponents parse(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return new ChannelComponents("", null, new LinkedHashMap<>());
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entity resolution to prevent XXE
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();
            Element root = doc.getDocumentElement();

            // ── Channel Info ─────────────────────────────────────────────────
            String name        = directChildText(root, "name");
            String description = directChildText(root, "description");
            String revision    = directChildText(root, "revision");

            StringBuilder info = new StringBuilder();
            if (!name.isEmpty())        info.append("Name: ").append(name);
            if (!description.isEmpty()) appendLine(info, "Description: " + description);
            if (!revision.isEmpty())    appendLine(info, "Revision: "    + revision);

            // ── Source Connector ─────────────────────────────────────────────
            ChannelConnector sourceConnector = null;
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE
                        && "sourceConnector".equals(child.getNodeName())) {
                    Element srcElem     = (Element) child;
                    String transportName = deepChildText(srcElem, "transportName");
                    String srcXml        = nodeToString(srcElem);
                    sourceConnector = new ChannelConnector(transportName, srcXml);
                    break;
                }
            }

            // ── Destination Connectors ────────────────────────────────────────
            LinkedHashMap<String, ChannelConnector> destinations = new LinkedHashMap<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE
                        && "destinationConnectors".equals(child.getNodeName())) {
                    NodeList connectors = child.getChildNodes();
                    for (int j = 0; j < connectors.getLength(); j++) {
                        Node conn = connectors.item(j);
                        if (conn.getNodeType() == Node.ELEMENT_NODE
                                && "connector".equals(conn.getNodeName())) {
                            Element connElem     = (Element) conn;
                            String connName      = deepChildText(connElem, "name");
                            String transportName = deepChildText(connElem, "transportName");
                            String connXml       = nodeToString(connElem);
                            if (!connName.isEmpty()) {
                                destinations.put(connName,
                                        new ChannelConnector(transportName, connXml));
                            }
                        }
                    }
                    break;
                }
            }

            return new ChannelComponents(info.toString(), sourceConnector, destinations);

        } catch (Exception e) {
            return new ChannelComponents("(parse error: " + e.getMessage() + ")",
                    null, new LinkedHashMap<>());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns text content of the first direct child element matching {@code tagName}. */
    private static String directChildText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && tagName.equals(child.getNodeName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }

    /** Returns text content of the first descendant element matching {@code tagName}. */
    private static String deepChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return "";
        return nodes.item(0).getTextContent().trim();
    }

    /** Serialises a DOM node to an indented XML string (no XML declaration). */
    private static String nodeToString(Node node) throws Exception {
        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString().trim();
    }

    private static void appendLine(StringBuilder sb, String text) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(text);
    }
}
