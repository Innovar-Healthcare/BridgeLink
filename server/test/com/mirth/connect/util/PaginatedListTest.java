/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class PaginatedListTest {

    // -------------------------------------------------------------------------
    // Concrete anonymous subclass backed by a fixed list
    // -------------------------------------------------------------------------

    /**
     * Creates a PaginatedList backed by the given items.
     * itemCount is the number of items in the backing list (not null).
     */
    private PaginatedList<String> createList(final List<String> items) {
        return new PaginatedList<String>() {
            @Override
            public Long getItemCount() {
                return (long) items.size();
            }

            @Override
            protected List<String> getItems(int offset, int limit) {
                if (offset >= items.size()) {
                    return new ArrayList<String>();
                }
                int end = Math.min(offset + limit, items.size());
                return new ArrayList<String>(items.subList(offset, end));
            }
        };
    }

    /**
     * Creates a PaginatedList with an unknown item count (getItemCount returns null).
     */
    private PaginatedList<String> createUnknownCountList(final List<String> items) {
        return new PaginatedList<String>() {
            @Override
            public Long getItemCount() {
                return null;
            }

            @Override
            protected List<String> getItems(int offset, int limit) {
                if (offset >= items.size()) {
                    return new ArrayList<String>();
                }
                int end = Math.min(offset + limit, items.size());
                return new ArrayList<String>(items.subList(offset, end));
            }
        };
    }

    // -------------------------------------------------------------------------
    // First page: 2 items, pageSize=2 -> hasNextPage true
    // -------------------------------------------------------------------------

    @Test
    public void testFirstPageWithTwoItemsHasNextPage() throws Exception {
        List<String> items = Arrays.asList("a", "b", "c", "d", "e");
        PaginatedList<String> list = createList(items);
        list.setPageSize(2);

        boolean loaded = list.loadPageNumber(1);

        assertTrue("loadPageNumber(1) should return true", loaded);
        assertEquals("First page should contain 2 items", 2, list.size());
        assertEquals("a", list.get(0));
        assertEquals("b", list.get(1));
        assertTrue("Should have a next page", list.hasNextPage());
    }

    // -------------------------------------------------------------------------
    // Last page: returns remaining items, hasNextPage false
    // -------------------------------------------------------------------------

    @Test
    public void testLastPageHasNoNextPage() throws Exception {
        List<String> items = Arrays.asList("a", "b", "c");
        PaginatedList<String> list = createList(items);
        list.setPageSize(2);

        list.loadPageNumber(2); // page 2 of 3-item list with pageSize 2

        assertEquals("Last page should contain 1 item", 1, list.size());
        assertEquals("c", list.get(0));
        assertFalse("Should not have a next page on last page", list.hasNextPage());
    }

    // -------------------------------------------------------------------------
    // loadPageNumber beyond items returns false
    // -------------------------------------------------------------------------

    @Test
    public void testLoadPageBeyondItemsReturnsFalse() throws Exception {
        List<String> items = Arrays.asList("a", "b");
        PaginatedList<String> list = createList(items);
        list.setPageSize(2);

        boolean loaded = list.loadPageNumber(5); // way beyond end

        assertFalse("loadPageNumber beyond items should return false", loaded);
    }

    // -------------------------------------------------------------------------
    // setPageSize(0) normalizes to 100 (DEFAULT_PAGE_SIZE)
    // -------------------------------------------------------------------------

    @Test
    public void testSetPageSizeZeroNormalizesToDefault() throws Exception {
        List<String> items = new ArrayList<String>();
        for (int i = 0; i < 150; i++) {
            items.add("item" + i);
        }
        PaginatedList<String> list = createList(items);
        list.setPageSize(0); // should normalize to DEFAULT_PAGE_SIZE = 100

        boolean loaded = list.loadPageNumber(1);
        assertTrue("Page should load successfully", loaded);
        assertEquals("Default page size of 100 should load 100 items", 100, list.size());
        assertTrue("Should have a next page", list.hasNextPage());
    }

    // -------------------------------------------------------------------------
    // setPageSize with negative value normalizes to 100
    // -------------------------------------------------------------------------

    @Test
    public void testSetPageSizeNegativeNormalizesToDefault() throws Exception {
        List<String> items = new ArrayList<String>();
        for (int i = 0; i < 150; i++) {
            items.add("item" + i);
        }
        PaginatedList<String> list = createList(items);
        list.setPageSize(-5); // should also normalize to DEFAULT_PAGE_SIZE = 100

        boolean loaded = list.loadPageNumber(1);
        assertTrue("Page should load successfully", loaded);
        assertEquals("Normalized page size of 100 should load 100 items", 100, list.size());
    }

    // -------------------------------------------------------------------------
    // Default pageSize == 0: loadPageNumber returns false without setPageSize
    // -------------------------------------------------------------------------

    @Test
    public void testLoadPageNumberWithoutSetPageSizeReturnsFalse() throws Exception {
        List<String> items = Arrays.asList("a", "b");
        PaginatedList<String> list = createList(items);
        // Do NOT call setPageSize — default is 0, guard returns false

        boolean loaded = list.loadPageNumber(1);
        assertFalse("loadPageNumber with pageSize==0 should return false", loaded);
    }

    // -------------------------------------------------------------------------
    // getPageCount: ceil(count / pageSize)
    // -------------------------------------------------------------------------

    @Test
    public void testGetPageCountExactDivision() {
        List<String> items = Arrays.asList("a", "b", "c", "d");
        PaginatedList<String> list = createList(items);
        list.setPageSize(2);

        assertEquals("4 items / pageSize 2 = 2 pages", Integer.valueOf(2), list.getPageCount());
    }

    @Test
    public void testGetPageCountCeilRounding() {
        List<String> items = Arrays.asList("a", "b", "c");
        PaginatedList<String> list = createList(items);
        list.setPageSize(2);

        assertEquals("3 items / pageSize 2 = ceil(1.5) = 2 pages", Integer.valueOf(2), list.getPageCount());
    }

    @Test
    public void testGetPageCountSingleItem() {
        List<String> items = Arrays.asList("a");
        PaginatedList<String> list = createList(items);
        list.setPageSize(10);

        assertEquals("1 item / pageSize 10 = 1 page", Integer.valueOf(1), list.getPageCount());
    }

    @Test
    public void testGetPageCountNullItemCountReturnsNull() {
        List<String> items = Arrays.asList("a", "b");
        PaginatedList<String> list = createUnknownCountList(items);
        list.setPageSize(2);

        assertNull("getPageCount with null itemCount should return null", list.getPageCount());
    }

    // -------------------------------------------------------------------------
    // getOffset
    // -------------------------------------------------------------------------

    @Test
    public void testGetOffsetPage1() {
        PaginatedList<String> list = createList(new ArrayList<String>());
        list.setPageSize(10);
        assertEquals("Offset for page 1 should be 0", 0, list.getOffset(1));
    }

    @Test
    public void testGetOffsetPage3() {
        PaginatedList<String> list = createList(new ArrayList<String>());
        list.setPageSize(10);
        assertEquals("Offset for page 3 with pageSize 10 should be 20", 20, list.getOffset(3));
    }

    // -------------------------------------------------------------------------
    // getPageNumber tracks current page after loadPageNumber
    // -------------------------------------------------------------------------

    @Test
    public void testGetPageNumberAfterLoad() throws Exception {
        List<String> items = Arrays.asList("a", "b", "c", "d");
        PaginatedList<String> list = createList(items);
        list.setPageSize(2);
        list.loadPageNumber(2);

        assertEquals("getPageNumber should return the loaded page number", 2, list.getPageNumber());
    }
}
