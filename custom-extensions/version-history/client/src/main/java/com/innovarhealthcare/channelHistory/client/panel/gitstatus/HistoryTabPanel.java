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
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.innovarhealthcare.channelHistory.client.diff.DiffComparisonPanel;
import com.innovarhealthcare.channelHistory.client.diff.model.VersionInfo;
import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemChange;
import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.innovarhealthcare.channelHistory.shared.util.CommitMessageUtil;
import com.mirth.connect.client.ui.UIConstants;
import net.miginfocom.swing.MigLayout;

/**
 * Left-panel tab for the commit History view in the Git Status tab.
 * Owns the history JList (with search bar + filter popup), changed-files JList,
 * embedded DiffComparisonPanel, and the HISTORY / EMPTY right cards.
 *
 * @author Thai Tran
 */
public class HistoryTabPanel extends JPanel {

    private static final String CARD_EMPTY = "EMPTY";
    private static final String CARD_HISTORY = "HISTORY";
    private static final String SEARCH_PLACEHOLDER = "Search by message...";

    private final VersionHistoryServiceClient client;
    private final VersionHistoryProperties versionHistoryProperties;

    // ── Data model ─────────────────────────────────────────────────────────────
    private List<CommitMetaData> allCommits = new ArrayList<>();
    private List<CommitMetaData> filteredCommits = new ArrayList<>();

    // ── Left: search bar ───────────────────────────────────────────────────────
    private JTextField searchField;
    private JButton filterToggleButton;
    private JPanel filterPopupPanel;
    private JLabel filterIndicatorLabel;
    private JTextField filterAuthorField;
    private JComboBox<String> filterTypeCombo;
    private JTextField filterNameField;
    private JTextField filterServerField;
    private JTextField filterHashField;
    private JComboBox<String> filterWithinCombo;
    private JButton filterClearButton;
    private JButton filterApplyButton;
    private DocumentListener searchDocumentListener;

    // ── Left: mode panels ──────────────────────────────────────────────────────
    private JPanel searchBarPanel;
    private JPanel fileFilterBar;

    // ── Left: file-path filter bar ─────────────────────────────────────────────
    private JLabel historyFilterLabel;
    private JButton clearFilterButton;
    private JProgressBar loadingBar;

    // ── Left: history list ─────────────────────────────────────────────────────
    private DefaultListModel<CommitMetaData> historyListModel;
    private JList<CommitMetaData> historyList;
    private JScrollPane historyListScrollPane;

    // ── Right cards ────────────────────────────────────────────────────────────
    private JPanel rightCards;
    private CardLayout rightCardLayout;
    private JLabel emptyLabel;

    // HISTORY card fields
    private JLabel historyCommitHashValue;
    private JLabel historyCommitAuthorValue;
    private JLabel historyCommitDateValue;
    private JLabel historyCommitMsgValue;
    private JSeparator metadataSeparator;
    private JLabel historyCommitTypeLabel, historyCommitTypeValue;
    private JLabel historyCommitNameLabel, historyCommitNameValue;
    private JLabel historyCommitServerLabel, historyCommitServerValue;
    private JSplitPane historyContentSplit;
    private DefaultListModel<RepoItemChange> changedFilesListModel;
    private JList<RepoItemChange> changedFilesList;
    private JScrollPane changedFilesScrollPane;
    private CommitMetaData currentHistoryCommit;

    // ── Main split pane ────────────────────────────────────────────────────────
    private JSplitPane splitPane;

    public HistoryTabPanel(VersionHistoryServiceClient client, VersionHistoryProperties versionHistoryProperties) {
        this.client = client;
        this.versionHistoryProperties = versionHistoryProperties;
        initComponents();
        initLayout();
        initListeners();
    }

    // ========== Public API ==========

    /**
     * Called by the shell's JTabbedPane ChangeListener when this tab becomes active.
     */
    public void onTabSelected() {
        loadRepoLog();
    }

