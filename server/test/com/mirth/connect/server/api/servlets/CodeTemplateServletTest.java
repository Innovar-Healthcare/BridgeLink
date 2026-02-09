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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.mirth.connect.model.ServerEventContext;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrarySaveResult;
import com.mirth.connect.model.codetemplates.CodeTemplateSummary;
import com.mirth.connect.server.api.ServletTestBase;
import com.mirth.connect.server.controllers.CodeTemplateController;
import com.mirth.connect.server.controllers.ControllerFactory;

public class CodeTemplateServletTest extends ServletTestBase {

    private static final String LIB_ID1 = "lib1";
    private static final String LIB_ID2 = "lib2";
    private static final String TEMPLATE_ID1 = "template1";
    private static final String TEMPLATE_ID2 = "template2";
    private static final String NONEXISTENT_ID = "nonexistent";

    private static CodeTemplateController mockCodeTemplateController;
    private CodeTemplateServlet servlet;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ServletTestBase.setup();

        mockCodeTemplateController = mock(CodeTemplateController.class);
        when(controllerFactory.createCodeTemplateController()).thenReturn(mockCodeTemplateController);

        // Setup libraries
        CodeTemplateLibrary lib1 = new CodeTemplateLibrary();
        lib1.setId(LIB_ID1);
        lib1.setName("Library 1");
        lib1.setEnabledChannelIds(new HashSet<>());
        lib1.setDisabledChannelIds(new HashSet<>());

        CodeTemplateLibrary lib2 = new CodeTemplateLibrary();
        lib2.setId(LIB_ID2);
        lib2.setName("Library 2");
        lib2.setEnabledChannelIds(new HashSet<>());
        lib2.setDisabledChannelIds(new HashSet<>());

        List<CodeTemplateLibrary> allLibraries = new ArrayList<>();
        allLibraries.add(lib1);
        allLibraries.add(lib2);

        List<CodeTemplateLibrary> singleLibrary = new ArrayList<>();
        singleLibrary.add(lib1);

        when(mockCodeTemplateController.getLibraries(isNull(), anyBoolean())).thenReturn(allLibraries);
        when(mockCodeTemplateController.getLibraries(Collections.singleton(LIB_ID1), false)).thenReturn(singleLibrary);
        when(mockCodeTemplateController.getLibraries(Collections.singleton(LIB_ID1), true)).thenReturn(singleLibrary);
        when(mockCodeTemplateController.getLibraries(Collections.singleton(NONEXISTENT_ID), false)).thenReturn(new ArrayList<>());
        when(mockCodeTemplateController.getLibraries(Collections.singleton(NONEXISTENT_ID), true)).thenReturn(new ArrayList<>());
        when(mockCodeTemplateController.updateLibraries(any(), any(), anyBoolean())).thenReturn(true);

        // Setup code templates
        CodeTemplate template1 = new CodeTemplate(TEMPLATE_ID1);
        template1.setName("Template 1");

        CodeTemplate template2 = new CodeTemplate(TEMPLATE_ID2);
        template2.setName("Template 2");

        List<CodeTemplate> allTemplates = new ArrayList<>();
        allTemplates.add(template1);
        allTemplates.add(template2);

        List<CodeTemplate> singleTemplate = new ArrayList<>();
        singleTemplate.add(template1);

        when(mockCodeTemplateController.getCodeTemplates(isNull())).thenReturn(allTemplates);
        when(mockCodeTemplateController.getCodeTemplates(Collections.singleton(TEMPLATE_ID1))).thenReturn(singleTemplate);
        when(mockCodeTemplateController.getCodeTemplates(Collections.singleton(NONEXISTENT_ID))).thenReturn(new ArrayList<>());
        when(mockCodeTemplateController.updateCodeTemplate(any(), any(), anyBoolean())).thenReturn(true);
        doNothing().when(mockCodeTemplateController).removeCodeTemplate(anyString(), any());

        // Setup code template summary
        List<CodeTemplateSummary> summaries = new ArrayList<>();
        CodeTemplateSummary summary = new CodeTemplateSummary(TEMPLATE_ID1, template1);
        summaries.add(summary);
        when(mockCodeTemplateController.getCodeTemplateSummary(any())).thenReturn(summaries);

        // Setup library save result
        CodeTemplateLibrarySaveResult saveResult = mock(CodeTemplateLibrarySaveResult.class);
        when(mockCodeTemplateController.updateLibrariesAndTemplates(any(), any(), any(), any(), any(), anyBoolean())).thenReturn(saveResult);

