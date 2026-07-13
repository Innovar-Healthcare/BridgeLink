/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
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
