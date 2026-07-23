package com.mirth.connect.smoketest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.smoketest.stubs.RecordingHttpStub;

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
}
