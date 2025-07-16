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

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

import javax.swing.table.AbstractTableModel;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LookupGroupTableModel extends AbstractTableModel {
    private final String[] columnNames = {"Group Name", "Updated Date"};
    private final List<LookupGroup> allGroups = new ArrayList<>();
    private List<LookupGroup> filteredGroups = new ArrayList<>();
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm a");
    private String lastFilterText = "";

    @Override
    public int getRowCount() {
        return filteredGroups.size();
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
        LookupGroup group = filteredGroups.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return group.getName();
            case 1:
                return formatter.format(group.getUpdatedDate());
            default:
                return null;
        }
    }

    public void addGroup(LookupGroup group) {
        allGroups.add(group);
        reapplyFilter();
        fireTableDataChanged();
    }

    public void removeGroup(int rowIndex) {
        LookupGroup group = filteredGroups.get(rowIndex);
        allGroups.remove(group);
        reapplyFilter();
        fireTableDataChanged();
    }

    public void updateGroupById(LookupGroup group) {
        int index = getIndexByGroupId(group.getId());
        if (index >= 0) {
            allGroups.set(index, group);
            reapplyFilter();
            fireTableDataChanged();
        }
    }

    public void addOrUpdateGroup(LookupGroup group) {
        int index = getIndexByGroupId(group.getId());
        if (index >= 0) {
            allGroups.set(index, group);
        } else {
            allGroups.add(group);
        }
        reapplyFilter();
        fireTableDataChanged();
    }

    public LookupGroup getGroup(int rowIndex) {
        return filteredGroups.get(rowIndex);
    }

    public void setGroups(List<LookupGroup> groups) {
        allGroups.clear();
        allGroups.addAll(groups);
        reapplyFilter();
        fireTableDataChanged();
    }

    public List<LookupGroup> getAllGroups() {
        return new ArrayList<>(allGroups);
    }

    public void clear() {
        allGroups.clear();
        filteredGroups.clear();
        fireTableDataChanged();
    }

    public void setFilter(String filterText) {
        lastFilterText = filterText != null ? filterText.trim().toLowerCase() : "";
        if (lastFilterText.isEmpty()) {
            filteredGroups = new ArrayList<>(allGroups);
        } else {
            filteredGroups = allGroups.stream()
                    .filter(group -> group.getName().toLowerCase().contains(lastFilterText))
                    .collect(Collectors.toList());
        }
        fireTableDataChanged();
    }

    public void clearFilter() {
        setFilter("");
    }

    public void reapplyFilter() {
        setFilter(lastFilterText);
    }

    public int getIndexByGroupId(int id) {
        for (int i = 0; i < allGroups.size(); i++) {
            if (allGroups.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }

    public int getFilteredIndexByGroupId(int id) {
        for (int i = 0; i < filteredGroups.size(); i++) {
            if (filteredGroups.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }

    public boolean containsGroupId(int id) {
        return getIndexByGroupId(id) >= 0;
    }
}

