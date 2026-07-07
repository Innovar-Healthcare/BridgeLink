/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 POJO coverage — MessageFilter getter/setter round-trips.
 */

package com.mirth.connect.model.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.model.filters.elements.ContentSearchElement;
import com.mirth.connect.model.filters.elements.MetaDataSearchElement;

import org.junit.Test;

public class MessageFilterTest {

    // ------------------------------------------------------------------
    // Default constructor: all fields null
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_allFieldsNull() {
        MessageFilter f = new MessageFilter();
        assertNull(f.getMaxMessageId());
        assertNull(f.getMinMessageId());
        assertNull(f.getTextSearch());
        assertNull(f.getStartDate());
        assertNull(f.getEndDate());
    }

    // ------------------------------------------------------------------
    // Getter/setter round-trips
    // ------------------------------------------------------------------

    @Test
    public void setMaxMessageId_roundTrip() {
        MessageFilter f = new MessageFilter();
        f.setMaxMessageId(9999L);
        assertEquals(Long.valueOf(9999L), f.getMaxMessageId());
    }

    @Test
    public void setMinMessageId_roundTrip() {
        MessageFilter f = new MessageFilter();
        f.setMinMessageId(1L);
        assertEquals(Long.valueOf(1L), f.getMinMessageId());
    }

    @Test
    public void setOriginalIdUpper_roundTrip() {
        MessageFilter f = new MessageFilter();
        f.setOriginalIdUpper(500L);
        assertEquals(Long.valueOf(500L), f.getOriginalIdUpper());
    }

    @Test
    public void setOriginalIdLower_roundTrip() {
        MessageFilter f = new MessageFilter();
        f.setOriginalIdLower(100L);
        assertEquals(Long.valueOf(100L), f.getOriginalIdLower());
    }

    @Test
    public void setImportIdUpper_roundTrip() {
        MessageFilter f = new MessageFilter();
        f.setImportIdUpper(200L);
        assertEquals(Long.valueOf(200L), f.getImportIdUpper());
    }

    @Test
    public void setImportIdLower_roundTrip() {
        MessageFilter f = new MessageFilter();
        f.setImportIdLower(50L);
        assertEquals(Long.valueOf(50L), f.getImportIdLower());
    }

    @Test
    public void setStartDate_roundTrip() {
        MessageFilter f = new MessageFilter();
        Calendar cal = Calendar.getInstance();
        f.setStartDate(cal);
        assertEquals(cal, f.getStartDate());
    }

    @Test
    public void setEndDate_roundTrip() {
        MessageFilter f = new MessageFilter();
        Calendar cal = Calendar.getInstance();
        f.setEndDate(cal);
        assertEquals(cal, f.getEndDate());
    }

    @Test
    public void setTextSearch_roundTrip() {
        MessageFilter f = new MessageFilter();
        f.setTextSearch("error occurred");
        assertEquals("error occurred", f.getTextSearch());
    }

    @Test
    public void setTextSearchRegex_true() {
        MessageFilter f = new MessageFilter();
        f.setTextSearchRegex(Boolean.TRUE);
        assertEquals(Boolean.TRUE, f.getTextSearchRegex());
    }

    @Test
    public void setTextSearchRegex_false() {
        MessageFilter f = new MessageFilter();
        f.setTextSearchRegex(Boolean.FALSE);
        assertEquals(Boolean.FALSE, f.getTextSearchRegex());
    }

    @Test
    public void setStatuses_roundTrip() {
        MessageFilter f = new MessageFilter();
        Set<Status> statuses = new HashSet<Status>();
        statuses.add(Status.ERROR);
        statuses.add(Status.SENT);
        f.setStatuses(statuses);
        assertNotNull(f.getStatuses());
        assertEquals(2, f.getStatuses().size());
        assertTrue(f.getStatuses().contains(Status.ERROR));
    }

    @Test
    public void setIncludedMetaDataIds_roundTrip() {
        MessageFilter f = new MessageFilter();
        List<Integer> ids = new ArrayList<Integer>();
        ids.add(1);
        ids.add(3);
        f.setIncludedMetaDataIds(ids);
        assertEquals(2, f.getIncludedMetaDataIds().size());
    }

    @Test
    public void setExcludedMetaDataIds_roundTrip() {
        MessageFilter f = new MessageFilter();
        List<Integer> ids = new ArrayList<Integer>();
        ids.add(2);
        f.setExcludedMetaDataIds(ids);
        assertEquals(1, f.getExcludedMetaDataIds().size());
    }

    @Test
    public void setServerId_roundTrip() {
        MessageFilter f = new MessageFilter();
        f.setServerId("server-001");
        assertEquals("server-001", f.getServerId());
    }

    @Test
    public void setTextSearchMetaDataColumns_roundTrip() {
        MessageFilter f = new MessageFilter();
        List<String> cols = new ArrayList<String>();
        cols.add("column1");
        cols.add("column2");
        f.setTextSearchMetaDataColumns(cols);
        assertEquals(2, f.getTextSearchMetaDataColumns().size());
    }

    @Test
    public void setSendAttemptsLower_roundTrip() {
        MessageFilter f = new MessageFilter();
        f.setSendAttemptsLower(0);
        assertEquals(Integer.valueOf(0), f.getSendAttemptsLower());
    }

    @Test
    public void setSendAttemptsUpper_roundTrip() {
        MessageFilter f = new MessageFilter();
        f.setSendAttemptsUpper(3);
        assertEquals(Integer.valueOf(3), f.getSendAttemptsUpper());
    }

    @Test
    public void setAttachment_true() {
        MessageFilter f = new MessageFilter();
        f.setAttachment(Boolean.TRUE);
        assertEquals(Boolean.TRUE, f.getAttachment());
    }

    @Test
    public void setAttachment_false() {
        MessageFilter f = new MessageFilter();
        f.setAttachment(Boolean.FALSE);
        assertEquals(Boolean.FALSE, f.getAttachment());
    }

    @Test
    public void setError_true() {
        MessageFilter f = new MessageFilter();
        f.setError(Boolean.TRUE);
        assertEquals(Boolean.TRUE, f.getError());
    }

    @Test
    public void setError_false() {
        MessageFilter f = new MessageFilter();
        f.setError(Boolean.FALSE);
        assertEquals(Boolean.FALSE, f.getError());
    }
}
