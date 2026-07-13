/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * Sample extension for the declarative WebAdmin plugin UI (IRT-1422).
 */

package com.mirth.connect.connectors.mocksmtp;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.util.ConnectionTestResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The action-button endpoint declared by webadmin/webadmin.json. Per the WebAdmin plugin
 * contract, action endpoints live under the contributing extension's own /extensions/<path>/
 * namespace and are called on the user's existing session.
 */
@Path("/extensions/mock-smtp")
@Tag(name = "Extension Services")
@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
public interface MockSmtpServletInterface extends BaseServletInterface {

    public static final String PLUGIN_POINT = "Mock SMTP Connector Service";

    @POST
    @Path("/webadmin/actions/test-connection")
    @Operation(summary = "Simulates an SMTP connection test. Always succeeds; performs no I/O.")
    @MirthOperation(name = "mockSmtpTestConnection", display = "Mock SMTP test connection", auditable = false)
    public ConnectionTestResponse testConnection() throws ClientException;
}
