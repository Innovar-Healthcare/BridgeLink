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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test class for GlobalVariableStore.
 */
public class GlobalVariableStoreTest {

    private GlobalVariableStore store;

    @Before
    public void setUp() {
        store = GlobalVariableStore.getInstance();
        store.clear();
        store.clearSync();
    }

    @After
    public void tearDown() {
        store.clear();
        store.clearSync();
    }

    @Test
    public void testSingletonBehavior() {
        GlobalVariableStore instance1 = GlobalVariableStore.getInstance();
        GlobalVariableStore instance2 = GlobalVariableStore.getInstance();
        
        assertSame("getInstance should return the same instance", instance1, instance2);
    }

    // Regular (non-sync) variable tests
    @Test
    public void testPutAndGet() {
        String key = "testKey";
        String value = "testValue";
        
        assertFalse("Should not contain key initially", store.containsKey(key));
        assertNull("Should return null for non-existent key", store.get(key));
        
        store.put(key, value);
        
        assertTrue("Should contain key after put", store.containsKey(key));
        assertEquals("Should return correct value", value, store.get(key));
    }

    @Test
    public void testRemove() {
        String key = "removeKey";
        String value = "removeValue";
        
        store.put(key, value);
        assertTrue("Should contain key after put", store.containsKey(key));
        
        store.remove(key);
        
        assertFalse("Should not contain key after remove", store.containsKey(key));
        assertNull("Should return null after remove", store.get(key));
    }

    @Test
    public void testPutAll() {
        Map<String, Object> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        testMap.put("key3", 123);
        
        store.putAll(testMap);
        
        for (Map.Entry<String, Object> entry : testMap.entrySet()) {
            assertTrue("Should contain key: " + entry.getKey(), store.containsKey(entry.getKey()));
            assertEquals("Should have correct value for key: " + entry.getKey(), 
                        entry.getValue(), store.get(entry.getKey()));
        }
    }

