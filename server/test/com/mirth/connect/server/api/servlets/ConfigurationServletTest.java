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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import com.mirth.connect.model.ChannelDependency;
import com.mirth.connect.model.ChannelMetadata;
import com.mirth.connect.model.ChannelTag;
import com.mirth.connect.model.DriverInfo;
import com.mirth.connect.model.EncryptionSettings;
import com.mirth.connect.model.LicenseInfo;
import com.mirth.connect.model.PasswordRequirements;
import com.mirth.connect.model.PublicServerSettings;
import com.mirth.connect.model.ServerConfiguration;
import com.mirth.connect.model.ServerSettings;
import com.mirth.connect.model.UpdateSettings;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ChannelController;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ScriptController;
import com.mirth.connect.util.ConfigurationProperty;
import com.mirth.connect.util.ConnectionTestResponse;

public class ConfigurationServletTest extends ServletTestBase {

    private static ConfigurationController mockConfigController;
    private static ScriptController mockScriptController;
    private static ChannelController mockChannelController;
    private ConfigurationServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        mockConfigController = mock(ConfigurationController.class);
        when(controllerFactory.createConfigurationController()).thenReturn(mockConfigController);

        mockScriptController = mock(ScriptController.class);
        when(controllerFactory.createScriptController()).thenReturn(mockScriptController);

        mockChannelController = mock(ChannelController.class);
        when(controllerFactory.createChannelController()).thenReturn(mockChannelController);

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
    public void beforeTest() {
        clearInvocations(mockConfigController, mockScriptController, mockChannelController);
        servlet = new TestConfigurationServlet(request, mock(SecurityContext.class));
    }

    // ========== getServerId ==========

    @Test
    public void testGetServerId() {
        when(mockConfigController.getServerId()).thenReturn("server-123");
        assertEquals("server-123", servlet.getServerId());
    }

    // ========== getVersion ==========

    @Test
    public void testGetVersion() {
        when(mockConfigController.getServerVersion()).thenReturn("4.5.2");
        assertEquals("4.5.2", servlet.getVersion());
    }

    // ========== getBuildDate ==========

    @Test
    public void testGetBuildDate() {
        when(mockConfigController.getBuildDate()).thenReturn("2025-01-01");
        assertEquals("2025-01-01", servlet.getBuildDate());
    }

    // ========== getStatus ==========

    @Test
    public void testGetStatus() {
        when(mockConfigController.getStatus()).thenReturn(ConfigurationController.STATUS_OK);
        assertEquals(ConfigurationController.STATUS_OK, servlet.getStatus());
    }

    // ========== getServerTimezone ==========

    @Test
    public void testGetServerTimezone() {
        when(mockConfigController.getServerTimezone(any())).thenReturn("America/New_York");
        assertEquals("America/New_York", servlet.getServerTimezone());
    }

    // ========== getServerTime ==========

    @Test
    public void testGetServerTime() {
        Calendar cal = Calendar.getInstance();
        when(mockConfigController.getServerTime()).thenReturn(cal);
        assertEquals(cal, servlet.getServerTime());
    }

    // ========== getJVMName ==========

    @Test
    public void testGetJVMName() {
        String jvmName = servlet.getJVMName();
        assertNotNull(jvmName);
        assertEquals(System.getProperty("java.vm.name"), jvmName);
    }

    // ========== getServerSettings ==========

    @Test
    public void testGetServerSettings() throws Exception {
        ServerSettings settings = new ServerSettings();
        when(mockConfigController.getServerSettings()).thenReturn(settings);
        assertEquals(settings, servlet.getServerSettings());
    }

    @Test(expected = MirthApiException.class)
    public void testGetServerSettingsControllerException() throws Exception {
        doThrow(new ControllerException("error")).when(mockConfigController).getServerSettings();
        try {
            servlet.getServerSettings();
        } finally {
            doReturn(new ServerSettings()).when(mockConfigController).getServerSettings();
        }
    }

