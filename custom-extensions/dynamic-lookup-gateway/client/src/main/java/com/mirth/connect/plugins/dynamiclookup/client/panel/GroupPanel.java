/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.client.panel;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.plugins.dynamiclookup.client.dialog.LookupGroupDialog;
import com.mirth.connect.plugins.dynamiclookup.client.exception.LookupApiClientException;
import com.mirth.connect.plugins.dynamiclookup.client.model.LookupGroupTableModel;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.client.util.FileChooser;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.request.ImportLookupGroupRequest;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.ExportGroupPagedResponse;
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
import javax.swing.BorderFactory;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

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
        JMenuItem editItem = new JMenuItem("Edit");
        editItem.addActionListener(e -> handleEditGroup());
        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(e -> handleDeleteGroup());
        JMenuItem exportItem = new JMenuItem("Export JSON");
        exportItem.addActionListener(e -> handleExportJson());

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
            if (file == null || !checkJsonFile(file)) return;

            int result = JOptionPane.showConfirmDialog(
                    parent,
                    "<html>If the group you're importing already exists,<br>"
                            + "<b>its information will be updated</b> and <b>all existing values will be permanently deleted</b> and replaced.<br><br>"
                            + "Do you want to proceed with updating the group?</html>",
                    "Confirm Import Overwrite",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (result != JOptionPane.YES_OPTION) return;

            JDialog progressDialog = new JDialog(parent, "Importing JSON", true);
            JProgressBar progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            JLabel statusLabel = new JLabel("Imported 0 entries");
            JButton cancelButton = new JButton("Cancel");
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(cancelButton);

            progressDialog.setLayout(new BorderLayout(10, 10));
            progressDialog.add(statusLabel, BorderLayout.NORTH);
            progressDialog.add(progressBar, BorderLayout.CENTER);
            progressDialog.add(buttonPanel, BorderLayout.SOUTH);
            progressDialog.setSize(350, 120);
            progressDialog.setLocationRelativeTo(parent);
            progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

            SwingWorker<Void, int[]> importWorker = new SwingWorker<Void, int[]>() {
                int finalGroupId = -1;

                @Override
                protected Void doInBackground() throws Exception {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(file);
                    int totalCount = 0;
                    JsonNode valuesNode = root.get("values");
                    if (valuesNode != null && valuesNode.isObject()) {
                        totalCount = valuesNode.size();
                    }

                    try (JsonParser parser = mapper.getFactory().createParser(file)) {
                        int groupId = -1;
                        boolean groupImported = false;
                        int batchSize = 1000;
                        int processed = 0;
                        Map<String, String> batch = new LinkedHashMap<>();

                        JsonToken token = parser.nextToken(); // Advance to first token
                        if (token != JsonToken.START_OBJECT) {
                            throw new IllegalStateException("Expected START_OBJECT at beginning of JSON file.");
                        }

                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            String fieldName = parser.getCurrentName();
                            if ("group".equals(fieldName)) {
                                parser.nextToken();
                                JsonNode groupNode = parser.readValueAsTree();

                                ImportLookupGroupRequest request = new ImportLookupGroupRequest();
                                request.setGroup(JsonUtils.fromJson(groupNode.toString(), LookupGroup.class));
                                request.setValues(Collections.emptyMap());
                                request.validate();

                                ImportLookupGroupResponse response = LookupServiceClient.getInstance().importGroup(true, JsonUtils.toJson(request));
                                groupId = response.getGroupId();
                                groupImported = true;
                                finalGroupId = groupId;
                            } else if ("values".equals(fieldName)) {
                                if (!groupImported) {
                                    throw new IllegalStateException("JSON file does not contain a valid 'group' object.");
                                }
                                parser.nextToken();
                                while (parser.nextToken() != JsonToken.END_OBJECT && !isCancelled()) {
                                    String key = parser.getCurrentName();
                                    parser.nextToken();
                                    String value = parser.getValueAsString();
                                    batch.put(key, value);
                                    processed++;

                                    if (batch.size() >= batchSize) {
                                        LookupServiceClient.getInstance().importValues(groupId, false, batch);
                                        batch.clear();
                                        publishProgress(processed, totalCount);
                                    }
                                }

                                if (!batch.isEmpty() && !isCancelled()) {
                                    LookupServiceClient.getInstance().importValues(groupId, false, batch);
                                    publishProgress(processed, totalCount);
                                }
                            } else {
                                parser.skipChildren();
                            }
                        }

                        Frame.userPreferences.put("currentDirectory", file.getParent());
                    } catch (Exception e) {
                        logger.error("Failed to import lookup group from JSON file", e);
                        throw e;
                    }

                    return null;
                }

                private void publishProgress(int processed, int total) {
                    int progress = total > 0 ? (int) ((processed / (double) total) * 100) : 0;
                    progress = Math.min(progress, 100);

                    publish(new int[]{progress, processed, total});
                }

                @Override
                protected void process(List<int[]> chunks) {
                    if (!chunks.isEmpty()) {
                        int[] latest = chunks.get(chunks.size() - 1);
                        progressBar.setIndeterminate(false);
                        progressBar.setValue(latest[0]);
                        statusLabel.setText("Imported " + latest[1] + " of " + latest[2] + " entries");
                    }
                }

                @Override
                protected void done() {
                    progressDialog.dispose();

                    if (isCancelled()) {
                        JOptionPane.showMessageDialog(parent, "Import cancelled by user.", "Import Cancelled", JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    try {
                        get(); // triggers exception handling if doInBackground failed

                        JOptionPane.showMessageDialog(parent, "Import completed successfully.", "Import Complete", JOptionPane.INFORMATION_MESSAGE);

                        // Update table
                        if (finalGroupId != -1) {
                            LookupGroup selectedGroup = groupTable.getSelectedRow() >= 0
                                    ? groupTableModel.getGroup(groupTable.getSelectedRow())
                                    : null;
                            try {
                                LookupGroup importedGroup = LookupServiceClient.getInstance().getGroupById(finalGroupId);
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

                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        showError("Import failed: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // restore interrupt status
                        showError("Import was interrupted.");
                    }
                }
            };

            cancelButton.addActionListener(e -> importWorker.cancel(true));
            progressDialog.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    if (JOptionPane.showConfirmDialog(progressDialog, "Cancel import?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        importWorker.cancel(true);
                    }
                }
            });

            importWorker.execute();
            progressDialog.setVisible(true);
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

        // Progress UI setup
        JDialog progressDialog = new JDialog(parent, "Exporting JSON", true);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        JLabel statusLabel = new JLabel("Exported 0 of ? entries");

        JButton cancelButton = new JButton("Cancel");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);

        progressDialog.setLayout(new BorderLayout(10, 10));
        progressDialog.add(statusLabel, BorderLayout.NORTH);
        progressDialog.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(buttonPanel, BorderLayout.SOUTH);
        progressDialog.setSize(350, 120);
        progressDialog.setLocationRelativeTo(parent);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        SwingWorker<Void, int[]> exportWorker = new SwingWorker<Void, int[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                int offset = 0;
                int limit = 10000;
                int processed = 0;
                int total = -1;
                Date exportDate = null;

                Map<String, String> allValues = new LinkedHashMap<>();

                try {
                    ExportGroupPagedResponse page = LookupServiceClient.getInstance().exportGroupPaged(group.getId(), offset, limit);

                    exportDate = page.getExportDate();
                    total = page.getTotalCount();
                    allValues.putAll(page.getValues());
                    processed += page.getValues().size();
                    publishProgress(processed, total);

                    while (!isCancelled() && page.getPagination().isHasMore()) {
                        offset += limit;
                        page = LookupServiceClient.getInstance().exportGroupPaged(group.getId(), offset, limit);
                        allValues.putAll(page.getValues());
                        processed += page.getValues().size();
                        publishProgress(processed, total);
                    }

                    ExportLookupGroupResponse exportResponse = new ExportLookupGroupResponse();
                    exportResponse.setGroup(group);
                    exportResponse.setValues(allValues);
                    exportResponse.setExportDate(exportDate);

                    String json = JsonUtils.toJsonPretty(exportResponse);
                    java.nio.file.Files.write(file.toPath(), json.getBytes());

                } catch (Exception ex) {
                    logger.error("Failed to export group to JSON", ex);
                    throw ex;
                }

                return null;
            }

            private void publishProgress(int processed, int total) {
                int progress = total > 0 ? (int) ((processed / (double) total) * 100) : 0;
                progress = Math.min(progress, 100);

                publish(new int[]{progress, processed, total});
            }

            @Override
            protected void process(List<int[]> chunks) {
                if (!chunks.isEmpty()) {
                    int[] latest = chunks.get(chunks.size() - 1);
                    progressBar.setValue(latest[0]);
                    statusLabel.setText("Exported " + latest[1] + " of " + latest[2] + " entries");
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                if (isCancelled()) {
                    JOptionPane.showMessageDialog(parent, "Export cancelled by user.", "Cancelled", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                try {
                    get(); // triggers exception handling if doInBackground failed

                    JOptionPane.showMessageDialog(parent, "JSON export completed.", "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    showError("Export failed: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // restore interrupt status
                    showError("Export was interrupted.");
                }
            }
        };

        cancelButton.addActionListener(e -> exportWorker.cancel(true));

        progressDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int confirm = JOptionPane.showConfirmDialog(
                        progressDialog,
                        "Export is still in progress. Do you want to cancel?",
                        "Confirm Cancel",
                        JOptionPane.YES_NO_OPTION
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    exportWorker.cancel(true);
                }
            }
        });

        exportWorker.execute();
        progressDialog.setVisible(true);
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
}