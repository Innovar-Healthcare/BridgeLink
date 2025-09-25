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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;

/**
 * Test class for ServerUUIDGenerator utility.
 */
public class ServerUUIDGeneratorTest {

    @Test
    public void testGetUUID_NotNull() {
        String uuid = ServerUUIDGenerator.getUUID();
        assertNotNull("Generated UUID should not be null", uuid);
    }

    @Test
    public void testGetUUID_ValidFormat() {
        String uuid = ServerUUIDGenerator.getUUID();
        
        // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (36 characters including hyphens)
        assertEquals("UUID should be 36 characters long", 36, uuid.length());
        assertTrue("UUID should contain hyphens in correct positions", 
                   uuid.charAt(8) == '-' && 
                   uuid.charAt(13) == '-' && 
                   uuid.charAt(18) == '-' && 
                   uuid.charAt(23) == '-');
        
        // Validate it's a proper UUID by parsing it
        UUID.fromString(uuid); // This will throw if invalid
    }

    @Test
    public void testGetUUID_Uniqueness() {
        Set<String> generatedUuids = new HashSet<>();
        int iterations = 1000;
        
        for (int i = 0; i < iterations; i++) {
            String uuid = ServerUUIDGenerator.getUUID();
            assertFalse("Generated UUID should be unique: " + uuid, 
                       generatedUuids.contains(uuid));
            generatedUuids.add(uuid);
        }
        
        assertEquals("All generated UUIDs should be unique", iterations, generatedUuids.size());
    }

    @Test
    public void testGetUUID_ThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int uuidsPerThread = 100;
        Set<String> allUuids = new HashSet<>();
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Set<String> threadUuids = new HashSet<>();
                    for (int j = 0; j < uuidsPerThread; j++) {
                        String uuid = ServerUUIDGenerator.getUUID();
                        threadUuids.add(uuid);
                    }
                    
                    synchronized (allUuids) {
                        allUuids.addAll(threadUuids);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals("All UUIDs generated concurrently should be unique", 
                    threadCount * uuidsPerThread, allUuids.size());
    }

    @Test
    public void testGetUUID_Version4() {
        String uuid = ServerUUIDGenerator.getUUID();
        UUID parsedUuid = UUID.fromString(uuid);
        
        // UUID version 4 should have version bits set correctly
        assertEquals("Generated UUID should be version 4", 4, parsedUuid.version());
    }

    @Test
    public void testGetUUID_Performance() {
        long startTime = System.currentTimeMillis();
        int iterations = 10000;
        
        for (int i = 0; i < iterations; i++) {
            ServerUUIDGenerator.getUUID();
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Performance test - should generate 10k UUIDs in reasonable time (< 1 second)
        assertTrue("UUID generation should be reasonably fast: " + duration + "ms", 
                  duration < 1000);
    }
}
