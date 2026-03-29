/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.diff;

import java.util.LinkedHashMap;

/**
 * Holds the parsed components of a Mirth channel XML for diff purposes.
 */
class ChannelComponents {
    final String channelInfo;
    final ChannelConnector sourceConnector;
    final LinkedHashMap<String, ChannelConnector> destinations;

    ChannelComponents(String channelInfo, ChannelConnector sourceConnector,
                      LinkedHashMap<String, ChannelConnector> destinations) {
        this.channelInfo     = channelInfo     != null ? channelInfo     : "";
        this.sourceConnector = sourceConnector;
        this.destinations    = destinations    != null ? destinations    : new LinkedHashMap<>();
    }
}
