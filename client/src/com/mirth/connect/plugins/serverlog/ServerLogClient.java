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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.JComponent;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.ForbiddenException;
import com.mirth.connect.client.ui.LoadedExtensions;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.model.DashboardStatus;
import com.mirth.connect.plugins.DashboardTabPlugin;
import com.mirth.connect.plugins.DashboardTablePlugin;

public class ServerLogClient extends DashboardTabPlugin {
    private ServerLogPanel serverLogPanel;
    private LinkedList<ServerLogItem> serverLogs;
    private static final ServerLogItem unauthorizedLog = new ServerLogItem("You are not authorized to view the server log.");
    private int currentServerLogSize;
    private boolean receivedNewLogs;
    private Long lastLogId;
    private String currentServerId;

    // The set of channel IDs currently highlighted on the dashboard. Empty means "no filter — show
    // everything from the unified server buffer". When this set changes we drop the local buffer
    // and reset lastLogId so the next fetch returns a fresh view of the new selection.
    private Set<String> selectedChannelIds = Collections.emptySet();

    public ServerLogClient(String name) {
        super(name);
        serverLogs = new LinkedList<ServerLogItem>();
        serverLogPanel = new ServerLogPanel(this);
        currentServerLogSize = serverLogPanel.getCurrentServerLogSize();
    }

    public void clearLog() {
        serverLogs.clear();
        serverLogPanel.updateTable(null);
    }

    public void resetServerLogSize(int newServerLogSize) {
        // The log size is always set to 100 on the server (per-buffer).
        // On the client side, the max size is 99. When the user reduces it we only need to trim
        // the local buffer; the server-side buffers are untouched.
        if (newServerLogSize < currentServerLogSize) {
            synchronized (this) {
                while (newServerLogSize < serverLogs.size()) {
                    serverLogs.removeLast();
                }
            }
            serverLogPanel.updateTable(serverLogs);
        }
        currentServerLogSize = newServerLogSize;
    }

    @Override
    public void prepareData() throws ClientException {
        prepareData(null);
    }

    @Override
    public void prepareData(List<DashboardStatus> statuses) throws ClientException {
        receivedNewLogs = false;

        // Track which channels are selected on the dashboard. If the selection changed since the
        // last poll, drop the local buffer and reset lastLogId so we re-fetch the relevant slice
        // (otherwise we'd miss items whose IDs are below the previous lastLogId).
        Set<String> nextSelection = extractChannelIds(statuses);
        if (!nextSelection.equals(selectedChannelIds)) {
            selectedChannelIds = nextSelection;
            synchronized (this) {
                serverLogs.clear();
            }
            lastLogId = null;
        }

        if (!serverLogPanel.isPaused()) {
            List<ServerLogItem> serverLogReceived = new ArrayList<ServerLogItem>();
            try {
                // null/empty channelIds → server returns from the unified buffer (legacy view).
                // non-empty → server returns the requested channels + system rows merged.
                Set<String> channelFilter = selectedChannelIds.isEmpty() ? null : selectedChannelIds;
                serverLogReceived = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(ServerLogServletInterface.class).getServerLogs(currentServerLogSize, lastLogId, channelFilter);
            } catch (ClientException e) {
                if (e instanceof ForbiddenException) {
                    LinkedList<ServerLogItem> unauthorizedLogs = new LinkedList<ServerLogItem>();
                    if (serverLogs.isEmpty() || !serverLogs.getLast().equals(unauthorizedLog)) {
                        unauthorizedLogs.add(unauthorizedLog);
                    }
                    serverLogReceived = unauthorizedLogs;
                    parent.alertThrowable(parent, e, false);
                } else {
                    throw e;
                }
            }

            if (serverLogReceived.size() > 0) {
                receivedNewLogs = true;

                ServerLogItem latestItem = serverLogReceived.get(0);
                if (latestItem.getId() != null && latestItem.getId() > 0) {
                    lastLogId = latestItem.getId();
                }

                synchronized (this) {
                    for (int i = serverLogReceived.size() - 1; i >= 0; i--) {
                        while (currentServerLogSize <= serverLogs.size()) {
                            serverLogs.removeLast();
                        }
                        serverLogs.addFirst(serverLogReceived.get(i));
                    }
                }
            }
        }
    }

    private static Set<String> extractChannelIds(List<DashboardStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> ids = new HashSet<String>();
        for (DashboardStatus status : statuses) {
            if (status != null && status.getChannelId() != null) {
                ids.add(status.getChannelId());
            }
        }
        return ids.isEmpty() ? Collections.<String>emptySet() : ids;
    }

    @Override
    public void update() {
        update(null);
    }

    @Override
    public void update(List<DashboardStatus> statuses) {
        boolean serverIdChanged = false;
        String serverId = null;
        for (DashboardTablePlugin plugin : LoadedExtensions.getInstance().getDashboardTablePlugins().values()) {
            serverId = plugin.getServerId();
            if (serverId != null) {
                break;
            }
        }
        if (currentServerId != serverId) {
            currentServerId = serverId;
            serverIdChanged = true;
        }

        if (!serverLogPanel.isPaused() && (receivedNewLogs || serverIdChanged)) {
            serverLogPanel.updateTable(serverLogs);
        }
    }

    @Override
    public JComponent getTabComponent() {
        return serverLogPanel;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {
        reset();
    }

    @Override
    public void reset() {
        clearLog();
    }

    @Override
    public String getPluginPointName() {
        return ServerLogServletInterface.PLUGIN_POINT;
    }
}
