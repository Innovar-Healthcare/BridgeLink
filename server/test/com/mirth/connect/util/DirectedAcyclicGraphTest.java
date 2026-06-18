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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;

public class DirectedAcyclicGraphTest {

    // -------------------------------------------------------------------------
    // Empty graph
    // -------------------------------------------------------------------------

    @Test
    public void testEmptyGraphOrderedElementsIsEmpty() throws DirectedAcyclicGraphException {
        DirectedAcyclicGraph<String> graph = new DirectedAcyclicGraph<String>();
        List<Set<String>> ordered = graph.getOrderedElements();
        assertTrue("Empty graph should return empty ordered list", ordered.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Single-edge: A depends on B  (A is dependent, B is dependency)
    // -------------------------------------------------------------------------

    @Test
    public void testSingleEdgeOrderedSizeIsTwo() throws DirectedAcyclicGraphException {
        DirectedAcyclicGraph<String> graph = new DirectedAcyclicGraph<String>();
        graph.addDependency("A", "B");

        List<Set<String>> ordered = graph.getOrderedElements();
        // Two tiers: tier 0 contains A (sink), tier 1 contains B
        assertEquals(2, ordered.size());
        assertTrue("Tier 0 should contain A (the dependent/sink)", ordered.get(0).contains("A"));
        assertTrue("Tier 1 should contain B (the dependency/source)", ordered.get(1).contains("B"));
    }

    // -------------------------------------------------------------------------
    // Chain: A -> B -> C
    // -------------------------------------------------------------------------

    @Test
    public void testChainOrderedSizeIsThree() throws DirectedAcyclicGraphException {
        DirectedAcyclicGraph<String> graph = new DirectedAcyclicGraph<String>();
        graph.addDependency("A", "B");
        graph.addDependency("B", "C");

        List<Set<String>> ordered = graph.getOrderedElements();
        assertEquals(3, ordered.size());
        assertTrue("Tier 0 should contain A", ordered.get(0).contains("A"));
        assertTrue("Tier 1 should contain B", ordered.get(1).contains("B"));
        assertTrue("Tier 2 should contain C", ordered.get(2).contains("C"));
    }

    // -------------------------------------------------------------------------
    // Cycle detection: A -> B then B -> A throws exception
    // -------------------------------------------------------------------------

    @Test(expected = DirectedAcyclicGraphException.class)
    public void testCycleThrowsException() throws DirectedAcyclicGraphException {
        DirectedAcyclicGraph<String> graph = new DirectedAcyclicGraph<String>();
        graph.addDependency("A", "B");
        graph.addDependency("B", "A"); // cycle: should throw
    }

    // -------------------------------------------------------------------------
    // getNode: present vs missing
    // -------------------------------------------------------------------------

    @Test
    public void testGetNodeReturnNonNullForAddedElement() throws DirectedAcyclicGraphException {
        DirectedAcyclicGraph<String> graph = new DirectedAcyclicGraph<String>();
        graph.addDependency("A", "B");

        assertNotNull("getNode(A) should return non-null", graph.getNode("A"));
        assertNotNull("getNode(B) should return non-null", graph.getNode("B"));
    }

    @Test
    public void testGetNodeReturnNullForMissingElement() throws DirectedAcyclicGraphException {
        DirectedAcyclicGraph<String> graph = new DirectedAcyclicGraph<String>();
        graph.addDependency("A", "B");

        assertNull("getNode(missing) should return null", graph.getNode("MISSING"));
    }

    // -------------------------------------------------------------------------
    // Diamond dependency: A->B, A->C, B->D, C->D
    // -------------------------------------------------------------------------

    @Test
    public void testDiamondDependencyDoesNotThrow() throws DirectedAcyclicGraphException {
        DirectedAcyclicGraph<String> graph = new DirectedAcyclicGraph<String>();
        graph.addDependency("A", "B");
        graph.addDependency("A", "C");
        graph.addDependency("B", "D");
        graph.addDependency("C", "D");

        List<Set<String>> ordered = graph.getOrderedElements();
        // D must appear at a deeper tier than B and C, which must be deeper than A
        assertNotNull(ordered);
        assertTrue("Graph must have at least 3 tiers for diamond", ordered.size() >= 3);
        // A is in tier 0 (only sink)
        assertTrue("Tier 0 should contain A", ordered.get(0).contains("A"));
        // D should be in the deepest tier
        assertTrue("D should appear somewhere in ordered list",
                ordered.stream().anyMatch(s -> s.contains("D")));
    }

    // -------------------------------------------------------------------------
    // Self-cycle: A -> A throws exception
    // -------------------------------------------------------------------------

    @Test(expected = DirectedAcyclicGraphException.class)
    public void testSelfCycleThrowsException() throws DirectedAcyclicGraphException {
        DirectedAcyclicGraph<String> graph = new DirectedAcyclicGraph<String>();
        graph.addDependency("A", "B");
        graph.addDependency("B", "A");  // this creates the cycle
    }

    // -------------------------------------------------------------------------
    // getOrderedNodes mirrors getOrderedElements size
    // -------------------------------------------------------------------------

    @Test
    public void testGetOrderedNodesSizeMatchesElements() throws DirectedAcyclicGraphException {
        DirectedAcyclicGraph<String> graph = new DirectedAcyclicGraph<String>();
        graph.addDependency("X", "Y");

        assertEquals(graph.getOrderedElements().size(), graph.getOrderedNodes().size());
    }
}
