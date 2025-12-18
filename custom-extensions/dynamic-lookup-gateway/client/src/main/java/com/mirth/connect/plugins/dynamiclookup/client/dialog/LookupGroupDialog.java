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

import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthFieldConstraints;
import com.mirth.connect.plugins.dynamiclookup.client.exception.LookupApiClientException;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.shared.capability.LookupJsonCapability;
import com.mirth.connect.plugins.dynamiclookup.shared.constant.LookupConstants;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroupExtra;
import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;
import com.mirth.connect.plugins.dynamiclookup.shared.validation.FieldPathFormatValidator;

import net.miginfocom.swing.MigLayout;

public class LookupGroupDialog extends MirthDialog {
    private final Logger logger = LogManager.getLogger(this.getClass());

    private JLabel nameLabel;
    private JTextField nameField;

    private JLabel descriptionLabel;
    private JTextArea descriptionField;
    private JScrollPane descriptionScrollPane;

    private JLabel versionLabel;
    private JTextField versionField;

    private JLabel cacheSizeLabel;
    private JTextField cacheSizeField;

    private JLabel cachePolicyLabel;
    private JComboBox<String> cachePolicyComboBox;

    private JLabel statisticsEnabledLabel;
    private JCheckBox statisticsEnabledCheckBox;

    private JLabel valueTypeLabel;
    private JComboBox<String> valueTypeComboBox;

    private JLabel jsonIndexTypeLabel;
    private JComboBox<String> jsonIndexTypeComboBox;

    private JLabel jsonIndexFieldsLabel;
    private DefaultListModel<String> jsonIndexFieldsModel;
    private JList<String> jsonIndexFieldsList;
    private JScrollPane jsonIndexFieldsScroll;

    private JButton addJsonFieldButton;
    private JButton removeJsonFieldButton;

    private JButton saveButton;
    private JButton cancelButton;

    private Frame parent;
    private LookupGroup lookupGroup;
    private boolean isEdit = false;
    private boolean saved = false;

