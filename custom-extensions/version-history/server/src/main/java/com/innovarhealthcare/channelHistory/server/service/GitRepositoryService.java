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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.innovarhealthcare.channelHistory.server.exception.GitConflictException;
import com.innovarhealthcare.channelHistory.server.exception.GitFileNotFoundException;
import com.innovarhealthcare.channelHistory.server.exception.GitNotConnectedException;
import com.innovarhealthcare.channelHistory.server.exception.GitOperationException;
import com.innovarhealthcare.channelHistory.server.exception.GitPushFailedException;
import com.innovarhealthcare.channelHistory.server.file.FileOperations;
import com.innovarhealthcare.channelHistory.server.git.HttpsTransportConfig;
import com.innovarhealthcare.channelHistory.server.git.SshTransportConfig;
import com.innovarhealthcare.channelHistory.server.util.GitCommitterHelper;
import com.innovarhealthcare.channelHistory.server.git.GitOperations;
import com.mirth.connect.model.User;
import com.innovarhealthcare.channelHistory.server.repository.ChannelRepository;
import com.innovarhealthcare.channelHistory.server.repository.CodeTemplateRepository;
import com.innovarhealthcare.channelHistory.server.repository.GlobalScriptRepository;
import com.innovarhealthcare.channelHistory.server.repository.LibraryRepository;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoChanges;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoFile;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoFolder;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoInfo;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemChange;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.model.GitSettings;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.controllers.ControllerFactory;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.api.TransportConfigCallback;

/**
 * Git Repository Infrastructure Service
 * <p>
 * Responsibilities:
 * - Manage Git connection and lifecycle
 * - Initialize repository (clone or open + pull)
 * - Configure SSH authentication
 * - Provide GitOperations and FileOperations
 * - Provide Repository instances (factory)
 * - Track Git availability status
 * <p>
 * Lifecycle:
 * 1. Create: new GitRepositoryService()
 * 2. Start: startGit(properties)
 * 3. Check: isGitAvailable()
 * 4. Use: getLibraryRepository(), getChannelRepository(), etc.
 * 5. Stop: stopGit()
 * <p>
 * Git Availability:
 * - If Git initialization fails, service continues running
 * - gitAvailable flag tracks whether Git is working
 * - Users see error when trying to use Git features
 * - Check getGitStatus() for current state
 * <p>
 * Thread Safety: All public methods are synchronized
 */
public class GitRepositoryService {

    private static final Logger logger = LogManager.getLogger(GitRepositoryService.class);
    private static final String DATA_DIR = "version-history";

    // ========== State ==========
    private boolean started;
    private boolean gitAvailable;
    private String gitUnavailableReason;

    // ========== Configuration ==========
    // Hold reference
    private VersionHistoryProperties versionHistoryProperties;

    private File repositoryDirectory;
    private String serverId;
    private ObjectXMLSerializer serializer;

    // ========== Infrastructure Components ==========
    private Git git;
    private TransportConfigCallback transportConfig;
    private GitOperations gitOperations;
    private FileOperations fileOperations;

    /**
     * Creates a new GitRepositoryService.
     * Must call startGit() before using.
     */
    public GitRepositoryService(VersionHistoryProperties versionHistoryProperties) {
        this.started = false;
        this.gitAvailable = false;
        this.gitUnavailableReason = "Git not initialized";
        this.versionHistoryProperties = versionHistoryProperties;
    }

    // ========== Lifecycle Methods ==========

