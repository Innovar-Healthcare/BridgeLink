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

import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoFile;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoFolder;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoInfo;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.util.CommitMessageUtil;
import com.mirth.connect.client.ui.UIConstants;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Left-panel tab for the Files browser in the Git Status view.
 * Owns the file browser JTree and the FILE_INFO / EMPTY right cards.
 *
 * @author Thai Tran
 */
public class FilesTabPanel extends JPanel {

    private static final String CARD_EMPTY       = "EMPTY";
    private static final String CARD_FILE_INFO   = "FILE_INFO";
    private static final String SEARCH_PLACEHOLDER = "Filter by file name...";

    private final VersionHistoryServiceClient client;
    private final Consumer<String>            onViewFullHistoryCallback;

    // ── Source of truth for filtering ─────────────────────────────────────────
    private RepoInfo currentRepoInfo;

    // ── Search bar ─────────────────────────────────────────────────────────────
    private JTextField       searchField;
    private JCheckBox        showAllCheckbox;
    private DocumentListener searchDocumentListener;

    // ── File tree ──────────────────────────────────────────────────────────────
    private JTree       fileTree;
    private JScrollPane fileTreeScrollPane;

    // ── Right cards ────────────────────────────────────────────────────────────
    private JPanel     rightCards;
    private CardLayout rightCardLayout;
    private JLabel     emptyLabel;

    // FILE_INFO card fields
    private JLabel  fileInfoNameValue;
    private JLabel  fileInfoPathValue;
    private JLabel  fileInfoSizeValue;
    private JPanel  fileInfoCommitSection;
    private JLabel  fileInfoCommitHashValue;
    private JLabel  fileInfoCommitAuthorValue;
    private JLabel  fileInfoCommitDateValue;
    private JLabel     fileInfoCommitMsgValue;
    private JSeparator fileInfoCommitSeparator;
    private JLabel     fileInfoCommitTypeLabel,   fileInfoCommitTypeValue;
    private JLabel     fileInfoCommitNameLabel,   fileInfoCommitNameValue;
    private JLabel     fileInfoCommitServerLabel, fileInfoCommitServerValue;
    private JPanel  fileInfoButtonSection;
    private JButton viewContentButton;
    private JButton viewFullHistoryButton;
    private String  currentFilePath;

    // ── Split pane ─────────────────────────────────────────────────────────────
    private JSplitPane splitPane;

    public FilesTabPanel(VersionHistoryServiceClient client,
                         Consumer<String> onViewFullHistoryCallback) {
        this.client                    = client;
        this.onViewFullHistoryCallback = onViewFullHistoryCallback;
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
        TreePath path = fileTree.getSelectionPath();
        if (path != null) {
            fileTree.clearSelection();
            fileTree.setSelectionPath(path);
        } else {
            emptyLabel.setText("Select a file from the tree to view its details and commit history");
            showCard(CARD_EMPTY);
        }
    }

    /**
     * Populates the file tree from the loaded RepoInfo. Shows the EMPTY card.
     * Called by the shell after LoadDataWorker completes.
     */
    public void populate(RepoInfo info) {
        currentRepoInfo = info;
        currentFilePath = null;
        emptyLabel.setText("Select a file from the tree to view its details and commit history");
        showCard(CARD_EMPTY);
        applyFilter(getSearchText());
    }

    /**
     * Resets to empty/loading state. Called before a new data load begins.
     */
    public void clear() {
        currentRepoInfo = null;
        fileTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
        currentFilePath = null;
        emptyLabel.setText("Select a file from the tree to view its details and commit history");
        showCard(CARD_EMPTY);
        // Reset search field without triggering intermediate filter calls
        if (searchDocumentListener != null) {
            searchField.getDocument().removeDocumentListener(searchDocumentListener);
        }
        searchField.setText(SEARCH_PLACEHOLDER);
        searchField.setForeground(Color.GRAY);
        if (searchDocumentListener != null) {
            searchField.getDocument().addDocumentListener(searchDocumentListener);
        }
    }

    // ========== Initialization ==========

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        // Search field (placeholder handled by FocusListener)
        searchField = new JTextField();
        searchField.setText(SEARCH_PLACEHOLDER);
        searchField.setForeground(Color.GRAY);
        searchField.setToolTipText("Filter by file name (matches full path, case-insensitive)");

