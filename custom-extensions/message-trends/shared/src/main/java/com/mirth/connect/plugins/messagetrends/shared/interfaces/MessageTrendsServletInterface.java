/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.shared.interfaces;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-08-23 10:25 AM
 */
//@formatter:off
@Path("/statistics/timeseries")
@Tag(name = "Message Trends")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MessageTrendsServletInterface extends BaseServletInterface {
	public static final String PERMISSION_ACCESS = "Access Message Trends";

	@GET
	@Path("/channels/{channelId}")
	@Operation(summary = "Get channel-level statistics.")
	@ApiResponse(
	    responseCode = "200",
	    description = "Channel statistics returned successfully.",
	    content = @Content(
	        mediaType = MediaType.APPLICATION_JSON,
	        examples = @ExampleObject(
	            name = "channelStats",
	            value = "[\n" +
	                    "  {\n" +
	                    "    \"id\": 4711,\n" +
	                    "    \"channelId\": \"123e4567-e89b-12d3-a456-426614174000\",\n" +
	                    "    \"connectorId\": null,\n" +
	                    "    \"serverId\": \"srv-01\",\n" +
	                    "    \"ts\": \"2025-05-01T08:15:00Z\",\n" +
	                    "    \"bucketSizeMinutes\": 5,\n" +
	                    "    \"received\": 120,\n" +
	                    "    \"filtered\": 5,\n" +
	                    "    \"queued\": 3,\n" +
	                    "    \"sent\": 112,\n" +
	                    "    \"error\": 0\n" +
	                    "  },\n" +
	                    "  {\n" +
	                    "    \"id\": 4712,\n" +
	                    "    \"channelId\": \"123e4567-e89b-12d3-a456-426614174000\",\n" +
	                    "    \"connectorId\": null,\n" +
	                    "    \"serverId\": \"srv-01\",\n" +
	                    "    \"ts\": \"2025-05-01T08:20:00Z\",\n" +
	                    "    \"bucketSizeMinutes\": 5,\n" +
	                    "    \"received\": 98,\n" +
	                    "    \"filtered\": 2,\n" +
	                    "    \"queued\": 1,\n" +
	                    "    \"sent\": 95,\n" +
	                    "    \"error\": 0\n" +
	                    "  }\n" +
	                    "]"
	        )
	    )
	)
	@MirthOperation(
	    name = "getChannelStatistics",
	    display = "Get channel time series statistics",
	    permission = PERMISSION_ACCESS
	)
	public String getChannelStatistics(
			@Param("channelId") 
			@Parameter(
				name = "channelId",
	            description = "The unique ID of the channel to fetch statistics for.",
	            example = "123e4567-e89b-12d3-a456-426614174000",
	            required = true)
			@PathParam("channelId") String channelId, 
			
			@Param("startTime")
			@Parameter(description = "Start timestamp (epoch seconds, UTC) for the query window.", required = true)
			@QueryParam("startTime") Long startTime, 
			
			@Param("endTime")
			@Parameter(description = "End timestamp (epoch seconds, UTC) for the query window.", required = true)
			@QueryParam("endTime") Long endTime, 
			
			@Param("interval")
			@Parameter(
			    description = "Bucket interval for aggregating statistics.",
			    required = false, // has default
			    schema = @Schema(
			        type = "string",
			        allowableValues = {"1minute","5minute","15minute","60minute","daily"},
			        defaultValue = "5minute"
			    )
			)
			@QueryParam("interval") @DefaultValue("5minute") String interval
	) throws ClientException;
	
	// Get connector-level statistics
	@GET
	@Path("/channels/{channelId}/connectors/{connectorId}")
	@Operation(summary = "Get connector-level statistics.")
	@ApiResponse(
	    responseCode = "200",
	    description = "Connector statistics returned successfully.",
	    content = @Content(
	        mediaType = MediaType.APPLICATION_JSON,
	        examples = @ExampleObject(
	            name = "channelStats",
	            value = "[\n" +
	                    "  {\n" +
	                    "    \"id\": 4711,\n" +
	                    "    \"channelId\": \"123e4567-e89b-12d3-a456-426614174000\",\n" +
	                    "    \"connectorId\": \"2\",\n" +
	                    "    \"serverId\": \"srv-01\",\n" +
	                    "    \"ts\": \"2025-05-01T08:15:00Z\",\n" +
	                    "    \"bucketSizeMinutes\": 5,\n" +
	                    "    \"received\": 120,\n" +
	                    "    \"filtered\": 5,\n" +
	                    "    \"queued\": 3,\n" +
	                    "    \"sent\": 112,\n" +
	                    "    \"error\": 0\n" +
	                    "  },\n" +
	                    "  {\n" +
	                    "    \"id\": 4712,\n" +
	                    "    \"channelId\": \"123e4567-e89b-12d3-a456-426614174000\",\n" +
	                    "    \"connectorId\": \"2\",\n" +
	                    "    \"serverId\": \"srv-01\",\n" +
	                    "    \"ts\": \"2025-05-01T08:20:00Z\",\n" +
	                    "    \"bucketSizeMinutes\": 5,\n" +
	                    "    \"received\": 98,\n" +
	                    "    \"filtered\": 2,\n" +
	                    "    \"queued\": 1,\n" +
	                    "    \"sent\": 95,\n" +
	                    "    \"error\": 0\n" +
	                    "  }\n" +
	                    "]"
	        )
	    )
	)
	@MirthOperation(
	    name = "getConnectorStatistics",
	    display = "Get connector time series statistics",
	    permission = PERMISSION_ACCESS
	)
	public String getConnectorStatistics(
			@Param("channelId") 
			@Parameter(
				name = "channelId",
	            description = "The unique ID of the channel to fetch statistics for.",
	            example = "123e4567-e89b-12d3-a456-426614174000",
	            required = true)
			@PathParam("channelId") String channelId, 
			
			@Param("connectorId") 
			@Parameter(
				name = "connectorId",
	            description = "The unique ID of the connector to fetch statistics for.",
	            example = "2",
	            required = true)
			@PathParam("connectorId") String connectorId, 
			
			@Param("startTime")
			@Parameter(description = "Start timestamp (epoch seconds, UTC) for the query window.", required = true)
			@QueryParam("startTime") Long startTime, 
			
			@Param("endTime")
			@Parameter(description = "End timestamp (epoch seconds, UTC) for the query window.", required = true)
			@QueryParam("endTime") Long endTime, 
			
			@Param("interval")
			@Parameter(
			    description = "Bucket interval for aggregating statistics.",
			    required = false, // has default
			    schema = @Schema(
			        type = "string",
			        allowableValues = {"1minute","5minute","15minute","60minute","daily"},
			        defaultValue = "5minute"
			    )
			)
			@QueryParam("interval") @DefaultValue("5minute") String interval
	) throws ClientException;
	
	// Get connector-level statistics
	@GET
	@Path("/server")
	@Operation(summary = "Get server-wide statistics.")
	@ApiResponse(
	    responseCode = "200",
	    description = "Server statistics returned successfully.",
	    content = @Content(
	        mediaType = MediaType.APPLICATION_JSON,
	        examples = @ExampleObject(
	            name = "channelStats",
	            value = "[\n" +
	                    "  {\n" +
	                    "    \"id\": 4711,\n" +
	                    "    \"channelId\": \"123e4567-e89b-12d3-a456-426614174000\",\n" +
	                    "    \"connectorId\": \"2\",\n" +
	                    "    \"serverId\": \"srv-01\",\n" +
	                    "    \"ts\": \"2025-05-01T08:15:00Z\",\n" +
	                    "    \"bucketSizeMinutes\": 5,\n" +
	                    "    \"received\": 120,\n" +
	                    "    \"filtered\": 5,\n" +
	                    "    \"queued\": 3,\n" +
	                    "    \"sent\": 112,\n" +
	                    "    \"error\": 0\n" +
	                    "  },\n" +
	                    "  {\n" +
	                    "    \"id\": 4712,\n" +
	                    "    \"channelId\": \"123e4567-e89b-12d3-a456-426614174000\",\n" +
	                    "    \"connectorId\": \"2\",\n" +
	                    "    \"serverId\": \"srv-01\",\n" +
	                    "    \"ts\": \"2025-05-01T08:20:00Z\",\n" +
	                    "    \"bucketSizeMinutes\": 5,\n" +
	                    "    \"received\": 98,\n" +
	                    "    \"filtered\": 2,\n" +
	                    "    \"queued\": 1,\n" +
	                    "    \"sent\": 95,\n" +
	                    "    \"error\": 0\n" +
	                    "  }\n" +
	                    "]"
	        )
	    )
	)
	@MirthOperation(
	    name = "getServerStatistics",
	    display = "Get server time series statistics",
	    permission = PERMISSION_ACCESS
	)
	public String getServerStatistics(
			@Param("startTime")
			@Parameter(description = "Start timestamp (epoch seconds, UTC) for the query window.", required = true)
			@QueryParam("startTime") Long startTime, 
			
			@Param("endTime")
			@Parameter(description = "End timestamp (epoch seconds, UTC) for the query window.", required = true)
			@QueryParam("endTime") Long endTime, 
			
			@Param("interval")
			@Parameter(
			    description = "Bucket interval for aggregating statistics.",
			    required = false, // has default
			    schema = @Schema(
			        type = "string",
			        allowableValues = {"1minute","5minute","15minute","60minute","daily"},
			        defaultValue = "5minute"
			    )
			)
			@QueryParam("interval") @DefaultValue("5minute") String interval
	) throws ClientException;
	
	@GET
	@Path("/intervals")
	@Operation(
	    summary = "Get available time intervals.",
	    description = "Returns the list of supported bucket intervals for time-series aggregation."
	)
	@ApiResponse(
	    responseCode = "200",
	    description = "Intervals returned successfully.",
	    content = @Content(
	        mediaType = MediaType.APPLICATION_JSON,
	        examples = @ExampleObject(
	            name = "intervals",
	            value = "[\"1minute\",\"5minute\",\"15minute\",\"60minute\",\"daily\"]"
	        )
	    )
	)
	@MirthOperation(
	    name = "getAvailableIntervals",
	    display = "Get available time intervals",
	    permission = PERMISSION_ACCESS
	)
	public String getAvailableIntervals() throws ClientException;

}
//@formatter:on