    /**
     * Starts Git infrastructure with configuration.
     * Does NOT throw exception if Git unavailable - sets flag instead.
     *
     * @throws Exception only for critical initialization errors (not Git-specific)
     */
    public synchronized void startGit() throws Exception {
        logger.info("Starting Git infrastructure...");

        // Check if enabled
        if (!this.versionHistoryProperties.isEnableVersionHistory()) {
            logger.warn("Version history is disabled in configuration");
            this.gitAvailable = false;
            this.gitUnavailableReason = "Version history is disabled";
            this.started = true;
            return;
        }

        try {
            // Initialize components
            initializeBasicComponents();
            validateConfiguration();
            transportConfig = buildTransportConfig(versionHistoryProperties.getGitSettings());

            // Validate remote connection before touching the local repository
            GitSettings gitSettings = versionHistoryProperties.getGitSettings();
            String connError = doValidateRemoteConnection(gitSettings.getRemoteRepositoryUrl(), gitSettings.getBranchName(), transportConfig);
            if (connError != null) {
                logger.warn("Git connection validation failed: {}", connError);
                this.gitAvailable = false;
                this.gitUnavailableReason = connError;
                this.started = true;
                return;
            }

            // Try to initialize Git repository
            initializeGitRepository();

            // Create operations
            createOperations();

            // Success!
            this.gitAvailable = true;
            this.gitUnavailableReason = null;
            this.started = true;

            logger.info("Git infrastructure started successfully");
            logger.info("Git is AVAILABLE and ready to use");

        } catch (Exception e) {
            // Git initialization failed - don't throw, just mark as unavailable
            logger.error("═══════════════════════════════════════════════════════");
            logger.error("Failed to initialize Git infrastructure: {}", e.getMessage());
            logger.error("Git features will be UNAVAILABLE");
            logger.error("Users will see error when trying to save/commit");
            logger.error("═══════════════════════════════════════════════════════");

            this.gitAvailable = false;
            this.gitUnavailableReason = e.getMessage();

            // Cleanup partial initialization
            cleanup();

            // Mark as started (but Git unavailable)
            this.started = true;
        }
    }

    /**
     * Stops Git infrastructure and releases resources.
     */
    public synchronized void stopGit() {
        logger.info("Stopping Git infrastructure...");

        cleanup();
        started = false;
        gitAvailable = false;
        gitUnavailableReason = "Git stopped";

        logger.info("Git infrastructure stopped");
    }

    /**
     * Checks if service is started.
     *
     * @return true if started
     */
    public boolean isStarted() {
        return started;
    }

    // ========== Git Status Methods ==========

    /**
     * Checks if Git is available and working.
     *
     * @return true if Git is available
     */
    public boolean isGitAvailable() {
        return gitAvailable;
    }

    /**
     * Gets the reason why Git is unavailable (if it is).
     *
     * @return reason string, or null if Git is available
     */
    public String getGitUnavailableReason() {
        return gitUnavailableReason;
    }

    /**
     * Gets Git status for display to user.
     *
     * @return GitStatus object with availability and message
     */
    public GitStatus getGitStatus() {
        if (!started) {
            return new GitStatus(false, "Service not started");
        }
        if (!gitAvailable) {
            return new GitStatus(false, gitUnavailableReason);
        }
        return new GitStatus(true, "Git is available and working");
    }

    // ========== Repository Factory Methods ==========

    /**
     * Gets LibraryRepository instance.
     *
     * @return LibraryRepository instance
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     */
    public LibraryRepository getLibraryRepository() {
        ensureStarted();
        ensureGitAvailable();
        return new LibraryRepository(gitOperations, fileOperations, serverId);
    }

    // Future repositories:
    public ChannelRepository getChannelRepository() {
        ensureStarted();
        ensureGitAvailable();
        return new ChannelRepository(gitOperations, fileOperations, serverId);
    }

    public CodeTemplateRepository getCodeTemplateRepository() {
        ensureStarted();
        ensureGitAvailable();
        return new CodeTemplateRepository(gitOperations, fileOperations, serverId);
    }

    public GlobalScriptRepository getGlobalScriptRepository() {
        ensureStarted();
        ensureGitAvailable();
        return new GlobalScriptRepository(gitOperations, fileOperations, serverId);
    }

    // ========== Direct Access Methods ==========

    /**
     * Gets GitOperations instance.
     *
     * @return GitOperations instance
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     */
    public GitOperations getGitOperations() {
        ensureStarted();
        ensureGitAvailable();
        return gitOperations;
    }

    /**
     * Gets FileOperations instance.
     *
     * @return FileOperations instance
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     */
    public FileOperations getFileOperations() {
        ensureStarted();
        ensureGitAvailable();
        return fileOperations;
    }

    // ========== Repository Info ==========

