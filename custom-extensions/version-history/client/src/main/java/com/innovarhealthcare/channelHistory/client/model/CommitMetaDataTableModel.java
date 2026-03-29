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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.innovarhealthcare.channelHistory.shared.model.CommitMetaData;

public class CommitMetaDataTableModel extends AbstractTableModel {
    private final List<CommitMetaData> revisions;
    private static final DateFormat df = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
    private static final String[] columnNames = {"Commit Id", "Message", "Committer", "Date", "Server Id", "Server Name"};

    /**
     * Constructor that accepts List<CommitMetaData> directly
     *
     * @param revisions List of CommitMetaData objects
     */
    public CommitMetaDataTableModel(List<CommitMetaData> revisions) {
        this.revisions = revisions != null ? new ArrayList<>(revisions) : new ArrayList<>();
    }

    @Override
    public int getRowCount() {
        return revisions.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0: // Commit Id
            case 1: // Message
            case 2: // Committer
            case 4: // Server Id
            case 5: // Server Name
                return String.class;
            case 3: // Date
                return String.class; // Formatted date is a string
            default:
                return super.getColumnClass(columnIndex);
        }
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        CommitMetaData meta = revisions.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return meta.getHash();
            case 1:
                return meta.getMessageContent();
            case 2:
                return meta.getCommitter();
            case 3:
                return formatTime(meta.getTimestamp());
            case 4:
                return meta.getServerId();
            case 5:
                String serverName = meta.getServerName();
                return serverName != null ? serverName : "";
            default:
                throw new IllegalArgumentException("Unknown column number: " + columnIndex);
        }
    }

    /**
     * Get the CommitMetaData object at the specified row
     *
     * @param row Row index
     * @return CommitMetaData object or null if row is out of bounds
     */
    public CommitMetaData getCommitMetaDataAt(int row) {
        if (row < 0 || row >= revisions.size()) {
            return null;
        }
        return revisions.get(row);
    }

    /**
     * Format timestamp to human-readable string
     * Shows relative time (e.g., "2 hours ago") for recent commits,
     * absolute date for older commits
     *
     * @param t Timestamp in milliseconds
     * @return Formatted time string
     */
    private String formatTime(long t) {
        if (t <= 0) {
            return "(unknown)";
        }
        Instant commitTime = Instant.ofEpochMilli(t);
        Instant now = Instant.now();
        Duration duration = Duration.between(commitTime, now);

        if (duration.toDays() > 3) {
            return df.format(new Date(t));
        }

        if (duration.toDays() > 0) {
            return duration.toDays() + " days ago";
        }

        if (duration.toHours() > 0) {
            return duration.toHours() + " hours ago";
        }

        if (duration.toMinutes() > 0) {
            return duration.toMinutes() + " minutes ago";
        }

        return duration.getSeconds() + " seconds ago";
    }
}