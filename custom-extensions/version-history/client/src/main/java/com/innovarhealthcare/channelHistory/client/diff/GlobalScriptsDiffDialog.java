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

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Map;

import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;

public class GlobalScriptsDiffDialog extends JDialog {

    private GlobalScriptsDiffPanel diffPanel;

    public GlobalScriptsDiffDialog(Window parent, String title, Map<String, String> scripts1, Map<String, String> scripts2, VersionInfo version1, VersionInfo version2) {
        super(parent, title, ModalityType.APPLICATION_MODAL);

        // Auto-sort: ensure LEFT=older, RIGHT=newer
        DiffPair sorted = sortVersions(scripts1, scripts2, version1, version2);

        initComponents(sorted.leftScripts, sorted.rightScripts, sorted.leftVersion, sorted.rightVersion);
        setupLayout();
        setupDialog();
    }

    private DiffPair sortVersions(Map<String, String> scripts1, Map<String, String> scripts2, VersionInfo version1, VersionInfo version2) {

        // Current version always goes RIGHT
        if (version1.isCurrent() && !version2.isCurrent()) {
            return new DiffPair(scripts2, scripts1, version2, version1);
        }
        if (version2.isCurrent() && !version1.isCurrent()) {
            return new DiffPair(scripts1, scripts2, version1, version2);
        }

        // Compare timestamps if both present
        if (version1.getTimestamp() != null && version2.getTimestamp() != null) {
            if (version1.getTimestamp().after(version2.getTimestamp())) {
                // version1 is newer, swap to put on right
                return new DiffPair(scripts2, scripts1, version2, version1);
            }
        }

        // Default: keep original order
        return new DiffPair(scripts1, scripts2, version1, version2);
    }

    private void initComponents(Map<String, String> leftScripts, Map<String, String> rightScripts, VersionInfo leftVersion, VersionInfo rightVersion) {
        diffPanel = new GlobalScriptsDiffPanel(leftScripts, rightScripts, leftVersion, rightVersion);
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        add(diffPanel, BorderLayout.CENTER);

        // Bottom button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setupDialog() {
        setSize(1200, 800);
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // ESC to close
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private static class DiffPair {
        final Map<String, String> leftScripts;
        final Map<String, String> rightScripts;
        final VersionInfo leftVersion;
        final VersionInfo rightVersion;

        DiffPair(Map<String, String> left, Map<String, String> right, VersionInfo leftVer, VersionInfo rightVer) {
            this.leftScripts = left;
            this.rightScripts = right;
            this.leftVersion = leftVer;
            this.rightVersion = rightVer;
        }
    }
}
