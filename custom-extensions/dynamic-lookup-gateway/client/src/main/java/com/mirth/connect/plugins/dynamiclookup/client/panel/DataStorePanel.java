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
import org.jdesktop.swingx.JXTaskPane;
import org.jdesktop.swingx.JXTaskPaneContainer;

import javax.swing.JPanel;
import javax.swing.ImageIcon;

import java.awt.Component;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;

public class DataStorePanel extends JPanel {
    private Frame parent;

    private JXTaskPane dataStoreTasks;
    private LookupTablePanel lookupTablePanel;
    private boolean isReordering = false;

    public DataStorePanel() {
        this.parent = PlatformUI.MIRTH_FRAME;

        dataStoreTasks = new CustomTaskPane(parent);
        dataStoreTasks.setTitle("Data Store");
        dataStoreTasks.setName("Data Store");
        dataStoreTasks.setFocusable(false);

        parent.addTask("doShowLookup", "Lookup Manager", "Contains information about Lookup Table.", "", new ImageIcon(com.mirth.connect.client.ui.Frame.class.getResource("images/table.png")), dataStoreTasks, null, this);
        parent.setNonFocusable(dataStoreTasks);

        dataStoreTasks.setVisible(true);
        keepComponentBefore(parent.taskPaneContainer, dataStoreTasks, parent.otherPane);
    }

    private void keepComponentBefore(JXTaskPaneContainer container, Component toMove, Component target) {
        container.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent e) {
                ensureOrder(container, toMove, target);
            }

            @Override
            public void componentRemoved(ContainerEvent e) {
                ensureOrder(container, toMove, target);
            }
        });

        // Initial call
        ensureOrder(container, toMove, target);
    }

    private void ensureOrder(JXTaskPaneContainer container, Component toMove, Component target) {
        if (isReordering) {
            return;
        }

        isReordering = true;
        try {
            Component[] components = container.getComponents();
            int moveIndex = -1;
            int targetIndex = -1;

            for (int i = 0; i < components.length; i++) {
                if (components[i] == toMove) {
                    moveIndex = i;
                } else if (components[i] == target) {
                    targetIndex = i;
                }
            }

            if (targetIndex != -1 && (moveIndex == -1 || moveIndex != targetIndex - 1)) {
                container.remove(toMove);

                if (moveIndex != -1 && moveIndex < targetIndex) {
                    targetIndex--;
                }

                container.add(toMove, targetIndex);
                container.revalidate();
                container.repaint();
            }
        } finally {
            isReordering = false;
        }
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
        private Frame parent;

        public CustomTaskPane(Frame parent) {
            this.parent = parent;
        }

        @Override
        public void setVisible(boolean aFlag) {
            if (!aFlag) {
                // set to invisible during transformerPane and filterPane
                if (parent.currentContentPage != null && parent.channelEditPanel != null && parent.channelEditPanel.transformerPane != null && parent.channelEditPanel.filterPane != null) {
                    if (parent.currentContentPage == parent.channelEditPanel.transformerPane || parent.currentContentPage == parent.channelEditPanel.filterPane) {
                        super.setVisible(false);
                        return;
                    }
                }
            }

            super.setVisible(true);
        }
    }
}
