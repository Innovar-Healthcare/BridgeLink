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

package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.mirth.connect.donkey.util.DonkeyElement;

/**
 * IRT-1516 verification: {@code MigratableConverter.unmarshal} defaults a MISSING
 * {@code version} attribute to {@code "3.0.0"} and runs the full {@code migrateN()} chain
 * (see {@code com.mirth.connect.model.converters.MigratableConverter}, DEFAULT_VERSION). The
 * IRT-1516 comment claimed this "round-trips" safely for any well-formed Migratable node. These
 * tests call the individual {@code migrateN()} methods directly (same pattern as
 * {@link ChannelTest#testMigrate3_12_0}) against hand-built XML in the CURRENT (modern) element
 * shape to show several of them are not no-ops against that shape, contrary to the claim.
 */
public class MigratableVersionOmissionTest {

    // @formatter:off
    private static final String MODERN_TRANSFORMER_XML =
            "<transformer>\n" +
            "  <elements/>\n" +
            "  <inboundTemplate></inboundTemplate>\n" +
            "  <outboundTemplate></outboundTemplate>\n" +
            "  <inboundDataType>HL7V2</inboundDataType>\n" +
            "  <outboundDataType>HL7V2</outboundDataType>\n" +
            "</transformer>";

    private static final String MODERN_FILTER_XML =
            "<filter>\n" +
            "  <elements/>\n" +
            "</filter>";

    private static final String MODERN_CHANNEL_PROPERTIES_RESOURCE_IDS_XML =
            "<properties>\n" +
            "  <resourceIds class=\"linked-hash-map\">\n" +
            "    <entry><string>Default Resource</string><string>[Default Resource]</string></entry>\n" +
            "  </resourceIds>\n" +
            "</properties>";

    private static final String MODERN_CHANNEL_WITH_EXPORT_DATA_XML =
            "<channel>\n" +
            "  <properties/>\n" +
            "  <exportData>\n" +
            "    <metadata/>\n" +
            "  </exportData>\n" +
            "</channel>";
    // @formatter:on

    /**
     * Modern {@code <transformer>} XML has no {@code <steps>} child (it was replaced by
     * {@code <elements>} back in 3.5.0). {@code Transformer.migrate3_0_1} still unconditionally
     * calls {@code getChildElement("steps").getChildElements()} with no null check, so it NPEs
     * instead of round-tripping.
     */
    @Test
    public void testTransformerMigrate3_0_1ThrowsOnModernShape() throws Exception {
        DonkeyElement element = new DonkeyElement(MODERN_TRANSFORMER_XML);
        Transformer transformer = new Transformer();

        try {
            transformer.migrate3_0_1(element);
            fail("Expected a NullPointerException because modern <transformer> XML has no <steps> child");
        } catch (NullPointerException expected) {
            // confirmed: getChildElement("steps") returned null here
        }
    }

    /**
     * Same defect as above for {@code Filter.migrate3_0_1}: modern {@code <filter>} XML has no
     * {@code <rules>} child (replaced by {@code <elements>} in 3.5.0).
     */
    @Test
    public void testFilterMigrate3_0_1ThrowsOnModernShape() throws Exception {
        DonkeyElement element = new DonkeyElement(MODERN_FILTER_XML);
        Filter filter = new Filter();

        try {
            filter.migrate3_0_1(element);
            fail("Expected a NullPointerException because modern <filter> XML has no <rules> child");
        } catch (NullPointerException expected) {
            // confirmed: getChildElement("rules") returned null here
        }
    }

