package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime configuration snapshot for the MessageTrends scheduler and its
 * runners.
 * 
 * - Immutable: all fields are final, modified via "with*" copy methods. -
 * Defaults: use defaultConfig() to get a safe, future-proof baseline. - V1:
 * only `enabled` is actively controlled by UI; other fields stay at defaults.
 */
public final class MessageTrendsConfig {

	// ----- Core -----
	/** Master switch to enable/disable the entire MessageTrends system. */
	private final boolean enabled;

	/** Single clock reference (usually UTC) for all runners. */
	private final Clock clock;

	// ----- Flush (add minute deltas) -----
	/** Whether the flush runner is active. */
	private final boolean flushEnabled;

	// ----- Rollup (1→5→15→60→1440) -----
	/** Whether the rollup runner is active. */
	private final boolean rollupEnabled;
	/** Fixed-rate interval (seconds) for rollup execution. */
	private final int rollupFixedRateSeconds;
	/** Safety lag by destination bucket (minutes bucket -> lag duration). */
	private final Map<Integer, Duration> rollupSafetyLagByBucket;

	// ----- Purge (retention) -----
	/** Whether the purge runner is active. */
	private final boolean purgeEnabled;
	/** Fixed-rate interval (seconds) for purge execution. */
	private final int purgeFixedRateSeconds;
	/** Throttle (milliseconds) between purging different buckets. */
	private final long purgeThrottleMs;
	/** Retention policy per bucket (minutes bucket -> keep duration). */
	private final Map<Integer, Duration> retentionByBucket;

	// ---- Constructor kept private to enforce defaults via factory ----
	private MessageTrendsConfig(boolean enabled, Clock clock, boolean flushEnabled, boolean rollupEnabled, int rollupFixedRateSeconds, Map<Integer, Duration> rollupSafetyLagByBucket, boolean purgeEnabled, int purgeFixedRateSeconds, long purgeThrottleMs, Map<Integer, Duration> retentionByBucket) {
		this.enabled = enabled;
		this.clock = (clock != null ? clock : Clock.systemUTC());

		this.flushEnabled = flushEnabled;

		this.rollupEnabled = rollupEnabled;
		this.rollupFixedRateSeconds = rollupFixedRateSeconds;
		this.rollupSafetyLagByBucket = toUnmodifiable(rollupSafetyLagByBucket);

		this.purgeEnabled = purgeEnabled;
		this.purgeFixedRateSeconds = purgeFixedRateSeconds;
		this.purgeThrottleMs = purgeThrottleMs;
		this.retentionByBucket = toUnmodifiable(retentionByBucket);

		validate();
	}

	// @formatter:off
	// ----- Factory with centralized defaults -----
	public static MessageTrendsConfig defaultConfig() {
		return new MessageTrendsConfig(
				/* enabled */ false, 
				/* clock */ Clock.systemUTC(), 
				/* flushEnabled */ true, 
				/* rollupEnabled */ true, 
				/* rollupFixedRateSeconds */ 120, 
				/* rollupSafetyLagByBucket */ defaultRollupSafetyLag(), 
				/* purgeEnabled */ true, 
				/* purgeFixedRateSeconds */ 24 * 3600, 
				/* purgeThrottleMs */ 1000L, 
				/* retentionByBucket */ defaultRetention());
	}
	// @formatter:on

	// ----- Getters -----
	public boolean isEnabled() {
		return enabled;
	}

	public Clock getClock() {
		return clock;
	}

	public boolean isFlushEnabled() {
		return flushEnabled;
	}

	public boolean isRollupEnabled() {
		return rollupEnabled;
	}

	public int getRollupFixedRateSeconds() {
		return rollupFixedRateSeconds;
	}

	public Map<Integer, Duration> getRollupSafetyLagByBucket() {
		return rollupSafetyLagByBucket;
	}

	public boolean isPurgeEnabled() {
		return purgeEnabled;
	}

	public int getPurgeFixedRateSeconds() {
		return purgeFixedRateSeconds;
	}

	public long getPurgeThrottleMs() {
		return purgeThrottleMs;
	}

	public Map<Integer, Duration> getRetentionByBucket() {
		return retentionByBucket;
	}

	// ----- Minimal withers (V1 only needs withEnabled; others can be added later)
	// -----
	public MessageTrendsConfig withEnabled(boolean value) {
		return new MessageTrendsConfig(value, this.clock, this.flushEnabled, this.rollupEnabled, this.rollupFixedRateSeconds, this.rollupSafetyLagByBucket, this.purgeEnabled, this.purgeFixedRateSeconds, this.purgeThrottleMs, this.retentionByBucket);
	}

	// @formatter:off
	/**
	 * Default safety lag for each rollup destination bucket.
	 *
	 * Rule of thumb: each rollup waits ~1/3–1/4 of its window size before rolling,
	 * to ensure that the source bucket has completed.
	 *
	 * Examples: 
	 * 1 → 5m rollup waits 2m before finalizing a 5m window
	 * 5 → 15m rollup waits 5m
	 * 15 → 60m rollup waits 15m 
	 * 60 → 1440m (daily) rollup waits 1h
	 */
	// @formatter:on
	private static Map<Integer, Duration> defaultRollupSafetyLag() {
		Map<Integer, Duration> m = new LinkedHashMap<Integer, Duration>();
		m.put(5, Duration.ofMinutes(2)); // 1→5
		m.put(15, Duration.ofMinutes(5)); // 5→15
		m.put(60, Duration.ofMinutes(15)); // 15→60
		m.put(1440, Duration.ofHours(1)); // 60→1440
		return m;
	}

	// ----- Defaults for complex fields -----
	private static Map<Integer, Duration> defaultRetention() {
		Map<Integer, Duration> m = new LinkedHashMap<Integer, Duration>();
		m.put(1, Duration.ofDays(1));
		m.put(5, Duration.ofDays(7));
		m.put(15, Duration.ofDays(30));
		m.put(60, Duration.ofDays(90));
		m.put(1440, Duration.ofDays(1095)); // ~3 years
		return m;
	}

	// ----- Validation & helpers -----
	private void validate() {
		if (rollupFixedRateSeconds < 30) {
			throw new IllegalArgumentException("rollupFixedRateSeconds must be >= 30");
		}

		if (purgeFixedRateSeconds < 3600) {
			throw new IllegalArgumentException("purgeFixedRateSeconds must be >= 3600");
		}

		if (purgeThrottleMs < 1000) {
			throw new IllegalArgumentException("purgeThrottleMs must be >= 1000");
		}
	}

	private static <K, V> Map<K, V> toUnmodifiable(Map<K, V> m) {
		if (m == null || m.isEmpty()) {
			return Collections.emptyMap();
		}

		return Collections.unmodifiableMap(new LinkedHashMap<K, V>(m));
	}
}
