/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * Tests for {@link Mirth#defaultEncodingMismatchWarning(Charset, String, String)} — the IRT-1914
 * startup warning that fires when the JVM default charset diverges from the host's native encoding
 * and the operator has not already pinned a default encoding.
 */
public class MirthTest {

    @Test
    public void mismatchWarning_sameCharsetReturnsNull() {
        assertNull(Mirth.defaultEncodingMismatchWarning(StandardCharsets.UTF_8, "UTF-8", null));
    }

    @Test
    public void mismatchWarning_canonicalMatchIgnoresCaseAndAlias() {
        // native.encoding reported with different case/alias is still the same charset -> no warning.
        assertNull(Mirth.defaultEncodingMismatchWarning(StandardCharsets.UTF_8, "utf-8", null));
        assertNull(Mirth.defaultEncodingMismatchWarning(Charset.forName("windows-1252"), "cp1252", null));
    }

    @Test
    public void mismatchWarning_nullNativeEncodingReturnsNull() {
        assertNull(Mirth.defaultEncodingMismatchWarning(StandardCharsets.UTF_8, null, null));
    }

    @Test
    public void mismatchWarning_blankNativeEncodingReturnsNull() {
        assertNull(Mirth.defaultEncodingMismatchWarning(StandardCharsets.UTF_8, "   ", null));
    }

    @Test
    public void mismatchWarning_divergenceWarnsAndNamesBoth() {
        String warning = Mirth.defaultEncodingMismatchWarning(StandardCharsets.UTF_8, "windows-1252", null);
        assertTrue("expected a warning", warning != null);
        assertTrue("warning should name the JVM default charset", warning.contains("UTF-8"));
        assertTrue("warning should name the native encoding", warning.contains("windows-1252"));
        assertTrue("warning should point at server.defaultencoding", warning.contains("server.defaultencoding"));
    }

    @Test
    public void mismatchWarning_unresolvableNativeEncodingStillWarns() {
        // An unresolvable native.encoding cannot be compared; surface it rather than swallow it.
        String warning = Mirth.defaultEncodingMismatchWarning(StandardCharsets.UTF_8, "not a charset!", null);
        assertTrue("expected a warning for an unresolvable native.encoding", warning != null);
        assertTrue(warning.contains("not a charset!"));
    }

    @Test
    public void mismatchWarning_configuredEncodingSuppressesWarning() {
        // The operator has already pinned a default encoding; the divergence is intentional and
        // handled, so do not warn (and do not misstate what DEFAULT_ENCODING connectors will use).
        assertNull(Mirth.defaultEncodingMismatchWarning(StandardCharsets.UTF_8, "windows-1252", "windows-1252"));
    }

    @Test
    public void mismatchWarning_blankConfiguredEncodingStillWarnsOnMismatch() {
        // A blank/whitespace configured value is treated as unset.
        String warning = Mirth.defaultEncodingMismatchWarning(StandardCharsets.UTF_8, "windows-1252", "   ");
        assertTrue("blank configured value should not suppress the warning", warning != null);
    }
}
