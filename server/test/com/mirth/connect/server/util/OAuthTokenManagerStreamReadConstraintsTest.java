/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */
package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.util.MirthJsonUtil;

/**
 * Closes {@code 26.7-UAT.md} gap {@code G-26.7-1}. This class drives the LIVE
 * {@link OAuthTokenManager} parse path rather than a re-implementation, reaching
 * {@code MAPPER.readTree(responseBody)} at {@code OAuthTokenManager.java:118} -- the one OAuth
 * path that parses an unbounded remote HTTP body because {@code EntityUtils.toString} at
 * {@code OAuthTokenManager.java:111} applies no size cap of its own.
 *
 * <p>The seam is deliberately SRC-untuned: D-01 scoped the raised {@code maxStringLength}
 * ceiling to {@code MirthJsonUtil} and {@code JsonXmlUtil} only, so this class characterizes the
 * configuration that actually ships and pins the WR-01 follow-on debt by assertion rather than
 * by prose (see {@link #oAuthTokenManagerMapperRunsOnUntunedStreamReadConstraintDefaults()}).</p>
 *
 * <p>Every over-limit expectation in this class is unsatisfiable on jackson 2.14.3, which shipped
 * no {@code StreamReadConstraints} at all -- this class is therefore the recorded D-02 divergence
 * (Phase 23.1 precedent) reproduced at the OAuth seam rather than at the {@code MirthJsonUtil}
 * seam where {@code JacksonStreamReadConstraintsTest} originally demonstrated it.</p>
 *
 * <p>Every fixture value below is synthetic: either a fabricated {@code dummy-} prefixed literal
 * or a single-character fill blob. None is a real credential and none is PHI, per the project
 * rule that message content is PHI.</p>
 */
public class OAuthTokenManagerStreamReadConstraintsTest {

    private static final String TOKEN_URL = "https://login.example.com/oauth2/v2.0/token";
    private static final String DUMMY_ACCESS_TOKEN = "dummy-access-token-26p7";

    /** jackson-core 2.18.10's default {@code StreamReadConstraints} nesting-depth ceiling. */
    private static final int MAX_NESTING_DEPTH = 1000;
    /** jackson-core 2.18.10's default {@code StreamReadConstraints} string-length ceiling. */
    private static final int MAX_STRING_LENGTH = 20_000_000;
    /** jackson-core 2.18.10's default {@code StreamReadConstraints} number-length ceiling. */
    private static final int MAX_NUMBER_LENGTH = 1000;

    // -----------------------------------------------------------------------
    // Test 1: the seam is proven UNTUNED by assertion (D-01 scope boundary)
    // -----------------------------------------------------------------------

    @Test
    public void oAuthTokenManagerMapperRunsOnUntunedStreamReadConstraintDefaults() throws Exception {
        Field mapperField = OAuthTokenManager.class.getDeclaredField("MAPPER");
        mapperField.setAccessible(true);
        ObjectMapper mapper = (ObjectMapper) mapperField.get(null);

        StreamReadConstraints constraints = mapper.getFactory().streamReadConstraints();

        assertEquals("OAuthTokenManager's static MAPPER must run on jackson 2.18.10's default "
                + "maxNestingDepth (no D-01 tuning reaches this seam)", MAX_NESTING_DEPTH,
                constraints.getMaxNestingDepth());
        assertEquals("OAuthTokenManager's static MAPPER must run on jackson 2.18.10's default "
                + "maxStringLength (no D-01 tuning reaches this seam)", MAX_STRING_LENGTH,
                constraints.getMaxStringLength());
        assertEquals("OAuthTokenManager's static MAPPER must run on jackson 2.18.10's default "
                + "maxNumberLength (no D-01 tuning reaches this seam)", MAX_NUMBER_LENGTH,
                constraints.getMaxNumberLength());

        assertTrue("MirthJsonUtil.MAX_JSON_STRING_LEN must remain strictly larger than the OAuth "
                + "seam's untuned maxStringLength: the OAuth seam did NOT receive the D-01 tuning, "
                + "and this divergence is tracked WR-01 follow-on debt, not an oversight",
                MirthJsonUtil.MAX_JSON_STRING_LEN > constraints.getMaxStringLength());
    }

    // -----------------------------------------------------------------------
    // Nesting depth dimension (tests 2-3)
    // -----------------------------------------------------------------------

    @Test
    public void tokenResponseAtTheMaximumNestingDepthStillYieldsAccessToken() throws Exception {
        String token = fetchToken(tokenBodyWithNestedExtra(MAX_NESTING_DEPTH - 1));

        assertEquals("A token response at exactly the maximum total document nesting depth "
                + "(1000) must still yield the access token", DUMMY_ACCESS_TOKEN, token);
    }

