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
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.innovarhealthcare.channelHistory.client.diff.GlobalScriptsDiffDialog;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;
import com.innovarhealthcare.channelHistory.client.model.CommitMetaDataTableModel;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.client.table.CommitMetaDataTable;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import net.miginfocom.swing.MigLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdesktop.swingx.JXTaskPane;
import org.jdesktop.swingx.JXTaskPaneContainer;

/**
 * History dialog for Global Scripts using JXTaskPane for actions menu (Mirth Connect style)
 * Provides diff, commit/push, and history functionality for global scripts version control
 */
public class GlobalScriptsHistoryDialog extends JDialog {
    private static Logger logger = LogManager.getLogger(GlobalScriptsHistoryDialog.class);

    private JXTaskPaneContainer taskPaneContainer;
    private JPanel historyPanel;

    // Actions (only 2: Diff and Revert)
    private Action differenceAction;
    private Action revertAction;

    private CommitMetaDataTable tblCommitMetaData;
    private JScrollPane historyScrollPane;
    private JComboBox<Integer> commitLimitCombo;

    private JPopupMenu popupMenu;

    private final Frame parent = PlatformUI.MIRTH_FRAME;

    public GlobalScriptsHistoryDialog(Window parent) {
        super(parent);

        initComponents();
        initLayout();

        load();

        pack();
        setModal(true);
        setVisible(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
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

        // Create actions: Diff and Revert only
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

        revertAction = new AbstractAction("Revert") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = tblCommitMetaData.getSelectedRow();
                if (row >= 0) {
                    CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
                    CommitMetaData meta = model.getCommitMetaDataAt(row);
                    revert(meta.getHash());
                }
            }
        };
        revertAction.setEnabled(false);
        revertAction.putValue(Action.SHORT_DESCRIPTION, "Revert global scripts to selected revision");
        revertAction.putValue(Action.SMALL_ICON, new ImageIcon(Frame.class.getResource("images/arrow_undo.png")));

        // Add actions to task pane
        actionsPane.add(differenceAction);
        actionsPane.add(revertAction);

        // Add task pane to container
        taskPaneContainer.add(actionsPane);

        // Create popup menu with same actions (MC pattern)
        // This popup will show on right-click in the history table
        popupMenu = new JPopupMenu();
        popupMenu.add(new JMenuItem(differenceAction));
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

