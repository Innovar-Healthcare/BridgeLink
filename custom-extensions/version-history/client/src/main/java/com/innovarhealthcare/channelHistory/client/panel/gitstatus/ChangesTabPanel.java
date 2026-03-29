/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.panel.gitstatus;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.innovarhealthcare.channelHistory.client.dialog.CommitMessageDialog;
import com.innovarhealthcare.channelHistory.client.dialog.ConflictResolutionDialog;
import com.innovarhealthcare.channelHistory.client.diff.DiffComparisonPanel;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoChanges;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import net.miginfocom.swing.MigLayout;

/**
 * Left-panel tab for the Changes (working-tree diff) view in the Git Status tab.
 * Owns the changes JTree (checkbox-based), the CHANGES_DIFF / EMPTY right cards,
 * and the [Select All] / [Deselect All] / [Commit & Push] action bar.
 *
 * @author Thai Tran
 */
public class ChangesTabPanel extends JPanel {

    private static final String CARD_EMPTY = "EMPTY";
    private static final String CARD_CHANGES_DIFF = "CHANGES_DIFF";

    private final VersionHistoryServiceClient client;

    // ── Changes tree ───────────────────────────────────────────────────────────
    private JTree changesTree;
    private JScrollPane changesTreeScrollPane;
    private JPanel treeAreaCards;
    private CardLayout treeAreaCardLayout;
    private JLabel noChangesLabel;

    /**
     * Paths (without [M]/[D]/[U] prefix) of currently checked leaf nodes.
     */
    private final Set<String> checkedPaths = new HashSet<>();

    // ── Right cards ────────────────────────────────────────────────────────────
    private JPanel rightCards;
    private CardLayout rightCardLayout;
    private JLabel emptyLabel;

    // CHANGES_DIFF card fields
    private JLabel changesDiffTitleLabel;
    private DiffComparisonPanel changesDiffPanel;

    // ── Action bar ─────────────────────────────────────────────────────────────
    private JButton selectAllButton;
    private JButton deselectAllButton;
    private JButton commitButton;

    // ── Split pane ─────────────────────────────────────────────────────────────
    private JSplitPane splitPane;

    public ChangesTabPanel(VersionHistoryServiceClient client) {
        this.client = client;
        initComponents();
        initLayout();
        initListeners();
    }

    // ========== Public API ==========

    /**
     * Called by the shell's JTabbedPane ChangeListener when this tab becomes active.
     * Re-fires any existing selection; shows EMPTY card if nothing is selected.
     */
    public void onTabSelected() {
        TreePath path = changesTree.getSelectionPath();
        if (path != null) {
            changesTree.clearSelection();
            changesTree.setSelectionPath(path);
        } else {
            emptyLabel.setText("Select a file from the changes list to view a diff of your local modifications");
            showCard(CARD_EMPTY);
        }
    }

    /**
     * Populates the changes tree from the loaded RepoChanges. Shows the EMPTY card.
     * All leaf nodes start checked by default. Called by the shell after LoadDataWorker
     * completes, and by the post-commit reload after a successful push.
     */
    public void populate(RepoChanges changes) {
        buildChangesTree(changes);
        emptyLabel.setText("Select a file from the changes list to view a diff of your local modifications");
        showCard(CARD_EMPTY);
    }

    /**
     * Resets to empty/loading state. Called before a new data load begins.
     */
    public void clear() {
        checkedPaths.clear();
        changesTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
        emptyLabel.setText("Select a file from the changes list to view a diff of your local modifications");
        showCard(CARD_EMPTY);
        updateCommitButton();
        updateSelectionButtons(false);
    }

    // ========== Initialization ==========

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        changesTree = new JTree(new DefaultMutableTreeNode());
        changesTree.setRootVisible(false);
        changesTree.setShowsRootHandles(true);
        changesTree.setCellRenderer(new CheckBoxNodeRenderer());
        changesTreeScrollPane = new JScrollPane(changesTree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        noChangesLabel = new JLabel("No changes in working tree", JLabel.CENTER);
        noChangesLabel.setForeground(new Color(150, 150, 150));
        noChangesLabel.setFont(new Font("Tahoma", Font.ITALIC, 12));

        rightCardLayout = new CardLayout();
        rightCards = new JPanel(rightCardLayout);
        rightCards.setBackground(UIConstants.BACKGROUND_COLOR);

        emptyLabel = new JLabel("Select a file from the changes list to view a diff of your local modifications", JLabel.CENTER);
        emptyLabel.setForeground(new Color(150, 150, 150));
        emptyLabel.setFont(new Font("Tahoma", Font.ITALIC, 12));
        rightCards.add(emptyLabel, CARD_EMPTY);

        rightCards.add(buildChangesDiffCard(), CARD_CHANGES_DIFF);

        selectAllButton = new JButton("Select All");
        selectAllButton.setEnabled(false);
        deselectAllButton = new JButton("Deselect All");
        deselectAllButton.setEnabled(false);
        commitButton = new JButton("Commit & Push");
        commitButton.setEnabled(false);
    }

