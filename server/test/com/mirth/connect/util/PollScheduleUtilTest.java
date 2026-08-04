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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Calendar;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import com.mirth.connect.donkey.model.channel.PollConnectorProperties;
import com.mirth.connect.donkey.model.channel.PollConnectorPropertiesAdvanced;
import com.mirth.connect.donkey.model.channel.PollingType;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

/**
 * Covers the server-side cron validation and next-poll-fire-time helpers backing
 * {@code POST /server/_validateCron} and {@code POST /connectors/poll/_nextFireTime} (IRT-1518).
 */
public class PollScheduleUtilTest {

    // ===== validateCronStructured =====

    @Test
    public void testValidateCronStructuredValid() {
        CronValidationResult result = PollScheduleUtil.validateCronStructured("0 0 12 * * ?");
        assertTrue(result.isValid());
        assertNull(result.getMessage());
    }

    @Test
    public void testValidateCronStructuredInvalid() {
        CronValidationResult result = PollScheduleUtil.validateCronStructured("not a cron expression");
        assertFalse(result.isValid());
        assertNotNull(result.getMessage());
    }

    @Test
    public void testValidateCronStructuredBlank() {
        // Unlike validateScriptStructured, blank has no special-case here: an empty string is not a
        // valid Quartz cron expression.
        assertFalse(PollScheduleUtil.validateCronStructured(null).isValid());
        assertFalse(PollScheduleUtil.validateCronStructured("").isValid());
    }

    @Test
    public void testValidateCronStructuredDayOfMonthAndDayOfWeekMutualExclusionGap() {
        // Quartz requires exactly one of day-of-month/day-of-week to be "?"; both "*" is invalid.
        CronValidationResult result = PollScheduleUtil.validateCronStructured("0 0 12 * * *");
        assertFalse(result.isValid());
    }

    // ===== parsePollConnectorPropertiesXml =====

    @Test
    public void testParsePollConnectorPropertiesXmlRoundTrip() throws Exception {
        PollConnectorProperties original = new PollConnectorProperties();
        original.setPollingType(PollingType.INTERVAL);
        original.setPollOnStart(true);
        original.setPollingFrequency(12345);

        // parsePollConnectorPropertiesXml renames whatever root tag it is given to the FQCN before
        // deserializing, so it does not matter what tag ObjectXMLSerializer chose here - this
        // exercises exactly the rename-then-deserialize logic the method implements.
        String xml = ObjectXMLSerializer.getInstance().serialize(original);
        PollConnectorProperties parsed = PollScheduleUtil.parsePollConnectorPropertiesXml(xml);

        assertEquals(original.getPollingType(), parsed.getPollingType());
        assertEquals(original.isPollOnStart(), parsed.isPollOnStart());
        assertEquals(original.getPollingFrequency(), parsed.getPollingFrequency());
    }

    @Test(expected = Exception.class)
    public void testParsePollConnectorPropertiesXmlInvalidXmlThrows() throws Exception {
        PollScheduleUtil.parsePollConnectorPropertiesXml("not valid xml at all");
    }

    // ===== validateForNextFireTime (IRT-1518 DoS fix) =====

    @Test
    public void testValidateForNextFireTimeRejectsAllDaysExcluded() {
        PollConnectorProperties properties = new PollConnectorProperties();
        properties.setPollingType(PollingType.INTERVAL);
        properties.setPollConnectorPropertiesAdvanced(allDaysExcludedAdvanced());

        try {
            PollScheduleUtil.validateForNextFireTime(properties);
            fail("Expected IllegalArgumentException for an all-days-excluded weekly restriction");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testValidateForNextFireTimeCronIgnoresRestrictions() {
        // CRON ignores the advanced restriction entirely, so even an all-days-excluded restriction
        // must not be rejected (and must not hang - the bug this validation guards against).
        PollConnectorProperties properties = new PollConnectorProperties();
        properties.setPollingType(PollingType.CRON);
        properties.setPollConnectorPropertiesAdvanced(allDaysExcludedAdvanced());

        PollScheduleUtil.validateForNextFireTime(properties); // must not throw
    }

    @Test
    public void testValidateForNextFireTimeNonWeeklyDoesNotValidate() {
        PollConnectorProperties properties = new PollConnectorProperties();
        properties.setPollingType(PollingType.INTERVAL);
        PollConnectorPropertiesAdvanced advanced = allDaysExcludedAdvanced();
        advanced.setWeekly(false);
        properties.setPollConnectorPropertiesAdvanced(advanced);

        PollScheduleUtil.validateForNextFireTime(properties); // must not throw
    }

    @Test
    public void testValidateForNextFireTimeAtLeastOneActiveDayPasses() {
        PollConnectorProperties properties = new PollConnectorProperties(); // default advanced: no days excluded
        properties.setPollingType(PollingType.INTERVAL);
        PollScheduleUtil.validateForNextFireTime(properties); // must not throw
    }

    private PollConnectorPropertiesAdvanced allDaysExcludedAdvanced() {
        PollConnectorPropertiesAdvanced advanced = new PollConnectorPropertiesAdvanced();
        advanced.setWeekly(true);
        boolean[] allExcluded = new boolean[8];
        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            allExcluded[day] = true;
        }
        advanced.setActiveDays(allExcluded);
        return advanced;
    }

    // ===== getNextFireTime =====

    @Test
    public void testGetNextFireTimeForNormalIntervalSchedule() throws Exception {
        PollConnectorProperties properties = new PollConnectorProperties();
        properties.setPollingType(PollingType.INTERVAL);
        properties.setPollingFrequency(5000);

        String nextFireTime = PollScheduleUtil.getNextFireTime(properties);
        assertFalse(StringUtils.isBlank(nextFireTime));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetNextFireTimeRejectsPathologicalRestrictionInsteadOfHanging() throws Exception {
        // The actual regression this guards: PollConnectorJobHandler.getNextFireTime() used to spin
        // forever in Quartz's DailyCalendar.getNextIncludedTime() for this input (IRT-1518). It must
        // now be rejected immediately via validateForNextFireTime rather than hanging.
        PollConnectorProperties properties = new PollConnectorProperties();
        properties.setPollingType(PollingType.INTERVAL);
        properties.setPollConnectorPropertiesAdvanced(allDaysExcludedAdvanced());

        PollScheduleUtil.getNextFireTime(properties);
    }
}
