/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.server.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.innovarhealthcare.channelHistory.server.exception.GitConflictException;
import com.innovarhealthcare.channelHistory.server.exception.GitFileNotFoundException;
import com.innovarhealthcare.channelHistory.server.exception.GitOperationException;
import com.innovarhealthcare.channelHistory.server.exception.GitPushFailedException;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoChanges;
import com.innovarhealthcare.channelHistory.shared.dto.response.RemoteStatus;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemChange;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * Handles all Git operations for the version history plugin.
 * Provides methods for pull, commit, push, and status checking.
 */
public class GitOperations {

    private static final Logger logger = LogManager.getLogger(GitOperations.class);

    private final Git git;
    private final String branch;
    private final TransportConfigCallback transportConfig;

    /**
     * Creates a new GitOperations instance
     *
     * @param git             The JGit Git instance
     * @param branch          The branch name (e.g., "main", "master")
     * @param transportConfig Transport config callback for authentication (SSH or HTTPS)
     */
    public GitOperations(Git git, String branch, TransportConfigCallback transportConfig) {
        if (git == null) {
            throw new IllegalArgumentException("Git instance cannot be null");
        }
        if (branch == null || branch.trim().isEmpty()) {
            throw new IllegalArgumentException("Branch cannot be null or empty");
        }
        if (transportConfig == null) {
            throw new IllegalArgumentException("Transport config cannot be null");
        }

        this.git = git;
        this.branch = branch;
        this.transportConfig = transportConfig;
    }

    /**
     * Reads all files from a directory in the latest commit
     *
     * @param directory Directory path (e.g., "libraries", "codetemplates")
     * @return List of committed files with content and metadata
     * @throws GitAPIException if git operation fails
     * @throws IOException     if I/O error occurs
     */
    public List<CommittedFile> readCommittedFiles(String directory) throws GitAPIException, IOException {

        logger.debug("Reading committed files from directory: {}", directory);

        List<CommittedFile> files = new ArrayList<>();
        Repository repo = git.getRepository();
        String path = directory + "/";

        // Get latest commit
        ObjectId lastCommitId = repo.resolve(Constants.HEAD);
        if (lastCommitId == null) {
            logger.warn("No commits in repository");
            return files;
        }

        RevWalk revWalk = new RevWalk(repo);
        RevCommit commit = revWalk.parseCommit(lastCommitId);
        RevTree tree = commit.getTree();

        // Walk through tree
        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(false);
        treeWalk.setFilter(PathFilter.create(path));

        while (treeWalk.next()) {
            if (treeWalk.isSubtree()) {
                treeWalk.enterSubtree();
            } else {
                try {
                    String fileName = treeWalk.getNameString();
                    String filePath = treeWalk.getPathString();

                    // Read file content from Git object
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repo.open(objectId);
                    byte[] content = loader.getBytes();

                    // Get last commit for this file
                    String commitId = lastCommitId.getName();

                    files.add(new CommittedFile(fileName, filePath, content, commitId));

                } catch (Exception e) {
                    logger.error("Failed to read file: {}", treeWalk.getPathString(), e);
                }
            }
        }

        revWalk.close();
        treeWalk.close();

        logger.info("Read {} files from directory: {}", files.size(), directory);
        return files;
    }

