package com.mirth.connect.smoketest;

import java.io.IOException;
import java.io.StringReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Java port of the {@code bl_login}/{@code deploy_channel_xml}/{@code deploy_channel} REST
 * conventions proven by {@code run-migration-test.sh} (feature/26.6.x) and
 * {@code test-irt832-http-receiver-content-length.sh}, for the smoke-test harness driver
 * (Phase 18, plan 18-06).
 *
 * <p>Every request carries {@code X-Requested-With: OpenAPI} (the CSRF-protection header
 * BridgeLink's REST API requires on every call) and reuses a single {@link HttpClient}
 * backed by a {@link CookieManager} so the login session cookie is sent on subsequent
 * requests automatically.
 *
 * <p><b>T-18-14 (accepted):</b> this client trusts all TLS certificates and disables
 * hostname verification. That is deliberately scoped to test-only use against the
 * harness's own ephemeral, self-signed, 127.0.0.1-bound server instance — never point
 * this class at a real deployment.
 */
public class RestClient {

    private final String baseUrl;
    private final HttpClient httpClient;

    public RestClient(String baseUrl) {
        this.baseUrl = baseUrl;
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .sslContext(trustAllSslContext())
                .sslParameters(noHostnameVerificationParameters())
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    public void login(String username, String password) throws IOException, InterruptedException {
        String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest request = newRequestBuilder(baseUrl + "/users/_login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Login to " + baseUrl + " failed: HTTP " + response.statusCode()
                    + " body=" + response.body());
        }
    }

    // ------------------------------------------------------------------
    // Import / deploy
    // ------------------------------------------------------------------

    /** POST the channel XML to create the channel definition. Accepts HTTP 200 or 201. */
    public void importChannel(String channelXml) throws IOException, InterruptedException {
        HttpRequest request = newRequestBuilder(baseUrl + "/channels")
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofString(channelXml))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();
        if (code != 200 && code != 201) {
            throw new IOException("Channel import failed: HTTP " + code + " body=" + response.body());
        }
    }

    /**
     * Deploy the given channel IDs. The {@code _deploy} endpoint returns HTTP 200 OR 204 on
     * success (proven convention, test-irt832-http-receiver-content-length.sh:133) — both are
     * treated as success.
     */
    public void deployChannels(List<String> channelIds) throws IOException, InterruptedException {
        StringBuilder xml = new StringBuilder("<set>");
        for (String id : channelIds) {
            xml.append("<string>").append(id).append("</string>");
        }
        xml.append("</set>");
        HttpRequest request = newRequestBuilder(baseUrl + "/channels/_deploy?returnErrors=true")
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofString(xml.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();
        // _deploy returns 200 OR 204 on success — both accepted.
        if (code != 200 && code != 204) {
            throw new IOException("Deploy failed: HTTP " + code + " body=" + response.body());
        }
    }

    /**
     * Poll {@code /channels/statuses?includeUndeployed=true} until the channel reaches
     * STARTED, following a STOPPED-then-{@code _start} fallback once (the D-GAP-13 pattern
     * from run-migration-test.sh). No fixed sleep — polls at a short fixed interval until
     * either STARTED is observed or {@code timeoutSeconds} elapses.
     */
    public void pollChannelStarted(String channelId, int timeoutSeconds) throws IOException, InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        boolean startAttempted = false;
        String lastState = "";
        while (System.nanoTime() < deadlineNanos) {
            lastState = getChannelState(channelId);
            if ("STARTED".equals(lastState)) {
                return;
            }
            if ("STOPPED".equals(lastState) && !startAttempted) {
                startAttempted = true;
                HttpRequest startRequest = newRequestBuilder(baseUrl + "/channels/" + channelId + "/_start?returnErrors=true")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                httpClient.send(startRequest, HttpResponse.BodyHandlers.discarding());
            }
            Thread.sleep(1000);
        }
        throw new IOException("Channel " + channelId + " did not reach STARTED within " + timeoutSeconds
                + "s (last observed state: '" + lastState + "')");
    }

    /**
     * Verify the channel is NOT stored as an InvalidChannel object.
     *
     * <p>{@code ChannelConverter.marshal()} outputs the ORIGINAL channel XML for InvalidChannel
     * objects, so grep-ing {@code GET /channels/{id}} for "invalidChannel" always false-negatives.
     * Instead: a channel present in {@code /channels/idsAndNames} but ABSENT from
     * {@code /channels/statuses?includeUndeployed=true} is stored as InvalidChannel (filtered out
     * of every status endpoint) — the proven statuses-absence detection pattern.
     *
     * @throws IllegalStateException with a message containing {@code SMOKE-FAILURE-CLASS: import}
     *         if the channel fails either presence check.
     */
    public void assertNotInvalidChannel(String channelId) throws IOException, InterruptedException {
        boolean inIdsAndNames = isChannelIdPresent(channelId);
        boolean inStatuses = isChannelPresentInStatuses(channelId);
        if (!inIdsAndNames || !inStatuses) {
            throw new IllegalStateException("SMOKE-FAILURE-CLASS: import - channel " + channelId
                    + " failed the InvalidChannel check (presentInChannelsList=" + inIdsAndNames
                    + ", presentInStatuses(includeUndeployed=true)=" + inStatuses + "). Absence from "
                    + "statuses after being defined indicates XStream deserialization failed and the "
                    + "channel was stored as an InvalidChannel object (check logs/mirth.log for a "
                    + "ChannelConverter ERROR).");
        }
    }

    // ------------------------------------------------------------------
    // Message pump
    // ------------------------------------------------------------------

    /**
     * Convenience for single-destination channels — every 18-05 fixture except
     * {@code doc-writer-test.xml} has exactly one destination at metaDataId 1.
     */
    public void processMessage(String channelId, String rawBody) throws IOException, InterruptedException {
        processMessage(channelId, rawBody, List.of(1));
    }

    /**
     * POST a raw message body to the channel's non-listener injection endpoint, explicitly
     * targeting {@code destinationMetaDataIds}.
     *
     * <p><b>Rule 1 fix (plan 18-07):</b> omitting the {@code destinationMetaDataId} query
     * parameter does NOT mean "all destinations" the way the equivalent Java client API's
     * {@code null} does — {@code Channel.createAndStoreSourceMessage()}
     * (donkey/.../channel/Channel.java) only falls back to "all destinations in the channel"
     * when {@code RawMessage.getDestinationMetaDataIds()} is {@code null}; Jersey injects an
     * EMPTY {@code Set<Integer>} (never {@code null}) for a collection-typed
     * {@code @QueryParam} with no matching query string entries, so every message pumped via
     * the bare endpoint silently routed to ZERO destinations and stalled at TRANSFORMED
     * forever — discovered here via a live single-message REST pump + message-detail
     * inspection (no destination connectorMessage was ever created). Every 18-07 caller must
     * now pass its channel's real destination metaDataId(s) explicitly.
     */
    public void processMessage(String channelId, String rawBody, Collection<Integer> destinationMetaDataIds) throws IOException, InterruptedException {
        HttpRequest request = newRequestBuilder(baseUrl + "/channels/" + channelId + "/messages"
                        + destinationMetaDataIdQuery(destinationMetaDataIds))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(rawBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();
        if (code != 200 && code != 201) {
            throw new IOException("processMessage failed for channel " + channelId + ": HTTP " + code
                    + " body=" + response.body());
        }
    }

    /** Convenience for single-destination channels (see {@link #processMessage(String, String)}). */
    public void processMessageBytes(String channelId, byte[] rawBytes) throws IOException, InterruptedException {
        processMessageBytes(channelId, rawBytes, List.of(1));
    }

    /**
     * Binary-safe variant of {@link #processMessage(String, String, Collection)} for channels
     * whose inbound data type is binary (e.g. DICOM, plan 18-07's dicom-test.xml).
     *
     * <p><b>Rule 1 fix (plan 18-07 — supersedes an earlier ISO-8859-1 raw-passthrough
     * attempt):</b> an ISO-8859-1 byte&lt;-&gt;char round trip preserves every byte over the
     * wire (verified independently), but {@code DICOMSerializer.toXML(String)}
     * (server/src/.../plugins/datatypes/dicom/DICOMSerializer.java) does NOT treat the raw
     * message string as a direct Latin-1 byte dump — it decodes it as Base64
     * ({@code Base64InputStream} wrapping {@code getBytesUsAscii(source)}), matching
     * {@code DICOMConverter}'s own {@code toDICOM()} encode path
     * ({@code StringUtils.newStringUsAscii(Base64Util.encodeBase64(...))}). Discovered live: an
     * ISO-8859-1 passthrough produced a byte-for-byte-correct stored raw message (confirmed via
     * message-detail inspection) that STILL failed with
     * {@code DicomCodingException: Not a DICOM Stream}, because the serializer expected
     * Base64-encoded ASCII text, not a raw byte dump. Base64 is pure US-ASCII, so the encoded
     * string is safe to send through the plain {@code text/plain} (UTF-8) endpoint with no
     * charset concerns at all.
     */
    public void processMessageBytes(String channelId, byte[] rawBytes, Collection<Integer> destinationMetaDataIds) throws IOException, InterruptedException {
        String base64Body = java.util.Base64.getEncoder().encodeToString(rawBytes);
        HttpRequest request = newRequestBuilder(baseUrl + "/channels/" + channelId + "/messages"
                        + destinationMetaDataIdQuery(destinationMetaDataIds))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(base64Body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();
        if (code != 200 && code != 201) {
            throw new IOException("processMessageBytes failed for channel " + channelId + ": HTTP " + code
                    + " body=" + response.body());
        }
    }

    // ------------------------------------------------------------------
    // Statistics
    // ------------------------------------------------------------------

    public long getSentCount(String channelId) throws IOException, InterruptedException {
        return getStatisticField(channelId, "sent");
    }

    public long getErrorCount(String channelId) throws IOException, InterruptedException {
        return getStatisticField(channelId, "error");
    }

    private long getStatisticField(String channelId, String field) throws IOException, InterruptedException {
        String url = baseUrl + "/channels/" + channelId + "/statistics";
        HttpResponse<String> response = httpClient.send(newJsonGetBuilder(url).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GET " + url + " failed: HTTP " + response.statusCode());
        }
        JsonObject obj = Json.createReader(new StringReader(response.body())).readObject();
        JsonObject stats = obj.containsKey("channelStatistics") ? obj.getJsonObject("channelStatistics") : obj;
        JsonValue val = stats.get(field);
        if (val == null) {
            return 0L;
        }
        if (val.getValueType() == JsonValue.ValueType.NUMBER) {
            return ((JsonNumber) val).longValue();
        }
        try {
            return Long.parseLong(val.toString().replace("\"", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private boolean isChannelIdPresent(String channelId) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                newJsonGetBuilder(baseUrl + "/channels/idsAndNames").build(), HttpResponse.BodyHandlers.ofString());
        return response.body() != null && response.body().contains("\"" + channelId + "\"");
    }

    private boolean isChannelPresentInStatuses(String channelId) throws IOException, InterruptedException {
        String state = getChannelState(channelId);
        return state != null && !state.isEmpty();
    }

    private String getChannelState(String channelId) throws IOException, InterruptedException {
        String url = baseUrl + "/channels/statuses?channelId=" + channelId + "&includeUndeployed=true";
        HttpResponse<String> response = httpClient.send(newJsonGetBuilder(url).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return "";
        }
        return extractState(response.body(), channelId);
    }

    /**
     * Parses the {@code /channels/statuses} response defensively — it may be a plain JSON
     * array (older/Mirth-style server) or wrapped as {@code {"list":{"dashboardStatus":...}}}
     * (BridgeLink 26.x), and {@code dashboardStatus} may itself be a single object or an array
     * when only one channel matches (test-irt832-http-receiver-content-length.sh:145-172).
     */
    private static String extractState(String json, String channelId) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        JsonStructure root;
        try {
            root = Json.createReader(new StringReader(json)).read();
        } catch (Exception e) {
            return "";
        }
        JsonArray statuses = extractDashboardStatusArray(root);
        for (JsonValue v : statuses) {
            if (v.getValueType() != JsonValue.ValueType.OBJECT) {
                continue;
            }
            JsonObject obj = (JsonObject) v;
            if (channelId.equals(obj.getString("channelId", null))) {
                return obj.getString("state", "");
            }
        }
        return "";
    }

    private static JsonArray extractDashboardStatusArray(JsonStructure root) {
        if (root.getValueType() == JsonValue.ValueType.ARRAY) {
            return (JsonArray) root;
        }
        if (root.getValueType() != JsonValue.ValueType.OBJECT) {
            return Json.createArrayBuilder().build();
        }
        JsonObject obj = (JsonObject) root;
        JsonValue list = obj.get("list");
        if (list != null && list.getValueType() == JsonValue.ValueType.OBJECT) {
            return normalizeToArray(((JsonObject) list).get("dashboardStatus"));
        }
        return normalizeToArray(obj.get("dashboardStatus"));
    }

    private static JsonArray normalizeToArray(JsonValue v) {
        if (v == null) {
            return Json.createArrayBuilder().build();
        }
        if (v.getValueType() == JsonValue.ValueType.ARRAY) {
            return (JsonArray) v;
        }
        return Json.createArrayBuilder().add(v).build();
    }

    /** Builds a {@code ?destinationMetaDataId=1&destinationMetaDataId=2...} query suffix (empty string if the collection is empty/null — callers should prefer being explicit, see {@link #processMessage(String, String, Collection)}). */
    private static String destinationMetaDataIdQuery(Collection<Integer> destinationMetaDataIds) {
        if (destinationMetaDataIds == null || destinationMetaDataIds.isEmpty()) {
            return "";
        }
        StringBuilder query = new StringBuilder("?");
        boolean first = true;
        for (Integer id : destinationMetaDataIds) {
            if (!first) {
                query.append('&');
            }
            query.append("destinationMetaDataId=").append(id);
            first = false;
        }
        return query.toString();
    }

    private HttpRequest.Builder newRequestBuilder(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("X-Requested-With", "OpenAPI")
                .timeout(Duration.ofSeconds(30));
    }

    private HttpRequest.Builder newJsonGetBuilder(String url) {
        return newRequestBuilder(url).header("Accept", "application/json").GET();
    }

    /**
     * T-18-14 (accepted): trust-all SSLContext, scoped to the harness talking to its own
     * ephemeral 127.0.0.1 server with a per-boot self-signed certificate. Test-only — this
     * class lives outside shipped code and is never wired into a runtime classpath.
     *
     * <p><b>Rule 1 fix (plan 18-07):</b> {@code java.net.http.HttpClient} internally wraps a
     * plain (non-extended) {@link X509TrustManager} with its own endpoint-identification logic
     * that still enforces a certificate SAN check even when
     * {@code SSLParameters.setEndpointIdentificationAlgorithm("")} is set — the auto-generated
     * self-signed keystore cert has no Subject Alternative Names, so every live connection
     * failed with {@code SSLHandshakeException: (certificate_unknown) No subject alternative
     * names present} (discovered here — 18-06's StubSelfTest never actually exercised
     * {@code RestClient.login()} against a live server). Implementing
     * {@link X509ExtendedTrustManager} directly (including its {@code SSLEngine}/{@code Socket}
     * overloads) makes {@code HttpClient} trust the custom manager's own accept-everything logic
     * instead of layering its own SAN check on top.
     */
    private static SSLContext trustAllSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustAllCerts = new TrustManager[] { new X509ExtendedTrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    // test-only: accept all
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // test-only: accept all
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {
                    // test-only: accept all
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {
                    // test-only: accept all
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) {
                    // test-only: accept all
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) {
                    // test-only: accept all
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            } };
            sslContext.init(null, trustAllCerts, new SecureRandom());
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to initialize test-only trust-all SSLContext", e);
        }
    }

    private static SSLParameters noHostnameVerificationParameters() {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm("");
        return sslParameters;
    }
}
