/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.util;

import java.util.Date;

public class TtlUtils {

	/**
	 * Checks if the given updatedAt timestamp is within the allowed TTL window.
	 *
	 * @param updatedAt  the timestamp to check
	 * @param ttlSeconds TTL in seconds (0 or less means no TTL enforced)
	 * @return true if updatedAt is within TTL or TTL is not enforced
	 */
	public static boolean isWithinTtlSeconds(Date updatedAt, long ttlSeconds) {
		if (updatedAt == null || ttlSeconds <= 0) {
			return true;
		}

		long now = System.currentTimeMillis();
		long cutoff = now - secondsToMillis(ttlSeconds);

		return updatedAt.getTime() >= cutoff;
	}

	/**
	 * Convert hours to seconds safely.
	 * 
	 * @param hours number of hours (<=0 returns 0)
	 * @return seconds equivalent, clamped to Long.MAX_VALUE if overflow
	 */
	public static long hoursToSeconds(long hours) {
		if (hours <= 0) {
			return 0;
		}

		if (hours > Long.MAX_VALUE / 3600L) {
			return Long.MAX_VALUE;
		}

		return hours * 3600L;
	}

	/**
	 * Convert hours and minutes to total seconds safely.
	 *
	 * @param hours   number of hours (must be >= 0)
	 * @param minutes number of minutes (must be >= 0)
	 * @return total seconds, clamped to Long.MAX_VALUE if overflow
	 * 
	 */
	public static long hoursMinutesToSeconds(long hours, long minutes) {
		long totalSeconds = 0;

		if (hours > 0) {
			if (hours > Long.MAX_VALUE / 3600L) {
				return Long.MAX_VALUE;
			}
			totalSeconds = hours * 3600L;
		}

		if (minutes > 0) {
			if (minutes > Long.MAX_VALUE / 60L) {
				return Long.MAX_VALUE;
			}
			long minutesSeconds = minutes * 60L;

			if (Long.MAX_VALUE - totalSeconds < minutesSeconds) {
				return Long.MAX_VALUE;
			}
			totalSeconds += minutesSeconds;
		}

		return totalSeconds;
	}

	/**
	 * Convert seconds to milliseconds safely.
	 * 
	 * @param seconds number of seconds (<=0 returns 0)
	 * @return milliseconds equivalent, clamped to Long.MAX_VALUE if overflow
	 */
	public static long secondsToMillis(long seconds) {
		if (seconds <= 0) {
			return 0;
		}

		// Check overflow
		if (seconds > Long.MAX_VALUE / 1000L) {
			return Long.MAX_VALUE;
		}

		return seconds * 1000L;
	}
}
