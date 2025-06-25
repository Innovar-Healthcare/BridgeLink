package com.mirth.connect.plugins.dynamiclookup.client.panel;

import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.plugins.dynamiclookup.client.model.LookupValueTableModel;

import javax.swing.JTable;

import javax.swing.event.CellEditorListener;
import javax.swing.table.TableCellEditor;

import java.awt.Frame;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.EventObject;

public class ButtonEditor extends ButtonPanel implements TableCellEditor {
    private final Frame parent = PlatformUI.MIRTH_FRAME;
    private final LookupValueTableModel model;
    private int currentRow;
    private transient ActionListener editListener;
    private transient ActionListener removeListener;

    public ButtonEditor(JTable table, LookupValueTableModel model, ActionListener editListener, ActionListener removeListener) {
        this.model = model;
        this.editListener = e -> editListener.actionPerformed(new ActionEvent(currentRow, e.getID(), e.getActionCommand()));
        this.removeListener = e -> removeListener.actionPerformed(new ActionEvent(currentRow, e.getID(), e.getActionCommand()));
        this.currentRow = -1;

        getEditButton().addActionListener(this.editListener);
        getRemoveButton().addActionListener(this.removeListener);
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        currentRow = row;
        if (isSelected) {
            setBackground(table.getSelectionBackground());
            setForeground(table.getSelectionForeground());
        } else {
            setBackground(table.getBackground());
            setForeground(table.getForeground());
        }
        return this;
    }

    @Override
    public Object getCellEditorValue() {
        return null;
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
        return true;
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent) {
        return true;
    }

    @Override
    public boolean stopCellEditing() {
        return true;
    }

    @Override
    public void cancelCellEditing() {
    }

    @Override
    public void addCellEditorListener(CellEditorListener l) {
    }

    @Override
    public void removeCellEditorListener(CellEditorListener l) {
    }
}
