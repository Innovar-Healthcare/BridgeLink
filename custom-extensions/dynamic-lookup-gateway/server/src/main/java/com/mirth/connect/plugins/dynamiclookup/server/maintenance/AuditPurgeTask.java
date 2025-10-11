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
import java.time.temporal.ChronoUnit;
import java.util.Date;

import com.mirth.connect.plugins.dynamiclookup.server.controller.LookupPropertiesProvider;
import com.mirth.connect.plugins.dynamiclookup.server.service.LookupService;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupProperties;

public class AuditPurgeTask extends AbstractScheduledTask {
	private static final int FIXED_RATE_SECONDS = 60 * 60; // 1 hours
	private static final int INITIAL_DELAY_SECONDS = 300; // 5 mins

	private final LookupService service;
	private final LookupPropertiesProvider provider;

	public AuditPurgeTask(LookupPropertiesProvider provider, LookupService service) {
		super("AuditPurge");
		this.provider = provider;
		this.service = service;
	}

	public int getFixedRateSeconds() {
		return FIXED_RATE_SECONDS;
	}

	public int getInitialDelaySeconds() {
		return INITIAL_DELAY_SECONDS;
	}

	@Override
	protected void runOnce() throws Exception {
		Instant now = Clock.systemUTC().instant();
		LookupProperties props = provider.get();

		if (!props.isAuditPruneEnabled()) {
			logDebug("Prune disabled; skipping.");
			return;
		}

		int retentionDays = props.getAuditPruneRetentionDays();
		if (retentionDays <= 0) {
			logWarn("RetentionDays <= 0; skipping purge.");
			return;
		}

		Instant cutoffInstant = now.minus(Duration.ofDays(retentionDays));
		final Date cutoff = Date.from(cutoffInstant);
		final int purged = service.deleteAuditEntriesBefore(cutoff);

		logInfo(String.format("Audit purge completed successfully: retention=%dd, cutoff=%s (UTC), purgedRows=%d", retentionDays, cutoffInstant.truncatedTo(ChronoUnit.SECONDS), purged));
	}
}
