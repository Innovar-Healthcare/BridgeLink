/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 codetemplates coverage — CodeTemplateSummary getter/setter.
 */

package com.mirth.connect.model.codetemplates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CodeTemplateSummaryTest {

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void idConstructor_setsId() {
        CodeTemplateSummary s = new CodeTemplateSummary("tmpl-abc");
        assertEquals("tmpl-abc", s.getCodeTemplateId());
    }

    @Test
    public void idConstructor_deletedFalse() {
        CodeTemplateSummary s = new CodeTemplateSummary("tmpl-001");
        assertFalse(s.isDeleted());
    }

    @Test
    public void setDeleted_true() {
        CodeTemplateSummary s = new CodeTemplateSummary("tmpl-001");
        s.setDeleted(true);
        assertTrue(s.isDeleted());
    }

    @Test
    public void setDeleted_false_afterTrue() {
        CodeTemplateSummary s = new CodeTemplateSummary("tmpl-001");
        s.setDeleted(true);
        s.setDeleted(false);
        assertFalse(s.isDeleted());
    }

    @Test
    public void idCtTemplateConstructor_setsTemplate() {
        CodeTemplate ct = new CodeTemplate("tmpl-id");
        ct.setName("My Template");
        CodeTemplateSummary s = new CodeTemplateSummary("tmpl-id", ct);
        assertEquals("My Template", s.getCodeTemplate().getName());
        assertEquals("tmpl-id", s.getCodeTemplateId());
    }

    @Test
    public void idConstructor_codeTemplateNull() {
        CodeTemplateSummary s = new CodeTemplateSummary("tmpl-xyz");
        assertNull(s.getCodeTemplate());
    }

    @Test
    public void setCodeTemplateId_roundTrip() {
        CodeTemplateSummary s = new CodeTemplateSummary("original-id");
        s.setCodeTemplateId("new-id");
        assertEquals("new-id", s.getCodeTemplateId());
    }

    @Test
    public void setCodeTemplate_roundTrip() {
        CodeTemplateSummary s = new CodeTemplateSummary("tmpl-r");
        CodeTemplate ct = new CodeTemplate("ct-id");
        ct.setName("Updated Template");
        s.setCodeTemplate(ct);
        assertEquals("Updated Template", s.getCodeTemplate().getName());
    }

    // ------------------------------------------------------------------
    // Multiple setters: last write wins
    // ------------------------------------------------------------------

    @Test
    public void setCodeTemplateId_multipleWrites_lastWins() {
        CodeTemplateSummary s = new CodeTemplateSummary("first");
        s.setCodeTemplateId("second");
        assertEquals("second", s.getCodeTemplateId());
    }
}
