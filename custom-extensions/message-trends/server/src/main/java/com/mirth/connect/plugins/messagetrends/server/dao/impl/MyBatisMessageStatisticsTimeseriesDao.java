package com.mirth.connect.plugins.messagetrends.server.dao.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.messagetrends.server.dao.MessageStatisticsTimeseriesDao;
import com.mirth.connect.plugins.messagetrends.server.util.DatabaseDialect.DatabaseType;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

public class MyBatisMessageStatisticsTimeseriesDao implements MessageStatisticsTimeseriesDao {
	private static final Logger logger = LogManager.getLogger(MyBatisMessageStatisticsTimeseriesDao.class);

	/** Mapper namespace to match our XML files. */
	private static final String NS = "MessageTrends";

	private final SqlSessionManager sqlSessionManager;
	private final DatabaseType dbType;

	public MyBatisMessageStatisticsTimeseriesDao(SqlSessionManager sqlSessionManager, DatabaseType dbType) {
		this.sqlSessionManager = sqlSessionManager;
		this.dbType = dbType;
	}

	@Override
	public int replaceRollupWindow(String serverId, Date startTs, int bucketSizeMinutes, List<MessageStatisticsTimeseries> list) {
		SqlSession session = sqlSessionManager.openSession();

		boolean commitSuccess = false;
		try {
			if (serverId == null) {
				throw new IllegalArgumentException("serverId cannot be null");
			}

			if (startTs == null) {
				throw new IllegalArgumentException("startTs cannot be null");
			}

			if (list == null) {
				throw new IllegalArgumentException("List of rollup rows cannot be null");
			}

			// Defensive checks: ensure all rows are consistent
			for (MessageStatisticsTimeseries row : list) {
				if (row.getTs() == null || !row.getTs().equals(startTs)) {
					throw new IllegalArgumentException("Row ts mismatch: expected " + startTs + ", got " + row.getTs());
				}
				if (row.getBucketSizeMinutes() == null || row.getBucketSizeMinutes() != bucketSizeMinutes) {
					throw new IllegalArgumentException("Row bucket mismatch: expected " + bucketSizeMinutes + ", got " + row.getBucketSizeMinutes());
				}
				if (row.getChannelId() == null) {
					row.setChannelId("");
				}
				if (row.getConnectorId() == null) {
					row.setConnectorId("");
				}
			}

			Map<String, Object> params = new HashMap<>();
			params.put("serverId", serverId);
			params.put("ts", startTs);
			params.put("bucketSizeMinutes", bucketSizeMinutes);

			int deleted = session.delete(NS + ".deleteRollupWindow", params);

			int inserted = 0;
			if (!list.isEmpty()) {
				inserted = session.insert(NS + ".insertRollupRows", list);
			}

			session.commit();
			commitSuccess = true;

			return inserted; // rows written
		} catch (Exception e) {
			logger.error("Failed to replace rollup window", e);
			return 0;
		} finally {
			if (!commitSuccess) {
				try {
					session.rollback();
				} catch (Exception ignored) {
				}
			}
			session.close();
		}
	}

	@Override
	public int purgeBefore(String serverId, int bucketSizeMinutes, Date cutoffTs) {
		SqlSession session = sqlSessionManager.openSession();
		boolean commitSuccess = false;
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("serverId", serverId);
			params.put("bucketSizeMinutes", bucketSizeMinutes);
			params.put("cutoffTs", cutoffTs);

			int deleted = session.delete(NS + ".purgeBefore", params);
			session.commit();
			commitSuccess = true;
			return deleted;
		} finally {
			if (!commitSuccess) {
				try {
					session.rollback();
				} catch (Exception ignored) {
				}
			}
			session.close();
		}
	}

	// ---------------------------------------------------------------------
	// Read APIs (single-node views) — open/close only
	// ---------------------------------------------------------------------

	@Override
	public List<MessageStatisticsTimeseries> selectSeriesServerChannel(String serverId, String channelId, Date startTs, Date endTs, int intervalMinutes) {

		SqlSession session = sqlSessionManager.openSession();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("serverId", serverId);
			params.put("channelId", channelId);
			params.put("startTs", startTs);
			params.put("endTs", endTs);
			params.put("intervalMinutes", intervalMinutes);

			return session.selectList(NS + ".selectSeriesServerChannel", params);
		} finally {
			session.close();
		}
	}

	@Override
	public List<MessageStatisticsTimeseries> selectSeriesServerConnector(String serverId, String channelId, String connectorId, Date startTs, Date endTs, int intervalMinutes) {

		SqlSession session = sqlSessionManager.openSession();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("serverId", serverId);
			params.put("channelId", channelId);
			params.put("connectorId", connectorId == null ? "" : connectorId);
			params.put("startTs", startTs);
			params.put("endTs", endTs);
			params.put("intervalMinutes", intervalMinutes);

			return session.selectList(NS + ".selectSeriesServerConnector", params);
		} finally {
			session.close();
		}
	}

	@Override
	public List<MessageStatisticsTimeseries> selectSeriesServerAll(String serverId, Date startTs, Date endTs, int intervalMinutes) {

		SqlSession session = sqlSessionManager.openSession();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("serverId", serverId);
			params.put("startTs", startTs);
			params.put("endTs", endTs);
			params.put("intervalMinutes", intervalMinutes);

			return session.selectList(NS + ".selectSeriesServerAll", params);
		} finally {
			session.close();
		}
	}
}
