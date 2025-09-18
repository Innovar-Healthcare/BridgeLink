package com.mirth.connect.plugins.messagetrends.client.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsDashboardPanel.View;
import com.mirth.connect.plugins.messagetrends.shared.model.TimeRangePresets;
import com.mirth.connect.plugins.messagetrends.shared.util.Intervals;

/**
 * Single-row controls bar split into Left | Right. Left: Interval ▾ · N [10–60]
 * · ◀ · ▶ Right: View ▾ · Chart ▾ · [Refresh] · ● Live / ○ Paused (label only)
 *
 * Buttons are added but NOT wired to any handlers yet. Code style: English
 * identifiers; visuals per spec.
 */
public class TrendsControlsBar extends JPanel {
	// Colors for Live/Paused badge
	private static final Color LIVE_FG = new java.awt.Color(0x1B5E20); // green 900
	private static final Color LIVE_BG = new java.awt.Color(0xE8F5E9); // green 50
	private static final Color PAUSED_FG = new java.awt.Color(0x424242); // grey 800
	private static final Color PAUSED_BG = new java.awt.Color(0xFFCCCC); // grey 200

	private static final int MAX_POINTS = 90;
	private static final int MIN_POINTS = 7;

	// Cache: preset -> allowed intervals (sorted asc by minutes)
	private final Map<String, List<String>> allowedByPreset = new LinkedHashMap<>();

	// Presets visible in the timeRange combo (filtered by having at least one valid
	// interval)
	private List<String> visiblePresets = Collections.emptyList();

	// Left group (time navigation)
	private final JComboBox<String> timeRangeCombo;
	private final JComboBox<String> intervalCombo;
	private final JButton prevButton; // ◀
	private final JButton nextButton; // ▶

	// Right group (display context)
	private final JComboBox<View> viewCombo;
	private final JComboBox<String> chartCombo;
	private final JButton refreshButton;
	private final JLabel liveStatusLabel; // "Live" / "Paused"

