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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.innovarhealthcare.connect.plugins.mockdimse.MockDIMSEDataTypeServerPlugin;
import com.mirth.connect.client.core.Version;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.PluginClass;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.plugins.DataTypeServerPlugin;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

/**
 * Tests GET /extensions/{extensionName}/webadmin/datatype-defaults/{dataTypeName} against the
 * frozen contract fixture (webadmin/datatype-defaults.response.xml), using the MockDIMSE test
 * classes the fixture was generated from (byte-identical mirrors of samples/mock-dimse-datatype's
 * Java — see webadmin/README.md).
 */
public class ExtensionWebAdminDataTypeDefaultsTest extends ServletTestBase {

    private static final String PLUGIN_NAME = "Mock DIMSE Data Type";
    private static final String EXTENSION_PATH = "mock-dimse-datatype";
    private static final String DATA_TYPE_NAME = "MockDIMSE";
    private static final String SERVER_PLUGIN_CLASS = MockDIMSEDataTypeServerPlugin.class.getName();
    private static final String PROPERTIES_CLASS = "com.innovarhealthcare.connect.plugins.mockdimse.MockDIMSEDataTypeProperties";

    private static ExtensionController mockExtensionController;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File extensionsDir;
    private ExtensionServlet servlet;
    private PluginMetaData pluginMetaData;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        try {
            ObjectXMLSerializer.getInstance().init(Version.getLatest().toString());
        } catch (Exception e) {
            // Ignore if it has already been initialized
        }

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

        writeManifest(EXTENSION_PATH);

        PluginClass serverClass = new PluginClass();
        serverClass.setName(SERVER_PLUGIN_CLASS);
        pluginMetaData = new PluginMetaData();
        pluginMetaData.setName(PLUGIN_NAME);
        pluginMetaData.setPath(EXTENSION_PATH);
        pluginMetaData.setPluginVersion("1.0.0");
        pluginMetaData.setServerClasses(new ArrayList<>(Arrays.asList(serverClass)));
        Map<String, PluginMetaData> pluginMap = new HashMap<>();
        pluginMap.put(PLUGIN_NAME, pluginMetaData);

        Map<String, DataTypeServerPlugin> dataTypePlugins = new HashMap<>();
        dataTypePlugins.put(DATA_TYPE_NAME, new MockDIMSEDataTypeServerPlugin());

        when(mockExtensionController.getPluginMetaData()).thenReturn(pluginMap);
        when(mockExtensionController.getConnectorMetaData()).thenReturn(new HashMap<>());
        when(mockExtensionController.getDataTypePlugins()).thenReturn(dataTypePlugins);
        when(mockExtensionController.isExtensionEnabled(PLUGIN_NAME)).thenReturn(true);

