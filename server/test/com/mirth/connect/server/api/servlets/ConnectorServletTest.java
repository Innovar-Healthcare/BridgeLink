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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
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
import com.mirth.connect.client.core.api.servlets.ConnectorServletInterface;
import com.mirth.connect.connectors.dimse.DICOMDispatcherProperties;
import com.mirth.connect.connectors.dimse.DICOMReceiverProperties;
import com.mirth.connect.connectors.doc.DocumentDispatcherProperties;
import com.mirth.connect.connectors.file.FileDispatcherProperties;
import com.mirth.connect.connectors.file.FileReceiverProperties;
import com.mirth.connect.connectors.http.HttpDispatcherProperties;
import com.mirth.connect.connectors.http.HttpReceiverProperties;
import com.mirth.connect.connectors.jdbc.DatabaseDispatcherProperties;
import com.mirth.connect.connectors.jdbc.DatabaseReceiverProperties;
import com.mirth.connect.connectors.jms.JmsDispatcherProperties;
import com.mirth.connect.connectors.jms.JmsReceiverProperties;
import com.mirth.connect.connectors.js.JavaScriptDispatcherProperties;
import com.mirth.connect.connectors.js.JavaScriptReceiverProperties;
import com.mirth.connect.connectors.smtp.SmtpDispatcherProperties;
import com.mirth.connect.connectors.tcp.TcpDispatcherProperties;
import com.mirth.connect.connectors.tcp.TcpReceiverProperties;
import com.mirth.connect.connectors.vm.VmDispatcherProperties;
import com.mirth.connect.connectors.vm.VmReceiverProperties;
import com.mirth.connect.connectors.ws.WebServiceDispatcherProperties;
import com.mirth.connect.connectors.ws.WebServiceReceiverProperties;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.CronProperty;
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
 * {@code POST /connectors/poll/_nextFireTime} (IRT-1518, IRT-1759).
 */
public class ConnectorServletTest extends ServletTestBase {

    private static final String CONNECTOR_TYPE = "Channel Writer";
    private static final String DISABLED_CONNECTOR_TYPE = "Disabled Connector";
    private static final String UNKNOWN_CONNECTOR_TYPE = "Unknown Connector";

    /**
     * The 16 connector types IRT-1663 (WebAdmin) plans to switch from hardcoded XML templates to
     * this endpoint, per the byte-parity check recorded in the IRT-1516 ticket: 11/20 were
     * structurally identical to the existing WebAdmin templates and 5/20 differed in ways where
     * this endpoint's output is authoritative (field order only, or the WebAdmin template was
     * simply wrong — see {@link #testFileReaderDefaultUsesUppercaseSchemeEnum} and
     * {@link #testFileWriterDefaultTemplateIsEmpty}).
     */
    private static final Map<String, Class<? extends ConnectorProperties>> ADOPTABLE_CONNECTORS = new LinkedHashMap<>();
    static {
        ADOPTABLE_CONNECTORS.put("Channel Reader", VmReceiverProperties.class);
        ADOPTABLE_CONNECTORS.put("Database Reader", DatabaseReceiverProperties.class);
        ADOPTABLE_CONNECTORS.put("DICOM Listener", DICOMReceiverProperties.class);
        ADOPTABLE_CONNECTORS.put("File Reader", FileReceiverProperties.class);
        ADOPTABLE_CONNECTORS.put("JavaScript Reader", JavaScriptReceiverProperties.class);
        ADOPTABLE_CONNECTORS.put("JMS Listener", JmsReceiverProperties.class);
        ADOPTABLE_CONNECTORS.put("Channel Writer", VmDispatcherProperties.class);
        ADOPTABLE_CONNECTORS.put("Database Writer", DatabaseDispatcherProperties.class);
        ADOPTABLE_CONNECTORS.put("DICOM Sender", DICOMDispatcherProperties.class);
        ADOPTABLE_CONNECTORS.put("Document Writer", DocumentDispatcherProperties.class);
        ADOPTABLE_CONNECTORS.put("File Writer", FileDispatcherProperties.class);
        ADOPTABLE_CONNECTORS.put("HTTP Sender", HttpDispatcherProperties.class);
        ADOPTABLE_CONNECTORS.put("JavaScript Writer", JavaScriptDispatcherProperties.class);
        ADOPTABLE_CONNECTORS.put("JMS Sender", JmsDispatcherProperties.class);
        ADOPTABLE_CONNECTORS.put("SMTP Sender", SmtpDispatcherProperties.class);
        ADOPTABLE_CONNECTORS.put("Web Service Sender", WebServiceDispatcherProperties.class);
    }

