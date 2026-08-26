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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.server.util.OAuthCredentials.OAuthToken;

/**
 * Closes {@code 26.7-VERIFICATION.md} gap missing item 1 (the OAuth-token leg of ROADMAP SC-3
 * and CONTEXT.md D-03). This class drives the REAL production Jackson seams rather than a
 * vanilla {@link ObjectMapper} built inside the test:
 * <ul>
 * <li>{@code OAuthCredentials.OAuthToken(String)} (OAuthCredentials.java:170-181), which
 * constructs its OWN {@code new ObjectMapper(new JsonFactory())} at line 171 and calls
 * {@code readTree}.</li>
 * <li>{@code OAuthTokenManager}'s private static {@code MAPPER} (OAuthTokenManager.java:44),
 * reached through the private {@code fetchAndCache()} at line 118 and {@code
 * extractErrorDescription} at line 154, exercised here with the same static-{@code HttpClients}
 * -mock pattern {@code OAuthTokenManagerTest} already uses.</li>
 * </ul>
 * Both mappers stay SRC-untuned per WR-01, and that is deliberate and out of scope here: every
 * fixture in this class stays far below jackson 2.18.10's 20,000,000-character default {@code
 * maxStringLength}. Every token, client id, client secret, scope, and refresh token below is a
 * fabricated {@code dummy-} prefixed literal -- never a real credential and never PHI.
 */
public class JacksonOAuthTokenRoundTripTest {

    private static final String TOKEN_URL = "https://login.example.com/oauth2/v2.0/token";

    // -----------------------------------------------------------------------
    // OAuthCredentials.OAuthToken(String) -- the constructor-owned mapper seam
    // -----------------------------------------------------------------------

    @Test
    public void oAuthTokenBindsAllThreeFieldsThroughProductionSeam() {
        String json = "{\"access_token\":\"dummy-access-token-26p7\",\"token_type\":\"Bearer\","
                + "\"expires_in\":3600}";

        OAuthToken token = new OAuthToken(json);

        assertEquals("access_token must bind through OAuthCredentials' own ObjectMapper seam",
                "dummy-access-token-26p7", token.getAccessToken());
        assertEquals("token_type must bind through OAuthCredentials' own ObjectMapper seam",
                "Bearer", token.getTokenType());
        assertEquals("expires_in must bind through OAuthCredentials' own ObjectMapper seam",
                3600L, token.getExpiresIn());
    }

    @Test
    public void oAuthTokenRoundTripsFieldStableThroughReserialization() throws Exception {
        String json = "{\"access_token\":\"dummy-access-token-26p7\",\"token_type\":\"Bearer\","
                + "\"expires_in\":3600}";

        OAuthToken first = new OAuthToken(json);

        // Regenerate the wire document from the bound fields, with the identical wire key
        // names. This test-side ObjectMapper only regenerates the document; it is never the
        // assertion seam -- the assertions below compare two OAuthToken instances, each bound
        // through OAuthCredentials' own production constructor.
        Map<String, Object> regenerated = new LinkedHashMap<>();
        regenerated.put("access_token", first.getAccessToken());
        regenerated.put("token_type", first.getTokenType());
        regenerated.put("expires_in", first.getExpiresIn());
        String regeneratedJson = new ObjectMapper().writeValueAsString(regenerated);

        OAuthToken second = new OAuthToken(regeneratedJson);

        assertEquals("access_token must be stable across a deserialize/serialize/deserialize "
                + "round-trip through the production seam", first.getAccessToken(), second.getAccessToken());
        assertEquals("token_type must be stable across the round-trip", first.getTokenType(),
                second.getTokenType());
        assertEquals("expires_in must be stable across the round-trip", first.getExpiresIn(),
                second.getExpiresIn());
    }

    @Test
    public void oAuthTokenBindsRegardlessOfPropertyOrderAndUnknownFields() {
        String json = "{\"token_type\":\"Bearer\",\"ext_expires_in\":3599,\"expires_in\":3600,"
                + "\"scope\":\"dummy-scope/.default\",\"access_token\":\"dummy-access-token-26p7\","
                + "\"refresh_token\":\"dummy-refresh-token-26p7\"}";

        OAuthToken token = new OAuthToken(json);

        assertEquals("access_token must bind regardless of key ordering or trailing unknown "
                + "properties (2.18 property-introspection characterization)", "dummy-access-token-26p7",
                token.getAccessToken());
        assertEquals("token_type must bind regardless of key ordering or trailing unknown "
                + "properties", "Bearer", token.getTokenType());
        assertEquals("expires_in must bind regardless of key ordering or trailing unknown "
                + "properties", 3600L, token.getExpiresIn());
    }

    // -----------------------------------------------------------------------
    // OAuthTokenManager's static MAPPER seam, reached via the static-HttpClients-mock pattern
    // -----------------------------------------------------------------------

    @Test
    public void oAuthTokenManagerExtractsTokenThroughItsOwnMapperSeam() throws Exception {
        String body = "{\"token_type\":\"Bearer\",\"ext_expires_in\":3599,\"expires_in\":3600,"
                + "\"scope\":\"dummy-scope/.default\",\"access_token\":\"dummy-access-token-26p7\","
                + "\"refresh_token\":\"dummy-refresh-token-26p7\"}";
        CloseableHttpClient mockClient = buildMockHttpClient(200, body);
        OAuthTokenManager manager = new OAuthTokenManager(TOKEN_URL, "dummy-client-id",
                "dummy-client-secret", "dummy-scope/.default");

        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient);

            String accessToken = manager.getAccessToken();

            assertEquals("OAuthTokenManager must extract access_token through its own static "
                    + "MAPPER.readTree seam (OAuthTokenManager.java:118)", "dummy-access-token-26p7",
                    accessToken);
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void oAuthTokenManagerErrorBodyIntrospectionIsStable() throws Exception {
        String errorBody = "{\"error\":\"invalid_client\","
                + "\"error_description\":\"dummy client credentials rejected\"}";
        CloseableHttpClient mockClient = buildMockHttpClient(400, errorBody);
        OAuthTokenManager manager = new OAuthTokenManager(TOKEN_URL, "dummy-client-id",
                "dummy-client-secret", "dummy-scope/.default");

        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient);

            try {
                manager.getAccessToken();
                fail("Expected an exception for a non-200 OAuth token response");
            } catch (Exception e) {
                assertTrue("extractErrorDescription's readTree of the same static MAPPER must "
                        + "produce a stable 'error: error_description' message "
                        + "(OAuthTokenManager.java:154)",
                        e.getMessage().contains("invalid_client: dummy client credentials rejected"));
            }
        } finally {
            manager.shutdown();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers -- replicated from OAuthTokenManagerTest.buildMockHttpClient verbatim
    // -----------------------------------------------------------------------

    private CloseableHttpClient buildMockHttpClient(int statusCode, String responseBody) throws Exception {
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);

        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8)));
        entity.setContentLength(responseBody.getBytes(StandardCharsets.UTF_8).length);

        when(mockStatusLine.getStatusCode()).thenReturn(statusCode);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockResponse.getEntity()).thenReturn(entity);
        when(mockClient.execute(any(HttpUriRequest.class))).thenReturn(mockResponse);

        return mockClient;
    }
}
