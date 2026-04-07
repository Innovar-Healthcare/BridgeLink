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
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import com.innovarhealthcare.channelHistory.client.dialog.ChannelDiffDialog;
import com.innovarhealthcare.channelHistory.client.dialog.CommitMessageDialog;
import com.innovarhealthcare.channelHistory.client.dialog.ConflictResolutionDialog;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;
import com.innovarhealthcare.channelHistory.client.exception.VersionHistoryClientException;
import com.innovarhealthcare.channelHistory.client.model.ChannelWithRaw;
import com.innovarhealthcare.channelHistory.client.model.CommitMetaDataTableModel;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.client.table.CommitMetaDataTable;
import com.innovarhealthcare.channelHistory.client.taskpanel.ChannelHistoryContext;
import com.innovarhealthcare.channelHistory.client.taskpanel.ChannelHistoryOperations;
import com.innovarhealthcare.channelHistory.client.taskpanel.VersionHistoryTaskPane;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.AbstractChannelTabPanel;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class ChannelHistoryTabPanel extends AbstractChannelTabPanel {
    private static final Logger logger = LogManager.getLogger(ChannelHistoryTabPanel.class);

    private JPanel disablePanel;
    private JPanel historyPanel;
    private JScrollPane historyScrollPane;
    private CommitMetaDataTable tblCommitMetaData;
    private String currentChannelId;

    private final Frame parent;
    private final VersionHistoryProperties versionHistoryProperties;

    public ChannelHistoryTabPanel(Frame parent) {
        this.parent = parent;

        initComponents();
        initLayout();

        versionHistoryProperties = new VersionHistoryProperties();

        // Load version history properties in background
        loadVersionHistoryProperties();

        // Setup visibility listener
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                onPanelShown();
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                onPanelHidden();
            }
        });
    }

    @Override
    public void load(Channel channel) {
        if (channel == null || StringUtils.isBlank(channel.getId())) {
            logger.warn("Cannot load history: invalid channel");
            return;
        }

        currentChannelId = channel.getId();

        // Load version history properties in background
        loadVersionHistoryProperties();
    }

    @Override
    public void save(Channel channel) {
        if (!versionHistoryProperties.isEnableVersionHistory()) {
            return;
        }

        if (!versionHistoryProperties.isEnableAutoCommit()) {
            // Write channel to working tree so Changes tab reflects pending changes.
            // Fire-and-forget: errors are logged but not shown to user.
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    VersionHistoryServiceClient.getInstance().writeChannel(channel);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                    } catch (java.util.concurrent.ExecutionException e) {
                        logger.warn("Failed to write channel to working tree after save: {}", e.getCause().getMessage());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }.execute();
            return;
        }

        String message = "";
        if (versionHistoryProperties.isEnableAutoCommitPrompt()) {
            // show prompt at here
            JTextArea textArea = new JTextArea(5, 30); // 5 rows, 30 columns
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(textArea);

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel("Enter a comment:"), BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);

            int result = JOptionPane.showConfirmDialog(parent, panel, "Auto Commit", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                message = StringUtils.trim(textArea.getText());
                if (message.isEmpty()) {
                    message = versionHistoryProperties.getAutoCommitMsg();
                }
            } else {
                message = versionHistoryProperties.getAutoCommitMsg();
            }
        } else {
            message = versionHistoryProperties.getAutoCommitMsg();
        }

        final String workingId = parent.startWorking("Commit & Push " + currentChannelId + " channel...");

        String finalMessage = message;
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private String errorMessage = null;

            @Override
            protected Void doInBackground() throws Exception {
                // Wait for save to complete (silent, in background)
                final int MAX_TRY = 10;
                int cnt = 0;
                while (parent.isSaveEnabled() && cnt < MAX_TRY) {
                    Thread.sleep(500);
                    cnt++;
                }

                // Check timeout
                if (cnt >= MAX_TRY) {
                    errorMessage = "Cannot commit: Channel is still being saved.\nPlease commit manually.";
                    return null;
                }

                // Extra buffer to ensure save is complete
                Thread.sleep(300);

                // Try to commit
                try {
                    String operationDetails = doCommitAndPushCurrentChannel(finalMessage);

                    logger.info("Channel committed successfully");

                    // Optional: Log operation details for debugging
                    logger.debug("Commit details: {}", operationDetails);
                } catch (Exception e) {
                    errorMessage = "Failed to commit channel:\n" + e.getMessage();
                    logger.error("Commit exception", e);
                }

                return null;
            }

            @Override
            protected void done() {
                parent.stopWorking(workingId);

                if (errorMessage != null) {
                    showError(errorMessage);
                } else {
                    // Reload history if needed (still showing)
                    if (isShowing()) {
                        loadHistory(false);
                    }
                }
            }
        };

        worker.execute();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        // Disable
        disablePanel = new JPanel();
        disablePanel.setBackground(this.getBackground());
        disablePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(0, 0, 0, 0, new Color(204, 204, 204)), "Version Control is disabled", TitledBorder.DEFAULT_JUSTIFICATION, 1, new Font("Tahoma", 1, 15)));

        // History
        historyPanel = new JPanel();
        historyPanel.setBackground(this.getBackground());
        historyPanel.setBorder(BorderFactory.createTitledBorder("History"));

        tblCommitMetaData = new CommitMetaDataTable();
        tblCommitMetaData.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    handlePopup(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    handlePopup(e);
                }
            }

            private void handlePopup(MouseEvent e) {
                // Select row if clicked on a row
                int row = tblCommitMetaData.rowAtPoint(e.getPoint());
                if (row >= 0 && !tblCommitMetaData.isRowSelected(row)) {
                    tblCommitMetaData.setRowSelectionInterval(row, row);
                }

                // Show popup at click position
                JPopupMenu popup = VersionHistoryTaskPane.getInstance().getPopupMenu();
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        historyScrollPane = new JScrollPane(tblCommitMetaData);
    }

    /**
     * Initializes the panel layout.
     * Note: Action buttons removed - now using Version History task pane instead.
     */
    private void initLayout() {
        setLayout(new MigLayout("insets 12, novisualpadding, hidemode 3, fill", "", "[][grow][]"));

        // History panel - shows commit history table
        historyPanel.setLayout(new MigLayout("insets 0 10 10 10, novisualpadding, hidemode 3, fill, gap 6", "[grow][]"));
        historyPanel.add(historyScrollPane, "sy, grow");

        // Disable panel - for any disable/enable controls if needed
        disablePanel.setLayout(new MigLayout("insets 0 10 10 10, novisualpadding, hidemode 3, fill, gap 6", "[]12[]12[][grow]"));

        // Layout components (removed actionPanel line)
        add(historyPanel, "newline, grow, pushx");
        add(disablePanel, "newline, growx, sx");
    }

    /**
     * Called when panel becomes visible
     */
    private void onPanelShown() {
        if (versionHistoryProperties.isEnableVersionHistory()) {
            // Show enabled state
            disablePanel.setVisible(false);
            historyPanel.setVisible(true);

            loadHistory(true);
        } else {
            // Show disabled state
            disablePanel.setVisible(true);
            historyPanel.setVisible(false);
        }

        // Always show Summary Tab Tasks while this panel selected
        parent.setVisibleTasks(parent.channelEditTasks, parent.channelEditPopupMenu, 1, 13, false);
        parent.setVisibleTasks(parent.channelEditTasks, parent.channelEditPopupMenu, 15, 15, false);

        showVersionHistoryTaskPane();
    }

    /**
     * Called when panel becomes hidden
     */
    private void onPanelHidden() {
        hideVersionHistoryTaskPane();
    }

    /**
     * Shows version history task pane with channel edit context.
     * Creates operations from this panel's methods and sets the appropriate context.
     */
    private void showVersionHistoryTaskPane() {
        if (!VersionHistoryTaskPane.isInitialized()) {
            return;
        }

        // Create operations adapter wrapping this panel's methods
        ChannelHistoryOperations operations = new ChannelHistoryOperations(this::showDiffWindow,           // Diff action
                this::commitThenPushAction,     // Commit & Push action
                () -> loadHistory(true),        // Pull/Reload action
                this::revertAction              // Revert action
        );

        // Create channel edit context
        ChannelHistoryContext context = new ChannelHistoryContext(operations);

        // Set context on task pane (this shows it and configures buttons)
        VersionHistoryTaskPane.getInstance().setContext(context);
    }

    /**
     * Hides version history task pane by setting context to null
     */
    private void hideVersionHistoryTaskPane() {
        if (VersionHistoryTaskPane.isInitialized()) {
            VersionHistoryTaskPane.getInstance().setContext(null);
        }
    }

    /**
     * Load version history properties from server in background
     */
    private void loadVersionHistoryProperties() {
        new LoadVersionHistoryPropertiesWorker().execute();
    }

    /**
     * Load git history in background thread
     *
     * @param showErrorOnFailure Whether to show error
     */
    private void loadHistory(boolean showErrorOnFailure) {
        new LoadGitHistoryWorker(showErrorOnFailure).execute();
    }

    /**
     * Reverts the current channel to a selected historical version.
     * <p>
     * Validates that exactly one version is selected from the history table,
     * then performs the revert operation after user confirmation.
     *
     * @see #revert(String, String)
     */
    private void revertAction() {
        int[] selectedRows = tblCommitMetaData.getSelectedRows();

        // Validate selection - must select exactly one version
        if (selectedRows.length == 0) {
            showError("Please select a version to revert to");
            return;
        }

        if (selectedRows.length > 1) {
            showError("Please select only one version to revert to");
            return;
        }

        // Get selected version metadata
        CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
        CommitMetaData selectedVersion = model.getCommitMetaDataAt(selectedRows[0]);

        if (selectedVersion == null) {
            showError("Invalid version selected");
            return;
        }

        // Perform revert operation
        revert(currentChannelId, selectedVersion.getHash());
    }

    /**
     * Shows diff window based on selected rows
     * - 0 rows: Show error
     * - 1 row: Compare current channel with selected version
     * - 2 rows: Compare two selected versions
     * - 3+ rows: Show error
     */
    private void showDiffWindow() {
        int[] selectedRows = tblCommitMetaData.getSelectedRows();

        // Validate selection
        if (selectedRows.length == 0) {
            showError("Please select at least one version to compare");
            return;
        }

        if (selectedRows.length > 2) {
            showError("Please select maximum 2 versions to compare");
            return;
        }

        CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();

        try {
            if (selectedRows.length == 1) {
                // Compare current channel with selected version
                showDiffWithCurrent(model.getCommitMetaDataAt(selectedRows[0]));
            } else {
                // Compare two selected versions
                showDiffBetweenVersions(model.getCommitMetaDataAt(selectedRows[0]), model.getCommitMetaDataAt(selectedRows[1]));
            }
        } catch (Exception e) {
            logger.error("Failed to show version comparison", e);
            showError("Cannot compare versions: " + e.getMessage());
        }
    }

    /**
     * Compare current channel with a historical version
     */
    private void showDiffWithCurrent(CommitMetaData historicalVersion) throws Exception {
        if (historicalVersion == null) {
            showError("Invalid version selected");
            return;
        }

        Client client = parent.mirthClient;
        String currentUserName = client.getCurrentUser().getUsername();

        // Get current channel (left side)
        Channel currentChannel = client.getChannel(currentChannelId, false);
        String currentXml = ObjectXMLSerializer.getInstance().serialize(currentChannel);

        // Get historical version (right side)
        ChannelWithRaw historicalData = VersionHistoryServiceClient.getInstance().loadChannelWithRawFromRepo(currentChannelId, historicalVersion.getHash());

        // Build VersionInfo objects
        String channelName = currentChannel.getName();

        VersionInfo leftVersion = VersionInfo.createCurrent(channelName, currentUserName);

        VersionInfo rightVersion = VersionInfo.createHistorical(channelName, historicalVersion.getHash().substring(0, 7), historicalVersion.getCommitter(), new Date(historicalVersion.getTimestamp()));

        // Show comparison dialog
        new ChannelDiffDialog(parent, leftVersion, rightVersion, currentXml, historicalData.getRawContent()).setVisible(true);
    }

    /**
     * Compare two historical versions
     */
    private void showDiffBetweenVersions(CommitMetaData version1, CommitMetaData version2) throws Exception {
        if (version1 == null || version2 == null) {
            showError("Invalid versions selected");
            return;
        }

        // Load both versions from repository
        ChannelWithRaw leftData = VersionHistoryServiceClient.getInstance().loadChannelWithRawFromRepo(currentChannelId, version1.getHash());

        ChannelWithRaw rightData = VersionHistoryServiceClient.getInstance().loadChannelWithRawFromRepo(currentChannelId, version2.getHash());

        Channel leftChannel = leftData.getChannel();
        Channel rightChannel = rightData.getChannel();

        // Build VersionInfo for left side
        VersionInfo leftVersion = VersionInfo.createHistorical(leftChannel.getName(), version1.getHash().substring(0, 7), version1.getCommitter(), new Date(version1.getTimestamp()));

        // Build VersionInfo for right side
        VersionInfo rightVersion = VersionInfo.createHistorical(rightChannel.getName(), version2.getHash().substring(0, 7), version2.getCommitter(), new Date(version2.getTimestamp()));

        // Show comparison dialog
        new ChannelDiffDialog(parent, leftVersion, rightVersion, leftData.getRawContent(), rightData.getRawContent()).setVisible(true);
    }


    private void revert(String channelId, String rev) {
        int option = JOptionPane.showConfirmDialog(
                parent,
                "Would you like to revert channel to this revision?\n\nThe channel will be loaded in the editor. Please save to complete the revert.",
                "Revert Channel",
                JOptionPane.YES_NO_OPTION
        );

        if (option != JOptionPane.YES_OPTION) {
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Channel, Void>() {
            @Override
            protected Channel doInBackground() throws Exception {
                return VersionHistoryServiceClient.getInstance().loadChannelFromRepo(channelId, rev);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    Channel channel = get();
                    PlatformUI.MIRTH_FRAME.editChannel(channel);
                    PlatformUI.MIRTH_FRAME.setSaveEnabled(true);
                } catch (ExecutionException e) {
                    logger.error("Failed to load channel revision for revert", e);
                    showError("Failed to load channel revision: " + e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void commitThenPushAction() {
        if (parent.isSaveEnabled()) {
            showInformation("This channel has been modified. You must save the channel changes before you can commit to remote repository");
            return;
        }

        String userId;
        try {
            userId = String.valueOf(parent.mirthClient.getCurrentUser().getId());
        } catch (ClientException e) {
            showError("Could not get current user: " + e.getMessage());
            return;
        }

        try {
            Channel channel = parent.mirthClient.getChannel(currentChannelId, false);

            CommitMessageDialog.CommitAction commitAction = msg -> VersionHistoryServiceClient.getInstance().commitAndPushChannel(channel, msg, userId, false);

            Runnable onSuccess = () -> {
                if (isShowing()) {
                    loadHistory(false);
                }
                showInformation("Channel committed successfully.");
            };

            java.util.function.Consumer<java.util.Map<String, String>> onConflict = backedUpContent -> new ConflictResolutionDialog(PlatformUI.MIRTH_FRAME, VersionHistoryServiceClient.getInstance(), backedUpContent, () -> {
                if (isShowing()) {
                    loadHistory(false);
                }
            }).setVisible(true);

            new CommitMessageDialog(PlatformUI.MIRTH_FRAME, commitAction, onSuccess, onConflict).setVisible(true);

        } catch (ClientException e) {
            showError("Could not load channel: " + e.getMessage());
        }
    }

    /**
     * Worker to load version history properties from server
     */
    private class LoadVersionHistoryPropertiesWorker extends SwingWorker<Properties, Void> {
        @Override
        protected Properties doInBackground() throws Exception {
            Properties properties;
            try {
                Client client = parent.mirthClient;
                properties = client.getPluginProperties(VersionControlConstants.PLUGIN_NAME);
            } catch (ClientException e) {
                logger.warn("Failed to load version history properties, using defaults", e);
                properties = new Properties();
            }

            return properties;
        }

        @Override
        protected void done() {
            try {
                Properties properties = get();
                versionHistoryProperties.fromProperties(properties);
            } catch (ExecutionException e) {
                logger.error("Failed to load version history properties", e);
                // Set default nếu load fail
                versionHistoryProperties.fromProperties(new Properties());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * SwingWorker to load commit history in background
     */
    private class LoadGitHistoryWorker extends SwingWorker<List<CommitMetaData>, Void> {
        private final boolean showErrorOnFailure;
        private final String workingId;

        LoadGitHistoryWorker(boolean showErrorOnFailure) {
            this.showErrorOnFailure = showErrorOnFailure;
            this.workingId = parent.startWorking("Loading channel history...");

            setLoadingState(true);
        }

        @Override
        protected List<CommitMetaData> doInBackground() throws Exception {
            // Background thread - load history from server
            logger.debug("Loading history for channel: {}", currentChannelId);
            return VersionHistoryServiceClient.getInstance().loadChannelHistory(currentChannelId);
        }

        @Override
        protected void done() {
            parent.stopWorking(workingId);
            setLoadingState(false);

            // EDT - update UI
            try {
                List<CommitMetaData> revisions = get();
                logger.debug("Loaded {} revisions", revisions.size());

                // Update table model
                CommitMetaDataTableModel model = new CommitMetaDataTableModel(revisions);
                tblCommitMetaData.setModel(model);


            } catch (ExecutionException e) {
                // Set empty model on error
                tblCommitMetaData.setModel(new CommitMetaDataTableModel(new ArrayList<>()));

                // Only log for unexpected exceptions
                Throwable cause = e.getCause();
                if (!(cause instanceof VersionHistoryClientException)) {
                    logger.error("Failed to load channel history", e);
                }

                if (showErrorOnFailure) {
                    // Extract and show error message
                    String errorMsg;
                    if (cause instanceof VersionHistoryClientException) {
                        VersionHistoryClientException vhException = (VersionHistoryClientException) cause;
                        errorMsg = vhException.getError().getMessage();
                    } else {
                        errorMsg = (cause != null && cause.getMessage() != null) ? cause.getMessage() : "An unexpected error occurred";
                    }

                    showError(errorMsg);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("History loading was interrupted");
            }
        }
    }

    private String doCommitAndPushCurrentChannel(String message) throws ClientException {
        Client client = parent.mirthClient;
        Channel channel = client.getChannel(currentChannelId, false);
        String userId = String.valueOf(client.getCurrentUser().getId());

        return VersionHistoryServiceClient.getInstance().commitAndPushChannel(channel, message, userId);
    }

    /**
     * Set loading state for the history panel
     *
     * @param loading true to show loading state, false to restore normal state
     */
    private void setLoadingState(boolean loading) {
        // Disable/enable table
        tblCommitMetaData.setEnabled(!loading);

        // Disable/enable action buttons

        // Clear table when starting to load
        if (loading) {
            tblCommitMetaData.setModel(new CommitMetaDataTableModel(new ArrayList<>()));
        }
    }

    private void showInformation(String msg) {
        PlatformUI.MIRTH_FRAME.alertInformation(parent, msg);
    }

    private void showError(String msg) {
        PlatformUI.MIRTH_FRAME.alertError(parent, msg);
    }
}
