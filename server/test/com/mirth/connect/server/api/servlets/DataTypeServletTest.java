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
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
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

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.client.core.api.RawContent;
import com.mirth.connect.client.core.api.servlets.DataTypeServletInterface;
import com.mirth.connect.donkey.model.message.MessageSerializer;
import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.donkey.model.message.SerializationType;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.model.converters.IMessageSerializer;
import com.mirth.connect.model.datatype.DataTypeDelegate;
import com.mirth.connect.model.datatype.DataTypeProperties;
import com.mirth.connect.model.datatype.SerializerProperties;
import com.mirth.connect.plugins.DataTypeServerPlugin;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.userutil.SerializerFactory;

/**
 * Covers {@code POST /datatypes/_toTree}, {@code POST /datatypes/_serialize}, and
 * {@code GET /datatypes/defaultProperties} (IRT-1515).
 *
 * <p>
 * Scope note: every method here resolves an {@link com.mirth.connect.server.userutil.SerializerFactory}
 * serializer, which reads from a data-type plugin registry populated by the full extension-loading
 * subsystem at server startup ({@code SerializerFactory}'s plugin map is a static field initialized
 * from {@code ExtensionController.getDataTypePlugins()}, not something this lightweight servlet-test
 * harness bootstraps). Only the input-validation guards - which return before ever touching
 * {@code SerializerFactory} - are covered here. A real toXML/fromXML round-trip through an actual
 * data type plugin (HL7v2, DELIMITED, etc.) needs a heavier integration fixture than exists elsewhere
 * in this test suite and is intentionally left out rather than faked.
 * </p>
 */
public class DataTypeServletTest extends ServletTestBase {

    /**
     * A synthetic data type key registered directly into {@link SerializerFactory}'s plugin
     * registry (via reflection, see {@link #registerGapTestDataTypePlugin()}) so the media-type gap
     * tests below can exercise the actual success path (_toTree, _serialize, defaultProperties)
     * without depending on the full extension-loading subsystem the class javadoc above documents
     * as out of scope for this harness.
     */
    private static final String GAP_DATA_TYPE = "GAPTESTTYPE";

