/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.api.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.client.core.api.servlets.AlertServletInterface;
import com.mirth.connect.model.ChannelHeader;
import com.mirth.connect.model.ChannelSummary;
import com.mirth.connect.model.alert.AlertActionGroup;
import com.mirth.connect.model.alert.AlertInfo;
import com.mirth.connect.model.alert.AlertModel;
import com.mirth.connect.model.alert.AlertStatus;
import com.mirth.connect.model.alert.DefaultTrigger;
import com.mirth.connect.server.alert.action.ChannelProtocol;
import com.mirth.connect.server.api.ServletTestBase;

@SuppressWarnings("unchecked")
public class AlertServletTest extends ServletTestBase {

    private static final String ALERT_ID1 = "alert1";
    private static final String ALERT_ID2 = "alert2";
    private static final String NONEXISTENT_ALERT_ID = "nonexistent";

    @BeforeClass
    public static void setup() throws Exception {
        ServletTestBase.setup();

        // Set up alert controller mocks
        AlertModel alert1 = new AlertModel(new DefaultTrigger(), new AlertActionGroup());
        alert1.setId(ALERT_ID1);
        alert1.setName("Test Alert 1");
        alert1.setEnabled(false);

        AlertModel alert2 = new AlertModel(new DefaultTrigger(), new AlertActionGroup());
        alert2.setId(ALERT_ID2);
        alert2.setName("Test Alert 2");
        alert2.setEnabled(true);

        List<AlertModel> allAlerts = new ArrayList<>();
        allAlerts.add(alert1);
        allAlerts.add(alert2);

        when(alertController.getAlert(ALERT_ID1)).thenReturn(alert1);
        when(alertController.getAlert(ALERT_ID2)).thenReturn(alert2);
        when(alertController.getAlert(NONEXISTENT_ALERT_ID)).thenReturn(null);
        when(alertController.getAlerts()).thenReturn(allAlerts);
        doNothing().when(alertController).updateAlert(any(AlertModel.class));
        doNothing().when(alertController).removeAlert(anyString());

        List<AlertStatus> statusList = new ArrayList<>();
        statusList.add(new AlertStatus());
        statusList.add(new AlertStatus());
        when(alertController.getAlertStatusList()).thenReturn(statusList);
    }

    // ========== Existing tests ==========

    @Test
    public void getAlertInfo() throws Throwable {
        Map<String, ChannelHeader> cachedChannels = new HashMap<String, ChannelHeader>();
        cachedChannels.put(CHANNEL_ID1, new ChannelHeader(1, Calendar.getInstance(), false));
        cachedChannels.put(CHANNEL_ID2, new ChannelHeader(1, Calendar.getInstance(), false));
        cachedChannels.put(DISALLOWED_CHANNEL_ID, new ChannelHeader(1, Calendar.getInstance(), false));

        AlertInfo info = (AlertInfo) ih.invoke(new AlertServlet(request, sc, controllerFactory), AlertServletInterface.class.getMethod("getAlertInfo", String.class, Map.class), new Object[] {
                "test", cachedChannels });
        assertAlertInfo(info);

        info = (AlertInfo) ih.invoke(new AlertServlet(request, sc, controllerFactory), AlertServletInterface.class.getMethod("getAlertInfo", Map.class), new Object[] {
                cachedChannels });
        assertAlertInfo(info);
    }

    private void assertAlertInfo(AlertInfo info) {
        boolean foundChannel1 = false;
        boolean foundChannel2 = false;
        for (ChannelSummary channelSummary : info.getChangedChannels()) {
            assertNotSame(DISALLOWED_CHANNEL_ID, channelSummary.getChannelId());

            if (channelSummary.getChannelId().equals(CHANNEL_ID1)) {
                foundChannel1 = true;
            } else if (channelSummary.getChannelId().equals(CHANNEL_ID2)) {
                foundChannel2 = true;
            }
        }
        assertTrue(foundChannel1);
        assertTrue(foundChannel2);

        Map<String, String> channelOptions = info.getProtocolOptions().get(ChannelProtocol.NAME);
        assertTrue(channelOptions.containsKey(CHANNEL_ID1));
        assertTrue(channelOptions.containsKey(CHANNEL_ID2));
        assertFalse(channelOptions.containsKey(DISALLOWED_CHANNEL_ID));
    }

    @Test
    public void getAlertProtocolOptions() throws Throwable {
        Map<String, Map<String, String>> options = (Map<String, Map<String, String>>) ih.invoke(new AlertServlet(request, sc, controllerFactory), AlertServletInterface.class.getMethod("getAlertProtocolOptions"), new Object[] {});

        Map<String, String> channelOptions = options.get(ChannelProtocol.NAME);
        assertTrue(channelOptions.containsKey(CHANNEL_ID1));
        assertTrue(channelOptions.containsKey(CHANNEL_ID2));
        assertFalse(channelOptions.containsKey(DISALLOWED_CHANNEL_ID));
    }

    // ========== New tests: createAlert ==========

