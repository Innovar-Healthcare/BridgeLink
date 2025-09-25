/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.client.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class RangeTextFormatter {
	private static final SimpleDateFormat POINT_FMT = new SimpleDateFormat("yyyy/MM/dd HH:mm");
	private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy/MM/dd");
	private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm");
	private static final SimpleDateFormat FULL_FMT = new SimpleDateFormat("yyyy/MM/dd HH:mm");

	public static String formatPoint(Date ts) {
		return (ts != null) ? POINT_FMT.format(ts) : "—";
	}

	public static String formatRange(Long startMs, Long endMs) {
		if (startMs == null || endMs == null) {
			return "(no range)";
		}

		Date s = new Date(startMs);
		Date e = new Date(endMs);

		boolean sameDay = DATE_FMT.format(s).equals(DATE_FMT.format(e));

		if (sameDay) {
			return DATE_FMT.format(s) + " " + TIME_FMT.format(s) + " → " + TIME_FMT.format(e);
		} else {
			return FULL_FMT.format(s) + " → " + FULL_FMT.format(e);
		}
	}
}