    /**
     * Reads file content at a specific revision (commit)
     *
     * @param filePath Relative file path from repository root (e.g., "libraries/abc-123")
     * @param revision Commit SHA or ref (e.g., "HEAD", "main", commit hash)
     * @return File content as bytes
     * @throws GitFileNotFoundException if file not found at revision
     * @throws GitAPIException          if git operation fails
     * @throws IOException              if I/O error occurs
     */
    public byte[] readFileAtRevision(String filePath, String revision) throws GitFileNotFoundException, GitAPIException, IOException {

        logger.debug("Reading file '{}' at revision '{}'", filePath, revision);

        Repository repo = git.getRepository();

        try (TreeWalk treeWalk = new TreeWalk(repo)) {
            // Resolve revision
            ObjectId revisionId = repo.resolve(revision);
            if (revisionId == null) {
                throw new GitFileNotFoundException("Invalid revision: " + revision + " for file: " + filePath);
            }

            // Parse commit
            RevCommit commit = repo.parseCommit(revisionId);

            // Walk tree to find file
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(filePath));
            treeWalk.addTree(commit.getTree());

            // Check if file exists
            if (!treeWalk.next()) {
                throw new GitFileNotFoundException("File not found: " + filePath + " at revision: " + revision);
            }

            // Load file content
            ObjectLoader loader = repo.open(treeWalk.getObjectId(0));
            byte[] content = loader.getBytes();

            logger.debug("Successfully read {} bytes from file '{}'", content.length, filePath);
            return content;

        } catch (GitFileNotFoundException e) {
            throw e;

        } catch (IOException e) {
            throw new IOException("Failed to read file: " + filePath + " at revision: " + revision, e);
        }
    }

    /**
     * Gets commit history for a file
     *
     * @param filePath Relative file path from repository root (e.g., "libraries/abc-123")
     * @return List of commit metadata, ordered from newest to oldest
     * @throws GitAPIException if git operation fails
     * @throws IOException     if I/O error occurs
     */
    public List<CommitMetaData> getFileHistory(String filePath) throws GitAPIException, IOException {

        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }

        logger.debug("Getting commit history for file: {}", filePath);

        List<CommitMetaData> history = new ArrayList<>();
        Repository repo = git.getRepository();

        // Get commit history for specific file
        LogCommand logCommand = git.log().add(repo.resolve(Constants.HEAD)).addPath(filePath);

        Iterable<RevCommit> commits = logCommand.call();

        for (RevCommit commit : commits) {
            String hash = commit.getId().getName();
            String committer = commit.getCommitterIdent() != null ? commit.getCommitterIdent().getName() : "Unknown";
            long timestamp = commit.getCommitTime() * 1000L; // Convert to milliseconds
            String message = commit.getFullMessage() != null ? commit.getFullMessage() : "";

            history.add(new CommitMetaData(hash, committer, timestamp, message));
        }

        logger.info("Found {} commits for file: {}", history.size(), filePath);
        return history;
    }

    /**
     * Gets file content at a specific commit revision as a UTF-8 string.
     *
     * @param filePath   Relative file path from repository root
     * @param commitHash Commit SHA to read the file at
     * @return File content as string, or empty string if not found
     * @throws GitFileNotFoundException if the file does not exist at that revision
     * @throws GitOperationException    if the Git operation fails
     */
    public String getFileContentAtRevision(String filePath, String commitHash) throws GitFileNotFoundException, GitOperationException {
        try {
            byte[] bytes = readFileAtRevision(filePath, commitHash);
            return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : "";
        } catch (GitFileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new GitOperationException("Failed to read file '" + filePath + "' at revision '" + commitHash + "': " + e.getMessage(), e);
        }
    }

    /**
     * Gets the current working tree changes: modified, removed, or missing files
     * and new untracked files.
     *
     * @return RepoChanges with changedFiles and untrackedFiles lists
     * @throws GitAPIException if git operation fails
     */
    public RepoChanges getRepoChanges() throws GitAPIException {
        logger.debug("Getting working tree changes");

        Status status = git.status().call();

        List<String> modifiedFiles = new ArrayList<>(status.getModified());

        List<String> deletedFiles = new ArrayList<>();
        deletedFiles.addAll(status.getRemoved());
        deletedFiles.addAll(status.getMissing());

        List<String> untrackedFiles = new ArrayList<>(status.getUntracked());

        logger.info("Found {} modified, {} deleted, {} untracked files", modifiedFiles.size(), deletedFiles.size(), untrackedFiles.size());
        return new RepoChanges(modifiedFiles, deletedFiles, untrackedFiles);
    }

    /**
     * Checks if the remote repository has changes that are not in local
     *
     * @return true if remote has changes, false otherwise
     * @throws GitAPIException if git operation fails
     * @throws IOException     if I/O error occurs
     */
    public boolean hasRemoteChanges() throws GitAPIException, IOException {
        logger.debug("Checking for remote changes on branch: {}", branch);

        // Fetch
        //@formatter:off
        git.fetch()
           .setRemote("origin")
           .setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/remotes/origin/" + branch))
           .setTransportConfigCallback(transportConfig)
           .call();
        //@formatter:on

        // Get refs
        Ref localRef = git.getRepository().findRef(branch);
        Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);

        // Simple comparison
        if (localRef == null || remoteRef == null) {
            return false;
        }

        return !localRef.getObjectId().equals(remoteRef.getObjectId());
    }

    /**
     * Fetches from origin and returns the number of commits local is ahead of and behind the
     * remote tracking branch. Both counts use a RevWalk from the respective tip to the merge-base,
     * so no upstream tracking configuration is required.
     *
     * @return RemoteStatus with aheadCount and behindCount
     * @throws GitAPIException if the fetch fails
     * @throws IOException     if a ref or object cannot be read
     */
    public RemoteStatus getAheadBehindCounts() throws GitAPIException, IOException {
        fetch();
        Ref localRef  = git.getRepository().findRef(branch);
        Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);
        if (localRef == null || remoteRef == null) {
            return new RemoteStatus(0, 0);
        }
        ObjectId localId  = localRef.getObjectId();
        ObjectId remoteId = remoteRef.getObjectId();
        if (localId.equals(remoteId)) {
            return new RemoteStatus(0, 0);
        }
        int ahead = 0;
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            walk.markStart(walk.parseCommit(localId));
            walk.markUninteresting(walk.parseCommit(remoteId));
            while (walk.next() != null){ 
                ahead++;
            }
        }
        int behind = 0;
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            walk.markStart(walk.parseCommit(remoteId));
            walk.markUninteresting(walk.parseCommit(localId));
            while (walk.next() != null){ 
                behind++;
            }
        }
        logger.debug("Remote sync status: ahead={}, behind={}", ahead, behind);
        return new RemoteStatus(ahead, behind);
    }

    /**
     * Pulls changes from remote repository with hard reset (overwrites local changes)
     *
     * @return Pull result message
     * @throws GitAPIException if git operation fails
     * @throws IOException     if I/O error occurs
     */
    public String pullWithOverwrite() throws GitAPIException, IOException {
        logger.info("Pulling with overwrite from origin/{}", branch);

        StringBuilder result = new StringBuilder();

        // Check for local changes that will be lost
        Status status = git.status().call();
        if (!status.getModified().isEmpty() || !status.getUntracked().isEmpty()) {
            result.append("Warning: Local changes will be discarded:\n");
            result.append("  Modified: ").append(status.getModified()).append("\n");
            result.append("  Untracked: ").append(status.getUntracked()).append("\n");
            logger.warn("Local changes will be discarded: {}", status.getModified());
        }

        // Fetch from remote
        FetchCommand fetchCommand = git.fetch();
        fetchCommand.setRemote("origin");
        fetchCommand.setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/remotes/origin/" + branch));
        fetchCommand.setTransportConfigCallback(transportConfig);

        FetchResult fetchResult = fetchCommand.call();
        result.append("Fetch completed: ").append(fetchResult.getMessages()).append("\n");

        // Reset to remote branch
        Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);
        if (remoteRef == null) {
            throw new GitAPIException("Remote branch origin/" + branch + " not found") {
            };
        }

        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(remoteRef.getName()).call();

        result.append("Reset to origin/").append(branch).append(" completed\n");

        logger.info("Pull with overwrite completed successfully");
        return result.toString();
    }

    /**
     * Pulls changes from remote using a normal merge strategy.
     * Fast-forward and clean merges complete silently.
     * Conflicts are auto-resolved by taking the remote (theirs) version of each conflicting file,
     * then a merge commit is created. This preserves any local unpushed commits.
     *
     * @throws GitAPIException if git operation fails
     * @throws IOException     if I/O error occurs
     */
    public void pullNormal() throws GitAPIException, IOException {
        logger.info("Pulling (normal merge) from origin/{}", branch);

        fetch();

        Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);
        if (remoteRef == null) {
            throw new IOException("Remote ref not found: refs/remotes/origin/" + branch);
        }

        MergeResult mergeResult = git.merge()
                .include(remoteRef)
                .setFastForward(MergeCommand.FastForwardMode.FF)
                .call();

        MergeResult.MergeStatus status = mergeResult.getMergeStatus();
        logger.info("Merge status: {}", status);

        if (status == MergeResult.MergeStatus.CONFLICTING) {
            Map<String, int[][]> conflicts = mergeResult.getConflicts();
            logger.warn("Merge conflicts in {} file(s); auto-resolving using remote version", conflicts.size());

            CheckoutCommand checkout = git.checkout();
            for (String conflictPath : conflicts.keySet()) {
                checkout.setStage(CheckoutCommand.Stage.THEIRS).addPath(conflictPath);
            }
            checkout.call();

            for (String conflictPath : conflicts.keySet()) {
                git.add().addFilepattern(conflictPath).call();
            }

            git.commit()
                    .setMessage("Merge remote-tracking branch 'origin/" + branch + "' (conflicts resolved using remote)")
                    .call();

            logger.info("Pull completed: conflicts in {} file(s) resolved using remote version", conflicts.size());
        } else if (status.isSuccessful()) {
            logger.info("Pull completed successfully: {}", status);
        } else {
            throw new IOException("Merge failed with status: " + status);
        }
    }

    /**
     * Stages files for commit
     *
     * @param filePaths List of file paths to stage (relative to repo root)
     * @throws GitAPIException if git operation fails
     */
    public void stageFiles(List<String> filePaths) throws GitAPIException {
        if (filePaths == null || filePaths.isEmpty()) {
            logger.warn("No files to stage");
            return;
        }

        logger.debug("Staging {} files", filePaths.size());

        for (String path : filePaths) {
            git.add().addFilepattern(path).call();
            logger.debug("Staged file: {}", path);
        }

        logger.info("Successfully staged {} files", filePaths.size());
    }

    /**
     * Commits staged changes
     *
     * @param message   Commit message
     * @param committer Person making the commit
     * @return Commit SHA
     * @throws GitAPIException if git operation fails
     */
    public String commit(String message, PersonIdent committer) throws GitAPIException {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Commit message cannot be empty");
        }
        if (committer == null) {
            throw new IllegalArgumentException("Committer cannot be null");
        }

        logger.info("Creating commit with message: {}", message);

        org.eclipse.jgit.revwalk.RevCommit commit = git.commit().setCommitter(committer).setMessage(message).call();

        String commitSha = commit.getName();
        logger.info("Commit created successfully: {}", commitSha);

        return commitSha;
    }

    /**
     * Commits files with staging in one operation
     *
     * @param filePaths List of file paths to commit
     * @param message   Commit message
     * @param committer Person making the commit
     * @return Commit SHA
     * @throws GitAPIException if git operation fails
     */
    public String commitFiles(List<String> filePaths, String message, PersonIdent committer) throws GitAPIException {
        stageFiles(filePaths);
        return commit(message, committer);
    }

    /**
     * Pushes commits to remote repository
     *
     * @param forcePush If true, uses force push
     * @return Push result summary
     * @throws GitAPIException if git operation fails
     */
    public String push(boolean forcePush) throws GitAPIException, GitPushFailedException {
        logger.info("Pushing to origin/{} (force: {})", branch, forcePush);

        PushCommand pushCommand = git.push();
        pushCommand.setRemote("origin");
        pushCommand.setRefSpecs(new RefSpec("refs/heads/" + branch));
        pushCommand.setForce(forcePush);
        pushCommand.setTransportConfigCallback(transportConfig);

        Iterable<PushResult> results = pushCommand.call();

        StringBuilder resultMessage = new StringBuilder();
        boolean pushSuccessful = false;

        for (PushResult result : results) {
            resultMessage.append("Remote: ").append(result.getURI()).append("\n");

            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                resultMessage.append("  Ref: ").append(update.getRemoteName()).append(", Status: ").append(update.getStatus()).append("\n");

                if (update.getStatus() == RemoteRefUpdate.Status.OK
                        || update.getStatus() == RemoteRefUpdate.Status.UP_TO_DATE) {
                    pushSuccessful = true;
                    logger.info("Push successful for ref: {} ({})", update.getRemoteName(), update.getStatus());
                } else {
                    logger.warn("Push status for {}: {}", update.getRemoteName(), update.getStatus());
                    if (update.getMessage() != null) {
                        resultMessage.append("    Message: ").append(update.getMessage()).append("\n");
                    }
                }
            }
        }

        if (!pushSuccessful) {
            throw new GitPushFailedException("Push failed: " + resultMessage.toString()) {
            };
        }

        return resultMessage.toString();
    }

    /**
     * Gets the current branch name
     *
     * @return Current branch name
     * @throws IOException if I/O error occurs
     */
    public String getCurrentBranch() throws IOException {
        return git.getRepository().getBranch();
    }

    /**
     * Validates that the current branch matches the configured branch
     *
     * @throws GitOperationException if branches don't match
     */
    public void validateCurrentBranch() throws GitOperationException {
        try {
            String currentBranch = getCurrentBranch();
            if (!branch.equals(currentBranch)) {
                throw new GitOperationException("Current branch is " + currentBranch + ", expected " + branch);
            }
        } catch (IOException e) {
            throw new GitOperationException("Failed to get current branch", e);
        }
    }

    /**
     * Checks if repository has any commits
     *
     * @return true if repository has commits, false otherwise
     * @throws IOException if I/O error occurs
     */
    public boolean hasCommits() throws IOException {
        return git.getRepository().resolve("HEAD") != null;
    }

    /**
     * Gets commit log for the entire repository
     *
     * @param maxCount Maximum number of commits to return
     * @return List of commit metadata, newest first
     * @throws GitOperationException if git operation fails
     */
    public List<CommitMetaData> getRepoLog(int maxCount) throws GitOperationException {
        logger.debug("Getting repository log, maxCount={}", maxCount);
        try {
            List<CommitMetaData> result = new ArrayList<>();
            for (RevCommit commit : git.log().setMaxCount(maxCount).call()) {
                String hash = commit.getId().getName();
                String committer = commit.getCommitterIdent() != null ? commit.getCommitterIdent().getName() : "Unknown";
                long timestamp = commit.getCommitTime() * 1000L;
                String message = commit.getFullMessage() != null ? commit.getFullMessage() : "";
                result.add(new CommitMetaData(hash, committer, timestamp, message));
            }
            logger.info("getRepoLog returned {} commits", result.size());
            return result;
        } catch (GitAPIException e) {
            throw new GitOperationException("Failed to get repository log: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the list of files changed in a specific commit
     *
     * @param commitHash Full or abbreviated commit SHA
     * @return List of file changes with path and change type
     * @throws GitOperationException if git operation fails or hash cannot be resolved
     */
    public List<RepoItemChange> getCommitChanges(String commitHash) throws GitOperationException {
        logger.debug("Getting commit changes for hash: {}", commitHash);
        Repository repo = git.getRepository();
        try (RevWalk revWalk = new RevWalk(repo); DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            formatter.setRepository(repo);

            ObjectId objectId = repo.resolve(commitHash);
            if (objectId == null) {
                throw new GitOperationException("Cannot resolve commit hash: " + commitHash);
            }

            RevCommit commit = revWalk.parseCommit(objectId);
            List<DiffEntry> diffs;

            if (commit.getParentCount() > 0) {
                RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
                diffs = formatter.scan(parent.getTree(), commit.getTree());
            } else {
                // Initial commit — compare against empty tree
                try (ObjectReader reader = repo.newObjectReader()) {
                    CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
                    newTreeParser.reset(reader, commit.getTree());
                    diffs = formatter.scan(new EmptyTreeIterator(), newTreeParser);
                }
            }

            List<RepoItemChange> result = new ArrayList<>();
            for (DiffEntry diff : diffs) {
                String path = diff.getChangeType() == DiffEntry.ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();
                String changeType = mapChangeType(diff.getChangeType());
                result.add(new RepoItemChange(path, changeType));
            }

            logger.info("getCommitChanges: {} changes for commit {}", result.size(), commitHash);
            return result;

        } catch (GitOperationException e) {
            throw e;
        } catch (IOException e) {
            throw new GitOperationException("Failed to get commit changes for: " + commitHash, e);
        }
    }

    /**
     * Reads the content of each file in {@code filePaths} from the working tree and returns
     * a map of relative path → content.
     * <ul>
     *   <li>File exists → value is the UTF-8 content string.</li>
     *   <li>File does not exist (deleted by the user) → value is {@code null} (deletion sentinel).</li>
     *   <li>File cannot be read → path is skipped and a warning is logged.</li>
     * </ul>
     * Callers must handle {@code null} values — they represent files that should be deleted
     * when the backup is restored via {@link #writeWorkingTreeFiles}.
     *
     * @param filePaths Relative paths to read (relative to the repository working tree root)
     * @return Map of relative path → file content or null; never null as a map
     */
    public Map<String, String> readWorkingTreeFiles(List<String> filePaths) {
        Map<String, String> result = new LinkedHashMap<>();
        if (filePaths == null || filePaths.isEmpty()) {
            return result;
        }
        java.io.File workTree = git.getRepository().getWorkTree();
        for (String relativePath : filePaths) {
            try {
                java.io.File file = new java.io.File(workTree, relativePath);
                if (file.exists() && file.isFile()) {
                    byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                    result.put(relativePath, new String(bytes, StandardCharsets.UTF_8));
                } else {
                    // File absent — the user staged a deletion; record with null sentinel
                    result.put(relativePath, null);
                    logger.debug("Backed up deletion of working-tree file '{}'", relativePath);
                }
            } catch (Exception e) {
                logger.warn("Could not back up working-tree file '{}': {}", relativePath, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Writes or deletes files in the working tree to match a backed-up state.
     * <ul>
     *   <li>Value is non-null → write content to the file (creates parent dirs as needed).</li>
     *   <li>Value is {@code null} → delete the file (deletion sentinel from
     *       {@link #readWorkingTreeFiles}).</li>
     * </ul>
     * Any individual entry that cannot be processed is logged and skipped so a partial
     * restore still proceeds.
     *
     * @param files Map of relative path → UTF-8 content, or null to delete
     * @throws GitOperationException if none of the entries could be processed
     */
    public void writeWorkingTreeFiles(Map<String, String> files) throws GitOperationException {
        if (files == null || files.isEmpty()) {
            return;
        }
        java.io.File workTree = git.getRepository().getWorkTree();
        int processed = 0;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String relativePath = entry.getKey();
            String content = entry.getValue();
            try {
                java.io.File file = new java.io.File(workTree, relativePath);
                if (content == null) {
                    // null sentinel — restore the deletion
                    java.nio.file.Files.deleteIfExists(file.toPath());
                    logger.debug("Deleted working-tree file '{}' (restore of deletion)", relativePath);
                } else {
                    java.io.File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    java.nio.file.Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8),
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                    logger.debug("Restored working-tree file '{}'", relativePath);
                }
                processed++;
            } catch (Exception e) {
                logger.error("Could not restore working-tree file '{}': {}", relativePath, e.getMessage(), e);
            }
        }
        if (processed == 0 && !files.isEmpty()) {
            throw new GitOperationException("Failed to restore any files to the working tree");
        }
        logger.info("Restored {}/{} file(s) to working tree", processed, files.size());
    }

    /**
     * Stages the given files, commits, rebases onto the remote if needed, and pushes.
     * Unlike saveAndPush flows, this method does NOT call pullWithOverwrite() first —
     * the files are already present in the working tree and must not be discarded.
     *
     * @param filePaths Relative paths of files to stage and commit
     * @param message   Commit message
     * @param committer Person making the commit
     * @throws GitConflictException   if the rebase is stopped by a conflict with remote changes
     * @throws GitOperationException  if any other Git operation fails
     * @throws GitPushFailedException if the push is rejected by the remote
     */
    public void commitAndPushFiles(List<String> filePaths, String message, PersonIdent committer) throws GitConflictException, GitOperationException, GitPushFailedException {
        try {
            stageFiles(filePaths);
            commit(message, committer);
            fetch();
            rebaseOntoRemote();
            push(false);
        } catch (GitConflictException | GitPushFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new GitOperationException("Failed to commit and push files: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches from origin, rebases the local branch onto the remote tracking ref, then pushes.
     * No staging or commit is performed — intended for pushing already-committed local work.
     *
     * @throws GitConflictException   if the rebase is stopped by a conflict with remote changes
     * @throws GitPushFailedException if the push is rejected by the remote
     * @throws GitAPIException        if any git API operation fails
     * @throws IOException            if an I/O error occurs during ref lookup
     */
    public void fetchRebaseAndPush() throws GitAPIException, IOException, GitConflictException, GitPushFailedException {
        fetch();
        rebaseOntoRemote();
        push(false);
    }

    /**
     * Fetches the configured branch from origin into refs/remotes/origin/<branch>.
     */
    private void fetch() throws GitAPIException {
        logger.debug("Fetching from origin/{}", branch);
        git.fetch().setRemote("origin").setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/remotes/origin/" + branch)).setTransportConfigCallback(transportConfig).call();
        logger.debug("Fetch completed");
    }

    /**
     * Rebases the current branch onto refs/remotes/origin/<branch>.
     * Returns immediately if the remote ref does not exist or the branch is already up-to-date.
     * Aborts the rebase and throws {@link GitConflictException} if a conflict is detected.
     */
    private void rebaseOntoRemote() throws GitAPIException, IOException, GitConflictException {
        Ref remoteRef = git.getRepository().findRef("refs/remotes/origin/" + branch);
        if (remoteRef == null) {
            logger.warn("Remote ref not found for origin/{}, skipping rebase", branch);
            return;
        }

        logger.debug("Rebasing onto origin/{}", branch);
        RebaseResult result = git.rebase().setUpstream(remoteRef.getObjectId()).call();

        RebaseResult.Status status = result.getStatus();
        logger.info("Rebase result: {}", status);

        if (status == RebaseResult.Status.OK || status == RebaseResult.Status.UP_TO_DATE || status == RebaseResult.Status.FAST_FORWARD) {
            return;
        }

        // Conflict or unexpected state — collect files and abort to restore clean state
        List<String> conflicts = result.getConflicts() != null ? new ArrayList<>(result.getConflicts()) : new ArrayList<>();

        try {
            git.rebase().setOperation(RebaseCommand.Operation.ABORT).call();
            logger.info("Rebase aborted; conflicting files: {}", conflicts);
        } catch (Exception abortEx) {
            logger.error("Failed to abort rebase after conflict: {}", abortEx.getMessage());
        }

        throw new GitConflictException("Rebase conflict with remote changes. Conflicting files: " + conflicts, conflicts);
    }

    private String mapChangeType(DiffEntry.ChangeType changeType) {
        switch (changeType) {
            case ADD:
                return "ADDED";
            case DELETE:
                return "DELETED";
            case MODIFY:
                return "MODIFIED";
            default:
                return "MODIFIED";
        }
    }

    /**
     * Gets the configured branch name
     *
     * @return Branch name
     */
    public String getBranch() {
        return branch;
    }

    /**
     * Represents a file from Git repository with its content and metadata
     */
    public static class CommittedFile {
        private final String fileName;
        private final String filePath;
        private final byte[] content;
        private final String lastCommitId;

        public CommittedFile(String fileName, String filePath, byte[] content, String lastCommitId) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.content = content;
            this.lastCommitId = lastCommitId;
        }

        public String getFileName() {
            return fileName;
        }

        public String getFilePath() {
            return filePath;
        }

        public byte[] getContent() {
            return content;
        }

        public String getContentAsString() {
            return new String(content, StandardCharsets.UTF_8);
        }

        public String getLastCommitId() {
            return lastCommitId;
        }
    }
}
