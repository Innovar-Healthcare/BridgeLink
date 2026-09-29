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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.model.purged.PurgedDocument;

/**
 * Phase 26.7 D-04 commit-1 characterization baseline (D-02/D-03): behavioral proof that a large
 * JSON string value and a populated {@code Map<String,String>} both round-trip through the real
 * production {@link MirthJsonUtil} REST-DTO seam ({@code toJson}/{@code fromJson}, the exact
 * {@code OBJECT_MAPPER} static that {@code ConfigurationServlet} and sibling servlets use), NOT a
 * freshly-constructed vanilla {@code ObjectMapper}.
 * <p>
 * <b>Baseline (this class, authored and committed GREEN against the on-disk jackson 2.14.3 jars,
 * before the 2.18.10 swap lands -- D-04 commit 1):</b> jackson-core before 2.15 has no
 * {@code StreamReadConstraints} at all, so a JSON string token of any practical size parses. The
 * Wave 2 target, jackson-core 2.18.10, enforces a 20,000,000-character default
 * {@code maxStringLength} more strictly per its own CVE-2026-68498 fix. The large-payload test
 * below is therefore red-capable: on 2.14.3 it is green because there is no cap to hit; once the
 * 2.18.10 jars land without the D-01 SRC tuning, this identical test throws
 * {@code StreamConstraintsException}. Wave 2 demonstrates that divergence (reverting the tuning
 * locally, observing the throw, and restoring it) rather than persisting a knowingly-red commit
 * here (D-04).
 * <p>
 * <b>Wave 2 status:</b> this suite now runs GREEN against the swapped jackson-core 2.18.10 jars
 * WITH the D-01 tuning applied at the production seam ({@code MirthJsonUtil.OBJECT_MAPPER},
 * constructed with {@code MirthJsonUtil.tunedFactory()}) -- the same assertions that
 * characterized the 2.14.3 baseline now characterize the tuned 2.18.10 behavior identically.
 * {@link JacksonSrcDivergenceProofTest} carries the demonstrated default-vs-tuned divergence
 * proof separately, so the tuning's necessity stays falsifiable without persisting a red state
 * here.
 * <p>
 * <b>Falsifiability discipline (D-02, Phase 23.1 precedent):</b> every assertion below is a
 * concrete equality/length check against the actual seam output. None uses
 * {@code org.junit.Assume} or a try/catch that swallows a real regression into a pass.
 * <p>
 * <b>SC-3 introspection finding (RESEARCH.md Pitfall 3):</b> a scan of {@code server/src},
 * {@code client/src}, {@code command/src}, and {@code donkey/src} for {@code @JsonCreator},
 * {@code PropertyNamingStrategy}, {@code @JsonNaming}, and {@code @JsonAlias} returns zero
 * matches:
 * <pre>
 * git grep -n -e '&#64;JsonCreator' -e 'PropertyNamingStrategy' -e '&#64;JsonNaming' -e '&#64;JsonAlias' \
 *     -- 'server/src' 'client/src' 'command/src' 'donkey/src'
 * (no output; exit 1)
 * </pre>
 * BridgeLink's own models therefore carry almost no exposure to the 2.18 property-introspection
 * / creator-detection rewrite. Deserialization in the four src trees above is overwhelmingly
 * {@code readTree} (JsonNode navigation) plus the single {@code Map<String,String>}
 * {@code readValue} exercised above. The one non-default introspection configuration in the
 * codebase --
 * {@code DefaultUsageController}'s {@code setVisibility(PropertyAccessor.FIELD, Visibility.ANY)}
 * for {@code PurgedDocument} serialization (server/src/com/mirth/connect/server/controllers/
 * DefaultUsageController.java:98-100) -- is characterized below
 * ({@link #purgedDocumentFieldVisibilityRoundTripIsStable()}); the expected, and confirmed,
 * result is NO divergence, which is itself the SC-3 introspection finding: with zero creator/
 * naming/alias annotations anywhere in BridgeLink's own models, the 2.18 introspection rewrite
 * has minimal bite for this codebase.
 */
public class JacksonStreamReadConstraintsTest {

    /**
     * A JSON string-value length safely above the 2.18.10 default 20,000,000-character
     * {@code maxStringLength} cap and safely below the phase's tuned 100MB ceiling
     * ({@code MirthJsonUtil.MAX_JSON_STRING_LEN}, landed in Wave 2) -- the exact D-01/D-02
     * boundary this baseline pins.
     */
    private static final int LARGE_VALUE_LENGTH = 30_000_000;

    /** Base64-alphabet characters only, so no JSON escaping is needed in the generated payload. */
    private static final char[] BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    /**
     * Pins the D-01 greppable ceiling by test: {@code MirthJsonUtil.MAX_JSON_STRING_LEN} must be
     * exactly 100,000,000 -- the deliberate, documented, bounded string-length cap raised from
     * jackson-core 2.18.10's own default (20,000,000) to preserve 2.14.3-era compatibility with
     * large base64-encoded clinical attachments, landed as part of the Wave 2 atomic swap.
     */
    @Test
    public void maxJsonStringLenConstantIsPinnedAtTheDocumentedCeiling() {
        assertEquals("MirthJsonUtil.MAX_JSON_STRING_LEN must stay pinned at the documented, "
                + "greppable D-01 ceiling", 100_000_000, MirthJsonUtil.MAX_JSON_STRING_LEN);
    }

