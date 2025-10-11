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

import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupProperties;

import net.miginfocom.swing.MigLayout;

public class LookupSettingDialog extends MirthDialog {
	private final Logger logger = LogManager.getLogger(this.getClass());

	private JCheckBox pruneEnabledCheck;
	private JSpinner retentionDaysSpinner;
	private JLabel retentionDaysLabel;
	private JButton saveButton;
	private JButton cancelButton;

	private Frame parent;
	private LookupProperties currentProperties;

	public LookupSettingDialog(Frame parent) {
		super(parent, true);

		this.parent = parent;

		initComponents();
		initLayout();

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setTitle("Lookup Settings");
		pack();
		setLocationRelativeTo(parent);

		// ---- Load server settings
		loadSettingsFromServer();

		// ---- Now show the dialog ----
		setVisible(true);
	}

	private void initComponents() {
		setBackground(UIConstants.BACKGROUND_COLOR);
		getContentPane().setBackground(getBackground());

		// --- Purge (prune) old logs checkbox ---
		pruneEnabledCheck = new JCheckBox("Enabled");
		pruneEnabledCheck.setOpaque(false);
		pruneEnabledCheck.setSelected(false);
		pruneEnabledCheck.addActionListener(e -> handlePruneEnabledChanged());

		// --- Retention days spinner ---
		retentionDaysLabel = new JLabel("Retention days:");
		retentionDaysSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 3650, 1));
		retentionDaysSpinner.setEnabled(pruneEnabledCheck.isSelected());

		saveButton = new JButton("Save");
		saveButton.addActionListener(evt -> save());

		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(evt -> close());
	}

	private void initLayout() {
		setLayout(new MigLayout("insets 12, novisualpadding, hidemode 3, fill"));

		JPanel settingPanel = new JPanel(new MigLayout("novisualpadding, hidemode 0, align center, insets 0 0 0 0, fill", "25[right][grow,fill]"));
		settingPanel.setBackground(UIConstants.BACKGROUND_COLOR);
		settingPanel.setBorder(BorderFactory.createEmptyBorder());

		settingPanel.add(new JLabel("Purge Old Audit History:"), "gapbottom 8");
		settingPanel.add(pruneEnabledCheck, "wrap, gapbottom 8");
		settingPanel.add(retentionDaysLabel, "gapbottom 8");
		settingPanel.add(retentionDaysSpinner, "wrap, w 60!, gapbottom 8");

		add(settingPanel, "grow, push");
		add(new JSeparator(), "newline, sx, growx");

		add(saveButton, "newline, sx, right, split 2");
		add(cancelButton);
	}

	private void save() {
		// Read UI values
		final boolean pruneEnabled = pruneEnabledCheck.isSelected();
		final int retentionDays = (Integer) retentionDaysSpinner.getValue();

		// Validate: retention must be >= 1 if prune is enabled
		if (pruneEnabled && retentionDays < 1) {
			showError("Retention days must be >= 1 when purge is enabled.");
			return;
		}

		if (currentProperties == null) {
			currentProperties = LookupProperties.getDefault();
		}
		final LookupProperties toSave = currentProperties;

		toSave.setAuditPruneEnabled(pruneEnabled);
		toSave.setAuditPruneRetentionDays(retentionDays);

		// Disable UI during save and show wait cursor
		saveButton.setEnabled(false);
		pruneEnabledCheck.setEnabled(false);
		retentionDaysSpinner.setEnabled(false);

		try {
			parent.mirthClient.setPluginProperties("Lookup Table Management System", toSave.toProperties());

			close();
		} catch (Exception e) {
			logger.error("Unexpected error while saving settings", e);
			showError("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
			return;
		}
	}

	private void close() {
		dispose();
	}

	private void loadSettingsFromServer() {
		pruneEnabledCheck.setEnabled(false);
		retentionDaysSpinner.setEnabled(false);
		saveButton.setEnabled(false);

		SwingWorker<LookupProperties, Void> worker = new SwingWorker<LookupProperties, Void>() {

			public LookupProperties doInBackground() throws Exception {
				Properties props = parent.mirthClient.getPluginProperties("Lookup Table Management System");
				return LookupProperties.fromProperties(props);
			}

			@Override
			public void done() {
				try {
					currentProperties = get();
				} catch (Exception ex) {
					showError("Failed to load settings.\n" + ex.getMessage());
					currentProperties = LookupProperties.getDefault();
				} finally {
					pruneEnabledCheck.setEnabled(true);
					retentionDaysSpinner.setEnabled(pruneEnabledCheck.isSelected());
					saveButton.setEnabled(true);

					applyPropertiesToUI(currentProperties);
				}
			}
		};

		worker.execute();
	}

	/**
	 * Applies the given LookupProperties values to the dialog UI controls. Called
	 * after loading settings from the server.
	 */
	private void applyPropertiesToUI(LookupProperties props) {
		if (props == null) {
			props = LookupProperties.getDefault();
		}

		// Extract fields from the properties model
		boolean pruneEnabled = props.isAuditPruneEnabled();
		int retentionDays = props.getAuditPruneRetentionDays();

		// Reflect values to controls
		pruneEnabledCheck.setSelected(pruneEnabled);
		retentionDaysSpinner.setValue(Math.max(1, retentionDays));

		// Ensure dependent controls update accordingly
		handlePruneEnabledChanged();
	}

	private void handlePruneEnabledChanged() {
		boolean enabled = pruneEnabledCheck.isSelected();

		// Enable/disable the spinner and label
		retentionDaysSpinner.setEnabled(enabled);
		retentionDaysLabel.setEnabled(enabled);
	}

	protected void showInformation(String msg) {
		PlatformUI.MIRTH_FRAME.alertInformation(this, msg);
	}

	protected void showError(String err) {
		PlatformUI.MIRTH_FRAME.alertError(this, err);
	}
}
