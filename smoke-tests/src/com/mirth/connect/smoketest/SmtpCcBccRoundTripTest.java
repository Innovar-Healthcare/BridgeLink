package com.mirth.connect.smoketest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.smoketest.stubs.SmtpStub;

/**
 * Phase 18.5 (NET-10) — SMTP CC/BCC automated coverage. This is the tracer for the phase
 * (plan 18.5-02, D-03 risk ordering): {@link #legacyNullNoNpe()} proves the phase's #1 risk
 * (SC-2 / F1) end-to-end through the REAL in-server {@code SmtpDispatcher.send()} path — a
 * deployed channel whose {@code <cc>}/{@code <bcc>} elements are entirely absent from the
 * fixture XML (not merely empty) dispatches with no NPE.
 *
 * <p><b>Plan 18.5-03 additions</b> — {@code smtp-ccbcc-test.xml} (channel
 * {@code SMTP_CCBCC_CHANNEL_ID}) has three destinations sharing this same shared stub, each
 * pumped once in {@link #startStubs()} and asserted by its own {@code @Test}:
 * <ul>
 *   <li>{@link #ccBccBlindDelivery()} (SC-1 / B1) — Destination 1: single distinct-domain
 *       to/cc/bcc mailboxes. Proves the ASVS V8 confidentiality control: bcc@ receives a copy
 *       (delivered) AND appears in NO visible header of ANY received copy (blind), while cc@
 *       DOES appear in the visible Cc header.</li>
 *   <li>{@link #multipleCcBccDelivery()} (SC-1 / B2) — Destination 2: comma-separated
 *       multi-recipient cc/bcc lists (cc1@/cc2@, bcc1@/bcc2@). Same delivered+blind property,
 *       proven per-recipient.</li>
 *   <li>{@link #whitespaceNotTrimmed_characterization()} (SC-5 / B5, D-04) — Destination 3:
 *       cc list with a space after the comma ({@code "a@ws.smoke.test, b@ws.smoke.test"}).
 *       Records the OBSERVED behavior only (no code fix): {@code SmtpDispatcher.java:212}'s
 *       {@code StringUtils.split(getCc(), ",")} does NOT trim tokens, but
 *       {@code javax.mail.internet.InternetAddress}'s own RFC822 parser (invoked by
 *       commons-email's {@code Email.addCc(String)}) silently absorbs the untrimmed leading
 *       whitespace, so BOTH recipients still parse to valid, distinct addresses and BOTH still
 *       receive a copy — empirically confirmed via a standalone {@code InternetAddress} probe
 *       against the shipped {@code javax.mail-1.6.2.jar} before authoring this assertion (see
 *       this plan's SUMMARY), not assumed from reading the split call alone.</li>
 * </ul>
 * These three stand on pre-existing stock {@code SmtpDispatcher} cc/bcc split-loop code (not
 * gated on PR #177) and use per-recipient {@code MimeMessage} header inspection (never a bare
 * "an email was sent" check) so a BCC-leaking regression would fail
 * {@link #ccBccBlindDelivery()}/{@link #multipleCcBccDelivery()} outright.
 *
 * <p><b>Rule 1 fix, live-discovered:</b> the Plan 18.5-01 {@code getReceivedMessagesForDomain}
 * accessor cannot prove BCC delivery — it is header-based (matches on
 * {@code MimeMessage.getAllRecipients()}, i.e. the STORED copy's own To/Cc/Bcc headers), and
 * every stored copy of a BCC'd message has a {@code null} Bcc header by design, so it always
 * returns empty for a bcc-only domain query even though GreenMail genuinely delivered a copy.
 * Confirmed live: the first full-harness run of this plan's tests failed both
 * {@link #ccBccBlindDelivery()} and {@link #multipleCcBccDelivery()} with a 30s poll timeout on
 * the bcc-domain check. {@link SmtpStub#getReceivedMessagesForRecipient(String)} (new, this
 * plan) queries GreenMail's per-recipient IMAP mailbox instead — genuinely delivery-based, not
 * header-based — and is what every delivery assertion below now uses.
 *
 * <p><b>Why this must run through a deployed channel, never a driver-side
 * {@code new SmtpDispatcher().send()} call (OQ-2):</b> {@code SmtpDispatcher} instantiates its
 * {@code EventController}/{@code ConfigurationController} via {@code ControllerFactory} in
 * instance-field initializers (SmtpDispatcher.java:48-50), which are only wired up inside the
 * running server JVM. The smoke-test driver JVM has no such wiring, so the only faithful way to
 * exercise the unguarded {@code StringUtils.split(getCc()/getBcc(), ",")} for-each loop
 * (SmtpDispatcher.java:207-219) against a genuinely null value is to deploy a real channel and
 * pump a message through the live server.
 *
 * <p><b>D-06 escalation — CONFIRMED (18.5-02 live finding):</b> on this branch,
 * {@link #legacyNullNoNpe()} fails with exactly the predicted defect: {@code getCc()}
 * deserializes to {@code null} (resolves OQ-1 — the omitted {@code <cc>} element does NOT
 * deserialize to {@code ""}), {@code StringUtils.split(null, ",")} returns {@code null}, and
 * the unguarded for-each throws {@code NullPointerException: Cannot read the array length
 * because "<local12>" is null} at {@code SmtpDispatcher.java:212} — caught by
 * {@code DestinationConnector}, surfacing as the channel's message going to ERROR (not SENT).
 * This is a genuine feature defect (the missing {@code isNotBlank} guard), NOT a test bug —
 * the fix belongs to PR #177, not this phase. Do NOT add a guard to {@code SmtpDispatcher} to
 * make this test pass; see this plan's SUMMARY for the full escalation record. Per user
 * decision, {@link #legacyNullNoNpe()} is now gated on the {@code PR177_PRESENT} system
 * property and SKIPS on this branch until PR #177 lands; the assertions themselves are
 * unchanged and will run for real (and prove GREEN) the moment that property is set.
 *
 * <p>Per the own-property convention established by {@link DicomTlsRoundTripTest}, this class
 * declares a local {@code requireSmtpProperty} replica rather than adding
 * {@code SMTP_CCBCC_PORT} to {@link SmokeTestBase#baseSetUp()} — other test classes keep
 * running unmodified without this new {@code -D} property set. A dedicated {@link SmtpStub} on
 * its own port ({@code SMTP_CCBCC_PORT}) avoids cross-channel message bleed on the
 * shared {@code SMTP_PORT} stub used by {@link StubChannelsTest} (RESEARCH Pitfall 3).
 */
