/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */
package com.innovarhealthcare.channelHistory.server.servlet;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.innovarhealthcare.channelHistory.server.controller.GitRepositoryController;
import com.innovarhealthcare.channelHistory.server.exception.GitConflictException;
import com.innovarhealthcare.channelHistory.server.exception.GitFileNotFoundException;
import com.innovarhealthcare.channelHistory.server.exception.GitNotConnectedException;
import com.innovarhealthcare.channelHistory.server.exception.GitOperationException;
import com.innovarhealthcare.channelHistory.server.exception.GitPushFailedException;
import com.innovarhealthcare.channelHistory.server.exception.VersionHistoryApiException;
import com.innovarhealthcare.channelHistory.server.service.VersionHistoryService;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.dto.request.CommitFilesRequest;
import com.innovarhealthcare.channelHistory.shared.dto.response.ErrorResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.ErrorResponseFactory;
import com.innovarhealthcare.channelHistory.shared.dto.response.LibrariesAndTemplatesResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoChanges;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoInfo;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.dto.response.RemoteStatus;
import com.innovarhealthcare.channelHistory.shared.interfaces.VersionHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryErrorCodes;
import com.innovarhealthcare.channelHistory.shared.util.JsonUtils;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.User;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.CodeTemplateController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.UserController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
public class VersionHistoryPluginServlet extends MirthServlet implements VersionHistoryServletInterface {
    private static final Logger logger = LogManager.getLogger(VersionHistoryPluginServlet.class);
    private static final UserController userController = ControllerFactory.getFactory().createUserController();
    private static final CodeTemplateController codeTemplateController = ControllerFactory.getFactory().createCodeTemplateController();
    public VersionHistoryPluginServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, VersionControlConstants.PLUGIN_POINTNAME);
    }
    @Override
    public String validateSetting(Properties properties) throws ClientException {
        try {
            return getService().validateGitConnection(properties);
        } catch (GitNotConnectedException e) {
            // Validation failed — return a 400 Bad Request with the detailed error message
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, e.getMessage());
        } catch (Exception e) {
            throw createErrorResponse(e);
        }
    }
    /**
     * Gets commit history for an entity
     *
     * @param id   Entity ID
     * @param mode Entity type: "channel", "library", "codetemplate"
     * @return JSON response with commit history
     */
    @Override
    public String getHistory(String id, String mode) {
        // Validate inputs
        if (id == null || id.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Entity ID is required");
        }
        if (mode == null || mode.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Mode is required");
        }
        logger.info("getHistory: id={}, mode={}", id, mode);
        try {
            List<CommitMetaData> history;
            // Route based on mode
            switch (mode) {
                case VersionControlConstants.MODE_CHANNEL:
                    history = getService().getChannelHistory(id);
                    break;
                case VersionControlConstants.MODE_CODE_TEMPLATE_LIBRARY:
                    history = getService().getLibraryHistory(id);
                    break;
                case VersionControlConstants.MODE_CODE_TEMPLATE:
                    history = getService().getCodeTemplateHistory(id);
                    break;
                case VersionControlConstants.MODE_GLOBAL_SCRIPTS:
                    history = getService().getGlobalScriptsHistory(id);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid mode: " + mode + ". Must be 'channel', 'library', or 'codetemplate'");
            }
            return JsonUtils.toJson(history);
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: id={}, mode={}", id, mode);
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (IllegalArgumentException e) {
            logger.error("Validation error: id={}, mode={}", id, mode);
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, e.getMessage());
        } catch (GitOperationException e) {
            logger.error("Git operation failed: id={}, mode={}", id, mode, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Failed to get commit history: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error: id={}, mode={}", id, mode, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get commit history: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    /**
     * Gets entity content from repository at a specific revision
     *
     * @param id       Entity ID
     * @param revision Commit SHA or ref (e.g., "HEAD", commit hash)
     * @param mode     Entity type: "channel", "library", "codetemplate"
     * @return Entity content as XML string
     */
    @Override
    public String getContentAtRevision(String id, String revision, String mode) {
        // Validate inputs
        if (id == null || id.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Entity ID is required");
        }
        if (revision == null || revision.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Revision is required");
        }
        if (mode == null || mode.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Mode is required");
        }
        logger.info("getContentAtRevision: id={}, revision={}, mode={}", id, revision, mode);
        try {
            // Route based on mode
            switch (mode) {
                case VersionControlConstants.MODE_CHANNEL:
                    return getService().getChannelContentAtRevision(id, revision);
                case VersionControlConstants.MODE_CODE_TEMPLATE_LIBRARY:
                    return getService().getLibraryContentAtRevision(id, revision);
                case VersionControlConstants.MODE_CODE_TEMPLATE:
                    return getService().getCodeTemplateContentAtRevision(id, revision);
                case VersionControlConstants.MODE_GLOBAL_SCRIPTS:
                    return getService().getGlobalScriptsContentAtRevision(id, revision);
                default:
                    throw new IllegalArgumentException("Invalid mode: " + mode + ". Must be 'channel', 'library', or 'codetemplate'");
            }
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: id={}, revision={}, mode={}", id, revision, mode);
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitFileNotFoundException e) {
            logger.warn("File not found: id={}, revision={}, mode={}", id, revision, mode);
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.FILE_NOT_FOUND, "File not found: " + id + " at revision: " + revision);
        } catch (IllegalArgumentException e) {
            logger.error("Validation error: id={}, revision={}, mode={}", id, revision, mode);
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, e.getMessage());
        } catch (GitOperationException e) {
            logger.error("Git operation failed: id={}, revision={}, mode={}", id, revision, mode, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Failed to get content: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error: id={}, revision={}, mode={}", id, revision, mode, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get content: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String loadChannelsMetadata() {
        try {
            List<RepoItemMetadata> metadataList = getService().loadChannelsMetadata();
            return JsonUtils.toJson(metadataList);
        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected...");
        } catch (VersionHistoryApiException e) {
            // Already a VersionHistoryApiException, rethrow as-is
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error loading channels from repo", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to load channels from repository: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String commitAndPushChannel(Channel channel, String message, String userId, boolean overwrite) {
        // Validate channel
        if (channel == null) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Channel is required");
        }
        // Validate commit message
        if (message == null || message.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Commit message is required");
        }
        // Validate and get user
        if (userId == null || userId.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "User ID is required");
        }
        User user;
        try {
            user = userController.getUser(Integer.valueOf(userId), null);
            if (user == null) {
                throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
            }
        } catch (NumberFormatException e) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Invalid user ID format: " + userId);
        } catch (ControllerException e) {
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
        }
        // Call service
        try {
            return getService().saveChannelAndPush(channel, message, user, overwrite);
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitConflictException e) {
            logger.error("Rebase conflict during commitAndPushChannel: {}", e.getMessage());
            ErrorResponse err = ErrorResponseFactory.build(VersionHistoryErrorCodes.GIT_CONFLICT, "Remote has conflicting changes. Your changes have been backed up.");
            if (!e.getBackedUpContent().isEmpty()) {
                err.setBackedUpContent(e.getBackedUpContent());
            }
            String conflictJson;
            try {
                conflictJson = JsonUtils.toJson(err);
            } catch (Exception serEx) {
                logger.error("Failed to serialize GIT_CONFLICT response: {}", serEx.getMessage(), serEx);
                throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.GIT_CONFLICT, "Remote has conflicting changes. Your changes have been backed up.");
            }
            Response conflictResponse = Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON).entity(conflictJson).build();
            throw new VersionHistoryApiException(conflictResponse);
        } catch (GitPushFailedException e) {
            logger.error("Push failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.PUSH_REJECTED, "Push rejected: " + e.getMessage());
        } catch (GitOperationException e) {
            logger.error("Git operation failed: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Git operation failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Validation failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Validation failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error saving channel", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to commit channel: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public void writeChannel(Channel channel) {
        if (channel == null) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Channel is required");
        }
        if (!getService().isGitAvailable()) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected.");
        }
        try {
            getService().writeChannelToRepo(channel);
        } catch (Exception e) {
            logger.error("Failed to write channel to working tree: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to write channel to working tree: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String loadCodeTemplatesMetadata() {
        try {
            List<RepoItemMetadata> metadataList = getService().loadCodeTemplatesMetadata();
            return JsonUtils.toJson(metadataList);
        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected...");
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error loading code templates from repo", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to load code templates from repository: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String commitAndPushCodeTemplate(String codeTemplateId, String message, String userId, boolean overwrite) {
        // Validate code template ID
        if (codeTemplateId == null || codeTemplateId.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Code template ID is required");
        }
        // Validate commit message
        if (message == null || message.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Commit message is required");
        }
        // Validate and get user
        if (userId == null || userId.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "User ID is required");
        }
        User user;
        try {
            user = userController.getUser(Integer.valueOf(userId), null);
            if (user == null) {
                throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
            }
        } catch (NumberFormatException e) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Invalid user ID format: " + userId);
        } catch (ControllerException e) {
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
        }
        // Get code template
        CodeTemplate template;
        try {
            template = codeTemplateController.getCodeTemplateById(codeTemplateId);
            if (template == null) {
                throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "Code template not found: " + codeTemplateId);
            }
        } catch (ControllerException e) {
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "Code template not found: " + codeTemplateId);
        }
        // Call service
        try {
            return getService().saveCodeTemplateAndPush(template, message, user, overwrite);
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitConflictException e) {
            logger.error("Rebase conflict during commitAndPushCodeTemplate: {}", e.getMessage());
            ErrorResponse err = ErrorResponseFactory.build(VersionHistoryErrorCodes.GIT_CONFLICT, "Remote has conflicting changes. Your changes have been backed up.");
            if (!e.getBackedUpContent().isEmpty()) {
                err.setBackedUpContent(e.getBackedUpContent());
            }
            String conflictJson;
            try {
                conflictJson = JsonUtils.toJson(err);
            } catch (Exception serEx) {
                logger.error("Failed to serialize GIT_CONFLICT response: {}", serEx.getMessage(), serEx);
                throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.GIT_CONFLICT, "Remote has conflicting changes. Your changes have been backed up.");
            }
            Response conflictResponse = Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON).entity(conflictJson).build();
            throw new VersionHistoryApiException(conflictResponse);
        } catch (GitPushFailedException e) {
            logger.error("Push failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.PUSH_REJECTED, "Push rejected: " + e.getMessage());
        } catch (GitOperationException e) {
            logger.error("Git operation failed: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Git operation failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Validation failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Validation failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error saving code template", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to commit code template: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String loadLibrariesAndTemplateMetadata() {
        try {
            LibrariesAndTemplatesResponse response = getService().loadLibrariesAndTemplateMetadata();
            return JsonUtils.toJson(response);
        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Unable to load libraries and template metadata.");
        } catch (VersionHistoryApiException e) {
            // Already a VersionHistoryApiException, rethrow as-is
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error loading libraries and template metadata", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to load libraries and template metadata: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String saveLibraries(List<CodeTemplateLibrary> libraries, String message, String userId) {
        // Validate libraries
        if (libraries == null) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Channel is required");
        }
        // Validate commit message
        if (message == null || message.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Commit message is required");
        }
        // Validate and get user
        if (userId == null || userId.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "User ID is required");
        }
        User user;
        try {
            user = userController.getUser(Integer.valueOf(userId), null);
            if (user == null) {
                throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
            }
        } catch (NumberFormatException e) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Invalid user ID format: " + userId);
        } catch (ControllerException e) {
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
        }
        try {
            return getService().saveLibrariesAndPush(libraries, message, user);
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitConflictException e) {
            logger.error("Rebase conflict during saveLibraries: {}", e.getMessage());
            ErrorResponse err = ErrorResponseFactory.build(VersionHistoryErrorCodes.GIT_CONFLICT, "Remote has conflicting changes. Your changes have been backed up.");
            if (!e.getBackedUpContent().isEmpty()) {
                err.setBackedUpContent(e.getBackedUpContent());
            }
            String conflictJson;
            try {
                conflictJson = JsonUtils.toJson(err);
            } catch (Exception serEx) {
                logger.error("Failed to serialize GIT_CONFLICT response: {}", serEx.getMessage(), serEx);
                throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.GIT_CONFLICT, "Remote has conflicting changes. Your changes have been backed up.");
            }
            Response conflictResponse = Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON).entity(conflictJson).build();
            throw new VersionHistoryApiException(conflictResponse);
        } catch (GitPushFailedException e) {
            logger.error("Push failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.PUSH_REJECTED, "Push rejected: " + e.getMessage());
        } catch (GitOperationException e) {
            logger.error("Git operation failed: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Git operation failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Validation failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Validation failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error saving libraries", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to save libraries: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String commitAndPushGlobalScripts(Map<String, String> globalScripts, String message, String userId) {
        // Validate commit message
        if (message == null || message.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Commit message is required");
        }
        // Validate and get user
        if (userId == null || userId.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "User ID is required");
        }
        User user;
        try {
            user = userController.getUser(Integer.valueOf(userId), null);
            if (user == null) {
                throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
            }
        } catch (NumberFormatException e) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Invalid user ID format: " + userId);
        } catch (ControllerException e) {
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + userId);
        }
        // Validate global scripts
        if (globalScripts == null) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Global scripts cannot be null");
        }
        if (globalScripts.isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Global scripts cannot be empty");
        }
        // Call service
        try {
            return getService().saveGlobalScriptsAndPush(globalScripts, message, user);
        } catch (GitNotConnectedException e) {
            // Git not available - 503 Service Unavailable
            logger.error("Git not connected: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitPushFailedException e) {
            // Push failed - 409 Conflict
            logger.error("Push failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.PUSH_REJECTED, "Push rejected: " + e.getMessage());
        } catch (GitOperationException e) {
            // Other Git operations failed - 500 Internal Server Error
            logger.error("Git operation failed: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Git operation failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // Validation failed (from Service layer) - 400 Bad Request
            logger.error("Validation failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Validation failed: " + e.getMessage());
        } catch (Exception e) {
            // Unexpected error - 500 Internal Server Error
            logger.error("Unexpected error saving global scripts", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to save global scripts: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String getRepoInfo() {
        try {
            RepoInfo repoInfo = getService().getRepoInfo();
            return JsonUtils.toJson(repoInfo);
        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting repo info", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get repository info: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String getRepoChanges() {
        try {
            RepoChanges repoChanges = getService().getRepoChanges();
            return JsonUtils.toJson(repoChanges);
        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting repo changes", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get repository changes: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String getFileContent(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "File path is required");
        }
        logger.info("getFileContent: filePath={}", filePath);
        try {
            return getService().getFileContent(filePath);
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: filePath={}", filePath);
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitFileNotFoundException e) {
            logger.warn("File not found in working tree: filePath={}", filePath);
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.FILE_NOT_FOUND, "File not found: " + filePath);
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting file content: filePath={}", filePath, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get file content: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String getFileContentAtHead(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "File path is required");
        }
        logger.info("getFileContentAtHead: filePath={}", filePath);
        try {
            return getService().getFileContentAtHead(filePath);
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: filePath={}", filePath);
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitFileNotFoundException e) {
            logger.warn("File not found at HEAD: filePath={}", filePath);
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.FILE_NOT_FOUND, "File not found at HEAD: " + filePath);
        } catch (GitOperationException e) {
            logger.error("Git operation failed: filePath={}", filePath, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Failed to get file content at HEAD: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting file content at HEAD: filePath={}", filePath, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get file content at HEAD: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String getRepoLog(int maxCount) {
        logger.info("getRepoLog: maxCount={}", maxCount);
        try {
            return JsonUtils.toJson(getService().getRepoLog(maxCount));
        } catch (GitNotConnectedException e) {
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitOperationException e) {
            logger.error("Git operation failed getting repo log", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Failed to get repository log: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting repo log", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get repository log: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String getCommitChanges(String commitHash) {
        if (commitHash == null || commitHash.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Commit hash is required");
        }
        logger.info("getCommitChanges: commitHash={}", commitHash);
        try {
            return JsonUtils.toJson(getService().getCommitChanges(commitHash));
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: commitHash={}", commitHash);
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitFileNotFoundException e) {
            logger.warn("Commit not found: commitHash={}", commitHash);
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.FILE_NOT_FOUND, "Commit not found: " + commitHash);
        } catch (GitOperationException e) {
            logger.error("Git operation failed: commitHash={}", commitHash, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Failed to get commit changes: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting commit changes: commitHash={}", commitHash, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get commit changes: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String getFileHistory(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "File path is required");
        }
        logger.info("getFileHistory: filePath={}", filePath);
        try {
            return JsonUtils.toJson(getService().getFileHistory(filePath));
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: filePath={}", filePath);
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitFileNotFoundException e) {
            logger.warn("File not found: filePath={}", filePath);
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.FILE_NOT_FOUND, "File not found: " + filePath);
        } catch (GitOperationException e) {
            logger.error("Git operation failed: filePath={}", filePath, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Failed to get file history: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting file history: filePath={}", filePath, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get file history: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String getFileContentAtRevision(String filePath, String commitHash) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "File path is required");
        }
        if (commitHash == null || commitHash.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Commit hash is required");
        }
        logger.info("getFileContentAtRevision: filePath={}, commitHash={}", filePath, commitHash);
        try {
            return getService().getFileContentAtRevision(filePath, commitHash);
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: filePath={}, commitHash={}", filePath, commitHash);
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitFileNotFoundException e) {
            logger.warn("File not found: filePath={}, commitHash={}", filePath, commitHash);
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.FILE_NOT_FOUND, "File not found: " + filePath + " at revision: " + commitHash);
        } catch (GitOperationException e) {
            logger.error("Git operation failed: filePath={}, commitHash={}", filePath, commitHash, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Failed to get file content at revision: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting file content at revision: filePath={}, commitHash={}", filePath, commitHash, e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get file content at revision: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String commitAndPushFiles(String requestJson) {
        CommitFilesRequest request;
        try {
            request = JsonUtils.fromJson(requestJson, CommitFilesRequest.class);
        } catch (Exception e) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Invalid request JSON: " + e.getMessage());
        }
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Commit message is required");
        }
        if (request.getFilePaths() == null || request.getFilePaths().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "At least one file path is required");
        }
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "User ID is required");
        }
        User user;
        try {
            user = userController.getUser(Integer.valueOf(request.getUserId()), null);
            if (user == null) {
                throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + request.getUserId());
            }
        } catch (NumberFormatException e) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Invalid user ID format: " + request.getUserId());
        } catch (ControllerException e) {
            throw new VersionHistoryApiException(Response.Status.NOT_FOUND, VersionHistoryErrorCodes.INVALID_REQUEST, "User not found: " + request.getUserId());
        }
        logger.info("commitAndPushFiles: {} file(s), userId={}", request.getFilePaths().size(), request.getUserId());
        try {
            getService().commitAndPushFiles(request.getFilePaths(), request.getMessage(), user);
            return JsonUtils.toJson("OK");
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: commitAndPushFiles");
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitConflictException e) {
            logger.error("Rebase conflict during commitAndPushFiles: {}", e.getMessage());
            ErrorResponse err = ErrorResponseFactory.build(VersionHistoryErrorCodes.GIT_CONFLICT, "Remote has conflicting changes. Your changes have been backed up.");
            if (!e.getBackedUpContent().isEmpty()) {
                err.setBackedUpContent(e.getBackedUpContent());
            }
            String conflictJson;
            try {
                conflictJson = JsonUtils.toJson(err);
            } catch (Exception serEx) {
                logger.error("Failed to serialize GIT_CONFLICT response: {}", serEx.getMessage(), serEx);
                throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.GIT_CONFLICT, "Remote has conflicting changes. Your changes have been backed up.");
            }
            Response conflictResponse = Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON).entity(conflictJson).build();
            throw new VersionHistoryApiException(conflictResponse);
        } catch (GitPushFailedException e) {
            logger.error("Push failed: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.PUSH_REJECTED, "Push rejected: " + e.getMessage());
        } catch (GitOperationException e) {
            logger.error("Git operation failed: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Git operation failed: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error committing files", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to commit files: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String restoreFiles(String requestJson) {
        if (requestJson == null || requestJson.trim().isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Request body is required");
        }
        Map<String, String> files;
        try {
            files = JsonUtils.getMapper().readValue(requestJson, JsonUtils.getMapper().getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (Exception e) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "Invalid request JSON: " + e.getMessage());
        }
        if (files == null || files.isEmpty()) {
            throw new VersionHistoryApiException(Response.Status.BAD_REQUEST, VersionHistoryErrorCodes.INVALID_REQUEST, "At least one file entry is required");
        }
        logger.info("restoreFiles: {} file(s)", files.size());
        try {
            getService().restoreFiles(files);
            return JsonUtils.toJson("OK");
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: restoreFiles");
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected.");
        } catch (GitOperationException e) {
            logger.error("Failed to restore files: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Failed to restore files: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error restoring files", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to restore files: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String getRemoteStatus() {
        logger.info("getRemoteStatus: fetching and computing ahead/behind counts");
        try {
            RemoteStatus status = getService().getRemoteStatus();
            return JsonUtils.toJson(status);
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: getRemoteStatus");
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitOperationException e) {
            logger.error("Git operation failed: getRemoteStatus: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Failed to get remote status: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting remote status", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Failed to get remote status: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public void pull() {
        logger.info("pull: pulling from remote (normal merge, conflicts resolved using remote)");
        try {
            getService().pullNormal();
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: pull");
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitOperationException e) {
            logger.error("Pull failed: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Pull failed: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during pull", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Pull failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    @Override
    public String push() {
        logger.info("push: pushing local commits to remote");
        try {
            getService().pushOnly();
            return JsonUtils.toJson("OK");
        } catch (GitNotConnectedException e) {
            logger.error("Git not connected: push");
            throw new VersionHistoryApiException(Response.Status.SERVICE_UNAVAILABLE, VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "Git repository is not connected. Please configure git connection first.");
        } catch (GitConflictException e) {
            logger.error("Rebase conflict during push: {}", e.getMessage());
            ErrorResponse err = ErrorResponseFactory.build(VersionHistoryErrorCodes.GIT_CONFLICT, "Remote has conflicting changes. Your changes have been backed up.");
            if (!e.getBackedUpContent().isEmpty()) {
                err.setBackedUpContent(e.getBackedUpContent());
            }
            String conflictJson;
            try {
                conflictJson = JsonUtils.toJson(err);
            } catch (Exception serEx) {
                logger.error("Failed to serialize GIT_CONFLICT response: {}", serEx.getMessage(), serEx);
                throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.GIT_CONFLICT, "Remote has conflicting changes.");
            }
            Response conflictResponse = Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON).entity(conflictJson).build();
            throw new VersionHistoryApiException(conflictResponse);
        } catch (GitPushFailedException e) {
            logger.error("Push rejected: {}", e.getMessage());
            throw new VersionHistoryApiException(Response.Status.CONFLICT, VersionHistoryErrorCodes.PUSH_REJECTED, "Push rejected: " + e.getMessage());
        } catch (GitOperationException e) {
            logger.error("Git operation failed: push: {}", e.getMessage(), e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.GIT_OPERATION_ERROR, "Push failed: " + e.getMessage());
        } catch (VersionHistoryApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during push", e);
            throw new VersionHistoryApiException(Response.Status.INTERNAL_SERVER_ERROR, VersionHistoryErrorCodes.UNKNOWN_ERROR, "Push failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
    private VersionHistoryService getService() {
        return GitRepositoryController.getInstance().getVersionHistoryService();
    }
    /**
     * Create structured error response from exception
     * Maps domain exceptions to user-friendly error codes and messages
     */
    private ClientException createErrorResponse(Exception e) {
        ErrorResponse error;
        // Map specific exceptions
        if (e instanceof GitNotConnectedException) {
            error = new ErrorResponse(VersionHistoryErrorCodes.GIT_NOT_CONNECTED, "it repository is not connected...");
        } else if (e instanceof FileNotFoundException) {
            error = new ErrorResponse(VersionHistoryErrorCodes.FILE_NOT_FOUND, "File not found in repository: " + e.getMessage());
        } else if (e instanceof ClientException) {
            // Re-throw ClientException as-is (for validation errors)
            return (ClientException) e;
        } else {
            // Default error for unexpected exceptions
            error = new ErrorResponse(VersionHistoryErrorCodes.UNKNOWN_ERROR, e.getMessage() != null ? e.getMessage() : "An unexpected error occurred");
        }
        try {
            return new ClientException(JsonUtils.toJson(error), e);
        } catch (Exception jsonError) {
            // Fallback if JSON serialization fails
            logger.error("Failed to serialize error response", jsonError);
            return new ClientException(error.getMessage(), e);
        }
    }
}
