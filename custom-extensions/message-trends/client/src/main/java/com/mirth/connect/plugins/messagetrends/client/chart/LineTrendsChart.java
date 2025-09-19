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
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

public class LineTrendsChart extends AbstractTrendsChart {

	private final TimeSeriesCollection dataset = new TimeSeriesCollection();

	private final TimeSeries received = new TimeSeries("Received"); // index 0
	private final TimeSeries sent = new TimeSeries("Sent"); // index 1
	private final TimeSeries filtered = new TimeSeries("Filtered"); // index 2
	private final TimeSeries queued = new TimeSeries("Queued"); // index 3
	private final TimeSeries error = new TimeSeries("Error"); // index 4

	private final XYLineAndShapeRenderer lineRenderer;

	public LineTrendsChart() {
		// dataset: keep series order consistent with setSeriesColors()/setView()
		dataset.addSeries(received);
		dataset.addSeries(sent);
		dataset.addSeries(filtered);
		dataset.addSeries(queued);
		dataset.addSeries(error);

		// chart & plot
		JFreeChart c = ChartFactory.createTimeSeriesChart("Message Volume", "Time", "Count", dataset, true, true, false);
		XYPlot p = c.getXYPlot();

		// renderer
		lineRenderer = new XYLineAndShapeRenderer(true, true);
		lineRenderer.setAutoPopulateSeriesPaint(false);
		lineRenderer.setDefaultShapesVisible(true);
		lineRenderer.setDefaultShapesFilled(true);
		lineRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator("{0}: ({1},{2})", new SimpleDateFormat("yyyy-MM-dd HH:mm"), NumberFormat.getIntegerInstance()));
		p.setRenderer(lineRenderer);

		// panel
		ChartPanel cp = new ChartPanel(c);

		// hook shared styling/behavior from base
		initCommon(c, p, cp, lineRenderer);
	}

	@Override
	protected void clearDataset() {
		received.clear();
		sent.clear();
		filtered.clear();
		queued.clear();
		error.clear();
	}

	@Override
	protected void addPoint(RegularTimePeriod p, MessageStatisticsTimeseries r) {
		received.addOrUpdate(p, r.getReceived());
		sent.addOrUpdate(p, r.getSent());
		filtered.addOrUpdate(p, r.getFiltered());
		queued.addOrUpdate(p, r.getQueued());
		error.addOrUpdate(p, r.getError());
	}
}
