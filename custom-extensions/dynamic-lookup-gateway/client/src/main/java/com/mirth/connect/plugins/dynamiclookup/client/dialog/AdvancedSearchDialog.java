/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.client.dialog;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.table.AbstractTableModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.plugins.dynamiclookup.shared.model.AdvancedJsonFilterState;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupProperties;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonCondition;
import com.mirth.connect.plugins.dynamiclookup.shared.model.json.JsonOperator;
import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

import net.miginfocom.swing.MigLayout;

public class AdvancedSearchDialog extends MirthDialog {
    private final Logger logger = LogManager.getLogger(this.getClass());

    // Split layout
    private JSplitPane splitPane;
    private JPanel leftPanel;
    private JPanel rightPanel;

    private JTextField keyPatternField;
    private JTable conditionTable;
    private ConditionTableModel conditionTableModel;

    private JButton loadFilterButton;
    private JButton deleteFilterButton;
    private DefaultListModel<SavedFilterEntry> savedFilterListModel;
    private JList<SavedFilterEntry> savedFilterList;

    private JButton saveFilterButton;
    private JButton applyButton;
    private JButton cancelButton;

    private boolean okPressed;
    private AdvancedJsonFilterState currentState;
    private final List<SavedFilterEntry> savedFilters = new ArrayList<>();

    private Frame parent;

