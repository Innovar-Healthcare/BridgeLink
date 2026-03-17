package com.mirth.connect.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ScriptController;

public class ServerSettingsTest {

    public static ServerSettings serverSettings = new ServerSettings();
    
    @BeforeClass
    public static void setup() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        ScriptController scriptController = mock(ScriptController.class);
        when(controllerFactory.createScriptController()).thenReturn(scriptController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);
        serverSettings.setEnvironmentName("envName");
        serverSettings.setServerName("serverName");
        serverSettings.setDefaultMetaDataColumns(null);
        serverSettings.setQueueBufferSize(1000);
        
    }
    
    @Test
    public void envornmentNameTest() {
        assertEquals("envName", serverSettings.getEnvironmentName());

    }
    
    @Test
    public void serverNameTest() {
        assertEquals("serverName", serverSettings.getServerName());
    }

    @Test
    public void defaultMetaDataColumnsTest() {
        assertNull(serverSettings.getDefaultMetaDataColumns());
    }
    
    @Test
    public void queueBufferSizeTest() {
        assertTrue(1000 == serverSettings.getQueueBufferSize());
    }

    // -----------------------------------------------------------------------
    // reconcileSmtpAuth — authType drives the smtpAuth boolean
    // -----------------------------------------------------------------------

    @Test
    public void testReconcileSmtpAuth_BasicSetsSmtpAuthTrue() {
        Properties p = new Properties();
        p.setProperty("smtp.auth.type", "BASIC");
        ServerSettings settings = new ServerSettings("env", "server", p);
        assertTrue("BASIC authType should set smtpAuth=true", settings.getSmtpAuth());
    }

    @Test
    public void testReconcileSmtpAuth_OAuthSetsSmtpAuthTrue() {
        Properties p = new Properties();
        p.setProperty("smtp.auth.type", "OAUTH");
        ServerSettings settings = new ServerSettings("env", "server", p);
        assertTrue("OAUTH authType should set smtpAuth=true", settings.getSmtpAuth());
    }

    @Test
    public void testReconcileSmtpAuth_NoneSetsSmtpAuthFalse() {
        Properties p = new Properties();
        p.setProperty("smtp.auth.type", "NONE");
        ServerSettings settings = new ServerSettings("env", "server", p);
        assertFalse("NONE authType should set smtpAuth=false", settings.getSmtpAuth());
    }

    @Test
    public void testReconcileSmtpAuth_NullAuthTypeNoChange() {
        // No smtp.auth.type set — smtpAuth should remain null (not forced to any value)
        Properties p = new Properties();
        ServerSettings settings = new ServerSettings("env", "server", p);
        assertNull("Missing authType should leave smtpAuth as null", settings.getSmtpAuth());
    }

    // -----------------------------------------------------------------------
    // OAuth fields round-trip through Properties
    // -----------------------------------------------------------------------

    @Test
    public void testOAuthFields_RoundTrip() {
        Properties p = new Properties();
        p.setProperty("smtp.auth.type", "OAUTH");
        p.setProperty("smtp.oauth.client.id", "my-client-id");
        p.setProperty("smtp.oauth.client.secret", "my-secret");
        p.setProperty("smtp.oauth.token.endpoint.url", "https://login.example.com/token");
        p.setProperty("smtp.oauth.scope", "https://outlook.office365.com/.default");

        ServerSettings settings = new ServerSettings("env", "server", p);

        assertEquals("my-client-id", settings.getSmtpOAuthClientId());
        assertEquals("my-secret", settings.getSmtpOAuthClientSecret());
        assertEquals("https://login.example.com/token", settings.getSmtpOAuthTokenEndpointUrl());
        assertEquals("https://outlook.office365.com/.default", settings.getSmtpOAuthScope());
    }

    @Test
    public void testOAuthFields_NullWhenAbsent() {
        ServerSettings settings = new ServerSettings("env", "server", new Properties());
        assertNull(settings.getSmtpOAuthClientId());
        assertNull(settings.getSmtpOAuthClientSecret());
        assertNull(settings.getSmtpOAuthTokenEndpointUrl());
        assertNull(settings.getSmtpOAuthScope());
    }

}
