package com.mirth.connect.plugins.dynamiclookup.client.panel;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.plugins.dynamiclookup.client.dialog.LookupGroupDialog;
import com.mirth.connect.plugins.dynamiclookup.client.exception.LookupApiClientException;
import com.mirth.connect.plugins.dynamiclookup.client.model.LookupGroupTableModel;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.client.util.FileChooser;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.ExportLookupGroupResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.ImportLookupGroupResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

import net.miginfocom.swing.MigLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.JOptionPane;
import javax.swing.JFileChooser;
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.BorderFactory;
import javax.swing.ListSelectionModel;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.io.File;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-05-13 10:25 AM
 */
public class GroupPanel extends JPanel {
    private final Logger logger = LogManager.getLogger(this.getClass());

    private final Frame parent = PlatformUI.MIRTH_FRAME;
    private JTextField groupFilterField;
    private JButton addGroupButton;
    private JButton importButton;
    private JTable groupTable;
    private LookupGroupTableModel groupTableModel;
    private JPopupMenu groupPopupMenu;

    public GroupPanel() {
        initComponents();
        initLayout();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        // Group Filter
        groupFilterField = new JTextField();
        groupFilterField.setToolTipText("Filter groups by name");
        groupFilterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterGroups();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterGroups();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterGroups();
            }
        });

        // Group Buttons
        addGroupButton = new JButton("Add");
        addGroupButton.addActionListener(e -> handleAddGroup());

        // Import button
        importButton = new JButton("Import JSON");
        importButton.setToolTipText("Import a lookup group and its values from a JSON file");
        importButton.addActionListener(e -> handleImportJson());

        // Group Table
        groupTableModel = new LookupGroupTableModel();
        groupTable = new JTable(groupTableModel);
        groupTable.setRowHeight(26);
        groupTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Popup Menu
        groupPopupMenu = new JPopupMenu();
        JMenuItem detailsItem = new JMenuItem("Detail");
        detailsItem.addActionListener(e -> {
            int selectedRow = groupTable.getSelectedRow();
            if (selectedRow >= 0) {
                LookupGroup group = groupTableModel.getGroup(selectedRow);
                String details = String.format(
                        "ID: %d\nName: %s\nDescription: %s\nVersion: %s\nCache Size: %d\nCache Policy: %s\nCreated Date: %s\nUpdated Date: %s",
                        group.getId(),
                        group.getName(),
                        group.getDescription() != null ? group.getDescription() : "",
                        group.getVersion() != null ? group.getVersion() : "",
                        group.getCacheSize(),
                        group.getCachePolicy() != null ? group.getCachePolicy() : "",
                        new SimpleDateFormat("yyyy-MM-dd hh:mm a").format(group.getCreatedDate()),
                        new SimpleDateFormat("yyyy-MM-dd hh:mm a").format(group.getUpdatedDate())
                );
                JOptionPane.showMessageDialog(parent, details, "Group Details", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        JMenuItem editItem = new JMenuItem("Edit");
        editItem.addActionListener(e -> handleEditGroup());
        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(e -> handleDeleteGroup());
        JMenuItem exportItem = new JMenuItem("Export JSON");
        exportItem.addActionListener(e -> handleExportJson());

        groupPopupMenu.add(detailsItem);
        groupPopupMenu.add(editItem);
        groupPopupMenu.add(removeItem);
        groupPopupMenu.add(exportItem);

        // Attach popup menu to table
        groupTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            private void showPopup(MouseEvent e) {
                int row = groupTable.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    groupTable.setRowSelectionInterval(row, row);
                    groupPopupMenu.show(groupTable, e.getX(), e.getY());
                }
            }
        });
    }

    // In GroupPanel.java, update initLayout
    private void initLayout() {
        setLayout(new MigLayout("insets 0, fill"));

        JPanel contentPanel = new JPanel(new MigLayout("insets 8, fill"));
        contentPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        contentPanel.setBorder(BorderFactory.createTitledBorder("Lookup Groups"));

        contentPanel.add(groupFilterField, "w 150!, growx, split 3");
        contentPanel.add(addGroupButton);
        contentPanel.add(importButton, "wrap");
        contentPanel.add(new JScrollPane(groupTable), "grow, push, wrap");

        add(contentPanel, "grow, push");
    }

    private void filterGroups() {
        String filterText = groupFilterField.getText().trim().toLowerCase();
        groupTableModel.setFilter(filterText);
    }

    private void handleAddGroup() {
        LookupGroup lookupGroup = new LookupGroup();
        LookupGroupDialog dialog = new LookupGroupDialog(parent, lookupGroup, false);
        if (dialog.isSaved()) {
            int selectedRow = groupTable.getSelectedRow();
            groupTableModel.addGroup(lookupGroup);
            if (selectedRow >= 0 && selectedRow < groupTableModel.getRowCount()) {
                groupTable.setRowSelectionInterval(selectedRow, selectedRow);
            }
        }
    }

    private void handleImportJson() {
        JFileChooser importFileChooser = new JFileChooser();
        importFileChooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));

        File currentDir = new File(Frame.userPreferences.get("currentDirectory", ""));
        if (currentDir.exists()) {
            importFileChooser.setCurrentDirectory(currentDir);
        }

        if (importFileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = importFileChooser.getSelectedFile();
            if (file != null) {
                if (!checkJsonFile(file)) {
                    return;
                }

                int result = JOptionPane.showConfirmDialog(
                        parent,
                        "<html>If the group you're importing already exists,<br>"
                                + "<b>its information will be updated</b> and <b>all existing values will be permanently deleted</b> and replaced.<br><br>"
                                + "Do you want to proceed with updating the group?</html>",
                        "Confirm Import Overwrite",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                boolean updateIfExists = (result == JOptionPane.YES_OPTION);

                LoadingDialog loadingDialog = new LoadingDialog(parent, "Importing group, please wait...");

                SwingWorker<ImportLookupGroupResponse, Void> worker = new SwingWorker<ImportLookupGroupResponse, Void>() {
                    @Override
                    protected ImportLookupGroupResponse doInBackground() {
                        try {
                            String jsonContent = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                            return LookupServiceClient.getInstance().importGroup(updateIfExists, jsonContent);
                        } catch (LookupApiClientException e) {
                            SwingUtilities.invokeLater(() -> showError(e.getError().getMessage()));
                        } catch (Exception e) {
                            logger.error("Unexpected error during import", e);
                            SwingUtilities.invokeLater(() ->
                                    showError("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage()));
                        }
                        return null;
                    }

                    @Override
                    protected void done() {
                        loadingDialog.dispose();

                        try {
                            ImportLookupGroupResponse response = get();
                            if (response == null) {
                                return;
                            }

                            String nl = System.lineSeparator();
                            StringBuilder message = new StringBuilder();
                            message.append("Group ID: ").append(response.getGroupId()).append(nl)
                                    .append("Imported Values: ").append(response.getImportedCount()).append(nl);

                            if (response.isSuccessful() && !response.hasErrors()) {
                                JOptionPane.showMessageDialog(parent, message.toString(), "Import Successful", JOptionPane.INFORMATION_MESSAGE);
                            } else if (response.isSuccessful() && response.hasErrors()) {
                                message.insert(0, "Import completed with warnings." + nl + nl);

                                StringBuilder errorText = new StringBuilder();
                                for (String err : response.getErrors()) {
                                    errorText.append("- ").append(err).append(nl);
                                }

                                JTextArea textArea = new JTextArea(errorText.toString(), 10, 50);
                                textArea.setEditable(false);
                                textArea.setLineWrap(true);
                                textArea.setWrapStyleWord(true);

                                JScrollPane scrollPane = new JScrollPane(textArea);
                                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

                                JPanel panel = new JPanel(new BorderLayout(10, 10));
                                panel.add(new JLabel(message.toString()), BorderLayout.NORTH);
                                panel.add(scrollPane, BorderLayout.CENTER);

                                JOptionPane.showMessageDialog(parent, panel, "Import Completed with Warnings", JOptionPane.WARNING_MESSAGE);
                            } else {
                                message.insert(0, "Import failed." + nl + nl);
                                if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                                    for (String err : response.getErrors()) {
                                        message.append("- ").append(err).append(nl);
                                    }
                                }
                                JOptionPane.showMessageDialog(parent, message.toString(), "Import Failed", JOptionPane.ERROR_MESSAGE);
                            }

                            // Update table
                            if (response.isSuccessful()) {
                                // Capture selected group before update
                                LookupGroup selectedGroup = groupTable.getSelectedRow() >= 0
                                        ? groupTableModel.getGroup(groupTable.getSelectedRow())
                                        : null;
                                try {
                                    LookupGroup importedGroup = LookupServiceClient.getInstance().getGroupById(response.getGroupId());
                                    groupTableModel.addOrUpdateGroup(importedGroup);

                                    if (selectedGroup != null) {
                                        int visibleIndex = groupTableModel.getFilteredIndexByGroupId(selectedGroup.getId());
                                        if (visibleIndex >= 0) {
                                            groupTable.setRowSelectionInterval(visibleIndex, visibleIndex);
                                            groupTable.scrollRectToVisible(groupTable.getCellRect(visibleIndex, 0, true));
                                        }
                                    }
                                } catch (Exception ex) {
                                    logger.warn("Imported group added, but failed to update table", ex);
                                }
                            }

                            Frame.userPreferences.put("currentDirectory", file.getParent());

                        } catch (Exception ex) {
                            logger.error("Failed to retrieve import result", ex);
                            showError("Unexpected error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
                        }
                    }
                };

                worker.execute();
                loadingDialog.setVisible(true); // Blocks until dialog is disposed
            }
        }
    }

    private boolean checkJsonFile(File file) {
        // 1. Extension check
        if (!file.getName().toLowerCase().endsWith(".json")) {
            showError("File does not have a .json extension.");
            return false;
        }

        // 2. MIME type check
        try {
            Path path = file.toPath();
            String mimeType = Files.probeContentType(path);
            if (mimeType == null || !(
                    mimeType.equals("application/json") ||
                            mimeType.equals("text/plain")
            )) {
                showError("File does not appear to be a valid JSON (detected type: " + mimeType + ").");
                return false;
            }
        } catch (IOException e) {
            showError("Failed to detect file type: " + e.getMessage());
            return false;
        }

        return true;
    }

    private void handleEditGroup() {
        int selectedRow = groupTable.getSelectedRow();
        if (selectedRow >= 0) {
            LookupGroup group = groupTableModel.getGroup(selectedRow);
            LookupGroup copy = new LookupGroup(group);
            LookupGroupDialog dialog = new LookupGroupDialog(parent, copy, true);
            if (dialog.isSaved()) {
                groupTableModel.updateGroupById(copy);

                // Reselect and scroll to updated row if it's still visible
                int visibleIndex = groupTableModel.getFilteredIndexByGroupId(copy.getId());
                if (visibleIndex >= 0) {
                    groupTable.setRowSelectionInterval(visibleIndex, visibleIndex);
                    groupTable.scrollRectToVisible(groupTable.getCellRect(visibleIndex, 0, true));
                }
            }
        }
    }

    private void handleDeleteGroup() {
        int selectedRow = groupTable.getSelectedRow();
        if (selectedRow >= 0) {
            LookupGroup group = groupTableModel.getGroup(selectedRow);
            int confirm = JOptionPane.showConfirmDialog(parent,
                    "Are you sure you want to delete group: " + group.getName() + "?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            // remove from server
            try {
                LookupServiceClient.getInstance().deleteGroup(group.getId());

                // remove from UI
                groupTableModel.removeGroup(selectedRow);
                if (groupTableModel.getRowCount() > 0) {
                    int newIndex = Math.min(selectedRow, groupTableModel.getRowCount() - 1);
                    groupTable.setRowSelectionInterval(newIndex, newIndex);
                } else {
                    groupTable.clearSelection();
                }
            } catch (LookupApiClientException e) {
                showError(e.getError().getMessage());
            } catch (Exception e) {
                logger.error("Unexpected error while remove value", e);
                showError("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }
    }

    private void handleExportJson() {
        int selectedRow = groupTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        LookupGroup group = groupTableModel.getGroup(selectedRow);

        String groupNameSlug = group.getName().toLowerCase().replaceAll("[^a-z0-9]+", "_");
        String dateString = new SimpleDateFormat("yyyy_MM_dd_HH_mm").format(new Date());
        String defaultFileName = "lookup_group_" + groupNameSlug + "_export_" + dateString + ".json";

        final File file = new FileChooser().createFileForExport(parent, defaultFileName, "json");
        if (file == null) {
            return;
        }

        try {
            ExportLookupGroupResponse response = LookupServiceClient.getInstance().exportGroup(group.getId());

            // Serialize response to JSON
            String json = JsonUtils.toJsonPretty(response);

            // Write to file
            java.nio.file.Files.write(file.toPath(), json.getBytes());

            JOptionPane.showMessageDialog(parent, "Exported successfully to:\n" + file.getAbsolutePath(), "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            logger.error("Failed to export group JSON", ex);
            showError("Failed to export JSON: " + ex.getMessage());
        }
    }

    // Public methods for external interaction
    public void addGroupSelectionListener(ListSelectionListener listener) {
        groupTable.getSelectionModel().addListSelectionListener(listener);
    }

    public LookupGroup getSelectedGroup() {
        int selectedRow = groupTable.getSelectedRow();
        return selectedRow >= 0 ? groupTableModel.getGroup(selectedRow) : null;
    }

    public void clearGroups() {
        groupTableModel.clear();
    }

    public void updateGroupTable(List<LookupGroup> groups) {
        groupTableModel.setGroups(groups);
    }

    private void showError(String err) {
        PlatformUI.MIRTH_FRAME.alertError(parent, err);
    }

    public class LoadingDialog extends JDialog {
        public LoadingDialog(Frame parent, String message) {
            super(parent, "Please wait...", true);
            setLayout(new BorderLayout(10, 10));
            setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            setResizable(false);

            JLabel label = new JLabel(message);
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);

            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            panel.add(label, BorderLayout.NORTH);
            panel.add(progressBar, BorderLayout.CENTER);

            add(panel);
            pack();
            setLocationRelativeTo(parent);
        }
    }
}