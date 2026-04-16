/*
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved.
 */

package com.mirth.connect.client.ui;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import com.mirth.connect.client.ui.components.MirthButton;
import com.mirth.connect.client.ui.components.MirthCheckBox;
import com.mirth.connect.client.ui.util.DisplayUtil;

/**
 * Modal dialog shown on login when the keystore passwords are still the defaults.
 * The user can optionally request immediate password regeneration.
 */
public class KeystoreWarningDialog extends MirthDialog {

    private boolean okClicked = false;
    private MirthCheckBox regenerateCheckBox;

    public KeystoreWarningDialog(Window owner) {
        super(owner, "Security Warning", true);
        initComponents();
        setLocationRelativeTo(owner);
        setVisible(true);
    }

    private void initComponents() {
        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.warningIcon"));

        JLabel messageLabel = new JLabel(
                "<html><b>The keystore passwords for this BridgeLink instance are still set to "
                + "default values.</b><br><br>"
                + "The keystore stores the SSL/TLS certificate for the BridgeLink API and<br>"
                + "Administrator (port 8443).<br><br>"
                + "<b>Note:</b> If any channels have message-level encryption enabled<br>"
                + "(encryptData=true), those stored messages will become unreadable after<br>"
                + "regeneration. Channels will continue to process new messages normally.<br><br>"
                + "<b>Note:</b> If encrypt.properties=true in mirth.properties, the encrypted<br>"
                + "database.password will also become unreadable after regeneration.<br>"
                + "This setting defaults to false in most installations.<br><br>"
                + "<b>Note:</b> BridgeLink must be restarted after regenerating the keystore<br>"
                + "for the new SSL certificate to take effect.</html>");

        regenerateCheckBox = new MirthCheckBox(
                "Generate a new keystore with new passwords (recommended)");
        regenerateCheckBox.setSelected(true);

        MirthButton okButton = new MirthButton("OK");
        okButton.setPreferredSize(new java.awt.Dimension(80, 24));
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                okClicked = true;
                dispose();
            }
        });

        MirthButton remindLaterButton = new MirthButton("Remind me later");
        remindLaterButton.setPreferredSize(new java.awt.Dimension(120, 24));
        remindLaterButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                okClicked = false;
                dispose();
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(okButton);
        buttonPanel.add(remindLaterButton);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(iconLabel)
                    .addComponent(messageLabel))
                .addComponent(regenerateCheckBox)
                .addComponent(buttonPanel, javax.swing.GroupLayout.DEFAULT_SIZE,
                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(iconLabel)
                    .addComponent(messageLabel))
                .addComponent(regenerateCheckBox)
                .addComponent(buttonPanel, javax.swing.GroupLayout.PREFERRED_SIZE,
                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        pack();
        DisplayUtil.setResizable(this, false);
    }

    /** Returns {@code true} if the user clicked OK (not "Remind me later"). */
    public boolean wasOkClicked() {
        return okClicked;
    }

    /** Returns {@code true} if the regeneration checkbox was checked when OK was clicked. */
    public boolean isRegenerateRequested() {
        return regenerateCheckBox.isSelected();
    }
}
