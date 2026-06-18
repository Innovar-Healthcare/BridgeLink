/*
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1056: Wave 4 userutil coverage — ListBuilder append/List delegation.
 * Tests must be in the same package to access package-private constructors.
 */

package com.mirth.connect.userutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ListBuilderTest {

    // ------------------------------------------------------------------
    // Default constructor: empty list
    // ------------------------------------------------------------------

    @Test
    public void defaultConstructor_empty() {
        ListBuilder lb = new ListBuilder();
        assertTrue(lb.isEmpty());
        assertEquals(0, lb.size());
    }

    // ------------------------------------------------------------------
    // Object constructor: one element
    // ------------------------------------------------------------------

    @Test
    public void objectConstructor_singleElement() {
        ListBuilder lb = new ListBuilder("first");
        assertEquals(1, lb.size());
        assertFalse(lb.isEmpty());
        assertEquals("first", lb.get(0));
    }

    // ------------------------------------------------------------------
    // List constructor: wraps existing list
    // ------------------------------------------------------------------

    @Test
    public void listConstructor_wrapsExisting() {
        List<String> existing = new ArrayList<String>();
        existing.add("a");
        existing.add("b");
        ListBuilder lb = new ListBuilder(existing);
        assertEquals(2, lb.size());
    }

    // ------------------------------------------------------------------
    // append: adds element and returns builder (fluent)
    // ------------------------------------------------------------------

    @Test
    public void append_addsElementReturnsBuilder() {
        ListBuilder lb = new ListBuilder();
        ListBuilder result = lb.append("one");
        assertEquals(lb, result); // same instance
        assertEquals(1, lb.size());
        assertEquals("one", lb.get(0));
    }

    @Test
    public void append_multipleElements_fluentChain() {
        ListBuilder lb = new ListBuilder();
        lb.append("a").append("b").append("c");
        assertEquals(3, lb.size());
        assertEquals("a", lb.get(0));
        assertEquals("b", lb.get(1));
        assertEquals("c", lb.get(2));
    }

    // ------------------------------------------------------------------
    // contains
    // ------------------------------------------------------------------

    @Test
    public void contains_presentElement_returnsTrue() {
        ListBuilder lb = new ListBuilder("test");
        assertTrue(lb.contains("test"));
    }

    @Test
    public void contains_absentElement_returnsFalse() {
        ListBuilder lb = new ListBuilder("test");
        assertFalse(lb.contains("other"));
    }

    // ------------------------------------------------------------------
    // remove by object
    // ------------------------------------------------------------------

    @Test
    public void remove_existingElement_removesIt() {
        ListBuilder lb = new ListBuilder();
        lb.append("x");
        lb.append("y");
        lb.remove("x");
        assertEquals(1, lb.size());
        assertFalse(lb.contains("x"));
    }

    // ------------------------------------------------------------------
    // remove by index
    // ------------------------------------------------------------------

    @Test
    public void removeByIndex_removesCorrectElement() {
        ListBuilder lb = new ListBuilder();
        lb.append("p").append("q").append("r");
        lb.remove(1);
        assertEquals(2, lb.size());
        assertEquals("p", lb.get(0));
        assertEquals("r", lb.get(1));
    }

    // ------------------------------------------------------------------
    // set
    // ------------------------------------------------------------------

    @Test
    public void set_replacesElement() {
        ListBuilder lb = new ListBuilder();
        lb.append("old");
        lb.set(0, "new");
        assertEquals("new", lb.get(0));
    }

    // ------------------------------------------------------------------
    // indexOf / lastIndexOf
    // ------------------------------------------------------------------

    @Test
    public void indexOf_returnsCorrectIndex() {
        ListBuilder lb = new ListBuilder();
        lb.append("a").append("b").append("a");
        assertEquals(0, lb.indexOf("a"));
    }

    @Test
    public void lastIndexOf_returnsLastIndex() {
        ListBuilder lb = new ListBuilder();
        lb.append("a").append("b").append("a");
        assertEquals(2, lb.lastIndexOf("a"));
    }

    // ------------------------------------------------------------------
    // add at index
    // ------------------------------------------------------------------

    @Test
    public void addAtIndex_insertsAtPosition() {
        ListBuilder lb = new ListBuilder();
        lb.append("first").append("third");
        lb.add(1, "second");
        assertEquals("second", lb.get(1));
        assertEquals(3, lb.size());
    }

    // ------------------------------------------------------------------
    // clear
    // ------------------------------------------------------------------

    @Test
    public void clear_emptiesTheList() {
        ListBuilder lb = new ListBuilder();
        lb.append("a").append("b");
        lb.clear();
        assertTrue(lb.isEmpty());
    }

    // ------------------------------------------------------------------
    // toArray
    // ------------------------------------------------------------------

    @Test
    public void toArray_returnsElements() {
        ListBuilder lb = new ListBuilder();
        lb.append("x").append("y");
        Object[] arr = lb.toArray();
        assertEquals(2, arr.length);
    }

    // ------------------------------------------------------------------
    // iterator / listIterator
    // ------------------------------------------------------------------

    @Test
    public void iterator_iteratesElements() {
        ListBuilder lb = new ListBuilder();
        lb.append(1).append(2).append(3);
        int count = 0;
        for (Object o : lb) {
            count++;
        }
        assertEquals(3, count);
    }

    // ------------------------------------------------------------------
    // addAll / containsAll / removeAll / retainAll
    // ------------------------------------------------------------------

    @Test
    public void addAll_addsMultiple() {
        ListBuilder lb = new ListBuilder();
        List<String> more = new ArrayList<String>();
        more.add("e1");
        more.add("e2");
        lb.addAll(more);
        assertEquals(2, lb.size());
    }

    @Test
    public void containsAll_allPresent_returnsTrue() {
        ListBuilder lb = new ListBuilder();
        lb.append("a").append("b").append("c");
        List<String> check = new ArrayList<String>();
        check.add("a");
        check.add("c");
        assertTrue(lb.containsAll(check));
    }

    @Test
    public void removeAll_removesMatchingElements() {
        ListBuilder lb = new ListBuilder();
        lb.append("a").append("b").append("c");
        List<String> toRemove = new ArrayList<String>();
        toRemove.add("a");
        toRemove.add("c");
        lb.removeAll(toRemove);
        assertEquals(1, lb.size());
        assertEquals("b", lb.get(0));
    }

    @Test
    public void retainAll_keepsOnlyMatching() {
        ListBuilder lb = new ListBuilder();
        lb.append("a").append("b").append("c");
        List<String> toRetain = new ArrayList<String>();
        toRetain.add("b");
        lb.retainAll(toRetain);
        assertEquals(1, lb.size());
        assertEquals("b", lb.get(0));
    }
}
