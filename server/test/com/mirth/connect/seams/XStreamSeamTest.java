/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.seams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.Connector;
import com.mirth.connect.model.DashboardStatus;
import com.mirth.connect.model.Filter;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.Rule;
import com.mirth.connect.model.Step;
import com.mirth.connect.model.Transformer;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.plugins.mapper.MapperStep;
import com.mirth.connect.plugins.rulebuilder.RuleBuilderRule;

/**
 * Dependency-seam characterization suite (D-09/D-10/D-11) for the SHIPPED xstream 1.4.20 jars,
 * as pinned by the v26.6.0 xstream 1.4.21 rollback (commit 6a483ab9d). This suite pins the
 * BEHAVIOR of real BridgeLink usage of {@link ObjectXMLSerializer} &mdash; a real Channel object
 * graph round trip, a genuinely older-schema channel export migrating cleanly, a second
 * serializer-managed model used by dashboard/status payloads, and the XStream security-framework
 * allowlist rejecting non-allowlisted types &mdash; NOT byte-exact golden output or API-surface
 * reflection checks (D-10; those would have missed the MirthDomReader-class behavioral bug that
 * caused the v26.6.0 rollback in the first place).
 * <p>
 * Phase 23's xstream 1.4.21 re-land (with the MirthDomReader fix, commit 22940c0f8) MUST keep
 * this suite green UNCHANGED. A red result here after that upgrade is the exact signal the
 * rollback was trying to prevent.
 */
public class XStreamSeamTest {

    private static final String CHANNEL_ID = "9f1e6b2a-2b7a-4a7a-9c1a-9b8c6a2f1a11";
    private static final String CHANNEL_NAME = "XStream Seam Round Trip Channel";
    private static final String SOURCE_MAPPER_VARIABLE = "patientId";
    private static final String SOURCE_MAPPER_MAPPING = "msg['PID']['PID.3']['PID.3.1'].toString()";
    private static final String FILTER_RULE_FIELD = "tmp['patientId']";

    @BeforeClass
    public static void setup() throws Exception {
        try {
            ObjectXMLSerializer.getInstance().init(Version.getLatest().toString());
        } catch (Exception e) {
            // Ignore if it has already been initialized
        }
    }

    @Test
    public void channelObjectGraphRoundTrips() throws Exception {
        Channel channel = buildRealChannel();

        String xml = ObjectXMLSerializer.getInstance().serialize(channel);
        Channel deserialized = ObjectXMLSerializer.getInstance().deserialize(xml, Channel.class);

        assertFalse("round-tripping a real Channel graph must not produce an InvalidChannel", deserialized instanceof InvalidChannel);
        assertEquals(channel.getId(), deserialized.getId());
        assertEquals(channel.getName(), deserialized.getName());

        // Connector count: 1 source + 1 destination
        assertNotNull(deserialized.getSourceConnector());
        assertEquals(1, deserialized.getDestinationConnectors().size());
        assertEquals("Destination 1", deserialized.getDestinationConnectors().get(0).getName());

        // Transformer step content survives the round trip (field-level, not just getId)
        assertNotNull(deserialized.getSourceConnector().getTransformer());
        List<Step> steps = deserialized.getSourceConnector().getTransformer().getElements();
        assertEquals(1, steps.size());
        assertTrue("transformer step should deserialize back to a MapperStep", steps.get(0) instanceof MapperStep);
        MapperStep mapperStep = (MapperStep) steps.get(0);
        assertEquals(SOURCE_MAPPER_VARIABLE, mapperStep.getVariable());
        assertEquals(SOURCE_MAPPER_MAPPING, mapperStep.getMapping());

        // Filter rule content survives the round trip
        assertNotNull(deserialized.getSourceConnector().getFilter());
        List<Rule> rules = deserialized.getSourceConnector().getFilter().getElements();
        assertEquals(1, rules.size());
        assertTrue("filter rule should deserialize back to a RuleBuilderRule", rules.get(0) instanceof RuleBuilderRule);
        assertEquals(FILTER_RULE_FIELD, ((RuleBuilderRule) rules.get(0)).getField());
    }

