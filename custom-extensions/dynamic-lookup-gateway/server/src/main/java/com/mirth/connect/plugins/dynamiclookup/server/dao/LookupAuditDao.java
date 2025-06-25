package com.mirth.connect.plugins.dynamiclookup.server.dao;

import com.mirth.connect.plugins.dynamiclookup.shared.model.HistoryFilterState;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupAudit;

import java.util.List;

public interface LookupAuditDao {
    void insertAuditEntry(LookupAudit audit);

    List<LookupAudit> getAuditEntriesByGroup(int groupId, int offset, int limit);

    List<LookupAudit> searchAuditEntriesByGroup(int groupId, int offset, int limit, HistoryFilterState filter);

    List<LookupAudit> getAuditEntriesByKey(int groupId, String keyValue, int limit);

    long getAuditEntryCount(int groupId);

    long searchAuditEntryCount(int groupId, HistoryFilterState filter);
}
