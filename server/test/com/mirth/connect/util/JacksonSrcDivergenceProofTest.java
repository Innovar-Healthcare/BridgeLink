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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phase 26.7 D-02/D-04 divergence proof (Wave 2): demonstrates the concrete, falsifiable
 * jackson-core 2.14.3-to-2.18.10 {@code StreamReadConstraints} behavioral divergence through a
 * DEFAULT-vs-tuned comparison, modeled on {@link RhinoEs6DivergenceProofTest}.
 * <p>
 * <b>Recorded divergence (D-02, captured this session against the swapped jackson 2.18.10
 * jars):</b>
 * <pre>
 * payload: a synthetic 30,000,000-character base64-alphabet JSON string value (safely above
 *          jackson-core 2.18.10's default 20,000,000-character maxStringLength, safely below
 *          MirthJsonUtil.MAX_JSON_STRING_LEN's 100,000,000-character tuned ceiling)
 * DEFAULT: a plain {@code new ObjectMapper()} (default StreamReadConstraints) throws
 *          com.fasterxml.jackson.core.exc.StreamConstraintsException
 *          ("String value length (30000000) exceeds the maximum allowed (20000000, from
 *          `StreamReadConstraints.getMaxStringLength()`)")
 * TUNED:   {@code new ObjectMapper(MirthJsonUtil.tunedFactory())} parses the identical payload
 *          successfully, returning the exact original string content.
 * </pre>
 * On jackson-core 2.14.3 (the D-04 commit-1 baseline this project shipped before Wave 2)
 * {@code StreamReadConstraints} does not exist at all, so a JSON string token of this size
 * parses under EITHER factory -- there is no cap to hit. The divergence characterized here is
 * therefore genuinely new to 2.18.10: it exists because jackson-core 2.18.10 enforces a default
 * {@code maxStringLength} that 2.14.3 never had, and it is exactly the boundary
 * {@link MirthJsonUtil}'s D-01 tuning (raising ONLY {@code maxStringLength}, leaving every other
 * structural cap at its 2.18.10 default) exists to preserve compatibility across.
 * <p>
 * <b>Falsifiability discipline (D-02, Phase 23.1 precedent):</b> this test fails if the DEFAULT
 * factory silently parses the payload (the divergence would not be real), and fails if the
 * TUNED factory ever threw (the D-01 tuning would not be load-bearing). No
 * {@code org.junit.Assume} is used anywhere in this class.
 * <p>
 * This class mutates no shared/static state -- both {@link JsonFactory} instances constructed
 * here are local to each test method -- so there is nothing to restore in a {@code finally}
 * block even though {@code server/build.xml}'s {@code forkmode="perTest"} would isolate any such
 * mutation regardless. The test below therefore proves the divergence unconditionally on every
 * run, independent of whether {@code MirthJsonUtil.OBJECT_MAPPER} itself happens to be tuned.
 * <p>
 * <b>Task 2 one-shot manual proof on the actual production seam (captured 2026-08-25, NOT
 * persisted):</b> per D-04, the without-tuning divergence was additionally demonstrated directly
 * against {@code MirthJsonUtil.OBJECT_MAPPER} (not just the local factories above) by temporarily
 * reverting {@code OBJECT_MAPPER}'s construction from {@code new ObjectMapper(tunedFactory())}
 * back to {@code new ObjectMapper()}, recompiling, and re-running
 * {@code JacksonStreamReadConstraintsTest#largeJsonStringValueParsesThroughMirthJsonUtilFromJson}
 * (the ~30MB baseline test) in isolation:
 * <pre>
 * BEFORE (reverted, no tuning): 1 failure, 3 passes (4 tests total)
 *   com.fasterxml.jackson.core.exc.StreamConstraintsException: String value length (20051327)
 *   exceeds the maximum allowed (20000000, from `StreamReadConstraints.getMaxStringLength()`)
 *     at com.mirth.connect.util.MirthJsonUtil.fromJson(MirthJsonUtil.java:78)
 *   (the reported 20,051,327 is the incremental text-buffer length jackson had accumulated at
 *   the moment it crossed the 20,000,000-character default cap while streaming the 30,000,000
 *   -character payload -- not the full input length -- which is exactly the expected shape of a
 *   streaming StreamReadConstraints violation.)
 * AFTER (tuning restored byte-identical): 4 passes, 0 failures -- `git diff` against
 *   MirthJsonUtil.java showed zero difference, confirming the reverted state was never committed.
 * </pre>
 * This is the load-bearing proof that the D-01 tuning at the {@code OBJECT_MAPPER} seam is
 * necessary, not merely decorative: removing it reproduces the exact divergence characterized
 * generically above.
 */
public class JacksonSrcDivergenceProofTest {

    /**
     * Safely above jackson-core 2.18.10's default 20,000,000-character maxStringLength and
     * safely below {@link MirthJsonUtil#MAX_JSON_STRING_LEN}'s tuned 100,000,000-character
     * ceiling -- the exact D-01/D-02 boundary this proof demonstrates.
     */
    private static final int LARGE_VALUE_LENGTH = 30_000_000;

    private static final char[] BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    @Test
    public void defaultFactoryThrowsButTunedFactoryPassesOnTheSameOversizedStringPayload() throws Exception {
        String blob = generateSyntheticBase64Blob(LARGE_VALUE_LENGTH);
        String json = "{\"data\":\"" + blob + "\"}";

        // --- Run 1: a DEFAULT-constraints ObjectMapper (no D-01 tuning) ---
        String defaultOutcome;
        try {
            ObjectMapper defaultMapper = new ObjectMapper(new JsonFactory());
            JsonNode parsed = defaultMapper.readTree(json);
            fail("Expected the default-constraints ObjectMapper to throw "
                    + "StreamConstraintsException on a " + LARGE_VALUE_LENGTH
                    + "-character string value, but it parsed successfully to: "
                    + parsed.get("data").asText().length() + " chars");
            return; // unreachable, keeps the compiler happy about definite assignment
        } catch (StreamConstraintsException e) {
            defaultOutcome = "THROW:" + e.getClass().getName();
        }

        // --- Run 2: MirthJsonUtil's shared D-01 tuned factory ---
        ObjectMapper tunedMapper = new ObjectMapper(MirthJsonUtil.tunedFactory());
        JsonNode tunedParsed = tunedMapper.readTree(json);
        String tunedValue = tunedParsed.get("data").asText();
        String tunedOutcome = "LEN:" + tunedValue.length();

        // Falsifiable per D-02: fails if DEFAULT and TUNED ever produced the same outcome, and
        // fails if the tuned path's parsed content ever diverged from the original blob.
        assertNotEquals("DEFAULT and TUNED must diverge on the same oversized string payload",
                defaultOutcome, tunedOutcome);
        assertTrue("DEFAULT must throw (not silently truncate or reject some other way)",
                defaultOutcome.startsWith("THROW:"));
        assertEquals("TUNED factory must parse the full-length string value",
                LARGE_VALUE_LENGTH, tunedValue.length());
        assertEquals("TUNED factory's parsed content must equal the original blob exactly",
                blob, tunedValue);
    }

    /**
     * Structural caps (nesting depth, number length, name length) are deliberately left at their
     * jackson-core 2.18.10 defaults by {@link MirthJsonUtil#tunedFactory()} -- only
     * {@code maxStringLength} is raised (D-01). This asserts the DoS backstop is still intact:
     * a document whose nesting depth exceeds the 2.18.10 default (1,000) still throws through
     * the tuned factory, exactly as it would through an untuned one.
     */
    @Test
    public void tunedFactoryStillEnforcesDefaultStructuralCapsOnExcessiveNestingDepth() throws Exception {
        StringBuilder deeplyNested = new StringBuilder();
        int depth = 1_200; // above the 2.18.10 default maxNestingDepth of 1,000
        for (int i = 0; i < depth; i++) {
            deeplyNested.append("{\"a\":");
        }
        deeplyNested.append("1");
        for (int i = 0; i < depth; i++) {
            deeplyNested.append("}");
        }

        ObjectMapper tunedMapper = new ObjectMapper(MirthJsonUtil.tunedFactory());
        try {
            tunedMapper.readTree(deeplyNested.toString());
            fail("Expected the tuned factory to still enforce the default maxNestingDepth cap "
                    + "and throw on a document nested " + depth + " levels deep, but it parsed "
                    + "successfully -- the D-01 tuning must raise ONLY maxStringLength, leaving "
                    + "structural caps at their 2.18.10 defaults");
        } catch (StreamConstraintsException e) {
            // expected: structural cap backstop intact
        }
    }

    /**
     * Generates a synthetic, non-PHI base64-alphabet string of the requested length.
     * Deterministic, never read from a file, never real clinical data -- PHI safety per
     * CLAUDE.md.
     */
    private static String generateSyntheticBase64Blob(int length) {
        char[] chars = new char[length];
        int alphabetLength = BASE64_ALPHABET.length;
        for (int i = 0; i < length; i++) {
            chars[i] = BASE64_ALPHABET[i % alphabetLength];
        }
        return new String(chars);
    }
}
