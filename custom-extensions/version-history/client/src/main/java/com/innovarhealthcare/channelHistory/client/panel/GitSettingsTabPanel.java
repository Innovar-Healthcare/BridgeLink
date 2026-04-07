/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.panel;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Properties;

import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthRadioButton;
import com.mirth.connect.client.ui.components.MirthTextField;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class GitSettingsTabPanel extends JPanel {

    private JLabel remoteRepositoryUrlLabel;
    private JTextField remoteRepositoryUrlField;

    private JLabel branchNameLabel;
    private JTextField branchNameField;

    private JLabel sshPrivateKeyLabel;

    private JRadioButton sshPasteKeyRadio;
    private JRadioButton sshFilePathRadio;
    private ButtonGroup sshKeyModeButtonGroup;

    private JTextArea sshPrivateKeyField;
    private JScrollPane sshKeyScrollPane;
    private JButton sshLoadButton;

    private JTextField sshPrivateKeyPathField;
    private JLabel sshPrivateKeyPathHintLabel;

    private JPanel sshPastePanel;

    private JButton validateGitButton;

    private final VersionHistoryProperties versionHistoryProperties;

    public GitSettingsTabPanel(VersionHistoryProperties versionHistoryProperties) {
        this.versionHistoryProperties = versionHistoryProperties;
        initComponents();
        initLayout();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        remoteRepositoryUrlLabel = new JLabel("Repository URL:");
        remoteRepositoryUrlField = new MirthTextField();
        remoteRepositoryUrlField.setToolTipText("Enter an SSH URL for the remote Git repository (e.g., git@github.com:user/repo.git).");

        branchNameLabel = new JLabel("Branch Name:");
        branchNameField = new MirthTextField();
        branchNameField.setToolTipText("Enter the branch name to use (e.g., main, develop, or feature/xyz).");

        sshPrivateKeyLabel = new JLabel("SSH Private Key:");

        sshPasteKeyRadio = new MirthRadioButton("Paste key");
        sshPasteKeyRadio.setFocusable(false);
        sshPasteKeyRadio.setBackground(UIConstants.BACKGROUND_COLOR);
        sshPasteKeyRadio.setSelected(true);
        sshPasteKeyRadio.addActionListener(e -> sshKeyModeActionPerformed());

        sshFilePathRadio = new MirthRadioButton("File path");
        sshFilePathRadio.setFocusable(false);
        sshFilePathRadio.setBackground(UIConstants.BACKGROUND_COLOR);
        sshFilePathRadio.addActionListener(e -> sshKeyModeActionPerformed());

        sshKeyModeButtonGroup = new ButtonGroup();
        sshKeyModeButtonGroup.add(sshPasteKeyRadio);
        sshKeyModeButtonGroup.add(sshFilePathRadio);

        sshPrivateKeyField = new JTextArea();
        sshPrivateKeyField.setWrapStyleWord(true);
        sshPrivateKeyField.setLineWrap(true);
        sshPrivateKeyField.setToolTipText("Paste your SSH private key, starting with '-----BEGIN OPENSSH PRIVATE KEY-----'.");
        sshKeyScrollPane = new JScrollPane(sshPrivateKeyField, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sshKeyScrollPane.setPreferredSize(new Dimension(300, 100));

        sshLoadButton = new JButton("Load");
        sshLoadButton.addActionListener(e -> loadPrivateKey());

        sshPrivateKeyPathField = new MirthTextField();
        sshPrivateKeyPathField.setToolTipText("<html>Specify the relative or absolute path to the SSH private key file.<br>" + "The key file can be read from the Mirth server file system.<br>" + "Examples:<br>" + "appdata/id_rsa<br>" + "appdata/mykey.pem<br>" + "c:/mycerts/id_rsa</html>");

        sshPrivateKeyPathHintLabel = new JLabel("The private key remains on the server — only the file path is stored.");
        sshPrivateKeyPathHintLabel.setFont(new Font("Tahoma", Font.PLAIN, 10));
        sshPrivateKeyPathHintLabel.setForeground(Color.GRAY);

        sshPastePanel = new JPanel(new MigLayout("hidemode 3, insets 0, novisualpadding", "[grow][]"));
        sshPastePanel.setBackground(UIConstants.BACKGROUND_COLOR);

        // Paste mode components
        sshPastePanel.add(sshKeyScrollPane, "growx");
        sshPastePanel.add(sshLoadButton, "aligny top, wrap");

        // File path mode components (hidden by default)
        sshPrivateKeyPathField.setVisible(false);
        sshPrivateKeyPathHintLabel.setVisible(false);
        sshPastePanel.add(sshPrivateKeyPathField, "w 450!, span 2, wrap");
        sshPastePanel.add(sshPrivateKeyPathHintLabel, "growx, span 2, wrap");

        validateGitButton = new JButton("Validate Connection");
        validateGitButton.addActionListener(e -> validateGitRemoteRepository());
    }

    private void initLayout() {
        setLayout(new MigLayout("hidemode 3, novisualpadding, insets 8", "[120,right][grow]"));

        add(remoteRepositoryUrlLabel, "right");
        add(remoteRepositoryUrlField, "w 450!, wrap");

        add(branchNameLabel, "right");
        add(branchNameField, "w 160!, wrap");

        add(sshPrivateKeyLabel, "right");
        add(sshPasteKeyRadio, "split 2");
        add(sshFilePathRadio, "wrap");

        add(new JLabel(), "right");
        add(sshPastePanel, "w 450!, wrap");

        // Validate button
        add(new JLabel(), "right");
        add(validateGitButton, "wrap");
    }

    private void sshKeyModeActionPerformed() {
        boolean isPaste = sshPasteKeyRadio.isSelected();

        // Paste mode components
        sshKeyScrollPane.setVisible(isPaste);
        sshLoadButton.setVisible(isPaste);

        // File path mode components
        sshPrivateKeyPathField.setVisible(!isPaste);
        sshPrivateKeyPathHintLabel.setVisible(!isPaste);
    }

    private void loadPrivateKey() {
        String content = PlatformUI.MIRTH_FRAME.browseForFileString(null);
        if (content != null) {
            sshPrivateKeyField.setText(content.trim());
        }
    }

    private void validateGitRemoteRepository() {
        if (!validateFields()) {
            return;
        }
        new GitValidationDialog();
    }

    // ========== Git Validation Dialog ==========

    private class GitValidationDialog extends JDialog {

        private JLabel statusLabel;
        private JProgressBar progressBar;
        private JButton closeButton;

        GitValidationDialog() {
            super(PlatformUI.MIRTH_FRAME, "Validate Git Connection", true);

            initComponents();
            initLayout();

            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            pack();
            setLocationRelativeTo(PlatformUI.MIRTH_FRAME);

            new ValidateWorker().execute();

            setVisible(true);
        }

        private void initComponents() {
            getContentPane().setBackground(UIConstants.BACKGROUND_COLOR);

            statusLabel = new JLabel("Validating Git connection...");

            progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);

            closeButton = new JButton("Close");
            closeButton.setEnabled(false);
            closeButton.addActionListener(e -> dispose());
        }

        private void initLayout() {
            setLayout(new MigLayout("insets 16, fill", "[grow, fill]", "[]8[]16[]"));

            add(statusLabel, "wmin 380, wrap");
            add(progressBar, "growx, pushx, wrap");
            add(closeButton, "right, w 100!");
        }

        private void onSuccess() {
            progressBar.setVisible(false);
            statusLabel.setText("<html><b style='color: #008000;'>&#10003; Connection successful</b></html>");
            closeButton.setEnabled(true);
            closeButton.requestFocusInWindow();
            pack();
        }

        private void onError(String message) {
            progressBar.setVisible(false);
            String safe = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            statusLabel.setText("<html><b style='color: #CC0000;'>&#10007; " + safe + "</b></html>");
            closeButton.setEnabled(true);
            closeButton.requestFocusInWindow();
            pack();
        }

        private final class ValidateWorker extends SwingWorker<String, Void> {
            @Override
            protected String doInBackground() throws Exception {
                return VersionHistoryServiceClient.getInstance().validateSetting(toGitSettingsProperties());
            }

            @Override
            protected void done() {
                try {
                    get();
                    onSuccess();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    onError(cause.getMessage() != null ? cause.getMessage() : "Unknown error");
                }
            }
        }
    }

    private Properties toGitSettingsProperties() {
        Properties properties = new Properties();
        String url = remoteRepositoryUrlField.getText().trim();
        String branch = branchNameField.getText().trim();

        if (!StringUtils.isEmpty(url)) {
            properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_REPO_URL, url);
        }
        if (!StringUtils.isEmpty(branch)) {
            properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_BRANCH, branch);
        }
        if (sshPasteKeyRadio.isSelected()) {
            String sshKey = sshPrivateKeyField.getText().trim();
            if (!StringUtils.isEmpty(sshKey)) {
                properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_SSH_KEY, sshKey);
            }
        } else {
            String sshPath = sshPrivateKeyPathField.getText().trim();
            if (!StringUtils.isEmpty(sshPath)) {
                properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_SSH_KEY_PATH, sshPath);
            }
        }
        return properties;
    }

    public void setProperties() {
        remoteRepositoryUrlField.setText(versionHistoryProperties.getGitSettings().getRemoteRepositoryUrl());
        branchNameField.setText(versionHistoryProperties.getGitSettings().getBranchName());

        String sshKey = versionHistoryProperties.getGitSettings().getSshPrivateKey();
        String sshPath = versionHistoryProperties.getGitSettings().getSshPrivateKeyPath();

        if (!StringUtils.isEmpty(sshPath)) {
            sshFilePathRadio.setSelected(true);
            sshPrivateKeyPathField.setText(sshPath);
        } else {
            sshPasteKeyRadio.setSelected(true);
            sshPrivateKeyField.setText(sshKey);
        }
        sshKeyModeActionPerformed();
    }

    public void getProperties() {
        versionHistoryProperties.getGitSettings().setRemoteRepositoryUrl(remoteRepositoryUrlField.getText().trim());
        versionHistoryProperties.getGitSettings().setBranchName(branchNameField.getText().trim());

        if (sshPasteKeyRadio.isSelected()) {
            versionHistoryProperties.getGitSettings().setSshPrivateKey(sshPrivateKeyField.getText().trim());
            versionHistoryProperties.getGitSettings().setSshPrivateKeyPath("");
        } else {
            versionHistoryProperties.getGitSettings().setSshPrivateKey("");
            versionHistoryProperties.getGitSettings().setSshPrivateKeyPath(sshPrivateKeyPathField.getText().trim());
        }
    }

    public boolean validateFields() {
        boolean valid = true;

        if (StringUtils.isEmpty(remoteRepositoryUrlField.getText().trim())) {
            valid = false;
            remoteRepositoryUrlField.setBackground(UIConstants.INVALID_COLOR);
        }

        if (StringUtils.isEmpty(branchNameField.getText().trim())) {
            valid = false;
            branchNameField.setBackground(UIConstants.INVALID_COLOR);
        }

        if (sshPasteKeyRadio.isSelected() && StringUtils.isEmpty(sshPrivateKeyField.getText().trim())) {
            valid = false;
            sshPrivateKeyField.setBackground(UIConstants.INVALID_COLOR);
        }

        if (sshFilePathRadio.isSelected() && StringUtils.isEmpty(sshPrivateKeyPathField.getText().trim())) {
            valid = false;
            sshPrivateKeyPathField.setBackground(UIConstants.INVALID_COLOR);
        }

        return valid;
    }

    public void resetInvalidState() {
        remoteRepositoryUrlField.setBackground(null);
        branchNameField.setBackground(null);
        sshPrivateKeyField.setBackground(null);
        sshPrivateKeyPathField.setBackground(null);
    }
}