        setTitle("Global Scripts History");
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
        // Auto-load history when dialog opens
        this.loadHistory(false);
    }

    public void loadHistory(boolean shouldNotifyOnComplete) {
        new LoadGitHistoryWorker(shouldNotifyOnComplete).execute();
    }

    private void showDiffLastChangeWindow() {
        CommitMetaDataTableModel model = (CommitMetaDataTableModel) tblCommitMetaData.getModel();
        CommitMetaData lastChange = model.getCommitMetaDataAt(tblCommitMetaData.getSelectedRow());

        if (lastChange == null) {
            showError("No global scripts revision selected");
            return;
        }

        try {
            //@formatter:off
            Client client = parent.mirthClient;
            String currentUserName = client.getCurrentUser().getUsername();

            // Load current global scripts
            Map<String, String> currentScripts = parent.globalScriptsPanel.exportAllScripts();

            // Load historical global scripts
            Map<String, String> historicalScripts = VersionHistoryServiceClient.getInstance().loadGlobalScriptsFromRepo(lastChange.getHash());

            // Create version info
            VersionInfo currentVersion = VersionInfo.createCurrent(
                    "Global Scripts",
                    currentUserName
            );

            VersionInfo historicalVersion = VersionInfo.createHistorical(
                    "Global Scripts",
                    lastChange.getHash().substring(0, 7),
                    lastChange.getCommitter(),
                    new Date(lastChange.getTimestamp())
            );

            // Show comparison dialog
            GlobalScriptsDiffDialog dialog = new GlobalScriptsDiffDialog(
                    parent,
                    "Compare Global Scripts",
                    historicalScripts,    // scripts1
                    currentScripts,       // scripts2
                    historicalVersion,    // version1
                    currentVersion        // version2 (will auto-sort to right)
            );
            dialog.setVisible(true);
            //@formatter:on
        } catch (Exception e) {
            logger.error("Failed to show global scripts comparison", e);
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
            //@formatter:off
            VersionHistoryServiceClient service = VersionHistoryServiceClient.getInstance();
            // Load both versions
            Map<String, String> scripts1 = service.loadGlobalScriptsFromRepo(ri1.getHash());
            Map<String, String> scripts2 = service.loadGlobalScriptsFromRepo(ri2.getHash());

            // Create version info
            VersionInfo version1 = VersionInfo.createHistorical(
                    "Global Scripts",
                    ri1.getHash().substring(0, 7),
                    ri1.getCommitter(),
                    new Date(ri1.getTimestamp())
            );
            VersionInfo version2 = VersionInfo.createHistorical(
                    "Global Scripts",
                    ri2.getHash().substring(0, 7),
                    ri2.getCommitter(),
                    new Date(ri2.getTimestamp())
            );

            // Dialog auto-sorts by timestamp
            GlobalScriptsDiffDialog dialog = new GlobalScriptsDiffDialog(
                    parent,
                    "Compare Global Scripts",
                    scripts1, scripts2,    // ← Neutral naming
                    version1, version2     // ← Dialog decides which is left/right
            );
            dialog.setVisible(true);
            //@formatter:on
        } catch (Exception e) {
            logger.error("Failed to show global scripts comparison", e);
            showError("Cannot compare versions: " + e.getMessage());
        }
    }

    private void revert(String commitHash) {
        int option = JOptionPane.showConfirmDialog(this, "Would you like to revert global scripts to this revision?", "Select an Option", JOptionPane.YES_NO_OPTION);

        if (option != JOptionPane.YES_OPTION) {
            return;
        }

        setCursor(Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

        new SwingWorker<Map<String, String>, Void>() {
            @Override
            protected Map<String, String> doInBackground() throws Exception {
                return VersionHistoryServiceClient.getInstance().loadGlobalScriptsFromRepo(commitHash);
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    Map<String, String> scriptsToRevert = get();
                    parent.globalScriptsPanel.importAllScripts(scriptsToRevert);
                    dispose();
                    showInformation("Global scripts reverted to selected version.\n\nNext steps:\n1. Save in the Global Scripts panel to apply changes\n2. Use Commit & Push to save this revert to the repository");
                } catch (ExecutionException e) {
                    logger.error("Error during global scripts revert", e);
                    showError("Failed to revert global scripts: " + e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }


    /**
     * SwingWorker to load global scripts commit history in background
     */
    private class LoadGitHistoryWorker extends SwingWorker<List<CommitMetaData>, Void> {
        private final boolean shouldNotifyOnComplete;

        LoadGitHistoryWorker(boolean shouldNotifyOnComplete) {
            this.shouldNotifyOnComplete = shouldNotifyOnComplete;
        }

        @Override
        protected List<CommitMetaData> doInBackground() throws Exception {
            logger.debug("Loading history for global scripts");
            int limit = (Integer) commitLimitCombo.getSelectedItem();
            return VersionHistoryServiceClient.getInstance().loadGlobalScriptsHistory(limit);
        }

        @Override
        protected void done() {
            try {
                List<CommitMetaData> revisions = get();
                logger.debug("Loaded {} revisions for global scripts", revisions.size());

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
                logger.error("Failed to load global scripts history", e);

                // Set empty model on error
                tblCommitMetaData.setModel(new CommitMetaDataTableModel(new ArrayList<>()));

                if (shouldNotifyOnComplete) {
                    // Extract error message
                    Throwable cause = e.getCause();
                    String errorMsg = cause != null && cause.getMessage() != null ? cause.getMessage() : "Failed to pull global scripts history from repository";

                    showError(errorMsg);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Global scripts history loading was interrupted");
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
