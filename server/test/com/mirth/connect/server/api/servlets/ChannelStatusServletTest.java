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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.SecurityContext;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.client.core.api.RawContent;
import com.mirth.connect.client.core.api.servlets.ChannelStatusServletInterface;
import com.mirth.connect.donkey.model.channel.DeployedState;
import com.mirth.connect.model.ChannelTag;
import com.mirth.connect.model.ChannelThreadInfo;
import com.mirth.connect.model.ChannelThreadReport;
import com.mirth.connect.model.DashboardChannelInfo;
import com.mirth.connect.model.DashboardStatus;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.channel.ErrorTaskHandler;
import com.mirth.connect.server.controllers.EngineController;

public class ChannelStatusServletTest extends ServletTestBase {

    static EngineController engineController;

    @BeforeClass
    public static void setup() throws Exception {
        ServletTestBase.setup();

        engineController = mock(EngineController.class);
        when(engineController.getChannelStatus(anyString())).thenAnswer((InvocationOnMock invocation) -> {
            DashboardStatus status = new DashboardStatus();
            status.setChannelId(invocation.getArgument(0));
            return status;
        });
        when(engineController.getChannelStatusList(any(), anyBoolean())).thenAnswer((InvocationOnMock invocation) -> {
            return getStatusList();
        });
        when(engineController.getChannelStatusList(any())).thenAnswer((InvocationOnMock invocation) -> {
            return getStatusList();
        });
        when(engineController.getDeployedIds()).thenAnswer((InvocationOnMock invocation) -> {
            Set<String> deployed = new HashSet<>();
            deployed.add("3");
            deployed.add("4");
            deployed.add("5");
            return deployed;
        });
        when(engineController.getChannelThreads(anyString(), anyInt())).thenAnswer((InvocationOnMock invocation) -> {
            String channelId = invocation.getArgument(0);
            if (!"deployed".equals(channelId)) {
                return null;
            }
            ChannelThreadReport report = new ChannelThreadReport();
            report.setChannelId(channelId);
            report.setChannelName("Deployed");
            report.setState(DeployedState.STOPPING);
            report.setLifecycleOverdue(true);
            ChannelThreadInfo thread = new ChannelThreadInfo();
            thread.setId(42L);
            thread.setName("Channel Dispatch Thread on Deployed (deployed)");
            thread.setState("WAITING");
            thread.getStackTrace().add("at java.net.SocketInputStream.socketRead0(Native Method)");
            report.getThreads().add(thread);
            return report;
        });
        when(controllerFactory.createEngineController()).thenReturn(engineController);

        when(configurationController.getChannelTags()).thenAnswer((InvocationOnMock invocation) -> {
            Set<ChannelTag> tags = new HashSet<>();
            tags.add(ChannelStatusServletTest.createTag("Tag1", Sets.newHashSet()));
            tags.add(ChannelStatusServletTest.createTag("Tag2", Sets.newHashSet("A", "B")));
            tags.add(ChannelStatusServletTest.createTag("Tag3", Sets.newHashSet("3", "4")));
            tags.add(ChannelStatusServletTest.createTag("Tag4", Sets.newHashSet("4")));
            return tags;
        });
    }

    private static DashboardStatus createStatus(String id, String name, DeployedState deployState) {
        DashboardStatus status = mock(DashboardStatus.class);
        when(status.getChannelId()).thenReturn(id);
        when(status.getName()).thenReturn(name);
        when(status.getState()).thenReturn(deployState);
        return status;
    }

    private static ChannelTag createTag(String name, Set<String> channelIds) {
        ChannelTag tag = mock(ChannelTag.class);
        when(tag.getName()).thenReturn(name);
        when(tag.getChannelIds()).thenReturn(channelIds);
        return tag;
    }

    private static List<DashboardStatus> getStatusList() {
        List<DashboardStatus> list = new LinkedList<>();
        list.add(ChannelStatusServletTest.createStatus("1", "One", DeployedState.STARTED));
        list.add(ChannelStatusServletTest.createStatus("2", "Two", DeployedState.STOPPED));
        list.add(ChannelStatusServletTest.createStatus("3", "Three", DeployedState.STARTED));
        list.add(ChannelStatusServletTest.createStatus("4", "Four", DeployedState.STARTED));
        list.add(ChannelStatusServletTest.createStatus("5", "Five", DeployedState.UNDEPLOYED));
        return list;
    }

