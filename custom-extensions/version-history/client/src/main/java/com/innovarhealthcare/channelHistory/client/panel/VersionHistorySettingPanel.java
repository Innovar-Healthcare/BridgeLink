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

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.event.KeyEvent;
import java.util.Properties;

import com.innovarhealthcare.channelHistory.client.plugin.VersionHistorySettingPlugin;
import com.innovarhealthcare.channelHistory.shared.model.VersionHistoryProperties;
import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import net.miginfocom.swing.MigLayout;

/**
 * @author Thai Tran
 * @create 2025-04-30 10:00 AM
 */
public class VersionHistorySettingPanel extends AbstractSettingsPanel {

    private VersionHistorySettingPlugin plugin;
    private Frame parent;

    private int previousTabIndex = 0;
    private VersionHistoryProperties versionHistoryProperties;

    private JTabbedPane tabbedPane;
    private GeneralTabPanel generalTabPanel;
    private GitSettingsTabPanel gitSettingsTabPanel;
    private GitBehaviorTabPanel gitBehaviorTabPanel;
    private GitStatusTabPanel gitStatusTabPanel;

    public VersionHistorySettingPanel(String tabName, VersionHistorySettingPlugin plugin) {
        super(tabName);
        this.plugin = plugin;
        this.parent = PlatformUI.MIRTH_FRAME;
        versionHistoryProperties = new VersionHistoryProperties();
        initComponents();
        initLayout();
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        tabbedPane = new JTabbedPane();
        tabbedPane.setFocusable(false);

        generalTabPanel = new GeneralTabPanel(versionHistoryProperties);
        gitSettingsTabPanel = new GitSettingsTabPanel(versionHistoryProperties);
        gitBehaviorTabPanel = new GitBehaviorTabPanel(versionHistoryProperties);
        gitStatusTabPanel = new GitStatusTabPanel(versionHistoryProperties);

        generalTabPanel.addEnabledActionListener(e -> visibleFields(generalTabPanel.isPluginEnabled()));

        tabbedPane.addTab("General", generalTabPanel);
        tabbedPane.addTab("Git Settings", gitSettingsTabPanel);
        tabbedPane.addTab("Git Behavior", gitBehaviorTabPanel);
        tabbedPane.addTab("Git Status", gitStatusTabPanel);

        tabbedPane.addChangeListener(e -> {
            int selected = tabbedPane.getSelectedIndex();
            if (selected == 3 && PlatformUI.MIRTH_FRAME.isSaveEnabled()) {
                SwingUtilities.invokeLater(() -> {
                    tabbedPane.setSelectedIndex(previousTabIndex);
                    switch (new UnsavedChangesDialog().showDialog()) {
                        case SAVE:    saveAndGoToGitStatus();    break;
                        case DISCARD: discardAndGoToGitStatus(); break;
                        case CANCEL:  break;
                    }
                });
            } else if (selected != 3) {
                previousTabIndex = selected;
            }
        });
    }

    private void initLayout() {
        setLayout(new MigLayout("fill, hidemode 3, novisualpadding, insets 12", "[grow]", "[grow]"));
        add(tabbedPane, "grow, push, sx");
    }

    public void visibleFields(boolean isVisible) {
        tabbedPane.setEnabledAt(1, isVisible);
        tabbedPane.setEnabledAt(2, isVisible);
        tabbedPane.setEnabledAt(3, isVisible);
        if (!isVisible && tabbedPane.getSelectedIndex() != 0) {
            tabbedPane.setSelectedIndex(0);
        }
    }

    public void setProperties(Properties properties) {
        versionHistoryProperties.fromProperties(properties);

        generalTabPanel.setProperties();
        gitSettingsTabPanel.setProperties();
        gitBehaviorTabPanel.setProperties();

        visibleFields(generalTabPanel.isPluginEnabled());

        repaint();
        this.getFrame().setSaveEnabled(false);
    }

    public Properties getProperties() {
        generalTabPanel.getProperties();
        gitSettingsTabPanel.getProperties();
        gitBehaviorTabPanel.getProperties();

        return versionHistoryProperties.toProperties();
    }

    public boolean validateFields() {
        resetInvalidSettings();

        if (!generalTabPanel.isPluginEnabled()) {
            return true;
        }

        boolean valid = true;
        StringBuilder errorMessage = new StringBuilder();

        if (!gitSettingsTabPanel.validateFields()) {
            valid = false;
            errorMessage.append("Git Settings are invalid.").append(System.lineSeparator());
        }

        if (!gitBehaviorTabPanel.validateFields()) {
            valid = false;
            errorMessage.append("Please provide a default commit message.").append(System.lineSeparator());
        }

        if (!valid) {
            showError(errorMessage.toString());
        }

        return valid;
    }

    private void resetPanels() {
        tabbedPane.setSelectedIndex(0);
        gitStatusTabPanel.reset();
        resetInvalidSettings();
    }

