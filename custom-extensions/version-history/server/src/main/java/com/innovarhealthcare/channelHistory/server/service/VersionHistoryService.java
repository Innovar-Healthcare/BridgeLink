/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.innovarhealthcare.channelHistory.server.exception.GitConflictException;
import com.innovarhealthcare.channelHistory.server.exception.GitFileNotFoundException;
import com.innovarhealthcare.channelHistory.server.exception.GitNotConnectedException;
import com.innovarhealthcare.channelHistory.server.exception.GitOperationException;
import com.innovarhealthcare.channelHistory.server.exception.GitPushFailedException;
import com.innovarhealthcare.channelHistory.server.repository.ChannelRepository;
import com.innovarhealthcare.channelHistory.server.repository.CodeTemplateRepository;
import com.innovarhealthcare.channelHistory.server.repository.GlobalScriptRepository;
import com.innovarhealthcare.channelHistory.server.repository.LibraryRepository;
import com.innovarhealthcare.channelHistory.server.util.GitCommitterHelper;
import com.innovarhealthcare.channelHistory.shared.dto.response.LibrariesAndTemplatesResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.LibraryMetadata;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoChanges;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoInfo;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemChange;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.innovarhealthcare.channelHistory.shared.util.CommitMessageUtil;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.User;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Version History Service (Business Logic Layer)
 * <p>
 * Responsibilities:
 * - Business logic and validation
 * - Orchestrate operations across repositories
 * - User-facing API for version history features
 * <p>
 * Delegates infrastructure to GitRepositoryService:
 * - Git connection management
 * - Repository initialization
 * - SSH configuration
 * - Operations and repository instances
 * <p>
 * Usage:
 * 1. Create: new VersionHistoryService(gitService)
 * 2. Use: saveLibrariesAndPush(...)
 * <p>
 * Thread Safety: Methods are not synchronized - caller responsible
 */
public class VersionHistoryService {

    private static final Logger logger = LogManager.getLogger(VersionHistoryService.class);

    // ========== Dependencies ==========
    private final GitRepositoryService gitRepositoryService;
    private VersionHistoryProperties versionHistoryProperties;

    /**
     * Creates VersionHistoryService with Git infrastructure.
     */
    public VersionHistoryService(GitRepositoryService gitRepositoryService, VersionHistoryProperties versionHistoryProperties) {
        if (gitRepositoryService == null) {
            throw new IllegalArgumentException("GitRepositoryService cannot be null");
        }
        this.gitRepositoryService = gitRepositoryService;
        this.versionHistoryProperties = versionHistoryProperties;
    }

    // ========== Business Methods ==========

    /**
     * Validates the Git connection described by the given properties.
     * Parses properties into a VersionHistoryProperties, then delegates to
     * GitRepositoryService.validateSSHConnection() which clones to a temp dir and deletes it.
     *
     * @param properties Plugin properties containing Git settings
     * @return Success message on success; error message on failure
     */
    public String validateGitConnection(Properties properties) {
        VersionHistoryProperties tempProperties = new VersionHistoryProperties(properties);
        String error = gitRepositoryService.validateSSHConnection(tempProperties.getGitSettings());
        if (error != null) {
            throw new GitNotConnectedException(error);
        }
        return "Successfully connected to the remote repository. Remember to save your changes.";
    }

    public boolean isAutoCommitEnabled() {
        return versionHistoryProperties.isEnableAutoCommit();
    }

    public boolean isEnableSyncDelete() {
        return versionHistoryProperties.isEnableSyncDelete();
    }

    public void writeChannelToRepo(Channel channel) {
        if (!gitRepositoryService.isGitAvailable()) {
            logger.debug("Git not available, skipping write channel to repo");
            return;
        }
        try {
            gitRepositoryService.getChannelRepository().save(channel);
            logger.info("Wrote channel '{}' to repo (no commit)", channel.getName());
        } catch (Exception e) {
            logger.error("Failed to write channel to repo: {}", e.getMessage(), e);
        }
    }

