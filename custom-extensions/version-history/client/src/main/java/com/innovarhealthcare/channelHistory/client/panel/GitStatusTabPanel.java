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

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.HierarchyEvent;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.innovarhealthcare.channelHistory.client.dialog.ConflictResolutionDialog;
import com.innovarhealthcare.channelHistory.client.exception.GitConflictClientException;
import com.innovarhealthcare.channelHistory.client.panel.gitstatus.ChangesTabPanel;
import com.innovarhealthcare.channelHistory.client.panel.gitstatus.FilesTabPanel;
import com.innovarhealthcare.channelHistory.client.panel.gitstatus.HistoryTabPanel;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoChanges;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoInfo;
import com.innovarhealthcare.channelHistory.shared.dto.response.RemoteStatus;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import net.miginfocom.swing.MigLayout;

/**
 * Shell panel for the Git Status tab in the Version History settings.
 * Owns the repository info header bar, the JTabbedPane, and the SyncLoadWorker.
 * All tab-specific UI and logic is delegated to the three sub-panels:
 * {@link FilesTabPanel}, {@link ChangesTabPanel}, and {@link HistoryTabPanel}.
 *
 * @author Thai Tran
 */
public class GitStatusTabPanel extends JPanel {

    // ── Tab indices ────────────────────────────────────────────────────────────
    private static final int TAB_FILES = 0;
    private static final int TAB_CHANGES = 1;
    private static final int TAB_HISTORY = 2;

    // ── Header bar ─────────────────────────────────────────────────────────────
    private JLabel localRepoPathValueLabel;
    private JLabel remoteUrlValueLabel;
    private JLabel branchValueLabel;
    private JLabel sizeValueLabel;

    // ── Sub-panels ─────────────────────────────────────────────────────────────
    private FilesTabPanel filesTabPanel;
    private ChangesTabPanel changesTabPanel;
    private HistoryTabPanel historyTabPanel;

    // ── Main tab pane ──────────────────────────────────────────────────────────
    private JTabbedPane leftTabbedPane;

    // ── Header action buttons ──────────────────────────────────────────────────
    private JButton reloadButton;
    private JButton pullButton;
    private JButton pushButton;
    private JLabel syncStatusLabel;

    // ── Status / controls ──────────────────────────────────────────────────────
    private JProgressBar loadingBar;
    private JLabel statusLabel;

    // ── Listener ───────────────────────────────────────────────────────────────
    private ChangeListener tabChangeListener;