    @Test
    public void tokenResponseOneLevelOverTheMaximumNestingDepthThrowsStreamConstraintsException() throws Exception {
        Throwable thrown = captureFetchFailure(200, tokenBodyWithNestedExtra(MAX_NESTING_DEPTH));

        assertEquals("StreamConstraintsException must propagate out of getAccessToken() "
                + "unwrapped on the 200 path -- nothing between OAuthTokenManager.java:118 and "
                + "the method boundary catches it", StreamConstraintsException.class, thrown.getClass());
        assertTrue("Message must name the exceeded nesting depth",
                thrown.getMessage().contains("Document nesting depth (1001)"));
        assertTrue("Message must name the limit accessor",
                thrown.getMessage().contains("getMaxNestingDepth"));
    }

    // -----------------------------------------------------------------------
    // String length dimension (tests 4-5)
    // -----------------------------------------------------------------------

    @Test
    public void accessTokenJustUnderTheMaximumStringLengthStillParses() throws Exception {
        String token = fetchToken(tokenBodyWithAccessTokenOfLength(MAX_STRING_LENGTH - 1));

        // Do NOT assertEquals two 20-million-character strings: a failure would build an
        // unusable failure message. Assert length and spot-check the boundary characters
        // instead.
        assertEquals("Returned access_token must be exactly one character under the default "
                + "maxStringLength ceiling", MAX_STRING_LENGTH - 1, token.length());
        assertEquals("First character of the fill blob must be preserved", 'A', token.charAt(0));
        assertEquals("Last character of the fill blob must be preserved", 'A',
                token.charAt(token.length() - 1));
    }

    @Test
    public void accessTokenOverTheMaximumStringLengthThrowsStreamConstraintsException() throws Exception {
        Throwable thrown = captureFetchFailure(200, tokenBodyWithAccessTokenOfLength(MAX_STRING_LENGTH + 1));

        assertEquals("StreamConstraintsException must propagate out of getAccessToken() "
                + "unwrapped on the 200 path", StreamConstraintsException.class, thrown.getClass());
        assertTrue("Message must name the exceeded string length",
                thrown.getMessage().contains("String value length (20000001)"));
        assertTrue("Message must name the limit accessor",
                thrown.getMessage().contains("getMaxStringLength"));
    }

    // -----------------------------------------------------------------------
    // Number length dimension (tests 6-7)
    // -----------------------------------------------------------------------

    /**
     * Observed side effect (recorded, not asserted, and no production code changed for it):
     * {@code json.path("expires_in").asInt(3600)} at {@code OAuthTokenManager.java:124} silently
     * truncates a value this large, and {@code scheduleRefresh} at {@code :137} floors the
     * resulting delay at 10 seconds, so token extraction itself is unaffected by the digit count.
     */
    @Test
    public void expiresInAtTheMaximumNumberLengthStillYieldsAccessToken() throws Exception {
        String token = fetchToken(tokenBodyWithExpiresInDigits(MAX_NUMBER_LENGTH));

        assertEquals("A token response with expires_in at exactly the maximum number length "
                + "(1000 digits) must still yield the access token", DUMMY_ACCESS_TOKEN, token);
    }

    @Test
    public void expiresInOverTheMaximumNumberLengthThrowsStreamConstraintsException() throws Exception {
        Throwable thrown = captureFetchFailure(200, tokenBodyWithExpiresInDigits(MAX_NUMBER_LENGTH + 1));

        assertEquals("StreamConstraintsException must propagate out of getAccessToken() "
                + "unwrapped on the 200 path", StreamConstraintsException.class, thrown.getClass());
        assertTrue("Message must name the exceeded number length",
                thrown.getMessage().contains("Number value length (1001)"));
        assertTrue("Message must name the limit accessor",
                thrown.getMessage().contains("getMaxNumberLength"));
    }

    // -----------------------------------------------------------------------
    // Test 8: the pinned non-200 swallow behavior
    // -----------------------------------------------------------------------

    /**
     * Pins the failure mode for the non-200 path: {@code extractErrorDescription} at
     * {@code OAuthTokenManager.java:152} catches every exception thrown by its own
     * {@code MAPPER.readTree} call and returns the raw response body unchanged, so an over-limit
     * error body does not surface a constraint failure at all -- it surfaces a plain
     * {@code java.lang.Exception} whose message embeds the entire unbounded remote body. This is
     * characterized here, not fixed; the finding is filed as WR-01 adjacent follow-on debt.
     */
    @Test
    public void oversizedErrorBodyIsEchoedVerbatimBecauseTheConstraintFailureIsSwallowed() throws Exception {
        String overLimitBody = tokenBodyWithNestedExtra(MAX_NESTING_DEPTH);
        Throwable thrown = captureFetchFailure(400, overLimitBody);

        assertEquals("The non-200 path must surface a plain java.lang.Exception, NOT the "
                + "constraints exception, because extractErrorDescription's catch-all swallows it "
                + "and returns the raw body instead", Exception.class, thrown.getClass());
        assertTrue("Message must name the HTTP status", thrown.getMessage().contains("HTTP 400"));
        assertTrue("Message must name the token endpoint URL",
                thrown.getMessage().contains(TOKEN_URL));
        assertTrue("Message must echo the raw remote body verbatim (a distinctive leading "
                + "fragment of the over-limit body)", thrown.getMessage().contains(DUMMY_ACCESS_TOKEN));
    }

