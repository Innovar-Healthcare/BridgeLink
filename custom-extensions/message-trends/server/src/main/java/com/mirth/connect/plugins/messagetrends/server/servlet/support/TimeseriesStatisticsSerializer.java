package com.mirth.connect.plugins.messagetrends.server.servlet.support;

import java.util.List;

import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;
import com.mirth.connect.plugins.messagetrends.shared.util.JsonUtils;

/**
 * Stateless JSON serializer for MessageStatisticsTimeseries lists. Used by all
 * three endpoints (channel / connector / server) to return a JSON array.
 *
 * Contract (each array element mirrors MessageStatisticsTimeseries fields): -
 * id (Long) - channelId (String) - connectorId (String, nullable) - serverId
 * (String) - ts (Date -> ISO-8601 via JsonUtils config) - bucketSizeMinutes
 * (Integer) - received, filtered, queued, sent, error (Integer)
 */
public final class TimeseriesStatisticsSerializer {

	private TimeseriesStatisticsSerializer() {
		// Utility class: no instances
	}

	/**
	 * Serialize a list into a compact JSON array string. Returns "[]" for null or
	 * empty lists.
	 */
	public static String toJsonFromEntities(List<MessageStatisticsTimeseries> entities) {
		if (entities == null || entities.isEmpty()) {
			return "[]";
		}
		try {
			// JsonUtils is configured to write ISO-8601 for Date and not timestamps
			return JsonUtils.toJson(entities);
		} catch (Exception e) {
			throw new RuntimeException("Failed to serialize statistics list to JSON", e);
		}
	}

	/**
	 * Serialize a list into a pretty-printed JSON array string (useful for
	 * logs/debug). Returns "[]" for null or empty lists.
	 */
	public static String toJsonPrettyFromEntities(List<MessageStatisticsTimeseries> entities) {
		if (entities == null || entities.isEmpty()) {
			return "[]";
		}
		try {
			return JsonUtils.toJsonPretty(entities);
		} catch (Exception e) {
			throw new RuntimeException("Failed to serialize statistics list to pretty JSON", e);
		}
	}
}
