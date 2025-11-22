/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.client.plugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.ClientPlugin;
import com.mirth.connect.plugins.dynamiclookup.client.panel.DataStorePanel;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.LookupJsonCapability;

public class LookupTableClientPlugin extends ClientPlugin {
    private DataStorePanel dataStorePane;
    private final Logger logger = LogManager.getLogger(this.getClass());

    public LookupTableClientPlugin(String name) {
        super(name);

        dataStorePane = new DataStorePanel();
    }

    @Override
    public String getPluginPointName() {
        return "Lookup Table Management System";
    }

    @Override
    public void start() {
        initLookupCapability();
    }

    @Override
    public void stop() {

    }

    @Override
    public void reset() {

    }

    private void initLookupCapability() {
        try {
            DatabaseInfo dbInfo = LookupServiceClient.getInstance().getDatabaseInfo();
            LookupJsonCapability.initialize(dbInfo);
        } catch (Exception e) {
            logger.error("Failed to initialize LookupJsonCapability. Falling back to TEXT-only mode.", e);
        }
    }
}
