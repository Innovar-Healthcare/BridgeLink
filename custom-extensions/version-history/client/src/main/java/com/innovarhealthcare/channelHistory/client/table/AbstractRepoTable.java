/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.table;

import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdesktop.swingx.decorator.Highlighter;
import org.jdesktop.swingx.decorator.HighlighterFactory;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */
public abstract class AbstractRepoTable extends MirthTable {
    private static final Logger logger = LogManager.getLogger(AbstractRepoTable.class);

    private final int hiddenColumnIndex;

    protected AbstractRepoTable(int hiddenColumnIndex) {
        super();
        this.hiddenColumnIndex = hiddenColumnIndex;
        initializeUI();
    }

    private void initializeUI() {
        try {
            // Add alternating row striping
            Highlighter rowStripe = HighlighterFactory.createAlternateStriping(UIConstants.HIGHLIGHTER_COLOR, UIConstants.BACKGROUND_COLOR);
            setHighlighters(rowStripe);

            // Configure table appearance
            setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
            setRowSelectionAllowed(true);
            setColumnSelectionAllowed(false);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        } catch (Exception e) {
            logger.error("Failed to initialize AbstractRepoTable UI", e);
        }
    }

    @Override
    public void setModel(TableModel dataModel) {
        if (dataModel == null) {
            logger.error("Null table model provided for {}", getClass().getSimpleName());
            return;
        }

        if (dataModel instanceof DefaultTableModel && dataModel.getColumnCount() == 0) {
            logger.warn("Received DefaultTableModel with 0 columns for {}. Skipping model setting.", getClass().getSimpleName());
            return;
        }

        if (validateModel(dataModel)) {
            super.setModel(dataModel);
            configureColumns();
        } else {
            logger.error("Invalid table model for {}. Expected model with at least 3 columns, got: {} with {} columns", getClass().getSimpleName(), dataModel.getClass().getName(), dataModel.getColumnCount());
        }
    }

    protected abstract int[] getColumnWidths();

    protected abstract boolean validateModel(TableModel dataModel);

    private void configureColumns() {
        try {
            TableColumnModel columnModel = getColumnModel();
            int columnCount = getColumnCount();
            int[] widths = getColumnWidths();

            // Set widths for visible columns
            for (int i = 0; i < columnCount && i < widths.length; i++) {
                if (i != hiddenColumnIndex) {
                    columnModel.getColumn(i).setPreferredWidth(widths[i]);
                }
            }

            // Hide specified column (e.g., Last Commit Id)
            if (hiddenColumnIndex >= 0 && hiddenColumnIndex < columnCount) {
                TableColumn hiddenColumn = columnModel.getColumn(hiddenColumnIndex);
                columnModel.removeColumn(hiddenColumn);
            }
        } catch (Exception e) {
            logger.error("Failed to configure table columns for {}", getClass().getSimpleName(), e);
        }
    }

    @Override
    public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
        if (rowIndex >= 0 && rowIndex < getRowCount() && columnIndex >= 0 && columnIndex < getColumnCount()) {
            super.changeSelection(rowIndex, columnIndex, true, false);
        }
    }
}
