package com.mirth.connect.plugins.messagetrends.client.panel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.Duration;
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

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.plugins.messagetrends.client.chart.LineTrendsChart;
import com.mirth.connect.plugins.messagetrends.client.chart.StackedTrendsChart;
import com.mirth.connect.plugins.messagetrends.client.chart.TrendsChart;
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

	private JComboBox<ChartType> chartTypeCombo;
	private JPanel chartCardPanel;
	private final Map<ChartType, TrendsChart> charts = new HashMap<>();
	private TrendsChart activeChart;
	private String currentTitle = "Message Volume";

	private JComboBox<String> intervalComboBox; // canonical codes ("1minute","5minute","15minute","60minute","daily")
	private JComboBox<String> timeRangeComboBox;
	private JComboBox<View> viewComboBox;
	private JButton refreshButton;

	// Summary views
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
		chartTypeCombo = new JComboBox<>(ChartType.values());
		chartCardPanel = new JPanel(new CardLayout());

		// LINE chart
		TrendsChart line = new LineTrendsChart();
		line.setSeriesColors(COLOR_RECEIVED, COLOR_SENT, COLOR_FILTERED, COLOR_QUEUED, COLOR_ERROR);
		charts.put(ChartType.LINE, line);
		chartCardPanel.add(line.getComponent(), ChartType.LINE.name());

		// STACKED chart
		TrendsChart stacked = new StackedTrendsChart();
		stacked.setSeriesColors(COLOR_RECEIVED, COLOR_SENT, COLOR_FILTERED, COLOR_QUEUED, COLOR_ERROR);
		charts.put(ChartType.STACKED, stacked);
		chartCardPanel.add(stacked.getComponent(), ChartType.STACKED.name());

		// NEW: set active
		activeChart = line;

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
		controls.add(new JLabel("Chart:"));
		controls.add(chartTypeCombo);
		controls.add(Box.createHorizontalStrut(16));
		controls.add(refreshButton);

		// wrap panel
		JPanel wrapPanel = new JPanel(new BorderLayout());
		wrapPanel.add(summaryCardPanel, BorderLayout.CENTER);
		wrapPanel.setPreferredSize(new Dimension(10, 65));
		summaryCardPanel.setPreferredSize(new Dimension(10, 65));

		add(controls, BorderLayout.NORTH);
		add(chartCardPanel, BorderLayout.CENTER);
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

		chartTypeCombo.addActionListener(e -> {
			ChartType type = (ChartType) chartTypeCombo.getSelectedItem();
			switchChart(type);
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

	// NEW
	private void switchChart(ChartType type) {
		if (type == null) {
			return;
		}

		TrendsChart target = charts.get(type);
		if (target == null || target == activeChart) {
			return;
		}

		// Replay state
		String intervalCode = (String) intervalComboBox.getSelectedItem();
		int minutes = Intervals.isValid(intervalCode) ? Intervals.minutesOf(intervalCode) : 1;

		target.setIntervalMinutes(minutes);

		if (lastStartTsMs != null && lastEndTsMs != null && lastEndTsMs > lastStartTsMs) {
			target.setWindowRange(lastStartTsMs, lastEndTsMs); // fix Ox theo preset
		}

		target.setView((View) viewComboBox.getSelectedItem());
		target.setTitle(currentTitle);
		target.setData(lastData);

		// Show card
		CardLayout cl = (CardLayout) chartCardPanel.getLayout();
		cl.show(chartCardPanel, type.name());
		activeChart = target;
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
		chartTypeCombo.setEnabled(enabled);
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

		activeChart.setIntervalMinutes(Intervals.minutesOf(intervalCode));
		activeChart.setWindowRange(lastStartTsMs, lastEndTsMs);

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
		String intervalCode = (String) intervalComboBox.getSelectedItem();
		activeChart.setIntervalMinutes(Intervals.minutesOf(intervalCode));
		activeChart.setWindowRange(lastStartTsMs, lastEndTsMs);
		activeChart.setView((View) viewComboBox.getSelectedItem());
		activeChart.setData(lastData);
	}

	private void updateChartTitle(String title) {
		currentTitle = title;
		if (activeChart != null) {
			activeChart.setTitle(title);
		}
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
		// cancel any pending fetch
		if (inFlight != null && !inFlight.isDone()) {
			inFlight.cancel(true);
		}

		selection = null;
		lastData = Collections.emptyList();
		lastStartTsMs = null;
		lastEndTsMs = null;

		if (activeChart != null) {
			activeChart.reset();
			activeChart.setTitle(title);
		}

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

	private enum ChartType {
		LINE("Line"), STACKED("Stacked Bars");

		final String label;

		ChartType(String l) {
			this.label = l;
		}

		@Override
		public String toString() {
			return label;
		}
	}
}