        showAllCheckbox = new JCheckBox("Show all");
        showAllCheckbox.setBackground(UIConstants.BACKGROUND_COLOR);
        showAllCheckbox.setToolTipText("Show all files in the repository, including non-Mirth folders");

        fileTree = new JTree(new DefaultMutableTreeNode());
        fileTree.setRootVisible(false);
        fileTree.setShowsRootHandles(true);
        fileTreeScrollPane = new JScrollPane(fileTree,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        rightCardLayout = new CardLayout();
        rightCards = new JPanel(rightCardLayout);
        rightCards.setBackground(UIConstants.BACKGROUND_COLOR);

        emptyLabel = new JLabel(
                "Select a file from the tree to view its details and commit history",
                JLabel.CENTER);
        emptyLabel.setForeground(new Color(150, 150, 150));
        emptyLabel.setFont(new Font("Tahoma", Font.ITALIC, 12));
        rightCards.add(emptyLabel, CARD_EMPTY);

        rightCards.add(buildFileInfoCard(), CARD_FILE_INFO);
    }

    private JPanel buildFileInfoCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(UIConstants.BACKGROUND_COLOR);

        JPanel form = new JPanel(
                new MigLayout("insets 8, novisualpadding, hidemode 3", "[right]12[grow,fill]"));
        form.setBackground(UIConstants.BACKGROUND_COLOR);

        fileInfoNameValue = new JLabel("—");
        fileInfoPathValue = new JLabel("—");
        fileInfoSizeValue = new JLabel("—");

        form.add(new JLabel("Name:"));
        form.add(fileInfoNameValue, "growx, wrap");
        form.add(new JLabel("Path:"));
        form.add(fileInfoPathValue, "growx, wrap");
        form.add(new JLabel("Size:"));
        form.add(fileInfoSizeValue, "growx, wrap");

        // Last commit section
        fileInfoCommitSection = new JPanel(
                new MigLayout("insets 8 0 0 0, novisualpadding, hidemode 3", "[right]12[grow,fill]"));
        fileInfoCommitSection.setBackground(UIConstants.BACKGROUND_COLOR);

        JLabel sectionTitle = new JLabel("Last Commit");
        sectionTitle.setFont(new Font("Tahoma", Font.BOLD, 11));
        fileInfoCommitSection.add(sectionTitle, "span 2, wrap");

        fileInfoCommitHashValue   = new JLabel("—");
        fileInfoCommitAuthorValue = new JLabel("—");
        fileInfoCommitDateValue   = new JLabel("—");
        fileInfoCommitMsgValue    = new JLabel("—");

        fileInfoCommitSeparator   = new JSeparator();
        fileInfoCommitTypeLabel   = new JLabel("Type:");
        fileInfoCommitTypeValue   = new JLabel("—");
        fileInfoCommitNameLabel   = new JLabel("Name:");
        fileInfoCommitNameValue   = new JLabel("—");
        fileInfoCommitServerLabel = new JLabel("Server:");
        fileInfoCommitServerValue = new JLabel("—");

        fileInfoCommitSeparator.setVisible(false);
        fileInfoCommitTypeLabel.setVisible(false);
        fileInfoCommitTypeValue.setVisible(false);
        fileInfoCommitNameLabel.setVisible(false);
        fileInfoCommitNameValue.setVisible(false);
        fileInfoCommitServerLabel.setVisible(false);
        fileInfoCommitServerValue.setVisible(false);

        fileInfoCommitSection.add(new JLabel("Hash:"));
        fileInfoCommitSection.add(fileInfoCommitHashValue,   "growx, wrap");
        fileInfoCommitSection.add(new JLabel("Author:"));
        fileInfoCommitSection.add(fileInfoCommitAuthorValue, "growx, wrap");
        fileInfoCommitSection.add(new JLabel("Date:"));
        fileInfoCommitSection.add(fileInfoCommitDateValue,   "growx, wrap");
        fileInfoCommitSection.add(new JLabel("Message:"));
        fileInfoCommitSection.add(fileInfoCommitMsgValue,    "growx, wrap");
        fileInfoCommitSection.add(fileInfoCommitSeparator,   "span 2, growx, wrap");
        fileInfoCommitSection.add(fileInfoCommitTypeLabel);
        fileInfoCommitSection.add(fileInfoCommitTypeValue,   "growx, wrap");
        fileInfoCommitSection.add(fileInfoCommitNameLabel);
        fileInfoCommitSection.add(fileInfoCommitNameValue,   "growx, wrap");
        fileInfoCommitSection.add(fileInfoCommitServerLabel);
        fileInfoCommitSection.add(fileInfoCommitServerValue, "growx, wrap");

