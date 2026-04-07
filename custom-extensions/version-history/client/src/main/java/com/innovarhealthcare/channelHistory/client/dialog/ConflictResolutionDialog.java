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

import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.mirth.connect.client.ui.MirthDialog;
import net.miginfocom.swing.MigLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Modal dialog shown after a rebase conflict during commitAndPushFiles().
 * The remote repository was updated while the commit was in progress; the server
 * backed up the local file content and reset to the remote HEAD.
 * <p>
 * The user can either dismiss (keep the remote version) or click
 * "Apply My Changes" to write the backed-up content back to the working tree
 * via {@code POST /restoreFiles}.
 *
 * @author Thai Tran
 */
public class ConflictResolutionDialog extends MirthDialog {

    private static final Logger logger = LogManager.getLogger(ConflictResolutionDialog.class);

    private final VersionHistoryServiceClient client;
    private final Map<String, String> backedUpContent;
    private final Runnable onApply;

    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JButton applyButton;
    private JButton dismissButton;

    public ConflictResolutionDialog(Frame parent, VersionHistoryServiceClient client,
            Map<String, String> backedUpContent, Runnable onApply) {
        super(parent, "Conflict — Remote Has Changes", true);
        this.client = client;
        this.backedUpContent = backedUpContent;
        this.onApply = onApply;
        initComponents();
        initLayout();
        initListeners();
        setSize(520, 230);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    // ========== Initialization ==========

    private void initComponents() {
        statusLabel = new JLabel(" ");
        statusLabel.setVisible(false);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);

        applyButton = new JButton("Apply My Changes");
        dismissButton = new JButton("Dismiss");
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 16, novisualpadding, fill",
                "[grow,fill]", "[][][grow][][]"));

        JLabel titleLabel = new JLabel("Remote has conflicting changes.");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        add(titleLabel, "wrap");

        add(new JLabel("The remote repository was updated while your commit was in progress."), "wrap");
        add(new JLabel("<html>Your changes have been backed up (<b>" + backedUpContent.size()
                + "</b> file(s)). The working tree now reflects the remote version.<br><br>"
                + "Click <b>Apply My Changes</b> to restore your changes to the working tree.<br>"
                + "The files will appear as modified so you can commit them again.</html>"),
                "grow, wrap, gaptop 4");

        add(statusLabel, "wrap");
        add(progressBar, "growx, wrap");

        JPanel buttonPanel = new JPanel(new MigLayout("insets 0, novisualpadding", "push[][]"));
        buttonPanel.add(dismissButton, "w 108!");
        buttonPanel.add(applyButton, "w 140!");
        add(buttonPanel, "growx, wrap");
    }

    private void initListeners() {
        dismissButton.addActionListener(e -> dispose());
        applyButton.addActionListener(e -> startRestore());

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (dismissButton.isEnabled()) dispose();
            }
        });
    }

    // ========== Restore Flow ==========

    private void startRestore() {
        applyButton.setEnabled(false);
        dismissButton.setEnabled(false);
        statusLabel.setVisible(false);
        progressBar.setVisible(true);
        new RestoreWorker().execute();
    }

    private final class RestoreWorker extends SwingWorker<Void, Void> {
        @Override
        protected Void doInBackground() throws Exception {
            client.restoreFiles(backedUpContent);
            return null;
        }

        @Override
        protected void done() {
            progressBar.setVisible(false);
            dismissButton.setEnabled(true);
            applyButton.setEnabled(true);
            try {
                get();
                dispose();
                onApply.run();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                String msg = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                logger.error("Failed to restore files to working tree", ex);
                String safe = msg.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                statusLabel.setText("<html><font color='#CC0000'>Error: " + safe + "</font></html>");
                statusLabel.setVisible(true);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
