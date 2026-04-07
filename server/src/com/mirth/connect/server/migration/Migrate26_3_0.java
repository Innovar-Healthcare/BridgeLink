/*
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.migration;

import com.mirth.connect.model.util.MigrationException;

/**
 * Server migrator for BridgeLink version 26.3.0 — SMTP OAuth 2.0 support.
 *
 * <p>No database schema changes are required for this version.
 * Serialized channel data migration (SmtpDispatcherProperties OAuth fields)
 * is handled automatically by the {@code Migratable} framework via
 * {@code SmtpDispatcherProperties.migrate26_3_0()}.</p>
 */
public class Migrate26_3_0 extends Migrator {

    @Override
    public void migrate() throws MigrationException {
        // No database schema changes required for 26.3.0
    }

    @Override
    public void migrateSerializedData() throws MigrationException {
        // Serialized data migration is handled by the Migratable framework
    }
}
