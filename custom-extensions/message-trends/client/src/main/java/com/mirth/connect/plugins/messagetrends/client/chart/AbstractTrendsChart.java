package com.mirth.connect.plugins.messagetrends.client.chart;

import java.awt.Color;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

import javax.swing.JComponent;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Minute;
import org.jfree.data.time.RegularTimePeriod;

import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsDashboardPanel.View;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageStatisticsTimeseries;

/** Abstract base for Trends charts (Line/Stacked). */
public abstract class AbstractTrendsChart implements TrendsChart {

	protected JFreeChart chart;
	protected ChartPanel panel;
	protected XYPlot plot;
	protected XYItemRenderer renderer;
	protected DateAxis xAxis;
	protected NumberAxis yAxis;

	protected int minutes = 1;
	protected Long winStartMs, winEndMs;
	protected String currentTitle = "Message Volume";

	/** Call from subclass constructor after creating chart/plot/panel/renderer. */
	protected final void initCommon(JFreeChart chart, XYPlot plot, ChartPanel panel, XYItemRenderer renderer) {
		this.chart = chart;
		this.plot = plot;
		this.panel = panel;
		this.renderer = renderer;
		this.xAxis = (DateAxis) plot.getDomainAxis();
		this.yAxis = (NumberAxis) plot.getRangeAxis();

		applyCommonStyle();
	}

	private void applyCommonStyle() {
		chart.setBackgroundPaint(Color.WHITE);
		chart.setAntiAlias(true);
		if (chart.getLegend() != null) {
			chart.getLegend().setBackgroundPaint(Color.WHITE);
		}

		panel.setBackground(Color.WHITE);
		panel.setDomainZoomable(false);
		panel.setMouseWheelEnabled(false);

		plot.setBackgroundPaint(Color.WHITE);
		plot.setDomainGridlinePaint(new Color(238, 238, 238));
		plot.setRangeGridlinePaint(new Color(238, 238, 238));
		plot.setDomainPannable(false);

		xAxis.setAutoRange(false);
		yAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
		yAxis.setNumberFormatOverride(NumberFormat.getIntegerInstance());
		yAxis.setAutoRangeIncludesZero(true);
		yAxis.setAutoRangeStickyZero(true);
	}

	@Override
	public final JComponent getComponent() {
		return panel;
	}

	@Override
	public void setTitle(String title) {
		this.currentTitle = (title != null) ? title : "Message Volume";
		chart.setTitle(this.currentTitle);
	}

	@Override
	public void setIntervalMinutes(int minutes) {
		this.minutes = Math.max(1, minutes);
		updateXAxisFormat();
	}

	@Override
	public void setWindowRange(long startMs, long endMs) {
		this.winStartMs = startMs;
		this.winEndMs = endMs;
		if (endMs > startMs) {
			xAxis.setRange(new Date(startMs), new Date(endMs)); // fix Ox
			xAxis.setAutoRange(false);
		}
		updateXAxisFormat();
	}

	@Override
	public void setView(View v) {
		boolean[] vis = new boolean[] { false, false, false, false, false };
		if (v == null) {
			v = View.ALL;
		}

		switch (v) {
		case ALL:
			vis = new boolean[] { true, true, true, true, true };
			break;
		case RECEIVED:
			vis[0] = true;
			break;
		case SENT:
			vis[1] = true;
			break;
		case FILTERED:
			vis[2] = true;
			break;
		case QUEUED:
			vis[3] = true;
			break;
		case ERRORS:
			vis[4] = true;
			break;
		}

		for (int i = 0; i < vis.length; i++) {
			renderer.setSeriesVisible(i, vis[i]);
			renderer.setSeriesVisibleInLegend(i, vis[i]);
		}
	}

	@Override
	public void setSeriesColors(Color received, Color sent, Color filtered, Color queued, Color error) {
		renderer.setSeriesPaint(0, received);
		renderer.setSeriesPaint(1, sent);
		renderer.setSeriesPaint(2, filtered);
		renderer.setSeriesPaint(3, queued);
		renderer.setSeriesPaint(4, error);
	}

	@Override
	public void setData(List<MessageStatisticsTimeseries> rows) {
		clearDataset();
		if (rows == null || rows.isEmpty()) {
			return; // keep axes as-is when no data
		}

		RegularTimePeriodFactory factory = new RegularTimePeriodFactory(minutes);
		for (MessageStatisticsTimeseries r : rows) {
			Date startTs = r.getTs();
			if (startTs == null) {
				continue;
			}

			long bucketMillis = (long) r.getBucketSizeMinutes() * 60_000L;
			Date endTs = new Date(startTs.getTime() + bucketMillis);

			RegularTimePeriod p = factory.of(endTs.getTime());
//			TimePeriod p = factory.of(endTs);
			addPoint(p, r);
		}

		// Oy: auto-range
		yAxis.setAutoRange(true);
	}

