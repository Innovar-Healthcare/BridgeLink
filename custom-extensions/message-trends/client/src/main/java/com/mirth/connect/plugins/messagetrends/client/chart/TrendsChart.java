package com.mirth.connect.plugins.messagetrends.client.chart;

import java.awt.Color;
import java.util.List;

import javax.swing.JComponent;

import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsDashboardPanel;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

public interface TrendsChart {
	JComponent getComponent();

	void setTitle(String title);

	void setIntervalMinutes(int minutes); // 1/5/15/60/1440

	void setWindowRange(long startMs, long endMs); // lock domain axis

	void setView(MessageTrendsDashboardPanel.View v);

	void setData(List<MessageStatisticsTimeseries> rows); // bind dataset (EDT)

	void setSeriesColors(Color received, Color sent, Color filtered, Color queued, Color error); // optional

	void reset(); // clear dataset
}
