/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.controllers;

import com.mirth.connect.donkey.model.message.Status;
import java.util.Map;

public class MessageTrendsController {
	 /**
     * Write one commit's deltas to a time-series sink.
     * The map structure is:
     *   channelId -> connectorId -> (Status -> delta)
     *
     * This default implementation is a no-op so the system
     * continues to work even when the plugin is not installed.
     */
    public void writeTimeseries(Map<String, Map<Integer, Map<Status, Long>>> stats) {
        // no-op
    }
}
