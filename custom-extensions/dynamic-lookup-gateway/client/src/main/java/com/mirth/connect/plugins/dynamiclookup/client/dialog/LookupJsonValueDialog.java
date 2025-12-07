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

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.plugins.dynamiclookup.client.exception.LookupApiClientException;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.LookupValueResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;

import net.miginfocom.swing.MigLayout;

public class LookupJsonValueDialog extends MirthDialog {

    private final Logger logger = LogManager.getLogger(getClass());

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JLabel keyLabel;
    private JTextField keyField;

    private JLabel valueLabel;
    private RSyntaxTextArea rawTextArea;
    private RTextScrollPane rawScrollPane;

    private JTree treeView;
    private JScrollPane treeScrollPane;

    private JTabbedPane tabbedPane;

    private JButton saveButton;
    private JButton cancelButton;

    private Frame parent;
    private LookupValue lookupValue;
    private LookupGroup lookupGroup;
    private boolean isEdit = false;
    private boolean saved = false;

    public LookupJsonValueDialog(Frame parent, LookupValue lookupValue, LookupGroup lookupGroup, boolean isEdit) {
        super(parent, true);

        this.parent = parent;
        this.isEdit = isEdit;
        this.lookupValue = lookupValue;
        this.lookupGroup = lookupGroup;

        initComponents();
        initLayout();
        resetComponents(lookupValue);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(String.format("Group %s - %s", lookupGroup.getName(), isEdit ? "Edit Value" : "Add Value"));

        setPreferredSize(new Dimension(700, 500));
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        keyLabel = new JLabel("Key:");
        keyField = new JTextField();

        valueLabel = new JLabel("Value:");

        // RAW editor (syntax-highlighted JSON)
        rawTextArea = new RSyntaxTextArea(5, 30);
        rawTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        rawTextArea.setCodeFoldingEnabled(true);
        rawTextArea.setAntiAliasingEnabled(true);
        rawTextArea.setLineWrap(true);
        rawTextArea.setWrapStyleWord(true);

        rawScrollPane = new RTextScrollPane(rawTextArea);
        rawScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        rawScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // TREE view
        treeView = new JTree(new DefaultMutableTreeNode("No data"));
        treeScrollPane = new JScrollPane(treeView);
        installTreeContextMenu();

        // Tabs: Raw / Tree
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Raw", rawScrollPane);
        tabbedPane.addTab("Tree", treeScrollPane);

        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedComponent() == treeScrollPane) {
                updateTreeFromRaw();
            }
        });

        saveButton = new JButton("Save");
        saveButton.addActionListener(evt -> save());

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(evt -> close());
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 12, novisualpadding, hidemode 3, fill"));

        JPanel mainPanel = new JPanel(new MigLayout("novisualpadding, hidemode 0, align center, insets 0 0 0 0, fill", "25[right][grow,fill]", ""));
        mainPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        mainPanel.setBorder(BorderFactory.createEmptyBorder());

        mainPanel.add(keyLabel, "right");
        mainPanel.add(keyField, "wmin 200, growx");

        mainPanel.add(valueLabel, "newline, right");
        mainPanel.add(tabbedPane, "wmin 200, hmin 200, grow, push");

        add(mainPanel, "grow, push");
        add(new JSeparator(), "newline, sx, growx");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        add(buttonPanel, "newline, sx, growx");
    }

    private void installTreeContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyValueItem = new JMenuItem("Copy");
        JMenuItem copyPathItem = new JMenuItem("Copy JSONPath");

        copyValueItem.addActionListener(e -> copySelectedNodeValue());
        copyPathItem.addActionListener(e -> copySelectedNodePath());

        menu.add(copyValueItem);
        menu.add(copyPathItem);

        treeView.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e);
            }

            private void maybeShowMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    TreePath path = treeView.getPathForLocation(e.getX(), e.getY());
                    if (path == null) {
                        return;
                    }
                    treeView.setSelectionPath(path);
                    menu.show(treeView, e.getX(), e.getY());
                }
            }
        });
    }

    private void resetComponents(LookupValue lookupValue) {
        keyField.setText(lookupValue.getKeyValue());

        String rawValue = lookupValue.getValueData();
        if (StringUtils.isEmpty(rawValue)) {
            rawTextArea.setText("");
        } else {
            // Normalize to compact JSON if possible
            try {
                JsonNode node = objectMapper.readTree(rawValue);
                String compact = objectMapper.writeValueAsString(node);
                rawTextArea.setText(compact);
            } catch (Exception e) {
                // If not valid JSON, just show raw so user can fix it
                rawTextArea.setText(rawValue);
            }
        }

        keyField.setEnabled(!this.isEdit);
    }

    private void save() {
        if (!validateProperties()) {
            return;
        }

        // Clear previous invalid backgrounds
        resetInvalidComponents();

        String key = keyField.getText().trim();
        String rawJson = rawTextArea.getText().trim();

        JsonNode node;
        try {
            node = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            rawTextArea.setBackground(UIConstants.INVALID_COLOR);
            showError("Invalid JSON value");
            tabbedPane.setSelectedComponent(rawScrollPane);
            return;
        }

        String compactJson;
        try {
            // Always save compact / normalized JSON
            compactJson = objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            logger.error("Error normalizing JSON before save", e);
            showError("Unexpected error while normalizing JSON: " + e.getMessage());
            return;
        }

        try {
            this.lookupValue.setKeyValue(key);
            this.lookupValue.setValueData(compactJson);

            if (!this.isEdit) {
                boolean exists;
                try {
                    exists = LookupServiceClient.getInstance().checkValueExists(this.lookupGroup.getId(), this.lookupValue.getKeyValue());
                } catch (LookupApiClientException e) {
                    showError("Error checking for existing value: " + e.getError().getMessage());
                    return;
                } catch (Exception e) {
                    logger.error("Unexpected error while checking existing value", e);
                    showError("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    return;
                }

                if (exists) {
                    int choice = JOptionPane.showConfirmDialog(this, "A value with the same key already exists. Do you want to overwrite it?", "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                    if (choice != JOptionPane.YES_OPTION) {
                        return; // Cancel save
                    }
                }
            }

            LookupValueResponse response = LookupServiceClient.getInstance().setValue(this.lookupGroup.getId(), this.lookupValue);

            this.lookupValue.setKeyValue(response.getKey());
            this.lookupValue.setValueData(response.getValue());
            this.lookupValue.setCreatedDate(response.getCreatedDate());
            this.lookupValue.setUpdatedDate(response.getUpdatedDate());

            this.saved = true;
            close();
        } catch (LookupApiClientException e) {
            showError(e.getError().getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while saving value", e);
            showError("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    private void updateTreeFromRaw() {
        String rawJson = rawTextArea.getText().trim();

        try {
            JsonNode node = objectMapper.readTree(rawJson);
            DefaultMutableTreeNode root = buildTreeNode(node, "root", "$");
            treeView.setModel(new DefaultTreeModel(root));
            expandAll(treeView);
        } catch (Exception e) {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("Invalid JSON (fix in Raw tab)");
            treeView.setModel(new DefaultTreeModel(root));
        }
    }

    private DefaultMutableTreeNode buildTreeNode(JsonNode node, String name, String jsonPath) {
        JsonTreeNodeData data = new JsonTreeNodeData(name, jsonPath, node);
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(data);

        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode child = entry.getValue();
                String childPath = jsonPath + "." + fieldName;
                treeNode.add(buildTreeNode(child, fieldName, childPath));
            });
        } else if (node.isArray()) {
            int index = 0;
            for (JsonNode child : node) {
                String elemName = "[" + index + "]";
                String childPath = jsonPath + elemName;
                treeNode.add(buildTreeNode(child, elemName, childPath));
                index++;
            }
        } else {
            String valueText = node.isNull() ? "null" : node.asText();
            JsonTreeNodeData valueData = new JsonTreeNodeData(name + ": " + valueText, jsonPath, node);
            treeNode.setUserObject(valueData);
        }

        return treeNode;
    }

    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private void copySelectedNodePath() {
        Object comp = treeView.getLastSelectedPathComponent();
        if (!(comp instanceof DefaultMutableTreeNode)) {
            return;
        }

        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) comp;
        Object userObject = treeNode.getUserObject();

        if (userObject instanceof JsonTreeNodeData) {
            JsonTreeNodeData data = (JsonTreeNodeData) userObject;
            String path = data.jsonPath;

            if (StringUtils.isNotEmpty(path)) {
                copyToClipboard(path);
                showInformation("Copied JSONPath: " + path);
            }
        }
    }

    private void copySelectedNodeValue() {
        Object comp = treeView.getLastSelectedPathComponent();
        if (!(comp instanceof DefaultMutableTreeNode)) {
            return;
        }

        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) comp;
        Object userObject = treeNode.getUserObject();

        if (userObject instanceof JsonTreeNodeData) {
            JsonTreeNodeData data = (JsonTreeNodeData) userObject;
            JsonNode node = data.node;

            try {
                String valueText;

                if (node.isObject() || node.isArray()) {
                    valueText = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
                } else {
                    valueText = objectMapper.writeValueAsString(node);
                }

                copyToClipboard(valueText);
                showInformation("Copied value to clipboard.");
            } catch (Exception e) {
                logger.error("Error copying JSON value", e);
                showError("Error copying value: " + e.getMessage());
            }
        }
    }

    private void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
    }

    private void close() {
        dispose();
    }

    public boolean isSaved() {
        return saved;
    }

    private boolean validateProperties() {
        boolean valid = true;
        StringBuilder errorMessage = new StringBuilder();

        resetInvalidComponents();

        String key = keyField.getText().trim();
        if (StringUtils.isEmpty(key)) {
            valid = false;
            keyField.setBackground(UIConstants.INVALID_COLOR);
            errorMessage.append("Please provide a key.").append(System.lineSeparator());
        }

        String value = rawTextArea.getText().trim();
        if (StringUtils.isEmpty(value)) {
            valid = false;
            rawTextArea.setBackground(UIConstants.INVALID_COLOR);
            errorMessage.append("Please provide a JSON value.").append(System.lineSeparator());
        }

        if (!valid) {
            showError(errorMessage.toString());
        }

        return valid;
    }

    public void resetInvalidComponents() {
        keyField.setBackground(null);
        rawTextArea.setBackground(null);
    }

    protected void showInformation(String msg) {
        PlatformUI.MIRTH_FRAME.alertInformation(this, msg);
    }

    protected void showError(String err) {
        PlatformUI.MIRTH_FRAME.alertError(this, err);
    }

    private static class JsonTreeNodeData {
        final String label;
        final String jsonPath;
        final JsonNode node;

        JsonTreeNodeData(String label, String jsonPath, JsonNode node) {
            this.label = label;
            this.jsonPath = jsonPath;
            this.node = node;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
