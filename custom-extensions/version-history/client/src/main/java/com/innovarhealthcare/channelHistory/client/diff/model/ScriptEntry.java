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

/**
 * Model representing a script entry with its left/right versions
 */
public class ScriptEntry {
    private final String name;
    private final String leftCode;
    private final String rightCode;
    private final ChangeType changeType;

    public ScriptEntry(String name, String leftCode, String rightCode, ChangeType changeType) {
        this.name = name;
        this.leftCode = leftCode;
        this.rightCode = rightCode;
        this.changeType = changeType;
    }

    public String getName() {
        return name;
    }

    public String getLeftCode() {
        return leftCode;
    }

    public String getRightCode() {
        return rightCode;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    @Override
    public String toString() {
        return name;
    }
}