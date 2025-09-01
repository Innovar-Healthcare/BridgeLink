package com.mirth.connect.plugins.messagetrends.server.dao.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;

import com.mirth.connect.plugins.messagetrends.server.dao.MessageStatisticsTimeseriesDao;
import com.mirth.connect.plugins.messagetrends.server.util.DatabaseDialect.DatabaseType;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

public class MyBatisMessageStatisticsTimeseriesDao implements MessageStatisticsTimeseriesDao {
	/** Mapper namespace to match our XML files. */
	private static final String NS = "MessageTrends";

	private final SqlSessionManager sqlSessionManager;
	private final DatabaseType dbType;

	public MyBatisMessageStatisticsTimeseriesDao(SqlSessionManager sqlSessionManager, DatabaseType dbType) {
		this.sqlSessionManager = sqlSessionManager;
		this.dbType = dbType;
	}

	@Override
	public int upsertMinuteDelta(MessageStatisticsTimeseries delta) {
		// Defensive normalization for channel-level rows
		if (delta.getConnectorId() == null) {
			delta.setConnectorId("");
		}

		if (delta.getBucketSizeMinutes() != 1) {
			throw new IllegalArgumentException("upsertMinuteDelta requires bucketSizeMinutes=1, but was " + delta.getBucketSizeMinutes());
		}

		SqlSession session = sqlSessionManager.openSession();

		boolean commitSuccess = false;
		try {
			int affected;
			if (dbType == DatabaseType.DERBY) {
				// Derby 2-step additive upsert
				affected = session.update(NS + ".updateAdditive", delta);
				if (affected == 0) {
					affected = session.insert(NS + ".insertNew", delta);
				}
			} else {
				// Single-statement additive upsert (DB-specific in mapper)
				affected = session.update(NS + ".upsertMinuteDelta", delta);
			}

			session.commit();
			commitSuccess = true;

			return affected;
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
	public int upsertRollupDelta(MessageStatisticsTimeseries delta) {
		if (delta.getConnectorId() == null) {
			delta.setConnectorId("");
		}
		SqlSession session = sqlSessionManager.openSession();
		boolean commitSuccess = false;
		try {
			int affected;
			if (dbType == DatabaseType.DERBY) {
				// Derby 2-step additive upsert for rollup buckets
				affected = session.update(NS + ".updateAdditive", delta);
				if (affected == 0) {
					affected = session.insert(NS + ".insertNew", delta);
				}
			} else {
				// Single-statement additive upsert (DB-specific in mapper)
				affected = session.update(NS + ".upsertRollupDelta", delta);
			}

			session.commit();
			commitSuccess = true;

			return affected;
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
