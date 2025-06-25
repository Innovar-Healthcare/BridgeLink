package com.mirth.connect.plugins.dynamiclookup.client.panel;

import com.mirth.connect.client.ui.UIConstants;

import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.SwingConstants;

import java.awt.BorderLayout;
import java.awt.Font;

public class NoGroupSelectedPanel extends JPanel {
    public NoGroupSelectedPanel() {
        super(new BorderLayout());
        setBackground(UIConstants.BACKGROUND_COLOR);

        JLabel messageLabel = new JLabel(
                "No group selected. Please choose one to proceed.",
                UIManager.getIcon("OptionPane.warningIcon"),
                SwingConstants.CENTER
        );
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.BOLD, 16f));
        messageLabel.setHorizontalTextPosition(SwingConstants.RIGHT);
        messageLabel.setIconTextGap(10);

        add(messageLabel, BorderLayout.CENTER);
    }
}