	@Override
	public void reset() {
		// Clear data
		clearDataset();
	}

	protected void updateXAxisFormat() {
		if (winStartMs == null || winEndMs == null || winEndMs <= winStartMs) {
			return;
		}

		long span = winEndMs - winStartMs;
		final long ONE_H = 60L * 60_000L;
		final long ONE_D = 24L * ONE_H;
		final long ONE_Y = 365L * ONE_D;

		String pattern = (span <= ONE_D) ? "HH:mm" : (span <= 7L * ONE_D) ? "MMM dd HH:mm" : (span <= 90L * ONE_D) ? "MMM dd" : (span <= 3L * ONE_Y) ? "yyyy-MM" : "yyyy";

		xAxis.setDateFormatOverride(new SimpleDateFormat(pattern));
	}

	/** minutes -> RegularTimePeriod mapper */
	protected static final class RegularTimePeriodFactory {
		private final int minutes;

		public RegularTimePeriodFactory(int minutes) {
			this.minutes = minutes;
		}

		public RegularTimePeriod of(Date d) {
			if (minutes >= 1440) {
				return new Day(d);
			}
			if (minutes >= 60) {
				return new Hour(d);
			}

			return new Minute(d);
		}

		public RegularTimePeriod of(long bucketStartMs) {
			if (minutes >= 1440) {
				return new Day(new Date(bucketStartMs)); // 1 day
			}
			if (minutes == 60) {
				return new Hour(new Date(bucketStartMs)); // 1 hour
			}
			if (minutes == 1) {
				return new Minute(new Date(bucketStartMs)); // 1 minute
			}

			return new MultiMinute(bucketStartMs, minutes, TimeZone.getTimeZone("UTC"));
		}
	}

	static final class MultiMinute extends RegularTimePeriod {
		private final long startMs;
		private final long endMs; // exclusive
		private final int minutes;
		private final TimeZone tz;

		MultiMinute(long startMs, int minutes, TimeZone tz) {
			this.minutes = minutes;
			this.tz = tz;
			long bucket = minutes * 60_000L;
			this.startMs = startMs;
			this.endMs = startMs + bucket;
		}

		@Override
		public long getFirstMillisecond() {
			return startMs;
		}

		@Override
		public long getLastMillisecond() {
			return endMs - 1;
		}

		public long getFirstMillisecond(Calendar calendar) {
			if (calendar == null) {
				throw new IllegalArgumentException("Calendar cannot be null");
			}
			Calendar c = (Calendar) calendar.clone();
			c.setTimeZone(tz);
			c.setTimeInMillis(startMs);
			return c.getTimeInMillis();
		}

		public long getLastMillisecond(Calendar calendar) {
			if (calendar == null) {
				throw new IllegalArgumentException("Calendar cannot be null");
			}
			Calendar c = (Calendar) calendar.clone();
			c.setTimeZone(tz);
			c.setTimeInMillis(endMs - 1);
			return c.getTimeInMillis();
		}

		@Override
		public void peg(Calendar calendar) {
			// no-op: startMs/endMs
		}

		@Override
		public RegularTimePeriod previous() {
			return new MultiMinute(startMs - minutes * 60_000L, minutes, tz);
		}

		@Override
		public RegularTimePeriod next() {
			return new MultiMinute(startMs + minutes * 60_000L, minutes, tz);
		}

		@Override
		public long getSerialIndex() {
			return startMs / (minutes * 60_000L);
		}

		@Override
		public int compareTo(Object o) {
			if (o == this)
				return 0;

			if (o instanceof RegularTimePeriod) {
				long t1 = this.getFirstMillisecond();
				long t2 = ((RegularTimePeriod) o).getFirstMillisecond();
				return Long.compare(t1, t2);
			}

			throw new ClassCastException("Cannot compare " + (o == null ? "null" : o.getClass().getName()) + " with MultiMinute");
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof MultiMinute))
				return false;
			MultiMinute that = (MultiMinute) o;
			return this.startMs == that.startMs && this.minutes == that.minutes;
		}

		@Override
		public int hashCode() {
			return Objects.hash(startMs, minutes);
		}
	}

	/** Subclasses implement dataset clearing. */
	protected abstract void clearDataset();

	/**
	 * Subclasses add 5 metrics for given time period (series order must be
	 * consistent).
	 */
	protected abstract void addPoint(RegularTimePeriod p, MessageStatisticsTimeseries r);
}