    public AdvancedSearchDialog(Frame parent, AdvancedJsonFilterState state) {
        super(parent, true);
        this.parent = parent;
        this.currentState = state != null ? state : new AdvancedJsonFilterState();

        initComponents();
        initLayout();

        resetComponents(state);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Advanced Search");
        pack();
        setLocationRelativeTo(parent);

        // ---- Load server settings
        loadSavedFiltersFromServer();

        // ---- Now show the dialog ----
        setVisible(true);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        // Left panel
        keyPatternField = new JTextField();

        conditionTableModel = new ConditionTableModel();

        conditionTable = new JTable(conditionTableModel);
        conditionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conditionTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        conditionTable.setRowHeight(26);

        // Right panel: Saved Filters UI (no actions yet)
        loadFilterButton = new JButton("Load");
        loadFilterButton.addActionListener(evt -> loadFilter());
        loadFilterButton.setEnabled(false);

        deleteFilterButton = new JButton("Delete");
        deleteFilterButton.addActionListener(evt -> deleteFilter());
        deleteFilterButton.setEnabled(false);

        savedFilterListModel = new DefaultListModel<>();
        savedFilterListModel.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                updateSavedFilterButtons();
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                updateSavedFilterButtons();
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                updateSavedFilterButtons();
            }
        });

        savedFilterList = new JList<>(savedFilterListModel);
        savedFilterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        savedFilterList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }

            boolean hasSelection = savedFilterList.getSelectedValue() != null;
            loadFilterButton.setEnabled(hasSelection);
            deleteFilterButton.setEnabled(hasSelection);
        });
        savedFilterList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isConsumed() && savedFilterList.getSelectedValue() != null) {
                    e.consume();
                    loadFilter();
                }
            }
        });

        // footer
        saveFilterButton = new JButton("Save Filter");
        saveFilterButton.addActionListener(evt -> saveFilter());

        applyButton = new JButton("Apply");
        applyButton.addActionListener(evt -> apply());

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(evt -> close());
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 12, novisualpadding, hidemode 3, fill"));

        //
        // LEFT PANEL (Controls)
        //
        leftPanel = new JPanel(new MigLayout("novisualpadding, hidemode 0, align left top, insets 10, fill", "30[right][grow,fill]", ""));
        leftPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        leftPanel.setBorder(BorderFactory.createTitledBorder("Controls"));

        JLabel keyLabel = new JLabel("Key Pattern:");
        leftPanel.add(keyLabel, "right");
        leftPanel.add(keyPatternField, "growx, wrap");

        JLabel keyHint = new JLabel("Use SQL pattern (% and _). Example: 2025-11-21_%");
        keyHint.setFont(keyHint.getFont().deriveFont(keyHint.getFont().getSize2D() - 1f));
        leftPanel.add(new JLabel(), ""); // spacer
        leftPanel.add(keyHint, "wrap");

        JLabel condLabel = new JLabel("JSON Conditions (AND):");
        leftPanel.add(condLabel, "gaptop 10, right");

        JScrollPane tableScroll = new JScrollPane(conditionTable);
        leftPanel.add(tableScroll, "grow, pushy, hmin 160, wrap");

        JPanel rowButtons = new JPanel(new MigLayout("insets 0", "[]5[]", ""));
        rowButtons.setBackground(UIConstants.BACKGROUND_COLOR);

        JButton addRowButton = new JButton("Add condition");
        JButton removeRowButton = new JButton("Remove selected");

        addRowButton.addActionListener(e -> addConditionRow());
        removeRowButton.addActionListener(e -> removeSelectedRow());

        rowButtons.add(addRowButton);
        rowButtons.add(removeRowButton);

        leftPanel.add(new JLabel(), ""); // spacer
        leftPanel.add(rowButtons, "growx, wrap");

        //
        // RIGHT PANEL (Saved Filters)
        //
        rightPanel = new JPanel(new MigLayout("novisualpadding, hidemode 0, insets 10, fill", "[grow,fill]", "[]10[grow,fill]"));
        rightPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        rightPanel.setBorder(BorderFactory.createTitledBorder("Saved Filters"));

        JPanel toolbar = new JPanel(new MigLayout("insets 0", "[]5[]5[]", ""));
        toolbar.setBackground(UIConstants.BACKGROUND_COLOR);
        toolbar.add(loadFilterButton);
        toolbar.add(deleteFilterButton);

        rightPanel.add(toolbar, "growx, wrap");

        JScrollPane listScroll = new JScrollPane(savedFilterList);
        rightPanel.add(listScroll, "grow, push");

        //
        // Split pane (draggable divider)
        //
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setResizeWeight(0.75); // left takes most space by default
        splitPane.setOneTouchExpandable(true); // collapse/expand arrows
        splitPane.setDividerSize(10);

        add(splitPane, "grow, push, wrap");

        add(new JSeparator(), "newline, sx, growx");

        // Footer panel (left = Save Filter..., right = Apply/Cancel)
        JPanel footerPanel = new JPanel(new MigLayout("insets 0, fill", "[grow][][]", "[]"));
        footerPanel.setBackground(UIConstants.BACKGROUND_COLOR);

        footerPanel.add(saveFilterButton, "left");
        footerPanel.add(applyButton, "right");
        footerPanel.add(cancelButton, "right");

        add(footerPanel, "newline, sx, growx");
    }

    private void resetComponents(AdvancedJsonFilterState state) {
        if (state == null) {
            return;
        }

        // Reset key pattern (null-safe)
        String keyPattern = state.getKeyPattern();
        keyPatternField.setText(keyPattern != null ? keyPattern : "");

        // Reset JSON conditions table (defensive copy to avoid mutating saved preset)
        List<JsonCondition> src = state.getConditions();
        List<JsonCondition> copy = new ArrayList<>();

        if (src != null) {
            for (JsonCondition c : src) {
                if (c == null) {
                    continue;
                }
                JsonCondition cc = new JsonCondition();
                cc.setField(c.getField());
                cc.setOp(c.getOp());
                cc.setValue(c.getValue());
                copy.add(cc);
            }
        }

        conditionTableModel.setConditions(copy);
        conditionTable.clearSelection();

        // Refresh UI
        conditionTable.revalidate();
        conditionTable.repaint();
    }

    private void updateSavedFilterButtons() {
        boolean hasItems = !savedFilterListModel.isEmpty();
        boolean hasSelection = savedFilterList.getSelectedValue() != null;

        loadFilterButton.setEnabled(hasItems && hasSelection);
        deleteFilterButton.setEnabled(hasItems && hasSelection);
    }

    private void loadSavedFiltersFromServer() {
        SwingWorker<LookupProperties, Void> worker = new SwingWorker<LookupProperties, Void>() {

            @Override
            protected LookupProperties doInBackground() throws Exception {
                Properties props = parent.mirthClient.getPluginProperties("Lookup Table Management System");
                return LookupProperties.fromProperties(props);
            }

            @Override
            protected void done() {
                try {
                    LookupProperties pluginProps = get();
                    String json = pluginProps.getAdvancedSearchSavedFiltersJson();
                    if (json == null || json.trim().isEmpty()) {
                        json = "[]";
                    }
                    applySavedFiltersJson(json);
                } catch (Exception ex) {
                    showError("Failed to load saved filters.\n" + ex.getMessage());
                    applySavedFiltersJson("[]"); // fallback
                }
            }
        };

        worker.execute();
    }

    private void saveSavedFiltersToServer() {
        try {
            // Build list from model (single source of truth)
            List<SavedFilterEntry> list = Collections.list(savedFilterListModel.elements());

            String json = JsonUtils.toJson(list);

            LookupProperties props = new LookupProperties(false, 0, json);
            parent.mirthClient.setPluginProperties("Lookup Table Management System", props.toAdvancedSearchFilterProperties(), true);
        } catch (Exception e) {
            logger.error("Unexpected error while saving settings", e);
            showError("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return;
        }
    }

    private void applySavedFiltersJson(String json) {
        savedFilters.clear();

        try {
            List<SavedFilterEntry> entries = JsonUtils.getMapper().readValue(json, new TypeReference<List<SavedFilterEntry>>() {
            });

            if (entries != null) {
                // sort by name (case-insensitive)
                entries.sort(Comparator.comparing(SavedFilterEntry::getName, String.CASE_INSENSITIVE_ORDER));

                for (SavedFilterEntry entry : entries) {
                    if (entry.getName() != null) {
                        savedFilterListModel.addElement(entry);
                    }
                }
            }
        } catch (Exception e) {
        }

        savedFilterList.clearSelection();
    }

    private void loadFilter() {
        SavedFilterEntry entry = savedFilterList.getSelectedValue();
        if (entry == null || entry.getState() == null) {
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this, "Load this filter and discard current changes?", "Confirm Load", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        resetComponents(entry.getState());
    }

    private void deleteFilter() {
        SavedFilterEntry entry = savedFilterList.getSelectedValue();
        if (entry == null) {
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this, "Delete saved filter '" + entry.getName() + "'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        // Remove from UI model
        savedFilterListModel.removeElement(entry);

        // Persist updated list to server
        saveSavedFiltersToServer();

        // Update selection/buttons
        savedFilterList.clearSelection();
    }

    private void saveFilter() {
        // 1) Collect current state
        AdvancedJsonFilterState state = collectStateFromUi();
        if (state == null) {
            return;
        }

        SavedFilterEntry selected = savedFilterList.getSelectedValue();

        // 2) Ask for name (prefill if selected)
        JTextField nameField = new JTextField(selected != null ? selected.getName() : "");

        Object[] message;
        int result;

        if (selected != null) {
            // Selected exists -> allow overwrite or new
            JRadioButton overwriteBtn = new JRadioButton("Overwrite selected filter", true);
            JRadioButton saveAsNewBtn = new JRadioButton("Save as new filter");

            ButtonGroup group = new ButtonGroup();
            group.add(overwriteBtn);
            group.add(saveAsNewBtn);

            message = new Object[] { "Save current filter as:", nameField, overwriteBtn, saveAsNewBtn };

            result = JOptionPane.showConfirmDialog(this, message, "Save Filter", JOptionPane.OK_CANCEL_OPTION);

            if (result != JOptionPane.OK_OPTION) {
                return;
            }

            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                showError("Filter name cannot be empty.");
                return;
            }

            if (overwriteBtn.isSelected()) {
                // Overwrite selected
                savedFilterListModel.removeElement(selected);
            }

            addOrReplaceFilter(name, state);

        } else {
            // No selection -> simple save new
            result = JOptionPane.showConfirmDialog(this, new Object[] { "Filter name:", nameField }, "Save Filter", JOptionPane.OK_CANCEL_OPTION);

            if (result != JOptionPane.OK_OPTION) {
                return;
            }

            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                showError("Filter name cannot be empty.");
                return;
            }

            addOrReplaceFilter(name, state);
        }

        sortSavedFilterListModel();
        saveSavedFiltersToServer();
    }

    private void addOrReplaceFilter(String name, AdvancedJsonFilterState state) {
        // Remove existing with same name (case-insensitive)
        for (int i = 0; i < savedFilterListModel.size(); i++) {
            SavedFilterEntry e = savedFilterListModel.getElementAt(i);
            if (e.getName() != null && e.getName().equalsIgnoreCase(name)) {
                savedFilterListModel.removeElement(e);
                break;
            }
        }

        SavedFilterEntry entry = new SavedFilterEntry(name, state);
        savedFilterListModel.addElement(entry);
        savedFilterList.setSelectedValue(entry, true);
    }

    private AdvancedJsonFilterState collectStateFromUi() {
        if (conditionTable.isEditing()) {
            conditionTable.getCellEditor().stopCellEditing();
        }

        String keyPattern = getKeyPattern();
        List<JsonCondition> conds = getConditions();

        // NEW: Reject empty filter (no key pattern AND no conditions)
        if (keyPattern.isEmpty() && conds.isEmpty()) {
            showError("Filter is empty. Please specify a Key Pattern or at least one JSON condition.");
            return null;
        }

        // cleanup then validate conditions
        for (int i = 0; i < conds.size(); i++) {

            JsonCondition c = conds.get(i);

            // cleanup
            String field = c.getField() != null ? c.getField().trim() : "";
            String value = c.getValue() != null ? c.getValue().toString().trim() : "";

            c.setField(field);
            c.setValue(value);

            // Validate
            if (field.isEmpty()) {
                showError("Condition " + (i + 1) + ": Field cannot be empty.");
                return null;
            }
            if (value.isEmpty()) {
                showError("Condition " + (i + 1) + ": Value cannot be empty.");
                return null;
            }
        }

        AdvancedJsonFilterState state = new AdvancedJsonFilterState();
        state.setKeyPattern(keyPattern);
        state.setConditions(conds);

        return state;
    }

    private void sortSavedFilterListModel() {
        if (savedFilterListModel.getSize() <= 1) {
            return;
        }

        List<SavedFilterEntry> list = Collections.list(savedFilterListModel.elements());

        list.sort(Comparator.comparing(SavedFilterEntry::getName, String.CASE_INSENSITIVE_ORDER));

        savedFilterListModel.clear();
        for (SavedFilterEntry entry : list) {
            savedFilterListModel.addElement(entry);
        }
    }

    private void apply() {
        if (conditionTable.isEditing()) {
            conditionTable.getCellEditor().stopCellEditing();
        }

        String keyPattern = getKeyPattern();
        List<JsonCondition> conds = getConditions();

        // cleanup then validate conditions
        for (int i = 0; i < conds.size(); i++) {

            JsonCondition c = conds.get(i);

            // cleanup
            String field = c.getField() != null ? c.getField().trim() : "";
            String value = c.getValue() != null ? c.getValue().toString().trim() : "";

            c.setField(field);
            c.setValue(value);

            // Validate
            if (field.isEmpty()) {
                showError("Condition " + (i + 1) + ": Field cannot be empty.");
                return;
            }
            if (value.isEmpty()) {
                showError("Condition " + (i + 1) + ": Value cannot be empty.");
                return;
            }
        }

        currentState.setKeyPattern(keyPattern != null ? keyPattern.trim() : null);
        currentState.setConditions(conds);

        okPressed = true;
        dispose();
    }

    private void close() {
        dispose();
    }

    private void addConditionRow() {
        conditionTableModel.addCondition(new JsonCondition("", JsonOperator.EQUAL, ""));
        int lastRow = conditionTableModel.getRowCount() - 1;
        conditionTable.setRowSelectionInterval(lastRow, lastRow);
        conditionTable.editCellAt(lastRow, 0);
    }

    private void removeSelectedRow() {
        int row = conditionTable.getSelectedRow();
        if (row >= 0) {
            conditionTableModel.removeCondition(row);
        }
    }

    public boolean isOkPressed() {
        return okPressed;
    }

    private String getKeyPattern() {
        return keyPatternField.getText().trim();
    }

    private List<JsonCondition> getConditions() {
        return new ArrayList<>(conditionTableModel.getConditions());
    }

    protected void showError(String err) {
        PlatformUI.MIRTH_FRAME.alertError(this, err);
    }

    // === Table Model Inner Class ===

    private static class ConditionTableModel extends AbstractTableModel {

        private final String[] columns = { "Field", "Operator", "Value" };
        private final List<JsonCondition> conditions = new ArrayList<>();

        @Override
        public int getRowCount() {
            return conditions.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col != 1; // only Field + Value editable
        }

        @Override
        public Object getValueAt(int row, int col) {
            JsonCondition c = conditions.get(row);
            switch (col) {
            case 0:
                return c.getField();
            case 1:
                return "equals";
            case 2:
                return c.getValue() == null ? "" : c.getValue().toString();
            }
            return "";
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            JsonCondition c = conditions.get(row);
            String text = value != null ? value.toString().trim() : "";

            if (col == 0) {
                c.setField(text);
            } else if (col == 2) {
                c.setValue(text);
            }

            fireTableCellUpdated(row, col);
        }

        public void addCondition(JsonCondition condition) {
            conditions.add(condition);
            int r = conditions.size() - 1;
            fireTableRowsInserted(r, r);
        }

        public void removeCondition(int index) {
            conditions.remove(index);
            fireTableRowsDeleted(index, index);
        }

        public void setConditions(List<JsonCondition> newConditions) {
            conditions.clear();
            if (newConditions != null) {
                conditions.addAll(newConditions);
            }

            fireTableDataChanged();
        }

        public List<JsonCondition> getConditions() {
            return conditions;
        }
    }

    private static final class SavedFilterEntry {

        private String name;
        private AdvancedJsonFilterState state;

        public SavedFilterEntry() {
            // Default constructor required for JSON deserialization
        }

        public SavedFilterEntry(String name, AdvancedJsonFilterState state) {
            this.name = name;
            this.state = state;
        }

        public String getName() {
            return name;
        }

        public AdvancedJsonFilterState getState() {
            return state;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setState(AdvancedJsonFilterState state) {
            this.state = state;
        }

        @Override
        public String toString() {
            return name != null ? name : "";
        }
    }
}