    private JPanel buildChangesDiffCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(UIConstants.BACKGROUND_COLOR);

        changesDiffTitleLabel = new JLabel(" ");
        changesDiffTitleLabel.setFont(new Font("Tahoma", Font.BOLD, 11));
        changesDiffTitleLabel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(204, 204, 204)), BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        VersionInfo leftVi = VersionInfo.builder().name("").version("HEAD").isCurrent(false).build();
        VersionInfo rightVi = VersionInfo.builder().name("").version("Current").isCurrent(true).build();
        changesDiffPanel = new DiffComparisonPanel(leftVi, rightVi);

        card.add(changesDiffTitleLabel, BorderLayout.NORTH);
        card.add(changesDiffPanel, BorderLayout.CENTER);
        return card;
    }

    private void initLayout() {
        setLayout(new BorderLayout());

        JPanel actionBar = new JPanel(new MigLayout("insets 4 8 4 8, novisualpadding", "[][][grow][]"));
        actionBar.setBackground(UIConstants.BACKGROUND_COLOR);
        actionBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)));
        actionBar.add(selectAllButton);
        actionBar.add(deselectAllButton);
        actionBar.add(new JLabel(), "growx");
        actionBar.add(commitButton, "w 120!");

        treeAreaCardLayout = new CardLayout();
        treeAreaCards = new JPanel(treeAreaCardLayout);
        treeAreaCards.add(changesTreeScrollPane, "TREE");
        treeAreaCards.add(noChangesLabel, "NO_CHANGES");

        JPanel leftPanel = new JPanel(new MigLayout("insets 0, novisualpadding, fill", "[grow,fill]", "[grow,fill][]"));
        leftPanel.add(treeAreaCards, "grow, push, wrap");
        leftPanel.add(actionBar, "growx, wrap");

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightCards);
        splitPane.setResizeWeight(0.3);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                splitPane.setDividerLocation(0.3);
                splitPane.removeComponentListener(this);
            }
        });

        add(splitPane, BorderLayout.CENTER);
    }

    private void initListeners() {
        // Selection listener — loads inline diff for the selected file
        changesTree.addTreeSelectionListener(e -> {
            TreePath path = changesTree.getSelectionPath();
            if (path == null) {
                emptyLabel.setText("Select a file from the changes list to view a diff of your local modifications");
                showCard(CARD_EMPTY);
                return;
            }
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (!node.isLeaf()) {
                emptyLabel.setText("Select a file from the changes list to view a diff of your local modifications");
                showCard(CARD_EMPTY);
                return;
            }
            String text = String.valueOf(node.getUserObject());
            if (text.startsWith("[M] ")) {
                onChangedFileSelected(text.substring(4), "Modified");
            } else if (text.startsWith("[D] ")) {
                onDeletedFileSelected(text.substring(4), "Deleted");
            } else if (text.startsWith("[U] ")) {
                onUntrackedFileSelected(text.substring(4), "Unversioned");
            }
        });

        // Mouse listener — toggles checkbox state on any click on a leaf node
        changesTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = changesTree.getRowForLocation(e.getX(), e.getY());
                if (row < 0) {
                    return;
                }
                TreePath path = changesTree.getPathForRow(row);
                if (path == null) {
                    return;
                }
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (!node.isLeaf()) {
                    return;
                }
                String text = String.valueOf(node.getUserObject());
                String filePath = text.length() > 4 ? text.substring(4) : text;
                if (checkedPaths.contains(filePath)) {
                    checkedPaths.remove(filePath);
                } else {
                    checkedPaths.add(filePath);
                }
                changesTree.repaint();
                updateCommitButton();
            }
        });

        selectAllButton.addActionListener(e -> selectAll());
        deselectAllButton.addActionListener(e -> deselectAll());
        commitButton.addActionListener(e -> onCommitAndPush());
    }

    // ========== Selection Handlers ==========

    private void onChangedFileSelected(String filePath, String changeTypeLabel) {
        changesDiffTitleLabel.setText("  " + getFileName(filePath) + "  [" + changeTypeLabel + "]");
        showCard(CARD_CHANGES_DIFF);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                return new String[]{client.getFileContentAtHead(filePath), client.getFileContent(filePath)};
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String[] r = get();
                    changesDiffPanel.updateDiff(r[0], r[1]);
                } catch (Exception ex) {
                    changesDiffPanel.updateDiff("Error: " + ex.getMessage(), "");
                }
            }
        }.execute();
    }

    private void onDeletedFileSelected(String filePath, String changeTypeLabel) {
        changesDiffTitleLabel.setText("  " + getFileName(filePath) + "  [" + changeTypeLabel + "]");
        showCard(CARD_CHANGES_DIFF);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return client.getFileContentAtHead(filePath);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    changesDiffPanel.updateDiff(get(), "");
                } catch (Exception ex) {
                    changesDiffPanel.updateDiff("Error: " + ex.getMessage(), "");
                }
            }
        }.execute();
    }

    private void onUntrackedFileSelected(String filePath, String changeTypeLabel) {
        changesDiffTitleLabel.setText("  " + getFileName(filePath) + "  [" + changeTypeLabel + "]");
        showCard(CARD_CHANGES_DIFF);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return client.getFileContent(filePath);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    changesDiffPanel.updateDiff("", get());
                } catch (Exception ex) {
                    changesDiffPanel.updateDiff("", "Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ========== Commit Action ==========

    private void onCommitAndPush() {
        List<String> selectedPaths = getCheckedFilePaths();
        if (selectedPaths.isEmpty()) {
            return;
        }

        String userId;
        try {
            userId = String.valueOf(PlatformUI.MIRTH_FRAME.mirthClient.getCurrentUser().getId());
        } catch (ClientException e) {
            PlatformUI.MIRTH_FRAME.alertError(this, "Could not get current user: " + e.getMessage());
            return;
        }

        Frame parent = PlatformUI.MIRTH_FRAME;

        // Called after a successful commit+push — reload the changes list
        Runnable onSuccess = () -> {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new SwingWorker<RepoChanges, Void>() {
                @Override
                protected RepoChanges doInBackground() throws Exception {
                    return client.getRepoChanges();
                }

                @Override
                protected void done() {
                    setCursor(Cursor.getDefaultCursor());
                    try {
                        populate(get());
                        PlatformUI.MIRTH_FRAME.alertInformation(ChangesTabPanel.this, "Files committed and pushed successfully.");
                    } catch (Exception ex) {
                        PlatformUI.MIRTH_FRAME.alertError(ChangesTabPanel.this, "Committed but failed to reload: " + ex.getMessage());
                    }
                }
            }.execute();
        };

        // Called when the server detects a rebase conflict — let the user restore their changes
        java.util.function.Consumer<Map<String, String>> onConflict = backedUpContent -> new ConflictResolutionDialog(parent, client, backedUpContent, () -> {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new SwingWorker<RepoChanges, Void>() {
                @Override
                protected RepoChanges doInBackground() throws Exception {
                    return client.getRepoChanges();
                }

                @Override
                protected void done() {
                    setCursor(Cursor.getDefaultCursor());
                    try {
                        populate(get());
                    } catch (Exception ex) {
                        PlatformUI.MIRTH_FRAME.alertError(ChangesTabPanel.this, "Failed to reload: " + ex.getMessage());
                    }
                }
            }.execute();
        }).setVisible(true);

        CommitMessageDialog dialog = new CommitMessageDialog(parent, client, selectedPaths, userId, onSuccess, onConflict);
        dialog.setVisible(true);
    }

    // ========== Checkbox Helpers ==========

    private void selectAll() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) changesTree.getModel().getRoot();
        Enumeration<?> e = root.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
            if (node.isLeaf()) {
                String text = String.valueOf(node.getUserObject());
                String filePath = text.length() > 4 ? text.substring(4) : text;
                checkedPaths.add(filePath);
            }
        }
        changesTree.repaint();
        updateCommitButton();
    }

    private void deselectAll() {
        checkedPaths.clear();
        changesTree.repaint();
        updateCommitButton();
    }

    private void updateCommitButton() {
        commitButton.setEnabled(!checkedPaths.isEmpty());
    }

    private void updateSelectionButtons(boolean hasChanges) {
        selectAllButton.setEnabled(hasChanges);
        deselectAllButton.setEnabled(hasChanges);
    }

    private List<String> getCheckedFilePaths() {
        List<String> result = new ArrayList<>();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) changesTree.getModel().getRoot();
        Enumeration<?> e = root.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
            if (node.isLeaf()) {
                String text = String.valueOf(node.getUserObject());
                String filePath = text.length() > 4 ? text.substring(4) : text;
                if (checkedPaths.contains(filePath)) {
                    result.add(filePath);
                }
            }
        }
        return result;
    }

    // ========== Tree Building ==========

    private void buildChangesTree(RepoChanges changes) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        checkedPaths.clear();

        List<String> modifiedFiles = changes.getModifiedFiles();
        List<String> deletedFiles = changes.getDeletedFiles();
        List<String> untrackedFiles = changes.getUntrackedFiles();

        int changedCount = (modifiedFiles != null ? modifiedFiles.size() : 0) + (deletedFiles != null ? deletedFiles.size() : 0);
        DefaultMutableTreeNode changedGroup = new DefaultMutableTreeNode("Changed (" + changedCount + ")");
        if (modifiedFiles != null) {
            for (String path : modifiedFiles) {
                changedGroup.add(new DefaultMutableTreeNode("[M] " + path));
                checkedPaths.add(path);
            }
        }
        if (deletedFiles != null) {
            for (String path : deletedFiles) {
                changedGroup.add(new DefaultMutableTreeNode("[D] " + path));
                checkedPaths.add(path);
            }
        }

        int untrackedCount = untrackedFiles != null ? untrackedFiles.size() : 0;
        DefaultMutableTreeNode untrackedGroup = new DefaultMutableTreeNode("Unversioned Files (" + untrackedCount + ")");
        if (untrackedFiles != null) {
            for (String path : untrackedFiles) {
                untrackedGroup.add(new DefaultMutableTreeNode("[U] " + path));
                checkedPaths.add(path);
            }
        }

        if (changedCount > 0) {
            root.add(changedGroup);
        }

        if (untrackedCount > 0) {
            root.add(untrackedGroup);
        }

        boolean hasChanges = root.getChildCount() > 0;

        changesTree.setModel(new DefaultTreeModel(root));
        expandAll(changesTree);
        updateCommitButton();
        updateSelectionButtons(hasChanges);

        if (hasChanges) {
            treeAreaCardLayout.show(treeAreaCards, "TREE");
        } else {
            treeAreaCardLayout.show(treeAreaCards, "NO_CHANGES");
        }
    }

    // ========== Helpers ==========

    private void showCard(String cardName) {
        rightCardLayout.show(rightCards, cardName);
    }

    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private static String getFileName(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    // ========== Inner Classes ==========

    /**
     * Renders leaf nodes with a JCheckBox + HTML-colored label (bypasses Substance LAF).
     * Folder nodes are rendered with bold text and no checkbox.
     */
    private final class CheckBoxNodeRenderer implements TreeCellRenderer {
        private static final String HEX_MODIFIED = "#CC6600";
        private static final String HEX_DELETED = "#CC0000";
        private static final String HEX_UNVERSIONED = "#009900";

        // Reusable components for leaf rendering
        private final JPanel leafPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        private final javax.swing.JCheckBox checkBox = new javax.swing.JCheckBox();
        private final JLabel leafLabel = new JLabel();

        // Reusable renderer for folder (group) nodes
        private final DefaultTreeCellRenderer folderRenderer = new DefaultTreeCellRenderer();

        CheckBoxNodeRenderer() {
            leafPanel.add(checkBox);
            leafPanel.add(leafLabel);
            leafPanel.setOpaque(true);
            checkBox.setOpaque(true);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            if (!(value instanceof DefaultMutableTreeNode)) {
                return folderRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            String text = String.valueOf(node.getUserObject());

            if (!node.isLeaf()) {
                // Group node — bold, no checkbox
                Component c = folderRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, false, row, hasFocus);
                ((JLabel) c).setFont(((JLabel) c).getFont().deriveFont(Font.BOLD));
                return c;
            }

            // Leaf node — checkbox + colored label
            String filePath = text.length() > 4 ? text.substring(4) : text;
            checkBox.setSelected(checkedPaths.contains(filePath));

            Color bg = selected ? UIManager.getColor("Tree.selectionBackground") : tree.getBackground();
            leafPanel.setBackground(bg);
            checkBox.setBackground(bg);

            if (selected) {
                leafLabel.setForeground(UIManager.getColor("Tree.selectionForeground"));
                leafLabel.setText(text.substring(0, 3) + " " + filePath);
            } else {
                String hex;
                if (text.startsWith("[M] ")) {
                    hex = HEX_MODIFIED;
                } else if (text.startsWith("[D] ")) {
                    hex = HEX_DELETED;
                } else {
                    hex = HEX_UNVERSIONED;
                }
                leafLabel.setText("<html><font color='" + hex + "'>" + text.substring(0, 3) + "</font> " + filePath + "</html>");
                leafLabel.setForeground(tree.getForeground());
            }

            return leafPanel;
        }
    }
}
