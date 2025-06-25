package com.mirth.connect.plugins.dynamiclookup.client.panel;

import javax.swing.table.TableCellRenderer;
import javax.swing.JTable;
import java.awt.Component;

public class ButtonRenderer extends ButtonPanel implements TableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (isSelected) {
            setBackground(table.getSelectionBackground());
            setForeground(table.getSelectionForeground());
        } else {
            setBackground(table.getBackground());
            setForeground(table.getForeground());
        }
        return this;
    }
}