    public void writeCodeTemplateToRepo(CodeTemplate ct) {
        if (!gitRepositoryService.isGitAvailable()) {
            logger.debug("Git not available, skipping write code template to repo");
            return;
        }
        try {
            gitRepositoryService.getCodeTemplateRepository().save(ct);
            logger.info("Wrote code template '{}' to repo (no commit)", ct.getName());
        } catch (Exception e) {
            logger.error("Failed to write code template to repo: {}", e.getMessage(), e);
        }
    }

    public void deleteChannelFromRepo(Channel channel) {
        if (!gitRepositoryService.isGitAvailable()) {
            logger.debug("Git not available, skipping delete channel from repo");
            return;
        }
        try {
            gitRepositoryService.getChannelRepository().delete(channel.getId());
            logger.info("Deleted channel '{}' from repo (no commit)", channel.getName());
        } catch (Exception e) {
            logger.error("Failed to delete channel from repo: {}", e.getMessage(), e);
        }
    }

    public void deleteCodeTemplateFromRepo(CodeTemplate ct) {
        if (!gitRepositoryService.isGitAvailable()) {
            logger.debug("Git not available, skipping delete code template from repo");
            return;
        }
        try {
            gitRepositoryService.getCodeTemplateRepository().delete(ct.getId());
            logger.info("Deleted code template '{}' from repo (no commit)", ct.getName());
        } catch (Exception e) {
            logger.error("Failed to delete code template from repo: {}", e.getMessage(), e);
        }
    }

