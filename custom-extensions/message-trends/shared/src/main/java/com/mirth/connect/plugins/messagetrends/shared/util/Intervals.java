/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.shared.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for supported time bucket intervals.
 *
 * Canonical public codes (ONLY these are accepted): - "1minute" -> 1 -
 * "5minute" -> 5 - "15minute" -> 15 - "60minute" -> 60 - "daily" -> 1440
 *
 * Any other input MUST be rejected with an error.
 */
public final class Intervals {

	// Canonical codes in a stable order (exposed by /intervals and used in UI)
	private static final List<String> CANONICAL_CODES = Collections.unmodifiableList(Arrays.asList("1minute", "5minute", "15minute", "60minute", "daily"));

	// code -> minutes (canonical only)
	private static final Map<String, Integer> CODE_TO_MINUTES;

	// minutes -> code (canonical only)
	private static final Map<Integer, String> MINUTES_TO_CODE;

	static {
		Map<String, Integer> c2m = new HashMap<String, Integer>();
		c2m.put("1minute", 1);
		c2m.put("5minute", 5);
		c2m.put("15minute", 15);
		c2m.put("60minute", 60);
		c2m.put("daily", 1440);
		CODE_TO_MINUTES = Collections.unmodifiableMap(c2m);

		Map<Integer, String> m2c = new HashMap<Integer, String>();
		m2c.put(1, "1minute");
		m2c.put(5, "5minute");
		m2c.put(15, "15minute");
		m2c.put(60, "60minute");
		m2c.put(1440, "daily");
		MINUTES_TO_CODE = Collections.unmodifiableMap(m2c);
	}

	private Intervals() {
		// utility class
	}

	/**
	 * Convert a canonical interval code to minutes.
	 *
	 * @param interval one of: "1minute","5minute","15minute","60minute","daily"
	 * @return minutes
	 * @throws IllegalArgumentException if null/blank or not one of the canonical
	 *                                  codes
	 */
	public static int minutesOf(String interval) {
		if (interval == null || interval.trim().isEmpty()) {
			throw new IllegalArgumentException("Interval is null or blank");
		}
		String key = normalize(interval);
		Integer minutes = CODE_TO_MINUTES.get(key);
		if (minutes == null) {
			throw new IllegalArgumentException("Unsupported interval: " + interval + ". Allowed: " + CANONICAL_CODES);
		}
		return minutes.intValue();
	}

	/**
	 * Return true only if the given string is a canonical code.
	 */
	public static boolean isValid(String interval) {
		if (interval == null)
			return false;
		return CODE_TO_MINUTES.containsKey(normalize(interval));
	}

	/**
	 * Convert minutes to the canonical code.
	 *
	 * @throws IllegalArgumentException if minutes is not one of {1,5,15,60,1440}
	 */
	public static String canonicalOfMinutes(int minutes) {
		String code = MINUTES_TO_CODE.get(Integer.valueOf(minutes));
		if (code == null) {
			throw new IllegalArgumentException("Unsupported minutes: " + minutes + ". Allowed: " + MINUTES_TO_CODE.keySet());
		}
		return code;
	}

	/**
	 * List of canonical codes in a stable order. Safe to expose via /intervals.
	 */
	public static List<String> canonicalCodes() {
		return CANONICAL_CODES;
	}

	private static String normalize(String s) {
		return s.trim().toLowerCase();
	}
}