    /**
     * {@code ChannelProperties.migrate3_2_0} unconditionally does
     * {@code element.addChildElement("resourceIds")} instead of
     * {@code addChildElementIfNotExists}, even though the modern shape already has a
     * {@code <resourceIds>} element (a {@code Map<String,String>} field). This produces a
     * duplicate sibling instead of a no-op.
     */
    @Test
    public void testChannelPropertiesMigrate3_2_0DuplicatesResourceIds() throws Exception {
        DonkeyElement properties = new DonkeyElement(MODERN_CHANNEL_PROPERTIES_RESOURCE_IDS_XML);
        ChannelProperties channelProperties = new ChannelProperties();

        assertEquals(1, countChildrenNamed(properties, "resourceIds"));

        channelProperties.migrate3_2_0(properties);

        assertEquals("migrate3_2_0 should be a no-op on the modern shape, but it added a second <resourceIds> sibling", 2, countChildrenNamed(properties, "resourceIds"));
    }

    /**
     * Chains {@code migrate3_2_0} then {@code migrate3_4_0} on the SAME modern
     * {@code <properties>} node, exactly as {@code MigratableConverter.migrateElement} would when
     * a client omits {@code version} (defaulting to "3.0.0", which triggers every migrateN() up
     * through 4.6.0/26.3.0 in sequence). {@code migrate3_4_0} operates on
     * {@code getChildElement("resourceIds")}, which returns the FIRST (original, modern,
     * already-migrated) sibling — not the one migrate3_2_0 just added. It treats that node's
     * children as if they were the OLD flat {@code <string>} list shape, but they are actually
     * {@code <entry>} elements, so {@code getTextContent()} on an {@code <entry>} concatenates
     * both of its child {@code <string>} text nodes together with no separator. The result is
     * silent data corruption: the real key "Default Resource" is replaced by the garbage
     * concatenation "Default Resource[Default Resource]", and the value is lost (set to empty).
     * This does not throw, which is arguably worse than a crash: a version-less round trip
     * "succeeds" but silently corrupts the resourceIds mapping, and the codebase still has a
     * leftover second-class duplicate {@code <resourceIds>} that never gets cleaned up.
     */
    @Test
    public void testChannelPropertiesMigrate3_2_0Then3_4_0CorruptsResourceIdsAndLeavesDuplicate() throws Exception {
        DonkeyElement properties = new DonkeyElement(MODERN_CHANNEL_PROPERTIES_RESOURCE_IDS_XML);
        ChannelProperties channelProperties = new ChannelProperties();

        channelProperties.migrate3_2_0(properties);
        channelProperties.migrate3_4_0(properties);

        assertEquals("A duplicate <resourceIds> sibling should still be present after migrate3_4_0", 2, countChildrenNamed(properties, "resourceIds"));

        DonkeyElement survivingResourceIds = properties.getChildElement("resourceIds");
        String corruptedKey = survivingResourceIds.getChildElement("entry").getChildElement("string").getTextContent();

        assertNotEquals("The original resourceIds key should have survived migration unmodified", "Default Resource", corruptedKey);
        assertEquals("Default Resource[Default Resource]", corruptedKey);
    }

    /**
     * {@code Channel.migrate3_5_0} unconditionally does
     * {@code element.addChildElement("exportData")} (no existence check), even though every
     * modern {@code Channel} already has an {@code exportData} field/element. This produces a
     * duplicate {@code <exportData>} sibling instead of a no-op.
     */
    @Test
    public void testChannelMigrate3_5_0DuplicatesExportData() throws Exception {
        DonkeyElement element = new DonkeyElement(MODERN_CHANNEL_WITH_EXPORT_DATA_XML);
        Channel channel = new Channel();

        assertEquals(1, countChildrenNamed(element, "exportData"));

        channel.migrate3_5_0(element);

        assertEquals("migrate3_5_0 should be a no-op on the modern shape, but it added a second <exportData> sibling", 2, countChildrenNamed(element, "exportData"));
    }

    private static int countChildrenNamed(DonkeyElement parent, String name) {
        int count = 0;
        for (DonkeyElement child : parent.getChildElements()) {
            if (child.getNodeName().equals(name)) {
                count++;
            }
        }
        return count;
    }
}
