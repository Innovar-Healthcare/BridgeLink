/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.dynamiclookup.server.service.LookupService;

final class PurgeRunner {
	private static final Logger logger = LogManager.getLogger(PurgeRunner.class);
	private static final int FIXED_RATE_SECONDS = 60 * 60; // 1 hours

	private final LookupService service;
	private final int retentionDays;

	PurgeRunner(LookupService service, int retentionDays) {
		this.service = service;
		this.retentionDays = retentionDays;
	}

	int getFixedRateSeconds() {
		return FIXED_RATE_SECONDS;
	}

	void runOnce() {
		Instant now = Clock.systemUTC().instant();
		try {
			Instant cutoffInstant = now.minus(Duration.ofDays(retentionDays));
			final Date cutoff = Date.from(cutoffInstant);
			final int purged = service.deleteAuditEntriesBefore(cutoff);

			logger.info("Audit purge completed: retention={}d, cutoff={}, purgedRows={}", retentionDays, cutoff, purged);
		} catch (Exception ex) {
			logger.warn("Audit purge failed: retention={}d", retentionDays, ex);
		}
	}

}