    @Test
    public void largeJsonStringValueParsesThroughMirthJsonUtilFromJson() throws JsonProcessingException {
        String blob = generateSyntheticBase64Blob(LARGE_VALUE_LENGTH);
        assertEquals("Sanity check: generated blob must be exactly the requested length",
                LARGE_VALUE_LENGTH, blob.length());

        String json = "{\"data\":\"" + blob + "\"}";

        Map<String, String> parsed = MirthJsonUtil.fromJson(json, new TypeReference<Map<String, String>>() {});

        assertNotNull("MirthJsonUtil.fromJson must return a non-null map for a well-formed document", parsed);
        assertEquals("Parsed value must contain exactly one key", 1, parsed.size());
        String parsedValue = parsed.get("data");
        assertNotNull("Parsed value for key 'data' must not be null", parsedValue);
        assertEquals("Parsed value length must equal the generated blob length (on 2.14.3 there is "
                + "no StreamReadConstraints cap to truncate or reject it; on an untuned 2.18.10 this "
                + "same assertion is unreachable because fromJson throws StreamConstraintsException "
                + "first -- the D-02 divergence)", LARGE_VALUE_LENGTH, parsedValue.length());
        assertEquals("Parsed value content must equal the generated blob exactly", blob, parsedValue);
    }

    @Test
    public void mapRoundTripsThroughMirthJsonUtilToJsonThenFromJson() throws JsonProcessingException {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("plainKey", "plainValue");
        input.put("unicodeKey-éüñ", "unicodeValue-日本語-😀");
        input.put("specialChars", "line1\nline2\ttabbed\"quoted\"\\backslash\\");
        input.put("emptyValue", "");
        input.put("numericLikeValue", "0012345");

        String json = MirthJsonUtil.toJson(input);
        Map<String, String> output = MirthJsonUtil.fromJson(json, new TypeReference<Map<String, String>>() {});

        assertEquals("Map<String,String> must round-trip content-stable through the real "
                + "MirthJsonUtil.toJson/fromJson seam (the exact ConfigurationServlet REST-DTO seam) "
                + "-- D-03", input, output);
    }

    @Test
    public void purgedDocumentFieldVisibilityRoundTripIsStable() throws JsonProcessingException {
        PurgedDocument input = buildSyntheticPurgedDocument();

        // Mirrors DefaultUsageController.createUsageStats (lines 98-100): the only non-default
        // Jackson introspection configuration in the codebase.
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);

        String json = mapper.writeValueAsString(input);
        PurgedDocument output = mapper.readValue(json, PurgedDocument.class);

        assertNotNull("Round-tripped PurgedDocument must not be null", output);
        assertEquals("mirthVersion must round-trip unchanged", input.getMirthVersion(), output.getMirthVersion());
        assertEquals("serverId must round-trip unchanged", input.getServerId(), output.getServerId());
        assertEquals("databaseType must round-trip unchanged", input.getDatabaseType(), output.getDatabaseType());
        assertEquals("users must round-trip unchanged", input.getUsers(), output.getUsers());
        assertEquals("invalidChannels must round-trip unchanged", input.getInvalidChannels(), output.getInvalidChannels());
        assertEquals("serverSpecs must round-trip unchanged", input.getServerSpecs(), output.getServerSpecs());
        assertEquals("globalScripts must round-trip unchanged", input.getGlobalScripts(), output.getGlobalScripts());
        assertEquals("channels must round-trip unchanged", input.getChannels(), output.getChannels());
    }

    /**
     * Builds a synthetic, non-PHI {@link PurgedDocument} shaped like real usage-stats output
     * (server metadata + a couple of representative field types), never real clinical or customer
     * data -- PHI safety per CLAUDE.md.
     */
    private static PurgedDocument buildSyntheticPurgedDocument() {
        PurgedDocument document = new PurgedDocument();
        document.setMirthVersion("26.9.0");
        document.setServerId("synthetic-server-id-0000");
        document.setDatabaseType("derby");
        document.setUsers(3);
        document.setInvalidChannels(0);

        Map<String, Object> serverSpecs = new LinkedHashMap<>();
        serverSpecs.put("os.name", "Linux");
        serverSpecs.put("java.version", "17.0.10");
        serverSpecs.put("cpu.count", 8);
        document.setServerSpecs(serverSpecs);

        Map<String, Integer> globalScripts = new LinkedHashMap<>();
        globalScripts.put("deploy", 1);
        globalScripts.put("undeploy", 1);
        document.setGlobalScripts(globalScripts);

        List<Map<String, Object>> channels = new ArrayList<>();
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("id", "synthetic-channel-id-0001");
        channel.put("enabled", true);
        channel.put("sourceConnectorType", "vm");
        channels.add(channel);
        document.setChannels(channels);

        return document;
    }

    /**
     * Generates a synthetic, non-PHI base64-alphabet string of the requested length. Content is
     * deterministic (cycles the base64 alphabet), never read from a file, and never contains real
     * clinical data -- PHI safety per CLAUDE.md.
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
