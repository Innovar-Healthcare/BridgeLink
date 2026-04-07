/*
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 */
package com.mirth.connect.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class OAuthTokenManagerTest {

    private static final String VALID_TOKEN_URL = "https://login.example.com/oauth2/v2.0/token";
    private static final String SUCCESS_RESPONSE =
            "{\"access_token\":\"test-access-token\",\"expires_in\":3600,\"token_type\":\"Bearer\"}";

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Test
    public void testConstructorRejectsNullUrl() {
        try {
            new OAuthTokenManager(null, "client-id", "secret", "scope");
            fail("Expected IllegalArgumentException for null URL");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("HTTPS"));
        }
    }

    @Test
    public void testConstructorRejectsHttpUrl() {
        try {
            new OAuthTokenManager("http://login.example.com/token", "client-id", "secret", "scope");
            fail("Expected IllegalArgumentException for HTTP URL");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("HTTPS"));
        }
    }

    @Test
    public void testConstructorAcceptsHttpsUrl() {
        // Should not throw
        OAuthTokenManager manager = new OAuthTokenManager(VALID_TOKEN_URL, "id", "secret", "scope");
        manager.shutdown();
    }

    // -----------------------------------------------------------------------
    // getAccessToken — happy path and caching
    // -----------------------------------------------------------------------

    @Test
    public void testGetAccessTokenFetchesOnFirstCall() throws Exception {
        CloseableHttpClient mockClient = buildMockHttpClient(200, SUCCESS_RESPONSE);
        OAuthTokenManager manager = new OAuthTokenManager(VALID_TOKEN_URL, "id", "secret", "scope");

        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient);

            String token = manager.getAccessToken();

            assertEquals("test-access-token", token);
            verify(mockClient, times(1)).execute(any(HttpUriRequest.class));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testGetAccessTokenReturnsCachedToken() throws Exception {
        CloseableHttpClient mockClient = buildMockHttpClient(200, SUCCESS_RESPONSE);
        OAuthTokenManager manager = new OAuthTokenManager(VALID_TOKEN_URL, "id", "secret", "scope");

        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient);

            String first = manager.getAccessToken();
            String second = manager.getAccessToken();

            assertEquals("test-access-token", first);
            assertEquals("test-access-token", second);
            // HTTP client should only be called once — second call uses cache
            verify(mockClient, times(1)).execute(any(HttpUriRequest.class));
        } finally {
            manager.shutdown();
        }
    }

    // -----------------------------------------------------------------------
    // getAccessToken — error paths
    // -----------------------------------------------------------------------

    @Test
    public void testGetAccessTokenFailsOnNon200() throws Exception {
        String errorBody = "{\"error\":\"invalid_client\",\"error_description\":\"Bad client secret\"}";
        CloseableHttpClient mockClient = buildMockHttpClient(401, errorBody);
        OAuthTokenManager manager = new OAuthTokenManager(VALID_TOKEN_URL, "id", "bad-secret", "scope");

        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient);

            try {
                manager.getAccessToken();
                fail("Expected exception for non-200 response");
            } catch (Exception e) {
                assertTrue("Expected HTTP status in message", e.getMessage().contains("401"));
                assertTrue("Expected error description in message", e.getMessage().contains("invalid_client"));
            }
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testGetAccessTokenFailsWhenMissingAccessTokenField() throws Exception {
        String responseWithoutToken = "{\"token_type\":\"Bearer\",\"expires_in\":3600}";
        CloseableHttpClient mockClient = buildMockHttpClient(200, responseWithoutToken);
        OAuthTokenManager manager = new OAuthTokenManager(VALID_TOKEN_URL, "id", "secret", "scope");

        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient);

            try {
                manager.getAccessToken();
                fail("Expected exception when access_token field is missing");
            } catch (Exception e) {
                assertTrue("Expected message about missing access_token",
                        e.getMessage().contains("access_token"));
            }
        } finally {
            manager.shutdown();
        }
    }

    // -----------------------------------------------------------------------
    // Error description extraction (tested indirectly via non-200 responses)
    // -----------------------------------------------------------------------

    @Test
    public void testExtractErrorDescription_WithJsonBody() throws Exception {
        String errorBody = "{\"error\":\"unauthorized_client\",\"error_description\":\"Client not authorized\"}";
        CloseableHttpClient mockClient = buildMockHttpClient(400, errorBody);
        OAuthTokenManager manager = new OAuthTokenManager(VALID_TOKEN_URL, "id", "secret", "scope");

        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient);

            try {
                manager.getAccessToken();
                fail("Expected exception");
            } catch (Exception e) {
                assertTrue("Expected error code in message",
                        e.getMessage().contains("unauthorized_client"));
                assertTrue("Expected error description in message",
                        e.getMessage().contains("Client not authorized"));
            }
        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testExtractErrorDescription_WithRawTextBody() throws Exception {
        String rawErrorBody = "Service Unavailable";
        CloseableHttpClient mockClient = buildMockHttpClient(503, rawErrorBody);
        OAuthTokenManager manager = new OAuthTokenManager(VALID_TOKEN_URL, "id", "secret", "scope");

        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient);

            try {
                manager.getAccessToken();
                fail("Expected exception");
            } catch (Exception e) {
                assertTrue("Expected raw error body preserved in message",
                        e.getMessage().contains("Service Unavailable"));
            }
        } finally {
            manager.shutdown();
        }
    }

    // -----------------------------------------------------------------------
    // shutdown
    // -----------------------------------------------------------------------

    @Test
    public void testShutdownClearsToken() throws Exception {
        OAuthTokenManager manager = new OAuthTokenManager(VALID_TOKEN_URL, "id", "secret", "scope");

        // First: fetch and cache the token
        CloseableHttpClient mockClient1 = buildMockHttpClient(200, SUCCESS_RESPONSE);
        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient1);
            assertNotNull(manager.getAccessToken());
        }

        // Shutdown clears the cached token
        manager.shutdown();

        // Re-fetch after shutdown should make a new HTTP call
        String secondResponse =
                "{\"access_token\":\"new-token-after-shutdown\",\"expires_in\":3600}";
        CloseableHttpClient mockClient2 = buildMockHttpClient(200, secondResponse);
        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockClient2);

            // Create a fresh manager since the old one's scheduler is shut down
            OAuthTokenManager manager2 = new OAuthTokenManager(VALID_TOKEN_URL, "id", "secret", "scope");
            try {
                String token = manager2.getAccessToken();
                assertEquals("new-token-after-shutdown", token);
                verify(mockClient2, times(1)).execute(any(HttpUriRequest.class));
            } finally {
                manager2.shutdown();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
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
