package com.mirth.connect.plugins.dynamiclookup.client.model;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupValue;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class LookupValueTableModel extends AbstractTableModel {
    private final String[] columnNames = {"Key", "Value", "Action"};
    private final List<LookupValue> values = new ArrayList<>();

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
                return null;
            default:
                return null;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 2;
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

