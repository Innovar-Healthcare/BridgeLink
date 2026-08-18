/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) NextGen Healthcare. All rights reserved.
 * https://www.nextgen.com/products-and-services/integration-engine
 *
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.client.ui;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
import javax.swing.event.HyperlinkListener;

import com.mirth.connect.client.ui.components.MirthButton;
import com.mirth.connect.client.ui.components.MirthCheckBox;
import com.mirth.connect.client.ui.util.DisplayUtil;

/**
 * Modal dialog shown on login informing users that BridgeLink has transitioned to the new WebAdmin
 * administrator client, with a link to learn more and an option to suppress the warning on future
 * logins.
 */
public class WebAdminMigrationDialog extends MirthDialog {

    private static final String WEB_ADMIN_URL = "https://www.bridgelink.net/web-admin/";

    private MirthCheckBox doNotShowAgainCheckBox;

    public WebAdminMigrationDialog(Window owner) {
        super(owner, "BridgeLink Administrator", true);
        initComponents();
        setLocationRelativeTo(owner);
        setVisible(true);
    }

    private void initComponents() {
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        javax.swing.Icon infoIcon = UIManager.getIcon("OptionPane.informationIcon");
        JLabel iconLabel = new JLabel(infoIcon);
        int iconWidth = (infoIcon != null) ? infoIcon.getIconWidth() : 32;

        // Use a JEditorPane so the "here" hyperlink is clickable. Match the look of a JLabel by
        // stripping the editor background/border and applying the default label font.
        Font labelFont = UIManager.getFont("Label.font");
        JEditorPane messagePane = new JEditorPane();
        messagePane.setContentType("text/html");
        messagePane.setEditable(false);
        messagePane.setOpaque(false);
        messagePane.setBackground(new Color(0, 0, 0, 0));
        messagePane.setBorder(null);
        if (labelFont != null) {
            String rule = "body { font-family: " + labelFont.getFamily() + "; font-size: "
                    + labelFont.getSize() + "pt; }";
            ((javax.swing.text.html.HTMLDocument) messagePane.getDocument()).getStyleSheet().addRule(rule);
        }
        messagePane.setText("<html><body>BridgeLink has transitioned to a new administrator client, "
                + "WebAdmin. Click <a href=\"" + WEB_ADMIN_URL + "\">here</a> to learn more."
                + "</body></html>");
        messagePane.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent evt) {
                if (evt.getEventType() == EventType.ACTIVATED) {
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(evt.getURL().toURI());
                        } else {
                            BareBonesBrowserLaunch.openURL(evt.getURL().toString());
                        }
                    } catch (Exception e) {
                        BareBonesBrowserLaunch.openURL(WEB_ADMIN_URL);
                    }
                }
            }
        });

        doNotShowAgainCheckBox = new MirthCheckBox("Don't show this warning again");

        MirthButton okButton = new MirthButton("OK");
        okButton.setPreferredSize(new java.awt.Dimension(80, 24));
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                dispose();
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(okButton);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(iconLabel)
                    .addComponent(messagePane))
                .addGroup(layout.createSequentialGroup()
                    .addGap(iconWidth + 6)
                    .addComponent(doNotShowAgainCheckBox))
                .addComponent(buttonPanel, javax.swing.GroupLayout.DEFAULT_SIZE,
                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(iconLabel)
                    .addComponent(messagePane))
                .addComponent(doNotShowAgainCheckBox)
                .addComponent(buttonPanel, javax.swing.GroupLayout.PREFERRED_SIZE,
                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        pack();
        DisplayUtil.setResizable(this, false);
    }

    /** Returns {@code true} if the "Don't show this warning again" checkbox was checked. */
    public boolean isDoNotShowAgainChecked() {
        return doNotShowAgainCheckBox.isSelected();
    }
}
