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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.SecurityContext;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

/**
 * Closes {@code 26.7-VERIFICATION.md} gap missing item 2 (the extension-servlet-JSON leg of
 * ROADMAP SC-3 and CONTEXT.md D-03). The round-trip runs entirely inside production code:
 * {@link ExtensionServlet}'s own private static {@code objectMapper} (ExtensionServlet.java:70)
 * performs BOTH the {@code readTree} of the on-disk {@code webadmin.json} (line 169) AND the
 * {@code writeValueAsString} of the assembled response tree (line 186). The test-side {@link
 * ObjectMapper} used here is only for parsing the returned {@link
 * com.mirth.connect.client.core.api.RawContent} for comparison -- it never performs the
 * round-trip itself.
 * <p>
 * The servlet's {@code objectMapper} is SRC-untuned per WR-01 and that stays out of scope here;
 * {@link #INTROSPECTION_FIXTURE_MANIFEST} is deliberately kept a few kilobytes, well below both
 * {@code WEBADMIN_MANIFEST_MAX_BYTES} (1 MB, above which the manifest is served as null) and
 * jackson 2.18.10's 20,000,000-character default {@code maxStringLength}. The manifest content
 * below is synthetic, non-clinical, and contains no PHI.
 */
public class JacksonExtensionManifestRoundTripTest extends ServletTestBase {

    private static final String PLUGIN_NAME = "Mock WebAdmin Plugin";
    private static final String EXTENSION_PATH = "mock-webadmin";
    private static final String PLUGIN_VERSION = "1.0.0";

    /**
     * A webadmin.json document shaped like a real manifest but chosen to stress the JSON shapes
     * the 2.18 introspection and number-handling rewrite could disturb: an integer, a nested
     * object three levels deep, an array of two objects, a unicode string (accented Latin, CJK,
     * emoji), a boolean true and false, an explicit JSON null leaf, a long value beyond {@code
     * Integer.MAX_VALUE}, a decimal with trailing significant digits, an empty object, an empty
     * array, a dotted key, and a dashed key.
     */
    private static final String INTROSPECTION_FIXTURE_MANIFEST = "{"
            + "\"manifestVersion\":1,"
            + "\"nested\":{\"level2\":{\"level3\":{\"leaf\":\"dummy-leaf-value\"}}},"
            + "\"items\":[{\"itemId\":1,\"itemName\":\"first\"},{\"itemId\":2,\"itemName\":\"second\"}],"
            + "\"unicodeText\":\"caf\\u00e9 \\u65e5\\u672c\\u8a9e \\ud83d\\ude00\","
            + "\"enabledFlag\":true,"
            + "\"disabledFlag\":false,"
            + "\"nullLeaf\":null,"
            + "\"largeLong\":9007199254740991,"
            + "\"preciseDecimal\":1.7500,"
            + "\"emptyObject\":{},"
            + "\"emptyArray\":[],"
            + "\"com.innovar.sample\":\"dummy-dotted-key-value\","
            + "\"display-name\":\"dummy-dashed-key-value\""
            + "}";

