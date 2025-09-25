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
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Before;
import org.junit.Test;

/**
 * Test class for GlobalChannelVariableStore.
 */
public class GlobalChannelVariableStoreTest {

    private GlobalChannelVariableStore store;

    @Before
    public void setUp() {
        store = new GlobalChannelVariableStore();
    }

    // Regular (non-sync) variable tests
    @Test
    public void testPutAndGet() {
        String key = "channelKey";
        String value = "channelValue";
        
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
        testMap.put("channelKey1", "channelValue1");
        testMap.put("channelKey2", "channelValue2");
        testMap.put("channelKey3", 789);
        
        store.putAll(testMap);
        
        for (Map.Entry<String, Object> entry : testMap.entrySet()) {
            assertTrue("Should contain key: " + entry.getKey(), store.containsKey(entry.getKey()));
            assertEquals("Should have correct value for key: " + entry.getKey(), 
                        entry.getValue(), store.get(entry.getKey()));
        }
    }

    @Test
    public void testGetVariables() {
        store.put("channelKey1", "channelValue1");
        store.put("channelKey2", "channelValue2");
        
        Map<String, Object> variables = store.getVariables();
        
        assertNotNull("Variables map should not be null", variables);
        assertEquals("Variables map should have correct size", 2, variables.size());
        assertEquals("Should contain channelKey1", "channelValue1", variables.get("channelKey1"));
        assertEquals("Should contain channelKey2", "channelValue2", variables.get("channelKey2"));
        
        // Test that returned map is unmodifiable
        try {
            variables.put("channelKey3", "channelValue3");
            assertFalse("Returned map should be unmodifiable", true);
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    public void testClear() {
        store.put("channelKey1", "channelValue1");
        store.put("channelKey2", "channelValue2");
        
        assertTrue("Should have variables", store.getVariables().size() > 0);
        
        store.clear();
        
        assertEquals("Should have no variables after clear", 0, store.getVariables().size());
        assertFalse("Should not contain channelKey1", store.containsKey("channelKey1"));
        assertFalse("Should not contain channelKey2", store.containsKey("channelKey2"));
    }

    @Test
    public void testToString() {
        store.put("channelKey1", "channelValue1");
        store.put("channelKey2", 456);
        
        String toString = store.toString();
        
        assertNotNull("toString should not be null", toString);
        assertTrue("toString should contain channelKey1", toString.contains("channelKey1"));
        assertTrue("toString should contain channelValue1", toString.contains("channelValue1"));
    }

    // Synchronized variable tests
    @Test
    public void testPutSyncAndGetSync() {
        String key = "syncChannelKey";
        String value = "syncChannelValue";
        
        assertFalse("Should not contain sync key initially", store.containsKeySync(key));
        
        store.putSync(key, value);
        
        assertTrue("Should contain sync key after put", store.containsKeySync(key));
        assertEquals("Should return correct sync value", value, store.getSync(key));
    }

    @Test
    public void testRemoveSync() {
        String key = "removeSyncChannelKey";
        String value = "removeSyncChannelValue";
        
        store.putSync(key, value);
        assertTrue("Should contain sync key after put", store.containsKeySync(key));
        
        store.removeSync(key);
        
        assertFalse("Should not contain sync key after remove", store.containsKeySync(key));
    }

    @Test
    public void testPutAllSync() {
        Map<String, Object> testMap = new HashMap<>();
        testMap.put("syncChannelKey1", "syncChannelValue1");
        testMap.put("syncChannelKey2", "syncChannelValue2");
        testMap.put("syncChannelKey3", 999);
        
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
        store.putSync("syncChannelKey1", "syncChannelValue1");
        store.putSync("syncChannelKey2", "syncChannelValue2");
        
        assertTrue("Should have sync keys", store.containsKeySync("syncChannelKey1"));
        assertTrue("Should have sync keys", store.containsKeySync("syncChannelKey2"));
        
        store.clearSync();
        
        assertFalse("Should not contain syncChannelKey1 after clear", 
                   store.containsKeySync("syncChannelKey1"));
        assertFalse("Should not contain syncChannelKey2 after clear", 
                   store.containsKeySync("syncChannelKey2"));
    }

    @Test
    public void testSyncVariableUpdate() {
        String key = "updateChannelKey";
        String initialValue = "initialChannel";
        String updatedValue = "updatedChannel";
        
        // Put initial value
        store.putSync(key, initialValue);
        assertEquals("Should have initial value", initialValue, store.getSync(key));
        
        // Update existing key
        store.putSync(key, updatedValue);
        assertEquals("Should have updated value", updatedValue, store.getSync(key));
    }

    @Test
    public void testConcurrentRegularVariableAccess() throws InterruptedException {
        int threadCount = 8;
        int operationsPerThread = 75;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "channelThread" + threadId + "_key" + j;
                        String value = "channelThread" + threadId + "_value" + j;
                        
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
                String key = "channelThread" + i + "_key" + j;
                String expectedValue = "channelThread" + i + "_value" + j;
                assertEquals("Value should persist after concurrent access", 
                           expectedValue, store.get(key));
            }
        }
    }

