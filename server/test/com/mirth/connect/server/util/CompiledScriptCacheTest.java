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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Script;

/**
 * Test class for CompiledScriptCache.
 */
public class CompiledScriptCacheTest {

    private CompiledScriptCache cache;

    @Before
    public void setUp() {
        cache = CompiledScriptCache.getInstance();
    }

    @After
    public void tearDown() {
        // Clear the cache after each test to avoid interference
        cache.removeCompiledScript("test1");
        cache.removeCompiledScript("test2");
        cache.removeCompiledScript("concurrent-test");
    }

    @Test
    public void testSingletonBehavior() {
        CompiledScriptCache instance1 = CompiledScriptCache.getInstance();
        CompiledScriptCache instance2 = CompiledScriptCache.getInstance();
        
        assertSame("getInstance should return the same instance", instance1, instance2);
    }

    @Test
    public void testPutAndGetCompiledScript() {
        String scriptId = "test1";
        Script mockScript = mock(Script.class);
        String sourceScript = "var x = 'test';";

        // Initially should be null
        assertNull("Script should not exist initially", cache.getCompiledScript(scriptId));
        assertNull("Source script should not exist initially", cache.getSourceScript(scriptId));

        // Put script
        cache.putCompiledScript(scriptId, mockScript, sourceScript);

        // Verify retrieval
        Script retrievedScript = cache.getCompiledScript(scriptId);
        String retrievedSource = cache.getSourceScript(scriptId);

        assertSame("Retrieved compiled script should be the same object", mockScript, retrievedScript);
        assertEquals("Retrieved source script should match", sourceScript, retrievedSource);
    }

    @Test
    public void testRemoveCompiledScript() {
        String scriptId = "test2";
        Script mockScript = mock(Script.class);
        String sourceScript = "var y = 'test';";

        // Put script
        cache.putCompiledScript(scriptId, mockScript, sourceScript);
        assertNotNull("Script should exist after putting", cache.getCompiledScript(scriptId));
        assertNotNull("Source script should exist after putting", cache.getSourceScript(scriptId));

        // Remove script
        cache.removeCompiledScript(scriptId);

        // Verify removal
        assertNull("Script should be null after removal", cache.getCompiledScript(scriptId));
        assertNull("Source script should be null after removal", cache.getSourceScript(scriptId));
    }

    @Test
    public void testGetCompiledScript_NonExistent() {
        Script result = cache.getCompiledScript("non-existent");
        assertNull("Non-existent script should return null", result);
    }

    @Test
    public void testGetSourceScript_NonExistent() {
        String result = cache.getSourceScript("non-existent");
        assertNull("Non-existent source script should return null", result);
    }

    @Test
    public void testOverwriteScript() {
        String scriptId = "overwrite-test";
        Script mockScript1 = mock(Script.class);
        Script mockScript2 = mock(Script.class);
        String sourceScript1 = "var a = 1;";
        String sourceScript2 = "var b = 2;";

        // Put first script
        cache.putCompiledScript(scriptId, mockScript1, sourceScript1);
        assertSame("Should retrieve first script", mockScript1, cache.getCompiledScript(scriptId));
        assertEquals("Should retrieve first source", sourceScript1, cache.getSourceScript(scriptId));

        // Overwrite with second script
        cache.putCompiledScript(scriptId, mockScript2, sourceScript2);
        assertSame("Should retrieve second script after overwrite", mockScript2, cache.getCompiledScript(scriptId));
        assertEquals("Should retrieve second source after overwrite", sourceScript2, cache.getSourceScript(scriptId));
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        String scriptId = "concurrent-test";
        Script[] scripts = new Script[threadCount];
        String[] sources = new String[threadCount];

        // Initialize mock scripts and sources
        for (int i = 0; i < threadCount; i++) {
            scripts[i] = mock(Script.class);
            sources[i] = "var x" + i + " = " + i + ";";
        }

        // Submit concurrent tasks
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    // Each thread puts its own script
                    cache.putCompiledScript(scriptId + "-" + index, scripts[index], sources[index]);
                    
                    // Verify the script can be retrieved
                    Script retrieved = cache.getCompiledScript(scriptId + "-" + index);
                    String sourceRetrieved = cache.getSourceScript(scriptId + "-" + index);
                    
                    assertSame("Concurrent access should work correctly", scripts[index], retrieved);
                    assertEquals("Concurrent source access should work correctly", sources[index], sourceRetrieved);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        endLatch.await();
        executor.shutdown();

        // Clean up concurrent test scripts
        for (int i = 0; i < threadCount; i++) {
            cache.removeCompiledScript(scriptId + "-" + i);
        }
    }

    @Test
    public void testNullValues() {
        String scriptId = "null-test";
        
        // Test putting null script
        cache.putCompiledScript(scriptId, null, "source");
        assertNull("Null compiled script should be stored as null", cache.getCompiledScript(scriptId));
        assertEquals("Source script should still be stored", "source", cache.getSourceScript(scriptId));
        
        cache.removeCompiledScript(scriptId);
        
        // Test putting null source
        Script mockScript = mock(Script.class);
        cache.putCompiledScript(scriptId, mockScript, null);
        assertSame("Compiled script should be stored", mockScript, cache.getCompiledScript(scriptId));
        assertNull("Null source script should be stored as null", cache.getSourceScript(scriptId));
        
        cache.removeCompiledScript(scriptId);
    }
}
