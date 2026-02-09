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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
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
import com.mirth.connect.model.ServerEvent;
import com.mirth.connect.model.ServerEvent.Level;
import com.mirth.connect.model.ServerEvent.Outcome;
import com.mirth.connect.model.filters.EventFilter;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

public class EventServletTest extends ServletTestBase {

    private static EventController mockEventController;
    private EventServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        mockEventController = mock(EventController.class);
        when(controllerFactory.createEventController()).thenReturn(mockEventController);

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
    public void beforeTest() throws Exception {
        // Reset mock to clear any leftover thenThrow stubs
        reset(mockEventController);

        // Set up common stubs using doReturn to avoid invoking mock methods
        doReturn(100).when(mockEventController).getMaxEventId();

        ServerEvent event1 = new ServerEvent();
        event1.setId(1);
        event1.setName("Deploy");
        event1.setLevel(Level.INFORMATION);
        event1.setOutcome(Outcome.SUCCESS);

        ServerEvent event2 = new ServerEvent();
        event2.setId(2);
        event2.setName("Undeploy");
        event2.setLevel(Level.INFORMATION);
        event2.setOutcome(Outcome.SUCCESS);

        List<ServerEvent> allEvents = new ArrayList<>();
        allEvents.add(event1);
        allEvents.add(event2);

        List<ServerEvent> singleEvent = new ArrayList<>();
        singleEvent.add(event1);

        doReturn(allEvents).when(mockEventController).getEvents(any(EventFilter.class), anyInt(), anyInt());
        doReturn(2L).when(mockEventController).getEventCount(any(EventFilter.class));
        doReturn("<events>exported</events>").when(mockEventController).exportAllEvents();

        servlet = new TestEventServlet(request, mock(SecurityContext.class));
    }

    // ========== getMaxEventId ==========

    @Test
    public void testGetMaxEventId() {
        Integer maxId = servlet.getMaxEventId();
        assertEquals(Integer.valueOf(100), maxId);
    }

    @Test(expected = MirthApiException.class)
    public void testGetMaxEventIdControllerException() throws Exception {
        doThrow(new ControllerException("error")).when(mockEventController).getMaxEventId();
        servlet.getMaxEventId();
    }

    // ========== getEvent ==========

    @Test
    public void testGetEventFound() throws Exception {
        ServerEvent event = new ServerEvent();
        event.setId(42);
        event.setName("Test Event");
        List<ServerEvent> events = new ArrayList<>();
        events.add(event);
        doReturn(events).when(mockEventController).getEvents(any(EventFilter.class), anyInt(), anyInt());

        ServerEvent result = servlet.getEvent(42);
        assertNotNull(result);
        assertEquals("Test Event", result.getName());
    }

    @Test(expected = MirthApiException.class)
    public void testGetEventNotFound() throws Exception {
        doReturn(new ArrayList<>()).when(mockEventController).getEvents(any(EventFilter.class), anyInt(), anyInt());
        servlet.getEvent(999);
    }

    @Test(expected = MirthApiException.class)
    public void testGetEventControllerException() throws Exception {
        doThrow(new ControllerException("error")).when(mockEventController).getEvents(any(EventFilter.class), anyInt(), anyInt());
        servlet.getEvent(1);
    }

    // ========== getEvents (filter) ==========

    @Test
    public void testGetEventsWithFilter() throws Exception {
        List<ServerEvent> events = new ArrayList<>();
        events.add(new ServerEvent());
        doReturn(events).when(mockEventController).getEvents(any(EventFilter.class), anyInt(), anyInt());

        EventFilter filter = new EventFilter();
        List<ServerEvent> result = servlet.getEvents(filter, 0, 50);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test(expected = MirthApiException.class)
    public void testGetEventsWithFilterControllerException() throws Exception {
        doThrow(new ControllerException("error")).when(mockEventController).getEvents(any(EventFilter.class), anyInt(), anyInt());
        servlet.getEvents(new EventFilter(), 0, 50);
    }

    // ========== getEvents (individual params) ==========

    @Test
    public void testGetEventsWithIndividualParams() throws Exception {
        List<ServerEvent> events = new ArrayList<>();
        events.add(new ServerEvent());
        events.add(new ServerEvent());
        doReturn(events).when(mockEventController).getEvents(any(EventFilter.class), anyInt(), anyInt());

        Set<Level> levels = new HashSet<>();
        levels.add(Level.INFORMATION);

        List<ServerEvent> result = servlet.getEvents(null, null, levels, null, null, "Deploy", null, null, null, null, null, 0, 50);
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    public void testGetEventsWithAllNullParams() throws Exception {
        List<ServerEvent> events = new ArrayList<>();
        doReturn(events).when(mockEventController).getEvents(any(EventFilter.class), anyInt(), anyInt());

        List<ServerEvent> result = servlet.getEvents(null, null, null, null, null, null, null, null, null, null, null, 0, 50);
        assertNotNull(result);
    }

    // ========== getEventCount (filter) ==========

    @Test
    public void testGetEventCountWithFilter() throws Exception {
        doReturn(42L).when(mockEventController).getEventCount(any(EventFilter.class));
        Long count = servlet.getEventCount(new EventFilter());
        assertEquals(Long.valueOf(42), count);
    }

    @Test(expected = MirthApiException.class)
    public void testGetEventCountWithFilterControllerException() throws Exception {
        doThrow(new ControllerException("error")).when(mockEventController).getEventCount(any(EventFilter.class));
        servlet.getEventCount(new EventFilter());
    }

    // ========== getEventCount (individual params) ==========

    @Test
    public void testGetEventCountWithIndividualParams() throws Exception {
        doReturn(10L).when(mockEventController).getEventCount(any(EventFilter.class));
        Long count = servlet.getEventCount(null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(Long.valueOf(10), count);
    }

    // ========== exportAllEvents ==========

    @Test
    public void testExportAllEvents() throws Exception {
        String result = servlet.exportAllEvents();
        assertEquals("<events>exported</events>", result);
    }

    @Test(expected = MirthApiException.class)
    public void testExportAllEventsControllerException() throws Exception {
        doThrow(new ControllerException("error")).when(mockEventController).exportAllEvents();
        servlet.exportAllEvents();
    }

    /**
     * Inner class to bypass login initialization in tests.
     */
    public class TestEventServlet extends EventServlet {
        public TestEventServlet(HttpServletRequest request, SecurityContext sc) {
            super(request, sc);
        }

        @Override
        protected boolean isUserAuthorized() {
            return true;
        }
    }
}
