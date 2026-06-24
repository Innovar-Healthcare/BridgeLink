/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mirth.connect.model.User;
import com.mirth.connect.model.notification.Notification;

public class ConnectServiceUtil {
    public final static Integer MILLIS_PER_DAY = 86400000;

    public static void registerUser(String serverId, String mirthVersion, User user, String[] protocols, String[] cipherSuites) throws ClientException {
        // no-op: BridgeLink operates no registration server
    }

    public static List<Notification> getNotifications(String serverId, String mirthVersion, Map<String, String> extensionVersions, String[] protocols, String[] cipherSuites) throws Exception {
        return new ArrayList<Notification>();
    }

    public static int getNotificationCount(String serverId, String mirthVersion, Map<String, String> extensionVersions, Set<Integer> archivedNotifications, String[] protocols, String[] cipherSuites) {
        return 0;
    }

    public static boolean sendStatistics(String serverId, String mirthVersion, boolean server, String data, String[] protocols, String[] cipherSuites) {
        return false;
    }
}
