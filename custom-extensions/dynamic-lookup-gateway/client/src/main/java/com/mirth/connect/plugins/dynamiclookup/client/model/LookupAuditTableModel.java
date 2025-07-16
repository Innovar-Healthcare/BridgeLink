/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.client.model;

import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.GroupAuditEntriesResponse;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

import java.text.SimpleDateFormat;
import java.util.Date;

public class LookupAuditTableModel extends AbstractTableModel {
    private final String[] columnNames = {"Key", "Action", "Old Value", "New Value", "User", "Timestamp"};
    private final List<GroupAuditEntriesResponse.AuditEntryResponse> values = new ArrayList<>();

    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public int getRowCount() {
        return values.size();
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
    public Object getValueAt(int rowIndex, int columnIndex) {
        GroupAuditEntriesResponse.AuditEntryResponse entry = values.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return entry.getKeyValue();
            case 1:
                return entry.getAction();
            case 2:
                return entry.getOldValue();
            case 3:
                return entry.getNewValue();
            case 4:
                return entry.getUserName();
            case 5:
                Date timestamp = entry.getTimestamp();
                return timestamp != null ? formatter.format(timestamp) : "";
            default:
                return null;
        }
    }

    public GroupAuditEntriesResponse.AuditEntryResponse getValue(int rowIndex) {
        return values.get(rowIndex);
    }

    public void setValues(List<GroupAuditEntriesResponse.AuditEntryResponse> newValues) {
        values.clear();
        values.addAll(newValues);
        fireTableDataChanged();
    }

    public void clear() {
        values.clear();
        fireTableDataChanged();
    }
}


