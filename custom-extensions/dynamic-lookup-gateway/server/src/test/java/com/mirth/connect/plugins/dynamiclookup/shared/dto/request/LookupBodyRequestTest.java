/*
 * Copyright (c) Innovar Healthcare. All rights reserved.
 * IRT-1997: revert-proof regression coverage for the body-based lookup key/name endpoints.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.dto.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.mirth.connect.plugins.dynamiclookup.shared.util.JsonUtils;

/**
 * Pins IRT-1997: lookup keys and group names that contain characters Jetty 12 rejects in a URL
 * path segment ({@code /}, {@code %}, {@code \}, tab, CR, LF) must survive transport in the JSON
 * request body. The fix moves the key/name out of the URL path
 * ({@code @Path(".../values/{key}")}) and into the body of new POST endpoints
 * ({@code getValueBody}, {@code setValueBody}, {@code deleteValueBody}, {@code getGroupByNameBody}),
 * carried by {@link LookupKeyRequest}, {@link LookupKeyValueRequest} and {@link LookupNameRequest}.
 *
 * <p>This test pins the DTO transport contract those endpoints rely on: a key/name round-trips
 * through {@code toJson}/{@code fromJson} byte-for-byte, and blank input is rejected by
 * {@code validate()}. It does not exercise the HTTP layer itself — that a real Jetty now accepts
 * the request is covered by the CI matrix build and the manual Administrator check in the ticket.
 */
public class LookupBodyRequestTest {

    /** The exact characters the ticket verified Jetty rejects in a path segment, plus a realistic key. */
    private static final String[] HOSTILE_KEYS = {
            "file:consentExceptions/ISCpatient_exception_20260519.csv", // the reported case: '/'
            "50%off",                                                   // '%'
            "a\\b",                                                     // '\'
            "line1\tline2",                                             // tab
            "line1\rline2",                                             // CR
            "line1\nline2",                                             // LF
            ".",                                                        // dot-segment defect
            ".."                                                        // dot-segment defect
    };

    @Test
    public void keyRequest_roundTripsHostileKeys() throws Exception {
        for (String key : HOSTILE_KEYS) {
            LookupKeyRequest request = new LookupKeyRequest();
            request.setKey(key);

            LookupKeyRequest parsed = JsonUtils.fromJson(JsonUtils.toJson(request), LookupKeyRequest.class);

            assertEquals("Key must survive JSON body round-trip unchanged", key, parsed.getKey());
            parsed.validate(); // must not throw for a non-blank key
        }
    }

    @Test
    public void keyValueRequest_roundTripsHostileKeysAndJsonValue() throws Exception {
        String jsonValue = "{\"path\":\"a/b\",\"pct\":\"50%\"}"; // a JSON-typed value, itself full of hostile chars

        for (String key : HOSTILE_KEYS) {
            LookupKeyValueRequest request = new LookupKeyValueRequest();
            request.setKey(key);
            request.setValue(jsonValue);

            LookupKeyValueRequest parsed = JsonUtils.fromJson(JsonUtils.toJson(request), LookupKeyValueRequest.class);

            assertEquals("Key must survive JSON body round-trip unchanged", key, parsed.getKey());
            assertEquals("Value must survive JSON body round-trip unchanged", jsonValue, parsed.getValue());
            parsed.validate();
        }
    }

    @Test
    public void nameRequest_roundTripsHostileNames() throws Exception {
        for (String name : HOSTILE_KEYS) {
            LookupNameRequest request = new LookupNameRequest();
            request.setName(name);

            LookupNameRequest parsed = JsonUtils.fromJson(JsonUtils.toJson(request), LookupNameRequest.class);

            assertEquals("Name must survive JSON body round-trip unchanged", name, parsed.getName());
            parsed.validate();
        }
    }

    @Test
    public void keyRequest_rejectsOnlyNullOrEmptyKey() {
        // Only null and "" are rejected...
        for (String rejected : new String[] { null, "" }) {
            try {
                LookupKeyRequest r = new LookupKeyRequest();
                r.setKey(rejected);
                r.validate();
                fail("Expected IllegalArgumentException for null/empty key: [" + rejected + "]");
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void keyRequest_acceptsWhitespaceOnlyKey() throws Exception {
        // ...but a whitespace/control-only key must be accepted: it is a legitimate stored key and
        // must remain deletable/editable. Rejecting it would reintroduce IRT-1997's symptom.
        for (String key : new String[] { " ", "\t", "\n" }) {
            LookupKeyRequest r = new LookupKeyRequest();
            r.setKey(key);
            r.validate(); // must not throw

            LookupKeyRequest parsed = JsonUtils.fromJson(JsonUtils.toJson(r), LookupKeyRequest.class);
            assertEquals("Whitespace-only key must survive round-trip", key, parsed.getKey());
        }
    }

    @Test
    public void keyValueRequest_acceptsWhitespaceOnlyKey() {
        LookupKeyValueRequest r = new LookupKeyValueRequest();
        r.setKey("\t");
        r.setValue("v");
        r.validate(); // whitespace-only key is fine
    }

    @Test
    public void keyValueRequest_rejectsEmptyKeyOrBlankValue() {
        // Empty key
        try {
            LookupKeyValueRequest r = new LookupKeyValueRequest();
            r.setKey("");
            r.setValue("v");
            r.validate();
            fail("Expected IllegalArgumentException for empty key");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        // Blank value (value keeps the stricter blank check, matching LookupValueRequest)
        for (String blankValue : new String[] { null, "", "   " }) {
            try {
                LookupKeyValueRequest r = new LookupKeyValueRequest();
                r.setKey("k");
                r.setValue(blankValue);
                r.validate();
                fail("Expected IllegalArgumentException for blank value: [" + blankValue + "]");
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void nameRequest_rejectsBlankName() {
        for (String blank : new String[] { null, "", "   " }) {
            try {
                LookupNameRequest r = new LookupNameRequest();
                r.setName(blank);
                r.validate();
                fail("Expected IllegalArgumentException for blank name: [" + blank + "]");
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }
}
