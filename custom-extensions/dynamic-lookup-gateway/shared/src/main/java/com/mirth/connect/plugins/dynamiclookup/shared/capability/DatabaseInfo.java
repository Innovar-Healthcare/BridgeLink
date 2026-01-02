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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DatabaseInfo {
    /**
     * Supported database types for SQL dialect branching.
     */
    public enum DatabaseType {
        DERBY, POSTGRESQL, MYSQL, // Includes MySQL and MariaDB
        SQLSERVER, ORACLE
    }

    private final DatabaseType type;
    private final int majorVersion;
    private final int minorVersion;

    private final String productName; // Raw product name from JDBC metadata (optional)
    private final String productVersion; // Raw full version string (optional)

    // @formatter:off
    @JsonCreator
    public DatabaseInfo(
            @JsonProperty("type") DatabaseType type,
            @JsonProperty("majorVersion") int majorVersion,
            @JsonProperty("minorVersion") int minorVersion,
            @JsonProperty("productName") String productName,
            @JsonProperty("productVersion") String productVersion
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.productName = productName;
        this.productVersion = productVersion;
    }
    // @formatter:on

    public DatabaseType getType() {
        return type;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductVersion() {
        return productVersion;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns true if this database version is >= given (major.minor)
     */
    public boolean isAtLeast(int major, int minor) {
        if (this.majorVersion > major) {
            return true;
        }

        if (this.majorVersion < major) {
            return false;
        }

        return this.minorVersion >= minor;
    }

    @Override
    // @formatter:off
    public String toString() {
        return "DatabaseInfo{" +
                "type=" + type +
                ", major=" + majorVersion +
                ", minor=" + minorVersion +
                ", productName='" + productName + '\'' +
                ", productVersion='" + productVersion + '\'' +
                '}';
    }
    // @formatter:on
}
