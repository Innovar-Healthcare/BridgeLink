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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serves the optional "webadmin.url" property to the static landing page, which uses it to link
 * to the BridgeLink Web Administrator serving this server.
 *
 * The value cannot be derived by the server: the Web Administrator is a separate install, may run
 * on another host, and its own configuration file is outside this server's install root.
 *
 * Like the landing page itself, this endpoint is unauthenticated, so it must expose nothing from
 * mirth.properties beyond this single admin-configured URL.
 */
public class WebAdminUrlServlet extends HttpServlet {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String webAdminUrl;

    public WebAdminUrlServlet(PropertiesConfiguration mirthProperties) {
        webAdminUrl = StringUtils.trimToEmpty(mirthProperties.getString("webadmin.url", ""));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");

        byte[] bytes = OBJECT_MAPPER.writeValueAsString(Collections.singletonMap("url", webAdminUrl)).getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }
}
