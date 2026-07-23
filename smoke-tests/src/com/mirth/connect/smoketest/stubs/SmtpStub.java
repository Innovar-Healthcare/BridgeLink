package com.mirth.connect.smoketest.stubs;

import javax.mail.internet.MimeMessage;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

/**
 * D-07 SMTP endpoint stub: wraps GreenMail 1.6.15 (18-RESEARCH.md Pattern 4).
 *
 * <p>Classpath note: GreenMail is paired with the shipped {@code javax.mail-1.6.2.jar}
 * (server/setup/server-lib/javax/) — NEVER {@code jakarta.mail} (Pitfall 5, incompatible
 * package namespace with GreenMail 1.6.15's javax.mail-based API).
 */
public class SmtpStub {

    private final int port;
    private GreenMail greenMail;

    public SmtpStub(int port) {
        this.port = port;
    }

    /** Starts an SMTP server bound explicitly to 127.0.0.1 (T-18-11). */
    public void start() {
        ServerSetup setup = new ServerSetup(port, "127.0.0.1", ServerSetup.PROTOCOL_SMTP);
        greenMail = new GreenMail(setup);
        greenMail.start();
    }

    public void stop() {
        if (greenMail != null) {
            greenMail.stop();
            greenMail = null;
        }
    }

    public MimeMessage[] getReceivedMessages() {
        return greenMail.getReceivedMessages();
    }

    /**
     * Delegates to GreenMail's own poll-with-timeout wait (plan 18-07's designated L2 API) —
     * blocks up to {@code timeoutMs} for {@code count} messages to arrive, returning as soon
     * as they do (Pitfall 7: no fixed sleeps).
     */
    public boolean waitForIncomingEmail(long timeoutMs, int count) {
        return greenMail.waitForIncomingEmail(timeoutMs, count);
    }

    /** Convenience accessor for a received message's text body. */
    public static String getBody(MimeMessage message) {
        return GreenMailUtil.getBody(message);
    }
}
