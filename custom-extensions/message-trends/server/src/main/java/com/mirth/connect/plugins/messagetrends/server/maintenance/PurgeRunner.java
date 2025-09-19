/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.messagetrends.server.service.MessageTrendsService;

final class PurgeRunner {
	private static final Logger logger = LogManager.getLogger(PurgeRunner.class);

	private final MessageTrendsService service;
	private final Clock clock;
	private final Map<Integer, Duration> retentionByBucket;
	private final int fixedRateSeconds;
	private final long throttleMs;

	/** Master enable switch for this runner. */
	private volatile boolean enabled = true;

	PurgeRunner(MessageTrendsService service, Clock clock, Map<Integer, Duration> retentionByBucket, int fixedRateSeconds, long throttleMs) {
		this.service = service;
		this.clock = clock == null ? Clock.systemUTC() : clock;

		this.retentionByBucket = sortBucketsDesc(retentionByBucket);

		this.fixedRateSeconds = Math.max(3600, fixedRateSeconds);
		this.throttleMs = Math.max(1000, throttleMs);
	}

	long initialDelaySeconds() {
		ZonedDateTime now = ZonedDateTime.now(clock);
		ZonedDateTime next = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
		return Duration.between(now, next).getSeconds();
	}

	int fixedRateSeconds() {
		return fixedRateSeconds;
	}

	/** Enable/disable the runner at runtime. */
	void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	void runOnce() {
		if (!enabled) {
			return; // Runner is disabled; skip work.
		}

		Instant now = clock.instant();
		long totalPurged = 0L;
		for (Map.Entry<Integer, Duration> e : retentionByBucket.entrySet()) {
			final Integer bucket = e.getKey();
			final Duration keep = e.getValue();
			try {
				final Date cutoff = Date.from(now.minus(keep));
				final int purged = service.purgeBeforeByBucket(bucket, cutoff);
				totalPurged += purged;
				logger.info("Purge bucket={}m cutoff={} purgedRows={}", bucket, cutoff, purged);

				if (throttleMs > 0) {
					try {
						Thread.sleep(throttleMs);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						logger.warn("Purge interrupted during throttle at bucket={}m", bucket);
						break;
					}

				}
			} catch (Exception ex) {
				logger.warn("Purge failed for bucket={}m", bucket, ex);
			}
		}

		logger.info("Purge run finished: totalPurgedRows={}", totalPurged);
	}

	private static Map<Integer, Duration> sortBucketsDesc(Map<Integer, Duration> input) {
		if (input == null || input.isEmpty()) {
			return Collections.emptyMap();
		}

		return input.entrySet().stream().sorted((a, b) -> Integer.compare(b.getKey(), a.getKey())) // DESC
				.collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
	}
}
