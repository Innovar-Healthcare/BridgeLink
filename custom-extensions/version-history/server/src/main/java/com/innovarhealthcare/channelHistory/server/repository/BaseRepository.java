/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.server.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.innovarhealthcare.channelHistory.server.exception.GitFileNotFoundException;
import com.innovarhealthcare.channelHistory.server.exception.GitOperationException;
import com.innovarhealthcare.channelHistory.server.exception.GitPushFailedException;
import com.innovarhealthcare.channelHistory.server.file.FileOperations;
import com.innovarhealthcare.channelHistory.server.git.GitOperations;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.util.CommitMessageUtil;
import com.mirth.connect.server.controllers.ControllerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Base implementation of Repository interface.
 * Provides common functionality for all repository types.
 *
 * @param <T> The entity type this repository manages
 */
public abstract class BaseRepository<T> implements Repository<T> {

    protected final Logger logger = LogManager.getLogger(getClass());

    protected final GitOperations gitOps;
    protected final FileOperations fileOps;
    protected final String serverId;

    /**
     * Creates a new repository instance
     *
     * @param gitOps  Git operations handler
     * @param fileOps File operations handler
     */
    public BaseRepository(GitOperations gitOps, FileOperations fileOps, String serverId) {
        if (gitOps == null) {
            throw new IllegalArgumentException("GitOperations cannot be null");
        }

        if (fileOps == null) {
            throw new IllegalArgumentException("FileOperations cannot be null");
        }

        if (serverId == null) {
            throw new IllegalArgumentException("Server ID cannot be null");
        }

        this.gitOps = gitOps;
        this.fileOps = fileOps;
        this.serverId = serverId;

        logger.debug("Initialized {} repository", getTypeName());
    }

    /**
     * Extracts the ID from an entity
     */
    protected abstract String extractId(T entity);

    /**
     * Extracts the name from an entity
     */
    protected abstract String extractName(T entity);

    /**
     * Gets the class type for deserialization
     */
    protected abstract Class<T> getEntityClass();

    /**
     * Deserializes XML content and verifies the object.
     * Each repository can implement custom validation logic.
     *
     * @param content  XML content
     * @param filePath File path (for logging)
     * @return Deserialized and verified object, or null if invalid
     */
    protected abstract T deserializeAndVerify(String content, String filePath);

    /**
     * Generates filename for an entity
     */
    protected String generateFilename(String id) {
        return id;
    }

    /**
     * Generates file path for an entity
     */
    protected String generateFilePath(String id) {
        return getDirectory() + "/" + generateFilename(id);
    }

    @Override
    public String save(T entity) throws IOException {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        String id = extractId(entity);
        String name = extractName(entity);

        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity ID cannot be null or empty");
        }

        logger.info("Saving {} '{}' (ID: {})", getTypeName(), name, id);

        // Write to file
        String filename = generateFilename(id);
        String path = fileOps.writeXml(getDirectory(), filename, entity);

        logger.info("Successfully saved {} '{}' to {}", getTypeName(), name, path);

