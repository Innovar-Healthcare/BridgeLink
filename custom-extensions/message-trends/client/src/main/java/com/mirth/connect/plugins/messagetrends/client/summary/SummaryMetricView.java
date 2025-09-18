package com.mirth.connect.plugins.messagetrends.client.summary;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsDashboardPanel.View;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

/**
 * Summary card for a single metric view (RECEIVED/SENT/FILTERED/QUEUED/ERRORS).
 * - For QUEUED: "Total" shows the LAST value, and "Avg rate" is "—". - For
 * others: "Total" is SUM, "Avg/bucket" = SUM / N, "Avg rate" = SUM /
 * minutes(start..end).
 */
public class SummaryMetricView extends JPanel implements SummaryView {

	private final JTable table;
	private final DefaultTableModel model;
	private View view;

	public SummaryMetricView() {
		setBorder(new TitledBorder("Statistics Summary"));
		setLayout(new BorderLayout(8, 8));

		// Columns are the metrics (header as requested)
		String[] columns = { "Total", "Average", "Minimum", "Maximum", "Peak Time", "Average Rate" };

		model = new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int r, int c) {
				return false;
			}
		};

		table = new JTable(model);
		table.setFillsViewportHeight(true);
		table.setRowHeight(22);
		table.getTableHeader().setReorderingAllowed(false);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

		// Center-align all values
		DefaultTableCellRenderer center = new DefaultTableCellRenderer();
		center.setHorizontalAlignment(SwingConstants.CENTER);
		for (int i = 0; i < columns.length; i++) {
			table.getColumnModel().getColumn(i).setCellRenderer(center);
		}

		// Preferred viewport
		table.setPreferredScrollableViewportSize(new Dimension(10, 32));

		JScrollPane sp = new JScrollPane(table);
		sp.setPreferredSize(new Dimension(10, 120));
		add(sp, BorderLayout.CENTER);

		// Placeholder row (no calculations here)
		reset();
	}

	/**
	 * Set values in the single data row, matching columns: Total, Avg/bucket, Min,
	 * Max, Peak, AvgRate.
	 */
	public void setStandardStatsRow(String total, String avgPerBucket, String min, String max, String peak, String avgRate) {
		model.setRowCount(0);
		model.addRow(new Object[] { safe(total), safe(avgPerBucket), safe(min), safe(max), safe(peak), safe(avgRate) });
	}

	/** Convenience alias (same order). */
	public void setStats(String total, String avgPerBucket, String min, String max, String peak, String avgRate) {
		setStandardStatsRow(total, avgPerBucket, min, max, peak, avgRate);
	}

	public void reset() {
		setStandardStatsRow("0", "0", "0", "0", "—", "—");
	}

	public void setQueuedHeader(boolean queuedMode) {
		String h0 = queuedMode ? "Last" : "Total"; // column 0
		table.getColumnModel().getColumn(0).setHeaderValue(h0);
		table.getTableHeader().revalidate();
		table.getTableHeader().repaint();
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	@Override
	public final JComponent getComponent() {
		return this;
	}

	@Override
	public void setView(View v) {
		// TODO Auto-generated method stub
		this.view = v;
	}

	@Override
	public void setData(List<MessageStatisticsTimeseries> data) {
		// TODO Auto-generated method stub
		View v = this.view;

		if (v == null || v == View.ALL) {
			return;
		}

		String metricName;
		ToIntFunction<MessageStatisticsTimeseries> getter;
		boolean isQueued = false;

		switch (v) {
		case RECEIVED:
			metricName = "Received";
			getter = MessageStatisticsTimeseries::getReceived;
			break;
		case SENT:
			metricName = "Sent";
			getter = MessageStatisticsTimeseries::getSent;
			break;
		case FILTERED:
			metricName = "Filtered";
			getter = MessageStatisticsTimeseries::getFiltered;
			break;
		case QUEUED:
			metricName = "Queued";
			getter = MessageStatisticsTimeseries::getQueued;
			isQueued = true;
			break;
		case ERRORS:
		default:
			metricName = "Errors";
			getter = MessageStatisticsTimeseries::getError;
			break;
		}

		long total = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		Date peakTs = null;
		for (MessageStatisticsTimeseries b : data) {
			int val = getter.applyAsInt(b);
			total += val;
			if (val < min)
				min = val;
			if (val > max) {
				max = val;
				peakTs = b.getTs();
			}
		}
		if (data.isEmpty()) {
			min = max = 0;
		}

		// Avg per bucket
		double avgBucket = data.isEmpty() ? 0.0 : (double) total / data.size();

		// Avg rate (per minute)
		double avgRate = 0.0;
		if (data.size() > 1) {
			Date start = data.get(0).getTs();
			Date end = data.get(data.size() - 1).getTs();
			if (start != null && end != null && end.after(start)) {
				double minutes = (end.getTime() - start.getTime()) / 60000.0;
				if (minutes > 0) {
					avgRate = total / minutes;
				}
			}
		}

		String peakStr = (peakTs != null) ? String.format("%tF %<tT", peakTs) : "—";
		String title = metricName + " Statistics Summary";

		String totalStr, avgRateStr;
		if (isQueued) {
			long lastQueued = data.isEmpty() ? 0L : data.get(data.size() - 1).getQueued();
			totalStr = formatNumber(lastQueued);
			avgRateStr = "—";
		} else {
			totalStr = formatNumber(total);
			avgRateStr = String.format(Locale.US, "%.1f msg/min", avgRate);
		}

		setBorder(new TitledBorder(title));
		setQueuedHeader(isQueued);
		setStats(totalStr, String.format(Locale.US, "%.1f", avgBucket), formatNumber(min), formatNumber(max), peakStr, avgRateStr);
	}

	@Override
	public void setSeriesColors(Color received, Color sent, Color filtered, Color queued, Color error) {
		// TODO Auto-generated method stub

	}

	private String formatNumber(long n) {
		return String.format(Locale.US, "%,d", n);
	}
}
