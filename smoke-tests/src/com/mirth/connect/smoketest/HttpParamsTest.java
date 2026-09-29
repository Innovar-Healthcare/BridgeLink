package com.mirth.connect.smoketest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.smoketest.stubs.RecordingHttpStub;
import com.mirth.connect.smoketest.stubs.RecordingHttpStub.RecordedRequest;

/**
 * D-03 dedicated JUnit parameter suite for the HTTP connector's non-default configuration
 * surface — custom request/response headers, query parameters, timeout, data type, static
 * resource, content type, and auth type. This is Phase 18.1's regression gate content: the
 * assertions here will turn red if a future Jetty/Jersey swap (Phase 26 CVE elimination)
 * breaks the HTTP parameter surface.
 *
 * <p>Rides the same single server boot as {@link NativePumpChannelsTest}/
 * {@link StubChannelsTest} (D-03 — no extra boot cost); JUnit's {@code batchtest} in
 * {@code build.xml} auto-discovers this class. Own {@code @BeforeClass} reads six new
 * {@code -D} properties locally (never added to {@link SmokeTestBase#baseSetUp()} — Pitfall
 * 9 — so the Phase 18 pump/stub suites keep running unmodified without them) and starts a
 * {@link RecordingHttpStub} that the sender-side tests (see plan 18.1-05 Task 3) assert
 * against.
 *
 * <p>Scope boundary (D-14): charset (non-UTF-8) and multipart parsing are explicitly out of
 * scope — no test in this suite exercises them.
 */
public class HttpParamsTest extends SmokeTestBase {

    private static final String RESPONSE_ID = "00000013-0000-0000-0000-000000000013";
    private static final String XMLBODY_ID = "00000014-0000-0000-0000-000000000014";
    private static final String BINARY_RECV_ID = "00000015-0000-0000-0000-000000000015";
    private static final String AUTH_BASIC_ID = "00000016-0000-0000-0000-000000000016";
    private static final String AUTH_DIGEST_ID = "00000017-0000-0000-0000-000000000017";
    private static final String SENDER_PARAMS_ID = "00000018-0000-0000-0000-000000000018";
    private static final String TIMEOUT_ID = "00000019-0000-0000-0000-000000000019";
    private static final String BINARY_SEND_ID = "00000020-0000-0000-0000-000000000020";

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    /**
     * IRT-828 (NET-07): a second, HTTP/1.1-pinned client for every Content-Length/gzip
     * assertion. Content-Length vs. chunked-transfer semantics are HTTP/1.1 concepts —
     * HTTP/2 multiplexes over frames and does not expose an equivalent header in the same
     * way, so pinning the version keeps the CL assertions unambiguous (Pitfall 3). Does NOT
     * replace {@link #CLIENT} above — existing methods are untouched.
     */
    private static final HttpClient CL_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static String httpResponsePort;
    private static String httpXmlBodyPort;
    private static String httpAuthBasicPort;
    private static String httpAuthDigestPort;
    private static String httpStubPort;

    private static RecordingHttpStub stub;

    @BeforeClass
    public static void httpParamsSetUp() throws Exception {
        httpResponsePort = requireHttpParamsProperty("HTTP_RESPONSE_PORT");
        httpXmlBodyPort = requireHttpParamsProperty("HTTP_XMLBODY_PORT");
        // HTTP_BINARY_PORT is read (fail-loud if unset) for wiring-completeness parity with
        // the other five properties, but binaryRoundTrip() below never dials the listener
        // port directly — it pumps via the REST injection endpoint and polls the
        // destination file, so no field is retained for it.
        requireHttpParamsProperty("HTTP_BINARY_PORT");
        httpAuthBasicPort = requireHttpParamsProperty("HTTP_AUTH_BASIC_PORT");
        httpAuthDigestPort = requireHttpParamsProperty("HTTP_AUTH_DIGEST_PORT");
        httpStubPort = requireHttpParamsProperty("HTTP_STUB_PORT");

        stub = new RecordingHttpStub(Integer.parseInt(httpStubPort));
        stub.start();
        registerStubStop(stub::stop);
    }

