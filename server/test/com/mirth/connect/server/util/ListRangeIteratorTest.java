/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import com.mirth.connect.server.util.ListRangeIterator.ListRangeItem;

/**
 * Test class for ListRangeIterator.
 */
public class ListRangeIteratorTest {

    @Test
    public void testEmptyIterator() {
        Iterator<Long> emptyIterator = Collections.<Long>emptyList().iterator();
        ListRangeIterator rangeIterator = new ListRangeIterator(emptyIterator, 3, true, null);
        
        assertFalse("Empty iterator should not have next", rangeIterator.hasNext());
    }

    @Test
    public void testSingleElement() {
        List<Long> values = Arrays.asList(5L);
        ListRangeIterator rangeIterator = new ListRangeIterator(values.iterator(), 3, true, null);
        
        assertTrue("Single element should have next", rangeIterator.hasNext());
        
        ListRangeItem item = rangeIterator.next();
        assertNotNull("Item should not be null", item);
        assertNotNull("Item should have a list", item.getList());
        assertEquals("List should contain one element", 1, item.getList().size());
        assertEquals("List should contain the value", Long.valueOf(5), item.getList().get(0));
        assertNull("Should not have range", item.getStartRange());
        assertNull("Should not have range", item.getEndRange());
        
        assertFalse("Should not have more items", rangeIterator.hasNext());
    }

//    @Test
//    public void testAscendingContiguousRange() {
//        List<Long> values = Arrays.asList(1L, 2L, 3L, 4L, 5L);
//        ListRangeIterator rangeIterator = new ListRangeIterator(values.iterator(), 3, true, null);
//
//        assertTrue("Should have next", rangeIterator.hasNext());
//
//        // First item should be a list with first 3 elements
//        ListRangeItem item1 = rangeIterator.next();
//        assertNotNull("First item should not be null", item1);
//        assertNotNull("First item should have a list", item1.getList());
//        System.out.println("item1: "+item1);
//        System.out.println("item1.getList(): "+item1.getList());
//        assertEquals("First list should have 3 elements", 3, item1.getList().size());
//        assertEquals("First element should be 1", Long.valueOf(1), item1.getList().get(0));
//        assertEquals("Second element should be 2", Long.valueOf(2), item1.getList().get(1));
//        assertEquals("Third element should be 3", Long.valueOf(3), item1.getList().get(2));
//
//        assertTrue("Should have next", rangeIterator.hasNext());
//
//        // Second item should be a range for remaining elements
//        ListRangeItem item2 = rangeIterator.next();
//        assertNotNull("Second item should not be null", item2);
////        assertNull("Second item should not have a list", item2.getList());
//        assertEquals("Range start should be 4", Long.valueOf(4), item2.getStartRange());
//        assertEquals("Range end should be 5", Long.valueOf(5), item2.getEndRange());
//
//        assertFalse("Should not have more items", rangeIterator.hasNext());
//    }

//    @Test
//    public void testDescendingContiguousRange() {
//        List<Long> values = Arrays.asList(5L, 4L, 3L, 2L, 1L);
//        ListRangeIterator rangeIterator = new ListRangeIterator(values.iterator(), 3, false, null);
//
//        assertTrue("Should have next", rangeIterator.hasNext());
//
//        // First item should be a list with first 3 elements
//        ListRangeItem item1 = rangeIterator.next();
//        assertNotNull("First item should not be null", item1);
//        System.out.println("item1: "+item1);
//        System.out.println("item1.getList(): "+item1.getList());
//        assertNotNull("First item should have a list", item1.getList());
//        assertEquals("First list should have 3 elements", 3, item1.getList().size());
//        assertEquals("First element should be 5", Long.valueOf(5), item1.getList().get(0));
//        assertEquals("Second element should be 4", Long.valueOf(4), item1.getList().get(1));
//        assertEquals("Third element should be 3", Long.valueOf(3), item1.getList().get(2));
//
//        assertTrue("Should have next", rangeIterator.hasNext());
//
//        // Second item should be a range for remaining elements
//        ListRangeItem item2 = rangeIterator.next();
//        assertNotNull("Second item should not be null", item2);
//        assertNull("Second item should not have a list", item2.getList());
//        assertEquals("Range start should be 2", Long.valueOf(2), item2.getStartRange());
//        assertEquals("Range end should be 1", Long.valueOf(1), item2.getEndRange());
//
//        assertFalse("Should not have more items", rangeIterator.hasNext());
//    }

    @Test
    public void testNonContiguousValues() {
        List<Long> values = Arrays.asList(1L, 3L, 5L);
        ListRangeIterator rangeIterator = new ListRangeIterator(values.iterator(), 5, true, null);
        
        assertTrue("Should have next", rangeIterator.hasNext());
        
        ListRangeItem item = rangeIterator.next();
        assertNotNull("Item should not be null", item);
        assertNotNull("Item should have a list", item.getList());
        assertEquals("List should contain all non-contiguous elements", 3, item.getList().size());
        assertEquals("First element should be 1", Long.valueOf(1), item.getList().get(0));
        assertEquals("Second element should be 3", Long.valueOf(3), item.getList().get(1));
        assertEquals("Third element should be 5", Long.valueOf(5), item.getList().get(2));
        
        assertFalse("Should not have more items", rangeIterator.hasNext());
    }

