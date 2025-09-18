package com.mirth.connect.plugins.messagetrends.client.summary;

import java.awt.Color;
import java.util.List;

import javax.swing.JComponent;

import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsDashboardPanel;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

public interface SummaryView {
	JComponent getComponent();

	void setView(MessageTrendsDashboardPanel.View v);

	void setWindowRange(long startMs, long endMs);

	void setData(List<MessageStatisticsTimeseries> rows); // bind dataset (EDT)

	void setSeriesColors(Color received, Color sent, Color filtered, Color queued, Color error); // optional

	void reset(); // clear dataset
}
