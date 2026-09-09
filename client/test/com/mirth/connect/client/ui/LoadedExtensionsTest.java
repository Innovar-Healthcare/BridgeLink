/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-2162: connectors without a client class must be skipped, not reflected on.
 */

package com.mirth.connect.client.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.mirth.connect.model.ConnectorMetaData;

/**
 * Coverage for the client-class guard in {@link LoadedExtensions#initialize()} (IRT-2162).
 *
 * {@code initialize()} itself is not directly testable -- it is a singleton that reaches through
 * {@link PlatformUI#MIRTH_FRAME} for both the connector metadata and the extension-enabled check --
 * so the decision it makes per connector lives in the package-private
 * {@link LoadedExtensions#hasClientClass(ConnectorMetaData)} and is pinned here instead. Same seam
 * pattern as {@link WebAdminMigrationDialogTest}, and headless-safe for the same reason: no Swing
 * component is constructed.
 *
 * This is not a defensive null check. A connector whose settings UI is contributed by WebAdmin
 * ships no client class at all, and the Administrator is expected to load without it -- so the
 * guard encodes a supported shape, and removing it reintroduces the login-blocking error dialog
 * this ticket fixed.
 */
public class LoadedExtensionsTest {

    /**
     * An omitted {@code <clientClassName>} element deserializes to null, which is what reached
     * {@code Class.forName(null)} before the guard existed.
     */
    @Test
    public void hasClientClass_nullClassName_isFalse() {
        assertFalse(LoadedExtensions.hasClientClass(connectorMetaData(null)));
    }

    /**
     * An explicitly empty {@code <clientClassName/>} element deserializes to the empty string. It
     * never reached the NPE -- it raised ClassNotFoundException instead -- but it means the same
     * thing and must be skipped the same way.
     */
    @Test
    public void hasClientClass_emptyClassName_isFalse() {
        assertFalse(LoadedExtensions.hasClientClass(connectorMetaData("")));
    }

    @Test
    public void hasClientClass_populatedClassName_isTrue() {
        assertTrue(LoadedExtensions.hasClientClass(connectorMetaData("com.mirth.connect.connectors.vm.ChannelReader")));
    }

    private static ConnectorMetaData connectorMetaData(String clientClassName) {
        ConnectorMetaData metaData = new ConnectorMetaData();
        metaData.setName("Work Queue Reader");
        metaData.setType(ConnectorMetaData.Type.SOURCE);
        metaData.setClientClassName(clientClassName);
        return metaData;
    }
}