public class SmtpCcBccRoundTripTest extends SmokeTestBase {

    private static final String SMTP_LEGACY_NULL_CHANNEL_ID = "00000038-0000-0000-0000-000000000038";
    private static final String SMTP_CCBCC_CHANNEL_ID = "00000037-0000-0000-0000-000000000037";

    private static SmtpStub smtpStub;

    /**
     * Local replica of {@code SmokeTestBase}'s private {@code requireProperty} /
     * {@code DicomTlsRoundTripTest}'s {@code requireDicomTlsProperty} — deliberately NOT added
     * to {@link SmokeTestBase#baseSetUp()} (own-property convention, see class javadoc).
     */
    private static String requireSmtpProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required system property '" + name + "' not set. "
                    + "SmtpCcBccRoundTripTest only runs against a live harness — invoke via "
                    + "smoke-tests/run-smoke-test.sh, which passes harness ports/paths as -D "
                    + "properties to `ant -f smoke-tests/build.xml test-run`.");
        }
        return value;
    }

    @BeforeClass
    public static void startStubs() throws Exception {
        smtpStub = new SmtpStub(Integer.parseInt(requireSmtpProperty("SMTP_CCBCC_PORT")));
        smtpStub.start();
        registerStubStop(smtpStub::stop);

        rest.processMessage(SMTP_LEGACY_NULL_CHANNEL_ID, Hl7Messages.ORU_R01_LF);
        // Plan 18.5-03: pump all three smtp-ccbcc-test.xml destinations (B1/B2/B5) once, up
        // front, exactly like doc-writer-test.xml's multi-destination pump pattern
        // (StubChannelsTest#pumpAll). Each destination's own @Test polls/asserts on the
        // resulting GreenMail state independently below.
        rest.processMessage(SMTP_CCBCC_CHANNEL_ID, Hl7Messages.ORU_R01_LF, List.of(1, 2, 3));
    }

    /**
     * SC-2 / F1 — the #1 risk of this phase. {@code smtp-legacy-null-test.xml}'s destination
     * connector omits the {@code <cc>}/{@code <bcc>} elements entirely (reproducing a
     * legacy-shaped export). {@link SmokeTestBase#assertThreeLevels} asserts the CHANNEL's own
     * SENT count is {@code >= 1} AND its ERROR count is {@code == 0} — the load-bearing proof
     * that the unguarded {@code StringUtils.split(null, ",")} for-each loop did not throw. The
     * {@code to-legacy@} delivery check inside is a secondary confirmation that the message
     * actually reached the destination (not merely that no exception surfaced), using the
     * dedicated per-domain accessor (never the global {@code waitForIncomingEmail} count,
     * which could false-positive on cross-channel bleed if this stub were ever shared).
     *
     * <p>CURRENT STATUS on this branch: GATED — see class javadoc's D-06 escalation note. The
     * defect is genuine and confirmed (documented above and in this plan's SUMMARY), but per
     * user decision this test is gated on the {@code PR177_PRESENT} system property and SKIPS
     * (not fails) until PR #177 lands. This is NOT the test being weakened to pass around the
     * defect (that would violate this plan's F1 prohibition) — the assertions below are
     * unchanged and unconditionally re-enabled the moment {@code PR177_PRESENT=true} is passed,
     * with zero changes needed to this file. {@code SmtpDispatcher} is NOT patched here
     * (D-06/D-04).
     */
    @Test
    public void legacyNullNoNpe() throws Exception {
        Assume.assumeTrue("PR #177 not present — SmtpDispatcher null cc/bcc guard absent on "
                + "this branch; legacy-null no-NPE proof is gated per D-06 user decision and "
                + "re-verifies once #177 lands", Boolean.getBoolean("PR177_PRESENT"));
        assertThreeLevels(SMTP_LEGACY_NULL_CHANNEL_ID, 1, () -> {
            pollUntil("SmtpStub received a copy for legacy.smoke.test", 30,
                    () -> smtpStub.getReceivedMessagesForDomain("legacy.smoke.test").length >= 1);
        });
    }

    /**
     * SC-1 / B1 — the phase's core value + the ASVS V8 confidentiality control.
     * {@code smtp-ccbcc-test.xml}'s Destination 1 sets distinct-domain to/cc/bcc/from
     * mailboxes. Proves TWO independent facts about {@code bcc@bcc.smoke.test}, never
     * collapsing them into a single "an email was sent" check (RESEARCH anti-pattern,
     * must_haves.prohibitions):
     * <ul>
     *   <li>(a) DELIVERED — {@code bcc@} receives its own copy in its own mailbox, confirmed via
     *       {@link SmtpStub#getReceivedMessagesForRecipient(String)} (NOT the domain-based
     *       accessor — see that method's 18.5-03 javadoc correction: domain filtering is
     *       header-based and can never observe a BCC delivery, since the Bcc header is stripped
     *       from every copy before the wire DATA phase).</li>
     *   <li>(b) BLIND — {@code bcc@bcc.smoke.test} appears in NO visible header (To/Cc/Bcc) of
     *       ANY copy the stub holds, and no copy carries a {@code Bcc} header at all (stock
     *       {@code javax.mail} SMTPTransport strips it before the wire DATA phase — pre-existing
     *       behavior, not modified by this phase).</li>
     * </ul>
     * Also confirms {@code cc@cc.smoke.test} IS visible in the Cc header of its own delivered
     * copy (the positive control — CC is NOT meant to be blind).
     */
    @Test
    public void ccBccBlindDelivery() throws Exception {
        assertThreeLevels(SMTP_CCBCC_CHANNEL_ID, 1, () -> {
            pollUntil("SmtpStub delivered a copy to to@to.smoke.test's own mailbox (B1)", 30,
                    () -> messagesForRecipientQuiet("to@to.smoke.test").length >= 1);
            pollUntil("SmtpStub delivered a copy to cc@cc.smoke.test's own mailbox (B1)", 30,
                    () -> messagesForRecipientQuiet("cc@cc.smoke.test").length >= 1);
            pollUntil("SmtpStub delivered a copy to bcc@bcc.smoke.test's own mailbox (B1, DELIVERED)", 30,
                    () -> messagesForRecipientQuiet("bcc@bcc.smoke.test").length >= 1);

            assertTrue("cc@cc.smoke.test should be visible in the Cc header of its own delivered copy",
                    anyMessageHeaderContains(smtpStub.getReceivedMessagesForRecipient("cc@cc.smoke.test"),
                            "Cc", "cc@cc.smoke.test"));

            assertNoBccLeak("bcc@bcc.smoke.test");
        });
    }

    /**
     * SC-1 / B2 — the multi-recipient adjacency edge: comma-separated CC/BCC lists, distinct
     * mailboxes. {@code smtp-ccbcc-test.xml}'s Destination 2 sets
     * {@code cc1@cc.smoke.test,cc2@cc.smoke.test} / {@code bcc1@bcc.smoke.test,bcc2@bcc.smoke.test}.
     * Every listed recipient must receive its own copy in its own mailbox (delivered, proven via
     * {@link SmtpStub#getReceivedMessagesForRecipient(String)} — see {@link #ccBccBlindDelivery()}
     * for why the domain-based accessor cannot prove BCC delivery), headers must show every cc
     * entry, and every bcc entry must stay blind exactly like the B1 single-recipient case.
     */
    @Test
    public void multipleCcBccDelivery() throws Exception {
        assertThreeLevels(SMTP_CCBCC_CHANNEL_ID, 1, () -> {
            pollUntil("SmtpStub delivered a copy to cc1@cc.smoke.test's own mailbox (B2)", 30,
                    () -> messagesForRecipientQuiet("cc1@cc.smoke.test").length >= 1);
            pollUntil("SmtpStub delivered a copy to cc2@cc.smoke.test's own mailbox (B2)", 30,
                    () -> messagesForRecipientQuiet("cc2@cc.smoke.test").length >= 1);
            pollUntil("SmtpStub delivered a copy to bcc1@bcc.smoke.test's own mailbox (B2, DELIVERED)", 30,
                    () -> messagesForRecipientQuiet("bcc1@bcc.smoke.test").length >= 1);
            pollUntil("SmtpStub delivered a copy to bcc2@bcc.smoke.test's own mailbox (B2, DELIVERED)", 30,
                    () -> messagesForRecipientQuiet("bcc2@bcc.smoke.test").length >= 1);

            assertTrue("cc1@cc.smoke.test's own delivered copy should show BOTH cc1@ and cc2@ in its Cc header",
                    anyMessageHeaderContains(smtpStub.getReceivedMessagesForRecipient("cc1@cc.smoke.test"), "Cc",
                            "cc1@cc.smoke.test")
                    && anyMessageHeaderContains(smtpStub.getReceivedMessagesForRecipient("cc1@cc.smoke.test"), "Cc",
                            "cc2@cc.smoke.test"));
            assertTrue("cc2@cc.smoke.test's own delivered copy should show BOTH cc1@ and cc2@ in its Cc header",
                    anyMessageHeaderContains(smtpStub.getReceivedMessagesForRecipient("cc2@cc.smoke.test"), "Cc",
                            "cc1@cc.smoke.test")
                    && anyMessageHeaderContains(smtpStub.getReceivedMessagesForRecipient("cc2@cc.smoke.test"), "Cc",
                            "cc2@cc.smoke.test"));

            assertNoBccLeak("bcc1@bcc.smoke.test", "bcc2@bcc.smoke.test");
        });
    }

    /**
     * SC-5 / B5 (D-04) — characterization only, no code fix. {@code smtp-ccbcc-test.xml}'s
     * Destination 3 sets {@code cc = "a@ws.smoke.test, b@ws.smoke.test"} (a literal space after
     * the comma). {@code SmtpDispatcher.java:212}'s {@code StringUtils.split(getCc(), ",")}
     * does NOT trim tokens, so the second token is literally {@code " b@ws.smoke.test"}
     * (leading space intact) when passed to commons-email's {@code Email.addCc(String)}.
     *
     * <p>OBSERVED (empirically confirmed via a standalone {@code InternetAddress} probe against
     * the shipped {@code javax.mail-1.6.2.jar} before writing this assertion — see this plan's
     * SUMMARY): {@code javax.mail.internet.InternetAddress}'s own RFC822 parser silently absorbs
     * the untrimmed surrounding whitespace, so BOTH {@code a@ws.smoke.test} and
     * {@code b@ws.smoke.test} still parse to valid, distinct addresses and BOTH still receive
     * their own copy in their own mailbox — the untrimmed split does NOT currently drop or
     * corrupt the second recipient. This assertion is pinned to that OBSERVED outcome, not to
     * what "should" happen; if a future library upgrade changes this behavior, this test is
     * meant to go red and be re-characterized, not silently patched to keep passing.
     */
    @Test
    public void whitespaceNotTrimmed_characterization() throws Exception {
        assertThreeLevels(SMTP_CCBCC_CHANNEL_ID, 1, () -> {
            pollUntil("SmtpStub delivered a copy to a@ws.smoke.test's own mailbox (B5)", 30,
                    () -> messagesForRecipientQuiet("a@ws.smoke.test").length >= 1);
            pollUntil("OBSERVED: SmtpStub delivered a copy to b@ws.smoke.test's own mailbox despite "
                    + "the untrimmed leading space (B5 characterization, record do not fix -- D-04)", 30,
                    () -> messagesForRecipientQuiet("b@ws.smoke.test").length >= 1);

            assertTrue("OBSERVED: Cc header on a@ws.smoke.test's own delivered copy should show "
                    + "both a@ws.smoke.test and b@ws.smoke.test (InternetAddress normalizes away "
                    + "the untrimmed leading space when rendering the header)",
                    anyMessageHeaderContains(smtpStub.getReceivedMessagesForRecipient("a@ws.smoke.test"), "Cc",
                            "a@ws.smoke.test")
                    && anyMessageHeaderContains(smtpStub.getReceivedMessagesForRecipient("a@ws.smoke.test"), "Cc",
                            "b@ws.smoke.test"));
        });
    }

    /**
     * Poll-lambda-friendly wrapper around {@link SmtpStub#getReceivedMessagesForRecipient(String)}
     * — {@code BooleanSupplier} cannot declare checked exceptions, and a transient lookup failure
     * (e.g. GreenMail has not yet auto-created the recipient's user) should read as "not yet
     * delivered", not abort the poll.
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
     * {@link MessagingException} as "absent".
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
     * T-18.5-01 (V8 confidentiality) — scans EVERY copy the stub currently holds (not merely a
     * per-domain subset) and asserts none of {@code bccAddresses} ever appears in a To or Cc
     * header, and that no copy carries a Bcc header at all (javax.mail's SMTPTransport strips
     * it before the wire DATA phase). Global scope is deliberate: a BCC leak into a To/Cc header
     * could in principle show up on ANY delivered copy, not only the ones addressed to a
     * bcc-domain recipient.
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
