/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.client.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.EntityException;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.plugins.dynamiclookup.client.exception.LookupApiClientException;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.DatabaseInfo;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.request.LookupValueRequest;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.ErrorResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.ExportGroupPagedResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.ExportLookupGroupResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.GroupAuditEntriesResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.GroupStatisticsResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.ImportLookupGroupResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.ImportValuesResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.LookupAllValuesResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.LookupValueResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.interfaces.LookupTableServletInterface;
import com.mirth.connect.plugins.dynamiclookup.shared.model.HistoryFilterState;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;
import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;
import com.mirth.connect.plugins.dynamiclookup.shared.util.LookupErrorCode;

public class LookupServiceClient {
    private static LookupServiceClient instance = null;
    private LookupTableServletInterface servlet;
    private final Logger logger = LogManager.getLogger(this.getClass());

    public static LookupServiceClient getInstance() {
        synchronized (LookupServiceClient.class) {
            if (instance == null) {
                instance = new LookupServiceClient();
            }

            return instance;
        }
    }

    public LookupServiceClient() {
    }

    public List<LookupGroup> getAllGroups() throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().getAllGroups();

            return JsonUtils.fromJsonList(response, LookupGroup.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to get all groups", e);
        }
    }

    public LookupGroup getGroupById(Integer groupId) throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().getGroupById(groupId);

            return JsonUtils.fromJson(response, LookupGroup.class);
        } catch (ClientException e) {
            // 3. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 4. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to get group by id", e);
        }
    }

    public LookupGroup getGroupByName(String name) throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().getGroupByName(name);

            return JsonUtils.fromJson(response, LookupGroup.class);
        } catch (ClientException e) {
            // 3. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 4. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to get group by name", e);
        }
    }

    public LookupGroup createGroup(LookupGroup group) throws ClientException {
        try {
            // 1. Serialize the request body
            String request = JsonUtils.toJson(group);

            // 2. Make the call
            String response = getServlet().createGroup(request);

            return JsonUtils.fromJson(response, LookupGroup.class);
        } catch (ClientException e) {
            // 3. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 4. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to create group", e);
        }
    }

    public LookupGroup updateGroup(LookupGroup group) throws ClientException {
        try {
            // 1. Serialize the request body
            String request = JsonUtils.toJson(group);

            // 2. Make the call
            String response = getServlet().updateGroup(group.getId(), request);

            return JsonUtils.fromJson(response, LookupGroup.class);
        } catch (ClientException e) {
            // 3. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 4. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to update group", e);
        }
    }

    public void deleteGroup(Integer groupId) throws ClientException {
        try {
            // 1. Make the call
            getServlet().deleteGroup(groupId);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to delete group", e);
        }
    }

    public ImportLookupGroupResponse importGroup(boolean updateIfExists, String json) throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().importGroup(updateIfExists, json);

            // 2. Parse successful response
            return JsonUtils.fromJson(response, ImportLookupGroupResponse.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to import group", e);
        }
    }

    public ExportLookupGroupResponse exportGroup(Integer groupId) throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().exportGroup(groupId);

            // 2. Parse successful response
            return JsonUtils.fromJson(response, ExportLookupGroupResponse.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to export group", e);
        }
    }

    public ExportGroupPagedResponse exportGroupPaged(Integer groupId, int offset, int limit) throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().exportGroupPaged(groupId, offset, limit);

            // 2. Parse successful response
            return JsonUtils.fromJson(response, ExportGroupPagedResponse.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to export group (paged)", e);
        }
    }

    public boolean checkValueExists(Integer groupId, String key) throws ClientException {
        try {
            String response = getServlet().getValue(groupId, key);
            JsonUtils.fromJson(response, LookupValue.class);
            return true;

        } catch (ClientException e) {
            try {
                rethrowParsedClientError(e, false); // Silent mode: no logging
            } catch (LookupApiClientException ex) {
                if (LookupErrorCode.VALUE_NOT_FOUND.equalsIgnoreCase(ex.getError().getCode())) {
                    return false;
                }
                throw ex;
            }
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error while checking value existence", e);
        }
    }

    public LookupValue getValue(Integer groupId, String key) throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().getValue(groupId, key);

            // 2. Parse successful response
            return JsonUtils.fromJson(response, LookupValue.class);

        } catch (ClientException e) {
            // 3. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 4. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to get value", e);
        }
    }

    public LookupValueResponse setValue(Integer groupId, LookupValue value) throws ClientException {
        try {
            // 1. Serialize the request body
            LookupValueRequest request = new LookupValueRequest();
            request.setValue(value.getValueData());

            // 2. Make the call
            String response = getServlet().setValue(groupId, value.getKeyValue(), JsonUtils.toJson(request));

            // 3. Parse successful response
            return JsonUtils.fromJson(response, LookupValueResponse.class);

        } catch (ClientException e) {
            // 4. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 5. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to set value", e);
        }
    }

    public void deleteValue(Integer groupId, String key) throws ClientException {
        try {
            // 1. Make the call
            getServlet().deleteValue(groupId, key);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to delete value", e);
        }
    }

    public LookupAllValuesResponse getAllValues(Integer groupId, int offset, int limit, String pattern) throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().getAllValues(groupId, offset, limit, pattern);

            return JsonUtils.fromJson(response, LookupAllValuesResponse.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to get all values", e);
        }
    }

    public ImportValuesResponse importValues(Integer groupId, boolean clearExisting, Map<String, String> values) throws ClientException {
        try {
            // 1. Serialize the request body
            Map<String, Object> request = new HashMap<>();
            request.put("values", values);

            // e. Make the call
            String response = getServlet().importValues(groupId, clearExisting, JsonUtils.toJson(request));

            return JsonUtils.fromJson(response, ImportValuesResponse.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to import values", e);
        }
    }

    public GroupAuditEntriesResponse getAllAuditEntries(Integer groupId, int offset, int limit) throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().getGroupAuditEntries(groupId, offset, limit);

            return JsonUtils.fromJson(response, GroupAuditEntriesResponse.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to get all audit entries", e);
        }
    }

    public GroupAuditEntriesResponse searchAuditEntries(Integer groupId, int offset, int limit, HistoryFilterState filter) throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().searchGroupAuditEntries(groupId, offset, limit, filter.toJson());

            return JsonUtils.fromJson(response, GroupAuditEntriesResponse.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to get search audit entries", e);
        }
    }

    public GroupStatisticsResponse getGroupStatistics(Integer groupId) throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().getGroupStatistics(groupId);

            return JsonUtils.fromJson(response, GroupStatisticsResponse.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to get group statistics", e);
        }
    }

    public void resetGroupStatistics(Integer groupId) throws ClientException {
        try {
            // 1. Make the call
            getServlet().resetGroupStatistics(groupId);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to clear group statistics", e);
        }
    }

    public void clearGroupCache(Integer groupId) throws ClientException {
        try {
            // 1. Make the call
            getServlet().clearGroupCache(groupId);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to clear group cache", e);
        }
    }

    public DatabaseInfo getDatabaseInfo() throws ClientException {
        try {
            // 1. Make the call
            String response = getServlet().getDatabaseInfo();

            return JsonUtils.fromJson(response, DatabaseInfo.class);
        } catch (ClientException e) {
            // 3. Rethrow ClientException with parsed ErrorResponse if available
            rethrowParsedClientError(e);

            return null; // unreachable — rethrowParsedClientError always throws
        } catch (Exception e) {
            // 4. JSON serialization or unexpected errors
            throw new RuntimeException("Failed to get database info", e);
        }
    }

    private LookupTableServletInterface getServlet() {
        if (servlet == null) {
            Client client = PlatformUI.MIRTH_FRAME.mirthClient;
            servlet = client.getServlet(LookupTableServletInterface.class);
        }

        return servlet;
    }

    private void rethrowParsedClientError(ClientException e) throws ClientException {
        rethrowParsedClientError(e, true); // default to logging enabled
    }

    private void rethrowParsedClientError(ClientException e, boolean logError) throws ClientException {
        Throwable cause = e.getCause();

        if (cause instanceof EntityException) {
            String rawEntity = (String) ((EntityException) cause).getEntity();

            ErrorResponse error;
            try {
                error = JsonUtils.fromJson(rawEntity, ErrorResponse.class);
                if (logError) {
                    logger.error("Parsed API error: {}", JsonUtils.toJson(error));
                }
            } catch (Exception parseError) {
                if (logError) {
                    logger.error("Failed to parse server error response: {}", rawEntity, parseError);
                }
                error = new ErrorResponse("UNPARSEABLE_RESPONSE", "Failed to parse server error");
            }

            throw new LookupApiClientException(error, e);
        }

        throw e; // fallback if not structured
    }
}
