package com.mirth.connect.plugins.dynamiclookup.client.panel;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.Insets;

public class ButtonPanel extends JPanel{
    private final JButton editButton;
    private final JButton removeButton;

    public ButtonPanel() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
        setOpaque(false);

        editButton = new JButton("Edit");
        editButton.setMargin(new Insets(2, 5, 2, 5));
        removeButton = new JButton("Remove");
        removeButton.setMargin(new Insets(2, 5, 2, 5));

        add(editButton);
        add(removeButton);
    }

    public JButton getEditButton() {
        return editButton;
    }

    public JButton getRemoveButton() {
        return removeButton;
    }
}
