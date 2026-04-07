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

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import com.innovarhealthcare.channelHistory.client.service.VersionHistoryServiceClient;
import com.innovarhealthcare.channelHistory.shared.dto.response.LibrariesAndTemplatesResponse;
import com.innovarhealthcare.channelHistory.shared.dto.response.LibraryMetadata;
import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrarySaveResult;
import net.miginfocom.swing.MigLayout;

/**
 * Dialog for importing code templates from Git repository
 * Displays templates grouped by library in a tree structure
 *
 * @author Thai Tran
 * @create 2024-11-20 2:30 PM
 */
public class ImportCodeTemplateDialog extends MirthDialog {

    // UI Components
    private JTree codeTemplateTree;
    private JScrollPane treeScrollPane;
    private JTextField searchField;
    private JButton clearSearchButton;
    private JButton selectAllButton;
    private JButton deselectAllButton;
    private JButton okButton;
    private JButton cancelButton;
    private JLabel selectionCountLabel;

    // Data
    private Map<String, CodeTemplateLibrary> codeTemplateLibraries;
    private List<LibraryMetadata> librariesMetadata;
    private List<RepoItemMetadata> templatesMetadata;
    private DefaultMutableTreeNode fullTreeRoot;

    // State
    private boolean loading = false;
    private final Frame parent;

    public ImportCodeTemplateDialog(Frame parent) {
        super(parent, true);

        this.parent = parent;
        this.codeTemplateLibraries = parent.codeTemplatePanel.getCachedCodeTemplateLibraries();

        initComponents();
        initLayout();

        enterLoadingState();

        // Start thread to load libraries and templates from repo
        new LoadLibrariesAndTemplatesWorker().execute();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Import Code Template From Repo");
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        // Create tree
        DefaultMutableTreeNode loadingNode = new DefaultMutableTreeNode("Loading...");
        codeTemplateTree = new JTree(loadingNode);
        codeTemplateTree.setRootVisible(false);
        codeTemplateTree.setShowsRootHandles(true);
        codeTemplateTree.setCellRenderer(new CodeTemplateTreeCellRenderer());
        codeTemplateTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        codeTemplateTree.addTreeSelectionListener(e -> updateSelectionCount());

        treeScrollPane = new JScrollPane(codeTemplateTree, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        treeScrollPane.setPreferredSize(new Dimension(550, 400));

        // Search field
        searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new SimpleDoc(this::applyFilter));

        clearSearchButton = new JButton("");
        clearSearchButton.setIcon(UIConstants.ICON_X);
        clearSearchButton.setToolTipText("Clear");
        clearSearchButton.addActionListener(e -> {
            searchField.setText("");
            applyFilter();
            searchField.requestFocusInWindow();
        });

        // Selection buttons
        selectAllButton = new JButton("Select All");
        selectAllButton.addActionListener(e -> selectAllTemplates());

        deselectAllButton = new JButton("Deselect All");
        deselectAllButton.addActionListener(e -> codeTemplateTree.clearSelection());

        // ✅ Selection counter
        selectionCountLabel = new JLabel("0 selected");
        selectionCountLabel.setForeground(Color.GRAY);

        // Buttons
        okButton = new JButton("Import");
        okButton.addActionListener(evt -> onOkImport(evt));
        okButton.setEnabled(false);

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(evt -> dispose());
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 8, novisualpadding, hidemode 3, fill", "[pref][grow][pref][pref][pref]", "[][grow][]"));

        // Row 1
        add(new JLabel("Search:"));
        add(searchField, "growx, pushx");
        add(clearSearchButton);
        add(selectAllButton, "w 100!");
        add(deselectAllButton, "w 100!, wrap");

        // Row 2
        add(treeScrollPane, "span 5, grow, push, wrap");

        // Row 3
        add(selectionCountLabel, "span 3, growx, pushx");
        add(okButton, "w 100!");
        add(cancelButton, "w 100!");
    }

    // ========== Loading State Management ==========
    private void enterLoadingState() {
        loading = true;
        selectionCountLabel.setText("⏳ Loading templates...");
        selectionCountLabel.setForeground(Color.GRAY);
        okButton.setEnabled(false);
        selectAllButton.setEnabled(false);
        deselectAllButton.setEnabled(false);
    }

    private void exitLoadingState() {
        loading = false;
        selectAllButton.setEnabled(true);
        deselectAllButton.setEnabled(true);
        updateSelectionCount();  // This will update label and enable okButton if needed
    }

