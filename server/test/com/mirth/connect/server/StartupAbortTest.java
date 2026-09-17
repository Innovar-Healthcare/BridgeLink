package com.mirth.connect.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;

import org.junit.Test;

/**
 * Unit tests for the aborted-startup exit status (IRT-2353).
 *
 * Background: the server does not run on the JVM's main thread. MirthLauncher loads Mirth
 * reflectively as a non-daemon Thread and starts it, so nothing in run() or startup() sets the
 * process status and every abort ended at 0. A systemd unit with Restart=on-failure recorded
 * Result=success, went inactive rather than failed, and was never restarted.
 *
 * The fix is deliberately minimal: each abort site exits at the same statement it did before, in
 * the same order, with the same shutdown hooks registered, and only the status changed.
 *
 * One honest exception, on the path this ticket is about. When a second- or third-retry-loop
 * failure carried a bare exception, the old unguarded e.getCause() threw NullPointerException out
 * of the catch block, so the session-closing finally that follows it DID run before the thread
 * died. That path now reaches System.exit like the others, which pre-empts that finally. The
 * shutdown hook runs either way and closes down the same subsystems, so this is a difference in
 * how a dying process tidies up, not in what an operator sees - but "nothing moves" would be an
 * overstatement and this is the one place it does.
 *
 * Coverage boundary: System.exit cannot be exercised in-process, so as with DerbyPreflightTest
 * and RootCheckTest what is tested here is every decision made around those calls - the status
 * constant, the message rendering, and source-scan guards over the call sites themselves. The
 * exit calls are covered end to end by smoke-tests/break-postgres-driver.sh. These tests are all
 * pure statics and so, unlike RootCheckTest, need no Guice or Mockito setup.
 */
public class StartupAbortTest {

    private static final String MIRTH_JAVA_PATH = "src/com/mirth/connect/server/Mirth.java";
    private static final String MIRTH_LAUNCHER_JAVA_PATH = "src/com/mirth/connect/server/launcher/MirthLauncher.java";

    // ===== The exit status itself =====

    @Test
    public void exitStatusIsNonZero() {
        // The whole point of IRT-2353. systemd counts 0 as success no matter what the unit file
        // says, so a zero here means Restart=on-failure never fires.
        assertTrue("an aborted startup must not exit 0", Mirth.EXIT_STARTUP_ABORTED != 0);
    }

    @Test
    public void exitStatusMatchesTheExistingPreflightConvention() {
        // checkDerbyJavaVersion (IRT-1488), checkRunningAsRoot (IRT-584) and MirthLauncher's own
        // root check all exit 1 already. One code for every abort, not one per cause.
        assertEquals(1, Mirth.EXIT_STARTUP_ABORTED);
    }

    // ===== causeMessage: the unguarded getCause() NPE on the reported line =====

    @Test
    public void causeMessageUnwrapsAWrappedDriverException() {
        // DonkeyConnectionPools.init wraps in RuntimeException, so this is the shape the first
        // retry loop produces. Pre-IRT-2353 behaviour, preserved byte for byte.
        Throwable wrapped = new RuntimeException(new SQLException("password authentication failed"));
        assertEquals("password authentication failed", Mirth.causeMessage(wrapped));
    }

    @Test
    public void causeMessageHandlesAnExceptionWithNoCause() {
        // The falsifying test. The second and third retry loops rethrow whatever getConnection()
        // threw, unwrapped. Against the pre-IRT-2353 expression this line throws
        // NullPointerException from inside the catch block, so the abort never reached its own
        // System.exit at all and the process died by another route - also at status 0.
        assertEquals("connection refused", Mirth.causeMessage(new SQLException("connection refused")));
    }

    @Test
    public void causeMessageHandlesNull() {
        assertEquals("", Mirth.causeMessage(null));
    }

    @Test
    public void causeMessageFallsBackToToStringWhenTheCauseHasNoMessage() {
        // Never render a bare "null" into an operator-facing log line.
        String rendered = Mirth.causeMessage(new RuntimeException(new NullPointerException()));
        assertEquals(new NullPointerException().toString(), rendered);
    }

