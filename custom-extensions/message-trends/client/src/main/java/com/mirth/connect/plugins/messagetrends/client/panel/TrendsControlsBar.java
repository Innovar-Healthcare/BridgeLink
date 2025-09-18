package com.mirth.connect.plugins.messagetrends.client.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsDashboardPanel.View;
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
	private static final Color PAUSED_BG = new java.awt.Color(0xEEEEEE); // grey 200

	// Left group (time navigation)
	private final JComboBox<String> intervalCombo;
	private final JSpinner nSpinner;
	private final JButton prevButton; // ◀
	private final JButton nextButton; // ▶

	// Right group (display context)
	private final JComboBox<View> viewCombo;
	private final JComboBox<String> chartCombo;
	private final JButton refreshButton;
	private final JLabel liveStatusLabel; // "● Live" / "○ Paused"

	public TrendsControlsBar() {
		super(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		setOpaque(false);

		// LEFT PANEL ---------------------------------------------------------
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		left.setOpaque(false);

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

		nSpinner = new JSpinner(new SpinnerNumberModel(30, 10, 60, 1));
		((JSpinner.DefaultEditor) nSpinner.getEditor()).getTextField().setColumns(3);
		left.add(labelled(nSpinner, "Buckets: "));

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

	public void setControlsEnabled(boolean enabled) {
		intervalCombo.setEnabled(enabled);
		nSpinner.setEnabled(enabled);
		prevButton.setEnabled(enabled);
		nextButton.setEnabled(enabled);
		viewCombo.setEnabled(enabled);
		chartCombo.setEnabled(enabled);
		refreshButton.setEnabled(enabled);
	}

	public JComboBox<String> getIntervalCombo() {
		return intervalCombo;
	}

	public JSpinner getNSpinner() {
		return nSpinner;
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
