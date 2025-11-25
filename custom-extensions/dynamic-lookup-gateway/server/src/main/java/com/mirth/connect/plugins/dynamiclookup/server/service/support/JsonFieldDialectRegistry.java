/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.service.support;

import java.util.Objects;

import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo.DatabaseType;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.LookupJsonCapability;

public class JsonFieldDialectRegistry {
    /**
     * Lazily initialized singleton dialect instance.
     */
    private static volatile JsonFieldDialect dialectInstance;

    private JsonFieldDialectRegistry() {
        // utility / registry class; no instances
    }

    /**
     * Returns the JsonFieldDialect for the current database.
     * <p>
     * The first call inspects the shared LookupJsonCapability singleton to determine the DatabaseType and creates the
     * appropriate dialect. Subsequent calls reuse the same instance.
     *
     * @throws IllegalStateException if LookupJsonCapability has not been initialized.
     */
    public static JsonFieldDialect getDialect() {
        JsonFieldDialect result = dialectInstance;
        if (result == null) {
            synchronized (JsonFieldDialectRegistry.class) {
                result = dialectInstance;
                if (result == null) {
                    result = createDialectFromCapability();
                    dialectInstance = result;
                }
            }
        }
        return result;
    }

    /**
     * Creates a JsonFieldDialect based on the current LookupJsonCapability. This is only called once, from within the
     * synchronized block.
     */
    private static JsonFieldDialect createDialectFromCapability() {
        LookupJsonCapability capability = LookupJsonCapability.getInstance();
        Objects.requireNonNull(capability, "LookupJsonCapability must be initialized before using JsonFieldDialectRegistry.");

        DatabaseType type = capability.getDatabaseInfo().getType();
        switch (type) {
        case POSTGRESQL:
            return new PostgresJsonFieldDialect();

        case MYSQL:
            return new MysqlJsonFieldDialect();

        case SQLSERVER:
            return new SqlServerJsonFieldDialect();

        default:
            // Any database type that does not support JSON field dialects
            // will use NoJsonFieldDialect. That dialect throws on usage.
            return new NoJsonFieldDialect();
        }
    }
}
