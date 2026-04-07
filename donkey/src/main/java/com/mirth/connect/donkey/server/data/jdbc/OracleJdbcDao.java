/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.data.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.mirth.connect.donkey.model.message.MessageContent;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.channel.Statistics;
import com.mirth.connect.donkey.server.data.StatisticsUpdater;
import com.mirth.connect.donkey.util.SerializerProvider;

public class OracleJdbcDao extends JdbcDao {

    public OracleJdbcDao(Donkey donkey, Connection connection, QuerySource querySource, PreparedStatementSource statementSource, SerializerProvider serializerProvider, boolean encryptMessageContent, boolean encryptAttachments, boolean encryptCustomMetaData, boolean decryptData, StatisticsUpdater statisticsUpdater, Statistics currentStats, Statistics totalStats, String statsServerId) {
        super(donkey, connection, querySource, statementSource, serializerProvider, encryptMessageContent, encryptAttachments, encryptCustomMetaData, decryptData, statisticsUpdater, currentStats, totalStats, statsServerId);
    }

    @Override
    protected void closeDatabaseObjectIfNeeded(AutoCloseable dbObject) {
        if (dbObject instanceof Statement) {
            close((Statement) dbObject);
        } else if (dbObject instanceof ResultSet) {
            close((ResultSet) dbObject);
        }
    }

    @Override
    protected void closeDatabaseObjectsIfNeeded(List<AutoCloseable> dbObjects) {
        for (AutoCloseable obj : dbObjects) {
            closeDatabaseObjectIfNeeded(obj);
        }
    }

    /**
     * ojdbc11+ serializes setBoolean() as "true"/"false" text for CHAR columns, which violates the
     * CHAR(1) constraint. Use setInt(0/1) instead so Oracle stores '0' or '1' as expected.
     */
    @Override
    protected void setDbBoolean(PreparedStatement statement, int parameterIndex, boolean value) throws SQLException {
        statement.setInt(parameterIndex, value ? 1 : 0);
    }

    /**
     * Oracle's closeDatabaseObjectIfNeeded always closes statements, which destroys batched data
     * between addBatch() and executeBatch() calls. Override to use individual inserts instead.
     */
    @Override
    public void batchInsertMessageContent(MessageContent messageContent) {
        insertMessageContent(messageContent);
    }

    @Override
    public void executeBatchInsertMessageContent(String channelId) {
        // No-op: content was already inserted individually in batchInsertMessageContent
    }

}
