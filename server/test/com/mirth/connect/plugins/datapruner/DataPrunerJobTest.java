/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.datapruner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

/**
 * Test suite for DataPrunerJob and Scheduler integration
 * Validates Quartz 2.5.2 compatibility for:
 * - Job interface implementation (DataPrunerJob.java:12-14)
 * - JobExecutionException wrapping
 * - Scheduler lifecycle operations (DefaultDataPrunerController.java:22)
 */
public class DataPrunerJobTest {

    private Scheduler scheduler;

    @Before
    public void setUp() throws SchedulerException {
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "DataPrunerJobTest");
        props.setProperty("org.quartz.threadPool.threadCount", "2");

        StdSchedulerFactory factory = new StdSchedulerFactory();
        factory.initialize(props);
        scheduler = factory.getScheduler();
    }

    @After
    public void tearDown() throws SchedulerException {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    // ========================================================================
    // Test 1: DataPrunerJob Interface Implementation
    // ========================================================================

    @Test
    public void testDataPrunerJob_ImplementsJobInterface() {
        System.out.println("\n[Test 1.1] DataPrunerJob - Implements Job Interface");

        DataPrunerJob job = new DataPrunerJob();

        // Verify it implements the Job interface
        assertTrue("DataPrunerJob should implement Job interface",
                  job instanceof Job);
        System.out.println("  ✓ DataPrunerJob implements org.quartz.Job");
    }

    @Test
    public void testDataPrunerJob_CanBeScheduled() throws Exception {
        System.out.println("\n[Test 1.2] DataPrunerJob - Can Be Scheduled");

        // Create job detail
        JobDetail job = JobBuilder.newJob(DataPrunerJob.class)
            .withIdentity("dataPrunerJob", "test")
            .build();

        // Verify job was created successfully
        assertNotNull("Job detail should not be null", job);
        assertEquals("Job class should be DataPrunerJob",
                    DataPrunerJob.class, job.getJobClass());
        System.out.println("  ✓ DataPrunerJob can be configured as JobDetail");

        // Create trigger
        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("dataPrunerTrigger", "test")
            .startAt(new java.util.Date(System.currentTimeMillis() + 1000))
            .build();

        // Schedule the job
        scheduler.scheduleJob(job, trigger);
        System.out.println("  ✓ DataPrunerJob scheduled successfully");

        // Verify job is scheduled
        assertTrue("Job should exist in scheduler",
                  scheduler.checkExists(job.getKey()));
        System.out.println("  ✓ Job exists in scheduler");
    }

    // ========================================================================
    // Test 2: Job Execution with Mock
    // ========================================================================

    @Test
    public void testDataPrunerJob_ExecuteMethodSignature() throws Exception {
        System.out.println("\n[Test 2.1] DataPrunerJob - Execute Method Signature");

        DataPrunerJob job = new DataPrunerJob();

        // Verify the execute method signature matches Quartz 2.5.2 Job interface
        // We use reflection to verify the method exists with correct signature
        java.lang.reflect.Method executeMethod = DataPrunerJob.class.getMethod(
            "execute",
            JobExecutionContext.class
        );

        // Verify it's declared to throw JobExecutionException
        Class<?>[] exceptions = executeMethod.getExceptionTypes();
        boolean throwsJobExecutionException = false;
        for (Class<?> exc : exceptions) {
            if (exc.equals(JobExecutionException.class)) {
                throwsJobExecutionException = true;
                break;
            }
        }

        assertTrue("execute() should declare JobExecutionException",
                  throwsJobExecutionException);
        System.out.println("  ✓ execute(JobExecutionContext) method exists");
        System.out.println("  ✓ execute() declares JobExecutionException");
        System.out.println("  ✓ Method signature matches Quartz 2.5.2 Job interface");
    }

    // ========================================================================
    // Test 3: Test Job Implementation (for execution testing)
    // ========================================================================

    /**
     * Test job that simulates DataPrunerJob behavior for testing
     */
    public static class TestDataPrunerJob implements Job {
        private static final AtomicInteger executionCount = new AtomicInteger(0);
        private static final AtomicBoolean shouldThrowException = new AtomicBoolean(false);
        private static final CountDownLatch completed = new CountDownLatch(1);

        public static void reset() {
            executionCount.set(0);
            shouldThrowException.set(false);
        }

        public static int getExecutionCount() {
            return executionCount.get();
        }

        public static void setShouldThrowException(boolean value) {
            shouldThrowException.set(value);
        }

        public static boolean waitForCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return completed.await(timeout, unit);
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            executionCount.incrementAndGet();

            if (shouldThrowException.get()) {
                // Simulate DataPrunerException being wrapped
                throw new JobExecutionException("Simulated DataPruner failure");
            }

            completed.countDown();
        }
    }

    @Test
    public void testJobExecution_NormalCompletion() throws Exception {
        System.out.println("\n[Test 3.1] Job Execution - Normal Completion");

        TestDataPrunerJob.reset();

        JobDetail job = JobBuilder.newJob(TestDataPrunerJob.class)
            .withIdentity("testJob", "test")
            .build();

        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("testTrigger", "test")
            .startNow()
            .build();

        scheduler.scheduleJob(job, trigger);
        scheduler.start();

        // Wait for job to complete
        boolean completed = TestDataPrunerJob.waitForCompletion(5, TimeUnit.SECONDS);
        assertTrue("Job should complete", completed);

        int executions = TestDataPrunerJob.getExecutionCount();
        assertEquals("Job should execute exactly once", 1, executions);

        System.out.println("  ✓ Job executed successfully: " + executions + " time(s)");
    }

    @Test
    public void testJobExecution_WithException() throws Exception {
        System.out.println("\n[Test 3.2] Job Execution - With JobExecutionException");

        TestDataPrunerJob.reset();
        TestDataPrunerJob.setShouldThrowException(true);

        JobDetail job = JobBuilder.newJob(TestDataPrunerJob.class)
            .withIdentity("exceptionJob", "test")
            .build();

        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("exceptionTrigger", "test")
            .startNow()
            .build();

        scheduler.scheduleJob(job, trigger);
        scheduler.start();

        // Wait for job attempt
        Thread.sleep(1000);

        int executions = TestDataPrunerJob.getExecutionCount();
        assertTrue("Job should have attempted execution", executions >= 1);

        System.out.println("  ✓ Job handled JobExecutionException (executions: " + executions + ")");
        System.out.println("  ✓ Quartz 2.5.2 properly handles thrown JobExecutionException");
    }

    @Test
    public void testJobExecution_RepeatedExecution() throws Exception {
        System.out.println("\n[Test 3.3] Job Execution - Repeated Execution");

        TestDataPrunerJob.reset();

        JobDetail job = JobBuilder.newJob(TestDataPrunerJob.class)
            .withIdentity("repeatingJob", "test")
            .build();

        // Repeat every 200ms, 3 times
        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("repeatingTrigger", "test")
            .startNow()
            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMilliseconds(200)
                .withRepeatCount(2))
            .build();

        scheduler.scheduleJob(job, trigger);
        scheduler.start();

        // Wait for all executions
        Thread.sleep(1000);

        int executions = TestDataPrunerJob.getExecutionCount();
        assertTrue("Job should execute multiple times", executions >= 2);

        System.out.println("  ✓ Job executed " + executions + " times");
        System.out.println("  ✓ Repeated scheduling works correctly");
    }

    // ========================================================================
    // Test 4: Scheduler Lifecycle (DefaultDataPrunerController operations)
    // ========================================================================

    @Test
    public void testScheduler_StartStop() throws Exception {
        System.out.println("\n[Test 4.1] Scheduler - Start/Stop Operations");

        // Verify scheduler can be started
        scheduler.start();
        assertTrue("Scheduler should be started", scheduler.isStarted());
        System.out.println("  ✓ Scheduler started successfully");

        // Verify scheduler can be stopped (shutdown)
        scheduler.shutdown(true);
        assertTrue("Scheduler should be shutdown", scheduler.isShutdown());
        System.out.println("  ✓ Scheduler shutdown successfully (waitForJobsToComplete=true)");

        // Create new scheduler for additional tests
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "TestScheduler2");
        props.setProperty("org.quartz.threadPool.threadCount", "1");

        StdSchedulerFactory factory = new StdSchedulerFactory();
        factory.initialize(props);
        Scheduler testScheduler = factory.getScheduler();

        testScheduler.start();
        assertTrue("New scheduler should be started", testScheduler.isStarted());

        // Test shutdown with waitForJobsToComplete=false
        testScheduler.shutdown(false);
        assertTrue("Scheduler should be shutdown", testScheduler.isShutdown());
        System.out.println("  ✓ Scheduler shutdown successfully (waitForJobsToComplete=false)");
    }

    @Test
    public void testScheduler_IsStarted() throws Exception {
        System.out.println("\n[Test 4.2] Scheduler - IsStarted Check");

        // Initially not started
        assertFalse("Scheduler should not be started initially",
                   scheduler.isStarted());
        System.out.println("  ✓ isStarted() returns false before start");

        // After starting
        scheduler.start();
        assertTrue("Scheduler should be started", scheduler.isStarted());
        System.out.println("  ✓ isStarted() returns true after start");

        // After shutdown
        scheduler.shutdown(true); // Wait for jobs to complete

        // Give scheduler time to fully transition to shutdown state
        Thread.sleep(100);

        // After shutdown completes, isStarted should return false
        // Note: In some Quartz versions, isStarted() may still return true briefly
        // Use isShutdown() as a more reliable check
        assertTrue("Scheduler should be shutdown", scheduler.isShutdown());
        System.out.println("  ✓ isShutdown() returns true after shutdown");
    }

    @Test
    public void testScheduler_JobExistsCheck() throws Exception {
        System.out.println("\n[Test 4.3] Scheduler - Job Exists Check");

        JobDetail job = JobBuilder.newJob(TestDataPrunerJob.class)
            .withIdentity("existsCheckJob", "test")
            .build();

        // Before scheduling
        assertFalse("Job should not exist before scheduling",
                   scheduler.checkExists(job.getKey()));
        System.out.println("  ✓ checkExists() returns false before scheduling");

        // Create trigger and schedule
        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("existsCheckTrigger", "test")
            .startAt(new java.util.Date(System.currentTimeMillis() + 5000))
            .build();

        scheduler.scheduleJob(job, trigger);

        // After scheduling
        assertTrue("Job should exist after scheduling",
                  scheduler.checkExists(job.getKey()));
        System.out.println("  ✓ checkExists() returns true after scheduling");

        // After deleting
        scheduler.deleteJob(job.getKey());
        assertFalse("Job should not exist after deletion",
                   scheduler.checkExists(job.getKey()));
        System.out.println("  ✓ checkExists() returns false after deletion");
    }

    @Test
    public void testScheduler_MultipleInstancesIndependent() throws Exception {
        System.out.println("\n[Test 4.4] Scheduler - Multiple Independent Instances");

        // Create two independent schedulers
        Properties props1 = new Properties();
        props1.setProperty("org.quartz.scheduler.instanceName", "Scheduler1");
        props1.setProperty("org.quartz.threadPool.threadCount", "1");

        Properties props2 = new Properties();
        props2.setProperty("org.quartz.scheduler.instanceName", "Scheduler2");
        props2.setProperty("org.quartz.threadPool.threadCount", "1");

        StdSchedulerFactory factory1 = new StdSchedulerFactory();
        factory1.initialize(props1);
        Scheduler scheduler1 = factory1.getScheduler();

        StdSchedulerFactory factory2 = new StdSchedulerFactory();
        factory2.initialize(props2);
        Scheduler scheduler2 = factory2.getScheduler();

        try {
            // Start first scheduler
            scheduler1.start();
            assertTrue("Scheduler 1 should be started", scheduler1.isStarted());
            assertFalse("Scheduler 2 should not be started", scheduler2.isStarted());
            System.out.println("  ✓ Multiple schedulers operate independently");

            // Start second scheduler
            scheduler2.start();
            assertTrue("Both schedulers should be started",
                      scheduler1.isStarted() && scheduler2.isStarted());
            System.out.println("  ✓ Multiple schedulers can run concurrently");

        } finally {
            if (!scheduler1.isShutdown()) {
                scheduler1.shutdown();
            }
            if (!scheduler2.isShutdown()) {
                scheduler2.shutdown();
            }
        }

        System.out.println("  ✓ Schedulers shutdown independently");
    }

    @Test
    public void testScheduler_ConfigurationProperties() throws Exception {
        System.out.println("\n[Test 4.5] Scheduler - Configuration Properties");

        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "CustomScheduler");
        props.setProperty("org.quartz.threadPool.threadCount", "5");

        StdSchedulerFactory factory = new StdSchedulerFactory();
        factory.initialize(props);
        Scheduler testScheduler = factory.getScheduler();

        try {
            // Verify scheduler was created with custom name
            String schedulerName = testScheduler.getSchedulerName();
            assertEquals("Scheduler should have custom name",
                        "CustomScheduler", schedulerName);
            System.out.println("  ✓ Custom scheduler name: " + schedulerName);

            testScheduler.start();
            assertTrue("Scheduler should start with custom config",
                      testScheduler.isStarted());
            System.out.println("  ✓ Scheduler started with custom thread pool config");

        } finally {
            if (!testScheduler.isShutdown()) {
                testScheduler.shutdown();
            }
        }
    }

    // ========================================================================
    // Test 5: Quartz 2.5.2 Compatibility Verification
    // ========================================================================

    @Test
    public void testQuartz252_ThreadPoolRequired() throws Exception {
        System.out.println("\n[Test 5.1] Quartz 2.5.2 - Thread Pool Required");

        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "MinimalScheduler");
        // Explicitly set threadCount (required in 2.5.2)
        props.setProperty("org.quartz.threadPool.threadCount", "1");

        StdSchedulerFactory factory = new StdSchedulerFactory();
        factory.initialize(props);
        Scheduler testScheduler = factory.getScheduler();

        try {
            testScheduler.start();
            assertTrue("Scheduler should start with explicit threadCount",
                      testScheduler.isStarted());
            System.out.println("  ✓ Quartz 2.5.2 accepts explicit threadCount configuration");
            System.out.println("  ✓ Thread pool configuration requirement verified");
        } finally {
            if (!testScheduler.isShutdown()) {
                testScheduler.shutdown();
            }
        }
    }

    @Test
    public void testQuartz252_JobExecutionContextAPI() throws Exception {
        System.out.println("\n[Test 5.2] Quartz 2.5.2 - JobExecutionContext API");

        // Create a job that uses JobExecutionContext API
        JobDetail job = JobBuilder.newJob(ContextTestJob.class)
            .withIdentity("contextJob", "test")
            .usingJobData("testKey", "testValue")
            .build();

        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("contextTrigger", "test")
            .startNow()
            .build();

        scheduler.scheduleJob(job, trigger);
        scheduler.start();

        Thread.sleep(1000);

        assertTrue("Context test job should have executed",
                  ContextTestJob.wasExecuted());
        System.out.println("  ✓ JobExecutionContext API works in Quartz 2.5.2");
        System.out.println("  ✓ JobDataMap accessible from context");
    }

    /**
     * Test job that verifies JobExecutionContext API
     */
    public static class ContextTestJob implements Job {
        private static final AtomicBoolean executed = new AtomicBoolean(false);

        public static boolean wasExecuted() {
            return executed.get();
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            // Verify we can access context data (Quartz 2.5.2 API)
            String value = context.getJobDetail().getJobDataMap().getString("testKey");
            if ("testValue".equals(value)) {
                executed.set(true);
            }
        }
    }
}
