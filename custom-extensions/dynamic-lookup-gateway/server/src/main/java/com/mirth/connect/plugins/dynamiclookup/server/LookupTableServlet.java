package com.mirth.connect.plugins.dynamiclookup.server;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.model.User;
import com.mirth.connect.plugins.dynamiclookup.server.exception.DuplicateGroupNameException;
import com.mirth.connect.plugins.dynamiclookup.server.exception.GroupNotFoundException;
import com.mirth.connect.plugins.dynamiclookup.server.exception.LookupApiException;
import com.mirth.connect.plugins.dynamiclookup.server.service.LookupService;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.LookupModelMapper;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.request.*;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.*;
import com.mirth.connect.plugins.dynamiclookup.shared.model.*;
import com.mirth.connect.plugins.dynamiclookup.shared.interfaces.LookupTableServletInterface;

import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;
import com.mirth.connect.plugins.dynamiclookup.shared.util.LookupErrorCode;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.client.core.ClientException;

import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.UserController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import java.util.*;
import java.util.stream.Collectors;

public class LookupTableServlet extends MirthServlet implements LookupTableServletInterface {
    private final Logger logger = LogManager.getLogger(this.getClass());

    private static final UserController userController = ControllerFactory.getFactory().createUserController();

    public LookupTableServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, "Lookup Table Management System");
    }

    @Override
    public String getAllGroups() throws ClientException {
        try {
            List<LookupGroup> groups = LookupService.getInstance().getAllGroups();

            return JsonUtils.toJson(groups);
        } catch (Exception e) {
            // Wrap and rethrow any exception as a client-facing exception
            // Could be validation, parsing, DB, or serialization errors
            throw new ClientException("Failed to process getAllGroups request. Error:  " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String getGroupById(Integer groupId) throws ClientException {
        try {
            LookupGroup group = LookupService.getInstance().getGroupById(groupId);

            if (group == null) {
                throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
            }

            return JsonUtils.toJson(group);

        } catch (LookupApiException e) {
            // Rethrow directly if it's already our custom API exception
            throw e;

        } catch (Exception e) {
            // Catch-all for unexpected internal errors
            throw new ClientException("Failed to process getGroupById request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String getGroupByName(String name) throws ClientException {
        try {
            if (name == null || name.trim().isEmpty()) {
                throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Group name must not be empty.");
            }

            LookupGroup group = LookupService.getInstance().getGroupByName(name);

            if (group == null) {
                throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with name: " + name);
            }

            return JsonUtils.toJson(group);
        } catch (LookupApiException e) {
            // Rethrow directly if it's already our custom API exception
            throw e;
        } catch (Exception e) {
            throw new ClientException("Failed to process getGroupByName request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String createGroup(String requestBody) throws ClientException {
        try {
            // Step 1: Parse incoming JSON string into a request DTO
            LookupGroupRequest request;
            try {
                request = JsonUtils.fromJson(requestBody, LookupGroupRequest.class);
            } catch (Exception e) {
                throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Invalid JSON format for group request: " + e.getMessage());
            }

            // Step 2: Validate required fields in the input
            try {
                request.validate();
            } catch (IllegalArgumentException e) {
                throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Validation failed: " + e.getMessage());
            }

            // Step 3: Map DTO to domain object
            LookupGroup group = LookupModelMapper.fromGroupDto(request);

            // Step 4: Try to create the group
            int groupId;
            try {
                groupId = LookupService.getInstance().createGroup(group);
            } catch (DuplicateGroupNameException e) {
                throw new LookupApiException(Response.Status.CONFLICT, LookupErrorCode.DUPLICATE_GROUP_NAME, "Group name already exists: " + group.getName());
            } catch (Exception e) {
                throw new LookupApiException(Response.Status.INTERNAL_SERVER_ERROR, LookupErrorCode.DATABASE_ERROR, "Database error while creating group: " + e.getMessage());
            }

            // Step 5: Load full persisted group for response
            LookupGroup fullGroup = LookupService.getInstance().getGroupById(groupId);
            if (fullGroup == null) {
                throw new LookupApiException(Response.Status.INTERNAL_SERVER_ERROR, LookupErrorCode.GROUP_NOT_FOUND, "Group created but could not be retrieved with ID: " + groupId);
            }

            // Step 6: Return JSON
            return JsonUtils.toJson(fullGroup);
        } catch (LookupApiException e) {
            // Rethrow cleanly to let the Web API layer handle it
            throw e;
        } catch (Exception e) {
            throw new ClientException("Failed to process createGroup request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String updateGroup(Integer groupId, String requestBody) throws ClientException {
        try {
            // Step 1: Parse incoming JSON string into a request DTO
            LookupGroupRequest request;
            try {
                request = JsonUtils.fromJson(requestBody, LookupGroupRequest.class);
            } catch (Exception e) {
                throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Invalid JSON format for group request: " + e.getMessage());
            }

            // Step 2: Validate required fields in the input
            try {
                request.validate();
            } catch (IllegalArgumentException e) {
                throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Validation failed: " + e.getMessage());
            }

            // Step 3: Map DTO to domain object
            LookupGroup group = LookupModelMapper.fromGroupDto(request);
            group.setId(groupId);

            // Step 4: Try to update the group
            try {
                LookupService.getInstance().updateGroup(group);
            } catch (GroupNotFoundException e) {
                throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
            } catch (DuplicateGroupNameException e) {
                throw new LookupApiException(Response.Status.CONFLICT, LookupErrorCode.DUPLICATE_GROUP_NAME, "Group name already exists: " + group.getName());
            } catch (Exception e) {
                throw new LookupApiException(Response.Status.INTERNAL_SERVER_ERROR, LookupErrorCode.DATABASE_ERROR, "Database error while updating group: " + e.getMessage());
            }

            // Step 5: Load full persisted group for response
            LookupGroup fullGroup = LookupService.getInstance().getGroupById(groupId);
            if (fullGroup == null) {
                throw new LookupApiException(Response.Status.INTERNAL_SERVER_ERROR, LookupErrorCode.GROUP_NOT_FOUND, "Group created but could not be retrieved with ID: " + groupId);
            }

            // Step 6: Return JSON
            return JsonUtils.toJson(fullGroup);
        } catch (LookupApiException e) {
            // Rethrow cleanly to let the Web API layer handle it
            throw e;
        } catch (Exception e) {
            throw new ClientException("Failed to process updateGroup request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public void deleteGroup(Integer groupId) throws ClientException {
        try {
            LookupService.getInstance().deleteGroup(groupId);
        } catch (GroupNotFoundException e) {
            throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
        }
    }

    @Override
    public String exportGroup(Integer groupId) throws ClientException {
        try {
            Map<String, String> values = LookupService.getInstance().getAllValues(groupId);

            LookupGroup group = LookupService.getInstance().getGroupById(groupId);
            Date now = new Date();  // current UTC timestamp

            ExportLookupGroupResponse response = new ExportLookupGroupResponse(group, values, now);

            return JsonUtils.toJson(response);
        } catch (GroupNotFoundException e) {
            throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
        } catch (Exception e) {
            throw new LookupApiException(Response.Status.INTERNAL_SERVER_ERROR, LookupErrorCode.DATABASE_ERROR, "Database error while creating group: " + e.getMessage());
        }
    }

    @Override
    public String importGroup(boolean updateIfExists, String requestBody) throws ClientException {
        try {
            // Step 1: Parse and validate JSON input
            ImportLookupGroupRequest request;
            try {
                request = JsonUtils.fromJson(requestBody, ImportLookupGroupRequest.class);
                request.validate();
            } catch (Exception e) {
                throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Invalid import request: " + e.getMessage());
            }

            // Step 2: Import logic
            LookupGroup group = request.getGroup();
            Map<String, String> values = request.getValues();

            // Step 3: get current user id
            String userId = String.valueOf(getCurrentUserId());

            // Delegating to service
            int count;
            try {
                count = LookupService.getInstance().importGroup(group, values, updateIfExists, userId);
            } catch (DuplicateGroupNameException e) {
                throw new LookupApiException(Response.Status.CONFLICT, LookupErrorCode.DUPLICATE_GROUP_NAME, "Group name already exists: " + group.getName());
            } catch (Exception e) {
                throw new LookupApiException(Response.Status.INTERNAL_SERVER_ERROR, LookupErrorCode.DATABASE_ERROR, "Database error while import group: " + e.getMessage());
            }

            // Step 4: Build response DTO
            ImportLookupGroupResponse response = ImportLookupGroupResponse.fromResult(group.getId(), count, Collections.emptyList());

            return JsonUtils.toJson(response);
        } catch (LookupApiException e) {
            // Rethrow cleanly to let the Web API layer handle it
            throw e;
        } catch (Exception e) {
            throw new ClientException("Failed to import lookup group: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String getAllValues(Integer groupId, Integer offset, Integer limit, String pattern) throws ClientException {
        try {
            // Validate group first
            LookupGroup lookupGroup = LookupService.getInstance().getGroupById(groupId);
            if (lookupGroup == null) {
                throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
            }

            // Normalize pagination and pattern
            int safeOffset = offset != null ? offset : 0;
            int safeLimit = limit != null ? limit : Integer.MAX_VALUE;
            String safePattern = (pattern != null) ? pattern.trim() : null;

            Map<String, String> paginated = LookupService.getInstance().searchLookupValues(groupId, safeOffset, safeLimit, safePattern);
            int totalCount = LookupService.getInstance().searchLookupValuesCount(groupId, safePattern);

            LookupAllValuesResponse response = LookupAllValuesResponse.fromResult(
                    groupId,
                    lookupGroup.getName(),
                    totalCount,
                    paginated,
                    safeLimit,
                    safeOffset
            );

            return JsonUtils.toJson(response);
        } catch (GroupNotFoundException e) {
            throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
        } catch (LookupApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException("Failed to process getAllValues request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String getValue(Integer groupId, String key) throws ClientException {
        try {
            // Step 1: Return JSON
            String value = LookupService.getInstance().getValue(groupId, key);

            if (value == null) {
                throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.VALUE_NOT_FOUND, "Lookup value not found with key: " + key);
            }

            LookupValue lookupValue = new LookupValue(key, value);

            // Step 2: Return JSON
            return JsonUtils.toJson(LookupModelMapper.toValueResponse(lookupValue, groupId, false));
        } catch (GroupNotFoundException e) {
            throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
        } catch (LookupApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException("Failed to process getValue request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String setValue(Integer groupId, String key, String requestBody) {
        try {
            // Step 1: Parse incoming JSON string into a request DTO
            LookupValueRequest request;
            try {
                request = JsonUtils.fromJson(requestBody, LookupValueRequest.class);
            } catch (Exception e) {
                throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Invalid JSON format for value request: " + e.getMessage());
            }

            // Step 2: Validate required fields in the input
            try {
                request.validate();
            } catch (IllegalArgumentException e) {
                throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Validation failed: " + e.getMessage());
            }

            // Step 3: get current user id
            String userId = String.valueOf(getCurrentUserId());

            // Step 4: Try to set value
            try {
                LookupService.getInstance().setValue(groupId, key, request.getValue(), userId);
            } catch (GroupNotFoundException e) {
                throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
            } catch (Exception e) {
                throw new LookupApiException(Response.Status.INTERNAL_SERVER_ERROR, LookupErrorCode.DATABASE_ERROR, "Database error while set value: " + e.getMessage());
            }

            // Step 5: Load full persisted value for response
            LookupValue lookupValue = LookupService.getInstance().getLookupValue(groupId, key);
            if (lookupValue == null) {
                throw new LookupApiException(Response.Status.INTERNAL_SERVER_ERROR, LookupErrorCode.GROUP_NOT_FOUND, "The value was saved, but failed to retrieve it afterward. groupId=" + groupId + ", key=" + key);
            }

            // Step 6: Return JSON
            return JsonUtils.toJson(LookupModelMapper.toValueResponse(lookupValue, groupId));

        } catch (LookupApiException e) {
            throw e;
        } catch (Exception e) {
//            throw new ClientException("Failed to process setValue request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public void deleteValue(Integer groupId, String key) throws ClientException {
        try {
            String userId = String.valueOf(getCurrentUserId());
            LookupService.getInstance().deleteValue(groupId, key, userId);
        } catch (GroupNotFoundException e) {
            throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
        }
    }

    @Override
    public String importValues(Integer groupId, boolean clearExisting, String requestBody) {
        // Step 1: Parse incoming JSON string into a request DTO
        ImportValuesRequest request;
        try {
            request = JsonUtils.fromJson(requestBody, ImportValuesRequest.class);
        } catch (Exception e) {
            throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Invalid JSON format for import values request: " + e.getMessage());
        }

        // Step 2: Validate required fields in the input
        try {
            request.validate();
        } catch (IllegalArgumentException e) {
            throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Validation failed: " + e.getMessage());
        }

        // Step 3: Map DTO to domain object
        Map<String, String> values = LookupModelMapper.fromImportValuesDto(request);

        // Step 4: Try to create the group
        String userId = String.valueOf(getCurrentUserId());
        try {
            int importedCount = LookupService.getInstance().importValues(groupId, values, clearExisting, userId);

            ImportValuesResponse response = new ImportValuesResponse();
            response.setGroupId(groupId);
            response.setStatus("success");
            response.setImportedCount(importedCount);
            response.setErrors(Collections.emptyList());

            return JsonUtils.toJson(response);
        } catch (GroupNotFoundException e) {
            throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
        } catch (Exception e) {
            throw new LookupApiException(Response.Status.INTERNAL_SERVER_ERROR, LookupErrorCode.DATABASE_ERROR, "Database error while importing values: " + e.getMessage());
        }
    }

    @Override
    public String batchGetValues(Integer groupId, String requestBody) throws ClientException {
        // Step 1: Parse incoming JSON string into a request DTO
        BatchGetValuesRequest request;
        try {
            request = JsonUtils.fromJson(requestBody, BatchGetValuesRequest.class);
        } catch (Exception e) {
            throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Invalid JSON format for import values request: " + e.getMessage());
        }

        // Step 2: Validate required fields in the input
        try {
            request.validate();
        } catch (IllegalArgumentException e) {
            throw new LookupApiException(Response.Status.BAD_REQUEST, LookupErrorCode.INVALID_REQUEST, "Validation failed: " + e.getMessage());
        }

        // Step 3: Map DTO to domain object
        List<String> keys = LookupModelMapper.fromBatchGetValues(request);

        // Step 4: Try to create the group
        try {
            Map<String, String> map = LookupService.getInstance().getBatchValues(groupId, keys);

            BatchGetValuesResponse response = new BatchGetValuesResponse();
            response.setGroupId(groupId);
            response.setValues(map);

            List<String> missingKeys = keys.stream()
                    .filter(k -> !map.containsKey(k))
                    .collect(Collectors.toList());

            response.setMissingKeys(missingKeys);

            return JsonUtils.toJson(response);
        } catch (GroupNotFoundException e) {
            throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
        } catch (Exception e) {
            throw new LookupApiException(Response.Status.INTERNAL_SERVER_ERROR, LookupErrorCode.DATABASE_ERROR, "Database error while creating group: " + e.getMessage());
        }
    }

    @Override
    public String getGroupStatistics(Integer groupId) throws ClientException {
        try {
            LookupStatistics dbStats = LookupService.getInstance().getStatistics(groupId);
            CacheStatistics cacheStatistics = LookupService.getInstance().getCacheStatistics(groupId);

            GroupStatisticsResponse response = GroupStatisticsResponse.fromResult(dbStats, cacheStatistics);

            return JsonUtils.toJson(response);

        } catch (GroupNotFoundException e) {
            throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
        } catch (Exception e) {
            // Catch-all for unexpected internal errors
            throw new ClientException("Failed to process getGroupStatistics request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String resetGroupStatistics(Integer groupId) throws ClientException {
        try {
            LookupService.getInstance().resetStatistics(groupId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Statistics reset successfully for group ID: " + groupId);

            return JsonUtils.toJson(response);

        } catch (GroupNotFoundException e) {
            throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
        } catch (Exception e) {
            // Catch-all for unexpected internal errors
            throw new ClientException("Failed to process getGroupStatistics request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String getGroupAuditEntries(Integer groupId, Integer offset, Integer limit) throws ClientException {
        try {
            // Validate group first
            LookupGroup lookupGroup = LookupService.getInstance().getGroupById(groupId);
            if (lookupGroup == null) {
                throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
            }

            // Normalize pagination and pattern
            int safeOffset = offset != null ? offset : 0;
            int safeLimit = limit != null ? limit : Integer.MAX_VALUE;

            List<LookupAudit> entries = LookupService.getInstance().getAuditEntries(groupId, safeOffset, safeLimit);
            int totalCount = LookupService.getInstance().getAuditEntryCount(groupId);

            List<User> users = userController.getAllUsers();

            GroupAuditEntriesResponse response = GroupAuditEntriesResponse.fromResult(groupId, entries, totalCount, safeLimit, safeOffset, users);

            return JsonUtils.toJson(response);

        } catch (LookupApiException e) {
            throw e;
        } catch (Exception e) {
            // Catch-all for unexpected internal errors
            throw new ClientException("Failed to process getGroupAuditEntries request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String searchGroupAuditEntries(Integer groupId, Integer offset, Integer limit, String filterState) throws ClientException {
        try {
            HistoryFilterState filter = (filterState != null && !filterState.isEmpty())
                    ? HistoryFilterState.fromJson(filterState)
                    : new HistoryFilterState();

            // Validate group first
            LookupGroup lookupGroup = LookupService.getInstance().getGroupById(groupId);
            if (lookupGroup == null) {
                throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
            }

            // Normalize pagination and pattern
            int safeOffset = offset != null ? offset : 0;
            int safeLimit = limit != null ? limit : 100;

            List<LookupAudit> entries = LookupService.getInstance().searchAuditEntries(groupId, safeOffset, safeLimit, filter);
            int totalCount = LookupService.getInstance().searchAuditEntryCount(groupId, filter);

            List<User> users = userController.getAllUsers();

            GroupAuditEntriesResponse response = GroupAuditEntriesResponse.fromResult(groupId, entries, totalCount, safeLimit, safeOffset, users);

            return JsonUtils.toJson(response);
        } catch (LookupApiException e) {
            throw e;
        } catch (Exception e) {
            // Catch-all for unexpected internal errors
            throw new ClientException("Failed to process searchGroupAuditEntries request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String clearGroupCache(Integer groupId) throws ClientException {
        try {
            LookupService.getInstance().clearGroupCache(groupId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Cache cleared successfully for group ID: " + groupId);

            return JsonUtils.toJson(response);

        } catch (GroupNotFoundException e) {
            throw new LookupApiException(Response.Status.NOT_FOUND, LookupErrorCode.GROUP_NOT_FOUND, "Lookup group not found with ID: " + groupId);
        } catch (Exception e) {
            // Catch-all for unexpected internal errors
            throw new ClientException("Failed to process getGroupStatistics request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public String clearAllCaches() throws ClientException {
        try {
            LookupService.getInstance().clearAllCaches();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "All caches cleared successfully");

            return JsonUtils.toJson(response);

        } catch (Exception e) {
            throw new ClientException("Failed to process clearAllCaches request. Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }
}
