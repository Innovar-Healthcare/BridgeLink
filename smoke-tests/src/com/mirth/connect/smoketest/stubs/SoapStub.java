package com.mirth.connect.smoketest.stubs;

import java.io.IOException;

/**
 * D-07 SOAP endpoint stub skeleton (RED phase — see StubSelfTest Test 2). Real
 * implementation (javax.xml.ws.Endpoint.publish + the A2 com.sun.net.httpserver fallback
 * gated on system property {@code smoke.soap.fallback}) lands in the GREEN commit.
 */
public class SoapStub {

    public SoapStub(int port) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public SoapStub(int port, boolean forceFallback) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public String getUrl() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public String getLastReceivedPayload() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void start() throws IOException {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void stop() {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
