package com.mirth.connect.plugins.messagetrends.server.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

/**
 * Singleton in-memory buffer for accumulating per-commit deltas. -
 * Channel-level cache (channelId -> Status -> count) - Connector-level cache
 * (channelId -> connectorId -> Status -> count) - Snapshot+reset API for
 * periodic flush to DB
 */
public final class MessageTrendsBuffer {
	private volatile boolean enabled = true;

	private MessageTrendsBuffer() {
	}

	private static final class Holder {
		static final MessageTrendsBuffer I = new MessageTrendsBuffer();
	}

	public static MessageTrendsBuffer getInstance() {
		return Holder.I;
	}

	// --- Counters ---
	private final ConcurrentMap<String, ConcurrentMap<Status, AtomicInteger>> channelStats = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<Status, AtomicInteger>>> connectorStats = new ConcurrentHashMap<>();

	public synchronized void setEnabled(boolean enabled) {
		if (this.enabled != enabled) {
			if (enabled) {
				resetAll();
			}

			this.enabled = enabled;
		}
	}

	/**
	 * Bulk add from Donkey map: channelId -> connectorId(Integer or null) ->
	 * (Status -> delta).
	 */
	public void addFromDonkeyMap(Map<String, Map<Integer, Map<Status, Long>>> stats) {
		if (!enabled || stats == null || stats.isEmpty()) {
			return;
		}

		for (Map.Entry<String, Map<Integer, Map<Status, Long>>> ch : stats.entrySet()) {
			final String channelId = ch.getKey();
			final Map<Integer, Map<Status, Long>> perConn = ch.getValue();
			if (perConn == null || perConn.isEmpty()) {
				continue;
			}

			for (Map.Entry<Integer, Map<Status, Long>> ce : perConn.entrySet()) {
				final Integer connInt = ce.getKey();
				final String connectorId = (connInt == null) ? null : String.valueOf(connInt);
				final Map<Status, Long> deltas = ce.getValue();
				if (deltas == null || deltas.isEmpty()) {
					continue;
				}

				addDeltas(channelId, connectorId, deltas);
			}
		}
	}

	/** Add deltas for one (channelId, connectorId?). */
	public void addDeltas(String channelId, String connectorId, Map<Status, Long> deltas) {
		if (channelId == null || deltas == null || deltas.isEmpty()) {
			return;
		}

		// Channel-level
		final ConcurrentMap<Status, AtomicInteger> ch = channelStats.computeIfAbsent(channelId, k -> new ConcurrentHashMap<>());
		ch.computeIfAbsent(Status.RECEIVED, k -> new AtomicInteger()).addAndGet(safeToInt(deltas.getOrDefault(Status.RECEIVED, 0L)));
		ch.computeIfAbsent(Status.FILTERED, k -> new AtomicInteger()).addAndGet(safeToInt(deltas.getOrDefault(Status.FILTERED, 0L)));
		ch.computeIfAbsent(Status.QUEUED, k -> new AtomicInteger()).addAndGet(safeToInt(deltas.getOrDefault(Status.QUEUED, 0L)));
		ch.computeIfAbsent(Status.SENT, k -> new AtomicInteger()).addAndGet(safeToInt(deltas.getOrDefault(Status.SENT, 0L)));
		ch.computeIfAbsent(Status.ERROR, k -> new AtomicInteger()).addAndGet(safeToInt(deltas.getOrDefault(Status.ERROR, 0L)));

		// Connector-level (optional)
		if (connectorId != null) {
			final ConcurrentMap<String, ConcurrentMap<Status, AtomicInteger>> perConn = connectorStats.computeIfAbsent(channelId, k -> new ConcurrentHashMap<>());
			final ConcurrentMap<Status, AtomicInteger> m = perConn.computeIfAbsent(connectorId, k -> new ConcurrentHashMap<>());

			m.computeIfAbsent(Status.RECEIVED, k -> new AtomicInteger()).addAndGet(safeToInt(deltas.getOrDefault(Status.RECEIVED, 0L)));
			m.computeIfAbsent(Status.FILTERED, k -> new AtomicInteger()).addAndGet(safeToInt(deltas.getOrDefault(Status.FILTERED, 0L)));
			m.computeIfAbsent(Status.QUEUED, k -> new AtomicInteger()).addAndGet(safeToInt(deltas.getOrDefault(Status.QUEUED, 0L)));
			m.computeIfAbsent(Status.SENT, k -> new AtomicInteger()).addAndGet(safeToInt(deltas.getOrDefault(Status.SENT, 0L)));
			m.computeIfAbsent(Status.ERROR, k -> new AtomicInteger()).addAndGet(safeToInt(deltas.getOrDefault(Status.ERROR, 0L)));
		}
	}