    public GitStatusTabPanel(VersionHistoryProperties versionHistoryProperties) {
        VersionHistoryServiceClient client = VersionHistoryServiceClient.getInstance();

        filesTabPanel = new FilesTabPanel(client, this::onViewFullHistory);
        changesTabPanel = new ChangesTabPanel(client);
        historyTabPanel = new HistoryTabPanel(client, versionHistoryProperties);

        initComponents();
        initLayout();
        initListeners();

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                // VersionHistorySettingPanel's ChangeListener guarantees no unsaved changes
                // exist before this panel becomes visible (via UnsavedChangesDialog).
                if (!PlatformUI.MIRTH_FRAME.isSaveEnabled()) {
                    loadDataWithSync();
                }
            }
        });
    }

    // ========== Initialization ==========

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        localRepoPathValueLabel = newValueLabel();
        remoteUrlValueLabel = newValueLabel();
        branchValueLabel = newValueLabel();
        sizeValueLabel = newValueLabel();

        leftTabbedPane = new JTabbedPane(JTabbedPane.TOP);
        leftTabbedPane.addTab("Files", filesTabPanel);
        leftTabbedPane.addTab("Changes", changesTabPanel);
        leftTabbedPane.addTab("History", historyTabPanel);

        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);

        statusLabel = new JLabel();
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Tahoma", Font.PLAIN, 11));
        statusLabel.setVisible(false);

        syncStatusLabel = new JLabel("—");
        syncStatusLabel.setFont(new Font("Tahoma", Font.PLAIN, 11));
        syncStatusLabel.setForeground(new Color(100, 100, 100));

        reloadButton = new JButton("↺");
        reloadButton.setToolTipText("Fetch from remote and update sync status");

        pullButton = new JButton("Pull");
        pullButton.setToolTipText("Pull from remote (overwrites local uncommitted changes)");

        pushButton = new JButton("Push");
        pushButton.setToolTipText("Push local commits to remote");
    }

    private void initLayout() {
        setLayout(new MigLayout("fill, hidemode 3, novisualpadding, insets 4", "[grow]", "[][grow][][]"));

        // ── Header bar ────────────────────────────────────────────────────────
        JPanel infoPanel = new JPanel(new MigLayout("insets 0, novisualpadding", "[right]4[grow,fill]20[right]4[grow,fill]20[right]4[grow,fill]20[right]4[grow,fill]"));
        infoPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        infoPanel.add(new JLabel("Local Path:"));
        infoPanel.add(localRepoPathValueLabel);
        infoPanel.add(new JLabel("Remote:"));
        infoPanel.add(remoteUrlValueLabel);
        infoPanel.add(new JLabel("Branch:"));
        infoPanel.add(branchValueLabel);
        infoPanel.add(new JLabel("Size:"));
        infoPanel.add(sizeValueLabel);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        actionPanel.add(syncStatusLabel);
        actionPanel.add(pullButton);
        actionPanel.add(pushButton);
        actionPanel.add(reloadButton);

        JPanel headerBar = new JPanel(new MigLayout("insets 4 8 4 8, novisualpadding", "[grow,fill][]"));
        headerBar.setBackground(UIConstants.BACKGROUND_COLOR);
        headerBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(204, 204, 204)));
        headerBar.add(infoPanel, "grow");
        headerBar.add(actionPanel);

        add(headerBar, "growx, wrap");
        add(leftTabbedPane, "grow, push, wrap");
        add(loadingBar, "growx, wrap");
        add(statusLabel, "growx, wrap");
    }

    private void initListeners() {
        tabChangeListener = e -> {
            switch (leftTabbedPane.getSelectedIndex()) {
                case TAB_FILES:
                    filesTabPanel.onTabSelected();
                    break;
                case TAB_CHANGES:
                    changesTabPanel.onTabSelected();
                    break;
                case TAB_HISTORY:
                    historyTabPanel.onTabSelected();
                    break;
            }
        };
        leftTabbedPane.addChangeListener(tabChangeListener);

        reloadButton.addActionListener(e -> loadDataWithSync());
        pullButton.addActionListener(e -> onPull());
        pushButton.addActionListener(e -> onPush());
    }

    // ========== Cross-tab Navigation ==========

    private void onViewFullHistory(String relativePath) {
        leftTabbedPane.removeChangeListener(tabChangeListener);
        leftTabbedPane.setSelectedIndex(TAB_HISTORY);
        leftTabbedPane.addChangeListener(tabChangeListener);
        historyTabPanel.loadHistory(relativePath);
    }

    // ========== Data Loading ==========

    private void loadDataWithSync() {
        enterLoadingState();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SyncLoadWorker().execute();
    }

    private void onPull() {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Pull changes from remote?\n\n"
                        + "• If remote has new commits: they will be merged into local.\n"
                        + "• If there are conflicts: remote version wins automatically.\n"
                        + "• Local committed work is preserved.",
                "Pull from Remote",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        setActionButtonsEnabled(false);
        loadingBar.setVisible(true);
        statusLabel.setVisible(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                VersionHistoryServiceClient.getInstance().pull();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadDataWithSync();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                    setCursor(Cursor.getDefaultCursor());
                    loadingBar.setVisible(false);
                    setActionButtonsEnabled(true);
                    statusLabel.setText("Pull failed: " + msg);
                    statusLabel.setVisible(true);
                }
            }
        }.execute();
    }

    private void onPush() {
        setActionButtonsEnabled(false);
        loadingBar.setVisible(true);
        statusLabel.setVisible(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                VersionHistoryServiceClient.getInstance().pushOnly();
                return null;
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                loadingBar.setVisible(false);
                try {
                    get();
                    loadDataWithSync();
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    setActionButtonsEnabled(true);
                    if (cause instanceof GitConflictClientException) {
                        Map<String, String> backed = ((GitConflictClientException) cause).getBackedUpContent();
                        if (backed.isEmpty()) {
                            // Rebase conflict during standalone push — no working-tree changes were
                            // backed up (the user's work is already committed locally). Pull first
                            // to incorporate remote changes, then retry the push.
                            statusLabel.setText("Push failed: Remote branch has diverged. Pull first, then retry.");
                            statusLabel.setVisible(true);
                        } else {
                            Frame parent = PlatformUI.MIRTH_FRAME;
                            new ConflictResolutionDialog(parent, VersionHistoryServiceClient.getInstance(), backed, () -> {
                                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                                new SwingWorker<RepoChanges, Void>() {
                                    @Override
                                    protected RepoChanges doInBackground() throws Exception {
                                        return VersionHistoryServiceClient.getInstance().getRepoChanges();
                                    }

                                    @Override
                                    protected void done() {
                                        setCursor(Cursor.getDefaultCursor());
                                        try {
                                            changesTabPanel.populate(get());
                                        } catch (Exception reloadEx) {
                                            PlatformUI.MIRTH_FRAME.alertError(GitStatusTabPanel.this, "Failed to reload: " + reloadEx.getMessage());
                                        }
                                    }
                                }.execute();
                            }).setVisible(true);
                        }
                    } else {
                        String msg = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                        statusLabel.setText("Push failed: " + msg);
                        statusLabel.setVisible(true);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setActionButtonsEnabled(true);
                } catch (Exception ex) {
                    setActionButtonsEnabled(true);
                    String msg = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
                    statusLabel.setText("Push failed: " + msg);
                    statusLabel.setVisible(true);
                }
            }
        }.execute();
    }

    private void enterLoadingState() {
        loadingBar.setVisible(true);
        statusLabel.setVisible(false);
        setActionButtonsEnabled(false);
        clearValues();
    }

    private void setActionButtonsEnabled(boolean enabled) {
        reloadButton.setEnabled(enabled);
        pullButton.setEnabled(enabled);
        pushButton.setEnabled(enabled);
    }

    private void exitLoadedState(RepoInfo info, RepoChanges changes, RemoteStatus remoteStatus) {
        setCursor(Cursor.getDefaultCursor());
        loadingBar.setVisible(false);
        statusLabel.setVisible(false);
        setActionButtonsEnabled(true);

        localRepoPathValueLabel.setText(info.getLocalRepoPath());
        remoteUrlValueLabel.setText(info.getRemoteUrl());
        branchValueLabel.setText(info.getBranch());
        sizeValueLabel.setText(formatBytes(info.getTotalSizeBytes()));

        updateSyncStatusLabel(remoteStatus);

        filesTabPanel.populate(info);
        changesTabPanel.populate(changes);

        // If the History tab is currently active, reload it now.
        // The ChangeListener only fires when the selected index *changes*, so if the
        // user is already on the History tab when Reload is clicked, the listener
        // would never fire and the tab would remain empty after clear().
        if (leftTabbedPane.getSelectedIndex() == TAB_HISTORY) {
            historyTabPanel.onTabSelected();
        }
    }

    private void updateSyncStatusLabel(RemoteStatus status) {
        if (status == null) {
            syncStatusLabel.setText("—");
            syncStatusLabel.setForeground(new Color(100, 100, 100));
            return;
        }
        int ahead = status.getAheadCount();
        int behind = status.getBehindCount();
        if (ahead == 0 && behind == 0) {
            syncStatusLabel.setText("✓ Up to date");
            syncStatusLabel.setForeground(new Color(0, 128, 0));
        } else if (ahead > 0 && behind == 0) {
            syncStatusLabel.setText("↑" + ahead + " to push");
            syncStatusLabel.setForeground(new Color(0, 100, 200));
        } else if (ahead == 0) {
            syncStatusLabel.setText("↓" + behind + " to pull");
            syncStatusLabel.setForeground(new Color(180, 80, 0));
        } else {
            syncStatusLabel.setText("↑" + ahead + " ↓" + behind);
            syncStatusLabel.setForeground(new Color(150, 0, 0));
        }
    }

    private void exitErrorState(String message) {
        setCursor(Cursor.getDefaultCursor());
        loadingBar.setVisible(false);
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        setActionButtonsEnabled(true);
    }

    private void clearValues() {
        localRepoPathValueLabel.setText("—");
        remoteUrlValueLabel.setText("—");
        branchValueLabel.setText("—");
        sizeValueLabel.setText("—");
        syncStatusLabel.setText("—");
        syncStatusLabel.setForeground(new Color(100, 100, 100));
        filesTabPanel.clear();
        changesTabPanel.clear();
        historyTabPanel.clear();
    }

    /**
     * Resets the loaded state so the next time this panel is shown it will
     * re-fetch data from the server. Call this after a save or refresh.
     */
    public void reset() {
        leftTabbedPane.setSelectedIndex(TAB_FILES);
        filesTabPanel.clear();
        changesTabPanel.clear();
        historyTabPanel.clear();
    }

    // ========== Helpers ==========

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static JLabel newValueLabel() {
        JLabel label = new JLabel("—");
        label.setFont(new Font("Tahoma", Font.PLAIN, 11));
        return label;
    }

    // ========== Background Worker ==========

    private static final class SyncLoadResult {
        final RepoInfo info;
        final RepoChanges changes;
        final RemoteStatus remoteStatus;

        SyncLoadResult(RepoInfo info, RepoChanges changes, RemoteStatus remoteStatus) {
            this.info = info;
            this.changes = changes;
            this.remoteStatus = remoteStatus;
        }
    }

    private final class SyncLoadWorker extends SwingWorker<SyncLoadResult, Void> {
        @Override
        protected SyncLoadResult doInBackground() throws Exception {
            VersionHistoryServiceClient c = VersionHistoryServiceClient.getInstance();

            // All three calls in parallel; getRemoteStatus() does git fetch internally
            CompletableFuture<RepoInfo> infoFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return c.getRepoInfo();
                } catch (ClientException e) {
                    throw new RuntimeException(e);
                }
            });
            CompletableFuture<RepoChanges> changesFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return c.getRepoChanges();
                } catch (ClientException e) {
                    throw new RuntimeException(e);
                }
            });
            CompletableFuture<RemoteStatus> statusFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return c.getRemoteStatus();
                } catch (ClientException e) {
                    throw new RuntimeException(e);
                }
            });

            try {
                return new SyncLoadResult(infoFuture.join(), changesFuture.join(), statusFuture.join());
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw ex;
            }
        }

        @Override
        protected void done() {
            try {
                SyncLoadResult result = get();
                exitLoadedState(result.info, result.changes, result.remoteStatus);
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String msg = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                exitErrorState("Failed to load repository status: " + msg);
            }
        }
    }
}
