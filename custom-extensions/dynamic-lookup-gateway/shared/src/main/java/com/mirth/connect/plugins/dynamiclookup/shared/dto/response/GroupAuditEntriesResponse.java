/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.dto.response;

import com.mirth.connect.model.User;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupAudit;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GroupAuditEntriesResponse {
    private int groupId;
    private int totalEntries;
    private List<AuditEntryResponse> entries;
    private Pagination pagination;

    public static GroupAuditEntriesResponse fromResult(
            int groupId,
            List<LookupAudit> rawEntries,
            int total,
            int limit,
            int offset,
            List<User> users
    ) {
        Map<String, String> userIdToName = users.stream()
                .collect(Collectors.toMap(
                        user -> String.valueOf(user.getId()),
                        User::getUsername
                ));

        // Ensure system user is included
        userIdToName.putIfAbsent("0", "System");

        List<AuditEntryResponse> enrichedEntries = rawEntries.stream()
                .map(audit -> {
                    AuditEntryResponse dto = new AuditEntryResponse();
                    dto.setId(audit.getId());
                    dto.setGroupId(audit.getGroupId());
                    dto.setKeyValue(audit.getKeyValue());
                    dto.setAction(audit.getAction());
                    dto.setOldValue(audit.getOldValue());
                    dto.setNewValue(audit.getNewValue());
                    dto.setTimestamp(audit.getTimestamp());
                    dto.setUserName(userIdToName.getOrDefault(audit.getUserId(), audit.getUserId()));
                    return dto;
                })
                .collect(Collectors.toList());

        GroupAuditEntriesResponse response = new GroupAuditEntriesResponse();
        response.groupId = groupId;
        response.totalEntries = total;
        response.entries = enrichedEntries;

        Pagination pagination = new Pagination();
        pagination.setLimit(limit);
        pagination.setOffset(offset);
        pagination.setHasMore((offset + enrichedEntries.size()) < total);
        response.pagination = pagination;

        return response;
    }

    // Getters and setters...

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public int getTotalEntries() {
        return totalEntries;
    }

    public void setTotalEntries(int totalEntries) {
        this.totalEntries = totalEntries;
    }

    public List<AuditEntryResponse> getEntries() {
        return entries;
    }

    public void setEntries(List<AuditEntryResponse> entries) {
        this.entries = entries;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }

    // Inner class: AuditEntryResponse
    public static class AuditEntryResponse {
        private long id;
        private int groupId;
        private String keyValue;
        private String action;
        private String oldValue;
        private String newValue;
        private String userName;
        private Date timestamp;

        // Getters and setters...

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public int getGroupId() {
            return groupId;
        }

        public void setGroupId(int groupId) {
            this.groupId = groupId;
        }

        public String getKeyValue() {
            return keyValue;
        }

        public void setKeyValue(String keyValue) {
            this.keyValue = keyValue;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getOldValue() {
            return oldValue;
        }

        public void setOldValue(String oldValue) {
            this.oldValue = oldValue;
        }

        public String getNewValue() {
            return newValue;
        }

        public void setNewValue(String newValue) {
            this.newValue = newValue;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }
    }

    // Inner class: Pagination
    public static class Pagination {
        private int limit;
        private int offset;
        private boolean hasMore;

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public int getOffset() {
            return offset;
        }

        public void setOffset(int offset) {
            this.offset = offset;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public void setHasMore(boolean hasMore) {
            this.hasMore = hasMore;
        }
    }
}


