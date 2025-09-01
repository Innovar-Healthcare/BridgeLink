package com.mirth.connect.plugins.messagetrends.client.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Minute;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.plugins.messagetrends.client.plugin.MessageTrendsDashboardTabPlugin;
import com.mirth.connect.plugins.messagetrends.client.service.MessageTrendsServiceClient;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;
import com.mirth.connect.plugins.messagetrends.shared.model.TimeRangePresets;
import com.mirth.connect.plugins.messagetrends.shared.util.Intervals;

/**
 * Dashboard panel for displaying Message Trends - Uses Intervals (canonical
 * codes) instead of a UI-only Bucket enum - Time ranges constrained by
 * TimeRangePresets.allowedRangesFor(minutes) - Fetches pre-rolled rows for the
 * selected bucket (no SUM on read)
 */
public class MessageTrendsDashboardPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final Frame parent;
	private final MessageTrendsDashboardTabPlugin plugin;
	private String channelId;
	private String channelName;

	private ChartPanel chartPanel;
	private JComboBox<String> intervalComboBox; // canonical codes ("1minute","5minute","15minute","60minute","daily")
	private JComboBox<String> timeRangeComboBox; // values = "<presetId>::<label>"
	private JComboBox<View> viewComboBox;
	private JButton refreshButton;

	private JPanel summaryPanel;
	private JLabel totalReceivedLabel;
	private JLabel totalSentLabel;
	private JLabel totalFilteredLabel;
	private JLabel totalErrorLabel;
	private JLabel peakTimeLabel;
	private JLabel avgRateLabel;

	private SwingWorker<List<MessageStatisticsTimeseries>, Void> inFlight;
	private List<MessageStatisticsTimeseries> lastData = Collections.emptyList();
	private Long lastStartTsMs; // preset window start
	private Long lastEndTsMs; // preset window end

	public enum View {
		ALL("All Message Types"), RECEIVED("Received Only"), SENT("Sent Only"), ERRORS("Errors Only");

		public final String label;

		View(String l) {
			label = l;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public MessageTrendsDashboardPanel(MessageTrendsDashboardTabPlugin plugin) {
		this.plugin = plugin;
		this.parent = PlatformUI.MIRTH_FRAME;

		initComponents();

		initLayout();

		wireEvents();

		// defaults
		intervalComboBox.setSelectedItem("5minute");
		String currentCode = (String) intervalComboBox.getSelectedItem();
		if (Intervals.isValid(currentCode)) {
			repopulateTimeRanges(currentCode);
			selectBestDefaultRange();
		}

		updateChartTitle("No Channel Selected");
	}

	private void initComponents() {
		// interval (bucket) selector — canonical codes from shared Intervals
		intervalComboBox = new JComboBox<>(Intervals.canonicalCodes().toArray(new String[0]));
		intervalComboBox.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				String code = String.valueOf(value);
				String label = humanLabelForInterval(code);
				return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
			}
		});

		// time range selector (contents depend on selected interval minutes)
		timeRangeComboBox = new JComboBox<>();
		timeRangeComboBox.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				String presetId = (String) value;
				String label = TimeRangePresets.PRESET_TO_LABEL.getOrDefault(presetId, presetId);
				return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
			}
		});

		// view selector
		viewComboBox = new JComboBox<>(View.values());

		// refresh
		refreshButton = new JButton("Refresh");

		// chart
		TimeSeriesCollection dataset = new TimeSeriesCollection();
		JFreeChart chart = ChartFactory.createTimeSeriesChart("Message Volume", "Time", "Count", dataset, true, true, false);
		XYPlot plot = chart.getXYPlot();
		plot.setBackgroundPaint(Color.WHITE);
		plot.setDomainGridlinePaint(new Color(238, 238, 238));
		plot.setRangeGridlinePaint(new Color(238, 238, 238));

		DateAxis axis = (DateAxis) plot.getDomainAxis();
		axis.setDateFormatOverride(new SimpleDateFormat("HH:mm"));

		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
		renderer.setDefaultShapesVisible(true);
		renderer.setDefaultShapesFilled(true);
		plot.setRenderer(renderer);

		chartPanel = new ChartPanel(chart);
		chartPanel.setPreferredSize(new Dimension(720, 360));
		chartPanel.setMouseWheelEnabled(true);

		// summary panel
		summaryPanel = new JPanel();
		summaryPanel.setBorder(new TitledBorder("Summary Statistics"));

		totalReceivedLabel = new JLabel("Total Received: 0");
		totalSentLabel = new JLabel("Total Sent: 0");
		totalFilteredLabel = new JLabel("Total Filtered: 0");
		totalErrorLabel = new JLabel("Total Errors: 0");
		peakTimeLabel = new JLabel("Peak Time: N/A");
		avgRateLabel = new JLabel("Avg. Processing Rate: 0 msgs/hour");
	}

	private void initLayout() {
		setLayout(new BorderLayout());

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		controls.add(new JLabel("Interval:"));
		controls.add(intervalComboBox);
		controls.add(new JLabel("Time Range:"));
		controls.add(timeRangeComboBox);
		controls.add(new JLabel("View:"));
		controls.add(viewComboBox);
		controls.add(Box.createHorizontalStrut(16));
		controls.add(refreshButton);

		summaryPanel.setLayout(new java.awt.GridLayout(2, 3, 10, 6));
		summaryPanel.add(totalReceivedLabel);
		summaryPanel.add(totalSentLabel);
		summaryPanel.add(totalFilteredLabel);
		summaryPanel.add(totalErrorLabel);
		summaryPanel.add(peakTimeLabel);
		summaryPanel.add(avgRateLabel);

		add(controls, BorderLayout.NORTH);
		add(chartPanel, BorderLayout.CENTER);
		add(summaryPanel, BorderLayout.SOUTH);
	}

	private void wireEvents() {
		intervalComboBox.addActionListener(e -> {
			String code = (String) intervalComboBox.getSelectedItem();
			if (!Intervals.isValid(code)) {
				return;
			}
			repopulateTimeRanges(code);
			selectBestDefaultRange();
			if (channelId != null) {
				refreshData();
			}
		});
		timeRangeComboBox.addActionListener(e -> {
			if (channelId != null) {
				refreshData();
			}
		});
		viewComboBox.addActionListener(e -> rebindDataset()); // no re-fetch
		refreshButton.addActionListener(e -> refreshData());
	}

	private void repopulateTimeRanges(String intervalCode) {
		int minutes = Intervals.minutesOf(intervalCode);
		List<String> ranges = TimeRangePresets.allowedRangesFor(minutes);
		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
		for (String presetId : ranges) {
			model.addElement(presetId);
		}
		timeRangeComboBox.setModel(model);
	}

	private void selectBestDefaultRange() {
		ComboBoxModel<String> model = timeRangeComboBox.getModel();
		int fallback = model.getSize() - 1;
		for (int i = 0; i < model.getSize(); i++) {
			String val = model.getElementAt(i);
			if ("last_24h".equals(val)) {
				timeRangeComboBox.setSelectedIndex(i);
				return;
			}
		}
		if (fallback >= 0) {
			timeRangeComboBox.setSelectedIndex(fallback);
		}
	}

	/** Set the channel to display statistics for */
	public void setChannelId(String channelId, String channelName) {
		this.channelId = channelId;
		this.channelName = channelName;

		refreshData();
	}

	private void setControlsEnabled(boolean enabled) {
		intervalComboBox.setEnabled(enabled);
		timeRangeComboBox.setEnabled(enabled);
		viewComboBox.setEnabled(enabled);
		refreshButton.setEnabled(enabled);
	}

	// -------------------- Refresh (Intervals-aware) --------------------

	public void refreshData() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::refreshData);
			return;
		}

		if (channelId == null) {
			updateChartTitle("No Channel Selected");
			lastData = Collections.emptyList();
			rebindDataset();
			return;
		}

		final String chId = this.channelId;
		final String chName = this.channelName;
		final String intervalCode = (String) intervalComboBox.getSelectedItem();
		if (!Intervals.isValid(intervalCode)) {
			parent.alertError(parent, "Unsupported interval: " + intervalCode);
			return;
		}

		final String presetId = getSelectedPresetId();
		final long now = System.currentTimeMillis();
		final long[] range = computeRangeFromPreset(presetId, now);
		final long startTs = range[0];
		final long endTs = range[1];

		// Remember the preset window for axis range
		lastStartTsMs = startTs;
		lastEndTsMs = endTs;

		if (inFlight != null && !inFlight.isDone()) {
			inFlight.cancel(true);
		}

		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		setControlsEnabled(false);

		SwingWorker<List<MessageStatisticsTimeseries>, Void> worker = new SwingWorker<List<MessageStatisticsTimeseries>, Void>() {
			@Override
			protected List<MessageStatisticsTimeseries> doInBackground() {
				try {
					if (isCancelled()) {
						return Collections.emptyList();
					}

					return MessageTrendsServiceClient.getInstance().getChannelStatistics(chId, startTs, endTs, intervalCode);
				} catch (ClientException e) {
					return Collections.emptyList();
				} catch (Throwable t) {
					return Collections.emptyList();
				}
			}

			@Override
			protected void done() {
				if (this != inFlight || isCancelled()) {
					return;
				}

				try {
					List<MessageStatisticsTimeseries> data = get();
					lastData = (data != null) ? data : Collections.emptyList();
					rebindDataset();
					updateSummary(lastData);
					updateChartTitle("Message Volume for " + chName);
				} catch (Exception ex) {
					parent.alertError(parent, "Error retrieving statistics. Exception: " + ex.getMessage());
				} finally {
					setCursor(Cursor.getDefaultCursor());
					setControlsEnabled(true);
				}
			}
		};
		inFlight = worker;
		worker.execute();
	}

	private String getSelectedPresetId() {
		Object sel = timeRangeComboBox.getSelectedItem();
		return (sel instanceof String) ? (String) sel : "last_24h";
	}

	private static long[] computeRangeFromPreset(String presetId, long nowMs) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(nowMs);
		Calendar start = (Calendar) cal.clone();
		switch (presetId) {
		case "last_30m":
			start.add(Calendar.MINUTE, -30);
			break;
		case "last_1h":
			start.add(Calendar.HOUR_OF_DAY, -1);
			break;
		case "last_6h":
			start.add(Calendar.HOUR_OF_DAY, -6);
			break;
		case "last_24h":
			start.add(Calendar.DAY_OF_MONTH, -1);
			break;
		case "last_3d":
			start.add(Calendar.DAY_OF_MONTH, -3);
			break;
		case "last_7d":
			start.add(Calendar.DAY_OF_MONTH, -7);
			break;
		case "last_30d":
			start.add(Calendar.DAY_OF_MONTH, -30);
			break;
		case "last_90d":
			start.add(Calendar.DAY_OF_MONTH, -90);
			break;
		case "last_180d":
			start.add(Calendar.DAY_OF_MONTH, -180);
			break;
		case "last_365d":
			start.add(Calendar.DAY_OF_MONTH, -365);
			break;
		case "last_730d":
			start.add(Calendar.DAY_OF_MONTH, -730);
			break;
		case "last_1095d":
			start.add(Calendar.DAY_OF_MONTH, -1095);
			break;
		default:
			start.add(Calendar.DAY_OF_MONTH, -1); // fallback 24h
		}
		return new long[] { start.getTimeInMillis(), cal.getTimeInMillis() };
	}

	private void rebindDataset() {
		TimeSeries receivedSeries = new TimeSeries("Received");
		TimeSeries sentSeries = new TimeSeries("Sent");
		TimeSeries filteredSeries = new TimeSeries("Filtered");
		TimeSeries errorSeries = new TimeSeries("Error");

		String intervalCode = (String) intervalComboBox.getSelectedItem();
		if (!Intervals.isValid(intervalCode)) {
			return;
		}
		int minutes = Intervals.minutesOf(intervalCode);

		RegularTimePeriodFactory periodFactory = new RegularTimePeriodFactory(minutes);

		for (MessageStatisticsTimeseries b : lastData) {
			Date ts = b.getTs(); // confirmed: returns Date
			if (ts == null) {
				continue;
			}
			RegularTimePeriod p = periodFactory.of(ts);
			receivedSeries.addOrUpdate(p, b.getReceived());
			sentSeries.addOrUpdate(p, b.getSent());
			filteredSeries.addOrUpdate(p, b.getFiltered());
			errorSeries.addOrUpdate(p, b.getError());
		}

		TimeSeriesCollection dataset = new TimeSeriesCollection();
		View v = (View) viewComboBox.getSelectedItem();
		if (v == View.ALL || v == View.RECEIVED) {
			dataset.addSeries(receivedSeries);
		}
		if (v == View.ALL || v == View.SENT) {
			dataset.addSeries(sentSeries);
		}
		if (v == View.ALL) {
			dataset.addSeries(filteredSeries);
		}
		if (v == View.ALL || v == View.ERRORS) {
			dataset.addSeries(errorSeries);
		}

		JFreeChart chart = chartPanel.getChart();
		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setDataset(dataset);

		XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
		renderer.setDefaultShapesVisible(true);
		renderer.setDefaultShapesFilled(true);

		DateAxis axis = (DateAxis) plot.getDomainAxis();
		if (minutes >= 1440) {
			axis.setDateFormatOverride(new SimpleDateFormat("MMM dd"));
		} else if (minutes >= 60) {
			axis.setDateFormatOverride(new SimpleDateFormat("MMM dd HH:mm"));
		} else {
			axis.setDateFormatOverride(new SimpleDateFormat("HH:mm"));
		}

		// lock axis to preset window regardless of dataset
		if (lastStartTsMs != null && lastEndTsMs != null && lastEndTsMs > lastStartTsMs) {
			axis.setRange(new Date(lastStartTsMs), new Date(lastEndTsMs));
		} else {
			axis.setAutoRange(true); // fallback (shouldn't happen, but safe)
		}
	}

	private void updateChartTitle(String title) {
		JFreeChart chart = chartPanel.getChart();
		chart.setTitle(title);
	}

	private void updateSummary(List<MessageStatisticsTimeseries> data) {
		long totalReceived = 0, totalSent = 0, totalFiltered = 0, totalError = 0;
		long maxReceived = 0;
		Date peakTime = null;

		for (MessageStatisticsTimeseries b : data) {
			totalReceived += b.getReceived();
			totalSent += b.getSent();
			totalFiltered += b.getFiltered();
			totalError += b.getError();
			if (b.getReceived() > maxReceived) {
				maxReceived = b.getReceived();
				peakTime = b.getTs();
			}
		}

		double avgRate = 0.0;
		if (data.size() > 1) {
			Date start = data.get(0).getTs();
			Date end = data.get(data.size() - 1).getTs();
			if (start != null && end != null) {
				long spanMs = end.getTime() - start.getTime();
				double hours = spanMs / (double) (60 * 60 * 1000L);
				if (hours > 0) {
					avgRate = totalReceived / hours;
				}
			}
		}

		totalReceivedLabel.setText("Total Received: " + formatNumber(totalReceived));
		totalSentLabel.setText("Total Sent: " + formatNumber(totalSent));
		totalFilteredLabel.setText("Total Filtered: " + formatNumber(totalFiltered));
		totalErrorLabel.setText("Total Errors: " + formatNumber(totalError));

		if (peakTime != null) {
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
			peakTimeLabel.setText("Peak Time: " + sdf.format(peakTime) + " (" + maxReceived + " msgs)");
		} else {
			peakTimeLabel.setText("Peak Time: N/A");
		}
		avgRateLabel.setText(String.format(Locale.US, "Avg. Processing Rate: %.1f msgs/hour", avgRate));
	}

	private String formatNumber(long n) {
		return String.format(Locale.US, "%,d", n);
	}

	public void cleanup() {
		/* no-op for now */ }

	public void reset() {
		channelId = null;
		updateChartTitle("No Channel Selected");
		lastData = Collections.emptyList();
		rebindDataset();
		totalReceivedLabel.setText("Total Received: 0");
		totalSentLabel.setText("Total Sent: 0");
		totalFilteredLabel.setText("Total Filtered: 0");
		totalErrorLabel.setText("Total Errors: 0");
		peakTimeLabel.setText("Peak Time: N/A");
		avgRateLabel.setText("Avg. Processing Rate: 0 msgs/hour");
	}

	/** Utility to map minutes -> RegularTimePeriod for consistent alignment */
	private static class RegularTimePeriodFactory {
		private final int minutes;

		RegularTimePeriodFactory(int minutes) {
			this.minutes = minutes;
		}

		RegularTimePeriod of(Date d) {
			if (minutes >= 1440) {
				return new Day(d);
			}
			if (minutes >= 60) {
				return new Hour(d);
			}
			return new Minute(d); // 1,5,15 minute buckets
		}
	}

	private static String humanLabelForInterval(String code) {
		switch (code) {
		case "1minute":
			return "1 Minute";
		case "5minute":
			return "5 Minutes";
		case "15minute":
			return "15 Minutes";
		case "60minute":
			return "1 Hour";
		case "daily":
			return "1 Day";
		default:
			return code;
		}
	}
}
