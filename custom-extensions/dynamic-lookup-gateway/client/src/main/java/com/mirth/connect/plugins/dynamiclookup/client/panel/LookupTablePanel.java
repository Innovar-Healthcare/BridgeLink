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

import java.awt.Color;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.plugins.dynamiclookup.client.dialog.LookupSettingDialog;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

import net.miginfocom.swing.MigLayout;

/**
 * @author Thai Tran (thaitran@innovarhealthcare.com)
 * @create 2025-05-13 10:25 AM
 */
public class LookupTablePanel extends JPanel {
	private final Logger logger = LogManager.getLogger(this.getClass());

	private final DataStorePanel taskPane;
	private final GroupPanel groupPanel;
	private final DetailsPanel detailsPanel;
	private final ValuePanel valuePanel;
	private final CacheStatusPanel cachePanel;
	private final HistoryPanel historyPanel;
	private final JTabbedPane tabbedPane;
	private JButton settingsButton;

	private final Frame parent = PlatformUI.MIRTH_FRAME;

	public LookupTablePanel(DataStorePanel taskPane) {
		this.taskPane = taskPane;

		this.groupPanel = new GroupPanel();
		this.detailsPanel = new DetailsPanel();
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
				detailsPanel.updateDetails(selectedGroup);
				break;
			case 1: // Values tab
				valuePanel.updateValues(selectedGroup);
				break;
			case 2: // Cache tab
				cachePanel.updateCaches(selectedGroup);
				break;
			case 3: // History tab
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
				detailsPanel.updateDetails(selectedGroup);
				break;
			case 1:
				valuePanel.updateValues(selectedGroup);
				break;
			case 2:
				cachePanel.updateCaches(selectedGroup);
				break;
			case 3:
				historyPanel.updateCachedUserMap(); // this will call retrieveUsers()
				historyPanel.updateHistory(selectedGroup);
				break;
			}
		});

		settingsButton = new JButton(new ImageIcon(Frame.class.getResource("images/wrench.png")));
		settingsButton.addActionListener(e -> {
			new LookupSettingDialog(parent);
		});
	}

	private void initLayout() {
		setLayout(new MigLayout("insets 0, novisualpadding, hidemode 3, fill, wrap 1", // wrap 1 = one column, next add goes to next row
				"[grow]", "[][grow]"));

		add(buildTopActionBar(), "growx");

		tabbedPane.addTab("Details", detailsPanel);
		tabbedPane.addTab("Values", valuePanel);
		tabbedPane.addTab("Cache Status", cachePanel);
		tabbedPane.addTab("History", historyPanel);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.setDividerLocation(300);
		splitPane.setResizeWeight(0.5);
		splitPane.setLeftComponent(groupPanel);
		splitPane.setRightComponent(tabbedPane);

		add(splitPane, "grow, push");

	}

	private JComponent buildTopActionBar() {
		JPanel bar = new JPanel(new MigLayout("insets 2 4 2 4, novisualpadding, fillx", // compact paddings, fill horizontally
				"[grow][]", "[]"));

		bar.add(Box.createHorizontalStrut(0), "growx");
		bar.add(settingsButton, "");
		bar.setOpaque(true);
		bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)));

		return bar;
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