    /**
     * The 4 connector types IRT-1516 flagged as NOT yet safe to adopt: a bare {@code new
     * XxxProperties()} is missing defaults that are normally injected by a connector-property
     * plugin (httpauth, mllpmode) in the full client/save path. These tests characterize —
     * pin down, not endorse — the current gap so a future change to either the plugin wiring or
     * this endpoint's assembler is a deliberate, visible diff instead of a silent behavior change
     * that WebAdmin (once it adopts these too) would inherit unnoticed.
     */
    private static final Map<String, Class<? extends ConnectorProperties>> GROUP_C_CONNECTORS = new LinkedHashMap<>();
    static {
        GROUP_C_CONNECTORS.put("HTTP Listener", HttpReceiverProperties.class);
        GROUP_C_CONNECTORS.put("Web Service Listener", WebServiceReceiverProperties.class);
        GROUP_C_CONNECTORS.put("TCP Listener", TcpReceiverProperties.class);
        GROUP_C_CONNECTORS.put("TCP Sender", TcpDispatcherProperties.class);
    }

    private static ExtensionController mockExtensionController;
    private ConnectorServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        // ServletTestBase does not initialize ObjectXMLSerializer. Without this, MigratableConverter
        // is never registered, so defaults() would silently stop stamping the version attribute
        // whenever this test class runs in isolation (it only "passed" before by inheriting
        // initialization from whichever other test class's @BeforeClass happened to run first in
        // the same JVM) - see ObjectXMLSerializerTest/ConnectorPropertiesUtilTest for the same guard.
        try {
            ObjectXMLSerializer.getInstance().init(com.mirth.connect.client.core.Version.getLatest().toString());
        } catch (Exception e) {
            // Already initialized by another test class
        }

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

        for (Map<String, Class<? extends ConnectorProperties>> group : new Map[] { ADOPTABLE_CONNECTORS, GROUP_C_CONNECTORS }) {
            for (Map.Entry<String, Class<? extends ConnectorProperties>> entry : group.entrySet()) {
                ConnectorMetaData meta = new ConnectorMetaData();
                meta.setName(entry.getKey());
                meta.setSharedClassName(entry.getValue().getName());
                doReturn(meta).when(mockExtensionController).getConnectorMetaDataByTransportName(entry.getKey());
                doReturn(true).when(mockExtensionController).isExtensionEnabled(entry.getKey());
            }
        }

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

    /**
     * IRT-1669's original coverage only exercised {@code defaults()} against
     * {@link VmDispatcherProperties} — one of the 20 connector types the endpoint serves. Loop
     * over all 16 IRT-1663 plans to adopt so a future change to any one connector's default
     * constructor (a field rename, a new required child element, etc.) fails a test here instead
     * of silently reaching WebAdmin.
     */
    @Test
    public void testDefaultsForAllAdoptableConnectorTypes() {
        for (Map.Entry<String, Class<? extends ConnectorProperties>> entry : ADOPTABLE_CONNECTORS.entrySet()) {
            String type = entry.getKey();
            Response response = servlet.defaults(type);
            assertEquals(type + ": expected 200", 200, response.getStatus());

            String xml = ((RawContent) response.getEntity()).getContent();
            assertTrue(type + ": missing <properties root element", xml.contains("<properties"));
            assertTrue(type + ": missing class attribute for " + entry.getValue().getName(), xml.contains("class=\"" + entry.getValue().getName() + "\""));
            assertTrue(type + ": missing version attribute (WebAdmin relies on this being stamped server-side)", xml.contains(" version=\""));
        }
    }

