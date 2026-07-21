/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 codetemplates coverage — CodeTemplateLibrarySaveResult getter/setter/inner classes.
 */

package com.mirth.connect.model.codetemplates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class CodeTemplateLibrarySaveResultTest {

    // ------------------------------------------------------------------
    // Default constructor: defaults
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_overrideNeededFalse() {
        CodeTemplateLibrarySaveResult r = new CodeTemplateLibrarySaveResult();
        assertFalse(r.isOverrideNeeded());
    }

    @Test
    public void defaultConstructor_librariesSuccessDefault() {
        CodeTemplateLibrarySaveResult r = new CodeTemplateLibrarySaveResult();
        // default is false unless set
        assertFalse(r.isLibrariesSuccess());
    }

    @Test
    public void defaultConstructor_libraryResultsNotNull() {
        CodeTemplateLibrarySaveResult r = new CodeTemplateLibrarySaveResult();
        assertNotNull(r.getLibraryResults());
    }

    @Test
    public void defaultConstructor_codeTemplateResultsNotNull() {
        CodeTemplateLibrarySaveResult r = new CodeTemplateLibrarySaveResult();
        assertNotNull(r.getCodeTemplateResults());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setOverrideNeeded_true() {
        CodeTemplateLibrarySaveResult r = new CodeTemplateLibrarySaveResult();
        r.setOverrideNeeded(true);
        assertTrue(r.isOverrideNeeded());
    }

    @Test
    public void setLibrariesSuccess_true() {
        CodeTemplateLibrarySaveResult r = new CodeTemplateLibrarySaveResult();
        r.setLibrariesSuccess(true);
        assertTrue(r.isLibrariesSuccess());
    }

    @Test
    public void setLibrariesCause_roundTrip() {
        CodeTemplateLibrarySaveResult r = new CodeTemplateLibrarySaveResult();
        RuntimeException cause = new RuntimeException("test error");
        r.setLibrariesCause(cause);
        assertEquals(cause, r.getLibrariesCause());
    }

    @Test
    public void setLibraryResults_roundTrip() {
        CodeTemplateLibrarySaveResult r = new CodeTemplateLibrarySaveResult();
        Map<String, CodeTemplateLibrarySaveResult.LibraryUpdateResult> results =
            new HashMap<String, CodeTemplateLibrarySaveResult.LibraryUpdateResult>();
        CodeTemplateLibrarySaveResult.LibraryUpdateResult lur = new CodeTemplateLibrarySaveResult.LibraryUpdateResult();
        lur.setNewRevision(3);
        results.put("lib-001", lur);
        r.setLibraryResults(results);
        assertEquals(1, r.getLibraryResults().size());
        assertEquals(3, r.getLibraryResults().get("lib-001").getNewRevision());
    }

    @Test
    public void setCodeTemplateResults_roundTrip() {
        CodeTemplateLibrarySaveResult r = new CodeTemplateLibrarySaveResult();
        Map<String, CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult> results =
            new HashMap<String, CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult>();
        CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult ctur = new CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult();
        ctur.setSuccess(true);
        ctur.setNewRevision(5);
        results.put("tmpl-001", ctur);
        r.setCodeTemplateResults(results);
        assertTrue(r.getCodeTemplateResults().get("tmpl-001").isSuccess());
        assertEquals(5, r.getCodeTemplateResults().get("tmpl-001").getNewRevision());
    }

    // ------------------------------------------------------------------
    // LibraryUpdateResult inner class
    // ------------------------------------------------------------------

    @Test
    public void libraryUpdateResult_newRevision() {
        CodeTemplateLibrarySaveResult.LibraryUpdateResult lur = new CodeTemplateLibrarySaveResult.LibraryUpdateResult();
        lur.setNewRevision(10);
        assertEquals(10, lur.getNewRevision());
    }

    @Test
    public void libraryUpdateResult_lastModified() {
        CodeTemplateLibrarySaveResult.LibraryUpdateResult lur = new CodeTemplateLibrarySaveResult.LibraryUpdateResult();
        Calendar cal = Calendar.getInstance();
        lur.setNewLastModified(cal);
        assertEquals(cal, lur.getNewLastModified());
    }

    // ------------------------------------------------------------------
    // CodeTemplateUpdateResult inner class
    // ------------------------------------------------------------------

    @Test
    public void codeTemplateUpdateResult_success_true() {
        CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult ctur = new CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult();
        ctur.setSuccess(true);
        assertTrue(ctur.isSuccess());
    }

    @Test
    public void codeTemplateUpdateResult_success_false() {
        CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult ctur = new CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult();
        ctur.setSuccess(false);
        assertFalse(ctur.isSuccess());
    }

    @Test
    public void codeTemplateUpdateResult_newRevision() {
        CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult ctur = new CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult();
        ctur.setNewRevision(7);
        assertEquals(7, ctur.getNewRevision());
    }

    @Test
    public void codeTemplateUpdateResult_lastModified() {
        CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult ctur = new CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult();
        Calendar cal = Calendar.getInstance();
        ctur.setNewLastModified(cal);
        assertEquals(cal, ctur.getNewLastModified());
    }

    @Test
    public void codeTemplateUpdateResult_cause() {
        CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult ctur = new CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult();
        Exception cause = new Exception("template error");
        ctur.setCause(cause);
        assertEquals(cause, ctur.getCause());
    }
}
