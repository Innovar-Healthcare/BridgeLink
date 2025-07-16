/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

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
