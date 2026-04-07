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
import java.util.Set;

import com.innovarhealthcare.channelHistory.client.diff.model.ChangeType;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;

/**
 * Panel for the "Channel" tab in
 * {@link com.innovarhealthcare.channelHistory.client.dialog.ChannelDiffDialog}.
 *
 * Left side  — JList of channel components (Channel Info, source, destinations)
 *              with [M]/[A]/[D] change indicators.
 * Right side — DiffComparisonPanel showing the selected component's XML diff.
 *
 * All items (changed and unchanged) are shown so the user can browse any component.
 * Auto-selects the first [M]/[A]/[D] item on open; falls back to first item if
 * everything is unchanged.
 */
public class ChannelDiffPanel extends JPanel {

    private final VersionInfo leftVersion;
    private final VersionInfo rightVersion;

    private final ChannelComponents leftComponents;
    private final ChannelComponents rightComponents;

    private JSplitPane splitPane;

    public ChannelDiffPanel(String leftXml, String rightXml,
                            VersionInfo leftVersion, VersionInfo rightVersion) {
        this.leftVersion  = leftVersion;
        this.rightVersion = rightVersion;

        this.leftComponents  = ChannelComponentParser.parse(leftXml);
        this.rightComponents = ChannelComponentParser.parse(rightXml);

        setLayout(new BorderLayout());

        List<ChannelEntry> entries = buildEntries();

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.2);
        splitPane.setBorder(null);

        splitPane.setLeftComponent(buildLeftPanel(entries));
        splitPane.setRightComponent(new JPanel()); // placeholder; replaced on selection

        add(splitPane, BorderLayout.CENTER);

