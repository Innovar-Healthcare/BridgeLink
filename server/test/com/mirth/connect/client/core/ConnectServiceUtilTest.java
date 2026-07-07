/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.mirth.connect.model.User;
import com.mirth.connect.model.notification.Notification;

public class ConnectServiceUtilTest {

    private static final String[] PROTOCOLS = new String[0];
    private static final String[] CIPHER_SUITES = new String[0];

    @Test
    public void getNotifications_returnsEmptyList() throws Exception {
        Map<String, String> extVersions = new HashMap<String, String>();
        List<Notification> result = ConnectServiceUtil.getNotifications(
                "server1", "4.6.0", extVersions, PROTOCOLS, CIPHER_SUITES);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void getNotificationCount_returnsZero() throws Exception {
        Map<String, String> extVersions = new HashMap<String, String>();
        Set<Integer> archived = new HashSet<Integer>();
        int count = ConnectServiceUtil.getNotificationCount(
                "server1", "4.6.0", extVersions, archived, PROTOCOLS, CIPHER_SUITES);
        assertEquals(0, count);
    }

    @Test
    public void sendStatistics_returnsFalse() throws Exception {
        boolean sent = ConnectServiceUtil.sendStatistics(
                "server1", "4.6.0", true, "{}", PROTOCOLS, CIPHER_SUITES);
        assertFalse(sent);
    }

    @Test
    public void registerUser_completesWithoutException() throws Exception {
        ConnectServiceUtil.registerUser("server1", "4.6.0", new User(), PROTOCOLS, CIPHER_SUITES);
    }

    @Test
    public void millisPerDay_constant_preserved() {
        assertEquals(Integer.valueOf(86400000), ConnectServiceUtil.MILLIS_PER_DAY);
    }
}
