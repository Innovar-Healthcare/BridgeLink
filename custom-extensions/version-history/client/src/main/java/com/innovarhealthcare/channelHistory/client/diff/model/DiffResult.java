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

import java.util.List;

public class DiffResult {
    private final List<DiffLine> leftLines;
    private final List<DiffLine> rightLines;

    public DiffResult(List<DiffLine> leftLines, List<DiffLine> rightLines) {
        this.leftLines = leftLines;
        this.rightLines = rightLines;
    }

    public List<DiffLine> getLeftLines() {
        return leftLines;
    }

    public List<DiffLine> getRightLines() {
        return rightLines;
    }
}
