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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.SecurityContext;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.MetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

public class ExtensionServletTest extends ServletTestBase {

    private static final String PLUGIN_NAME = "Test Plugin";
    private static final String CONNECTOR_NAME = "Test Connector";
    private static final String NONEXISTENT_NAME = "Nonexistent";

    private static ExtensionController mockExtensionController;
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

        // Re-stub shared stubs after reset
        Map<String, PluginMetaData> pluginMap = new HashMap<>();
        PluginMetaData pluginMeta = new PluginMetaData();
        pluginMeta.setName(PLUGIN_NAME);
        pluginMap.put(PLUGIN_NAME, pluginMeta);
        doReturn(pluginMap).when(mockExtensionController).getPluginMetaData();

        Map<String, ConnectorMetaData> connectorMap = new HashMap<>();
        ConnectorMetaData connectorMeta = new ConnectorMetaData();
        connectorMeta.setName(CONNECTOR_NAME);
        connectorMap.put(CONNECTOR_NAME, connectorMeta);
        doReturn(connectorMap).when(mockExtensionController).getConnectorMetaData();

        doReturn(true).when(mockExtensionController).isExtensionEnabled(PLUGIN_NAME);
        doReturn(false).when(mockExtensionController).isExtensionEnabled(NONEXISTENT_NAME);

        Properties pluginProps = new Properties();
        pluginProps.setProperty("key1", "value1");
        doReturn(pluginProps).when(mockExtensionController).getPluginProperties(anyString(), any());

        servlet = new TestExtensionServlet(request, mock(SecurityContext.class));
    }

    // ========== getConnectorMetaData ==========

    @Test
    public void testGetConnectorMetaData() {
        Map<String, ConnectorMetaData> result = servlet.getConnectorMetaData();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(CONNECTOR_NAME));
    }

    // ========== getPluginMetaData ==========

    @Test
    public void testGetPluginMetaData() {
        Map<String, PluginMetaData> result = servlet.getPluginMetaData();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(PLUGIN_NAME));
    }

    // ========== getExtensionMetaData ==========

    @Test
    public void testGetExtensionMetaDataPlugin() {
        MetaData result = servlet.getExtensionMetaData(PLUGIN_NAME);
        assertNotNull(result);
        assertEquals(PLUGIN_NAME, result.getName());
    }

    @Test
    public void testGetExtensionMetaDataConnector() {
        MetaData result = servlet.getExtensionMetaData(CONNECTOR_NAME);
        assertNotNull(result);
        assertEquals(CONNECTOR_NAME, result.getName());
    }

    @Test(expected = MirthApiException.class)
    public void testGetExtensionMetaDataNotFound() {
        servlet.getExtensionMetaData(NONEXISTENT_NAME);
    }

    // ========== isExtensionEnabled ==========

    @Test
    public void testIsExtensionEnabledTrue() {
        assertTrue(servlet.isExtensionEnabled(PLUGIN_NAME));
    }

    @Test
    public void testIsExtensionEnabledFalse() {
        assertFalse(servlet.isExtensionEnabled(NONEXISTENT_NAME));
    }

    // ========== setExtensionEnabled ==========

    @Test
    public void testSetExtensionEnabled() throws Exception {
        doNothing().when(mockExtensionController).setExtensionEnabled(anyString(), anyBoolean());
        servlet.setExtensionEnabled(PLUGIN_NAME, true);
        verify(mockExtensionController).setExtensionEnabled(PLUGIN_NAME, true);
    }

    @Test(expected = MirthApiException.class)
    public void testSetExtensionEnabledControllerException() throws Exception {
        doThrow(new ControllerException("enable error")).when(mockExtensionController).setExtensionEnabled(anyString(), anyBoolean());
        servlet.setExtensionEnabled(PLUGIN_NAME, true);
    }

    // ========== uninstallExtension ==========

    @Test
    public void testUninstallExtension() throws Exception {
        doNothing().when(mockExtensionController).prepareExtensionForUninstallation(anyString());
        servlet.uninstallExtension("extensionPath");
        verify(mockExtensionController).prepareExtensionForUninstallation("extensionPath");
    }

    @Test(expected = MirthApiException.class)
    public void testUninstallExtensionControllerException() throws Exception {
        doThrow(new ControllerException("uninstall error")).when(mockExtensionController).prepareExtensionForUninstallation(anyString());
        servlet.uninstallExtension("extensionPath");
    }

    // ========== getPluginProperties ==========

    @Test
    public void testGetPluginProperties() throws Exception {
        Properties result = servlet.getPluginProperties(PLUGIN_NAME, null);
        assertNotNull(result);
        assertEquals("value1", result.getProperty("key1"));
    }

    @Test(expected = MirthApiException.class)
    public void testGetPluginPropertiesControllerException() throws Exception {
        doThrow(new ControllerException("props error")).when(mockExtensionController).getPluginProperties(anyString(), any());
        servlet.getPluginProperties(PLUGIN_NAME, null);
    }

    // ========== setPluginProperties ==========

    @Test
    public void testSetPluginProperties() throws Exception {
        doNothing().when(mockExtensionController).setPluginProperties(anyString(), any(Properties.class), anyBoolean());
        doNothing().when(mockExtensionController).updatePluginProperties(anyString(), any(Properties.class));
        Properties props = new Properties();
        props.setProperty("key2", "value2");
        servlet.setPluginProperties(PLUGIN_NAME, props, false);
        verify(mockExtensionController).setPluginProperties(PLUGIN_NAME, props, false);
        verify(mockExtensionController).updatePluginProperties(PLUGIN_NAME, props);
    }

    @Test(expected = MirthApiException.class)
    public void testSetPluginPropertiesControllerException() throws Exception {
        doThrow(new ControllerException("set error")).when(mockExtensionController).setPluginProperties(anyString(), any(Properties.class), anyBoolean());
        servlet.setPluginProperties(PLUGIN_NAME, new Properties(), false);
    }

    /**
     * Inner class to bypass login initialization and authorization in tests.
     */
    public class TestExtensionServlet extends ExtensionServlet {
        public TestExtensionServlet(HttpServletRequest request, SecurityContext sc) {
            super(request, sc);
        }

        @Override
        protected boolean isUserAuthorized() {
            return true;
        }

        @Override
        protected boolean isUserAuthorized(boolean audit) {
            return true;
        }

        @Override
        protected void checkUserAuthorizedForExtension(String extensionName) {
            // No-op for tests
        }
    }
}
