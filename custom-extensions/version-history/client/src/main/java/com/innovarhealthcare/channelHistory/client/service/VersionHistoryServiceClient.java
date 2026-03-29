/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.service;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.innovarhealthcare.channelHistory.client.exception.GitConflictClientException;
import com.innovarhealthcare.channelHistory.client.exception.VersionHistoryClientException;
import com.innovarhealthcare.channelHistory.client.model.ChannelWithRaw;
import com.innovarhealthcare.channelHistory.client.model.CodeTemplateWithRaw;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.dto.request.CommitFilesRequest;
import com.innovarhealthcare.channelHistory.shared.dto.response.ErrorResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.LibrariesAndTemplatesResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoChanges;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoInfo;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemChange;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.interfaces.VersionHistoryServletInterface;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryErrorCodes;
import com.innovarhealthcare.channelHistory.shared.util.JsonUtils;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.EntityException;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.donkey.util.xstream.SerializerException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VersionHistoryServiceClient {
    private static VersionHistoryServiceClient instance = null;
    private final Logger logger = LogManager.getLogger(this.getClass());

    public static VersionHistoryServiceClient getInstance() {
        synchronized (VersionHistoryServiceClient.class) {
            if (instance == null) {
                instance = new VersionHistoryServiceClient();
            }
            return instance;
        }
    }

    private VersionHistoryServiceClient() {
    }

    /**
     * Validate the Git remote repository settings on the server
     *
     * @param properties Git settings as Properties (url, branch, ssh key/path)
     * @return Server response message
     * @throws ClientException if the settings are invalid or the connection fails
     */
    public String validateSetting(Properties properties) throws ClientException {
        try {
            return getServlet().validateSetting(properties);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to validate Git settings: " + e.getMessage(), e);
        }
    }

    /**
     * Load complete commit history for a channel
     *
     * @param channelId Channel UUID
     * @return List of commit history entries (newest first)
     * @throws ClientException if channel not found or Git error occurs
     */
    public List<CommitMetaData> loadChannelHistory(String channelId) throws ClientException {
        try {
            // 1. Make the call
            String jsonResponse = getServlet().getHistory(channelId, VersionControlConstants.MODE_CHANNEL);
            return JsonUtils.fromJsonList(jsonResponse, CommitMetaData.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new ClientException("Failed to load channel history", e);
        }
    }

    /**
     * Load channel list metadata from repository
     *
     * @return List of channel metadata (id, name, path, lastCommitId)
     * @throws ClientException if Git operations fail
     */
    public List<RepoItemMetadata> loadChannelListFromRepo() throws ClientException {
        try {
            // 1. Make the call
            String jsonResponse = getServlet().loadChannelsMetadata();
            return JsonUtils.fromJsonList(jsonResponse, RepoItemMetadata.class);
        } catch (ClientException e) {
            // 2. Rethrow ClientException with parsed ErrorResponse if available
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            // 3. JSON serialization or unexpected errors
            throw new ClientException("Failed to load channel list from repo: " + e.getMessage(), e);
        }
    }

    /**
     * Load channel from repository at specific revision
     *
     * @param channelId Channel UUID
     * @param revision  Git revision (typically metadata.getLastCommitId())
     * @return Channel object
     * @throws ClientException if channel not found, invalid, or Git error occurs
     */
    public Channel loadChannelFromRepo(String channelId, String revision) throws ClientException {
        try {
            String xmlContent = loadChannelRawContentFromRepo(channelId, revision);
            Channel channel = ObjectXMLSerializer.getInstance().deserialize(xmlContent, Channel.class);

            if (channel == null) {
                throw new ClientException("Failed to deserialize channel content for ID: " + channelId);
            }

            if (channel instanceof InvalidChannel) {
                InvalidChannel invalidChannel = (InvalidChannel) channel;
                String errorMsg = invalidChannel.getCause() != null ? invalidChannel.getCause().getMessage() : "Unknown deserialization error";
                throw new ClientException("Invalid channel content for ID: " + channelId + ". Error: " + errorMsg);
            }

            return channel;

        } catch (SerializerException e) {
            throw new ClientException("Failed to deserialize channel XML for ID: " + channelId + ". " + e.getMessage(), e);
        }
    }

    /**
     * Load Channel object with raw content from repository
     * Use this when you need both the Channel object and raw XML content (e.g., for diff comparison)
     */
    public ChannelWithRaw loadChannelWithRawFromRepo(String channelId, String revision) throws ClientException {
        try {
            String xmlContent = loadChannelRawContentFromRepo(channelId, revision);
            Channel channel = ObjectXMLSerializer.getInstance().deserialize(xmlContent, Channel.class);

            if (channel == null) {
                throw new ClientException("Failed to deserialize channel content for ID: " + channelId);
            }

            if (channel instanceof InvalidChannel) {
                InvalidChannel invalidChannel = (InvalidChannel) channel;
                String errorMsg = invalidChannel.getCause() != null ? invalidChannel.getCause().getMessage() : "Unknown deserialization error";
                throw new ClientException("Invalid channel content for ID: " + channelId + ". Error: " + errorMsg);
            }

            return new ChannelWithRaw(channel, xmlContent);

        } catch (SerializerException e) {
            throw new ClientException("Failed to deserialize channel XML for ID: " + channelId + ". " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method - load channel using metadata
     *
     * @param metadata Channel metadata from loadChannelListOnRepo()
     */
    public Channel loadChannelFromRepo(RepoItemMetadata metadata) throws ClientException {
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }
        return loadChannelFromRepo(metadata.getId(), metadata.getLastCommitId());
    }

    /**
     * Commit and push a channel to the repository
     *
     * @param channel The channel object to commit
     * @param message User's commit message describing the changes
     * @param userId  The user ID performing the commit
     * @return String containing the operation result and commit information
     * @throws ClientException if channel is invalid, commit fails, or push operation fails
     */
    /**
     * Writes a channel to the Git working tree without committing or pushing.
     * Used when autoCommit=false to keep the working tree in sync after each save,
     * so the Changes tab reflects pending changes correctly.
     * Errors are intentionally not surfaced to the user — the write is best-effort.
     *
     * @param channel The channel object to write
     * @throws ClientException if Git is not connected or writing fails
     */
    public void writeChannel(Channel channel) throws ClientException {
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be null");
        }
        try {
            getServlet().writeChannel(channel);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to write channel to working tree: " + e.getMessage(), e);
        }
    }

    /**
     * Commit and push a channel to the repository.
     *
     * @param channel   The channel object to commit
     * @param message   Commit message
     * @param userId    The user ID performing the commit
     * @param overwrite true  = auto-commit strategy (pullWithOverwrite, silent on conflict)
     *                  false = manual-commit strategy (rebase+backup, throws
     *                  {@link GitConflictClientException} on conflict)
     * @throws GitConflictClientException if overwrite=false and remote has conflicting changes
     * @throws ClientException            if commit or push fails for any other reason
     */
    public String commitAndPushChannel(Channel channel, String message, String userId, boolean overwrite) throws ClientException {
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be null");
        }
        if (StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("Commit message cannot be null or empty");
        }
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        try {
            return getServlet().commitAndPushChannel(channel, message, userId, overwrite);
        } catch (ClientException e) {
            ClientException parsed = rethrowParsedClientError(e, true);
            if (!overwrite && parsed instanceof VersionHistoryClientException) {
                VersionHistoryClientException vhe = (VersionHistoryClientException) parsed;
                if (VersionHistoryErrorCodes.GIT_CONFLICT.equals(vhe.getError().getCode())) {
                    throw new GitConflictClientException(vhe.getError(), e);
                }
            }
            throw parsed;
        } catch (Exception e) {
            throw new ClientException("Failed to commit and push channel: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience overload for auto-commit (overwrite=true, backward compat).
     */
    public String commitAndPushChannel(Channel channel, String message, String userId) throws ClientException {
        return commitAndPushChannel(channel, message, userId, true);
    }

    public String saveLibraries(List<CodeTemplateLibrary> libraries, String message, String userId) throws ClientException {
        if (libraries == null) {
            throw new IllegalArgumentException("libraries cannot be null");
        }
        if (StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("Commit message cannot be null or empty");
        }
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        try {
            return getServlet().saveLibraries(libraries, message, userId);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to save and push libraries: " + e.getMessage(), e);
        }
    }

    /**
     * Load libraries and code template metadata from repository
     * Returns both library information and template metadata for client-side grouping
     *
     * @return LibrariesAndTemplatesResponse containing libraries and template metadata
     * @throws ClientException if Git operations fail
     */
    public LibrariesAndTemplatesResponse loadLibrariesAndTemplateMetadata() throws ClientException {
        try {
            String jsonResponse = getServlet().loadLibrariesAndTemplateMetadata();
            return JsonUtils.fromJson(jsonResponse, LibrariesAndTemplatesResponse.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load libraries and template metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Load complete commit history for a code template
     *
     * @param codeTemplateId Code template UUID
     * @return List of commit history entries (newest first)
     * @throws ClientException if code template not found or Git error occurs
     */
    public List<CommitMetaData> loadCodeTemplateHistory(String codeTemplateId) throws ClientException {
        if (StringUtils.isBlank(codeTemplateId)) {
            throw new ClientException("Code template ID cannot be null or empty");
        }

        try {
            String jsonResponse = getServlet().getHistory(codeTemplateId, VersionControlConstants.MODE_CODE_TEMPLATE);
            return JsonUtils.fromJsonList(jsonResponse, CommitMetaData.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load code template history: " + e.getMessage(), e);
        }
    }

    /**
     * Load code template list metadata from repository
     *
     * @return List of code template metadata (id, name, path, lastCommitId)
     * @throws ClientException if Git operations fail
     */
    public List<RepoItemMetadata> loadCodeTemplateListFromRepo() throws ClientException {
        try {
            String jsonResponse = getServlet().loadCodeTemplatesMetadata();
            return JsonUtils.fromJsonList(jsonResponse, RepoItemMetadata.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load code template list from repo: " + e.getMessage(), e);
        }
    }

    /**
     * Load code template from repository at specific revision
     *
     * @param templateId Code template UUID
     * @param revision   Git revision (typically metadata.getLastCommitId())
     * @return CodeTemplate object
     * @throws ClientException if template not found, invalid, or Git error occurs
     */
    public CodeTemplate loadCodeTemplateFromRepo(String templateId, String revision) throws ClientException {
        try {
            String xmlContent = loadCodeTemplateRawContentFromRepo(templateId, revision);
            CodeTemplate template = ObjectXMLSerializer.getInstance().deserialize(xmlContent, CodeTemplate.class);

            if (template == null) {
                throw new ClientException("Failed to deserialize code template content for ID: " + templateId);
            }

            return template;

        } catch (SerializerException e) {
            throw new ClientException("Failed to deserialize code template XML for ID: " + templateId + ". " + e.getMessage(), e);
        }
    }

    /**
     * Load code template with raw XML content from repository
     *
     * @param templateId Code template UUID
     * @param revision   Git revision (typically metadata.getLastCommitId())
     * @return CodeTemplateWithRaw object containing both CodeTemplate and raw XML
     * @throws ClientException if template not found, invalid, or Git error occurs
     */
    public CodeTemplateWithRaw loadCodeTemplateWithRawFromRepo(String templateId, String revision) throws ClientException {
        try {
            String xmlContent = loadCodeTemplateRawContentFromRepo(templateId, revision);
            CodeTemplate template = ObjectXMLSerializer.getInstance().deserialize(xmlContent, CodeTemplate.class);

            if (template == null) {
                throw new ClientException("Failed to deserialize code template content for ID: " + templateId);
            }

            return new CodeTemplateWithRaw(template, xmlContent);
        } catch (SerializerException e) {
            throw new ClientException("Failed to deserialize code template XML for ID: " + templateId + ". " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method - load code template using metadata
     *
     * @param metadata Code template metadata from loadCodeTemplateListFromRepo()
     */
    public CodeTemplate loadCodeTemplateFromRepo(RepoItemMetadata metadata) throws ClientException {
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }
        return loadCodeTemplateFromRepo(metadata.getId(), metadata.getLastCommitId());
    }

    /**
     * Commit and push a code template to the repository
     *
     * @param codeTemplateId The code template ID (UUID) to commit
     * @param message        User's commit message describing the changes
     * @param userId         The user ID performing the commit
     * @return String containing the operation result and commit information
     * @throws ClientException if code template is invalid, commit fails, or push operation fails
     */
    public String commitAndPushCodeTemplate(String codeTemplateId, String message, String userId, boolean overwrite) throws ClientException {
        if (StringUtils.isBlank(codeTemplateId)) {
            throw new IllegalArgumentException("Code template ID cannot be null or empty");
        }
        if (StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("Commit message cannot be null or empty");
        }
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        try {
            return getServlet().commitAndPushCodeTemplate(codeTemplateId, message, userId, overwrite);
        } catch (ClientException e) {
            ClientException parsed = rethrowParsedClientError(e, true);
            if (!overwrite && parsed instanceof VersionHistoryClientException) {
                VersionHistoryClientException vhe = (VersionHistoryClientException) parsed;
                if (VersionHistoryErrorCodes.GIT_CONFLICT.equals(vhe.getError().getCode())) {
                    throw new GitConflictClientException(vhe.getError(), e);
                }
            }
            throw parsed;
        } catch (Exception e) {
            throw new ClientException("Failed to commit and push code template: " + e.getMessage(), e);
        }
    }

    /**
     * Commit and push global scripts to the repository
     *
     * @param globalScripts Map of global scripts (Deploy, Undeploy, Preprocessor, Postprocessor) to commit
     * @param message       User's commit message describing the changes
     * @param userId        The user ID performing the commit
     * @return String containing the operation result and commit information
     * @throws ClientException if global scripts are invalid, commit fails, or push operation fails
     */
    public String commitAndPushGlobalScripts(Map<String, String> globalScripts, String message, String userId) throws ClientException {
        if (globalScripts == null) {
            throw new IllegalArgumentException("Global scripts cannot be null");
        }
        if (globalScripts.isEmpty()) {
            throw new IllegalArgumentException("Global scripts cannot be empty");
        }
        if (StringUtils.isBlank(message)) {
            throw new IllegalArgumentException("Commit message cannot be null or empty");
        }
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        try {
            return getServlet().commitAndPushGlobalScripts(globalScripts, message, userId);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to commit and push global scripts: " + e.getMessage(), e);
        }
    }

    /**
     * Load complete commit history for global scripts
     *
     * @return List of commit history entries (newest first)
     * @throws ClientException if Git error occurs or global scripts not found
     */
    public List<CommitMetaData> loadGlobalScriptsHistory() throws ClientException {
        try {
            String jsonResponse = getServlet().getHistory("scripts", VersionControlConstants.MODE_GLOBAL_SCRIPTS);
            return JsonUtils.fromJsonList(jsonResponse, CommitMetaData.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load global scripts history: " + e.getMessage(), e);
        }
    }

    /**
     * Load global scripts from repository at specific revision
     *
     * @param revision Git revision (commit hash)
     * @return Map of global scripts (Deploy, Undeploy, Preprocessor, Postprocessor)
     * @throws ClientException if global scripts not found, invalid, or Git error occurs
     */
    public Map<String, String> loadGlobalScriptsFromRepo(String revision) throws ClientException {
        if (StringUtils.isBlank(revision)) {
            throw new ClientException("Revision cannot be null or empty");
        }

        try {
            // Call servlet to get global scripts content at specific revision
            String xmlContent = getServlet().getContentAtRevision("scripts", revision, VersionControlConstants.MODE_GLOBAL_SCRIPTS);

            // Deserialize JSON response to Map
            @SuppressWarnings("unchecked") Map<String, String> scripts = ObjectXMLSerializer.getInstance().deserialize(xmlContent, Map.class);

            if (scripts == null || scripts.isEmpty()) {
                throw new ClientException("No global scripts found at revision: " + revision);
            }

            return scripts;

        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load global scripts from repository: " + e.getMessage(), e);
        }
    }

    /**
     * Get local repository info (path, remote URL, branch, size, file browser)
     *
     * @return RepoInfo with repository structure details
     * @throws ClientException if Git is not connected or an error occurs
     */
    public RepoInfo getRepoInfo() throws ClientException {
        try {
            String jsonResponse = getServlet().getRepoInfo();
            return JsonUtils.fromJson(jsonResponse, RepoInfo.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to get repository info: " + e.getMessage(), e);
        }
    }

    /**
     * Get current working tree changes (modified/removed/missing and untracked files)
     *
     * @return RepoChanges with changedFiles and untrackedFiles
     * @throws ClientException if Git is not connected or an error occurs
     */
    public RepoChanges getRepoChanges() throws ClientException {
        try {
            String jsonResponse = getServlet().getRepoChanges();
            return JsonUtils.fromJson(jsonResponse, RepoChanges.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to get repository changes: " + e.getMessage(), e);
        }
    }

    /**
     * Get file content from the working tree
     *
     * @param filePath Relative path from repository root (e.g., "Channels/abc-123.xml")
     * @return Raw file content as string
     * @throws ClientException if file not found or Git is not connected
     */
    public String getFileContent(String filePath) throws ClientException {
        try {
            return getServlet().getFileContent(filePath);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to get file content: " + e.getMessage(), e);
        }
    }

    /**
     * Get file content at HEAD revision
     *
     * @param filePath Relative path from repository root (e.g., "Channels/abc-123.xml")
     * @return Raw file content at HEAD as string
     * @throws ClientException if file not found at HEAD or Git is not connected
     */
    public String getFileContentAtHead(String filePath) throws ClientException {
        try {
            return getServlet().getFileContentAtHead(filePath);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to get file content at HEAD: " + e.getMessage(), e);
        }
    }

    /**
     * Private helper: Load raw XML content from repository
     */
    private String loadChannelRawContentFromRepo(String channelId, String revision) throws ClientException {
        if (StringUtils.isBlank(channelId)) {
            throw new ClientException("Channel ID cannot be null or empty");
        }
        if (StringUtils.isBlank(revision)) {
            throw new ClientException("Revision cannot be null or empty");
        }

        try {
            String xmlContent = getServlet().getContentAtRevision(channelId, revision, VersionControlConstants.MODE_CHANNEL);

            if (StringUtils.isBlank(xmlContent)) {
                throw new ClientException("Channel not found or content is empty: " + channelId);
            }

            return xmlContent;

        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load channel from repository: " + e.getMessage(), e);
        }
    }

    /**
     * Private helper: Load raw XML content of code template from repository
     */
    private String loadCodeTemplateRawContentFromRepo(String templateId, String revision) throws ClientException {
        if (StringUtils.isBlank(templateId)) {
            throw new ClientException("Code template ID cannot be null or empty");
        }
        if (StringUtils.isBlank(revision)) {
            throw new ClientException("Revision cannot be null or empty");
        }

        try {
            String xmlContent = getServlet().getContentAtRevision(templateId, revision, VersionControlConstants.MODE_CODE_TEMPLATE);

            if (StringUtils.isBlank(xmlContent)) {
                throw new ClientException("Code template not found or content is empty: " + templateId);
            }

            return xmlContent;

        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to load code template from repository: " + e.getMessage(), e);
        }
    }

    /**
     * Get repository commit log
     *
     * @param maxCount Maximum number of commits to return
     * @return List of commit metadata entries (newest first)
     * @throws ClientException if Git is not connected or an error occurs
     */
    public List<CommitMetaData> getRepoLog(int maxCount) throws ClientException {
        try {
            String jsonResponse = getServlet().getRepoLog(maxCount);
            return JsonUtils.fromJsonList(jsonResponse, CommitMetaData.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to get repository log: " + e.getMessage(), e);
        }
    }

    /**
     * Get list of files changed in a specific commit
     *
     * @param commitHash Full SHA of the commit
     * @return List of changed files with their change type (ADDED/MODIFIED/DELETED)
     * @throws ClientException if commit not found, Git is not connected, or an error occurs
     */
    public List<RepoItemChange> getCommitChanges(String commitHash) throws ClientException {
        try {
            String jsonResponse = getServlet().getCommitChanges(commitHash);
            return JsonUtils.fromJsonList(jsonResponse, RepoItemChange.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to get commit changes: " + e.getMessage(), e);
        }
    }

    /**
     * Get commit history for a specific file path (works for any file type in the repo)
     *
     * @param filePath Relative path from repository root (e.g., "Channels/abc-123.xml")
     * @return List of commit metadata entries (newest first)
     * @throws ClientException if file not found, Git is not connected, or an error occurs
     */
    public List<CommitMetaData> getFileHistory(String filePath) throws ClientException {
        try {
            String jsonResponse = getServlet().getFileHistory(filePath);
            return JsonUtils.fromJsonList(jsonResponse, CommitMetaData.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to get file history: " + e.getMessage(), e);
        }
    }

    /**
     * Get raw file content at a specific commit revision (works for any file type)
     *
     * @param filePath   Relative path from repository root (e.g., "Channels/abc-123.xml")
     * @param commitHash Full SHA of the commit
     * @return Raw file content as string
     * @throws ClientException if file not found at revision, Git is not connected, or an error occurs
     */
    public String getFileContentAtRevision(String filePath, String commitHash) throws ClientException {
        try {
            return getServlet().getFileContentAtRevision(filePath, commitHash);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to get file content at revision: " + e.getMessage(), e);
        }
    }

    /**
     * Get commit history for a specific item (channel, code template, global scripts)
     *
     * @param id   Item identifier (UUID for channels/templates, "scripts" for global scripts)
     * @param mode One of the MODE_* constants from VersionControlConstants
     * @return List of commit metadata entries (newest first)
     * @throws ClientException if item not found or Git is not connected
     */
    public List<CommitMetaData> getHistory(String id, String mode) throws ClientException {
        try {
            String jsonResponse = getServlet().getHistory(id, mode);
            return JsonUtils.fromJsonList(jsonResponse, CommitMetaData.class);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to get history: " + e.getMessage(), e);
        }
    }

    /**
     * Get raw file content at a specific revision
     *
     * @param id       Item identifier (UUID for channels/templates, "scripts" for global scripts)
     * @param revision Git commit hash or revision spec (e.g. "abc1234^" for parent)
     * @param mode     One of the MODE_* constants from VersionControlConstants
     * @return Raw file content as string
     * @throws ClientException if item not found at revision or Git is not connected
     */
    public String getContentAtRevision(String id, String revision, String mode) throws ClientException {
        try {
            return getServlet().getContentAtRevision(id, revision, mode);
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to get content at revision: " + e.getMessage(), e);
        }
    }

    /**
     * Commits and pushes a specific set of files directly by path.
     * Works for any file type in the repo — not limited to Mirth entities.
     * <p>
     * Throws {@link GitConflictClientException} when the server detects a rebase conflict.
     * That exception carries the backed-up content of all committed files so the caller
     * can offer the user a restore option.
     *
     * @param filePaths Relative file paths to stage and commit
     * @param message   Commit message
     * @param userId    ID of the committing user
     * @throws GitConflictClientException if the rebase conflicts with remote changes
     * @throws ClientException            if Git is not connected, push is rejected, or an error occurs
     */
    public void commitAndPushFiles(List<String> filePaths, String message, String userId) throws ClientException {
        try {
            CommitFilesRequest request = new CommitFilesRequest(filePaths, message, userId);
            getServlet().commitAndPushFiles(JsonUtils.toJson(request));
        } catch (ClientException e) {
            ClientException parsed = rethrowParsedClientError(e, true);
            if (parsed instanceof VersionHistoryClientException) {
                VersionHistoryClientException vhe = (VersionHistoryClientException) parsed;
                if (VersionHistoryErrorCodes.GIT_CONFLICT.equals(vhe.getError().getCode())) {
                    throw new GitConflictClientException(vhe.getError(), e);
                }
            }
            throw parsed;
        } catch (Exception e) {
            throw new ClientException("Failed to commit and push files: " + e.getMessage(), e);
        }
    }

    /**
     * Writes backed-up file content back to the working tree on the server (no commit, no push).
     * Called after a rebase conflict to restore the user's local changes so they can recommit.
     *
     * @param files Map of relative file paths to their content
     * @throws ClientException if Git is not connected or writing the files fails
     */
    public void restoreFiles(Map<String, String> files) throws ClientException {
        try {
            getServlet().restoreFiles(JsonUtils.toJson(files));
        } catch (ClientException e) {
            throw rethrowParsedClientError(e, true);
        } catch (Exception e) {
            throw new ClientException("Failed to restore files: " + e.getMessage(), e);
        }
    }

    private VersionHistoryServletInterface getServlet() {
        Client client = PlatformUI.MIRTH_FRAME.mirthClient;
        return client.getServlet(VersionHistoryServletInterface.class);
    }

    /**
     * Parse structured error response from server and rethrow as VersionHistoryClientException
     *
     * @param e        Original ClientException from server
     * @param logError Whether to log the parsed error
     * @return VersionHistoryClientException with parsed error details
     */
    private ClientException rethrowParsedClientError(ClientException e, boolean logError) {
        Throwable cause = e.getCause();

        // Try to extract structured error from response entity
        if (cause instanceof EntityException) {
            String rawEntity = (String) ((EntityException) cause).getEntity();

            ErrorResponse error;
            try {
                // Try to parse as structured ErrorResponse
                error = JsonUtils.fromJson(rawEntity, ErrorResponse.class);

                return new VersionHistoryClientException(error, e);
            } catch (Exception parseError) {
                // Failed to parse - could be plain text error or different format
                if (logError) {
                    logger.error("Failed to parse server error response: {}", rawEntity, parseError);
                }

                // Create error response from raw text
                error = new ErrorResponse(VersionHistoryErrorCodes.UNPARSEABLE_RESPONSE, rawEntity);

                return new VersionHistoryClientException(error, e);
            }
        }

        // No EntityException - return original
        if (logError) {
            logger.error("Unstructured client exception: {}", e.getMessage(), e);
        }

        return e;
    }
}
