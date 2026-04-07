/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.exception;

import com.innovarhealthcare.channelHistory.shared.dto.response.ErrorResponse;

import java.util.Collections;
import java.util.Map;

/**
 * Thrown by {@link com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient}
 * when the server responds with a GIT_CONFLICT error code.  Carries the pre-reset content of
 * the files that were being committed, so the caller can offer the user a restore option.
 */
public class GitConflictClientException extends VersionHistoryClientException {

    public GitConflictClientException(ErrorResponse error, Throwable cause) {
        super(error, cause);
    }

    /**
     * Returns the map of relative file paths to their working-tree content
     * at the time the conflict was detected (before the server reset to remote HEAD).
     * Never null; may be empty if the server did not capture any content.
     */
    public Map<String, String> getBackedUpContent() {
        Map<String, String> content = getError().getBackedUpContent();
        return content != null ? content : Collections.emptyMap();
    }
}
