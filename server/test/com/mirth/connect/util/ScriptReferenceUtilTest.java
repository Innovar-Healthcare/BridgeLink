/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import com.mirth.connect.model.reference.ScriptReference;
import com.mirth.connect.model.reference.ScriptReference.Type;
import com.mirth.connect.model.reference.ScriptReferenceCategory;

/**
 * Covers the catalog IRT-1520 serves from {@code GET /server/scriptReferences}. WebAdmin's future
 * consumer (IRT-1666) treats this as a stable contract, so these tests pin the entry count and
 * spot-check representative entries rather than asserting on all ~207 individually.
 */
public class ScriptReferenceUtilTest {

    @Test
    public void testGetReferencesCountIsStable() {
        // Ported 1:1 from the desktop client's ReferenceListFactory (addCodeTemplateReferences,
        // addMiscellaneousReferences, addE4XReferences). Update this number deliberately if entries
        // are intentionally added/removed; an unexpected change here means entries were silently
        // lost or duplicated.
        List<ScriptReference> references = ScriptReferenceUtil.getReferences();
        assertNotNull(references);
        assertEquals(207, references.size());
    }

    @Test
    public void testGetReferencesIsUnmodifiable() {
        List<ScriptReference> references = ScriptReferenceUtil.getReferences();
        try {
            references.add(ScriptReference.variable(null, null, "x", "x", "x"));
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testGetReferencesContainsOnlyPortedTypes() {
        // ClassReference (Type.CLASS) is Piece B (userutil Reflections/JavaParser scanning) and was
        // intentionally not ported to the server model - see IRT-1520.
        for (ScriptReference reference : ScriptReferenceUtil.getReferences()) {
            assertNotNull(reference.getType());
            assertTrue("Unexpected type " + reference.getType() + " for entry " + reference.getName(),
                    reference.getType() == Type.CODE || reference.getType() == Type.VARIABLE || reference.getType() == Type.FUNCTION);
        }
    }

    @Test
    public void testGetReferencesEveryEntryHasNameAndDescription() {
        for (ScriptReference reference : ScriptReferenceUtil.getReferences()) {
            assertFalse("blank name on a " + reference.getType() + " entry", StringUtils.isBlank(reference.getName()));
            assertFalse("blank description for entry: " + reference.getName(), StringUtils.isBlank(reference.getDescription()));
        }
    }

    @Test
    public void testGetReferencesContainsKnownCodeEntry() {
        ScriptReference logInfo = findByName(ScriptReferenceUtil.getReferences(), "Log an Info Statement");
        assertNotNull(logInfo);
        assertEquals(Type.CODE, logInfo.getType());
        assertEquals("Logging and Alerts", logInfo.getCategory());
        assertEquals("logger.info('message');", logInfo.getReplacementCode());
    }

    @Test
    public void testGetReferencesContainsKnownVariableEntry() {
        ScriptReference channelId = findByName(ScriptReferenceUtil.getReferences(), "Channel ID");
        assertNotNull(channelId);
        assertEquals(Type.VARIABLE, channelId.getType());
        assertEquals("Channel Functions", channelId.getCategory());
        assertEquals("channelId", channelId.getReplacementCode());
    }

    @Test
    public void testGetReferencesContainsKnownFunctionEntry() {
        ScriptReference getMapValue = findByName(ScriptReferenceUtil.getReferences(), "Get Map Value");
        assertNotNull(getMapValue);
        assertEquals(Type.FUNCTION, getMapValue.getType());
        assertNotNull(getMapValue.getFunctionDefinition());
        assertEquals("$", getMapValue.getFunctionDefinition().getName());
    }

    @Test
    public void testGetReferencesHasNoJtdsEntries() {
        // IRT-2458: 26.9 removed the jTDS driver, so the catalog must not offer it.
        for (ScriptReference reference : ScriptReferenceUtil.getReferences()) {
            assertFalse("jTDS entry: " + reference.getName(), StringUtils.containsIgnoreCase(reference.getName(), "jtds"));
            assertFalse("jTDS code in: " + reference.getName(), StringUtils.containsIgnoreCase(reference.getReplacementCode(), "jtds"));
        }
    }

    @Test
    public void testGetReferencesHasOneEntryPerSqlServerString() {
        List<ScriptReference> references = ScriptReferenceUtil.getReferences();

        ScriptReference driver = findByName(references, "Microsoft SQL Server Driver");
        assertNotNull(driver);
        assertEquals("\"com.microsoft.sqlserver.jdbc.SQLServerDriver\"", driver.getReplacementCode());

        ScriptReference template = findByName(references, "Microsoft SQL Server Connection Template");
        assertNotNull(template);
        assertEquals("\"jdbc:sqlserver://host:port;databaseName=dbname\"", template.getReplacementCode());

        int sqlServerEntries = 0;
        for (ScriptReference reference : references) {
            if (StringUtils.containsIgnoreCase(reference.getReplacementCode(), "sqlserver")) {
                sqlServerEntries++;
            }
        }
        assertEquals(2, sqlServerEntries);
    }

    @Test
    public void testGetReferencesDatabaseNamesAreUnique() {
        Set<String> names = new HashSet<String>();
        for (ScriptReference reference : ScriptReferenceUtil.getReferences()) {
            if (ScriptReferenceCategory.DATABASE.toString().equals(reference.getCategory())) {
                assertTrue("duplicate Database entry: " + reference.getName(), names.add(reference.getName()));
            }
        }
    }

    private ScriptReference findByName(List<ScriptReference> references, String name) {
        for (ScriptReference reference : references) {
            if (name.equals(reference.getName())) {
                return reference;
            }
        }
        return null;
    }
}
