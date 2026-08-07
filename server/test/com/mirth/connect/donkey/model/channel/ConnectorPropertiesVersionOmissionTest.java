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

package com.mirth.connect.donkey.model.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import com.mirth.connect.donkey.util.DonkeyElement;

/**
 * IRT-1516 verification (see {@code com.mirth.connect.model.MigratableVersionOmissionTest} for
 * the {@code Channel}/{@code Transformer}/{@code Filter}/{@code ChannelProperties} counterparts).
 * {@code DestinationConnectorProperties.migrate3_2_0} and
 * {@code SourceConnectorProperties.migrate3_2_0} unconditionally add a {@code <resourceIds>}
 * child instead of using {@code addChildElementIfNotExists} (which the very same classes use for
 * other fields, e.g. {@code migrate3_1_0}'s {@code validateResponse}/{@code processBatch}). Since
 * a missing {@code version} attribute defaults to "3.0.0" in
 * {@code com.mirth.connect.model.converters.MigratableConverter}, both {@code migrate3_2_0} and
 * the following {@code migrate3_4_0} always run together against modern (already-migrated)
 * {@code resourceIds} XML, corrupting it exactly like {@code ChannelProperties} does.
 */
public class ConnectorPropertiesVersionOmissionTest {

    // @formatter:off
    private static final String MODERN_RESOURCE_IDS_XML =
            "<properties>\n" +
            "  <resourceIds class=\"linked-hash-map\">\n" +
            "    <entry><string>Default Resource</string><string>[Default Resource]</string></entry>\n" +
            "  </resourceIds>\n" +
            "</properties>";
    // @formatter:on

    @Test
    public void testDestinationConnectorPropertiesMigrate3_2_0DuplicatesResourceIds() throws Exception {
        DonkeyElement element = new DonkeyElement(MODERN_RESOURCE_IDS_XML);
        DestinationConnectorProperties properties = new DestinationConnectorProperties();

        assertEquals(1, countChildrenNamed(element, "resourceIds"));

        properties.migrate3_2_0(element);

        assertEquals("migrate3_2_0 should be a no-op on the modern shape, but it added a second <resourceIds> sibling", 2, countChildrenNamed(element, "resourceIds"));
    }

    @Test
    public void testDestinationConnectorPropertiesMigrate3_2_0Then3_4_0CorruptsResourceIds() throws Exception {
        DonkeyElement element = new DonkeyElement(MODERN_RESOURCE_IDS_XML);
        DestinationConnectorProperties properties = new DestinationConnectorProperties();

        properties.migrate3_2_0(element);
        properties.migrate3_4_0(element);

        assertEquals("A duplicate <resourceIds> sibling should still be present after migrate3_4_0", 2, countChildrenNamed(element, "resourceIds"));

        String corruptedKey = element.getChildElement("resourceIds").getChildElement("entry").getChildElement("string").getTextContent();
        assertNotEquals("Default Resource", corruptedKey);
        assertEquals("Default Resource[Default Resource]", corruptedKey);
    }

    @Test
    public void testSourceConnectorPropertiesMigrate3_2_0DuplicatesResourceIds() throws Exception {
        DonkeyElement element = new DonkeyElement(MODERN_RESOURCE_IDS_XML);
        SourceConnectorProperties properties = new SourceConnectorProperties();

        assertEquals(1, countChildrenNamed(element, "resourceIds"));

        properties.migrate3_2_0(element);

        assertEquals("migrate3_2_0 should be a no-op on the modern shape, but it added a second <resourceIds> sibling", 2, countChildrenNamed(element, "resourceIds"));
    }

    @Test
    public void testSourceConnectorPropertiesMigrate3_2_0Then3_4_0CorruptsResourceIds() throws Exception {
        DonkeyElement element = new DonkeyElement(MODERN_RESOURCE_IDS_XML);
        SourceConnectorProperties properties = new SourceConnectorProperties();

        properties.migrate3_2_0(element);
        properties.migrate3_4_0(element);

        assertEquals("A duplicate <resourceIds> sibling should still be present after migrate3_4_0", 2, countChildrenNamed(element, "resourceIds"));

        String corruptedKey = element.getChildElement("resourceIds").getChildElement("entry").getChildElement("string").getTextContent();
        assertNotEquals("Default Resource", corruptedKey);
        assertEquals("Default Resource[Default Resource]", corruptedKey);
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
