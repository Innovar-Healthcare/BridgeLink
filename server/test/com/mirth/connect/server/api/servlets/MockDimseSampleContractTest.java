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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
 * Guards the samples/mock-dimse-datatype cross-file invariants that CI cannot otherwise see (the
 * sample is deliberately not part of the server build):
 *
 * - every propertyGroups field key in webadmin/webadmin.json names a real Java field of the
 *   sample's matching group properties class (the engine's channel deserialization strictly
 *   rejects unknown child elements, so a drifted key breaks every channel save);
 * - the manifest's dataTypes name matches the delegate's plugin point name (the Endpoint 3 URL
 *   segment and the data type registry key);
 * - plugin.xml registers the data type server plugin in serverClasses (the endpoint's
 *   own-classes-only check matches on it);
 * - the MockDIMSE classes on the test classpath (used by ExtensionWebAdminDataTypeDefaultsTest to
 *   validate the frozen contract fixture) are byte-identical mirrors of the sample's sources.
 *
 * The whole class is skipped (JUnit assumption) when the samples directory is not present.
 */
public class MockDimseSampleContractTest {

    private static final Pattern FIELD_PATTERN = Pattern.compile("private\\s+[\\w.<>\\[\\]]+\\s+(\\w+)\\s*(?:=[^;]*)?;");

    private static final String SAMPLE_PACKAGE_DIR = "com/innovarhealthcare/connect/plugins/mockdimse";

    private static final Map<String, String> GROUP_CLASS_FILES = new HashMap<>();
    static {
        GROUP_CLASS_FILES.put("serialization", "MockDIMSESerializationProperties.java");
        GROUP_CLASS_FILES.put("deserialization", "MockDIMSEDeserializationProperties.java");
        GROUP_CLASS_FILES.put("batch", "MockDIMSEBatchProperties.java");
        GROUP_CLASS_FILES.put("responseGeneration", "MockDIMSEResponseGenerationProperties.java");
        GROUP_CLASS_FILES.put("responseValidation", "MockDIMSEResponseValidationProperties.java");
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private File sampleDir;

    @Before
    public void locateSample() {
        // Tests run with the server module as the working directory; the samples tree is a sibling
        File dir = new File("../samples/mock-dimse-datatype");
        Assume.assumeTrue("samples/mock-dimse-datatype not present — skipping", dir.isDirectory());
        sampleDir = dir;
    }

    @Test
    public void testManifestFieldKeysNameRealGroupFields() throws Exception {
        JsonNode manifest = objectMapper.readTree(FileUtils.readFileToString(new File(sampleDir, "webadmin/webadmin.json"), StandardCharsets.UTF_8));

        int checkedFields = 0;
        for (JsonNode dataType : manifest.path("dataTypes")) {
            for (JsonNode propertyGroup : dataType.path("propertyGroups")) {
                String group = propertyGroup.path("group").asText();
                String classFile = GROUP_CLASS_FILES.get(group);
                assertTrue("manifest declares unknown property group \"" + group + "\"", classFile != null);

                String source = FileUtils.readFileToString(new File(sampleDir, "shared/" + SAMPLE_PACKAGE_DIR + "/" + classFile), StandardCharsets.UTF_8);
                Set<String> javaFields = new HashSet<>();
                Matcher matcher = FIELD_PATTERN.matcher(source);
                while (matcher.find()) {
                    javaFields.add(matcher.group(1));
                }

                for (JsonNode field : propertyGroup.path("fields")) {
                    String key = field.path("key").asText();
                    assertTrue("manifest field key \"" + key + "\" does not name a Java field of " + classFile + " — channel save would reject it", javaFields.contains(key));
                    checkedFields++;
                }
            }
        }
        assertFalse("manifest declares no data type property group fields", checkedFields == 0);
    }

    @Test
    public void testManifestDataTypeNameMatchesPluginPointName() throws Exception {
        JsonNode manifest = objectMapper.readTree(FileUtils.readFileToString(new File(sampleDir, "webadmin/webadmin.json"), StandardCharsets.UTF_8));
        String delegateSource = FileUtils.readFileToString(new File(sampleDir, "shared/" + SAMPLE_PACKAGE_DIR + "/MockDIMSEDataTypeDelegate.java"), StandardCharsets.UTF_8);

        Matcher name = Pattern.compile("getName\\(\\)\\s*\\{\\s*return\\s+\"([^\"]+)\"").matcher(delegateSource);
        assertTrue("could not locate the delegate's getName() literal", name.find());

        for (JsonNode dataType : manifest.path("dataTypes")) {
            assertEquals("manifest dataTypes name must match the delegate's plugin point name", name.group(1), dataType.path("name").asText());
        }
    }

    @Test
    public void testPluginXmlRegistersServerPlugin() throws Exception {
        String pluginXml = FileUtils.readFileToString(new File(sampleDir, "plugin.xml"), StandardCharsets.UTF_8);
        assertTrue("plugin.xml must register the data type server plugin in serverClasses — the endpoint's own-classes-only check matches on it",
                pluginXml.contains("<string>com.innovarhealthcare.connect.plugins.mockdimse.MockDIMSEDataTypeServerPlugin</string>"));
    }

    @Test
    public void testTestClasspathMirrorsSampleSources() throws Exception {
        // ExtensionWebAdminDataTypeDefaultsTest validates the frozen fixture against the compiled
        // test-tree copies of these classes; if the sample drifts, the fixture check goes blind
        File testTreeDir = new File("test/" + SAMPLE_PACKAGE_DIR);
        int compared = 0;

        for (String sourceRoot : new String[] { "shared", "server" }) {
            File samplePackageDir = new File(sampleDir, sourceRoot + "/" + SAMPLE_PACKAGE_DIR);
            for (File sampleFile : samplePackageDir.listFiles((d, fileName) -> fileName.endsWith(".java"))) {
                File mirrorFile = new File(testTreeDir, sampleFile.getName());
                assertTrue("no test-tree mirror for sample class " + sampleFile.getName(), mirrorFile.isFile());
                assertTrue("test-tree mirror of " + sampleFile.getName() + " differs from the sample source — keep the copies byte-identical",
                        FileUtils.contentEquals(sampleFile, mirrorFile));
                compared++;
            }
        }
        assertFalse("no sample sources found to compare", compared == 0);
    }
}
