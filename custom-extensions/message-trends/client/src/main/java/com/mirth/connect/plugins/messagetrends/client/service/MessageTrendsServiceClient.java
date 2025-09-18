/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.client.service;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.EntityException;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.plugins.messagetrends.client.exception.MessageTrendsClientException;
import com.mirth.connect.plugins.messagetrends.shared.dto.response.ErrorResponse;
import com.mirth.connect.plugins.messagetrends.shared.interfaces.MessageTrendsServletInterface;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;
import com.mirth.connect.plugins.messagetrends.shared.util.JsonUtils;

public class MessageTrendsServiceClient {
	private static MessageTrendsServiceClient instance = null;
	private MessageTrendsServletInterface servlet;
	private final Logger logger = LogManager.getLogger(this.getClass());

	public static MessageTrendsServiceClient getInstance() {
		synchronized (MessageTrendsServiceClient.class) {
			if (instance == null) {
				instance = new MessageTrendsServiceClient();
			}

			return instance;
		}
	}

	public MessageTrendsServiceClient() {
	}

	public List<MessageStatisticsTimeseries> getChannelStatistics(String channelId, Long startMillis, Long endMillis, String interval) throws ClientException {
		try {
			if (startMillis == null || endMillis == null) {
				throw new IllegalArgumentException("startMillis and endMillis are required (epoch millis)");
			}
			if (!(startMillis < endMillis)) {
				throw new IllegalArgumentException("startMillis must be < endMillis");
			}

			// Convert millis -> seconds (epoch seconds, UTC)
			Long startSec = startMillis / 1000L;
			Long endSec = endMillis / 1000L;

			String response = getServlet().getChannelStatistics(channelId, startSec, endSec, interval);

			return JsonUtils.fromJsonList(response, MessageStatisticsTimeseries.class);

		} catch (ClientException e) {
			try {
				rethrowParsedClientError(e, false); // Silent mode: no logging
			} catch (MessageTrendsClientException ex) {
				throw ex;
			}
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Unexpected error while fetching channel statistics", e);
		}
	}

	public List<MessageStatisticsTimeseries> getConnectorStatistics(String channelId, String connectorId, Long startMillis, Long endMillis, String interval) throws ClientException {
		try {
			if (startMillis == null || endMillis == null) {
				throw new IllegalArgumentException("startMillis and endMillis are required (epoch millis)");
			}
			if (!(startMillis < endMillis)) {
				throw new IllegalArgumentException("startMillis must be < endMillis");
			}

			// Convert millis -> seconds (epoch seconds, UTC)
			Long startSec = startMillis / 1000L;
			Long endSec = endMillis / 1000L;

			String response = getServlet().getConnectorStatistics(channelId, connectorId, startSec, endSec, interval);

			return JsonUtils.fromJsonList(response, MessageStatisticsTimeseries.class);

		} catch (ClientException e) {
			try {
				rethrowParsedClientError(e, false); // Silent mode: no logging
			} catch (MessageTrendsClientException ex) {
				throw ex;
			}
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Unexpected error while fetching connector statistics", e);
		}
	}

	public List<MessageStatisticsTimeseries> getServerStatistics(Long startMillis, Long endMillis, String interval) throws ClientException {
		try {
			if (startMillis == null || endMillis == null) {
				throw new IllegalArgumentException("startMillis and endMillis are required (epoch millis)");
			}
			if (!(startMillis < endMillis)) {
				throw new IllegalArgumentException("startMillis must be < endMillis");
			}

			// Convert millis -> seconds (epoch seconds, UTC)
			Long startSec = startMillis / 1000L;
			Long endSec = endMillis / 1000L;

			String response = getServlet().getServerStatistics(startSec, endSec, interval);

			return JsonUtils.fromJsonList(response, MessageStatisticsTimeseries.class);

		} catch (ClientException e) {
			try {
				rethrowParsedClientError(e, false); // Silent mode: no logging
			} catch (MessageTrendsClientException ex) {
				throw ex;
			}
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Unexpected error while fetching server statistics", e);
		}
	}

	public String getAvailableIntervals() throws ClientException {
		return "";
	}

	private MessageTrendsServletInterface getServlet() {
		if (servlet == null) {
			Client client = PlatformUI.MIRTH_FRAME.mirthClient;
			servlet = client.getServlet(MessageTrendsServletInterface.class);
		}

		return servlet;
	}

	private void rethrowParsedClientError(ClientException e) throws ClientException {
		rethrowParsedClientError(e, true); // default to logging enabled
	}

	private void rethrowParsedClientError(ClientException e, boolean logError) throws ClientException {
		Throwable cause = e.getCause();

		if (cause instanceof EntityException) {
			String rawEntity = (String) ((EntityException) cause).getEntity();

			ErrorResponse error;
			try {
				error = JsonUtils.fromJson(rawEntity, ErrorResponse.class);
				if (logError) {
					logger.error("Parsed API error: {}", JsonUtils.toJson(error));
				}
			} catch (Exception parseError) {
				if (logError) {
					logger.error("Failed to parse server error response: {}", rawEntity, parseError);
				}
				error = new ErrorResponse("UNPARSEABLE_RESPONSE", "Failed to parse server error");
			}

			throw new MessageTrendsClientException(error, e);
		}

		throw e; // fallback if not structured
	}
}
