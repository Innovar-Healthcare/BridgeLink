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

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.CacheStatistics;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.GroupStatisticsResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

import net.miginfocom.swing.MigLayout;

public class CacheStatusPanel extends JPanel {
	private final Logger logger = LogManager.getLogger(this.getClass());
	private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm a");

	private final Frame parent = PlatformUI.MIRTH_FRAME;

	private JPanel noGroupSelectedPanel;
	private JPanel contentPanel;

	private LookupGroup selectedGroup;

	public CacheStatusPanel() {
		initComponents();
		initLayout();
	}

	private void initComponents() {
		setBackground(UIConstants.BACKGROUND_COLOR);

		// No group selected panel
		noGroupSelectedPanel = new NoGroupSelectedPanel();
	}

	private void initLayout() {
		setLayout(new CardLayout());

		// Content panel with titled border
		contentPanel = new JPanel(new MigLayout("insets 8, wrap 1, fill", "[grow]", "[]"));
		contentPanel.setBackground(UIConstants.BACKGROUND_COLOR);

		add(contentPanel, "content");
		add(noGroupSelectedPanel, "noGroup");
	}

	public void updateCaches(LookupGroup selectedGroup) {
		boolean showContent = selectedGroup != null;

		contentPanel.setVisible(showContent);
		noGroupSelectedPanel.setVisible(!showContent);

		this.selectedGroup = selectedGroup;

		if (showContent) {
			refreshUI();
		}
	}

