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

/**
 * Cell renderer for the component list in {@link ChannelDiffPanel}.
 *
 * Uses HTML colour tags to bypass Mirth Connect's Substance LAF, which overrides
 * {@code setForeground()} on {@code DefaultListCellRenderer} cells during paint.
 *
 *   [M] Modified  → #CC6600  (204, 102,  0) — orange
 *   [A] Added     → #009900  (  0, 153,  0) — green
 *   [D] Deleted   → #CC0000  (204,   0,  0) — red
 *   Unchanged     → #777777  — muted gray
 *   Selected      → plain text (LAF selection foreground)
 */
class ChannelEntryListCellRenderer extends DefaultListCellRenderer {

    private static final String HEX_MODIFIED  = "#CC6600";
    private static final String HEX_ADDED     = "#009900";
    private static final String HEX_DELETED   = "#CC0000";
    private static final String HEX_UNCHANGED = "#777777";

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value instanceof ChannelEntry) {
            ChannelEntry entry = (ChannelEntry) value;
            String    label = entry.getLabel();
            ChangeType type = entry.getChangeType();

            if (isSelected) {
                setText(prefixFor(type) + " " + label);
            } else if (type == ChangeType.UNCHANGED) {
                setText("<html><font color='" + HEX_UNCHANGED + "'>" + label + "</font></html>");
            } else {
                String hex = hexFor(type);
                setText("<html><font color='" + hex + "'>" + prefixFor(type) + "</font> "
                        + label + "</html>");
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
            default:       return "";
        }
    }

    private static String hexFor(ChangeType type) {
        switch (type) {
            case MODIFIED: return HEX_MODIFIED;
            case ADDED:    return HEX_ADDED;
            case DELETED:  return HEX_DELETED;
            default:       return HEX_UNCHANGED;
        }
    }
}
