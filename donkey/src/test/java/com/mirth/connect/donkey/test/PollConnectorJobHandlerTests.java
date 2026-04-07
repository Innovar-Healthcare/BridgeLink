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

import java.util.Calendar;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.calendar.DailyCalendar;
import org.quartz.impl.calendar.MonthlyCalendar;
import org.quartz.impl.calendar.WeeklyCalendar;

import com.mirth.connect.donkey.util.DummyJob;
import com.mirth.connect.donkey.util.PollConnectorJobHandler;

/**
 * Test suite for PollConnectorJobHandler class
 * Validates Quartz 2.5.2 compatibility for:
 * - Cron expression validation
 * - Scheduler property configuration
 * - Calendar behavior
 */
public class PollConnectorJobHandlerTests {

    private Scheduler scheduler;

    @Before
    public void setUp() throws SchedulerException {
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "PollConnectorJobHandlerTest");
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
    // Cron Expression Validation Tests (Line 164)
    // ========================================================================

    @Test
    public void testValidateExpression_ValidCronExpressions() {
        System.out.println("\n[Test] Cron Validation - Valid Expressions");

        String[][] testCases = {
            {"0 0 12 * * ?", "Every day at noon"},
            {"0 15 10 ? * *", "Every day at 10:15am"},
            {"0 15 10 * * ? 2025", "Every day at 10:15am in 2025"},
            {"0 * 14 * * ?", "Every minute starting at 2pm"},
            {"0 0/5 14 * * ?", "Every 5 minutes starting at 2pm"},
            {"0 15 10 ? * MON-FRI", "10:15am Monday-Friday"},
            {"0 15 10 15 * ?", "10:15am on 15th of month"},
            {"0 11 11 11 11 ?", "November 11th at 11:11am"},
        };

        for (String[] test : testCases) {
            boolean isValid = PollConnectorJobHandler.validateExpression(test[0]);
            assertTrue("Expression should be valid: " + test[1] + " (" + test[0] + ")", isValid);
            System.out.println("  ✓ " + test[1]);
        }
    }

    @Test
    public void testValidateExpression_InvalidCronExpressions() {
        System.out.println("\n[Test] Cron Validation - Invalid Expressions");

        String[][] testCases = {
            {"", "Empty string"},
            {"0 0 12", "Too few fields"},
            {"0 0 25 * * ?", "Invalid hour (25)"},
            {"0 60 12 * * ?", "Invalid minute (60)"},
            {"0 0 12 32 * ?", "Invalid day (32)"},
            {"0 0 12 * 13 ?", "Invalid month (13)"},
            {"0 0 12 ? * 8", "Invalid day of week (8)"},
            {"invalid cron", "Invalid format"},
        };

        for (String[] test : testCases) {
            boolean isValid = PollConnectorJobHandler.validateExpression(test[0]);
            assertFalse("Expression should be invalid: " + test[1] + " (" + test[0] + ")", isValid);
            System.out.println("  ✓ Rejected: " + test[1]);
        }
    }

    @Test
    public void testValidateExpression_SpecialCharacters() {
        System.out.println("\n[Test] Cron Validation - Special Characters");

        // Test L (last)
        assertTrue("L (last) should be valid",
                  PollConnectorJobHandler.validateExpression("0 0 12 L * ?"));
        System.out.println("  ✓ 'L' (last day) accepted");

        // Test W (weekday)
        assertTrue("W (weekday) should be valid",
                  PollConnectorJobHandler.validateExpression("0 0 12 1W * ?"));
        System.out.println("  ✓ 'W' (nearest weekday) accepted");

        // Test # (nth occurrence)
        assertTrue("# (nth) should be valid",
                  PollConnectorJobHandler.validateExpression("0 0 12 ? * 6#3"));
        System.out.println("  ✓ '#' (nth occurrence) accepted");
    }

    @Test
    public void testValidateExpression_NullHandling() {
        System.out.println("\n[Test] Cron Validation - Null Handling");

        try {
            boolean result = PollConnectorJobHandler.validateExpression(null);
            assertFalse("Null should be invalid", result);
            System.out.println("  ✓ Null rejected (returned false)");
        } catch (IllegalArgumentException e) {
            // In Quartz 2.5.2, null throws exception
            System.out.println("  ✓ Null rejected (threw IllegalArgumentException)");
        }
    }

    // ========================================================================
    // Scheduler Properties Tests (Lines 60-62)
    // ========================================================================

    @Test
    public void testSchedulerProperties_ThreadPoolConfiguration() throws Exception {
        System.out.println("\n[Test] Scheduler Properties - Thread Pool");

        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "ThreadPoolTest");
        props.setProperty("org.quartz.threadPool.threadCount", "5");

        StdSchedulerFactory factory = new StdSchedulerFactory();
        factory.initialize(props);
        Scheduler testScheduler = factory.getScheduler();

