/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.serverlog;

import java.util.List;
import java.util.Set;

import com.mirth.connect.server.ExtensionLoader;

public abstract class ServerLogController {

    private static ServerLogController instance = null;

    public static ServerLogController getInstance() {
        synchronized (DefaultServerLogController.class) {
            if (instance == null) {
                instance = ExtensionLoader.getInstance().getControllerInstance(ServerLogController.class);

                if (instance == null) {
                    instance = new DefaultServerLogController();
                }
            }

            return instance;
        }
    }

    public abstract void addLogItem(ServerLogItem logItem);

    public List<ServerLogItem> getServerLogs(int fetchSize, Long lastLogId) {
        return getServerLogs(fetchSize, lastLogId, null);
    }

    /**
     * Returns recent server log entries.
     *
     * @param fetchSize  max number of entries to return
     * @param lastLogId  if non-null, only entries with an id strictly greater than this are returned
     * @param channelIds if null or empty, returns entries from the unified buffer (all channels).
     *                   Otherwise returns entries from the requested per-channel buffers merged
     *                   with the system buffer (entries with no channel context).
     */
    public abstract List<ServerLogItem> getServerLogs(int fetchSize, Long lastLogId, Set<String> channelIds);
}
