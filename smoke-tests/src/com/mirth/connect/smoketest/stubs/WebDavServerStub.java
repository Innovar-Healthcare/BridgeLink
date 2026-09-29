package com.mirth.connect.smoketest.stubs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/**
 * D-07 embedded WebDAV endpoint stub (CVE-06/22-05), mirroring {@link DicomScpStub}'s
 * embedded-server lifecycle (constructor, {@code start()}/{@code stop()}, {@code isListening()}
 * TCP probe, bind 127.0.0.1-only).
 *
 * <p><b>A2 fallback invoked at plan time (not merely held in reserve):</b> 22-RESEARCH.md
 * recommended {@code io.milton:milton-server-ce:4.1.1.2676} for this leg. Downloaded and
 * inspected at plan time: its POM pulls a large NON-OPTIONAL transitive graph on top of
 * {@code milton-api} -- {@code org.simpleframework:simple} (its own embedded HTTP container),
 * {@code mina-core}, {@code hazelcast}, {@code caffeine}, {@code commons-fileupload2-jakarta},
 * {@code jackson-databind}, {@code commons-beanutils}, {@code milton-mail-api}/{@code
 * milton-mail-server}, {@code org.apache.oltu.oauth2.client}/{@code common}, {@code
 * jakarta.inject-api} -- none of which BridgeLink ships and none resolvable through this
 * project's manual jar-vendoring model (no Maven/Gradle dependency resolver; every jar and its
 * own transitives would need to be hand-downloaded and pinned). That is a materially different,
 * and materially worse, problem than the "Java-17/servlet-API alignment" risk 22-RESEARCH.md
 * A2 anticipated -- it is a dependency-footprint mismatch with the project's build model, for a
 * TEST-SCOPE-ONLY dependency. Per the plan's explicit allowance ("steal SoapStub's
 * fallback-mode precedent... in case Milton's Java-17/servlet-API alignment is shaky at plan
 * time"), this stub takes the {@code SoapStub}-style real-verbs fallback AS THE PRIMARY (not
 * opt-in) implementation: a hand-built WebDAV server on {@code com.sun.net.httpserver}
 * (JDK-native, zero new jars, matching {@code smoke-tests/build.xml}'s documented
 * classpath-borrowing model), implementing genuine DAV semantics (real multistatus PROPFIND XML,
 * real GET/PUT/DELETE/MKCOL/MOVE against the filesystem, real HTTP Basic Auth, real TLS for the
 * webdavs:// leg) -- a real DAV server, not a fixed-response stub, satisfying the same "no silent
 * no-op gate" principle 22-RESEARCH.md's Don't-Hand-Roll table cites for the Milton option.
 *
 * <p>Supports optional HTTPS (a second listener on its own port, reusing the SAME DAV root and
 * credentials) so the round-trip test can exercise {@code webdav://} vs {@code webdavs://}
 * (D-07 behavioral-risk coverage) against the identical server implementation.
 */
public class WebDavServerStub {

    private static final DateTimeFormatter HTTP_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

    private final int httpPort;
    private final int httpsPort;
    private final File rootDir;
    private final String username;
    private final String password;

    private String keyStorePath;
    private String keyStorePassword;

    private HttpServer httpServer;
    private HttpsServer httpsServer;

    /**
     * @param httpPort
     *            plaintext (webdav://) listener port.
     * @param httpsPort
     *            TLS (webdavs://) listener port, or {@code <= 0} to skip starting an HTTPS
     *            listener entirely.
     * @param rootDir
     *            filesystem directory backing the DAV collection root ("/").
     * @param username
     *            HTTP Basic Auth username required on every request (never anonymous -- D-07
     *            covers basic auth).
     * @param password
     *            HTTP Basic Auth password.
     */
    public WebDavServerStub(int httpPort, int httpsPort, File rootDir, String username, String password) {
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
        this.rootDir = rootDir;
        this.username = username;
        this.password = password;
    }

    /** Must be called before {@link #start()} if {@code httpsPort > 0} was supplied. */
    public void setTls(String keyStorePath, String keyStorePassword) {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
    }

