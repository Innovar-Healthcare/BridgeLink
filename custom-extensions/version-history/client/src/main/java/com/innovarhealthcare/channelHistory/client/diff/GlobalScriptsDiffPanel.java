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
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.innovarhealthcare.channelHistory.client.diff.model.ChangeType;
import com.innovarhealthcare.channelHistory.client.diff.model.ScriptEntry;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;

class GlobalScriptsDiffPanel extends JPanel {

    // Fixed order for script types
    private static final List<String> SCRIPT_ORDER = Arrays.asList("Deploy", "Undeploy", "Preprocessor", "Postprocessor");

    private Map<String, String> leftScripts;
    private Map<String, String> rightScripts;
    private VersionInfo leftVersion;
    private VersionInfo rightVersion;

    private JList<ScriptEntry> scriptList;
    private DiffComparisonPanel codeDiffPanel;
    private JLabel summaryLabel;

    public GlobalScriptsDiffPanel(Map<String, String> left, Map<String, String> right, VersionInfo leftVersion, VersionInfo rightVersion) {
        this.leftScripts = left != null ? left : new HashMap<>();
        this.rightScripts = right != null ? right : new HashMap<>();
        this.leftVersion = leftVersion;
        this.rightVersion = rightVersion;

        setLayout(new BorderLayout());

        // Top: Summary
        add(createSummaryPanel(), BorderLayout.NORTH);

        // Center: Split view
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        // IMPORTANT: Create detail panel FIRST to initialize codeDiffPanel
        JPanel detailPanel = createDetailPanel();
        JScrollPane scriptListPanel = createScriptListPanel();

        splitPane.setLeftComponent(scriptListPanel);
        splitPane.setRightComponent(detailPanel);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.2);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        summaryLabel = new JLabel();
        updateSummaryLabel();

        panel.add(summaryLabel);
        return panel;
    }

    private void updateSummaryLabel() {
        List<ScriptEntry> entries = buildScriptEntries();

        int modified = 0, added = 0, deleted = 0, unchanged = 0;

        for (ScriptEntry entry : entries) {
            switch (entry.getChangeType()) {
                case MODIFIED:
                    modified++;
                    break;
                case ADDED:
                    added++;
                    break;
                case DELETED:
                    deleted++;
                    break;
                case UNCHANGED:
                    unchanged++;
                    break;
            }
        }

        StringBuilder summary = new StringBuilder("Global Scripts: ");
        List<String> parts = new ArrayList<>();

        if (modified > 0) {
            parts.add(modified + " modified");
        }

        if (added > 0) {
            parts.add(added + " added");
        }

        if (deleted > 0) {
            parts.add(deleted + " deleted");
        }

        if (unchanged > 0) {
            parts.add(unchanged + " unchanged");
        }

        summary.append(String.join(", ", parts));
        summaryLabel.setText(summary.toString());
    }

    private JScrollPane createScriptListPanel() {
        List<ScriptEntry> entries = buildScriptEntries();

        DefaultListModel<ScriptEntry> model = new DefaultListModel<>();
        for (ScriptEntry entry : entries) {
            model.addElement(entry);
        }

        scriptList = new JList<>(model);
        scriptList.setCellRenderer(new ScriptListCellRenderer());
        scriptList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        scriptList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onScriptSelected();
            }
        });

        // Default select "Deploy"
        if (!entries.isEmpty()) {
            scriptList.setSelectedIndex(0);
        }

        JScrollPane scrollPane = new JScrollPane(scriptList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Scripts"));

        return scrollPane;
    }

    private JPanel createDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        codeDiffPanel = new DiffComparisonPanel(leftVersion, rightVersion);
        panel.add(codeDiffPanel, BorderLayout.CENTER);

        return panel;
    }

    private List<ScriptEntry> buildScriptEntries() {
        Set<String> allKeys = new LinkedHashSet<>();

        // Add in fixed order first
        for (String key : SCRIPT_ORDER) {
            if (leftScripts.containsKey(key) || rightScripts.containsKey(key)) {
                allKeys.add(key);
            }
        }

        // Add any custom scripts (not in standard order)
        allKeys.addAll(leftScripts.keySet());
        allKeys.addAll(rightScripts.keySet());

        List<ScriptEntry> entries = new ArrayList<>();

        for (String key : allKeys) {
            String leftCode = leftScripts.get(key);
            String rightCode = rightScripts.get(key);

            ChangeType changeType = determineChangeType(leftCode, rightCode);

            entries.add(new ScriptEntry(key, leftCode, rightCode, changeType));
        }

        return entries;
    }

    private ChangeType determineChangeType(String left, String right) {
        if (left == null && right == null) {
            return ChangeType.UNCHANGED;
        }

        if (left == null) {
            return ChangeType.ADDED;
        }

        if (right == null) {
            return ChangeType.DELETED;
        }

        if (left.equals(right)) {
            return ChangeType.UNCHANGED;
        }

        return ChangeType.MODIFIED;
    }

    private void onScriptSelected() {
        if (codeDiffPanel == null) {
            return;
        }

        ScriptEntry selected = scriptList.getSelectedValue();
        if (selected == null) {
            return;
        }

        String leftCode = selected.getLeftCode() != null ? selected.getLeftCode() : "";
        String rightCode = selected.getRightCode() != null ? selected.getRightCode() : "";

        codeDiffPanel.updateDiff(leftCode, rightCode);
    }
}
