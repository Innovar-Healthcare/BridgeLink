/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mirth.connect.model.ServerEvent;
import com.mirth.connect.model.ServerEvent.Level;
import com.mirth.connect.model.ServerEvent.Outcome;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

public class LookupAuditLogger {
    private final ConfigurationController configurationController;
    private final EventController eventController;

    private static final LookupAuditLogger INSTANCE = new LookupAuditLogger(ControllerFactory.getFactory().createConfigurationController(), ControllerFactory.getFactory().createEventController());

    public static LookupAuditLogger getInstance() {
        return INSTANCE;
    }

    private LookupAuditLogger(ConfigurationController configurationController, EventController eventController) {
        this.configurationController = configurationController;
        this.eventController = eventController;
    }

    private void logServerEvent(String name, Map<String, String> attributes) {
        ServerEvent event = new ServerEvent(configurationController.getServerId(), name, Level.INFORMATION, Outcome.SUCCESS, attributes);
        eventController.dispatchEvent(event);
    }

    public void logHelperGroupDeleted(String groupName, Integer groupId) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("source", "LookupHelper");
        if (groupName != null) {
            attrs.put("groupName", groupName);
        }
        if (groupId != null) {
            attrs.put("groupId", String.valueOf(groupId));
        }

        logServerEvent("Delete group invoked through Lookup Table Management System", attrs);
    }

    public void logHelperGroupCreated(LookupGroup group) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("source", "LookupHelper");

        String json;
        try {
            json = JsonUtils.toJson(group);
        } catch (Exception e) {
            json = "{ \"error\": \"Failed to serialize LookupGroup to JSON\" }";
        }

        attrs.put("requestBody", json);

        logServerEvent("Create new group invoked through Lookup Table Management System", attrs);
    }
}
