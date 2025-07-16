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

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;

import com.mirth.connect.plugins.dynamiclookup.client.exception.LookupApiClientException;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.LookupValueResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;

import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.JButton;
import javax.swing.WindowConstants;
import javax.swing.JPanel;
import javax.swing.BorderFactory;
import javax.swing.JSeparator;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;

public class LookupValueDialog extends MirthDialog {
    private final Logger logger = LogManager.getLogger(this.getClass());
    private JLabel keyLabel;
    private JTextField keyField;

    private JLabel valueLabel;
    private JTextArea valueField;
    private JScrollPane valueScrollPane;
    private JButton saveButton;
    private JButton cancelButton;

    private Frame parent;
    private LookupValue lookupValue;
    private LookupGroup lookupGroup;
    private boolean isEdit = false;
    private boolean saved = false;

    public LookupValueDialog(Frame parent, LookupValue lookupValue, LookupGroup lookupGroup, boolean isEdit) {
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
        valueField = new JTextArea(3, 20);
        valueField.setLineWrap(true);
        valueField.setWrapStyleWord(true);
        valueScrollPane = new JScrollPane(valueField);
        valueScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        valueScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

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

        addPanel.add(keyLabel, "right");
        addPanel.add(keyField, "w 200!");

        addPanel.add(valueLabel, "newline, right");
        addPanel.add(valueScrollPane, "w 200!, h 80!");

        add(addPanel, "growx");
        add(new JSeparator(), "newline, sx, growx");

        add(saveButton, "newline, sx, right, split 2");
        add(cancelButton);
    }

    private void resetComponents(LookupValue lookupValue) {
        keyField.setText(lookupValue.getKeyValue());
        valueField.setText(lookupValue.getValueData());

        keyField.setEnabled(!this.isEdit);
    }

    private void save() {
        if (!validateProperties()) {
            return;
        }
        try {
            this.lookupValue.setKeyValue(keyField.getText().trim());
            this.lookupValue.setValueData(valueField.getText().trim());

            if (!this.isEdit) {
                boolean exists;
                try {
                    exists = LookupServiceClient.getInstance().checkValueExists(this.lookupGroup.getId(), this.lookupValue.getKeyValue());
                } catch (LookupApiClientException e) {
                    // For structured errors that aren't NOT_FOUND
                    showError("Error checking for existing value: " + e.getError().getMessage());
                    return;
                } catch (Exception e) {
                    logger.error("Unexpected error while checking existing value", e);
                    showError("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    return;
                }

                if (exists) {
                    int choice = JOptionPane.showConfirmDialog(
                            this,
                            "A value with the same key already exists. Do you want to overwrite it?",
                            "Confirm Overwrite",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );

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

        String name = keyField.getText().trim();
        if (StringUtils.isEmpty(name)) {
            valid = false;
            keyField.setBackground(UIConstants.INVALID_COLOR);
            errorMessage.append("Please provide a key.")
                    .append(System.lineSeparator());
        }

        String version = valueField.getText().trim();
        if (StringUtils.isEmpty(version)) {
            valid = false;
            valueField.setBackground(UIConstants.INVALID_COLOR);
            errorMessage.append("Please provide a value.")
                    .append(System.lineSeparator());
        }

        if (!valid) {
            showError(errorMessage.toString());
        }

        return valid;
    }

    public void resetInvalidComponents() {
        keyField.setBackground(null);
        valueField.setBackground(null);
    }

    protected void showInformation(String msg) {
        PlatformUI.MIRTH_FRAME.alertInformation(this, msg);
    }

    protected void showError(String err) {
        PlatformUI.MIRTH_FRAME.alertError(this, err);
    }
}
