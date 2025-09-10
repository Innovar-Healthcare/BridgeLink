package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
	private final Map<Integer, Instant> lastCapProcessedByBucket = new ConcurrentHashMap<>();

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

	/** One execution tick. Safe to call from a ScheduledExecutor. */
	void runOnce() {
		if (!enabled) {
			return;
		}
		try {
			final Instant now = clock.instant(); // UTC

			// 1' → 5' (fixed 2-minute lag). If no progress/failure → stop chaining.
			final Instant ts5 = roll5Min(now);
			if (ts5 == null) {
				log.debug("roll5Min: no progress; skip 15/60/day.");
				return;
			}

			// 5' → 15'
			final Instant ts15 = rollUpGeneric(ts5, 5, 15, "roll15Min");
			if (ts15 == null) {
				log.debug("roll15Min: no progress; skip 60/day.");
				return;
			}

			// 15' → 60'
			final Instant ts60 = rollUpGeneric(ts15, 15, 60, "roll60Min");
			if (ts60 == null) {
				log.debug("roll60Min: no progress; skip 1-day.");
				return;
			}

			// 60' → 1440' (1 day)
			rollUpGeneric(ts60, 60, 1440, "rollOneDay");

		} catch (Throwable t) {
			log.warn("RollupRunner runOnce failed", t);
		}
	}

	/**
	 * 1' → 5' rollup (with fixed 2-minute safety lag).
	 *
	 * Computes the newest aligned 5' boundary <= (now - 2 minutes), reads 1' rows
	 * in [cap5-5', cap5), and overwrites the 5' window at ts = cap5-5'.
	 *
	 * Returns the 5' boundary end (cap5) if successful, or null if no progress
	 * (already processed) or if the write failed. The returned boundary is used to
	 * trigger the next rollup (5' → 15').
	 */

	private Instant roll5Min(Instant now) {
		final int srcBucket = 1, dstBucket = 5;

		// Safety lag
		final Duration lag = Duration.ofMinutes(2);
		final Instant cap = clampToBucketBoundary(dstBucket, now.minus(lag));

		final Instant last = lastCapProcessedByBucket.get(dstBucket);
		if (last != null && !cap.isAfter(last)) {
			log.debug("roll5Min skipped: cap={} not after last={}", cap, last);
			return null; // no progress
		}

		final Instant fromTs = cap.minus(Duration.ofMinutes(dstBucket));
		final Date from = Date.from(fromTs);
		final Date to = Date.from(cap);

		log.debug("roll5Min start window [{} , {})", fromTs, cap);

		final List<MessageStatisticsTimeseries> rowsToWrite = buildRowsForWindow(srcBucket, dstBucket, from, to);

		if (rowsToWrite.isEmpty()) {
			lastCapProcessedByBucket.put(dstBucket, cap);
			log.info("roll5Min {}→{} [{} , {}): empty", srcBucket, dstBucket, fromTs, cap);
			return cap;
		}

		try {
			int wrote = service.replaceRollupWindow(from, dstBucket, rowsToWrite);

			log.info("roll5Min {}→{} [{} , {}): wrote={}", srcBucket, dstBucket, fromTs, cap, wrote);

			lastCapProcessedByBucket.put(dstBucket, cap);

			return cap; // progressed
		} catch (Exception e) {
			log.warn("Overwrite window failed for {}→{} [{} , {}): {}", srcBucket, dstBucket, fromTs, cap, e.toString());
			return null; // failure → signal caller to skip next steps
		}
	}

	/**
	 * Generic rollup (always overwrite; no gating). Uses tsSrc as the current
	 * source boundary to determine which destination bucket to (re)write. Returns
	 * toTsDst (boundary end) for chaining; never null unless tsSrc is null or on
	 * failure.
	 *
	 * @param tsSrc     current boundary of the source bucket (e.g., ts5, ts15,
	 *                  ts60)
	 * @param srcBucket source bucket minutes (e.g., 5, 15, 60)
	 * @param dstBucket destination bucket minutes (e.g., 15, 60, 1440)
	 * @param opName    log prefix (e.g., "roll15Min", "roll60Min", "rollOneDay")
	 */
	private Instant rollUpGeneric(Instant tsSrc, int srcBucket, int dstBucket, String opName) {
		if (tsSrc == null) {
			return null;
		}

		// Identify the destination bucket that contains tsSrc
		final Instant endDst = ceilToBucketBoundary(dstBucket, tsSrc);
		final Instant startDst = endDst.minus(Duration.ofMinutes(dstBucket));

		// Partial end: up to tsSrc, but not beyond endDst (final when tsSrc == endDst)
		final Instant toTsDst = tsSrc.isBefore(endDst) ? tsSrc : endDst;

		// Read src-bucket rows in [startDst, toTsDst) and overwrite the dst window at
		// ts = startDst
		final Date from = Date.from(startDst);
		final Date to = Date.from(toTsDst);
		final List<MessageStatisticsTimeseries> rowsToWrite = buildRowsForWindow(srcBucket, dstBucket, from, to);

		try {
			int wrote = service.replaceRollupWindow(from, dstBucket, rowsToWrite);

			log.info("{} {}→{} [{} , {}): wrote={}", opName, srcBucket, dstBucket, startDst, toTsDst, wrote);

			return toTsDst;
		} catch (Exception e) {
			log.warn("Overwrite window failed for {} {}→{} [{} , {}): {}", opName, srcBucket, dstBucket, startDst, toTsDst, e.toString());
			return null; // signal failure so upper layers can skip if desired
		}
	}

	private List<MessageStatisticsTimeseries> buildRowsForWindow(int srcBucket, int dstBucket, Date fromTs, Date toTs) {

		final List<MessageStatisticsTimeseries> srcRows = service.getServerSeries(fromTs, toTs, srcBucket);

		if (srcRows == null || srcRows.isEmpty()) {
			return Collections.emptyList();
		}

		final Map<RollKey, MessageStatisticsTimeseries> grouped = new LinkedHashMap<>();

		for (MessageStatisticsTimeseries r : srcRows) {
			final String serverId = r.getServerId();
			final String channelId = r.getChannelId();
			final String connectorId = r.getConnectorId();

			// Convert Date -> Instant for boundary math
			final Instant boundary = clampToBucketBoundary(dstBucket, r.getTs().toInstant());
			final Date bucketTs = Date.from(boundary);

			final RollKey key = new RollKey(serverId, channelId, connectorId, bucketTs, dstBucket);

			MessageStatisticsTimeseries acc = grouped.get(key);
			if (acc == null) {
				acc = new MessageStatisticsTimeseries();
				acc.setServerId(serverId);
				acc.setChannelId(channelId);
				acc.setConnectorId(connectorId);
				acc.setTs(bucketTs);
				acc.setBucketSizeMinutes(dstBucket);
				acc.setReceived(0);
				acc.setFiltered(0);
				acc.setQueued(0);
				acc.setSent(0);
				acc.setError(0);
				grouped.put(key, acc);
			}
			// sum metrics (null-safe)
			acc.setReceived(nz(acc.getReceived()) + nz(r.getReceived()));
			acc.setFiltered(nz(acc.getFiltered()) + nz(r.getFiltered()));
			acc.setQueued(nz(acc.getQueued()) + nz(r.getQueued()));
			acc.setSent(nz(acc.getSent()) + nz(r.getSent()));
			acc.setError(nz(acc.getError()) + nz(r.getError()));
		}

		return new ArrayList<>(grouped.values());
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

	/**
	 * Ceil up to the next bucket boundary (or keep t if already aligned). Example:
	 * bucket=15' - t=10:07 → returns 10:15 - t=10:15 → returns 10:15
	 */
	private static Instant ceilToBucketBoundary(int bucketMinutes, Instant t) {
		if (bucketMinutes <= 0) {
			throw new IllegalArgumentException("bucketMinutes must be > 0");
		}
		Instant floor = clampToBucketBoundary(bucketMinutes, t);
		return floor.equals(t) ? t : floor.plus(Duration.ofMinutes(bucketMinutes));
	}

	private static <K, V> Map<K, V> unmodifiableCopy(Map<K, V> m) {
		if (m == null || m.isEmpty()) {
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(m));
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
