/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

public class QueuedBackfillHelper {

	public interface QueueReader {
		long getQueuedCount(String channelId, String connectorId) throws Exception;
	}

	public static final class QKey {
		public final String serverId, channelId, connectorId;

		public QKey(String s, String c, String k) {
			this.serverId = s;
			this.channelId = c;
			this.connectorId = k;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}

			if (!(o instanceof QKey)) {
				return false;
			}

			QKey x = (QKey) o;
			return Objects.equals(serverId, x.serverId) && Objects.equals(channelId, x.channelId) && Objects.equals(connectorId, x.connectorId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(serverId, channelId, connectorId);
		}
	}

	private final QueueReader queueReader;

	/** Hot set: keys currently known to have queued>0 (kept ultra-simple). */
	private final Set<QKey> positiveQueued = ConcurrentHashMap.newKeySet();

	public QueuedBackfillHelper(QueueReader queueReader) {
		this.queueReader = Objects.requireNonNull(queueReader);
	}

	/** Call at the start of a “new minute cycle” if you want a fresh set. */
	public void resetPositiveSet() {
		positiveQueued.clear();
	}

	/**
	 * (1) Backfill QUEUED rows for keys with queued>0 that had no activity this
	 * minute.
	 */
	public void backfillMinute(Date ts, Map<RollKey, MessageStatisticsTimeseries> grouped) {
		if (positiveQueued.isEmpty()) {
			return;
		}

		// Snapshot to avoid concurrent modification interference
		List<QKey> snapshot = new ArrayList<>(positiveQueued);

		for (QKey qk : snapshot) {
			RollKey connKey = new RollKey(qk.serverId, qk.channelId, qk.connectorId, ts, 1);
			if (grouped.containsKey(connKey)) {
				// Already have a row from activity path; skip.
				continue;
			}

			MessageStatisticsTimeseries m = new MessageStatisticsTimeseries();
			m.setServerId(qk.serverId);
			m.setChannelId(qk.channelId);
			m.setConnectorId(qk.connectorId);
			m.setTs(ts);
			m.setBucketSizeMinutes(1);
			m.setReceived(0);
			m.setFiltered(0);
			m.setSent(0);
			m.setError(0);
			m.setQueued(readQueuedClamped(qk.channelId, qk.connectorId));

			grouped.put(connKey, m);
		}
	}

	/**
	 * (2) Update hot set after flush: keep keys with queued>0, remove otherwise.
	 */
	public void updateAfterFlush(Collection<MessageStatisticsTimeseries> flushedRows) {
		resetPositiveSet();

		if (flushedRows == null || flushedRows.isEmpty()) {
			return;
		}

		for (MessageStatisticsTimeseries r : flushedRows) {
			final String serverId = r.getServerId();
			final String channelId = r.getChannelId();
			final String connectorId = r.getConnectorId();

			QKey k = new QKey(serverId, channelId, connectorId);
			final int qi = (r.getQueued() == null) ? 0 : r.getQueued();

			if (qi > 0) {
				positiveQueued.add(k);
			}
		}
	}

	/* ---------- helpers ---------- */

	private int readQueuedClamped(String channelId, String connectorId) {
		long q = 0L;
		try {
			q = queueReader.getQueuedCount(channelId, connectorId);
		} catch (Exception ignore) {
			/* best effort; next minute can retry */
		}

		if (q <= 0) {
			return 0;
		}

		return (q > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) q;
	}
}
