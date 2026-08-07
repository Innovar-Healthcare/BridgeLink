/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.model.converters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.connectors.dimse.DICOMDispatcherProperties;
import com.mirth.connect.connectors.dimse.DICOMReceiverProperties;
import com.mirth.connect.connectors.doc.DocumentDispatcherProperties;
import com.mirth.connect.connectors.file.FileDispatcherProperties;
import com.mirth.connect.connectors.file.FileReceiverProperties;
import com.mirth.connect.connectors.http.HttpDispatcherProperties;
import com.mirth.connect.connectors.jdbc.DatabaseDispatcherProperties;
import com.mirth.connect.connectors.jdbc.DatabaseReceiverProperties;
import com.mirth.connect.connectors.jms.JmsDispatcherProperties;
import com.mirth.connect.connectors.jms.JmsReceiverProperties;
import com.mirth.connect.connectors.js.JavaScriptDispatcherProperties;
import com.mirth.connect.connectors.js.JavaScriptReceiverProperties;
import com.mirth.connect.connectors.smtp.SmtpDispatcherProperties;
import com.mirth.connect.connectors.vm.VmDispatcherProperties;
import com.mirth.connect.connectors.vm.VmReceiverProperties;
import com.mirth.connect.connectors.ws.WebServiceDispatcherProperties;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ChannelExportData;
import com.mirth.connect.model.Connector;
import com.mirth.connect.model.Connector.Mode;
import com.mirth.connect.model.Filter;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.Transformer;

/**
 * IRT-1516/IRT-1663 contract test: WebAdmin plans to take the {@code GET
 * /connectors/{type}/defaults} response (a freshly-instantiated, version-stamped {@code
 * <properties>} fragment — see {@link com.mirth.connect.server.util.ConnectorPropertiesUtil}) and
 * embed it verbatim as a connector's {@code properties} element inside a channel it constructs
 * client-side, keeping the version attribute the endpoint stamped (per the version-omission
 * findings — see {@code com.mirth.connect.model.MigratableVersionOmissionTest} and
 * {@code ChannelVersionOmissionIntegrationTest} in this package — omitting version is unsafe).
 *
 * This test proves that contract for each of the 16 connector types IRT-1663 plans to adopt: a
 * freshly-instantiated instance of the SAME class {@code ConnectorPropertiesUtil} serializes
 * (i.e. exactly what the endpoint returns) round-trips cleanly when embedded in a full
 * {@link Channel} and put through the real {@code ObjectXMLSerializer.serialize}/{@code
 * deserialize} pair — not just in isolation.
 */
public class ConnectorDefaultsChannelEmbeddingTest {

    private static final Map<String, Class<? extends ConnectorProperties>> SOURCE_CONNECTORS = new LinkedHashMap<>();
    static {
        SOURCE_CONNECTORS.put("Channel Reader", VmReceiverProperties.class);
        SOURCE_CONNECTORS.put("Database Reader", DatabaseReceiverProperties.class);
        SOURCE_CONNECTORS.put("DICOM Listener", DICOMReceiverProperties.class);
        SOURCE_CONNECTORS.put("File Reader", FileReceiverProperties.class);
        SOURCE_CONNECTORS.put("JavaScript Reader", JavaScriptReceiverProperties.class);
        SOURCE_CONNECTORS.put("JMS Listener", JmsReceiverProperties.class);
    }

    private static final Map<String, Class<? extends ConnectorProperties>> DESTINATION_CONNECTORS = new LinkedHashMap<>();
    static {
        DESTINATION_CONNECTORS.put("Channel Writer", VmDispatcherProperties.class);
        DESTINATION_CONNECTORS.put("Database Writer", DatabaseDispatcherProperties.class);
        DESTINATION_CONNECTORS.put("DICOM Sender", DICOMDispatcherProperties.class);
        DESTINATION_CONNECTORS.put("Document Writer", DocumentDispatcherProperties.class);
        DESTINATION_CONNECTORS.put("File Writer", FileDispatcherProperties.class);
        DESTINATION_CONNECTORS.put("HTTP Sender", HttpDispatcherProperties.class);
        DESTINATION_CONNECTORS.put("JavaScript Writer", JavaScriptDispatcherProperties.class);
        DESTINATION_CONNECTORS.put("JMS Sender", JmsDispatcherProperties.class);
        DESTINATION_CONNECTORS.put("SMTP Sender", SmtpDispatcherProperties.class);
        DESTINATION_CONNECTORS.put("Web Service Sender", WebServiceDispatcherProperties.class);
    }

