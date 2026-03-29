/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.diff.model;

public class DiffLine {
    private final int lineNumber;
    private final String content;
    private final ChangeType type;

    public DiffLine(int lineNumber, String content, ChangeType type) {
        this.lineNumber = lineNumber;
        this.content = content;
        this.type = type;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getContent() {
        return content;
    }

    public ChangeType getType() {
        return type;
    }
}