    /**
     * Loads commit history for a specific file (file-filtered view).
     *
     * @param relativePath repo-relative file path
     */
    public void loadHistory(String relativePath) {
        enterFileHistoryMode();
        showLoadingState();

        new SwingWorker<List<CommitMetaData>, Void>() {
            @Override
            protected List<CommitMetaData> doInBackground() throws Exception {
                return client.getFileHistory(relativePath);
            }

            @Override
            protected void done() {
                loadingBar.setVisible(false);
                try {
                    List<CommitMetaData> history = get();
                    allCommits = history != null ? history : new ArrayList<CommitMetaData>();
                    historyFilterLabel.setText("Filtered: " + relativePath);
                    clearFilterButton.setVisible(true);
                    refreshList();
                } catch (Exception ex) {
                    emptyLabel.setText("Failed to load file history");
                    showCard(CARD_EMPTY);
                    JOptionPane.showMessageDialog(HistoryTabPanel.this,
                            "Failed to load file history: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Resets the history list to empty state and shows the EMPTY card.
     */
    public void clear() {
        allCommits = new ArrayList<>();
        filteredCommits = new ArrayList<>();
        historyListModel.clear();
        historyFilterLabel.setText("All commits");
        clearFilterButton.setVisible(false);
        currentHistoryCommit = null;
        emptyLabel.setText("");
        showCard(CARD_EMPTY);
    }

    // ========== Data Loading ==========

    private void loadRepoLog() {
        enterFilterMode();
        showLoadingState();

        new SwingWorker<List<CommitMetaData>, Void>() {
            @Override
            protected List<CommitMetaData> doInBackground() throws Exception {
                return client.getRepoLog(versionHistoryProperties.getHistoryLogMaxCount());
            }

            @Override
            protected void done() {
                loadingBar.setVisible(false);
                try {
                    List<CommitMetaData> log = get();
                    allCommits = log != null ? log : new ArrayList<CommitMetaData>();
                    historyFilterLabel.setText("All commits");
                    clearFilterButton.setVisible(false);
                    refreshList();
                } catch (Exception ex) {
                    emptyLabel.setText("Failed to load repository log");
                    showCard(CARD_EMPTY);
                    JOptionPane.showMessageDialog(HistoryTabPanel.this,
                            "Failed to load repository log: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ========== Initialization ==========

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        // Search field (placeholder handled by FocusListener)
        searchField = new JTextField();
        searchField.setText(SEARCH_PLACEHOLDER);
        searchField.setForeground(Color.GRAY);
        searchField.setToolTipText("Filter by commit message");

        filterToggleButton = new JButton("\u25BC");
        filterToggleButton.setFont(new Font("Tahoma", Font.PLAIN, 10));
        filterToggleButton.setToolTipText("Toggle advanced filter");
        filterToggleButton.setFocusPainted(false);

        filterPopupPanel = buildFilterPopupPanel();

        filterIndicatorLabel = new JLabel();
        filterIndicatorLabel.setFont(new Font("Tahoma", Font.PLAIN, 11));
        filterIndicatorLabel.setForeground(new Color(204, 102, 0));
        filterIndicatorLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        filterIndicatorLabel.setVisible(false);

        // File-path filter bar
        historyFilterLabel = new JLabel("All commits");
        historyFilterLabel.setFont(new Font("Tahoma", Font.ITALIC, 11));
        historyFilterLabel.setForeground(new Color(100, 100, 100));

        clearFilterButton = new JButton();
        clearFilterButton.setIcon(UIConstants.ICON_X);
        clearFilterButton.setToolTipText("Clear Filter");
        clearFilterButton.setIconTextGap(5);
        clearFilterButton.setVisible(false);
        clearFilterButton.addActionListener(e -> clearFileFilter());

        loadingBar = new JProgressBar();
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);

        // History list
        historyListModel = new DefaultListModel<>();
        historyList = new JList<>(historyListModel);
        historyList.setCellRenderer(new CommitListCellRenderer());
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyListScrollPane = new JScrollPane(historyList);

        // Right cards
        rightCardLayout = new CardLayout();
        rightCards = new JPanel(rightCardLayout);
        rightCards.setBackground(UIConstants.BACKGROUND_COLOR);

        emptyLabel = new JLabel("Select a file in the Files tab to browse its commit history", JLabel.CENTER);
        emptyLabel.setForeground(new Color(150, 150, 150));
        emptyLabel.setFont(new Font("Tahoma", Font.ITALIC, 12));
        rightCards.add(emptyLabel, CARD_EMPTY);

        rightCards.add(buildHistoryCard(), CARD_HISTORY);
    }

    private JPanel buildFilterPopupPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 6, novisualpadding", "[right]6[grow,fill]"));
        panel.setBackground(UIConstants.BACKGROUND_COLOR);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)));

        filterAuthorField = new JTextField();
        filterAuthorField.setToolTipText("Filter by committer name");
        filterTypeCombo = new JComboBox<>(new String[]{
                "Any type", "Channel", "Code Template", "Library", "Global Scripts"
        });
        filterTypeCombo.setToolTipText("Filter by entity type");
        filterNameField = new JTextField();
        filterNameField.setToolTipText("Filter by entity name");
        filterServerField = new JTextField();
        filterServerField.setToolTipText("Filter by server name or server ID");
        filterHashField = new JTextField();
        filterHashField.setToolTipText("Filter by commit hash (partial match supported)");
        filterWithinCombo = new JComboBox<>(new String[]{
                "Any time", "Last 15 minutes", "Last hour",
                "Last 24 hours", "Last 7 days", "Last 30 days", "Last 90 days"
        });
        filterWithinCombo.setToolTipText("Filter by commit age");
        filterClearButton = new JButton();
        filterClearButton.setIcon(UIConstants.ICON_X);
        filterClearButton.setToolTipText("Clear Filters");
        filterClearButton.setIconTextGap(5);
        filterApplyButton = new JButton();
        filterApplyButton.setIcon(UIConstants.ICON_FILE_PICKER);
        filterApplyButton.setToolTipText("Apply Filter");
        filterApplyButton.setIconTextGap(5);

        panel.add(new JLabel("Author:"));
        panel.add(filterAuthorField, "growx, wrap");
        panel.add(new JLabel("Type:"));
        panel.add(filterTypeCombo, "growx, wrap");
        panel.add(new JLabel("Name:"));
        panel.add(filterNameField, "growx, wrap");
        panel.add(new JLabel("Server:"));
        panel.add(filterServerField, "growx, wrap");
        panel.add(new JLabel("Hash:"));
        panel.add(filterHashField, "growx, wrap");
        panel.add(new JLabel("Within:"));
        panel.add(filterWithinCombo, "growx, wrap");

        JPanel buttonRow = new JPanel(new MigLayout("insets 0, novisualpadding", "[grow][]6[]"));
        buttonRow.setBackground(UIConstants.BACKGROUND_COLOR);
        buttonRow.add(new JLabel(), "growx, push");
        buttonRow.add(filterClearButton);
        buttonRow.add(filterApplyButton);
        panel.add(buttonRow, "span 2, growx, wrap");

        panel.setVisible(false);
        return panel;
    }

    private JPanel buildHistoryCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(UIConstants.BACKGROUND_COLOR);

        JPanel commitMetaPanel = new JPanel(new MigLayout("insets 8 8 4 8, novisualpadding, hidemode 3", "[right]8[grow,fill]"));
        commitMetaPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        commitMetaPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(204, 204, 204)));

        historyCommitHashValue = new JLabel("—");
        historyCommitAuthorValue = new JLabel("—");
        historyCommitDateValue = new JLabel("—");
        historyCommitMsgValue = new JLabel("—");

        metadataSeparator = new JSeparator();
        historyCommitTypeLabel = new JLabel("Type:");
        historyCommitTypeValue = new JLabel("—");
        historyCommitNameLabel = new JLabel("Name:");
        historyCommitNameValue = new JLabel("—");
        historyCommitServerLabel = new JLabel("Server:");
        historyCommitServerValue = new JLabel("—");

        metadataSeparator.setVisible(false);
        historyCommitTypeLabel.setVisible(false);
        historyCommitTypeValue.setVisible(false);
        historyCommitNameLabel.setVisible(false);
        historyCommitNameValue.setVisible(false);
        historyCommitServerLabel.setVisible(false);
        historyCommitServerValue.setVisible(false);

        commitMetaPanel.add(new JLabel("Commit:"));
        commitMetaPanel.add(historyCommitHashValue, "growx, wrap");
        commitMetaPanel.add(new JLabel("Author:"));
        commitMetaPanel.add(historyCommitAuthorValue, "growx, wrap");
        commitMetaPanel.add(new JLabel("Date:"));
        commitMetaPanel.add(historyCommitDateValue, "growx, wrap");
        commitMetaPanel.add(new JLabel("Message:"));
        commitMetaPanel.add(historyCommitMsgValue, "growx, wrap");
        commitMetaPanel.add(metadataSeparator, "span 2, growx, wrap");
        commitMetaPanel.add(historyCommitTypeLabel);
        commitMetaPanel.add(historyCommitTypeValue, "growx, wrap");
        commitMetaPanel.add(historyCommitNameLabel);
        commitMetaPanel.add(historyCommitNameValue, "growx, wrap");
        commitMetaPanel.add(historyCommitServerLabel);
        commitMetaPanel.add(historyCommitServerValue, "growx, wrap");

        changedFilesListModel = new DefaultListModel<>();
        changedFilesList = new JList<>(changedFilesListModel);
        changedFilesList.setCellRenderer(new RepoItemChangeCellRenderer());
        changedFilesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        changedFilesScrollPane = new JScrollPane(changedFilesList);
        changedFilesScrollPane.setBorder(BorderFactory.createTitledBorder("Changed files"));

        JPanel diffPlaceholder = buildDiffPlaceholder("Select a file to view diff");

        historyContentSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, changedFilesScrollPane, diffPlaceholder);
        historyContentSplit.setResizeWeight(0.3);
        historyContentSplit.setDividerSize(6);
        historyContentSplit.setBorder(null);
        historyContentSplit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                historyContentSplit.setDividerLocation(0.3);
                historyContentSplit.removeComponentListener(this);
            }
        });

        card.add(commitMetaPanel, BorderLayout.NORTH);
        card.add(historyContentSplit, BorderLayout.CENTER);
        return card;
    }

    private void initLayout() {
        setLayout(new BorderLayout());

        // Search bar row: [searchField] [▼]
        searchBarPanel = new JPanel(new MigLayout("insets 4 4 2 4, novisualpadding", "[grow,fill][]"));
        searchBarPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        searchBarPanel.add(searchField, "growx");
        searchBarPanel.add(filterToggleButton);

        // File-path filter bar: [label] [Clear filter] [loading]
        fileFilterBar = new JPanel(new MigLayout("insets 2 4 2 4, novisualpadding", "[grow,fill][][]"));
        fileFilterBar.setBackground(UIConstants.BACKGROUND_COLOR);
        fileFilterBar.add(historyFilterLabel, "growx");
        fileFilterBar.add(clearFilterButton);
        fileFilterBar.add(loadingBar, "w 80!");
        fileFilterBar.setVisible(false);

        JPanel leftTopPanel = new JPanel();
        leftTopPanel.setLayout(new BoxLayout(leftTopPanel, BoxLayout.Y_AXIS));
        leftTopPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        leftTopPanel.add(searchBarPanel);
        leftTopPanel.add(filterPopupPanel);
        leftTopPanel.add(filterIndicatorLabel);
        leftTopPanel.add(fileFilterBar);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        leftPanel.add(leftTopPanel, BorderLayout.NORTH);
        leftPanel.add(historyListScrollPane, BorderLayout.CENTER);

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
        // History list selection
        historyList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            CommitMetaData commit = historyList.getSelectedValue();
            if (commit == null) return;
            onHistoryCommitSelected(commit);
        });

        // Changed files list selection
        changedFilesList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            RepoItemChange change = changedFilesList.getSelectedValue();
            if (change == null || currentHistoryCommit == null) return;
            onHistoryChangedFileSelected(change, currentHistoryCommit.getHash());
        });

        // Filter indicator: click opens popup
        filterIndicatorLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                filterPopupPanel.setVisible(true);
                revalidate();
                repaint();
            }
        });

        // Toggle filter popup
        filterToggleButton.addActionListener(e -> {
            filterPopupPanel.setVisible(!filterPopupPanel.isVisible());
            revalidate();
            repaint();
        });

        // Apply button: apply popup filter and close popup
        filterApplyButton.addActionListener(e -> {
            filterPopupPanel.setVisible(false);
            revalidate();
            repaint();
            refreshList();
        });

        // Clear button: reset all filter state
        filterClearButton.addActionListener(e -> resetFilter());

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

        // Real-time search on keystroke
        searchDocumentListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onSearchChanged(); }
            @Override public void removeUpdate(DocumentEvent e)  { onSearchChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onSearchChanged(); }
        };
        searchField.getDocument().addDocumentListener(searchDocumentListener);
    }

    // ========== Filter ==========

    private void enterFileHistoryMode() {
        searchBarPanel.setVisible(false);
        filterPopupPanel.setVisible(false);
        filterIndicatorLabel.setVisible(false);
        fileFilterBar.setVisible(true);
        revalidate();
        repaint();
    }

    private void enterFilterMode() {
        fileFilterBar.setVisible(false);
        searchBarPanel.setVisible(true);
        revalidate();
        repaint();
        resetFilter();
    }

    private void onSearchChanged() {
        if (SEARCH_PLACEHOLDER.equals(searchField.getText())) return;
        refreshList();
    }

    private void resetFilter() {
        filterAuthorField.setText("");
        filterTypeCombo.setSelectedIndex(0);
        filterNameField.setText("");
        filterServerField.setText("");
        filterHashField.setText("");
        filterWithinCombo.setSelectedIndex(0);
        // Reset search field without triggering intermediate refreshes
        searchField.getDocument().removeDocumentListener(searchDocumentListener);
        searchField.setText(SEARCH_PLACEHOLDER);
        searchField.setForeground(Color.GRAY);
        searchField.getDocument().addDocumentListener(searchDocumentListener);
        refreshList();
    }

    private void clearFileFilter() {
        loadRepoLog();
    }

    private void refreshList() {
        filteredCommits = applyFilter(allCommits);
        updateFilterIndicator();
        DefaultListModel<CommitMetaData> newModel = new DefaultListModel<>();
        for (CommitMetaData c : filteredCommits) {
            newModel.addElement(c);
        }
        historyList.setModel(newModel);
        historyListModel = newModel;
        if (!historyListModel.isEmpty()) {
            historyList.setSelectedIndex(0);
        } else {
            emptyLabel.setText("No commits found");
            showCard(CARD_EMPTY);
        }
    }

    private List<CommitMetaData> applyFilter(List<CommitMetaData> commits) {
        String search = getSearchText().toLowerCase();
        String authorFilter = filterAuthorField.getText().trim().toLowerCase();
        String typeFilter = (String) filterTypeCombo.getSelectedItem();
        String nameFilter = filterNameField.getText().trim().toLowerCase();
        String serverFilter = filterServerField.getText().trim().toLowerCase();
        String hashFilter = filterHashField.getText().trim().toLowerCase();
        String withinFilter = (String) filterWithinCombo.getSelectedItem();

        boolean filterByType = typeFilter != null && !typeFilter.startsWith("Any");
        boolean filterByWithin = withinFilter != null && !withinFilter.startsWith("Any");
        long withinThreshold = getWithinThreshold(withinFilter);
        long now = System.currentTimeMillis();

        List<CommitMetaData> result = new ArrayList<>();

        for (CommitMetaData commit : commits) {
            String raw = commit.getMessage();
            String msgContent = CommitMessageUtil.extractContent(raw != null ? raw : "");
            String committer = orEmpty(commit.getCommitter());
            String hash = orEmpty(commit.getHash());
            String name = "";
            String type = "";
            String serverName = "";
            String serverId = "";

            if (CommitMessageUtil.isValidFormat(raw)) {
                name = orEmpty(CommitMessageUtil.extractName(raw));
                type = orEmpty(CommitMessageUtil.extractType(raw));
                serverName = orEmpty(CommitMessageUtil.extractServerName(raw));
                serverId = orEmpty(CommitMessageUtil.extractServerId(raw));
            }

            // Search box: message subject match only
            if (!search.isEmpty() && !msgContent.toLowerCase().contains(search)) continue;

            // Popup filters (AND logic)
            if (!authorFilter.isEmpty() && !committer.toLowerCase().contains(authorFilter)) continue;
            if (filterByType) {
                boolean typeMatch = typeFilter.equals(type)
                        || ("Library".equals(typeFilter) && "Batch Libraries".equals(type));
                if (!typeMatch) continue;
            }
            if (!nameFilter.isEmpty() && !name.toLowerCase().contains(nameFilter)) continue;
            if (!serverFilter.isEmpty()) {
                boolean serverMatch = serverName.toLowerCase().contains(serverFilter)
                        || serverId.toLowerCase().contains(serverFilter);
                if (!serverMatch) continue;
            }
            if (!hashFilter.isEmpty() && !hash.toLowerCase().contains(hashFilter)) continue;
            if (filterByWithin && (now - commit.getTimestamp()) > withinThreshold) continue;

            result.add(commit);
        }

        return result;
    }

    private void updateFilterIndicator() {
        if (isPopupFilterActive()) {
            filterIndicatorLabel.setText("<html><u>Filters active \u2014 showing "
                    + filteredCommits.size() + "/" + allCommits.size() + " commits</u></html>");
            filterIndicatorLabel.setVisible(true);
        } else {
            filterIndicatorLabel.setVisible(false);
        }
    }

    private boolean isPopupFilterActive() {
        if (!filterAuthorField.getText().trim().isEmpty()) return true;
        if (filterTypeCombo.getSelectedIndex() != 0) return true;
        if (!filterNameField.getText().trim().isEmpty()) return true;
        if (!filterServerField.getText().trim().isEmpty()) return true;
        if (!filterHashField.getText().trim().isEmpty()) return true;
        if (filterWithinCombo.getSelectedIndex() != 0) return true;
        return false;
    }

    private String getSearchText() {
        String text = searchField.getText();
        return SEARCH_PLACEHOLDER.equals(text) ? "" : text;
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }

    private static long getWithinThreshold(String within) {
        if ("Last 15 minutes".equals(within)) return 15L * 60 * 1000;
        if ("Last hour".equals(within))       return 60L * 60 * 1000;
        if ("Last 24 hours".equals(within))   return 24L * 60 * 60 * 1000;
        if ("Last 7 days".equals(within))     return 7L * 24 * 60 * 60 * 1000;
        if ("Last 30 days".equals(within))    return 30L * 24 * 60 * 60 * 1000;
        if ("Last 90 days".equals(within))    return 90L * 24 * 60 * 60 * 1000;
        return Long.MAX_VALUE;
    }

    // ========== Selection Handlers ==========

    private void onHistoryCommitSelected(CommitMetaData commit) {
        currentHistoryCommit = commit;

        historyCommitHashValue.setText(commit.getShortHash());
        historyCommitAuthorValue.setText(commit.getCommitter());
        historyCommitDateValue.setText(formatTimestamp(commit.getTimestamp()));
        historyCommitMsgValue.setText(trimMessage(commit.getMessage()));

        String raw = commit.getMessage();
        boolean hasMetadata = CommitMessageUtil.isValidFormat(raw);

        metadataSeparator.setVisible(hasMetadata);
        historyCommitTypeLabel.setVisible(hasMetadata);
        historyCommitTypeValue.setVisible(hasMetadata);
        historyCommitNameLabel.setVisible(hasMetadata);
        historyCommitNameValue.setVisible(hasMetadata);
        historyCommitServerLabel.setVisible(hasMetadata);
        historyCommitServerValue.setVisible(hasMetadata);

        if (hasMetadata) {
            historyCommitTypeValue.setText(CommitMessageUtil.extractType(raw));
            historyCommitNameValue.setText(CommitMessageUtil.extractName(raw));
            String serverName = CommitMessageUtil.extractServerName(raw);
            String serverId = CommitMessageUtil.extractServerId(raw);
            String serverText = (serverName != null) ? serverName + " (" + serverId + ")" : serverId;
            historyCommitServerValue.setText(serverText);
        }

        changedFilesListModel.clear();
        historyContentSplit.setBottomComponent(buildDiffPlaceholder("Loading changes\u2026"));
        showCard(CARD_HISTORY);

        String hash = commit.getHash();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<RepoItemChange>, Void>() {
            @Override
            protected List<RepoItemChange> doInBackground() throws Exception {
                return client.getCommitChanges(hash);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    List<RepoItemChange> changes = get();
                    changedFilesListModel.clear();
                    if (changes != null) {
                        for (RepoItemChange c : changes) changedFilesListModel.addElement(c);
                    }
                    historyContentSplit.setBottomComponent(buildDiffPlaceholder("Select a file to view diff"));
                    if (!changedFilesListModel.isEmpty()) {
                        changedFilesList.setSelectedIndex(0);
                    }
                } catch (Exception ex) {
                    historyContentSplit.setBottomComponent(
                            buildDiffPlaceholder("Error loading changes: " + ex.getMessage()));
                }
            }
        }.execute();
    }

    private void onHistoryChangedFileSelected(RepoItemChange change, String commitHash) {
        String filePath = change.getPath();
        String changeType = change.getChangeType();
        String parentHash = commitHash + "^";
        String fileName = getFileName(filePath);

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                String left = "";
                String right = "";
                if ("ADDED".equals(changeType)) {
                    try { right = client.getFileContentAtRevision(filePath, commitHash); } catch (Exception ignored) {}
                } else if ("DELETED".equals(changeType)) {
                    try { left = client.getFileContentAtRevision(filePath, parentHash); } catch (Exception ignored) {}
                } else {
                    try { right = client.getFileContentAtRevision(filePath, commitHash); } catch (Exception ignored) {}
                    try { left = client.getFileContentAtRevision(filePath, parentHash); } catch (Exception ignored) {}
                }
                return new String[]{left, right};
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String[] content = get();
                    String shortHash = commitHash.length() >= 7 ? commitHash.substring(0, 7) : commitHash;

                    VersionInfo leftVi;
                    VersionInfo rightVi;
                    if ("ADDED".equals(changeType)) {
                        leftVi = VersionInfo.builder().name(fileName).version("New file").isCurrent(false).build();
                        rightVi = VersionInfo.builder().name(fileName).version(shortHash).isCurrent(false).build();
                    } else if ("DELETED".equals(changeType)) {
                        leftVi = VersionInfo.builder().name(fileName).version(shortHash).isCurrent(false).build();
                        rightVi = VersionInfo.builder().name(fileName).version("Deleted").isCurrent(false).build();
                    } else {
                        String parentShort = parentHash.length() >= 8 ? parentHash.substring(0, 7) : parentHash;
                        leftVi = VersionInfo.builder().name(fileName).version(parentShort).isCurrent(false).build();
                        rightVi = VersionInfo.builder().name(fileName).version(shortHash).isCurrent(false).build();
                    }

                    DiffComparisonPanel diffPanel = new DiffComparisonPanel(leftVi, rightVi);
                    historyContentSplit.setBottomComponent(diffPanel);
                    diffPanel.updateDiff(content[0], content[1]);
                } catch (Exception ex) {
                    historyContentSplit.setBottomComponent(
                            buildDiffPlaceholder("Error loading diff: " + ex.getMessage()));
                }
            }
        }.execute();
    }

    // ========== Helpers ==========

    private void showCard(String cardName) {
        rightCardLayout.show(rightCards, cardName);
    }

    private void showLoadingState() {
        allCommits = new ArrayList<>();
        historyListModel.clear();
        emptyLabel.setText("Loading...");
        showCard(CARD_EMPTY);
        loadingBar.setVisible(true);
    }

    private static JPanel buildDiffPlaceholder(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setForeground(new Color(150, 150, 150));
        label.setFont(new Font("Tahoma", Font.ITALIC, 12));
        panel.add(label, BorderLayout.CENTER);
        return panel;
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

    private static String formatRelativeTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        if (seconds < 15)  return "just now";
        if (seconds < 60)  return seconds + " seconds ago";
        long minutes = seconds / 60;
        if (minutes < 60)  return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        long hours = minutes / 60;
        if (hours < 24)    return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        long days = hours / 24;
        if (days < 7)      return days + " day" + (days == 1 ? "" : "s") + " ago";
        return new SimpleDateFormat("MMM dd, yyyy").format(new Date(timestamp));
    }

    // ========== Inner Classes ==========

    private static final class CommitListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof CommitMetaData) {
                CommitMetaData commit = (CommitMetaData) value;
                String hash = commit.getShortHash();
                String msg = commit.getMessage();
                if (msg != null) {
                    msg = CommitMessageUtil.extractContent(msg);
                    if (msg.length() > 60) {
                        msg = msg.substring(0, 57) + "...";
                    }
                } else {
                    msg = "(no message)";
                }
                String author = commit.getCommitter() != null ? commit.getCommitter() : "Unknown";
                String date = formatRelativeTime(commit.getTimestamp());

                if (isSelected) {
                    setText("<html><b>" + hash + "</b> " + msg
                            + "<br><small>" + author + " \u2022 " + date + "</small></html>");
                } else {
                    setText("<html><b>" + hash + "</b> " + msg
                            + "<br><font color='#757575'><small>" + author + " \u2022 " + date
                            + "</small></font></html>");
                }
                setFont(getFont().deriveFont(Font.PLAIN, 12f));
            }
            return this;
        }
    }

    private static final class RepoItemChangeCellRenderer extends DefaultListCellRenderer {
        private static final String HEX_MODIFIED = "#CC6600";
        private static final String HEX_ADDED    = "#009900";
        private static final String HEX_DELETED  = "#CC0000";

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof RepoItemChange) {
                RepoItemChange change = (RepoItemChange) value;
                String changeType = change.getChangeType();
                String path = change.getPath();

                String prefix;
                String hex;
                if ("ADDED".equals(changeType)) {
                    prefix = "[A]";
                    hex = HEX_ADDED;
                } else if ("DELETED".equals(changeType)) {
                    prefix = "[D]";
                    hex = HEX_DELETED;
                } else {
                    prefix = "[M]";
                    hex = HEX_MODIFIED;
                }

                if (isSelected) {
                    setText(prefix + " " + path);
                } else {
                    setText("<html><font color='" + hex + "'>" + prefix + "</font> " + path + "</html>");
                }
                setToolTipText(path);
                setFont(getFont().deriveFont(Font.PLAIN, 12f));
            }
            return this;
        }
    }
}
