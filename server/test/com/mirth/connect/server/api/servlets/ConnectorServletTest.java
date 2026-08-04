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

package com.mirth.connect.server.api.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.Calendar;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.client.core.api.RawContent;
import com.mirth.connect.connectors.vm.VmDispatcherProperties;
import com.mirth.connect.donkey.model.channel.PollConnectorProperties;
import com.mirth.connect.donkey.model.channel.PollConnectorPropertiesAdvanced;
import com.mirth.connect.donkey.model.channel.PollingType;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;

/**
 * Covers {@code GET /connectors/{type}/defaults} (IRT-1516) and
 * {@code POST /connectors/poll/_nextFireTime} (IRT-1518).
 */
public class ConnectorServletTest extends ServletTestBase {

    private static final String CONNECTOR_TYPE = "Channel Writer";
    private static final String DISABLED_CONNECTOR_TYPE = "Disabled Connector";
    private static final String UNKNOWN_CONNECTOR_TYPE = "Unknown Connector";

    private static ExtensionController mockExtensionController;
    private ConnectorServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        mockExtensionController = mock(ExtensionController.class);
        when(controllerFactory.createExtensionController()).thenReturn(mockExtensionController);

        // ConnectorServlet resolves ExtensionController via a static final field initialized at
        // class-load time, so the Guice binding below must be in place before ConnectorServlet is
        // first referenced (i.e. before the first @Before instantiates TestConnectorServlet).
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
        reset(mockExtensionController);

        ConnectorMetaData enabledMeta = new ConnectorMetaData();
        enabledMeta.setName(CONNECTOR_TYPE);
        enabledMeta.setSharedClassName(VmDispatcherProperties.class.getName());
        doReturn(enabledMeta).when(mockExtensionController).getConnectorMetaDataByTransportName(CONNECTOR_TYPE);
        doReturn(true).when(mockExtensionController).isExtensionEnabled(CONNECTOR_TYPE);

        ConnectorMetaData disabledMeta = new ConnectorMetaData();
        disabledMeta.setName(DISABLED_CONNECTOR_TYPE);
        disabledMeta.setSharedClassName(VmDispatcherProperties.class.getName());
        doReturn(disabledMeta).when(mockExtensionController).getConnectorMetaDataByTransportName(DISABLED_CONNECTOR_TYPE);
        doReturn(false).when(mockExtensionController).isExtensionEnabled(DISABLED_CONNECTOR_TYPE);

        doReturn(null).when(mockExtensionController).getConnectorMetaDataByTransportName(UNKNOWN_CONNECTOR_TYPE);

        servlet = new TestConnectorServlet(request, mock(SecurityContext.class));
    }

    // ========== defaults (IRT-1516) ==========

    @Test
    public void testDefaultsReturnsConnectorPropertiesXml() {
        Response response = servlet.defaults(CONNECTOR_TYPE);
        assertEquals(200, response.getStatus());
        RawContent body = (RawContent) response.getEntity();
        assertNotNull(body);
        assertTrue(body.getContent().contains("<properties"));
        assertTrue(body.getContent().contains("VmDispatcherProperties"));
    }

    @Test(expected = MirthApiException.class)
    public void testDefaultsBlankType() {
        servlet.defaults("");
    }

    @Test(expected = MirthApiException.class)
    public void testDefaultsUnknownType() {
        servlet.defaults(UNKNOWN_CONNECTOR_TYPE);
    }

    @Test(expected = MirthApiException.class)
    public void testDefaultsDisabledExtension() {
        servlet.defaults(DISABLED_CONNECTOR_TYPE);
    }

    // ========== nextFireTime (IRT-1518) ==========

    @Test(expected = MirthApiException.class)
    public void testNextFireTimeBlankBody() {
        servlet.nextFireTime("");
    }

    @Test(expected = MirthApiException.class)
    public void testNextFireTimeInvalidXml() {
        servlet.nextFireTime("not valid xml at all");
    }

    @Test
    public void testNextFireTimeNormalSchedule() throws Exception {
        PollConnectorProperties properties = new PollConnectorProperties();
        properties.setPollingType(PollingType.INTERVAL);
        properties.setPollingFrequency(5000);
        String xml = ObjectXMLSerializer.getInstance().serialize(properties);

        Response response = servlet.nextFireTime(xml);
        assertEquals(200, response.getStatus());
        RawContent body = (RawContent) response.getEntity();
        assertNotNull(body);
        assertTrue(body.getContent().contains("nextFireTime"));
    }

    @Test(expected = MirthApiException.class)
    public void testNextFireTimePathologicalRestrictionRejected() throws Exception {
        // Regression guard for the DoS-class hang fixed in IRT-1518: an all-days-excluded weekly
        // restriction must be rejected with a 400, not hang the request thread.
        PollConnectorProperties properties = new PollConnectorProperties();
        properties.setPollingType(PollingType.INTERVAL);
        PollConnectorPropertiesAdvanced advanced = new PollConnectorPropertiesAdvanced();
        advanced.setWeekly(true);
        boolean[] allExcluded = new boolean[8];
        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            allExcluded[day] = true;
        }
        advanced.setActiveDays(allExcluded);
        properties.setPollConnectorPropertiesAdvanced(advanced);
        String xml = ObjectXMLSerializer.getInstance().serialize(properties);

        servlet.nextFireTime(xml);
    }

    /**
     * Inner class to bypass login initialization in tests.
     */
    public class TestConnectorServlet extends ConnectorServlet {
        public TestConnectorServlet(HttpServletRequest request, SecurityContext sc) {
            super(request, sc);
        }

        @Override
        protected boolean isUserAuthorized() {
            return true;
        }
    }
}