    public LookupGroupDialog(Frame parent, LookupGroup lookupGroup, boolean isEdit) {
        super(parent, true);

        this.parent = parent;
        this.lookupGroup = lookupGroup;
        this.isEdit = isEdit;

        initComponents();
        initLayout();

        configureJsonControls();

        resetComponents(lookupGroup);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(String.format("%s", isEdit ? "Edit Group" : "Add Group"));
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private void initComponents() {
        boolean jsonSupported = false;

        try {
            LookupJsonCapability capability = LookupJsonCapability.getInstance();
            jsonSupported = capability.isJsonSupported();
        } catch (Exception e) {
            // no-op
        }

        setBackground(UIConstants.BACKGROUND_COLOR);
        getContentPane().setBackground(getBackground());

        nameLabel = new JLabel("Name:");
        nameField = new JTextField();

        descriptionLabel = new JLabel("Description:");
        descriptionField = new JTextArea();
        descriptionField.setWrapStyleWord(true);
        descriptionField.setLineWrap(true);
        descriptionScrollPane = new JScrollPane(descriptionField, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        descriptionScrollPane.setPreferredSize(new Dimension(200, 50));

        versionLabel = new JLabel("Version:");
        versionField = new JTextField();

        cacheSizeLabel = new JLabel("Cache Size:");
        cacheSizeField = new JTextField();
        cacheSizeField.setDocument(new MirthFieldConstraints(8, false, false, true));

        cachePolicyLabel = new JLabel("Cache Policy:");
        cachePolicyComboBox = new JComboBox<String>();
        cachePolicyComboBox.addItem("LRU");
        cachePolicyComboBox.addItem("FIFO");
        cachePolicyComboBox.getModel().setSelectedItem("LRU");

        statisticsEnabledLabel = new JLabel("Statistics Enabled:");
        statisticsEnabledCheckBox = new JCheckBox();
        statisticsEnabledCheckBox.setSelected(true);
        statisticsEnabledCheckBox.setBackground(new Color(255, 255, 255));
        // --- VALUE TYPE ---
        valueTypeLabel = new JLabel("Value Type:");
        valueTypeComboBox = new JComboBox<>();
        valueTypeComboBox.addItem(LookupConstants.VALUE_TYPE_TEXT);
        if (jsonSupported) {
            valueTypeComboBox.addItem(LookupConstants.VALUE_TYPE_JSON);
        }
        valueTypeComboBox.setSelectedItem(LookupConstants.VALUE_TYPE_TEXT);
        valueTypeComboBox.addActionListener(e -> updateJsonIndexControls());

        // --- JSON INDEX TYPE ---
        jsonIndexTypeLabel = new JLabel("JSON Index:");
        jsonIndexTypeComboBox = new JComboBox<>();
        jsonIndexTypeComboBox.addItem(LookupConstants.JSON_INDEX_NONE);
        jsonIndexTypeComboBox.addItem(LookupConstants.JSON_INDEX_FIELD);
        jsonIndexTypeComboBox.setSelectedItem(LookupConstants.JSON_INDEX_NONE);
        jsonIndexTypeComboBox.addActionListener(e -> updateJsonIndexControls());

        // --- JSON INDEX FIELDS ---
        jsonIndexFieldsLabel = new JLabel("Indexed JSON Fields:");
        jsonIndexFieldsModel = new DefaultListModel<>();
        jsonIndexFieldsList = new JList<>(jsonIndexFieldsModel);
        jsonIndexFieldsList.setVisibleRowCount(5);

        jsonIndexFieldsScroll = new JScrollPane(jsonIndexFieldsList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Buttons add/remove
        addJsonFieldButton = new JButton("Add");
        removeJsonFieldButton = new JButton("Remove");

        addJsonFieldButton.addActionListener(e -> addJsonField());
        removeJsonFieldButton.addActionListener(e -> removeJsonField());

        saveButton = new JButton("Save");
        saveButton.addActionListener(evt -> save());

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(evt -> close());
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 12, novisualpadding, hidemode 3, fill"));

        JPanel addPanel = new JPanel(new MigLayout("novisualpadding, hidemode 0, align center, insets 0 0 0 0, fill", "25[right][fill]"));

        addPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        addPanel.setBorder(BorderFactory.createEmptyBorder());
        addPanel.setMinimumSize(getMinimumSize());
        addPanel.setMaximumSize(getMaximumSize());

        addPanel.add(nameLabel, "right");
        addPanel.add(nameField, "w 200!");

        addPanel.add(descriptionLabel, "newline, right");
        addPanel.add(descriptionScrollPane);

        addPanel.add(versionLabel, "newline, right");
        addPanel.add(versionField, "w 100!");

        addPanel.add(cacheSizeLabel, "newline, right");
        addPanel.add(cacheSizeField, "w 100!, split 2");
        JLabel infoIcon = new JLabel("<html><span style='color:#666;'>0 = disabled</span></html>");
        addPanel.add(infoIcon, "gapleft 6");

        addPanel.add(cachePolicyLabel, "newline, right");
        addPanel.add(cachePolicyComboBox, "w 100!");

        addPanel.add(statisticsEnabledLabel, "newline, right");
        addPanel.add(statisticsEnabledCheckBox, "w 20!");

        // --- VALUE TYPE ---
        addPanel.add(valueTypeLabel, "newline, right");
        addPanel.add(valueTypeComboBox, "w 120!");

        // --- JSON INDEX TYPE ---
        addPanel.add(jsonIndexTypeLabel, "newline, right");
        addPanel.add(jsonIndexTypeComboBox, "w 120!");

        // --- JSON INDEX FIELDS (list + buttons) ---
        addPanel.add(jsonIndexFieldsLabel, "newline, right, top");

        JPanel jsonButtons = new JPanel(new MigLayout("insets 0, gap 4, fillx", "[grow]", "[]4[]"));
        jsonButtons.add(addJsonFieldButton, "growx, wrap");
        jsonButtons.add(removeJsonFieldButton, "growx");

        addPanel.add(jsonIndexFieldsScroll, "w 200!, h 80!, split 2");
        addPanel.add(jsonButtons, "top");

        add(addPanel, "growx");
        add(new JSeparator(), "newline, sx, growx");

        add(saveButton, "newline, sx, right, split 2");
        add(cancelButton);
    }

    private void configureJsonControls() {

    }

    private void resetComponents(LookupGroup lookupGroup) {
        nameField.setText(lookupGroup.getName());
        descriptionField.setText(lookupGroup.getDescription());
        versionField.setText(lookupGroup.getVersion());
        cacheSizeField.setText(String.valueOf(lookupGroup.getCacheSize()));
        cachePolicyComboBox.getModel().setSelectedItem(lookupGroup.getCachePolicy());
        statisticsEnabledCheckBox.setSelected(lookupGroup.isStatisticsEnabled());

        String valueType = LookupConstants.normalizeValueType(lookupGroup.getValueType());
        valueTypeComboBox.setSelectedItem(lookupGroup.getValueType());

        // ----- extra -----
        LookupGroupExtra extra = lookupGroup.getExtra();
        if (extra != null) {

            // JSON Index mode
            String jsonIndexMode = extra.getJsonIndexMode() != null ? extra.getJsonIndexMode() : LookupConstants.JSON_INDEX_NONE;
            jsonIndexTypeComboBox.setSelectedItem(jsonIndexMode);

            // JSON indexed fields
            jsonIndexFieldsModel.clear();

            if (LookupConstants.isJsonValueType(valueType) && LookupConstants.isFieldMode(jsonIndexMode) && extra.getIndexedJsonFields() != null) {
                try {
                    List<String> fields = JsonUtils.fromJsonList(extra.getIndexedJsonFields(), String.class);
                    for (String f : fields) {
                        if (f != null && !f.isEmpty()) {
                            jsonIndexFieldsModel.addElement(f);
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
        updateJsonIndexControls();
    }

    private void addJsonField() {
        String field = JOptionPane.showInputDialog(this, "JSON field path (e.g. email, address.city):", "Add JSON Field", JOptionPane.PLAIN_MESSAGE);

        if (field != null) {
            field = field.trim();
            if (!field.isEmpty() && !jsonIndexFieldsModel.contains(field)) {
                jsonIndexFieldsModel.addElement(field);
            }
        }
    }

    private void removeJsonField() {
        List<String> selected = jsonIndexFieldsList.getSelectedValuesList();
        for (String s : selected) {
            jsonIndexFieldsModel.removeElement(s);
        }
    }

    private void updateJsonIndexControls() {
        if (isEdit) {
            valueTypeComboBox.setEnabled(false);
        }

        String valueType = (String) valueTypeComboBox.getSelectedItem();
        boolean isJson = LookupConstants.isJsonValueType(valueType);

        jsonIndexTypeLabel.setEnabled(isJson);
        jsonIndexTypeComboBox.setEnabled(isJson);

        if (!isJson) {
            jsonIndexTypeComboBox.setSelectedItem("NONE");
        }

        String indexType = (String) jsonIndexTypeComboBox.getSelectedItem();
        boolean fieldMode = isJson && "FIELD".equals(indexType);

        jsonIndexFieldsLabel.setEnabled(fieldMode);
        jsonIndexFieldsList.setEnabled(fieldMode);
        jsonIndexFieldsScroll.setEnabled(fieldMode);

        addJsonFieldButton.setEnabled(fieldMode);
        removeJsonFieldButton.setEnabled(fieldMode);
    }

    private void save() {
        if (!validateProperties()) {
            return;
        }

        this.lookupGroup.setName(nameField.getText().trim());
        this.lookupGroup.setDescription(descriptionField.getText().trim());
        this.lookupGroup.setVersion(versionField.getText().trim());
        this.lookupGroup.setCacheSize(Integer.parseInt(cacheSizeField.getText().trim()));
        this.lookupGroup.setCachePolicy((String) cachePolicyComboBox.getSelectedItem());
        this.lookupGroup.setValueType((String) valueTypeComboBox.getSelectedItem());
        this.lookupGroup.setStatisticsEnabled(statisticsEnabledCheckBox.isSelected());

        String valueType = (String) valueTypeComboBox.getSelectedItem();

        // lookup group extra
        LookupGroupExtra extra = new LookupGroupExtra();
        if (LookupConstants.isJsonValueType(valueType)) {
            String jsonIndexMode = (String) jsonIndexTypeComboBox.getSelectedItem();
            extra.setJsonIndexMode(jsonIndexMode);

            if (LookupConstants.isFieldMode(jsonIndexMode)) {
                List<String> fields = new ArrayList<>();
                for (int i = 0; i < jsonIndexFieldsModel.size(); i++) {
                    fields.add(jsonIndexFieldsModel.get(i));
                }
                if (!fields.isEmpty()) {
                    try {
                        String json = JsonUtils.toJson(fields);
                        extra.setIndexedJsonFields(json);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to serialize indexed JSON fields", e);
                    }
                }
            }
        }

        this.lookupGroup.setExtra(extra);
        try {
            LookupGroup response;
            if (this.lookupGroup.getId() > 0) {
                response = LookupServiceClient.getInstance().updateGroup(this.lookupGroup);
            } else {
                response = LookupServiceClient.getInstance().createGroup(this.lookupGroup);
            }

            this.lookupGroup.setId(response.getId());
            this.lookupGroup.setCreatedDate(response.getCreatedDate());
            this.lookupGroup.setUpdatedDate(response.getUpdatedDate());

            this.saved = true;

            close();

        } catch (LookupApiClientException e) {
            showError(e.getError().getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while saving group", e);
            showError("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
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

        // Reset backgrounds
        resetInvalidComponents();

        String name = nameField.getText().trim();
        if (StringUtils.isEmpty(name)) {
            valid = false;
            nameField.setBackground(UIConstants.INVALID_COLOR);
            errorMessage.append("Please provide a name.").append(System.lineSeparator());
        }

        String version = versionField.getText().trim();
        if (StringUtils.isEmpty(version)) {
            valid = false;
            versionField.setBackground(UIConstants.INVALID_COLOR);
            errorMessage.append("Please provide a version.").append(System.lineSeparator());
        }

        String cacheSize = cacheSizeField.getText().trim();
        if (StringUtils.isEmpty(cacheSize)) {
            valid = false;
            cacheSizeField.setBackground(UIConstants.INVALID_COLOR);
            errorMessage.append("Please provide a cache size.").append(System.lineSeparator());
        }

        // Indexed JSON fields (Field mode)
        String valueType = (String) valueTypeComboBox.getSelectedItem();
        if (LookupConstants.isJsonValueType(valueType)) {
            String jsonIndexMode = (String) jsonIndexTypeComboBox.getSelectedItem();
            if (LookupConstants.isFieldMode(jsonIndexMode)) {
                for (int i = 0; i < jsonIndexFieldsModel.size(); i++) {
                    String field = jsonIndexFieldsModel.get(i);
                    try {
                        FieldPathFormatValidator.validate(field);
                    } catch (IllegalArgumentException ex) {
                        valid = false;

                        jsonIndexFieldsList.setBackground(UIConstants.INVALID_COLOR);
                        errorMessage.append("Indexed JSON field #").append(i + 1).append(": ").append(ex.getMessage()).append(System.lineSeparator());
                    }
                }
            }
        }

        if (!valid) {
            showError(errorMessage.toString());
        }

        return valid;
    }

    public void resetInvalidComponents() {
        nameField.setBackground(null);
        versionField.setBackground(null);
        cacheSizeField.setBackground(null);

        jsonIndexFieldsList.setBackground(UIConstants.BACKGROUND_COLOR);
    }

    protected void showInformation(String msg) {
        PlatformUI.MIRTH_FRAME.alertInformation(this, msg);
    }

    protected void showError(String err) {
        PlatformUI.MIRTH_FRAME.alertError(this, err);
    }
}
