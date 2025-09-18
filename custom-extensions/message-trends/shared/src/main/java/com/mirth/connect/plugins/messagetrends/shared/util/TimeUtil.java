package com.mirth.connect.plugins.messagetrends.shared.util;

import java.util.Date;

public final class TimeUtil {

	private TimeUtil() {
		// prevent instantiation
	}

	/**
	 * Convert epoch seconds (UTC) to java.util.Date.
	 * <p>
	 * Throws IllegalArgumentException if the input looks like epoch millis (>=
	 * 1e12).
	 *
	 * @param epochSeconds epoch timestamp in seconds (UTC)
	 * @param fieldName    used in error message
	 * @return Date object representing the instant
	 */
	public static Date toDateFromEpochSeconds(Long epochSeconds, String fieldName) {
		if (epochSeconds == null) {
			return null;
		}
		if (epochSeconds >= 1_000_000_000_000L) {
			throw new IllegalArgumentException(fieldName + " must be in epoch seconds, not millis (got " + epochSeconds + ")");
		}
		long ms = Math.multiplyExact(epochSeconds, 1000L);
		return new Date(ms);
	}

	/**
	 * Convert java.util.Date to epoch seconds (UTC).
	 *
	 * @param date input Date
	 * @return epoch seconds, or null if date is null
	 */
	public static Long toEpochSeconds(Date date) {
		return (date != null) ? date.getTime() / 1000L : null;
	}
}
