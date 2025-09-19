/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.server.dao.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.messagetrends.server.dao.MessageStatisticsTimeseriesDao;
import com.mirth.connect.plugins.messagetrends.server.util.ConnectorIdNormalizer;
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

			final List<MessageStatisticsTimeseries> dbRows = new ArrayList<>(list.size());
			for (MessageStatisticsTimeseries r : list) {
				if (r.getChannelId() == null) {
					throw new IllegalArgumentException("channelId cannot be null for rollup rows");
				}
				if (r.getTs() == null || !r.getTs().equals(startTs)) {
					throw new IllegalArgumentException("Row ts mismatch: expected " + startTs + ", got " + r.getTs());
				}
				if (r.getBucketSizeMinutes() == null || r.getBucketSizeMinutes() != bucketSizeMinutes) {
					throw new IllegalArgumentException("Row bucket mismatch: expected " + bucketSizeMinutes + ", got " + r.getBucketSizeMinutes());
				}

				// shallow copy
				MessageStatisticsTimeseries c = new MessageStatisticsTimeseries(r);
				c.setConnectorId(ConnectorIdNormalizer.toDb(c.getConnectorId()));

				dbRows.add(c);
			}

			Map<String, Object> params = new HashMap<>();
			params.put("serverId", serverId);
			params.put("ts", startTs);
			params.put("bucketSizeMinutes", bucketSizeMinutes);

			int deleted = session.delete(NS + ".deleteRollupWindow", params);

			int inserted = 0;
			if (!dbRows.isEmpty()) {
				inserted = session.insert(NS + ".insertRollupRows", dbRows);
			}

			session.commit();
			commitSuccess = true;

			return inserted; // rows written
		} catch (Exception e) {
			logger.error("Failed to replace rollup window", e);
			debugPrintRows(list);
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
	public List<MessageStatisticsTimeseries> selectSeriesServerChannel(String serverId, String channelId, Date startTs, Date endTs, int bucketSizeMinutes) {
		return selectSeriesServerConnector(serverId, channelId, null, startTs, endTs, bucketSizeMinutes);
	}

	@Override
	public List<MessageStatisticsTimeseries> selectSeriesServerConnector(String serverId, String channelId, String connectorId, Date startTs, Date endTs, int bucketSizeMinutes) {

		SqlSession session = sqlSessionManager.openSession();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("serverId", serverId);
			params.put("channelId", channelId);
			params.put("connectorId", ConnectorIdNormalizer.toDb(connectorId));
			params.put("startTs", startTs);
			params.put("endTs", endTs);
			params.put("bucketSizeMinutes", bucketSizeMinutes);

			List<MessageStatisticsTimeseries> rows = session.selectList(NS + ".selectSeriesServerChannelConnector", params);

			for (MessageStatisticsTimeseries r : rows) {
				r.setConnectorId(ConnectorIdNormalizer.toApi(r.getConnectorId()));
			}

			return rows;
		} finally {
			session.close();
		}
	}

	@Override
	public List<MessageStatisticsTimeseries> selectSeriesServerAll(String serverId, Date startTs, Date endTs, int bucketSizeMinutes) {

		SqlSession session = sqlSessionManager.openSession();
		try {
			Map<String, Object> params = new HashMap<>();
			params.put("serverId", serverId);
			params.put("startTs", startTs);
			params.put("endTs", endTs);
			params.put("bucketSizeMinutes", bucketSizeMinutes);

			List<MessageStatisticsTimeseries> rows = session.selectList(NS + ".selectSeriesServerAll", params);

			for (MessageStatisticsTimeseries r : rows) {
				r.setConnectorId(ConnectorIdNormalizer.toApi(r.getConnectorId()));
			}

			return rows;
		} finally {
			session.close();
		}
	}

	private void debugPrintRows(List<MessageStatisticsTimeseries> list) {
		if (list == null || list.isEmpty()) {
			logger.debug("insertRollupRows: list is empty");
			return;
		}

		logger.debug("insertRollupRows: dumping {} rows", list.size());
		for (int i = 0; i < list.size(); i++) {
			MessageStatisticsTimeseries r = list.get(i);
			logger.debug("[{}] serverId={} channelId={} connectorId={} ts={} bucket={} " + "recv={} filt={} queue={} sent={} err={}", i, r.getServerId(), r.getChannelId(), r.getConnectorId(), r.getTs(), r.getBucketSizeMinutes(), r.getReceived(), r.getFiltered(), r.getQueued(), r.getSent(), r.getError());
		}
	}
}
