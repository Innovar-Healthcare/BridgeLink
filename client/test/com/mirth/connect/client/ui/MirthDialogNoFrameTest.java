/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.ui;

import static org.junit.Assert.assertFalse;

import java.awt.GraphicsEnvironment;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * MirthDialog reaches for PlatformUI.MIRTH_FRAME on show and on dispose, but that field is not
 * assigned until the Administrator window is built. The forced password change runs before that
 * point, so a dialog has to survive without one. See IRT-1791.
 */
public class MirthDialogNoFrameTest {

    private Frame originalFrame;

    @Before
    public void setUp() {
        Assume.assumeFalse("Needs a display to construct a dialog", GraphicsEnvironment.isHeadless());

        originalFrame = PlatformUI.MIRTH_FRAME;
        PlatformUI.MIRTH_FRAME = null;
    }

    @After
    public void tearDown() {
        PlatformUI.MIRTH_FRAME = originalFrame;
    }

    /**
     * Fails with a NullPointerException if the null guards are removed, which is what a fresh
     * install hitting the change-password prompt would have seen.
     */
    @Test
    public void dialogCanBeShownAndDisposedWithoutTheAdministratorWindow() {
        MirthDialog dialog = new MirthDialog(null, "test", false) {
        };

        // setVisible(true) would block on a modal dialog, and false exercises the same dereference
        dialog.setVisible(false);
        dialog.dispose();

        assertFalse(dialog.isVisible());
    }
}
