/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 plugin POJO coverage — ServerLogItem constructor/getter/setter round-trips.
 */

package com.mirth.connect.plugins.serverlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Date;

import org.junit.Test;

public class ServerLogItemTest {

    // ------------------------------------------------------------------
    // Default constructor: all null
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_allNull() {
        ServerLogItem item = new ServerLogItem();
        assertNull(item.getServerId());
        assertNull(item.getId());
        assertNull(item.getLevel());
        assertNull(item.getDate());
        assertNull(item.getMessage());
    }

    // ------------------------------------------------------------------
    // Single-arg constructor: sets message only
    // ------------------------------------------------------------------

    @Test
    public void messageConstructor_setsMessage() {
        ServerLogItem item = new ServerLogItem("Test log message");
        assertEquals("Test log message", item.getMessage());
    }

    // ------------------------------------------------------------------
    // Full constructor
    // ------------------------------------------------------------------

    @Test
    public void fullConstructor_setsAllFields() {
        Date now = new Date();
        ServerLogItem item = new ServerLogItem(
            "server-001", 42L, "ERROR", now,
            "main", "com.example.App", "123",
            "An error occurred", "java.lang.NullPointerException"
        );
        assertEquals("server-001", item.getServerId());
        assertEquals(Long.valueOf(42L), item.getId());
        assertEquals("ERROR", item.getLevel());
        assertEquals(now, item.getDate());
        assertEquals("main", item.getThreadName());
        assertEquals("com.example.App", item.getCategory());
        assertEquals("123", item.getLineNumber());
        assertEquals("An error occurred", item.getMessage());
        assertEquals("java.lang.NullPointerException", item.getThrowableInformation());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setServerId_roundTrip() {
        ServerLogItem item = new ServerLogItem();
        item.setServerId("server-xyz");
        assertEquals("server-xyz", item.getServerId());
    }

    @Test
    public void setId_roundTrip() {
        ServerLogItem item = new ServerLogItem();
        item.setId(99L);
        assertEquals(Long.valueOf(99L), item.getId());
    }

    @Test
    public void setLevel_info() {
        ServerLogItem item = new ServerLogItem();
        item.setLevel("INFO");
        assertEquals("INFO", item.getLevel());
    }

    @Test
    public void setLevel_warn() {
        ServerLogItem item = new ServerLogItem();
        item.setLevel("WARN");
        assertEquals("WARN", item.getLevel());
    }

    @Test
    public void setLevel_debug() {
        ServerLogItem item = new ServerLogItem();
        item.setLevel("DEBUG");
        assertEquals("DEBUG", item.getLevel());
    }

    @Test
    public void setDate_roundTrip() {
        ServerLogItem item = new ServerLogItem();
        Date d = new Date();
        item.setDate(d);
        assertEquals(d, item.getDate());
    }

    @Test
    public void setThreadName_roundTrip() {
        ServerLogItem item = new ServerLogItem();
        item.setThreadName("worker-1");
        assertEquals("worker-1", item.getThreadName());
    }

    @Test
    public void setCategory_roundTrip() {
        ServerLogItem item = new ServerLogItem();
        item.setCategory("com.mirth.connect.server.Mirth");
        assertEquals("com.mirth.connect.server.Mirth", item.getCategory());
    }

    @Test
    public void setLineNumber_roundTrip() {
        ServerLogItem item = new ServerLogItem();
        item.setLineNumber("456");
        assertEquals("456", item.getLineNumber());
    }

    @Test
    public void setMessage_roundTrip() {
        ServerLogItem item = new ServerLogItem();
        item.setMessage("Server started successfully");
        assertEquals("Server started successfully", item.getMessage());
    }

    @Test
    public void setThrowableInformation_roundTrip() {
        ServerLogItem item = new ServerLogItem();
        item.setThrowableInformation("java.io.IOException: file not found");
        assertEquals("java.io.IOException: file not found", item.getThrowableInformation());
    }

    // ------------------------------------------------------------------
    // Mutation: multiple setter calls - last write wins
    // ------------------------------------------------------------------

    @Test
    public void setLevel_multipleWrites_lastWins() {
        ServerLogItem item = new ServerLogItem();
        item.setLevel("INFO");
        item.setLevel("ERROR");
        assertEquals("ERROR", item.getLevel());
    }

    @Test
    public void setMessage_multipleWrites_lastWins() {
        ServerLogItem item = new ServerLogItem();
        item.setMessage("First message");
        item.setMessage("Second message");
        assertEquals("Second message", item.getMessage());
    }
}