    /**
     * Byte-parity finding from IRT-1516: the WebAdmin hardcoded template used
     * {@code <scheme>file</scheme>} (lowercase), but the real Java enum value serialized by this
     * endpoint is uppercase. This endpoint is authoritative; pin the correct value down so it
     * can't silently regress back to a case that WebAdmin's File Reader/Writer panels wouldn't
     * recognize.
     */
    @Test
    public void testFileReaderDefaultUsesUppercaseSchemeEnum() {
        Response response = servlet.defaults("File Reader");
        String xml = ((RawContent) response.getEntity()).getContent();
        assertTrue(xml.contains("<scheme>FILE</scheme>"));
    }

    /**
     * Second byte-parity finding from IRT-1516: the WebAdmin template pre-filled
     * {@code <template>} with {@code ${message.encodedData}}, but the real Core default for File
     * Writer is empty — the template field is meant to be blank until the user fills it in.
     */
    @Test
    public void testFileWriterDefaultTemplateIsEmpty() {
        Response response = servlet.defaults("File Writer");
        String xml = ((RawContent) response.getEntity()).getContent();
        assertTrue(xml.contains("<scheme>FILE</scheme>"));
        assertTrue("Expected an empty <template/> default, not a pre-filled value", xml.contains("<template/>"));
    }

    // ========== defaults content-type / contract gap coverage (IRT-1516, public #173 pattern) ==========

