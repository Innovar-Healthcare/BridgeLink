package com.mirth.connect.plugins.dynamiclookup.client.panel;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import org.jdesktop.swingx.JXTaskPane;

import javax.swing.*;

public class DataStorePanel extends JPanel {
    private Frame parent;

    private JXTaskPane dataStoreTasks;
    private LookupTablePanel lookupTablePanel;

    public DataStorePanel() {
        this.parent = PlatformUI.MIRTH_FRAME;

        dataStoreTasks = new CustomTaskPane();
        dataStoreTasks.setTitle("Data Store");
        dataStoreTasks.setName("Data Store");
        dataStoreTasks.setFocusable(false);

        parent.addTask("doShowLookup", "Lookup Manager", "Contains information about Lookup Table.", "", new ImageIcon(com.mirth.connect.client.ui.Frame.class.getResource("images/table.png")), dataStoreTasks, null, this);
        parent.setNonFocusable(dataStoreTasks);

        parent.taskPaneContainer.add(dataStoreTasks, parent.taskPaneContainer.getComponentCount() - 1);
        dataStoreTasks.setVisible(true);
    }

    public void unBold() {
        parent.setBold(dataStoreTasks, -1);
    }

    public void doShowLookup() {
        if (lookupTablePanel == null) {
            lookupTablePanel = new LookupTablePanel(this);
        }

        if (!parent.confirmLeave()) {
            return;
        }

        parent.setBold(parent.viewPane, -1);
        parent.setBold(dataStoreTasks, 0);
        parent.setPanelName("Lookup Manager");
        parent.setCurrentContentPage(lookupTablePanel);
        parent.setFocus(dataStoreTasks);

        lookupTablePanel.doRefresh();
    }

    private class CustomTaskPane extends JXTaskPane {
        @Override
        public void setVisible(boolean aFlag) {
            if (!aFlag) {
                return; // Ignore hide attempts
            }

            super.setVisible(true);
        }
    }
}
