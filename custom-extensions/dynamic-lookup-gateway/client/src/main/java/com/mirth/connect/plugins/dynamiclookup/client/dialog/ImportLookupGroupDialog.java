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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.Properties;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.UIConstants;

import net.miginfocom.swing.MigLayout;

/**
 * @author Jim(Zi Min) Weng
 * @create 2025-08-22 11:12 AM
 */
public class ImportLookupGroupDialog extends MirthDialog {
	private Frame parent;
	private String[] defaultGroupNames = { "Race", "Ethnicity", "Administrative Gender", "Marital Status", "Religion" };
	private boolean saved = false;
	private JLabel importFromLabel;
	private JLabel fromSystemLabel;
	private JLabel fromFileLabel;
	private JRadioButton fromSystemRadioButton;
	private JRadioButton fromFileRadioButton;
	private JButton browseFileButton;
	private JTextField filePathField;
	private JComboBox<String> defaultGroupComboBox;
	private JButton importButton;
	private JButton cancelButton;
	private Properties importProperties;

	public ImportLookupGroupDialog(Frame parent) {
		super(parent, true);

		this.parent = parent;

		initComponents();
		initLayout();

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setTitle(String.format("Import Default Lookup Group"));
		pack();
		setLocationRelativeTo(parent);
		setVisible(true);
	}

	public void initComponents() {
		setBackground(UIConstants.BACKGROUND_COLOR);
		getContentPane().setBackground(getBackground());

		// --- Radio buttons for source selection ---
		importFromLabel = new JLabel("Import Lookup Group from:");
		fromSystemRadioButton = new JRadioButton("System");
		fromSystemRadioButton.setBackground(getBackground());

		fromFileRadioButton = new JRadioButton("File");
		fromFileRadioButton.setBackground(getBackground());

		ButtonGroup sourceGroup = new ButtonGroup();
		sourceGroup.add(fromSystemRadioButton);
		sourceGroup.add(fromFileRadioButton);

		// Default: System selected
		fromSystemRadioButton.setSelected(true);

		// --- File section ---
		fromFileLabel = new JLabel("Import from file:");
		filePathField = new JTextField();
		browseFileButton = new JButton("Browse");
		browseFileButton.addActionListener(e -> {
			JFileChooser importFileChooser = new JFileChooser();
			importFileChooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));

			// Restore last-used directory if available
			File currentDir = new File(parent.userPreferences.get("currentDirectory", ""));
			if (currentDir.exists()) {
				importFileChooser.setCurrentDirectory(currentDir);
			}

			if (importFileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
				File selectedFile = importFileChooser.getSelectedFile();

				// Save directory for next time
				Frame.userPreferences.put("currentDirectory", selectedFile.getParent());

				// Set path in text field
				filePathField.setText(selectedFile.getAbsolutePath());
			}

		});

		// --- System section ---
		fromSystemLabel = new JLabel("Import from system:");
		defaultGroupComboBox = new JComboBox<>(defaultGroupNames);

		// --- Buttons ---
		importButton = new JButton("Import");
		cancelButton = new JButton("Cancel");

		importButton.addActionListener(e -> {
			// Implement import logic here
			setImportProperties(new Properties());
			saved = true;
			dispose();
		});

		cancelButton.addActionListener(e -> dispose());

		ItemListener stateListener = e -> {
			if (e.getStateChange() == ItemEvent.SELECTED)
				handleSourceToggle();
		};

		fromSystemRadioButton.addItemListener(stateListener);
		fromFileRadioButton.addItemListener(stateListener);

		// Ensure initial UI matches the default selection
		handleSourceToggle();
	}

	private void handleSourceToggle() {

		boolean systemSelected = fromSystemRadioButton.isSelected();

		fromSystemLabel.setEnabled(systemSelected);
		defaultGroupComboBox.setEnabled(systemSelected);

		fromFileLabel.setEnabled(!systemSelected);
		filePathField.setEditable(!systemSelected);
		browseFileButton.setEnabled(!systemSelected);

		// A simple revalidate/repaint of the dialog is enough
		revalidate();
		repaint();
	}

	public void initLayout() {
		// Main panel: 2 columns (labels left, inputs aligned)
		JPanel mainPanel = new JPanel(new MigLayout("insets 20, fill, wrap 2", "[left]10[grow, fill]" // col1 = left-aligned labels, col2 = aligned input fields
		));
		mainPanel.setBackground(getBackground());

		// --- Radio buttons section ---
		JPanel radioPanel = new JPanel(new MigLayout("insets 0, wrap 3", "[][][]"));
		radioPanel.setBackground(getBackground());
		radioPanel.add(importFromLabel);
		radioPanel.add(fromSystemRadioButton);
		radioPanel.add(fromFileRadioButton);

		// full-width row for radios
		mainPanel.add(radioPanel, "span, align left, wrap");

		// --- File Import Section ---
		JLabel fromFileLabel = new JLabel("Import from file:");
		mainPanel.add(fromFileLabel);
		mainPanel.add(filePathField, "growx, split 2"); // input starts aligned
		mainPanel.add(browseFileButton, "wrap"); // browse button next to field

		// --- System Selection Section ---
		JLabel fromSystemLabel = new JLabel("Import from system:");
		mainPanel.add(fromSystemLabel);
		mainPanel.add(defaultGroupComboBox, "growx, wrap"); // dropdown starts aligned

		// --- Action Buttons ---
		JPanel buttonPanel = new JPanel(new MigLayout("insets 0", "[right]"));
		buttonPanel.setBackground(getBackground());
		buttonPanel.add(importButton, "tag ok");
		buttonPanel.add(cancelButton, "tag cancel, gapleft 10");

		mainPanel.add(buttonPanel, "span, align right");

		// --- Add to dialog ---
		getContentPane().add(mainPanel);

		// --- Enlarge the dialog ---
		setPreferredSize(new Dimension(500, 220));
		pack();
		setLocationRelativeTo(null); // Center on screen (optional)
	}

	public boolean isSaved() {
		return saved;
	}

	private void setImportProperties(Properties properties) {
		this.importProperties = new Properties();
		this.importProperties.setProperty("importMethod", fromSystemRadioButton.isSelected() ? "system" : "file");
		this.importProperties.setProperty("filePath", filePathField.getText());
		this.importProperties.setProperty("defaultGroup", (String) defaultGroupComboBox.getSelectedItem());
	}

	public Properties getImportProperties() {
		return importProperties;
	}
}