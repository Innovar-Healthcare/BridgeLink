package com.mirth.connect.plugins.messagetrends.client.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JComponent;

import com.mirth.connect.model.DashboardStatus;
import com.mirth.connect.plugins.DashboardTabPlugin;
import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsDashboardPanel;

public class MessageTrendsDashboardTabPlugin extends DashboardTabPlugin {
	private MessageTrendsDashboardPanel panel;
	private static final String NO_SERVER_SELECTED = "No Server Selected";
	private static final String NO_CHANNEL_SELECTED = "No Channel Selected";

	public MessageTrendsDashboardTabPlugin(String name) {
		super(name);

		panel = new MessageTrendsDashboardPanel(this);
	}

	// used for setting actions to be called for updating when there is no status
	// selected
	@Override
	public void update() {
		// call the other function with no channel selected (null).
		update(null);
	}

	// used for setting actions to be called for updating when there is a status
	// selected
	@Override
	public void update(List<DashboardStatus> statuses) {
		Map<String, List<Integer>> selectedConnectorMap = null;

		if (statuses != null) {
			selectedConnectorMap = new ConcurrentHashMap<String, List<Integer>>();

			for (DashboardStatus status : statuses) {
				String channelId = status.getChannelId();
				Integer metaDataId = status.getMetaDataId();

				List<Integer> selectedConnectors = selectedConnectorMap.get(channelId);

				if (selectedConnectors == null) {
					selectedConnectors = new ArrayList<Integer>();
					selectedConnectorMap.put(channelId, selectedConnectors);
				}

				selectedConnectors.add(metaDataId);
			}
		}

		String selectedChannelId = (statuses != null && statuses.size() == 1) ? statuses.get(0).getChannelId() : NO_CHANNEL_SELECTED;
		String selectedChannelName = (statuses != null && statuses.size() == 1) ? statuses.get(0).getName() : NO_CHANNEL_SELECTED;

		panel.setChannelId(selectedChannelId, selectedChannelName);

//		dcsp.setSelectedConnectors(selectedConnectorMap);
//		dcsp.updateTable(getChannelLog(statuses));
//		dcsp.adjustPauseResumeButton(selectedChannelId);
	}

	@Override
	public JComponent getTabComponent() {
		return panel;
	}

	@Override
	public String getPluginPointName() {
		return "Message Trends";
	}

//	@Override
//	public ImageIcon getTabIcon() {
//		// Return an appropriate icon or null for default
//		return new ImageIcon(getClass().getResource("/com/bridgelink/plugins/messagetrends/client/images/chart_icon.png"));
//	}
//
//	@Override
//	public String getTabTitle() {
//		return "Message Trends";
//	}
//
//	@Override
//	public MessageTrendsPanel getDashboardTabComponent() {
//		return panel;
//	}

	@Override
	public void start() {
		// Optional: perform any startup operations
	}

	@Override
	public void stop() {
		// Clean up resources
//		if (panel != null) {
//			panel.cleanup();
//		}
	}

	@Override
	public void reset() {
		// Reset the panel state
//        if (panel != null) {
//            panel.reset();
//        }
	}

}
