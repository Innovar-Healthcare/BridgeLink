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

/**
 * Holds the parsed representation of a single channel connector (source or destination).
 */
class ChannelConnector {
    final String transportName;
    final String xmlContent;

    ChannelConnector(String transportName, String xmlContent) {
        this.transportName = transportName != null ? transportName : "";
        this.xmlContent    = xmlContent    != null ? xmlContent    : "";
    }
}
