/*
 * Copyright (c) 2025 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.server.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpStatus;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Manages OAuth 2.0 Client Credentials access tokens for SMTP XOAUTH2 authentication.
 *
 * <p>Fetches a token from the configured token endpoint, caches it in memory, and
 * proactively schedules a refresh {@value #REFRESH_BUFFER_SECONDS} seconds before expiry.
 * Token values are never logged in plaintext (FR-8.2).</p>
 *
 * <p>Callers must invoke {@link #shutdown()} when the owning component is stopped/undeployed
 * to cancel the scheduled refresh and release the executor thread.</p>
 */
public class OAuthTokenManager {

    private static final Logger logger = LogManager.getLogger(OAuthTokenManager.class);
    private static final int REFRESH_BUFFER_SECONDS = 60;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String tokenEndpointUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;

    // Guarded by 'this'
    private volatile String cachedToken;
    private ScheduledFuture<?> scheduledRefresh;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "OAuthTokenRefresh");
        t.setDaemon(true);
        return t;
    });

    /**
     * @param tokenEndpointUrl  OAuth token endpoint — must start with {@code https://}
     * @param clientId          OAuth client ID
     * @param clientSecret      OAuth client secret (plaintext)
     * @param scope             OAuth scope (e.g. {@code https://outlook.office365.com/.default})
     * @throws IllegalArgumentException if the token endpoint URL does not use HTTPS
     */
    public OAuthTokenManager(String tokenEndpointUrl, String clientId, String clientSecret, String scope) {
        if (tokenEndpointUrl == null || !tokenEndpointUrl.toLowerCase().startsWith("https://")) {
            throw new IllegalArgumentException("OAuth token endpoint URL must use HTTPS (got: " + tokenEndpointUrl + ")");
        }
        this.tokenEndpointUrl = tokenEndpointUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
    }

    /**
     * Returns the current cached access token. On the first call, fetches a new token
     * from the token endpoint. Subsequent calls return the cached token until it is
     * proactively refreshed by the background scheduler.
     *
     * @return the current access token
     * @throws Exception if the token endpoint returns an error or is unreachable
     */
    public synchronized String getAccessToken() throws Exception {
        if (cachedToken == null) {
            fetchAndCache();
        }
        return cachedToken;
    }

    /**
     * Forces an immediate token refresh, replacing any cached token.
     * Called by the background scheduler before the current token expires.
     */
    private void fetchAndCache() throws Exception {
        logger.debug("Fetching OAuth access token from endpoint: {}", tokenEndpointUrl);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(tokenEndpointUrl);
            post.setEntity(new UrlEncodedFormEntity(Arrays.asList(
                    new BasicNameValuePair("grant_type", "client_credentials"),
                    new BasicNameValuePair("client_id", clientId),
                    new BasicNameValuePair("client_secret", clientSecret),
                    new BasicNameValuePair("scope", scope)
            ), StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (statusCode != HttpStatus.SC_OK) {
                    throw new Exception("OAuth token request failed. HTTP " + statusCode
                            + " from " + tokenEndpointUrl + ": " + extractErrorDescription(responseBody));
                }

                JsonNode json = MAPPER.readTree(responseBody);
                String accessToken = json.path("access_token").asText(null);
                if (accessToken == null || accessToken.isEmpty()) {
                    throw new Exception("OAuth token response did not contain an access_token field");
                }

                int expiresIn = json.path("expires_in").asInt(3600);
                cachedToken = accessToken;
                logger.debug("OAuth access token obtained successfully, expires in {}s", expiresIn);

                scheduleRefresh(expiresIn);
            }
        }
    }

    private synchronized void scheduleRefresh(int expiresInSeconds) {
        if (scheduledRefresh != null && !scheduledRefresh.isDone()) {
            scheduledRefresh.cancel(false);
        }
        long delaySeconds = Math.max(expiresInSeconds - REFRESH_BUFFER_SECONDS, 10);
        scheduledRefresh = scheduler.schedule(() -> {
            synchronized (OAuthTokenManager.this) {
                try {
                    logger.debug("Proactively refreshing OAuth access token");
                    fetchAndCache();
                } catch (Exception e) {
                    logger.error("Failed to proactively refresh OAuth access token. Next send will retry.", e);
                    cachedToken = null;
                }
            }
        }, delaySeconds, TimeUnit.SECONDS);
        logger.debug("OAuth token refresh scheduled in {}s", delaySeconds);
    }

    private String extractErrorDescription(String responseBody) {
        try {
            JsonNode json = MAPPER.readTree(responseBody);
            String error = json.path("error").asText("");
            String desc = json.path("error_description").asText("");
            return error.isEmpty() ? responseBody : (error + ": " + desc);
        } catch (Exception ignored) {
            return responseBody;
        }
    }

    /**
     * Cancels any pending token refresh and releases the background thread.
     * Must be called when the owning component is undeployed.
     */
    public synchronized void shutdown() {
        if (scheduledRefresh != null) {
            scheduledRefresh.cancel(false);
        }
        scheduler.shutdownNow();
        cachedToken = null;
        logger.debug("OAuthTokenManager shut down");
    }
}
