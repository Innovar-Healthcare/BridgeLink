package com.mirth.connect.smoketest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.smoketest.stubs.SmtpStub;

/**
 * Phase 18.5 (NET-10) — SC-3, the JS SMTP API coverage, and the LAST, PR-#177-GATED slice of
 * this phase (plan 18.5-05). The new 7-arg {@code send(to,cc,bcc,from,subject,body,charset)}
 * that PR #177 adds to the userutil {@code SMTPConnection}/{@code ServerSMTPConnection} API must
 * deliver to all recipients with BCC blind (D1), and the pre-existing legacy 6-arg /
 * 5-arg overloads must keep delivering unchanged (D2/D3, they delegate with a null bcc
 * internally).
 *
 * <p><b>Why this must run through a deployed channel, never a driver-side call (OQ-2):</b>
 * {@code ServerSMTPConnection.send()} calls {@code ControllerFactory}-backed collaborators
 * unconditionally and is NOT reachable from the smoke driver JVM, which has no such wiring. So
 * all three arities are driven in-server through {@code smtp-js-ccbcc-test.xml}'s single
 * JavaScript Writer destination (channel {@code SMTP_JS_CHANNEL_ID}), whose Rhino script calls
 * the real API — this class never calls {@code ServerSMTPConnection}/{@code SmtpDispatcher}
 * directly.
 *
 * <p><b>Dual-safe gating (this class's entire reason for existing on this branch today):</b>
 * <ol>
 *   <li>The 7-arg call lives ONLY inside the deployed channel's Rhino script string (never
 *       compiled Java) — see {@code smtp-js-ccbcc-test.xml}'s Destination 1 — so the
 *       smoke-tests build compiles cleanly whether or not PR #177 has landed (Pitfall 1). The
 *       fixture still DEPLOYS today: Rhino resolves the overload at call time, not deploy
 *       time.</li>
 *   <li>{@link #startStubs()} (this class's {@code @BeforeClass}) FIRST asserts
 *       {@code Assume.assumeTrue(Boolean.getBoolean("PR177_PRESENT"))} — the verdict
 *       {@code run-smoke-test.sh}'s {@code detect_pr177_presence()} stage (plan 18.5-01)
 *       computes from the BUILT server-setup and forwards as a system property. When PR #177
 *       is absent (the current branch), the WHOLE CLASS skips cleanly: the dedicated
 *       {@link SmtpStub} on {@code SMTP_JS_PORT} never starts, and the JS channel is never
 *       pumped, so there is no runtime "no such method" error from Rhino attempting to resolve
 *       the not-yet-existing 7-arg overload. Once PR #177 lands and {@code PR177_PRESENT} flips
 *       true, the gate opens and every assertion below runs for real (D-06: they must be GREEN
 *       in CI once #177 lands, part of the merge gate) — with zero code changes needed in this
 *       file.</li>
 * </ol>
 *
 * <p>Per the own-property / dedicated-stub-per-port convention established by
 * {@link DicomTlsRoundTripTest} and {@link SmtpCcBccRoundTripTest} (RESEARCH Pitfall 3), this
 * class declares its OWN {@code requireSmtpJsProperty} replica (not added to
 * {@link SmokeTestBase#baseSetUp()}) and starts its OWN dedicated {@link SmtpStub} on
 * {@code SMTP_JS_PORT} — never sharing {@link SmtpCcBccRoundTripTest}'s
 * {@code SMTP_CCBCC_PORT} stub, to avoid cross-channel GreenMail message bleed.
 */
public class SmtpJsApiCcBccTest extends SmokeTestBase {

    private static final String SMTP_JS_CHANNEL_ID = "00000039-0000-0000-0000-000000000039";

    /** Matches smtp-js-ccbcc-test.xml's channelMap.patientName selector value for D1 (7-arg). */
    private static final String SELECTOR_7ARG = "JsApi7Arg";

    /** Matches smtp-js-ccbcc-test.xml's channelMap.patientName selector value for D2 (6-arg). */
    private static final String SELECTOR_6ARG = "JsApi6Arg";

    /** Matches smtp-js-ccbcc-test.xml's channelMap.patientName selector value for D3 (5-arg). */
    private static final String SELECTOR_5ARG = "JsApi5Arg";

    private static SmtpStub smtpStub;

    private static boolean pr177Present;