    /**
     * Saves a SINGLE channel and commits/pushes to git repository
     *
     * @param channel Channel to save
     * @param message Commit message (can be null or empty)
     * @param user    User making the commit
     * @return Result message with details of the operation
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitPushFailedException   if push operation fails
     * @throws GitOperationException    if other Git operations fail
     * @throws IllegalArgumentException if validation fails
     */
    public String saveChannelAndPush(Channel channel, String message, User user, boolean overwrite)
            throws GitNotConnectedException, GitConflictException, GitPushFailedException, GitOperationException, IllegalArgumentException {

        logger.info("saveChannelAndPush: channel={}, overwrite={}", channel != null ? channel.getId() : null, overwrite);

        // Check Git availability
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Validate inputs
        validateChannel(channel);
        validateUser(user);

        ChannelRepository repository = gitRepositoryService.getChannelRepository();

        if (overwrite) {
            // Auto-commit: pullWithOverwrite → write file → commit → push
            PersonIdent committer = GitCommitterHelper.fromUser(user);
            String result = repository.saveAndPush(channel, message, committer, false);
            logger.info("saveChannelAndPush (overwrite) completed successfully");
            return result;
        } else {
            // Manual commit: write file → rebase+backup → commit → push (conflict-aware)
            try {
                String filePath = repository.save(channel);
                String commitMessage = repository.buildCommitMessage(channel, message);
                gitRepositoryService.commitAndPushFiles(List.of(filePath), commitMessage, user);
                logger.info("saveChannelAndPush (manual) completed successfully");
                return "Committed and pushed channel '" + channel.getName() + "' to remote repository";
            } catch (java.io.IOException e) {
                throw new GitOperationException("Failed to write channel to working tree: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Saves a SINGLE code template and commits/pushes to git repository
     *
     * @param template Code template to save
     * @param message  Commit message (can be null or empty)
     * @param user     User making the commit
     * @return Result message with details of the operation
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitPushFailedException   if push operation fails
     * @throws GitOperationException    if other Git operations fail
     * @throws IllegalArgumentException if validation fails
     */
    public String saveCodeTemplateAndPush(CodeTemplate template, String message, User user, boolean overwrite) throws GitNotConnectedException, GitConflictException, GitPushFailedException, GitOperationException, IllegalArgumentException {

        logger.info("saveCodeTemplateAndPush: template={}, overwrite={}", template != null ? template.getId() : null, overwrite);

        // Check Git availability
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Validate inputs
        validateCodeTemplate(template);
        validateUser(user);

        // Resolve message (null/empty = auto commit)
        if (message == null || message.isEmpty()) {
            message = versionHistoryProperties.getAutoCommitMsg();
        }

        CodeTemplateRepository repository = gitRepositoryService.getCodeTemplateRepository();

        if (overwrite) {
            // Auto-commit: pullWithOverwrite → write file → commit → push
            PersonIdent committer = GitCommitterHelper.fromUser(user);
            String result = repository.saveAndPush(template, message, committer, false);
            logger.info("saveCodeTemplateAndPush (overwrite) completed successfully");
            return result;
        } else {
            // Manual commit: write file → rebase+backup → commit → push (conflict-aware)
            try {
                String filePath = repository.save(template);
                String commitMessage = repository.buildCommitMessage(template, message);
                gitRepositoryService.commitAndPushFiles(List.of(filePath), commitMessage, user);
                logger.info("saveCodeTemplateAndPush (manual) completed successfully");
                return "Committed and pushed code template '" + template.getName() + "' to remote repository";
            } catch (java.io.IOException e) {
                throw new GitOperationException("Failed to write code template to working tree: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Saves libraries and commits/pushes to git repository.
     * <p>
     * This is the ONLY public business method for libraries.
     *
     * @param libraries List of libraries to save
     * @param message   Commit message (can be null or empty)
     * @param user      User making the commit
     * @return Result message with details of the operation
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitPushFailedException   if push operation fails (rejected or error)
     * @throws GitOperationException    if other Git operations fail (commit, fetch, pull, etc.)
     * @throws IllegalArgumentException if validation fails (null/empty libraries or user)
     */
    public String saveLibrariesAndPush(List<CodeTemplateLibrary> libraries, String message, User user) throws GitNotConnectedException, GitConflictException, GitPushFailedException, GitOperationException, IllegalArgumentException {

        logger.info("saveLibrariesAndPush called with {} libraries", libraries != null ? libraries.size() : 0);

        // Check if Git is available
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Validate input
        validateLibraries(libraries);
        validateUser(user);

        LibraryRepository repository = gitRepositoryService.getLibraryRepository();

        // Manual commit: write files → rebase+backup → commit → push (conflict-aware)
        try {
            List<String> paths = repository.saveAll(libraries);
            String commitMessage = repository.buildBatchCommitMessage(libraries, message, GitCommitterHelper.fromUser(user));
            gitRepositoryService.commitAndPushFiles(paths, commitMessage, user);
            logger.info("saveLibrariesAndPush completed successfully");
            return "Committed and pushed " + libraries.size() + " libraries to remote repository";
        } catch (java.io.IOException e) {
            throw new GitOperationException("Failed to write libraries to working tree: " + e.getMessage(), e);
        }
    }

    /**
     * Saves global scripts and commits/pushes to git repository
     *
     * @param scripts Map of global scripts (Deploy, Undeploy, Preprocessor, Postprocessor)
     * @param message Commit message (can be null or empty)
     * @param user    User making the commit
     * @return Result message with details of the operation
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitPushFailedException   if push operation fails
     * @throws GitOperationException    if other Git operations fail
     * @throws IllegalArgumentException if validation fails
     */
    public String saveGlobalScriptsAndPush(Map<String, String> scripts, String message, User user) throws GitNotConnectedException, GitPushFailedException, GitOperationException, IllegalArgumentException {

        logger.info("saveGlobalScriptsAndPush: scripts={}", scripts != null ? scripts.keySet() : null);

        // Check Git availability
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Validate inputs
        validateGlobalScripts(scripts);
        validateUser(user);

        // Resolve message (null/empty = auto commit)
        if (message == null || message.isEmpty()) {
            message = versionHistoryProperties.getAutoCommitMsg();
        }

        // Convert User to PersonIdent
        PersonIdent committer = GitCommitterHelper.fromUser(user);

        // Get repository
        GlobalScriptRepository repository = gitRepositoryService.getGlobalScriptRepository();

        // Execute operation
        boolean forcePush = false;
        String result = repository.saveAndPush(scripts, message, committer, forcePush);

        logger.info("saveGlobalScriptsAndPush completed successfully");
        return result;
    }

    public String deleteChannelAndPush(Channel channel, String message, User user) throws GitNotConnectedException, GitPushFailedException, GitOperationException, IllegalArgumentException {

        logger.info("deleteChannelAndPush: channel={}", channel != null ? channel.getId() : null);

        // Check Git availability
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Validate inputs
        validateChannel(channel);
        validateUser(user);

        // Resolve message (null/empty = auto commit)
        if (message == null || message.isEmpty()) {
            message = versionHistoryProperties.getAutoCommitMsg();
        }

        // Convert User to PersonIdent
        PersonIdent committer = GitCommitterHelper.fromUser(user);

        // Get repository
        ChannelRepository repository = gitRepositoryService.getChannelRepository();

        // Execute operation
        boolean forcePush = false;
        String result = repository.deleteAndPush(channel.getId(), message, committer, forcePush);

        logger.info("deleteChannelAndPush completed successfully");
        return result;
    }

    public String deleteCodeTemplateAndPush(CodeTemplate template, String message, User user) throws GitNotConnectedException, GitPushFailedException, GitOperationException, IllegalArgumentException {

        logger.info("deleteCodeTemplateAndPush: template={}", template != null ? template.getId() : null);

        // Check Git availability
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Validate inputs
        validateCodeTemplate(template);
        validateUser(user);

        // Resolve message (null/empty = auto commit)
        if (message == null || message.isEmpty()) {
            message = versionHistoryProperties.getAutoCommitMsg();
        }

        // Convert User to PersonIdent
        PersonIdent committer = GitCommitterHelper.fromUser(user);

        // Get repository
        CodeTemplateRepository repository = gitRepositoryService.getCodeTemplateRepository();

        // Execute operation
        boolean forcePush = false;
        String result = repository.deleteAndPush(template.getId(), message, committer, forcePush);

        logger.info("deleteCodeTemplateAndPush completed successfully");
        return result;
    }

    /**
     * Loads metadata of all libraries from Git repository
     *
     * @return List of library metadata (id, name, filePath, lastCommitId)
     * @throws GitNotConnectedException if Git repository is not available
     */
    public List<RepoItemMetadata> loadLibrariesMetadata() throws GitNotConnectedException {

        logger.info("loadLibrariesMetadata called");

        // Check Git availability
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Get repository and load metadata
        LibraryRepository repository = gitRepositoryService.getLibraryRepository();
        List<RepoItemMetadata> metadata = repository.loadMetadata();

        logger.info("Loaded {} library metadata items", metadata.size());
        return metadata;
    }

    /**
     * Loads metadata of all channels from Git repository
     *
     * @return List of channel metadata (id, name, filePath, lastCommitId)
     * @throws GitNotConnectedException if Git repository is not available
     */
    public List<RepoItemMetadata> loadChannelsMetadata() throws GitNotConnectedException {

        logger.info("loadChannelsMetadata called");

        // Check Git availability
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Get repository and load metadata
        ChannelRepository repository = gitRepositoryService.getChannelRepository();
        List<RepoItemMetadata> metadata = repository.loadMetadata();

        logger.info("Loaded {} channel metadata items", metadata.size());
        return metadata;
    }

    /**
     * Loads metadata of all code templates from Git repository
     *
     * @return List of code template metadata (id, name, filePath, lastCommitId)
     * @throws GitNotConnectedException if Git repository is not available
     */
    public List<RepoItemMetadata> loadCodeTemplatesMetadata() throws GitNotConnectedException {

        logger.info("loadCodeTemplatesMetadata called");

        // Check Git availability
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Get repository and load metadata
        CodeTemplateRepository repository = gitRepositoryService.getCodeTemplateRepository();
        List<RepoItemMetadata> metadata = repository.loadMetadata();

        logger.info("Loaded {} code template metadata items", metadata.size());
        return metadata;
    }

    public LibrariesAndTemplatesResponse loadLibrariesAndTemplateMetadata() throws GitNotConnectedException {
        logger.info("loadLibrariesAndTemplateMetadata called");

        // Check Git availability
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Load all libraries (full objects needed for codeTemplateIds)
        LibraryRepository libRepository = gitRepositoryService.getLibraryRepository();
        List<CodeTemplateLibrary> libraries = libRepository.loadAll();
        logger.debug("Loaded {} libraries", libraries.size());

        // Convert libraries to metadata (extract only necessary fields)
        List<LibraryMetadata> libraryMetadata = new ArrayList<>();
        for (CodeTemplateLibrary library : libraries) {
            List<String> templateIds = new ArrayList<>();

            // Extract code template IDs from library
            if (library.getCodeTemplates() != null) {
                for (CodeTemplate template : library.getCodeTemplates()) {
                    if (template != null && template.getId() != null) {
                        templateIds.add(template.getId());
                    }
                }
            }

            libraryMetadata.add(new LibraryMetadata(library.getId(), library.getName(), templateIds));
        }
        logger.debug("Converted {} libraries to metadata", libraryMetadata.size());

        // Load code template metadata
        CodeTemplateRepository repository = gitRepositoryService.getCodeTemplateRepository();
        List<RepoItemMetadata> templateMetadata = repository.loadMetadata();
        logger.debug("Loaded {} template metadata items", templateMetadata.size());

        // Create and return response
        LibrariesAndTemplatesResponse response = new LibrariesAndTemplatesResponse(libraryMetadata, templateMetadata);

        logger.info("Successfully loaded {} libraries and {} templates metadata", libraryMetadata.size(), templateMetadata.size());

        return response;
    }

    /**
     * Gets library content (as XML string) at a specific revision
     *
     * @param id       Library ID
     * @param revision Commit SHA or ref (e.g., "HEAD", commit hash)
     * @return Library content as XML string
     * @throws GitNotConnectedException if Git is not available
     * @throws GitFileNotFoundException if file not found at revision
     * @throws GitOperationException    if Git operation fails
     */
    public String getLibraryContentAtRevision(String id, String revision) throws GitNotConnectedException, GitFileNotFoundException, GitOperationException {

        logger.info("getLibraryContentAtRevision: id={}, revision={}", id, revision);

        // Validate
        validateId(id);
        validateRevision(revision);

        // Check Git availability
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Get repository and content
        LibraryRepository repository = gitRepositoryService.getLibraryRepository();
        String content = repository.getContent(id, revision);

        logger.info("Successfully loaded library content: id={}, size={} bytes", id, content.length());
        return content;
    }

    /**
     * Gets channel content (as XML string) at a specific revision
     *
     * @param id       Channel ID
     * @param revision Commit SHA or ref
     * @return Channel content as XML string
     * @throws GitNotConnectedException if Git is not available
     * @throws GitFileNotFoundException if file not found at revision
     * @throws GitOperationException    if Git operation fails
     */
    public String getChannelContentAtRevision(String id, String revision) throws GitNotConnectedException, GitFileNotFoundException, GitOperationException {

        logger.info("getChannelContentAtRevision: id={}, revision={}", id, revision);

        validateId(id);
        validateRevision(revision);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        ChannelRepository repository = gitRepositoryService.getChannelRepository();
        String content = repository.getContent(id, revision);

        logger.info("Successfully loaded channel content: id={}, size={} bytes", id, content.length());
        return content;
    }

    /**
     * Gets code template content (as XML string) at a specific revision
     *
     * @param id       Code template ID
     * @param revision Commit SHA or ref
     * @return Code template content as XML string
     * @throws GitNotConnectedException if Git is not available
     * @throws GitFileNotFoundException if file not found at revision
     * @throws GitOperationException    if Git operation fails
     */
    public String getCodeTemplateContentAtRevision(String id, String revision) throws GitNotConnectedException, GitFileNotFoundException, GitOperationException {

        logger.info("getCodeTemplateContentAtRevision: id={}, revision={}", id, revision);

        validateId(id);
        validateRevision(revision);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        CodeTemplateRepository repository = gitRepositoryService.getCodeTemplateRepository();
        String content = repository.getContent(id, revision);

        logger.info("Successfully loaded code template content: id={}, size={} bytes", id, content.length());
        return content;
    }

    public String getGlobalScriptsContentAtRevision(String id, String revision) throws GitNotConnectedException, GitFileNotFoundException, GitOperationException {

        logger.info("getCodeTemplateContentAtRevision: id={}, revision={}", id, revision);

        validateId(id);
        validateRevision(revision);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        GlobalScriptRepository repository = gitRepositoryService.getGlobalScriptRepository();
        String content = repository.getContent(id, revision);

        logger.info("Successfully loaded code template content: id={}, size={} bytes", id, content.length());
        return content;
    }

    /**
     * Gets commit history for a library
     *
     * @param id Library ID
     * @return List of commit metadata, ordered from newest to oldest
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitOperationException    if Git operation fails
     * @throws IllegalArgumentException if ID is invalid
     */
    public List<CommitMetaData> getLibraryHistory(String id) throws GitNotConnectedException, GitOperationException, IllegalArgumentException {

        logger.info("getLibraryHistory: id={}", id);

        // Validate
        validateId(id);

        // Check Git availability
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        // Get repository and history
        LibraryRepository repository = gitRepositoryService.getLibraryRepository();
        List<CommitMetaData> history = repository.getHistory(id);

        logger.info("Retrieved {} commits for library: {}", history.size(), id);
        return history;
    }

    /**
     * Gets commit history for a channel
     *
     * @param id Channel ID
     * @return List of commit metadata, ordered from newest to oldest
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitOperationException    if Git operation fails
     * @throws IllegalArgumentException if ID is invalid
     */
    public List<CommitMetaData> getChannelHistory(String id) throws GitNotConnectedException, GitOperationException, IllegalArgumentException {

        logger.info("getChannelHistory: id={}", id);

        validateId(id);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        ChannelRepository repository = gitRepositoryService.getChannelRepository();
        List<CommitMetaData> history = repository.getHistory(id);

        logger.info("Retrieved {} commits for channel: {}", history.size(), id);
        return history;
    }

    /**
     * Gets commit history for a code template
     *
     * @param id Code template ID
     * @return List of commit metadata, ordered from newest to oldest
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitOperationException    if Git operation fails
     * @throws IllegalArgumentException if ID is invalid
     */
    public List<CommitMetaData> getCodeTemplateHistory(String id) throws GitNotConnectedException, GitOperationException, IllegalArgumentException {

        logger.info("getCodeTemplateHistory: id={}", id);

        validateId(id);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        CodeTemplateRepository repository = gitRepositoryService.getCodeTemplateRepository();
        List<CommitMetaData> history = repository.getHistory(id);

        logger.info("Retrieved {} commits for code template: {}", history.size(), id);
        return history;
    }

    public List<CommitMetaData> getGlobalScriptsHistory(String id) throws GitNotConnectedException, GitOperationException, IllegalArgumentException {

        logger.info("getGlobalScriptsHistory: id={}", id);

        validateId(id);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        GlobalScriptRepository repository = gitRepositoryService.getGlobalScriptRepository();
        List<CommitMetaData> history = repository.getHistory(id);

        logger.info("Retrieved {} commits for global scripts: {}", history.size(), id);
        return history;
    }

    /**
     * Returns a snapshot of the local repository's structure and size.
     *
     * @return RepoInfo with local path, remote URL, branch, total size, and folder list
     * @throws GitNotConnectedException if Git repository is not available
     */
    public RepoInfo getRepoInfo() throws GitNotConnectedException {
        logger.info("getRepoInfo called");

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        return gitRepositoryService.getRepoInfo();
    }

    /**
     * Returns the current working tree changes (modified/removed/missing and untracked files).
     *
     * @return RepoChanges with changedFiles and untrackedFiles
     * @throws GitNotConnectedException if Git repository is not available
     */
    public RepoChanges getRepoChanges() throws GitNotConnectedException, GitOperationException {
        logger.info("getRepoChanges called");

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        return gitRepositoryService.getRepoChanges();
    }

    /**
     * Returns the raw content of a file from the working tree filesystem.
     *
     * @param filePath Relative path from repository root
     * @return File content as string
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitFileNotFoundException if the file does not exist
     */
    public String getFileContent(String filePath) throws GitNotConnectedException, GitFileNotFoundException {
        logger.info("getFileContent called: filePath={}", filePath);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        return gitRepositoryService.getFileContent(filePath);
    }

    /**
     * Returns the raw content of a file at HEAD revision from the Git object store.
     *
     * @param filePath Relative path from repository root
     * @return File content as string
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitFileNotFoundException if the file does not exist at HEAD
     */
    public String getFileContentAtHead(String filePath) throws GitNotConnectedException, GitFileNotFoundException, GitOperationException {
        logger.info("getFileContentAtHead called: filePath={}", filePath);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        return gitRepositoryService.getFileContentAtHead(filePath);
    }

    /**
     * Gets commit history for a specific file path.
     *
     * @param filePath Relative path from repository root (e.g., "Channels/abc.xml")
     * @return List of commit metadata, newest first
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitFileNotFoundException if filePath is blank
     * @throws GitOperationException    if Git operation fails
     */
    public List<CommitMetaData> getFileHistory(String filePath) throws GitNotConnectedException, GitFileNotFoundException, GitOperationException {
        logger.info("getFileHistory called: filePath={}", filePath);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        return gitRepositoryService.getFileHistory(filePath);
    }

    /**
     * Returns the raw content of a file at a specific commit revision.
     *
     * @param filePath   Relative path from repository root (e.g., "Channels/abc.xml")
     * @param commitHash Commit SHA to read the file at
     * @return File content as UTF-8 string
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitFileNotFoundException if filePath or commitHash is blank, or file not found
     * @throws GitOperationException    if Git operation fails
     */
    public String getFileContentAtRevision(String filePath, String commitHash) throws GitNotConnectedException, GitFileNotFoundException, GitOperationException {
        logger.info("getFileContentAtRevision called: filePath={}, commitHash={}", filePath, commitHash);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        return gitRepositoryService.getFileContentAtRevision(filePath, commitHash);
    }

    /**
     * Gets commit log for the entire repository.
     *
     * @param maxCount Maximum number of commits to return
     * @return List of commit metadata, newest first
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitOperationException    if Git operation fails
     */
    public List<CommitMetaData> getRepoLog(int maxCount) throws GitNotConnectedException, GitOperationException {
        logger.info("getRepoLog called, maxCount={}", maxCount);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        return gitRepositoryService.getRepoLog(maxCount);
    }

    /**
     * Gets files changed in a specific commit.
     *
     * @param commitHash Commit SHA to inspect
     * @return List of file changes
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitFileNotFoundException if commitHash is blank
     * @throws GitOperationException    if Git operation fails
     */
    public List<RepoItemChange> getCommitChanges(String commitHash) throws GitNotConnectedException, GitFileNotFoundException, GitOperationException {
        logger.info("getCommitChanges called, commitHash={}", commitHash);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git is not available: " + reason);
        }

        return gitRepositoryService.getCommitChanges(commitHash);
    }

    // ========== Status Methods ==========

    /**
     * Checks if Git is available and ready to use.
     *
     * @return true if Git is available
     */
    public boolean isGitAvailable() {
        return gitRepositoryService.isGitAvailable();
    }

    /**
     * Gets Git status information.
     *
     * @return GitStatus object with availability and message
     */
    public GitRepositoryService.GitStatus getGitStatus() {
        return gitRepositoryService.getGitStatus();
    }

    // ========== Validation Methods ==========
    private void validateChannel(Channel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be null");
        }
        if (channel.getId() == null || channel.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("Channel ID cannot be null or empty");
        }
        if (channel.getName() == null || channel.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Channel name cannot be null or empty");
        }
    }

    private void validateCodeTemplate(CodeTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("Code template cannot be null");
        }
        if (template.getId() == null || template.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("Code template ID cannot be null or empty");
        }
        if (template.getName() == null || template.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Code template name cannot be null or empty");
        }
    }

    /**
     * Validates libraries list.
     *
     * @param libraries List of libraries to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateLibraries(List<CodeTemplateLibrary> libraries) {
        if (libraries == null) {
            throw new IllegalArgumentException("Libraries list cannot be null");
        }

        if (libraries.isEmpty()) {
            throw new IllegalArgumentException("Libraries list cannot be empty");
        }

        for (int i = 0; i < libraries.size(); i++) {
            CodeTemplateLibrary library = libraries.get(i);

            if (library == null) {
                throw new IllegalArgumentException("Library at index " + i + " is null");
            }

            if (library.getId() == null || library.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("Library at index " + i + " has null or empty ID");
            }

            if (library.getName() == null || library.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Library at index " + i + " (ID: " + library.getId() + ") has null or empty name");
            }
        }
    }

    /**
     * Validates global scripts map
     */
    private void validateGlobalScripts(Map<String, String> scripts) throws IllegalArgumentException {
        if (scripts == null) {
            throw new IllegalArgumentException("Global scripts cannot be null");
        }

        // Check for valid script types only (content can be empty)
        for (String scriptType : scripts.keySet()) {
            if (scriptType == null || scriptType.trim().isEmpty()) {
                throw new IllegalArgumentException("Script type cannot be null or empty");
            }

            // Validate against known types
            if (!isValidScriptType(scriptType)) {
                throw new IllegalArgumentException("Invalid script type: " + scriptType + ". Expected: Deploy, Undeploy, Preprocessor, or Postprocessor");
            }
        }

        logger.debug("Global scripts validation passed: {} script types", scripts.size());
    }

    /**
     * Checks if script type is valid
     */
    private boolean isValidScriptType(String scriptType) {
        final String DEPLOY_SCRIPT = "Deploy";
        final String UNDEPLOY_SCRIPT = "Undeploy";
        final String PREPROCESSOR_SCRIPT = "Preprocessor";
        final String POSTPROCESSOR_SCRIPT = "Postprocessor";

        return DEPLOY_SCRIPT.equals(scriptType) || UNDEPLOY_SCRIPT.equals(scriptType) || PREPROCESSOR_SCRIPT.equals(scriptType) || POSTPROCESSOR_SCRIPT.equals(scriptType);
    }

    /**
     * Validates user object.
     *
     * @param user User to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
    }

    private void validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }
    }

    /**
     * Commits and pushes a specific set of files directly by path.
     *
     * @param filePaths Relative file paths to stage and commit
     * @param message   Commit message
     * @param user      Committer user
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitConflictException     if the rebase is stopped by a conflict with remote changes
     * @throws GitOperationException    if filePaths/message invalid or a Git operation fails
     * @throws GitPushFailedException   if the push is rejected
     */
    public void commitAndPushFiles(List<String> filePaths, String message, User user)
            throws GitNotConnectedException, GitConflictException, GitOperationException, GitPushFailedException {
        logger.info("commitAndPushFiles called, {} file(s)", filePaths != null ? filePaths.size() : 0);

        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git repository is not available: " + reason);
        }

        String serverId = ControllerFactory.getFactory().createConfigurationController().getServerId();
        String serverName = ControllerFactory.getFactory().createConfigurationController().getServerName();
        String enrichedMessage = CommitMessageUtil.createGeneric(message, serverId, serverName);

        gitRepositoryService.commitAndPushFiles(filePaths, enrichedMessage, user);
    }

    /**
     * Writes backed-up content back to the working tree (no commit or push).
     * Used after a rebase conflict to restore the user's local changes.
     *
     * @param files Map of relative file paths to UTF-8 content
     * @throws GitNotConnectedException if Git repository is not available
     * @throws GitOperationException    if writing fails
     */
    public void restoreFiles(java.util.Map<String, String> files)
            throws GitNotConnectedException, GitOperationException {
        if (!gitRepositoryService.isGitAvailable()) {
            String reason = gitRepositoryService.getGitUnavailableReason();
            logger.error("Git not available: {}", reason);
            throw new GitNotConnectedException("Git repository is not available: " + reason);
        }
        gitRepositoryService.restoreFiles(files);
    }

    private void validateRevision(String revision) {
        if (revision == null || revision.trim().isEmpty()) {
            throw new IllegalArgumentException("Revision cannot be null or empty");
        }
    }
}
