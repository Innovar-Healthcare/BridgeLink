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
import java.util.List;

import com.innovarhealthcare.channelHistory.server.exception.GitFileNotFoundException;
import com.innovarhealthcare.channelHistory.server.exception.GitOperationException;
import com.innovarhealthcare.channelHistory.server.exception.GitPushFailedException;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Base repository interface for all entity types.
 * Defines common CRUD operations and git integration.
 *
 * @param <T> The entity type this repository manages
 */
public interface Repository<T> {

    /**
     * Saves an entity to the repository (file only, no git commit)
     *
     * @param entity The entity to save
     * @return Relative file path from repository root (e.g., "Libraries/library-123.xml")
     * @throws IOException if file write fails
     */
    String save(T entity) throws IOException;

    /**
     * Saves an entity and commits/pushes to git
     *
     * @param entity    The entity to save
     * @param message   Commit message (optional, will use default if null)
     * @param committer Person making the commit
     * @param forcePush Whether to force push
     * @return Result message with commit/push status
     * @throws GitPushFailedException if push to remote fails
     * @throws GitOperationException  if save, commit, or other git operation fails
     */
    String saveAndPush(T entity, String message, PersonIdent committer, boolean forcePush) throws GitPushFailedException, GitOperationException;

    /**
     * Loads an entity by ID
     *
     * @param id The entity ID
     * @return The loaded entity
     * @throws IOException if file read or deserialization fails
     */
    T load(String id) throws IOException;

    /**
     * Checks if an entity exists
     *
     * @param id The entity ID
     * @return true if entity exists, false otherwise
     */
    boolean exists(String id);

    /**
     * Deletes an entity
     *
     * @param id The entity ID
     * @return true if entity was deleted, false if it didn't exist
     * @throws IOException if deletion fails
     */
    boolean delete(String id) throws IOException;

    String deleteAndPush(String id, String message, PersonIdent committer, boolean forcePush) throws GitPushFailedException, GitOperationException;

    /**
     * Lists all entities in this repository
     *
     * @return List of entity IDs
     * @throws IOException if directory read fails
     */
//    List<String> listAll() throws IOException;

    /**
     * Loads metadata of all items from Git repository (latest commit).
     * Returns lightweight metadata without full object content.
     *
     * @return List of item metadata from repository (empty list if no commits or error)
     */
    List<RepoItemMetadata> loadMetadata();

    List<CommitMetaData> getHistory(String id) throws GitOperationException, IllegalArgumentException;

    List<CommitMetaData> getHistory(String id, int limit) throws GitOperationException, IllegalArgumentException;

    String getContent(String fileName, String revision) throws GitFileNotFoundException, GitOperationException;

    /**
     * Gets the directory name for this repository type
     *
     * @return Directory name (e.g., "Channels", "Libraries")
     */
    String getDirectory();

    /**
     * Gets the type name for this repository
     *
     * @return Type name (e.g., "Channel", "Library")
     */
    String getTypeName();
}