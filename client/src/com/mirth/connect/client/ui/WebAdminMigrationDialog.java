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

import javax.swing.GroupLayout;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.LayoutStyle.ComponentPlacement;
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

        // Same branded heading banner as the login/about screens (MirthHeadingPanel paints
        // PlatformUI.BACKGROUND_IMAGE), laid out identically to AboutMirth's header.
        MirthHeadingPanel headingPanel = new MirthHeadingPanel();
        JLabel titleLabel = new JLabel("WebAdmin");
        titleLabel.setFont(new Font("Tahoma", Font.BOLD, 18));
        titleLabel.setForeground(UIConstants.HEADER_TITLE_TEXT_COLOR);

        GroupLayout headingLayout = new GroupLayout(headingPanel);
        headingPanel.setLayout(headingLayout);
        headingLayout.setHorizontalGroup(
            headingLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(headingLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(titleLabel, GroupLayout.DEFAULT_SIZE, 358, Short.MAX_VALUE)
                    .addContainerGap())
        );
        headingLayout.setVerticalGroup(
            headingLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(headingLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(titleLabel, GroupLayout.DEFAULT_SIZE, 27, Short.MAX_VALUE)
                    .addContainerGap())
        );

        // Use a JEditorPane so the "here" hyperlink is clickable. Match the look of a JLabel by
        // stripping the editor background/border and applying the default label font.
        Font labelFont = UIConstants.DIALOG_FONT;
        JEditorPane messagePane = new JEditorPane();
        messagePane.setContentType("text/html");
        messagePane.setEditable(false);
        messagePane.setOpaque(false);
        messagePane.setBackground(new Color(0, 0, 0, 0));
        messagePane.setBorder(null);
        String rule = "body { font-family: " + labelFont.getFamily() + "; font-size: "
                + labelFont.getSize() + "pt; }";
        ((javax.swing.text.html.HTMLDocument) messagePane.getDocument()).getStyleSheet().addRule(rule);
        messagePane.setText("<html><body>BridgeLink has transitioned to a new administrator client, WebAdmin."
                + "<br>Click <a href=\"" + WEB_ADMIN_URL + "\">here</a> to learn more."
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
        doNotShowAgainCheckBox.setOpaque(false);

        MirthButton okButton = new MirthButton("OK");
        okButton.setPreferredSize(new java.awt.Dimension(80, 24));
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                dispose();
            }
        });

        JSeparator separator = new JSeparator();

        // White content area with the heading spanning edge-to-edge and the OK button pushed to the
        // bottom-right corner -- the same GroupLayout structure AboutMirth uses so the dialog packs
        // tight instead of leaving trailing whitespace.
        getContentPane().setBackground(UIConstants.BACKGROUND_COLOR);
        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);

        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(headingPanel, GroupLayout.DEFAULT_SIZE, 378, Short.MAX_VALUE)
                .addGroup(layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(messagePane, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addContainerGap())
                .addGroup(layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(separator, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addContainerGap())
                .addGroup(layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(doNotShowAgainCheckBox)
                    .addPreferredGap(ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(okButton)
                    .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addComponent(headingPanel, GroupLayout.PREFERRED_SIZE, 49, GroupLayout.PREFERRED_SIZE)
                .addGap(14, 14, 14)
                .addComponent(messagePane, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(ComponentPlacement.UNRELATED)
                .addComponent(separator, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                    .addComponent(doNotShowAgainCheckBox)
                    .addComponent(okButton))
                .addContainerGap()
        );

        pack();
        DisplayUtil.setResizable(this, false);
    }

    /** Returns {@code true} if the "Don't show this warning again" checkbox was checked. */
    public boolean isDoNotShowAgainChecked() {
        return doNotShowAgainCheckBox.isSelected();
    }
}
