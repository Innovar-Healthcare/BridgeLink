package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.messagetrends.server.core.MessageTrendsBuffer;
import com.mirth.connect.plugins.messagetrends.server.service.MessageTrendsService;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

final class MinuteFlushRunner {
	private static final Logger log = LogManager.getLogger(MinuteFlushRunner.class);

	private final MessageTrendsService service;
	private final MessageTrendsBuffer buffer = MessageTrendsBuffer.getInstance();

	private final Clock clock; // UTC
	private final int sweepIntervalSeconds; // 5–10s
	private final String serverId;

	/** Master enable switch for this runner. */
	private volatile boolean enabled = true;

	MinuteFlushRunner(MessageTrendsService service, Clock clock, int sweepIntervalSeconds, String serverId) {
		this.service = service;
		this.clock = clock == null ? Clock.systemUTC() : clock;
		this.sweepIntervalSeconds = Math.max(1, sweepIntervalSeconds);
		this.serverId = serverId;
	}

	int getSweepIntervalSeconds() {
		return sweepIntervalSeconds;
	}

	long initialDelaySecondsToNextMinuteBoundary() {
		Instant now = clock.instant();
		Instant next = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES);
		return Math.max(0, Duration.between(now, next).getSeconds());
	}

	/** Enable/disable the runner at runtime. */
	void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	void runOnce() {
		if (!enabled) {
			return; // Runner is disabled; skip work.
		}

		try {
			Instant now = clock.instant();
			Instant bucketStart = now.truncatedTo(ChronoUnit.MINUTES); // minute boundary UTC
			List<MessageStatisticsTimeseries> rows = buffer.snapshotAndReset(bucketStart, /* bucket= */1, serverId, ZoneId.of("UTC"));
			if (rows.isEmpty())
				return;

			int ok = 0;
			for (MessageStatisticsTimeseries m : rows) {
				try {
					service.upsertMinuteDelta(m);
					ok++;
				} catch (Throwable t) {
					log.warn("MinuteFlushRunner upsert failed for channel={} connector={} ts={}", m.getChannelId(), m.getConnectorId(), m.getTs(), t);
				}
			}
			log.debug("MinuteFlushRunner: flushed {} deltas at {}", ok, bucketStart);
		} catch (Throwable t) {
			log.warn("MinuteFlushRunner runOnce failed", t);
		}
	}
}
