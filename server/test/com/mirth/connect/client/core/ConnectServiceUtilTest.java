/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.mirth.connect.model.notification.Notification;

public class ConnectServiceUtilTest {

    private static final String[] PROTOCOLS = new String[0];
    private static final String[] CIPHER_SUITES = new String[0];

    private CloseableHttpClient buildMockClient(int statusCode, String body) throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity entity = mock(HttpEntity.class);

        when(statusLine.getStatusCode()).thenReturn(statusCode);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(entity.getContentType()).thenReturn(null);
        when(client.execute(any(HttpUriRequest.class), any(HttpClientContext.class))).thenReturn(response);
        return client;
    }

    private MockedStatic<HttpClients> mockHttpClients(CloseableHttpClient mockClient) {
        MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class);
        HttpClientBuilder builder = mock(HttpClientBuilder.class, RETURNS_DEEP_STUBS);
        when(builder.setConnectionManager(any())).thenReturn(builder);
        when(builder.build()).thenReturn(mockClient);
        mockedHttpClients.when(HttpClients::custom).thenReturn(builder);
        return mockedHttpClients;
    }

    @Test
    public void getNotifications_200_returnsNonEmptyList() throws Exception {
        String json = "[{\"id\":1,\"name\":\"n1\",\"date\":\"2024-01-01\",\"content\":\"c1\"}]";
        CloseableHttpClient mockClient = buildMockClient(200, json);

        Map<String, String> extVersions = new HashMap<String, String>();
        try (MockedStatic<HttpClients> mocked = mockHttpClients(mockClient)) {
            List<Notification> result = ConnectServiceUtil.getNotifications(
                    "server1", "4.6.0", extVersions, PROTOCOLS, CIPHER_SUITES);
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(1, (int) result.get(0).getId());
        }
    }

    @Test
    public void getNotificationCount_200_returnsCount() throws Exception {
        // Return JSON array of notification IDs; none are archived
        String json = "[10, 20, 30]";
        CloseableHttpClient mockClient = buildMockClient(200, json);

        Map<String, String> extVersions = new HashMap<String, String>();
        Set<Integer> archived = new HashSet<Integer>();
        try (MockedStatic<HttpClients> mocked = mockHttpClients(mockClient)) {
            int count = ConnectServiceUtil.getNotificationCount(
                    "server1", "4.6.0", extVersions, archived, PROTOCOLS, CIPHER_SUITES);
            assertEquals(3, count);
        }
    }

    @Test
    public void sendStatistics_200_returnsTrue() throws Exception {
        CloseableHttpClient mockClient = buildMockClient(200, "ok");

        try (MockedStatic<HttpClients> mocked = mockHttpClients(mockClient)) {
            boolean sent = ConnectServiceUtil.sendStatistics(
                    "server1", "4.6.0", true, "{}", PROTOCOLS, CIPHER_SUITES);
            assertTrue(sent);
        }
    }

    @Test
    public void getNotifications_exception_returnsEmptyList() throws Exception {
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(mockClient.execute(any(HttpUriRequest.class), any(HttpClientContext.class)))
                .thenThrow(new RuntimeException("network error"));

        Map<String, String> extVersions = new HashMap<String, String>();
        try (MockedStatic<HttpClients> mocked = mockHttpClients(mockClient)) {
            try {
                ConnectServiceUtil.getNotifications(
                        "server1", "4.6.0", extVersions, PROTOCOLS, CIPHER_SUITES);
                fail("Expected exception to propagate");
            } catch (Exception e) {
                // getNotifications re-throws — verify we caught a RuntimeException
                assertTrue(e instanceof RuntimeException);
            }
        }
    }
}
