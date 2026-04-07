/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.dialog;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.KeyEvent;

import com.innovarhealthcare.channelHistory.client.diff.CodeTemplateFunctionDiffPanel;
import com.innovarhealthcare.channelHistory.client.diff.DiffComparisonPanel;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class CodeTemplateDiffDialog extends JDialog {

    private DiffComparisonPanel diffPanel;

    public CodeTemplateDiffDialog(Frame parent, VersionInfo leftVersion, VersionInfo rightVersion, String leftXml, String rightXml) {
        super(parent, "Code Template Diff", true);

        // Auto-sort: current version always on right, newer timestamp on right
        DiffPair sorted = sortVersions(leftVersion, rightVersion, leftXml, rightXml);

        initComponents(sorted.leftVersion, sorted.rightVersion);
        setupLayout(sorted);
        setupDialog(parent);

        diffPanel.updateDiff(sorted.leftXml, sorted.rightXml);
    }

    private DiffPair sortVersions(VersionInfo version1, VersionInfo version2, String xml1, String xml2) {
        // Current version always goes RIGHT
        if (version1.isCurrent() && !version2.isCurrent()) {
            return new DiffPair(xml2, xml1, version2, version1);
        }
        if (version2.isCurrent() && !version1.isCurrent()) {
            return new DiffPair(xml1, xml2, version1, version2);
        }

        // Compare timestamps — newer goes RIGHT
        if (version1.getTimestamp() != null && version2.getTimestamp() != null) {
            if (version1.getTimestamp().after(version2.getTimestamp())) {
                return new DiffPair(xml2, xml1, version2, version1);
            }
        }

        // Default: keep original order
        return new DiffPair(xml1, xml2, version1, version2);
    }

    private void initComponents(VersionInfo leftVersion, VersionInfo rightVersion) {
        diffPanel = new DiffComparisonPanel(leftVersion, rightVersion);
    }

    private void setupLayout(DiffPair sorted) {
        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab("XML Diff", diffPanel);

        CodeTemplateFunctionDiffPanel functionPanel = new CodeTemplateFunctionDiffPanel(
                sorted.leftXml, sorted.rightXml, sorted.leftVersion, sorted.rightVersion);
        tabbedPane.addTab("Code Template", functionPanel);

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setupDialog(Frame parent) {
        setSize(1200, 800);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private static class DiffPair {
        final String leftXml;
        final String rightXml;
        final VersionInfo leftVersion;
        final VersionInfo rightVersion;

        DiffPair(String leftXml, String rightXml, VersionInfo leftVersion, VersionInfo rightVersion) {
            this.leftXml      = leftXml;
            this.rightXml     = rightXml;
            this.leftVersion  = leftVersion;
            this.rightVersion = rightVersion;
        }
    }
}
