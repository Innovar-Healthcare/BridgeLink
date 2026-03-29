/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.diff;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.innovarhealthcare.channelHistory.client.diff.model.ChangeType;
import com.innovarhealthcare.channelHistory.client.diff.model.DiffLine;
import com.innovarhealthcare.channelHistory.client.diff.model.DiffResult;
import com.innovarhealthcare.channelHistory.client.diff.model.ScriptDiffEngine;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;

public class DiffComparisonPanel extends JPanel {

    private static final Color HEADER_BG    = new Color(250, 251, 252);
    private static final Color BORDER_COLOR = new Color(224, 224, 224);
    private static final Font  HEADER_FONT  = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font  DATE_FONT    = new Font("SansSerif", Font.PLAIN, 11);

    private static final String CARD_SPLIT   = "SPLIT";
    private static final String CARD_UNIFIED = "UNIFIED";
    private static final String CARD_BINARY  = "BINARY";

    // ── Split mode ────────────────────────────────────────────────────────────
    private final DiffTextPane  leftPane;
    private final DiffTextPane  rightPane;
    private       JScrollPane   leftScroll;
    private       JScrollPane   rightScroll;

    // ── Unified mode ──────────────────────────────────────────────────────────
    private final DiffTextPane  unifiedPane;
    private final JScrollPane   unifiedScroll;

    // ── Layout ────────────────────────────────────────────────────────────────
    private final CardLayout    cardLayout;
    private final JPanel        contentArea;
    private final JButton       toggleButton;
    private final JButton       prevButton;
    private final JButton       nextButton;
    private final JLabel        binaryNoticeLabel;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean      isSplitMode       = true;
    private String       currentLeftText   = "";
    private String       currentRightText  = "";

    // ── Navigation state ──────────────────────────────────────────────────────
    private List<Integer> splitChangedBlocks   = Collections.emptyList();
    private List<Integer> unifiedChangedBlocks = Collections.emptyList();
    private List<DiffLine> lastLeftLines       = Collections.emptyList();
    private List<DiffLine> lastRightLines      = Collections.emptyList();
    private List<DiffLine> lastUnifiedLines    = Collections.emptyList();
    private int            navIndex            = -1;

    public DiffComparisonPanel(VersionInfo leftVersion, VersionInfo rightVersion) {
        setLayout(new BorderLayout());

        leftPane    = new DiffTextPane();
        rightPane   = new DiffTextPane();
        unifiedPane = new DiffTextPane();

        // ── Toolbar ───────────────────────────────────────────────────────────
        prevButton   = new JButton("\u25b2 Prev");
        nextButton   = new JButton("\u25bc Next");
        toggleButton = new JButton("Unified");

        prevButton.setEnabled(false);
        nextButton.setEnabled(false);

        prevButton.addActionListener(e -> navigatePrev());
        nextButton.addActionListener(e -> navigateNext());
        toggleButton.addActionListener(e -> toggleMode());

        JPanel navPanel    = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        navPanel.add(prevButton);
        navPanel.add(nextButton);

        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        togglePanel.add(toggleButton);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        toolbar.add(navPanel,    BorderLayout.WEST);
        toolbar.add(togglePanel, BorderLayout.EAST);

        // ── Split content ─────────────────────────────────────────────────────
        JPanel splitContent = new JPanel(new GridLayout(1, 2, 1, 0));
        splitContent.add(createPanelWithHeader(leftPane,  leftVersion,  true));
        splitContent.add(createPanelWithHeader(rightPane, rightVersion, false));

        // ── Unified content ───────────────────────────────────────────────────
        unifiedScroll = new JScrollPane(unifiedPane);
        JPanel unifiedContent = new JPanel(new BorderLayout());
        unifiedContent.add(unifiedScroll, BorderLayout.CENTER);

        // ── Binary notice card ────────────────────────────────────────────────
        binaryNoticeLabel = new JLabel(
                "<html><center><b>Binary file</b><br><br>" +
                "This file contains binary content and cannot be displayed as text.</center></html>",
                JLabel.CENTER);
        binaryNoticeLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        binaryNoticeLabel.setForeground(new Color(120, 120, 120));

        JPanel binaryCard = new JPanel(new BorderLayout());
        binaryCard.add(binaryNoticeLabel, BorderLayout.CENTER);

        // ── Card area ─────────────────────────────────────────────────────────
        cardLayout  = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.add(splitContent,   CARD_SPLIT);
        contentArea.add(unifiedContent, CARD_UNIFIED);
        contentArea.add(binaryCard,     CARD_BINARY);

        add(toolbar,     BorderLayout.NORTH);
        add(contentArea, BorderLayout.CENTER);

        setupSynchronizedScrolling();
    }

