package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
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

	// ----- Flush (add minute deltas) -----
	/** Whether the flush runner is active. */
	private final boolean flushEnabled;
	/** Sweep interval (seconds) for scanning the in-memory buffer. */
	private final int flushSweepSeconds;
	/** Clock to align minute boundaries (usually UTC). */
	private final Clock flushClock;

	// ----- Rollup (1→5→15→60→1440) -----
	/** Whether the rollup runner is active. */
	private final boolean rollupEnabled;
	/** Fixed-rate interval (seconds) for rollup execution. */
	private final int rollupFixedRateSeconds;
	/** Safety lag by destination bucket (minutes bucket -> lag duration). */
	private final Map<Integer, Duration> rollupSafetyLagByBucket;
	/** Clock for rollup alignment (usually UTC). */
	private final Clock rollupClock;

	// ----- Purge (retention) -----
	/** Whether the purge runner is active. */
	private final boolean purgeEnabled;
	/** Fixed-rate interval (seconds) for purge execution. */
	private final int purgeFixedRateSeconds;
	/** Throttle (milliseconds) between purging different buckets. */
	private final long purgeThrottleMs;
	/**
	 * Local time zone used to schedule the daily purge window (e.g., 02:00 local).
	 */
	private final ZoneId purgeZone;
	/** Retention policy per bucket (minutes bucket -> keep duration). */
	private final Map<Integer, Duration> retentionByBucket;
	/** Clock used when computing purge cutoffs (usually UTC). */
	private final Clock purgeClock;

	// ---- Constructor kept private to enforce defaults via factory ----
	private MessageTrendsConfig(boolean enabled, boolean flushEnabled, int flushSweepSeconds, Clock flushClock, boolean rollupEnabled, int rollupFixedRateSeconds, Map<Integer, Duration> rollupSafetyLagByBucket, Clock rollupClock, boolean purgeEnabled, int purgeFixedRateSeconds, long purgeThrottleMs, ZoneId purgeZone, Map<Integer, Duration> retentionByBucket, Clock purgeClock) {
		this.enabled = enabled;

		this.flushEnabled = flushEnabled;
		this.flushSweepSeconds = flushSweepSeconds;
		this.flushClock = flushClock != null ? flushClock : Clock.systemUTC();

		this.rollupEnabled = rollupEnabled;
		this.rollupFixedRateSeconds = rollupFixedRateSeconds;
		this.rollupSafetyLagByBucket = toUnmodifiable(rollupSafetyLagByBucket);
		this.rollupClock = rollupClock != null ? rollupClock : Clock.systemUTC();

		this.purgeEnabled = purgeEnabled;
		this.purgeFixedRateSeconds = purgeFixedRateSeconds;
		this.purgeThrottleMs = purgeThrottleMs;
		this.purgeZone = purgeZone != null ? purgeZone : ZoneId.systemDefault();
		this.retentionByBucket = toUnmodifiable(retentionByBucket);
		this.purgeClock = purgeClock != null ? purgeClock : Clock.systemUTC();

		validate();
	}

	// ----- Factory with centralized defaults -----
	public static MessageTrendsConfig defaultConfig() {
		return new MessageTrendsConfig(/* enabled */ false, /* flushEnabled */ true, /* flushSweepSeconds */ 10, /* flushClock */ Clock.systemUTC(), /* rollupEnabled */ true, /* rollupFixedRateSeconds */ 120, /* rollupSafetyLagByBucket */ defaultRollupSafetyLag(), /* rollupClock */ Clock.systemUTC(), /* purgeEnabled */ true, /* purgeFixedRateSeconds */ 24 * 3600, /* purgeThrottleMs */ 200L, /* purgeZone */ ZoneId.systemDefault(), /* retentionByBucket */ defaultRetention(), /* purgeClock */ Clock.systemUTC());
	}

	// ----- Getters -----
	public boolean isEnabled() {
		return enabled;
	}

	public boolean isFlushEnabled() {
		return flushEnabled;
	}

	public int getFlushSweepSeconds() {
		return flushSweepSeconds;
	}

	public Clock getFlushClock() {
		return flushClock;
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

	public Clock getRollupClock() {
		return rollupClock;
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

	public ZoneId getPurgeZone() {
		return purgeZone;
	}

	public Map<Integer, Duration> getRetentionByBucket() {
		return retentionByBucket;
	}

	public Clock getPurgeClock() {
		return purgeClock;
	}

	// ----- Minimal withers (V1 only needs withEnabled; others can be added later)
	// -----
	public MessageTrendsConfig withEnabled(boolean value) {
		return new MessageTrendsConfig(value, this.flushEnabled, this.flushSweepSeconds, this.flushClock, this.rollupEnabled, this.rollupFixedRateSeconds, this.rollupSafetyLagByBucket, this.rollupClock, this.purgeEnabled, this.purgeFixedRateSeconds, this.purgeThrottleMs, this.purgeZone, this.retentionByBucket, this.purgeClock);
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

	private static Map<Integer, Duration> defaultRollupSafetyLag() {
		Map<Integer, Duration> m = new LinkedHashMap<Integer, Duration>();
		m.put(5, Duration.ofMinutes(5));
		m.put(15, Duration.ofMinutes(15));
		m.put(60, Duration.ofMinutes(30));
		m.put(1440, Duration.ofHours(2));
		return m;
	}

	// ----- Validation & helpers -----
	private void validate() {
		if (flushSweepSeconds < 1) {
			throw new IllegalArgumentException("flushSweepSeconds must be >= 1");
		}
		if (rollupFixedRateSeconds < 30) {
			throw new IllegalArgumentException("rollupFixedRateSeconds must be >= 30");
		}
		if (purgeFixedRateSeconds < 3600) {
			throw new IllegalArgumentException("purgeFixedRateSeconds must be >= 3600");
		}
		if (purgeThrottleMs < 0) {
			throw new IllegalArgumentException("purgeThrottleMs must be >= 0");
		}
	}

	private static <K, V> Map<K, V> toUnmodifiable(Map<K, V> m) {
		if (m == null || m.isEmpty())
			return Collections.emptyMap();
		return Collections.unmodifiableMap(new LinkedHashMap<K, V>(m));
	}
}
