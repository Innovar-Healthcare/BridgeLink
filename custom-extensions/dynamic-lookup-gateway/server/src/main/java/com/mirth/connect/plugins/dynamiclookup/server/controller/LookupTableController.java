/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.controller;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.dynamiclookup.server.cache.LookupCacheManager;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupAuditDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupGroupExtraDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupStatisticsDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.LookupValueDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.impl.MyBatisLookupAuditDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.impl.MyBatisLookupGroupDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.impl.MyBatisLookupGroupExtraDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.impl.MyBatisLookupStatisticsDao;
import com.mirth.connect.plugins.dynamiclookup.server.dao.impl.MyBatisLookupValueDao;
import com.mirth.connect.plugins.dynamiclookup.server.exception.LookupTableException;
import com.mirth.connect.plugins.dynamiclookup.server.maintenance.AuditPurgeTask;
import com.mirth.connect.plugins.dynamiclookup.server.migration.LookupDatabaseMigrator;
import com.mirth.connect.plugins.dynamiclookup.server.service.LookupService;
import com.mirth.connect.plugins.dynamiclookup.server.userutil.LookupHelper;
import com.mirth.connect.plugins.dynamiclookup.server.util.DatabaseDialect;
import com.mirth.connect.plugins.dynamiclookup.server.util.SqlSessionManagerProvider;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.LookupJsonCapability;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupProperties;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;
import com.mirth.connect.server.controllers.ExtensionController;

public class LookupTableController implements LookupPropertiesProvider {
    private final Logger logger = LogManager.getLogger(this.getClass());

    private final LookupService lookupService = LookupService.getInstance();
    private LookupCacheManager cacheManager;

    private volatile LookupProperties currentProperties = LookupProperties.getDefault();
    private AuditPurgeTask auditPurgeTask;

    private final ScheduledExecutorService restoreExec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Lookup Table Restore Reconcile");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> pendingRestore;

    private static LookupTableController instance = null;

    public static LookupTableController getInstance() {
        synchronized (LookupTableController.class) {
            if (instance == null) {
                instance = new LookupTableController();
            }
            return instance;
        }
    }

    @Override
    public LookupProperties get() {
        return currentProperties;
    }

    public void init(Properties properties) throws LookupTableException {
        try {
            SqlSessionManager sqlSessionManager = getSqlSessionManager();

            DatabaseInfo dbInfo = DatabaseDialect.determineDatabase(sqlSessionManager);

            // Initialize LookupJsonContext
            LookupJsonCapability.initialize(dbInfo);

            // Initialize database if needed
            new LookupDatabaseMigrator(sqlSessionManager).initializeDatabase();

            // Create DAO instances

            LookupGroupDao groupDao = new MyBatisLookupGroupDao(sqlSessionManager);
            LookupGroupExtraDao groupExtraDao = new MyBatisLookupGroupExtraDao(sqlSessionManager);
            LookupValueDao valueDao = new MyBatisLookupValueDao(sqlSessionManager, dbInfo.getType());
            LookupAuditDao auditDao = new MyBatisLookupAuditDao(sqlSessionManager);
            LookupStatisticsDao statisticsDao = new MyBatisLookupStatisticsDao(sqlSessionManager);

            // Create cache manager
            cacheManager = new LookupCacheManager(groupDao);

            // Init lookup service
            lookupService.init(groupDao, valueDao, auditDao, statisticsDao, groupExtraDao, cacheManager);

            // Initialize helper methods for transformers
            LookupHelper.initialize(lookupService);

            // Initialize Properties
            currentProperties = LookupProperties.fromProperties(properties);

            auditPurgeTask = new AuditPurgeTask(this, lookupService);

            logger.info("Lookup Table Management System plugin initialized successfully");
        } catch (Exception e) {
            throw new LookupTableException(e);
        }
    }

    public void update(Properties properties) throws LookupTableException {
        // Reconcile job pending -> cancel
        if (pendingRestore != null) {
            pendingRestore.cancel(false);
            pendingRestore = null;
        }

        updateProperties(LookupProperties.fromProperties(properties));

        refreshAuditPurgeSchedule();
    }

