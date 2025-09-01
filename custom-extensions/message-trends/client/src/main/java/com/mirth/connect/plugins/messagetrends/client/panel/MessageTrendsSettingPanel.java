package com.mirth.connect.plugins.messagetrends.client.panel;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;

import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.components.MirthRadioButton;
import com.mirth.connect.plugins.messagetrends.client.plugin.MessageTrendsClientPlugin;
import com.mirth.connect.plugins.messagetrends.shared.model.MessageTrendsProperties;

import net.miginfocom.swing.MigLayout;

public class MessageTrendsSettingPanel extends AbstractSettingsPanel {
	private MessageTrendsClientPlugin plugin;

	private JPanel enabledPanel;
	private JLabel enabledLabel;
	private MirthRadioButton yesEnabledRadio;
	private MirthRadioButton noEnabledRadio;
	private ButtonGroup enabledButtonGroup;

	private Frame parent;

	private MessageTrendsProperties messageTrendsProperties;

	public MessageTrendsSettingPanel(String tabName, MessageTrendsClientPlugin plugin) {
		super(tabName);

		this.plugin = plugin;
		this.parent = PlatformUI.MIRTH_FRAME;

		this.messageTrendsProperties = MessageTrendsProperties.fromProperties(new Properties());

		initComponents();

		initLayout();
	}

	private void initComponents() {
		setBackground(UIConstants.BACKGROUND_COLOR);

		enabledPanel = new JPanel();
		enabledPanel.setBackground(UIConstants.BACKGROUND_COLOR);
		enabledPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)), "Enable", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", 1, 11)));

		enabledLabel = new JLabel("Enable:");
		yesEnabledRadio = new MirthRadioButton("Yes");
		yesEnabledRadio.setFocusable(false);
		yesEnabledRadio.setBackground(Color.white);
		yesEnabledRadio.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				enabledActionPerformed();
			}
		});

		noEnabledRadio = new MirthRadioButton("No");
		noEnabledRadio.setFocusable(false);
		noEnabledRadio.setBackground(Color.white);
		noEnabledRadio.setSelected(true);
		noEnabledRadio.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				enabledActionPerformed();
			}
		});

		enabledButtonGroup = new ButtonGroup();
		enabledButtonGroup.add(yesEnabledRadio);
		enabledButtonGroup.add(noEnabledRadio);
	}

	private void initLayout() {
		setLayout(new MigLayout("hidemode 3, novisualpadding, insets 12", "[grow]"));

		// enabledPanel: Right-aligned label with 150-pixel first column
		enabledPanel.setLayout(new MigLayout("hidemode 3, novisualpadding, insets 0", "[120,right][grow]"));
		enabledPanel.add(enabledLabel);
		enabledPanel.add(yesEnabledRadio, "split, gapleft 12");
		enabledPanel.add(noEnabledRadio, "wrap");

		add(enabledPanel, "grow, sx, wrap");
	}

	private void enabledActionPerformed() {
//		visibleFields(yesEnabledRadio.isSelected());
	}

	public void setProperties(Properties properties) {
		messageTrendsProperties = MessageTrendsProperties.fromProperties(properties);

		yesEnabledRadio.setSelected(messageTrendsProperties.isEnabled());
		noEnabledRadio.setSelected(!messageTrendsProperties.isEnabled());
	}

	public Properties getProperties() {
		messageTrendsProperties.setEnabled(yesEnabledRadio.isSelected());

		return messageTrendsProperties.toProperties();
	}

	@Override
	public void doRefresh() {
		if (PlatformUI.MIRTH_FRAME.alertRefresh()) {
			return;
		}

		resetInvalidSettings();

		final String workingId = getFrame().startWorking("Loading " + getTabName() + " properties...");

		final Properties serverProperties = new Properties();

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

			public Void doInBackground() {
				try {
					Properties propertiesFromServer = plugin.getPropertiesFromServer();
					if (propertiesFromServer != null) {
						serverProperties.putAll(propertiesFromServer);
					}
				} catch (Exception e) {
					getFrame().alertThrowable(getFrame(), e);
				}
				return null;
			}

			@Override
			public void done() {
				setProperties(serverProperties);
				getFrame().stopWorking(workingId);
			}
		};

		worker.execute();
	}

	@Override
	public boolean doSave() {
		final String workingId = getFrame().startWorking("Saving " + getTabName() + " properties...");

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

			public Void doInBackground() {
				try {
					plugin.setPropertiesToServer(getProperties());
				} catch (Exception e) {
					getFrame().alertThrowable(getFrame(), e);
				}
				return null;
			}

			@Override
			public void done() {
				setSaveEnabled(false);
				getFrame().stopWorking(workingId);
			}
		};

		worker.execute();

		return true;
	}

	public void resetInvalidSettings() {

	}

	protected void showError(String err) {
		PlatformUI.MIRTH_FRAME.alertError(this, err);
	}
}
