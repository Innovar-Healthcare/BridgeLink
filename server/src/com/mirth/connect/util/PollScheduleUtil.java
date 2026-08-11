/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) NextGen Healthcare. All rights reserved.
 * https://www.nextgen.com/products-and-services/integration-engine
 *
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.util;

import java.text.ParseException;
import java.util.Calendar;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.quartz.CronExpression;
import org.quartz.SchedulerException;

import com.mirth.connect.donkey.model.channel.CronProperty;
import com.mirth.connect.donkey.model.channel.PollConnectorProperties;
import com.mirth.connect.donkey.model.channel.PollConnectorPropertiesAdvanced;
import com.mirth.connect.donkey.model.channel.PollingType;
import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.donkey.util.PollConnectorJobHandler;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

/**
 * Server-side helpers backing the cron validation and next-poll-fire-time preview REST endpoints,
 * both of which delegate to the real Quartz machinery already used by {@link PollConnectorJobHandler}
 * instead of re-implementing cron parsing/scheduling logic.
 */
public class PollScheduleUtil {

    private PollScheduleUtil() {}

    public static CronValidationResult validateCronStructured(String expression) {
        try {
            CronExpression.validateExpression(StringUtils.defaultString(expression));
            return CronValidationResult.valid();
        } catch (ParseException e) {
            return CronValidationResult.invalid(e.getMessage());
        }
    }

    /**
     * Deserializes a {@code <pollConnectorProperties>} XML fragment (the same form embedded inside
     * a connector in channel XML) into a {@link PollConnectorProperties}. The standalone-serialized
     * form of this class has no XStream alias, so its root tag is normally the fully-qualified class
     * name; the embedded field-name tag is renamed to that before deserializing.
     */
    public static PollConnectorProperties parsePollConnectorPropertiesXml(String xml) throws Exception {
        DonkeyElement element = new DonkeyElement(xml);
        element.setNodeName(PollConnectorProperties.class.getName());
        return ObjectXMLSerializer.getInstance().deserialize(element.toXml(), PollConnectorProperties.class);
    }

    /**
     * Rejects input that {@link #getNextFireTime} cannot safely hand to the Quartz machinery:
     * <ul>
     * <li>For CRON polling, an expression that is structurally invalid to Quartz — e.g. one
     * specifying BOTH a day-of-week AND a day-of-month. {@code configureJob} builds the trigger via
     * {@code CronScheduleBuilder.cronSchedule(...)} with no guard of its own, so such an expression
     * otherwise escapes as an unchecked {@link RuntimeException} and surfaces to the caller as a 500
     * instead of a 400 (IRT-1759). Validating here with the same {@link CronExpression} check that
     * {@code _validateCron} uses turns it into an {@link IllegalArgumentException} the servlet maps
     * to a graceful bad request.
     * <li>For a non-CRON weekly restriction, one that excludes every day of the week. Both the Java
     * client's and WebAdmin's Advanced Polling dialogs already refuse to save this ("At least one
     * day must be selected."), but that guard lives only in those two UI widgets, not in the model
     * or this endpoint's input, and Quartz's DailyCalendar.getNextIncludedTime() loops indefinitely
     * for it (IRT-1518).
     * </ul>
     */
    public static void validateForNextFireTime(PollConnectorProperties properties) {
        if (properties.getPollingType() == PollingType.CRON) {
            for (CronProperty cronJob : properties.getCronJobs()) {
                try {
                    CronExpression.validateExpression(StringUtils.defaultString(cronJob.getExpression()));
                } catch (ParseException e) {
                    throw new IllegalArgumentException(e.getMessage());
                }
            }
            return; // CRON ignores the weekly/daily restrictions entirely; nothing else to validate.
        }

        PollConnectorPropertiesAdvanced advanced = properties.getPollConnectorPropertiesAdvanced();
        if (advanced == null || !advanced.isWeekly()) {
            return;
        }

        boolean[] inactiveDays = advanced.getInactiveDays();
        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            if (day < inactiveDays.length && !inactiveDays[day]) {
                return; // at least one active day
            }
        }
        throw new IllegalArgumentException("At least one day must be active in the polling restrictions.");
    }

    /**
     * Computes the next poll fire time exactly as the desktop client previews it
     * (PollingSettingsPanel). Passing a null job class skips creating a real Quartz Scheduler/thread
     * pool, so this is cheap and side-effect-free to call per request.
     */
    public static String getNextFireTime(PollConnectorProperties properties) throws SchedulerException {
        validateForNextFireTime(properties);
        PollConnectorJobHandler handler = new PollConnectorJobHandler(properties, UUID.randomUUID().toString(), false);
        handler.configureJob(null, null, "NextFireTimePreview");
        return handler.getNextFireTime();
    }
}
