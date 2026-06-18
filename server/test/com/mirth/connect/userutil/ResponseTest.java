/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 userutil coverage — Response constructors/getter/setter/toString.
 */

package com.mirth.connect.userutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResponseTest {

    // ------------------------------------------------------------------
    // Default constructor
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_statusNull() {
        Response r = new Response();
        assertNull(r.getStatus());
    }

    // ------------------------------------------------------------------
    // String-only constructor: sets message
    // ------------------------------------------------------------------

    @Test
    public void messageConstructor_setsMessage() {
        Response r = new Response("Processing complete");
        assertEquals("Processing complete", r.getMessage());
    }

    // ------------------------------------------------------------------
    // Status + message constructor
    // ------------------------------------------------------------------

    @Test
    public void statusMessageConstructor_setsStatusAndMessage() {
        Response r = new Response(Status.SENT, "Sent successfully");
        assertEquals(Status.SENT, r.getStatus());
        assertEquals("Sent successfully", r.getMessage());
    }

    @Test
    public void statusMessageConstructor_error() {
        Response r = new Response(Status.ERROR, "Connection failed");
        assertEquals(Status.ERROR, r.getStatus());
        assertEquals("Connection failed", r.getMessage());
    }

    // ------------------------------------------------------------------
    // Status + message + statusMessage constructor
    // ------------------------------------------------------------------

    @Test
    public void threeArgConstructor_setsAllFields() {
        Response r = new Response(Status.QUEUED, "Queued", "Waiting in queue");
        assertEquals(Status.QUEUED, r.getStatus());
        assertEquals("Queued", r.getMessage());
        assertEquals("Waiting in queue", r.getStatusMessage());
    }

    // ------------------------------------------------------------------
    // Full 4-arg constructor
    // ------------------------------------------------------------------

    @Test
    public void fourArgConstructor_setsAllFields() {
        Response r = new Response(Status.ERROR, "Failed", "Timeout error", "Connection timed out after 30s");
        assertEquals(Status.ERROR, r.getStatus());
        assertEquals("Failed", r.getMessage());
        assertEquals("Timeout error", r.getStatusMessage());
        assertEquals("Connection timed out after 30s", r.getError());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setMessage_roundTrip() {
        Response r = new Response();
        r.setMessage("New message");
        assertEquals("New message", r.getMessage());
    }

    @Test
    public void setStatus_sent() {
        Response r = new Response();
        r.setStatus(Status.SENT);
        assertEquals(Status.SENT, r.getStatus());
    }

    @Test
    public void setStatus_filtered() {
        Response r = new Response();
        r.setStatus(Status.FILTERED);
        assertEquals(Status.FILTERED, r.getStatus());
    }

    @Test
    public void setStatus_transformed() {
        Response r = new Response();
        r.setStatus(Status.TRANSFORMED);
        assertEquals(Status.TRANSFORMED, r.getStatus());
    }

    @Test
    public void setStatus_received() {
        Response r = new Response();
        r.setStatus(Status.RECEIVED);
        assertEquals(Status.RECEIVED, r.getStatus());
    }

    @Test
    public void setStatus_pending() {
        Response r = new Response();
        r.setStatus(Status.PENDING);
        assertEquals(Status.PENDING, r.getStatus());
    }

    @Test
    public void setError_roundTrip() {
        Response r = new Response();
        r.setError("java.io.IOException: Connection refused");
        assertEquals("java.io.IOException: Connection refused", r.getError());
    }

    @Test
    public void setStatusMessage_roundTrip() {
        Response r = new Response();
        r.setStatusMessage("ACK received");
        assertEquals("ACK received", r.getStatusMessage());
    }

    // ------------------------------------------------------------------
    // toString: non-null
    // ------------------------------------------------------------------

    @Test
    public void toString_nonNull() {
        Response r = new Response(Status.SENT, "OK");
        String str = r.toString();
        assertNotNull(str);
        assertTrue(str.length() > 0);
    }
}
