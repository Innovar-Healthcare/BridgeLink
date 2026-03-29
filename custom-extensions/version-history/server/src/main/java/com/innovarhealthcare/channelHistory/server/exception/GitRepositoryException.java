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

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */

public class GitRepositoryException extends Exception {
    public GitRepositoryException(String message) {
        super(message);
    }

    public GitRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public GitRepositoryException(Throwable cause) {
        super(cause);
    }
}
