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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.innovarhealthcare.channelHistory.client.diff.model.ChangeType;
import com.innovarhealthcare.channelHistory.client.diff.model.ScriptEntry;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;

/**
 * Panel for the "Code Template" tab in {@link com.innovarhealthcare.channelHistory.client.dialog.CodeTemplateDiffDialog}.
 *
 * Left side — JList of changed functions ([M] Modified, [A] Added, [D] Deleted).
 * Right side — DiffComparisonPanel showing the selected function's code diff.
 */
public class CodeTemplateFunctionDiffPanel extends JPanel {

    private final VersionInfo leftVersion;
    private final VersionInfo rightVersion;

    private final Map<String, String> leftFunctions;
    private final Map<String, String> rightFunctions;

    private JSplitPane splitPane;

    public CodeTemplateFunctionDiffPanel(String leftXml, String rightXml,
                                         VersionInfo leftVersion, VersionInfo rightVersion) {
        this.leftVersion  = leftVersion;
        this.rightVersion = rightVersion;

        this.leftFunctions  = CodeTemplateFunctionParser.parse(leftXml);
        this.rightFunctions = CodeTemplateFunctionParser.parse(rightXml);

        setLayout(new BorderLayout());

        List<ScriptEntry> changedEntries = buildChangedEntries();

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.2);
        splitPane.setBorder(null);

        splitPane.setLeftComponent(buildLeftPanel(changedEntries));
        splitPane.setRightComponent(new JPanel()); // placeholder; replaced on selection

        add(splitPane, BorderLayout.CENTER);

        // Wire selection and auto-select first item
        if (!changedEntries.isEmpty()) {
            JList<ScriptEntry> list = findList(splitPane);
            if (list != null) {
                list.addListSelectionListener(e -> {
                    if (!e.getValueIsAdjusting()) {
                        onFunctionSelected(list.getSelectedValue());
                    }
                });
                list.setSelectedIndex(0);
            }
        }
    }

    // ── Left panel ────────────────────────────────────────────────────────────

    private JPanel buildLeftPanel(List<ScriptEntry> changedEntries) {
        JPanel panel = new JPanel(new BorderLayout());

        boolean noFunctionsAtAll = leftFunctions.isEmpty() && rightFunctions.isEmpty();

        if (noFunctionsAtAll) {
            panel.add(centeredLabel("No functions found", Color.GRAY), BorderLayout.CENTER);
            return panel;
        }

        if (changedEntries.isEmpty()) {
            panel.add(centeredLabel("No changes found", Color.GRAY), BorderLayout.CENTER);
            return panel;
        }

        DefaultListModel<ScriptEntry> model = new DefaultListModel<>();
        for (ScriptEntry entry : changedEntries) {
            model.addElement(entry);
        }

        JList<ScriptEntry> list = new JList<>(model);
        list.setCellRenderer(new FunctionListCellRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Functions"));

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    // ── Selection handler ─────────────────────────────────────────────────────

    private void onFunctionSelected(ScriptEntry entry) {
        if (entry == null) return;

        String funcName   = entry.getName();
        String leftCode   = entry.getLeftCode()  != null ? entry.getLeftCode()  : "";
        String rightCode  = entry.getRightCode() != null ? entry.getRightCode() : "";

        VersionInfo lv;
        VersionInfo rv;

        switch (entry.getChangeType()) {
            case ADDED:
                lv = VersionInfo.builder().name(funcName).version("Not in previous version").isCurrent(false).build();
                rv = copyWithName(rightVersion, funcName);
                leftCode = "";
                break;

            case DELETED:
                lv = copyWithName(leftVersion, funcName);
                rv = VersionInfo.builder().name(funcName).version("Deleted").isCurrent(false).build();
                rightCode = "";
                break;

            default: // MODIFIED
                lv = copyWithName(leftVersion, funcName);
                rv = copyWithName(rightVersion, funcName);
                break;
        }

        DiffComparisonPanel diffPanel = new DiffComparisonPanel(lv, rv);
        splitPane.setRightComponent(diffPanel);
        diffPanel.updateDiff(leftCode, rightCode);
    }

    // ── Build changed-function list ───────────────────────────────────────────

    private List<ScriptEntry> buildChangedEntries() {
        Set<String> allNames = new LinkedHashSet<>();
        allNames.addAll(leftFunctions.keySet());
        allNames.addAll(rightFunctions.keySet());

        List<ScriptEntry> result = new ArrayList<>();

        for (String name : allNames) {
            String leftCode  = leftFunctions.get(name);
            String rightCode = rightFunctions.get(name);

            ChangeType type = determineChangeType(leftCode, rightCode);

            if (type != ChangeType.UNCHANGED) {
                result.add(new ScriptEntry(name, leftCode, rightCode, type));
            }
        }

        return result;
    }

    private static ChangeType determineChangeType(String left, String right) {
        if (left == null)  return ChangeType.ADDED;
        if (right == null) return ChangeType.DELETED;
        if (left.equals(right)) return ChangeType.UNCHANGED;
        return ChangeType.MODIFIED;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Copies a VersionInfo, replacing only the name field. */
    private static VersionInfo copyWithName(VersionInfo original, String name) {
        return VersionInfo.builder()
                .name(name)
                .version(original.getVersion())
                .author(original.getAuthor())
                .timestamp(original.getTimestamp())
                .isCurrent(original.isCurrent())
                .build();
    }

    /** Builds a centred, greyed-out placeholder label. */
    private static JLabel centeredLabel(String text, Color color) {
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setForeground(color);
        label.setFont(new Font("SansSerif", Font.ITALIC, 13));
        return label;
    }

    /**
     * Retrieves the JList embedded in the left component of the split pane after
     * {@link #buildLeftPanel} has been called.  Returns {@code null} if the left
     * component is a plain label (no-functions / no-changes case).
     */
    @SuppressWarnings("unchecked")
    private static JList<ScriptEntry> findList(JSplitPane pane) {
        java.awt.Component left = pane.getLeftComponent();
        if (!(left instanceof JPanel)) return null;

        for (java.awt.Component c : ((JPanel) left).getComponents()) {
            if (c instanceof JScrollPane) {
                java.awt.Component view = ((JScrollPane) c).getViewport().getView();
                if (view instanceof JList) return (JList<ScriptEntry>) view;
            }
        }
        return null;
    }
}