    @Test
    public void testGetVariables() {
        store.put("key1", "value1");
        store.put("key2", "value2");
        
        Map<String, Object> variables = store.getVariables();
        
        assertNotNull("Variables map should not be null", variables);
        assertEquals("Variables map should have correct size", 2, variables.size());
        assertEquals("Should contain key1", "value1", variables.get("key1"));
        assertEquals("Should contain key2", "value2", variables.get("key2"));
        
        // Test that returned map is unmodifiable
        try {
            variables.put("key3", "value3");
            assertFalse("Returned map should be unmodifiable", true);
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    public void testClear() {
        store.put("key1", "value1");
        store.put("key2", "value2");
        
        assertTrue("Should have variables", store.getVariables().size() > 0);
        
        store.clear();
        
        assertEquals("Should have no variables after clear", 0, store.getVariables().size());
        assertFalse("Should not contain key1", store.containsKey("key1"));
        assertFalse("Should not contain key2", store.containsKey("key2"));
    }

    @Test
    public void testToString() {
        store.put("key1", "value1");
        store.put("key2", 123);
        
        String toString = store.toString();
        
        assertNotNull("toString should not be null", toString);
        assertTrue("toString should contain key1", toString.contains("key1"));
        assertTrue("toString should contain value1", toString.contains("value1"));
    }

    // Synchronized variable tests
    @Test
    public void testPutSyncAndGetSync() {
        String key = "syncKey";
        String value = "syncValue";
        
        assertFalse("Should not contain sync key initially", store.containsKeySync(key));
        
        store.putSync(key, value);
        
        assertTrue("Should contain sync key after put", store.containsKeySync(key));
        assertEquals("Should return correct sync value", value, store.getSync(key));
    }

    @Test
    public void testRemoveSync() {
        String key = "removeSyncKey";
        String value = "removeSyncValue";
        
        store.putSync(key, value);
        assertTrue("Should contain sync key after put", store.containsKeySync(key));
        
        store.removeSync(key);
        
        assertFalse("Should not contain sync key after remove", store.containsKeySync(key));
    }

    @Test
    public void testPutAllSync() {
        Map<String, Object> testMap = new HashMap<>();
        testMap.put("syncKey1", "syncValue1");
        testMap.put("syncKey2", "syncValue2");
        testMap.put("syncKey3", 456);
        
        store.putAllSync(testMap);
        
        for (Map.Entry<String, Object> entry : testMap.entrySet()) {
            assertTrue("Should contain sync key: " + entry.getKey(), 
                      store.containsKeySync(entry.getKey()));
            assertEquals("Should have correct sync value for key: " + entry.getKey(), 
                        entry.getValue(), store.getSync(entry.getKey()));
        }
    }

    @Test
    public void testClearSync() {
        store.putSync("syncKey1", "syncValue1");
        store.putSync("syncKey2", "syncValue2");
        
        assertTrue("Should have sync keys", store.containsKeySync("syncKey1"));
        assertTrue("Should have sync keys", store.containsKeySync("syncKey2"));
        
        store.clearSync();
        
        assertFalse("Should not contain syncKey1 after clear", store.containsKeySync("syncKey1"));
        assertFalse("Should not contain syncKey2 after clear", store.containsKeySync("syncKey2"));
    }

    @Test
    public void testSyncVariableUpdate() {
        String key = "updateKey";
        String initialValue = "initial";
        String updatedValue = "updated";
        
        // Put initial value
        store.putSync(key, initialValue);
        assertEquals("Should have initial value", initialValue, store.getSync(key));
        
        // Update existing key
        store.putSync(key, updatedValue);
        assertEquals("Should have updated value", updatedValue, store.getSync(key));
    }

    @Test
    public void testConcurrentRegularVariableAccess() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "thread" + threadId + "_key" + j;
                        String value = "thread" + threadId + "_value" + j;
                        
                        store.put(key, value);
                        Object retrievedValue = store.get(key);
                        assertEquals("Concurrent put/get should work", value, retrievedValue);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Verify all values are still there
        for (int i = 0; i < threadCount; i++) {
            for (int j = 0; j < operationsPerThread; j++) {
                String key = "thread" + i + "_key" + j;
                String expectedValue = "thread" + i + "_value" + j;
                assertEquals("Value should persist after concurrent access", 
                           expectedValue, store.get(key));
            }
        }
    }

    @Test
    public void testConcurrentSyncVariableAccess() throws InterruptedException {
        int threadCount = 5;
        int operationsPerThread = 50;
        String sharedKey = "sharedSyncKey";
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Initialize the sync variable
        store.putSync(sharedKey, 0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Each thread updates its own key
                        String key = "syncThread" + threadId + "_" + j;
                        store.putSync(key, threadId * 1000 + j);
                        
                        Object value = store.getSync(key);
                        assertNotNull("Sync value should not be null", value);
                        assertEquals("Sync value should match", threadId * 1000 + j, value);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Verify all sync values are still there
        for (int i = 0; i < threadCount; i++) {
            for (int j = 0; j < operationsPerThread; j++) {
                String key = "syncThread" + i + "_" + j;
                Integer expectedValue = i * 1000 + j;
                assertEquals("Sync value should persist after concurrent access", 
                           expectedValue, store.getSync(key));
            }
        }
    }

    @Test
    public void testSeparateRegularAndSyncStores() {
        String key = "sameKey";
        String regularValue = "regularValue";
        String syncValue = "syncValue";
        
        // Put in both stores with same key
        store.put(key, regularValue);
        store.putSync(key, syncValue);
        
        // They should be independent
        assertEquals("Regular store should have its value", regularValue, store.get(key));
        assertEquals("Sync store should have its value", syncValue, store.getSync(key));
        
        assertTrue("Regular store should contain key", store.containsKey(key));
        assertTrue("Sync store should contain key", store.containsKeySync(key));
        
        // Removing from one should not affect the other
        store.remove(key);
        assertFalse("Regular store should not contain key", store.containsKey(key));
        assertTrue("Sync store should still contain key", store.containsKeySync(key));
        assertEquals("Sync store should still have its value", syncValue, store.getSync(key));
    }
}
