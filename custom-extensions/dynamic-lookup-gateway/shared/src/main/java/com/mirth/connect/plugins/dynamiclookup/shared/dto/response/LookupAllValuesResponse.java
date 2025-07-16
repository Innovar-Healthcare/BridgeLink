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

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;

import java.util.List;

public class LookupAllValuesResponse {
    private int groupId;
    private String groupName;
    private int totalCount;
    private List<LookupValue> values;
    private Pagination pagination;

    // Constructors
    public LookupAllValuesResponse() {
    }

    public LookupAllValuesResponse(int groupId, String groupName, int totalCount, List<LookupValue> values, Pagination pagination) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.totalCount = totalCount;
        this.values = values;
        this.pagination = pagination;
    }

    // Getters and Setters
    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public List<LookupValue> getValues() {
        return values;
    }

    public void setValues(List<LookupValue> values) {
        this.values = values;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }

    public static LookupAllValuesResponse fromResult(
            Integer groupId,
            String groupName,
            int totalCount,
            List<LookupValue> paginated,
            int limit,
            int offset
    ) {
        LookupAllValuesResponse response = new LookupAllValuesResponse();
        response.setGroupId(groupId);
        response.setGroupName(groupName);
        response.setTotalCount(totalCount);
        response.setValues(paginated);

        Pagination pagination = new Pagination();
        pagination.setLimit(limit);
        pagination.setOffset(offset);
        pagination.setHasMore(offset + limit < totalCount);
        response.setPagination(pagination);

        return response;
    }

    // ✅ Static inner class for pagination
    public static class Pagination {
        private int limit;
        private int offset;
        private boolean hasMore;

        // Getters and setters
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

