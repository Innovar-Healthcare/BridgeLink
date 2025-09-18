package com.mirth.connect.plugins.messagetrends.client.summary;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsDashboardPanel.View;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

/**
 * Summary card for ALL view: shows totals for Received, Sent, Filtered, Last
 * Queued, and Errors.
 */
public class SummaryAllView extends JPanel implements SummaryView {

	private final JLabel totalReceivedLabel = new JLabel("Total Received: 0");
	private final JLabel totalSentLabel = new JLabel("Total Sent: 0");
	private final JLabel totalFilteredLabel = new JLabel("Total Filtered: 0");
	private final JLabel totalQueuedLabel = new JLabel("Last Queued: 0");
	private final JLabel totalErrorLabel = new JLabel("Total Errors: 0");
	private final JLabel blankLabel = new JLabel("");

	private View view;

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
	private void setTotals(long received, long sent, long filtered, long queued, long error) {
		totalReceivedLabel.setText("Total Received: " + format(received));
		totalSentLabel.setText("Total Sent: " + format(sent));
		totalFilteredLabel.setText("Total Filtered: " + format(filtered));
		totalQueuedLabel.setText("Last Queued: " + format(queued));
		totalErrorLabel.setText("Total Errors: " + format(error));
	}

	/** Optional: set placeholder text for the extra slot. */
	private void setBlankSlotText(String text) {
		blankLabel.setText(text == null ? "" : text);
	}

	public void reset() {
		setTotals(0, 0, 0, 0, 0);
		setBlankSlotText("");
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

		if (v == null || v != View.ALL) {
			return;
		}

		long totalReceived = 0, totalSent = 0, totalFiltered = 0, totalError = 0;
		for (MessageStatisticsTimeseries b : data) {
			totalReceived += b.getReceived();
			totalSent += b.getSent();
			totalFiltered += b.getFiltered();
			totalError += b.getError();
		}

		long lastQueued = data.isEmpty() ? 0L : data.get(data.size() - 1).getQueued();

		setTotals(totalReceived, totalSent, totalFiltered, lastQueued, totalError);
	}

	@Override
	public void setSeriesColors(Color received, Color sent, Color filtered, Color queued, Color error) {
		// TODO Auto-generated method stub
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

	private static String format(long n) {
		return String.format(Locale.US, "%,d", n);
	}
}
