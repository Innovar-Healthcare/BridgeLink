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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.connectors.vm.VmDispatcherProperties;
import com.mirth.connect.connectors.vm.VmReceiverProperties;
import com.mirth.connect.donkey.util.xstream.SerializerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.ChannelExportData;
import com.mirth.connect.model.Connector;
import com.mirth.connect.model.Connector.Mode;
import com.mirth.connect.model.Filter;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.Transformer;

/**
 * IRT-1516 verification, real-API level (through {@code ObjectXMLSerializer.deserialize}, not
 * direct {@code migrateN()} calls — see {@code com.mirth.connect.model.MigratableVersionOmissionTest}
 * and {@code com.mirth.connect.donkey.model.channel.ConnectorPropertiesVersionOmissionTest} for
 * those). The IRT-1516 comment claimed "Core already re-stamps the current server version onto
 * every well-formed Migratable node at save time... omitting the version attribute round-trips
 * and is re-stamped". These tests build a CURRENT-shape {@link Channel} (and a standalone
 * {@link Transformer}) the way {@link ObjectXMLSerializer#serialize} produces them today, strip
 * the {@code version} attribute(s) the way a client that stopped stamping them would, and
 * deserialize with the real {@link ObjectXMLSerializer#deserialize} entry point used by
 * {@code createChannel}/{@code updateChannel}.
 */
public class ChannelVersionOmissionIntegrationTest {

    private static final Pattern VERSION_ATTRIBUTE = Pattern.compile(" version=\"[^\"]*\"");
    private static final Pattern ROOT_CHANNEL_TAG = Pattern.compile("^<channel version=\"[^\"]*\">");

    @BeforeClass
    public static void setup() throws Exception {
        try {
            ObjectXMLSerializer.getInstance().init(Version.getLatest().toString());
        } catch (Exception e) {
            // Already initialized by another test class
        }
    }

    /**
     * Transformer.class is NOT in {@code ImportConverter3_0_0.isMigratable()}, so a standalone
     * deserialize skips the legacy pre-3.0.0 pipeline entirely and goes straight through
     * {@code MigratableConverter}, which is the mechanism IRT-1516's comment was actually
     * describing. Unlike a {@code Channel}, there is no {@code ChannelConverter} catch-all around
     * a standalone {@code Transformer}, so the resulting exception propagates all the way to the
     * caller instead of being swallowed into an {@code InvalidChannel} placeholder.
     */
    @Test
    public void testStandaloneTransformerVersionlessThrowsToCaller() throws Exception {
        Transformer transformer = new Transformer();
        transformer.setInboundDataType("HL7V2");
        transformer.setOutboundDataType("HL7V2");

        String xml = ObjectXMLSerializer.getInstance().serialize(transformer);
        String stripped = VERSION_ATTRIBUTE.matcher(xml).replaceAll("");
        assertFalse("test fixture sanity check: XML should no longer contain a version attribute", stripped.contains("version="));

        try {
            ObjectXMLSerializer.getInstance().deserialize(stripped, Transformer.class);
            fail("Expected deserialize() to throw for a version-less modern-shape Transformer, but it returned normally");
        } catch (SerializerException e) {
            Throwable npe = rootCause(e);
            assertTrue("Expected the root cause to be the NullPointerException from Transformer.migrate3_0_1's unchecked getChildElement(\"steps\"), but was: " + npe, npe instanceof NullPointerException);
        }
    }

    @Test
    public void testStandaloneTransformerWithVersionDeserializesCleanly() throws Exception {
        Transformer transformer = new Transformer();
        transformer.setInboundDataType("HL7V2");
        transformer.setOutboundDataType("HL7V2");

        String xml = ObjectXMLSerializer.getInstance().serialize(transformer);
        Transformer result = (Transformer) ObjectXMLSerializer.getInstance().deserialize(xml, Transformer.class);

        assertEquals("HL7V2", result.getInboundDataType());
        assertEquals("HL7V2", result.getOutboundDataType());
        assertTrue(result.getElements().isEmpty());
    }

    /**
     * Strips the version attribute from EVERY element, including the root {@code <channel>} tag —
     * i.e. exactly what a client that "drops all client-side version stamping" (IRT-1663's
     * premise) would send. Because the root has no version attribute,
     * {@code ImportConverter3_0_0.migrate()} does NOT short-circuit (its only guard is
     * {@code element.hasAttribute("version")} on the root) and instead runs the ancient
     * pre-3.0.0 {@code ImportConverter.convertChannelString}/{@code migrateChannel} pipeline
     * against this modern-shape XML, which immediately NPEs because it expects 1.x/2.x XML
     * shapes that no longer exist. {@code ChannelConverter.unmarshal} catches this (and any
     * exception thrown anywhere in the subtree) and returns an {@link InvalidChannel} instead of
     * propagating it — so the deserialize() CALL doesn't throw, but the channel a client gets
     * back is a broken placeholder, not the channel it sent.
     */
    @Test
    public void testFullyVersionlessChannelBecomesInvalidChannelViaLegacyImportConverterPath() throws Exception {
        Channel channel = buildModernChannel();
        String xml = ObjectXMLSerializer.getInstance().serialize(channel);
        String stripped = VERSION_ATTRIBUTE.matcher(xml).replaceAll("");
        assertFalse("test fixture sanity check: XML should no longer contain a version attribute", stripped.contains("version="));

        Object result = ObjectXMLSerializer.getInstance().deserialize(stripped, Channel.class);

        assertTrue("Expected a version-less full Channel to deserialize as InvalidChannel, but got: " + result.getClass(), result instanceof InvalidChannel);
        Throwable cause = rootCause(((InvalidChannel) result).getCause());
        assertTrue("Expected the root cause to be the NPE from the legacy ImportConverter.convertChannel (pre-3.0.0 shape assumptions), but was: " + cause, cause instanceof NullPointerException);
    }

