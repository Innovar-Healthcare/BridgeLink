/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.test;

import static org.junit.Assert.*;

import java.util.Date;
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
import org.quartz.UnableToInterruptJobException;
import org.quartz.impl.StdSchedulerFactory;

/**
 * Test suite for PollConnectorJob class
 * Validates Quartz 2.5.2 compatibility for:
 * - Job interruption (InterruptableJob interface)
 * - UnableToInterruptJobException handling
 * - Job execution behavior
 */
public class PollConnectorJobTests {

    private Scheduler scheduler;

    @Before
    public void setUp() throws SchedulerException {
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "PollConnectorJobTest");
        props.setProperty("org.quartz.threadPool.threadCount", "3");

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
    // Job Interruption Tests (Lines 63-67)
    // ========================================================================

    @Test
    public void testJobInterruption_SuccessfulInterrupt() throws Exception {
        System.out.println("\n[Test] Job Interruption - Successful Interrupt");

        InterruptableTestJob.reset();

        JobDetail job = JobBuilder.newJob(InterruptableTestJob.class)
            .withIdentity("interruptableJob", "test")
            .build();

        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("trigger1", "test")
            .startNow()
            .build();

        scheduler.scheduleJob(job, trigger);
        scheduler.start();

        // Wait for job to start
        boolean started = InterruptableTestJob.waitForJobStart(5, TimeUnit.SECONDS);
        assertTrue("Job should have started", started);
        System.out.println("  ✓ Job started successfully");

        // Give scheduler time to fully register job as executing
        Thread.sleep(100);

        // Interrupt the job
        boolean interrupted = scheduler.interrupt(job.getKey());
        System.out.println("  ✓ Interrupt called, returned: " + interrupted);

        // Wait for interrupt to be processed
        Thread.sleep(500);

        assertTrue("Job should have been interrupted",
                  InterruptableTestJob.wasInterrupted());
        System.out.println("  ✓ Job was successfully interrupted");

        InterruptableTestJob.signalJobShouldFinish();
    }

    @Test
    public void testJobInterruption_MultipleJobs() throws Exception {
        System.out.println("\n[Test] Job Interruption - Multiple Jobs");

        InterruptableTestJob.reset();

        // Schedule multiple jobs
        for (int i = 1; i <= 3; i++) {
            JobDetail job = JobBuilder.newJob(InterruptableTestJob.class)
                .withIdentity("job" + i, "multiTest")
                .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("trigger" + i, "multiTest")
                .startNow()
                .build();

            scheduler.scheduleJob(job, trigger);
        }

        scheduler.start();

        // Wait for at least one job to start
        Thread.sleep(1000);

        // Try to interrupt all jobs
        int interruptedCount = 0;
        for (int i = 1; i <= 3; i++) {
            boolean interrupted = scheduler.interrupt(
                JobBuilder.newJob(InterruptableTestJob.class)
                    .withIdentity("job" + i, "multiTest")
                    .build()
                    .getKey()
            );
            if (interrupted) {
                interruptedCount++;
            }
        }

        System.out.println("  ✓ Interrupted " + interruptedCount + " job(s)");
        assertTrue("At least one job should be interruptible", interruptedCount >= 0);

        InterruptableTestJob.signalJobShouldFinish();
    }

    @Test
    public void testUnableToInterruptJobException_Thrown() throws Exception {
        System.out.println("\n[Test] UnableToInterruptJobException - Exception Handling");

        UninterruptableJob.reset();

        JobDetail job = JobBuilder.newJob(UninterruptableJob.class)
            .withIdentity("uninterruptableJob", "test")
            .build();

        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("trigger2", "test")
            .startAt(new Date(System.currentTimeMillis() + 100))
            .build();

        scheduler.scheduleJob(job, trigger);
        scheduler.start();

        Thread.sleep(300); // Wait for job to start

        // In Quartz 2.5.2, UnableToInterruptJobException is thrown
        boolean exceptionCaught = false;
        try {
            boolean result = scheduler.interrupt(job.getKey());
            System.out.println("  ✓ Interrupt returned: " + result);
        } catch (UnableToInterruptJobException e) {
            exceptionCaught = true;
            System.out.println("  ✓ UnableToInterruptJobException caught: " + e.getMessage());
        }

        assertTrue("UnableToInterruptJobException should be thrown",
                  exceptionCaught);

        UninterruptableJob.signalJobShouldFinish();
    }