    /**
     * Local replica of {@code SmokeTestBase}'s private {@code requireProperty} (RESEARCH
     * Pitfall 9) — deliberately NOT added to {@link SmokeTestBase#baseSetUp()}'s required-
     * property list, so {@link NativePumpChannelsTest}/{@link StubChannelsTest} keep running
     * unmodified without these six new {@code -D} properties set.
     */
    private static String requireHttpParamsProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required system property '" + name + "' not set. "
                    + "HttpParamsTest only runs against a live harness — invoke via "
                    + "smoke-tests/run-smoke-test.sh, which passes harness ports/paths as -D "
                    + "properties to `ant -f smoke-tests/build.xml test-run`.");
        }
        return value;
    }

    /**
     * D-13/D-04: fixed non-default response status code (202) + custom response header +
     * responseContentType, asserted directly on the wire response, then the L2/L1 three-level
     * check on the destination artifact (D-08).
     */
    @Test
    public void responseCluster() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpResponsePort + "/"))
                .timeout(Duration.ofSeconds(15))
                .POST(BodyPublishers.ofString(Hl7Messages.ORU_R01_LF, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = CLIENT.send(request, BodyHandlers.ofString());
        assertEquals("Fixed non-default responseStatusCode (D-13)", 202, response.statusCode());
        assertEquals("Custom responseHeaders entry", "bridgelink-1811",
                response.headers().firstValue("X-Smoke-Response").orElse(null));
        assertTrue("responseContentType should start with application/json",
                response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));

        assertThreeLevels(RESPONSE_ID, 1, () -> {
            Path out = pollForFile(Paths.get(outDir, "http-response", "output.hl7"), 60);
            String content = readFile(out);
            assertTrue("http-response destination content should contain the transformed patient token",
                    content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    /**
     * D-12: CUSTOM static resource served at a sub-context path — GET-only, no message
     * created (StaticResourceHandler intercepts before the channel dispatch).
     */
    @Test
    public void staticResource() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpResponsePort + "/static/smoke"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("smoke-static-content", response.body());
        assertTrue("Static resource Content-Type should start with text/x-smoke",
                response.headers().firstValue("Content-Type").orElse("").startsWith("text/x-smoke"));
    }

    /**
     * IRT-828-1 (NET-07): small CUSTOM static resource — Content-Length present, equal to
     * the actual body byte count, on an HTTP/1.1-pinned client. Same fixture/resource as
     * {@link #staticResource()} above; this method adds the CL assertion that test does not
     * make.
     */
    @Test
    public void staticResourceContentLengthSmall() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpResponsePort + "/static/smoke"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<byte[]> response = CL_CLIENT.send(request, BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        assertEquals("Content-Length must equal actual body bytes (IRT-828-1)",
                response.body().length, contentLength);
        assertEquals("smoke-static-content", new String(response.body(), StandardCharsets.UTF_8));
    }

    /**
     * IRT-828-2 (NET-07), THE regression case: a 40960-byte CUSTOM static resource — above
     * the ~30720-byte early-commit threshold where pre-fix Jetty 12 committed the response
     * before the full Content-Length could be computed, corrupting the header. Content-Length
     * must be present and equal to both the known fixture size and the actual body length.
     */
    @Test
    public void staticResourceContentLengthLarge() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpResponsePort + "/static/large"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<byte[]> response = CL_CLIENT.send(request, BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        assertEquals("Content-Length must equal actual body bytes (IRT-828-2 regression case)",
                response.body().length, contentLength);
        assertEquals("Large CUSTOM static resource must be exactly 40960 bytes (>30720 early-commit threshold)",
                40960, response.body().length);
    }

    /**
     * IRT-828-3 (NET-07): gzip tolerance rule against the same 40960-byte resource. Never
     * assert "Content-Length must be absent" (Pitfall 5) — if present it must equal the
     * COMPRESSED body length, and the gunzipped body must decompress back to the original
     * 40960 bytes. {@code java.net.http} sends no {@code Accept-Encoding} by default and
     * never auto-decompresses (Assumption A1) — explicit header, manual decompression here.
     */
    @Test
    public void staticResourceGzip() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpResponsePort + "/static/large"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        HttpResponse<byte[]> response = CL_CLIENT.send(request, BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        assertEquals("gzip", response.headers().firstValue("Content-Encoding").orElse(null));

        response.headers().firstValueAsLong("Content-Length").ifPresent(len ->
                assertEquals("If present, Content-Length must equal the COMPRESSED body length (never absent-only assumption)",
                        response.body().length, len));

        byte[] plain;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(response.body()))) {
            plain = gzip.readAllBytes();
        }
        assertEquals("Gunzipped body must decompress back to the original 40960 bytes",
                40960, plain.length);
    }

    /**
     * IRT-828-4 (NET-07): FILE resourceType — the resource value is a filesystem path the
     * harness pre-creates at 102400 bytes ({@code STATIC_FILE_PATH}, run-smoke-test.sh
     * {@code allocate_work_dirs()}). Content-Length must be present, equal to the known size,
     * and equal to the actual body length.
     */
    @Test
    public void staticResourceFile() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpResponsePort + "/static/file"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<byte[]> response = CL_CLIENT.send(request, BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        assertEquals("Content-Length must equal actual body bytes (IRT-828-4)",
                response.body().length, contentLength);
        assertEquals("FILE static resource must be exactly 102400 bytes",
                102400, response.body().length);
    }

    /**
     * D-11: xmlBody+includeMetadata envelope case — doubles as the listener-side custom
     * request-header/query-param visibility proof (do NOT add a separate header-echo test,
     * per CONTEXT specifics).
     */
    @Test
    public void xmlBodyEnvelope() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpXmlBodyPort + "/?smokeParam=p1"))
                .timeout(Duration.ofSeconds(15))
                .header("X-Smoke-Req", "r1")
                .header("Content-Type", "application/xml")
                .POST(BodyPublishers.ofString("<smoke>envelope</smoke>", StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = CLIENT.send(request, BodyHandlers.ofString());
        assertTrue("xmlBody pump expected a 2xx response, got " + response.statusCode(),
                response.statusCode() >= 200 && response.statusCode() < 300);

        assertThreeLevels(XMLBODY_ID, 1, () -> {
            Path out = pollForFile(Paths.get(outDir, "http-xmlbody", "output.txt"), 60);
            String content = readFile(out);
            assertTrue("xmlBody destination content should contain the parsed query param",
                    content.contains("Param: p1"));
            assertTrue("xmlBody destination content should contain the parsed request header",
                    content.contains("Header: r1"));
        });
    }

    /**
     * D-10: binary round trip both directions — sender posts binary content
     * (dataTypeBinary=true), listener with matching binaryMimeTypes receives it intact.
     * Byte-compares (never string), per RESEARCH Pitfall 10's three-hop encoding chain.
     */
    @Test
    public void binaryRoundTrip() throws Exception {
        byte[] payload = new byte[64];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        payload[0] = 0x00;
        payload[1] = (byte) 0xFF;

        rest.processMessageBytes(BINARY_SEND_ID, payload);

        assertThreeLevels(BINARY_SEND_ID, 1, () -> {
            Path out = pollForFile(Paths.get(outDir, "http-binary", "output.bin"), 60);
            pollUntil("binary output.bin bytes match the sent payload", 30, () -> {
                try {
                    return Arrays.equals(payload, Files.readAllBytes(out));
                } catch (IOException e) {
                    return false;
                }
            });
            assertArrayEquals("Binary round-trip content should be byte-identical (Pitfall 10)",
                    payload, Files.readAllBytes(out));
        });

        assertTrue("HTTP_BINARY listener channel (binary-recv) should have sent at least one message",
                rest.getSentCount(BINARY_RECV_ID) >= 1);
    }

    /**
     * D-07/D-08: Basic listener source-auth — negative sub-case FIRST (wrong credentials,
     * then no credentials at all), asserting 401 AND zero received messages (the
     * silently-disabled-auth catch), then the positive sub-case. Statistics were cleared
     * script-side before the driver ran, so ordering within this single method controls the
     * before/after comparison (no JUnit method-order dependency).
     */
    @Test
    public void basicAuth() throws Exception {
        URI listenerUri = URI.create("http://127.0.0.1:" + httpAuthBasicPort + "/");

        HttpResponse<String> wrongCreds = CLIENT.send(HttpRequest.newBuilder(listenerUri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString("smokeuser:WRONG".getBytes(StandardCharsets.UTF_8)))
                .POST(BodyPublishers.ofString(Hl7Messages.ORU_R01_LF, StandardCharsets.UTF_8))
                .build(), BodyHandlers.ofString());
        assertEquals("Wrong Basic credentials must be rejected with 401", 401, wrongCreds.statusCode());

        HttpResponse<String> noCreds = CLIENT.send(HttpRequest.newBuilder(listenerUri)
                .timeout(Duration.ofSeconds(15))
                .POST(BodyPublishers.ofString(Hl7Messages.ORU_R01_LF, StandardCharsets.UTF_8))
                .build(), BodyHandlers.ofString());
        assertEquals("Missing Authorization header must be rejected with 401", 401, noCreds.statusCode());

        assertEquals("Silently-disabled-auth catch: wrong/missing credentials must create zero messages (D-08)",
                0, rest.getReceivedCount(AUTH_BASIC_ID));

        HttpResponse<String> correctCreds = CLIENT.send(HttpRequest.newBuilder(listenerUri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString("smokeuser:smokepass".getBytes(StandardCharsets.UTF_8)))
                .POST(BodyPublishers.ofString(Hl7Messages.ORU_R01_LF, StandardCharsets.UTF_8))
                .build(), BodyHandlers.ofString());
        assertTrue("Basic auth positive case expected a 2xx response, got " + correctCreds.statusCode(),
                correctCreds.statusCode() >= 200 && correctCreds.statusCode() < 300);

        assertThreeLevels(AUTH_BASIC_ID, 1, () -> {
            Path out = pollForFile(Paths.get(outDir, "http-auth-basic", "output.hl7"), 60);
            String content = readFile(out);
            assertTrue("http-auth-basic destination content should contain the transformed patient token",
                    content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    /**
     * D-07/D-08, Pitfall 3: Digest listener source-auth. Negative sub-case uses
     * {@code java.net.http} (no credentials at all — the challenge path rejects
     * unauthenticated requests without needing Digest support in the driver). Positive
     * sub-case uses borrowed Apache HttpClient 4.5.13, mirroring {@code HttpDispatcher}'s own
     * client-side Digest wiring — this exact handshake has never been auto-tested in this
     * repo before, so a red result here is treated as a potential REAL product finding
     * (RESEARCH Open Question 2), not automatically a test bug.
     */
    @Test
    public void digestAuth() throws Exception {
        URI listenerUri = URI.create("http://127.0.0.1:" + httpAuthDigestPort + "/");

        HttpResponse<String> noCreds = CLIENT.send(HttpRequest.newBuilder(listenerUri)
                .timeout(Duration.ofSeconds(15))
                .POST(BodyPublishers.ofString(Hl7Messages.ORU_R01_LF, StandardCharsets.UTF_8))
                .build(), BodyHandlers.ofString());
        assertEquals("Missing credentials must be rejected with 401 (challenge path)", 401, noCreds.statusCode());
        assertEquals("Silently-disabled-auth catch: missing credentials must create zero messages (D-08)",
                0, rest.getReceivedCount(AUTH_DIGEST_ID));

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpClientContext ctx = HttpClientContext.create();
            BasicCredentialsProvider creds = new BasicCredentialsProvider();
            creds.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("smokeuser", "smokepass"));
            ctx.setCredentialsProvider(creds);
            HttpPost post = new HttpPost(listenerUri);
            post.setEntity(new StringEntity(Hl7Messages.ORU_R01_LF, ContentType.TEXT_PLAIN));
            try (CloseableHttpResponse response = client.execute(post, ctx)) {
                assertEquals("Digest positive case (borrowed Apache HttpClient) expected 200",
                        200, response.getStatusLine().getStatusCode());
            }
        }

        assertThreeLevels(AUTH_DIGEST_ID, 1, () -> {
            Path out = pollForFile(Paths.get(outDir, "http-auth-digest", "output.hl7"), 60);
            String content = readFile(out);
            assertTrue("http-auth-digest destination content should contain the transformed patient token",
                    content.contains(Hl7Messages.EXPECTED_PATIENT));
        });
    }

    /**
     * D-09/D-04: sender wire-format assertions against the recording stub — proves the
     * HTTP Sender actually put the custom header, query param, content type, and (after the
     * non-preemptive challenge) the correct Basic Authorization header on the wire. The stub
     * recording IS the L2 artifact for this sender-side channel (StubChannelsTest soap
     * precedent), so the wire assertions live inside {@link #assertThreeLevels}'s
     * artifactCheck.
     */
    @Test
    public void senderParams() throws Exception {
        rest.processMessage(SENDER_PARAMS_ID, Hl7Messages.ORU_R01_LF);

        assertThreeLevels(SENDER_PARAMS_ID, 1, () -> {
            // Pitfall 2: non-preemptive Basic auth-out sends request #1 bare; only after the
            // stub's 401+challenge does the sender retry with credentials.
            pollUntil("stub /auth recorded the bare-then-authorized challenge sequence (>= 2 requests)",
                    30, () -> stub.getRequests("/auth").size() >= 2);

            List<RecordedRequest> authRequests = stub.getRequests("/auth");
            RecordedRequest first = authRequests.get(0);
            RecordedRequest last = authRequests.get(authRequests.size() - 1);

            assertTrue("Non-preemptive auth: request #1 must arrive with NO Authorization header",
                    !first.headers.containsKey("Authorization"));

            String expectedAuth = "Basic " + Base64.getEncoder()
                    .encodeToString("smokeuser:smokepass".getBytes(StandardCharsets.UTF_8));
            assertEquals("Final recorded request must carry the correct Basic Authorization header",
                    expectedAuth, firstHeader(last, "Authorization"));
            assertTrue("Recorded request query should contain smokeQ=q1",
                    last.query != null && last.query.contains("smokeQ=q1"));
            assertEquals("Custom X-Smoke-Out header should have been recorded",
                    "out-1811", firstHeader(last, "X-Smoke-Out"));
            String contentType = firstHeader(last, "Content-Type");
            assertTrue("Recorded Content-Type should start with application/json",
                    contentType != null && contentType.startsWith("application/json"));
            assertTrue("Recorded request body should be non-empty", last.body.length > 0);
        });
    }

    /**
     * D-06: isolated timeout channel — socketTimeout=2000 against the stub's /stall
     * endpoint (~5s default stall). Does NOT use {@link #assertThreeLevels} (it hard-asserts
     * zero errors; this channel inverts L1 by design). Asserts errorCount >= 1, exactly one
     * recorded /stall request (proves dispatch reached the wire — the failure is the
     * timeout, not a connect refusal), and the D-06 timeout SIGNATURE itself: the message
     * error content must contain {@code SocketTimeoutException} (RESEARCH Pattern 4 item 3).
     */
    @Test
    public void senderTimeout() throws Exception {
        rest.processMessage(TIMEOUT_ID, Hl7Messages.ORU_R01_LF);

        pollUntil("timeout channel error count >= 1", 30, () -> {
            try {
                return rest.getErrorCount(TIMEOUT_ID) >= 1;
            } catch (Exception e) {
                return false;
            }
        });

        assertEquals("Exactly one /stall request should have been recorded (dispatch reached the wire)",
                1, stub.getRequests("/stall").size());

        String messagesWithContent = rest.getMessagesWithContent(TIMEOUT_ID);
        assertTrue("Timeout message error content should contain the SocketTimeoutException signature (D-06)",
                messagesWithContent.contains("SocketTimeoutException"));
    }

    private static String firstHeader(RecordedRequest request, String headerName) {
        List<String> values = request.headers.get(headerName);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
