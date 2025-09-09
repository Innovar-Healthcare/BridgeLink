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
	private static final Logger log = LogManager.getLogger(PurgeRunner.class);

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
		ZonedDateTime next = now.withHour(2).withMinute(0).withSecond(0).withNano(0);
		if (!next.isAfter(now)) {
			next = next.plusDays(1);
		}

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
		retentionByBucket.forEach((bucket, keep) -> {
			try {
				Date cutoff = Date.from(now.minus(keep));
				int purged = service.purgeBeforeByBucket(bucket, cutoff);
				log.info("Purge bucket={}m cutoff={} purgedRows={}", bucket, cutoff, purged);
				if (throttleMs > 0) {
					Thread.sleep(throttleMs);
				}
			} catch (Throwable t) {
				log.warn("Purge failed for bucket={}", bucket, t);
			}
		});
	}

	private static Map<Integer, Duration> sortBucketsDesc(Map<Integer, Duration> input) {
		if (input == null || input.isEmpty())
			return Collections.emptyMap();
		return input.entrySet().stream().sorted((a, b) -> Integer.compare(b.getKey(), a.getKey())) // DESC
				.collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
	}
}