        return path;
    }

    @Override
    public String saveAndPush(T entity, String message, PersonIdent committer, boolean forcePush) throws GitPushFailedException, GitOperationException {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        if (committer == null) {
            throw new IllegalArgumentException("Committer cannot be null");
        }

        String id = extractId(entity);
        String name = extractName(entity);

        logger.info("Saving and pushing {} '{}' (ID: {})", getTypeName(), name, id);

        StringBuilder result = new StringBuilder();

        try {
            // Validate git state
            gitOps.validateCurrentBranch();

            if (!gitOps.hasCommits()) {
                throw new GitOperationException("No commits in repository, cannot pull or push");
            }

            // Check for remote changes
            result.append("Remote Check Result:\n");
            boolean remoteHasChanges = gitOps.hasRemoteChanges();
            result.append("  Remote Changes: ").append(remoteHasChanges ? "Detected" : "None").append("\n");

            if (remoteHasChanges) {
                result.append("Pull Overwrite Result:\n");
                String pullResult = gitOps.pullWithOverwrite();
                result.append(pullResult);
            } else {
                result.append("  Skipped: No pull needed, local and remote branches are in sync\n");
            }

            // Save entity
            String path = save(entity);

            // Build commit message using util
            String commitMessage = buildCommitMessage(entity, message);

            // Commit
            String commitSha = gitOps.commitFiles(Collections.singletonList(path), commitMessage, committer);

            result.append("Commit: Staged and committed ").append(getTypeName().toLowerCase()).append(" '").append(name).append("' (").append(commitSha.substring(0, 7)).append(")\n");

            // ========== POST-COMMIT HOOK ==========
            postCommit(entity, id, name, commitSha);
            // ======================================

            // Push
            result.append("Push Result:\n");
            String pushResult = gitOps.push(forcePush);
            result.append(pushResult);

            result.append("\nSuccess: Committed and pushed ").append(getTypeName().toLowerCase()).append(" '").append(name).append("' to remote repository");

            logger.info("Successfully saved and pushed {} '{}'", getTypeName(), name);

            return result.toString();

        } catch (GitPushFailedException e) {
            logger.error("Push failed for {} '{}': {}", getTypeName(), name, e.getMessage());
            throw e; // Rethrow as-is
        } catch (GitAPIException e) {
            throw new GitOperationException("Git operation failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new GitOperationException("I/O error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new GitOperationException("Unexpected error: " + e.getMessage(), e);
        }
    }

    @Override
    public T load(String id) throws IOException {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }

        String path = generateFilePath(id);

        logger.debug("Loading {} with ID: {}", getTypeName(), id);

        T entity = fileOps.readXml(path, getEntityClass());

        logger.debug("Successfully loaded {} with ID: {}", getTypeName(), id);

        return entity;
    }

    @Override
    public boolean exists(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }

        String path = generateFilePath(id);
        return fileOps.fileExists(path);
    }

    @Override
    public boolean delete(String id) throws IOException {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }

        String path = generateFilePath(id);

        logger.info("Deleting {} with ID: {}", getTypeName(), id);

        boolean deleted = fileOps.deleteFile(path);

        if (deleted) {
            logger.info("Successfully deleted {} with ID: {}", getTypeName(), id);
        } else {
            logger.warn("{} with ID {} does not exist", getTypeName(), id);
        }

        return deleted;
    }

    @Override
    public final String deleteAndPush(String id, String message, PersonIdent committer, boolean forcePush) throws GitPushFailedException, GitOperationException {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }
        if (committer == null) {
            throw new IllegalArgumentException("Committer cannot be null");
        }

        logger.info("Deleting and pushing {} with ID: {}", getTypeName(), id);

        StringBuilder result = new StringBuilder();

        try {
            // Validate git state
            gitOps.validateCurrentBranch();

            if (!gitOps.hasCommits()) {
                throw new GitOperationException("No commits in repository, cannot pull or push");
            }

            // Check for remote changes
            result.append("Remote Check Result:\n");
            boolean remoteHasChanges = gitOps.hasRemoteChanges();
            result.append("  Remote Changes: ").append(remoteHasChanges ? "Detected" : "None").append("\n");

            if (remoteHasChanges) {
                result.append("Pull Overwrite Result:\n");
                String pullResult = gitOps.pullWithOverwrite();
                result.append(pullResult);
            } else {
                result.append("  Skipped: No pull needed, local and remote branches are in sync\n");
            }

            // Delete entity
            String path = generateFilePath(id);
            boolean deleted = delete(id);

            if (!deleted) {
                throw new GitOperationException(getTypeName() + " with ID " + id + " does not exist");
            }

            // Build commit message
            String serverName = ControllerFactory.getFactory().createConfigurationController().getServerName();
            String commitMessage = "Delete " + getTypeName() + " (ID: " + id + ")\n\n" + "Message: " + (message != null ? message : "No message provided") + "\n" + "Server: " + serverName + " (" + serverId + ")";

            // Commit
            String commitSha = gitOps.commitFiles(Collections.singletonList(path), commitMessage, committer);

            result.append("Commit: Staged and committed deletion of ").append(getTypeName().toLowerCase()).append(" '").append(id).append("' (").append(commitSha.substring(0, 7)).append(")\n");

            // Push
            result.append("Push Result:\n");
            String pushResult = gitOps.push(forcePush);
            result.append(pushResult);

            result.append("\nSuccess: Committed and pushed deletion of ").append(getTypeName().toLowerCase()).append(" '").append(id).append("' to remote repository");

            logger.info("Successfully deleted and pushed {} with ID: {}", getTypeName(), id);

            return result.toString();

        } catch (GitPushFailedException e) {
            logger.error("Push failed for {} deletion '{}': {}", getTypeName(), id, e.getMessage());
            throw e;
        } catch (GitAPIException e) {
            throw new GitOperationException("Git operation failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new GitOperationException("I/O error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new GitOperationException("Unexpected error: " + e.getMessage(), e);
        }
    }