    private DataTypeServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();
        registerGapTestDataTypePlugin();
    }

    /**
     * Reflectively injects a minimal, deterministic {@link DataTypeServerPlugin} into
     * {@link SerializerFactory}'s plugin registry. That registry is a private static field
     * populated once from the full extension-loading subsystem
     * ({@code ExtensionController.getDataTypePlugins()}) - not something this lightweight
     * servlet-test harness bootstraps (see class javadoc). Referencing {@link SerializerFactory}
     * here still triggers its normal (real) static initialization first - already proven not to
     * throw by the shipped blank/null-dataType tests below and by Plan 01's green suite run - after
     * which this method augments the resulting map with one synthetic, fully-controlled data type
     * so the content-type gap tests can reach the real success path (RawContent/text response
     * construction) without touching any real data-type plugin or servlet/util source.
     */
    private static void registerGapTestDataTypePlugin() throws Exception {
        Field dataPluginsField = SerializerFactory.class.getDeclaredField("dataPlugins");
        dataPluginsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, DataTypeServerPlugin> dataPlugins = (Map<String, DataTypeServerPlugin>) dataPluginsField.get(null);
        dataPlugins.put(GAP_DATA_TYPE, new GapTestDataTypeServerPlugin());
    }

    @Before
    public void beforeTest() {
        servlet = new TestDataTypeServlet(request, mock(SecurityContext.class));
    }

    // ========== toTree ==========

    @Test(expected = MirthApiException.class)
    public void testToTreeBlankDataType() {
        servlet.toTree("", "some message");
    }

    @Test(expected = MirthApiException.class)
    public void testToTreeNullDataType() {
        servlet.toTree(null, "some message");
    }

    // ========== serialize ==========

    @Test(expected = MirthApiException.class)
    public void testSerializeBlankDirection() {
        // Direction is checked before dataType is resolved, so this exercises the guard without
        // ever reaching SerializerFactory.
        servlet.serialize("HL7V2", "", "some message");
    }

    @Test(expected = MirthApiException.class)
    public void testSerializeNullDirection() {
        servlet.serialize("HL7V2", null, "some message");
    }

    // ========== defaultProperties ==========

    @Test(expected = MirthApiException.class)
    public void testDefaultPropertiesBlankDataType() {
        servlet.defaultProperties("");
    }

    @Test(expected = MirthApiException.class)
    public void testDefaultPropertiesNullDataType() {
        servlet.defaultProperties(null);
    }

    // ========== content-type gap coverage (Plan 26.5-02, #173-class regression) ==========

    /**
     * {@code _toTree}'s {@code @Produces} admits {@code application/json} (WebAdmin's fetch wrapper
     * sends {@code Accept: application/json}), but the actual response Content-Type must always
     * stay pinned to {@code application/xml} - a raw message-tree document, never the JSON
     * envelope. Neither of the shipped blank/null-dataType tests above assert
     * {@link Response#getMediaType()}, so a future edit that dropped the explicit
     * {@code MediaType.APPLICATION_XML_TYPE} argument from {@code DataTypeServlet.toTree}'s
     * {@code Response.ok(...)} call would pass the shipped suite silently.
     */
    @Test
    public void testToTreeMediaTypePinnedToXml() {
        Response response = servlet.toTree(GAP_DATA_TYPE, "hello");
        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_XML_TYPE, response.getMediaType());
        RawContent body = (RawContent) response.getEntity();
        assertEquals("<tree>hello</tree>", body.getContent());
    }

    /**
     * 406-avoidance contract guard: even though {@code toTree} always pins
     * {@code application/xml} on the wire (see {@link #testToTreeMediaTypePinnedToXml}), the
     * interface's {@code @Produces} must keep advertising {@code application/json} so a
     * JSON-preferring client is not rejected with 406 before the resource method ever runs.
     */
    @Test
    public void testToTreeInterfaceProducesIncludesJson() throws Exception {
        Method toTreeMethod = DataTypeServletInterface.class.getMethod("toTree", String.class, String.class);
        Produces produces = toTreeMethod.getAnnotation(Produces.class);
        if (produces == null) {
            produces = DataTypeServletInterface.class.getAnnotation(Produces.class);
        }
        assertNotNull("toTree() must carry a @Produces annotation (method- or type-level)", produces);
        List<String> producedTypes = Arrays.asList(produces.value());
        assertTrue("406-avoidance: @Produces must still list application/json", producedTypes.contains(MediaType.APPLICATION_JSON));
    }

    /**
     * {@code _serialize}'s media type varies PER DIRECTION: {@code toXML} parses to a message tree
     * (application/xml, RawContent), while {@code fromXML} renders back to the raw message format
     * (text/plain, a bare String - RawContent's writer only advertises XML/JSON, per RESEARCH
     * Pitfall 5). This test pins the toXML leg.
     */
    @Test
    public void testSerializeToXmlDirectionMediaTypePinnedToXml() {
        Response response = servlet.serialize(GAP_DATA_TYPE, "toXML", "hello");
        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_XML_TYPE, response.getMediaType());
        RawContent body = (RawContent) response.getEntity();
        assertEquals("<tree>hello</tree>", body.getContent());
    }

    /**
     * Companion leg of {@link #testSerializeToXmlDirectionMediaTypePinnedToXml}: {@code fromXML}
     * must pin {@code text/plain} and its entity must be a bare {@code String}, NOT a
     * {@link RawContent} - RawContent's {@code MessageBodyWriter} only advertises XML/JSON, so
     * wrapping this direction in it would break the response at write time (RESEARCH Pitfall 5 /
     * PATTERNS "RawContent entity extraction").
     */
    @Test
    public void testSerializeFromXmlDirectionMediaTypePinnedToTextPlainAsBareString() {
        Response response = servlet.serialize(GAP_DATA_TYPE, "fromXML", "hello");
        assertEquals(200, response.getStatus());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getMediaType());
        String body = (String) response.getEntity();
        assertEquals("raw-hello", body);
    }

    /**
     * 406-avoidance contract guard for {@code _serialize}, mirroring
     * {@link #testToTreeInterfaceProducesIncludesJson}.
     */
    @Test
    public void testSerializeInterfaceProducesIncludesJson() throws Exception {
        Method serializeMethod = DataTypeServletInterface.class.getMethod("serialize", String.class, String.class, String.class);
        Produces produces = serializeMethod.getAnnotation(Produces.class);
        if (produces == null) {
            produces = DataTypeServletInterface.class.getAnnotation(Produces.class);
        }
        assertNotNull("serialize() must carry a @Produces annotation (method- or type-level)", produces);
        List<String> producedTypes = Arrays.asList(produces.value());
        assertTrue("406-avoidance: @Produces must still list application/json", producedTypes.contains(MediaType.APPLICATION_JSON));
    }

    /**
     * {@code defaultProperties} always returns a plain JSON property-shape map via
     * {@link com.mirth.connect.client.core.api.RawContent}; pinning it closes out the per-endpoint
     * media-type coverage for this servlet alongside {@code toTree}/{@code serialize} above.
     */
    @Test
    public void testDefaultPropertiesMediaTypePinnedToJson() {
        Response response = servlet.defaultProperties(GAP_DATA_TYPE);
        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        RawContent body = (RawContent) response.getEntity();
        assertNotNull(body.getContent());
    }

    /**
     * Minimal, deterministic {@link DataTypeServerPlugin} used only to exercise the success path of
     * this servlet's endpoints (see {@link #registerGapTestDataTypePlugin()}). Not a real data type
     * plugin - it performs no actual serialization logic, only returns fixed, recognizable strings
     * so the tests above can assert on both media type and body content.
     */
    private static class GapTestDataTypeServerPlugin extends DataTypeServerPlugin {

        private final DataTypeDelegate delegate = new GapTestDataTypeDelegate();

        @Override
        public String getPluginPointName() {
            return GAP_DATA_TYPE;
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        protected DataTypeDelegate getDataTypeDelegate() {
            return delegate;
        }
    }

    private static class GapTestDataTypeDelegate implements DataTypeDelegate {

        @Override
        public String getName() {
            return GAP_DATA_TYPE;
        }

        @Override
        public IMessageSerializer getSerializer(SerializerProperties properties) {
            return new GapTestSerializer();
        }

        @Override
        public boolean isBinary() {
            return false;
        }

        @Override
        public SerializationType getDefaultSerializationType() {
            return SerializationType.RAW;
        }

        @Override
        public DataTypeProperties getDefaultProperties() {
            return new GapTestDataTypeProperties();
        }
    }

    private static class GapTestSerializer implements IMessageSerializer {

        @Override
        public boolean isSerializationRequired(boolean toXml) {
            return false;
        }

        @Override
        public String transformWithoutSerializing(String message, MessageSerializer outboundSerializer) {
            return null;
        }

        @Override
        public String toXML(String source) throws MessageSerializerException {
            return "<tree>" + source + "</tree>";
        }

        @Override
        public String fromXML(String source) throws MessageSerializerException {
            return "raw-" + source;
        }

        @Override
        public Map<String, Object> getMetaDataFromMessage(String message) {
            return null;
        }

        @Override
        public void populateMetaData(String message, Map<String, Object> map) {}

        @Override
        public String toJSON(String message) throws MessageSerializerException {
            return null;
        }

        @Override
        public String fromJSON(String message) throws MessageSerializerException {
            return null;
        }
    }

    private static class GapTestDataTypeProperties extends DataTypeProperties {

        @Override
        public void migrate3_0_1(DonkeyElement element) {}

        @Override
        public void migrate3_0_2(DonkeyElement element) {}

        @Override
        public void migrate3_1_0(DonkeyElement element) {}

        @Override
        public Map<String, Object> getPurgedProperties() {
            return new HashMap<String, Object>();
        }
    }

    /**
     * Inner class to bypass login initialization in tests.
     */
    public class TestDataTypeServlet extends DataTypeServlet {
        public TestDataTypeServlet(HttpServletRequest request, SecurityContext sc) {
            super(request, sc);
        }

        @Override
        protected boolean isUserAuthorized() {
            return true;
        }
    }
}