    @Test
    public void causeMessageSurvivesASelfReferentialCause() {
        // Throwable.initCause rejects self-causation, but getCause() is overridable and some
        // third-party exception types do override it. Such a throwable must resolve to itself
        // rather than recursing.
        SQLException selfReferential = new SQLException("self") {
            private static final long serialVersionUID = 1L;

            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertEquals("self", Mirth.causeMessage(selfReferential));
    }

    // ===== The database abort line =====

    @Test
    public void databaseAbortMessageIsVerbatimTheShippedText() {
        // Operator runbooks grep for this string and it is recorded verbatim in the 26.8-03 phase
        // summary. It must not drift.
        assertEquals("Error establishing connection to database, aborting startup. "
                + "Failed to start database with class loader jdk.internal.loader.ClassLoaders$AppClassLoader",
                Mirth.databaseAbortMessage(new RuntimeException(new SQLException(
                        "Failed to start database with class loader jdk.internal.loader.ClassLoaders$AppClassLoader"))));
    }

    @Test
    public void databaseAbortMessagePrefixIsUnchanged() {
        assertEquals("Error establishing connection to database, aborting startup. ",
                Mirth.DATABASE_ABORT_MSG_PREFIX);
    }

    // ===== No abort path may exit 0 again =====

    @Test
    public void onlyTheNormalShutdownExitUsesStatusZero() throws Exception {
        // Source scan, in the idiom of StartupStringsAsciiTest. The single legitimate
        // System.exit(0) is main()'s, reached only after the command loop ends normally. A new
        // abort path added with exit(0) would reintroduce this bug silently, because nothing else
        // in the suite asserts on the process status.
        assertEquals("only main()'s normal-shutdown exit may use status 0",
                1, countOutsideComments(MIRTH_JAVA_PATH, "System.exit(0)"));
    }

    @Test
    public void everyOtherExitUsesTheAbortStatus() throws Exception {
        // Pins the five abort sites: the two preflights, the port check, the resources check and
        // the database abort. If this number changes, a call site was added or removed and the
        // test above should be read alongside it.
        assertEquals("every exit but main()'s must use EXIT_STARTUP_ABORTED",
                5, countOutsideComments(MIRTH_JAVA_PATH, "System.exit(EXIT_STARTUP_ABORTED)"));
    }

    // ===== MirthLauncher: the abort that happens before the server thread exists =====

    @Test
    public void mirthLauncherExitsNonZeroWhenTheServerThreadNeverStarted() throws Exception {
        // MirthLauncher cannot reference EXIT_STARTUP_ABORTED - it runs from
        // mirth-server-launcher.jar and must not depend on mirth-server.jar, the same constraint
        // that forces ROOT_CHECK_ERROR_MSG to be duplicated - so this is the only thing standing
        // between the literal there and a silent revert to the pre-IRT-2353 exit 0.
        assertEquals("MirthLauncher must record a failure status when Mirth never started",
                1, countOutsideComments(MIRTH_LAUNCHER_JAVA_PATH, "exitStatus = 1"));
        assertEquals(1, countOutsideComments(MIRTH_LAUNCHER_JAVA_PATH, "System.exit(exitStatus)"));
    }

    @Test
    public void mirthLauncherExitsAfterTheFinallySoTheJarStillCloses() throws Exception {
        // The exit must follow the finally block, or mirthClientCoreJarFile is left open. Pinned
        // by position because there is no way to observe it without forking a JVM.
        String stripped = strippedSource(MIRTH_LAUNCHER_JAVA_PATH);
        assertTrue("System.exit(exitStatus) must come after the finally block",
                stripped.indexOf("System.exit(exitStatus)") > stripped.indexOf("} finally {"));
    }

    @Test
    public void thePortAbortExitsBeforeTheShutdownHookIsRegistered() throws Exception {
        // The port abort sits above the hook registration, so it runs no hook - exactly as the
        // bare return it replaced did. checkDerbyJavaVersion carries the same requirement and
        // says so in its own comment (IRT-1488 / 16-REVIEW WR-05). If the registration is ever
        // moved earlier, this abort silently starts running shutdown() against subsystems that
        // were never started.
        //
        // Anchored on the port check rather than on the first exit in the file, which would pass
        // trivially on the two preflight methods declared above run(). Only the port abort can be
        // pinned positionally: the resources abort is textually below the hook registration (it
        // is in run()'s else branch) yet executes before it, so source order says nothing there.
        String stripped = strippedSource(MIRTH_JAVA_PATH);

        int portCheck = stripped.indexOf("if (!httpPort || !httpsPort)");
        assertTrue("the port check must be present", portCheck > 0);

        int portAbort = stripped.indexOf("System.exit(EXIT_STARTUP_ABORTED)", portCheck);
        int hookRegistration = stripped.indexOf("Runtime.getRuntime().addShutdownHook");
        assertTrue("the shutdown hook registration must be present", hookRegistration > 0);

        assertTrue("the port abort must exit before the shutdown hook is registered",
                portAbort > 0 && portAbort < hookRegistration);
    }

    /**
     * Counts occurrences of a literal in a source file, ignoring line and block comments. Comments
     * have to be stripped or these assertions are not falsifiable: the comments in Mirth.java
     * discuss System.exit by name, so a naive scan counts the prose, and the only way to satisfy
     * it would be to write around the prose - leaving the next reader in the same trap. String
     * literals are not stripped; no source in scope embeds one of these tokens.
     */
    private static int countOutsideComments(String path, String needle) throws Exception {
        String stripped = strippedSource(path);

        int count = 0;
        int index = stripped.indexOf(needle);
        while (index >= 0) {
            count++;
            index = stripped.indexOf(needle, index + 1);
        }

        return count;
    }

    private static String strippedSource(String path) throws Exception {
        File source = new File(path);
        assertTrue("source must be found at " + source.getAbsolutePath(), source.isFile());

        String text = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

}