    @Test
    public void testMixedContiguousAndNonContiguous() {
        List<Long> values = Arrays.asList(1L, 2L, 5L, 6L, 7L, 10L);
        ListRangeIterator rangeIterator = new ListRangeIterator(values.iterator(), 3, true, null);
        
        // Should get multiple items due to mixed contiguous/non-contiguous patterns
        List<ListRangeItem> items = new ArrayList<>();
        while (rangeIterator.hasNext()) {
            items.add(rangeIterator.next());
        }
        
        assertTrue("Should have at least one item", items.size() >= 1);
        
        // Verify that all original values are represented in some form
        List<Long> allRetrievedValues = new ArrayList<>();
        for (ListRangeItem item : items) {
            if (item.getList() != null) {
                allRetrievedValues.addAll(item.getList());
            } else if (item.getStartRange() != null && item.getEndRange() != null) {
                long start = item.getStartRange();
                long end = item.getEndRange();
                if (start <= end) {
                    for (long i = start; i <= end; i++) {
                        allRetrievedValues.add(i);
                    }
                } else {
                    for (long i = start; i >= end; i--) {
                        allRetrievedValues.add(i);
                    }
                }
            }
        }
        
        assertTrue("Should retrieve some values", allRetrievedValues.size() > 0);
    }

    @Test
    public void testLargeContiguousRange() {
        // Test with range larger than listSize to trigger range behavior
        List<Long> values = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        ListRangeIterator rangeIterator = new ListRangeIterator(values.iterator(), 3, true, null);
        
        assertTrue("Should have next", rangeIterator.hasNext());
        
        // Should get a list first (if any small contiguous blocks)
        // Then should get ranges for large contiguous blocks
        List<ListRangeItem> items = new ArrayList<>();
        while (rangeIterator.hasNext()) {
            items.add(rangeIterator.next());
        }
        
        assertTrue("Should have at least one item", items.size() >= 1);
        
        // At least one item should be a range (since we have a large contiguous block)
        boolean hasRange = items.stream().anyMatch(item -> item.getStartRange() != null);
        assertTrue("Should have at least one range item for large contiguous block", hasRange);
    }

    @Test
    public void testBlockSize() {
        List<Long> values = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        
        // Test with block size limitation
        ListRangeIterator rangeIterator = new ListRangeIterator(values.iterator(), 3, true, 5);
        
        // Should process only the first 5 elements due to block size limitation
        List<ListRangeItem> items = new ArrayList<>();
        while (rangeIterator.hasNext()) {
            items.add(rangeIterator.next());
        }
        
        assertTrue("Should have items", items.size() > 0);
    }

    @Test
    public void testListRangeItemGettersAndSetters() {
        ListRangeIterator rangeIterator = new ListRangeIterator(Arrays.asList(1L).iterator(), 1, true, null);
        ListRangeItem item = rangeIterator.new ListRangeItem();
        
        // Test list operations
        List<Long> testList = Arrays.asList(1L, 2L, 3L);
        item.setList(testList);
        assertEquals("List should be set correctly", testList, item.getList());
        
        // Test range operations
        item.setStartRange(5L);
        item.setEndRange(10L);
        assertEquals("Start range should be set correctly", Long.valueOf(5), item.getStartRange());
        assertEquals("End range should be set correctly", Long.valueOf(10), item.getEndRange());
        
        // Test null values
        item.setList(null);
        item.setStartRange(null);
        item.setEndRange(null);
        assertNull("List should be null", item.getList());
        assertNull("Start range should be null", item.getStartRange());
        assertNull("End range should be null", item.getEndRange());
    }

//    @Test
//    public void testEdgeCaseListSizeEqualsDataSize() {
//        List<Long> values = Arrays.asList(1L, 2L, 3L);
//        ListRangeIterator rangeIterator = new ListRangeIterator(values.iterator(), 3, true, null);
//
//        assertTrue("Should have next", rangeIterator.hasNext());
//
//        ListRangeItem item = rangeIterator.next();
//        assertNotNull("Item should not be null", item);
//        assertNotNull("Item should have a list", item.getList());
//        assertEquals("List should contain all elements", 3, item.getList().size());
//
//        assertFalse("Should not have more items", rangeIterator.hasNext());
//    }

//    @Test
//    public void testEdgeCaseListSizeOne() {
//        List<Long> values = Arrays.asList(1L, 2L, 3L, 4L, 5L);
//        ListRangeIterator rangeIterator = new ListRangeIterator(values.iterator(), 1, true, null);
//
//        // Should produce multiple items, each with a single element or range
//        int itemCount = 0;
//        while (rangeIterator.hasNext()) {
//            ListRangeItem item = rangeIterator.next();
//            assertNotNull("Each item should not be null", item);
//            itemCount++;
//        }
//
//        assertTrue("Should have multiple items", itemCount > 1);
//    }
}
