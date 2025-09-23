package com.mirth.connect.server.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.mirth.connect.model.util.MigrationException;

import com.mirth.connect.model.Channel;
import com.mirth.connect.donkey.model.channel.MetaDataColumn;

public class Migrate4_6_0 extends Migrator {
    @Override
    public void migrate() throws MigrationException {

        // Custom migration: update mapping names in serialized Channel objects
        Connection connection = null;
        Statement selectStatement = null;
        java.sql.PreparedStatement updateStatement = null;
        java.sql.ResultSet resultSet = null;
        com.mirth.connect.model.converters.ObjectXMLSerializer serializer = com.mirth.connect.model.converters.ObjectXMLSerializer.getInstance();
        try {
            connection = getConnection();
            selectStatement = connection.createStatement();
            resultSet = selectStatement.executeQuery("SELECT ID, CHANNEL FROM CHANNEL");
            while (resultSet.next()) {
                String id = resultSet.getString(1);
                String serializedData = resultSet.getString(2);
                Object obj = serializer.deserialize(serializedData, com.mirth.connect.model.Channel.class);
                com.mirth.connect.model.Channel channel = (com.mirth.connect.model.Channel) obj;
                boolean changed = false;
                if (channel.getProperties() != null && channel.getProperties().getMetaDataColumns() != null) {
                    for (com.mirth.connect.donkey.model.channel.MetaDataColumn col : channel.getProperties().getMetaDataColumns()) {
                        if ("mirth_type".equals(col.getMappingName())) {
                            col.setMappingName("message_type");
                            changed = true;
                        } else if ("mirth_source".equals(col.getMappingName())) {
                            col.setMappingName("message_source");
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    String migratedData = serializer.serialize(channel);
                    updateStatement = connection.prepareStatement("UPDATE CHANNEL SET CHANNEL = ? WHERE ID = ?");
                    updateStatement.setString(1, migratedData);
                    updateStatement.setString(2, id);
                    updateStatement.executeUpdate();
                    updateStatement.close();
                }
            }
        } catch (Exception e) {
            throw new MigrationException(e);
        } finally {
            org.apache.commons.dbutils.DbUtils.closeQuietly(resultSet);
            org.apache.commons.dbutils.DbUtils.closeQuietly(selectStatement);
            org.apache.commons.dbutils.DbUtils.closeQuietly(updateStatement);
        }
    }

    @Override
    public void migrateSerializedData() throws MigrationException {
    }
}
