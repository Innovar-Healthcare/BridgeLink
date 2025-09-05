package com.mirth.connect.plugins.messagetrends.client.panel;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public class SummaryMetricView extends JPanel {
	private final JTable table;
	private final DefaultTableModel model;

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
		setStandardStatsRow("0", "0", "0", "0", "0 @ —", "0 msg/min");
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}
}
