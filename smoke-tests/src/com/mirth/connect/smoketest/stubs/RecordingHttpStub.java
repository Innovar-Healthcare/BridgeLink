package com.mirth.connect.smoketest.stubs;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * D-05 recording in-JVM HTTP stub (18.1-RESEARCH.md Pattern 3) — proves what the HTTP Sender
 * actually put on the wire. JDK built-in {@code com.sun.net.httpserver}, zero new jars, bound
 * to {@code 127.0.0.1} only (T-18.1-01, SoapStub {@link SoapStub#startFallback()} convention).
 *
 * <p>Three contexts:
 * <ul>
 *   <li>{@code /record} — records every request, always responds 200 "OK".</li>
 *   <li>{@code /auth} — records every request; 401 + {@code WWW-Authenticate: Basic} challenge
 *       when no {@code Authorization} header is present, 200 "OK" once one is (18.1-RESEARCH.md
 *       Pitfall 2: non-preemptive Apache HttpClient sends request #1 bare and only retries with
 *       credentials after the challenge — both requests are recorded, which is the D-09
 *       evidence).</li>
 *   <li>{@code /stall} — records the request, sleeps {@code stallMillis} (default 5000, restores
 *       the interrupt flag if interrupted), then responds 200 "late" (D-06 socketTimeout case).</li>
 * </ul>
 */
public class RecordingHttpStub {

    private final int port;
    private final long stallMillis;
    private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());

    private HttpServer server;

    public RecordingHttpStub(int port) {
        this(port, 5000L);
    }

    public RecordingHttpStub(int port, long stallMillis) {
        this.port = port;
        this.stallMillis = stallMillis;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        server.createContext("/record", exchange -> {
            try {
                record(exchange);
                respond(exchange, 200, "OK");
            } finally {
                exchange.close();
            }
        });

        server.createContext("/auth", exchange -> {
            try {
                RecordedRequest recorded = record(exchange);
                if (recorded.headers.containsKey("Authorization")) {
                    respond(exchange, 200, "OK");
                } else {
                    exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"smoke\"");
                    respond(exchange, 401, "auth required");
                }
            } finally {
                exchange.close();
            }
        });

        server.createContext("/stall", exchange -> {
            try {
                record(exchange);
                try {
                    Thread.sleep(stallMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                respond(exchange, 200, "late");
            } finally {
                exchange.close();
            }
        });

        server.start();
    }

    /** Reads the request body BEFORE the response is written, and records the request. */
    private RecordedRequest record(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(exchange.getRequestHeaders());
        byte[] body = exchange.getRequestBody().readAllBytes();
        RecordedRequest recorded = new RecordedRequest(method, path, query, headers, body);
        requests.add(recorded);
        return recorded;
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    /** Returns a snapshot copy of recorded requests whose path starts with {@code pathPrefix}. */
    public List<RecordedRequest> getRequests(String pathPrefix) {
        List<RecordedRequest> snapshot;
        synchronized (requests) {
            snapshot = new ArrayList<>(requests);
        }
        List<RecordedRequest> filtered = new ArrayList<>();
        for (RecordedRequest r : snapshot) {
            if (r.path.startsWith(pathPrefix)) {
                filtered.add(r);
            }
        }
        return filtered;
    }

    /** Null-guarded stop — {@code stop(0)} would not interrupt an in-flight {@code /stall} sleep. */
    public void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
    }

    /** Immutable snapshot of a single request received by the stub. */
    public static final class RecordedRequest {
        public final String method;
        public final String path;
        public final String query;
        public final Map<String, List<String>> headers;
        public final byte[] body;

        RecordedRequest(String method, String path, String query, Map<String, List<String>> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }
    }
}