    @BeforeClass
    public static void setup() throws Exception {
        try {
            ObjectXMLSerializer.getInstance().init(Version.getLatest().toString());
        } catch (Exception e) {
            // Already initialized by another test class
        }
    }

    @Test
    public void testEachAdoptableSourceConnectorEmbedsCleanlyInAFullChannel() throws Exception {
        for (Map.Entry<String, Class<? extends ConnectorProperties>> entry : SOURCE_CONNECTORS.entrySet()) {
            String type = entry.getKey();
            ConnectorProperties properties = entry.getValue().getDeclaredConstructor().newInstance();

            Channel channel = buildChannelSkeleton(type);
            Connector source = new Connector(type);
            source.setMode(Mode.SOURCE);
            source.setTransportName(type);
            source.setProperties(properties);
            source.setTransformer(buildTransformer());
            source.setFilter(new Filter());
            source.setEnabled(true);
            channel.setSourceConnector(source);

            assertChannelRoundTripsCleanly(type, channel, entry.getValue());
        }
    }

    @Test
    public void testEachAdoptableDestinationConnectorEmbedsCleanlyInAFullChannel() throws Exception {
        for (Map.Entry<String, Class<? extends ConnectorProperties>> entry : DESTINATION_CONNECTORS.entrySet()) {
            String type = entry.getKey();
            ConnectorProperties properties = entry.getValue().getDeclaredConstructor().newInstance();

            Channel channel = buildChannelSkeleton(type);
            Connector source = new Connector("Source");
            source.setMode(Mode.SOURCE);
            source.setTransportName("Channel Reader");
            source.setProperties(new VmReceiverProperties());
            source.setTransformer(buildTransformer());
            source.setFilter(new Filter());
            source.setEnabled(true);
            channel.setSourceConnector(source);

            Connector destination = new Connector(type);
            destination.setMode(Mode.DESTINATION);
            destination.setTransportName(type);
            destination.setProperties(properties);
            destination.setTransformer(buildTransformer());
            destination.setFilter(new Filter());
            destination.setEnabled(true);
            channel.addDestination(destination);

            assertChannelRoundTripsCleanly(type, channel, entry.getValue());
        }
    }

    private static void assertChannelRoundTripsCleanly(String connectorType, Channel channel, Class<? extends ConnectorProperties> expectedPropertiesClass) throws Exception {
        String xml = ObjectXMLSerializer.getInstance().serialize(channel);
        assertTrue(connectorType + ": fixture sanity check - expected the connector's properties class in the serialized XML", xml.contains("class=\"" + expectedPropertiesClass.getName() + "\""));

        Object result = ObjectXMLSerializer.getInstance().deserialize(xml, Channel.class);

        assertFalse(connectorType + ": channel embedding this connector's default properties came back as InvalidChannel instead of a real Channel" + describeInvalidChannelCause(result), result instanceof InvalidChannel);
    }

    private static String describeInvalidChannelCause(Object result) {
        if (!(result instanceof InvalidChannel)) {
            return "";
        }
        Throwable cause = ((InvalidChannel) result).getCause();
        return " (cause: " + cause + ")";
    }

    private static Channel buildChannelSkeleton(String connectorType) {
        Channel channel = new Channel();
        channel.setId("test-channel-" + connectorType.replaceAll("\\s+", "-").toLowerCase());
        channel.setName("Test Channel - " + connectorType);
        channel.setRevision(1);
        channel.setExportData(new ChannelExportData());
        return channel;
    }

    private static Transformer buildTransformer() {
        Transformer transformer = new Transformer();
        transformer.setInboundDataType("HL7V2");
        transformer.setOutboundDataType("HL7V2");
        return transformer;
    }
}