    public void resetInvalidSettings() {
        gitSettingsTabPanel.resetInvalidState();
        gitBehaviorTabPanel.resetInvalidState();
    }

    @Override
    public void doRefresh() {
        if (PlatformUI.MIRTH_FRAME.alertRefresh()) {
            return;
        }

        resetPanels();

        final String workingId = getFrame().startWorking("Loading " + getTabName() + " properties...");

        final Properties serverProperties = new Properties();

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            public Void doInBackground() {
                try {
                    Properties propertiesFromServer = plugin.getPropertiesFromServer();
                    if (propertiesFromServer != null) {
                        serverProperties.putAll(propertiesFromServer);
                    }
                } catch (Exception e) {
                    getFrame().alertThrowable(getFrame(), e);
                }
                return null;
            }

            @Override
            public void done() {
                setProperties(serverProperties);
                tabbedPane.setSelectedIndex(0);
                gitStatusTabPanel.reset();
                getFrame().stopWorking(workingId);
            }
        };

        worker.execute();
    }

    @Override
    public boolean doSave() {
        if (!validateFields()) {
            return false;
        }

        final String workingId = getFrame().startWorking("Saving " + getTabName() + " properties...");

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

            public Void doInBackground() {
                try {
                    plugin.setPropertiesToServer(getProperties());
                } catch (Exception e) {
                    getFrame().alertThrowable(getFrame(), e);
                }
                return null;
            }

            @Override
            public void done() {
                setSaveEnabled(false);
                resetPanels();
                getFrame().stopWorking(workingId);
            }
        };

        worker.execute();

        return true;
    }

    private void saveAndGoToGitStatus() {
        if (!validateFields()) {
            return;
        }
        final String workingId = getFrame().startWorking("Saving " + getTabName() + " properties...");
        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            public Boolean doInBackground() {
                try {
                    plugin.setPropertiesToServer(getProperties());
                    return true;
                } catch (Exception e) {
                    getFrame().alertThrowable(getFrame(), e);
                    return false;
                }
            }

            @Override
            public void done() {
                try {
                    if (get()) {
                        setSaveEnabled(false);
                        resetInvalidSettings();
                        gitStatusTabPanel.reset();
                        tabbedPane.setSelectedIndex(3);
                    }
                } catch (Exception ignored) {
                }
                getFrame().stopWorking(workingId);
            }
        };
        worker.execute();
    }

    private void discardAndGoToGitStatus() {
        final String workingId = getFrame().startWorking("Loading " + getTabName() + " properties...");
        final Properties serverProperties = new Properties();
        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            public Boolean doInBackground() {
                try {
                    Properties propertiesFromServer = plugin.getPropertiesFromServer();
                    if (propertiesFromServer != null) {
                        serverProperties.putAll(propertiesFromServer);
                    }
                    return true;
                } catch (Exception e) {
                    getFrame().alertThrowable(getFrame(), e);
                    return false;
                }
            }

            @Override
            public void done() {
                try {
                    if (get()) {
                        setProperties(serverProperties);
                        gitStatusTabPanel.reset();
                        tabbedPane.setSelectedIndex(3);
                    }
                } catch (Exception ignored) {
                }
                getFrame().stopWorking(workingId);
            }
        };
        worker.execute();
    }

    protected void showError(String err) {
        PlatformUI.MIRTH_FRAME.alertError(this, err);
    }

    // ── Unsaved Changes Dialog ────────────────────────────────────────────────

    private enum DialogResult { SAVE, DISCARD, CANCEL }

    private class UnsavedChangesDialog extends MirthDialog {
        private DialogResult result = DialogResult.CANCEL;

        UnsavedChangesDialog() {
            super(parent, true);
            setTitle("Save Settings");
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            initComponents();
            setSize(420, 155);
            setLocationRelativeTo(parent);
            getRootPane().registerKeyboardAction(
                    e -> dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW
            );
        }

        private void initComponents() {
            setLayout(new MigLayout("insets 16, fill", "[grow]", "[][grow][]"));
            getContentPane().setBackground(UIConstants.BACKGROUND_COLOR);

            add(new JLabel("<html>You have unsaved changes.<br>Would you like to save or discard them before viewing Git Status?</html>"), "wrap");
            add(new JLabel(), "grow, push, wrap");

            JButton saveButton = new JButton("Save");
            saveButton.addActionListener(e -> { result = DialogResult.SAVE; dispose(); });

            JButton discardButton = new JButton("Discard");
            discardButton.addActionListener(e -> { result = DialogResult.DISCARD; dispose(); });

            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> { result = DialogResult.CANCEL; dispose(); });

            add(saveButton, "split 3, w 80!");
            add(discardButton, "w 80!, gapafter push");
            add(cancelButton, "w 80!");
        }

        DialogResult showDialog() {
            setVisible(true); // blocks (modal)
            return result;
        }
    }
}
