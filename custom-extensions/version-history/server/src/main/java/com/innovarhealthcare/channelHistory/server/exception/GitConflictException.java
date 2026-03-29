/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.server.exception;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Thrown when a rebase-based push fails due to conflicts with remote changes.
 * Carries the list of conflicting file paths and, when available, the backed-up
 * content of those files from before the remote was applied.
 */
public class GitConflictException extends GitOperationException {

    private final List<String> conflictingFiles;
    private final Map<String, String> backedUpContent;

    public GitConflictException(String message, List<String> conflictingFiles) {
        super(message);
        this.conflictingFiles = conflictingFiles != null ? conflictingFiles : Collections.emptyList();
        this.backedUpContent = Collections.emptyMap();
    }

    public GitConflictException(String message, List<String> conflictingFiles, Map<String, String> backedUpContent) {
        super(message);
        this.conflictingFiles = conflictingFiles != null ? conflictingFiles : Collections.emptyList();
        this.backedUpContent = backedUpContent != null ? backedUpContent : Collections.emptyMap();
    }

    public List<String> getConflictingFiles() {
        return conflictingFiles;
    }

    public Map<String, String> getBackedUpContent() {
        return backedUpContent;
    }
}
