package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * NET-07 (IRT-831/832): Jetty regression coverage for contextPath routing and response
 * Content-Length, against fixtures {@code 00000021}-{@code 00000023} wired in plan 18.2-01.
 * Sibling of {@link HttpParamsTest} rather than an extension of it — a dedicated class gets
 * its own JUnit report name (mirrors 18.1 D-03 rationale) since this suite is the wire
 * contract Phase 22/26 Jersey/Jetty bumps will be gated on.
 *
 * <p>Rides the same single server boot as every other {@code *Test} class discovered by
 * {@code build.xml}'s {@code batchtest} (no registration needed). Own {@code @BeforeClass}
 * reads three new {@code -D} properties locally via a replica of
 * {@link SmokeTestBase}'s private {@code requireProperty} — deliberately NOT added to
 * {@link SmokeTestBase#baseSetUp()} (Pitfall 9: it would break
 * {@code NativePumpChannelsTest}/{@code StubChannelsTest}, which never set these properties).
 */
public class JettyRegressionTest extends SmokeTestBase {

    private static final String CTXPATH_ID = "00000021-0000-0000-0000-000000000021";
    private static final String LARGE_ID = "00000022-0000-0000-0000-000000000022";
    private static final String ERROR500_ID = "00000023-0000-0000-0000-000000000023";

    private static final String EXPECTED_ERROR_BODY = "IRT-832 smoke test error response body.";

    /**
     * IRT-828/832: HTTP/1.1-pinned client for every Content-Length/gzip assertion (Pitfall
     * 3 — CL vs. chunked-transfer semantics are HTTP/1.1 concepts). {@code java.net.http}
     * sends no {@code Accept-Encoding} by default and never auto-decompresses (Assumption
     * A1), so gzip assertions set the header explicitly and decompress manually below.
     */
    private static final HttpClient CL_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static String ctxpathPort;
    private static String largePort;
    private static String error500Port;

    @BeforeClass
    public static void jettyRegressionSetUp() throws Exception {
        ctxpathPort = requireJettyRegressionProperty("HTTP_CTXPATH_PORT");
        largePort = requireJettyRegressionProperty("HTTP_LARGE_PORT");
        error500Port = requireJettyRegressionProperty("HTTP_ERROR500_PORT");
    }

