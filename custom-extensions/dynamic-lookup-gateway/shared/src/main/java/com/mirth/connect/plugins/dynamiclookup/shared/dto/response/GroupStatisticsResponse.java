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

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupStatistics;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Represents the overall lookup statistics and cache metrics
 * for a specific lookup group, including database-level stats
 * and runtime cache diagnostics.
 */
public class GroupStatisticsResponse {

    private int groupId;
    private long totalLookups;
    private long cacheHits;
    private double hitRate;
    private Date lastAccessed;
    private Date resetDate;
    private CacheStatistics cacheStatistics;

    // Getters
    public int getGroupId() {
        return groupId;
    }

    public long getTotalLookups() {
        return totalLookups;
    }

    public long getCacheHits() {
        return cacheHits;
    }

    public double getHitRate() {
        return hitRate;
    }

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public Date getResetDate() {
        return resetDate;
    }

    public CacheStatistics getCacheStatistics() {
        return cacheStatistics;
    }

    /**
     * Factory method to construct a full statistics response for a lookup group.
     */
    public static GroupStatisticsResponse fromResult(LookupStatistics stats, CacheStatistics cacheStatistics) {
        GroupStatisticsResponse response = new GroupStatisticsResponse();

        response.groupId = stats.getGroupId();
        response.totalLookups = stats.getTotalLookups();
        response.cacheHits = stats.getCacheHits();
        response.hitRate = computeHitRate(stats.getCacheHits(), stats.getTotalLookups());
        response.lastAccessed = stats.getLastAccessed();
        response.resetDate = stats.getResetDate();
        response.cacheStatistics = cacheStatistics;

        return response;
    }

    private static double computeHitRate(long hits, long total) {
        return (total > 0) ? (double) hits / total : 0.0;
    }
}




