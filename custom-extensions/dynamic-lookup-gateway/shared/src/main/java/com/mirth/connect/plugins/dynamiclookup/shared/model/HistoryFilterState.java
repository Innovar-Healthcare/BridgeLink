/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.model;

import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

import java.util.Date;
import java.util.Objects;

public class HistoryFilterState {
    private String keyValue;
    private String action;
    private String userId;
    private Date startDate;
    private Date endDate;

    // Constructors
    public HistoryFilterState() {
    }

    public HistoryFilterState(String keyValue, String action, String userId, Date startDate, Date endDate) {
        this.keyValue = keyValue;
        this.action = action;
        this.userId = userId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // Getters and Setters
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    // Check if all fields are empty
    public boolean isEmpty() {
        return isNullOrEmpty(keyValue) &&
                isNullOrEmpty(action) &&
                isNullOrEmpty(userId) &&
                startDate == null &&
                endDate == null;
    }

    private boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    // Delegate JSON methods to JsonUtils
    public String toJson() throws Exception {
        return JsonUtils.toJson(this);
    }

    public static HistoryFilterState fromJson(String json) throws Exception {
        return JsonUtils.fromJson(json, HistoryFilterState.class);
    }

    @Override
    public String toString() {
        return "HistoryFilterState{" +
                "keyValue='" + keyValue + '\'' +
                ", action='" + action + '\'' +
                ", userId='" + userId + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HistoryFilterState)) return false;
        HistoryFilterState that = (HistoryFilterState) o;
        return Objects.equals(keyValue, that.keyValue) &&
                Objects.equals(action, that.action) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(startDate, that.startDate) &&
                Objects.equals(endDate, that.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyValue, action, userId, startDate, endDate);
    }
}