    /**
     * Local replica of {@code SmokeTestBase}'s private {@code requireProperty} (RESEARCH
     * Pitfall 9) — deliberately NOT added to {@link SmokeTestBase#baseSetUp()}'s required-
     * property list, so the Phase 18 pump/stub suites keep running unmodified without these
     * three new {@code -D} properties set.
     */
    private static String requireJettyRegressionProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required system property '" + name + "' not set. "
                    + "JettyRegressionTest only runs against a live harness — invoke via "
                    + "smoke-tests/run-smoke-test.sh, which passes harness ports/paths as -D "
                    + "properties to `ant -f smoke-tests/build.xml test-run`.");
        }
        return value;
    }

    /**
     * IRT-831 (NET-07): source-level {@code contextPath} strict prefix + slash matching on
     * fixture 00000021. Sub-cases (single method, mirroring {@link HttpParamsTest#basicAuth()}'s
     * negative-then-positive-in-one-method shape, so a single {@link #assertThreeLevels}
     * call controls the final message count):
     * <ul>
     *   <li>A1 — in-context root: {@code GET /smokectx} → 200 (creates a channel message)</li>
     *   <li>A2 — sub-path under context: {@code GET /smokectx/sub/path} → 200 (creates a
     *       channel message)</li>
     *   <li>A3 — outside context: {@code GET /other} → 404 (DefaultHandler; no message)</li>
     *   <li>A5, THE regression case — shared-prefix, non-slash-delimited path:
     *       {@code GET /smokectxing} → 404 (must NOT match {@code /smokectx}; pre-fix a root
     *       ContextHandler accepted every URL regardless of the trailing character after the
     *       prefix)</li>
     *   <li>B1 — static resource under the context: {@code GET /smokectx/data} → 200, body
     *       EXACTLY {@code smoke-ctx-resource-content} (StaticResourceHandler intercepts
     *       before channel dispatch; no message)</li>
     *   <li>B2 — sub-path of the resource path falls through to the channel handler instead
     *       of matching the resource: {@code GET /smokectx/data/wrong} → 200, body does NOT
     *       contain {@code smoke-ctx-resource-content} (creates a channel message)</li>
     * </ul>
     * Message count derivation: A1 + A2 + B2's fall-through = 3 (A3/A5 are 404s with no
     * channel dispatch; B1 is intercepted by the static resource with no channel dispatch).
     */
    @Test
    public void contextPathRouting() throws Exception {
        String base = "http://127.0.0.1:" + ctxpathPort;

        // A1: in-context root.
        HttpResponse<String> inContext = CL_CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/smokectx")).timeout(Duration.ofSeconds(15)).GET().build(),
                BodyHandlers.ofString());
        assertEquals("A1: request inside the contextPath must succeed", 200, inContext.statusCode());

        // A2: sub-path under the context.
        HttpResponse<String> subPath = CL_CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/smokectx/sub/path")).timeout(Duration.ofSeconds(15)).GET().build(),
                BodyHandlers.ofString());
        assertEquals("A2: sub-path under the contextPath must succeed", 200, subPath.statusCode());

        // A3: outside the context entirely.
        HttpResponse<String> outside = CL_CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/other")).timeout(Duration.ofSeconds(15)).GET().build(),
                BodyHandlers.ofString());
        assertEquals("A3: request outside the contextPath must 404", 404, outside.statusCode());

        // A5, THE regression case: shares the "/smokectx" prefix but is NOT a slash-delimited
        // sub-path — must NOT match.
        HttpResponse<String> strictPrefix = CL_CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/smokectxing")).timeout(Duration.ofSeconds(15)).GET().build(),
                BodyHandlers.ofString());
        assertEquals("A5 regression: shared-prefix path '/smokectxing' must NOT match '/smokectx' (strict prefix+slash)",
                404, strictPrefix.statusCode());

        // B1: exact static-resource path under the context — intercepted, no channel dispatch.
        HttpResponse<String> resource = CL_CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/smokectx/data")).timeout(Duration.ofSeconds(15)).GET().build(),
                BodyHandlers.ofString());
        assertEquals("B1: static resource under the context must succeed", 200, resource.statusCode());
        assertEquals("B1: static resource body must match exactly", "smoke-ctx-resource-content", resource.body());

        // B2: sub-path of the resource path falls through to the channel handler.
        HttpResponse<String> resourceSubPath = CL_CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/smokectx/data/wrong")).timeout(Duration.ofSeconds(15)).GET().build(),
                BodyHandlers.ofString());
        assertEquals("B2: resource sub-path must still succeed (falls through to the channel)",
                200, resourceSubPath.statusCode());
        assertFalse("B2: resource sub-path must NOT match the static resource's exact-path content",
                resourceSubPath.body().contains("smoke-ctx-resource-content"));

        assertThreeLevels(CTXPATH_ID, 3, () -> {
            // A1/A2/B2 are all bare GETs with no request body, so the File Writer's
            // ${message.encodedData} template legitimately writes a 0-byte file — this
            // fixture's L2 check is file EXISTENCE, not non-empty content (unlike
            // HttpParamsTest's HL7-body fixtures, whose destination files carry a
            // transformed payload). Do not use SmokeTestBase#pollForFile here — it hard-
            // requires length() > 0, which this artifact will never satisfy.
            Path out = Paths.get(outDir, "http-ctxpath", "output.txt");
            pollUntil("http-ctxpath destination file exists", 30, () -> Files.exists(out));
        });
    }

    /**
     * IRT-832 (NET-07): 102400-byte generated response body via a JavaScript Writer
     * destination on fixture 00000022, default (200) status.
     * <ul>
     *   <li>832-1 — plain GET: Content-Length present, == 102400, == body length</li>
     *   <li>832-2 — GET with {@code Accept-Encoding: gzip}: {@code Content-Encoding} ==
     *       "gzip"; Content-Length tolerance (absent OR == compressed size, never
     *       "must be absent" — Pitfall 5); gunzipped body == 102400 bytes</li>
     * </ul>
     * Both GETs pump real messages (the JavaScript Writer's return value IS the response
     * body AND the L2 artifact — senderParams() precedent, RESEARCH key_links); two GETs on
     * this channel means {@code minSent} == 2 for the trailing {@link #assertThreeLevels}
     * call.
     */
    @Test
    public void responseContentLengthLarge() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + largePort + "/");

        // 832-1: plain GET.
        HttpResponse<byte[]> plain = CL_CLIENT.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(), BodyHandlers.ofByteArray());
        assertEquals(200, plain.statusCode());
        long plainContentLength = plain.headers().firstValueAsLong("Content-Length").orElse(-1);
        assertEquals("832-1: Content-Length must equal actual body bytes", plain.body().length, plainContentLength);
        assertEquals("832-1: response body must be exactly 102400 bytes", 102400, plain.body().length);

        // 832-2: gzip tolerance rule.
        HttpResponse<byte[]> gz = CL_CLIENT.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                        .header("Accept-Encoding", "gzip").GET().build(),
                BodyHandlers.ofByteArray());
        assertEquals(200, gz.statusCode());
        assertEquals("832-2: Content-Encoding must be gzip", "gzip", gz.headers().firstValue("Content-Encoding").orElse(null));
        gz.headers().firstValueAsLong("Content-Length").ifPresent(len ->
                assertEquals("832-2: if present, Content-Length must equal the COMPRESSED body length",
                        gz.body().length, len));
        byte[] gunzipped;
        try (GZIPInputStream gzipStream = new GZIPInputStream(new ByteArrayInputStream(gz.body()))) {
            gunzipped = gzipStream.readAllBytes();
        }
        assertEquals("832-2: gunzipped body must decompress back to 102400 bytes", 102400, gunzipped.length);

        assertThreeLevels(LARGE_ID, 2, () -> {
            // The wire response body IS the L2 artifact for the JS-Writer destination
            // (senderParams() precedent) — already verified above; no separate file check.
        });
    }

    /**
     * IRT-832-3 (NET-07): fixed {@code responseStatusCode=500} on fixture 00000023 — the
     * message still processes to SENT with zero ERROR statuses (Assumption A2, verified live
     * here via {@link #assertThreeLevels}'s L1 check), only the HTTP status is forced to 500.
     * Body equals exactly the known fixture string; Content-Length matches its byte length.
     */
    @Test
    public void errorResponseContentLength() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + error500Port + "/");

        HttpResponse<byte[]> response = CL_CLIENT.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(), BodyHandlers.ofByteArray());
        assertEquals("832-3: fixed responseStatusCode=500 must be returned on the wire", 500, response.statusCode());
        assertEquals("832-3: body must match the known fixture string exactly",
                EXPECTED_ERROR_BODY, new String(response.body(), StandardCharsets.UTF_8));
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        assertEquals("832-3: Content-Length must equal the known string's byte length",
                EXPECTED_ERROR_BODY.getBytes(StandardCharsets.UTF_8).length, contentLength);

        assertThreeLevels(ERROR500_ID, 1, () -> {
            // The wire response body IS the L2 artifact for the JS-Writer destination
            // (senderParams() precedent) — already verified above; no separate file check.
        });
    }
}
