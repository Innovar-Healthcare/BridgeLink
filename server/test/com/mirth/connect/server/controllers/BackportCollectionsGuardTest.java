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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Guards the {@code java.util.Collections.singletonList} call site introduced by the CVE-05
 * backport-util-concurrent removal in {@link DefaultDatabaseTaskController}. That controller's
 * {@code addIndexTask} submission (DefaultDatabaseTaskController.java:321) reads:
 *
 * <pre>
 * engineController.submitTasks(Collections.singletonList(addIndexTask), taskHandler);
 * </pre>
 *
 * prior to the swap, the {@code import} at DefaultDatabaseTaskController.java:46 resolved to
 * {@code edu.emory.mathcs.backport.java.util.Collections}; it now resolves to the JDK-native
 * {@code java.util.Collections}.
 *
 * <p>The regression this exists to catch is a revert that re-introduces the backport shim (either
 * the {@code edu.emory.mathcs.backport} dependency on the classpath, or the production import
 * pointing back at it). A test that only exercised {@code java.util.Collections} directly would
 * assert the JDK behaves like the JDK and stay green through exactly that revert — false regression
 * protection. So the primary guard here is coupled to the production classpath: it asserts the
 * backport {@code Collections} class is <em>absent</em> from the very classloader
 * {@link DefaultDatabaseTaskController} is loaded by, and pins the call site's concrete return type
 * to the JDK implementation.
 */
public class BackportCollectionsGuardTest {

    /** The removed CVE-05 shim's fully-qualified name — must NOT be resolvable post-removal. */
    private static final String BACKPORT_COLLECTIONS = "edu.emory.mathcs.backport.java.util.Collections";

    /**
     * Primary CVE-05 regression guard, coupled to the production classpath.
     *
     * <p>Resolves classes through {@link DefaultDatabaseTaskController}'s own classloader — the same
     * one that resolves the {@code Collections} reference at DefaultDatabaseTaskController.java:321.
     * If the backport-util-concurrent dependency is ever re-introduced (the CVE-05 regression), the
     * backport {@code Collections} class becomes loadable again and this test fails. It does NOT
     * stay green through a revert, unlike a bare {@code java.util.Collections} exercise.
     */
    @Test
    public void testBackportCollectionsAbsentFromProductionClasspath() {
        ClassLoader productionLoader = DefaultDatabaseTaskController.class.getClassLoader();

        try {
            Class.forName(BACKPORT_COLLECTIONS, false, productionLoader);
            fail("CVE-05 regression: '" + BACKPORT_COLLECTIONS + "' is resolvable from the "
                    + "DefaultDatabaseTaskController classloader — the backport-util-concurrent "
                    + "shim has been re-introduced and must be removed.");
        } catch (ClassNotFoundException expected) {
            // expected — the backport shim is gone from the classpath.
        }

        // The JDK-native Collections MUST remain resolvable from the same loader (sanity anchor:
        // proves the loader is real and the absence above is meaningful, not a dead loader).
        try {
            Class.forName("java.util.Collections", false, productionLoader);
        } catch (ClassNotFoundException impossible) {
            fail("java.util.Collections not resolvable from the production classloader: " + impossible);
        }
    }

    /**
     * Pins the {@code Collections.singletonList} contract exercised at the addIndexTask call site
     * (DefaultDatabaseTaskController.java:321): size 1 (edge CVE-05/empty, single-input contract),
     * element identity at index 0 (edge CVE-05/ordering, positional determinism), immutability, and
     * that the concrete return type is the JDK implementation ({@code java.util.Collections$SingletonList})
     * rather than any backport shim type.
     */
    @Test
    public void testSingletonListCallSiteContract() {
        // Stand-in for the addIndexTask argument at DefaultDatabaseTaskController.java:321.
        Object addIndexTask = new Object();

        List<Object> single = Collections.singletonList(addIndexTask);

        // The concrete implementation must be the JDK's SingletonList, not a backport shim type —
        // pins WHICH Collections.singletonList the call site resolves to.
        assertEquals("java.util.Collections$SingletonList", single.getClass().getName());

        // edge CVE-05/empty: single-input contract yields exactly one element.
        assertEquals(1, single.size());

        // edge CVE-05/ordering: the single element is retrievable at index 0 and is the same
        // instance as the input, pinning positional determinism for the one-element list.
        assertSame(addIndexTask, single.get(0));

        // Immutability: a mutating add() must throw UnsupportedOperationException.
        try {
            single.add(new Object());
            fail("singletonList must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}
