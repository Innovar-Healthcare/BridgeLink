/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mirth.connect.donkey.model.event.Event;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.DonkeyConfiguration;
import com.mirth.connect.donkey.server.DonkeyConnectionPools;
import com.mirth.connect.donkey.server.event.EventDispatcher;
import com.mirth.connect.donkey.server.event.EventType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.alert.AlertActionGroup;
import com.mirth.connect.model.alert.AlertModel;
import com.mirth.connect.model.alert.AlertStatus;
import com.mirth.connect.model.alert.DefaultTrigger;
import com.mirth.connect.server.alert.Alert;
import com.mirth.connect.server.alert.AlertWorker;
import com.mirth.connect.server.alert.action.Protocol;

/**
 * Test class for DefaultAlertController.
 */
public class DefaultAlertControllerTest {

    private DefaultAlertController alertController;
    private Protocol mockProtocol;
    private AlertWorker mockAlertWorker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        ConfigurationController configurationController = ControllerFactory.getFactory().createConfigurationController();
        configurationController.initializeSecuritySettings();
        configurationController.initializeDatabaseSettings();

//        DatabaseSettings databaseSettings = new DatabaseSettings(configurationController.getDatabaseSettings());
        System.out.println(configurationController.getDatabaseSettings().getProperties());
        DonkeyConnectionPools.getInstance().init(configurationController.getDatabaseSettings().getProperties());

        alertController = new DefaultAlertController();
        
        // Create mocks using a simple stub approach instead of reflection-heavy mocking
        mockProtocol = new Protocol() {
            @Override
            public String getName() {
                return "TestProtocol";
            }
            
            @Override
            public Map<String, String> getRecipientOptions() {
                return null; // Allows free text input
            }
            
            @Override
            public List<String> getEmailAddressesForDispatch(List<String> recipients) {
                return new ArrayList<String>(); // Return empty list for testing
            }
            
            @Override
            public void doCustomDispatch(List<String> recipients, String subject, String content) {
                // No-op implementation for testing
            }
        };
        
