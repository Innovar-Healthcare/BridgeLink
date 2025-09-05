package com.mirth.connect.plugins.messagetrends.client.panel;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.Locale;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

public class SummaryAllView extends JPanel {
	private final JLabel totalReceivedLabel = new JLabel("Total Received: 0");
	private final JLabel totalSentLabel = new JLabel("Total Sent: 0");
	private final JLabel totalFilteredLabel = new JLabel("Total Filtered: 0");
	private final JLabel totalQueuedLabel = new JLabel("Total Queued: 0");
	private final JLabel totalErrorLabel = new JLabel("Total Errors: 0");
	private final JLabel blankLabel = new JLabel("");

	public SummaryAllView() {
		setBorder(new TitledBorder("All Statistics Summary"));
		setLayout(new GridLayout(2, 3, 10, 6));

		add(totalReceivedLabel);
		add(totalSentLabel);
		add(totalFilteredLabel);
		add(totalQueuedLabel);
		add(totalErrorLabel);
		add(blankLabel);
	}

	/** Simple setter to push totals (no calculation here). */
	public void setTotals(long received, long sent, long filtered, long queued, long error) {
		totalReceivedLabel.setText("Total Received: " + format(received));
		totalSentLabel.setText("Total Sent: " + format(sent));
		totalFilteredLabel.setText("Total Filtered: " + format(filtered));
		totalQueuedLabel.setText("Total Queued: " + format(queued));
		totalErrorLabel.setText("Total Errors: " + format(error));
	}

	/** Optional: set series colors to match the chart palette. */
	public void setSeriesColors(Color received, Color sent, Color filtered, Color queued, Color error) {
		if (received != null) {
			totalReceivedLabel.setForeground(received);
		}

		if (sent != null) {
			totalSentLabel.setForeground(sent);
		}

		if (filtered != null) {
			totalFilteredLabel.setForeground(filtered);
		}

		if (queued != null) {
			totalQueuedLabel.setForeground(queued);
		}

		if (error != null) {
			totalErrorLabel.setForeground(error);
		}
	}

	/** Optional: set placeholder text for the extra slot. */
	public void setBlankSlotText(String text) {
		blankLabel.setText(text == null ? "" : text);
	}

	public void reset() {
		setTotals(0, 0, 0, 0, 0);
		setBlankSlotText("");
	}

	private static String format(long n) {
		return String.format(Locale.US, "%,d", n);
	}
}
