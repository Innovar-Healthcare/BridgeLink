/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.panel;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionListener;

import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthRadioButton;
import net.miginfocom.swing.MigLayout;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class GeneralTabPanel extends JPanel {

    private JPanel enabledPanel;
    private JLabel enabledLabel;
    private MirthRadioButton yesEnabledRadio;
    private MirthRadioButton noEnabledRadio;
    private ButtonGroup enabledButtonGroup;
    private JLabel enabledDescLabel;

    private final VersionHistoryProperties versionHistoryProperties;

    public GeneralTabPanel(VersionHistoryProperties versionHistoryProperties) {
        this.versionHistoryProperties = versionHistoryProperties;
        initComponents();
        initLayout();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        enabledPanel = new JPanel();
        enabledPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        enabledPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)),
                "Enable",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Tahoma", 1, 11)
        ));

        enabledLabel = new JLabel("Enable:");

        yesEnabledRadio = new MirthRadioButton("Yes");
        yesEnabledRadio.setFocusable(false);
        yesEnabledRadio.setBackground(Color.white);

        noEnabledRadio = new MirthRadioButton("No");
        noEnabledRadio.setFocusable(false);
        noEnabledRadio.setBackground(Color.white);
        noEnabledRadio.setSelected(true);

        enabledButtonGroup = new ButtonGroup();
        enabledButtonGroup.add(yesEnabledRadio);
        enabledButtonGroup.add(noEnabledRadio);

        enabledDescLabel = new JLabel("Master switch — enables or disables the entire Channel History feature.");
        enabledDescLabel.setForeground(Color.GRAY);
        enabledDescLabel.setFont(new Font("Tahoma", Font.PLAIN, 10));
    }

    private void initLayout() {
        setLayout(new MigLayout("hidemode 3, novisualpadding, insets 8", "[grow]"));

        enabledPanel.setLayout(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
        enabledPanel.add(enabledLabel);
        enabledPanel.add(yesEnabledRadio, "split, gapleft 12");
        enabledPanel.add(noEnabledRadio, "wrap");
        enabledPanel.add(new JLabel()); // spacer for alignment
        enabledPanel.add(enabledDescLabel, "gapleft 12, wrap");

        add(enabledPanel, "grow, sx, wrap");
    }

    public void addEnabledActionListener(ActionListener listener) {
        yesEnabledRadio.addActionListener(listener);
        noEnabledRadio.addActionListener(listener);
    }

    public boolean isPluginEnabled() {
        return yesEnabledRadio.isSelected();
    }

    public void setProperties() {
        yesEnabledRadio.setSelected(versionHistoryProperties.isEnableVersionHistory());
        noEnabledRadio.setSelected(!versionHistoryProperties.isEnableVersionHistory());
    }

    public void getProperties() {
        versionHistoryProperties.setEnableVersionHistory(yesEnabledRadio.isSelected());
    }
}
