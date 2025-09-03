package com.mirth.connect.plugins.messagetrends.shared.model;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared constants and helpers for default retention and allowed time range
 * presets.
 */
public final class TimeRangePresets {

	private TimeRangePresets() {
	}

	/** Default retention per bucket size (minutes). */
	public static Map<Integer, Duration> getDefaultRetention() {
		Map<Integer, Duration> m = new LinkedHashMap<Integer, Duration>();
		m.put(1, Duration.ofDays(1));
		m.put(5, Duration.ofDays(7));
		m.put(15, Duration.ofDays(30));
		m.put(60, Duration.ofDays(90));
		m.put(1440, Duration.ofDays(1095)); // ~3 years
		return Collections.unmodifiableMap(m);
	}

	/** Ordered list of preset identifiers (shortest to longest). */
	public static final List<String> PRESETS = Collections.unmodifiableList(Arrays.asList("last_1h", "last_3h", "last_6h", "last_12h", "last_24h", "last_3d", "last_7d", "last_14d", "last_30d", "last_90d", "last_180d", "last_365d", "last_730d", "last_1095d"));

	/** Map preset -> duration. */
	public static final Map<String, Duration> PRESET_TO_DURATION;
	static {
		Map<String, Duration> tmp = new HashMap<String, Duration>();
		tmp.put("last_1h", Duration.ofHours(1));
		tmp.put("last_3h", Duration.ofHours(3));
		tmp.put("last_6h", Duration.ofHours(6));
		tmp.put("last_12h", Duration.ofHours(12));
		tmp.put("last_24h", Duration.ofDays(1));
		tmp.put("last_3d", Duration.ofDays(3));
		tmp.put("last_7d", Duration.ofDays(7));
		tmp.put("last_14d", Duration.ofDays(14));
		tmp.put("last_30d", Duration.ofDays(30));
		tmp.put("last_90d", Duration.ofDays(90));
		tmp.put("last_180d", Duration.ofDays(180));
		tmp.put("last_365d", Duration.ofDays(365));
		tmp.put("last_730d", Duration.ofDays(730));
		tmp.put("last_1095d", Duration.ofDays(1095));
		PRESET_TO_DURATION = Collections.unmodifiableMap(tmp);
	}

	public static final Map<String, String> PRESET_TO_LABEL;
	static {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("last_30m", "Last 30 Minutes");
		m.put("last_1h", "Last 1 Hour");
		m.put("last_3h", "Last 3 Hours");
		m.put("last_6h", "Last 6 Hours");
		m.put("last_12h", "Last 12 Hours");
		m.put("last_24h", "Last 24 Hours");
		m.put("last_3d", "Last 3 Days");
		m.put("last_7d", "Last 7 Days");
		m.put("last_14d", "Last 14 Days");
		m.put("last_30d", "Last Month"); // 30 days -> Last Month
		m.put("last_90d", "Last 3 Months"); // 90 days -> Last 3 Months
		m.put("last_180d", "Last 6 Months"); // 180 days -> Last 6 Months
		m.put("last_365d", "Last 1 Year");
		m.put("last_730d", "Last 2 Years");
		m.put("last_1095d", "Last 3 Years");
		PRESET_TO_LABEL = Collections.unmodifiableMap(m);
	}

	private static final Map<Integer, Duration> MIN_WINDOW_BY_BUCKET;
	static {
		Map<Integer, Duration> tmp = new LinkedHashMap<Integer, Duration>();
		// Manual minimum windows per bucket (to keep enough points on chart)
		// 1m -> >= 1h
		// 5m -> >= 6h
		// 15m -> >= 12h
		// 60m -> >= 3d
		// 1440m (1d) -> >= 30d (this matches your expected list starting at last_30d)
		tmp.put(1, Duration.ofHours(1));
		tmp.put(5, Duration.ofHours(6));
		tmp.put(15, Duration.ofHours(12));
		tmp.put(60, Duration.ofDays(3));
		tmp.put(1440, Duration.ofDays(30));
		MIN_WINDOW_BY_BUCKET = Collections.unmodifiableMap(tmp);
	}

	/**
	 * Allowed presets for a given bucket size: minWindow(bucket) <= preset <=
	 * retention(bucket).
	 */
	public static List<String> allowedRangesFor(int bucketMinutes) {
		Duration retention = getDefaultRetention().get(bucketMinutes);
		if (retention == null) {
			return Collections.emptyList();
		}
		Duration minWindow = MIN_WINDOW_BY_BUCKET.get(bucketMinutes);
		if (minWindow == null) {
			// fallback: at least one bucket long if not specified
			minWindow = Duration.ofMinutes(bucketMinutes);
		}

		final Duration minW = minWindow;
		return PRESETS.stream().filter(p -> {
			Duration d = PRESET_TO_DURATION.get(p);
			return d != null && d.compareTo(minW) >= 0 // NEW: enforce manual minimum window
					&& d.compareTo(retention) <= 0; // existing retention ceiling
		}).collect(java.util.stream.Collectors.toList());
	}

	/** Resolve preset identifier to Duration (null if unknown). */
	public static Duration toDuration(String preset) {
		return PRESET_TO_DURATION.get(preset);
	}

	/**
	 * Coerce a preset to one that is valid for the given bucketMinutes, based on
	 * retention. If the requested preset exceeds retention, the longest valid
	 * preset is returned. If none are valid, returns null.
	 */
	public static String coerceRangeForBucket(int bucketMinutes, String requestedPreset) {
		List<String> allowed = allowedRangesFor(bucketMinutes);
		if (allowed.isEmpty()) {
			return null;
		}
		if (requestedPreset != null && allowed.contains(requestedPreset)) {
			return requestedPreset;
		}
		// fallback: pick the longest allowed (last in ordered list)
		return allowed.get(allowed.size() - 1);
	}
}
