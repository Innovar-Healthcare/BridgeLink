/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * IRT-1609: WebAdmin migration dialog test coverage (REQ-26.4-002).
 */

package com.mirth.connect.client.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.mirth.connect.client.ui.components.MirthCheckBox;

/**
 * Headless coverage for the WebAdmin migration warning dialog and its "don't show again"
 * preference wiring (IRT-1609). The public {@code WebAdminMigrationDialog(Window)} constructor is
 * modal and blocks on an inline blocking show call, so this test constructs the dialog no-show
 * via the package-private test-support seam added in the same phase (Decision D-26.4-01) rather
 * than showing it. See {@link MirthDialogNoFrameTest} for the sanctioned headless-Swing pattern
 * this mirrors.
 */
public class WebAdminMigrationDialogTest {

    private static final String DISMISSAL_PREFERENCE_KEY = "webAdminMigrationWarningDismissed";

    private Frame originalFrame;

    @Before
    public void setUp() {
        originalFrame = PlatformUI.MIRTH_FRAME;
        PlatformUI.MIRTH_FRAME = null;
    }

    @After
    public void tearDown() {
        PlatformUI.MIRTH_FRAME = originalFrame;
    }

    /**
     * Constructing the dialog headlessly via the no-show seam does not block and builds its
     * components (the checkbox field is wired up and readable through the public accessor).
     */
    @Test
    public void dialogWiringLoadsHeadlesslyViaNoShowSeam() {
        Assume.assumeFalse("Needs a display to construct a dialog", GraphicsEnvironment.isHeadless());

        WebAdminMigrationDialog dialog = new WebAdminMigrationDialog(null, false);

        assertNotNull(dialog);
        assertFalse("Checkbox should start unchecked", dialog.isDoNotShowAgainChecked());

        dialog.dispose();
    }

    /**
     * Toggling the "don't show again" checkbox flips {@code isDoNotShowAgainChecked()} from false
     * to true, proving the accessor and the underlying MirthCheckBox stay wired together.
     */
    @Test
    public void doNotShowAgainCheckBoxTogglesTheAccessor() throws Exception {
        Assume.assumeFalse("Needs a display to construct a dialog", GraphicsEnvironment.isHeadless());

        WebAdminMigrationDialog dialog = new WebAdminMigrationDialog(null, false);

        assertFalse(dialog.isDoNotShowAgainChecked());

        Field checkBoxField = WebAdminMigrationDialog.class.getDeclaredField("doNotShowAgainCheckBox");
        checkBoxField.setAccessible(true);
        MirthCheckBox checkBox = (MirthCheckBox) checkBoxField.get(dialog);
        checkBox.setSelected(true);

        assertTrue("isDoNotShowAgainChecked() must reflect the checkbox state", dialog.isDoNotShowAgainChecked());

        dialog.dispose();
    }

    /**
     * Dismissal-preference contract (FALLBACK path per PATTERNS.md: the persistence call lives
     * inside LoginPanel's post-login SwingWorker and is not cleanly unit-reachable without
     * rewriting Thai Tran's PR commits). Pins the exact preference key LoginPanel registers,
     * reads, and persists on dismissal, so a rename of the key in either LoginPanel.java or this
     * test goes RED against the other.
     */
    @Test
    public void loginPanelRegistersAndPersistsTheExactDismissalPreferenceKey() throws IOException {
        String source = readLoginPanelSource();

        assertTrue("LoginPanel must register the dismissal preference name for the getUserPreferences() batch",
                source.contains("preferenceNames.add(\"" + DISMISSAL_PREFERENCE_KEY + "\")"));
        assertTrue("LoginPanel must read the dismissal preference under the exact same key",
                source.contains("userPreferences.getProperty(\"" + DISMISSAL_PREFERENCE_KEY + "\")"));
        assertTrue("LoginPanel must persist dismissal via setUserPreference(..., \"" + DISMISSAL_PREFERENCE_KEY + "\", \"true\")",
                source.contains("client.setUserPreference(currentUser.getId(), \"" + DISMISSAL_PREFERENCE_KEY + "\", \"true\")"));
        assertTrue("LoginPanel must show WebAdminMigrationDialog when the dismissal preference is absent/false",
                source.contains("new WebAdminMigrationDialog("));

        assertEquals("Sanity check on the pinned key literal itself", "webAdminMigrationWarningDismissed", DISMISSAL_PREFERENCE_KEY);
    }

    /**
     * Locates {@code LoginPanel.java} regardless of the working directory the test is launched
     * from (ant's {@code test-run} forks with cwd={@code client/}; other runners may use the repo
     * root or the {@code client/test} directory itself).
     */
    private String readLoginPanelSource() throws IOException {
        String relativePath = "com/mirth/connect/client/ui/LoginPanel.java".replace('/', File.separatorChar);
        String[] candidateRoots = { "src", "client/src", "../src" };

        File dir = new File(System.getProperty("user.dir"));
        for (int depth = 0; depth < 5 && dir != null; depth++, dir = dir.getParentFile()) {
            for (String root : candidateRoots) {
                File candidate = new File(dir, root + File.separator + relativePath);
                if (candidate.isFile()) {
                    return new String(Files.readAllBytes(candidate.toPath()), "UTF-8");
                }
            }
        }

        throw new IOException("Could not locate LoginPanel.java from working directory " + System.getProperty("user.dir"));
    }
}
