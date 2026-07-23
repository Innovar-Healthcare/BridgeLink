package com.mirth.connect.smoketest.stubs;

import javax.mail.internet.MimeMessage;

/**
 * D-07 SMTP endpoint stub skeleton (RED phase — see StubSelfTest Test 1). Real
 * implementation (wraps GreenMail 1.6.15) lands in the GREEN commit.
 */
public class SmtpStub {

    public SmtpStub(int port) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void start() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public void stop() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public MimeMessage[] getReceivedMessages() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public static String getBody(MimeMessage message) {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