	private void refreshUI() {
		contentPanel.removeAll();

		GroupStatisticsResponse data = fetchGroupStatistics(selectedGroup);
		if (data == null || data.getCacheStatistics() == null) {
			showError("Failed to fetch cache statistics.");

			contentPanel.revalidate();
			contentPanel.repaint();

			return;
		}

		// Create horizontal layout
		JPanel horizontalPanel = new JPanel(new MigLayout("insets 0, fill, gapx 5", "[220!][grow, fill]", "[]"));
		horizontalPanel.setBackground(UIConstants.BACKGROUND_COLOR);

		// LEFT: Text Info Panel
		JPanel textInfoPanel = createTextInfoPanel(data);
		horizontalPanel.add(textInfoPanel, "growy");

		// RIGHT: Chart Stack Panel
		JPanel chartsPanel = new JPanel(new MigLayout("insets 0, wrap 1", "[grow, fill]", "[]10[]10[]"));
		chartsPanel.setBackground(UIConstants.BACKGROUND_COLOR);
		chartsPanel.setBorder(BorderFactory.createTitledBorder("Statistics Visualizations"));

		// 1. Bar Chart: Cache Lookups Breakdown
		long totalLookups = data.getTotalLookups();
		long cacheHits = data.getCacheHits();
		long nonHits = totalLookups - cacheHits;

		DefaultCategoryDataset lookupDataset = new DefaultCategoryDataset();
		lookupDataset.addValue(cacheHits, "Lookups", "Cache Hits");
		lookupDataset.addValue(nonHits, "Lookups", "Misses");

		JFreeChart lookupChart = ChartFactory.createBarChart("Group Lookups Breakdown", "Result Type", "Count", lookupDataset, PlotOrientation.VERTICAL, false, true, false);
		ChartPanel lookupChartPanel = new ChartPanel(lookupChart);
		lookupChartPanel.setPreferredSize(new Dimension(300, 200));
		chartsPanel.add(lookupChartPanel, "growx, wrap");

		// 2. Bar Chart: Current Entries vsConfigured Max
		CacheStatistics stats = data.getCacheStatistics();
		boolean cacheEnabled = stats.getConfiguredMaxEntries() > 0;

		if (cacheEnabled) {
			DefaultCategoryDataset sizeDataset = new DefaultCategoryDataset();
			sizeDataset.addValue(stats.getCurrentEntryCount(), "Cache", "Current Entries");
			sizeDataset.addValue(stats.getConfiguredMaxEntries(), "Cache", "Configured Max");

			JFreeChart sizeBarChart = ChartFactory.createBarChart("Cache Entry Count", "Category", "Entries", sizeDataset, PlotOrientation.VERTICAL, false, true, false);
			ChartPanel sizeBarChartPanel = new ChartPanel(sizeBarChart);
			sizeBarChartPanel.setPreferredSize(new Dimension(300, 200));
			chartsPanel.add(sizeBarChartPanel, "growx, wrap");

			// 3. Pie Chart: Cache Hit vs Miss Rate (only if stats are supported)
			if (!stats.isStatsSupported()) {
				JPanel unsupportedPanel = new JPanel();
				unsupportedPanel.setBackground(UIConstants.BACKGROUND_COLOR);
				unsupportedPanel.add(new JLabel("<html><span style='font-size:14pt;'><b>Hit/Miss distribution is not available for this eviction policy.</b></span></html>"));
				chartsPanel.add(unsupportedPanel);

			} else if (stats.getHitCount() > 0 || stats.getMissCount() > 0) {
				DefaultPieDataset pieDataset = new DefaultPieDataset();
				pieDataset.setValue("Hits", stats.getHitCount());
				pieDataset.setValue("Misses", stats.getMissCount());

				JFreeChart pieChart = ChartFactory.createPieChart("Hit vs Miss Distribution", pieDataset, true, true, false);
				ChartPanel pieChartPanel = new ChartPanel(pieChart);
				pieChartPanel.setPreferredSize(new Dimension(300, 200));
				chartsPanel.add(pieChartPanel, "growx, wrap");

			} else {
				JPanel emptyStatsPanel = new JPanel();
				emptyStatsPanel.setBackground(UIConstants.BACKGROUND_COLOR);
				emptyStatsPanel.add(new JLabel("<html><span style='font-size:14pt;'><b>Hit/Miss distribution will appear after lookups occur.</b></span></html>"));
				chartsPanel.add(emptyStatsPanel);
			}
		}

		// Add both panels to the content
		JScrollPane chartScroll = new JScrollPane(chartsPanel);
		chartScroll.setBorder(null);
		chartScroll.setPreferredSize(new Dimension(400, 600)); // adjust as needed
		horizontalPanel.add(chartScroll, "grow, wrap");

		contentPanel.add(horizontalPanel, "span, grow, push");

		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private GroupStatisticsResponse fetchGroupStatistics(LookupGroup group) {
		try {
			return LookupServiceClient.getInstance().getGroupStatistics(group.getId());
		} catch (ClientException e) {
			logger.error("Failed to fetch group statistics for group ID {}", group.getId(), e);
			showError("Unable to fetch group statistics for the selected group:\n" + e.getMessage());
			return null;
		}
	}

	private void resetGroupStatistics(LookupGroup group) {
		try {
			LookupServiceClient.getInstance().resetGroupStatistics(group.getId());
		} catch (ClientException e) {
			logger.error("Failed to reset group statistics for group ID {}", group.getId(), e);
			showError("Unable to reset group statistics for the selected group:\n" + e.getMessage());
		}
	}

	private void clearGroupCache(LookupGroup group) {
		try {
			LookupServiceClient.getInstance().clearGroupCache(group.getId());
		} catch (ClientException e) {
			logger.error("Failed to clear group cache for group ID {}", group.getId(), e);
			showError("Unable to clear group cache for the selected group:\n" + e.getMessage());
		}
	}

	private JPanel createTextInfoPanel(GroupStatisticsResponse data) {
		JPanel mainPanel = new JPanel(new GridLayout(0, 1, 10, 10));
		mainPanel.setBackground(UIConstants.BACKGROUND_COLOR);

		DecimalFormat numberFormat = new DecimalFormat("#,###");
		DecimalFormat percentFormat = new DecimalFormat("0.00%");

		// Top: Lookup Group Stats
		JPanel groupPanel = new JPanel(new MigLayout("insets 8, wrap 1, fillx", "[left]", "[]5"));
		groupPanel.setBackground(UIConstants.BACKGROUND_COLOR);
		groupPanel.setBorder(BorderFactory.createTitledBorder("Lookup Group Statistics"));
		groupPanel.setToolTipText("These statistics are retrieved from the database (persistent values).");

		groupPanel.add(new JLabel("Group ID: " + data.getGroupId()));
		groupPanel.add(new JLabel("Total Lookups: " + numberFormat.format(data.getTotalLookups())));
		groupPanel.add(new JLabel("Cache Hits: " + numberFormat.format(data.getCacheHits())));
		groupPanel.add(new JLabel("Hit Rate (Group Level): " + percentFormat.format(data.getHitRate())));
		groupPanel.add(new JLabel("Last Accessed: " + formatDisplayDate(data.getLastAccessed())));
		groupPanel.add(new JLabel("Reset Date: " + formatDisplayDate(data.getResetDate())));

		// Bottom: Cache Stats
		CacheStatistics stats = data.getCacheStatistics();
		boolean cacheEnabled = stats.getConfiguredMaxEntries() > 0;

		JPanel cachePanel = new JPanel(new MigLayout("insets 8, wrap 1, fillx", "[left]", "[]5"));
		cachePanel.setBackground(UIConstants.BACKGROUND_COLOR);
		cachePanel.setBorder(BorderFactory.createTitledBorder("Cache Performance"));
		cachePanel.setToolTipText("These statistics come from in-memory cache metrics (real-time values).");

		if (cacheEnabled) {
			cachePanel.add(new JLabel("Eviction Policy: " + stats.getEvictionPolicy()));
			cachePanel.add(new JLabel("Current Entry Count: " + stats.getCurrentEntryCount()));
			cachePanel.add(new JLabel("Configured Max Entries: " + stats.getConfiguredMaxEntries()));

			if (stats.isStatsSupported()) {
				cachePanel.add(new JLabel("Hit Count: " + numberFormat.format(stats.getHitCount())));
				cachePanel.add(new JLabel("Miss Count: " + numberFormat.format(stats.getMissCount())));
				cachePanel.add(new JLabel("Eviction Count: " + numberFormat.format(stats.getEvictionCount())));
				cachePanel.add(new JLabel("Hit Ratio (Cache Level): " + percentFormat.format(stats.getHitRatio())));
				cachePanel.add(new JLabel("Miss Ratio (Cache Level): " + percentFormat.format(stats.getMissRatio())));
				cachePanel.add(new JLabel("Total Load Time: " + stats.getTotalLoadTimeFormatted()));
			} else {
				cachePanel.add(new JLabel("<html><div style='font-size:12pt; font-weight:bold;'>Cache statistics are not<br>supported for this eviction policy.</div></html>"));
			}
		} else {
			cachePanel.add(new JLabel("<html><div style='font-size:12pt; font-weight:bold;'>Cache statistics is disabled.</div></html>"));
			cachePanel.setEnabled(false);
		}

		mainPanel.add(groupPanel);
		mainPanel.add(cachePanel);
		mainPanel.add(buildActionButtonsPanel(cacheEnabled));

		return mainPanel;
	}

	private JPanel buildActionButtonsPanel(boolean cacheEnabled) {
		JButton clearGroupStatsButton = new JButton("Reset Group Statistics");
		clearGroupStatsButton.setToolTipText("Clears database-stored lookup counters for this group.");
		clearGroupStatsButton.addActionListener(e -> {
			int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to reset group statistics?", "Confirm Reset", JOptionPane.YES_NO_OPTION);
			if (confirm == JOptionPane.YES_OPTION) {
				resetGroupStatistics(selectedGroup);
				refreshUI();
			}
		});

		JButton clearCacheButton = new JButton("Clear Cache");
		clearCacheButton.setToolTipText("Clears all entries and in-memory cache metrics for this group.");
		clearCacheButton.addActionListener(e -> {
			int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear the in-memory cache?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
			if (confirm == JOptionPane.YES_OPTION) {
				clearGroupCache(selectedGroup);
				refreshUI();
			}
		});
		clearCacheButton.setEnabled(cacheEnabled);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		buttonPanel.setBackground(UIConstants.BACKGROUND_COLOR);
		buttonPanel.add(clearGroupStatsButton);
		buttonPanel.add(clearCacheButton);

		return buttonPanel;
	}

	private String formatDisplayDate(Date date) {
		if (date == null) {
			return "-";
		}

		return formatter.format(date);
	}

	private void showError(String err) {
		PlatformUI.MIRTH_FRAME.alertError(parent, err);
	}
}