	/** Snapshot current counters and reset to zero. */
	public List<MessageStatisticsTimeseries> snapshotAndReset(Date ts, int bucketMinutes, String serverId) {
		List<MessageStatisticsTimeseries> out = new ArrayList<>(256);

		// Channel-level rows
		for (Map.Entry<String, ConcurrentMap<Status, AtomicInteger>> ch : channelStats.entrySet()) {
			final String channelId = ch.getKey();
			final Map<Status, AtomicInteger> counters = ch.getValue();
			if (counters == null || counters.isEmpty()) {
				continue;
			}

			MessageStatisticsTimeseries row = new MessageStatisticsTimeseries(channelId, null, ts, bucketMinutes, serverId);
			row.setReceived(getAndZero(counters, Status.RECEIVED));
			row.setFiltered(getAndZero(counters, Status.FILTERED));
			row.setQueued(getAndZero(counters, Status.QUEUED));
			row.setSent(getAndZero(counters, Status.SENT));
			row.setError(getAndZero(counters, Status.ERROR));

			// Compact zero counters to keep memory usage low
			counters.entrySet().removeIf(e -> e.getValue().get() == 0);

			out.add(row);
		}

		// Connector-level rows
		for (Map.Entry<String, ConcurrentMap<String, ConcurrentMap<Status, AtomicInteger>>> ch : connectorStats.entrySet()) {
			final String channelId = ch.getKey();
			final Map<String, ConcurrentMap<Status, AtomicInteger>> perConn = ch.getValue();
			if (perConn == null || perConn.isEmpty()) {
				continue;
			}

			Iterator<Map.Entry<String, ConcurrentMap<Status, AtomicInteger>>> it = perConn.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, ConcurrentMap<Status, AtomicInteger>> ce = it.next();
				final String connectorId = ce.getKey();
				final Map<Status, AtomicInteger> counters = ce.getValue();
				if (counters == null || counters.isEmpty()) {
					it.remove();
					continue;
				}

				MessageStatisticsTimeseries row = new MessageStatisticsTimeseries(channelId, connectorId, ts, bucketMinutes, serverId);
				row.setReceived(getAndZero(counters, Status.RECEIVED));
				row.setFiltered(getAndZero(counters, Status.FILTERED));
				row.setQueued(getAndZero(counters, Status.QUEUED));
				row.setSent(getAndZero(counters, Status.SENT));
				row.setError(getAndZero(counters, Status.ERROR));

				counters.entrySet().removeIf(e -> e.getValue().get() == 0);
				if (counters.isEmpty()) {
					it.remove();
				}

				out.add(row);
			}

			if (perConn.isEmpty()) {
				connectorStats.remove(channelId, perConn);
			}
		}

		return out;
	}

	// --- Helpers ---

	private void resetAll() {
		channelStats.clear();
		connectorStats.clear();
	}

	private static int getAndZero(Map<Status, AtomicInteger> map, Status key) {
		AtomicInteger ai = map.get(key);
		return (ai == null) ? 0 : ai.getAndSet(0);
	}

	private static int safeToInt(long v) {
		return (v > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (v < Integer.MIN_VALUE) ? Integer.MIN_VALUE : (int) v;
	}
}