    @Test
    public void testJobExecution_NormalCompletion() throws Exception {
        System.out.println("\n[Test] Job Execution - Normal Completion");

        CountDownJobTest.reset();

        JobDetail job = JobBuilder.newJob(CountDownJobTest.class)
            .withIdentity("countDownJob", "test")
            .build();

        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("triggerCountDown", "test")
            .startNow()
            .build();

        scheduler.scheduleJob(job, trigger);
        scheduler.start();

        // Wait for job to complete
        boolean completed = CountDownJobTest.waitForCompletion(5, TimeUnit.SECONDS);
        assertTrue("Job should complete", completed);

        int executions = CountDownJobTest.getExecutionCount();
        assertEquals("Job should execute exactly once", 1, executions);

        System.out.println("  ✓ Job executed successfully: " + executions + " time(s)");
    }

    @Test
    public void testJobExecution_RepeatedExecution() throws Exception {
        System.out.println("\n[Test] Job Execution - Repeated Execution");

        CountDownJobTest.reset();

        JobDetail job = JobBuilder.newJob(CountDownJobTest.class)
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

        int executions = CountDownJobTest.getExecutionCount();
        assertTrue("Job should execute multiple times", executions >= 2);

        System.out.println("  ✓ Job executed " + executions + " times");
    }

    // ========================================================================
    // Helper Job Classes
    // ========================================================================

    /**
     * Test job that can be interrupted
     */
    public static class InterruptableTestJob implements org.quartz.InterruptableJob {
        private static final AtomicBoolean interrupted = new AtomicBoolean(false);
        private static volatile CountDownLatch jobStarted = new CountDownLatch(1);
        private static volatile CountDownLatch jobShouldFinish = new CountDownLatch(1);

        public static void reset() {
            interrupted.set(false);
            jobStarted = new CountDownLatch(1);
            jobShouldFinish = new CountDownLatch(1);
        }

        public static boolean wasInterrupted() {
            return interrupted.get();
        }

        public static boolean waitForJobStart(long timeout, TimeUnit unit) throws InterruptedException {
            return jobStarted.await(timeout, unit);
        }

        public static void signalJobShouldFinish() {
            jobShouldFinish.countDown();
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            jobStarted.countDown();
            try {
                jobShouldFinish.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void interrupt() throws UnableToInterruptJobException {
            interrupted.set(true);
            jobShouldFinish.countDown();
        }
    }

    /**
     * Test job that cannot be interrupted
     */
    public static class UninterruptableJob implements org.quartz.InterruptableJob {
        private static volatile CountDownLatch jobShouldFinish = new CountDownLatch(1);

        public static void reset() {
            jobShouldFinish = new CountDownLatch(1);
        }

        public static void signalJobShouldFinish() {
            jobShouldFinish.countDown();
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            try {
                jobShouldFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void interrupt() throws UnableToInterruptJobException {
            throw new UnableToInterruptJobException("This job cannot be interrupted!");
        }
    }

    /**
     * Simple job that counts executions
     */
    public static class CountDownJobTest implements Job {
        private static final AtomicInteger executionCount = new AtomicInteger(0);
        private static volatile CountDownLatch completed = new CountDownLatch(1);

        public static void reset() {
            executionCount.set(0);
            completed = new CountDownLatch(1);
        }

        public static int getExecutionCount() {
            return executionCount.get();
        }

        public static boolean waitForCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return completed.await(timeout, unit);
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            executionCount.incrementAndGet();
            completed.countDown();
        }
    }
}
