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
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.HierarchyEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.innovarhealthcare.channelHistory.client.panel.gitstatus.ChangesTabPanel;
import com.innovarhealthcare.channelHistory.client.panel.gitstatus.FilesTabPanel;
import com.innovarhealthcare.channelHistory.client.panel.gitstatus.HistoryTabPanel;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoChanges;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoInfo;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import net.miginfocom.swing.MigLayout;

/**
 * Shell panel for the Git Status tab in the Version History settings.
 * Owns the repository info header bar, the JTabbedPane, and the LoadDataWorker.
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

    // ── Status / controls ──────────────────────────────────────────────────────
    private JProgressBar loadingBar;
    private JLabel statusLabel;
    private JButton refreshButton;

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
                    loadData();
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

        refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadData());
    }

    private void initLayout() {
        setLayout(new MigLayout("fill, hidemode 3, novisualpadding, insets 4", "[grow]", "[][grow][][]"));

        // ── Header bar ────────────────────────────────────────────────────────
        JPanel headerBar = new JPanel(new MigLayout("insets 4 8 4 8, novisualpadding", "[right]4[grow,fill]20[right]4[grow,fill]20[right]4[grow,fill]20[right]4[grow,fill]"));
        headerBar.setBackground(UIConstants.BACKGROUND_COLOR);
        headerBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(204, 204, 204)));
        headerBar.add(new JLabel("Local Path:"));
        headerBar.add(localRepoPathValueLabel);
        headerBar.add(new JLabel("Remote:"));
        headerBar.add(remoteUrlValueLabel);
        headerBar.add(new JLabel("Branch:"));
        headerBar.add(branchValueLabel);
        headerBar.add(new JLabel("Size:"));
        headerBar.add(sizeValueLabel);

        add(headerBar, "growx, wrap");
        add(leftTabbedPane, "grow, push, wrap");
        add(loadingBar, "growx, wrap");
        add(statusLabel, "growx, wrap");
        add(refreshButton, "right");
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
    }

    // ========== Cross-tab Navigation ==========

    private void onViewFullHistory(String relativePath) {
        leftTabbedPane.removeChangeListener(tabChangeListener);
        leftTabbedPane.setSelectedIndex(TAB_HISTORY);
        leftTabbedPane.addChangeListener(tabChangeListener);
        historyTabPanel.loadHistory(relativePath);
    }

    // ========== Data Loading ==========

    private void loadData() {
        enterLoadingState();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new LoadDataWorker().execute();
    }

    private void enterLoadingState() {
        loadingBar.setVisible(true);
        statusLabel.setVisible(false);
        refreshButton.setEnabled(false);
        clearValues();
    }

    private void exitLoadedState(RepoInfo info, RepoChanges changes) {
        setCursor(Cursor.getDefaultCursor());
        loadingBar.setVisible(false);
        statusLabel.setVisible(false);
        refreshButton.setEnabled(true);

        localRepoPathValueLabel.setText(info.getLocalRepoPath());
        remoteUrlValueLabel.setText(info.getRemoteUrl());
        branchValueLabel.setText(info.getBranch());
        sizeValueLabel.setText(formatBytes(info.getTotalSizeBytes()));

        filesTabPanel.populate(info);
        changesTabPanel.populate(changes);

        // If the History tab is currently active, reload it now.
        // The ChangeListener only fires when the selected index *changes*, so if the
        // user is already on the History tab when Refresh is clicked, the listener
        // would never fire and the tab would remain empty after clear().
        if (leftTabbedPane.getSelectedIndex() == TAB_HISTORY) {
            historyTabPanel.onTabSelected();
        }
    }

    private void exitErrorState(String message) {
        setCursor(Cursor.getDefaultCursor());
        loadingBar.setVisible(false);
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        refreshButton.setEnabled(true);
    }

    private void clearValues() {
        localRepoPathValueLabel.setText("—");
        remoteUrlValueLabel.setText("—");
        branchValueLabel.setText("—");
        sizeValueLabel.setText("—");
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

    private static final class LoadResult {
        final RepoInfo info;
        final RepoChanges changes;

        LoadResult(RepoInfo info, RepoChanges changes) {
            this.info = info;
            this.changes = changes;
        }
    }

    private final class LoadDataWorker extends SwingWorker<LoadResult, Void> {
        @Override
        protected LoadResult doInBackground() throws Exception {
            VersionHistoryServiceClient c = VersionHistoryServiceClient.getInstance();

            // Fetch repoInfo and repoChanges in parallel
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

            try {
                return new LoadResult(infoFuture.join(), changesFuture.join());
            } catch (CompletionException ex) {
                // Unwrap: CompletionException → RuntimeException → ClientException
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
                LoadResult result = get();
                exitLoadedState(result.info, result.changes);
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String msg = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                exitErrorState("Failed to load repository status: " + msg);
            }
        }
    }
}
