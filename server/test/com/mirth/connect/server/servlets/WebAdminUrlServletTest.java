/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) NextGen Healthcare. All rights reserved.
 * https://www.nextgen.com/products-and-services/integration-engine
 *
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.servlets;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.Test;

/**
 * Servlet-unit coverage for {@link WebAdminUrlServlet} (REQ-26.2-195-test). {@code
 * WebAdminUrlServlet} is a plain unauthenticated {@link javax.servlet.http.HttpServlet} — it does
 * NOT redirect the client anywhere. {@code doGet} serves the configured {@code webadmin.url}
 * property as a JSON body ({@code {"url":"<value>"}}) with {@code Content-Type: application/json},
 * {@code Cache-Control: no-store}, and {@code X-Content-Type-Options: nosniff}. This test
 * therefore asserts the JSON body + security-header contract only — it must NOT assert any
 * redirect call or 3xx status, since the servlet emits neither.
 *
 * <p>Modeled on {@link WebStartServletTest} / the RESEARCH.md code example (same package, same
 * plain-{@code HttpServlet} + {@link PropertiesConfiguration} constructor shape) rather than
 * {@code ExtensionWebAdminServletTest}/{@code ServletTestBase} (Guice + auth) — the wrong weight
 * class for a bare, unauthenticated servlet. A pure Mockito mock of {@code HttpServletResponse}
 * (rather than a hand-rolled fake implementing the full interface) is used here so the test
 * never needs to reference any redirect method at all — the servlet under test emits none.
 */
public class WebAdminUrlServletTest {

    @Test
    public void testDoGetBlankUrlReturnsEmptyJson() throws Exception {
        PropertiesConfiguration props = new PropertiesConfiguration();
        // webadmin.url intentionally left unset — exercises the blank/absent-property default.

        WebAdminUrlServlet servlet = new WebAdminUrlServlet(props);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(capturingStream(captured));

        servlet.doGet(request, response);

        assertEquals("{\"url\":\"\"}", captured.toString(StandardCharsets.UTF_8));
        verify(response).setContentType("application/json");
        verify(response).setHeader("Cache-Control", "no-store");
        verify(response).setHeader("Pragma", "no-cache");
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
    }

    @Test
    public void testDoGetConfiguredUrlReturnsValueJson() throws Exception {
        PropertiesConfiguration props = new PropertiesConfiguration();
        props.setProperty("webadmin.url", "https://webadmin.example.com");

        WebAdminUrlServlet servlet = new WebAdminUrlServlet(props);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(capturingStream(captured));

        servlet.doGet(request, response);

        assertEquals("{\"url\":\"https://webadmin.example.com\"}", captured.toString(StandardCharsets.UTF_8));
        verify(response).setContentType("application/json");
        verify(response).setHeader("Cache-Control", "no-store");
        verify(response).setHeader("Pragma", "no-cache");
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
    }

    @Test
    public void testDoGetConfiguredUrlIsTrimmed() throws Exception {
        PropertiesConfiguration props = new PropertiesConfiguration();
        props.setProperty("webadmin.url", "  https://webadmin.example.com  ");

        WebAdminUrlServlet servlet = new WebAdminUrlServlet(props);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(capturingStream(captured));

        servlet.doGet(request, response);

        assertEquals("{\"url\":\"https://webadmin.example.com\"}", captured.toString(StandardCharsets.UTF_8));
    }

    /** A minimal {@link ServletOutputStream} that copies every written byte into {@code sink}. */
    private static ServletOutputStream capturingStream(ByteArrayOutputStream sink) {
        return new ServletOutputStream() {
            @Override
            public void write(int b) {
                sink.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                // not needed for a synchronous test double
            }
        };
    }
}
