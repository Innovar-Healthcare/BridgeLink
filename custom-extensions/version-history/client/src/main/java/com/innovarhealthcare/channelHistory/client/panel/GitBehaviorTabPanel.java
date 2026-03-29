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
import com.mirth.connect.client.ui.components.MirthComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import com.innovarhealthcare.channelHistory.shared.VersionControlConstants;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthRadioButton;
import com.mirth.connect.client.ui.components.MirthTextPane;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class GitBehaviorTabPanel extends JPanel {

    private JPanel autoCommitPanel;
    private JLabel autoCommitLabel;
    private MirthRadioButton autoCommitYes;
    private MirthRadioButton autoCommitNo;
    private ButtonGroup autoCommitButtonGroup;
    private JLabel promptLabel;
    private MirthRadioButton promptYes;
    private MirthRadioButton promptNo;
    private ButtonGroup promptButtonGroup;
    private JLabel defaultMessageLabel;
    private JTextPane defaultMessageField;
    private JScrollPane defaultMessageScrollPane;

    private JLabel syncDeleteLabel;
    private MirthRadioButton syncDeleteYes;
    private MirthRadioButton syncDeleteNo;
    private ButtonGroup syncDeleteButtonGroup;
    private JLabel syncDeleteDescLabel;

    private JLabel historyLogMaxCountLabel;
    private MirthComboBox<Integer> historyLogMaxCountCombo;
    private JLabel historyLogMaxCountDescLabel;

    private static final Integer[] LOG_MAX_COUNT_OPTIONS = {100, 200, 500, 1000, 2000, 3000, 4000, 5000};

    private final VersionHistoryProperties versionHistoryProperties;

    public GitBehaviorTabPanel(VersionHistoryProperties versionHistoryProperties) {
        this.versionHistoryProperties = versionHistoryProperties;
        initComponents();
        initLayout();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        autoCommitPanel = new JPanel();
        autoCommitPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        autoCommitPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)),
                "Auto Commit",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Tahoma", 1, 11)
        ));

        autoCommitLabel = new JLabel("Enable:");

        autoCommitYes = new MirthRadioButton("Yes");
        autoCommitYes.setFocusable(false);
        autoCommitYes.setBackground(Color.white);
        autoCommitYes.addActionListener(e -> autoCommitActionPerformed());

        autoCommitNo = new MirthRadioButton("No");
        autoCommitNo.setFocusable(false);
        autoCommitNo.setBackground(Color.white);
        autoCommitNo.setSelected(true);
        autoCommitNo.addActionListener(e -> autoCommitActionPerformed());

        autoCommitButtonGroup = new ButtonGroup();
        autoCommitButtonGroup.add(autoCommitYes);
        autoCommitButtonGroup.add(autoCommitNo);

        promptLabel = new JLabel("Prompt:");

        promptYes = new MirthRadioButton("Yes");
        promptYes.setFocusable(false);
        promptYes.setBackground(Color.white);
        promptYes.addActionListener(e -> promptYesNoActionPerformed());

        promptNo = new MirthRadioButton("No");
        promptNo.setFocusable(false);
        promptNo.setBackground(Color.white);
        promptNo.setSelected(true);
        promptNo.addActionListener(e -> promptYesNoActionPerformed());

        promptButtonGroup = new ButtonGroup();
        promptButtonGroup.add(promptYes);
        promptButtonGroup.add(promptNo);

        defaultMessageLabel = new JLabel("Default Message:");
        defaultMessageField = new MirthTextPane();
        defaultMessageScrollPane = new JScrollPane(defaultMessageField, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        defaultMessageScrollPane.setPreferredSize(new Dimension(300, 100));

        syncDeleteLabel = new JLabel("Sync Delete:");

        syncDeleteYes = new MirthRadioButton("Yes");
        syncDeleteYes.setFocusable(false);
        syncDeleteYes.setBackground(Color.white);

        syncDeleteNo = new MirthRadioButton("No");
        syncDeleteNo.setFocusable(false);
        syncDeleteNo.setBackground(Color.white);
        syncDeleteNo.setSelected(true);

        syncDeleteButtonGroup = new ButtonGroup();
        syncDeleteButtonGroup.add(syncDeleteYes);
        syncDeleteButtonGroup.add(syncDeleteNo);

        syncDeleteDescLabel = new JLabel("When a channel or code template is deleted, automatically remove it from Git and push to remote.");
        syncDeleteDescLabel.setFont(new Font("Tahoma", Font.PLAIN, 10));
        syncDeleteDescLabel.setForeground(Color.GRAY);

        historyLogMaxCountLabel = new JLabel("Max Commits:");
        historyLogMaxCountCombo = new MirthComboBox<>();
        for (Integer option : LOG_MAX_COUNT_OPTIONS) {
            historyLogMaxCountCombo.addItem(option);
        }
        historyLogMaxCountDescLabel = new JLabel("Maximum number of commits loaded in the History tab.");
        historyLogMaxCountDescLabel.setFont(new Font("Tahoma", Font.PLAIN, 10));
        historyLogMaxCountDescLabel.setForeground(Color.GRAY);
    }

    private void initLayout() {
        setLayout(new MigLayout("hidemode 3, novisualpadding, insets 8", "[grow]"));

        // Auto Commit section
        autoCommitPanel.setLayout(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
        autoCommitPanel.add(autoCommitLabel);
        autoCommitPanel.add(autoCommitYes, "split, gapleft 12");
        autoCommitPanel.add(autoCommitNo, "wrap");
        autoCommitPanel.add(promptLabel);
        autoCommitPanel.add(promptYes, "split, gapleft 12");
        autoCommitPanel.add(promptNo, "wrap");
        autoCommitPanel.add(defaultMessageLabel);
        autoCommitPanel.add(defaultMessageScrollPane, "gapleft 12, wrap");
        add(autoCommitPanel, "grow, sx, wrap");

        // Sync Delete section
        JPanel syncDeletePanel = new JPanel();
        syncDeletePanel.setBackground(UIConstants.BACKGROUND_COLOR);
        syncDeletePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)),
                "Sync Delete",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Tahoma", 1, 11)
        ));
        syncDeletePanel.setLayout(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
        syncDeletePanel.add(syncDeleteLabel);
        syncDeletePanel.add(syncDeleteYes, "split, gapleft 12");
        syncDeletePanel.add(syncDeleteNo, "wrap");
        syncDeletePanel.add(new JLabel()); // spacer
        syncDeletePanel.add(syncDeleteDescLabel, "gapleft 12, wrap");
        add(syncDeletePanel, "grow, sx, wrap");

        // History section
        JPanel historyPanel = new JPanel();
        historyPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        historyPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)),
                "History",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Tahoma", 1, 11)
        ));
        historyPanel.setLayout(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
        historyPanel.add(historyLogMaxCountLabel);
        historyPanel.add(historyLogMaxCountCombo, "gapleft 12, wrap");
        historyPanel.add(new JLabel()); // spacer
        historyPanel.add(historyLogMaxCountDescLabel, "gapleft 12, wrap");
        add(historyPanel, "grow, sx, wrap");
    }

    private void autoCommitActionPerformed() {
        boolean selected = autoCommitYes.isSelected();
        promptYes.setEnabled(selected);
        promptNo.setEnabled(selected);
        defaultMessageField.setEnabled(selected);
    }

    private void promptYesNoActionPerformed() {
//        defaultMessageField.setVisible(promptNo.isSelected());
    }

    public void setProperties() {
        autoCommitYes.setSelected(versionHistoryProperties.isEnableAutoCommit());
        autoCommitNo.setSelected(!versionHistoryProperties.isEnableAutoCommit());

        promptYes.setSelected(versionHistoryProperties.isEnableAutoCommitPrompt());
        promptNo.setSelected(!versionHistoryProperties.isEnableAutoCommitPrompt());
        defaultMessageField.setText(versionHistoryProperties.getAutoCommitMsg());

        syncDeleteYes.setSelected(versionHistoryProperties.isEnableSyncDelete());
        syncDeleteNo.setSelected(!versionHistoryProperties.isEnableSyncDelete());

        // Select the matching item; fall back to default if stored value not in the list
        int storedMax = versionHistoryProperties.getHistoryLogMaxCount();
        historyLogMaxCountCombo.setSelectedItem(storedMax);
        if (historyLogMaxCountCombo.getSelectedItem() == null
                || !historyLogMaxCountCombo.getSelectedItem().equals(storedMax)) {
            historyLogMaxCountCombo.setSelectedItem(VersionControlConstants.REPO_LOG_MAX_COUNT);
        }

        autoCommitActionPerformed();
    }

    public void getProperties() {
        versionHistoryProperties.setEnableAutoCommit(autoCommitYes.isSelected());
        versionHistoryProperties.setEnableAutoCommitPrompt(promptYes.isSelected());
        versionHistoryProperties.setAutoCommitMsg(defaultMessageField.getText().trim());
        versionHistoryProperties.setEnableSyncDelete(syncDeleteYes.isSelected());
        Integer selectedMax = (Integer) historyLogMaxCountCombo.getSelectedItem();
        if (selectedMax != null) {
            versionHistoryProperties.setHistoryLogMaxCount(selectedMax);
        }
    }

    public boolean validateFields() {
        if (autoCommitYes.isSelected() && StringUtils.isEmpty(defaultMessageField.getText().trim())) {
            defaultMessageField.setBackground(UIConstants.INVALID_COLOR);
            return false;
        }
        return true;
    }

    public void resetInvalidState() {
        defaultMessageField.setBackground(null);
    }
}
