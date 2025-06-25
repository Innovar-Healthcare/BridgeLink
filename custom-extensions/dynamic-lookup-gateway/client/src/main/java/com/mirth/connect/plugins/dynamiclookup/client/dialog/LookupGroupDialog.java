package com.mirth.connect.plugins.dynamiclookup.client.dialog;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthFieldConstraints;

import com.mirth.connect.plugins.dynamiclookup.client.exception.LookupApiClientException;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.WindowConstants;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;
import javax.swing.JSeparator;

import java.awt.Dimension;

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

    private JButton saveButton;
    private JButton cancelButton;

    private Frame parent;
    private LookupGroup lookupGroup;
    private boolean saved = false;

    public LookupGroupDialog(Frame parent, LookupGroup lookupGroup, boolean isEdit) {
        super(parent, true);

        this.parent = parent;
        this.lookupGroup = lookupGroup;

        initComponents();
        initLayout();

        resetComponents(lookupGroup);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(String.format("%s", isEdit ? "Edit Group" : "Add Group"));
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    private void initComponents() {
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
        addPanel.add(cacheSizeField, "w 100!");

        addPanel.add(cachePolicyLabel, "newline, right");
        addPanel.add(cachePolicyComboBox, "w 100!");

        add(addPanel, "growx");
        add(new JSeparator(), "newline, sx, growx");

        add(saveButton, "newline, sx, right, split 2");
        add(cancelButton);
    }

    private void resetComponents(LookupGroup lookupGroup) {
        nameField.setText(lookupGroup.getName());
        descriptionField.setText(lookupGroup.getDescription());
        versionField.setText(lookupGroup.getVersion());
        cacheSizeField.setText(String.valueOf(lookupGroup.getCacheSize()));
        cachePolicyComboBox.getModel().setSelectedItem(lookupGroup.getCachePolicy());
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
            errorMessage.append("Please provide a name.")
                    .append(System.lineSeparator());
        }

        String version = versionField.getText().trim();
        if (StringUtils.isEmpty(version)) {
            valid = false;
            versionField.setBackground(UIConstants.INVALID_COLOR);
            errorMessage.append("Please provide a version.")
                    .append(System.lineSeparator());
        }

        String cacheSize = cacheSizeField.getText().trim();
        if (StringUtils.isEmpty(cacheSize)) {
            valid = false;
            cacheSizeField.setBackground(UIConstants.INVALID_COLOR);
            errorMessage.append("Please provide a cache size.")
                    .append(System.lineSeparator());
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
    }

    protected void showInformation(String msg) {
        PlatformUI.MIRTH_FRAME.alertInformation(this, msg);
    }

    protected void showError(String err) {
        PlatformUI.MIRTH_FRAME.alertError(this, err);
    }
}
