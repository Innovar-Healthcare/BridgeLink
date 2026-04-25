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
import javax.swing.JPasswordField;
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
import com.innovarhealthcare.channelHistory.shared.model.GitSettings;
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

    // Auth type switcher
    private JRadioButton sshAuthRadio;
    private JRadioButton httpsAuthRadio;
    private ButtonGroup authTypeButtonGroup;

    // SSH section
    private JPanel sshPanel;
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

    // HTTPS section
    private JPanel httpsPanel;
    private JRadioButton httpsInlineRadio;
    private JRadioButton httpsFilePathRadio;
    private ButtonGroup httpsCredsModeButtonGroup;
    private JLabel httpsUsernameLabel;
    private JTextField httpsUsernameField;
    private JLabel httpsPasswordLabel;
    private JPasswordField httpsPasswordField;
    private JTextField httpsCredentialsPathField;
    private JLabel httpsCredentialsPathHintLabel;

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
        remoteRepositoryUrlField.setToolTipText("Enter the remote Git repository URL (SSH or HTTPS).");

        branchNameLabel = new JLabel("Branch Name:");
        branchNameField = new MirthTextField();
        branchNameField.setToolTipText("Enter the branch name to use (e.g., main, develop, or feature/xyz).");

        // ── Auth type switcher ──────────────────────────────────────────
        sshAuthRadio = new MirthRadioButton("SSH");
        sshAuthRadio.setFocusable(false);
        sshAuthRadio.setBackground(UIConstants.BACKGROUND_COLOR);
        sshAuthRadio.setSelected(true);
        sshAuthRadio.addActionListener(e -> authTypeActionPerformed());

        httpsAuthRadio = new MirthRadioButton("HTTPS");
        httpsAuthRadio.setFocusable(false);
        httpsAuthRadio.setBackground(UIConstants.BACKGROUND_COLOR);
        httpsAuthRadio.addActionListener(e -> authTypeActionPerformed());

        authTypeButtonGroup = new ButtonGroup();
        authTypeButtonGroup.add(sshAuthRadio);
        authTypeButtonGroup.add(httpsAuthRadio);

        // ── SSH section ─────────────────────────────────────────────────
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
        sshPrivateKeyPathField.setToolTipText("<html>Specify the relative or absolute path to the SSH private key file.<br>" +
                "The key file can be read from the Mirth server file system.<br>" +
                "Examples:<br>appdata/id_rsa<br>appdata/mykey.pem<br>c:/mycerts/id_rsa</html>");

        sshPrivateKeyPathHintLabel = new JLabel("The private key remains on the server — only the file path is stored.");
        sshPrivateKeyPathHintLabel.setFont(new Font("Tahoma", Font.PLAIN, 10));
        sshPrivateKeyPathHintLabel.setForeground(Color.GRAY);

        sshPastePanel = new JPanel(new MigLayout("hidemode 3, insets 0, novisualpadding", "[grow][]"));
        sshPastePanel.setBackground(UIConstants.BACKGROUND_COLOR);
        sshPastePanel.add(sshKeyScrollPane, "growx");
        sshPastePanel.add(sshLoadButton, "aligny top, wrap");
        sshPrivateKeyPathField.setVisible(false);
        sshPrivateKeyPathHintLabel.setVisible(false);
        sshPastePanel.add(sshPrivateKeyPathField, "w 450!, span 2, wrap");
        sshPastePanel.add(sshPrivateKeyPathHintLabel, "growx, span 2, wrap");

        sshPanel = new JPanel(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
        sshPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        sshPanel.add(sshPrivateKeyLabel, "right");
        sshPanel.add(sshPasteKeyRadio, "split 2");
        sshPanel.add(sshFilePathRadio, "wrap");
        sshPanel.add(new JLabel(), "right");
        sshPanel.add(sshPastePanel, "w 450!, wrap");

        // ── HTTPS section ───────────────────────────────────────────────
        httpsInlineRadio = new MirthRadioButton("Inline");
        httpsInlineRadio.setFocusable(false);
        httpsInlineRadio.setBackground(UIConstants.BACKGROUND_COLOR);
        httpsInlineRadio.setSelected(true);
        httpsInlineRadio.addActionListener(e -> httpsCredsModeActionPerformed());

        httpsFilePathRadio = new MirthRadioButton("File path");
        httpsFilePathRadio.setFocusable(false);
        httpsFilePathRadio.setBackground(UIConstants.BACKGROUND_COLOR);
        httpsFilePathRadio.addActionListener(e -> httpsCredsModeActionPerformed());

        httpsCredsModeButtonGroup = new ButtonGroup();
        httpsCredsModeButtonGroup.add(httpsInlineRadio);
        httpsCredsModeButtonGroup.add(httpsFilePathRadio);

        httpsUsernameLabel = new JLabel("Username:");
        httpsUsernameField = new MirthTextField();
        httpsUsernameField.setToolTipText("Enter your Git username.");

        httpsPasswordLabel = new JLabel("PAT:");
        httpsPasswordField = new JPasswordField();
        httpsPasswordField.setBackground(UIConstants.BACKGROUND_COLOR);
        httpsPasswordField.setToolTipText("Enter your Personal Access Token (PAT).");

        httpsCredentialsPathField = new MirthTextField();
        httpsCredentialsPathField.setToolTipText("<html>Path to a credentials file on the Mirth server.<br>" +
                "Format: line 1 = username, line 2 = PAT</html>");
        httpsCredentialsPathField.setVisible(false);

        httpsCredentialsPathHintLabel = new JLabel("Format: line 1 = username, line 2 = PAT");
        httpsCredentialsPathHintLabel.setFont(new Font("Tahoma", Font.PLAIN, 10));
        httpsCredentialsPathHintLabel.setForeground(Color.GRAY);
        httpsCredentialsPathHintLabel.setVisible(false);

        httpsPanel = new JPanel(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
        httpsPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        httpsPanel.add(new JLabel("Credentials:"), "right");
        httpsPanel.add(httpsInlineRadio, "split 2");
        httpsPanel.add(httpsFilePathRadio, "wrap");
        httpsPanel.add(httpsUsernameLabel, "right");
        httpsPanel.add(httpsUsernameField, "w 300!, wrap");
        httpsPanel.add(httpsPasswordLabel, "right");
        httpsPanel.add(httpsPasswordField, "w 300!, wrap");
        httpsPanel.add(new JLabel(), "right");
        httpsPanel.add(httpsCredentialsPathField, "w 450!, wrap");
        httpsPanel.add(new JLabel(), "right");
        httpsPanel.add(httpsCredentialsPathHintLabel, "wrap");
        httpsPanel.setVisible(false);

        validateGitButton = new JButton("Validate Connection");
        validateGitButton.addActionListener(e -> validateGitRemoteRepository());
    }

    private void initLayout() {
        setLayout(new MigLayout("hidemode 3, novisualpadding, insets 8", "[120,right][grow]"));

        add(remoteRepositoryUrlLabel, "right");
        add(remoteRepositoryUrlField, "w 450!, wrap");

        add(branchNameLabel, "right");
        add(branchNameField, "w 160!, wrap");

        add(new JLabel("Auth Type:"), "right");
        add(sshAuthRadio, "split 2");
        add(httpsAuthRadio, "wrap");

        add(sshPanel, "span 2, growx, wrap");
        add(httpsPanel, "span 2, growx, wrap");

        add(new JLabel(), "right");
        add(validateGitButton, "wrap");
    }

    private void authTypeActionPerformed() {
        boolean isSsh = sshAuthRadio.isSelected();
        sshPanel.setVisible(isSsh);
        httpsPanel.setVisible(!isSsh);
    }

    private void sshKeyModeActionPerformed() {
        boolean isPaste = sshPasteKeyRadio.isSelected();
        sshKeyScrollPane.setVisible(isPaste);
        sshLoadButton.setVisible(isPaste);
        sshPrivateKeyPathField.setVisible(!isPaste);
        sshPrivateKeyPathHintLabel.setVisible(!isPaste);
    }

    private void httpsCredsModeActionPerformed() {
        boolean isInline = httpsInlineRadio.isSelected();
        httpsUsernameLabel.setVisible(isInline);
        httpsUsernameField.setVisible(isInline);
        httpsPasswordLabel.setVisible(isInline);
        httpsPasswordField.setVisible(isInline);
        httpsCredentialsPathField.setVisible(!isInline);
        httpsCredentialsPathHintLabel.setVisible(!isInline);
    }

    private void loadPrivateKey() {
        String content = PlatformUI.MIRTH_FRAME.browseForFileString(null);
        if (content != null) {
            sshPrivateKeyField.setText(content.trim());
        }
    }

    private void validateGitRemoteRepository() {
        resetInvalidState();
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

        if (httpsAuthRadio.isSelected()) {
            properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_AUTH_TYPE, GitSettings.AUTH_TYPE_HTTPS);
            if (httpsInlineRadio.isSelected()) {
                String username = httpsUsernameField.getText().trim();
                String password = new String(httpsPasswordField.getPassword()).trim();
                if (!StringUtils.isEmpty(username)) {
                    properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_HTTPS_USERNAME, username);
                }
                if (!StringUtils.isEmpty(password)) {
                    properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_HTTPS_PASSWORD, password);
                }
            } else {
                String credPath = httpsCredentialsPathField.getText().trim();
                if (!StringUtils.isEmpty(credPath)) {
                    properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_HTTPS_CREDENTIALS_PATH, credPath);
                }
            }
        } else {
            properties.setProperty(VersionHistoryProperties.VERSION_HISTORY_REMOTE_AUTH_TYPE, GitSettings.AUTH_TYPE_SSH);
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
        }

        return properties;
    }

    public void setProperties() {
        remoteRepositoryUrlField.setText(versionHistoryProperties.getGitSettings().getRemoteRepositoryUrl());
        branchNameField.setText(versionHistoryProperties.getGitSettings().getBranchName());

        String authType = versionHistoryProperties.getGitSettings().getAuthType();
        if (GitSettings.AUTH_TYPE_HTTPS.equalsIgnoreCase(authType)) {
            httpsAuthRadio.setSelected(true);
            String credPath = versionHistoryProperties.getGitSettings().getHttpsCredentialsPath();
            if (!StringUtils.isEmpty(credPath)) {
                httpsFilePathRadio.setSelected(true);
                httpsCredentialsPathField.setText(credPath);
            } else {
                httpsInlineRadio.setSelected(true);
                httpsUsernameField.setText(versionHistoryProperties.getGitSettings().getHttpsUsername());
                httpsPasswordField.setText(versionHistoryProperties.getGitSettings().getHttpsPassword());
            }
            httpsCredsModeActionPerformed();
        } else {
            sshAuthRadio.setSelected(true);
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

        authTypeActionPerformed();
    }

    public void getProperties() {
        versionHistoryProperties.getGitSettings().setRemoteRepositoryUrl(remoteRepositoryUrlField.getText().trim());
        versionHistoryProperties.getGitSettings().setBranchName(branchNameField.getText().trim());

        if (httpsAuthRadio.isSelected()) {
            versionHistoryProperties.getGitSettings().setAuthType(GitSettings.AUTH_TYPE_HTTPS);
            versionHistoryProperties.getGitSettings().setSshPrivateKey("");
            versionHistoryProperties.getGitSettings().setSshPrivateKeyPath("");
            if (httpsInlineRadio.isSelected()) {
                versionHistoryProperties.getGitSettings().setHttpsUsername(httpsUsernameField.getText().trim());
                versionHistoryProperties.getGitSettings().setHttpsPassword(new String(httpsPasswordField.getPassword()).trim());
                versionHistoryProperties.getGitSettings().setHttpsCredentialsPath("");
            } else {
                versionHistoryProperties.getGitSettings().setHttpsUsername("");
                versionHistoryProperties.getGitSettings().setHttpsPassword("");
                versionHistoryProperties.getGitSettings().setHttpsCredentialsPath(httpsCredentialsPathField.getText().trim());
            }
        } else {
            versionHistoryProperties.getGitSettings().setAuthType(GitSettings.AUTH_TYPE_SSH);
            versionHistoryProperties.getGitSettings().setHttpsUsername("");
            versionHistoryProperties.getGitSettings().setHttpsPassword("");
            versionHistoryProperties.getGitSettings().setHttpsCredentialsPath("");
            if (sshPasteKeyRadio.isSelected()) {
                versionHistoryProperties.getGitSettings().setSshPrivateKey(sshPrivateKeyField.getText().trim());
                versionHistoryProperties.getGitSettings().setSshPrivateKeyPath("");
            } else {
                versionHistoryProperties.getGitSettings().setSshPrivateKey("");
                versionHistoryProperties.getGitSettings().setSshPrivateKeyPath(sshPrivateKeyPathField.getText().trim());
            }
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

        if (sshAuthRadio.isSelected()) {
            if (sshPasteKeyRadio.isSelected() && StringUtils.isEmpty(sshPrivateKeyField.getText().trim())) {
                valid = false;
                sshPrivateKeyField.setBackground(UIConstants.INVALID_COLOR);
            }
            if (sshFilePathRadio.isSelected() && StringUtils.isEmpty(sshPrivateKeyPathField.getText().trim())) {
                valid = false;
                sshPrivateKeyPathField.setBackground(UIConstants.INVALID_COLOR);
            }
        }

        if (httpsAuthRadio.isSelected()) {
            if (httpsInlineRadio.isSelected()) {
                if (StringUtils.isEmpty(httpsUsernameField.getText().trim())) {
                    valid = false;
                    httpsUsernameField.setBackground(UIConstants.INVALID_COLOR);
                }
                if (httpsPasswordField.getPassword().length == 0) {
                    valid = false;
                    httpsPasswordField.setBackground(UIConstants.INVALID_COLOR);
                }
            } else {
                if (StringUtils.isEmpty(httpsCredentialsPathField.getText().trim())) {
                    valid = false;
                    httpsCredentialsPathField.setBackground(UIConstants.INVALID_COLOR);
                }
            }
        }

        return valid;
    }

    public void resetInvalidState() {
        remoteRepositoryUrlField.setBackground(null);
        branchNameField.setBackground(null);
        sshPrivateKeyField.setBackground(null);
        sshPrivateKeyPathField.setBackground(null);
        httpsUsernameField.setBackground(null);
        httpsPasswordField.setBackground(null);
        httpsCredentialsPathField.setBackground(null);
    }
}
