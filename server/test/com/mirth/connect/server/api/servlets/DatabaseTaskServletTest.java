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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.SecurityContext;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.model.DatabaseTask;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.DatabaseTaskController;

public class DatabaseTaskServletTest extends ServletTestBase {

    private static final String TASK_ID1 = "task1";
    private static final String TASK_ID2 = "task2";
    private static final String NONEXISTENT_TASK_ID = "nonexistent";

    private static DatabaseTaskController mockDbTaskController;
    private DatabaseTaskServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        mockDbTaskController = mock(DatabaseTaskController.class);
        when(controllerFactory.createDatabaseTaskController()).thenReturn(mockDbTaskController);

        Map<String, DatabaseTask> tasks = new HashMap<>();
        DatabaseTask task1 = new DatabaseTask(TASK_ID1);
        DatabaseTask task2 = new DatabaseTask(TASK_ID2);
        tasks.put(TASK_ID1, task1);
        tasks.put(TASK_ID2, task2);
        when(mockDbTaskController.getDatabaseTasks()).thenReturn(tasks);
        when(mockDbTaskController.runDatabaseTask(anyString())).thenReturn("Task completed");

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
        servlet = new TestDatabaseTaskServlet(request, mock(SecurityContext.class));
    }

    // ========== getDatabaseTasks ==========

    @Test
    public void testGetDatabaseTasks() {
        Map<String, DatabaseTask> tasks = servlet.getDatabaseTasks();
        assertNotNull(tasks);
        assertEquals(2, tasks.size());
    }

    @Test(expected = MirthApiException.class)
    public void testGetDatabaseTasksException() throws Exception {
        doThrow(new Exception("db error")).when(mockDbTaskController).getDatabaseTasks();
        try {
            servlet.getDatabaseTasks();
        } finally {
            Map<String, DatabaseTask> tasks = new HashMap<>();
            tasks.put(TASK_ID1, new DatabaseTask(TASK_ID1));
            tasks.put(TASK_ID2, new DatabaseTask(TASK_ID2));
            doReturn(tasks).when(mockDbTaskController).getDatabaseTasks();
        }
    }

    // ========== getDatabaseTask ==========

    @Test
    public void testGetDatabaseTaskFound() {
        DatabaseTask task = servlet.getDatabaseTask(TASK_ID1);
        assertNotNull(task);
    }

    @Test(expected = MirthApiException.class)
    public void testGetDatabaseTaskNotFound() {
        servlet.getDatabaseTask(NONEXISTENT_TASK_ID);
    }

    @Test(expected = MirthApiException.class)
    public void testGetDatabaseTaskEmptyMap() throws Exception {
        when(mockDbTaskController.getDatabaseTasks()).thenReturn(new HashMap<>());
        try {
            servlet.getDatabaseTask(TASK_ID1);
        } finally {
            Map<String, DatabaseTask> tasks = new HashMap<>();
            tasks.put(TASK_ID1, new DatabaseTask(TASK_ID1));
            tasks.put(TASK_ID2, new DatabaseTask(TASK_ID2));
            when(mockDbTaskController.getDatabaseTasks()).thenReturn(tasks);
        }
    }

    // ========== runDatabaseTask ==========

    @Test
    public void testRunDatabaseTask() throws Exception {
        String result = servlet.runDatabaseTask(TASK_ID1);
        assertEquals("Task completed", result);
    }

    @Test(expected = MirthApiException.class)
    public void testRunDatabaseTaskException() throws Exception {
        doThrow(new Exception("run error")).when(mockDbTaskController).runDatabaseTask(anyString());
        try {
            servlet.runDatabaseTask(TASK_ID1);
        } finally {
            doReturn("Task completed").when(mockDbTaskController).runDatabaseTask(anyString());
        }
    }

    // ========== cancelDatabaseTask ==========

    @Test
    public void testCancelDatabaseTask() throws Exception {
        doNothing().when(mockDbTaskController).cancelDatabaseTask(anyString());
        servlet.cancelDatabaseTask(TASK_ID1);
        verify(mockDbTaskController).cancelDatabaseTask(TASK_ID1);
    }

    @Test(expected = MirthApiException.class)
    public void testCancelDatabaseTaskException() throws Exception {
        doThrow(new Exception("cancel error")).when(mockDbTaskController).cancelDatabaseTask(anyString());
        try {
            servlet.cancelDatabaseTask(TASK_ID1);
        } finally {
            doNothing().when(mockDbTaskController).cancelDatabaseTask(anyString());
        }
    }

    /**
     * Inner class to bypass login initialization in tests.
     */
    public class TestDatabaseTaskServlet extends DatabaseTaskServlet {
        public TestDatabaseTaskServlet(HttpServletRequest request, SecurityContext sc) {
            super(request, sc);
        }

        @Override
        protected boolean isUserAuthorized() {
            return true;
        }
    }
}
