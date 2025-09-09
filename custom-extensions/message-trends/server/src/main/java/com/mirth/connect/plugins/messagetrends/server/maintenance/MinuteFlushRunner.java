package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.messagetrends.server.core.MessageTrendsBuffer;
import com.mirth.connect.plugins.messagetrends.server.service.MessageTrendsService;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

final class MinuteFlushRunner {
	private static final Logger log = LogManager.getLogger(MinuteFlushRunner.class);
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
			lastFlushedTs = ts;

			List<MessageStatisticsTimeseries> rows = buffer.snapshotAndReset(ts, 1, serverId);
			if (rows.isEmpty()) {
				return;
			}

			int inserted = service.replaceRollupWindow(ts, 1, rows);

			log.debug("MinuteFlushRunner: flushed {} rows for {}", inserted, bucketStart);
		} catch (Throwable t) {
			log.warn("MinuteFlushRunner runOnce failed", t);
		}
	}
}