    /**
     * Returns a snapshot of the repository's structure and size.
     * Scans the top two levels only (folder → files) and skips the .git directory.
     *
     * @return RepoInfo with local path, remote URL, branch, total size, and folder list
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     */
    public RepoInfo getRepoInfo() {
        ensureStarted();
        ensureGitAvailable();

        String remoteUrl = versionHistoryProperties.getGitSettings().getRemoteRepositoryUrl();
        String branch = versionHistoryProperties.getGitSettings().getBranchName();
        String localRepoPath = repositoryDirectory.getAbsolutePath();
        long totalSizeBytes = FileUtils.sizeOfDirectory(repositoryDirectory);

        List<RepoFolder> folders = new ArrayList<>();
        File[] topLevel = repositoryDirectory.listFiles();
        if (topLevel != null) {
            for (File entry : topLevel) {
                if (!entry.isDirectory() || ".git".equals(entry.getName())) {
                    continue;
                }
                List<RepoFile> files = new ArrayList<>();
                File[] children = entry.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (child.isFile()) {
                            files.add(new RepoFile(child.getName(), child.length()));
                        }
                    }
                }
                folders.add(new RepoFolder(entry.getName(), files.size(), files));
            }
        }

        return new RepoInfo(localRepoPath, remoteUrl, branch, totalSizeBytes, folders);
    }

    /**
     * Returns the current working tree changes (modified/removed/missing and untracked files).
     *
     * @return RepoChanges snapshot
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     * @throws GitOperationException    if the status command fails
     */
    public RepoChanges getRepoChanges() throws GitOperationException {
        ensureStarted();
        ensureGitAvailable();
        try {
            return gitOperations.getRepoChanges();
        } catch (Exception e) {
            throw new GitOperationException("Failed to get repository changes: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the raw content of a file from the working tree filesystem.
     *
     * @param filePath Relative path from repository root (e.g., "Channels/abc.xml")
     * @return File content as UTF-8 string
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     * @throws GitFileNotFoundException if the file does not exist in the working tree
     */
    public String getFileContent(String filePath) {
        ensureStarted();
        ensureGitAvailable();
        try {
            return fileOperations.readFileContent(filePath);
        } catch (IOException e) {
            throw new GitFileNotFoundException("File not found in working tree: " + filePath);
        }
    }

    /**
     * Returns the raw content of a file at HEAD revision from the Git object store.
     *
     * @param filePath Relative path from repository root (e.g., "Channels/abc.xml")
     * @return File content as UTF-8 string
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     * @throws GitFileNotFoundException if the file does not exist at HEAD
     * @throws GitOperationException    if the Git read operation fails
     */
    public String getFileContentAtHead(String filePath) throws GitFileNotFoundException, GitOperationException {
        ensureStarted();
        ensureGitAvailable();
        try {
            byte[] bytes = gitOperations.readFileAtRevision(filePath, "HEAD");
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (GitFileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new GitOperationException("Failed to read file at HEAD: " + filePath + " — " + e.getMessage(), e);
        }
    }

    /**
     * Returns the commit history for a specific file path.
     *
     * @param filePath Relative path from repository root (e.g., "Channels/abc.xml")
     * @return List of commit metadata, newest first
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     * @throws GitFileNotFoundException if filePath is blank
     * @throws GitOperationException    if the log command fails
     */
    public synchronized List<CommitMetaData> getFileHistory(String filePath) throws GitOperationException {
        ensureStarted();
        ensureGitAvailable();
        if (filePath == null || filePath.isBlank()) {
            throw new GitFileNotFoundException("File path is required");
        }
        try {
            return gitOperations.getFileHistory(filePath);
        } catch (Exception e) {
            throw new GitOperationException("Failed to get file history for: " + filePath + " — " + e.getMessage(), e);
        }
    }

    /**
     * Returns the raw content of a file at a specific commit revision.
     *
     * @param filePath   Relative path from repository root (e.g., "Channels/abc.xml")
     * @param commitHash Commit SHA to read the file at
     * @return File content as UTF-8 string
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     * @throws GitFileNotFoundException if filePath or commitHash is blank, or file not found
     * @throws GitOperationException    if the Git read operation fails
     */
    public synchronized String getFileContentAtRevision(String filePath, String commitHash) throws GitFileNotFoundException, GitOperationException {
        ensureStarted();
        ensureGitAvailable();
        if (filePath == null || filePath.isBlank()) {
            throw new GitFileNotFoundException("File path is required");
        }
        if (commitHash == null || commitHash.isBlank()) {
            throw new GitFileNotFoundException("Commit hash is required");
        }
        try {
            return gitOperations.getFileContentAtRevision(filePath, commitHash);
        } catch (GitFileNotFoundException e) {
            throw e;
        } catch (GitOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new GitOperationException("Failed to read file '" + filePath + "' at revision '" + commitHash + "': " + e.getMessage(), e);
        }
    }

    /**
     * Returns the commit log for the entire repository.
     *
     * @param maxCount Maximum number of commits to return
     * @return List of commit metadata, newest first
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     * @throws GitOperationException    if the log command fails
     */
    public synchronized List<CommitMetaData> getRepoLog(int maxCount) throws GitOperationException {
        ensureStarted();
        ensureGitAvailable();
        try {
            return gitOperations.getRepoLog(maxCount);
        } catch (GitOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new GitOperationException("Failed to get repository log: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the list of files changed in a specific commit.
     *
     * @param commitHash Commit SHA to inspect
     * @return List of file changes with path and change type
     * @throws IllegalStateException    if service not started
     * @throws GitNotConnectedException if Git is not available
     * @throws GitFileNotFoundException if commitHash is blank
     * @throws GitOperationException    if the diff operation fails
     */
    public synchronized List<RepoItemChange> getCommitChanges(String commitHash) throws GitOperationException {
        ensureStarted();
        ensureGitAvailable();
        if (commitHash == null || commitHash.isBlank()) {
            throw new GitFileNotFoundException("Commit hash is required");
        }
        try {
            return gitOperations.getCommitChanges(commitHash);
        } catch (GitOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new GitOperationException("Failed to get commit changes: " + e.getMessage(), e);
        }
    }

    /**
     * Stages the given files, commits with the user's identity, rebases onto remote, and pushes.
     * On rebase conflict, backs up working-tree content of all committed files, calls
     * pullWithOverwrite() to restore the remote version, then rethrows the conflict so the
     * caller can present the backed-up content to the user.
     *
     * @param filePaths Relative file paths to stage and commit
     * @param message   Commit message
     * @param user      Committer user
     * @throws GitConflictException     if the rebase is stopped by a conflict with remote changes
     * @throws GitOperationException    if filePaths/message invalid or a Git operation fails
     * @throws GitPushFailedException   if the push is rejected
     */
    public synchronized void commitAndPushFiles(List<String> filePaths, String message, User user)
            throws GitConflictException, GitOperationException, GitPushFailedException {
        ensureStarted();
        ensureGitAvailable();
        if (filePaths == null || filePaths.isEmpty()) {
            throw new GitFileNotFoundException("At least one file path is required");
        }
        if (message == null || message.isBlank()) {
            throw new GitOperationException("Commit message is required");
        }
        try {
            gitOperations.commitAndPushFiles(filePaths, message, GitCommitterHelper.fromUser(user));
        } catch (GitConflictException e) {
            // Back up the working-tree content of every file that was being committed
            // (still present after the aborted rebase) before pullWithOverwrite() resets them.
            java.util.Map<String, String> backed = gitOperations.readWorkingTreeFiles(filePaths);
            logger.warn("Rebase conflict on {} file(s); backed up {} file(s), resetting to remote",
                    e.getConflictingFiles().size(), backed.size());
            try {
                gitOperations.pullWithOverwrite();
            } catch (Exception pullEx) {
                logger.error("pullWithOverwrite() after conflict failed: {}", pullEx.getMessage(), pullEx);
            }
            throw new GitConflictException(e.getMessage(), e.getConflictingFiles(), backed);
        }
    }

    /**
     * Writes the given content strings back to the working tree on disk.
     * No commit or push is performed.  Used to restore backed-up content after a rebase conflict.
     *
     * @param files Map of relative file paths to UTF-8 content
     * @throws GitOperationException if none of the files could be written
     */
    public synchronized void restoreFiles(java.util.Map<String, String> files) throws GitOperationException {
        ensureStarted();
        ensureGitAvailable();
        if (files == null || files.isEmpty()) {
            throw new GitOperationException("At least one file entry is required");
        }
        gitOperations.writeWorkingTreeFiles(files);
    }

    // ========== Connection Validation ==========

    /**
     * Validates the remote connection using the given gitSettings.
     * Uses ls-remote — no objects are downloaded, no temp directory needed.
     * Never throws — returns null on success or an error message string on failure.
     *
     * @param gitSettings Settings to validate (URL, branch, auth credentials)
     * @return null on success; error message on failure
     */
    public String validateRemoteConnection(GitSettings gitSettings) {
        if (gitSettings == null) {
            return "Git settings cannot be null";
        }

        String remoteUrl = gitSettings.getRemoteRepositoryUrl();
        String branch = gitSettings.getBranchName();

        if (remoteUrl == null || remoteUrl.trim().isEmpty()) {
            return "Remote repository URL is not configured";
        }
        if (branch == null || branch.trim().isEmpty()) {
            return "Branch name is not configured";
        }

        return doValidateRemoteConnection(remoteUrl, branch, buildTransportConfig(gitSettings));
    }

    private String doValidateRemoteConnection(String remoteUrl, String branch, TransportConfigCallback config) {
        try {
            // Use ls-remote instead of clone — only establishes the connection and lists refs,
            // no objects are downloaded and no temp directory is needed. Much faster.
            Collection<Ref> refs = Git.lsRemoteRepository()
                    .setRemote(remoteUrl)
                    .setHeads(true)
                    .setTags(false)
                    .setTransportConfigCallback(config)
                    .call();

            // Verify the configured branch exists on the remote
            String expectedRef = "refs/heads/" + branch;
            boolean branchFound = refs.stream().anyMatch(ref -> expectedRef.equals(ref.getName()));
            if (!branchFound) {
                return "Branch '" + branch + "' not found on the remote. Check the branch name.";
            }

            logger.info("Connection validation succeeded for: {}", remoteUrl);
            return null;

        } catch (Exception e) {
            logger.warn("Connection validation failed: {}", e.getMessage());
            return friendlyValidationError(e);
        }
    }

    private String friendlyValidationError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Unknown error during connection validation";
        logger.warn("Remote connection validation failed: {}", msg);
        return msg;
    }

    // ========== Private Initialization Methods ==========

    /**
     * Initializes basic components (serializer, directories, server ID).
     */
    private void initializeBasicComponents() throws Exception {
        logger.debug("Initializing basic components...");

        // Get serializer
        serializer = ObjectXMLSerializer.getInstance();

        // Get server ID
        serverId = ControllerFactory.getFactory().createConfigurationController().getServerId();

        // Setup repository directory
        repositoryDirectory = new File(Donkey.getInstance().getConfiguration().getAppData(), DATA_DIR);

        logger.debug("Repository directory: {}", repositoryDirectory.getAbsolutePath());
        logger.debug("Server ID: {}", serverId);
    }

    /**
     * Validates configuration from properties.
     */
    private void validateConfiguration() {
        logger.debug("Validating configuration...");

        if (versionHistoryProperties.getGitSettings() == null) {
            throw new IllegalStateException("Git settings not configured");
        }

        String remoteUrl = versionHistoryProperties.getGitSettings().getRemoteRepositoryUrl();
        if (remoteUrl == null || remoteUrl.trim().isEmpty()) {
            throw new IllegalStateException("Remote repository URL is not configured");
        }

        String branch = versionHistoryProperties.getGitSettings().getBranchName();
        if (branch == null || branch.trim().isEmpty()) {
            throw new IllegalStateException("Branch name is not configured");
        }

        logger.debug("Configuration validated successfully");
    }

    /**
     * Builds a TransportConfigCallback for the given GitSettings.
     * Returns an SshTransportConfig for SSH auth or HttpsTransportConfig for HTTPS/PAT auth.
     */
    private TransportConfigCallback buildTransportConfig(GitSettings gitSettings) {
        if (gitSettings.isHTTPS()) {
            logger.debug("Building HTTPS transport config");
            return new HttpsTransportConfig(gitSettings);
        }
        logger.debug("Building SSH transport config");
        return new SshTransportConfig(gitSettings);
    }

    /**
     * Initializes or clones Git repository.
     * Throws exception if fails - will be caught by startGit().
     * <p>
     * Flow:
     * 1. Check if .git directory exists
     * 2. If exists: Open and pull latest changes
     * 3. If not exists: Clone from remote
     */
    private void initializeGitRepository() throws Exception {
        logger.debug("Initializing git repository...");

        // Create directory if needed
        if (!repositoryDirectory.exists()) {
            if (!repositoryDirectory.mkdirs()) {
                throw new IOException("Failed to create directory: " + repositoryDirectory.getAbsolutePath());
            }
            logger.info("Created repository directory: {}", repositoryDirectory.getAbsolutePath());
        }

        File gitDir = new File(repositoryDirectory, ".git");

        if (gitDir.exists()) {
            // Repository exists - open and pull
            openAndUpdate();
        } else {
            // No repository - clone from remote
            cloneFromRemote();
        }

        logger.info("Git repository initialized successfully");
    }

    /**
     * Opens existing repository, syncs remote URL/branch from current config, then pulls.
     */
    private void openAndUpdate() throws Exception {
        logger.info("Opening existing repository...");

        git = Git.open(repositoryDirectory);
        logger.info("Repository opened at: {}", repositoryDirectory.getAbsolutePath());

        // Sync remote URL and branch from current plugin settings into .git/config
        syncRemoteConfig();

        // Try to pull latest changes (non-fatal if fails)
        pullLatestChanges();
    }

    /**
     * Writes the current remoteRepositoryUrl and branchName from plugin settings
     * into the local .git/config so that subsequent push/pull use the new values.
     */
    private void syncRemoteConfig() throws IOException {
        String newUrl = versionHistoryProperties.getGitSettings().getRemoteRepositoryUrl();

        StoredConfig config = git.getRepository().getConfig();
        String currentUrl = config.getString("remote", "origin", "url");

        if (!newUrl.equals(currentUrl)) {
            logger.info("Remote URL changed from '{}' to '{}', updating .git/config", currentUrl, newUrl);
            config.setString("remote", "origin", "url", newUrl);
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            config.save();
        }
    }

    /**
     * Clones repository from remote.
     * Throws exception if clone fails.
     */
    private void cloneFromRemote() throws Exception {
        logger.info("No local repository found, cloning from remote...");

        String remoteUrl = versionHistoryProperties.getGitSettings().getRemoteRepositoryUrl();
        String branch = versionHistoryProperties.getGitSettings().getBranchName();

        logger.info("Cloning from: {}", remoteUrl);
        logger.info("Branch: {}", branch);

        git = Git.cloneRepository().setURI(remoteUrl).setDirectory(repositoryDirectory).setBranch(branch).setTransportConfigCallback(transportConfig).call();

        logger.info("Successfully cloned repository from: {}", remoteUrl);
    }

    /**
     * Pulls latest changes from remote.
     * Non-fatal: logs warning if fails but doesn't throw.
     */
    private void pullLatestChanges() {
        logger.info("Pulling latest changes from remote...");

        try {
            PullResult result = git.pull().setRemote("origin").setRemoteBranchName(versionHistoryProperties.getGitSettings().getBranchName()).setTransportConfigCallback(transportConfig).call();

            if (result.isSuccessful()) {
                logger.info("Successfully pulled latest changes");
            } else {
                logger.warn("Pull completed with issues: {}", result.getMergeResult());
            }

        } catch (Exception e) {
            logger.warn("Failed to pull from remote: {}", e.getMessage());
            logger.warn("Continuing with local repository");
            // Don't throw - can work with local repo
        }
    }

    /**
     * Creates operations instances.
     */
    private void createOperations() {
        logger.debug("Creating operations...");

        gitOperations = new GitOperations(git, versionHistoryProperties.getGitSettings().getBranchName(), transportConfig);

        fileOperations = new FileOperations(repositoryDirectory, serializer);

        logger.debug("Operations created successfully");
    }

    /**
     * Cleans up resources.
     */
    private void cleanup() {
        if (git != null) {
            git.close();
            git = null;
        }

        gitOperations = null;
        fileOperations = null;
        transportConfig = null;
    }

    /**
     * Ensures service is started before operations.
     *
     * @throws IllegalStateException if service not started
     */
    private void ensureStarted() {
        if (!started) {
            throw new IllegalStateException("GitRepositoryService is not started. Call startGit() first.");
        }
    }

    /**
     * Ensures Git is available before operations.
     *
     * @throws GitNotConnectedException if Git is not available
     */
    private void ensureGitAvailable() {
        if (!gitAvailable) {
            throw new GitNotConnectedException("Git repository is not connected. " + gitUnavailableReason);
        }
    }

    // ========== Status Classes ==========

    /**
     * Git status information for display/API
     */
    public static class GitStatus {
        private final boolean available;
        private final String message;

        public GitStatus(boolean available, String message) {
            this.available = available;
            this.message = message;
        }

        public boolean isAvailable() {
            return available;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "GitStatus{available=" + available + ", message='" + message + "'}";
        }
    }
}