    @Test
    public void testConcurrentSyncVariableAccess() throws InterruptedException {
        int threadCount = 5;
        int operationsPerThread = 40;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "syncChannelThread" + threadId + "_" + j;
                        String value = "syncChannelValue" + threadId + "_" + j;
                        
                        store.putSync(key, value);
                        Object retrievedValue = store.getSync(key);
                        assertEquals("Concurrent sync put/get should work", value, retrievedValue);
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
                String key = "syncChannelThread" + i + "_" + j;
                String expectedValue = "syncChannelValue" + i + "_" + j;
                assertEquals("Sync value should persist after concurrent access", 
                           expectedValue, store.getSync(key));
            }
        }
    }

    @Test
    public void testSeparateRegularAndSyncStores() {
        String key = "sameChannelKey";
        String regularValue = "regularChannelValue";
        String syncValue = "syncChannelValue";
        
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

    @Test
    public void testMultipleInstancesIndependence() {
        GlobalChannelVariableStore store1 = new GlobalChannelVariableStore();
        GlobalChannelVariableStore store2 = new GlobalChannelVariableStore();
        
        String key = "independenceKey";
        String value1 = "value1";
        String value2 = "value2";
        
        // Put different values in different instances
        store1.put(key, value1);
        store2.put(key, value2);
        
        // They should be independent
        assertEquals("Store1 should have its value", value1, store1.get(key));
        assertEquals("Store2 should have its value", value2, store2.get(key));
        
        // Same for sync variables
        store1.putSync(key, value1);
        store2.putSync(key, value2);
        
        assertEquals("Store1 sync should have its value", value1, store1.getSync(key));
        assertEquals("Store2 sync should have its value", value2, store2.getSync(key));
    }

    @Test
    public void testVariousDataTypes() {
        // Test with different data types
        store.put("stringKey", "stringValue");
        store.put("intKey", 123);
        store.put("longKey", 123L);
        store.put("boolKey", true);
        store.put("doubleKey", 123.45);
        
        assertEquals("String value should work", "stringValue", store.get("stringKey"));
        assertEquals("Integer value should work", Integer.valueOf(123), store.get("intKey"));
        assertEquals("Long value should work", Long.valueOf(123L), store.get("longKey"));
        assertEquals("Boolean value should work", Boolean.TRUE, store.get("boolKey"));
        assertEquals("Double value should work", Double.valueOf(123.45), store.get("doubleKey"));
        
        // Same for sync variables
        store.putSync("syncStringKey", "syncStringValue");
        store.putSync("syncIntKey", 456);
        
        assertEquals("Sync string value should work", "syncStringValue", store.getSync("syncStringKey"));
        assertEquals("Sync integer value should work", Integer.valueOf(456), store.getSync("syncIntKey"));
    }
}
