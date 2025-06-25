package com.mirth.connect.plugins.dynamiclookup.server.dao;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupStatistics;

import java.util.List;

public interface LookupStatisticsDao {
    void insertStatistics(int groupId);

    void updateStatistics(int groupId, boolean cacheHit);

    LookupStatistics getStatistics(int groupId);

    void resetStatistics(int groupId);

    List<LookupStatistics> getAllStatistics();
}
