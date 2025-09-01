package com.mirth.connect.plugins.messagetrends.shared.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persisted UI configuration for MessageTrends.
 *
 * V1: - enabled: runtime toggle - defaultResolutions: dashboard resolution
 * options (subset of ALLOWED_RESOLUTIONS) - defaultTimeRange: dashboard initial
 * time range
 */
public class MessageTrendsProperties { // not final anymore

	// ----- Allowed resolutions -----
	/** Valid bucket sizes (minutes) supported by MessageTrends. */
	public static final List<Integer> ALLOWED_RESOLUTIONS_LIST = Arrays.asList(1, 5, 15, 60, 1440);

	public static final Set<Integer> ALLOWED_RESOLUTIONS = new HashSet<>(ALLOWED_RESOLUTIONS_LIST);

	// ----- Fields -----
	private boolean enabled;
	private List<Integer> defaultResolutions;
	private String defaultTimeRange;

	// ----- Constructors -----
	public MessageTrendsProperties() {
		this(false, ALLOWED_RESOLUTIONS_LIST, "last_24h");
	}

	public MessageTrendsProperties(boolean enabled, List<Integer> defaultResolutions, String defaultTimeRange) {
		this.enabled = enabled;
		this.defaultResolutions = defaultResolutions;
		this.defaultTimeRange = defaultTimeRange;
	}

	// ----- Getters/Setters -----
	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public List<Integer> getDefaultResolutions() {
		return defaultResolutions;
	}

	public void setDefaultResolutions(List<Integer> defaultResolutions) {
		this.defaultResolutions = defaultResolutions;
	}

	public String getDefaultTimeRange() {
		return defaultTimeRange;
	}

	public void setDefaultTimeRange(String defaultTimeRange) {
		this.defaultTimeRange = defaultTimeRange;
	}

	// ----- Keys -----
	private static final String KEY_ENABLED = "messagetrends.enabled";
	private static final String KEY_RESOLUTIONS = "messagetrends.dashboard.defaultResolutions";
	private static final String KEY_RANGE = "messagetrends.dashboard.defaultTimeRange";

	// ----- Converters -----
	public Properties toProperties() {
		Properties p = new Properties();
		p.setProperty(KEY_ENABLED, Boolean.toString(enabled));
		if (defaultResolutions != null && !defaultResolutions.isEmpty()) {
			String csv = defaultResolutions.stream().map(String::valueOf).collect(Collectors.joining(","));
			p.setProperty(KEY_RESOLUTIONS, csv);
		}
		if (defaultTimeRange != null) {
			p.setProperty(KEY_RANGE, defaultTimeRange);
		}
		return p;
	}

	public static MessageTrendsProperties fromProperties(Properties p) {
		if (p == null) {
			return getDefault();
		}
		boolean enabled = parseBoolean(p.getProperty(KEY_ENABLED), false);

		List<Integer> resolutions = parseCsvIntList(p.getProperty(KEY_RESOLUTIONS, ALLOWED_RESOLUTIONS_LIST.stream().map(String::valueOf).collect(Collectors.joining(",")))).stream().filter(ALLOWED_RESOLUTIONS::contains).collect(Collectors.toList());

		if (resolutions.isEmpty()) {
			resolutions = getDefault().getDefaultResolutions();
		}

		String range = p.getProperty(KEY_RANGE, "last_24h");

		return new MessageTrendsProperties(enabled, resolutions, range);
	}

	public static MessageTrendsProperties getDefault() {
		return new MessageTrendsProperties(false, ALLOWED_RESOLUTIONS_LIST, "last_24h");
	}

	// ----- Helpers -----
	private static boolean parseBoolean(String raw, boolean defaultValue) {
		if (raw == null)
			return defaultValue;
		String s = raw.trim().toLowerCase();
		switch (s) {
		case "true":
		case "1":
		case "yes":
		case "on":
			return true;
		case "false":
		case "0":
		case "no":
		case "off":
			return false;
		default:
			return defaultValue;
		}
	}

	private static List<Integer> parseCsvIntList(String csv) {
		return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::valueOf).collect(Collectors.toList());
	}
}
