/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.diff;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;
import java.awt.Font;

import com.innovarhealthcare.channelHistory.client.diff.model.ChangeType;
import com.innovarhealthcare.channelHistory.client.diff.model.ScriptEntry;

/**
 * Cell renderer for the function list in the Code Template diff view.
 * Shows [M] / [A] / [D] prefix with colour-coded text.
 *
 * Uses HTML coloring to bypass Substance LAF color-scheme overrides.
 * Colors match GitStatusTabPanel.TreeCellRenderer:
 *   [M] Modified   → #CC6600  (204, 102,  0)
 *   [A] Added      → #009900  (  0, 153,  0)
 *   [D] Deleted    → #CC0000  (204,   0,  0)
 */
class FunctionListCellRenderer extends DefaultListCellRenderer {

    private static final String HEX_MODIFIED = "#CC6600";
    private static final String HEX_ADDED    = "#009900";
    private static final String HEX_DELETED  = "#CC0000";

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value instanceof ScriptEntry) {
            ScriptEntry entry = (ScriptEntry) value;
            String prefix = prefixFor(entry.getChangeType());
            String name   = entry.getName();

            if (isSelected) {
                setText(prefix + " " + name);
            } else {
                String hex = hexFor(entry.getChangeType());
                setText("<html><font color='" + hex + "'>" + prefix + "</font> " + name + "</html>");
            }

            setFont(getFont().deriveFont(Font.PLAIN, 13f));
        }

        return this;
    }

    private static String prefixFor(ChangeType type) {
        switch (type) {
            case MODIFIED: return "[M]";
            case ADDED:    return "[A]";
            case DELETED:  return "[D]";
            default:       return "   ";
        }
    }

    private static String hexFor(ChangeType type) {
        switch (type) {
            case MODIFIED: return HEX_MODIFIED;
            case ADDED:    return HEX_ADDED;
            case DELETED:  return HEX_DELETED;
            default:       return "#757575";
        }
    }
}