    /**
     * Local replica of {@code SmokeTestBase}'s private {@code requireProperty} /
     * {@code SmtpCcBccRoundTripTest}'s {@code requireSmtpProperty} — deliberately NOT added to
     * {@link SmokeTestBase#baseSetUp()} (own-property convention: other test classes keep
     * running unmodified without {@code SMTP_JS_PORT} set).
     */
    private static String requireSmtpJsProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required system property '" + name + "' not set. "
                    + "SmtpJsApiCcBccTest only runs against a live harness — invoke via "
                    + "smoke-tests/run-smoke-test.sh, which passes harness ports/paths as -D "
                    + "properties to `ant -f smoke-tests/build.xml test-run`.");
        }
        return value;
    }

    /**
     * FIRST checks the {@code PR177_PRESENT} Assume gate — if false (the current branch), this
     * whole class SKIPS cleanly and nothing below runs: no dedicated stub starts, no channel is
     * pumped. Only when the gate is open does this method start the dedicated
     * {@link SmtpStub} on {@code SMTP_JS_PORT} and pump the JS channel three times, once per
     * overload arity, each selected via a distinct HL7 PID last name
     * ({@code channelMap.patientName} in {@code smtp-js-ccbcc-test.xml}'s transformer step).
     */
    @BeforeClass
    public static void startStubs() throws Exception {
        pr177Present = Boolean.getBoolean("PR177_PRESENT");
        Assume.assumeTrue("PR #177 not present — JS 7-arg send absent on this branch; JS-API "
                + "coverage (SC-3) is gated per this phase's dual-safe posture and re-verifies "
                + "once #177 lands", pr177Present);

        smtpStub = new SmtpStub(Integer.parseInt(requireSmtpJsProperty("SMTP_JS_PORT")));
        smtpStub.start();
        registerStubStop(smtpStub::stop);

        rest.processMessage(SMTP_JS_CHANNEL_ID, hl7WithSelector(SELECTOR_7ARG));
        rest.processMessage(SMTP_JS_CHANNEL_ID, hl7WithSelector(SELECTOR_6ARG));
        rest.processMessage(SMTP_JS_CHANNEL_ID, hl7WithSelector(SELECTOR_5ARG));
    }

    /**
     * Substitutes the stock corpus's PID last name ("McDoogal") with {@code selector} so
     * {@code smtp-js-ccbcc-test.xml}'s transformer step
     * ({@code channelMap.put('patientName', msg['PID']['PID.5']['PID.5.1'].toString())}) hands
     * the destination script an unambiguous per-message overload selector, without needing a
     * new HL7 corpus constant in {@link Hl7Messages}.
     */
    private static String hl7WithSelector(String selector) {
        return Hl7Messages.ORU_R01_LF.replace("McDoogal", selector);
    }

    /**
     * D1 — the new 7-arg {@code send(to,cc,bcc,from,subject,body,charset)} PR #177 adds. Proves
     * the ASVS V8 confidentiality control on the JS-API path, mirroring plan 18.5-03's
     * delivered+blind discipline: {@code to@}/{@code cc@} are delivered AND visible in headers,
     * {@code bcc@} is delivered AND absent from every visible header of every received copy
     * (T-18.5-01). Never weakened to a bare "an email was sent" check
     * (must_haves.prohibitions).
     */
    @Test
    public void jsApi7ArgDelivers() throws Exception {
        assertThreeLevels(SMTP_JS_CHANNEL_ID, 1, () -> {
            pollUntil("SmtpStub delivered a copy to to@to.smoke.test's own mailbox (D1, 7-arg)", 30,
                    () -> messagesForRecipientQuiet("to@to.smoke.test").length >= 1);
            pollUntil("SmtpStub delivered a copy to cc@cc.smoke.test's own mailbox (D1, 7-arg)", 30,
                    () -> messagesForRecipientQuiet("cc@cc.smoke.test").length >= 1);
            pollUntil("SmtpStub delivered a copy to bcc@bcc.smoke.test's own mailbox (D1, 7-arg, DELIVERED)", 30,
                    () -> messagesForRecipientQuiet("bcc@bcc.smoke.test").length >= 1);

            assertTrue("cc@cc.smoke.test should be visible in the Cc header of its own delivered copy (D1)",
                    anyMessageHeaderContains(smtpStub.getReceivedMessagesForRecipient("cc@cc.smoke.test"),
                            "Cc", "cc@cc.smoke.test"));

            assertNoBccLeak("bcc@bcc.smoke.test");
        });
    }

    /**
     * D2 — the pre-existing legacy 6-arg {@code send(to,cc,from,subject,body,charset)} must
     * keep delivering to@/cc@ unchanged, with no bcc@ delivery at all (this overload takes no
     * bcc parameter — it delegates with a null bcc internally, per the JS API's existing
     * contract).
     */
    @Test
    public void jsApi6ArgBackCompat() throws Exception {
        assertThreeLevels(SMTP_JS_CHANNEL_ID, 1, () -> {
            pollUntil("SmtpStub delivered a copy to to@to.smoke.test's own mailbox (D2, 6-arg)", 30,
                    () -> messagesForRecipientQuiet("to@to.smoke.test").length >= 1);
            pollUntil("SmtpStub delivered a copy to cc@cc.smoke.test's own mailbox (D2, 6-arg)", 30,
                    () -> messagesForRecipientQuiet("cc@cc.smoke.test").length >= 1);

            assertTrue("cc@cc.smoke.test should be visible in the Cc header of its own delivered copy (D2)",
                    anyMessageHeaderContains(smtpStub.getReceivedMessagesForRecipient("cc@cc.smoke.test"),
                            "Cc", "cc@cc.smoke.test"));
        });
    }

    /**
     * D3 — the pre-existing legacy 5-arg {@code send(to,cc,from,subject,body)} must keep
     * delivering to@/cc@ unchanged (no charset argument, no bcc argument).
     */
    @Test
    public void jsApi5ArgBackCompat() throws Exception {
        assertThreeLevels(SMTP_JS_CHANNEL_ID, 1, () -> {
            pollUntil("SmtpStub delivered a copy to to@to.smoke.test's own mailbox (D3, 5-arg)", 30,
                    () -> messagesForRecipientQuiet("to@to.smoke.test").length >= 1);
            pollUntil("SmtpStub delivered a copy to cc@cc.smoke.test's own mailbox (D3, 5-arg)", 30,
                    () -> messagesForRecipientQuiet("cc@cc.smoke.test").length >= 1);

            assertTrue("cc@cc.smoke.test should be visible in the Cc header of its own delivered copy (D3)",
                    anyMessageHeaderContains(smtpStub.getReceivedMessagesForRecipient("cc@cc.smoke.test"),
                            "Cc", "cc@cc.smoke.test"));
        });
    }

    /**
     * Poll-lambda-friendly wrapper around {@link SmtpStub#getReceivedMessagesForRecipient(String)}
     * — {@code BooleanSupplier} cannot declare checked exceptions, and a transient lookup
     * failure (e.g. GreenMail has not yet auto-created the recipient's user) should read as
     * "not yet delivered", not abort the poll. Mirrors {@link SmtpCcBccRoundTripTest}'s helper
     * of the same name.
     */
    private static MimeMessage[] messagesForRecipientQuiet(String emailAddress) {
        try {
            return smtpStub.getReceivedMessagesForRecipient(emailAddress);
        } catch (Exception e) {
            return new MimeMessage[0];
        }
    }

    /**
     * Returns whether {@code headerName} on {@code message} contains {@code needle}, swallowing
     * {@link MessagingException} as "absent". Mirrors {@link SmtpCcBccRoundTripTest}'s helper of
     * the same name.
     */
    private static boolean headerContains(MimeMessage message, String headerName, String needle) {
        try {
            String[] values = message.getHeader(headerName);
            if (values == null) {
                return false;
            }
            for (String value : values) {
                if (value != null && value.contains(needle)) {
                    return true;
                }
            }
            return false;
        } catch (MessagingException e) {
            return false;
        }
    }

    private static boolean anyMessageHeaderContains(MimeMessage[] messages, String headerName, String needle) {
        for (MimeMessage message : messages) {
            if (headerContains(message, headerName, needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * T-18.5-01 (V8 confidentiality) — scans EVERY copy the dedicated {@code SMTP_JS_PORT} stub
     * currently holds (not merely a per-domain subset) and asserts none of {@code bccAddresses}
     * ever appears in a To or Cc header, and that no copy carries a Bcc header at all
     * (javax.mail's SMTPTransport strips it before the wire DATA phase). Mirrors
     * {@link SmtpCcBccRoundTripTest#assertNoBccLeak(String...)}.
     */
    private static void assertNoBccLeak(String... bccAddresses) throws MessagingException {
        for (MimeMessage message : smtpStub.getReceivedMessages()) {
            assertNull("Bcc header must never be transmitted on the wire (javax.mail "
                    + "SMTPTransport strips it before the SMTP DATA phase)", message.getHeader("Bcc"));
            for (String bcc : bccAddresses) {
                assertFalse("To header must never contain BCC address " + bcc,
                        headerContains(message, "To", bcc));
                assertFalse("Cc header must never contain BCC address " + bcc,
                        headerContains(message, "Cc", bcc));
            }
        }
    }
}