    /**
     * #173 regression guard: {@code ConnectorServletInterface.defaults}'s {@code @Produces} admits
     * {@code application/json} so WebAdmin's {@code Accept: application/json} fetch does not 406,
     * but the actual response Content-Type must always stay pinned to {@code application/xml} - a
     * raw {@code <properties class="...">} document, never the JSON envelope. Neither
     * {@link #testDefaultsReturnsConnectorPropertiesXml} nor
     * {@link #testDefaultsForAllAdoptableConnectorTypes} assert
     * {@link Response#getMediaType()}, so a future edit that drops the explicit
     * {@code MediaType.APPLICATION_XML_TYPE} argument from {@code ConnectorServlet.defaults}'s
     * {@code Response.ok(...)} call would pass the shipped suite silently.
     */
    @Test
    public void testDefaultsMediaTypePinnedToXml() {
        Response response = servlet.defaults(CONNECTOR_TYPE);
        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_XML_TYPE, response.getMediaType());
        RawContent body = (RawContent) response.getEntity();
        assertTrue("expected the serialized connector properties class attribute", body.getContent().contains("class=\""));
    }

    /**
     * 406-avoidance contract guard: even though {@code defaults} always pins
     * {@code application/xml} on the wire (see {@link #testDefaultsMediaTypePinnedToXml}), the
     * interface's {@code @Produces} must keep advertising {@code application/json} so a
     * JSON-preferring client is not rejected with 406 Not Acceptable before the resource method
     * ever runs. Reads the annotation directly off {@link ConnectorServletInterface#defaults} (with
     * a type-level fallback, since JAX-RS permits {@code @Produces} at either level) rather than
     * duplicating the media-type list as a literal, so the guard tracks the real contract.
     */
    @Test
    public void testDefaultsInterfaceProducesIncludesJson() throws Exception {
        Method defaultsMethod = ConnectorServletInterface.class.getMethod("defaults", String.class);
        Produces produces = defaultsMethod.getAnnotation(Produces.class);
        if (produces == null) {
            produces = ConnectorServletInterface.class.getAnnotation(Produces.class);
        }
        assertNotNull("defaults() must carry a @Produces annotation (method- or type-level)", produces);
        List<String> producedTypes = Arrays.asList(produces.value());
        assertTrue("406-avoidance: @Produces must still list application/json", producedTypes.contains(MediaType.APPLICATION_JSON));
    }

    /**
     * Explicit status-code pin for the unknown-type leg: {@link #testDefaultsUnknownType} only
     * asserts the exception type via {@code @Test(expected = ...)}, which would still pass if the
     * servlet ever started throwing {@code MirthApiException} with a different (e.g. 500) status
     * for an unknown connector type. Assert the 404 explicitly.
     */
    @Test
    public void testDefaultsUnknownTypeReturns404() {
        try {
            servlet.defaults(UNKNOWN_CONNECTOR_TYPE);
            fail("expected a 404 Not Found for an unknown connector type");
        } catch (MirthApiException e) {
            assertEquals(404, e.getResponse().getStatus());
        }
    }

    /**
     * IRT-1516 documenting test (accepted-risk security note, not a vulnerability): {@code defaults}
     * carries {@code @MirthOperation(..., auditable = false)} and its implementation never calls
     * {@code checkUserAuthorized()} / {@code isUserAuthorized()} - it is intentionally reachable
     * without any permission gate, because the data returned is a static, non-PHI connector-defaults
     * shape (the same shape WebAdmin needs pre-auth-scoping). Every other test in this class runs
     * against {@link TestConnectorServlet}, which defensively overrides {@code isUserAuthorized()}
     * to {@code true} - that override is never exercised by {@code defaults}, so this test
     * deliberately uses a servlet subclass with NO such override to prove the endpoint needs none.
     * If this test ever starts failing because {@code defaults} began calling
     * {@code checkUserAuthorized()}, that is a deliberate access-control change to review, not a
     * regression to "fix" by re-adding an override here.
     */
    @Test
    public void testDefaultsUngatedWithoutAuthBypassOverride() {
        ConnectorServlet ungatedServlet = new ConnectorServlet(request, mock(SecurityContext.class)) {
            // Intentionally no isUserAuthorized() override - documents that defaults() never
            // consults it.
        };

        Response response = ungatedServlet.defaults(CONNECTOR_TYPE);
        assertEquals("IRT-1516: defaults() must stay reachable with no permission gate installed", 200, response.getStatus());
    }

    // ========== nextFireTime content-type gap coverage (IRT-1518/IRT-1759) ==========

    /**
     * Companion media-type pin to {@link #testNextFireTimeValidCronSchedule}: the valid-cron path
     * must keep returning {@code application/json} (never falling back to the XML envelope the way
     * {@code defaults} intentionally pins XML).
     */
    @Test
    public void testNextFireTimeValidCronMediaTypePinnedToJson() throws Exception {
        PollConnectorProperties properties = new PollConnectorProperties();
        properties.setPollingType(PollingType.CRON);
        List<CronProperty> cronJobs = new ArrayList<CronProperty>();
        cronJobs.add(new CronProperty("noon daily", "0 0 12 * * ?"));
        properties.setCronJobs(cronJobs);
        String xml = ObjectXMLSerializer.getInstance().serialize(properties);

        Response response = servlet.nextFireTime(xml);
        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    }

    // ========== defaults — known IRT-1516 group C gap (NOT in ADOPTABLE_CONNECTORS) ==========

    /**
     * Characterization test, not an endorsement: a bare {@code new HttpReceiverProperties()} /
     * {@code new WebServiceReceiverProperties()} has an empty {@code pluginProperties}, because
     * the httpauth plugin's {@code NoneHttpAuthProperties} default is normally injected by the
     * full client/save path, not by the properties class's own constructor. IRT-1516 flagged this
     * as why these 2 connectors are NOT in the 16 WebAdmin can adopt today. If this test starts
     * failing because {@code pluginProperties} now contains "httpauth", that's good news — it
     * means the assembler gap closed and these 2 can move into {@link #ADOPTABLE_CONNECTORS}.
     */
    @Test
    public void testHttpAndWebServiceListenerDefaultsOmitHttpAuthPluginDefault() {
        for (String type : new String[] { "HTTP Listener", "Web Service Listener" }) {
            Response response = servlet.defaults(type);
            String xml = ((RawContent) response.getEntity()).getContent();
            assertTrue(type + ": expected an empty <pluginProperties/>", xml.contains("<pluginProperties/>"));
            assertFalse(type + ": KNOWN GAP (IRT-1516) appears to have closed - httpauth default now present; consider moving this connector into ADOPTABLE_CONNECTORS", xml.toLowerCase().contains("httpauth"));
        }
    }

    /**
     * Characterization test, not an endorsement: a bare {@code new TcpReceiverProperties()} /
     * {@code new TcpDispatcherProperties()} default to a bare
     * {@code com.mirth.connect.model.transmission.framemode.FrameModeProperties} (pluginPointName
     * "MLLP"), not the richer {@code com.mirth.connect.plugins.mllpmode.MLLPModeProperties} the
     * mllpmode plugin normally supplies via the full client/save path. TCP Listener additionally
     * drops {@code responseConnectorPluginProperties} entirely. IRT-1516 flagged this as why these
     * 2 connectors are NOT in the 16 WebAdmin can adopt today.
     */
    @Test
    public void testTcpConnectorsDefaultToBareFrameModeNotMllpModePlugin() {
        for (String type : new String[] { "TCP Listener", "TCP Sender" }) {
            Response response = servlet.defaults(type);
            String xml = ((RawContent) response.getEntity()).getContent();
            assertTrue(type + ": expected bare FrameModeProperties as the transmission mode default", xml.contains("class=\"com.mirth.connect.model.transmission.framemode.FrameModeProperties\""));
            assertFalse(type + ": KNOWN GAP (IRT-1516) appears to have closed - MLLPModeProperties now present; consider moving this connector into ADOPTABLE_CONNECTORS", xml.contains("MLLPModeProperties"));
        }

        Response listenerResponse = servlet.defaults("TCP Listener");
        String listenerXml = ((RawContent) listenerResponse.getEntity()).getContent();
        assertFalse("TCP Listener: KNOWN GAP (IRT-1516) appears to have closed - responseConnectorPluginProperties now present", listenerXml.contains("responseConnectorPluginProperties"));
    }

    // ========== nextFireTime (IRT-1518, IRT-1759) ==========

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

    @Test
    public void testNextFireTimeQuartzInvalidCronRejectedAs400() throws Exception {
        // IRT-1759: a CRON expression that is structurally invalid to Quartz - here specifying BOTH
        // a day-of-month (1) AND a day-of-week (1) - must degrade to a 400 Bad Request, mirroring
        // _validateCron, rather than escaping the unguarded CronScheduleBuilder.cronSchedule(...)
        // call in configureJob as a RuntimeException and surfacing to the client as a 500.
        PollConnectorProperties properties = new PollConnectorProperties();
        properties.setPollingType(PollingType.CRON);
        List<CronProperty> cronJobs = new ArrayList<CronProperty>();
        cronJobs.add(new CronProperty("both DOW and DOM", "0 0 12 1 * 1"));
        properties.setCronJobs(cronJobs);
        String xml = ObjectXMLSerializer.getInstance().serialize(properties);

        try {
            servlet.nextFireTime(xml);
            fail("expected a 400 Bad Request for a Quartz-invalid cron expression");
        } catch (MirthApiException e) {
            assertEquals("Quartz-invalid cron must be a 400, not a 500", 400, e.getResponse().getStatus());
        }
    }

    @Test
    public void testNextFireTimeValidCronSchedule() throws Exception {
        PollConnectorProperties properties = new PollConnectorProperties();
        properties.setPollingType(PollingType.CRON);
        List<CronProperty> cronJobs = new ArrayList<CronProperty>();
        cronJobs.add(new CronProperty("noon daily", "0 0 12 * * ?"));
        properties.setCronJobs(cronJobs);
        String xml = ObjectXMLSerializer.getInstance().serialize(properties);

        Response response = servlet.nextFireTime(xml);
        assertEquals(200, response.getStatus());
        RawContent body = (RawContent) response.getEntity();
        assertNotNull(body);
        assertTrue(body.getContent().contains("nextFireTime"));
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