        servlet = new TestExtensionServlet(request, mock(SecurityContext.class));
    }

    // ========== success paths ==========

    @Test
    public void testDataTypeDefaultsRootForm() throws Exception {
        Element actualRoot = parse(fetchDefaults());

        assertEquals("dataTypeProperties", actualRoot.getNodeName());
        assertEquals(PROPERTIES_CLASS, actualRoot.getAttribute("class"));
        assertFalse("version attribute must carry the real engine version", actualRoot.getAttribute("version").isEmpty());
        assertFalse("body must not contain a version placeholder", actualRoot.getAttribute("version").contains("{{"));
    }

    @Test
    public void testDataTypeDefaultsMatchContractFixture() throws Exception {
        Element actualRoot = parse(fetchDefaults());
        Element fixtureRoot = parse(readFixture());

        assertEquals(fixtureRoot.getNodeName(), actualRoot.getNodeName());
        assertStructuralSubset(fixtureRoot, actualRoot, "/" + fixtureRoot.getNodeName());
    }

    @Test
    public void testDataTypeDefaultsAllGroupsPresent() throws Exception {
        // The contract requires every property group element in the body; WebAdmin skips the
        // manifest when a manifest-declared group element is missing
        Element actualRoot = parse(fetchDefaults());

        for (String group : new String[] { "serializationProperties", "deserializationProperties",
                "batchProperties", "responseGenerationProperties", "responseValidationProperties" }) {
            assertEquals("group element " + group, 1, actualRoot.getElementsByTagName(group).getLength());
        }
    }

    @Test
    public void testDataTypeDefaultsContentTypePinnedToXml() throws Exception {
        // Produces admits application/json (WebAdmin sends Accept: application/json), but the
        // response media type must always be application/xml per the contract
        javax.ws.rs.core.Response response = servlet.getWebAdminDataTypeDefaults(PLUGIN_NAME, DATA_TYPE_NAME);
        assertEquals(javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE, response.getMediaType());
    }

    // ========== 404 legs ==========

    @Test
    public void testDataTypeDefaultsNotFoundForUnknownExtension() throws Exception {
        assertNotFound(() -> servlet.getWebAdminDataTypeDefaults("Nonexistent", DATA_TYPE_NAME));
    }

    @Test
    public void testDataTypeDefaultsNotFoundWhenDisabled() throws Exception {
        when(mockExtensionController.isExtensionEnabled(PLUGIN_NAME)).thenReturn(false);
        assertNotFound(() -> servlet.getWebAdminDataTypeDefaults(PLUGIN_NAME, DATA_TYPE_NAME));
    }

    @Test
    public void testDataTypeDefaultsNotFoundWithoutManifest() throws Exception {
        FileUtils.deleteDirectory(new File(extensionsDir, EXTENSION_PATH));
        assertNotFound(() -> servlet.getWebAdminDataTypeDefaults(PLUGIN_NAME, DATA_TYPE_NAME));
    }

    @Test
    public void testDataTypeDefaultsNotFoundForUnknownDataType() throws Exception {
        assertNotFound(() -> servlet.getWebAdminDataTypeDefaults(PLUGIN_NAME, "UnknownType"));
    }

    @Test
    public void testDataTypeDefaultsNotFoundWhenDataTypeDeclaredByOtherExtension() throws Exception {
        // The registry knows the data type, but the named extension's serverClasses don't
        // declare the plugin's class — the own-classes-only guard
        PluginClass otherClass = new PluginClass();
        otherClass.setName("com.example.other.OtherDataTypeServerPlugin");
        pluginMetaData.setServerClasses(new ArrayList<>(Arrays.asList(otherClass)));

        assertNotFound(() -> servlet.getWebAdminDataTypeDefaults(PLUGIN_NAME, DATA_TYPE_NAME));
    }

    @Test
    public void testDataTypeDefaultsNotFoundWithoutServerClasses() throws Exception {
        pluginMetaData.setServerClasses(null);
        assertNotFound(() -> servlet.getWebAdminDataTypeDefaults(PLUGIN_NAME, DATA_TYPE_NAME));
    }

    @Test
    public void testDataTypeDefaultsNotFoundForConnectorOnlyExtension() throws Exception {
        // An extension resolved through connector metadata declares no server plugin classes
        String connectorName = "Some Connector";
        ConnectorMetaData connectorMetaData = new ConnectorMetaData();
        connectorMetaData.setName(connectorName);
        connectorMetaData.setPath(EXTENSION_PATH);
        connectorMetaData.setPluginVersion("1.0.0");
        Map<String, ConnectorMetaData> connectorMap = new HashMap<>();
        connectorMap.put(connectorName, connectorMetaData);

        when(mockExtensionController.getPluginMetaData()).thenReturn(new HashMap<>());
        when(mockExtensionController.getConnectorMetaData()).thenReturn(connectorMap);
        when(mockExtensionController.isExtensionEnabled(connectorName)).thenReturn(true);

        assertNotFound(() -> servlet.getWebAdminDataTypeDefaults(connectorName, DATA_TYPE_NAME));
    }

    // ========== helpers ==========

    private String fetchDefaults() {
        javax.ws.rs.core.Response response = servlet.getWebAdminDataTypeDefaults(PLUGIN_NAME, DATA_TYPE_NAME);
        return ((com.mirth.connect.client.core.api.RawContent) response.getEntity()).getContent();
    }

    private void writeManifest(String extensionPath) throws Exception {
        File manifestFile = new File(extensionsDir, extensionPath + "/webadmin/webadmin.json");
        FileUtils.writeStringToFile(manifestFile, "{\"manifestVersion\":2}", StandardCharsets.UTF_8);
    }

    private Element parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }

    private String readFixture() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("webadmin/datatype-defaults.response.xml")) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
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
     * Asserts that every element in the fixture tree appears in the actual tree with identical
     * attributes (version excluded — fixtures carry the stamps of the engine that generated them)
     * and identical leaf text.
     */
    private void assertStructuralSubset(Element fixture, Element actual, String path) {
        NamedNodeMap fixtureAttributes = fixture.getAttributes();
        for (int i = 0; i < fixtureAttributes.getLength(); i++) {
            Node attribute = fixtureAttributes.item(i);
            if (!"version".equals(attribute.getNodeName())) {
                assertEquals(path + "/@" + attribute.getNodeName(), attribute.getNodeValue(), actual.getAttribute(attribute.getNodeName()));
            }
        }

        List<Element> fixtureChildren = childElements(fixture);
        if (fixtureChildren.isEmpty()) {
            assertEquals(path + " text", fixture.getTextContent().trim(), actual.getTextContent().trim());
            return;
        }

        Map<String, List<Element>> actualByName = new HashMap<>();
        for (Element child : childElements(actual)) {
            actualByName.computeIfAbsent(child.getNodeName(), k -> new ArrayList<>()).add(child);
        }

        Map<String, Integer> seen = new HashMap<>();
        for (Element fixtureChild : fixtureChildren) {
            String name = fixtureChild.getNodeName();
            int index = seen.merge(name, 1, Integer::sum) - 1;
            List<Element> candidates = actualByName.get(name);
            assertNotNull(path + "/" + name + " missing from actual output", candidates);
            assertTrue(path + "/" + name + "[" + index + "] missing from actual output", index < candidates.size());
            assertStructuralSubset(fixtureChild, candidates.get(index), path + "/" + name + (index > 0 ? "[" + index + "]" : ""));
        }
    }

    private List<Element> childElements(Element element) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) nodes.item(i));
            }
        }
        return children;
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