    private void updateSelectionCount() {
        // Don't update if still loading
        if (loading) {
            return;
        }

        TreePath[] paths = codeTemplateTree.getSelectionPaths();

        if (paths == null || paths.length == 0) {
            selectionCountLabel.setText("0 selected");
            okButton.setEnabled(false);
            return;
        }

        // Count only template nodes
        int count = 0;
        for (TreePath path : paths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node.getUserObject() instanceof TemplateTreeNode) {
                count++;
            }
        }

        selectionCountLabel.setText(count + " selected");
        okButton.setEnabled(count > 0);
    }

    private void selectAllTemplates() {
        List<TreePath> paths = new ArrayList<>();

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) codeTemplateTree.getModel().getRoot();

        // Traverse all nodes
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode libraryNode = (DefaultMutableTreeNode) root.getChildAt(i);

            // Add all template children
            for (int j = 0; j < libraryNode.getChildCount(); j++) {
                DefaultMutableTreeNode templateNode = (DefaultMutableTreeNode) libraryNode.getChildAt(j);
                paths.add(new TreePath(templateNode.getPath()));
            }
        }

        // Select all paths
        codeTemplateTree.setSelectionPaths(paths.toArray(new TreePath[0]));
    }

    // ========== Background Worker ==========

    private final class LoadLibrariesAndTemplatesWorker extends SwingWorker<LibrariesAndTemplatesResponse, Void> {
        @Override
        protected LibrariesAndTemplatesResponse doInBackground() throws Exception {
            return VersionHistoryServiceClient.getInstance().loadLibrariesAndTemplateMetadata();
        }

        @Override
        protected void done() {
            try {
                LibrariesAndTemplatesResponse response = get();

                librariesMetadata = response.getLibraries();
                templatesMetadata = response.getTemplates();

                buildTreeModel();
                applyFilter();

                exitLoadingState();
            } catch (Exception ex) {
                PlatformUI.MIRTH_FRAME.alertError(parent, "Failed to load libraries and code templates from repository.\n" + (ex.getMessage() != null ? ex.getMessage() : "Unknown error"));

                loading = false;
                selectionCountLabel.setText("Failed to load");
                selectionCountLabel.setForeground(Color.RED);
                okButton.setEnabled(false);
                selectAllButton.setEnabled(false);
                deselectAllButton.setEnabled(false);
            }
        }
    }

    // ========== Tree Building ==========

    private void buildTreeModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");

        // Build map: templateId -> metadata
        Map<String, RepoItemMetadata> templateMap = new HashMap<>();
        for (RepoItemMetadata meta : templatesMetadata) {
            templateMap.put(meta.getId(), meta);
        }

        // Track which templates have been added to libraries
        Set<String> addedTemplateIds = new HashSet<>();

        // ✅ Sort libraries by name (case-insensitive)
        List<LibraryMetadata> sortedLibraries = new ArrayList<>(librariesMetadata);
        sortedLibraries.sort(Comparator.comparing(lib -> {
            String name = lib.getName();
            return name != null ? name.toLowerCase() : "";
        }));

        // Add libraries with their templates
        for (LibraryMetadata library : sortedLibraries) {
            DefaultMutableTreeNode libraryNode = new DefaultMutableTreeNode(new LibraryTreeNode(library.getId(), library.getName()));

            // Add templates for this library
            List<String> templateIds = library.getCodeTemplateIds();
            if (templateIds != null) {
                List<RepoItemMetadata> libraryTemplates = new ArrayList<>();
                for (String templateId : templateIds) {
                    RepoItemMetadata templateMeta = templateMap.get(templateId);
                    if (templateMeta != null) {
                        libraryTemplates.add(templateMeta);
                        addedTemplateIds.add(templateId);
                    }
                }

                // Sort templates by name
                libraryTemplates.sort(Comparator.comparing(t -> {
                    String name = t.getName();
                    return name != null ? name.toLowerCase() : "";
                }));

                for (RepoItemMetadata templateMeta : libraryTemplates) {
                    DefaultMutableTreeNode templateNode = new DefaultMutableTreeNode(new TemplateTreeNode(templateMeta));
                    libraryNode.add(templateNode);
                }
            }

            // Always add library node (even if empty)
            root.add(libraryNode);
        }

        // Add "Unknown Library" for templates not in any library
        DefaultMutableTreeNode unknownLibraryNode = new DefaultMutableTreeNode(new LibraryTreeNode(null, "Unknown Library"));

        for (RepoItemMetadata templateMeta : templatesMetadata) {
            if (!addedTemplateIds.contains(templateMeta.getId())) {
                DefaultMutableTreeNode templateNode = new DefaultMutableTreeNode(new TemplateTreeNode(templateMeta));
                unknownLibraryNode.add(templateNode);
            }
        }

        // Add "Unknown Library" if it has templates
        if (unknownLibraryNode.getChildCount() > 0) {
            root.add(unknownLibraryNode);
        }

        // Store full tree for filtering
        fullTreeRoot = root;

        // Set tree model
        codeTemplateTree.setModel(new DefaultTreeModel(root));

        // Expand all libraries by default
        expandAll();
    }

    // ========== Filtering ==========

    private void applyFilter() {
        if (fullTreeRoot == null) {
            return;
        }

        String text = Optional.ofNullable(searchField.getText()).orElse("").trim();

        if (text.isEmpty()) {
            // No filter - show all
            codeTemplateTree.setModel(new DefaultTreeModel(fullTreeRoot));
            expandAll();
            return;
        }

        String needle = text.toLowerCase();

        // Build filtered tree
        DefaultMutableTreeNode filteredRoot = new DefaultMutableTreeNode("Root");

        // Iterate through libraries in full tree
        for (int i = 0; i < fullTreeRoot.getChildCount(); i++) {
            DefaultMutableTreeNode libraryNode = (DefaultMutableTreeNode) fullTreeRoot.getChildAt(i);
            LibraryTreeNode libraryData = (LibraryTreeNode) libraryNode.getUserObject();

            DefaultMutableTreeNode filteredLibraryNode = new DefaultMutableTreeNode(libraryData);

            // Check each template in this library
            for (int j = 0; j < libraryNode.getChildCount(); j++) {
                DefaultMutableTreeNode templateNode = (DefaultMutableTreeNode) libraryNode.getChildAt(j);
                TemplateTreeNode templateData = (TemplateTreeNode) templateNode.getUserObject();

                // Match against template name or ID
                String name = templateData.metadata.getName().toLowerCase();
                String id = templateData.metadata.getId().toLowerCase();

                if (name.contains(needle) || id.contains(needle)) {
                    filteredLibraryNode.add(new DefaultMutableTreeNode(templateData));
                }
            }

            // Only add library if it has matching templates
            if (filteredLibraryNode.getChildCount() > 0) {
                filteredRoot.add(filteredLibraryNode);
            }
        }

        codeTemplateTree.setModel(new DefaultTreeModel(filteredRoot));
        expandAll();
    }

    private void expandAll() {
        for (int i = 0; i < codeTemplateTree.getRowCount(); i++) {
            codeTemplateTree.expandRow(i);
        }
    }

    // ========== Import Action ==========

    private void onOkImport(ActionEvent evt) {
        TreePath[] selectedPaths = codeTemplateTree.getSelectionPaths();

        if (selectedPaths == null || selectedPaths.length == 0) {
            PlatformUI.MIRTH_FRAME.alertInformation(parent, "Please select at least one code template to import!");
            return;
        }

        // Collect selected templates
        List<TemplateTreeNode> selectedTemplates = new ArrayList<>();
        for (TreePath path : selectedPaths) {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObject = selectedNode.getUserObject();
            if (userObject instanceof TemplateTreeNode) {
                selectedTemplates.add((TemplateTreeNode) userObject);
            }
        }

        if (selectedTemplates.isEmpty()) {
            PlatformUI.MIRTH_FRAME.alertInformation(parent, "Please select code templates (not libraries) to import!");
            return;
        }

        // Create simple import dialog
        ImportDialog importDialog = new ImportDialog(new ArrayList<>(codeTemplateLibraries.values()), selectedTemplates.size());

        // Set import callback
        importDialog.setImportCallback(() -> {
            CodeTemplateLibrary selectedLib = importDialog.getSelectedLibrary();

            SwingWorker<ImportResult, String> worker = new SwingWorker<ImportResult, String>() {
                @Override
                protected ImportResult doInBackground() throws Exception {
                    int successCount = 0;
                    int failCount = 0;
                    List<String> errors = new ArrayList<>();

                    int total = selectedTemplates.size();
                    publish("Importing " + total + " template(s) into: " + selectedLib.getName());
                    publish("─".repeat(60));

                    for (int i = 0; i < total; i++) {
                        TemplateTreeNode templateNode = selectedTemplates.get(i);
                        RepoItemMetadata metadata = templateNode.metadata;

                        importDialog.setProgress(i + 1, total);
                        publish(String.format("[%d/%d] %s", i + 1, total, metadata.getName()));

                        try {
                            CodeTemplate template = VersionHistoryServiceClient.getInstance().loadCodeTemplateFromRepo(metadata);

                            if (template == null) {
                                failCount++;
                                errors.add(metadata.getName() + ": Could not load content");
                                publish("  ❌ Failed: Could not load content");
                                continue;
                            }

                            String error = doAddCodeTemplate(template, selectedLib);
                            if (error == null) {
                                successCount++;
                                publish("  ✅ Imported");
                            } else {
                                failCount++;
                                errors.add(metadata.getName() + ": " + error);
                                publish("  ❌ " + error);
                            }
                        } catch (Exception e) {
                            failCount++;
                            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                            errors.add(metadata.getName() + ": " + msg);
                            publish("  ❌ " + msg);
                        }

                        Thread.sleep(100);
                    }

                    return new ImportResult(successCount, failCount, errors);
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String msg : chunks) {
                        importDialog.appendLog(msg);
                    }
                }

                @Override
                protected void done() {
                    try {
                        ImportResult result = get();

                        importDialog.appendLog("─".repeat(60));
                        importDialog.complete(result.failCount > 0, result.successCount, result.failCount);

                        if (result.successCount > 0) {
                            importDialog.setRefreshNeeded(true);
                        }
                    } catch (Exception e) {
                        importDialog.appendLog("❌ Import failed: " + e.getMessage());
                        importDialog.complete(true, 0, selectedTemplates.size());
                    }
                }
            };

            worker.execute();
        });

        importDialog.setVisible(true);

        if (importDialog.isRefreshNeeded()) {
            parent.codeTemplatePanel.doRefreshCodeTemplates();
            dispose();
        }
    }

    /**
     * Add code template to library
     *
     * @param template        Template to add
     * @param selectedLibrary Target library
     * @return null if success, error message if failed
     */
    private String doAddCodeTemplate(CodeTemplate template, CodeTemplateLibrary selectedLibrary) {
        try {
            Client client = parent.mirthClient;

            String templateId = template.getId();
            CodeTemplate idTemplateMatch = getTemplateById(templateId);

            if (idTemplateMatch != null) {
                return "Template already exists";
            }

            Map<String, CodeTemplateLibrary> libraryMap = new HashMap<>();
            for (CodeTemplateLibrary library : client.getCodeTemplateLibraries(null, false)) {
                libraryMap.put(library.getId(), library);
            }

            List<CodeTemplateLibrary> libraries = new ArrayList<>();
            selectedLibrary.getCodeTemplates().add(template);
            libraries.add(selectedLibrary);

            Map<String, CodeTemplate> codeTemplateMap = new HashMap<>();
            for (CodeTemplateLibrary library : libraries) {
                library = new CodeTemplateLibrary(library);

                CodeTemplateLibrary matchingLibrary = libraryMap.get(library.getId());
                if (matchingLibrary != null) {
                    library.getEnabledChannelIds().addAll(matchingLibrary.getEnabledChannelIds());
                    library.getDisabledChannelIds().addAll(matchingLibrary.getDisabledChannelIds());
                    library.getDisabledChannelIds().removeAll(library.getEnabledChannelIds());

                    for (CodeTemplate serverCodeTemplate : matchingLibrary.getCodeTemplates()) {
                        boolean found = false;
                        for (CodeTemplate codeTemplate : library.getCodeTemplates()) {
                            if (serverCodeTemplate.getId().equals(codeTemplate.getId())) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            library.getCodeTemplates().add(serverCodeTemplate);
                        }
                    }
                }

                for (CodeTemplate codeTemplate : library.getCodeTemplates()) {
                    if (codeTemplate.getName() != null) {
                        codeTemplateMap.put(codeTemplate.getId(), codeTemplate);
                    }
                }

                libraryMap.put(library.getId(), library);
            }

            CodeTemplateLibrarySaveResult updateSummary = client.updateLibrariesAndTemplates(new ArrayList<>(libraryMap.values()), new HashSet<>(), new ArrayList<>(codeTemplateMap.values()), new HashSet<>(), true);

            if (!updateSummary.isOverrideNeeded()) {
                if (updateSummary.isLibrariesSuccess()) {
                    List<CodeTemplate> failedCodeTemplates = new ArrayList<>();
                    Throwable firstCause = null;

                    for (Entry<String, CodeTemplateLibrarySaveResult.CodeTemplateUpdateResult> entry : updateSummary.getCodeTemplateResults().entrySet()) {
                        if (!entry.getValue().isSuccess()) {
                            failedCodeTemplates.add(codeTemplateMap.get(entry.getKey()));
                            if (firstCause == null) {
                                firstCause = entry.getValue().getCause();
                            }
                        }
                    }

                    if (failedCodeTemplates.isEmpty()) {
                        return null;
                    }

                    if (firstCause != null) {
                        return firstCause.getMessage();
                    } else {
                        return "Unknown error";
                    }
                } else {
                    return updateSummary.getLibrariesCause().getMessage();
                }
            } else {
                return "One or more code templates or libraries is outdated";
            }

        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : "Unknown error";
        }
    }

    /**
     * Checks to see if the passed in template id already exists
     */
    private CodeTemplate getTemplateById(String id) throws ClientException {
        Client client = parent.mirthClient;
        try {
            for (CodeTemplate template : client.getAllCodeTemplates()) {
                if (template.getId().equalsIgnoreCase(id)) {
                    return template;
                }
            }
        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e);
        }

        return null;
    }

    // ========== Tree Node Classes ==========

    /**
     * Represents a library node in the tree
     */
    private static class LibraryTreeNode {
        String id;
        String name;

        LibraryTreeNode(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Represents a template node in the tree
     */
    private static class TemplateTreeNode {
        RepoItemMetadata metadata;

        TemplateTreeNode(RepoItemMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public String toString() {
            return metadata.getName();
        }
    }

    // ========== Custom Tree Cell Renderer ==========

    /**
     * Custom renderer to display libraries and templates with icons and formatting
     */
    private static class CodeTemplateTreeCellRenderer extends DefaultTreeCellRenderer {
        private Icon libraryIcon;
        private Icon templateIcon;

        public CodeTemplateTreeCellRenderer() {
            // Use default Swing icons
            libraryIcon = UIManager.getIcon("Tree.closedIcon");
            templateIcon = UIManager.getIcon("FileView.fileIcon");
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();

                if (userObject instanceof LibraryTreeNode) {
                    LibraryTreeNode libNode = (LibraryTreeNode) userObject;
                    setIcon(libraryIcon);

                    // Show template count
                    int count = node.getChildCount();
                    setText(libNode.name + " (" + count + ")");

                    // Gray out "Unknown Library"
                    if (libNode.id == null) {
                        setForeground(Color.GRAY);
                    }
                } else if (userObject instanceof TemplateTreeNode) {
                    TemplateTreeNode tempNode = (TemplateTreeNode) userObject;
                    setIcon(templateIcon);

                    // Show name with shortened ID
                    String displayText = tempNode.metadata.getName();
                    String id = tempNode.metadata.getId();

                    if (id != null && !id.isEmpty()) {
                        displayText += " (" + id + ")";
                    }

                    setText(displayText);
                }
            }

            return this;
        }
    }

    // ========== Helper Classes ==========

    /**
     * Simple DocumentListener helper
     */
    private static final class SimpleDoc implements DocumentListener {
        private final Runnable r;

        SimpleDoc(Runnable r) {
            this.r = r;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            r.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            r.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            r.run();
        }
    }

    /**
     * Result class for import operation
     */
    private static class ImportResult {
        final int successCount;
        final int failCount;
        final List<String> errors;

        ImportResult(int successCount, int failCount, List<String> errors) {
            this.successCount = successCount;
            this.failCount = failCount;
            this.errors = errors;
        }
    }

    /**
     * Simple import dialog: confirmation + library selection + progress + result
     */
    private class ImportDialog extends JDialog {

        private JLabel messageLabel;
        private JLabel libraryLabel;
        private JComboBox<String> libraryComboBox;
        private JProgressBar progressBar;
        private JTextArea logArea;
        private JScrollPane logScrollPane;
        private JButton importButton;
        private JButton closeButton;

        private final List<CodeTemplateLibrary> libraries;
        private CodeTemplateLibrary selectedLibrary;
        private boolean refreshNeeded = false;
        private boolean importing = false;

        private List<CodeTemplateLibrary> sortedLibs;
        private Runnable importCallback;

        public ImportDialog(List<CodeTemplateLibrary> libraries, int templateCount) {
            super(ImportCodeTemplateDialog.this, "Import Code Templates", true);
            this.libraries = libraries;

            initComponents(templateCount);
            initLayout();

            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            pack();
            setLocationRelativeTo(ImportCodeTemplateDialog.this);
        }

        private void initComponents(int templateCount) {
            messageLabel = new JLabel(String.format("<html><b>Import %d code template%s?</b></html>", templateCount, templateCount > 1 ? "s" : ""));

            libraryLabel = new JLabel("Select library:");

            sortedLibs = new ArrayList<>(libraries);
            sortedLibs.sort(Comparator.comparing(l -> {
                String n = l.getName();
                return n == null ? "" : n.toLowerCase();
            }));

            String[] libraryNames = sortedLibs.stream().map(l -> l.getName() == null ? "(unnamed)" : l.getName()).toArray(String[]::new);

            libraryComboBox = new JComboBox<>(libraryNames);
            progressBar = new JProgressBar(0, templateCount);
            progressBar.setStringPainted(true);
            progressBar.setVisible(false);

            logArea = new JTextArea(12, 50);
            logArea.setEditable(false);
            logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            logArea.setBackground(new Color(250, 250, 250));

            logScrollPane = new JScrollPane(logArea);
            logScrollPane.setVisible(false);

            importButton = new JButton("Import");
            importButton.addActionListener(e -> {
                int idx = libraryComboBox.getSelectedIndex();
                if (idx >= 0 && idx < sortedLibs.size()) {
                    selectedLibrary = sortedLibs.get(idx);
                    startImport();
                }
            });

            closeButton = new JButton("Cancel");
            closeButton.addActionListener(e -> {
                if (!importing) {
                    dispose();
                }
            });
        }

        private void initLayout() {
            setLayout(new MigLayout("insets 16, hidemode 3, fill", "[pref][grow, fill]", "[]10[]8[]10[grow]12[]"));

            // Row 1: message
            add(messageLabel, "span 2, wrap");

            // Row 2: library select
            add(libraryLabel, "ay 0.5");
            add(libraryComboBox, "growx, pushx, wmin 280, wrap");

            // Row 3: progress (hidden initially)
            add(progressBar, "span 2, growx, pushx, wrap");

            // Row 4: log (hidden initially)
            add(logScrollPane, "span 2, grow, push, wmin 520, hmin 220, wrap");

            // Row 5: buttons (right aligned)
            add(importButton, "span 2, split 2, right, w 110!");
            add(closeButton, "w 110!");
        }

        private void startImport() {
            importing = true;

            messageLabel.setText("<html><b>Importing templates...</b></html>");

            libraryLabel.setVisible(false);
            libraryComboBox.setVisible(false);

            progressBar.setVisible(true);
            logScrollPane.setVisible(true);

            importButton.setEnabled(false);
            closeButton.setText("Close");
            closeButton.setEnabled(false);

            pack();

            if (importCallback != null) {
                importCallback.run();
            }
        }

        public void appendLog(String message) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }

        public void setProgress(int current, int total) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setMaximum(total);
                progressBar.setValue(current);
                progressBar.setString(String.format("%d / %d", current, total));
            });
        }

        public void complete(boolean hasErrors, int successCount, int failCount) {
            SwingUtilities.invokeLater(() -> {
                importing = false;

                progressBar.setValue(progressBar.getMaximum());

                if (hasErrors) {
                    messageLabel.setText(String.format("<html><b style='color: #CC6600;'>\u26A0 Import completed: %d succeeded, %d failed</b></html>", successCount, failCount));
                } else {
                    messageLabel.setText(String.format("<html><b style='color: #008000;'>\u2713 Import completed successfully (%d templates)</b></html>", successCount));
                }

                closeButton.setEnabled(true);
                closeButton.requestFocusInWindow();
                pack();
            });
        }

        public void setImportCallback(Runnable callback) {
            this.importCallback = callback;
        }

        public CodeTemplateLibrary getSelectedLibrary() {
            return selectedLibrary;
        }

        public void setRefreshNeeded(boolean needed) {
            this.refreshNeeded = needed;
        }

        public boolean isRefreshNeeded() {
            return refreshNeeded;
        }
    }
}