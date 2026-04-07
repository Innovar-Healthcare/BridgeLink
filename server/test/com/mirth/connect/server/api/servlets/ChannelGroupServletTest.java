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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.SecurityContext;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.model.ChannelGroup;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ChannelController;
import com.mirth.connect.server.controllers.ControllerFactory;

public class ChannelGroupServletTest extends ServletTestBase {

    private static final String GROUP_ID1 = "group1";
    private static final String GROUP_ID2 = "group2";

    private static ChannelController mockChannelController;
    private ChannelGroupServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        mockChannelController = mock(ChannelController.class);
        when(controllerFactory.createChannelController()).thenReturn(mockChannelController);

        List<ChannelGroup> allGroups = new ArrayList<>();
        ChannelGroup group1 = new ChannelGroup();
        group1.setId(GROUP_ID1);
        group1.setName("Group 1");
        ChannelGroup group2 = new ChannelGroup();
        group2.setId(GROUP_ID2);
        group2.setName("Group 2");
        allGroups.add(group1);
        allGroups.add(group2);

        List<ChannelGroup> singleGroup = new ArrayList<>();
        singleGroup.add(group1);

        when(mockChannelController.getChannelGroups(isNull())).thenReturn(allGroups);
        Set<String> singleIdSet = new HashSet<>();
        singleIdSet.add(GROUP_ID1);
        when(mockChannelController.getChannelGroups(singleIdSet)).thenReturn(singleGroup);
        when(mockChannelController.updateChannelGroups(anySet(), anySet(), anyBoolean())).thenReturn(true);

        // Inject mock ControllerFactory via Guice so static fields in ChannelGroupServlet use it
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);
    }

    @Before
    public void beforeTest() {
        servlet = new TestChannelGroupServlet(request, mock(SecurityContext.class));
    }

    // ========== getChannelGroups ==========

    @Test
    public void testGetChannelGroupsAllWhenIdsNull() {
        List<ChannelGroup> groups = servlet.getChannelGroups(null);
        assertNotNull(groups);
        assertEquals(2, groups.size());
    }

    @Test
    public void testGetChannelGroupsAllWhenIdsEmpty() {
        List<ChannelGroup> groups = servlet.getChannelGroups(new HashSet<>());
        assertNotNull(groups);
        assertEquals(2, groups.size());
    }

    @Test
    public void testGetChannelGroupsFilteredByIds() {
        Set<String> ids = new HashSet<>();
        ids.add(GROUP_ID1);
        List<ChannelGroup> groups = servlet.getChannelGroups(ids);
        assertNotNull(groups);
        assertEquals(1, groups.size());
        assertEquals(GROUP_ID1, groups.get(0).getId());
    }

    // ========== getChannelGroupsPost ==========

    @Test
    public void testGetChannelGroupsPostDelegatesToGet() {
        List<ChannelGroup> groups = servlet.getChannelGroupsPost(null);
        assertNotNull(groups);
        assertEquals(2, groups.size());
    }

    @Test
    public void testGetChannelGroupsPostFilteredByIds() {
        Set<String> ids = new HashSet<>();
        ids.add(GROUP_ID1);
        List<ChannelGroup> groups = servlet.getChannelGroupsPost(ids);
        assertNotNull(groups);
        assertEquals(1, groups.size());
    }

    // ========== updateChannelGroups ==========

    @Test
    public void testUpdateChannelGroupsSuccess() throws Exception {
        Set<ChannelGroup> groups = new HashSet<>();
        ChannelGroup group = new ChannelGroup();
        group.setId(GROUP_ID1);
        group.setName("Updated Group");
        groups.add(group);

        Set<String> removedIds = new HashSet<>();
        removedIds.add(GROUP_ID2);

        boolean result = servlet.updateChannelGroups(groups, removedIds, false);
        assertTrue(result);
        verify(mockChannelController).updateChannelGroups(groups, removedIds, false);
    }

    @Test
    public void testUpdateChannelGroupsWithOverride() throws Exception {
        Set<ChannelGroup> groups = new HashSet<>();
        boolean result = servlet.updateChannelGroups(groups, new HashSet<>(), true);
        assertTrue(result);
    }

    @Test
    public void testUpdateChannelGroupsControllerException() throws Exception {
        when(mockChannelController.updateChannelGroups(anySet(), anySet(), anyBoolean()))
                .thenThrow(new ControllerException("update error"));
        try {
            servlet.updateChannelGroups(new HashSet<>(), new HashSet<>(), false);
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
            // Expected: ControllerException thrown by mock, either wrapped as MirthApiException
            // by the servlet's catch block, or propagated directly at runtime
        } finally {
            when(mockChannelController.updateChannelGroups(anySet(), anySet(), anyBoolean())).thenReturn(true);
        }
    }

    /**
     * Inner class to bypass login initialization in tests.
     */
    public class TestChannelGroupServlet extends ChannelGroupServlet {
        public TestChannelGroupServlet(HttpServletRequest request, SecurityContext sc) {
            super(request, sc);
        }

        @Override
        protected boolean isUserAuthorized() {
            return true;
        }
    }
}
