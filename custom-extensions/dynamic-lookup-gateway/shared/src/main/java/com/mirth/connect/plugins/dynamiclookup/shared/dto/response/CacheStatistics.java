package com.mirth.connect.plugins.dynamiclookup.shared.dto.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.concurrent.TimeUnit;

/**
 * A complete, serializable representation of in-memory cache performance
 * for a lookup group, including raw stats, derived metrics, and configuration context.
 */
public class CacheStatistics {

    // 1. Configuration & Support Context
    private final boolean statsSupported;
    private final String evictionPolicy;
    private final int currentEntryCount;
    private final int configuredMaxEntries;

    // 2. Raw Stats (mirroring Guava's CacheStats)
    private final long hitCount;
    private final long missCount;
    private final long loadSuccessCount;
    private final long loadExceptionCount;
    private final long totalLoadTime;  // nanoseconds
    private final long evictionCount;

    // 3. Derived Metrics
    private final double hitRatio;
    private final double missRatio;
    private final String totalLoadTimeFormatted;

    @JsonCreator
    public CacheStatistics(
            @JsonProperty("statsSupported") boolean statsSupported,
            @JsonProperty("evictionPolicy") String evictionPolicy,
            @JsonProperty("currentEntryCount") int currentEntryCount,
            @JsonProperty("configuredMaxEntries") int configuredMaxEntries,
            @JsonProperty("hitCount") long hitCount,
            @JsonProperty("missCount") long missCount,
            @JsonProperty("loadSuccessCount") long loadSuccessCount,
            @JsonProperty("loadExceptionCount") long loadExceptionCount,
            @JsonProperty("totalLoadTime") long totalLoadTime,
            @JsonProperty("evictionCount") long evictionCount
    ) {

        this.statsSupported = statsSupported;
        this.evictionPolicy = evictionPolicy != null ? evictionPolicy.toUpperCase() : "UNKNOWN";
        this.currentEntryCount = currentEntryCount;
        this.configuredMaxEntries = configuredMaxEntries;

        this.hitCount = hitCount;
        this.missCount = missCount;
        this.loadSuccessCount = loadSuccessCount;
        this.loadExceptionCount = loadExceptionCount;
        this.totalLoadTime = totalLoadTime;
        this.evictionCount = evictionCount;

        long totalRequests = hitCount + missCount;
        this.hitRatio = (totalRequests > 0) ? (double) hitCount / totalRequests : 0.0;
        this.missRatio = (totalRequests > 0) ? (double) missCount / totalRequests : 0.0;
        this.totalLoadTimeFormatted = formatLoadTime(totalLoadTime);
    }

    private String formatLoadTime(long nanos) {
        long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
        return (millis < 1000) ? millis + " ms" : (millis / 1000) + " sec";
    }

    // --- Getters ---

    public boolean isStatsSupported() {
        return statsSupported;
    }

    public String getEvictionPolicy() {
        return evictionPolicy;
    }

    public int getCurrentEntryCount() {
        return currentEntryCount;
    }

    public int getConfiguredMaxEntries() {
        return configuredMaxEntries;
    }

    public long getHitCount() {
        return hitCount;
    }

    public long getMissCount() {
        return missCount;
    }

    public long getLoadSuccessCount() {
        return loadSuccessCount;
    }

    public long getLoadExceptionCount() {
        return loadExceptionCount;
    }

    public long getTotalLoadTime() {
        return totalLoadTime;
    }

    public long getEvictionCount() {
        return evictionCount;
    }

    public double getHitRatio() {
        return hitRatio;
    }

    public double getMissRatio() {
        return missRatio;
    }

    public String getTotalLoadTimeFormatted() {
        return totalLoadTimeFormatted;
    }
}



