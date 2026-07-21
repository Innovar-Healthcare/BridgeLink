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

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;
import com.innovarhealthcare.channelHistory.client.model.CodeTemplateWithRaw;
import com.innovarhealthcare.channelHistory.client.model.CommitMetaDataTableModel;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.client.table.CommitMetaDataTable;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import net.miginfocom.swing.MigLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdesktop.swingx.JXTaskPane;
import org.jdesktop.swingx.JXTaskPaneContainer;

/**
 * Test version using JXTaskPane for menu panel (like Mirth Connect style)
 */
public class CodeTemplateHistoryDialogWithTaskPane extends JDialog {
    private static Logger logger = LogManager.getLogger(CodeTemplateHistoryDialogWithTaskPane.class);

    private JXTaskPaneContainer taskPaneContainer;
    private JPanel historyPanel;

    // Actions
    private Action differenceAction;
    private Action commitPushAction;
    private Action pullAction;
    private Action revertAction;

    private CommitMetaDataTable tblCommitMetaData;
    private JScrollPane historyScrollPane;
    private JComboBox<Integer> commitLimitCombo;

    private final String codeTemplateId;
    private JPopupMenu popupMenu;

    private final Frame parent = PlatformUI.MIRTH_FRAME;

    public CodeTemplateHistoryDialogWithTaskPane(Window parent, String codeTemplateId) {
        super(parent);

        this.codeTemplateId = codeTemplateId;

        initComponents();
        initLayout();

        load();

        pack();
        setModal(true);
        setVisible(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    }

    private void initComponents() {
        // Create task pane container
        taskPaneContainer = new JXTaskPaneContainer();

        // Setup background painters (MC style)
        setupBackgroundPainters();

        // Add border without title to match History panel style
        taskPaneContainer.setBorder(BorderFactory.createEtchedBorder());

        // Create Version History task pane
        JXTaskPane actionsPane = new JXTaskPane();

        actionsPane.setTitle("Version History");
        actionsPane.setName("Version History");
        actionsPane.setFocusable(false);

        // Create actions in order: Diff, Commit & Push, Pull, Revert
        // Using MC standard icons
        differenceAction = new AbstractAction("Diff") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedCount = tblCommitMetaData.getSelectedRowCount();
                if (selectedCount == 1) {
                    showDiffLastChangeWindow();
                } else if (selectedCount == 2) {
                    showDiffWindow();
                }
            }
        };
        differenceAction.setEnabled(false);
        differenceAction.putValue(Action.SHORT_DESCRIPTION, "Compare versions (1 row: vs current, 2 rows: compare each other)");
        differenceAction.putValue(Action.SMALL_ICON, new ImageIcon(Frame.class.getResource("images/application_view_detail.png")));

        commitPushAction = new AbstractAction("Commit & Push") {
            @Override
            public void actionPerformed(ActionEvent e) {
                commitThenPush();
            }
        };
        commitPushAction.putValue(Action.SHORT_DESCRIPTION, "Save changes and push to repository");
        commitPushAction.putValue(Action.SMALL_ICON, new ImageIcon(Frame.class.getResource("images/accept.png")));