    public void start() throws LookupTableException {
        try {
            // Preload lookup tables for better performance
            preloadLookupTables();

            refreshAuditPurgeSchedule();
        } catch (Exception e) {
            throw new LookupTableException(e);
        }
    }

    public void stop() throws LookupTableException {
        try {
            // Clear all caches
            if (cacheManager != null) {
                cacheManager.clearAllCaches();
            }

            if (auditPurgeTask != null) {
                auditPurgeTask.stop();
                auditPurgeTask = null;
            }
        } catch (Exception e) {
            throw new LookupTableException(e);
        } finally {
            // Cancel any pending restore
            if (pendingRestore != null) {
                pendingRestore.cancel(false);
                pendingRestore = null;
            }
        }
    }

    public void onRestoreTriggered() {
        // Cancel any previous pending task
        if (pendingRestore != null && !pendingRestore.isDone()) {
            pendingRestore.cancel(false);
            pendingRestore = null;
        }

        try {
            pendingRestore = restoreExec.schedule(() -> {
                try {
                    // Read persisted properties from MC core after DB write
                    Properties persisted = ExtensionController.getInstance().getPluginProperties("Lookup Table Management System"); // plugin point name

                    if (persisted != null) {
                        update(persisted);
                    }
                } catch (Exception e) {
                    logger.error("Failed to reconcile after restore", e);
                }
            }, 5, TimeUnit.SECONDS);
            logger.debug("Scheduled reconcile after restore (5s delay).");
        } catch (RejectedExecutionException rex) {
            logger.warn("Restore reconcile skipped: executor already shut down.");
        }
    }

    public synchronized void updateProperties(LookupProperties newProps) {
        this.currentProperties = newProps;
    }

    private void refreshAuditPurgeSchedule() {
        if (auditPurgeTask == null) {
            logger.warn("Audit purge task is not initialized; skipping schedule refresh.");
            return;
        }

        LookupProperties props = currentProperties;

        boolean enabled = props.isAuditPruneEnabled();
        long intervalSec = auditPurgeTask.getFixedRateSeconds();
        long initialDelaySec = auditPurgeTask.getInitialDelaySeconds();

        auditPurgeTask.refresh(enabled, intervalSec, initialDelaySec);

        logger.info("Audit purge schedule {} (interval={}s, delay={}s)", (enabled ? "enabled" : "disabled"), intervalSec, initialDelaySec);
    }

    /**
     * Get SqlSessionManager from Mirth's context
     */
    private SqlSessionManager getSqlSessionManager() {
        return SqlSessionManagerProvider.get();
    }

    /**
     * Preload frequently used lookup tables
     */
    private void preloadLookupTables() {
        logger.info("Preloading lookup tables...");
        try {
            List<LookupGroup> groups = lookupService.getAllGroups();
            int count = 0;
            for (LookupGroup group : groups) {
                // Build cache instance for this group
                cacheManager.createOrRebuildGroupCache(group);

                int size = group.getCacheSize();

                if (size <= 0) {
                    // disabled – skip preload values
                    continue;
                }

                // Load values into cache
                int valueCount = preloadGroupValues(group);
                if (valueCount > 0) {
                    count++;
                    logger.info("Preloaded {} values for group: {} (ID: {})", valueCount, group.getName(), group.getId());
                }
            }
            logger.info("Completed preloading {} lookup groups", count);
        } catch (Exception e) {
            logger.error("Error preloading lookup tables", e);
        }
    }

    /**
     * Preload values for a specific group
     */
    private int preloadGroupValues(LookupGroup group) {
        try {
            int limit = group.getCacheSize() * 2;
            List<LookupValue> values = lookupService.searchLookupValues(group.getId(), 0, limit, null);

            // Skip if the group is empty or too large for caching
            if (values == null || values.isEmpty()) {
                return 0;
            }

            // Load values into cache
            for (LookupValue value : values) {
                cacheManager.putValue(group.getId(), value.getKeyValue(), value.getValueData(), value.getUpdatedDate());
            }
            return values.size();
        } catch (Exception e) {
            logger.warn("Failed to preload values for group: {} (ID: {}): {}", group.getName(), group.getId(), e.getMessage());
            return 0;
        }
    }
}
