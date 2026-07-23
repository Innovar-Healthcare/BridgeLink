package com.mirth.connect.smoketest.stubs;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.ws.Endpoint;

import com.sun.net.httpserver.HttpServer;

/**
 * D-07 SOAP endpoint stub (18-RESEARCH.md Pattern 4).
 *
 * <p>Default mode: publishes a real {@link javax.xml.ws.Endpoint} (JAX-WS RI 2.3.0.2,
 * borrowed from {@code server-lib/javax/jaxws} + {@code jaxb}) backed by a trivial
 * {@code @WebService} echo/receiver implementation, so the WS Sender/Receiver connectors
 * under test talk to a genuine WSDL-serving SOAP endpoint bound to 127.0.0.1 (T-18-11).
 *
 * <p><b>A2 fallback:</b> if constructed with {@code forceFallback=true} (or the JVM system
 * property {@code smoke.soap.fallback=true} is set — the harness's CI-facing knob), a
 * plain {@link com.sun.net.httpserver.HttpServer} responder serves a canned SOAP 1.1
 * envelope + WSDL instead of a real JAX-WS {@code Endpoint}. This is the contingency for
 * JAX-WS RI / JDK-25 incompatibility (18-RESEARCH.md Assumption A2 — Dan's flagged risk
 * area). Default: off (real JAX-WS path).
 */
public class SoapStub {

    private final int port;
    private final String path;
    private final boolean fallback;
    private final AtomicReference<String> lastReceivedPayload = new AtomicReference<>();

    private Endpoint endpoint;
    private HttpServer fallbackServer;

    public SoapStub(int port) {
        this(port, "soapStub", Boolean.getBoolean("smoke.soap.fallback"));
    }

    public SoapStub(int port, boolean forceFallback) {
        this(port, "soapStub", forceFallback);
    }

    private SoapStub(int port, String path, boolean fallback) {
        this.port = port;
        this.path = path;
        this.fallback = fallback;
    }

    public String getUrl() {
        return "http://127.0.0.1:" + port + "/" + path;
    }

    public String getLastReceivedPayload() {
        return lastReceivedPayload.get();
    }

    public void start() throws IOException {
        if (fallback) {
            startFallback();
        } else {
            startJaxWs();
        }
    }

    private void startJaxWs() {
        try {
            endpoint = Endpoint.publish(getUrl(), new SoapEchoImpl(lastReceivedPayload));
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException("SoapStub failed to publish a JAX-WS endpoint — check that "
                    + "the jaxws/jaxb classpath filesets (server-lib/javax/jaxws + ext, jaxb + ext) are "
                    + "present on smoke-tests/build.xml's classpath (Pitfall 6)", e);
        }
    }

    private void startFallback() throws IOException {
        fallbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        fallbackServer.createContext("/" + path, exchange -> {
            try {
                String query = exchange.getRequestURI().getQuery();
                byte[] out;
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && query != null && query.contains("wsdl")) {
                    out = ("<?xml version=\"1.0\"?><definitions name=\"SoapStubFallback\" "
                            + "xmlns=\"http://schemas.xmlsoap.org/wsdl/\"></definitions>")
                                    .getBytes(StandardCharsets.UTF_8);
                } else {
                    byte[] body = exchange.getRequestBody().readAllBytes();
                    lastReceivedPayload.set(new String(body, StandardCharsets.UTF_8));
                    out = ("<?xml version=\"1.0\"?>"
                            + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                            + "<soap:Body><receiveResponse>OK</receiveResponse></soap:Body></soap:Envelope>")
                                    .getBytes(StandardCharsets.UTF_8);
                }
                exchange.getResponseHeaders().add("Content-Type", "text/xml");
                exchange.sendResponseHeaders(200, out.length);
                exchange.getResponseBody().write(out);
            } finally {
                exchange.close();
            }
        });
        fallbackServer.start();
    }

    public void stop() {
        if (endpoint != null) {
            endpoint.stop();
            endpoint = null;
        }
        if (fallbackServer != null) {
            fallbackServer.stop(0);
            fallbackServer = null;
        }
    }

    /**
     * Static nested @WebService implementor — deliberately NOT a non-static inner class, so
     * JAX-WS RI's runtime modeler processes a plain top-level-shaped implementor with no
     * enclosing-instance synthetic state. Receives the shared {@code lastReceivedPayload}
     * reference explicitly instead of capturing the outer stub instance.
     */
    @WebService
    public static class SoapEchoImpl {

        private final AtomicReference<String> lastReceivedPayload;

        public SoapEchoImpl(AtomicReference<String> lastReceivedPayload) {
            this.lastReceivedPayload = lastReceivedPayload;
        }

        @WebMethod
        public String receive(@WebParam(name = "payload") String payload) {
            lastReceivedPayload.set(payload);
            return "OK";
        }
    }
}
