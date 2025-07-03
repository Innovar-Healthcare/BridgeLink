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

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Date;

public class ExportGroupPagedResponse {

    private Integer groupId;
    private Integer totalCount;
    private Pagination pagination;
    private Map<String, String> values;
    private Date exportDate;

    public ExportGroupPagedResponse() {
    }

    public ExportGroupPagedResponse(Integer groupId, Integer totalCount, Pagination pagination,
                                    Map<String, String> values, Date exportDate) {
        this.groupId = groupId;
        this.totalCount = totalCount;
        this.pagination = pagination;
        this.values = values;
        this.exportDate = exportDate;
    }

    public static ExportGroupPagedResponse fromResult(
            Integer groupId,
            int offset,
            int limit,
            int totalCount,
            List<LookupValue> valuesList
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        for (LookupValue lv : valuesList) {
            values.put(lv.getKeyValue(), lv.getValueData());
        }

        Pagination pagination = new Pagination();
        pagination.setOffset(offset);
        pagination.setLimit(limit);
        pagination.setHasMore(offset + limit < totalCount);

        return new ExportGroupPagedResponse(groupId, totalCount, pagination, values, new Date());
    }

    // Getters and setters

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values = values;
    }

    public Date getExportDate() {
        return exportDate;
    }

    public void setExportDate(Date exportDate) {
        this.exportDate = exportDate;
    }

    //Static inner class
    public static class Pagination {
        private int offset;
        private int limit;
        private boolean hasMore;

        public int getOffset() {
            return offset;
        }

        public void setOffset(int offset) {
            this.offset = offset;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public void setHasMore(boolean hasMore) {
            this.hasMore = hasMore;
        }
    }
}


