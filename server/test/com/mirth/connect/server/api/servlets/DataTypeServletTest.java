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

import static org.mockito.Mockito.mock;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.SecurityContext;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.server.api.ServletTestBase;

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

    private DataTypeServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();
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
