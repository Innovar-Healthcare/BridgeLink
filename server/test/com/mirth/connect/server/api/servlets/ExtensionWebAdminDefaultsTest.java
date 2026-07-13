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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
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
import com.mirth.connect.client.core.Version;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

/**
 * Tests the success path of GET /extensions/{extensionName}/webadmin/defaults/{transportName}
 * against the frozen contract fixture (webadmin/defaults.response.xml), using the real
 * SmtpDispatcherProperties class the fixture was generated from.
 *
 * The fixture was generated on a 26.3.1 engine, so the comparison is structural: after
 * normalizing version attributes, every fixture element must appear in the actual output with
 * identical text and attributes. The 26.6 field set currently matches the fixture exactly; a
 * newer engine line may legitimately add fields without breaking the contract.
 */
public class ExtensionWebAdminDefaultsTest extends ServletTestBase {

    private static final String PLUGIN_NAME = "Mock SMTP Connector";
    private static final String CONNECTOR_NAME = "Mock SMTP Sender";
    private static final String EXTENSION_PATH = "mock-smtp";
    private static final String SHARED_CLASS = "com.mirth.connect.connectors.smtp.SmtpDispatcherProperties";

    private static ExtensionController mockExtensionController;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File extensionsDir;
    private ExtensionServlet servlet;

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

        File manifestFile = new File(extensionsDir, EXTENSION_PATH + "/webadmin/webadmin.json");
        FileUtils.writeStringToFile(manifestFile, "{\"manifestVersion\":1}", StandardCharsets.UTF_8);

        PluginMetaData pluginMetaData = new PluginMetaData();
        pluginMetaData.setName(PLUGIN_NAME);
        pluginMetaData.setPath(EXTENSION_PATH);
        pluginMetaData.setPluginVersion("1.0.0");
        Map<String, PluginMetaData> pluginMap = new HashMap<>();
        pluginMap.put(PLUGIN_NAME, pluginMetaData);

        ConnectorMetaData connectorMetaData = new ConnectorMetaData();
        connectorMetaData.setName(CONNECTOR_NAME);
        connectorMetaData.setPath(EXTENSION_PATH);
        connectorMetaData.setPluginVersion("1.0.0");
        connectorMetaData.setSharedClassName(SHARED_CLASS);
        Map<String, ConnectorMetaData> connectorMap = new HashMap<>();
        connectorMap.put(CONNECTOR_NAME, connectorMetaData);

        when(mockExtensionController.getPluginMetaData()).thenReturn(pluginMap);
        when(mockExtensionController.getConnectorMetaData()).thenReturn(connectorMap);
        when(mockExtensionController.getConnectorMetaDataByTransportName(CONNECTOR_NAME)).thenReturn(connectorMetaData);
        when(mockExtensionController.isExtensionEnabled(PLUGIN_NAME)).thenReturn(true);
        when(mockExtensionController.isExtensionEnabled(CONNECTOR_NAME)).thenReturn(true);

        servlet = new TestExtensionServlet(request, mock(SecurityContext.class));
    }

    @Test
    public void testDefaultsRootForm() throws Exception {
        Element actualRoot = parse(fetchDefaults(PLUGIN_NAME));

        assertEquals("properties", actualRoot.getNodeName());
        assertEquals(SHARED_CLASS, actualRoot.getAttribute("class"));
        assertFalse("version attribute must carry the real engine version", actualRoot.getAttribute("version").isEmpty());
        assertFalse("body must not contain a version placeholder", actualRoot.getAttribute("version").contains("{{"));
    }

    @Test
    public void testDefaultsMatchContractFixture() throws Exception {
        Element actualRoot = parse(fetchDefaults(PLUGIN_NAME));
        Element fixtureRoot = parse(readFixture());

        assertEquals(fixtureRoot.getNodeName(), actualRoot.getNodeName());
        assertStructuralSubset(fixtureRoot, actualRoot, "/" + fixtureRoot.getNodeName());
    }

    @Test
    public void testDefaultsContentTypePinnedToXml() throws Exception {
        // Produces admits application/json (WebAdmin sends Accept: application/json), but the
        // response media type must always be application/xml per the contract
        javax.ws.rs.core.Response response = servlet.getWebAdminConnectorDefaults(PLUGIN_NAME, CONNECTOR_NAME);
        assertEquals(javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE, response.getMediaType());
    }

    @Test
    public void testDefaultsResolvableByConnectorName() throws Exception {
        // The extension is also addressable by its connector metadata name
        Element actualRoot = parse(fetchDefaults(CONNECTOR_NAME));
        assertEquals("properties", actualRoot.getNodeName());
        assertEquals(SHARED_CLASS, actualRoot.getAttribute("class"));
    }

    private String fetchDefaults(String extensionName) {
        javax.ws.rs.core.Response response = servlet.getWebAdminConnectorDefaults(extensionName, CONNECTOR_NAME);
        return ((com.mirth.connect.client.core.api.RawContent) response.getEntity()).getContent();
    }

    // ========== helpers ==========

    private Element parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }

    private String readFixture() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("webadmin/defaults.response.xml")) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    /**
     * Asserts that every element in the fixture tree appears in the actual tree with identical
     * attributes (version excluded — the fixture carries 26.3.1 stamps) and identical leaf text.
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
