package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.messagetrends.server.service.MessageTrendsService;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

/**
 * Periodic rollup job that aggregates lower-resolution data into
 * higher-resolution buckets.
 *
 * Implementation notes for current service API: - No checkpoint API is
 * available, so we roll a single safe window per run: for each pipeline step
 * (src -> dst), we compute [toTs - dst, toTs) where toTs is the most recent
 * aligned boundary <= (now - safetyLag(dst)). - Aggregation is delegated to DAO
 * via service.getServerSeries(from, to, dstBucket), which is expected to return
 * series already grouped at the destination bucket size. - Each returned row is
 * upserted via service.upsertRollupDelta(row). - Idempotency is expected at DAO
 * level (upsert on unique key).
 */
final class RollupRunner {
	private static final Logger log = LogManager.getLogger(RollupRunner.class);

	private final MessageTrendsService service;
	private final Clock clock; // should be UTC

	/** Source->Destination bucket pipeline in minutes. */
	private final Map<Integer, Integer> pipeline = Collections.unmodifiableMap(new LinkedHashMap<Integer, Integer>() {
		{
			put(1, 5);
			put(5, 15);
			put(15, 60);
			put(60, 1440);
		}
	});

	/**
	 * Safety lag per destination bucket (minutes -> Duration). Mutable via setter.
	 */
	private volatile Map<Integer, Duration> safetyLag = defaultSafetyLag();

	/** Fixed-rate schedule in seconds (e.g., 120s). */
	private final int fixedRateSeconds;

	/** Master enable switch for this runner. */
	private volatile boolean enabled = true;

	RollupRunner(MessageTrendsService service, Clock clock, int fixedRateSeconds, String serverIdIgnored) {
		this.service = service;
		this.clock = (clock == null) ? Clock.systemUTC() : clock;
		this.fixedRateSeconds = Math.max(30, fixedRateSeconds);
	}

	/** Initial delay before the first run (seconds). */
	long initialDelaySeconds() {
		return 30L;
	}

	/** Fixed rate between runs (seconds). */
	int fixedRateSeconds() {
		return fixedRateSeconds;
	}

	/** Enable/disable the runner at runtime. */
	void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Replace the safety lag map at runtime. A defensive copy is stored. If null or
	 * empty, a safe default will be used.
	 */
	void setSafetyLagByBucket(Map<Integer, Duration> safetyLagByBucket) {
		if (safetyLagByBucket == null || safetyLagByBucket.isEmpty()) {
			this.safetyLag = defaultSafetyLag();
		} else {
			this.safetyLag = unmodifiableCopy(safetyLagByBucket);
		}
	}

	/** One execution tick. Safe to call from a ScheduledExecutor. */
	void runOnce() {
		if (!enabled) {
			return;
		}

		try {
			final Instant now = clock.instant();

			for (Entry<Integer, Integer> step : pipeline.entrySet()) {
				final int srcBucket = step.getKey();
				final int dstBucket = step.getValue();

				// Determine a conservative "safe" upper bound to avoid late data.
				Duration lag = safetyLag.get(dstBucket);
				if (lag == null || lag.isNegative()) {
					lag = Duration.ofMinutes(dstBucket); // fallback: one full destination bucket
				}

				// Align the exclusive upper bound to the previous destination bucket boundary.
				final Instant safeTo = now.minus(lag).truncatedTo(ChronoUnit.MINUTES);
				final Instant toTs = clampToBucketBoundary(dstBucket, safeTo); // exclusive upper bound
				final Instant fromTs = toTs.minus(Duration.ofMinutes(dstBucket)); // single dst window

				if (!toTs.isAfter(fromTs)) {
					continue;
				}

				// Ask DAO to return server-wide series grouped at the dst bucket size for
				// [fromTs, toTs).
				// If you plan to roll per-channel/per-connector, call the corresponding service
				// methods instead.
				final List<MessageStatisticsTimeseries> series = service.getServerSeries(java.util.Date.from(fromTs), java.util.Date.from(toTs), dstBucket);

				int wrote = 0;
				for (MessageStatisticsTimeseries row : series) {
					// Ensure bucket size is correctly set to destination bucket (defensive).
					row.setBucketSizeMinutes(dstBucket);
					wrote += service.upsertRollupDelta(row);
				}

				if (wrote > 0) {
					log.info("Rollup {}→{}: upserted={} window=[{}, {})", srcBucket, dstBucket, wrote, fromTs, toTs);
				} else {
					log.debug("Rollup {}→{}: no rows to upsert for window=[{}, {})", srcBucket, dstBucket, fromTs, toTs);
				}
			}
		} catch (Throwable t) {
			log.warn("RollupRunner failed", t);
		}
	}

	/**
	 * Aligns the instant to the previous destination bucket boundary (exclusive
	 * upper bound).
	 */
	private static Instant clampToBucketBoundary(int dstBucketMin, Instant safeTo) {
		long epochMin = safeTo.getEpochSecond() / 60L;
		long aligned = (epochMin / dstBucketMin) * dstBucketMin;
		return Instant.ofEpochSecond(aligned * 60L);
	}

	private static Map<Integer, Duration> defaultSafetyLag() {
		Map<Integer, Duration> m = new LinkedHashMap<Integer, Duration>();
		m.put(5, Duration.ofMinutes(5));
		m.put(15, Duration.ofMinutes(15));
		m.put(60, Duration.ofMinutes(30));
		m.put(1440, Duration.ofHours(2));
		return Collections.unmodifiableMap(m);
	}

	private static <K, V> Map<K, V> unmodifiableCopy(Map<K, V> m) {
		return Collections.unmodifiableMap(new LinkedHashMap<K, V>(m));
	}
}