    /**
     * Keeps the root {@code <channel version="...">} attribute (so
     * {@code ImportConverter3_0_0.migrate()} short-circuits immediately, per its
     * {@code hasAttribute("version")} root-only check) but strips {@code version} from every
     * NESTED element. This isolates the second, independent bug: with no legacy pipeline
     * involved, {@code SourceConnectorProperties.migrate3_2_0} (defaulted to "3.0.0" like every
     * other nested node) still unconditionally adds a duplicate {@code <resourceIds>} sibling.
     * XStream's reflection unmarshalling then hits BOTH {@code <resourceIds>} elements for the
     * same {@code Map<String,String>} field — the second one is still in the old
     * {@code linked-hash-set}/{@code <string>} shape {@code migrate3_2_0} produces, so XStream
     * throws trying to convert a {@code LinkedHashSet} into a {@code Map}. This, too, is caught
     * by {@code ChannelConverter} and surfaces as an {@link InvalidChannel}, not a thrown
     * exception.
     */
    @Test
    public void testRootVersionPresentNestedVersionlessChannelBecomesInvalidChannelViaResourceIdsTypeMismatch() throws Exception {
        Channel channel = buildModernChannel();
        String xml = ObjectXMLSerializer.getInstance().serialize(channel);

        Matcher rootTagMatcher = ROOT_CHANNEL_TAG.matcher(xml);
        assertTrue("test fixture sanity check: expected xml to start with a versioned <channel> root tag", rootTagMatcher.find());
        String rootTag = rootTagMatcher.group();
        String rest = xml.substring(rootTag.length());
        String nestedVersionless = rootTag + VERSION_ATTRIBUTE.matcher(rest).replaceAll("");

        Object result = ObjectXMLSerializer.getInstance().deserialize(nestedVersionless, Channel.class);

        assertTrue("Expected a nested-version-less Channel (root version kept) to deserialize as InvalidChannel, but got: " + result.getClass(), result instanceof InvalidChannel);
        Throwable cause = ((InvalidChannel) result).getCause();
        assertTrue("Expected a ConversionException converting the duplicated <resourceIds> (Set vs Map), but was: " + cause, cause instanceof com.thoughtworks.xstream.converters.ConversionException);
        assertTrue(cause.getMessage().contains("resourceIds"));
    }

    @Test
    public void testFullChannelWithVersionDeserializesCleanly() throws Exception {
        Channel channel = buildModernChannel();
        String xml = ObjectXMLSerializer.getInstance().serialize(channel);

        Object result = ObjectXMLSerializer.getInstance().deserialize(xml, Channel.class);

        assertFalse("A fully-versioned channel should never come back as InvalidChannel", result instanceof InvalidChannel);
        Channel deserialized = (Channel) result;
        assertEquals(channel.getId(), deserialized.getId());
        assertEquals(channel.getName(), deserialized.getName());
        assertTrue(deserialized.getSourceConnector().getTransformer().getElements().isEmpty());
    }

    private static Channel buildModernChannel() {
        Channel channel = new Channel();
        channel.setId("test-channel-id");
        channel.setName("Test Channel");
        channel.setRevision(1);

        Connector source = new Connector("Source");
        source.setMode(Mode.SOURCE);
        source.setTransportName("Channel Reader");
        source.setProperties(new VmReceiverProperties());
        source.setTransformer(buildTransformer());
        source.setFilter(new Filter());
        source.setEnabled(true);
        channel.setSourceConnector(source);

        Connector destination = new Connector("Destination 1");
        destination.setMode(Mode.DESTINATION);
        destination.setTransportName("Channel Writer");
        destination.setProperties(new VmDispatcherProperties());
        destination.setTransformer(buildTransformer());
        destination.setFilter(new Filter());
        destination.setEnabled(true);
        channel.addDestination(destination);

        channel.setExportData(new ChannelExportData());

        return channel;
    }

    private static Transformer buildTransformer() {
        Transformer transformer = new Transformer();
        transformer.setInboundDataType("HL7V2");
        transformer.setOutboundDataType("HL7V2");
        return transformer;
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
