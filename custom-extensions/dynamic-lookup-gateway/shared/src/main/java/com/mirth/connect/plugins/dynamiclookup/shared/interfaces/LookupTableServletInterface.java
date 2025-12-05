/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.interfaces;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-05-13 10:25 AM
 */
//@formatter:off
@Path("/v1/lookups")
@Tag(name = "Lookup Table")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface LookupTableServletInterface extends BaseServletInterface {
    public static final String PERMISSION_ACCESS = "Access Lookup Table";

    @POST
    @Path("/groups")
    @Operation(summary = "Creates a new lookup group.")
    @ApiResponse(
            responseCode = "200",
            description = "The created lookup group returned as a JSON object.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = {
                            @ExampleObject(
                                name = "text-group",
                                summary = "Example TEXT group response",
                                value = "{\n" +
                                        "  \"id\": 3,\n" +
                                        "  \"name\": \"Provider Directory\",\n" +
                                        "  \"description\": \"Provider information lookup\",\n" +
                                        "  \"version\": \"1.0\",\n" +
                                        "  \"cacheSize\": 500,\n" +
                                        "  \"cachePolicy\": \"LRU\",\n" +
                                        "  \"valueType\": \"TEXT\",\n" +
                                        "  \"createdDate\": \"2025-05-01T08:15:00Z\",\n" +
                                        "  \"updatedDate\": \"2025-05-01T08:15:00Z\"\n" +
                                        "}"
                            ),
                            @ExampleObject(
                                name = "json-group",
                                summary = "Example JSON group response",
                                value = "{\n" +
                                        "  \"id\": 3,\n" +
                                        "  \"name\": \"Provider Directory\",\n" +
                                        "  \"description\": \"Provider information lookup\",\n" +
                                        "  \"version\": \"1.0\",\n" +
                                        "  \"cacheSize\": 500,\n" +
                                        "  \"cachePolicy\": \"LRU\",\n" +
                                        "  \"valueType\": \"JSON\",\n" +
                                        "  \"extra\": {\n" +
                                        "    \"jsonIndexMode\": \"FIELD\",\n" +
                                        "    \"indexedJsonFields\": \"[\\\"email\\\", \\\"address.city\\\"]\"\n" +
                                        "  },\n" +
                                        "  \"createdDate\": \"2025-05-01T08:15:00Z\",\n" +
                                        "  \"updatedDate\": \"2025-05-01T08:15:00Z\"\n" +
                                        "}"
                            )
                        }
            )
    )
    @MirthOperation(name = "createGroup", display = "Create new group", permission = PERMISSION_ACCESS)
    public String createGroup(
            @Param("requestBody")
            @RequestBody(
                    description = "JSON object representing the lookup group to be created.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = {
                                    @ExampleObject(
                                        name = "text-group",
                                        summary = "Example TEXT group (default)",
                                        value = "{\n" +
                                                "  \"name\": \"Provider Directory\",\n" +
                                                "  \"description\": \"Provider information lookup\",\n" +
                                                "  \"version\": \"1.0\",\n" +
                                                "  \"cacheSize\": 500,\n" +
                                                "  \"cachePolicy\": \"LRU\",\n" +
                                                "  \"valueType\": \"TEXT\"\n" +
                                                "}"
                                    ),
                                    @ExampleObject(
                                        name = "json-group-field-index",
                                        summary = "Example JSON group with FIELD index",
                                        value = "{\n" +
                                                "  \"name\": \"Provider Directory\",\n" +
                                                "  \"description\": \"Provider information lookup\",\n" +
                                                "  \"version\": \"1.0\",\n" +
                                                "  \"cacheSize\": 500,\n" +
                                                "  \"cachePolicy\": \"LRU\",\n" +
                                                "  \"valueType\": \"JSON\",\n" +
                                                "  \"extra\": {\n" +
                                                "    \"jsonIndexMode\": \"FIELD\",\n" +
                                                "    \"indexedJsonFields\": \"[\\\"email\\\", \\\"address.city\\\"]\"\n" +
                                                "  }\n" +
                                                "}"
                                    ),
                                    @ExampleObject(
                                        name = "json-group-gin-index",
                                        summary = "Example JSON group with GIN index (PostgreSQL only)",
                                        value = "{\n" +
                                                "  \"name\": \"Provider Directory\",\n" +
                                                "  \"description\": \"Provider information lookup\",\n" +
                                                "  \"version\": \"1.0\",\n" +
                                                "  \"cacheSize\": 500,\n" +
                                                "  \"cachePolicy\": \"LRU\",\n" +
                                                "  \"valueType\": \"JSON\",\n" +
                                                "  \"extra\": {\n" +
                                                "    \"jsonIndexMode\": \"GIN\"\n" +
                                                "  }\n" +
                                                "}"
                                    ),
                                    @ExampleObject(
                                        name = "json-group-none-index",
                                        summary = "Example JSON group with NONE index",
                                        value = "{\n" +
                                                "  \"name\": \"Provider Directory\",\n" +
                                                "  \"description\": \"Provider information lookup\",\n" +
                                                "  \"version\": \"1.0\",\n" +
                                                "  \"cacheSize\": 500,\n" +
                                                "  \"cachePolicy\": \"LRU\",\n" +
                                                "  \"valueType\": \"JSON\",\n" +
                                                "  \"extra\": {\n" +
                                                "    \"jsonIndexMode\": \"NONE\"\n" +
                                                "  }\n" +
                                                "}"
                                    )
                                }
                    )
            )
            String requestBody
    ) throws ClientException;

    @GET
    @Path("/groups")
    @Operation(summary = "Returns all lookup groups.")
    @MirthOperation(name = "getAllGroups", display = "Get lookup group list", permission = PERMISSION_ACCESS)
    public String getAllGroups() throws ClientException;

    @PUT
    @Path("/groups/{groupId}")
    @Operation(summary = "Updates a specified group.")
    @ApiResponse(
        responseCode = "200",
        description = "Group was successfully updated.",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = {
                @ExampleObject(
                    name = "text-group-response",
                    summary = "Updated TEXT group",
                    value =
                            "{\n" +
                            "  \"id\": 3,\n" +
                            "  \"name\": \"Provider Directory\",\n" +
                            "  \"description\": \"Updated provider information lookup\",\n" +
                            "  \"version\": \"1.1\",\n" +
                            "  \"cacheSize\": 1000,\n" +
                            "  \"cachePolicy\": \"LRU\",\n" +
                            "  \"valueType\": \"TEXT\",\n" +
                            "  \"createdDate\": \"2025-05-01T08:15:00Z\",\n" +
                            "  \"updatedDate\": \"2025-05-01T09:30:00Z\"\n" +
                            "}"
                ),
                @ExampleObject(
                    name = "json-group-response",
                    summary = "Updated JSON group (FIELD index mode)",
                    value =
                            "{\n" +
                            "  \"id\": 5,\n" +
                            "  \"name\": \"Provider Directory JSON\",\n" +
                            "  \"description\": \"JSON provider lookup updated\",\n" +
                            "  \"version\": \"2.0\",\n" +
                            "  \"cacheSize\": 800,\n" +
                            "  \"cachePolicy\": \"FIFO\",\n" +
                            "  \"valueType\": \"JSON\",\n" +
                            "  \"extra\": {\n" +
                            "    \"jsonIndexMode\": \"FIELD\",\n" +
                            "    \"indexedJsonFields\": \"[\\\"email\\\", \\\"address.city\\\"]\"\n" +
                            "  },\n" +
                            "  \"createdDate\": \"2025-05-01T08:15:00Z\",\n" +
                            "  \"updatedDate\": \"2025-05-01T10:00:00Z\"\n" +
                            "}"
                )
            }
        )
    )
    @MirthOperation(name = "updateGroup", display = "Update group", permission = PERMISSION_ACCESS)
    public String updateGroup(
        @Param("groupId")
        @Parameter(
            name = "groupId",
            description = "The unique ID of the group to update.",
            example = "1",
            required = true)
        @PathParam("groupId") Integer groupId,

        @RequestBody(
            description = "JSON object representing the lookup group fields to update.",
            required = true,
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                examples = {
                    @ExampleObject(
                        name = "text-group-request",
                        summary = "Update TEXT group",
                        value =
                                "{\n" +
                                "  \"name\": \"Provider Directory Updated\",\n" +
                                "  \"description\": \"Updated provider information\",\n" +
                                "  \"version\": \"1.1\",\n" +
                                "  \"cacheSize\": 900,\n" +
                                "  \"cachePolicy\": \"LRU\",\n" +
                                "  \"valueType\": \"TEXT\"\n" +
                                "}"
                    ),
                    @ExampleObject(
                        name = "json-group-request-field-index",
                        summary = "Update JSON group with FIELD index",
                        value =
                                "{\n" +
                                "  \"name\": \"Provider Directory JSON Updated\",\n" +
                                "  \"description\": \"Updated JSON provider info\",\n" +
                                "  \"version\": \"2.0\",\n" +
                                "  \"cacheSize\": 800,\n" +
                                "  \"cachePolicy\": \"FIFO\",\n" +
                                "  \"valueType\": \"JSON\",\n" +
                                "  \"extra\": {\n" +
                                "    \"jsonIndexMode\": \"FIELD\",\n" +
                                "    \"indexedJsonFields\": \"[\\\"email\\\", \\\"address.city\\\"]\"\n" +
                                "  }\n" +
                                "}"
                    ),
                    @ExampleObject(
                        name = "json-group-request-gin-index",
                        summary = "Update JSON group with GIN index (PostgreSQL only)",
                        value =
                                "{\n" +
                                "  \"name\": \"Provider Directory JSON Updated\",\n" +
                                "  \"description\": \"Updated JSON provider info\",\n" +
                                "  \"version\": \"2.0\",\n" +
                                "  \"cacheSize\": 800,\n" +
                                "  \"cachePolicy\": \"FIFO\",\n" +
                                "  \"valueType\": \"JSON\",\n" +
                                "  \"extra\": {\n" +
                                "    \"jsonIndexMode\": \"GIN\"\n" +
                                "  }\n" +
                                "}"
                    ),
                    @ExampleObject(
                        name = "json-group-request-none-index",
                        summary = "Update JSON group with NONE index",
                        value =
                                "{\n" +
                                "  \"name\": \"Provider Directory JSON Updated\",\n" +
                                "  \"description\": \"Updated JSON provider info\",\n" +
                                "  \"version\": \"2.0\",\n" +
                                "  \"cacheSize\": 800,\n" +
                                "  \"cachePolicy\": \"FIFO\",\n" +
                                "  \"valueType\": \"JSON\",\n" +
                                "  \"extra\": {\n" +
                                "    \"jsonIndexMode\": \"NONE\"\n" +
                                "  }\n" +
                                "}"
                    )
                }
            )
        )
        String requestBody
    ) throws ClientException;


    @GET
    @Path("/groups/{groupId}")
    @Operation(summary = "Returns a specific lookup group by id.")
    @ApiResponse(
            responseCode = "200",
            description = "The lookup group returned as a JSON object.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "group",
                            value = "{\n" +
                                    "  \"id\": 3,\n" +
                                    "  \"name\": \"Provider Directory\",\n" +
                                    "  \"description\": \"Provider information lookup\",\n" +
                                    "  \"version\": \"1.0\",\n" +
                                    "  \"cacheSize\": 500,\n" +
                                    "  \"cachePolicy\": \"LRU\",\n" +
                                    "  \"createdDate\": \"2025-05-01T08:15:00Z\",\n" +
                                    "  \"updatedDate\": \"2025-05-01T08:15:00Z\"\n" +
                                    "  \"valueType\": \"TEXT\"\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "getGroupById", display = "Get lookup group", permission = PERMISSION_ACCESS)
    public String getGroupById(
            @Param("groupId")
            @Parameter(description = "The unique id of the group to retrieve.", required = true)
            @PathParam("groupId") Integer groupId
    ) throws ClientException;

    @GET
    @Path("/groups/name/{name}")
    @Operation(summary = "Returns a specific lookup group by name.")
    @ApiResponse(
            responseCode = "200",
            description = "The lookup group returned as a JSON object.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "group",
                            value = "{\n" +
                                    "  \"id\": 3,\n" +
                                    "  \"name\": \"Provider Directory\",\n" +
                                    "  \"description\": \"Provider information lookup\",\n" +
                                    "  \"version\": \"1.0\",\n" +
                                    "  \"cacheSize\": 500,\n" +
                                    "  \"cachePolicy\": \"LRU\",\n" +
                                    "  \"createdDate\": \"2025-05-01T08:15:00Z\",\n" +
                                    "  \"updatedDate\": \"2025-05-01T08:15:00Z\"\n" +
                                    "  \"valueType\": \"TEXT\"\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "getGroupByName", display = "Get lookup group", permission = PERMISSION_ACCESS)
    public String getGroupByName(
            @Param("name")
            @Parameter(description = "The unique name of the group to retrieve.", required = true)
            @PathParam("name") String name
    ) throws ClientException;

    @DELETE
    @Path("/groups/{groupId}")
    @Operation(summary = "Delete a specific group.")
    @MirthOperation(name = "deleteGroup", display = "Delete group", permission = PERMISSION_ACCESS)
    public void deleteGroup(
            @Param("groupId")
            @Parameter(description = "The unique id of the group to delete.", required = true)
            @PathParam("groupId") Integer groupId
    ) throws ClientException;

    @GET
    @Path("/groups/{groupId}/values")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieves all values from a specific lookup group with optional pagination and key filtering.")
    @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved values",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "Successful response",
                            value = "{\n" +
                                    "  \"groupId\": 1,\n" +
                                    "  \"groupName\": \"Billing Codes\",\n" +
                                    "  \"totalCount\": 7,\n" +
                                    "  \"values\": [\n" +
                                    "    {\n" +
                                    "      \"keyValue\": \"99213\",\n" +
                                    "      \"valueData\": \"Office Visit\",\n" +
                                    "      \"createdDate\": \"2025-06-22T01:19:25.213+00:00\",\n" +
                                    "      \"updatedDate\": \"2025-06-22T01:29:45.123+00:00\"\n" +
                                    "    },\n" +
                                    "    {\n" +
                                    "      \"keyValue\": \"99214\",\n" +
                                    "      \"valueData\": \"Office Visit, Level 4\",\n" +
                                    "      \"createdDate\": \"2025-06-22T01:19:25.213+00:00\",\n" +
                                    "      \"updatedDate\": \"2025-06-22T01:19:25.213+00:00\"\n" +
                                    "    }\n" +
                                    "    // truncated for brevity\n" +
                                    "  ],\n" +
                                    "  \"pagination\": {\n" +
                                    "    \"limit\": 100,\n" +
                                    "    \"offset\": 0,\n" +
                                    "    \"hasMore\": false\n" +
                                    "  }\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "getAllValues", display = "Retrieves all values", permission = PERMISSION_ACCESS)
    public String getAllValues(
            @Param("groupId")
            @Parameter(description = "The unique ID of the group to retrieve.", required = true)
            @PathParam("groupId") Integer groupId,

            @Param("offset")
            @Parameter(description = "Offset for pagination (default: 0)", required = false)
            @QueryParam("offset") @DefaultValue("0") Integer offset,

            @Param("limit")
            @Parameter(description = "Maximum number of values to return (default: 100)", required = false)
            @QueryParam("limit") @DefaultValue("100") Integer limit,

            @Param("pattern")
            @Parameter(description = "Filter keys by pattern (optional)", required = false)
            @QueryParam("pattern") String pattern
    ) throws ClientException;


    @GET
    @Path("/groups/{groupId}/values/{key}")
    @Operation(summary = "Retrieve a lookup value by group and key.")
    @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved the lookup value.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "LookupValue",
                            summary = "A sample lookup value response",
                            value = "{\n" +
                                    "  \"groupId\": 1,\n" +
                                    "  \"key\": \"99213\",\n" +
                                    "  \"value\": \"Office Visit, Established Patient\",\n" +
//                                    "  \"createdDate\": \"2025-05-28T15:17:12.504+00:00\",\n" +
//                                    "  \"updatedDate\": \"2025-05-28T15:17:12.504+00:00\"\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "getValue", display = "Retrieve Lookup Value", permission = PERMISSION_ACCESS)
    public String getValue(
            @Param("groupId")
            @Parameter(
                    name = "groupId",
                    description = "The unique ID of the lookup group containing the value.",
                    example = "1",
                    required = true
            )
            @PathParam("groupId") Integer groupId,

            @Param("key")
            @Parameter(
                    name = "key",
                    description = "The primary key of the lookup value within the specified group.",
                    example = "99213",
                    required = true
            )
            @PathParam("key") String key
    ) throws ClientException;

    @PUT
    @Path("/groups/{groupId}/values/{key}")
    @Operation(
            summary = "Set or update the value for a specific key within a lookup group.",
            description = "Updates the value associated with the specified key in the given lookup group. " +
                    "Expects a JSON body with a 'value' field. If the key exists, the value is updated; otherwise, a new entry may be created."
    )
    @ApiResponse(
            responseCode = "200",
            description = "The lookup value returned as a JSON object.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "value",
                            value = "{\n" +
                                    "  \"groupId\": 1,\n" +
                                    "  \"key\": \"99213\",\n" +
                                    "  \"value\": \"Office Visit, Established Patient - Level 3\",\n" +
                                    "  \"createdDate\": \"2025-05-01T08:15:00Z\",\n" +
                                    "  \"updatedDate\": \"2025-05-01T08:15:00Z\"\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(
            name = "setValue",
            display = "Set Lookup Value",
            permission = PERMISSION_ACCESS
    )
    public String setValue(
            @Param("groupId")
            @Parameter(
                    name = "groupId",
                    description = "The unique ID of the lookup group containing the key-value pair.",
                    example = "42",
                    required = true
            )
            @PathParam("groupId") Integer groupId,

            @Param("key")
            @Parameter(
                    name = "key",
                    description = "The primary key of the lookup value to update within the specified group.",
                    example = "99213",
                    required = true
            )
            @PathParam("key") String key,

            @Param("requestBody")
            @RequestBody(
                    description = "Raw JSON string containing a 'value' field.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    name = "value",
                                    summary = "Set or update value",
                                    value = "{ \"value\": \"Office Visit, Established Patient - Level 3\" }"
                            )
                    )
            )
            String requestBody

    ) throws ClientException;

    @DELETE
    @Path("/groups/{groupId}/values/{key}")
    @Operation(summary = "Delete a lookup value by group and key.")
    @MirthOperation(name = "deleteValue", display = "Delete Lookup Value", permission = PERMISSION_ACCESS)
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteValue(
            @Param("groupId")
            @Parameter(
                    name = "groupId",
                    description = "The unique ID of the lookup group containing the value.",
                    example = "42",
                    required = true
            )
            @PathParam("groupId") Integer groupId,

            @Param("key")
            @Parameter(
                    name = "key",
                    description = "The primary key of the lookup value within the specified group.",
                    example = "M",
                    required = true
            )
            @PathParam("key") String key
    ) throws ClientException;

    @POST
    @Path("/groups/{groupId}/values/search")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Searches values in a lookup group using filter criteria.",
            description = "Performs a filtered search within the specified lookup group. "
                    + "Search supports pagination and multiple filter fields such as keyFilter and valueFilter. "
                    + "If no filter criteria are provided, all values in the group will be returned (equivalent to an unfiltered search). "
                    + "This endpoint is designed for UI usage."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved filtered or unfiltered values",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "Successful response",
                            value = "{\n" +
                                    "  \"groupId\": 1,\n" +
                                    "  \"groupName\": \"Billing Codes\",\n" +
                                    "  \"totalCount\": 7,\n" +
                                    "  \"values\": [\n" +
                                    "    {\n" +
                                    "      \"keyValue\": \"99213\",\n" +
                                    "      \"valueData\": \"Office Visit\",\n" +
                                    "      \"createdDate\": \"2025-06-22T01:19:25.213+00:00\",\n" +
                                    "      \"updatedDate\": \"2025-06-22T01:29:45.123+00:00\"\n" +
                                    "    },\n" +
                                    "    {\n" +
                                    "      \"keyValue\": \"99214\",\n" +
                                    "      \"valueData\": \"Office Visit, Level 4\",\n" +
                                    "      \"createdDate\": \"2025-06-22T01:19:25.213+00:00\",\n" +
                                    "      \"updatedDate\": \"2025-06-22T01:19:25.213+00:00\"\n" +
                                    "    }\n" +
                                    "    // truncated for brevity\n" +
                                    "  ],\n" +
                                    "  \"pagination\": {\n" +
                                    "    \"limit\": 100,\n" +
                                    "    \"offset\": 0,\n" +
                                    "    \"hasMore\": false\n" +
                                    "  }\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "searchValues", display = "Search values", permission = PERMISSION_ACCESS)
    public String searchValues(
            @Param("groupId")
            @Parameter(description = "The unique ID of the group to retrieve.", required = true)
            @PathParam("groupId") Integer groupId,

            @Param("offset")
            @Parameter(description = "Offset for pagination (default: 0)", required = false)
            @QueryParam("offset") @DefaultValue("0") Integer offset,

            @Param("limit")
            @Parameter(description = "Maximum number of values to return (default: 100)", required = false)
            @QueryParam("limit") @DefaultValue("100") Integer limit,

            @Param("filterState")
            @RequestBody(
                    description = "JSON object representing filter criteria for value search.",
                    required = false,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    name = "filter",
                                    summary = "Example filter for value search",
                                    value = "{\n" +
                                            "  \"keyFilter\": \"provider\",\n" +
                                            "  \"valueFilter\": \"active\"\n" +
                                            "}"
                            )
                    )
            )
            String filterState
    ) throws ClientException;
    
    @POST
    @Path("/groups/{groupId}/values")
    @Operation(summary = "Imports key-value pairs into a specific lookup group.")
    @ApiResponse(
            responseCode = "200",
            description = "Import successful",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "Success",
                            value = "{\n" +
                                    "  \"groupId\": 1,\n" +
                                    "  \"status\": \"success\",\n" +
                                    "  \"importedCount\": 4,\n" +
                                    "  \"errors\": []\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "importValues", display = "Import lookup values", permission = PERMISSION_ACCESS)
    public String importValues(
            @Param("groupId")
            @Parameter(description = "The ID of the group to import values into.", required = true)
            @PathParam("groupId") Integer groupId,

            @Param("clearExist")
            @Parameter(description = "If true, existing values in the group will be cleared before import. Default: false", required = false)
            @QueryParam("clearExist")
            @DefaultValue("false") boolean clearExist,

            @Param("requestBody")
            @RequestBody(
                    description = "Raw JSON containing a 'values' object with key-value pairs to import.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    name = "ImportValuesExample",
                                    summary = "Key-value import payload",
                                    value = "{\n" +
                                            "  \"values\": {\n" +
                                            "    \"99213\": \"Office Visit, Established Patient\",\n" +
                                            "    \"99214\": \"Office Visit, Level 4\",\n" +
                                            "    \"99215\": \"Office Visit, Level 5\",\n" +
                                            "    \"J0696\": \"Injection, Ceftriaxone Sodium\"\n" +
                                            "  }\n" +
                                            "}"
                            )
                    )
            )
            String requestBody
    ) throws ClientException;

    @GET
    @Path("/groups/{groupId}/export")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Exports a lookup group and its values for backup or migration.")
    @ApiResponse(
            responseCode = "200",
            description = "Successfully exported the lookup group and its values.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "ExportExample",
                            summary = "Exported lookup group for backup/migration",
                            value = "{\n" +
                                    "  \"group\": {\n" +
                                    "    \"id\": 1,\n" +
                                    "    \"name\": \"Billing Codes\",\n" +
                                    "    \"description\": \"Standard billing codes for claims\",\n" +
                                    "    \"version\": \"1.0\",\n" +
                                    "    \"cacheSize\": 1000,\n" +
                                    "    \"cachePolicy\": \"LRU\",\n" +
                                    "    \"createdDate\": \"2024-12-15T08:00:00Z\",\n" +
                                    "    \"updatedDate\": \"2025-05-01T10:00:00Z\"\n" +
                                    "  },\n" +
                                    "  \"values\": {\n" +
                                    "    \"99213\": \"Office Visit, Established Patient\",\n" +
                                    "    \"99214\": \"Office Visit, Level 4\",\n" +
                                    "    \"99215\": \"Office Visit, Level 5\",\n" +
                                    "    \"J0696\": \"Injection, Ceftriaxone Sodium\"\n" +
                                    "  },\n" +
                                    "  \"exportDate\": \"2025-05-27T14:30:00Z\"\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "exportGroup", display = "Export Lookup Group", permission = PERMISSION_ACCESS)
    public String exportGroup(
            @Param("groupId")
            @Parameter(description = "The ID of the lookup group to export.", required = true)
            @PathParam("groupId") Integer groupId
    ) throws ClientException;

    @GET
    @Path("/groups/{groupId}/exportPaged")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Exports values for a lookup group in pages, for large backups or migrations.")
    @ApiResponse(
            responseCode = "200",
            description = "Successfully exported a page of values for the specified lookup group.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "PagedExportExample",
                            summary = "A single page of lookup values",
                            value = "{\n" +
                                    "  \"groupId\": 1,\n" +
                                    "  \"offset\": 0,\n" +
                                    "  \"limit\": 10000,\n" +
                                    "  \"values\": {\n" +
                                    "    \"99213\": \"Office Visit, Established Patient\",\n" +
                                    "    \"99214\": \"Office Visit, Level 4\"\n" +
                                    "  }\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "exportGroupPaged", display = "Export Paged Lookup Group", permission = PERMISSION_ACCESS)
    public String exportGroupPaged(
            @Param("groupId")
            @Parameter(description = "The ID of the lookup group to export.", required = true)
            @PathParam("groupId") Integer groupId,

            @QueryParam("offset")
            @DefaultValue("0")
            @Parameter(description = "Offset for pagination.")
            Integer offset,

            @QueryParam("limit")
            @DefaultValue("10000")
            @Parameter(description = "Maximum number of entries to return.")
            Integer limit
    ) throws ClientException;

    @POST
    @Path("/groups/import")
    @Operation(summary = "Imports a lookup group and its values for migration or restore. If the group already exists, all of its existing values will be deleted and replaced with the new ones.")
    @ApiResponse(
            responseCode = "200",
            description = "Successfully imported the lookup group and its values.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "ImportExample",
                            summary = "Imported lookup group",
                            value = "{\n" +
                                    "  \"status\": \"success\",\n" +
                                    "  \"groupId\": 1,\n" +
                                    "  \"importedCount\": 4,\n" +
                                    "  \"errors\": []\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "importGroup", display = "Import Lookup Group", permission = PERMISSION_ACCESS)
    public String importGroup(
            @Param("updateIfExists")
            @Parameter(description = "If true, updates the group if it already exists. Default: false", required = false)
            @QueryParam("updateIfExists") @DefaultValue("false") boolean updateIfExists,

            @Param("requestBody")
            @RequestBody(
                description = "Raw JSON containing a lookup group and its values to import. " +
                              "Supports TEXT and JSON groups. For JSON groups, values must be JSON strings.",
                required = true,
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = {

                        // -------------------------- TEXT GROUP --------------------------
                        @ExampleObject(
                            name = "import-text-group",
                            summary = "Import a TEXT lookup group",
                            value =
                                    "{\n" +
                                    "  \"group\": {\n" +
                                    "    \"name\": \"Billing Codes\",\n" +
                                    "    \"description\": \"Standard billing codes for claims\",\n" +
                                    "    \"version\": \"1.0\",\n" +
                                    "    \"cacheSize\": 1000,\n" +
                                    "    \"cachePolicy\": \"LRU\",\n" +
                                    "    \"valueType\": \"TEXT\"\n" +
                                    "  },\n" +
                                    "  \"values\": {\n" +
                                    "    \"99213\": \"Office Visit, Established Patient\",\n" +
                                    "    \"99214\": \"Office Visit, Level 4\",\n" +
                                    "    \"99215\": \"Office Visit, Level 5\",\n" +
                                    "    \"J0696\": \"Injection, Ceftriaxone Sodium\"\n" +
                                    "  }\n" +
                                    "}"
                        ),

                        // ----------------------- JSON + FIELD ---------------------------
                        @ExampleObject(
                            name = "import-json-group-field",
                            summary = "Import a JSON lookup group with FIELD index",
                            value =
                                    "{\n" +
                                    "  \"group\": {\n" +
                                    "    \"name\": \"User Directory\",\n" +
                                    "    \"description\": \"JSON-based user data\",\n" +
                                    "    \"version\": \"1.0\",\n" +
                                    "    \"cacheSize\": 500,\n" +
                                    "    \"cachePolicy\": \"FIFO\",\n" +
                                    "    \"valueType\": \"JSON\",\n" +
                                    "    \"extra\": {\n" +
                                    "      \"jsonIndexMode\": \"FIELD\",\n" +
                                    "      \"indexedJsonFields\": \"[\\\"email\\\", \\\"address.city\\\"]\"\n" +
                                    "    }\n" +
                                    "  },\n" +
                                    "  \"values\": {\n" +
                                    "    \"user_1\": \"{ \\\"email\\\": \\\"user1@example.com\\\", \\\"status\\\": \\\"active\\\", \\\"address\\\": { \\\"city\\\": \\\"Houston\\\" } }\",\n" +
                                    "    \"user_2\": \"{ \\\"email\\\": \\\"user2@example.com\\\", \\\"status\\\": \\\"inactive\\\", \\\"address\\\": { \\\"city\\\": \\\"Austin\\\" } }\"\n" +
                                    "  }\n" +
                                    "}"
                        ),

                        // ------------------------- JSON + GIN ---------------------------
                        @ExampleObject(
                            name = "import-json-group-gin",
                            summary = "Import a JSON lookup group with GIN index (PostgreSQL only)",
                            value =
                                    "{\n" +
                                    "  \"group\": {\n" +
                                    "    \"name\": \"Audit Log\",\n" +
                                    "    \"description\": \"GIN-indexed JSON entries for fast search\",\n" +
                                    "    \"version\": \"2.0\",\n" +
                                    "    \"cacheSize\": 800,\n" +
                                    "    \"cachePolicy\": \"LRU\",\n" +
                                    "    \"valueType\": \"JSON\",\n" +
                                    "    \"extra\": {\n" +
                                    "      \"jsonIndexMode\": \"GIN\"\n" +
                                    "    }\n" +
                                    "  },\n" +
                                    "  \"values\": {\n" +
                                    "    \"log1\": \"{ \\\"event\\\": \\\"LOGIN\\\", \\\"success\\\": true }\",\n" +
                                    "    \"log2\": \"{ \\\"event\\\": \\\"LOGOUT\\\", \\\"success\\\": false }\"\n" +
                                    "  }\n" +
                                    "}"
                        ),

                        // ------------------------- JSON + NONE --------------------------
                        @ExampleObject(
                            name = "import-json-group-none",
                            summary = "Import a JSON lookup group with NONE index",
                            value =
                                    "{\n" +
                                    "  \"group\": {\n" +
                                    "    \"name\": \"User Metadata\",\n" +
                                    "    \"description\": \"JSON without any index (slower search, full scan)\",\n" +
                                    "    \"version\": \"1.0\",\n" +
                                    "    \"cacheSize\": 300,\n" +
                                    "    \"cachePolicy\": \"LRU\",\n" +
                                    "    \"valueType\": \"JSON\",\n" +
                                    "    \"extra\": {\n" +
                                    "      \"jsonIndexMode\": \"NONE\"\n" +
                                    "    }\n" +
                                    "  },\n" +
                                    "  \"values\": {\n" +
                                    "    \"user_100\": \"{ \\\"role\\\": \\\"admin\\\", \\\"status\\\": \\\"active\\\" }\",\n" +
                                    "    \"user_200\": \"{ \\\"role\\\": \\\"support\\\", \\\"status\\\": \\\"inactive\\\" }\"\n" +
                                    "  }\n" +
                                    "}"
                        )
                    }
                )
            )

            String requestBody
    ) throws ClientException;


    @POST
    @Path("/groups/{groupId}/values/batch")
    @Operation(summary = "Batch Get Values", description = "Retrieves multiple values from a lookup group in a single request.")
    @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved values for provided keys.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "BatchGetExample",
                            summary = "Successful lookup",
                            value = "{\n" +
                                    "  \"groupId\": 1,\n" +
                                    "  \"values\": {\n" +
                                    "    \"99213\": \"Office Visit, Established Patient\",\n" +
                                    "    \"99214\": \"Office Visit, Level 4\",\n" +
                                    "    \"99215\": \"Office Visit, Level 5\",\n" +
                                    "    \"J0696\": \"Injection, Ceftriaxone Sodium\"\n" +
                                    "  },\n" +
                                    "  \"missingKeys\": []\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "batchGetValues", display = "Batch Get Values", permission = PERMISSION_ACCESS)
    public String batchGetValues(
            @Param("groupId")
            @Parameter(description = "The ID of the group to retrieve values from.", required = true)
            @PathParam("groupId") Integer groupId,

            @Param("requestBody")
            @RequestBody(
                    description = "JSON body containing a list of keys to retrieve.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    name = "BatchGetRequest",
                                    summary = "Keys to fetch",
                                    value = "{ \"keys\": [\"99213\", \"99214\", \"99215\", \"J0696\"] }"
                            )
                    )
            )
            String requestBody
    ) throws ClientException;

    @GET
    @Path("/groups/{groupId}/statistics")
    @Operation(
            summary = "Get Group Statistics",
            description = "Retrieves usage and cache statistics for a specific lookup group."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved group statistics.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "GroupStatisticsExample",
                            summary = "Group usage and cache stats",
                            value = "{\n" +
                                    "  \"groupId\": 1,\n" +
                                    "  \"totalLookups\": 15423,\n" +
                                    "  \"cacheHits\": 12501,\n" +
                                    "  \"hitRate\": 0.81,\n" +
                                    "  \"lastAccessed\": \"2025-05-01T16:45:12Z\",\n" +
                                    "  \"resetDate\": \"2025-04-01T00:00:00Z\",\n" +
                                    "  \"cacheStatistics\": {\n" +
                                    "    \"size\": 267,\n" +
                                    "    \"maxSize\": 1000,\n" +
                                    "    \"hitCount\": 12501,\n" +
                                    "    \"missCount\": 2922,\n" +
                                    "    \"evictionCount\": 0\n" +
                                    "  }\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "getGroupStatistics", display = "Get Group Statistics", permission = PERMISSION_ACCESS)
    public String getGroupStatistics(
            @Param("groupId")
            @Parameter(description = "The ID of the group to retrieve statistics for.", required = true)
            @PathParam("groupId") Integer groupId
    ) throws ClientException;

    @POST
    @Path("/groups/{groupId}/statistics/reset")
    @Operation(
            summary = "Reset Group Statistics",
            description = "Resets total lookups and cache hits for a specific group, and updates the reset timestamp."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Statistics successfully reset.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "ResetSuccessExample",
                            summary = "Successful statistics reset",
                            value = "{\n" +
                                    "  \"status\": \"success\",\n" +
                                    "  \"message\": \"Statistics reset successfully for group ID: 1\"\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "resetGroupStatistics", display = "Reset Group Statistics", permission = PERMISSION_ACCESS)
    public String resetGroupStatistics(
            @Param("groupId")
            @Parameter(description = "The ID of the group whose statistics will be reset.", required = true)
            @PathParam("groupId") Integer groupId
    ) throws ClientException;

    @GET
    @Path("/groups/{groupId}/audit")
    @Operation(
            summary = "Get Group Audit Entries",
            description = "Retrieves a paginated list of audit entries for a specific lookup group."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved audit entries.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "AuditEntriesExample",
                            summary = "Audit log with pagination",
                            value = "{\n" +
                                    "  \"groupId\": 1,\n" +
                                    "  \"totalEntries\": 1243,\n" +
                                    "  \"entries\": [...],\n" +
                                    "  \"pagination\": {\n" +
                                    "    \"limit\": 100,\n" +
                                    "    \"offset\": 0,\n" +
                                    "    \"hasMore\": true\n" +
                                    "  }\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "getGroupAuditEntries", display = "Get Group Audit Entries", permission = PERMISSION_ACCESS)
    public String getGroupAuditEntries(
            @Param("groupId")
            @Parameter(description = "The ID of the group to retrieve audit entries for.", required = true)
            @PathParam("groupId") Integer groupId,

            @QueryParam("offset")
            @DefaultValue("0")
            @Parameter(description = "Offset for pagination.")
            Integer offset,

            @QueryParam("limit")
            @DefaultValue("100")
            @Parameter(description = "Maximum number of entries to return.")
            Integer limit

    ) throws ClientException;

    @POST
    @Path("/groups/{groupId}/audit/search")
    @Operation(
            summary = "Search Group Audit Entries",
            description = "Retrieves a paginated list of audit entries for a specific group using filter criteria."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved audit entries.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "AuditEntriesExample",
                            summary = "Audit log with pagination",
                            value = "{\n" +
                                    "  \"groupId\": 1,\n" +
                                    "  \"totalEntries\": 1243,\n" +
                                    "  \"entries\": [...],\n" +
                                    "  \"pagination\": {\n" +
                                    "    \"limit\": 100,\n" +
                                    "    \"offset\": 0,\n" +
                                    "    \"hasMore\": true\n" +
                                    "  }\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "searchGroupAuditEntries", display = "Search Group Audit Entries", permission = PERMISSION_ACCESS)
    public String searchGroupAuditEntries(
            @Param("groupId")
            @PathParam("groupId")
            Integer groupId,

            @QueryParam("offset")
            @DefaultValue("0")
            Integer offset,

            @QueryParam("limit")
            @DefaultValue("100")
            Integer limit,

            @Param("filterState")
            @RequestBody(
                    description = "JSON object representing filter criteria for audit entry search.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    name = "filter",
                                    summary = "Example filter for audit entry search",
                                    value = "{\n" +
                                            "  \"keyValue\": \"Provider\",\n" +
                                            "  \"action\": \"CREATE\",\n" +
                                            "  \"userId\": \"1\",\n" +
                                            "  \"startDate\": \"2025-06-01T00:00:00\",\n" +
                                            "  \"endDate\": \"2025-06-16T23:59:59\"\n" +
                                            "}"
                            )
                    )
            )
            String filterState
    ) throws ClientException;


    @POST
    @Path("/groups/{groupId}/cache/clear")
    @Operation(
            summary = "Clear Group Cache",
            description = "Clears the in-memory cache for a specific lookup group."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Cache cleared successfully.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "SuccessResponse",
                            summary = "Successful cache clear",
                            value = "{\n" +
                                    "  \"status\": \"success\",\n" +
                                    "  \"message\": \"Cache cleared successfully for group ID: 1\"\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "clearGroupCache", display = "Clear Group Cache", permission = PERMISSION_ACCESS)
    public String clearGroupCache(
            @Param("groupId")
            @Parameter(description = "The ID of the group to clear the cache for.", required = true)
            @PathParam("groupId") Integer groupId
    ) throws ClientException;

    @POST
    @Path("/cache/clear")
    @Operation(
            summary = "Clear All Caches",
            description = "Clears the in-memory caches for all lookup groups."
    )
    @ApiResponse(
            responseCode = "200",
            description = "All caches cleared successfully.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "SuccessResponse",
                            summary = "All caches cleared",
                            value = "{\n" +
                                    "  \"status\": \"success\",\n" +
                                    "  \"message\": \"All caches cleared successfully\"\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "clearAllCaches", display = "Clear All Caches", permission = PERMISSION_ACCESS)
    public String clearAllCaches() throws ClientException;
    
    @GET
    @Path("/databaseInfo")
    @Operation(
            summary = "Get database metadata",
            description = "Returns detailed information about the connected database, including type, version, and raw JDBC metadata."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Database information retrieved successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            name = "DatabaseInfoExample",
                            summary = "Example response for database metadata",
                            value = "{\n" +
                                    "  \"type\": \"POSTGRESQL\",\n" +
                                    "  \"majorVersion\": 16,\n" +
                                    "  \"minorVersion\": 2,\n" +
                                    "  \"productName\": \"PostgreSQL\",\n" +
                                    "  \"productVersion\": \"PostgreSQL 16.2 on x86_64-pc-linux-gnu\"\n" +
                                    "}"
                    )
            )
    )
    @MirthOperation(name = "getDatabaseInfo", display = "Get Database Info", permission = PERMISSION_ACCESS, auditable = false)
    public String getDatabaseInfo() throws ClientException;

}
//@formatter:on