    public void start() throws IOException {
        rootDir.mkdirs();

        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", httpPort), 0);
        httpServer.createContext("/", new DavHandler());
        httpServer.setExecutor(null);
        httpServer.start();

        if (httpsPort > 0) {
            httpsServer = HttpsServer.create(new InetSocketAddress("127.0.0.1", httpsPort), 0);
            try {
                SSLContext sslContext = buildSslContext(keyStorePath, keyStorePassword);
                httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            } catch (GeneralSecurityException e) {
                throw new IOException("Failed to initialize TLS for WebDavServerStub", e);
            }
            httpsServer.createContext("/", new DavHandler());
            httpsServer.setExecutor(null);
            httpsServer.start();
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (httpsServer != null) {
            httpsServer.stop(0);
            httpsServer = null;
        }
    }

    /** True if a TCP connection can be established to the plaintext listener (liveness check). */
    public boolean isListening() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", httpPort), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static SSLContext buildSslContext(String keyStorePath, String keyStorePassword) throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(keyStorePath)) {
            keyStore.load(in, keyStorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePassword.toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    /**
     * Real WebDAV verb dispatcher: HTTP Basic Auth gate, then OPTIONS/PROPFIND/GET/HEAD/PUT/
     * DELETE/MKCOL/MOVE against {@link #rootDir}. A minimal but genuine DAV level-1
     * implementation -- multistatus PROPFIND XML, real filesystem I/O -- not a canned-response
     * stub.
     */
    private class DavHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!checkAuth(exchange)) {
                    return;
                }

                String method = exchange.getRequestMethod();
                switch (method) {
                    case "OPTIONS":
                        handleOptions(exchange);
                        break;
                    case "PROPFIND":
                        handlePropfind(exchange);
                        break;
                    case "GET":
                        handleGet(exchange, true);
                        break;
                    case "HEAD":
                        handleGet(exchange, false);
                        break;
                    case "PUT":
                        handlePut(exchange);
                        break;
                    case "DELETE":
                        handleDelete(exchange);
                        break;
                    case "MKCOL":
                        handleMkcol(exchange);
                        break;
                    case "MOVE":
                        handleMove(exchange);
                        break;
                    default:
                        sendEmpty(exchange, 405);
                }
            } finally {
                exchange.close();
            }
        }

        private boolean checkAuth(HttpExchange exchange) throws IOException {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header != null && header.startsWith("Basic ")) {
                String decoded = new String(Base64.getDecoder().decode(header.substring("Basic ".length())), StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                if (colon >= 0) {
                    String user = decoded.substring(0, colon);
                    String pass = decoded.substring(colon + 1);
                    if (username.equals(user) && password.equals(pass)) {
                        return true;
                    }
                }
            }

            exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"webdav-smoke\"");
            sendEmpty(exchange, 401);
            return false;
        }

        private void handleOptions(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("DAV", "1");
            exchange.getResponseHeaders().add("Allow", "OPTIONS, GET, HEAD, PUT, DELETE, PROPFIND, MKCOL, MOVE");
            sendEmpty(exchange, 200);
        }

        private void handleGet(HttpExchange exchange, boolean withBody) throws IOException {
            File target = resolve(exchange);
            if (!target.exists()) {
                sendEmpty(exchange, 404);
                return;
            }

            if (target.isDirectory()) {
                // Rule 1 fix: a collection genuinely EXISTS, so GET/HEAD on one must NOT 404 --
                // Sardine's exists(url) issues a HEAD request (not PROPFIND), and a bare 404
                // here made every collection (including "/" itself) look nonexistent, which
                // broke WebDavConnection.isConnected()/isValid()/canRead()/canWrite() and made
                // FileReceiver.onStart()'s connection-pool validateObject() fail at deploy time.
                // This minimal DAV server has no real directory-listing body for GET (Sardine
                // uses PROPFIND for that), so just signal presence with an empty 200.
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            byte[] content = withBody ? Files.readAllBytes(target.toPath()) : new byte[0];
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, withBody ? content.length : -1);
            if (withBody) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            }
        }

        private void handlePut(HttpExchange exchange) throws IOException {
            File target = resolve(exchange);
            File parent = target.getParentFile();
            if (parent == null || !parent.isDirectory()) {
                // RFC 4918 SS9.7.1: PUT to a collection whose parent does not exist -> 409 Conflict.
                sendEmpty(exchange, 409);
                return;
            }

            boolean existed = target.exists();
            try (InputStream is = exchange.getRequestBody(); OutputStream os = new FileOutputStream(target)) {
                is.transferTo(os);
            }
            sendEmpty(exchange, existed ? 204 : 201);
        }

        private void handleDelete(HttpExchange exchange) throws IOException {
            File target = resolve(exchange);
            if (!target.exists()) {
                sendEmpty(exchange, 404);
                return;
            }
            deleteRecursively(target);
            sendEmpty(exchange, 204);
        }

        private void handleMkcol(HttpExchange exchange) throws IOException {
            File target = resolve(exchange);
            if (target.exists()) {
                sendEmpty(exchange, 405);
                return;
            }
            File parent = target.getParentFile();
            if (parent == null || !parent.isDirectory()) {
                sendEmpty(exchange, 409);
                return;
            }
            target.mkdir();
            sendEmpty(exchange, 201);
        }

        private void handleMove(HttpExchange exchange) throws IOException {
            File source = resolve(exchange);
            if (!source.exists()) {
                sendEmpty(exchange, 404);
                return;
            }

            String destinationHeader = exchange.getRequestHeaders().getFirst("Destination");
            if (destinationHeader == null) {
                sendEmpty(exchange, 400);
                return;
            }

            String destinationPath = URI.create(destinationHeader).getPath();
            File destination = resolvePath(destinationPath);
            File destinationParent = destination.getParentFile();
            if (destinationParent == null || !destinationParent.isDirectory()) {
                sendEmpty(exchange, 409);
                return;
            }

            boolean existed = destination.exists();
            if (existed) {
                deleteRecursively(destination);
            }

            try {
                Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                sendEmpty(exchange, 500);
                return;
            }
            sendEmpty(exchange, existed ? 204 : 201);
        }

        private void handlePropfind(HttpExchange exchange) throws IOException {
            File target = resolve(exchange);
            if (!target.exists()) {
                sendEmpty(exchange, 404);
                return;
            }

            String depthHeader = exchange.getRequestHeaders().getFirst("Depth");
            boolean includeChildren = target.isDirectory() && !"0".equals(depthHeader);

            // Fully drain the request body (PROPFIND may carry a <D:propfind> XML request body
            // requesting specific properties) -- this implementation always returns the same
            // "allprop"-equivalent property set regardless of what was requested, which is
            // sufficient for Sardine's list()/exists() call shapes.
            exchange.getRequestBody().readAllBytes();

            String requestRawPath = exchange.getRequestURI().getRawPath();

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            xml.append("<D:multistatus xmlns:D=\"DAV:\">");
            appendResponse(xml, requestRawPath, target);

            if (includeChildren) {
                File[] children = target.listFiles();
                if (children != null) {
                    for (File child : children) {
                        String childHref = joinHref(requestRawPath, encodeSegment(child.getName()));
                        appendResponse(xml, childHref, child);
                    }
                }
            }

            xml.append("</D:multistatus>");

            byte[] body = xml.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml; charset=UTF-8");
            exchange.sendResponseHeaders(207, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private void appendResponse(StringBuilder xml, String href, File file) {
            xml.append("<D:response>");
            xml.append("<D:href>").append(escapeXml(href)).append("</D:href>");
            xml.append("<D:propstat><D:prop>");
            xml.append("<D:displayname>").append(escapeXml(file.getName())).append("</D:displayname>");
            if (file.isDirectory()) {
                xml.append("<D:resourcetype><D:collection/></D:resourcetype>");
            } else {
                xml.append("<D:resourcetype/>");
                xml.append("<D:getcontentlength>").append(file.length()).append("</D:getcontentlength>");
            }
            xml.append("<D:getlastmodified>").append(HTTP_DATE_FORMAT.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneOffset.UTC))).append("</D:getlastmodified>");
            xml.append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>");
            xml.append("</D:response>");
        }

        /** Resolves the current request's URL path against {@link #rootDir}. */
        private File resolve(HttpExchange exchange) {
            return resolvePath(exchange.getRequestURI().getPath());
        }

        private File resolvePath(String urlPath) {
            String relative = urlPath.startsWith("/") ? urlPath.substring(1) : urlPath;
            if (relative.isEmpty()) {
                return rootDir;
            }
            // Reject path traversal outside the DAV root (test-harness hygiene, not a
            // production security control).
            Path resolved = rootDir.toPath().resolve(relative).normalize();
            if (!resolved.startsWith(rootDir.toPath())) {
                throw new IllegalArgumentException("Path escapes DAV root: " + urlPath);
            }
            return resolved.toFile();
        }

        private String joinHref(String basePath, String encodedChildName) {
            String base = basePath.endsWith("/") ? basePath : basePath + "/";
            return base + encodedChildName;
        }

        private String encodeSegment(String segment) {
            try {
                return URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20");
            } catch (IOException e) {
                // UTF-8 is always supported; unreachable.
                return segment;
            }
        }

        private String escapeXml(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private void deleteRecursively(File file) throws IOException {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursively(child);
                    }
                }
            }
            Files.deleteIfExists(file.toPath());
        }

        private void sendEmpty(HttpExchange exchange, int statusCode) throws IOException {
            exchange.sendResponseHeaders(statusCode, -1);
        }
    }

    /**
     * Standalone launcher entry point (Rule 1 fix): unlike {@code DicomScpStub} (whose DIMSE
     * Sender destination only connects out lazily, at send time), {@code FileReceiver.onStart()}
     * eagerly opens and validates its connection at CHANNEL DEPLOY time -- which happens well
     * before {@code run-smoke-test.sh}'s JUnit driver phase (where an embedded, JUnit-owned
     * stub instance would normally start). Starting this stub from inside
     * {@code WebDavRoundTripTest}'s {@code @BeforeClass} (the original design) would leave
     * {@code file-webdav-test.xml} deploying against nothing listening yet, and
     * {@code FileReceiver.onStart()} would throw at deploy time before the driver ever runs.
     * {@code run-smoke-test.sh} therefore launches this class as an independent OS process
     * (mirroring the SFTP leg's independent atmoz/sftp Docker container) BEFORE the channel
     * import/deploy stage, and stops it (SIGTERM) during teardown; {@code WebDavRoundTripTest}
     * only reads the already-running server's DAV root directory via {@code WEBDAV_ROOT_DIR} to
     * seed/poll files, connecting to the same ports either way.
     *
     * <p>Args: {@code <httpPort> <httpsPort> <rootDir> <username> <password> [<keyStorePath>
     * <keyStorePassword>]}. {@code httpsPort <= 0} skips the HTTPS listener; when
     * {@code httpsPort > 0}, {@code keyStorePath}/{@code keyStorePassword} are required.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 5 && args.length != 7) {
            System.err.println("Usage: WebDavServerStub <httpPort> <httpsPort> <rootDir> <username> <password> [<keyStorePath> <keyStorePassword>]");
            System.exit(2);
        }

        int httpPort = Integer.parseInt(args[0]);
        int httpsPort = Integer.parseInt(args[1]);
        File rootDir = new File(args[2]);
        String username = args[3];
        String password = args[4];

        WebDavServerStub stub = new WebDavServerStub(httpPort, httpsPort, rootDir, username, password);
        if (args.length == 7) {
            stub.setTls(args[5], args[6]);
        }

        stub.start();
        System.out.println("WebDavServerStub listening: http=" + httpPort + (httpsPort > 0 ? (" https=" + httpsPort) : "") + " root=" + rootDir);

        Runtime.getRuntime().addShutdownHook(new Thread(stub::stop));

        // Block forever -- the parent script stops this process via SIGTERM (shutdown hook
        // above calls stop()), mirroring how run-smoke-test.sh manages the Mirth server
        // process itself.
        Thread.currentThread().join();
    }
}
