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

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;
import java.util.List;

import com.innovarhealthcare.channelHistory.client.diff.model.ChangeType;
import com.innovarhealthcare.channelHistory.client.diff.model.DiffLine;

class DiffTextPane extends JTextPane {

    private static final Color ADDED_BG = new Color(230, 255, 237);
    private static final Color DELETED_BG = new Color(255, 238, 240);
    private static final Color UNCHANGED_BG = Color.WHITE;

    private static final Font CODE_FONT = new Font("Monospaced", Font.PLAIN, 12);

    public DiffTextPane() {
        setFont(CODE_FONT);
        setEditable(false);
    }

    public void setDiffLines(List<DiffLine> lines) {
        StyledDocument doc = getStyledDocument();

        try {
            doc.remove(0, doc.getLength());

            for (DiffLine line : lines) {
                Style style = createStyleForLine(line);
                String prefix = getPrefixForLineType(line.getType());  // ADD
                doc.insertString(doc.getLength(), String.format("%4d %s %s\n", line.getLineNumber(), prefix, line.getContent()), style);
            }

            setCaretPosition(0);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private Style createStyleForLine(DiffLine line) {
        Style style = addStyle(null, null);

        switch (line.getType()) {
            case ADDED:
                StyleConstants.setBackground(style, ADDED_BG);
                break;
            case DELETED:
                StyleConstants.setBackground(style, DELETED_BG);
                break;
            default:
                StyleConstants.setBackground(style, UNCHANGED_BG);
                break;
        }

        StyleConstants.setFontFamily(style, "Monospaced");
        StyleConstants.setFontSize(style, 12);

        return style;
    }

    private String getPrefixForLineType(ChangeType type) {
        switch (type) {
            case ADDED:
                return "+";
            case DELETED:
                return "-";
            case MODIFIED:
                return "~";  // Or use "±" or "*"
            default:
                return " ";  // Space for alignment
        }
    }
}
