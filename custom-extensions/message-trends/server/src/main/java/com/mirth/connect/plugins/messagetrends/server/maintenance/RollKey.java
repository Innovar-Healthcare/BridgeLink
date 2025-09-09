package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.util.Date;
import java.util.Objects;

/**
 * Composite key for identifying a unique rollup window row. Uniqueness is
 * defined by (serverId, channelId, connectorId, bucketTs, bucketSizeMinutes).
 */
public final class RollKey {

	private final String serverId;
	private final String channelId;
	private final String connectorId; // may be null for channel-level
	private final Date bucketTs; // bucket boundary timestamp
	private final int bucketSizeMinutes;

	public RollKey(String serverId, String channelId, String connectorId, Date bucketTs, int bucketSizeMinutes) {
		this.serverId = serverId;
		this.channelId = channelId;
		this.connectorId = connectorId;
		this.bucketTs = bucketTs;
		this.bucketSizeMinutes = bucketSizeMinutes;
	}

	public String getServerId() {
		return serverId;
	}

	public String getChannelId() {
		return channelId;
	}

	public String getConnectorId() {
		return connectorId;
	}

	public Date getBucketTs() {
		return bucketTs;
	}

	public int getBucketSizeMinutes() {
		return bucketSizeMinutes;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof RollKey))
			return false;
		RollKey x = (RollKey) o;
		return bucketSizeMinutes == x.bucketSizeMinutes && Objects.equals(serverId, x.serverId) && Objects.equals(channelId, x.channelId) && Objects.equals(connectorId, x.connectorId) && Objects.equals(bucketTs, x.bucketTs);
	}

	@Override
	public int hashCode() {
		return Objects.hash(serverId, channelId, connectorId, bucketTs, bucketSizeMinutes);
	}

	@Override
	public String toString() {
		return "RollKey{" + "serverId='" + serverId + '\'' + ", channelId='" + channelId + '\'' + ", connectorId='" + connectorId + '\'' + ", bucketTs=" + bucketTs + ", bucketSizeMinutes=" + bucketSizeMinutes + '}';
	}
}