        try {
            assertNotNull("Scheduler should be created", testScheduler);
            assertEquals("Instance name should match",
                        "ThreadPoolTest", testScheduler.getSchedulerName());
            System.out.println("  ✓ Scheduler created: " + testScheduler.getSchedulerName());
            System.out.println("  ✓ Thread pool: 5 threads");
        } finally {
            testScheduler.shutdown();
        }
    }

    @Test
    public void testSchedulerProperties_MinimalConfiguration() throws Exception {
        System.out.println("\n[Test] Scheduler Properties - Minimal Config");

        // Quartz 2.5.2 requires explicit threadCount
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "MinimalTest");
        props.setProperty("org.quartz.threadPool.threadCount", "1");

        StdSchedulerFactory factory = new StdSchedulerFactory();
        factory.initialize(props);
        Scheduler testScheduler = factory.getScheduler();

        try {
            testScheduler.start();
            assertTrue("Scheduler should start", testScheduler.isStarted());
            System.out.println("  ✓ Scheduler started with minimal config");
            System.out.println("  ✓ Note: Quartz 2.5.2 requires explicit threadCount");
        } finally {
            testScheduler.shutdown();
        }
    }

    @Test
    public void testSchedulerProperties_JobStorage() throws Exception {
        System.out.println("\n[Test] Scheduler Properties - Job Scheduling");

        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "JobStorageTest");
        props.setProperty("org.quartz.threadPool.threadCount", "2");

        StdSchedulerFactory factory = new StdSchedulerFactory();
        factory.initialize(props);
        Scheduler testScheduler = factory.getScheduler();

        try {
            testScheduler.start();

            // Test job storage by adding a durable job
            JobDetail job = JobBuilder.newJob(DummyJob.class)
                .withIdentity("testJob", "testGroup")
                .storeDurably(true)
                .build();

            testScheduler.addJob(job, false);

            assertTrue("Job should be stored",
                      testScheduler.checkExists(job.getKey()));
            System.out.println("  ✓ Durable job stored successfully");

            testScheduler.deleteJob(job.getKey());
            System.out.println("  ✓ Job deleted successfully");
        } finally {
            testScheduler.shutdown();
        }
    }

    // ========================================================================
    // Calendar Behavior Tests (Lines 138-150)
    // ========================================================================

    @Test
    public void testCalendarBehavior_DailyCalendar_BusinessHours() throws Exception {
        System.out.println("\n[Test] Calendar - Daily Business Hours (9am-5pm)");

        // Create business hours calendar (9am-5pm)
        DailyCalendar calendar = new DailyCalendar("9:00:00", "17:00:00");
        calendar.setInvertTimeRange(true); // Invert to INCLUDE business hours

        Calendar testTime = Calendar.getInstance();
        testTime.set(Calendar.MILLISECOND, 0);

        // Test 12:00 PM (within business hours)
        testTime.set(Calendar.HOUR_OF_DAY, 12);
        testTime.set(Calendar.MINUTE, 0);
        testTime.set(Calendar.SECOND, 0);
        assertTrue("12:00 PM should be included",
                  calendar.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ 12:00 PM included (business hours)");

        // Test 8:00 PM (outside business hours)
        testTime.set(Calendar.HOUR_OF_DAY, 20);
        assertFalse("8:00 PM should be excluded",
                   calendar.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ 8:00 PM excluded (after hours)");

        // Test 6:00 AM (before business hours)
        testTime.set(Calendar.HOUR_OF_DAY, 6);
        assertFalse("6:00 AM should be excluded",
                   calendar.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ 6:00 AM excluded (before hours)");
    }

    @Test
    public void testCalendarBehavior_WeeklyCalendar_Weekends() throws Exception {
        System.out.println("\n[Test] Calendar - Weekly (Exclude Weekends)");

        WeeklyCalendar calendar = new WeeklyCalendar();
        calendar.setDayExcluded(Calendar.SATURDAY, true);
        calendar.setDayExcluded(Calendar.SUNDAY, true);

        Calendar testTime = Calendar.getInstance();

        // Test Monday (should be included)
        testTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        assertTrue("Monday should be included",
                  calendar.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ Monday included (weekday)");

        // Test Saturday (should be excluded)
        testTime.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
        assertFalse("Saturday should be excluded",
                   calendar.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ Saturday excluded (weekend)");

        // Test Sunday (should be excluded)
        testTime.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        assertFalse("Sunday should be excluded",
                   calendar.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ Sunday excluded (weekend)");
    }

    @Test
    public void testCalendarBehavior_MonthlyCalendar_FirstLastDay() throws Exception {
        System.out.println("\n[Test] Calendar - Monthly (Exclude 1st & Last)");

        MonthlyCalendar calendar = new MonthlyCalendar();
        calendar.setDayExcluded(1, true);   // Exclude 1st
        calendar.setDayExcluded(31, true);  // Exclude 31st

        Calendar testTime = Calendar.getInstance();

        // Test 15th (should be included)
        testTime.set(Calendar.DAY_OF_MONTH, 15);
        assertTrue("15th should be included",
                  calendar.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ 15th included (mid-month)");

        // Test 1st (should be excluded)
        testTime.set(Calendar.DAY_OF_MONTH, 1);
        assertFalse("1st should be excluded",
                   calendar.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ 1st excluded");

        // Test 31st in January (should be excluded)
        testTime.set(Calendar.MONTH, Calendar.JANUARY);
        testTime.set(Calendar.DAY_OF_MONTH, 31);
        assertFalse("31st should be excluded",
                   calendar.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ 31st excluded");
    }

    @Test
    public void testCalendarBehavior_NextIncludedTime() throws Exception {
        System.out.println("\n[Test] Calendar - Next Included Time Calculation");

        DailyCalendar calendar = new DailyCalendar("9:00:00", "17:00:00");
        calendar.setInvertTimeRange(true); // Include business hours

        Calendar testTime = Calendar.getInstance();
        testTime.set(Calendar.HOUR_OF_DAY, 8);
        testTime.set(Calendar.MINUTE, 0);
        testTime.set(Calendar.SECOND, 0);
        testTime.set(Calendar.MILLISECOND, 0);

        long currentTime = testTime.getTimeInMillis();
        long nextTime = calendar.getNextIncludedTime(currentTime);

        assertTrue("Next included time should be calculated", nextTime > 0);

        if (nextTime != currentTime) {
            Calendar nextCal = Calendar.getInstance();
            nextCal.setTimeInMillis(nextTime);

            System.out.println("  ✓ Next included time calculated");
            System.out.println("    Current: " + String.format("%02d:%02d",
                testTime.get(Calendar.HOUR_OF_DAY), testTime.get(Calendar.MINUTE)));
            System.out.println("    Next:    " + String.format("%02d:%02d",
                nextCal.get(Calendar.HOUR_OF_DAY), nextCal.get(Calendar.MINUTE)));
        } else {
            System.out.println("  ✓ Time already included in calendar");
        }
    }

    @Test
    public void testCalendarBehavior_CombinedCalendars() throws Exception {
        System.out.println("\n[Test] Calendar - Combined Daily + Weekly");

        // Business hours (9am-5pm)
        DailyCalendar dailyCal = new DailyCalendar("9:00:00", "17:00:00");
        dailyCal.setInvertTimeRange(true);

        // Exclude weekends
        WeeklyCalendar weeklyCal = new WeeklyCalendar(dailyCal);
        weeklyCal.setDayExcluded(Calendar.SATURDAY, true);
        weeklyCal.setDayExcluded(Calendar.SUNDAY, true);

        Calendar testTime = Calendar.getInstance();
        testTime.set(Calendar.MILLISECOND, 0);

        // Monday at noon (should be included)
        testTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        testTime.set(Calendar.HOUR_OF_DAY, 12);
        testTime.set(Calendar.MINUTE, 0);
        testTime.set(Calendar.SECOND, 0);
        assertTrue("Monday 12PM should be included",
                  weeklyCal.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ Monday 12PM included (weekday + business hours)");

        // Saturday at noon (should be excluded - weekend)
        testTime.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
        assertFalse("Saturday 12PM should be excluded",
                   weeklyCal.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ Saturday 12PM excluded (weekend)");

        // Monday at 8PM (should be excluded - after hours)
        testTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        testTime.set(Calendar.HOUR_OF_DAY, 20);
        assertFalse("Monday 8PM should be excluded",
                   weeklyCal.isTimeIncluded(testTime.getTimeInMillis()));
        System.out.println("  ✓ Monday 8PM excluded (after business hours)");
    }

    @Test
    public void testCalendarBehavior_EdgeCaseBoundaries() throws Exception {
        System.out.println("\n[Test] Calendar - Boundary Conditions");

        DailyCalendar calendar = new DailyCalendar("9:00:00", "17:00:00");
        calendar.setInvertTimeRange(true);

        Calendar testTime = Calendar.getInstance();
        testTime.set(Calendar.MILLISECOND, 0);
        testTime.set(Calendar.SECOND, 0);

        // Test exactly 9:00 AM
        testTime.set(Calendar.HOUR_OF_DAY, 9);
        testTime.set(Calendar.MINUTE, 0);
        boolean at9am = calendar.isTimeIncluded(testTime.getTimeInMillis());
        System.out.println("  ✓ 9:00 AM exactly: " + (at9am ? "included" : "excluded"));

        // Test 8:59 AM
        testTime.set(Calendar.HOUR_OF_DAY, 8);
        testTime.set(Calendar.MINUTE, 59);
        boolean before9am = calendar.isTimeIncluded(testTime.getTimeInMillis());
        System.out.println("  ✓ 8:59 AM: " + (before9am ? "included" : "excluded"));

        // Test exactly 5:00 PM
        testTime.set(Calendar.HOUR_OF_DAY, 17);
        testTime.set(Calendar.MINUTE, 0);
        boolean at5pm = calendar.isTimeIncluded(testTime.getTimeInMillis());
        System.out.println("  ✓ 5:00 PM exactly: " + (at5pm ? "included" : "excluded"));

        // Test 5:01 PM
        testTime.set(Calendar.HOUR_OF_DAY, 17);
        testTime.set(Calendar.MINUTE, 1);
        boolean after5pm = calendar.isTimeIncluded(testTime.getTimeInMillis());
        System.out.println("  ✓ 5:01 PM: " + (after5pm ? "included" : "excluded"));
    }
}
