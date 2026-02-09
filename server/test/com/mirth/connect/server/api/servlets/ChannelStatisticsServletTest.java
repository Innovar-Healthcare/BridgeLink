/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.api.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.model.ChannelStatistics;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ChannelController;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.EngineController;

public class ChannelStatisticsServletTest extends ServletTestBase {

    private static final String CHANNEL_ID_A = "channelA";
    private static final String CHANNEL_ID_B = "channelB";
    private static final String SERVER_ID = "serverId1";

    private static EngineController mockEngineController;
    private static ChannelController mockChannelController;
    private static ConfigurationController mockConfigController;

    @BeforeClass
    public static void setup() throws Exception {
        ServletTestBase.setup();

        mockEngineController = mock(EngineController.class);
        mockChannelController = mock(ChannelController.class);
        mockConfigController = mock(ConfigurationController.class);

        when(controllerFactory.createEngineController()).thenReturn(mockEngineController);
        when(controllerFactory.createChannelController()).thenReturn(mockChannelController);
        when(controllerFactory.createConfigurationController()).thenReturn(mockConfigController);

        when(mockConfigController.getServerId()).thenReturn(SERVER_ID);

        // Setup stats list
        ChannelStatistics statsA = new ChannelStatistics();
        statsA.setChannelId(CHANNEL_ID_A);
        statsA.setServerId(SERVER_ID);
        statsA.setReceived(100);
        statsA.setSent(80);
        statsA.setFiltered(10);
        statsA.setError(5);
        statsA.setQueued(5);

        ChannelStatistics statsB = new ChannelStatistics();
        statsB.setChannelId(CHANNEL_ID_B);
        statsB.setServerId(SERVER_ID);
        statsB.setReceived(200);
        statsB.setSent(150);
        statsB.setFiltered(30);
        statsB.setError(10);
        statsB.setQueued(10);

        List<ChannelStatistics> statsList = new ArrayList<>();
        statsList.add(statsA);
        statsList.add(statsB);

        List<ChannelStatistics> singleStatsList = new ArrayList<>();
        singleStatsList.add(statsA);

        when(mockEngineController.getChannelStatisticsList(any(), anyBoolean(), any(), any())).thenReturn(statsList);
        when(mockEngineController.getChannelStatisticsList(any(Set.class), anyBoolean())).thenReturn(singleStatsList);

        doNothing().when(mockChannelController).resetStatistics(any(), any());
        doNothing().when(mockChannelController).resetAllStatistics();
    }

    // ========== getStatistics (list) ==========

    @Test
    public void testGetStatisticsBasic() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        List<ChannelStatistics> stats = servlet.getStatistics(null, false, null, null, false);
        assertNotNull(stats);
        assertEquals(2, stats.size());
    }

    @Test
    public void testGetStatisticsWithChannelIds() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        Set<String> channelIds = new HashSet<>(Arrays.asList(CHANNEL_ID_A));
        List<ChannelStatistics> stats = servlet.getStatistics(channelIds, false, null, null, false);
        assertNotNull(stats);
    }

    @Test
    public void testGetStatisticsAggregated() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        List<ChannelStatistics> stats = servlet.getStatistics(null, false, null, null, true);
        assertNotNull(stats);
        assertEquals(1, stats.size());

        ChannelStatistics aggregated = stats.get(0);
        assertEquals(SERVER_ID, aggregated.getServerId());
        assertEquals(300, aggregated.getReceived());
        assertEquals(230, aggregated.getSent());
        assertEquals(40, aggregated.getFiltered());
        assertEquals(15, aggregated.getError());
        assertEquals(15, aggregated.getQueued());
    }

    @Test(expected = MirthApiException.class)
    public void testGetStatisticsIncludeAndExcludeConflict() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        Set<Integer> includeIds = new HashSet<>(Arrays.asList(0));
        Set<Integer> excludeIds = new HashSet<>(Arrays.asList(1));
        servlet.getStatistics(null, false, includeIds, excludeIds, false);
    }

    @Test
    public void testGetStatisticsWithIncludeMetadataIds() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        Set<Integer> includeIds = new HashSet<>(Arrays.asList(0, 1));
        List<ChannelStatistics> stats = servlet.getStatistics(null, false, includeIds, null, false);
        assertNotNull(stats);
    }

    @Test
    public void testGetStatisticsWithExcludeMetadataIds() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        Set<Integer> excludeIds = new HashSet<>(Arrays.asList(2));
        List<ChannelStatistics> stats = servlet.getStatistics(null, false, null, excludeIds, false);
        assertNotNull(stats);
    }

    @Test
    public void testGetStatisticsIncludeUndeployed() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        List<ChannelStatistics> stats = servlet.getStatistics(null, true, null, null, false);
        assertNotNull(stats);
    }

    // ========== getStatisticsPost ==========

    @Test
    public void testGetStatisticsPostDelegatesToGet() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        List<ChannelStatistics> stats = servlet.getStatisticsPost(null, false, null, null, false);
        assertNotNull(stats);
        assertEquals(2, stats.size());
    }

    @Test
    public void testGetStatisticsPostAggregated() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        List<ChannelStatistics> stats = servlet.getStatisticsPost(null, false, null, null, true);
        assertEquals(1, stats.size());
        assertEquals(300, stats.get(0).getReceived());
    }

    // ========== getStatistics (single channel) ==========

    @Test
    public void testGetStatisticsSingleChannelFound() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        ChannelStatistics stats = servlet.getStatistics(CHANNEL_ID_A);
        assertNotNull(stats);
        assertEquals(CHANNEL_ID_A, stats.getChannelId());
    }

    @Test
    public void testGetStatisticsSingleChannelEmptyResult() {
        // Mock empty result for a specific channel
        when(mockEngineController.getChannelStatisticsList(new HashSet<>(Arrays.asList("emptyChannel")), true))
                .thenReturn(new ArrayList<>());

        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        ChannelStatistics stats = servlet.getStatistics("emptyChannel");
        assertNotNull(stats);
        assertEquals("emptyChannel", stats.getChannelId());
        assertEquals(SERVER_ID, stats.getServerId());
        assertEquals(0, stats.getReceived());
    }

    // ========== clearStatistics ==========

    @Test
    public void testClearStatisticsAllFlags() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        Map<String, List<Integer>> channelConnectorMap = new HashMap<>();
        channelConnectorMap.put(CHANNEL_ID_A, Arrays.asList(0, 1));
        servlet.clearStatistics(channelConnectorMap, true, true, true, true);
        verify(mockChannelController).resetStatistics(any(), any());
    }

    @Test
    public void testClearStatisticsReceivedOnly() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        Map<String, List<Integer>> channelConnectorMap = new HashMap<>();
        channelConnectorMap.put(CHANNEL_ID_A, Arrays.asList(0));
        servlet.clearStatistics(channelConnectorMap, true, false, false, false);
        verify(mockChannelController).resetStatistics(any(), any());
    }

    @Test
    public void testClearStatisticsNoFlags() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        Map<String, List<Integer>> channelConnectorMap = new HashMap<>();
        servlet.clearStatistics(channelConnectorMap, false, false, false, false);
        verify(mockChannelController).resetStatistics(any(), any());
    }

    // ========== clearAllStatistics ==========

    @Test
    public void testClearAllStatistics() {
        ChannelStatisticsServlet servlet = new ChannelStatisticsServlet(request, sc, controllerFactory);
        servlet.clearAllStatistics();
        verify(mockChannelController).resetAllStatistics();
    }
}
