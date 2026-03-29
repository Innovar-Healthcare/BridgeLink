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

import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import com.mirth.connect.client.ui.components.MirthTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class CommitMetaDataTable extends MirthTable {
    private static final Logger logger = LogManager.getLogger(CommitMetaDataTable.class);
    private final MultiLineTableCellRenderer messageRenderer = new MultiLineTableCellRenderer();

    public CommitMetaDataTable() {
        super();
        initializeUI();
    }

    private void initializeUI() {
        try {
            // Configure table appearance
            setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
            setRowSelectionAllowed(true);
            setColumnSelectionAllowed(false);
            setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            setRowHeight(20);
        } catch (Exception e) {
            logger.error("Failed to initialize CommitMetaDataTable UI", e);
        }
    }

    @Override
    public void setModel(TableModel dataModel) {
        super.setModel(dataModel);
        configureColumns();
        applyMessageRenderer();
        updateRowHeights();
    }

    private void configureColumns() {
        if (getColumnCount() == 5) { // Ensure model has expected columns
            try {
                TableColumnModel columnModel = getColumnModel();
                columnModel.getColumn(0).setPreferredWidth(150); // Commit Id (short hash)
                columnModel.getColumn(1).setPreferredWidth(250); // Message
                columnModel.getColumn(2).setPreferredWidth(40); // Committer
                columnModel.getColumn(3).setPreferredWidth(80); // Date
                columnModel.getColumn(4).setPreferredWidth(150); // Server Id
            } catch (Exception e) {
                logger.error("Failed to configure table columns", e);
            }
        }
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        if (column == 1) {
            return messageRenderer;
        }
        return super.getCellRenderer(row, column);
    }

    private void applyMessageRenderer() {
        try {
            if (getColumnCount() >= 2) {
                getColumnModel().getColumn(1).setCellRenderer(messageRenderer);
            }
        } catch (Exception e) {
            logger.error("Failed to apply MultiLineTableCellRenderer", e);
        }
    }

    private void updateRowHeights() {
        SwingUtilities.invokeLater(() -> {
            try {
                int column = 1; // Message column
                if (getColumnCount() <= column) {
                    return;
                }

                int columnWidth = getColumnModel().getColumn(column).getWidth();

                for (int row = 0; row < getRowCount(); row++) {
                    Object value = getValueAt(row, column);
                    String text = value != null ? value.toString() : "";

                    JTextArea tempArea = new JTextArea(text);
                    tempArea.setLineWrap(true);
                    tempArea.setWrapStyleWord(true);
                    tempArea.setFont(getFont());
                    tempArea.setSize(columnWidth, Short.MAX_VALUE);

                    int preferredHeight = tempArea.getPreferredSize().height;
                    int currentHeight = getRowHeight(row);
                    int finalHeight = Math.max(preferredHeight, 20);

                    if (currentHeight != finalHeight) {
                        setRowHeight(row, finalHeight);
                    }

//                    logger.debug("Row {} -> Height: {}, Text: {}", row, finalHeight, text);
                }
            } catch (Exception e) {
                logger.error("Failed to update row heights", e);
            }
        });
    }


    @Override
    public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
        if (rowIndex >= 0 && rowIndex < getRowCount() && columnIndex >= 0 && columnIndex < getColumnCount()) {
            super.changeSelection(rowIndex, columnIndex, true, false);
        }
    }

}