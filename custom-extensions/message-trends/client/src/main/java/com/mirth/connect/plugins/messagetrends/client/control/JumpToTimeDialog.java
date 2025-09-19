/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.client.control;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.WindowConstants;
import javax.swing.text.DateFormatter;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.MirthDialog;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthDatePicker;
import com.mirth.connect.client.ui.components.MirthTimePicker;

import net.miginfocom.swing.MigLayout;

public class JumpToTimeDialog extends MirthDialog {

	private final Frame parent;

	private MirthDatePicker datePicker;
	private MirthTimePicker timePicker;

	private JButton okButton;
	private JButton cancelButton;

	private Long selectedEndMillis = null;

	public JumpToTimeDialog(Frame parent) {
		super(parent, true);

		this.parent = parent;

		initComponents();
		initLayout();

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setTitle("Jump to time");
		pack();
		setLocationRelativeTo(parent);
		setVisible(true); // modal
	}

	private void initComponents() {
		setBackground(UIConstants.BACKGROUND_COLOR);
		getContentPane().setBackground(getBackground());

		datePicker = new MirthDatePicker(); // default: today
		timePicker = new MirthTimePicker(); // default: current time (HH:mm)

		okButton = new JButton("OK");
		okButton.addActionListener(evt -> save());

		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(evt -> close());
	}

	private void initLayout() {
		setLayout(new MigLayout("insets 12, novisualpadding, hidemode 3, fill"));

		JPanel form = new JPanel(new MigLayout("insets 0 10 10 10, novisualpadding, hidemode 0, align center, fill", "5[right][fill][80]"));
		form.setBackground(UIConstants.BACKGROUND_COLOR);
		form.setBorder(BorderFactory.createEmptyBorder());
		form.setMinimumSize(getMinimumSize());
		form.setMaximumSize(getMaximumSize());

		form.add(new JLabel("Date/Time:"), "right");
		form.add(datePicker, "w 150!");
		form.add(timePicker, "w 80!, gapleft 8, wrap");

		add(form, "growx");
		add(new JSeparator(), "newline, sx, growx");

		add(okButton, "newline, sx, right, split 2");
		add(cancelButton);
	}

	private void save() {
		if (!validateTime()) {
			return;
		}

		try {
			Calendar dateCal = buildDateTimeCalendar();
			selectedEndMillis = dateCal.getTimeInMillis();
		} catch (Exception ex) {
			selectedEndMillis = null;
			showError("Invalid date/time. Error: " + ex.getMessage());
			return;
		}

		close();
	}

	private void close() {
		dispose();
	}

	private boolean validateTime() {
		try {
			Calendar picked = buildDateTimeCalendar();
			long pickedMs = picked.getTimeInMillis();
			long nowMs = System.currentTimeMillis();

			if (pickedMs > nowMs) {
				showError("Selected time cannot be in the future.");
				return false;
			}
			return true;
		} catch (Exception e) {
			showError("Invalid date/time. Error: " + e.getMessage());
			return false;
		}
	}

	private Calendar buildDateTimeCalendar() throws ParseException {
		Date date = datePicker.getDate();
		String time = timePicker.getDate();

		if (date == null) {
			throw new ParseException("Date is required", 0);
		}

		Calendar cal = Calendar.getInstance();
		cal.setTime(date);

		if (timePicker.isEnabled()) {
			if (time == null || time.trim().isEmpty()) {
				throw new ParseException("Time is required", 0);
			}
			DateFormatter timeFormatter = new DateFormatter(new SimpleDateFormat("hh:mm aa"));
			Calendar timeCal = Calendar.getInstance();
			timeCal.setTime((Date) timeFormatter.stringToValue(time));

			cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
			cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
			cal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND));
		} else {
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
		}

		cal.set(Calendar.MILLISECOND, 0);
		return cal;
	}

	private void showError(String err) {
		PlatformUI.MIRTH_FRAME.alertError(this, err);
	}

	public Long getSelectedEndMillis() {
		return selectedEndMillis;
	}

	public static Long showDialog(Frame parent) {
		JumpToTimeDialog d = new JumpToTimeDialog(parent);
		return d.getSelectedEndMillis();
	}
}
