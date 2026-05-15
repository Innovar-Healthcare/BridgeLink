/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.serverlog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultServerLogController extends ServerLogController {

    // Maximum entries per buffer (the unified buffer, the per-channel buffers, and the system buffer
    // are each capped at this value). Not the number of lines.
    private static final int LOG_SIZE = 100;

    // Unified buffer of every entry, used when no channel filter is requested.
    private final LinkedList<ServerLogItem> allLogs = new LinkedList<ServerLogItem>();

    // Per-channel buffers so a noisy channel cannot evict a quiet channel's history.
    private final Map<String, LinkedList<ServerLogItem>> channelLogs = new HashMap<String, LinkedList<ServerLogItem>>();

    // System buffer: entries with no channel context (startup, shutdown, FATAL, etc.). Always
    // merged into channel-filtered results so critical events remain visible regardless of selection.
    private final LinkedList<ServerLogItem> systemLogs = new LinkedList<ServerLogItem>();

    private final Object lock = new Object();

    protected DefaultServerLogController() {}

    @Override
    public void addLogItem(ServerLogItem logItem) {
        synchronized (lock) {
            addBounded(allLogs, logItem);
            String channelId = logItem.getChannelId();
            if (channelId != null) {
                LinkedList<ServerLogItem> bucket = channelLogs.get(channelId);
                if (bucket == null) {
                    bucket = new LinkedList<ServerLogItem>();
                    channelLogs.put(channelId, bucket);
                }
                addBounded(bucket, logItem);
            } else {
                addBounded(systemLogs, logItem);
            }
        }
    }

    private static void addBounded(LinkedList<ServerLogItem> buffer, ServerLogItem item) {
        if (buffer.size() == LOG_SIZE) {
            buffer.removeLast();
        }
        buffer.addFirst(item);
    }

    @Override
    public List<ServerLogItem> getServerLogs(int fetchSize, Long lastLogId, Set<String> channelIds) {
        // Defensive: a missing or negative fetchSize from the REST binding would otherwise blow
        // up the ArrayList constructor below with NegativeArraySizeException.
        if (fetchSize < 0) {
            fetchSize = 0;
        }

        // Snapshot the relevant buffers under the lock, then build the response outside it so we
        // don't hold the lock across the (small) sort/filter cost.
        List<ServerLogItem> snapshot;

        synchronized (lock) {
            if (channelIds == null || channelIds.isEmpty()) {
                // No channel filter: return from the unified buffer (unchanged legacy behavior).
                snapshot = new ArrayList<ServerLogItem>(allLogs);
            } else {
                // Merge the requested per-channel buffers with the system buffer. Deduplicate by
                // id in case the same item somehow ended up in both (defensive — shouldn't happen
                // with the current addLogItem branching).
                Set<Long> seenIds = new HashSet<Long>();
                snapshot = new ArrayList<ServerLogItem>();
                for (String channelId : channelIds) {
                    LinkedList<ServerLogItem> bucket = channelLogs.get(channelId);
                    if (bucket == null) {
                        continue;
                    }
                    for (ServerLogItem item : bucket) {
                        if (item.getId() != null && seenIds.add(item.getId())) {
                            snapshot.add(item);
                        }
                    }
                }
                for (ServerLogItem item : systemLogs) {
                    if (item.getId() != null && seenIds.add(item.getId())) {
                        snapshot.add(item);
                    }
                }
            }
        }

        // Sort newest first by id (id is monotonically increasing per server).
        Collections.sort(snapshot, new Comparator<ServerLogItem>() {
            @Override
            public int compare(ServerLogItem a, ServerLogItem b) {
                Long ida = a.getId();
                Long idb = b.getId();
                if (ida == null && idb == null) return 0;
                if (ida == null) return 1;
                if (idb == null) return -1;
                return Long.compare(idb, ida);
            }
        });

        List<ServerLogItem> result = new ArrayList<ServerLogItem>(Math.min(fetchSize, snapshot.size()));
        for (ServerLogItem item : snapshot) {
            if (lastLogId != null && item.getId() != null && item.getId() <= lastLogId) {
                continue;
            }
            result.add(item);
            if (result.size() >= fetchSize) {
                break;
            }
        }
        return result;
    }
}
