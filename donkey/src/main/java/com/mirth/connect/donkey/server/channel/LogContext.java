/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.channel;

import org.apache.logging.log4j.ThreadContext;

/**
 * Thin wrapper around Log4j2 ThreadContext (MDC) for channel-scoped log
 * enrichment. Each helper method returns an AutoCloseable that restores
 * the previous value of the affected keys on close, so scopes may be
 * nested safely with try-with-resources and pooled threads cannot leak
 * stale context between unrelated work units.
 */
public final class LogContext {

    public static final String CHANNEL_ID = "channelId";
    public static final String CHANNEL_NAME = "channelName";
    public static final String META_DATA_ID = "metaDataId";
    public static final String CONNECTOR = "connectorName";
    public static final String MESSAGE_ID = "messageId";
    public static final String SCRIPT_PHASE = "scriptPhase";
    public static final String SCRIPT_LINE = "scriptLine";

    private LogContext() {}

    public static Scope channel(String channelId, String channelName) {
        Scope scope = new Scope();
        scope.put(CHANNEL_ID, channelId);
        scope.put(CHANNEL_NAME, channelName);
        return scope;
    }

    public static Scope connector(String connectorName, int metaDataId) {
        Scope scope = new Scope();
        scope.put(CONNECTOR, connectorName);
        scope.put(META_DATA_ID, Integer.toString(metaDataId));
        return scope;
    }

    public static Scope message(long messageId) {
        Scope scope = new Scope();
        scope.put(MESSAGE_ID, Long.toString(messageId));
        return scope;
    }

    /**
     * Scope for a JavaScript script execution failure: which phase (filter, transformer,
     * response, etc.) and which line in the user's script triggered the error. lineNumber &lt; 0
     * indicates the line is unknown.
     */
    public static Scope script(String phase, int lineNumber) {
        Scope scope = new Scope();
        scope.put(SCRIPT_PHASE, phase);
        if (lineNumber >= 0) {
            scope.put(SCRIPT_LINE, Integer.toString(lineNumber));
        }
        return scope;
    }

    public static void clear() {
        ThreadContext.clearMap();
    }

    /**
     * Records previous values for any keys it touches and restores them
     * on close. Always use with try-with-resources.
     */
    public static final class Scope implements AutoCloseable {
        private final java.util.Map<String, String> previous = new java.util.HashMap<>();
        private final java.util.Set<String> addedKeys = new java.util.HashSet<>();

        private void put(String key, String value) {
            if (value == null) {
                return;
            }
            String prior = ThreadContext.get(key);
            if (!previous.containsKey(key) && !addedKeys.contains(key)) {
                if (prior == null) {
                    addedKeys.add(key);
                } else {
                    previous.put(key, prior);
                }
            }
            ThreadContext.put(key, value);
        }

        @Override
        public void close() {
            for (String key : addedKeys) {
                ThreadContext.remove(key);
            }
            for (java.util.Map.Entry<String, String> e : previous.entrySet()) {
                ThreadContext.put(e.getKey(), e.getValue());
            }
        }
    }
}