    // ========== setServerSettings ==========

    @Test
    public void testSetServerSettings() throws Exception {
        doNothing().when(mockConfigController).setServerSettings(any(ServerSettings.class));
        servlet.setServerSettings(new ServerSettings());
        verify(mockConfigController).setServerSettings(any(ServerSettings.class));
    }

    @Test(expected = MirthApiException.class)
    public void testSetServerSettingsControllerException() throws Exception {
        doThrow(new ControllerException("error")).when(mockConfigController).setServerSettings(any(ServerSettings.class));
        try {
            servlet.setServerSettings(new ServerSettings());
        } finally {
            doNothing().when(mockConfigController).setServerSettings(any(ServerSettings.class));
        }
    }

    // ========== getPublicServerSettings ==========

    @Test
    public void testGetPublicServerSettings() throws Exception {
        PublicServerSettings publicSettings = new PublicServerSettings(new ServerSettings());
        when(mockConfigController.getPublicServerSettings()).thenReturn(publicSettings);
        assertEquals(publicSettings, servlet.getPublicServerSettings());
    }

    // ========== getEncryptionSettings ==========

    @Test
    public void testGetEncryptionSettings() throws Exception {
        EncryptionSettings encSettings = new EncryptionSettings();
        when(mockConfigController.getEncryptionSettings()).thenReturn(encSettings);
        assertEquals(encSettings, servlet.getEncryptionSettings());
    }

    // ========== getUpdateSettings ==========

    @Test
    public void testGetUpdateSettings() throws Exception {
        UpdateSettings updateSettings = new UpdateSettings();
        when(mockConfigController.getUpdateSettings()).thenReturn(updateSettings);
        assertEquals(updateSettings, servlet.getUpdateSettings());
    }

    @Test(expected = MirthApiException.class)
    public void testGetUpdateSettingsControllerException() throws Exception {
        doThrow(new ControllerException("error")).when(mockConfigController).getUpdateSettings();
        try {
            servlet.getUpdateSettings();
        } finally {
            doReturn(new UpdateSettings()).when(mockConfigController).getUpdateSettings();
        }
    }

    // ========== setUpdateSettings ==========

    @Test
    public void testSetUpdateSettings() throws Exception {
        doNothing().when(mockConfigController).setUpdateSettings(any(UpdateSettings.class));
        servlet.setUpdateSettings(new UpdateSettings());
        verify(mockConfigController).setUpdateSettings(any(UpdateSettings.class));
    }

    // ========== getLicenseInfo ==========

    @Test
    public void testGetLicenseInfo() {
        LicenseInfo info = servlet.getLicenseInfo();
        assertNotNull(info);
    }

    // ========== getGuid ==========

    @Test
    public void testGetGuid() {
        when(mockConfigController.generateGuid()).thenReturn("guid-abc-123");
        assertEquals("guid-abc-123", servlet.getGuid());
    }

    // ========== getGlobalScripts ==========

    @Test
    public void testGetGlobalScripts() throws Exception {
        Map<String, String> scripts = new HashMap<>();
        scripts.put("Deploy", "// deploy script");
        when(mockScriptController.getGlobalScripts()).thenReturn(scripts);
        Map<String, String> result = servlet.getGlobalScripts();
        assertEquals(1, result.size());
        assertEquals("// deploy script", result.get("Deploy"));
    }

    @Test(expected = MirthApiException.class)
    public void testGetGlobalScriptsControllerException() throws Exception {
        doThrow(new ControllerException("error")).when(mockScriptController).getGlobalScripts();
        try {
            servlet.getGlobalScripts();
        } finally {
            doReturn(new HashMap<>()).when(mockScriptController).getGlobalScripts();
        }
    }

    // ========== setGlobalScripts ==========

