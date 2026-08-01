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

package com.mirth.connect.client.core.api.servlets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;

@Path("/connectors")
@Tag(name = "Connectors")
@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
public interface ConnectorServletInterface extends BaseServletInterface {

    /*
     * Produces declares JSON as well so JSON-accepting clients (the WebAdmin fetch wrapper sends
     * Accept: application/json) are not rejected with 406; the implementation pins the actual
     * response Content-Type to application/xml.
     */
    @GET
    @Path("/{type}/defaults")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Operation(summary = "Returns freshly instantiated, version-stamped default properties for a built-in connector transport, serialized in the same <properties class=\"...\"> form used inside saved channel XML. Raw XML response (Content-Type application/xml), not the standard serialized envelope. Returns 404 when the transport is unknown or its extension is disabled.")
    @MirthOperation(name = "getConnectorDefaults", display = "Get connector defaults", auditable = false)
    public Response defaults(
            @Param("type") @Parameter(description = "The connector transport name to instantiate defaults for, e.g. \"HTTP Sender\", \"DICOM Listener\", \"Channel Reader\". URL-encode spaces (HTTP%20Sender).", required = true) @PathParam("type") String type) throws ClientException;

    /*
     * Produces declares only JSON here since the actual response is always JSON; no 406-avoidance
     * wrapping is needed (unlike the raw-XML endpoints above).
     */
    @POST
    @Path("/poll/_nextFireTime")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Computes the next poll fire time for a pollConnectorProperties configuration using the server's real Quartz scheduler machinery (no Scheduler thread pool is started). Body is a <pollConnectorProperties> XML fragment, the same form embedded in channel XML. Plain JSON response, not the standard serialized envelope.")
    @MirthOperation(name = "getNextFireTime", display = "Get next poll fire time", auditable = false)
    public Response nextFireTime(@Param("pollConnectorProperties") @RequestBody(description = "The <pollConnectorProperties> XML fragment, the same form embedded in channel XML.", required = true, content = @Content(mediaType = MediaType.APPLICATION_XML, schema = @Schema(implementation = String.class))) String pollConnectorPropertiesXml) throws ClientException;
}