        pullAction = new AbstractAction("Pull") {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadHistory(true);
            }
        };
        pullAction.putValue(Action.SHORT_DESCRIPTION, "Pull latest changes from repository");
        pullAction.putValue(Action.SMALL_ICON, new ImageIcon(Frame.class.getResource("images/arrow_refresh.png")));

        revertAction = new AbstractAction("Revert") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = tblCommitMetaData.getSelectedRow();
                if (row >= 0) {
                    CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
                    CommitMetaData meta = model.getCommitMetaDataAt(row);
                    revert(codeTemplateId, meta.getHash());
                }
            }
        };
        revertAction.setEnabled(false);
        revertAction.putValue(Action.SHORT_DESCRIPTION, "Revert code template to selected revision");
        revertAction.putValue(Action.SMALL_ICON, new ImageIcon(Frame.class.getResource("images/arrow_undo.png")));

        // Add actions to task pane in specified order
        actionsPane.add(differenceAction);
        actionsPane.add(commitPushAction);
        actionsPane.add(pullAction);
        actionsPane.add(revertAction);

        // Add task pane to container
        taskPaneContainer.add(actionsPane);

        // Create popup menu with same actions and same order as task pane (MC pattern)
        // This popup will show on right-click in the history table
        popupMenu = new JPopupMenu();
        popupMenu.add(new JMenuItem(differenceAction));
        popupMenu.add(new JMenuItem(commitPushAction));
        popupMenu.add(new JMenuItem(pullAction));
        popupMenu.add(new JMenuItem(revertAction));

        // History panel
        historyPanel = new JPanel();
        historyPanel.setBackground(this.getBackground());
        historyPanel.setBorder(BorderFactory.createTitledBorder("History"));

        tblCommitMetaData = new CommitMetaDataTable();
        tblCommitMetaData.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    updateActionStates();
                }
            }
        });

        tblCommitMetaData.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                checkForPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                checkForPopup(e);
            }

            private void checkForPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    // Select row under mouse if not already selected
                    int row = tblCommitMetaData.rowAtPoint(e.getPoint());
                    if (row >= 0 && !tblCommitMetaData.isRowSelected(row)) {
                        tblCommitMetaData.setRowSelectionInterval(row, row);
                    }

                    // Show popup menu
                    showPopupMenu(e);
                }
            }
        });

        historyScrollPane = new JScrollPane(tblCommitMetaData);

        commitLimitCombo = new JComboBox<>(new Integer[]{5, 10, 20, 50, 100, 200, 500});
        commitLimitCombo.setSelectedItem(10);
        commitLimitCombo.addActionListener(e -> loadHistory(false));
    }

    /**
     * Show popup menu with context-aware item visibility (MC pattern)
     */
    private void showPopupMenu(MouseEvent e) {
        int selectedCount = tblCommitMetaData.getSelectedRowCount();

        // Update menu item visibility and enabled state based on selection
        for (Component comp : popupMenu.getComponents()) {
            if (comp instanceof JMenuItem) {
                JMenuItem item = (JMenuItem) comp;
                Action action = item.getAction();

                if (action == differenceAction) {
                    // Diff: 1 row (vs current) or 2 rows (compare each other)
                    boolean canDiff = (selectedCount == 1 || selectedCount == 2);
                    item.setVisible(canDiff);
                    item.setEnabled(canDiff);
                } else if (action == commitPushAction) {
                    // Commit & Push: always visible and enabled
                    item.setVisible(true);
                    item.setEnabled(true);
                } else if (action == pullAction) {
                    // Pull: always visible and enabled
                    item.setVisible(true);
                    item.setEnabled(true);
                } else if (action == revertAction) {
                    // Revert: only 1 row
                    item.setVisible(selectedCount == 1);
                    item.setEnabled(selectedCount == 1);
                }
            }
        }

        popupMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 12, novisualpadding, hidemode 3, fill", "[180!]3[grow]", // 180px for task pane, gap 3px (tighter), rest grows
                "[grow]"));

        setTitle("Code Template History (TaskPane Version)");
        setPreferredSize(new Dimension(1200, 700));
        Dimension dlgSize = getPreferredSize();
        Dimension frmSize = parent.getSize();
        Point loc = parent.getLocation();

        if ((frmSize.width == 0 && frmSize.height == 0) || (loc.x == 0 && loc.y == 0)) {
            setLocationRelativeTo(null);
        } else {
            setLocation((frmSize.width - dlgSize.width) / 2 + loc.x, (frmSize.height - dlgSize.height) / 2 + loc.y);
        }

        // Wrap task pane in panel to add padding/margin like History panel
        JPanel taskPaneWrapper = new JPanel();
        taskPaneWrapper.setBackground(this.getBackground());
        taskPaneWrapper.setBorder(BorderFactory.createTitledBorder("Action"));
        taskPaneWrapper.setLayout(new MigLayout("insets 0 10 10 10, novisualpadding, hidemode 3, fill", "[grow]", "[grow]"));
        taskPaneWrapper.add(taskPaneContainer, "grow, push");

        // Add task pane wrapper on the left
        add(taskPaneWrapper, "growy, spany");

        // History panel on the right
        historyPanel.setLayout(new MigLayout("insets 0 10 10 10, novisualpadding, hidemode 3, fill, gap 4", "[grow][]", "[]2[grow]"));
        historyPanel.add(new JLabel("Show last:"), "split 2");
        historyPanel.add(commitLimitCombo, "wrap 2");
        historyPanel.add(historyScrollPane, "span 2, grow, push");

        add(historyPanel, "grow, push");
    }

    /**
     * Setup background painters for task pane container (MC style)
     */
    private void setupBackgroundPainters() {
        // Remove background painter - use default/transparent background
        taskPaneContainer.setBackground(this.getBackground());
    }

    private void updateActionStates() {
        int selectedCount = tblCommitMetaData.getSelectedRowCount();

        // Diff: enabled for 1 or 2 rows
        differenceAction.setEnabled(selectedCount == 1 || selectedCount == 2);

        // Revert: only for 1 row
        revertAction.setEnabled(selectedCount == 1);
    }

    public void load() {
        // Commit & Push is always enabled (not dependent on auto-commit setting)
        this.loadHistory(false);
    }

    public void loadHistory(boolean shouldNotifyOnComplete) {
        new LoadGitHistoryWorker(shouldNotifyOnComplete).execute();
    }

    private void showDiffLastChangeWindow() {
        CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
        CommitMetaData lastChange = model.getCommitMetaDataAt(tblCommitMetaData.getSelectedRow());

        if (lastChange == null) {
            showError("No code template revision selected");
            return;
        }

        try {
            Client client = parent.mirthClient;
            String currentUserName = client.getCurrentUser().getUsername();

            // Load current code template
            CodeTemplate leftCodeTemplate = client.getCodeTemplate(codeTemplateId);
            String left = ObjectXMLSerializer.getInstance().serialize(leftCodeTemplate);

            // Load historical code template
            CodeTemplateWithRaw right = VersionHistoryServiceClient.getInstance().loadCodeTemplateWithRawFromRepo(codeTemplateId, lastChange.getHash());

            // Build VersionInfo for current version
            VersionInfo currentVersion = VersionInfo.createCurrent(leftCodeTemplate.getName(), currentUserName);

            // Build VersionInfo for historical version
            VersionInfo historicalVersion = VersionInfo.createHistorical(leftCodeTemplate.getName(), lastChange.getHash().substring(0, 7), lastChange.getCommitter(), new Date(lastChange.getTimestamp()));

            // Create and show comparison dialog
            new CodeTemplateDiffDialog(parent, currentVersion, historicalVersion, left, right.getRawContent()).setVisible(true);
        } catch (Exception e) {
            logger.error("Failed to show code template comparison", e);
            showError("Cannot compare versions: " + e.getMessage());
        }
    }

    private void showDiffWindow() {
        int[] rows = tblCommitMetaData.getSelectedRows();

        // Validate selection
        if (rows.length != 2) {
            showError("Please select exactly 2 versions to compare");
            return;
        }

        CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
        CommitMetaData ri1 = model.getCommitMetaDataAt(rows[0]);
        CommitMetaData ri2 = model.getCommitMetaDataAt(rows[1]);

        if (ri1 == null || ri2 == null) {
            showError("Invalid version selection");
            return;
        }

        try {
            // Load versions
            CodeTemplateWithRaw left = VersionHistoryServiceClient.getInstance().loadCodeTemplateWithRawFromRepo(codeTemplateId, ri1.getHash());
            CodeTemplateWithRaw right = VersionHistoryServiceClient.getInstance().loadCodeTemplateWithRawFromRepo(codeTemplateId, ri2.getHash());

            CodeTemplate leftCodeTemplate = left.getCodeTemplate();
            CodeTemplate rightCodeTemplate = right.getCodeTemplate();

            // Build VersionInfo for left side
            VersionInfo leftVersion = VersionInfo.createHistorical(leftCodeTemplate.getName(), ri1.getHash().substring(0, 7), ri1.getCommitter(), new Date(ri1.getTimestamp()));

            // Build VersionInfo for right side
            VersionInfo rightVersion = VersionInfo.createHistorical(rightCodeTemplate.getName(), ri2.getHash().substring(0, 7), ri2.getCommitter(), new Date(ri2.getTimestamp()));

            // Create and show comparison dialog
            new CodeTemplateDiffDialog(parent, leftVersion, rightVersion, left.getRawContent(), right.getRawContent()).setVisible(true);
        } catch (Exception e) {
            logger.error("Failed to show code template comparison", e);
            showError("Cannot compare versions: " + e.getMessage());
        }
    }

    private void revert(String codeTemplateId, String rev) {
        int option = JOptionPane.showConfirmDialog(this, "Would you like to revert code template to this revision?", "Select an Option", JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() throws Exception {
                    CodeTemplate codeTemplate = VersionHistoryServiceClient.getInstance().loadCodeTemplateFromRepo(codeTemplateId, rev);
                    return parent.mirthClient.updateCodeTemplate(codeTemplate, true);
                }

                @Override
                protected void done() {
                    setCursor(Cursor.getDefaultCursor());
                    try {
                        if (get()) {
                            parent.codeTemplatePanel.doRefreshCodeTemplates();
                            showInformation("Code template reverted successfully.\n\nNote: Changes have been saved to Mirth Connect automatically.");
                        }
                    } catch (ExecutionException e) {
                        logger.error("Failed to revert code template", e);
                        showError("Failed to revert code template: " + e.getCause().getMessage());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }.execute();
        }
    }

    private void commitThenPush() {
        VersionHistoryServiceClient client = VersionHistoryServiceClient.getInstance();

        new CommitMessageDialog(parent, message -> {
            String userId = String.valueOf(parent.mirthClient.getCurrentUser().getId());
            client.commitAndPushCodeTemplate(codeTemplateId, message, userId, false);
        }, () -> loadHistory(false), backedUpContent -> {
            new ConflictResolutionDialog(parent, client, backedUpContent, () -> loadHistory(false)).setVisible(true);
        }).setVisible(true);
    }

    /**
     * SwingWorker to load code template commit history in background
     */
    private class LoadGitHistoryWorker extends SwingWorker<List<CommitMetaData>, Void> {
        private final boolean shouldNotifyOnComplete;

        LoadGitHistoryWorker(boolean shouldNotifyOnComplete) {
            this.shouldNotifyOnComplete = shouldNotifyOnComplete;
        }

        @Override
        protected List<CommitMetaData> doInBackground() throws Exception {
            logger.debug("Loading history for code template: {}", codeTemplateId);
            int limit = (Integer) commitLimitCombo.getSelectedItem();
            return VersionHistoryServiceClient.getInstance().loadCodeTemplateHistory(codeTemplateId, limit);
        }

        @Override
        protected void done() {
            try {
                List<CommitMetaData> revisions = get();
                logger.debug("Loaded {} revisions for code template", revisions.size());

                // Update table model
                CommitMetaDataTableModel model = new CommitMetaDataTableModel(revisions);
                tblCommitMetaData.setModel(model);

                // Update action states
                updateActionStates();

                // Show success notification if requested
                if (shouldNotifyOnComplete) {
                    showInformation("History refreshed!");
                }

            } catch (ExecutionException e) {
                logger.error("Failed to load code template history", e);

                // Set empty model on error
                tblCommitMetaData.setModel(new CommitMetaDataTableModel(new ArrayList<>()));

                if (shouldNotifyOnComplete) {
                    // Extract error message
                    Throwable cause = e.getCause();
                    String errorMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "Failed to pull code template history from repository";

                    showError(errorMsg);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Code template history loading was interrupted");
            }
        }
    }

    private void showInformation(String msg) {
        PlatformUI.MIRTH_FRAME.alertInformation(this, msg);
    }

    private void showError(String msg) {
        PlatformUI.MIRTH_FRAME.alertError(this, msg);
    }
}