    @Test
    public void testCreateAlert() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        AlertModel newAlert = new AlertModel(new DefaultTrigger(), new AlertActionGroup());
        newAlert.setName("New Alert");
        // Should not throw
        servlet.createAlert(newAlert);
        verify(alertController).updateAlert(newAlert);
    }

    @Test(expected = MirthApiException.class)
    public void testCreateAlertControllerException() throws Throwable {
        doThrow(new ControllerException("create error")).when(alertController).updateAlert(any(AlertModel.class));
        try {
            AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
            servlet.createAlert(new AlertModel(new DefaultTrigger(), new AlertActionGroup()));
        } finally {
            // Restore normal behavior
            doNothing().when(alertController).updateAlert(any(AlertModel.class));
        }
    }

    // ========== New tests: getAlert ==========

    @Test
    public void testGetAlertFound() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        AlertModel alert = servlet.getAlert(ALERT_ID1);
        assertNotNull(alert);
        assertEquals(ALERT_ID1, alert.getId());
        assertEquals("Test Alert 1", alert.getName());
    }

    @Test(expected = MirthApiException.class)
    public void testGetAlertNotFound() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        servlet.getAlert(NONEXISTENT_ALERT_ID);
    }

    // ========== New tests: getAlerts ==========

    @Test
    public void testGetAlertsAllWhenIdsEmpty() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        List<AlertModel> alerts = servlet.getAlerts(null);
        assertNotNull(alerts);
        assertEquals(2, alerts.size());
    }

    @Test
    public void testGetAlertsAllWhenIdsEmptySet() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        List<AlertModel> alerts = servlet.getAlerts(new HashSet<>());
        assertNotNull(alerts);
        assertEquals(2, alerts.size());
    }

    @Test
    public void testGetAlertsFilteredByIds() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        Set<String> ids = new HashSet<>();
        ids.add(ALERT_ID1);
        List<AlertModel> alerts = servlet.getAlerts(ids);
        assertNotNull(alerts);
        assertEquals(1, alerts.size());
        assertEquals(ALERT_ID1, alerts.get(0).getId());
    }

    @Test
    public void testGetAlertsFilteredByNonexistentId() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        Set<String> ids = new HashSet<>();
        ids.add(NONEXISTENT_ALERT_ID);
        List<AlertModel> alerts = servlet.getAlerts(ids);
        assertNotNull(alerts);
        assertEquals(0, alerts.size());
    }

    // ========== New tests: getAlertsPost ==========

    @Test
    public void testGetAlertsPostDelegatesToGetAlerts() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        Set<String> ids = new HashSet<>();
        ids.add(ALERT_ID2);
        List<AlertModel> alerts = servlet.getAlertsPost(ids);
        assertNotNull(alerts);
        assertEquals(1, alerts.size());
        assertEquals(ALERT_ID2, alerts.get(0).getId());
    }

    // ========== New tests: getAlertStatusList ==========

    @Test
    public void testGetAlertStatusList() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        List<AlertStatus> statusList = servlet.getAlertStatusList();
        assertNotNull(statusList);
        assertEquals(2, statusList.size());
    }

    // ========== New tests: updateAlert ==========

    @Test
    public void testUpdateAlert() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        AlertModel updateAlert = new AlertModel(new DefaultTrigger(), new AlertActionGroup());
        updateAlert.setId(ALERT_ID1);
        updateAlert.setName("Updated Alert");
        servlet.updateAlert(ALERT_ID1, updateAlert);
        verify(alertController).updateAlert(updateAlert);
    }

    // ========== New tests: enableAlert ==========

    @Test
    public void testEnableAlert() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        servlet.enableAlert(ALERT_ID1);
        // The alert should have been set to enabled and updated
        verify(alertController).getAlert(ALERT_ID1);
    }

    @Test(expected = MirthApiException.class)
    public void testEnableAlertNotFound() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        servlet.enableAlert(NONEXISTENT_ALERT_ID);
    }

    // ========== New tests: disableAlert ==========

    @Test
    public void testDisableAlert() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        servlet.disableAlert(ALERT_ID2);
        verify(alertController).getAlert(ALERT_ID2);
    }

    @Test(expected = MirthApiException.class)
    public void testDisableAlertNotFound() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        servlet.disableAlert(NONEXISTENT_ALERT_ID);
    }

    // ========== New tests: removeAlert ==========

    @Test
    public void testRemoveAlert() throws Throwable {
        AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
        servlet.removeAlert(ALERT_ID1);
        verify(alertController).removeAlert(ALERT_ID1);
    }

    @Test(expected = MirthApiException.class)
    public void testRemoveAlertControllerException() throws Throwable {
        doThrow(new ControllerException("remove error")).when(alertController).removeAlert(anyString());
        try {
            AlertServlet servlet = new AlertServlet(request, sc, controllerFactory);
            servlet.removeAlert("someId");
        } finally {
            doNothing().when(alertController).removeAlert(anyString());
        }
    }
}