    // -----------------------------------------------------------------------
    // Fixture builders -- all synthetic and non-PHI
    // -----------------------------------------------------------------------

    /** Synthetic, non-PHI OAuth client credentials -- never a real tenant. */
    private OAuthTokenManager newManager() {
        return new OAuthTokenManager(TOKEN_URL, "dummy-client-id", "dummy-client-secret", "dummy-scope/.default");
    }

    /** Replicated verbatim from {@link OAuthTokenManagerTest#buildMockHttpClient}. */
    private CloseableHttpClient buildMockHttpClient(int statusCode, String responseBody) throws Exception {
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);

        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new java.io.ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)));
        entity.setContentLength(responseBody.getBytes(StandardCharsets.UTF_8).length);

        when(mockStatusLine.getStatusCode()).thenReturn(statusCode);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockResponse.getEntity()).thenReturn(entity);
        when(mockClient.execute(any(HttpUriRequest.class))).thenReturn(mockResponse);

        return mockClient;
    }

    /**
     * Drives the real production entry point end to end for a 200 response and returns the
     * extracted access token. Must NOT catch anything: a real regression has to propagate and
     * redden this test.
     */
    private String fetchToken(String body) throws Exception {
        CloseableHttpClient mockClient = buildMockHttpClient(200, body);
        OAuthTokenManager manager = newManager();
        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient);
            return manager.getAccessToken();
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Drives the real production entry point end to end for the given status code and body,
     * captures whatever is thrown, and returns it for the caller to assert its exact type and
     * message on. This is the one permitted catch shape in this class -- deliberate
     * capture-then-assert, never a swallow.
     */
    private Throwable captureFetchFailure(int statusCode, String body) throws Exception {
        CloseableHttpClient mockClient = buildMockHttpClient(statusCode, body);
        OAuthTokenManager manager = newManager();
        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient);
            try {
                manager.getAccessToken();
            } catch (Throwable t) {
                return t;
            }
            fail("Expected getAccessToken() to throw for statusCode=" + statusCode
                    + " with a boundary-exceeding payload");
            return null; // unreachable
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Builds a token-endpoint body whose top-level object carries {@code access_token},
     * {@code token_type}, {@code expires_in}, and an {@code extra} key wrapping
     * {@code extraDepth} levels of nested single-key objects around a short string literal.
     *
     * <p><b>Nesting arithmetic (the single most error-prone part of this class):</b> the TOTAL
     * document nesting depth is {@code extraDepth + 1} -- the top-level object itself counts as
     * nesting level 1, and each nested wrapper below it adds one more level. Empirically
     * confirmed at plan time against the on-disk 2.18.10 jars: {@code extraDepth} 999 (total
     * depth 1000) parses; {@code extraDepth} 1000 (total depth 1001) throws.</p>
     */
    private String tokenBodyWithNestedExtra(int extraDepth) {
        StringBuilder nested = new StringBuilder();
        for (int i = 0; i < extraDepth; i++) {
            nested.append("{\"n\":");
        }
        nested.append("\"leaf\"");
        for (int i = 0; i < extraDepth; i++) {
            nested.append('}');
        }
        return "{\"access_token\":\"" + DUMMY_ACCESS_TOKEN + "\",\"token_type\":\"Bearer\","
                + "\"expires_in\":3600,\"extra\":" + nested + "}";
    }

    /**
     * Builds a token-endpoint body whose {@code access_token} value is {@code length}
     * repetitions of a single base64-alphabet fill character ({@code 'A'}), plus the usual
     * {@code token_type} and {@code expires_in} fields. Built with a {@code char[]} fill, not
     * string concatenation in a loop, so a 20-million-character fixture stays fast.
     */
    private String tokenBodyWithAccessTokenOfLength(int length) {
        char[] fill = new char[length];
        Arrays.fill(fill, 'A');
        String accessToken = new String(fill);
        return "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
    }

    /**
     * Builds a token-endpoint body with the standard {@code access_token} and an
     * {@code expires_in} value of {@code digits} repeated digit characters ({@code '9'}).
     */
    private String tokenBodyWithExpiresInDigits(int digits) {
        char[] fill = new char[digits];
        Arrays.fill(fill, '9');
        String expiresIn = new String(fill);
        return "{\"access_token\":\"" + DUMMY_ACCESS_TOKEN + "\",\"token_type\":\"Bearer\","
                + "\"expires_in\":" + expiresIn + "}";
    }
}
