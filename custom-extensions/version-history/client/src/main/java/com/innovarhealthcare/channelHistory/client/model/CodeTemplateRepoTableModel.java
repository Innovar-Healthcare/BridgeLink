/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.model;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.innovarhealthcare.channelHistory.shared.dto.response.RepoItemMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2024-11-27 4:25 PM
 */
public class CodeTemplateRepoTableModel extends AbstractTableModel {
    private static final Logger logger = LogManager.getLogger(CodeTemplateRepoTableModel.class);
    private static final String[] COLUMN_NAMES = {"Code Template Id", "Code Template Name", "Last Commit Id"};
    private final List<RepoItemMetadata> entries;

    /**
     * Constructor with metadata list
     *
     * @param metadataList List of code template metadata (no full content)
     */
    public CodeTemplateRepoTableModel(List<RepoItemMetadata> metadataList) {
        this.entries = new ArrayList<>();
        for (RepoItemMetadata metadata : metadataList) {
            if (metadata != null && metadata.getId() != null) {
                entries.add(metadata);
            } else {
                logger.warn("Skipping null or invalid metadata entry");
            }
        }
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        RepoItemMetadata metadata = entries.get(rowIndex);

        switch (columnIndex) {
            case 0: // Code Template Id
                return metadata.getId() != null ? metadata.getId() : "(unknown)";
            case 1: // Code Template Name
                return metadata.getName() != null ? metadata.getName() : "(unknown)";
            case 2: // Last Commit Id
                return metadata.getLastCommitId() != null ? metadata.getLastCommitId() : "(unknown)";
            default:
                throw new IllegalArgumentException("Unknown column number: " + columnIndex);
        }
    }

    /**
     * Get metadata at specified row
     *
     * @param row Row index
     * @return RepoItemMetadata or null if invalid row
     */
    public RepoItemMetadata getMetadataAt(int row) {
        if (row < 0 || row >= entries.size()) {
            return null;
        }
        return entries.get(row);
    }

    /**
     * Get code template ID at specified row
     *
     * @param row Row index
     * @return Template ID or null if invalid row
     */
    public String getCodeTemplateIdAt(int row) {
        RepoItemMetadata metadata = getMetadataAt(row);
        return metadata != null ? metadata.getId() : null;
    }

    /**
     * Get code template name at specified row
     *
     * @param row Row index
     * @return Template name or null if invalid row
     */
    public String getCodeTemplateNameAt(int row) {
        RepoItemMetadata metadata = getMetadataAt(row);
        return metadata != null ? metadata.getName() : null;
    }

    /**
     * Get last commit ID at specified row
     *
     * @param row Row index
     * @return Last commit ID or null if invalid row
     */
    public String getLastCommitIdAt(int row) {
        RepoItemMetadata metadata = getMetadataAt(row);
        return metadata != null ? metadata.getLastCommitId() : null;
    }

    /**
     * Get file path at specified row
     *
     * @param row Row index
     * @return File path or null if invalid row
     */
    public String getPathAt(int row) {
        RepoItemMetadata metadata = getMetadataAt(row);
        return metadata != null ? metadata.getPath() : null;
    }

    /**
     * Get all metadata entries
     *
     * @return Unmodifiable list of metadata
     */
    public List<RepoItemMetadata> getAllMetadata() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Get selected metadata by indices
     *
     * @param selectedRows Array of selected row indices
     * @return List of selected metadata
     */
    public List<RepoItemMetadata> getSelectedMetadata(int[] selectedRows) {
        List<RepoItemMetadata> selected = new ArrayList<>();
        for (int row : selectedRows) {
            RepoItemMetadata metadata = getMetadataAt(row);
            if (metadata != null) {
                selected.add(metadata);
            }
        }
        return selected;
    }
}