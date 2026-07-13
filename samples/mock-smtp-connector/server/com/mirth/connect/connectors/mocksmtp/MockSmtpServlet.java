/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * Sample extension for the declarative WebAdmin plugin UI (IRT-1422).
 */

package com.mirth.connect.connectors.mocksmtp;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.util.ConnectionTestResponse;

public class MockSmtpServlet extends MirthServlet implements MockSmtpServletInterface {

    public MockSmtpServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, PLUGIN_POINT);
    }

    @Override
    public ConnectionTestResponse testConnection() {
        return new ConnectionTestResponse(ConnectionTestResponse.Type.SUCCESS, "Mock SMTP: connection test succeeded (no I/O performed).");
    }
}
