package com.mirth.connect.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Phase 18.2 plan 04 (NET-07): folds the webserver-surface regressions IRT-834 (Swagger UI
 * Content-Length), IRT-835 (WebStart JNLP Content-Length + NPE guard), and the 404-missing-
 * installer case of IRT-833 into the in-harness smoke suite, per user decision D-A —
 * IRT-834/835 fold in fully, IRT-833 is restricted to the free 404 case (no pre-boot
 * installer file-drop plumbing).
 *
 * <p>Dials the already-booted main HTTPS webserver directly (no channel deploy, no REST
 * login) via the shared {@link RestClient#trustAllSslContext()} /
 * {@link RestClient#noHostnameVerificationParameters()} TLS factory (T-18-14 scope: harness
 * &harr; own-server on 127.0.0.1 only). Uses {@link SmokeTestBase#httpsPort}, already read by
 * {@code baseSetUp()} — no new {@code -D} system property is introduced.
 *
 * <p>Content-Length semantics are an HTTP/1.1 concept (chunked-transfer vs. fixed-length
 * framing) so the client below is pinned to HTTP/1.1, mirroring {@code HttpParamsTest}'s
 * {@code CL_CLIENT} pattern (Pitfall 3 precedent).
 */
public class WebServerRegressionTest extends SmokeTestBase {

    /**
     * IRT-834 regression threshold: bodies larger than ~30 KB triggered the pre-fix Jetty 12
     * early-commit bug (response committed before Content-Length could be computed). swagger-
     * ui-bundle.js is ~974 KB — far past this threshold.
     */
    private static final long CONTENT_LENGTH_REGRESSION_THRESHOLD = 30720L;

    private static HttpClient client;
    private static String baseUrl;

    @BeforeClass
    public static void webServerRegressionSetUp() {
        client = HttpClient.newBuilder()
                .sslContext(RestClient.trustAllSslContext())
                .sslParameters(RestClient.noHostnameVerificationParameters())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        baseUrl = "https://127.0.0.1:" + httpsPort;
    }

    // ------------------------------------------------------------------
    // IRT-834 — Swagger UI static assets served through SwaggerUiFilter
    // ------------------------------------------------------------------

    /** Phase 1 (script parity): GET /api/ (index.html) — Content-Length present, matches body. */
    @Test
    public void swaggerUiIndexHtmlContentLength() throws Exception {
        HttpResponse<byte[]> response = get("/api/");
        assertEquals("GET /api/ should serve index.html", 200, response.statusCode());
        assertContentLengthMatchesBody(response);
    }

    /**
     * Phase 2 (script parity), THE primary regression case: swagger-ui-bundle.js is ~974 KB,
     * far past the ~30 KB early-commit threshold where pre-fix Jetty 12 corrupted/omitted the
     * Content-Length header.
     */
    @Test
    public void swaggerUiBundleJsContentLengthRegression() throws Exception {
        HttpResponse<byte[]> response = get("/api/lib/swagger-ui-bundle.js");
        assertEquals("GET /api/lib/swagger-ui-bundle.js should be served", 200, response.statusCode());

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        assertTrue("Content-Length must be present and greater than the "
                + CONTENT_LENGTH_REGRESSION_THRESHOLD + "-byte early-commit regression threshold, was "
                + contentLength, contentLength > CONTENT_LENGTH_REGRESSION_THRESHOLD);
        assertEquals("Content-Length must equal the actual body byte count (IRT-834 regression case)",
                response.body().length, contentLength);
    }

    /** Phase 3 (script parity): a second static file type (CSS) — Content-Length present, matches body. */
    @Test
    public void swaggerUiCssContentLength() throws Exception {
        HttpResponse<byte[]> response = get("/api/css/swagger-ui.css");
        assertEquals("GET /api/css/swagger-ui.css should be served", 200, response.statusCode());
        assertContentLengthMatchesBody(response);
    }

    /**
     * Phase 4 (script parity): a real JAX-RS API path must pass through SwaggerUiFilter via
     * {@code chain.doFilter} rather than being intercepted as a static file. {@code
     * /server/version} has no matching file under {@code public_api_html/}, is unauthenticated
     * (no channel/session needed), and requires only the CSRF {@code X-Requested-With} header
     * — this is the same convention {@code run-smoke-test.sh} itself uses to detect the server
     * version pre-login.
     */
    @Test
    public void swaggerUiApiPathPassthrough() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/server/version"))
                .timeout(Duration.ofSeconds(15))
                .header("X-Requested-With", "OpenAPI")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals("GET /api/server/version (with CSRF header) must reach JAX-RS through "
                + "chain.doFilter, not be intercepted as a static file", 200, response.statusCode());
        assertTrue("Response Content-Type should not be text/html (that would mean SwaggerUiFilter "
                        + "served it as a static file instead of passing it to JAX-RS)",
                !response.headers().firstValue("Content-Type").orElse("").toLowerCase().contains("text/html"));
        assertTrue("Response body should be a non-empty version string",
                response.body() != null && !response.body().trim().isEmpty());
    }

    // ------------------------------------------------------------------
    // IRT-835 — WebStartServlet JNLP Content-Length + NPE guard
    // ------------------------------------------------------------------

    /**
     * Phases 1-3 (script parity): /webstart returns 200 with the JNLP content type,
     * Content-Length present/positive/matching the actual body, and a well-formed XML
     * document rooted at {@code <jnlp>}.
     */
    @Test
    public void webStartContentTypeLengthAndXml() throws Exception {
        HttpResponse<byte[]> response = get("/webstart");
        assertEquals("GET /webstart should return the administrator JNLP", 200, response.statusCode());
        assertTrue("Content-Type must contain application/x-java-jnlp-file, was "
                        + response.headers().firstValue("Content-Type").orElse("<absent>"),
                response.headers().firstValue("Content-Type").orElse("").contains("application/x-java-jnlp-file"));

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        assertTrue("Content-Length must be present and > 0, was " + contentLength, contentLength > 0);
        assertEquals("Content-Length must equal the actual body byte count (IRT-835 regression case)",
                response.body().length, contentLength);

        assertJnlpRootElement(response.body());
    }

    /**
     * Phase 4 (script parity): the NPE guard — an unrecognised query parameter makes
     * {@code isWebstartRequestValid} return false; pre-fix this let {@code jnlpDocument}
     * remain null and NPE'd inside {@code docSerializer.toXML()}. Post-fix this returns a
     * clean HTTP 404 before serialisation is attempted.
     */
    @Test
    public void webStartBadParamReturns404() throws Exception {
        HttpResponse<byte[]> response = get("/webstart?badparam=injected");
        assertEquals("Unrecognised /webstart query parameter must return a clean 404 "
                + "(pre-fix this NPE'd inside docSerializer.toXML())", 404, response.statusCode());
    }

    /** Phase 5 (script parity): the /webstart.jnlp alias gets the same assertions as /webstart. */
    @Test
    public void webStartJnlpAliasContentLength() throws Exception {
        HttpResponse<byte[]> response = get("/webstart.jnlp");
        assertEquals("GET /webstart.jnlp should return the administrator JNLP", 200, response.statusCode());

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        assertTrue("Content-Length must be present and > 0, was " + contentLength, contentLength > 0);
        assertEquals("Content-Length must equal the actual body byte count (/webstart.jnlp alias)",
                response.body().length, contentLength);

        assertJnlpRootElement(response.body());
    }

    // ------------------------------------------------------------------
    // IRT-833 — 404-only missing-installer case (D-A: no file-drop plumbing)
    // ------------------------------------------------------------------

    /**
     * The harness distribution ships no {@code public_html/installers/} directory and no
     * installer files are dropped for this test (D-A scope). {@code
     * MirthWebServer.InstallerFileHandler} must return a clean 404 rather than an error or a
     * hang when no matching installer file is found.
     */
    @Test
    public void missingInstallerReturns404() throws Exception {
        HttpResponse<byte[]> response = get("/launcher/macos.dmg");
        assertEquals("Missing installer file must return a clean 404 (IRT-833, D-A 404-only scope)",
                404, response.statusCode());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private HttpResponse<byte[]> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return client.send(request, BodyHandlers.ofByteArray());
    }

    private void assertContentLengthMatchesBody(HttpResponse<byte[]> response) {
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        assertTrue("Content-Length header must be present, was absent", contentLength >= 0);
        assertEquals("Content-Length must equal the actual body byte count",
                response.body().length, contentLength);
    }

    /** Parses the JNLP body as XML and asserts the root element is {@code <jnlp>}. */
    private void assertJnlpRootElement(byte[] body) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        Element root = document.getDocumentElement();
        assertEquals("JNLP document root element must be <jnlp>", "jnlp", root.getTagName());
    }
}
