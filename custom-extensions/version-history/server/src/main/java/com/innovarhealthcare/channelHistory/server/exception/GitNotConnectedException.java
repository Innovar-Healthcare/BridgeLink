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
 * Thrown when Git service is not initialized/connected yet
 */
public class GitNotConnectedException extends RuntimeException {
    public GitNotConnectedException(String message) {
        super(message);
    }
    
    public GitNotConnectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
