/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.http.HttpSession;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mirth.connect.model.User;

/**
 * Test class for UserSessionCache.
 */
public class UserSessionCacheTest {

    @Mock
    private HttpSession mockSession1;
    
    @Mock
    private HttpSession mockSession2;
    
    @Mock
    private User mockUser1;
    
    @Mock
    private User mockUser2;

    private UserSessionCache cache;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        cache = UserSessionCache.getInstance();
        
        // Setup mock behavior
        when(mockSession1.getId()).thenReturn("session1");
        when(mockSession2.getId()).thenReturn("session2");
        when(mockUser1.getId()).thenReturn(1);
        when(mockUser2.getId()).thenReturn(2);
    }

    @Test
    public void testSingletonBehavior() {
        UserSessionCache instance1 = UserSessionCache.getInstance();
        UserSessionCache instance2 = UserSessionCache.getInstance();
        
        assertSame("getInstance should return the same instance", instance1, instance2);
    }

    @Test
    public void testRegisterSessionForUser() {
        // Register a session
        cache.registerSessionForUser(mockSession1, mockUser1);
        
        // Verify the registration was logged (we can't directly test the internal map)
        // The method should complete without exception
    }

    @Test
    public void testInvalidateAllSessionsForUser_SingleSession() {
        // Register a session
        cache.registerSessionForUser(mockSession1, mockUser1);
        
        // Invalidate sessions for user 1
        cache.invalidateAllSessionsForUser(1);
        
        // Verify the session's authorized attribute was removed
        verify(mockSession1).removeAttribute("authorized");
    }

    @Test
    public void testInvalidateAllSessionsForUser_MultipleSessions() {
        // Register multiple sessions for the same user
        HttpSession mockSession3 = mock(HttpSession.class);
        when(mockSession3.getId()).thenReturn("session3");
        
        cache.registerSessionForUser(mockSession1, mockUser1);
        cache.registerSessionForUser(mockSession3, mockUser1);
        cache.registerSessionForUser(mockSession2, mockUser2); // Different user
        
        // Invalidate sessions for user 1 only
        cache.invalidateAllSessionsForUser(1);
        
        // Verify both sessions for user 1 were invalidated
        verify(mockSession1).removeAttribute("authorized");
        verify(mockSession3).removeAttribute("authorized");
        
        // Verify user 2's session was not affected
        verify(mockSession2, never()).removeAttribute("authorized");
    }

    @Test
    public void testInvalidateAllSessionsForUser_NonExistentUser() {
        // Register a session for user 1
        cache.registerSessionForUser(mockSession1, mockUser1);
        
        // Try to invalidate sessions for non-existent user
        cache.invalidateAllSessionsForUser(999);
        
        // Verify user 1's session was not affected
        verify(mockSession1, never()).removeAttribute("authorized");
    }

    @Test
    public void testInvalidateAllSessionsForUser_AlreadyInvalidatedSession() {
        // Register a session
        cache.registerSessionForUser(mockSession1, mockUser1);
        
        // Mock the session throwing IllegalStateException (already invalidated)
        doThrow(new IllegalStateException("Session already invalidated"))
            .when(mockSession1).removeAttribute("authorized");
        
        // This should not throw an exception
        cache.invalidateAllSessionsForUser(1);
        
        // Verify the method was still called
        verify(mockSession1).removeAttribute("authorized");
    }

    @Test
    public void testConcurrentSessionRegistration() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Create mock sessions and users for concurrent testing
        HttpSession[] sessions = new HttpSession[threadCount];
        User[] users = new User[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            sessions[i] = mock(HttpSession.class);
            users[i] = mock(User.class);
            when(sessions[i].getId()).thenReturn("session" + i);
            when(users[i].getId()).thenReturn(i);
        }

        // Submit concurrent registration tasks
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    cache.registerSessionForUser(sessions[index], users[index]);
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

        // The test passes if no exceptions were thrown during concurrent access
    }

    @Test
    public void testConcurrentInvalidation() throws InterruptedException {
        // Register sessions for multiple users
        User[] users = new User[5];
        for (int i = 0; i < 5; i++) {
            users[i] = mock(User.class);
            when(users[i].getId()).thenReturn(i + 1);
            
            HttpSession session = mock(HttpSession.class);
            when(session.getId()).thenReturn("session" + i);
            doNothing().when(session).removeAttribute("authorized");
            
            cache.registerSessionForUser(session, users[i]);
        }

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Submit concurrent invalidation tasks
        for (int i = 0; i < threadCount; i++) {
            final int userId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    cache.invalidateAllSessionsForUser(userId);
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

        // The test passes if no exceptions were thrown during concurrent access
    }

    @Test
    public void testRegisterMultipleSessionsForSameUser() {
        HttpSession mockSession3 = mock(HttpSession.class);
        when(mockSession3.getId()).thenReturn("session3");
        
        // Register multiple sessions for the same user
        cache.registerSessionForUser(mockSession1, mockUser1);
        cache.registerSessionForUser(mockSession3, mockUser1);
        
        // Invalidate all sessions for the user
        cache.invalidateAllSessionsForUser(1);
        
        // Verify both sessions were invalidated
        verify(mockSession1).removeAttribute("authorized");
        verify(mockSession3).removeAttribute("authorized");
    }

    @Test
    public void testInvalidateSessionsMultipleTimes() {
        // Register a session
        cache.registerSessionForUser(mockSession1, mockUser1);
        
        // Invalidate multiple times
        cache.invalidateAllSessionsForUser(1);
        cache.invalidateAllSessionsForUser(1); // Should not cause issues
        
        // The first call should have removed the session, so the second call should not interact with it
        verify(mockSession1, times(1)).removeAttribute("authorized");
    }
}