	public TrendsControlsBar() {
		super(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		setOpaque(false);

		// LEFT PANEL ---------------------------------------------------------
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		left.setOpaque(false);

		// Time Range (first control on the left)
		timeRangeCombo = new JComboBox<>();
		timeRangeCombo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				String presetId = (String) value;
				String label = TimeRangePresets.PRESET_TO_LABEL.getOrDefault(presetId, presetId);
				return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
			}
		});
		timeRangeCombo.setSelectedItem("last_1h"); // default
		left.add(labelled(timeRangeCombo, "Time Range: "));

		intervalCombo = new JComboBox<>(Intervals.canonicalCodes().toArray(new String[0]));
		intervalCombo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				String code = String.valueOf(value);
				String label = humanLabelForInterval(code);
				return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
			}
		});
		if (intervalCombo.getItemCount() > 0) {
			intervalCombo.setSelectedIndex(0);
		}
		left.add(labelled(intervalCombo, "Interval: "));

		prevButton = new JButton();
		prevButton.setIcon(new ImageIcon(Frame.class.getResource("images/book_previous.png")));
		prevButton.setToolTipText("Previous (shift left)");
		left.add(prevButton);

		nextButton = new JButton("");
		nextButton.setIcon(new ImageIcon(Frame.class.getResource("images/book_next.png")));
		nextButton.setToolTipText("Next (shift right)");
		left.add(nextButton);

		// RIGHT PANEL --------------------------------------------------------
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		right.setOpaque(false);

		viewCombo = new JComboBox<>(View.values());
		right.add(labelled(viewCombo, "View: "));

		chartCombo = new JComboBox<>(new String[] { "Line", "Stacked" });
		right.add(labelled(chartCombo, "Chart: "));

		refreshButton = new JButton("Refresh");
		refreshButton.setToolTipText("Refresh current window");
		right.add(refreshButton);

		liveStatusLabel = new JLabel("Live"); // Visual only; updated elsewhere
		liveStatusLabel.setOpaque(true);
		liveStatusLabel.setForeground(LIVE_FG);
		liveStatusLabel.setBackground(LIVE_BG);
		liveStatusLabel.setBorder(new javax.swing.border.CompoundBorder(new LineBorder(LIVE_FG, 1, true), new EmptyBorder(2, 8, 2, 8)));
		liveStatusLabel.setFont(liveStatusLabel.getFont().deriveFont(Font.BOLD));
		right.add(liveStatusLabel);

		add(left, BorderLayout.WEST);
		add(right, BorderLayout.EAST);

		rebuildIntervalCache();
		timeRangeCombo.setModel(new DefaultComboBoxModel<>(visiblePresets.toArray(new String[0])));

		// Wire up
		updateIntervalsForSelectedRange(); // initial
		timeRangeCombo.addActionListener(e -> updateIntervalsForSelectedRange());
	}

	/** Wrap a component with a small label: "Label ▾" style */
	private JPanel labelled(JComponent comp, String label) {
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setOpaque(false);
		JLabel l = new JLabel(label);
		l.setLabelFor(comp);
		p.add(l);
		p.add(comp);
		return p;
	}

	public void setNextEnabled(boolean enabled) {
		nextButton.setEnabled(enabled);
	}

	public void setControlsEnabled(boolean enabled) {
		timeRangeCombo.setEnabled(enabled);
		intervalCombo.setEnabled(enabled);
		prevButton.setEnabled(enabled);
		nextButton.setEnabled(enabled);
		viewCombo.setEnabled(enabled);
		chartCombo.setEnabled(enabled);
		refreshButton.setEnabled(enabled);
	}

	public JComboBox<String> getTimeRangeCombo() {
		return timeRangeCombo;
	}

	public JComboBox<String> getIntervalCombo() {
		return intervalCombo;
	}

	public JButton getPrevButton() {
		return prevButton;
	}

	public JButton getNextButton() {
		return nextButton;
	}

	public JComboBox<View> getViewCombo() {
		return viewCombo;
	}

	public JComboBox<String> getChartCombo() {
		return chartCombo;
	}

	public JButton getRefreshButton() {
		return refreshButton;
	}

	public JLabel getLiveStatusLabel() {
		return liveStatusLabel;
	}

	// ------- Helper to toggle status text externally -----------------------
	public void setLive(boolean live) {
		liveStatusLabel.setText(live ? "Live" : "Paused");
		liveStatusLabel.setOpaque(true);
		liveStatusLabel.setForeground(live ? LIVE_FG : PAUSED_FG);
		liveStatusLabel.setBackground(live ? LIVE_BG : PAUSED_BG);
		// pill-like border (rounded)
		liveStatusLabel.setBorder(new javax.swing.border.CompoundBorder(new LineBorder(live ? LIVE_FG : PAUSED_FG, 1, true), new EmptyBorder(2, 8, 2, 8)));
		// font weight
		liveStatusLabel.setFont(liveStatusLabel.getFont().deriveFont(live ? Font.BOLD : Font.PLAIN));
		liveStatusLabel.setToolTipText(live ? "Following real-time data" : "Paused view; use Next or Refresh to catch up");
	}

	// ===== Range → Interval filtering & default pick ========================
	// === CACHE BUILDERS (ADD) ===

	private void updateIntervalsForSelectedRange() {
		final String preset = (String) timeRangeCombo.getSelectedItem();
		if (preset == null) {
			intervalCombo.setModel(new DefaultComboBoxModel<>());
			return;
		}

		final List<String> allowed = allowedByPreset.getOrDefault(preset, Collections.emptyList());
		if (allowed.isEmpty()) {
			// preset has no valid intervals under current MIN/MAX -> clear
			intervalCombo.setModel(new DefaultComboBoxModel<>());
			return;
		}

		final String prev = (String) intervalCombo.getSelectedItem();
		intervalCombo.setModel(new DefaultComboBoxModel<>(allowed.toArray(new String[0])));

		// keep previous if still valid
		if (prev != null && allowed.contains(prev)) {
			final int rangeMinutes = presetToMinutes(preset);
			final int pts = pointsForRange(rangeMinutes, Intervals.minutesOf(prev));
			if (pts <= MAX_POINTS && pts >= MIN_POINTS) {
				intervalCombo.setSelectedItem(prev);
				return;
			}
		}

		// otherwise pick first
		intervalCombo.setSelectedIndex(0);
	}

	private void rebuildIntervalCache() {
		allowedByPreset.clear();

		final List<String> allPresets = TimeRangePresets.PRESETS; // use your existing source
		final List<String> visible = new ArrayList<>(allPresets.size());

		for (String preset : allPresets) {
			final List<String> allowed = computeAllowedIntervalsForPreset(preset);
			if (!allowed.isEmpty()) {
				allowedByPreset.put(preset, allowed);
				visible.add(preset);
			}
		}
		visiblePresets = Collections.unmodifiableList(visible);
	}

	private List<String> computeAllowedIntervalsForPreset(String preset) {
		final int rangeMinutes = presetToMinutes(preset);
		final List<String> allowed = new ArrayList<>();

		for (String code : Intervals.canonicalCodes()) {
			final int bucketMin = Intervals.minutesOf(code);
			final int points = pointsForRange(rangeMinutes, bucketMin);
			if (points <= MAX_POINTS && points >= MIN_POINTS) {
				allowed.add(code);
			}
		}
		allowed.sort(Comparator.comparingInt(Intervals::minutesOf));
		return allowed;
	}

	private static int presetToMinutes(String preset) {
		Duration d = TimeRangePresets.toDuration(preset);
		return d == null ? 60 : (int) d.toMinutes();
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

	private static int pointsForRange(int rangeMinutes, int bucketMin) {
		return (int) Math.ceil(rangeMinutes / (double) bucketMin);
	}
}