    @Test
    public void knownOldFormatChannelXmlDeserializes() throws Exception {
        Channel result = ObjectXMLSerializer.getInstance().deserialize(OLD_FORMAT_CHANNEL_XML_3_6_0, Channel.class);

        assertFalse("a genuinely older-schema (3.6.0) channel export must migrate cleanly, not become an InvalidChannel", result instanceof InvalidChannel);
        assertEquals("da253473-28af-44b3-b2cc-16d6a4b7eaf5", result.getId());
        assertEquals("A", result.getName());
        assertNotNull("migrated channel must retain a source connector", result.getSourceConnector());
        assertEquals(1, result.getDestinationConnectors().size());
        assertEquals("Destination 1", result.getDestinationConnectors().get(0).getName());
    }

    @Test
    public void dashboardPayloadRoundTrips() throws Exception {
        DashboardStatus status = new DashboardStatus();
        status.setChannelId(CHANNEL_ID);
        status.setName(CHANNEL_NAME);
        status.setStatusType(DashboardStatus.StatusType.CHANNEL);
        status.setMetaDataId(0);
        status.setQueueEnabled(true);
        status.setQueued(42L);

        String xml = ObjectXMLSerializer.getInstance().serialize(status);
        DashboardStatus deserialized = ObjectXMLSerializer.getInstance().deserialize(xml, DashboardStatus.class);

        assertNotNull(deserialized);
        assertEquals(status.getChannelId(), deserialized.getChannelId());
        assertEquals(status.getName(), deserialized.getName());
        assertEquals(status.getStatusType(), deserialized.getStatusType());
        assertEquals(status.getMetaDataId(), deserialized.getMetaDataId());
        assertEquals(status.isQueueEnabled(), deserialized.isQueueEnabled());
        assertEquals(status.getQueued(), deserialized.getQueued());
    }

    @Test
    public void disallowedTypeIsRejected() throws Exception {
        testDisallowedType("java.lang.ProcessBuilder");
        testDisallowedType("java.lang.Thread");
        testDisallowedType("java.io.InputStream");
    }

    private void testDisallowedType(String className) throws Exception {
        try {
            // Should throw an exception -- the XStream security-framework allowlist (T-18-04)
            // rejects this type before it can be instantiated.
            ObjectXMLSerializer.getInstance().deserialize("<" + className + "/>", Class.forName(className));
            fail("Deserializing " + className + " should have failed, but didn't.");
        } catch (Exception ignore) {
        }
    }

    private Channel buildRealChannel() {
        Channel channel = new Channel();
        channel.setId(CHANNEL_ID);
        channel.setName(CHANNEL_NAME);
        channel.setRevision(1);

        Connector sourceConnector = new Connector();
        sourceConnector.setName("sourceConnector");
        sourceConnector.setTransportName("Channel Reader");
        sourceConnector.setMode(Connector.Mode.SOURCE);
        sourceConnector.setEnabled(true);

        Filter filter = new Filter();
        List<Rule> rules = new ArrayList<Rule>();
        RuleBuilderRule rule = new RuleBuilderRule();
        rule.setName("Patient ID Exists");
        rule.setField(FILTER_RULE_FIELD);
        rule.setCondition(RuleBuilderRule.Condition.EXISTS);
        rules.add(rule);
        filter.setElements(rules);
        sourceConnector.setFilter(filter);

        Transformer transformer = new Transformer();
        List<Step> steps = new ArrayList<Step>();
        MapperStep mapperStep = new MapperStep();
        mapperStep.setName("Extract Patient ID");
        mapperStep.setVariable(SOURCE_MAPPER_VARIABLE);
        mapperStep.setMapping(SOURCE_MAPPER_MAPPING);
        mapperStep.setScope(MapperStep.Scope.CHANNEL);
        steps.add(mapperStep);
        transformer.setElements(steps);
        sourceConnector.setTransformer(transformer);

        channel.setSourceConnector(sourceConnector);

        Connector destinationConnector = new Connector();
        destinationConnector.setName("Destination 1");
        destinationConnector.setTransportName("Channel Writer");
        destinationConnector.setMode(Connector.Mode.DESTINATION);
        destinationConnector.setEnabled(true);
        destinationConnector.setFilter(new Filter());
        destinationConnector.setTransformer(new Transformer());
        channel.addDestination(destinationConnector);

        return channel;
    }

