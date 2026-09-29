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
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.Operation.ExecuteType;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;

@Path("/datatypes")
@Tag(name = "Data Types")
@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
public interface DataTypeServletInterface extends BaseServletInterface {

    /*
     * Produces declares JSON as well so JSON-accepting clients (the WebAdmin fetch wrapper sends
     * Accept: application/json) are not rejected with 406; the implementation pins the actual
     * response Content-Type to application/xml.
     */
    @POST
    @Path("/_toTree")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Operation(summary = "Parses an inbound message using the server's data type serializer and returns the message tree as XML, for building a read-only message preview. Raw XML response (Content-Type application/xml), not the standard serialized envelope.")
    @MirthOperation(name = "getMessageTree", display = "Get message tree", type = ExecuteType.ASYNC, auditable = false)
    public Response toTree(
            @Param("dataType") @Parameter(description = "The data type plugin point to parse with, e.g. HL7V2, DELIMITED, NCPDP, EDI/X12, DICOM. Case-insensitive.", required = true) @QueryParam("dataType") String dataType,
            @Param("message") @RequestBody(description = "The raw inbound message to parse. Default serialization properties for the data type are used.", required = true, content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(implementation = String.class))) String message) throws ClientException;

    /*
     * Produces declares JSON as well so JSON-accepting clients (the WebAdmin fetch wrapper sends
     * Accept: application/json) are not rejected with 406; the implementation pins the actual
     * response Content-Type per direction (application/xml for toXML, text/plain for fromXML).
     */
    @POST
    @Path("/_serialize")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    @Operation(summary = "Serializes a message with the server's data type serializer. direction=toXML parses an inbound message to its XML tree (Content-Type application/xml); direction=fromXML renders XML back to the raw message format (Content-Type text/plain). Default serialization properties are used. Raw response, not the standard serialized envelope.")
    @MirthOperation(name = "serializeMessage", display = "Serialize message", type = ExecuteType.ASYNC, auditable = false)
    public Response serialize(
            @Param("dataType") @Parameter(description = "The data type plugin point, e.g. HL7V2, DELIMITED, NCPDP, EDI/X12, DICOM. Case-insensitive.", required = true) @QueryParam("dataType") String dataType,
            @Param("direction") @Parameter(description = "The serialization direction: toXML or fromXML. Case-insensitive.", required = true) @QueryParam("direction") String direction,
            @Param("message") @RequestBody(description = "The message to serialize. Default serialization properties for the data type are used.", required = true, content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(implementation = String.class))) String message) throws ClientException;

    /*
     * The implementation returns RawContent so the property map is emitted as plain JSON, bypassing
     * the XStream/Staxon envelope. Unlike the endpoints above there is no 406 concern here: the
     * declared Produces (JSON) matches the actual output.
     */
    @GET
    @Path("/defaultProperties")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Returns the default serialization and deserialization property shapes for a data type, so a client can render a properties editor without re-implementing the defaults. Plain JSON response, not the standard serialized envelope.")
    @MirthOperation(name = "getDataTypeProperties", display = "Get data type default properties", type = ExecuteType.ASYNC, auditable = false)
    public Response defaultProperties(
            @Param("dataType") @Parameter(description = "The data type plugin point, e.g. HL7V2, DELIMITED, NCPDP, EDI/X12, DICOM. Case-insensitive.", required = true) @QueryParam("dataType") String dataType) throws ClientException;
}
