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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

public class ScriptDiffEngine {

    /**
     * Computes a unified diff — a single interleaved list of DiffLines where
     * DELETED lines (from left, prefix "-") appear before ADDED lines (from right,
     * prefix "+") at each change site, and UNCHANGED lines (prefix " ") fill the
     * context between changes.
     */
    public static List<DiffLine> computeUnifiedDiff(String leftText, String rightText) {
        List<String> leftLines  = splitIntoLines(leftText);
        List<String> rightLines = splitIntoLines(rightText);

        Patch<String> patch = DiffUtils.diff(leftLines, rightLines);

        List<DiffLine> result = new ArrayList<>();
        int leftPos = 0; // cursor into leftLines

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            // Emit unchanged context lines before this delta
            int deltaSourceStart = delta.getSource().getPosition();
            while (leftPos < deltaSourceStart) {
                result.add(new DiffLine(leftPos + 1, leftLines.get(leftPos), ChangeType.UNCHANGED));
                leftPos++;
            }

            // Emit deleted lines from left
            List<String> sourceLines = delta.getSource().getLines();
            for (int i = 0; i < sourceLines.size(); i++) {
                result.add(new DiffLine(delta.getSource().getPosition() + i + 1, sourceLines.get(i), ChangeType.DELETED));
            }
            leftPos += delta.getSource().size();

            // Emit added lines from right
            List<String> targetLines = delta.getTarget().getLines();
            for (int i = 0; i < targetLines.size(); i++) {
                result.add(new DiffLine(delta.getTarget().getPosition() + i + 1, targetLines.get(i), ChangeType.ADDED));
            }
        }

        // Emit any remaining unchanged lines
        while (leftPos < leftLines.size()) {
            result.add(new DiffLine(leftPos + 1, leftLines.get(leftPos), ChangeType.UNCHANGED));
            leftPos++;
        }

        return result;
    }

    public static DiffResult computeDiff(String leftText, String rightText) {
        List<String> leftLines = splitIntoLines(leftText);
        List<String> rightLines = splitIntoLines(rightText);

        Patch<String> patch = DiffUtils.diff(leftLines, rightLines);

        return buildDiffResult(leftLines, rightLines, patch);
    }

    private static List<String> splitIntoLines(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(text.split("\n", -1));
    }

    private static DiffResult buildDiffResult(List<String> leftLines, List<String> rightLines, Patch<String> patch) {

        Set<Integer> leftChangedLines = new HashSet<>();
        Set<Integer> rightChangedLines = new HashSet<>();

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            switch (delta.getType()) {
                case DELETE:
                    for (int i = delta.getSource().getPosition(); i < delta.getSource().getPosition() + delta.getSource().size(); i++) {
                        leftChangedLines.add(i);
                    }
                    break;
                case INSERT:
                    for (int i = delta.getTarget().getPosition(); i < delta.getTarget().getPosition() + delta.getTarget().size(); i++) {
                        rightChangedLines.add(i);
                    }
                    break;
                case CHANGE:
                    for (int i = delta.getSource().getPosition(); i < delta.getSource().getPosition() + delta.getSource().size(); i++) {
                        leftChangedLines.add(i);
                    }
                    for (int i = delta.getTarget().getPosition(); i < delta.getTarget().getPosition() + delta.getTarget().size(); i++) {
                        rightChangedLines.add(i);
                    }
                    break;
            }
        }

        List<DiffLine> left = new ArrayList<>();
        for (int i = 0; i < leftLines.size(); i++) {
            ChangeType type = leftChangedLines.contains(i) ? ChangeType.DELETED : ChangeType.UNCHANGED;
            left.add(new DiffLine(i + 1, leftLines.get(i), type));
        }

        List<DiffLine> right = new ArrayList<>();
        for (int i = 0; i < rightLines.size(); i++) {
            ChangeType type = rightChangedLines.contains(i) ? ChangeType.ADDED : ChangeType.UNCHANGED;
            right.add(new DiffLine(i + 1, rightLines.get(i), type));
        }

        return new DiffResult(left, right);
    }
}
