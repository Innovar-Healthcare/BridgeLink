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

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;

import javax.swing.table.AbstractTableModel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class LookupValueTableModel extends AbstractTableModel {
    public static final int KEY_COLUMN = 0;
    public static final int VALUE_COLUMN = 1;
    public static final int UPDATED_DATE_COLUMN = 2;
    public static final int ACTION_COLUMN = 3;

    private final String[] columnNames = {"Key", "Value", "Updated Date", "Action"};
    private final List<LookupValue> values = new ArrayList<>();
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm a");

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
        LookupValue value = values.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return value.getKeyValue();
            case 1:
                return value.getValueData();
            case 2:
                return formatter.format(value.getUpdatedDate());
            case 3:
                return null;
            default:
                return null;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == ACTION_COLUMN;
    }

    public void addValue(LookupValue value) {
        values.add(value);
        fireTableDataChanged();
    }

    public void removeValue(int rowIndex) {
        values.remove(rowIndex);
        fireTableDataChanged();
    }

    public void updateValue(int rowIndex, LookupValue newValue) {
        values.set(rowIndex, newValue);
        fireTableDataChanged();
    }

    public LookupValue getValue(int rowIndex) {
        return values.get(rowIndex);
    }

    public void setValues(List<LookupValue> newValues) {
        values.clear();
        values.addAll(newValues);
        fireTableDataChanged();
    }

    public void clear() {
        values.clear();
        fireTableDataChanged();
    }
}

