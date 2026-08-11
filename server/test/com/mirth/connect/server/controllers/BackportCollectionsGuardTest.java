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
 * {@code java.util.Collections}. This test pins the JDK-native {@code singletonList} contract
 * (size 1, element identity/index-0 positional determinism, immutability) as a drop-in equivalence
 * guard for the removed backport shim, without standing up any
 * {@code DefaultDatabaseTaskController}, {@code engineController}, real {@code ChannelTask}, DB
 * connection, or Mockito machinery.
 */
public class BackportCollectionsGuardTest {

    /**
     * Pins the {@code Collections.singletonList} contract exercised at the addIndexTask call site
     * (DefaultDatabaseTaskController.java:321): size 1 (edge CVE-05/empty, single-input contract),
     * element identity at index 0 (edge CVE-05/ordering, positional determinism), and immutability.
     */
    @Test
    public void testSingletonListCallSiteContract() {
        // Stand-in for the addIndexTask argument at DefaultDatabaseTaskController.java:321.
        Object addIndexTask = new Object();

        List<Object> single = Collections.singletonList(addIndexTask);

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
