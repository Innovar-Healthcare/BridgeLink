/*
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.migration;

import com.mirth.connect.model.util.MigrationException;

public class Migrate26_3_1 extends Migrator {

    @Override
    public void migrate() throws MigrationException {
        // No database schema changes required for 26.3.1
    }

    @Override
    public void migrateSerializedData() throws MigrationException {
        // No serialized data migration required for 26.3.1
    }
}
