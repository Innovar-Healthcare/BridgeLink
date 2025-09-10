package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.messagetrends.server.core.MessageTrendsBuffer;
import com.mirth.connect.plugins.messagetrends.server.service.MessageTrendsService;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

final class MinuteFlushRunner {
	private static final Logger logger = LogManager.getLogger(MinuteFlushRunner.class);
	private static final int FIXED_RATE_SECONDS = 60;

	private final MessageTrendsService service;
	private final MessageTrendsBuffer buffer = MessageTrendsBuffer.getInstance();

	private final Clock clock; // UTC
	private final String serverId;
	private Date lastFlushedTs = null;

	/** Master enable switch for this runner. */
	private volatile boolean enabled = true;

	MinuteFlushRunner(MessageTrendsService service, Clock clock, String serverId) {
		this.service = service;
		this.clock = clock == null ? Clock.systemUTC() : clock;
		this.serverId = serverId;
	}

	int getFixedRateSeconds() {
		return FIXED_RATE_SECONDS;
	}

	long initialDelaySecondsToNextMinuteBoundary() {
		Instant now = clock.instant();
		Instant next = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES);
		return Math.max(0, Duration.between(now, next).getSeconds());
	}

	/** Enable/disable the runner at runtime. */
	void setEnabled(boolean enabled) {
		if (this.enabled != enabled) {
			buffer.setEnabled(enabled);

			this.enabled = enabled;
		}

	}

	void runOnce() {
		if (!enabled) {
			return; // Runner is disabled; skip work.
		}

		try {
			Instant bucketStart = clock.instant().truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.MINUTES);
			Date ts = Date.from(bucketStart);

			if (lastFlushedTs != null && ts.equals(lastFlushedTs)) {
				return;
			}

			List<MessageStatisticsTimeseries> rows = buffer.snapshotAndReset(ts, 1, serverId);
			if (rows.isEmpty()) {
				return;
			}

			final Map<RollKey, MessageStatisticsTimeseries> grouped = new LinkedHashMap<>();
			for (MessageStatisticsTimeseries r : rows) {
				final String serverId = r.getServerId();
				final String channelId = r.getChannelId();
				final String connectorId = r.getConnectorId();
				final RollKey key = new RollKey(serverId, channelId, connectorId, ts, 1);

				MessageStatisticsTimeseries acc = grouped.get(key);
				if (acc == null) {
					acc = new MessageStatisticsTimeseries();
					acc.setServerId(serverId);
					acc.setChannelId(channelId);
					acc.setConnectorId(connectorId);
					acc.setTs(ts);
					acc.setBucketSizeMinutes(1);
					acc.setReceived(0);
					acc.setFiltered(0);
					acc.setQueued(0);
					acc.setSent(0);
					acc.setError(0);
					grouped.put(key, acc);
				}
				// sum metrics (null-safe)
				acc.setReceived(nz(acc.getReceived()) + nz(r.getReceived()));
				acc.setFiltered(nz(acc.getFiltered()) + nz(r.getFiltered()));
				acc.setQueued(nz(acc.getQueued()) + nz(r.getQueued()));
				acc.setSent(nz(acc.getSent()) + nz(r.getSent()));
				acc.setError(nz(acc.getError()) + nz(r.getError()));
			}

			final List<MessageStatisticsTimeseries> rowsToWrite = new ArrayList<>(grouped.values());

			if (rowsToWrite.isEmpty()) {
				return;
			}

			int inserted = service.replaceRollupWindow(ts, 1, rowsToWrite);

			lastFlushedTs = ts;

			logger.debug("MinuteFlushRunner: flushed {} rows for {}", inserted, ts);
		} catch (Throwable t) {
			logger.warn("MinuteFlushRunner runOnce failed", t);
		}
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
