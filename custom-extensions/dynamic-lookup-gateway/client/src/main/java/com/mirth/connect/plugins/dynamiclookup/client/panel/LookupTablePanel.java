package com.mirth.connect.plugins.dynamiclookup.client.panel;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;
import net.miginfocom.swing.MigLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-05-13 10:25 AM
 */
public class LookupTablePanel extends JPanel {
    private final Logger logger = LogManager.getLogger(this.getClass());

    private final DataStorePanel taskPane;
    private final GroupPanel groupPanel;
    private final ValuePanel valuePanel;
    private final CacheStatusPanel cachePanel;
    private final HistoryPanel historyPanel;
    private final JTabbedPane tabbedPane;

    private final Frame parent = PlatformUI.MIRTH_FRAME;

    public LookupTablePanel(DataStorePanel taskPane) {
        this.taskPane = taskPane;

        this.groupPanel = new GroupPanel();
        this.valuePanel = new ValuePanel();
        this.cachePanel = new CacheStatusPanel();
        this.historyPanel = new HistoryPanel();
        this.tabbedPane = new JTabbedPane();

        initComponents();
        initLayout();
    }

    private void initComponents() {
        // Connect group selection to panel update based on selected tab
        groupPanel.addGroupSelectionListener(e -> {
            LookupGroup selectedGroup = groupPanel.getSelectedGroup();
            int selectedTab = tabbedPane.getSelectedIndex();
            switch (selectedTab) {
                case 0: // Values tab
                    valuePanel.updateValues(selectedGroup);
                    break;
                case 1: // Cache tab
                    cachePanel.updateCaches(selectedGroup);
                    break;
                case 2: // History tab
                    historyPanel.updateHistory(selectedGroup);
                    break;
            }
        });

        // Tab change listener to trigger update when user switches tab
        tabbedPane.addChangeListener(e -> {
            LookupGroup selectedGroup = groupPanel.getSelectedGroup();
            int selectedTab = tabbedPane.getSelectedIndex();
            switch (selectedTab) {
                case 0:
                    valuePanel.updateValues(selectedGroup);
                    break;
                case 1:
                    cachePanel.updateCaches(selectedGroup);
                    break;
                case 2:
                    historyPanel.updateCachedUserMap(); // this will call retrieveUsers()
                    historyPanel.updateHistory(selectedGroup);
                    break;
            }
        });
    }

    private void initLayout() {
        setLayout(new MigLayout("insets 0, novisualpadding, hidemode 3, fill"));

        // Setup tabbed pane with panels
        tabbedPane.addTab("Values", valuePanel);
        tabbedPane.addTab("Cache Status", cachePanel);
        tabbedPane.addTab("History", historyPanel);

        // Split Pane for Group and Value Panels
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.5);
        splitPane.setLeftComponent(groupPanel);
        splitPane.setRightComponent(tabbedPane);

        add(splitPane, "grow, push");
    }

    public void doRefresh() {
        final String workingId = parent.startWorking("Loading lookup groups...");

        SwingWorker<List<LookupGroup>, Void> worker = new SwingWorker<List<LookupGroup>, Void>() {

            @Override
            public List<LookupGroup> doInBackground() throws ClientException {
                return LookupServiceClient.getInstance().getAllGroups();
            }

            @Override
            public void done() {
                try {
                    groupPanel.updateGroupTable(get());
                } catch (Throwable t) {
                    if (t instanceof ExecutionException) {
                        t = t.getCause();
                    }
                    parent.alertThrowable(parent, t, "Error loading groups: " + t.toString());
                } finally {
                    parent.stopWorking(workingId);
                }
            }
        };

        worker.execute();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();

        taskPane.unBold();
    }
}