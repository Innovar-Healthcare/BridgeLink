/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.api.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Guards the samples/mock-smtp-connector cross-file invariants that CI cannot otherwise see (the
 * sample is deliberately not part of the server build):
 *
 * - every connector-panel field key in webadmin/webadmin.json names a real Java field of
 *   MockSmtpDispatcherProperties (the engine's channel deserialization strictly rejects unknown
 *   child elements, so a drifted key breaks every channel save);
 * - the manifest's transportName matches the connector metadata name in destination.xml;
 * - action endpoints live under the extension's own /extensions/<path>/ namespace.
 *
 * The whole class is skipped (JUnit assumption) when the samples directory is not present.
 */
public class MockSmtpSampleContractTest {

    private static final Pattern FIELD_PATTERN = Pattern.compile("private\\s+[\\w.<>\\[\\]]+\\s+(\\w+)\\s*;");

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private File sampleDir;

    @Before
    public void locateSample() {
        // Tests run with the server module as the working directory; the samples tree is a sibling
        File dir = new File("../samples/mock-smtp-connector");
        Assume.assumeTrue("samples/mock-smtp-connector not present — skipping", dir.isDirectory());
        sampleDir = dir;
    }

    @Test
    public void testManifestFieldKeysNameRealPropertiesFields() throws Exception {
        JsonNode manifest = objectMapper.readTree(FileUtils.readFileToString(new File(sampleDir, "webadmin/webadmin.json"), StandardCharsets.UTF_8));

        Set<String> manifestKeys = new HashSet<>();
        for (JsonNode panel : manifest.path("connectorPanels")) {
            for (JsonNode section : panel.path("sections")) {
                for (JsonNode field : section.path("fields")) {
                    manifestKeys.add(field.path("key").asText());
                }
            }
        }
        assertFalse("manifest declares no connector panel fields", manifestKeys.isEmpty());

        String source = FileUtils.readFileToString(new File(sampleDir, "shared/com/mirth/connect/connectors/mocksmtp/MockSmtpDispatcherProperties.java"), StandardCharsets.UTF_8);
        Set<String> javaFields = new HashSet<>();
        Matcher matcher = FIELD_PATTERN.matcher(source);
        while (matcher.find()) {
            javaFields.add(matcher.group(1));
        }

        for (String key : manifestKeys) {
            assertTrue("manifest field key \"" + key + "\" does not name a Java field of MockSmtpDispatcherProperties — channel save would reject it", javaFields.contains(key));
        }
    }

    @Test
    public void testManifestTransportNameMatchesConnectorMetadata() throws Exception {
        JsonNode manifest = objectMapper.readTree(FileUtils.readFileToString(new File(sampleDir, "webadmin/webadmin.json"), StandardCharsets.UTF_8));
        String destinationXml = FileUtils.readFileToString(new File(sampleDir, "destination.xml"), StandardCharsets.UTF_8);

        Matcher name = Pattern.compile("<name>([^<]+)</name>").matcher(destinationXml);
        assertTrue(name.find());

        for (JsonNode panel : manifest.path("connectorPanels")) {
            assertEquals("manifest transportName must match the destination.xml connector name", name.group(1), panel.path("transportName").asText());
        }
    }

    @Test
    public void testActionEndpointsUseExtensionNamespace() throws Exception {
        JsonNode manifest = objectMapper.readTree(FileUtils.readFileToString(new File(sampleDir, "webadmin/webadmin.json"), StandardCharsets.UTF_8));
        String destinationXml = FileUtils.readFileToString(new File(sampleDir, "destination.xml"), StandardCharsets.UTF_8);

        Matcher path = Pattern.compile("connectorMetaData path=\"([^\"]+)\"").matcher(destinationXml);
        assertTrue(path.find());
        String namespace = "/extensions/" + path.group(1) + "/";

        for (JsonNode panel : manifest.path("connectorPanels")) {
            for (JsonNode action : panel.path("actions")) {
                assertTrue("action endpoint must live under " + namespace, action.path("endpoint").asText().startsWith(namespace));
            }
        }
    }
}
