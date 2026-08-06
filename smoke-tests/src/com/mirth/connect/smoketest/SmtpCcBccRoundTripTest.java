package com.mirth.connect.smoketest;

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
}