    @Test
    public void testGetChannelStatusListFilterByName() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("user")).thenReturn("1");
        when(session.getAttribute("authorized")).thenReturn(Boolean.TRUE);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession()).thenReturn(session);

        SecurityContext sc = mock(SecurityContext.class);
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        // don't need to pass in list, engineController.getChannelStatusList() has been mocked to return a list of DashboardStatus, which we will filter on
        List<DashboardStatus> statusList = servlet.getChannelStatusList(null, "Name:Three", true);
        assertEquals(1, statusList.size());
        assertEquals("3", statusList.get(0).getChannelId());
        assertEquals("Three", statusList.get(0).getName());

        statusList = servlet.getChannelStatusList(null, "Name:3", true);
        assertEquals(0, statusList.size());

        statusList = servlet.getChannelStatusList(null, "Name:FAKE", true);
        assertEquals(0, statusList.size());
    }

    @Test
    public void testGetChannelStatusListNoFilter() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("user")).thenReturn("1");
        when(session.getAttribute("authorized")).thenReturn(Boolean.TRUE);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession()).thenReturn(session);

        SecurityContext sc = mock(SecurityContext.class);
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        // don't need to pass in list, engineController.getChannelStatusList() has been mocked to return a list of DashboardStatus, which we will filter on
        List<DashboardStatus> statusList = servlet.getChannelStatusList(null, null, true);

        // statusList == [{Dashboard Status with ID = "1"}, {Dashboard Status with ID = "2"}, {Dashboard Status with ID = "3"},
        //              {Dashboard Status with ID = "4"}, {Dashboard Status with ID = "5"}]
        assertEquals(5, statusList.size());
        assertEquals("1", statusList.get(0).getChannelId());
        assertEquals("2", statusList.get(1).getChannelId());
        assertEquals("3", statusList.get(2).getChannelId());
        assertEquals("4", statusList.get(3).getChannelId());
        assertEquals("5", statusList.get(4).getChannelId());
    }

    @Test
    public void testGetChannelStatusListFilterByTag() throws Exception {
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        // don't need to pass in list, engineController.getChannelStatusList() has been mocked to return a list of DashboardStatus, which we will filter on
        List<DashboardStatus> statusList = servlet.getChannelStatusList(null, "Tag:Tag3", true);
        // statusList == [{Dashboard Status with ID = "3"}, {Dashboard Status with ID = "4"}]
        assertEquals(2, statusList.size());
        assertEquals("3", statusList.get(0).getChannelId());
        assertEquals("4", statusList.get(1).getChannelId());

        // statusList == []
        statusList = servlet.getChannelStatusList(null, "Tag:Tag2", true);
        assertEquals(0, statusList.size());

        // statusList == []
        statusList = servlet.getChannelStatusList(null, "Tag:FAKE", true);
        assertEquals(0, statusList.size());
    }

    @Test
    public void testGetDashboardChannelInfo() throws Exception {
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        DashboardChannelInfo dashboardChannelInfo = servlet.getDashboardChannelInfo(1, "Tag:Tag3");
        // dashboardChannelInfo.getDeployedChannelCount() == 3
        assertEquals(3, dashboardChannelInfo.getDeployedChannelCount());
        // dashboardChannelInfo.getRemainingChannelIds() == ["4"]
        assertEquals(1, dashboardChannelInfo.getRemainingChannelIds().size());
        assertEquals("4", dashboardChannelInfo.getRemainingChannelIds().iterator().next());
        // dashboardChannelInfo.getRemainingChannelIds() == [{Dashboard Status with ID = "3"}, {Dashboard Status with ID = "4"}]
        assertEquals(2, dashboardChannelInfo.getDashboardStatuses().size());
        assertEquals("3", dashboardChannelInfo.getDashboardStatuses().get(0).getChannelId());
        assertEquals("4", dashboardChannelInfo.getDashboardStatuses().get(1).getChannelId());
    }

    /**
     * The redaction case runs against both method objects Jersey could hand the invocation
     * handler. It used to check only the interface method, and the implementation method is the
     * one Jersey actually passes since the 2.48 upgrade -- CheckAuthorizedChannelId resolves its
     * channel by matching a Param name, and Param is declared only on the interface, so a handler
     * that trusts the method it is given loses the channel and lets a restricted user reach a
     * redacted channel (IRT-1798).
     */
    @Test
    public void getChannelStatus() throws Throwable {
        DashboardStatus status = (DashboardStatus) ih.invoke(new ChannelStatusServlet(request, sc, controllerFactory), ChannelStatusServlet.class.getMethod("getChannelStatus", String.class), new Object[] {
                CHANNEL_ID1 });
        assertEquals(CHANNEL_ID1, status.getChannelId());

        for (Method method : new Method[] {
                ChannelStatusServlet.class.getMethod("getChannelStatus", String.class),
                ChannelStatusServletInterface.class.getMethod("getChannelStatus", String.class) }) {
            assertForbiddenInvocation(new ChannelStatusServlet(request, sc, controllerFactory), method, new Object[] {
                    DISALLOWED_CHANNEL_ID });
        }
    }

    @Test
    public void redactChannelStatuses() throws Throwable {
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        servlet.setOperation(null);

        List<DashboardStatus> statuses = new ArrayList<DashboardStatus>();
        statuses.add(DASHBOARD_STATUS1);
        statuses.add(DASHBOARD_STATUS2);
        List<DashboardStatus> redactedStatuses = servlet.redactChannelStatuses(statuses);
        assertEquals(statuses, redactedStatuses);

        List<DashboardStatus> statuses2 = new ArrayList<DashboardStatus>();
        statuses2.add(DASHBOARD_STATUS1);
        statuses2.add(DASHBOARD_STATUS2);
        statuses2.add(DISALLOWED_DASHBOARD_STATUS);
        redactedStatuses = servlet.redactChannelStatuses(statuses2);
        assertEquals(statuses, redactedStatuses);
    }

    @Test
    public void redactConnectorInfo() throws Throwable {
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        servlet.setOperation(null);

        Map<String, List<Integer>> connectorInfo = new HashMap<String, List<Integer>>();
        connectorInfo.put(CHANNEL_ID1, new ArrayList<Integer>());
        connectorInfo.put(CHANNEL_ID2, new ArrayList<Integer>());
        Map<String, List<Integer>> redactedConnectorInfo = servlet.redactConnectorInfo(connectorInfo);
        assertEquals(connectorInfo, redactedConnectorInfo);

        Map<String, List<Integer>> connectorInfo2 = new HashMap<String, List<Integer>>();
        connectorInfo2.put(CHANNEL_ID1, new ArrayList<Integer>());
        connectorInfo2.put(CHANNEL_ID2, new ArrayList<Integer>());
        connectorInfo2.put(DISALLOWED_CHANNEL_ID, new ArrayList<Integer>());
        redactedConnectorInfo = servlet.redactConnectorInfo(connectorInfo2);
        assertEquals(connectorInfo, redactedConnectorInfo);
    }

    // ========== New tests: start/stop/halt/pause/resume channels ==========

    @Test
    public void testStartChannels() throws Throwable {
        doNothing().when(engineController).startChannels(any(), any());
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        Set<String> channelIds = new HashSet<>();
        channelIds.add(CHANNEL_ID1);
        channelIds.add(CHANNEL_ID2);
        servlet.startChannels(channelIds, false);
        verify(engineController).startChannels(any(), any(ErrorTaskHandler.class));
    }

    @Test
    public void testStopChannels() throws Throwable {
        doNothing().when(engineController).stopChannels(any(), any());
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        Set<String> channelIds = new HashSet<>();
        channelIds.add(CHANNEL_ID1);
        servlet.stopChannels(channelIds, false);
        verify(engineController).stopChannels(any(), any(ErrorTaskHandler.class));
    }

    @Test
    public void testHaltChannels() throws Throwable {
        doNothing().when(engineController).haltChannels(any(), any());
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        Set<String> channelIds = new HashSet<>();
        channelIds.add(CHANNEL_ID1);
        servlet.haltChannels(channelIds, false);
        verify(engineController).haltChannels(any(), any(ErrorTaskHandler.class));
    }

    @Test
    public void testPauseChannels() throws Throwable {
        doNothing().when(engineController).pauseChannels(any(), any());
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        Set<String> channelIds = new HashSet<>();
        channelIds.add(CHANNEL_ID1);
        servlet.pauseChannels(channelIds, false);
        verify(engineController).pauseChannels(any(), any(ErrorTaskHandler.class));
    }

    @Test
    public void testResumeChannels() throws Throwable {
        doNothing().when(engineController).resumeChannels(any(), any());
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        Set<String> channelIds = new HashSet<>();
        channelIds.add(CHANNEL_ID1);
        servlet.resumeChannels(channelIds, false);
        verify(engineController).resumeChannels(any(), any(ErrorTaskHandler.class));
    }

    // ========== New tests: start/stop connectors ==========

    @Test
    public void testStartConnectors() throws Throwable {
        doNothing().when(engineController).startConnector(any(), any());
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        Map<String, List<Integer>> connectorInfo = new HashMap<>();
        connectorInfo.put(CHANNEL_ID1, Collections.singletonList(1));
        servlet.startConnectors(connectorInfo, false);
        verify(engineController).startConnector(any(), any(ErrorTaskHandler.class));
    }

    @Test
    public void testStopConnectors() throws Throwable {
        doNothing().when(engineController).stopConnector(any(), any());
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        Map<String, List<Integer>> connectorInfo = new HashMap<>();
        connectorInfo.put(CHANNEL_ID1, Collections.singletonList(1));
        servlet.stopConnectors(connectorInfo, false);
        verify(engineController).stopConnector(any(), any(ErrorTaskHandler.class));
    }

    // ========== New tests: getChannelStatusListPost delegates ==========

    @Test
    public void testGetChannelStatusListPostDelegates() throws Exception {
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        List<DashboardStatus> statusList = servlet.getChannelStatusListPost(null, null, true);
        assertEquals(5, statusList.size());
    }

    // ========== New tests: getDashboardChannelInfo with no filter ==========

    @Test
    public void testGetDashboardChannelInfoNoFilter() throws Exception {
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        DashboardChannelInfo info = servlet.getDashboardChannelInfo(10, null);
        assertEquals(3, info.getDeployedChannelCount());
    }

    @Test
    public void testGetDashboardChannelInfoEmptyFilter() throws Exception {
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        DashboardChannelInfo info = servlet.getDashboardChannelInfo(10, "");
        assertEquals(3, info.getDeployedChannelCount());
    }

    @Test
    public void testGetDashboardChannelInfoSmallFetchSize() throws Exception {
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        DashboardChannelInfo info = servlet.getDashboardChannelInfo(1, null);
        assertEquals(3, info.getDeployedChannelCount());
        // With fetchSize of 1, we should have remaining channel IDs
        assertEquals(2, info.getRemainingChannelIds().size());
    }

    // ========== IRT-2107: GET /channels/{channelId}/_threads ==========

    /**
     * Pins the content negotiation the endpoint depends on: the interface must declare both JSON and
     * XML so a client that accepts only one of them is not rejected with 406 before the method runs,
     * and the response must pin its Content-Type to JSON because that is the only form the payload
     * has. Both halves have regressed before on other raw endpoints (public PR #173).
     */
    @Test
    public void testGetChannelThreadsDeclaresJsonAndXmlAndPinsJson() throws Exception {
        java.lang.reflect.Method method = ChannelStatusServletInterface.class.getMethod("getChannelThreads", String.class, Integer.class);
        javax.ws.rs.Produces produces = method.getAnnotation(javax.ws.rs.Produces.class);
        assertNotNull("@Produces must be declared on the method itself", produces);
        List<String> mediaTypes = java.util.Arrays.asList(produces.value());
        assertTrue(mediaTypes.contains(javax.ws.rs.core.MediaType.APPLICATION_JSON));
        assertTrue(mediaTypes.contains(javax.ws.rs.core.MediaType.APPLICATION_XML));
        assertNotNull("path must be /{channelId}/_threads", method.getAnnotation(javax.ws.rs.Path.class));
        assertEquals("/{channelId}/_threads", method.getAnnotation(javax.ws.rs.Path.class).value());
        assertNotNull(method.getAnnotation(javax.ws.rs.GET.class));

        // Every parameter carries @Param, or the server answers with an empty 500
        for (java.lang.annotation.Annotation[] annotations : method.getParameterAnnotations()) {
            boolean hasParam = false;
            for (java.lang.annotation.Annotation annotation : annotations) {
                if (annotation instanceof com.mirth.connect.client.core.api.Param) {
                    hasParam = true;
                }
            }
            assertTrue("every servlet parameter needs @Param", hasParam);
        }

        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        javax.ws.rs.core.Response response = servlet.getChannelThreads("deployed", null);

        assertEquals(200, response.getStatus());
        assertEquals(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertTrue("the entity must be RawContent so it bypasses the envelope writers", response.getEntity() instanceof RawContent);

        JsonNode json = new ObjectMapper().readTree(((RawContent) response.getEntity()).getContent());
        assertEquals("deployed", json.get("channelId").asText());
        assertEquals("STOPPING", json.get("state").asText());
        assertTrue(json.get("lifecycleOverdue").asBoolean());
        assertEquals(1, json.get("threads").size());
        assertEquals(42L, json.get("threads").get(0).get("id").asLong());
        assertEquals("WAITING", json.get("threads").get(0).get("state").asText());
        assertEquals(1, json.get("threads").get(0).get("stackTrace").size());
        verify(engineController).getChannelThreads("deployed", 0);
    }

    @Test
    public void testGetChannelThreadsPassesMaxFrames() throws Exception {
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        servlet.getChannelThreads("deployed", 7);
        verify(engineController).getChannelThreads("deployed", 7);
    }

    @Test
    public void testGetChannelThreadsNotDeployedIs404() throws Exception {
        ChannelStatusServlet servlet = new ChannelStatusServlet(request, sc, controllerFactory);
        try {
            servlet.getChannelThreads("notdeployed", null);
            org.junit.Assert.fail("expected 404");
        } catch (MirthApiException e) {
            assertEquals(404, e.getResponse().getStatus());
        }
    }
}
