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

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.plugins.dynamiclookup.client.dialog.LookupValueDialog;
import com.mirth.connect.plugins.dynamiclookup.client.exception.LookupApiClientException;
import com.mirth.connect.plugins.dynamiclookup.client.model.LookupValueTableModel;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.client.util.FileChooser;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.ImportValuesResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.LookupAllValuesResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;
import com.mirth.connect.plugins.dynamiclookup.shared.util.CsvLineParser;

import net.miginfocom.swing.MigLayout;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-05-13 10:25 AM
 */
public class ValuePanel extends JPanel {
	private final Logger logger = LogManager.getLogger(this.getClass());

	private final Frame parent = PlatformUI.MIRTH_FRAME;

	private JPanel noGroupSelectedPanel;
	private JPanel contentPanel;

	private JTable valueTable;
	private LookupValueTableModel valueTableModel;
	private JTextField valueFilterField;
	private JButton searchButton;
	private JButton addValueButton;
	private JButton removeSelectedButton;
	private JButton importCsvButton;
	private JButton exportButton;
	private JButton prevPageButton;
	private JButton nextPageButton;
	private JButton goToPageButton;
	private JLabel pageInfoLabel;
	private JComboBox<Integer> pageSizeComboBox;
	private JLabel entriesInfoLabel;

	private LookupGroup selectedGroup;

	private int currentPage = 1;
	private int pageSize = 25;
	private int totalCount = 0;

	public ValuePanel() {
		initComponents();
		initLayout();

		updateValues(null);
	}