    @Test
    public void testSetGlobalScripts() throws Exception {
        doNothing().when(mockScriptController).setGlobalScripts(any(Map.class));
        Map<String, String> scripts = new HashMap<>();
        scripts.put("Deploy", "// deploy");
        servlet.setGlobalScripts(scripts);
        verify(mockScriptController).setGlobalScripts(scripts);
    }

    // ========== getConfigurationMap ==========

    @Test
    public void testGetConfigurationMap() throws Exception {
        Map<String, ConfigurationProperty> map = new HashMap<>();
        map.put("key1", new ConfigurationProperty("value1", "comment1"));
        when(mockConfigController.getConfigurationProperties()).thenReturn(map);
        Map<String, ConfigurationProperty> result = servlet.getConfigurationMap();
        assertEquals(1, result.size());
        assertTrue(result.containsKey("key1"));
    }

    // ========== setConfigurationMap ==========

    @Test
    public void testSetConfigurationMap() throws Exception {
        doNothing().when(mockConfigController).setConfigurationProperties(any(Map.class), anyBoolean());
        Map<String, ConfigurationProperty> map = new HashMap<>();
        servlet.setConfigurationMap(map);
        verify(mockConfigController).setConfigurationProperties(map, true);
    }

    // ========== getDatabaseDrivers ==========

    @Test
    public void testGetDatabaseDrivers() throws Exception {
        List<DriverInfo> drivers = new ArrayList<>();
        when(mockConfigController.getDatabaseDrivers()).thenReturn(drivers);
        assertEquals(drivers, servlet.getDatabaseDrivers());
    }

    // ========== setDatabaseDrivers ==========

    @Test
    public void testSetDatabaseDrivers() throws Exception {
        doNothing().when(mockConfigController).setDatabaseDrivers(any(List.class));
        List<DriverInfo> drivers = new ArrayList<>();
        servlet.setDatabaseDrivers(drivers);
        verify(mockConfigController).setDatabaseDrivers(drivers);
    }

    // ========== getPasswordRequirements ==========

    @Test
    public void testGetPasswordRequirements() {
        PasswordRequirements reqs = new PasswordRequirements();
        when(mockConfigController.getPasswordRequirements()).thenReturn(reqs);
        assertEquals(reqs, servlet.getPasswordRequirements());
    }

    // ========== getAvailableCharsetEncodings ==========

    @Test
    public void testGetAvailableCharsetEncodings() throws Exception {
        List<String> encodings = new ArrayList<>();
        encodings.add("UTF-8");
        encodings.add("ISO-8859-1");
        when(mockConfigController.getAvailableCharsetEncodings()).thenReturn(encodings);
        List<String> result = servlet.getAvailableCharsetEncodings();
        assertEquals(2, result.size());
        assertTrue(result.contains("UTF-8"));
    }

    // ========== sendTestEmail ==========

    @Test
    public void testSendTestEmail() throws Exception {
        ConnectionTestResponse response = new ConnectionTestResponse(ConnectionTestResponse.Type.SUCCESS, "OK");
        when(mockConfigController.sendTestEmail(any(Properties.class))).thenReturn(response);
        ConnectionTestResponse result = servlet.sendTestEmail(new Properties());
        assertEquals(ConnectionTestResponse.Type.SUCCESS, result.getType());
    }

    // ========== getChannelTags ==========

    @Test
    public void testGetChannelTags() throws Exception {
        Set<ChannelTag> tags = new HashSet<>();
        when(mockConfigController.getChannelTags()).thenReturn(tags);
        assertEquals(tags, servlet.getChannelTags());
    }

    // ========== setChannelTags ==========

    @Test
    public void testSetChannelTags() throws Exception {
        Set<ChannelTag> tags = new HashSet<>();
        servlet.setChannelTags(tags);
        verify(mockConfigController).setChannelTags(tags);
    }

    // ========== getChannelDependencies ==========

    @Test
    public void testGetChannelDependencies() {
        Set<ChannelDependency> deps = new HashSet<>();
        when(mockConfigController.getChannelDependencies()).thenReturn(deps);
        assertEquals(deps, servlet.getChannelDependencies());
    }

