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
	// Map<bucketMinutes, Instant capProcessed>
	private final Map<Integer, Instant> lastCapProcessedByBucket = new ConcurrentHashMap<>();

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
		if (!enabled)
			return;

		try {
			final Instant now = clock.instant(); // UTC

			for (Map.Entry<Integer, Integer> step : pipeline.entrySet()) {
				final int srcBucket = step.getKey();
				final int dstBucket = step.getValue();

				// Safety lag (fallback: one full dst window)
				Duration lag = safetyLag.get(dstBucket);
				if (lag == null || lag.isNegative())
					lag = Duration.ofMinutes(dstBucket);

				// Latest aligned boundary <= (now - lag)
				final Instant cap = clampToBucketBoundary(dstBucket, now.minus(lag));

				// In-memory gating to avoid reprocessing the same boundary
				final Instant lastCap = lastCapProcessedByBucket.get(dstBucket);
				if (lastCap != null && !cap.isAfter(lastCap))
					continue;

				// Single newest window: [from, to) = [cap - dst, cap)
				final Instant fromTs = cap.minus(Duration.ofMinutes(dstBucket));
				final Instant toTs = cap;

				// Read raw rows from source bucket for this window (per-dimension rows)
				final List<MessageStatisticsTimeseries> srcRows = service.getServerSeries(Date.from(fromTs), Date.from(toTs), srcBucket);

				if (srcRows == null || srcRows.isEmpty()) {
					// Forward-only policy: accept gaps
					lastCapProcessedByBucket.put(dstBucket, cap);
					continue;
				}

				// ---- Group BY UNIQUE KEY: (server_id, channel_id, connector_id, boundary,
				// bucket) ----
				final Map<RollKey, MessageStatisticsTimeseries> grouped = new LinkedHashMap<>();

				for (MessageStatisticsTimeseries r : srcRows) {
					final String serverId = r.getServerId();
					final String channelId = r.getChannelId();
					final String connectorId = r.getConnectorId();

					final Instant boundary = clampToBucketBoundary(dstBucket, r.getTs().toInstant());
					final RollKey key = new RollKey(serverId, channelId, connectorId, boundary, dstBucket);

					MessageStatisticsTimeseries acc = grouped.get(key);
					if (acc == null) {
						acc = new MessageStatisticsTimeseries();
						acc.setServerId(serverId);
						acc.setChannelId(channelId);
						acc.setConnectorId(connectorId);
						acc.setTs(Date.from(boundary)); // dst boundary
						acc.setBucketSizeMinutes(dstBucket); // dst bucket
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

				final List<MessageStatisticsTimeseries> rowsToWrite = new ArrayList<>(grouped.values());
				if (rowsToWrite.isEmpty()) {
					lastCapProcessedByBucket.put(dstBucket, cap);
					continue;
				}

				try {
					// One transactional overwrite for the whole window:
					// - DELETE existing rows for this (server, ANY channel/connector) at ts=fromTs
					// & dstBucket
					// - INSERT batch rowsToWrite (REPLACE semantics, not additive)
					int wrote = service.replaceRollupWindow(Date.from(fromTs), dstBucket, rowsToWrite);
					log.info("Rolled {}→{} window [{} , {}): wrote={}", srcBucket, dstBucket, fromTs, toTs, wrote);

					lastCapProcessedByBucket.put(dstBucket, cap);

				} catch (Throwable e) {
					log.warn("Overwrite window failed for {}→{} [{} , {}): {}", srcBucket, dstBucket, fromTs, toTs, e.toString());
					// do not advance gating on failure
				}
			}
		} catch (Throwable t) {
			log.warn("RollupRunner runOnce failed", t);
		}
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	// Composite key for grouping
	private static final class RollKey {
		final String serverId, channelId, connectorId;
		final Instant boundary;
		final int dstBucket;

		RollKey(String s, String c, String k, Instant b, int d) {
			this.serverId = s;
			this.channelId = c;
			this.connectorId = k;
			this.boundary = b;
			this.dstBucket = d;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof RollKey))
				return false;
			RollKey x = (RollKey) o;
			return dstBucket == x.dstBucket && java.util.Objects.equals(serverId, x.serverId) && java.util.Objects.equals(channelId, x.channelId) && java.util.Objects.equals(connectorId, x.connectorId) && java.util.Objects.equals(boundary, x.boundary);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(serverId, channelId, connectorId, boundary, dstBucket);
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
