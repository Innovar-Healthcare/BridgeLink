/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.client.chart;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickMarkPosition;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StackedXYBarRenderer;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeTableXYDataset;

import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

public class StackedTrendsChart extends AbstractTrendsChart {

	private TimeTableXYDataset dataset;
	private final StackedXYBarRenderer barRenderer;

	public StackedTrendsChart() {
		// dataset
		dataset = new TimeTableXYDataset();

		// chart & plot
		JFreeChart c = ChartFactory.createTimeSeriesChart("Message Volume", "Time", "Count", dataset, true, true, false);
		XYPlot p = c.getXYPlot();

		// renderer
		barRenderer = new StackedXYBarRenderer();
		barRenderer.setRenderAsPercentages(false);
		barRenderer.setDrawBarOutline(false);
		barRenderer.setShadowVisible(false);
		barRenderer.setMargin(0.1);
		barRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator("{0}: ({1},{2})", new SimpleDateFormat("yyyy-MM-dd HH:mm"), NumberFormat.getIntegerInstance()));
		p.setRenderer(barRenderer);

		// X ticks centered on bar groups
		((DateAxis) p.getDomainAxis()).setTickMarkPosition(DateTickMarkPosition.MIDDLE);

		// panel
		ChartPanel cp = new ChartPanel(c);

		// hook shared styling/behavior from base
		initCommon(c, p, cp, barRenderer);
	}

	@Override
	protected void clearDataset() {
		// Use replacement to be compatible with older JFreeChart versions
		dataset = new TimeTableXYDataset();
		plot.setDataset(dataset);
	}

	@Override
	protected void addPoint(RegularTimePeriod p, MessageStatisticsTimeseries r) {
		dataset.add(p, r.getReceived(), "Received");
		dataset.add(p, r.getSent(), "Sent");
		dataset.add(p, r.getFiltered(), "Filtered");
		dataset.add(p, r.getQueued(), "Queued");
		dataset.add(p, r.getError(), "Error");
	}
}
