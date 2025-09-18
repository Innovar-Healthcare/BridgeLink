package com.mirth.connect.plugins.messagetrends.client.panel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

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
import com.mirth.connect.plugins.messagetrends.client.summary.SummaryAllView;
import com.mirth.connect.plugins.messagetrends.client.summary.SummaryMetricView;
import com.mirth.connect.plugins.messagetrends.client.summary.SummaryView;
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
	private JPanel summaryCardPanel;
	private final Map<SummaryType, SummaryView> summaryViews = new HashMap<>();
	private SummaryView activeSummary;

	private SwingWorker<List<MessageStatisticsTimeseries>, Void> inFlight;
	private List<MessageStatisticsTimeseries> lastData = Collections.emptyList();

	private final Map<String, Long> lastFetchByInterval = new HashMap<>();

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

		wireEvents();

		updateChartTitle("No Channel/Connector Selected");
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
		summaryCardPanel = new JPanel(new CardLayout());

		SummaryView summaryAll = new SummaryAllView();
		summaryAll.setSeriesColors(COLOR_RECEIVED, COLOR_SENT, COLOR_FILTERED, COLOR_QUEUED, COLOR_ERROR);
		summaryViews.put(SummaryType.ALL, summaryAll);
		summaryCardPanel.add(summaryAll.getComponent(), SummaryType.ALL.name());

		SummaryView summaryMetric = new SummaryMetricView();
		summaryMetric.setSeriesColors(COLOR_RECEIVED, COLOR_SENT, COLOR_FILTERED, COLOR_QUEUED, COLOR_ERROR);
		summaryViews.put(SummaryType.METRIC, summaryMetric);
		summaryCardPanel.add(summaryMetric.getComponent(), SummaryType.METRIC.name());

		activeSummary = summaryAll;
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

	private void wireEvents() {
		// View dropdown from controls bar
		controlsBar.getViewCombo().addActionListener(e -> {
			View v = (View) controlsBar.getViewCombo().getSelectedItem();
			SummaryType type = v == View.ALL ? SummaryType.ALL : SummaryType.METRIC;
			switchSummaryView(type);

			rebindDataset();
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

	private void switchSummaryView(SummaryType type) {
		if (type == null) {
			return;
		}

		SummaryView target = summaryViews.get(type);
		if (target == null || target == activeSummary) {
			return;
		}

		// Show card
		CardLayout cl = (CardLayout) summaryCardPanel.getLayout();
		cl.show(summaryCardPanel, type.name());
		activeSummary = target;
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

		activeSummary.setView((View) controlsBar.getViewCombo().getSelectedItem());
		activeSummary.setData(lastData);
	}

	private void updateChartTitle(String title) {
		currentTitle = title;
		if (activeChart != null) {
			activeChart.setTitle(title);
		}
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

		if (activeSummary != null) {
			activeSummary.reset();
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

	private enum SummaryType {
		ALL("All"), METRIC("Metric");

		final String label;

		SummaryType(String l) {
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