        // Inject mock ControllerFactory via Guice
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
        servlet = new TestCodeTemplateServlet(request, mock(SecurityContext.class));
    }

    // ========== getCodeTemplateLibraries ==========

    @Test
    public void testGetCodeTemplateLibrariesAll() throws Exception {
        List<CodeTemplateLibrary> libraries = servlet.getCodeTemplateLibraries(null, false);
        assertNotNull(libraries);
        assertEquals(2, libraries.size());
    }

    @Test
    public void testGetCodeTemplateLibrariesAllWithEmptySet() throws Exception {
        List<CodeTemplateLibrary> libraries = servlet.getCodeTemplateLibraries(new HashSet<>(), false);
        assertNotNull(libraries);
        assertEquals(2, libraries.size());
    }

    @Test
    public void testGetCodeTemplateLibrariesFiltered() throws Exception {
        Set<String> ids = new HashSet<>();
        ids.add(LIB_ID1);
        List<CodeTemplateLibrary> libraries = servlet.getCodeTemplateLibraries(ids, false);
        assertNotNull(libraries);
        assertEquals(1, libraries.size());
        assertEquals(LIB_ID1, libraries.get(0).getId());
    }

    // ========== getCodeTemplateLibrariesPost ==========

    @Test
    public void testGetCodeTemplateLibrariesPostDelegates() throws Exception {
        List<CodeTemplateLibrary> libraries = servlet.getCodeTemplateLibrariesPost(null, false);
        assertNotNull(libraries);
        assertEquals(2, libraries.size());
    }

    // ========== getCodeTemplateLibrary (single) ==========

    @Test
    public void testGetCodeTemplateLibraryFound() throws Exception {
        CodeTemplateLibrary library = servlet.getCodeTemplateLibrary(LIB_ID1, false);
        assertNotNull(library);
        assertEquals(LIB_ID1, library.getId());
        assertEquals("Library 1", library.getName());
    }

    @Test(expected = MirthApiException.class)
    public void testGetCodeTemplateLibraryNotFound() throws Exception {
        servlet.getCodeTemplateLibrary(NONEXISTENT_ID, false);
    }

    // ========== updateCodeTemplateLibraries ==========

    @Test
    public void testUpdateCodeTemplateLibraries() throws Exception {
        List<CodeTemplateLibrary> libraries = new ArrayList<>();
        CodeTemplateLibrary lib = new CodeTemplateLibrary();
        lib.setId(LIB_ID1);
        lib.setName("Updated Library");
        libraries.add(lib);

        boolean result = servlet.updateCodeTemplateLibraries(libraries, false);
        assertTrue(result);
    }

    @Test
    public void testUpdateCodeTemplateLibrariesWithOverride() throws Exception {
        boolean result = servlet.updateCodeTemplateLibraries(new ArrayList<>(), true);
        assertTrue(result);
    }

    @Test(expected = MirthApiException.class)
    public void testUpdateCodeTemplateLibrariesControllerException() throws Exception {
        when(mockCodeTemplateController.updateLibraries(any(), any(), anyBoolean()))
                .thenThrow(new ControllerException("update error"));
        try {
            servlet.updateCodeTemplateLibraries(new ArrayList<>(), false);
        } finally {
            when(mockCodeTemplateController.updateLibraries(any(), any(), anyBoolean())).thenReturn(true);
        }
    }

    // ========== getCodeTemplates ==========

    @Test
    public void testGetCodeTemplatesAll() throws Exception {
        List<CodeTemplate> templates = servlet.getCodeTemplates(null);
        assertNotNull(templates);
        assertEquals(2, templates.size());
    }

    @Test
    public void testGetCodeTemplatesAllWithEmptySet() throws Exception {
        List<CodeTemplate> templates = servlet.getCodeTemplates(new HashSet<>());
        assertNotNull(templates);
        assertEquals(2, templates.size());
    }

    @Test
    public void testGetCodeTemplatesFiltered() throws Exception {
        Set<String> ids = new HashSet<>();
        ids.add(TEMPLATE_ID1);
        List<CodeTemplate> templates = servlet.getCodeTemplates(ids);
        assertNotNull(templates);
        assertEquals(1, templates.size());
        assertEquals(TEMPLATE_ID1, templates.get(0).getId());
    }

    // ========== getCodeTemplatesPost ==========

    @Test
    public void testGetCodeTemplatesPostDelegates() throws Exception {
        List<CodeTemplate> templates = servlet.getCodeTemplatesPost(null);
        assertNotNull(templates);
        assertEquals(2, templates.size());
    }

    // ========== getCodeTemplate (single) ==========

    @Test
    public void testGetCodeTemplateFound() throws Exception {
        CodeTemplate template = servlet.getCodeTemplate(TEMPLATE_ID1);
        assertNotNull(template);
        assertEquals(TEMPLATE_ID1, template.getId());
        assertEquals("Template 1", template.getName());
    }

    @Test(expected = MirthApiException.class)
    public void testGetCodeTemplateNotFound() throws Exception {
        servlet.getCodeTemplate(NONEXISTENT_ID);
    }

    // ========== getCodeTemplateSummary ==========

    @Test
    public void testGetCodeTemplateSummary() throws Exception {
        Map<String, Integer> clientRevisions = new HashMap<>();
        clientRevisions.put(TEMPLATE_ID1, 1);
        List<CodeTemplateSummary> summaries = servlet.getCodeTemplateSummary(clientRevisions);
        assertNotNull(summaries);
        assertEquals(1, summaries.size());
    }

    // ========== updateCodeTemplate ==========

    @Test
    public void testUpdateCodeTemplate() throws Exception {
        CodeTemplate template = new CodeTemplate(TEMPLATE_ID1);
        template.setName("Updated Template");
        boolean result = servlet.updateCodeTemplate(TEMPLATE_ID1, template, false);
        assertTrue(result);
    }

    @Test
    public void testUpdateCodeTemplateWithOverride() throws Exception {
        CodeTemplate template = new CodeTemplate(TEMPLATE_ID1);
        boolean result = servlet.updateCodeTemplate(TEMPLATE_ID1, template, true);
        assertTrue(result);
    }

    @Test(expected = MirthApiException.class)
    public void testUpdateCodeTemplateControllerException() throws Exception {
        when(mockCodeTemplateController.updateCodeTemplate(any(), any(), anyBoolean()))
                .thenThrow(new ControllerException("update error"));
        try {
            servlet.updateCodeTemplate(TEMPLATE_ID1, new CodeTemplate(TEMPLATE_ID1), false);
        } finally {
            when(mockCodeTemplateController.updateCodeTemplate(any(), any(), anyBoolean())).thenReturn(true);
        }
    }

    // ========== removeCodeTemplate ==========

    @Test
    public void testRemoveCodeTemplate() throws Exception {
        servlet.removeCodeTemplate(TEMPLATE_ID1);
        verify(mockCodeTemplateController).removeCodeTemplate(anyString(), any());
    }

    @Test(expected = MirthApiException.class)
    public void testRemoveCodeTemplateControllerException() throws Exception {
        doThrow(new ControllerException("remove error")).when(mockCodeTemplateController).removeCodeTemplate(anyString(), any());
        try {
            servlet.removeCodeTemplate(TEMPLATE_ID1);
        } finally {
            doNothing().when(mockCodeTemplateController).removeCodeTemplate(anyString(), any());
        }
    }

    // ========== updateLibrariesAndTemplates ==========

    @Test
    public void testUpdateLibrariesAndTemplates() throws Exception {
        List<CodeTemplateLibrary> libraries = new ArrayList<>();
        Set<String> removedLibraryIds = new HashSet<>();
        List<CodeTemplate> updatedTemplates = new ArrayList<>();
        Set<String> removedTemplateIds = new HashSet<>();

        CodeTemplateLibrarySaveResult result = servlet.updateLibrariesAndTemplates(
                libraries, removedLibraryIds, updatedTemplates, removedTemplateIds, false);
        assertNotNull(result);
    }

    @Test
    public void testUpdateLibrariesAndTemplatesWithOverride() throws Exception {
        CodeTemplateLibrarySaveResult result = servlet.updateLibrariesAndTemplates(
                new ArrayList<>(), new HashSet<>(), new ArrayList<>(), new HashSet<>(), true);
        assertNotNull(result);
    }

    /**
     * Inner class to bypass login initialization in tests.
     */
    public class TestCodeTemplateServlet extends CodeTemplateServlet {
        public TestCodeTemplateServlet(HttpServletRequest request, SecurityContext sc) {
            super(request, sc);
        }

        @Override
        protected boolean isUserAuthorized() {
            return true;
        }
    }
}
