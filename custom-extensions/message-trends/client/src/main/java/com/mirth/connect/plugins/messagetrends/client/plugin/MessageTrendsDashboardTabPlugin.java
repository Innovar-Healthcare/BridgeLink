package com.mirth.connect.plugins.messagetrends.client.plugin;

import java.util.List;

import javax.swing.JComponent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.model.DashboardStatus;
import com.mirth.connect.plugins.DashboardTabPlugin;
import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsDashboardPanel;
import com.mirth.connect.plugins.messagetrends.client.panel.MessageTrendsDashboardPanel.SelectionBlockReason;

public class MessageTrendsDashboardTabPlugin extends DashboardTabPlugin {
	private final Logger logger = LogManager.getLogger(this.getClass());

	private MessageTrendsDashboardPanel panel;

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

		if (statuses == null || statuses.isEmpty()) {
			panel.blockSelection(SelectionBlockReason.NO_SELECTION);
			return;
		}

		if (statuses.size() != 1) {
			panel.blockSelection(SelectionBlockReason.MULTI_SELECTED);
			return;
		}

		DashboardStatus s = statuses.get(0);
		String channelId = s.getChannelId();

		switch (s.getStatusType()) {
		case CHANNEL:
			panel.unblockSelection();
			panel.setSelection(channelId, s.getName(), null, null);
			break;

		case SOURCE_CONNECTOR:
		case DESTINATION_CONNECTOR:
			String channelName = PlatformUI.MIRTH_FRAME.channelPanel.getCachedChannelIdsAndNames().get(channelId);

			panel.unblockSelection();
			panel.setSelection(channelId, channelName, s.getMetaDataId(), s.getName());
			break;

		default:
			panel.blockSelection(SelectionBlockReason.NO_SELECTION);
			break;
		}
	}

	@Override
	public JComponent getTabComponent() {
		return panel;
	}

	@Override
	public String getPluginPointName() {
		return "Message Trends";
	}

	@Override
	public void start() {
	}

	@Override
	public void stop() {
	}

	@Override
	public void reset() {
	}

}
