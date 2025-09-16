package com.mirth.connect.plugins.messagetrends.client.panel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

import javax.swing.Box;
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
	private final Logger logger = LogManager.getLogger(this.getClass());

	private final Frame parent;
	private final MessageTrendsDashboardTabPlugin plugin;
	private SelectionInfo selection;

	private ChartPanel chartPanel;
	private JComboBox<String> intervalComboBox; // canonical codes ("1minute","5minute","15minute","60minute","daily")
	private JComboBox<String> timeRangeComboBox;
	private JComboBox<View> viewComboBox;
	private JButton refreshButton;

	// NEW: Summary views
	private SummaryAllView summaryAllView;
	private SummaryMetricView summaryMetricView;
	private JPanel summaryCardPanel;
	private static final String CARD_ALL = "ALL";
	private static final String CARD_METRIC = "METRIC";

	private SwingWorker<List<MessageStatisticsTimeseries>, Void> inFlight;
	private List<MessageStatisticsTimeseries> lastData = Collections.emptyList();

	private final Map<String, Long> lastFetchByInterval = new HashMap<>();
	private Map<String, String> lastTimeRangeByInterval = new HashMap<>();
	private boolean updatingRange = false;
	private Long lastStartTsMs; // preset window start
	private Long lastEndTsMs; // preset window end

	// Fixed colors for each metric series
	private static final Color COLOR_RECEIVED = new Color(20, 110, 255);
	private static final Color COLOR_SENT = new Color(40, 170, 40);
	private static final Color COLOR_FILTERED = new Color(255, 140, 0);
	private static final Color COLOR_QUEUED = new Color(153, 102, 255);
	private static final Color COLOR_ERROR = new Color(200, 40, 40);

	public enum SelectionBlockReason {
		NO_SELECTION, MULTI_SELECTED
	}

	public enum View {
		ALL("All Message Types"), RECEIVED("Received Only"), SENT("Sent Only"), FILTERED("Filtered Only"), QUEUED("Queued Only"), ERRORS("Errors Only");

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

		initLastTimeRangeByInterval();

		wireEvents();

		updateTimeRangeSelection();

		updateChartTitle("No Channel/Connector Selected");

		showSummaryForView(View.ALL);
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
		if (intervalComboBox.getItemCount() > 0) {
			intervalComboBox.setSelectedIndex(0);
		}

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
		plot.setDomainPannable(false);

		DateAxis axis = (DateAxis) plot.getDomainAxis();
		axis.setDateFormatOverride(new SimpleDateFormat("HH:mm"));

		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
		renderer.setDefaultShapesVisible(true);
		renderer.setDefaultShapesFilled(true);
		renderer.setAutoPopulateSeriesPaint(false);
		renderer.setSeriesPaint(0, COLOR_RECEIVED);
		renderer.setSeriesPaint(1, COLOR_SENT);
		renderer.setSeriesPaint(2, COLOR_FILTERED);
		renderer.setSeriesPaint(3, COLOR_QUEUED);
		renderer.setSeriesPaint(4, COLOR_ERROR);
		plot.setRenderer(renderer);

		chartPanel = new ChartPanel(chart);
		chartPanel.setPreferredSize(new Dimension(720, 360));
		chartPanel.setDomainZoomable(false);
		chartPanel.setMouseWheelEnabled(false);

		// Summary All + Metric views
		summaryAllView = new SummaryAllView();
		summaryAllView.setSeriesColors(COLOR_RECEIVED, COLOR_SENT, COLOR_FILTERED, COLOR_QUEUED, COLOR_ERROR);

		summaryMetricView = new SummaryMetricView();

		// Card panel to hold both
		summaryCardPanel = new JPanel(new CardLayout());
		summaryCardPanel.add(summaryAllView, CARD_ALL);
		summaryCardPanel.add(summaryMetricView, CARD_METRIC);
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

		// wrap panel
		JPanel wrapPanel = new JPanel(new BorderLayout());
		wrapPanel.add(summaryCardPanel, BorderLayout.CENTER);
		wrapPanel.setPreferredSize(new Dimension(10, 65));
		summaryCardPanel.setPreferredSize(new Dimension(10, 65));

		add(controls, BorderLayout.NORTH);
		add(chartPanel, BorderLayout.CENTER);
		add(wrapPanel, BorderLayout.SOUTH);
	}

	private void initLastTimeRangeByInterval() {
		lastTimeRangeByInterval.put("1minute", "last_1h");
		lastTimeRangeByInterval.put("5minute", "last_6h");
		lastTimeRangeByInterval.put("15minute", "last_24h");
		lastTimeRangeByInterval.put("60minute", "last_7d");
		lastTimeRangeByInterval.put("daily", "last_90d");
	}

	private void wireEvents() {
		intervalComboBox.addActionListener(e -> {
			String code = (String) intervalComboBox.getSelectedItem();
			if (!Intervals.isValid(code)) {
				return;
			}

			updateTimeRangeSelection();

			refreshData();
		});

		timeRangeComboBox.addActionListener(e -> {
			if (updatingRange) {
				return;
			}

			String code = (String) intervalComboBox.getSelectedItem();
			Object sel = timeRangeComboBox.getSelectedItem();
			if (Intervals.isValid(code) && sel instanceof String) {
				lastTimeRangeByInterval.put(code, (String) sel);
			}

			refreshData();
		});

		viewComboBox.addActionListener(e -> {
			View v = (View) viewComboBox.getSelectedItem();
			showSummaryForView(v);
			rebindDataset();
			updateSummary(lastData);
		});

		refreshButton.addActionListener(e -> refreshData());
	}

	private void updateTimeRangeSelection() {
		String code = (String) intervalComboBox.getSelectedItem();
		if (!Intervals.isValid(code)) {
			return;
		}

		int minutes = Intervals.minutesOf(code);
		List<String> allowed = TimeRangePresets.allowedRangesFor(minutes);

		// Rebuild model
		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
		for (String presetId : allowed) {
			model.addElement(presetId);
		}

		updatingRange = true;
		try {
			timeRangeComboBox.setModel(model);

			String saved = lastTimeRangeByInterval.get(code);
			if (saved != null && allowed.contains(saved)) {
				timeRangeComboBox.setSelectedItem(saved);
			} else if (!allowed.isEmpty()) {
				String fallback = allowed.get(0);
				timeRangeComboBox.setSelectedItem(fallback);
				lastTimeRangeByInterval.put(code, fallback);
			}
		} finally {
			updatingRange = false;
		}
	}

	public void setSelection(String channelId, String channelName, Integer connectorId, String connectorName) {
		SelectionInfo newSel = new SelectionInfo(channelId, channelName, connectorId, connectorName);
		boolean needRefresh = false;

		if (this.selection == null || !Objects.equals(this.selection.getChannelId(), newSel.getChannelId()) || !Objects.equals(this.selection.getConnectorId(), newSel.getConnectorId())) {
			needRefresh = true;
		}

		if (!needRefresh) {
			final String intervalCode = (String) intervalComboBox.getSelectedItem();
			if (Intervals.isValid(intervalCode)) {
				long now = System.currentTimeMillis();
				long last = lastFetchByInterval.getOrDefault(intervalCode, 0L);
				long gateMs = Intervals.minutesOf(intervalCode) * 60_000L;

				if (now - last >= gateMs) {
					needRefresh = true;
				}
			}
		}

		if (needRefresh) {
			this.selection = newSel;
			refreshData();
		}
	}

	public void blockSelection(SelectionBlockReason reason) {
		setControlsEnabled(false);

		reset(warningMessageForReason(reason));
	}

	public void unblockSelection() {

	}

	private void setControlsEnabled(boolean enabled) {
		intervalComboBox.setEnabled(enabled);
		timeRangeComboBox.setEnabled(enabled);
		viewComboBox.setEnabled(enabled);
		refreshButton.setEnabled(enabled);
	}

	private String warningMessageForReason(SelectionBlockReason reason) {
		switch (reason) {
		case MULTI_SELECTED:
			return "Multiple items selected. Please select exactly one channel or one connector.";
		case NO_SELECTION:
		default:
			return "No valid selection. Please select a channel or a connector.";
		}
	}

	// -------------------- Refresh (Intervals-aware) --------------------

	public void refreshData() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::refreshData);
			return;
		}

		if (selection == null || selection.getChannelId() == null) {
			return;
		}

		final String chId = selection.getChannelId();
		final String chName = selection.getChannelName();
		final String connId = Objects.toString(selection.getConnectorId(), null);
		final String connName = selection.getConnectorName();
		final String intervalCode = (String) intervalComboBox.getSelectedItem();

		if (!Intervals.isValid(intervalCode)) {
			parent.alertError(parent, "Unsupported interval: " + intervalCode);
			return;
		}

		// Record fetch timestamp
		lastFetchByInterval.put(intervalCode, System.currentTimeMillis());

		if (connId == null) {
			updateChartTitle("Message Volume for Channel: " + chName);
		} else {
			updateChartTitle("Message Volume for Connector: " + connName + " (Channel: " + chName + ")");
		}

		final String presetId = getSelectedPresetId();
		final long now = System.currentTimeMillis();
		long bucketMillis = Intervals.minutesOf(intervalCode) * 60_000L;
		final long[] range = computeRangeFromPreset(presetId, now);
		final long startTs = range[0] - bucketMillis;
		final long endTs = range[1];

		// Remember the preset window for axis range
		lastStartTsMs = startTs + bucketMillis;
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

					if (connId == null) {
						return MessageTrendsServiceClient.getInstance().getChannelStatistics(chId, startTs, endTs, intervalCode);
					}

					return MessageTrendsServiceClient.getInstance().getConnectorStatistics(chId, connId, startTs, endTs, intervalCode);

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
		// fallback = 24h
		Duration d = TimeRangePresets.PRESET_TO_DURATION.getOrDefault(presetId, Duration.ofDays(1));

		long start = nowMs - d.toMillis();
		long end = nowMs;

		return new long[] { start, end };
	}

	private void rebindDataset() {
		TimeSeries receivedSeries = new TimeSeries("Received");
		TimeSeries sentSeries = new TimeSeries("Sent");
		TimeSeries filteredSeries = new TimeSeries("Filtered");
		TimeSeries queuedSeries = new TimeSeries("Queued");
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

			long bucketMillis = b.getBucketSizeMinutes() * 60_000L;
			Date endTs = new Date(ts.getTime() + bucketMillis);

			RegularTimePeriod p = periodFactory.of(endTs);
			receivedSeries.addOrUpdate(p, b.getReceived());
			sentSeries.addOrUpdate(p, b.getSent());
			filteredSeries.addOrUpdate(p, b.getFiltered());
			queuedSeries.addOrUpdate(p, b.getQueued());
			errorSeries.addOrUpdate(p, b.getError());
		}

		TimeSeriesCollection dataset = new TimeSeriesCollection();

		dataset.addSeries(receivedSeries); // index 0
		dataset.addSeries(sentSeries); // index 1
		dataset.addSeries(filteredSeries); // index 2
		dataset.addSeries(queuedSeries); // index 3
		dataset.addSeries(errorSeries); // index 4

		JFreeChart chart = chartPanel.getChart();
		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setDataset(dataset);

		XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
		renderer.setDefaultShapesVisible(true);
		renderer.setDefaultShapesFilled(true);

		View v = (View) viewComboBox.getSelectedItem();

		// Default
		boolean[] visible = new boolean[5];

		switch (v) {
		case ALL:
			Arrays.fill(visible, true);
			break;
		case RECEIVED:
			visible[0] = true;
			break;
		case SENT:
			visible[1] = true;
			break;
		case FILTERED:
			visible[2] = true;
			break;
		case QUEUED:
			visible[3] = true;
			break;
		case ERRORS:
			visible[4] = true;
			break;
		default:
			break;
		}

		// Apply flags
		for (int i = 0; i < visible.length; i++) {
			renderer.setSeriesVisible(i, visible[i]);
			renderer.setSeriesVisibleInLegend(i, visible[i]);
		}

		DateAxis axis = (DateAxis) plot.getDomainAxis();
		// Determine visible span
		long spanMs = 0L;
		if (lastStartTsMs != null && lastEndTsMs != null && lastEndTsMs > lastStartTsMs) {
			spanMs = lastEndTsMs - lastStartTsMs;
		}

		String pattern;
		final long ONE_H = 60L * 60_000L;
		final long ONE_D = 24L * ONE_H;
		final long ONE_Y = 365L * ONE_D;

		if (spanMs <= ONE_D) {
			pattern = "HH:mm";
		} else if (spanMs <= 7L * ONE_D) {
			pattern = "MMM dd HH:mm";
		} else if (spanMs <= 90L * ONE_D) {
			pattern = "MMM dd";
		} else if (spanMs <= 3L * ONE_Y) {
			pattern = "yyyy-MM";
		} else {
			pattern = "yyyy";
		}

		axis.setDateFormatOverride(new SimpleDateFormat(pattern));

		// lock axis to preset window regardless of dataset
		if (lastStartTsMs != null && lastEndTsMs != null && lastEndTsMs > lastStartTsMs) {
			axis.setRange(new Date(lastStartTsMs), new Date(lastEndTsMs));
		}

		// Ox: lock auto-range
		axis.setAutoRange(false);

		// Oy: auto-range
		plot.getRangeAxis().setAutoRange(true);
	}

	private void updateChartTitle(String title) {
		JFreeChart chart = chartPanel.getChart();
		chart.setTitle(title);
	}

	private void updateSummary(List<MessageStatisticsTimeseries> data) {
		View v = (View) viewComboBox.getSelectedItem();
		if (v == null) {
			return;
		}

		switch (v) {
		case ALL: {
			long totalReceived = 0, totalSent = 0, totalFiltered = 0, totalError = 0;
			for (MessageStatisticsTimeseries b : data) {
				totalReceived += b.getReceived();
				totalSent += b.getSent();
				totalFiltered += b.getFiltered();
				totalError += b.getError();
			}

			long lastQueued = data.isEmpty() ? 0L : data.get(data.size() - 1).getQueued();

			summaryAllView.setTotals(totalReceived, totalSent, totalFiltered, lastQueued, totalError);
			break;
		}
		case RECEIVED:
		case SENT:
		case FILTERED:
		case QUEUED:
		case ERRORS: {
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

			summaryMetricView.setBorder(new TitledBorder(title));
			summaryMetricView.setQueuedHeader(isQueued);
			summaryMetricView.setStats(totalStr, String.format(Locale.US, "%.1f", avgBucket), formatNumber(min), formatNumber(max), peakStr, avgRateStr);

			break;
		}
		}
	}

	private String formatNumber(long n) {
		return String.format(Locale.US, "%,d", n);
	}

	public void cleanup() {
		/* no-op for now */ }

	public void reset(String title) {
		selection = null;
		updateChartTitle(title);
		lastData = Collections.emptyList();
		rebindDataset();

		summaryAllView.reset();
		summaryMetricView.reset();
	}

	private void showSummaryForView(View v) {
		CardLayout cl = (CardLayout) summaryCardPanel.getLayout();
		if (v == View.ALL) {
			cl.show(summaryCardPanel, CARD_ALL);
		} else {
			cl.show(summaryCardPanel, CARD_METRIC);
		}
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

	private static class SelectionInfo {
		private final String channelId;
		private final String channelName;
		private final Integer connectorId;
		private final String connectorName;

		public SelectionInfo(String channelId, String channelName, Integer connectorId, String connectorName) {
			this.channelId = channelId;
			this.channelName = channelName;
			this.connectorId = connectorId;
			this.connectorName = connectorName;
		}

		public boolean isChannelLevel() {
			return connectorId == null;
		}

		public String getChannelId() {
			return channelId;
		}

		public String getChannelName() {
			return channelName;
		}

		public Integer getConnectorId() {
			return connectorId;
		}

		public String getConnectorName() {
			return connectorName;
		}

		@Override
		public String toString() {
			return isChannelLevel() ? "Channel[" + channelName + "]" : "Connector[" + connectorName + "] in Channel[" + channelName + "]";
		}
	}

}
