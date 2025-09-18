package com.mirth.connect.plugins.messagetrends.client.panel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

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
import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsDashboardPanel.SelectionBlockReason;
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

	private TrendsControlsBar controlsBar;

	private JPanel chartCardPanel;
	private final Map<ChartType, TrendsChart> charts = new HashMap<>();
	private TrendsChart activeChart;
	private String currentTitle = "Message Volume";

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

	private Long lastStartTsMs; // preset window start
	private Long lastEndTsMs; // preset window end

	// --- Windowing state ----------------------------------------------------
	private Long endTsMs; // right edge of the current window
	private boolean isLive; // UI state only, derived in refresh

	// Fixed colors for each metric series
	private static final Color COLOR_RECEIVED = new Color(20, 110, 255);
	private static final Color COLOR_SENT = new Color(40, 170, 40);
	private static final Color COLOR_FILTERED = new Color(255, 140, 0);
	private static final Color COLOR_QUEUED = new Color(153, 102, 255);
	private static final Color COLOR_ERROR = new Color(200, 40, 40);
	private static final double SHIFT_FRACTION = 0.5;

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

		updateChartTitle("No Channel/Connector Selected");

		showSummaryForView(View.ALL);
	}

	private void initComponents() {
		controlsBar = new TrendsControlsBar();

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

		// wrap panel
		JPanel wrapPanel = new JPanel(new BorderLayout());
		wrapPanel.add(summaryCardPanel, BorderLayout.CENTER);
		wrapPanel.setPreferredSize(new Dimension(10, 65));
		summaryCardPanel.setPreferredSize(new Dimension(10, 65));

		add(controlsBar, BorderLayout.NORTH);
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
		// View dropdown from controls bar
		controlsBar.getViewCombo().addActionListener(e -> {
			View v = (View) controlsBar.getViewCombo().getSelectedItem();
			showSummaryForView(v);
			rebindDataset();
			updateSummary(lastData);
		});

		// Chart dropdown from controls bar ("Line" / "Stacked")
		controlsBar.getChartCombo().addActionListener(e -> {
			Object sel = controlsBar.getChartCombo().getSelectedItem();
			ChartType type = ("Stacked".equals(sel)) ? ChartType.STACKED : ChartType.LINE;
			switchChart(type);
		});

		// Interval changed → Go Live (simplify as you decided)
		controlsBar.getIntervalCombo().addActionListener(e -> refreshData(RefreshMode.FORCE_LIVE));

		// Refresh button passthrough (keeps old behavior for now)
		controlsBar.getRefreshButton().addActionListener(e -> refreshData(RefreshMode.FORCE_LIVE));

		// Prev / Next buttons (paused navigation)
		controlsBar.getPrevButton().addActionListener(e -> {
			if (endTsMs == null) {
				refreshData(RefreshMode.FORCE_LIVE);
			} else {
				// shift left by stride buckets (you can compute stride elsewhere)
				final String presetId = (String) controlsBar.getTimeRangeCombo().getSelectedItem();
				final long rangeMs = TimeRangePresets.toDuration(presetId).toMillis();

				endTsMs -= (long) (SHIFT_FRACTION * rangeMs); // move window left
				refreshData(RefreshMode.KEEP_POSITION);
			}
		});

		controlsBar.getNextButton().addActionListener(e -> {
			if (endTsMs == null) {
				refreshData(RefreshMode.FORCE_LIVE);
			} else {
				final String intervalCode = (String) controlsBar.getIntervalCombo().getSelectedItem();
				final long bucketMillis = Intervals.minutesOf(intervalCode) * 60_000L;
				final long liveCap = snapToBucket(System.currentTimeMillis(), bucketMillis);
				final String presetId = (String) controlsBar.getTimeRangeCombo().getSelectedItem();
				final long rangeMs = TimeRangePresets.toDuration(presetId).toMillis();
				endTsMs += (long) (SHIFT_FRACTION * rangeMs); // move window right

				if (endTsMs >= liveCap) {
					refreshData(RefreshMode.FORCE_LIVE);
				} else {
					refreshData(RefreshMode.KEEP_POSITION);
				}
			}
		});
	}

	public void setSelection(String channelId, String channelName, Integer connectorId, String connectorName) {
		SelectionInfo newSel = new SelectionInfo(channelId, channelName, connectorId, connectorName);

		if (this.selection == null || !Objects.equals(this.selection.getChannelId(), newSel.getChannelId()) || !Objects.equals(this.selection.getConnectorId(), newSel.getConnectorId())) {
			this.selection = newSel;
			refreshData(RefreshMode.FORCE_LIVE);

			return;
		}

		final String intervalCode = (String) controlsBar.getIntervalCombo().getSelectedItem();
		if (Intervals.isValid(intervalCode)) {
			long now = System.currentTimeMillis();
			long last = lastFetchByInterval.getOrDefault(intervalCode, 0L);
			long gateMs = Intervals.minutesOf(intervalCode) * 60_000L;

			if (now - last >= gateMs && isLive) {
				refreshData(RefreshMode.FORCE_LIVE);
			}
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
		String intervalCode = (String) controlsBar.getIntervalCombo().getSelectedItem();
		int minutes = Intervals.isValid(intervalCode) ? Intervals.minutesOf(intervalCode) : 1;

		target.setIntervalMinutes(minutes);

		if (lastStartTsMs != null && lastEndTsMs != null && lastEndTsMs > lastStartTsMs) {
			target.setWindowRange(lastStartTsMs, lastEndTsMs);
		}

		target.setView((View) controlsBar.getViewCombo().getSelectedItem());
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
		controlsBar.setControlsEnabled(enabled);
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
		refreshData(RefreshMode.KEEP_POSITION); // default path if you call it directly
	}

	public void refreshData(RefreshMode mode) {
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
		final String intervalCode = (String) controlsBar.getIntervalCombo().getSelectedItem();

		if (!Intervals.isValid(intervalCode)) {
			parent.alertError(parent, "Unsupported interval: " + intervalCode);
			return;
		}

		if (mode == RefreshMode.FORCE_LIVE) {
			// Record fetch timestamp
			lastFetchByInterval.put(intervalCode, System.currentTimeMillis());
		}

		if (connId == null) {
			updateChartTitle("Message Volume for Channel: " + chName);
		} else {
			updateChartTitle("Message Volume for Connector: " + connName + " (Channel: " + chName + ")");
		}

		// TEMP window: keep prior behavior until N/prev/next is wired.
		final long nowMs = System.currentTimeMillis();
		final long bucketMillis = Intervals.minutesOf(intervalCode) * 60_000L;
		final long liveCapMs = snapToBucket(nowMs, bucketMillis);

		if (mode == RefreshMode.FORCE_LIVE || endTsMs == null) {
			endTsMs = liveCapMs;
		} else {
			endTsMs = snapToBucket(endTsMs, bucketMillis);
		}

		// Badge state
		isLive = (Objects.equals(endTsMs, liveCapMs));
		controlsBar.setLive(isLive);

		// range
		String presetId = (String) controlsBar.getTimeRangeCombo().getSelectedItem();
		long rangeMs = TimeRangePresets.toDuration(presetId).toMillis();

		final int points = (int) Math.max(1L, ceilDiv(rangeMs, bucketMillis));
		final long windowMs = points * bucketMillis;
		final long startTsMs = endTsMs - windowMs;

		// Fetch range includes one extra leading bucket for end-of-bucket plotting
		final long startFetch = startTsMs - bucketMillis;
		final long endFetch = endTsMs;

		// Lock axes before data lands
		lastStartTsMs = startTsMs;
		lastEndTsMs = endTsMs;
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
						return MessageTrendsServiceClient.getInstance().getChannelStatistics(chId, startFetch, endFetch, intervalCode);
					}

					return MessageTrendsServiceClient.getInstance().getConnectorStatistics(chId, connId, startFetch, endFetch, intervalCode);

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

	private void rebindDataset() {
		String intervalCode = (String) controlsBar.getIntervalCombo().getSelectedItem();
		activeChart.setIntervalMinutes(Intervals.minutesOf(intervalCode));
		activeChart.setWindowRange(lastStartTsMs, lastEndTsMs);
		activeChart.setView((View) controlsBar.getViewCombo().getSelectedItem());
		activeChart.setData(lastData);
	}

	private void updateChartTitle(String title) {
		currentTitle = title;
		if (activeChart != null) {
			activeChart.setTitle(title);
		}
	}

	private void updateSummary(List<MessageStatisticsTimeseries> data) {
		View v = (View) controlsBar.getViewCombo().getSelectedItem();
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

	/** Floor-snap a timestamp to the given bucket size in ms (UTC). */
	private static long snapToBucket(long epochMs, long bucketMillis) {
		if (bucketMillis <= 0) {
			return epochMs;
		}

		return (epochMs / bucketMillis) * bucketMillis;
	}

	private static long ceilDiv(long a, long b) {
		return (a + b - 1) / b;
	}

	/** How the caller wants refresh to behave. */
	private enum RefreshMode {
		KEEP_POSITION, // keep endTsMs as-is (e.g., N change, Prev/Next)
		FORCE_LIVE, // force endTsMs to liveCap (manual Refresh, interval change)
	}

}
