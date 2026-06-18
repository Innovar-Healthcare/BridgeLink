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
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.mirth.connect.model.ChannelDependency;

public class ChannelDependencyGraphTest {

    // -------------------------------------------------------------------------
    // Empty set: getOrderedElements is empty
    // -------------------------------------------------------------------------

    @Test
    public void testEmptyDependenciesOrderedEmpty() throws ChannelDependencyException {
        ChannelDependencyGraph graph = new ChannelDependencyGraph(new HashSet<ChannelDependency>());
        List<Set<String>> ordered = graph.getOrderedElements();
        assertTrue("Empty dependency set should produce empty ordered list", ordered.isEmpty());
    }

    @Test
    public void testNullDependenciesOrderedEmpty() throws ChannelDependencyException {
        ChannelDependencyGraph graph = new ChannelDependencyGraph(null);
        List<Set<String>> ordered = graph.getOrderedElements();
        assertTrue("Null dependency set should produce empty ordered list", ordered.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Single dependency: ch1 depends on ch2 -> ordered size 2
    // -------------------------------------------------------------------------

    @Test
    public void testSingleDependencyOrderedSizeTwo() throws ChannelDependencyException {
        // ChannelDependency(dependentId, dependencyId): ch1 depends on ch2
        Set<ChannelDependency> deps = new HashSet<ChannelDependency>();
        deps.add(new ChannelDependency("ch1", "ch2"));

        ChannelDependencyGraph graph = new ChannelDependencyGraph(deps);
        List<Set<String>> ordered = graph.getOrderedElements();

        assertEquals("Single dependency should produce 2 tiers", 2, ordered.size());
        assertTrue("Tier 0 should contain ch1 (the dependent)", ordered.get(0).contains("ch1"));
        assertTrue("Tier 1 should contain ch2 (the dependency)", ordered.get(1).contains("ch2"));
    }

    // -------------------------------------------------------------------------
    // Cyclic: ch1 depends ch2 and ch2 depends ch1 -> throws ChannelDependencyException
    // -------------------------------------------------------------------------

    @Test(expected = ChannelDependencyException.class)
    public void testCyclicDependencyThrowsException() throws ChannelDependencyException {
        Set<ChannelDependency> deps = new HashSet<ChannelDependency>();
        deps.add(new ChannelDependency("ch1", "ch2"));
        deps.add(new ChannelDependency("ch2", "ch1")); // cycle

        // Constructor iterates over the set — will throw when cycle is found
        new ChannelDependencyGraph(deps);
    }

    // -------------------------------------------------------------------------
    // addDependency(ChannelDependency) post-construction
    // -------------------------------------------------------------------------

    @Test
    public void testAddDependencyAfterConstruction() throws ChannelDependencyException {
        ChannelDependencyGraph graph = new ChannelDependencyGraph(new HashSet<ChannelDependency>());
        graph.addDependency(new ChannelDependency("chA", "chB"));

        List<Set<String>> ordered = graph.getOrderedElements();
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0).contains("chA"));
        assertTrue(ordered.get(1).contains("chB"));
    }

    // -------------------------------------------------------------------------
    // Cycle added post-construction via addDependency(ChannelDependency)
    // -------------------------------------------------------------------------

    @Test(expected = ChannelDependencyException.class)
    public void testAddDependencyCycleThrows() throws ChannelDependencyException {
        Set<ChannelDependency> deps = new HashSet<ChannelDependency>();
        deps.add(new ChannelDependency("chX", "chY"));

        ChannelDependencyGraph graph = new ChannelDependencyGraph(deps);
        // Adding reverse edge should throw
        graph.addDependency(new ChannelDependency("chY", "chX"));
    }

    // -------------------------------------------------------------------------
    // getNode works on channel IDs
    // -------------------------------------------------------------------------

    @Test
    public void testGetNodeReturnsNonNullForKnownChannel() throws ChannelDependencyException {
        Set<ChannelDependency> deps = new HashSet<ChannelDependency>();
        deps.add(new ChannelDependency("sender", "receiver"));

        ChannelDependencyGraph graph = new ChannelDependencyGraph(deps);
        assertNotNull("getNode(sender) should be non-null", graph.getNode("sender"));
        assertNotNull("getNode(receiver) should be non-null", graph.getNode("receiver"));
    }

    // -------------------------------------------------------------------------
    // Chain: ch1 -> ch2 -> ch3 -> three tiers
    // -------------------------------------------------------------------------

    @Test
    public void testChainThreeTiers() throws ChannelDependencyException {
        Set<ChannelDependency> deps = new HashSet<ChannelDependency>();
        deps.add(new ChannelDependency("ch1", "ch2"));
        deps.add(new ChannelDependency("ch2", "ch3"));

        ChannelDependencyGraph graph = new ChannelDependencyGraph(deps);
        List<Set<String>> ordered = graph.getOrderedElements();
        assertEquals(3, ordered.size());
    }
}