    // ========== setChannelDependencies ==========

    @Test
    public void testSetChannelDependencies() {
        Set<ChannelDependency> deps = new HashSet<>();
        servlet.setChannelDependencies(deps);
        verify(mockConfigController).setChannelDependencies(deps);
    }

    // ========== getChannelMetadata ==========

    @Test
    public void testGetChannelMetadata() {
        Map<String, ChannelMetadata> metadata = new HashMap<>();
        when(mockConfigController.getChannelMetadata()).thenReturn(metadata);
        assertEquals(metadata, servlet.getChannelMetadata());
    }

    // ========== setChannelMetadata ==========

    @Test
    public void testSetChannelMetadata() {
        Map<String, ChannelMetadata> metadata = new HashMap<>();
        servlet.setChannelMetadata(metadata);
        verify(mockConfigController).setChannelMetadata(metadata);
    }

    // ========== getRhinoLanguageVersion ==========

    @Test
    public void testGetRhinoLanguageVersionDefault() {
        when(mockConfigController.getRhinoLanguageVersion()).thenReturn(null);
        int version = servlet.getRhinoLanguageVersion();
        assertEquals(org.mozilla.javascript.Context.VERSION_DEFAULT, version);
    }

    @Test
    public void testGetRhinoLanguageVersionValid() {
        when(mockConfigController.getRhinoLanguageVersion()).thenReturn(200);
        int version = servlet.getRhinoLanguageVersion();
        assertEquals(200, version);
    }

    // ========== getServerConfiguration ==========

    @Test
    public void testGetServerConfigurationBasic() throws Exception {
        ServerConfiguration config = new ServerConfiguration();
        when(mockConfigController.getServerConfiguration()).thenReturn(config);
        ServerConfiguration result = servlet.getServerConfiguration(null, false, false);
        assertNotNull(result);
    }

    @Test(expected = MirthApiException.class)
    public void testGetServerConfigurationInvalidState() {
        // DEPLOYING is not an allowed initial state
        servlet.getServerConfiguration(com.mirth.connect.donkey.model.channel.DeployedState.DEPLOYING, false, false);
    }

    // ========== setServerConfiguration ==========

    @Test
    public void testSetServerConfiguration() throws Exception {
        doNothing().when(mockConfigController).setServerConfiguration(any(ServerConfiguration.class), anyBoolean(), anyBoolean());
        servlet.setServerConfiguration(new ServerConfiguration(), false, false);
        verify(mockConfigController).setServerConfiguration(any(ServerConfiguration.class), anyBoolean(), anyBoolean());
    }

    @Test(expected = MirthApiException.class)
    public void testSetServerConfigurationException() throws Exception {
        doThrow(new ControllerException("restore error")).when(mockConfigController).setServerConfiguration(any(ServerConfiguration.class), anyBoolean(), anyBoolean());
        try {
            servlet.setServerConfiguration(new ServerConfiguration(), false, false);
        } finally {
            doNothing().when(mockConfigController).setServerConfiguration(any(ServerConfiguration.class), anyBoolean(), anyBoolean());
        }
    }

    // ========== getProperty ==========

    @Test
    public void testGetProperty() throws Exception {
        when(mockConfigController.getProperty(anyString(), anyString())).thenReturn("propertyValue");
        assertEquals("propertyValue", servlet.getProperty("group1", "name1"));
    }

    /**
     * Inner class to bypass login initialization in tests.
     */
    public class TestConfigurationServlet extends ConfigurationServlet {
        public TestConfigurationServlet(HttpServletRequest request, SecurityContext sc) {
            super(request, sc);
        }

        @Override
        protected boolean isUserAuthorized() {
            return true;
        }

        @Override
        protected void checkUserAuthorizedForExtension(String extensionName) {
            // No-op for tests
        }
    }
}