    // ── Toggle ────────────────────────────────────────────────────────────────

    private void toggleMode() {
        isSplitMode = !isSplitMode;
        cardLayout.show(contentArea, isSplitMode ? CARD_SPLIT : CARD_UNIFIED);
        toggleButton.setText(isSplitMode ? "Unified" : "Split");
        navIndex = -1;
        updateNavButtons();
    }

    // ── Diff rendering ────────────────────────────────────────────────────────

    public void updateDiff(String leftContent, String rightContent) {
        currentLeftText  = leftContent  != null ? leftContent  : "";
        currentRightText = rightContent != null ? rightContent : "";

        // Binary detection: check for null bytes, same heuristic used by git.
        // Show a notification card instead of garbled text.
        if (isBinary(currentLeftText) || isBinary(currentRightText)) {
            cardLayout.show(contentArea, CARD_BINARY);
            toggleButton.setEnabled(false);
            prevButton.setEnabled(false);
            nextButton.setEnabled(false);
            splitChangedBlocks   = Collections.emptyList();
            unifiedChangedBlocks = Collections.emptyList();
            navIndex = -1;
            return;
        }

        // Restore toolbar state in case a previous diff was binary
        toggleButton.setEnabled(true);

        try {
            // Split diff
            DiffResult diff = ScriptDiffEngine.computeDiff(currentLeftText, currentRightText);
            lastLeftLines  = diff.getLeftLines();
            lastRightLines = diff.getRightLines();
            leftPane.setDiffLines(lastLeftLines);
            rightPane.setDiffLines(lastRightLines);

            // Unified diff
            lastUnifiedLines = ScriptDiffEngine.computeUnifiedDiff(currentLeftText, currentRightText);
            unifiedPane.setDiffLines(lastUnifiedLines);

            // Show the correct diff card (binary guard above may have switched to CARD_BINARY)
            cardLayout.show(contentArea, isSplitMode ? CARD_SPLIT : CARD_UNIFIED);

            // Build navigation block lists (one entry per consecutive changed run)
            splitChangedBlocks   = buildSplitChangedBlocks(lastLeftLines, lastRightLines);
            unifiedChangedBlocks = buildChangedBlocks(lastUnifiedLines);

            // Auto-scroll to first change block; scroll to top when no changes exist
            List<Integer> activeBlocks = activeChangedBlocks();
            if (!activeBlocks.isEmpty()) {
                navIndex = 0;
                updateNavButtons();
                scrollToBlock(activeBlocks.get(0));
            } else {
                navIndex = -1;
                updateNavButtons();
                SwingUtilities.invokeLater(() -> {
                    leftScroll.getVerticalScrollBar().setValue(0);
                    rightScroll.getVerticalScrollBar().setValue(0);
                    unifiedScroll.getVerticalScrollBar().setValue(0);
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
            leftPane.setText("Error computing diff: " + e.getMessage());
            rightPane.setText("");
            unifiedPane.setText("Error computing diff: " + e.getMessage());
            splitChangedBlocks   = Collections.emptyList();
            unifiedChangedBlocks = Collections.emptyList();
            navIndex = -1;
            updateNavButtons();
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void navigateNext() {
        List<Integer> blocks = activeChangedBlocks();
        if (blocks.isEmpty()) return;
        navIndex = Math.min(navIndex + 1, blocks.size() - 1);
        scrollToBlock(blocks.get(navIndex));
        updateNavButtons();
    }

    private void navigatePrev() {
        List<Integer> blocks = activeChangedBlocks();
        if (blocks.isEmpty()) return;
        navIndex = Math.max(navIndex - 1, 0);
        scrollToBlock(blocks.get(navIndex));
        updateNavButtons();
    }

    private void scrollToBlock(int rowIndex) {
        if (isSplitMode) {
            // Scroll only left; the sync listener propagates to right
            scrollPaneToBlock(leftPane, leftScroll, rowIndex);
        } else {
            scrollPaneToBlock(unifiedPane, unifiedScroll, rowIndex);
        }
    }

    private void scrollPaneToBlock(DiffTextPane pane, JScrollPane scroll, int rowIndex) {
        // Double invokeLater: first pass lets any pending layout/paint events drain,
        // second pass runs after layout is complete so modelToView returns a valid rect.
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.text.Element root = pane.getDocument().getDefaultRootElement();
                if (rowIndex < 0 || rowIndex >= root.getElementCount()) return;
                int offset = root.getElement(rowIndex).getStartOffset();
                Rectangle rect = pane.modelToView(offset);
                if (rect == null) return;
                int viewH = scroll.getViewport().getHeight();
                int y = Math.max(0, rect.y - viewH / 2);
                scroll.getVerticalScrollBar().setValue(y);
            } catch (Exception ex) {
                // ignore
            }
        }));
    }

    private void updateNavButtons() {
        List<Integer> rows = activeChangedBlocks();
        prevButton.setEnabled(!rows.isEmpty() && navIndex > 0);
        nextButton.setEnabled(!rows.isEmpty() && navIndex < rows.size() - 1);
    }

    private List<Integer> activeChangedBlocks() {
        return isSplitMode ? splitChangedBlocks : unifiedChangedBlocks;
    }

    // ── Block builders ────────────────────────────────────────────────────────
    // Each entry is the start row index of a consecutive run of changed lines.

    private static List<Integer> buildSplitChangedBlocks(List<DiffLine> left, List<DiffLine> right) {
        List<Integer> result = new ArrayList<>();
        int n = Math.min(left.size(), right.size());
        boolean inBlock = false;
        for (int i = 0; i < n; i++) {
            boolean changed = left.get(i).getType() != ChangeType.UNCHANGED
                    || right.get(i).getType() != ChangeType.UNCHANGED;
            if (changed && !inBlock) {
                result.add(i);
                inBlock = true;
            } else if (!changed) {
                inBlock = false;
            }
        }
        return result;
    }

    private static List<Integer> buildChangedBlocks(List<DiffLine> lines) {
        List<Integer> result = new ArrayList<>();
        boolean inBlock = false;
        for (int i = 0; i < lines.size(); i++) {
            boolean changed = lines.get(i).getType() != ChangeType.UNCHANGED;
            if (changed && !inBlock) {
                result.add(i);
                inBlock = true;
            } else if (!changed) {
                inBlock = false;
            }
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JPanel createPanelWithHeader(DiffTextPane textPane, VersionInfo version, boolean isLeft) {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(HEADER_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JLabel line1 = new JLabel(formatVersionLine(version));
        line1.setFont(HEADER_FONT);
        line1.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel line2 = new JLabel(formatDateLine(version));
        line2.setFont(DATE_FONT);
        line2.setForeground(new Color(117, 117, 117));
        line2.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(line1);
        header.add(Box.createVerticalStrut(4));
        header.add(line2);

        JScrollPane scrollPane = new JScrollPane(textPane);
        if (isLeft) {
            leftScroll = scrollPane;
        } else {
            rightScroll = scrollPane;
        }

        panel.add(header,     BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private String formatVersionLine(VersionInfo version) {
        if (version == null) {
            return "No version info";
        }

        StringBuilder sb = new StringBuilder();

        if (version.isCurrent()) {
            sb.append("🟢 CURRENT");
        } else {
            sb.append(version.getVersion());
        }

        if (version.getAuthor() != null && !version.getAuthor().isEmpty()) {
            sb.append(" \u2022 ").append(version.getAuthor());
        }

        return sb.toString();
    }

    private String formatDateLine(VersionInfo version) {
        if (version == null || version.getTimestamp() == null) {
            return "";
        }

        return new SimpleDateFormat("MMM dd, yyyy HH:mm").format(version.getTimestamp());
    }

    /**
     * Returns true if the content appears to be a binary file.
     * Uses null-byte detection — the same heuristic git itself applies.
     */
    private static boolean isBinary(String content) {
        return content.indexOf('\0') >= 0;
    }

    private void setupSynchronizedScrolling() {
        JScrollBar leftVBar  = leftScroll.getVerticalScrollBar();
        JScrollBar rightVBar = rightScroll.getVerticalScrollBar();

        leftVBar.addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                rightVBar.setValue(e.getValue());
            }
        });

        rightVBar.addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                leftVBar.setValue(e.getValue());
            }
        });
    }
}