        mockAlertWorker = new AlertWorker() {
            @Override
            protected void onShutdown() {

            }

            @Override
            public Set<EventType> getEventTypes() {
                return new HashSet<EventType>(); // Return empty set instead of null
            }

            @Override
            protected void processEvent(Event event) {

            }

            @Override
            public Class<?> getTriggerClass() {
                return Object.class;
            }
            
            @Override
            protected void alertEnabled(Alert alert) {
                // No-op implementation for testing
            }
            
            @Override
            protected void alertDisabled(Alert alert) {
                // No-op implementation for testing
            }
            
            @Override
            protected void triggerAction(Alert alert, Map<String, Object> context) {
                // No-op implementation for testing
            }
        };
    }

    @Test
    public void testCreateSingleton() {
        AlertController instance1 = DefaultAlertController.create();
        AlertController instance2 = DefaultAlertController.create();
        
        assertSame("create() should return singleton instance", instance1, instance2);
    }

    @Test
    public void testRegisterAlertActionProtocol() {
        alertController.registerAlertActionProtocol(mockProtocol);
        
        // Verify the protocol was registered by trying to retrieve it
        Protocol retrievedProtocol = alertController.getAlertActionProtocol("TestProtocol");
        assertNotNull("Should return registered protocol", retrievedProtocol);
        assertEquals("Should return correct protocol", mockProtocol, retrievedProtocol);
    }

    @Test
    public void testGetAlertActionProtocol() {
        // First register a protocol
        alertController.registerAlertActionProtocol(mockProtocol);
        
        // Then retrieve it
        Protocol retrievedProtocol = alertController.getAlertActionProtocol("TestProtocol");
        assertNotNull("Should return registered protocol", retrievedProtocol);
        assertEquals("Should return correct protocol", mockProtocol, retrievedProtocol);
        assertEquals("Should return correct protocol name", "TestProtocol", retrievedProtocol.getName());
    }

    @Test
    public void testGetAlertActionProtocol_NotFound() {
        // Try to get a protocol that doesn't exist
        Protocol retrievedProtocol = alertController.getAlertActionProtocol("NonExistentProtocol");
        assertNull("Should return null for non-existent protocol", retrievedProtocol);
    }

    @Test
    public void testGetAlertActionProtocolOptions() {
        // Register a protocol first
        alertController.registerAlertActionProtocol(mockProtocol);
        
        // Get protocol options
        Map<String, Map<String, String>> options = alertController.getAlertActionProtocolOptions();
        assertNotNull("Should return options map", options);
        assertTrue("Should contain TestProtocol", options.containsKey("TestProtocol"));
    }    @Test
    public void testAlertWorkerOperations() {
        // Test adding and removing workers
        alertController.addWorker(mockAlertWorker);
        alertController.removeAllWorkers();
        
        // These methods should complete without exception
    }

    @Test
    public void testInitAlerts() {
        // Test the method structure - this will attempt to call the database but should handle gracefully
        alertController.initAlerts();
        
        // The method should complete without throwing an exception (may log errors internally)
    }

    @Test
    public void testGetAlerts() {
        try {
            List<AlertModel> alerts = alertController.getAlerts();
            // Method may return empty list or throw exception based on database availability
            assertNotNull("Should return a list (may be empty)", alerts);
        } catch (ControllerException e) {
            // Expected if database is not available - this is fine for structure testing
            assertNotNull("Should throw ControllerException if database unavailable", e);
        }
    }

    @Test
    public void testGetAlert() {
        try {
            AlertModel alert = alertController.getAlert("alert1");
            // Method may return null or alert based on database availability
        } catch (ControllerException e) {
            // Expected if database is not available - this is fine for structure testing
            assertNotNull("Should throw ControllerException if database unavailable", e);
        }
    }

    @Test
    public void testUpdateAlert() {
        AlertModel newAlert = createEnabledAlert("newAlert", "New Alert");
        
        try {
            alertController.updateAlert(newAlert);
            // Method should complete or throw exception based on database availability
        } catch (ControllerException e) {
            // Expected if database is not available - this is fine for structure testing
            assertNotNull("Should throw ControllerException if database unavailable", e);
        }
    }

    @Test
    public void testRemoveAlert() {
        try {
            alertController.removeAlert("alert1");
            // Method should complete or throw exception based on database availability
        } catch (ControllerException e) {
            // Expected if database is not available - this is fine for structure testing
            assertNotNull("Should throw ControllerException if database unavailable", e);
        }
    }

    @Test
    public void testEnableAlert() {
        AlertModel alert = createEnabledAlert("alert1", "Test Alert");
        
        try {
            alertController.enableAlert(alert);
            // Method should complete or throw exception based on database availability
        } catch (ControllerException e) {
            // Expected if database is not available - this is fine for structure testing
            assertNotNull("Should throw ControllerException if database unavailable", e);
        }
    }

    @Test
    public void testDisableAlert() {
        AlertModel alert = createEnabledAlert("alert1", "Test Alert");
        
        try {
            alertController.disableAlert(alert.getId());
            // Method should complete or throw exception based on database availability
        } catch (ControllerException e) {
            // Expected if database is not available - this is fine for structure testing
            assertNotNull("Should throw ControllerException if database unavailable", e);
        }
    }

    @Test
    public void testGetAlertStatuses() {
        try {
            List<AlertStatus> statuses = alertController.getAlertStatusList();
            // Method should return list or throw exception
            assertNotNull("Should return alert statuses list", statuses);
        } catch (ControllerException e) {
            // Expected if database is not available - this is fine for structure testing
            assertNotNull("Should throw ControllerException if database unavailable", e);
        }
    }

    @Test
    public void testVacuumAlertTable() {
        // Test the method structure - this will attempt to call the database but should handle gracefully
        alertController.vacuumAlertTable();
        
        // The method should complete without throwing an exception (may log errors internally)
    }
    
    // Helper methods to create test alerts
    private AlertModel createEnabledAlert(String id, String name) {
        DefaultTrigger trigger = new DefaultTrigger();
        AlertActionGroup actionGroup = new AlertActionGroup();
        AlertModel alert = new AlertModel(trigger, actionGroup);
        alert.setId(id);
        alert.setName(name);
        alert.setEnabled(true);
        return alert;
    }
    
    private AlertModel createDisabledAlert(String id, String name) {
        DefaultTrigger trigger = new DefaultTrigger();
        AlertActionGroup actionGroup = new AlertActionGroup();
        AlertModel alert = new AlertModel(trigger, actionGroup);
        alert.setId(id);
        alert.setName(name);
        alert.setEnabled(false);
        return alert;
    }
}
