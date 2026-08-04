/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TemplateValueReplacerTest {

    private TemplateValueReplacer templateReplacer;

    @Before
    public void setUp() throws Exception {
        templateReplacer = new TestTemplateValueReplacer();
    }

    @After
    public void tearDown() throws Exception {
        templateReplacer = null;
    }

    @Test
    public void testReplaceValues() {
        // replaceValues(String template, String channelId, String channelname) calls loadContextFromMap(VelocityContext context, Map<String, ?> map) below
        assertEquals("value1", templateReplacer.replaceValues("value1", "channelId", "channelName"));
        assertEquals("valueOfVelocity1", templateReplacer.replaceValues("$velocity1", "channelId", "channelName"));
        assertEquals("$velocityUnknown", templateReplacer.replaceValues("$velocityUnknown", "channelId", "channelName"));
        assertEquals("unusedVelocity1", templateReplacer.replaceValues("unusedVelocity1", "channelId", "channelName"));
        assertEquals("valueOfVelocityHyphen", templateReplacer.replaceValues("$velocity-hyphen", "channelId", "channelName"));
    }
    
    // ===== replaceValues(template, channelId, Map<String, Object>) - IRT-1519 =====
    // Uses a real TemplateValueReplacer (not the loadContextFromMap-overriding fixture above),
    // since GlobalChannelVariableStoreFactory is a self-contained in-memory singleton that is safe
    // to touch directly in a unit test; a synthetic/unrecognized channel ID does not throw (it
    // lazily creates an empty store), matching what ConfigurationServlet.replaceTemplate relies on.

    @Test
    public void testReplaceValuesWithMapPlainStringPassthrough() {
        TemplateValueReplacer replacer = new TemplateValueReplacer();
        assertEquals("plain string, no templating", replacer.replaceValues("plain string, no templating", "test-channel-id", Collections.<String, Object>emptyMap()));
    }

    @Test
    public void testReplaceValuesWithMapResolvesCallerSuppliedVariable() {
        TemplateValueReplacer replacer = new TemplateValueReplacer();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("extra1", "extraValue1");
        assertEquals("extraValue1", replacer.replaceValues("$extra1", "test-channel-id", map));
    }

    @Test
    public void testReplaceValuesWithMapResolvesChannelId() {
        TemplateValueReplacer replacer = new TemplateValueReplacer();
        assertEquals("test-channel-id", replacer.replaceValues("$channelId", "test-channel-id", Collections.<String, Object>emptyMap()));
    }

    @Test
    public void testReplaceValuesWithMapUnrecognizedChannelIdDoesNotThrow() {
        TemplateValueReplacer replacer = new TemplateValueReplacer();
        // A brand new, never-before-seen channel ID; GlobalChannelVariableStoreFactory must lazily
        // create an empty store rather than throwing.
        String result = replacer.replaceValues("$unknownVar", java.util.UUID.randomUUID().toString(), Collections.<String, Object>emptyMap());
        assertEquals("$unknownVar", result);
    }

    @Test
    public void testReplaceValuesWithMapBuiltInUuidAndSystime() {
        TemplateValueReplacer replacer = new TemplateValueReplacer();

        String uuidResult = replacer.replaceValues("$UUID", "test-channel-id", Collections.<String, Object>emptyMap());
        assertNotNull(uuidResult);
        assertFalse(uuidResult.isEmpty());
        assertFalse("$UUID".equals(uuidResult));

        String systimeResult = replacer.replaceValues("$SYSTIME", "test-channel-id", Collections.<String, Object>emptyMap());
        assertNotNull(systimeResult);
        assertTrue(systimeResult.matches("\\d+"));
    }

    private class TestTemplateValueReplacer extends TemplateValueReplacer {
        @Override
        protected void loadContextFromMap(VelocityContext context, Map<String, ?> map) {
            Map<String, String> velocityMap = new HashMap<String, String>();
            velocityMap.put("velocity1", "valueOfVelocity1");
            velocityMap.put("unusedVelocity1", "valueOfUnusedVelocity1");
            velocityMap.put("velocity-hyphen", "valueOfVelocityHyphen");
            super.loadContextFromMap(context, velocityMap);
        }
    }
}

