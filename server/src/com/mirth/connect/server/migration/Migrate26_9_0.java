/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.migration;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.Version;
import com.mirth.connect.model.DriverInfo;
import com.mirth.connect.model.util.MigrationException;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;

/**
 * CVE-04 (mssql-jdbc CVE upgrade): jTDS has been fully retired from the shipped driver list
 * (see Phase 25 plans 01/02). An existing install's OWN persisted driver list (in the app DB,
 * written by a prior setDatabaseDrivers call) still carries the "SQL Server/Sybase (jTDS)"
 * DriverInfo entry after upgrade, since shipping a new dbdrivers.xml does not rewrite an
 * install's own persisted state. Strip that entry so it no longer appears as a dangling,
 * ClassNotFoundException-prone dropdown option. When the persisted list has no Microsoft SQL
 * Server entry to fall back on, substitute the canonical Microsoft entry in place of jTDS
 * rather than removing it, so the upgrade never leaves such an install with no SQL Server
 * driver in the dropdown (IRT-1912).
 *
 * This migrator touches ONLY the DB-resident persisted driver list via
 * getDatabaseDrivers()/setDatabaseDrivers() — it never touches the on-disk mcserver connection
 * URL or any customer channel-stored driver string, both of which are docs-only (see 25-04).
 *
 * This class ALSO implements {@link ConfigurationMigrator} (IRT-2217, criterion 2): on a
 * pre-26.9 upgrade whose host is not UTF-8 and whose mirth.properties lacks
 * server.defaultencoding, updateConfiguration() pins that property to the host encoding so a
 * connector left on "Default" keeps its pre-JEP-400 behavior instead of silently switching to
 * UTF-8 on the Java 21 move. That config path is independent of the driver-list work above --
 * updateConfiguration() is invoked by ServerMigrator.migrateConfiguration(), a different
 * framework path than migrate(), so the two do not interact.
 */
public class Migrate26_9_0 extends Migrator implements ConfigurationMigrator {

    private static final String JTDS_CLASS = "net.sourceforge.jtds.jdbc.Driver";
    private static final String MSSQL_CLASS = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    private Logger logger = LogManager.getLogger(getClass());

    @Override
    public Map<String, Object> getConfigurationPropertiesToAdd() {
        return null;
    }

    @Override
    public String[] getConfigurationPropertiesToRemove() {
        return null;
    }

    @Override
    public void updateConfiguration(PropertiesConfiguration configuration) {
        if (getStartingVersion() == null || getStartingVersion().ordinal() < Version.v26_9_0.ordinal()) {
            if (!configuration.containsKey("server.defaultencoding")) {
                String hostEncoding = getHostEncoding();
                if (StringUtils.isNotBlank(hostEncoding) && !StringUtils.equals(hostEncoding, StandardCharsets.UTF_8.name())) {
                    configuration.setProperty("server.defaultencoding", hostEncoding);
                    configuration.getLayout().setBlancLinesBefore("server.defaultencoding", 1);
                    configuration.getLayout().setComment("server.defaultencoding",
                            "Set on upgrade so DEFAULT_ENCODING connectors keep the pre-Java-18 host encoding after the JEP 400 UTF-8 change. Remove this property to follow the JVM default (UTF-8).");
                }
            }
        }
    }

    // Making class mockable (IRT-2217). Reads native.encoding, NOT the JVM's own default-charset
    // API -- under JEP 400 (JDK 18+) that API always returns UTF-8 regardless of host, so it
    // would never detect a non-UTF-8 Windows host on the Java 21 target this migration exists
    // to protect. native.encoding (JDK 17+) reports the true pre-JEP-400 host encoding.
    String getHostEncoding() {
        return System.getProperty("native.encoding");
    }

    @Override
    public void migrate() throws MigrationException {
        ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
        try {
            List<DriverInfo> drivers = configurationController.getDatabaseDrivers();
            boolean changed = false;

            // The jTDS driver was retired in favor of mssql-jdbc (CVE-04). If the persisted list
            // already carries a Microsoft SQL Server entry, the jTDS entry is safe to drop. If it
            // does NOT, jTDS is the install's only SQL Server option, so we must substitute the
            // canonical Microsoft entry in place rather than remove it, or the upgrade leaves the
            // install with no SQL Server driver and no way to pick one from the dropdown (IRT-1912).
            boolean hasMssql = drivers.stream().anyMatch(d -> StringUtils.equals(d.getClassName(), MSSQL_CLASS));

            for (ListIterator<DriverInfo> it = drivers.listIterator(); it.hasNext();) {
                DriverInfo driver = it.next();

                if (StringUtils.equals(driver.getClassName(), JTDS_CLASS)) {
                    if (hasMssql) {
                        logger.info("Removing retired jTDS driver entry (mssql-jdbc CVE upgrade, CVE-04)");
                        it.remove();
                    } else {
                        // Substitute in place so the entry keeps jTDS's position, and flip hasMssql
                        // so any second jTDS row is removed rather than adding a duplicate Microsoft entry.
                        logger.info("Substituting retired jTDS driver entry with Microsoft SQL Server (mssql-jdbc CVE upgrade, CVE-04; IRT-1912)");
                        it.set(canonicalMssqlEntry());
                        hasMssql = true;
                    }
                    changed = true;
                }
            }

            // Only re-persist the list when something actually changed. Installs whose
            // persisted list never carried the jTDS entry (e.g. a fresh 26.6.0 install) must
            // remain a true no-op, otherwise this migrator would snapshot the shipped
            // dbdrivers.xml defaults into the DB and permanently shadow future updates to it.
            if (changed) {
                configurationController.setDatabaseDrivers(drivers);
            }
        } catch (ControllerException e) {
            throw new MigrationException(e);
        }
    }

    /**
     * The canonical "Microsoft SQL Server" entry, sourced from {@link DriverInfo#getDefaultDrivers()}
     * so no driver literal is duplicated here. Matched by class name (not list position) so a future
     * reordering of the default list cannot silently pick the wrong entry.
     */
    private static DriverInfo canonicalMssqlEntry() {
        return DriverInfo.getDefaultDrivers().stream()
                .filter(d -> StringUtils.equals(d.getClassName(), MSSQL_CLASS))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Microsoft SQL Server entry missing from DriverInfo.getDefaultDrivers()"));
    }

    @Override
    public void migrateSerializedData() throws MigrationException {}
}
