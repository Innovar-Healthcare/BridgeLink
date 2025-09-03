package com.mirth.connect.plugins.messagetrends.server.dao;

import java.util.Date;
import java.util.List;

import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

public interface MessageStatisticsTimeseriesDao {
	/**
	 * Additive upsert for 1-minute bucket.
	 */
	int upsertMinuteDelta(MessageStatisticsTimeseries delta);

	/**
	 * Additive upsert for rollup buckets (5, 15, 60, 1440).
	 */
	int upsertRollupDelta(MessageStatisticsTimeseries delta);

	/**
	 * Additive replace Rollup Window for buckets (5, 15, 60, 1440).
	 */
	int replaceRollupWindow(Date startTs, int bucketSizeMinutes, List<MessageStatisticsTimeseries> list);

	/**
	 * Purge old records for a given server and bucket size before cutoff.
	 */
	int purgeBefore(String serverId, int bucketSizeMinutes, Date cutoffTs);

	// --- Read APIs mapped to REST ---

	/**
	 * View #1: Channel-level statistics (aggregate all connectors in channel on
	 * this server).
	 */
	List<MessageStatisticsTimeseries> selectSeriesServerChannel(String serverId, String channelId, Date startTs, Date endTs, int intervalMinutes);

	/**
	 * View #2: Connector-level statistics (specific connector of channel on this
	 * server).
	 */
	List<MessageStatisticsTimeseries> selectSeriesServerConnector(String serverId, String channelId, String connectorId, Date startTs, Date endTs, int intervalMinutes);

	/**
	 * View #3: Server-level statistics (aggregate all channels and connectors on
	 * this server).
	 */
	List<MessageStatisticsTimeseries> selectSeriesServerAll(String serverId, Date startTs, Date endTs, int intervalMinutes);
}