    // Real channel export salvaged from server/test/com/mirth/connect/server/controllers/"Config
    // 1.xml" (channel "A", id da253473-28af-44b3-b2cc-16d6a4b7eaf5), a committed test fixture
    // authored 2018-04-20 at schema version 3.6.0 -- genuinely older than any version this suite
    // runs against (current schema version per Version.getLatest() is 26.6.0). Exercises the real
    // Channel.migrateX() chain end-to-end, the exact codepath class (MirthDomReader /
    // MigratableConverter) whose behavioral regression caused the v26.6.0 xstream rollback.
    // @formatter:off
    private static final String OLD_FORMAT_CHANNEL_XML_3_6_0 =
            "    <channel version=\"3.6.0\">\n" +
            "      <id>da253473-28af-44b3-b2cc-16d6a4b7eaf5</id>\n" +
            "      <nextMetaDataId>2</nextMetaDataId>\n" +
            "      <name>A</name>\n" +
            "      <description></description>\n" +
            "      <revision>1</revision>\n" +
            "      <sourceConnector version=\"3.6.0\">\n" +
            "        <metaDataId>0</metaDataId>\n" +
            "        <name>sourceConnector</name>\n" +
            "        <properties class=\"com.mirth.connect.connectors.vm.VmReceiverProperties\" version=\"3.6.0\">\n" +
            "          <pluginProperties/>\n" +
            "          <sourceConnectorProperties version=\"3.6.0\">\n" +
            "            <responseVariable>None</responseVariable>\n" +
            "            <respondAfterProcessing>true</respondAfterProcessing>\n" +
            "            <processBatch>false</processBatch>\n" +
            "            <firstResponse>false</firstResponse>\n" +
            "            <processingThreads>1</processingThreads>\n" +
            "            <resourceIds class=\"linked-hash-map\">\n" +
            "              <entry>\n" +
            "                <string>Default Resource</string>\n" +
            "                <string>[Default Resource]</string>\n" +
            "              </entry>\n" +
            "            </resourceIds>\n" +
            "            <queueBufferSize>1000</queueBufferSize>\n" +
            "          </sourceConnectorProperties>\n" +
            "        </properties>\n" +
            "        <transformer version=\"3.6.0\">\n" +
            "          <elements/>\n" +
            "          <inboundDataType>HL7V2</inboundDataType>\n" +
            "          <outboundDataType>HL7V2</outboundDataType>\n" +
            "          <inboundProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DataTypeProperties\" version=\"3.6.0\">\n" +
            "            <serializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2SerializationProperties\" version=\"3.6.0\">\n" +
            "              <handleRepetitions>true</handleRepetitions>\n" +
            "              <handleSubcomponents>true</handleSubcomponents>\n" +
            "              <useStrictParser>false</useStrictParser>\n" +
            "              <useStrictValidation>false</useStrictValidation>\n" +
            "              <stripNamespaces>true</stripNamespaces>\n" +
            "              <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "              <convertLineBreaks>true</convertLineBreaks>\n" +
            "            </serializationProperties>\n" +
            "            <deserializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DeserializationProperties\" version=\"3.6.0\">\n" +
            "              <useStrictParser>false</useStrictParser>\n" +
            "              <useStrictValidation>false</useStrictValidation>\n" +
            "              <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "            </deserializationProperties>\n" +
            "            <batchProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2BatchProperties\" version=\"3.6.0\">\n" +
            "              <splitType>MSH_Segment</splitType>\n" +
            "              <batchScript></batchScript>\n" +
            "            </batchProperties>\n" +
            "            <responseGenerationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseGenerationProperties\" version=\"3.6.0\">\n" +
            "              <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "              <successfulACKCode>AA</successfulACKCode>\n" +
            "              <successfulACKMessage></successfulACKMessage>\n" +
            "              <errorACKCode>AE</errorACKCode>\n" +
            "              <errorACKMessage>An Error Occurred Processing Message.</errorACKMessage>\n" +
            "              <rejectedACKCode>AR</rejectedACKCode>\n" +
            "              <rejectedACKMessage>Message Rejected.</rejectedACKMessage>\n" +
            "              <msh15ACKAccept>false</msh15ACKAccept>\n" +
            "              <dateFormat>yyyyMMddHHmmss.SSS</dateFormat>\n" +
            "            </responseGenerationProperties>\n" +
            "            <responseValidationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseValidationProperties\" version=\"3.6.0\">\n" +
            "              <successfulACKCode>AA,CA</successfulACKCode>\n" +
            "              <errorACKCode>AE,CE</errorACKCode>\n" +
            "              <rejectedACKCode>AR,CR</rejectedACKCode>\n" +
            "              <validateMessageControlId>true</validateMessageControlId>\n" +
            "              <originalMessageControlId>Destination_Encoded</originalMessageControlId>\n" +
            "              <originalIdMapVariable></originalIdMapVariable>\n" +
            "            </responseValidationProperties>\n" +
            "          </inboundProperties>\n" +
            "          <outboundProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DataTypeProperties\" version=\"3.6.0\">\n" +
            "            <serializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2SerializationProperties\" version=\"3.6.0\">\n" +
            "              <handleRepetitions>true</handleRepetitions>\n" +
            "              <handleSubcomponents>true</handleSubcomponents>\n" +
            "              <useStrictParser>false</useStrictParser>\n" +
            "              <useStrictValidation>false</useStrictValidation>\n" +
            "              <stripNamespaces>true</stripNamespaces>\n" +
            "              <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "              <convertLineBreaks>true</convertLineBreaks>\n" +
            "            </serializationProperties>\n" +
            "            <deserializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DeserializationProperties\" version=\"3.6.0\">\n" +
            "              <useStrictParser>false</useStrictParser>\n" +
            "              <useStrictValidation>false</useStrictValidation>\n" +
            "              <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "            </deserializationProperties>\n" +
            "            <batchProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2BatchProperties\" version=\"3.6.0\">\n" +
            "              <splitType>MSH_Segment</splitType>\n" +
            "              <batchScript></batchScript>\n" +
            "            </batchProperties>\n" +
            "            <responseGenerationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseGenerationProperties\" version=\"3.6.0\">\n" +
            "              <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "              <successfulACKCode>AA</successfulACKCode>\n" +
            "              <successfulACKMessage></successfulACKMessage>\n" +
            "              <errorACKCode>AE</errorACKCode>\n" +
            "              <errorACKMessage>An Error Occurred Processing Message.</errorACKMessage>\n" +
            "              <rejectedACKCode>AR</rejectedACKCode>\n" +
            "              <rejectedACKMessage>Message Rejected.</rejectedACKMessage>\n" +
            "              <msh15ACKAccept>false</msh15ACKAccept>\n" +
            "              <dateFormat>yyyyMMddHHmmss.SSS</dateFormat>\n" +
            "            </responseGenerationProperties>\n" +
            "            <responseValidationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseValidationProperties\" version=\"3.6.0\">\n" +
            "              <successfulACKCode>AA,CA</successfulACKCode>\n" +
            "              <errorACKCode>AE,CE</errorACKCode>\n" +
            "              <rejectedACKCode>AR,CR</rejectedACKCode>\n" +
            "              <validateMessageControlId>true</validateMessageControlId>\n" +
            "              <originalMessageControlId>Destination_Encoded</originalMessageControlId>\n" +
            "              <originalIdMapVariable></originalIdMapVariable>\n" +
            "            </responseValidationProperties>\n" +
            "          </outboundProperties>\n" +
            "        </transformer>\n" +
            "        <filter version=\"3.6.0\">\n" +
            "          <elements/>\n" +
            "        </filter>\n" +
            "        <transportName>Channel Reader</transportName>\n" +
            "        <mode>SOURCE</mode>\n" +
            "        <enabled>true</enabled>\n" +
            "        <waitForPrevious>true</waitForPrevious>\n" +
            "      </sourceConnector>\n" +
            "      <destinationConnectors>\n" +
            "        <connector version=\"3.6.0\">\n" +
            "          <metaDataId>1</metaDataId>\n" +
            "          <name>Destination 1</name>\n" +
            "          <properties class=\"com.mirth.connect.connectors.vm.VmDispatcherProperties\" version=\"3.6.0\">\n" +
            "            <pluginProperties/>\n" +
            "            <destinationConnectorProperties version=\"3.6.0\">\n" +
            "              <queueEnabled>false</queueEnabled>\n" +
            "              <sendFirst>false</sendFirst>\n" +
            "              <retryIntervalMillis>10000</retryIntervalMillis>\n" +
            "              <regenerateTemplate>false</regenerateTemplate>\n" +
            "              <retryCount>0</retryCount>\n" +
            "              <rotate>false</rotate>\n" +
            "              <includeFilterTransformer>false</includeFilterTransformer>\n" +
            "              <threadCount>1</threadCount>\n" +
            "              <threadAssignmentVariable></threadAssignmentVariable>\n" +
            "              <validateResponse>false</validateResponse>\n" +
            "              <resourceIds class=\"linked-hash-map\">\n" +
            "                <entry>\n" +
            "                  <string>Default Resource</string>\n" +
            "                  <string>[Default Resource]</string>\n" +
            "                </entry>\n" +
            "              </resourceIds>\n" +
            "              <queueBufferSize>1000</queueBufferSize>\n" +
            "              <reattachAttachments>true</reattachAttachments>\n" +
            "            </destinationConnectorProperties>\n" +
            "            <channelId>none</channelId>\n" +
            "            <channelTemplate>${message.encodedData}</channelTemplate>\n" +
            "            <mapVariables/>\n" +
            "          </properties>\n" +
            "          <transformer version=\"3.6.0\">\n" +
            "            <elements/>\n" +
            "            <inboundDataType>HL7V2</inboundDataType>\n" +
            "            <outboundDataType>HL7V2</outboundDataType>\n" +
            "            <inboundProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DataTypeProperties\" version=\"3.6.0\">\n" +
            "              <serializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2SerializationProperties\" version=\"3.6.0\">\n" +
            "                <handleRepetitions>true</handleRepetitions>\n" +
            "                <handleSubcomponents>true</handleSubcomponents>\n" +
            "                <useStrictParser>false</useStrictParser>\n" +
            "                <useStrictValidation>false</useStrictValidation>\n" +
            "                <stripNamespaces>true</stripNamespaces>\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "                <convertLineBreaks>true</convertLineBreaks>\n" +
            "              </serializationProperties>\n" +
            "              <deserializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DeserializationProperties\" version=\"3.6.0\">\n" +
            "                <useStrictParser>false</useStrictParser>\n" +
            "                <useStrictValidation>false</useStrictValidation>\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "              </deserializationProperties>\n" +
            "              <batchProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2BatchProperties\" version=\"3.6.0\">\n" +
            "                <splitType>MSH_Segment</splitType>\n" +
            "                <batchScript></batchScript>\n" +
            "              </batchProperties>\n" +
            "              <responseGenerationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseGenerationProperties\" version=\"3.6.0\">\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "                <successfulACKCode>AA</successfulACKCode>\n" +
            "                <successfulACKMessage></successfulACKMessage>\n" +
            "                <errorACKCode>AE</errorACKCode>\n" +
            "                <errorACKMessage>An Error Occurred Processing Message.</errorACKMessage>\n" +
            "                <rejectedACKCode>AR</rejectedACKCode>\n" +
            "                <rejectedACKMessage>Message Rejected.</rejectedACKMessage>\n" +
            "                <msh15ACKAccept>false</msh15ACKAccept>\n" +
            "                <dateFormat>yyyyMMddHHmmss.SSS</dateFormat>\n" +
            "              </responseGenerationProperties>\n" +
            "              <responseValidationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseValidationProperties\" version=\"3.6.0\">\n" +
            "                <successfulACKCode>AA,CA</successfulACKCode>\n" +
            "                <errorACKCode>AE,CE</errorACKCode>\n" +
            "                <rejectedACKCode>AR,CR</rejectedACKCode>\n" +
            "                <validateMessageControlId>true</validateMessageControlId>\n" +
            "                <originalMessageControlId>Destination_Encoded</originalMessageControlId>\n" +
            "                <originalIdMapVariable></originalIdMapVariable>\n" +
            "              </responseValidationProperties>\n" +
            "            </inboundProperties>\n" +
            "            <outboundProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DataTypeProperties\" version=\"3.6.0\">\n" +
            "              <serializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2SerializationProperties\" version=\"3.6.0\">\n" +
            "                <handleRepetitions>true</handleRepetitions>\n" +
            "                <handleSubcomponents>true</handleSubcomponents>\n" +
            "                <useStrictParser>false</useStrictParser>\n" +
            "                <useStrictValidation>false</useStrictValidation>\n" +
            "                <stripNamespaces>true</stripNamespaces>\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "                <convertLineBreaks>true</convertLineBreaks>\n" +
            "              </serializationProperties>\n" +
            "              <deserializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DeserializationProperties\" version=\"3.6.0\">\n" +
            "                <useStrictParser>false</useStrictParser>\n" +
            "                <useStrictValidation>false</useStrictValidation>\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "              </deserializationProperties>\n" +
            "              <batchProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2BatchProperties\" version=\"3.6.0\">\n" +
            "                <splitType>MSH_Segment</splitType>\n" +
            "                <batchScript></batchScript>\n" +
            "              </batchProperties>\n" +
            "              <responseGenerationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseGenerationProperties\" version=\"3.6.0\">\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "                <successfulACKCode>AA</successfulACKCode>\n" +
            "                <successfulACKMessage></successfulACKMessage>\n" +
            "                <errorACKCode>AE</errorACKCode>\n" +
            "                <errorACKMessage>An Error Occurred Processing Message.</errorACKMessage>\n" +
            "                <rejectedACKCode>AR</rejectedACKCode>\n" +
            "                <rejectedACKMessage>Message Rejected.</rejectedACKMessage>\n" +
            "                <msh15ACKAccept>false</msh15ACKAccept>\n" +
            "                <dateFormat>yyyyMMddHHmmss.SSS</dateFormat>\n" +
            "              </responseGenerationProperties>\n" +
            "              <responseValidationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseValidationProperties\" version=\"3.6.0\">\n" +
            "                <successfulACKCode>AA,CA</successfulACKCode>\n" +
            "                <errorACKCode>AE,CE</errorACKCode>\n" +
            "                <rejectedACKCode>AR,CR</rejectedACKCode>\n" +
            "                <validateMessageControlId>true</validateMessageControlId>\n" +
            "                <originalMessageControlId>Destination_Encoded</originalMessageControlId>\n" +
            "                <originalIdMapVariable></originalIdMapVariable>\n" +
            "              </responseValidationProperties>\n" +
            "            </outboundProperties>\n" +
            "          </transformer>\n" +
            "          <responseTransformer version=\"3.6.0\">\n" +
            "            <elements/>\n" +
            "            <inboundDataType>HL7V2</inboundDataType>\n" +
            "            <outboundDataType>HL7V2</outboundDataType>\n" +
            "            <inboundProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DataTypeProperties\" version=\"3.6.0\">\n" +
            "              <serializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2SerializationProperties\" version=\"3.6.0\">\n" +
            "                <handleRepetitions>true</handleRepetitions>\n" +
            "                <handleSubcomponents>true</handleSubcomponents>\n" +
            "                <useStrictParser>false</useStrictParser>\n" +
            "                <useStrictValidation>false</useStrictValidation>\n" +
            "                <stripNamespaces>true</stripNamespaces>\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "                <convertLineBreaks>true</convertLineBreaks>\n" +
            "              </serializationProperties>\n" +
            "              <deserializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DeserializationProperties\" version=\"3.6.0\">\n" +
            "                <useStrictParser>false</useStrictParser>\n" +
            "                <useStrictValidation>false</useStrictValidation>\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "              </deserializationProperties>\n" +
            "              <batchProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2BatchProperties\" version=\"3.6.0\">\n" +
            "                <splitType>MSH_Segment</splitType>\n" +
            "                <batchScript></batchScript>\n" +
            "              </batchProperties>\n" +
            "              <responseGenerationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseGenerationProperties\" version=\"3.6.0\">\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "                <successfulACKCode>AA</successfulACKCode>\n" +
            "                <successfulACKMessage></successfulACKMessage>\n" +
            "                <errorACKCode>AE</errorACKCode>\n" +
            "                <errorACKMessage>An Error Occurred Processing Message.</errorACKMessage>\n" +
            "                <rejectedACKCode>AR</rejectedACKCode>\n" +
            "                <rejectedACKMessage>Message Rejected.</rejectedACKMessage>\n" +
            "                <msh15ACKAccept>false</msh15ACKAccept>\n" +
            "                <dateFormat>yyyyMMddHHmmss.SSS</dateFormat>\n" +
            "              </responseGenerationProperties>\n" +
            "              <responseValidationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseValidationProperties\" version=\"3.6.0\">\n" +
            "                <successfulACKCode>AA,CA</successfulACKCode>\n" +
            "                <errorACKCode>AE,CE</errorACKCode>\n" +
            "                <rejectedACKCode>AR,CR</rejectedACKCode>\n" +
            "                <validateMessageControlId>true</validateMessageControlId>\n" +
            "                <originalMessageControlId>Destination_Encoded</originalMessageControlId>\n" +
            "                <originalIdMapVariable></originalIdMapVariable>\n" +
            "              </responseValidationProperties>\n" +
            "            </inboundProperties>\n" +
            "            <outboundProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DataTypeProperties\" version=\"3.6.0\">\n" +
            "              <serializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2SerializationProperties\" version=\"3.6.0\">\n" +
            "                <handleRepetitions>true</handleRepetitions>\n" +
            "                <handleSubcomponents>true</handleSubcomponents>\n" +
            "                <useStrictParser>false</useStrictParser>\n" +
            "                <useStrictValidation>false</useStrictValidation>\n" +
            "                <stripNamespaces>true</stripNamespaces>\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "                <convertLineBreaks>true</convertLineBreaks>\n" +
            "              </serializationProperties>\n" +
            "              <deserializationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2DeserializationProperties\" version=\"3.6.0\">\n" +
            "                <useStrictParser>false</useStrictParser>\n" +
            "                <useStrictValidation>false</useStrictValidation>\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "              </deserializationProperties>\n" +
            "              <batchProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2BatchProperties\" version=\"3.6.0\">\n" +
            "                <splitType>MSH_Segment</splitType>\n" +
            "                <batchScript></batchScript>\n" +
            "              </batchProperties>\n" +
            "              <responseGenerationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseGenerationProperties\" version=\"3.6.0\">\n" +
            "                <segmentDelimiter>\\r</segmentDelimiter>\n" +
            "                <successfulACKCode>AA</successfulACKCode>\n" +
            "                <successfulACKMessage></successfulACKMessage>\n" +
            "                <errorACKCode>AE</errorACKCode>\n" +
            "                <errorACKMessage>An Error Occurred Processing Message.</errorACKMessage>\n" +
            "                <rejectedACKCode>AR</rejectedACKCode>\n" +
            "                <rejectedACKMessage>Message Rejected.</rejectedACKMessage>\n" +
            "                <msh15ACKAccept>false</msh15ACKAccept>\n" +
            "                <dateFormat>yyyyMMddHHmmss.SSS</dateFormat>\n" +
            "              </responseGenerationProperties>\n" +
            "              <responseValidationProperties class=\"com.mirth.connect.plugins.datatypes.hl7v2.HL7v2ResponseValidationProperties\" version=\"3.6.0\">\n" +
            "                <successfulACKCode>AA,CA</successfulACKCode>\n" +
            "                <errorACKCode>AE,CE</errorACKCode>\n" +
            "                <rejectedACKCode>AR,CR</rejectedACKCode>\n" +
            "                <validateMessageControlId>true</validateMessageControlId>\n" +
            "                <originalMessageControlId>Destination_Encoded</originalMessageControlId>\n" +
            "                <originalIdMapVariable></originalIdMapVariable>\n" +
            "              </responseValidationProperties>\n" +
            "            </outboundProperties>\n" +
            "          </responseTransformer>\n" +
            "          <filter version=\"3.6.0\">\n" +
            "            <elements/>\n" +
            "          </filter>\n" +
            "          <transportName>Channel Writer</transportName>\n" +
            "          <mode>DESTINATION</mode>\n" +
            "          <enabled>true</enabled>\n" +
            "          <waitForPrevious>true</waitForPrevious>\n" +
            "        </connector>\n" +
            "      </destinationConnectors>\n" +
            "      <preprocessingScript>// Modify the message variable below to pre process data\n" +
            "return message;</preprocessingScript>\n" +
            "      <postprocessingScript>// This script executes once after a message has been processed\n" +
            "// Responses returned from here will be stored as &quot;Postprocessor&quot; in the response map\n" +
            "return;</postprocessingScript>\n" +
            "      <deployScript>// This script executes once when the channel is deployed\n" +
            "// You only have access to the globalMap and globalChannelMap here to persist data\n" +
            "return;</deployScript>\n" +
            "      <undeployScript>// This script executes once when the channel is undeployed\n" +
            "// You only have access to the globalMap and globalChannelMap here to persist data\n" +
            "return;</undeployScript>\n" +
            "      <properties version=\"3.6.0\">\n" +
            "        <clearGlobalChannelMap>true</clearGlobalChannelMap>\n" +
            "        <messageStorageMode>DEVELOPMENT</messageStorageMode>\n" +
            "        <encryptData>false</encryptData>\n" +
            "        <removeContentOnCompletion>false</removeContentOnCompletion>\n" +
            "        <removeOnlyFilteredOnCompletion>false</removeOnlyFilteredOnCompletion>\n" +
            "        <removeAttachmentsOnCompletion>false</removeAttachmentsOnCompletion>\n" +
            "        <initialState>STARTED</initialState>\n" +
            "        <storeAttachments>true</storeAttachments>\n" +
            "        <metaDataColumns>\n" +
            "          <metaDataColumn>\n" +
            "            <name>SOURCE</name>\n" +
            "            <type>STRING</type>\n" +
            "            <mappingName>mirth_source</mappingName>\n" +
            "          </metaDataColumn>\n" +
            "          <metaDataColumn>\n" +
            "            <name>TYPE</name>\n" +
            "            <type>STRING</type>\n" +
            "            <mappingName>mirth_type</mappingName>\n" +
            "          </metaDataColumn>\n" +
            "        </metaDataColumns>\n" +
            "        <attachmentProperties version=\"3.6.0\">\n" +
            "          <type>None</type>\n" +
            "          <properties/>\n" +
            "        </attachmentProperties>\n" +
            "        <resourceIds class=\"linked-hash-map\">\n" +
            "          <entry>\n" +
            "            <string>Default Resource</string>\n" +
            "            <string>[Default Resource]</string>\n" +
            "          </entry>\n" +
            "        </resourceIds>\n" +
            "      </properties>\n" +
            "      <exportData>\n" +
            "        <metadata>\n" +
            "          <enabled>true</enabled>\n" +
            "          <lastModified>\n" +
            "            <time>1524163143097</time>\n" +
            "            <timezone>America/Los_Angeles</timezone>\n" +
            "          </lastModified>\n" +
            "          <pruningSettings>\n" +
            "            <archiveEnabled>true</archiveEnabled>\n" +
            "          </pruningSettings>\n" +
            "        </metadata>\n" +
            "      </exportData>\n" +
            "    </channel>\n";
    // @formatter:on
}
