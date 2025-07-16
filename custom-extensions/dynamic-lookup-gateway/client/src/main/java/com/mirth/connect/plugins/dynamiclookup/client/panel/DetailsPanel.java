/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.client.panel;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

import net.miginfocom.swing.MigLayout;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;

import java.awt.Font;
import java.awt.CardLayout;
import java.awt.Dimension;

import java.text.SimpleDateFormat;

public class DetailsPanel extends JPanel {
    private final Logger logger = LogManager.getLogger(this.getClass());
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm a");

    private final Frame parent = PlatformUI.MIRTH_FRAME;

    private JPanel noGroupSelectedPanel;
    private JPanel contentPanel;
    private JTextArea detailsTextArea;

    private LookupGroup selectedGroup;

    public DetailsPanel() {
        initComponents();
        initLayout();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        noGroupSelectedPanel = new NoGroupSelectedPanel();

        detailsTextArea = new JTextArea();
        detailsTextArea.setEditable(false);
        detailsTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailsTextArea.setBackground(UIConstants.BACKGROUND_COLOR);
        detailsTextArea.setLineWrap(false);
        detailsTextArea.setWrapStyleWord(false);
        detailsTextArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    private void initLayout() {
        setLayout(new CardLayout());

        contentPanel = new JPanel(new MigLayout("insets 8, wrap 1, fillx", "[grow]", "[]"));
        contentPanel.setBackground(UIConstants.BACKGROUND_COLOR);

        JScrollPane scrollPane = new JScrollPane(detailsTextArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(500, 200));

        // Place at top, do not stretch vertically
        contentPanel.add(scrollPane, "aligny top, growx, wrap");

        add(contentPanel, "content");
        add(noGroupSelectedPanel, "noGroup");
    }


    public void updateDetails(LookupGroup selectedGroup) {
        boolean showContent = selectedGroup != null;

        contentPanel.setVisible(showContent);
        noGroupSelectedPanel.setVisible(!showContent);

        this.selectedGroup = selectedGroup;

        if (showContent) {
            refreshUI();
        }
    }

    private void refreshUI() {
        if (selectedGroup == null) {
            detailsTextArea.setText("");
            return;
        }

        String details = String.format(
                "%-15s: %d%n" +
                        "%-15s: %s%n" +
                        "%-15s: %s%n" +
                        "%-15s: %s%n" +
                        "%-15s: %d%n" +
                        "%-15s: %s%n" +
                        "%-15s: %s%n" +
                        "%-15s: %s%n",
                "ID", selectedGroup.getId(),
                "Name", selectedGroup.getName(),
                "Description", selectedGroup.getDescription() != null ? selectedGroup.getDescription() : "",
                "Version", selectedGroup.getVersion() != null ? selectedGroup.getVersion() : "",
                "Cache Size", selectedGroup.getCacheSize(),
                "Cache Policy", selectedGroup.getCachePolicy() != null ? selectedGroup.getCachePolicy() : "",
                "Created Date", formatter.format(selectedGroup.getCreatedDate()),
                "Updated Date", formatter.format(selectedGroup.getUpdatedDate())
        );

        detailsTextArea.setText(details);
    }
}