    private static ExtensionController mockExtensionController;
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File extensionsDir;
    private TestExtensionServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        mockExtensionController = mock(ExtensionController.class);
        when(controllerFactory.createExtensionController()).thenReturn(mockExtensionController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);
    }

    @Before
    public void beforeTest() throws Exception {
        reset(mockExtensionController);
        extensionsDir = tempFolder.newFolder("extensions");
        servlet = new TestExtensionServlet(request, mock(SecurityContext.class));

        writeManifest(EXTENSION_PATH, INTROSPECTION_FIXTURE_MANIFEST);
        stubEnabledPlugin(PLUGIN_NAME, EXTENSION_PATH, PLUGIN_VERSION);
    }

    @Test
    public void webAdminManifestRoundTripsContentStableThroughServletMapper() throws Exception {
        JsonNode actual = objectMapper.readTree(servlet.getWebAdminManifests().getContent());

        assertEquals("getWebAdminManifests must return exactly one entry", 1,
                actual.get("entries").size());
        JsonNode manifest = actual.get("entries").get(0).get("manifest");
        JsonNode expected = objectMapper.readTree(INTROSPECTION_FIXTURE_MANIFEST);

        // JsonNode equality is deep and value-typed: this single assertion fails if any leaf
        // changes type, precision, content ordering, or encoding through the production
        // read-then-write round-trip (ExtensionServlet.java:169 then :186).
        assertEquals("Manifest must round-trip content-stable through ExtensionServlet's own "
                + "static objectMapper (readTree then writeValueAsString)", expected, manifest);
    }

    @Test
    public void webAdminManifestNumericAndUnicodeFidelitySurvivesRoundTrip() throws Exception {
        JsonNode actual = objectMapper.readTree(servlet.getWebAdminManifests().getContent());
        JsonNode manifest = actual.get("entries").get(0).get("manifest");

        JsonNode largeLong = manifest.get("largeLong");
        assertTrue("largeLong must survive as a value convertible to long beyond "
                + "Integer.MAX_VALUE", largeLong.canConvertToLong());
        assertEquals("largeLong must round-trip to the exact long value", 9007199254740991L,
                largeLong.longValue());

        // The servlet's ObjectMapper is untuned per WR-01: DeserializationFeature
        // .USE_BIG_DECIMAL_FOR_FLOATS is disabled by default, so readTree binds a decimal
        // literal as a DoubleNode rather than a DecimalNode, and Double-based asText()
        // normalizes away trailing significant digits (1.7500 -> 1.75). This is the real,
        // falsifiable characterization of the untuned seam, not an assumption about what an
        // exact-decimal-preserving mapper would do.
        assertEquals("preciseDecimal round-trips through the untuned ObjectMapper's default "
                + "double-based number handling, which does not preserve trailing significant "
                + "digits", "1.75", manifest.get("preciseDecimal").asText());
        assertTrue("preciseDecimal must still round-trip as a numeric value, not a string",
                manifest.get("preciseDecimal").isNumber());

        assertEquals("unicodeText must round-trip byte-for-byte (accented Latin, CJK, emoji)",
                "café 日本語 😀", manifest.get("unicodeText").asText());

        assertTrue("enabledFlag must round-trip as a JSON boolean", manifest.get("enabledFlag").isBoolean());
        assertTrue("enabledFlag must round-trip as true", manifest.get("enabledFlag").booleanValue());
        assertTrue("disabledFlag must round-trip as a JSON boolean", manifest.get("disabledFlag").isBoolean());
        assertTrue("disabledFlag must round-trip as false", !manifest.get("disabledFlag").booleanValue());

        assertTrue("nullLeaf must round-trip as an explicit JSON null", manifest.get("nullLeaf").isNull());

        assertTrue("emptyObject must round-trip as an object", manifest.get("emptyObject").isObject());
        assertEquals("emptyObject must round-trip with zero fields", 0, manifest.get("emptyObject").size());

        assertTrue("emptyArray must round-trip as an array", manifest.get("emptyArray").isArray());
        assertEquals("emptyArray must round-trip with zero elements", 0, manifest.get("emptyArray").size());

        assertTrue("a dotted key must survive property-name introspection unmangled",
                manifest.has("com.innovar.sample"));
        assertTrue("a dashed key must survive property-name introspection unmangled",
                manifest.has("display-name"));
    }

    @Test
    public void webAdminManifestEntryEnvelopeFieldsBindThroughServletMapper() throws Exception {
        JsonNode actual = objectMapper.readTree(servlet.getWebAdminManifests().getContent());
        JsonNode entry = actual.get("entries").get(0);

        // Covers the ObjectNode WRITE half of the seam that getWebAdminManifests() itself
        // builds (ExtensionServlet.java:178-182), distinct from the manifest passthrough above.
        assertEquals("name must round-trip through the servlet's own ObjectNode envelope",
                PLUGIN_NAME, entry.get("name").asText());
        assertEquals("path must round-trip through the servlet's own ObjectNode envelope",
                EXTENSION_PATH, entry.get("path").asText());
        assertEquals("version must round-trip through the servlet's own ObjectNode envelope",
                PLUGIN_VERSION, entry.get("version").asText());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void stubEnabledPlugin(String name, String path, String version) {
        PluginMetaData pluginMetaData = new PluginMetaData();
        pluginMetaData.setName(name);
        pluginMetaData.setPath(path);
        pluginMetaData.setPluginVersion(version);

        Map<String, PluginMetaData> pluginMap = new LinkedHashMap<>();
        pluginMap.put(name, pluginMetaData);

        when(mockExtensionController.getPluginMetaData()).thenReturn(pluginMap);
        when(mockExtensionController.getConnectorMetaData()).thenReturn(new HashMap<>());
        when(mockExtensionController.isExtensionEnabled(name)).thenReturn(true);
    }

    private void writeManifest(String extensionPath, String content) throws Exception {
        File manifestFile = new File(extensionsDir, extensionPath + "/webadmin/webadmin.json");
        FileUtils.writeStringToFile(manifestFile, content, StandardCharsets.UTF_8);
    }

    /**
     * Bypasses login/authorization and points the extensions directory at the temp folder.
     * Replicated from {@link ExtensionWebAdminServletTest}'s identical inner class.
     */
    public class TestExtensionServlet extends ExtensionServlet {
        public TestExtensionServlet(HttpServletRequest request, SecurityContext sc) {
            super(request, sc);
        }

        @Override
        protected String getExtensionsPath() {
            return extensionsDir.getAbsolutePath();
        }

        @Override
        protected boolean isUserAuthorized() {
            return true;
        }

        @Override
        protected boolean isUserAuthorized(boolean audit) {
            return true;
        }
    }
}