//    @Override
//    public List<String> listAll() throws IOException {
//        logger.debug("Listing all {} entities", getTypeName());
//
//        List<String> filePaths = fileOps.listFiles(getDirectory(), "*.xml");
//
//        // Extract IDs from file paths
//        List<String> ids = filePaths.stream().map(path -> {
//            String filename = path.substring(path.lastIndexOf('/') + 1);
//            return filename.replace(".xml", "");
//        }).collect(Collectors.toList());
//
//        logger.debug("Found {} {} entities", ids.size(), getTypeName());
//
//        return ids;
//    }

    @Override
    public List<RepoItemMetadata> loadMetadata() {
        try {
            // ✅ Get files through GitOperations (NO direct Git access)
            List<GitOperations.CommittedFile> files = gitOps.readCommittedFiles(getDirectory());

            List<RepoItemMetadata> metadataList = new ArrayList<>();

            for (GitOperations.CommittedFile file : files) {
                try {
                    String fileName = file.getFileName();
                    String filePath = file.getFilePath();

                    // Domain logic: validate UUID
                    if (!isValidUUID(fileName)) {
                        logger.debug("Skipping non-UUID filename: {}", filePath);
                        continue;
                    }

                    // Domain logic: deserialize
                    String content = file.getContentAsString();
                    T obj = deserializeAndVerify(content, filePath);
                    if (obj == null) {
                        continue;
                    }

                    // Domain logic: extract and validate
                    String itemId = extractId(obj);
                    String itemName = extractName(obj);

                    if (itemId == null || itemId.isEmpty()) {
                        logger.warn("Skipping {} with null/empty ID: {}", getTypeName(), filePath);
                        continue;
                    }

                    if (!itemId.equals(fileName)) {
                        logger.warn("{} ID mismatch: filename='{}' but id='{}' in path: {}", getTypeName(), fileName, itemId, filePath);
                        continue;
                    }

                    // Add to list
                    metadataList.add(new RepoItemMetadata(itemId, itemName != null ? itemName : itemId, filePath, file.getLastCommitId()));

                } catch (Exception e) {
                    logger.error("Failed to process file: {}", file.getFilePath(), e);
                }
            }

            logger.info("Loaded {} {} metadata from repository", metadataList.size(), getTypeName());
            return metadataList;

        } catch (GitAPIException | IOException e) {
            logger.error("Failed to load metadata from repository", e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets file content at a specific revision
     *
     * @param fileName File name (e.g., "abc-123" for libraries/abc-123)
     * @param revision Commit SHA or ref (e.g., "HEAD", "main", commit hash)
     * @return File content as string
     * @throws GitFileNotFoundException if file not found at revision
     * @throws GitOperationException    if git operation fails
     */
    @Override
    public String getContent(String fileName, String revision) throws GitFileNotFoundException, GitOperationException {

        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }
        if (revision == null || revision.trim().isEmpty()) {
            throw new IllegalArgumentException("Revision cannot be null or empty");
        }

        logger.debug("Getting content for {} '{}' at revision '{}'", getTypeName(), fileName, revision);

        try {
            // Build file path
            String filePath = getDirectory() + "/" + fileName;

            // ✅ Read through GitOperations (NO direct Git access)
            byte[] content = gitOps.readFileAtRevision(filePath, revision);

            // Convert to string
            String contentStr = new String(content, StandardCharsets.UTF_8);

            logger.debug("Successfully loaded content for {} '{}' ({} bytes)", getTypeName(), fileName, content.length);

            return contentStr;

        } catch (GitFileNotFoundException e) {
            // Pass through
            logger.warn("File not found: {} at revision {}", fileName, revision);
            throw e;

        } catch (GitAPIException | IOException e) {
            logger.error("Failed to get content for file: {} at revision: {}", fileName, revision, e);
            throw new GitOperationException("Failed to get content for file: " + fileName + " at revision: " + revision, e);
        }
    }

    /**
     * Gets commit history for an entity
     *
     * @param id Entity ID (file name)
     * @return List of commit metadata, ordered from newest to oldest
     * @throws GitOperationException    if Git operation fails
     * @throws IllegalArgumentException if ID is invalid
     */
    @Override
    public List<CommitMetaData> getHistory(String id) throws GitOperationException, IllegalArgumentException {

        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }

        // Validate file name (security check)
        if (id.contains("..") || id.contains("/") || id.contains("\\")) {
            throw new IllegalArgumentException("Invalid ID: " + id);
        }

        logger.debug("Getting history for {} '{}'", getTypeName(), id);

        try {
            // Build file path
            String filePath = getDirectory() + "/" + id;

            // ✅ Call GitOperations (NO direct Git access)
            List<CommitMetaData> history = gitOps.getFileHistory(filePath);

            logger.info("Found {} commits for {} '{}'", history.size(), getTypeName(), id);
            return history;

        } catch (GitAPIException | IOException e) {
            logger.error("Failed to get history for {} '{}'", getTypeName(), id, e);
            throw new GitOperationException("Failed to get commit history for " + getTypeName() + ": " + id, e);
        }
    }

    /**
     * Hook called after successful commit, before push.
     * Override to perform entity-specific actions like saving revision metadata.
     *
     * @param entity    The entity that was committed
     * @param id        Entity ID
     * @param name      Entity name
     * @param commitSha The Git commit SHA
     */
    protected void postCommit(T entity, String id, String name, String commitSha) {
        // Default: do nothing
        // Subclasses override if needed
    }

    /**
     * Validates if string is a valid UUID format
     */
    private boolean isValidUUID(String str) {
        if (str == null) {
            return false;
        }
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Builds commit message for an entity
     */
    public String buildCommitMessage(T entity, String userMessage) {
        // Get server name
        String serverName = ControllerFactory.getFactory().createConfigurationController().getServerName();

        return CommitMessageUtil.create(entity, userMessage, serverId, serverName);
    }
}