	private void initComponents() {
		setBackground(UIConstants.BACKGROUND_COLOR);

		// No group selected panel
		noGroupSelectedPanel = new NoGroupSelectedPanel();

		// Content panel

		// Value Filter
		valueFilterField = new JTextField();
		valueFilterField.setToolTipText("Filter values by key or value");
		valueFilterField.addActionListener(e -> {
			currentPage = 1;
			loadPage(currentPage);
		});

		// Value Table
		valueTableModel = new LookupValueTableModel();
		valueTable = new JTable(valueTableModel);
		valueTable.setRowHeight(26);
		valueTable.getColumnModel().getColumn(LookupValueTableModel.ACTION_COLUMN).setCellRenderer(new ButtonRenderer());
		valueTable.getColumnModel().getColumn(LookupValueTableModel.ACTION_COLUMN).setCellEditor(new ButtonEditor(valueTable, valueTableModel, e -> handleEditValue((Integer) e.getSource()), e -> handleRemoveValue((Integer) e.getSource())));
		valueTable.getColumnModel().getColumn(LookupValueTableModel.ACTION_COLUMN).setPreferredWidth(120); // Adjust for button width
		valueTable.getColumnModel().getColumn(LookupValueTableModel.ACTION_COLUMN).setMinWidth(120); // Adjust for button width
		valueTable.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				boolean hasSelection = valueTable.getSelectedRowCount() > 0;
				removeSelectedButton.setEnabled(hasSelection);
			}
		});

		// Search/Filter button
		searchButton = new JButton("Search", UIManager.getIcon("FileView.fileIcon"));
		searchButton.setIcon(UIConstants.ICON_FILE_PICKER);
		searchButton.setToolTipText("Search");
		searchButton.setIconTextGap(5);
		searchButton.addActionListener(e -> {
			currentPage = 1;
			loadPage(currentPage);
		});

		// Value Buttons
		addValueButton = new JButton("Add");
		addValueButton.addActionListener(e -> {
			handleAddValue();
		});

		// Value Buttons
		removeSelectedButton = new JButton("Remove Selected");
		removeSelectedButton.addActionListener(e -> {
			handleRemoveSeleted();
		});
		removeSelectedButton.setEnabled(false);

		importCsvButton = new JButton("Import Csv");
		importCsvButton.addActionListener(e -> {
			handleImportCsv();
		});

		exportButton = new JButton("Export");
		exportButton.addActionListener(e -> {
			handleExport();
		});

		prevPageButton = new JButton("Previous");
		prevPageButton.addActionListener(e -> goToPage(currentPage - 1));

		nextPageButton = new JButton("Next");
		nextPageButton.addActionListener(e -> goToPage(currentPage + 1));

		goToPageButton = new JButton("Go");
		goToPageButton.addActionListener(e -> showGoToPageDialog());

		pageInfoLabel = new JLabel("Page 1");
		entriesInfoLabel = new JLabel("Showing 0 to 0 of 0 entries");

		pageSizeComboBox = new JComboBox<>(new Integer[] { 10, 25, 50, 100, 200, 500, 1000 });
		pageSizeComboBox.setSelectedItem(pageSize);
		pageSizeComboBox.addActionListener(e -> {
			int selected = (int) pageSizeComboBox.getSelectedItem();
			if (selected != pageSize) {
				currentPage = 1;
				pageSize = selected;
				loadPage(currentPage);
			}
		});
	}

	private void initLayout() {
		setLayout(new CardLayout());

		// Content panel with titled border
		contentPanel = new JPanel(new MigLayout("insets 8, fill"));
		contentPanel.setBackground(UIConstants.BACKGROUND_COLOR);

		JPanel topButtonPanel = new JPanel(new MigLayout("insets 0, fill", "", ""));
		topButtonPanel.setBackground(UIConstants.BACKGROUND_COLOR);
		topButtonPanel.add(valueFilterField, "w 150!, growx, split 2");
		topButtonPanel.add(searchButton);
		topButtonPanel.add(addValueButton, "gapleft push, split 4");
		topButtonPanel.add(removeSelectedButton);
		topButtonPanel.add(importCsvButton);
		topButtonPanel.add(exportButton);

		contentPanel.add(topButtonPanel, "growx, wrap");
		contentPanel.add(new JScrollPane(valueTable), "grow, push, wrap");

		JPanel paginationPanel = new JPanel(new BorderLayout());
		paginationPanel.setBackground(UIConstants.BACKGROUND_COLOR);

		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		leftPanel.setBackground(UIConstants.BACKGROUND_COLOR);
		leftPanel.add(new JLabel("Page size:"));
		leftPanel.add(pageSizeComboBox);
		leftPanel.add(entriesInfoLabel);

		JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		rightPanel.setBackground(UIConstants.BACKGROUND_COLOR);
		rightPanel.add(prevPageButton);
		rightPanel.add(pageInfoLabel);
		rightPanel.add(nextPageButton);
		rightPanel.add(goToPageButton);

		paginationPanel.add(leftPanel, BorderLayout.WEST);
		paginationPanel.add(rightPanel, BorderLayout.EAST);

		contentPanel.add(paginationPanel, "growx, wrap");

		add(contentPanel, "content");
		add(noGroupSelectedPanel, "noGroup");
	}

	public void updateValues(LookupGroup selectedGroup) {
		boolean showContent = selectedGroup != null;

		contentPanel.setVisible(showContent);
		noGroupSelectedPanel.setVisible(!showContent);

		// Detect group change
		if (!Objects.equals(this.selectedGroup, selectedGroup)) {
			// Clear filters only on group change
			valueFilterField.setText("");
		}

		this.selectedGroup = selectedGroup;
		this.currentPage = 1;

		loadPage(currentPage);
	}

	private void loadPage(int page) {
		valueTableModel.clear();

		if (selectedGroup == null) {
			return;
		}

		int offset = (page - 1) * pageSize;

		new SwingWorker<LookupAllValuesResponse, Void>() {
			protected LookupAllValuesResponse doInBackground() throws Exception {
				String pattern = valueFilterField.getText().trim();
				return LookupServiceClient.getInstance().getAllValues(selectedGroup.getId(), offset, pageSize, pattern);
			}

			protected void done() {
				try {
					LookupAllValuesResponse response = get();
					List<LookupValue> values = response.getValues();
					totalCount = response.getTotalCount();
					valueTableModel.setValues(values);
					currentPage = page;
					updatePaginationControls();
				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					if (cause instanceof LookupApiClientException) {
						showError("Failed to load values: " + cause.getMessage());
					} else {
						logger.error("Unexpected error while loading values", e);
						showError("An unexpected error occurred: " + cause.getMessage());
					}
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt(); // Restore interrupt status
					showError("Operation was interrupted.");
				}
			}
		}.execute();
	}

	private void updatePaginationControls() {
		int totalPages = (int) Math.ceil((double) totalCount / pageSize);
		if (totalPages == 0) {
			pageInfoLabel.setText("Page 0 of 0");
		} else {
			pageInfoLabel.setText("Page " + currentPage + " of " + totalPages);
		}

		if (totalCount == 0) {
			entriesInfoLabel.setText("Showing 0 to 0 of 0 entries");
		} else {
			int start = (currentPage - 1) * pageSize + 1;
			int end = Math.min(currentPage * pageSize, totalCount);
			entriesInfoLabel.setText("Showing " + start + " to " + end + " of " + totalCount + " entries");
		}

		prevPageButton.setEnabled(currentPage > 1);
		nextPageButton.setEnabled(currentPage < totalPages);
	}

	private void goToPage(int page) {
		if (page >= 1 && (page - 1) * pageSize < totalCount) {
			loadPage(page);
		}
	}

	private void showGoToPageDialog() {
		if (totalCount == 0) {
			return;
		}

		JComboBox<Integer> pageSelector = new JComboBox<>();
		int totalPages = (int) Math.ceil((double) totalCount / pageSize);

		for (int i = 1; i <= totalPages; i++) {
			pageSelector.addItem(i);
		}

		int result = JOptionPane.showConfirmDialog(this, pageSelector, "Select Page", JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {
			Integer selectedPage = (Integer) pageSelector.getSelectedItem();
			if (selectedPage != null) {
				loadPage(selectedPage);
			}
		}
	}

	private void setPaginationControlsEnabled(boolean enabled) {
		prevPageButton.setEnabled(enabled);
		nextPageButton.setEnabled(enabled);
		pageSizeComboBox.setEnabled(enabled);
	}

	private void handleAddValue() {
		if (selectedGroup != null) {
			LookupValue lookupValue = new LookupValue();
			LookupValueDialog dialog = new LookupValueDialog(parent, lookupValue, selectedGroup, false);
			if (dialog.isSaved()) {
				currentPage = 1;
				loadPage(currentPage);
			}
		} else {
			showError("Please select a Group");
		}
	}

	private void handleRemoveSeleted() {
		if (selectedGroup == null) {
			return;
		}

		int[] selectedRows = valueTable.getSelectedRows();
		if (selectedRows.length == 0) {
			JOptionPane.showMessageDialog(this, "No rows selected.", "Delete", JOptionPane.WARNING_MESSAGE);
			return;
		}

		int confirm = JOptionPane.showConfirmDialog(this, "Delete " + selectedRows.length + " selected values?", "Confirm Delete", JOptionPane.YES_NO_OPTION);

		if (confirm != JOptionPane.YES_OPTION) {
			return;
		}

		List<String> keysToDelete = new ArrayList<>();
		for (int row : selectedRows) {
			int rowIndex = valueTable.convertRowIndexToModel(row);
			LookupValue value = valueTableModel.getValue(rowIndex);
			keysToDelete.add(value.getKeyValue());
		}

		// --- Progress dialog (modal) ---
		final JDialog progressDialog = new JDialog(this.parent, "Deleting Values", true);
		final JProgressBar progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		final JLabel statusLabel = new JLabel("Deleted 0 of " + keysToDelete.size());
		final JButton cancelButton = new JButton("Cancel");

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttonPanel.add(cancelButton);

		progressDialog.setLayout(new BorderLayout(10, 10));
		progressDialog.add(statusLabel, BorderLayout.NORTH);
		progressDialog.add(progressBar, BorderLayout.CENTER);
		progressDialog.add(buttonPanel, BorderLayout.SOUTH);
		progressDialog.setSize(350, 120);
		progressDialog.setLocationRelativeTo(this.parent);
		progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

		SwingWorker<Void, int[]> worker = new SwingWorker<Void, int[]>() {

			private final List<String> failedKeys = new ArrayList<>();
			private int processed = 0;

			@Override
			protected Void doInBackground() {
				for (String key : keysToDelete) {
					if (isCancelled()) {
						break;
					}

					try {
						LookupServiceClient.getInstance().deleteValue(selectedGroup.getId(), key);
					} catch (Exception ex) {
						logger.warn("Failed to delete key '{}' in group {}: {}", key, selectedGroup.getId(), ex.getMessage());
						failedKeys.add(key);
					}

					processed++;
					publishProgress(processed, keysToDelete.size());
				}

				return null;
			}

			private void publishProgress(int processed, int total) {
				int progress = total > 0 ? (int) ((processed / (double) total) * 100) : 0;
				progress = Math.min(progress, 100);

				publish(new int[] { progress, processed, total });
			}

			@Override
			protected void process(List<int[]> chunks) {
				if (!chunks.isEmpty()) {
					int[] latest = chunks.get(chunks.size() - 1);
					int pct = latest[0];
					int done = latest[1];
					int total = latest[2];
					progressBar.setValue(pct);
					statusLabel.setText("Deleted " + done + " of " + total);
				}
			}

			@Override
			protected void done() {
				try {
					progressDialog.dispose();
					get(); // propagate exceptions if any

					// Reload table once at the end
					updateValues(selectedGroup);

					if (isCancelled()) {
						JOptionPane.showMessageDialog(parent, "Delete was cancelled. Completed " + processed + "/" + keysToDelete.size() + ".", "Delete Cancelled", JOptionPane.WARNING_MESSAGE);
					} else {
						if (failedKeys.isEmpty()) {
							JOptionPane.showMessageDialog(parent, "Deleted " + processed + " values.", "Delete Complete", JOptionPane.INFORMATION_MESSAGE);
						} else {
							// Trim failed list for readability if large
							String failedPreview = String.join(", ", failedKeys);
							if (failedPreview.length() > 200) {
								failedPreview = failedPreview.substring(0, 200) + "...";
							}
							JOptionPane.showMessageDialog(parent, "Deleted " + (processed - failedKeys.size()) + "/" + keysToDelete.size() + ". Failed: " + failedKeys.size() + " (" + failedPreview + ")", "Delete Completed with Errors", JOptionPane.WARNING_MESSAGE);
						}
					}
				} catch (ExecutionException ex) {
					Throwable cause = ex.getCause();
					showError("Delete failed: " + (cause != null && cause.getMessage() != null ? cause.getMessage() : ex.getClass().getSimpleName()));
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					showError("Delete was interrupted.");
				} finally {
				}
			}
		};

		// Cancel button
		cancelButton.addActionListener(e -> worker.cancel(true));

		// Window close confirmation
		progressDialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				int confirm = JOptionPane.showConfirmDialog(progressDialog, "Delete is in progress. Do you want to cancel?", "Confirm Cancel", JOptionPane.YES_NO_OPTION);
				if (confirm == JOptionPane.YES_OPTION) {
					worker.cancel(true);
				}
			}
		});

		worker.execute();
		progressDialog.setVisible(true);
	}

	private void handleEditValue(int row) {
		if (row >= 0) {
			LookupValue lookupValue = valueTableModel.getValue(row);
			LookupValue copy = new LookupValue(lookupValue);
			if (selectedGroup != null) {
				LookupValueDialog dialog = new LookupValueDialog(parent, copy, selectedGroup, true);
				if (dialog.isSaved()) {
					loadPage(currentPage);
				}
			}
		}
	}

	private void handleRemoveValue(int row) {
		LookupValue value = valueTableModel.getValue(row);
		if (row >= 0) {
			int confirm = JOptionPane.showConfirmDialog(parent, "Are you sure you want to delete value with key: " + value.getKeyValue() + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);

			if (confirm == JOptionPane.YES_OPTION) {
				if (selectedGroup != null) {
					try {
						LookupServiceClient.getInstance().deleteValue(selectedGroup.getId(), value.getKeyValue());
						loadPage(currentPage);
					} catch (LookupApiClientException e) {
						showError(e.getError().getMessage());
					} catch (Exception e) {
						logger.error("Unexpected error while remove value", e);
						showError("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
					}
				}
			}
		}
	}

	private void handleImportCsv() {
		if (selectedGroup == null) {
			showError("Please select a Group");
			return;
		}

		JFileChooser importFileChooser = new JFileChooser();
		importFileChooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));

		File currentDir = new File(Frame.userPreferences.get("currentDirectory", ""));
		if (currentDir.exists()) {
			importFileChooser.setCurrentDirectory(currentDir);
		}

		if (importFileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = importFileChooser.getSelectedFile();
			if (file != null) {

				if (!checkCsvFile(file)) {
					return;
				}

				int result = JOptionPane.showConfirmDialog(this, "Do you want to clear existing values before import?", "Clear Existing Values?", JOptionPane.YES_NO_OPTION);

				boolean clearExisting = (result == JOptionPane.YES_OPTION);

				// Create modal progress dialog
				JDialog progressDialog = new JDialog(this.parent, "Importing CSV", true);
				JProgressBar progressBar = new JProgressBar(0, 100);
				progressBar.setStringPainted(true);
				JLabel statusLabel = new JLabel("Imported 0 of ? entries");

				JButton cancelButton = new JButton("Cancel");
				JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
				buttonPanel.add(cancelButton);

				progressDialog.setLayout(new BorderLayout(10, 10));
				progressDialog.add(statusLabel, BorderLayout.NORTH);
				progressDialog.add(progressBar, BorderLayout.CENTER);
				progressDialog.add(buttonPanel, BorderLayout.SOUTH);
				progressDialog.setSize(350, 120);
				progressDialog.setLocationRelativeTo(this.parent);
				progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

				SwingWorker<Void, int[]> importWorker = new SwingWorker<Void, int[]>() {
					@Override
					protected Void doInBackground() throws Exception {
						try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
							// Count total lines for accurate progress
							int totalLines = -1; // skip header
							while (reader.readLine() != null) {
								totalLines++;
							}

							// Re-open reader for actual import
							try (BufferedReader reader2 = new BufferedReader(new FileReader(file))) {
								String line;
								boolean isFirstLine = true;
								boolean isFirstBatch = true;
								int lineNumber = 0;
								int processedLines = 0;
								int batchSize = 100;
								Map<String, String> batchMap = new LinkedHashMap<>();
								Set<String> seenKeys = new HashSet<>();

								while ((line = reader2.readLine()) != null && !isCancelled()) {
									lineNumber++;
									if (isFirstLine) {
										isFirstLine = false;
										continue;
									}

									if (line.trim().isEmpty()) {
										continue;
									}

									Map.Entry<String, String> entry = CsvLineParser.parseLine(line, lineNumber);
									if (entry == null) {
										logger.error("Skipping malformed or empty entry at line " + lineNumber + ": " + line);
										continue;
									}

									String key = entry.getKey();

									if (!seenKeys.add(key)) {
										continue;
									}

									if (batchMap.containsKey(key)) {
										continue;
									}

									batchMap.put(entry.getKey(), entry.getValue());

									processedLines++;
									publishProgress(processedLines, totalLines);

									if (batchMap.size() == batchSize) {
										importValues(selectedGroup.getId(), batchMap, clearExisting && isFirstBatch);
										batchMap.clear();
										isFirstBatch = false;
									}
								}

								if (!isCancelled() && !batchMap.isEmpty()) {
									importValues(selectedGroup.getId(), batchMap, clearExisting && isFirstBatch);
								}

								Frame.userPreferences.put("currentDirectory", file.getParent());
							}

						} catch (Exception e) {
							logger.error("Failed to import lookup values from CSV file", e);
							throw e;
						}

						return null;
					}

					private void publishProgress(int processed, int total) {
						int progress = total > 0 ? (int) ((processed / (double) total) * 100) : 0;
						progress = Math.min(progress, 100);

						publish(new int[] { progress, processed, total });
					}

					@Override
					protected void process(List<int[]> chunks) {
						if (!chunks.isEmpty()) {
							int[] latest = chunks.get(chunks.size() - 1);
							int progress = latest[0];
							int imported = latest[1];
							int total = latest[2];
							progressBar.setValue(progress);
							statusLabel.setText("Imported " + imported + " of " + total + " entries");
						}
					}

					@Override
					protected void done() {
						progressDialog.dispose();

						updateValues(selectedGroup);

						if (isCancelled()) {
							JOptionPane.showMessageDialog(parent, "CSV import was cancelled.", "Import Cancelled", JOptionPane.WARNING_MESSAGE);
							return;
						}

						try {
							get(); // triggers exception handling if doInBackground failed

							JOptionPane.showMessageDialog(parent, "CSV import completed.", "Import Complete", JOptionPane.INFORMATION_MESSAGE);
						} catch (ExecutionException e) {
							Throwable cause = e.getCause();
							showError("Import failed: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()));
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt(); // restore interrupt status
							showError("Import was interrupted.");
						}
					}
				};

				// Cancel button
				cancelButton.addActionListener(e -> importWorker.cancel(true));

				// Window close
				progressDialog.addWindowListener(new WindowAdapter() {
					@Override
					public void windowClosing(WindowEvent e) {
						int confirm = JOptionPane.showConfirmDialog(progressDialog, "Import is still in progress. Do you want to cancel?", "Confirm Cancel", JOptionPane.YES_NO_OPTION);
						if (confirm == JOptionPane.YES_OPTION) {
							importWorker.cancel(true);
						}
					}
				});

				importWorker.execute();
				progressDialog.setVisible(true);
			}
		}
	}

	private boolean checkCsvFile(File file) {
		// 1. Extension check
		if (!file.getName().toLowerCase().endsWith(".csv")) {
			showError("File does not have a .csv extension.");
			return false;
		}

		// 2. MIME type check
		try {
			Path path = file.toPath();
			String mimeType = Files.probeContentType(path);
			if (mimeType == null || !(mimeType.equals("text/plain") || mimeType.equals("text/csv") || mimeType.equals("application/vnd.ms-excel") || mimeType.startsWith("text/"))) {
				showError("File does not appear to be a valid CSV (detected type: " + mimeType + ").");
				return false;
			}
		} catch (IOException e) {
			showError("Failed to detect file type: " + e.getMessage());
			return false;
		}

		// 3. Content check
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String header = reader.readLine();
			if (header == null || header.split(",", -1).length < 2) {
				showError("CSV file must contain at least two columns: key and value.");
				return false;
			}
		} catch (IOException e) {
			showError("Failed to read file for content validation: " + e.getMessage());
			return false;
		}

		return true;
	}

	private void importValues(Integer groupId, Map<String, String> values, boolean clearExisting) {
		try {
			ImportValuesResponse response = LookupServiceClient.getInstance().importValues(groupId, clearExisting, values);
		} catch (LookupApiClientException e) {
			showError(e.getError().getMessage());
		} catch (Exception e) {
			logger.error("Unexpected error while importing values", e);
			showError("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
		}
	}

	private void handleExport() {
		if (selectedGroup == null) {
			showError("Please select a Group");
			return;
		}

		Date currentDate = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm");
		String dateString = formatter.format(currentDate);
		String defaultFileName = "all_values_" + dateString + ".csv";

		final File file = new FileChooser().createFileForExport(parent, defaultFileName, "csv");
		if (file == null) {
			return;
		}

		// Create modal dialog
		JDialog progressDialog = new JDialog(this.parent, "Exporting CSV", true); // modal = true
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
		progressDialog.setLocationRelativeTo(this.parent);
		progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

		SwingWorker<Void, int[]> exportWorker = new SwingWorker<Void, int[]>() {
			@Override
			protected Void doInBackground() throws Exception {
				int offset = 0;
				int limit = 1000;
				int processed = 0;
				int total = -1;
				boolean wroteHeader = false;

				try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
					while (!isCancelled()) {
						LookupAllValuesResponse page = LookupServiceClient.getInstance().getAllValues(selectedGroup.getId(), offset, limit, "");

						List<LookupValue> pageValues = page.getValues();
						if (pageValues == null || pageValues.isEmpty()) {
							break;
						}

						if (!wroteHeader) {
							writer.write("key,value");
							writer.newLine();
							wroteHeader = true;
						}

						for (LookupValue value : pageValues) {
							if (isCancelled()) {
								break;
							}

							writer.write(String.format("%s,%s", escapeCsv(value.getKeyValue()), escapeCsv(value.getValueData())));
							writer.newLine();

							processed++;
							if (total < 0) {
								total = page.getTotalCount();
							}

							publishProgress(processed, total);
						}

						if (pageValues.size() < limit || isCancelled()) {
							break;
						}

						offset += limit;
					}

					Frame.userPreferences.put("currentDirectory", file.getParent());

				} catch (Exception e) {
					logger.error("Failed to export values to CSV", e);
					throw e;
				}

				return null;
			}

			private void publishProgress(int processed, int total) {
				int progress = total > 0 ? (int) ((processed / (double) total) * 100) : 0;
				progress = Math.min(progress, 100);

				publish(new int[] { progress, processed, total });
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

					JOptionPane.showMessageDialog(parent, "CSV export completed.", "Export Complete", JOptionPane.INFORMATION_MESSAGE);
				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					showError("Export failed: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt(); // restore interrupt status
					showError("Export was interrupted.");
				}
			}
		};

		// Cancel button action
		cancelButton.addActionListener(e -> exportWorker.cancel(true));

		// Handle window close
		progressDialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				int confirm = JOptionPane.showConfirmDialog(progressDialog, "Export is still in progress. Do you want to cancel?", "Confirm Cancel", JOptionPane.YES_NO_OPTION);
				if (confirm == JOptionPane.YES_OPTION) {
					exportWorker.cancel(true);
				}
			}
		});

		exportWorker.execute();
		progressDialog.setVisible(true);
	}

	private String escapeCsv(String input) {
		if (input == null)
			return "";
		if (input.contains(",") || input.contains("\"") || input.contains("\n")) {
			input = input.replace("\"", "\"\"");
			return "\"" + input + "\"";
		}
		return input;
	}

	private void showInformation(String msg) {
		PlatformUI.MIRTH_FRAME.alertInformation(parent, msg);
	}

	private void showError(String err) {
		PlatformUI.MIRTH_FRAME.alertError(parent, err);
	}
}