package com.mirth.connect.plugins.dynamiclookup.client.panel;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthDatePicker;
import com.mirth.connect.client.ui.components.MirthTimePicker;

import com.mirth.connect.model.User;

import com.mirth.connect.plugins.dynamiclookup.client.exception.LookupApiClientException;
import com.mirth.connect.plugins.dynamiclookup.client.model.LookupAuditTableModel;
import com.mirth.connect.plugins.dynamiclookup.client.service.LookupServiceClient;
import com.mirth.connect.plugins.dynamiclookup.shared.dto.response.GroupAuditEntriesResponse;
import com.mirth.connect.plugins.dynamiclookup.shared.model.HistoryFilterState;
import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

import net.miginfocom.swing.MigLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.JOptionPane;
import javax.swing.text.DateFormatter;
import javax.swing.SwingConstants;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Date;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class HistoryPanel extends JPanel {
    private final Logger logger = LogManager.getLogger(this.getClass());

    private final Frame parent = PlatformUI.MIRTH_FRAME;

    private JPanel noGroupSelectedPanel;
    private JPanel contentPanel;

    private JTable auditTable;
    private LookupAuditTableModel auditTableModel;
    private JTextField keyFilterField;
    private JComboBox<String> actionFilterComboBox;
    private JComboBox<String> userFilterComboBox;
    private MirthDatePicker startDatePicker;
    private MirthTimePicker startTimePicker;
    private MirthDatePicker endDatePicker;
    private MirthTimePicker endTimePicker;

    private JButton searchButton;
    private JButton clearButton;

    private JButton prevPageButton;
    private JButton nextPageButton;
    private JButton goToPageButton;
    private JLabel pageInfoLabel;
    private JComboBox<Integer> pageSizeComboBox;

    private LookupGroup selectedGroup;
    private Map<Integer, String> userMapById = new LinkedHashMap<>(); //

    private int currentPage = 1;
    private int pageSize = 25;
    private int totalCount = 0;

    public HistoryPanel() {
        initComponents();
        initLayout();

        updateHistory(null);
    }

    private void initComponents() {
        setBackground(UIConstants.BACKGROUND_COLOR);

        // No group selected panel
        noGroupSelectedPanel = new NoGroupSelectedPanel();

        // Content panel
        // Filter Group
        startDatePicker = new MirthDatePicker();
        startTimePicker = new MirthTimePicker();
        startTimePicker.setSaveEnabled(false);
        startDatePicker.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent arg0) {
                startTimePicker.setEnabled(startDatePicker.getDate() != null);
            }
        });

        endDatePicker = new MirthDatePicker();
        endTimePicker = new MirthTimePicker();
        endTimePicker.setSaveEnabled(false);
        endDatePicker.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent arg0) {
                endTimePicker.setEnabled(endDatePicker.getDate() != null);
            }
        });

        keyFilterField = new JTextField();
        keyFilterField.setToolTipText("Filter History by Key");
        keyFilterField.addActionListener(e -> {
            currentPage = 1;
            loadPage(currentPage);
        });

        actionFilterComboBox = new JComboBox<>(new String[]{UIConstants.ALL_OPTION, "CREATE", "UPDATE", "DELETE", "DELETE_ALL", "IMPORT", "CLEAR_ALL"});
        userFilterComboBox = new JComboBox<>(new String[]{}); // Replace with dynamic user list as needed

        // Search button
        searchButton = new JButton("", UIManager.getIcon("FileView.fileIcon"));
        searchButton.setIcon(UIConstants.ICON_FILE_PICKER);
        searchButton.setToolTipText("Search");
        searchButton.setIconTextGap(5);
        searchButton.addActionListener(e -> {
            currentPage = 1;
            loadPage(currentPage);
        });

        // Clear Filter then Search button
        clearButton = new JButton("");
        clearButton.setIcon(UIConstants.ICON_X);
        clearButton.setToolTipText("Clear");
        clearButton.addActionListener(e -> {
            // Clear all filter fields
            clearFilterFields();

            currentPage = 1;
            loadPage(currentPage);
        });

        // Audit/History Table
        auditTableModel = new LookupAuditTableModel();
        auditTable = new JTable(auditTableModel);
        auditTable.setRowHeight(26);

        prevPageButton = new JButton("Previous");
        prevPageButton.addActionListener(e -> goToPage(currentPage - 1));

        nextPageButton = new JButton("Next");
        nextPageButton.addActionListener(e -> goToPage(currentPage + 1));

        goToPageButton = new JButton("Go");
        goToPageButton.addActionListener(e -> showGoToPageDialog());

        pageInfoLabel = new JLabel("Page 1");

        pageSizeComboBox = new JComboBox<>(new Integer[]{10, 25, 50, 100, 200, 500, 1000});
        pageSizeComboBox.setSelectedItem(pageSize);
        pageSizeComboBox.addActionListener(e -> {
            int selected = (int) pageSizeComboBox.getSelectedItem();
            if (selected != pageSize) {
                currentPage = 1;
                pageSize = selected;
                loadPage(currentPage);
            }
        });
    }

    private void initLayout() {
        setLayout(new CardLayout());

        // Content panel with titled border
        contentPanel = new JPanel(new MigLayout("insets 8, wrap, fill"));
        contentPanel.setBackground(UIConstants.BACKGROUND_COLOR);

        JPanel topFilterPanel = new JPanel(new MigLayout("insets 0, fillx, wrap"));
        topFilterPanel.setBackground(UIConstants.BACKGROUND_COLOR);

        // Time Range Group
        JPanel timeGroup = new JPanel(new MigLayout("insets 0", "[60!][120!]10[100!]", ""));
        timeGroup.setBackground(UIConstants.BACKGROUND_COLOR);

        JLabel startLabel = new JLabel("Start Time:");
        startLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        timeGroup.add(startLabel, "gapright 5");
        timeGroup.add(startDatePicker, "w 120!, gapright 10");
        timeGroup.add(startTimePicker, "w 100!, wrap");

        JLabel endLabel = new JLabel("End Time:");
        endLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        timeGroup.add(endLabel, "gapright 5");
        timeGroup.add(endDatePicker, "w 120!, gapright 10");
        timeGroup.add(endTimePicker, "w 100!");
        topFilterPanel.add(timeGroup, "growx, wrap");

        // Filter Criteria Group
        JPanel filterGroup = new JPanel(new MigLayout("insets 0", "[60!][230!]20[][80!]20[][80!]", ""));
        filterGroup.setBackground(UIConstants.BACKGROUND_COLOR);
        filterGroup.add(new JLabel("Key:"), "align left, gapright 5");
        filterGroup.add(keyFilterField, "w 230!");
        filterGroup.add(new JLabel("Action:"), "align left");
        filterGroup.add(actionFilterComboBox);
        filterGroup.add(new JLabel("User:"), "align left");
        filterGroup.add(userFilterComboBox);
        topFilterPanel.add(filterGroup, "growx, wrap");

        // Action Buttons Group
        JPanel actionGroup = new JPanel(new MigLayout("insets 0", "[60!][]10[]", ""));
        actionGroup.setBackground(UIConstants.BACKGROUND_COLOR);
        actionGroup.add(new JLabel("Search:"), "align left, gapright 5");
        actionGroup.add(searchButton);
        actionGroup.add(clearButton);
        topFilterPanel.add(actionGroup, "growx, wrap");

        contentPanel.add(topFilterPanel, "growx, wrap");
        contentPanel.add(new JScrollPane(auditTable), "grow, push, wrap");

        JPanel paginationPanel = new JPanel(new BorderLayout());
        paginationPanel.setBackground(UIConstants.BACKGROUND_COLOR);

        JPanel leftBottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftBottomPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        leftBottomPanel.add(new JLabel("Page size:"));
        leftBottomPanel.add(pageSizeComboBox);

        JPanel rightBottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightBottomPanel.setBackground(UIConstants.BACKGROUND_COLOR);
        rightBottomPanel.add(prevPageButton);
        rightBottomPanel.add(pageInfoLabel);
        rightBottomPanel.add(nextPageButton);
        rightBottomPanel.add(goToPageButton);

        paginationPanel.add(leftBottomPanel, BorderLayout.WEST);
        paginationPanel.add(rightBottomPanel, BorderLayout.EAST);

        contentPanel.add(paginationPanel, "growx, wrap");

        add(contentPanel, "content");
        add(noGroupSelectedPanel, "noGroup");
    }

    public void updateHistory(LookupGroup selectedGroup) {
        boolean showContent = selectedGroup != null;

        contentPanel.setVisible(showContent);
        noGroupSelectedPanel.setVisible(!showContent);

        // Detect group change
        if (!Objects.equals(this.selectedGroup, selectedGroup)) {
            clearFilterFields(); // Clear filters only on group change
        }

        this.selectedGroup = selectedGroup;
        this.currentPage = 1;

        loadPage(currentPage);
    }

    private void loadPage(int page) {
        auditTableModel.clear();

        if (selectedGroup == null) {
            return;
        }

        int offset = (page - 1) * pageSize;

        new SwingWorker<GroupAuditEntriesResponse, Void>() {
            protected GroupAuditEntriesResponse doInBackground() throws Exception {
                HistoryFilterState filter = buildHistoryFilterStateFromUI();
                return LookupServiceClient.getInstance().searchAuditEntries(selectedGroup.getId(), offset, pageSize, filter);
            }

            protected void done() {
                try {
                    GroupAuditEntriesResponse response = get();
                    List<GroupAuditEntriesResponse.AuditEntryResponse> values = response.getEntries();
                    totalCount = response.getTotalEntries();
                    auditTableModel.setValues(values);
                    currentPage = page;
                    updatePaginationControls();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof LookupApiClientException) {
                        showError("Failed to load audit entries: " + cause.getMessage());
                    } else {
                        logger.error("Unexpected error while loading audit entries", e);
                        showError("An unexpected error occurred: " + cause.getMessage());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); // Restore interrupt status
                    showError("Operation was interrupted.");
                }
            }
        }.execute();
    }

    private void updatePaginationControls() {
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        pageInfoLabel.setText("Page " + currentPage + " of " + totalPages);
        prevPageButton.setEnabled(currentPage > 1);
        nextPageButton.setEnabled(currentPage < totalPages);
    }

    private void goToPage(int page) {
        if (page >= 1 && (page - 1) * pageSize < totalCount) {
            loadPage(page);
        }
    }

    private void showGoToPageDialog() {
        if (totalCount == 0) {
            return;
        }

        JComboBox<Integer> pageSelector = new JComboBox<>();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        for (int i = 1; i <= totalPages; i++) {
            pageSelector.addItem(i);
        }

        int result = JOptionPane.showConfirmDialog(this, pageSelector, "Select Page", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            Integer selectedPage = (Integer) pageSelector.getSelectedItem();
            if (selectedPage != null) {
                loadPage(selectedPage);
            }
        }
    }

    public void updateCachedUserMap() {
        try {
            // Retrieve updated user list from server
            parent.retrieveUsers(); // populates parent.users
        } catch (ClientException e) {
            parent.alertThrowable(this, e);
            return;
        }

        // Clear and repopulate the ID → name map
        userMapById.clear();
        userMapById.put(-1, UIConstants.ALL_OPTION); // default: "All Users"
        userMapById.put(0, "System");                // system user

        for (User user : parent.users) {
            userMapById.put(user.getId(), user.getUsername());
        }

        // Now update the combo box with usernames
        userFilterComboBox.removeAllItems();
        for (String username : userMapById.values()) {
            userFilterComboBox.addItem(username);
        }

        userFilterComboBox.setSelectedIndex(0);
    }

    private String getSelectedUserId() {
        String selectedName = (String) userFilterComboBox.getSelectedItem();

        if (selectedName == null || selectedName.equals(UIConstants.ALL_OPTION)) {
            return null;
        }

        for (Map.Entry<Integer, String> entry : userMapById.entrySet()) {
            if (entry.getValue().equals(selectedName)) {
                return String.valueOf(entry.getKey()); // convert int ID to String
            }
        }

        return null;
    }

    private void clearFilterFields() {
        keyFilterField.setText("");
        actionFilterComboBox.setSelectedIndex(0);
        userFilterComboBox.setSelectedIndex(0);
        startDatePicker.setDate(null);
        endDatePicker.setDate(null);
    }

    private HistoryFilterState buildHistoryFilterStateFromUI() {
        String keyValue = normalizeField(keyFilterField.getText());
        String action = normalizeComboBoxValue((String) actionFilterComboBox.getSelectedItem());
        String userId = getSelectedUserId();

        Date startDateTime = getCombinedDateTime(startDatePicker, startTimePicker);
        Date endDateTime = getCombinedDateTime(endDatePicker, endTimePicker);

        HistoryFilterState filter = new HistoryFilterState();
        filter.setKeyValue(keyValue);
        filter.setAction(action);
        filter.setUserId(userId);
        filter.setStartDate(startDateTime);
        filter.setEndDate(endDateTime);

        return filter;
    }

    private String normalizeField(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    private String normalizeComboBoxValue(String value) {
        return (value == null || UIConstants.ALL_OPTION.equals(value.trim())) ? null : value.trim();
    }

    private Date getCombinedDateTime(MirthDatePicker datePicker, MirthTimePicker timePicker) {
        try {
            Date date = datePicker.getDate();
            String time = timePicker.getDate(); // returns formatted time string like "10:15 AM"

            if (date != null && time != null) {
                DateFormatter timeFormatter = new DateFormatter(new SimpleDateFormat("hh:mm aa"));
                Date parsedTime = (Date) timeFormatter.stringToValue(time);

                Calendar dateCal = Calendar.getInstance();
                dateCal.setTime(date);

                Calendar timeCal = Calendar.getInstance();
                timeCal.setTime(parsedTime);

                Calendar combinedCal = Calendar.getInstance();
                combinedCal.setTime(date);
                if (timePicker.isEnabled()) {
                    combinedCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                    combinedCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                    combinedCal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND));
                    combinedCal.set(Calendar.MILLISECOND, 0);
                } else {
                    combinedCal.set(Calendar.HOUR_OF_DAY, 0);
                    combinedCal.set(Calendar.MINUTE, 0);
                    combinedCal.set(Calendar.SECOND, 0);
                    combinedCal.set(Calendar.MILLISECOND, 0);
                }

                return combinedCal.getTime();
            }
        } catch (ParseException e) {
            // Optionally log or ignore
        }

        return null;
    }

    private void showError(String err) {
        PlatformUI.MIRTH_FRAME.alertError(parent, err);
    }
}
