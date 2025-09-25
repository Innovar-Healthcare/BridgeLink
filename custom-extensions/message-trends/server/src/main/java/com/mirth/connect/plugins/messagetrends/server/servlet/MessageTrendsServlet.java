/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.server.servlet;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.plugins.messagetrends.server.service.MessageTrendsService;
import com.mirth.connect.plugins.messagetrends.server.servlet.support.TimeseriesStatisticsSerializer;
import com.mirth.connect.plugins.messagetrends.shared.interfaces.MessageTrendsServletInterface;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;
import com.mirth.connect.plugins.messagetrends.shared.util.Intervals;
import com.mirth.connect.plugins.messagetrends.shared.util.JsonUtils;
import com.mirth.connect.plugins.messagetrends.shared.util.TimeUtil;
import com.mirth.connect.server.api.MirthServlet;

public class MessageTrendsServlet extends MirthServlet implements MessageTrendsServletInterface {
	private final Logger logger = LogManager.getLogger(this.getClass());

	public MessageTrendsServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
		super(request, sc, "Message Trends Management System");
	}

	@Override
	public String getChannelStatistics(String channelId, Long startTime, Long endTime, String interval) throws ClientException {
		try {
			// 0) Basic validation
			if (channelId == null || channelId.isEmpty()) {
				throw new IllegalArgumentException("channelId is required");
			}

			if (startTime == null || endTime == null) {
				throw new IllegalArgumentException("startTime and endTime are required (epoch seconds, UTC)");
			}

			// 1) Convert epoch seconds -> Date (with guard against milliseconds mistakenly
			// sent)
			Date startDate = TimeUtil.toDateFromEpochSeconds(startTime, "startTime");
			Date endDate = TimeUtil.toDateFromEpochSeconds(endTime, "endTime");

			if (!startDate.before(endDate)) { // end exclusive by convention
				throw new IllegalArgumentException("startTime must be < endTime");
			}

			// 2) Convert interval string -> minutes
			int bucketSizeMinutes = Intervals.minutesOf(interval);

			List<MessageStatisticsTimeseries> rawlist = MessageTrendsService.getInstance().getChannelSeries(channelId, startDate, endDate, bucketSizeMinutes);

			return TimeseriesStatisticsSerializer.toJsonFromEntities(rawlist);
		} catch (Exception e) {
			throw new ClientException("Failed to process getChannelStatistics request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
		}
	}

	@Override
	public String getConnectorStatistics(String channelId, String connectorId, Long startTime, Long endTime, String interval) throws ClientException {
		try {
			// 0) Basic validation
			if (channelId == null || channelId.isEmpty()) {
				throw new IllegalArgumentException("channelId is required");
			}

			if (startTime == null || endTime == null) {
				throw new IllegalArgumentException("startTime and endTime are required (epoch seconds, UTC)");
			}

			// 1) Convert epoch seconds -> Date (with guard against milliseconds mistakenly
			// sent)
			Date startDate = TimeUtil.toDateFromEpochSeconds(startTime, "startTime");
			Date endDate = TimeUtil.toDateFromEpochSeconds(endTime, "endTime");

			if (!startDate.before(endDate)) { // end exclusive by convention
				throw new IllegalArgumentException("startTime must be < endTime");
			}

			// 2) Convert interval string -> minutes
			int bucketSizeMinutes = Intervals.minutesOf(interval);

			List<MessageStatisticsTimeseries> rawlist = MessageTrendsService.getInstance().getConnectorSeries(channelId, connectorId, startDate, endDate, bucketSizeMinutes);

			return TimeseriesStatisticsSerializer.toJsonFromEntities(rawlist);
		} catch (Exception e) {
			throw new ClientException("Failed to process getConnectorStatistics request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
		}
	}

	@Override
	public String getServerStatistics(Long startTime, Long endTime, String interval) throws ClientException {
		try {
			// 0) Basic validation
			if (startTime == null || endTime == null) {
				throw new IllegalArgumentException("startTime and endTime are required (epoch seconds, UTC)");
			}

			// 1) Convert epoch seconds -> Date (with guard against milliseconds mistakenly
			// sent)
			Date startDate = TimeUtil.toDateFromEpochSeconds(startTime, "startTime");
			Date endDate = TimeUtil.toDateFromEpochSeconds(endTime, "endTime");

			if (!startDate.before(endDate)) { // end exclusive by convention
				throw new IllegalArgumentException("startTime must be < endTime");
			}

			// 2) Convert interval string -> minutes
			int bucketSizeMinutes = Intervals.minutesOf(interval);

			List<MessageStatisticsTimeseries> rawlist = MessageTrendsService.getInstance().getServerSeries(startDate, endDate, bucketSizeMinutes);

			return TimeseriesStatisticsSerializer.toJsonFromEntities(rawlist);
		} catch (Exception e) {
			throw new ClientException("Failed to process getServerStatistics request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
		}
	}

	@Override
	public String getAvailableIntervals() throws ClientException {
		try {
			List<String> rawlist = Intervals.canonicalCodes();

			return JsonUtils.toJson(rawlist);
		} catch (Exception e) {
			throw new ClientException("Failed to process getAvailableIntervals request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
		}
	}
}
