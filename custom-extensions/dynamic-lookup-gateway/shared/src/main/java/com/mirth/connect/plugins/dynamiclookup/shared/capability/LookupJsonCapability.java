/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.capability;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.mirth.connect.plugins.dynamiclookup.shared.constant.LookupConstants;

/**
 * Describes JSON feature support based on a specific DatabaseInfo. Used by both server and client.
 */
public final class LookupJsonCapability {

    // ----------------------------------------------------------------------
    // Instance fields
    // ----------------------------------------------------------------------

    private final DatabaseInfo databaseInfo;
    private final boolean jsonSupported;
    private final Set<String> supportedJsonIndexModes;
    private final int maxIdentifierLength;

    // Optional singleton for server-side (initialized once)
    private static volatile LookupJsonCapability instance;

    private LookupJsonCapability(DatabaseInfo dbInfo, boolean jsonSupported, Set<String> modes, int maxIdentifierLength) {
        this.databaseInfo = Objects.requireNonNull(dbInfo, "databaseInfo");
        this.jsonSupported = jsonSupported;
        this.supportedJsonIndexModes = Collections.unmodifiableSet(new HashSet<>(modes));
        this.maxIdentifierLength = maxIdentifierLength;
    }

    // ----------------------------------------------------------------------
    // Static factory: DatabaseInfo → Capability
    // ----------------------------------------------------------------------

    public static LookupJsonCapability forDatabase(DatabaseInfo dbInfo) {
        Objects.requireNonNull(dbInfo, "dbInfo");

        switch (dbInfo.getType()) {

        case POSTGRESQL:
            if (dbInfo.isAtLeast(9, 4)) {
                return postgres9_4(dbInfo);
            }
            return noJson(dbInfo);

        case MYSQL:
            if (dbInfo.isAtLeast(8, 0)) {
                return mysql8_0(dbInfo);
            }
            return noJson(dbInfo);

        case SQLSERVER:
            return sqlserver(dbInfo);

        case ORACLE:
            if (dbInfo.isAtLeast(12, 2)) {
                return oracle12_2(dbInfo);
            }
            return noJson(dbInfo);

        default:
            return noJson(dbInfo);
        }
    }

    // ----------------------------------------------------------------------
    // Built-in profiles
    // ----------------------------------------------------------------------

    private static LookupJsonCapability noJson(DatabaseInfo dbInfo) {
        return new LookupJsonCapability(dbInfo, false, Collections.emptySet(), 32);
    }

    private static LookupJsonCapability postgres9_4(DatabaseInfo dbInfo) {
        return new LookupJsonCapability(dbInfo, true, Set.of(LookupConstants.JSON_INDEX_NONE, LookupConstants.JSON_INDEX_FIELD), 63);
    }

    private static LookupJsonCapability mysql8_0(DatabaseInfo dbInfo) {
        return new LookupJsonCapability(dbInfo, true, Set.of(LookupConstants.JSON_INDEX_NONE, LookupConstants.JSON_INDEX_FIELD), 64);
    }

    private static LookupJsonCapability sqlserver(DatabaseInfo dbInfo) {
        return new LookupJsonCapability(dbInfo, true, Set.of(LookupConstants.JSON_INDEX_NONE, LookupConstants.JSON_INDEX_FIELD), 128);
    }

    private static LookupJsonCapability oracle12_2(DatabaseInfo dbInfo) {
        return new LookupJsonCapability(dbInfo, true, Set.of(LookupConstants.JSON_INDEX_NONE, LookupConstants.JSON_INDEX_FIELD), 128);
    }
    // ----------------------------------------------------------------------
    // Optional server-side singleton
    // ----------------------------------------------------------------------

    /**
     * Initialize the capability singleton on the server side. Subsequent calls do nothing.
     */
    public static synchronized void initialize(DatabaseInfo dbInfo) {
        if (instance != null) {
            return;
        }
        instance = forDatabase(dbInfo);
    }

    /**
     * Get the server-side singleton capability.
     */
    public static LookupJsonCapability getInstance() {
        if (instance == null) {
            throw new IllegalStateException("LookupJsonCapability has not been initialized.");
        }

        return instance;
    }

    // ----------------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------------

    public DatabaseInfo getDatabaseInfo() {
        return databaseInfo;
    }

    public boolean isJsonSupported() {
        return jsonSupported;
    }

    public Set<String> getSupportedJsonIndexModes() {
        return supportedJsonIndexModes;
    }

    public boolean isJsonIndexModeSupported(String mode) {
        if (!jsonSupported || mode == null) {
            return false;
        }

        for (String m : supportedJsonIndexModes) {
            if (m.equalsIgnoreCase(mode)) {
                return true;
            }
        }
        return false;
    }

    public int getMaxIdentifierLength() {
        return maxIdentifierLength;
    }

    // @formatter:off
    @Override
    public String toString() {
        return "LookupJsonCapability{" +
                "db=" + databaseInfo +
                ", jsonSupported=" + jsonSupported +
                ", modes=" + supportedJsonIndexModes +
                ", maxIdentifierLength=" + maxIdentifierLength +
                '}';
    }
    // @formatter:on
}
