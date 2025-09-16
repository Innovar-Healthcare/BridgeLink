package com.mirth.connect.plugins.messagetrends.server.util;

/**
 * Normalizes connectorId for DB and API/UI.
 *
 * Convention: - Channel-level in DB uses "__EMPTY__" (NOT NULL across all DBs).
 * - Channel-level in API/UI uses "" (empty string).
 */
public final class ConnectorIdNormalizer {

	/** Sentinel stored in DB for channel-level rows. */
	public static final String EMPTY_CONNECTOR = "__EMPTY__";

	private ConnectorIdNormalizer() {
		// no instance
	}

	/** Normalize a connectorId going INTO the database. null/"" -> "__EMPTY__". */
	public static String toDb(String connectorId) {
		return (connectorId == null || connectorId.isEmpty()) ? EMPTY_CONNECTOR : connectorId;
	}

	/**
	 * Normalize a connectorId coming OUT to API/UI. "__EMPTY__" -> "" (else
	 * passthrough incl. null).
	 */
	public static String toApi(String connectorIdFromDb) {
		if (connectorIdFromDb == null) {
			return null; // keep null if caller expects nullable
		}

		return EMPTY_CONNECTOR.equals(connectorIdFromDb) ? "" : connectorIdFromDb;
	}

	/**
	 * Quick check: is the given connectorId (any side) representing channel-level?
	 */
	public static boolean isChannelLevel(String connectorId) {
		return connectorId == null || connectorId.isEmpty() || EMPTY_CONNECTOR.equals(connectorId);
	}
}
