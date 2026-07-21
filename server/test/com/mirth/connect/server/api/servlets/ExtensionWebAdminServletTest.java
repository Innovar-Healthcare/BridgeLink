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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

/**
 * Tests GET /extensions/_webadmin and the 404 semantics of GET
 * /extensions/{extensionName}/webadmin/defaults/{transportName} against the frozen contract
 * fixtures copied from the BridgeLink-Web-UI repo (see webadmin/README.md).
 */
public class ExtensionWebAdminServletTest extends ServletTestBase {

    private static final String PLUGIN_NAME = "Mock SMTP Connector";
    private static final String CONNECTOR_NAME = "Mock SMTP Sender";
    private static final String EXTENSION_PATH = "mock-smtp";

    private static ExtensionController mockExtensionController;
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File extensionsDir;
    private ExtensionServlet servlet;

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
    }

    // ========== getWebAdminManifests ==========

    @Test
    public void testManifestListMatchesContractFixture() throws Exception {
        JsonNode fixture = readManifestListFixture();
        writeManifest(EXTENSION_PATH, objectMapper.writeValueAsString(fixture.get("entries").get(0).get("manifest")));

        stubExtension(PLUGIN_NAME, CONNECTOR_NAME, EXTENSION_PATH, true);

        JsonNode actual = objectMapper.readTree(servlet.getWebAdminManifests().getContent());
        assertEquals(fixture, actual);
    }

    @Test
    public void testUnparseableManifestServedAsNull() throws Exception {
        writeManifest(EXTENSION_PATH, "{not json");
        stubExtension(PLUGIN_NAME, CONNECTOR_NAME, EXTENSION_PATH, true);

        JsonNode actual = objectMapper.readTree(servlet.getWebAdminManifests().getContent());
        assertEquals(1, actual.get("entries").size());
        JsonNode entry = actual.get("entries").get(0);
        assertEquals(PLUGIN_NAME, entry.get("name").asText());
        assertTrue(entry.get("manifest").isNull());
    }

    @Test
    public void testDisabledExtensionFiltered() throws Exception {
        writeManifest(EXTENSION_PATH, "{\"manifestVersion\":1}");
        stubExtension(PLUGIN_NAME, CONNECTOR_NAME, EXTENSION_PATH, false);

        JsonNode actual = objectMapper.readTree(servlet.getWebAdminManifests().getContent());
        assertEquals(0, actual.get("entries").size());
    }

    @Test
    public void testExtensionWithoutManifestFiltered() throws Exception {
        stubExtension(PLUGIN_NAME, CONNECTOR_NAME, EXTENSION_PATH, true);

        JsonNode actual = objectMapper.readTree(servlet.getWebAdminManifests().getContent());
        assertEquals(0, actual.get("entries").size());
    }

    @Test
    public void testPathEscapeGuard() throws Exception {
        // Plant a manifest OUTSIDE the extensions dir that a traversal path would reach
        File outside = new File(tempFolder.getRoot(), "outside/webadmin");
        FileUtils.writeStringToFile(new File(outside, "webadmin.json"), "{\"manifestVersion\":1}", StandardCharsets.UTF_8);

        stubExtension(PLUGIN_NAME, CONNECTOR_NAME, "../outside", true);

        JsonNode actual = objectMapper.readTree(servlet.getWebAdminManifests().getContent());
        assertEquals(0, actual.get("entries").size());
    }

    @Test
    public void testPathEscapeGuardAbsolutePath() throws Exception {
        File outside = new File(tempFolder.getRoot(), "abs/webadmin");
        FileUtils.writeStringToFile(new File(outside, "webadmin.json"), "{\"manifestVersion\":1}", StandardCharsets.UTF_8);

        stubExtension(PLUGIN_NAME, CONNECTOR_NAME, new File(tempFolder.getRoot(), "abs").getAbsolutePath(), true);

        JsonNode actual = objectMapper.readTree(servlet.getWebAdminManifests().getContent());
        assertEquals(0, actual.get("entries").size());
    }

    @Test
    public void testEmptyEntriesWhenNoExtensionsInstalled() throws Exception {
        when(mockExtensionController.getPluginMetaData()).thenReturn(new HashMap<>());
        when(mockExtensionController.getConnectorMetaData()).thenReturn(new HashMap<>());

        JsonNode actual = objectMapper.readTree(servlet.getWebAdminManifests().getContent());
        assertEquals(0, actual.get("entries").size());
    }

    @Test
    public void testConnectorOnlyExtensionUsesConnectorMetaData() throws Exception {
        writeManifest(EXTENSION_PATH, "{\"manifestVersion\":1}");

        ConnectorMetaData connectorMetaData = connectorMetaData(CONNECTOR_NAME, EXTENSION_PATH);
        Map<String, ConnectorMetaData> connectorMap = new HashMap<>();
        connectorMap.put(CONNECTOR_NAME, connectorMetaData);
        when(mockExtensionController.getPluginMetaData()).thenReturn(new HashMap<>());
        when(mockExtensionController.getConnectorMetaData()).thenReturn(connectorMap);
        when(mockExtensionController.getConnectorMetaDataByTransportName(CONNECTOR_NAME)).thenReturn(connectorMetaData);
        when(mockExtensionController.isExtensionEnabled(CONNECTOR_NAME)).thenReturn(true);

        JsonNode actual = objectMapper.readTree(servlet.getWebAdminManifests().getContent());
        assertEquals(1, actual.get("entries").size());
        JsonNode entry = actual.get("entries").get(0);
        assertEquals(CONNECTOR_NAME, entry.get("name").asText());
        assertEquals(EXTENSION_PATH, entry.get("path").asText());
        assertEquals("1.0.0", entry.get("version").asText());
    }

    // ========== getWebAdminConnectorDefaults 404 legs ==========

    @Test
    public void testDefaultsNotFoundForUnknownExtension() throws Exception {
        stubExtension(PLUGIN_NAME, CONNECTOR_NAME, EXTENSION_PATH, true);
        assertNotFound(() -> servlet.getWebAdminConnectorDefaults("Nonexistent", CONNECTOR_NAME));
    }

    @Test
    public void testDefaultsNotFoundWhenDisabled() throws Exception {
        writeManifest(EXTENSION_PATH, "{\"manifestVersion\":1}");
        stubExtension(PLUGIN_NAME, CONNECTOR_NAME, EXTENSION_PATH, false);
        assertNotFound(() -> servlet.getWebAdminConnectorDefaults(PLUGIN_NAME, CONNECTOR_NAME));
    }

    @Test
    public void testDefaultsNotFoundWithoutManifest() throws Exception {
        stubExtension(PLUGIN_NAME, CONNECTOR_NAME, EXTENSION_PATH, true);
        assertNotFound(() -> servlet.getWebAdminConnectorDefaults(PLUGIN_NAME, CONNECTOR_NAME));
    }

    @Test
    public void testDefaultsNotFoundWhenTransportDeclaredByOtherExtension() throws Exception {
        writeManifest(EXTENSION_PATH, "{\"manifestVersion\":1}");
        stubExtension(PLUGIN_NAME, CONNECTOR_NAME, EXTENSION_PATH, true);

        // Same transport name, but declared by an extension at a different path
        ConnectorMetaData otherConnector = connectorMetaData(CONNECTOR_NAME, "other-extension");
        when(mockExtensionController.getConnectorMetaDataByTransportName(CONNECTOR_NAME)).thenReturn(otherConnector);

        assertNotFound(() -> servlet.getWebAdminConnectorDefaults(PLUGIN_NAME, CONNECTOR_NAME));
    }

    @Test
    public void testDefaultsNotFoundForUndeclaredTransport() throws Exception {
        writeManifest(EXTENSION_PATH, "{\"manifestVersion\":1}");
        stubExtension(PLUGIN_NAME, CONNECTOR_NAME, EXTENSION_PATH, true);
        when(mockExtensionController.getConnectorMetaDataByTransportName("Unknown Transport")).thenReturn(null);

        assertNotFound(() -> servlet.getWebAdminConnectorDefaults(PLUGIN_NAME, "Unknown Transport"));
    }

    // ========== helpers ==========

    private void stubExtension(String pluginName, String connectorName, String path, boolean enabled) {
        PluginMetaData pluginMetaData = new PluginMetaData();
        pluginMetaData.setName(pluginName);
        pluginMetaData.setPath(path);
        pluginMetaData.setPluginVersion("1.0.0");
        Map<String, PluginMetaData> pluginMap = new HashMap<>();
        pluginMap.put(pluginName, pluginMetaData);

        ConnectorMetaData connectorMetaData = connectorMetaData(connectorName, path);
        Map<String, ConnectorMetaData> connectorMap = new HashMap<>();
        connectorMap.put(connectorName, connectorMetaData);

        when(mockExtensionController.getPluginMetaData()).thenReturn(pluginMap);
        when(mockExtensionController.getConnectorMetaData()).thenReturn(connectorMap);
        when(mockExtensionController.getConnectorMetaDataByTransportName(connectorName)).thenReturn(connectorMetaData);
        when(mockExtensionController.isExtensionEnabled(pluginName)).thenReturn(enabled);
        when(mockExtensionController.isExtensionEnabled(connectorName)).thenReturn(enabled);
    }

    private ConnectorMetaData connectorMetaData(String name, String path) {
        ConnectorMetaData connectorMetaData = new ConnectorMetaData();
        connectorMetaData.setName(name);
        connectorMetaData.setPath(path);
        connectorMetaData.setPluginVersion("1.0.0");
        connectorMetaData.setServerClassName("com.mirth.connect.connectors.smtp.SmtpDispatcher");
        connectorMetaData.setSharedClassName("com.mirth.connect.connectors.smtp.SmtpDispatcherProperties");
        return connectorMetaData;
    }

    private void writeManifest(String extensionPath, String content) throws Exception {
        File manifestFile = new File(extensionsDir, extensionPath + "/webadmin/webadmin.json");
        FileUtils.writeStringToFile(manifestFile, content, StandardCharsets.UTF_8);
    }

    private JsonNode readManifestListFixture() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("webadmin/manifest-list.response.json")) {
            return objectMapper.readTree(IOUtils.toString(is, StandardCharsets.UTF_8));
        }
    }

    private void assertNotFound(Runnable invocation) {
        try {
            invocation.run();
            fail("Expected MirthApiException with 404 status");
        } catch (MirthApiException e) {
            assertEquals(Status.NOT_FOUND.getStatusCode(), e.getResponse().getStatus());
        }
    }

    /**
     * Bypasses login/authorization and points the extensions directory at the temp folder.
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
