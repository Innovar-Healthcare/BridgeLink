/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.server.controller;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.controllers.MessageTrendsController;
import com.mirth.connect.plugins.messagetrends.server.core.MessageTrendsBuffer;

public class TimeSeriesStatisticsController extends MessageTrendsController {
	private final Logger logger = LogManager.getLogger(getClass());

	@Override
	public void writeTimeseries(Map<String, Map<Integer, Map<Status, Long>>> stats) {
		try {
			MessageTrendsBuffer.getInstance().addFromDonkeyMap(stats);
		} catch (Throwable t) {
			// Never disrupt the message path
			logger.warn("Message Trends: writeTimeseries failed (non-fatal).", t);
		}
	}
}
