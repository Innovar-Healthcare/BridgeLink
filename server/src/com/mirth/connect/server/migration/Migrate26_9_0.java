/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.migration;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.ControllerException;
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
 * ClassNotFoundException-prone dropdown option.
 *
 * This migrator touches ONLY the DB-resident persisted driver list via
 * getDatabaseDrivers()/setDatabaseDrivers() — it never touches the on-disk mcserver connection
 * URL or any customer channel-stored driver string, both of which are docs-only (see 25-04).
 */
public class Migrate26_9_0 extends Migrator {

    private Logger logger = LogManager.getLogger(getClass());

    @Override
    public void migrate() throws MigrationException {
        ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
        try {
            List<DriverInfo> drivers = configurationController.getDatabaseDrivers();
            boolean removed = false;

            for (Iterator<DriverInfo> it = drivers.iterator(); it.hasNext();) {
                DriverInfo driver = it.next();

                // The jTDS driver was retired in favor of mssql-jdbc (CVE-04); strip the
                // now-dangling "SQL Server/Sybase (jTDS)" entry from the persisted driver list.
                if (StringUtils.equals(driver.getClassName(), "net.sourceforge.jtds.jdbc.Driver")) {
                    logger.info("Removing retired jTDS driver entry (mssql-jdbc CVE upgrade, CVE-04)");
                    it.remove();
                    removed = true;
                }
            }

            // Only re-persist the list when something actually changed. Installs whose
            // persisted list never carried the jTDS entry (e.g. a fresh 26.6.0 install) must
            // remain a true no-op, otherwise this migrator would snapshot the shipped
            // dbdrivers.xml defaults into the DB and permanently shadow future updates to it.
            if (removed) {
                configurationController.setDatabaseDrivers(drivers);
            }
        } catch (ControllerException e) {
            throw new MigrationException(e);
        }
    }

    @Override
    public void migrateSerializedData() throws MigrationException {}
}