        // Wire selection and auto-select
        if (!entries.isEmpty()) {
            JList<ChannelEntry> list = findList(splitPane);
            if (list != null) {
                list.addListSelectionListener(e -> {
                    if (!e.getValueIsAdjusting()) {
                        onEntrySelected(list.getSelectedValue());
                    }
                });

                // Prefer first changed item; fall back to index 0
                int selectIndex = 0;
                for (int i = 0; i < entries.size(); i++) {
                    if (entries.get(i).getChangeType() != ChangeType.UNCHANGED) {
                        selectIndex = i;
                        break;
                    }
                }
                list.setSelectedIndex(selectIndex);
            }
        }
    }

    // ── Entry building ────────────────────────────────────────────────────────

    private List<ChannelEntry> buildEntries() {
        List<ChannelEntry> entries = new ArrayList<>();

        // 1. Channel Info — always present
        String leftInfo  = leftComponents.channelInfo;
        String rightInfo = rightComponents.channelInfo;
        ChangeType infoType = leftInfo.equals(rightInfo) ? ChangeType.UNCHANGED : ChangeType.MODIFIED;
        entries.add(new ChannelEntry("Channel Info", infoType, leftInfo, rightInfo, null, null));

        // 2. Source Connector
        ChannelConnector leftSrc  = leftComponents.sourceConnector;
        ChannelConnector rightSrc = rightComponents.sourceConnector;

        if (leftSrc != null && rightSrc != null) {
            if (leftSrc.transportName.equals(rightSrc.transportName)) {
                ChangeType type = leftSrc.xmlContent.equals(rightSrc.xmlContent)
                        ? ChangeType.UNCHANGED : ChangeType.MODIFIED;
                entries.add(new ChannelEntry(
                        "Source: " + leftSrc.transportName, type,
                        leftSrc.xmlContent, rightSrc.xmlContent, null, null));
            } else {
                // Transport type changed: show as deleted old + added new
                entries.add(new ChannelEntry(
                        "Source: " + leftSrc.transportName, ChangeType.DELETED,
                        leftSrc.xmlContent, "", "Deleted", null));
                entries.add(new ChannelEntry(
                        "Source: " + rightSrc.transportName, ChangeType.ADDED,
                        "", rightSrc.xmlContent, null, "Added"));
            }
        } else if (leftSrc == null && rightSrc != null) {
            entries.add(new ChannelEntry(
                    "Source: " + rightSrc.transportName, ChangeType.ADDED,
                    "", rightSrc.xmlContent, null, "Added"));
        } else if (leftSrc != null) {
            entries.add(new ChannelEntry(
                    "Source: " + leftSrc.transportName, ChangeType.DELETED,
                    leftSrc.xmlContent, "", "Deleted", null));
        }

        // 3. Destination Connectors — union of both sides, keyed by name
        Set<String> allNames = new LinkedHashSet<>();
        allNames.addAll(leftComponents.destinations.keySet());
        allNames.addAll(rightComponents.destinations.keySet());

        for (String name : allNames) {
            ChannelConnector leftDest  = leftComponents.destinations.get(name);
            ChannelConnector rightDest = rightComponents.destinations.get(name);

            if (leftDest != null && rightDest != null) {
                ChangeType type = leftDest.xmlContent.equals(rightDest.xmlContent)
                        ? ChangeType.UNCHANGED : ChangeType.MODIFIED;
                entries.add(new ChannelEntry(
                        "Dest: " + name, type,
                        leftDest.xmlContent, rightDest.xmlContent, null, null));
            } else if (leftDest == null) {
                entries.add(new ChannelEntry(
                        "Dest: " + name, ChangeType.ADDED,
                        "", rightDest.xmlContent, null, "Added"));
            } else {
                entries.add(new ChannelEntry(
                        "Dest: " + name, ChangeType.DELETED,
                        leftDest.xmlContent, "", "Deleted", null));
            }
        }

        return entries;
    }

    // ── Selection handler ─────────────────────────────────────────────────────

    private void onEntrySelected(ChannelEntry entry) {
        if (entry == null) return;

        String componentName = entry.getLabel();

        VersionInfo lv = entry.getLeftVersionOverride() != null
                ? VersionInfo.builder()
                        .name(componentName)
                        .version(entry.getLeftVersionOverride())
                        .isCurrent(false)
                        .build()
                : copyWithName(leftVersion, componentName);

        VersionInfo rv = entry.getRightVersionOverride() != null
                ? VersionInfo.builder()
                        .name(componentName)
                        .version(entry.getRightVersionOverride())
                        .isCurrent(false)
                        .build()
                : copyWithName(rightVersion, componentName);

        DiffComparisonPanel diffPanel = new DiffComparisonPanel(lv, rv);
        splitPane.setRightComponent(diffPanel);
        diffPanel.updateDiff(entry.getLeftContent(), entry.getRightContent());
    }

    // ── Left panel ────────────────────────────────────────────────────────────

    private JPanel buildLeftPanel(List<ChannelEntry> entries) {
        JPanel panel = new JPanel(new BorderLayout());

        if (entries.isEmpty()) {
            JLabel label = new JLabel("No components found", JLabel.CENTER);
            label.setForeground(Color.GRAY);
            label.setFont(new Font("SansSerif", Font.ITALIC, 13));
            panel.add(label, BorderLayout.CENTER);
            return panel;
        }

        DefaultListModel<ChannelEntry> model = new DefaultListModel<>();
        for (ChannelEntry entry : entries) {
            model.addElement(entry);
        }

        JList<ChannelEntry> list = new JList<>(model);
        list.setCellRenderer(new ChannelEntryListCellRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Components"));

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static VersionInfo copyWithName(VersionInfo original, String name) {
        return VersionInfo.builder()
                .name(name)
                .version(original.getVersion())
                .author(original.getAuthor())
                .timestamp(original.getTimestamp())
                .isCurrent(original.isCurrent())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static JList<ChannelEntry> findList(JSplitPane pane) {
        java.awt.Component left = pane.getLeftComponent();
        if (!(left instanceof JPanel)) return null;
        for (java.awt.Component c : ((JPanel) left).getComponents()) {
            if (c instanceof JScrollPane) {
                java.awt.Component view = ((JScrollPane) c).getViewport().getView();
                if (view instanceof JList) return (JList<ChannelEntry>) view;
            }
        }
        return null;
    }
}
