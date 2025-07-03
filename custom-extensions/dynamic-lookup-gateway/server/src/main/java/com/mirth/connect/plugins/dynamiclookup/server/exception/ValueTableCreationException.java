/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.exception;

public class ValueTableCreationException extends RuntimeException {
    private final Throwable cause;

    public ValueTableCreationException(String message, Throwable cause) {
        super(message);
        this.cause = cause;
    }

    public ValueTableCreationException(String message) {
        this(message, null);
    }

    @Override
    public Throwable getCause() {
        return cause;
    }
}