package com.mirth.connect.plugins.dynamiclookup.client.plugin;

import com.mirth.connect.plugins.ClientPlugin;
import com.mirth.connect.plugins.dynamiclookup.client.panel.DataStorePanel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    }

    @Override
    public void stop() {

    }

    @Override
    public void reset() {

    }
}
