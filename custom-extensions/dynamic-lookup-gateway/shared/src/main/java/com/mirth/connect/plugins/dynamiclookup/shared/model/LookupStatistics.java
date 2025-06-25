package com.mirth.connect.plugins.dynamiclookup.shared.model;

import java.util.Date;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-05-13 10:25 AM
 */
public class LookupStatistics {
    private int groupId;
    private long totalLookups;
    private long cacheHits;
    private Date lastAccessed;
    private Date resetDate;

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public long getTotalLookups() {
        return totalLookups;
    }

    public void setTotalLookups(long totalLookups) {
        this.totalLookups = totalLookups;
    }

    public long getCacheHits() {
        return cacheHits;
    }

    public void setCacheHits(long cacheHits) {
        this.cacheHits = cacheHits;
    }

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(Date lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public Date getResetDate() {
        return resetDate;
    }

    public void setResetDate(Date resetDate) {
        this.resetDate = resetDate;
    }
}
