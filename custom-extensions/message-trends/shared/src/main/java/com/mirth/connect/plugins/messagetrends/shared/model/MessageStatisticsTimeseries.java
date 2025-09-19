/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.shared.model;

import java.util.Date;
import java.util.Objects;

public class MessageStatisticsTimeseries {
	private Long id;
	private String channelId;
	private String connectorId;
	private Date ts;
	private Integer bucketSizeMinutes;

	private Integer received;
	private Integer filtered;
	private Integer queued;
	private Integer sent;
	private Integer error;

	private String serverId;

	public MessageStatisticsTimeseries() {
	}

	public MessageStatisticsTimeseries(MessageStatisticsTimeseries other) {
		if (other == null) {
			throw new IllegalArgumentException("Source MessageStatisticsTimeseries cannot be null");
		}
		this.id = other.getId();
		this.serverId = other.getServerId();
		this.channelId = other.getChannelId();
		this.connectorId = other.getConnectorId();
		this.ts = other.getTs() == null ? null : new Date(other.getTs().getTime());
		this.bucketSizeMinutes = other.getBucketSizeMinutes();
		this.received = other.getReceived();
		this.filtered = other.getFiltered();
		this.queued = other.getQueued();
		this.sent = other.getSent();
		this.error = other.getError();
	}

	public MessageStatisticsTimeseries(String channelId, String connectorId, Date ts, int bucketSizeMinutes, String serverId) {
		this.channelId = channelId;
		this.connectorId = connectorId;
		this.ts = ts;
		this.bucketSizeMinutes = bucketSizeMinutes;
		this.serverId = serverId;

		normalize();
	}

	/**
	 * Normalize connectorId so it is never null (empty string if channel-level).
	 */
	public void normalize() {
		if (connectorId == null) {
			connectorId = "";
		}
	}

	// --- Getters and Setters ---

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getChannelId() {
		return channelId;
	}

	public void setChannelId(String channelId) {
		this.channelId = channelId;
	}

	public String getConnectorId() {
		return connectorId;
	}

	public void setConnectorId(String connectorId) {
		this.connectorId = connectorId;
	}

	public Date getTs() {
		return ts;
	}

	public void setTs(Date ts) {
		this.ts = ts;
	}

	public Integer getBucketSizeMinutes() {
		return bucketSizeMinutes;
	}

	public void setBucketSizeMinutes(Integer bucketSizeMinutes) {
		this.bucketSizeMinutes = bucketSizeMinutes;
	}

	public Integer getReceived() {
		return received;
	}

	public void setReceived(Integer received) {
		this.received = received;
	}

	public Integer getFiltered() {
		return filtered;
	}

	public void setFiltered(Integer filtered) {
		this.filtered = filtered;
	}

	public Integer getQueued() {
		return queued;
	}

	public void setQueued(Integer queued) {
		this.queued = queued;
	}

	public Integer getSent() {
		return sent;
	}

	public void setSent(Integer sent) {
		this.sent = sent;
	}

	public Integer getError() {
		return error;
	}

	public void setError(Integer error) {
		this.error = error;
	}

	public String getServerId() {
		return serverId;
	}

	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	// --- Builder (useful for tests and initialization) ---
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private final MessageStatisticsTimeseries m = new MessageStatisticsTimeseries();

		public Builder id(Long v) {
			m.id = v;
			return this;
		}

		public Builder channelId(String v) {
			m.channelId = v;
			return this;
		}

		public Builder connectorId(String v) {
			m.connectorId = v;
			return this;
		}

		public Builder ts(Date v) {
			m.ts = v;
			return this;
		}

		public Builder bucket(int v) {
			m.bucketSizeMinutes = v;
			return this;
		}

		public Builder received(int v) {
			m.received = v;
			return this;
		}

		public Builder filtered(int v) {
			m.filtered = v;
			return this;
		}

		public Builder queued(int v) {
			m.queued = v;
			return this;
		}

		public Builder sent(int v) {
			m.sent = v;
			return this;
		}

		public Builder error(int v) {
			m.error = v;
			return this;
		}

		public Builder serverId(String v) {
			m.serverId = v;
			return this;
		}

		public MessageStatisticsTimeseries build() {
			m.normalize();
			return m;
		}
	}

	// --- equals/hashCode based on logical key ---
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof MessageStatisticsTimeseries)) {
			return false;
		}

		MessageStatisticsTimeseries that = (MessageStatisticsTimeseries) o;
		return Objects.equals(serverId, that.serverId) && Objects.equals(channelId, that.channelId) && Objects.equals(connectorId, that.connectorId) && Objects.equals(ts, that.ts) && Objects.equals(bucketSizeMinutes, that.bucketSizeMinutes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(serverId, channelId, connectorId, ts, bucketSizeMinutes);
	}

	@Override
	public String toString() {
		return "MessageStatisticsTimeseries{" + "id=" + id + ", serverId='" + serverId + '\'' + ", channelId='" + channelId + '\'' + ", connectorId='" + connectorId + '\'' + ", ts=" + ts + ", bucketSizeMinutes=" + bucketSizeMinutes + ", received=" + received + ", filtered=" + filtered + ", queued=" + queued + ", sent=" + sent + ", error=" + error + '}';
	}
}