        form.add(fileInfoCommitSection, "span 2, growx, hidemode 3, wrap");

        // Buttons
        fileInfoButtonSection = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        fileInfoButtonSection.setBackground(UIConstants.BACKGROUND_COLOR);
        viewContentButton     = new JButton("View Content");
        viewFullHistoryButton = new JButton("View Full History");
        fileInfoButtonSection.add(viewContentButton);
        fileInfoButtonSection.add(viewFullHistoryButton);

        form.add(fileInfoButtonSection, "span 2, growx, hidemode 3, wrap");

        card.add(form, BorderLayout.NORTH);
        return card;
    }

    private void initLayout() {
        setLayout(new BorderLayout());

        // Search bar row: [searchField] [Show all]
        JPanel searchBarPanel = new JPanel(new MigLayout("insets 4 4 2 4, novisualpadding", "[grow,fill][]"));
        searchBarPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        searchBarPanel.add(searchField, "growx");
        searchBarPanel.add(showAllCheckbox);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        leftPanel.add(searchBarPanel, BorderLayout.NORTH);
        leftPanel.add(fileTreeScrollPane, BorderLayout.CENTER);

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
        // Search field placeholder handling
        searchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (SEARCH_PLACEHOLDER.equals(searchField.getText())) {
                    searchField.getDocument().removeDocumentListener(searchDocumentListener);
                    searchField.setText("");
                    searchField.setForeground(Color.BLACK);
                    searchField.getDocument().addDocumentListener(searchDocumentListener);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (searchField.getText().isEmpty()) {
                    searchField.getDocument().removeDocumentListener(searchDocumentListener);
                    searchField.setText(SEARCH_PLACEHOLDER);
                    searchField.setForeground(Color.GRAY);
                    searchField.getDocument().addDocumentListener(searchDocumentListener);
                }
            }
        });

        // Real-time filter on keystroke
        searchDocumentListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onSearchChanged(); }
            @Override public void removeUpdate(DocumentEvent e)  { onSearchChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onSearchChanged(); }
        };
        searchField.getDocument().addDocumentListener(searchDocumentListener);

        showAllCheckbox.addActionListener(e -> onSearchChanged());

        fileTree.addTreeSelectionListener(e -> {
            TreePath path = fileTree.getSelectionPath();
            if (path == null) {
                emptyLabel.setText(
                        "Select a file from the tree to view its details and commit history");
                showCard(CARD_EMPTY);
                return;
            }
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object obj = node.getUserObject();
            if (obj instanceof FileNode) {
                onFileSelected((FileNode) obj);
            } else {
                onFolderSelected(String.valueOf(obj));
            }
        });

        viewContentButton.addActionListener(e -> {
            if (currentFilePath != null) openFileContentView(currentFilePath);
        });
        viewFullHistoryButton.addActionListener(e -> {
            if (currentFilePath != null && onViewFullHistoryCallback != null) {
                onViewFullHistoryCallback.accept(currentFilePath);
            }
        });
    }

    // ========== Selection Handlers ==========

    private void onFileSelected(FileNode fileNode) {
        currentFilePath = fileNode.relativePath;

        fileInfoNameValue.setText(getFileName(fileNode.relativePath));
        fileInfoPathValue.setText(fileNode.relativePath);
        fileInfoSizeValue.setText(fileNode.sizeBytes >= 0 ? formatBytes(fileNode.sizeBytes) : "—");

        fileInfoCommitSection.setVisible(true);
        fileInfoButtonSection.setVisible(true);
        fileInfoCommitHashValue.setText("Loading…");
        fileInfoCommitAuthorValue.setText("—");
        fileInfoCommitDateValue.setText("—");
        fileInfoCommitMsgValue.setText("—");
        showCard(CARD_FILE_INFO);

        String id   = extractId(fileNode.relativePath);
        String mode = detectMode(fileNode.relativePath);
        int limit = 0;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<CommitMetaData>, Void>() {
            @Override protected List<CommitMetaData> doInBackground() throws Exception {
                return client.getHistory(id, mode, limit);
            }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    List<CommitMetaData> history = get();
                    if (history != null && !history.isEmpty()) {
                        CommitMetaData last = history.get(0);
                        fileInfoCommitHashValue.setText(last.getShortHash());
                        fileInfoCommitAuthorValue.setText(last.getCommitter());
                        fileInfoCommitDateValue.setText(formatTimestamp(last.getTimestamp()));
                        fileInfoCommitMsgValue.setText(trimMessage(last.getMessage()));

                        String raw = last.getMessage();
                        boolean hasMeta = CommitMessageUtil.isValidFormat(raw);
                        fileInfoCommitSeparator.setVisible(hasMeta);
                        fileInfoCommitTypeLabel.setVisible(hasMeta);
                        fileInfoCommitTypeValue.setVisible(hasMeta);
                        fileInfoCommitNameLabel.setVisible(hasMeta);
                        fileInfoCommitNameValue.setVisible(hasMeta);
                        fileInfoCommitServerLabel.setVisible(hasMeta);
                        fileInfoCommitServerValue.setVisible(hasMeta);
                        if (hasMeta) {
                            fileInfoCommitTypeValue.setText(CommitMessageUtil.extractType(raw));
                            fileInfoCommitNameValue.setText(CommitMessageUtil.extractName(raw));
                            String serverName = CommitMessageUtil.extractServerName(raw);
                            String serverId   = CommitMessageUtil.extractServerId(raw);
                            fileInfoCommitServerValue.setText(
                                    serverName != null ? serverName + " (" + serverId + ")" : serverId);
                        }
                    } else {
                        fileInfoCommitHashValue.setText("No commits");
                        fileInfoCommitAuthorValue.setText("—");
                        fileInfoCommitDateValue.setText("—");
                        fileInfoCommitMsgValue.setText("—");
                        fileInfoCommitSeparator.setVisible(false);
                        fileInfoCommitTypeLabel.setVisible(false);
                        fileInfoCommitTypeValue.setVisible(false);
                        fileInfoCommitNameLabel.setVisible(false);
                        fileInfoCommitNameValue.setVisible(false);
                        fileInfoCommitServerLabel.setVisible(false);
                        fileInfoCommitServerValue.setVisible(false);
                    }
                } catch (Exception ex) {
                    fileInfoCommitHashValue.setText("Error");
                }
            }
        }.execute();
    }

    private void onFolderSelected(String folderName) {
        currentFilePath = null;
        fileInfoNameValue.setText(folderName);
        fileInfoPathValue.setText(folderName + "/");
        fileInfoSizeValue.setText("—");
        fileInfoCommitSection.setVisible(false);
        fileInfoButtonSection.setVisible(false);
        showCard(CARD_FILE_INFO);
    }

    // ========== File Content Viewer ==========

    private void openFileContentView(String filePath) {
        String fileName = getFileName(filePath);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return client.getFileContent(filePath);
            }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String content = get();
                    if (isBinary(content)) {
                        JOptionPane.showMessageDialog(FilesTabPanel.this,
                                "Cannot display binary file: " + fileName,
                                "Binary File", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    showTextViewer(fileName, content);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(FilesTabPanel.this,
                            "Failed to fetch file content: " + cause.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showTextViewer(String title, String content) {
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(frame, title, true);

        JTextArea textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setCaretPosition(0);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeButton);

        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(textArea), BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(frame);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);
    }

    // ========== Filter ==========

    private void onSearchChanged() {
        applyFilter(getSearchText());
    }

    private String getSearchText() {
        String text = searchField.getText();
        return SEARCH_PLACEHOLDER.equals(text) ? "" : text.trim();
    }

    private void applyFilter(String text) {
        if (currentRepoInfo == null) return;
        // Clear selection and reset right card on every filter change
        fileTree.clearSelection();
        currentFilePath = null;
        emptyLabel.setText("Select a file from the tree to view its details and commit history");
        showCard(CARD_EMPTY);

        if (text.isEmpty() && showAllCheckbox.isSelected()) {
            buildFileTree(currentRepoInfo);
            return;
        }

        String lower = text.toLowerCase();
        boolean showAll = showAllCheckbox.isSelected();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        List<RepoFolder> folders = currentRepoInfo.getFolders() != null
                ? currentRepoInfo.getFolders() : Collections.emptyList();

        for (RepoFolder folder : folders) {
            if (!showAll && !isMirthFolder(folder.getName())) continue;

            List<RepoFile> files = folder.getFiles() != null
                    ? folder.getFiles() : Collections.emptyList();

            DefaultMutableTreeNode folderNode = null; // lazy-create; only added if it has matching children
            int matchCount = 0;

            for (RepoFile file : files) {
                String fullPath = folder.getName() + "/" + file.getName();
                if (fullPath.toLowerCase().contains(lower)) {
                    if (folderNode == null) {
                        folderNode = new DefaultMutableTreeNode(""); // placeholder label
                    }
                    String displayText = file.getName() + "  (" + formatBytes(file.getSizeBytes()) + ")";
                    folderNode.add(new DefaultMutableTreeNode(
                            new FileNode(displayText, fullPath, file.getSizeBytes())));
                    matchCount++;
                }
            }

            if (folderNode != null) {
                folderNode.setUserObject(folder.getName() + "  (" + matchCount + " files)");
                root.add(folderNode);
            }
        }

        fileTree.setModel(new DefaultTreeModel(root));
        expandAll(fileTree);
    }

    // ========== Tree Building ==========

    private void buildFileTree(RepoInfo info) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        if (info.getFolders() != null) {
            for (RepoFolder folder : info.getFolders()) {
                DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(
                        folder.getName() + "  (" + folder.getFileCount() + " files)");
                if (folder.getFiles() != null) {
                    for (RepoFile file : folder.getFiles()) {
                        String relativePath = folder.getName() + "/" + file.getName();
                        String displayText  = file.getName() + "  ("
                                + formatBytes(file.getSizeBytes()) + ")";
                        folderNode.add(new DefaultMutableTreeNode(
                                new FileNode(displayText, relativePath, file.getSizeBytes())));
                    }
                }
                root.add(folderNode);
            }
        }
        fileTree.setModel(new DefaultTreeModel(root));
        expandAll(fileTree);
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

    private static boolean isMirthFolder(String folderName) {
        String lower = folderName.toLowerCase();
        return lower.equals("channels")
                || lower.equals("codetemplates")
                || lower.equals("libraries")
                || lower.equals("globalscripts");
    }

    private static String detectMode(String relativePath) {
        String lower = relativePath.toLowerCase();
        if (lower.startsWith("channels/"))      return VersionControlConstants.MODE_CHANNEL;
        if (lower.startsWith("codetemplates/")) return VersionControlConstants.MODE_CODE_TEMPLATE;
        if (lower.startsWith("libraries/"))     return VersionControlConstants.MODE_CODE_TEMPLATE_LIBRARY;
        if (lower.startsWith("globalscripts/")) return VersionControlConstants.MODE_GLOBAL_SCRIPTS;
        if (lower.contains("codetemplate") || lower.contains("libraries")
                || lower.contains("library")) {
            return VersionControlConstants.MODE_CODE_TEMPLATE;
        }
        return VersionControlConstants.MODE_CHANNEL;
    }

    private static String extractId(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        String filename = slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
        if (filename.endsWith(".xml")) {
            filename = filename.substring(0, filename.length() - 4);
        }
        return filename;
    }

    private static String getFileName(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    private static String formatTimestamp(long millis) {
        return new SimpleDateFormat("MMM dd, yyyy HH:mm").format(new Date(millis));
    }

    private static String trimMessage(String message) {
        if (message == null) return "—";
        return CommitMessageUtil.extractContent(message);
    }

    private static boolean isBinary(String content) {
        int limit = Math.min(content.length(), 8192);
        for (int i = 0; i < limit; i++) {
            if (content.charAt(i) == '\0') return true;
        }
        return false;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L)            return bytes + " B";
        if (bytes < 1024L * 1024)     return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ========== Inner Classes ==========

    static final class FileNode {
        final String displayText;
        final String relativePath;
        final long   sizeBytes;

        FileNode(String displayText, String relativePath, long sizeBytes) {
            this.displayText  = displayText;
            this.relativePath = relativePath;
            this.sizeBytes    = sizeBytes;
        }

        @Override
        public String toString() { return displayText; }
    }
}
