package com.mirth.connect.plugins.messagetrends.server.service;

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.messagetrends.server.dao.MessageStatisticsTimeseriesDao;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;
import com.mirth.connect.server.controllers.ControllerFactory;

public class MessageTrendsService {
	private static MessageTrendsService instance = null;
	private MessageStatisticsTimeseriesDao dao;
	private String serverId;
	private final Logger logger = LogManager.getLogger(this.getClass());

	public static MessageTrendsService getInstance() {
		synchronized (MessageTrendsService.class) {
			if (instance == null) {
				instance = new MessageTrendsService();
			}
			return instance;
		}
	}

	private MessageTrendsService() {
	}

	public void init(MessageStatisticsTimeseriesDao dao) {
		this.dao = dao;
		this.serverId = ControllerFactory.getFactory().createConfigurationController().getServerId();
	}

	private void ensureInit() {
		if (dao == null || serverId == null) {
			throw new IllegalStateException("MessageTrendsService not initialized with DAO + serverId");
		}
	}

	/** Upsert 1-minute delta row (raw write path). */
	public int upsertMinuteDelta(MessageStatisticsTimeseries delta) {
		ensureInit();
		try {
			delta.setServerId(serverId);
			return dao.upsertMinuteDelta(delta);
		} catch (Exception e) {
			logger.warn("Failed to upsert minute delta", e);
			return 0;
		}
	}

	/** Upsert rollup deltas */
	public int upsertRollupDelta(MessageStatisticsTimeseries delta) {
		ensureInit();
		try {
			delta.setServerId(serverId);
			return dao.upsertRollupDelta(delta);
		} catch (Exception e) {
			logger.warn("Failed to upsert rollup delta", e);
			return 0;
		}
	}

	/** Replace Rollup Window */
	public int replaceRollupWindow(Date startTs, int bucketSizeMinutes, List<MessageStatisticsTimeseries> list) {
		ensureInit();
		try {
			return dao.replaceRollupWindow(startTs, bucketSizeMinutes, list);
		} catch (Exception e) {
			logger.warn("Failed to replace rollup window", e);
			return 0;
		}
	}

	/** Query statistics for a specific channel (all connectors collapsed). */
	public List<MessageStatisticsTimeseries> getChannelSeries(String channelId, Date startTs, Date endTs, int bucketSizeMinutes) {
		ensureInit();
		return dao.selectSeriesServerChannel(serverId, channelId, startTs, endTs, bucketSizeMinutes);
	}

	/** Query statistics for a specific connector. */
	public List<MessageStatisticsTimeseries> getConnectorSeries(String channelId, String connectorId, Date startTs, Date endTs, int bucketSizeMinutes) {
		ensureInit();
		return dao.selectSeriesServerConnector(serverId, channelId, connectorId, startTs, endTs, bucketSizeMinutes);
	}

	/** Query server-wide statistics (all channels). */
	public List<MessageStatisticsTimeseries> getServerSeries(Date startTs, Date endTs, int bucketSizeMinutes) {
		ensureInit();
		return dao.selectSeriesServerAll(serverId, startTs, endTs, bucketSizeMinutes);
	}

	/** Purge old statistics for a given bucket. */
	public int purgeBeforeByBucket(int bucketSizeMinutes, Date cutoffTs) {
		ensureInit();

		return dao.purgeBefore(serverId, bucketSizeMinutes, cutoffTs);
	}
}
