/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.seams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Scriptable;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;
import com.mirth.connect.server.util.javascript.MirthContextFactory;

/**
 * Rhino script-facing OSHI characterization suite (IRT-1801, D-05). No BridgeLink Java source
 * imports OSHI or JNA -- the entire consumer surface is {@code Packages.oshi.*} reached from
 * Rhino channel scripts, so a compile-time break on the jar swap is impossible and only a
 * runtime reflective test like this one can catch a break on that surface.
 * <p>
 * Framing (no hedging): this suite is evidence that the script-facing OSHI surface resolves
 * through the real, production {@link MirthContextFactory} at the shipped ES6 language level and
 * returns real CPU topology values on {@code oshi-core-6.12.0.jar} + {@code jna-5.18.1.jar} +
 * {@code jna-platform-5.18.1.jar}, on whatever architecture this suite happens to run (x86_64 CI
 * runners, arm64 developer Macs). It is NOT aarch64 evidence: CI runners are x86_64 only, and the
 * platform-specific {@code oshi.hardware.platform.linux.LinuxCentralProcessor} code path this
 * upgrade actually fixes is unreachable from any CI leg. The aarch64 proof is the separate
 * {@code regression-scripts/test-irt1801-oshi-arm64.sh} linux/arm64 container leg (plan 03).
 * <p>
 * Harness copied verbatim from {@link RhinoSeamTest}: the same mocked {@code ControllerFactory}/
 * {@code ConfigurationController}/{@code ExtensionController} wiring, the same Guice static
 * injection, and the same {@code server/conf} {@code URLClassLoader} dance (closed in a
 * {@code finally}) needed for {@code MirthContextFactory}'s one-time static initializer to find
 * {@code mirth.properties} without a {@code build.xml} edit.
 */
// ENLIGHTEN CALL SURFACE (re-derived 2026-08-27)
// Source: local Enlighten export, not committed -- ~/mirthdev/Enlighten/individual/channel/
// "Memory Monitor.xml" and ~/mirthdev/Enlighten/channelGroup/"Enlighten Interface Monitoring.xml"
// (both outside this repository; read for method names only, per D-06 and CLAUDE.md's PHI /
// customer-IP prohibition). This re-derives RESEARCH.md Assumption A2 / Open Question 3 instead
// of taking the D-06 twelve-call list on trust.
//
// Every method actually invoked on the oshi object chain (systemInfo -> hardware -> processor /
// memory) in the real channel and channel group exports, cross-checked against D-06:
//   new Packages.oshi.SystemInfo()        -- constructor; no separate getSystemInfo() accessor
//                                             exists on this surface -- confirmed against D-06
//   .getHardware()                        -- confirmed against D-06
//   .getProcessor()                       -- confirmed against D-06
//   .getMemory()                          -- confirmed against D-06
//   processor.getSystemCpuLoadTicks()     -- confirmed against D-06
//   processor.getLogicalProcessorCount()  -- confirmed against D-06
//   processor.getSystemCpuLoad()          -- confirmed against D-06 (D-07 break, pinned below)
//   processor.getVendorFreq()             -- confirmed against D-06 (D-07 break, pinned below)
//   memory.getTotal()                     -- confirmed against D-06
//   memory.getAvailable()                 -- confirmed against D-06
//   memory.getSwapUsed()                  -- confirmed against D-06 (D-07 break, pinned below)
//   memory.getSwapTotal()                 -- confirmed against D-06 (D-07 break, pinned below)
//
// Newly discovered beyond D-06's twelve:
//   processor.getSystemCpuLoadBetweenTicks(priorTicks) -- newly discovered: appears ONLY as a
//     commented-out alternative line in the channel group export, never actually invoked by any
//     live script statement. Already covered by this suite's D-07 replacement pinning
//     (movedCallReplacementsReturnSaneValues), so no separate corpus entry is needed for it.
//
// Out of the Enlighten OSHI surface, explicitly excluded:
//   osBean.getSystemLoadAverage() -- invoked on java.lang.management.ManagementFactory.
//     getOperatingSystemMXBean(), not on the oshi object chain at all. Not an OSHI call; excluded
//     with reason rather than silently dropped.
//
// No method name was found on the oshi object chain beyond the D-06 twelve plus the one
// commented-out getSystemCpuLoadBetweenTicks reference above. Only method names appear in this
// comment; no channel structure, message content, or connector setting was transcribed.
public class OshiScriptSurfaceSeamTest {

    private static MirthContextFactory contextFactory;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        ConfigurationController configurationController = mock(ConfigurationController.class);
        // rhino.languageversion = es6 is the shipped mirth.properties default: characterize the
        // same language version BridgeLink runs scripts under in production.
        when(configurationController.getRhinoLanguageVersion()).thenReturn(Context.VERSION_ES6);
        when(configurationController.getServerVersion()).thenReturn("26.9.0-seam-test");
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        // Empty maps: required or sealed-scope construction NPEs during MirthContextFactory init.
        ExtensionController extensionController = mock(ExtensionController.class);
        when(extensionController.getConnectorMetaData()).thenReturn(new HashMap<String, ConnectorMetaData>());
        when(extensionController.getPluginMetaData()).thenReturn(new HashMap<String, PluginMetaData>());
        when(controllerFactory.createExtensionController()).thenReturn(extensionController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);

        // MirthContextFactory's constructor triggers a one-time static initializer that loads
        // mirth.properties as a classpath resource via the thread context classloader.
        // server/build.xml's testclasspath does not include server/conf -- make the real,
        // already-committed server/conf/mirth.properties available for the duration of this
        // one-time class init without touching build.xml.
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        File confDir = new File("conf");
        // The loader is CLOSED in the finally block: restoring the previous context classloader is
        // not enough on its own -- an unclosed URLClassLoader holds a file handle on server/conf
        // for the JVM's lifetime. The one-time class init it exists for has already happened by
        // then.
        URLClassLoader confLoader = null;
        try {
            if (confDir.isDirectory()) {
                URL confUrl = confDir.toURI().toURL();
                confLoader = new URLClassLoader(new URL[] { confUrl }, originalClassLoader);
                Thread.currentThread().setContextClassLoader(confLoader);
            }
            // Real MirthContextFactory -- BridgeLink's own Rhino seam, not raw Context.enter().
            contextFactory = new MirthContextFactory(null, null, false);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            if (confLoader != null) {
                confLoader.close();
            }
        }
    }

    private String evaluate(String script) {
        Context cx = contextFactory.enterContext();
        try {
            Scriptable scope = cx.initStandardObjects();
            Object result = cx.evaluateString(scope, script, "seamtest", 1, null);
            return Context.toString(result);
        } finally {
            Context.exit();
        }
    }

    // CR-02 correction (Phase 23.1): the numeric conversion happens INSIDE the entered Rhino
    // Context, before Context.exit() -- converting a raw Rhino result after exit is unreliable.
    private double evaluateNumber(String script) {
        Context cx = contextFactory.enterContext();
        try {
            Scriptable scope = cx.initStandardObjects();
            Object result = cx.evaluateString(scope, script, "seamtest", 1, null);
            return Context.toNumber(result);
        } finally {
            Context.exit();
        }
    }

    /**
     * The IRT-1801 capability itself: {@code getPhysicalPackageCount()} is the exact call whose
     * 3.9.1 aarch64 implementation logged {@code Couldn't find physical package count. Assuming
     * 1.} at ERROR and guessed a wrong value (26.9-RESEARCH.md Finding 4/5). On 6.12.0 it resolves
     * a real topology count through the reflective {@code Packages.oshi.*} surface, on every
     * architecture this suite runs on.
     */
    @Test
    public void tracerEndToEndPhysicalTopology() {
        String physicalPackageCountScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "p.getPhysicalPackageCount();";

        // PRECONDITION: range predicates only, never absolute values -- physical package/processor
        // counts are machine-dependent (26.9-RESEARCH.md Pattern 3: measured 1 package on an Apple
        // M2 Pro and 1 package inside a linux/arm64 container this research session). This suite
        // runs on x86_64 CI runners and arm64 developer Macs, never the aarch64 leg that actually
        // exercises LinuxCentralProcessor (that proof is the separate container script, plan 03).
        // A count of 0 would mean OSHI silently failed to enumerate topology at all rather than
        // just guessing wrong, which would be a different and worse defect than the one IRT-1801
        // reports.
        double physicalPackageCount = evaluateNumber(physicalPackageCountScript);
        assertTrue("getPhysicalPackageCount() must report at least 1 physical package",
                physicalPackageCount >= 1);

        String physicalProcessorCountScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "p.getPhysicalProcessorCount();";

        double physicalProcessorCount = evaluateNumber(physicalProcessorCountScript);
        assertTrue("getPhysicalProcessorCount() must report at least 1 physical processor",
                physicalProcessorCount >= 1);
    }

    // ========== Predicate vocabulary for range-based OSHI assertions ==========
    //
    // PRECONDITION: never assert a CPU utilization value strictly greater than zero. An idle
    // container measured a load of exactly 0.0 this research session (26.9-RESEARCH.md Pattern
    // 3); asserting > 0 would make this suite intermittently red on a quiet CI runner. The closed
    // interval [0.0, 1.0] is the only safe range, including both endpoints -- see UNIT_INTERVAL.
    //
    // PRECONDITION: never assert a frequency value of exactly 0, and never treat 0 as the
    // "unavailable" sentinel. OSHI signals "unavailable" with exactly -1, not 0 -- measured -1 for
    // both getVendorFreq() and getMaxFreq() on linux/arm64 this research session, while measuring
    // 3504000000 / 2400000000 on an Apple M2 Pro. The safe range is the exact sentinel -1 or a
    // strictly positive value, never 0 -- see PRED_FREQ_SENTINEL_OR_POSITIVE. A template that
    // treats 0 as "unavailable" would render "0 Hz" to the customer where it should render "N/A"
    // (docs/irt-1801-oshi-6x-enlighten-migration.md, sentinel correction).
    //
    // PRECONDITION: never assert a per-slot meaning for the getSystemCpuLoadTicks() array. Its
    // eight slots carry platform-dependent semantics (the USER/NICE/SYSTEM/IDLE/IOWAIT/IRQ/
    // SOFTIRQ/STEAL ordering differs by platform), which is precisely why
    // getSystemCpuLoadBetweenTicks exists. This suite asserts only the array's length (exactly 8)
    // and that every slot is non-negative -- see EXACTLY_EIGHT and BOOLEAN_TRUE below.

    private static final String PRED_AT_LEAST_ONE = "AT_LEAST_ONE";
    private static final String PRED_POSITIVE = "POSITIVE";
    private static final String PRED_NON_NEGATIVE = "NON_NEGATIVE";
    private static final String PRED_EXACTLY_EIGHT = "EXACTLY_EIGHT";
    private static final String PRED_BOOLEAN_TRUE = "BOOLEAN_TRUE";
    private static final String PRED_UNIT_INTERVAL = "UNIT_INTERVAL";

    /**
     * The half-open interval {@code (0.0, 1.0]} a since-boot busy fraction must land in:
     * strictly positive -- never exactly {@code 0.0}, which is exactly what a hardcoded-zero
     * {@code getSystemCpuLoadBetweenTicks} receiver would report -- and at most {@code 1.0}.
     * This is stricter than {@link #PRED_UNIT_INTERVAL} on purpose: it exists to be REJECTED by
     * {@link #assertPredicateRejects(String, String)} for a hardcoded-zero receiver, which
     * {@link #PRED_UNIT_INTERVAL}'s closed interval could not do, since {@code 0.0} is inside a
     * closed unit interval.
     */
    private static final String PRED_POSITIVE_UNIT_INTERVAL = "POSITIVE_UNIT_INTERVAL";

    /**
     * Accepts only the documented "unavailable" frequency sentinel of exactly {@code -1}, or a
     * strictly positive value. Rejects {@code 0} and every other negative value. Replaces the
     * former at-least-negative-one predicate (26.9-VERIFICATION.md Anti-Patterns, lines 261-263,
     * 361, 491, gap 2): that predicate also accepted {@code 0}, which is exactly the sentinel
     * confusion the migration note warns CR-1 against -- a template treating {@code 0} as
     * "unavailable" renders {@code 0 Hz} where it should render {@code N/A}.
     */
    private static final String PRED_FREQ_SENTINEL_OR_POSITIVE = "FREQ_SENTINEL_OR_POSITIVE";

    // ========== OSHI tick memoizer window (IRT-1801 gap 2 closure, plan 26.9-06 Task 1) ==========
    //
    // oshi-core 6.x memoizes CentralProcessor.getSystemCpuLoadTicks() reads: two reads on the
    // same processor instance taken inside the expiration window resolve to the SAME cached
    // array. This is the exact mechanism that made the original tick-delta assertion in
    // movedCallReplacementsReturnSaneValues pass by construction -- its 100ms sleep was well
    // inside this window, so both reads were always the same sample and the delta was 0.0
    // regardless of whether the replacement call worked at all.

    /**
     * The OSHI tick memoizer expiration in milliseconds. Established 2026-08-27 by disassembling
     * the shipped {@code oshi-core-6.12.0.jar}'s {@code oshi.util.Memoizer.queryExpirationConfig()}
     * bytecode (<code>javap -c -p -cp server/lib/oshi-core-6.12.0.jar oshi.util.Memoizer</code>):
     * the method body reads <code>sipush 300</code> as the literal default int argument to
     * <code>oshi.util.GlobalConfig.get("oshi.util.memoizer.expiration", 300)</code>, then converts
     * that to nanoseconds via <code>TimeUnit.MILLISECONDS.toNanos</code>. The property key
     * {@code oshi.util.memoizer.expiration} was confirmed from the same jar's
     * {@code oshi.util.GlobalConfig.OSHI_UTIL_MEMOIZER_EXPIRATION} constant pool entry
     * (<code>javap -v -p -cp server/lib/oshi-core-6.12.0.jar oshi.util.GlobalConfig</code>). This
     * is a measured fact sourced from the jar itself, not an assumption -- plan 26.9-07 consumes
     * it for the Enlighten migration note's sampling-interval guidance (CR-1).
     */
    private static final int MEMOIZER_EXPIRATION_MS = 300;

    /**
     * Comfortably inside {@link #MEMOIZER_EXPIRATION_MS}: roughly a third of it, so two reads
     * separated by this sleep should resolve to the same memoized sample.
     */
    private static final int BELOW_MEMOIZER_SLEEP_MS = 100;

    /**
     * {@link #MEMOIZER_EXPIRATION_MS} plus a 150ms margin, so scheduler jitter cannot push a
     * sample back inside the window.
     */
    private static final int ABOVE_MEMOIZER_SLEEP_MS = 450;

    /**
     * A measured finding, not part of the plan's original design: on this host (macOS), waiting
     * past {@link #ABOVE_MEMOIZER_SLEEP_MS} is NECESSARY but not always SUFFICIENT for
     * {@code getSystemCpuLoadTicks()} to return a fresh sample on the very next read. A single
     * post-sleep read was observed to still return the byte-identical prior array in roughly 1 in
     * 5 to 1 in 10 runs (measured 2026-08-27, standalone Rhino harness, 15-30 trials per sleep
     * duration from 450ms to 1200ms, failures not eliminated even at 1200ms). This means OSHI's
     * documented 300ms Java-side memoizer window is not the only source of staleness on this
     * platform; the underlying OS CPU-tick data itself appears to carry additional, occasional
     * refresh latency beyond OSHI's own cache expiration. Retrying up to
     * {@link #TICK_ADVANCE_MAX_ATTEMPTS} times, each attempt sleeping
     * {@link #ABOVE_MEMOIZER_SLEEP_MS} again before re-checking, does not weaken what this suite
     * proves: a genuinely stale, hardcoded-zero, or otherwise broken replacement fails every
     * attempt and still reddens the assertion, while a working replacement almost always succeeds
     * on the first attempt (measured average 1.12 attempts across 50 trials) and reliably
     * succeeds within {@link #TICK_ADVANCE_MAX_ATTEMPTS}. Flagged for plan 26.9-07: the
     * migration note's sampling-interval guidance should account for this measured platform
     * latency, not just OSHI's documented memoizer window.
     */
    private static final int TICK_ADVANCE_MAX_ATTEMPTS = 3;

    /**
     * Pins the OSHI tick memoizer as a measured, falsifiable fact: two
     * {@code getSystemCpuLoadTicks()} reads on the same {@code CentralProcessor} instance taken
     * within {@link #MEMOIZER_EXPIRATION_MS} resolve to the identical cached array (slot-for-slot
     * equality), and two reads separated by more than that window, retried up to
     * {@link #TICK_ADVANCE_MAX_ATTEMPTS} times per the {@link #TICK_ADVANCE_MAX_ATTEMPTS} javadoc,
     * eventually do not -- the later read's element sum has strictly advanced, because idle ticks
     * always accumulate on a running host. Both behaviors are observed through the same
     * {@code Packages.oshi.*} Rhino path the Enlighten template uses, never a direct Java import.
     * This is what keeps plan 26.9-07's customer-facing sampling-interval guidance honest: a naive
     * per-message tick-delta recipe sampled inside this window reports {@code 0.0} for a reason
     * unrelated to the host being idle.
     */
    @Test
    public void tickMemoizerWindowGovernsSampleFreshness() {
        String withinWindowScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "var first = p.getSystemCpuLoadTicks();"
                + "java.lang.Thread.sleep(" + BELOW_MEMOIZER_SLEEP_MS + ");"
                + "var second = p.getSystemCpuLoadTicks();"
                + "var identical = 1;"
                + "for (var i = 0; i < first.length; i++) { if (second[i] != first[i]) { identical = 0; } }"
                + "identical;";
        assertMatchesPredicate(withinWindowScript, PRED_BOOLEAN_TRUE);

        assertMatchesPredicate(tickAdvanceRetryScript(), PRED_BOOLEAN_TRUE);
    }

    /**
     * Builds a script that reads a baseline tick sum, then retries up to
     * {@link #TICK_ADVANCE_MAX_ATTEMPTS} times (sleeping {@link #ABOVE_MEMOIZER_SLEEP_MS} before
     * each re-check) until the tick sum strictly advances past the baseline, returning {@code 1}
     * once it does or {@code 0} if every attempt is exhausted without an advance. See the
     * {@link #TICK_ADVANCE_MAX_ATTEMPTS} javadoc for why this retries rather than checking once: a
     * hardcoded-zero or otherwise broken replacement fails every attempt and still reddens this
     * assertion, while a working replacement succeeds well within the attempt budget.
     */
    private static String tickAdvanceRetryScript() {
        return "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "var baseline = p.getSystemCpuLoadTicks();"
                + "var baselineSum = 0;"
                + "for (var i = 0; i < baseline.length; i++) { baselineSum += baseline[i]; }"
                + "var advanced = 0;"
                + "var attempt = 0;"
                + "while (attempt < " + TICK_ADVANCE_MAX_ATTEMPTS + " && advanced === 0) {"
                + "  java.lang.Thread.sleep(" + ABOVE_MEMOIZER_SLEEP_MS + ");"
                + "  var cur = p.getSystemCpuLoadTicks();"
                + "  var curSum = 0;"
                + "  for (var j = 0; j < cur.length; j++) { curSum += cur[j]; }"
                + "  if (curSum > baselineSum) { advanced = 1; }"
                + "  attempt = attempt + 1;"
                + "}"
                + "advanced;";
    }

    /**
     * Evaluates {@code script} through {@link #evaluateNumber(String)} and asserts the result
     * against the named predicate, failing with a message that names both the script and the
     * measured value so a red run is diagnosable without a debugger. Every numeric OSHI assertion
     * in this suite routes through here rather than through exact-equality or string comparison,
     * per the class-level range-predicate discipline (RESEARCH.md Pattern 3 / Anti-Patterns).
     */
    /**
     * The predicate switch itself, extracted out of {@link #assertMatchesPredicate(String, String)}
     * so both the positive assertion and {@link #assertPredicateRejects(String, String)} share
     * one source of truth for what each predicate key means. Returns rather than asserts, so a
     * falsifiability claim (a script that must be REJECTED by a predicate) can be executed rather
     * than only ever asserted in prose.
     */
    private boolean matchesPredicate(double value, String predicateKey) {
        switch (predicateKey) {
            case PRED_AT_LEAST_ONE:
                return value >= 1;
            case PRED_POSITIVE:
                return value > 0;
            case PRED_NON_NEGATIVE:
                return value >= 0;
            case PRED_FREQ_SENTINEL_OR_POSITIVE:
                return value == -1 || value > 0;
            case PRED_EXACTLY_EIGHT:
                return value == 8;
            case PRED_BOOLEAN_TRUE:
                return value == 1;
            case PRED_UNIT_INTERVAL:
                return value >= 0.0 && value <= 1.0;
            case PRED_POSITIVE_UNIT_INTERVAL:
                return value > 0.0 && value <= 1.0;
            default:
                fail("Unknown predicate key: " + predicateKey);
                return false; // unreachable: fail() always throws
        }
    }

    private void assertMatchesPredicate(String script, String predicateKey) {
        double value = evaluateNumber(script);
        String message = "Script failed predicate " + predicateKey + " (value=" + value + "): " + script;
        assertTrue(message, matchesPredicate(value, predicateKey));
    }

    /**
     * Asserts that {@code script}, evaluated through {@link #evaluateNumber(String)}, does NOT
     * satisfy {@code predicateKey} -- the negative-control half of a falsifiability claim. This
     * helper exists so a falsifiability claim is executed rather than merely asserted in a
     * comment: a hardcoded-zero or argument-ignoring replacement call must fail this assertion,
     * not just fail to be checked.
     */
    private void assertPredicateRejects(String script, String predicateKey) {
        double value = evaluateNumber(script);
        String message = "Script unexpectedly SATISFIED predicate " + predicateKey + " (value="
                + value + "), but this script is a negative control that must be REJECTED: " + script;
        assertTrue(message, !matchesPredicate(value, predicateKey));
    }

    /**
     * The D-06 calls unchanged between oshi-core 3.9.1 and 6.12.0, each paired with a predicate
     * key (never a literal expected value -- OSHI output is machine-dependent, unlike the
     * deterministic Rhino corpus in {@link RhinoEngineVersionDifferentialTest}).
     */
    private static final String[][] ENLIGHTEN_UNCHANGED_CORPUS = {
            // SystemInfo -> HardwareAbstractionLayer -> CentralProcessor/GlobalMemory all resolve
            // and yield non-null objects.
            { "var si = new Packages.oshi.SystemInfo();"
                    + "var hal = si.getHardware();"
                    + "var p = hal.getProcessor();"
                    + "var m = hal.getMemory();"
                    + "(hal != null && p != null && m != null) ? 1 : 0;", PRED_BOOLEAN_TRUE },
            // processor.getLogicalProcessorCount() is at least 1.
            { "var si = new Packages.oshi.SystemInfo();"
                    + "var hal = si.getHardware();"
                    + "var p = hal.getProcessor();"
                    + "p.getLogicalProcessorCount();", PRED_AT_LEAST_ONE },
            // processor.getSystemCpuLoadTicks() is non-null with length exactly 8.
            { "var si = new Packages.oshi.SystemInfo();"
                    + "var hal = si.getHardware();"
                    + "var p = hal.getProcessor();"
                    + "var ticks = p.getSystemCpuLoadTicks();"
                    + "ticks.length;", PRED_EXACTLY_EIGHT },
            // Every slot in that tick array is at least 0 -- length and non-negativity only, never
            // a per-slot meaning (platform-dependent ordering, see PRECONDITION block above).
            { "var si = new Packages.oshi.SystemInfo();"
                    + "var hal = si.getHardware();"
                    + "var p = hal.getProcessor();"
                    + "var ticks = p.getSystemCpuLoadTicks();"
                    + "var allNonNegative = 1;"
                    + "for (var i = 0; i < ticks.length; i++) { if (ticks[i] < 0) { allNonNegative = 0; } }"
                    + "allNonNegative;", PRED_BOOLEAN_TRUE },
            // memory.getTotal() is greater than 0.
            { "var si = new Packages.oshi.SystemInfo();"
                    + "var hal = si.getHardware();"
                    + "var m = hal.getMemory();"
                    + "m.getTotal();", PRED_POSITIVE },
            // memory.getAvailable() is at least 0.
            { "var si = new Packages.oshi.SystemInfo();"
                    + "var hal = si.getHardware();"
                    + "var m = hal.getMemory();"
                    + "m.getAvailable();", PRED_NON_NEGATIVE },
    };

    /**
     * SC-2/D-06: every unchanged Enlighten call executes green through {@code Packages.oshi.*} on
     * the new jars, asserted by range predicate rather than absolute value.
     */
    @Test
    public void unchangedEnlightenCallSurface() {
        for (String[] entry : ENLIGHTEN_UNCHANGED_CORPUS) {
            assertMatchesPredicate(entry[0], entry[1]);
        }
    }

    /**
     * The 6.x capabilities that did not exist on the Enlighten 3.9.1 surface and are the reason
     * this upgrade exists (26.9-RESEARCH.md "Enlighten Call Migration Map", NEW row):
     * {@code getPhysicalPackageCount}, {@code getPhysicalProcessorCount}, {@code getMaxFreq},
     * {@code getProcessorIdentifier().getName()}, and {@code getProcessorIdentifier().
     * getMicroarchitecture()}. These are what the plan 04 migration map offers CR-1 as newly
     * available.
     */
    @Test
    public void newSixDotXCapabilities() {
        String physicalPackageCountScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "p.getPhysicalPackageCount();";
        assertMatchesPredicate(physicalPackageCountScript, PRED_AT_LEAST_ONE);

        String physicalProcessorCountScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "p.getPhysicalProcessorCount();";
        assertMatchesPredicate(physicalProcessorCountScript, PRED_AT_LEAST_ONE);

        // getMaxFreq() is legitimately -1 on linux/arm64 (unavailable sentinel, never 0).
        String maxFreqScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "p.getMaxFreq();";
        assertMatchesPredicate(maxFreqScript, PRED_FREQ_SENTINEL_OR_POSITIVE);

        // getProcessorIdentifier().getName() is legitimately the single character "-" in a
        // container; only a genuinely resolved non-empty string is asserted, never a specific
        // vendor string. assertResolvedNonEmptyString additionally rejects the literals Rhino's
        // Context.toString renders for a null/undefined result, which a bare non-empty check
        // would miss (26.9-VERIFICATION.md Anti-Patterns, lines 369-371).
        String processorNameScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "p.getProcessorIdentifier().getName();";
        String processorName = evaluate(processorNameScript);
        assertResolvedNonEmptyString("getProcessorIdentifier().getName()", processorName);

        // getProcessorIdentifier().getMicroarchitecture() is legitimately "unknown" on ARM hosts
        // (26.9-VERIFICATION.md Anti-Patterns, lines 378-380).
        String microarchitectureScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "p.getProcessorIdentifier().getMicroarchitecture();";
        String microarchitecture = evaluate(microarchitectureScript);
        assertResolvedNonEmptyString("getProcessorIdentifier().getMicroarchitecture()",
                microarchitecture);
    }

    /**
     * Fails when {@code value} is null, empty after trimming, or equals one of the two literals
     * Rhino's {@link Context#toString(Object)} renders for a null or undefined Java result --
     * both of which are non-null, non-empty strings that a bare {@code != null && !isEmpty()}
     * check would silently accept (26.9-VERIFICATION.md Anti-Patterns, lines 369-371, 378-380).
     * The two literals are derived rather than typed, so this helper cannot drift from what
     * {@code Context.toString} actually produces: the null rendering comes from
     * {@code String.valueOf} applied to a null reference, and the undefined rendering comes from
     * evaluating Rhino's own {@code undefined} token through {@link #evaluate(String)}.
     */
    private void assertResolvedNonEmptyString(String label, String value) {
        String nullRendering = String.valueOf((Object) null);
        String undefinedRendering = evaluate("undefined;");
        assertTrue(label + " must be a non-null, non-empty string that is not Rhino's rendering "
                + "of an unresolved null/undefined result (value=" + value + ")",
                value != null && !value.trim().isEmpty() && !value.equals(nullRendering)
                        && !value.equals(undefinedRendering));
    }

    // ========== D-07: the three 3.9.1-to-6.x breaks, pinned as falsifiable expected failures ==========
    //
    // Framing (no hedging): the three tests below are not evidence that anything regressed. They
    // are the falsifiability half of the plan 04 migration map -- the map claims these calls no
    // longer work and their replacements do, and these tests are what make that claim capable of
    // going red. Without them the map is an assertion; with them it is a pinned contract. If OSHI
    // ever restores getVendorFreq() on CentralProcessor, movedMemberCallsThrowEcmaError() goes red
    // and the map gets corrected instead of quietly rotting.
    //
    // The two exception types are NOT interchangeable (RESEARCH.md Pitfall 3, measured through
    // Rhino this session): getSystemCpuLoad() with no arguments throws EvaluatorException, because
    // a getSystemCpuLoad(long) overload DOES exist so Rhino finds the member and fails on arity.
    // getVendorFreq()/getSwapUsed()/getSwapTotal() throw EcmaError, because no member of that name
    // exists at all so the property lookup itself misses. Assertions below check exception TYPE
    // and the presence of the method name in the message, and nothing else -- never the full
    // EcmaError message text, which embeds the receiver object's machine-dependent toString().

    /**
     * {@code processor.getSystemCpuLoad()} with no arguments: a {@code getSystemCpuLoad(long)}
     * overload exists, so Rhino resolves the member and fails on arity with
     * {@link EvaluatorException}, not {@link EcmaError}.
     */
    @Test
    public void removedNoArgSystemCpuLoadThrowsEvaluatorException() {
        String script = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "p.getSystemCpuLoad();";
        try {
            evaluate(script);
            fail("processor.getSystemCpuLoad() with no arguments must throw EvaluatorException on "
                    + "oshi-core 6.12.0 (D-07): the no-arg overload was removed, but "
                    + "getSystemCpuLoad(long) still exists so Rhino should fail on arity, not on a "
                    + "missing member.");
        } catch (EvaluatorException e) {
            assertTrue("EvaluatorException message must name the arity-mismatched method",
                    e.getMessage() != null && e.getMessage().contains("getSystemCpuLoad"));
        }
    }

    /**
     * The three D-07 calls whose backing member was moved off the 3.9.1 type entirely
     * (no {@code getSystemCpuLoad(long)}-style arity overload exists for any of these): pinned as
     * {@link EcmaError}, method name only.
     */
    private static final String[][] MOVED_CALL_ECMA_ERROR_CORPUS = {
            { "var si = new Packages.oshi.SystemInfo();"
                    + "var hal = si.getHardware();"
                    + "var p = hal.getProcessor();"
                    + "p.getVendorFreq();", "getVendorFreq" },
            { "var si = new Packages.oshi.SystemInfo();"
                    + "var hal = si.getHardware();"
                    + "var m = hal.getMemory();"
                    + "m.getSwapUsed();", "getSwapUsed" },
            { "var si = new Packages.oshi.SystemInfo();"
                    + "var hal = si.getHardware();"
                    + "var m = hal.getMemory();"
                    + "m.getSwapTotal();", "getSwapTotal" },
    };

    @Test
    public void movedMemberCallsThrowEcmaError() {
        for (String[] entry : MOVED_CALL_ECMA_ERROR_CORPUS) {
            String script = entry[0];
            String methodName = entry[1];
            try {
                evaluate(script);
                fail(methodName + "() must throw EcmaError on oshi-core 6.12.0 (D-07): the member "
                        + "was moved off this type entirely, so the property lookup itself should "
                        + "miss.");
            } catch (EcmaError e) {
                // PRECONDITION: never assert on the full EcmaError message text. It embeds the
                // receiver object's toString(), which renders host-specific figures (e.g.
                // "Available: 2.7 GiB/16 GiB" on one host, "Available: 5.4 GiB/7.7 GiB" on
                // another) -- a test green on the developer machine and red in CI, or vice versa.
                // Only the method name's presence is checked.
                assertTrue("EcmaError message must name the missing method " + methodName,
                        e.getMessage() != null && e.getMessage().contains(methodName));
            }
        }
    }

    // ========== CR-01 closure: in-suite negative controls for the tick-delta replacement ==========
    //
    // The two builders below take the receiver expression as a String parameter so the real
    // processor and the stub receivers share one source of truth for the script shape -- there is
    // no second hand-maintained copy of either script (WR-11).

    /**
     * Builds a script computing a since-boot busy fraction through
     * {@code processorExpr.getSystemCpuLoadBetweenTicks} against an all-zero prior tick array.
     * The real processor must return a value in {@link #PRED_POSITIVE_UNIT_INTERVAL}'s
     * {@code (0.0, 1.0]} interval; {@link #HARDCODED_ZERO_PROCESSOR_STUB} must be REJECTED by
     * that same predicate.
     */
    private static String sinceBootBusyFractionScript(String processorExpr) {
        return "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var real = hal.getProcessor();"
                + "var p = " + processorExpr + ";"
                + "var probe = p.getSystemCpuLoadTicks();"
                // The all-zero prior array is built reflectively, never by zeroing an array
                // returned from getSystemCpuLoadTicks(): whether that array is a defensive copy
                // or the memoizer's own instance is not pinned by this suite. The length is taken
                // from the probe rather than hardcoded to 8 because the 6.x implementation rejects
                // a prior array whose length differs from its tick-type count.
                + "var zeroPrior = java.lang.reflect.Array.newInstance(java.lang.Long.TYPE, probe.length);"
                + "p.getSystemCpuLoadBetweenTicks(zeroPrior);";
    }

    /**
     * Builds a script asserting that a just-read, immediately-consumed fresh tick array yields
     * exactly {@code 0.0} through {@code processorExpr.getSystemCpuLoadBetweenTicks}. Retries up
     * to {@link #TICK_ADVANCE_MAX_ATTEMPTS} times (see that constant's javadoc): the read and the
     * call happen back-to-back, so a scheduler stall long enough to expire the memoizer between
     * them is rare but must not make a working replacement flaky. {@link
     * #ARGUMENT_IGNORING_PROCESSOR_STUB} fails every attempt regardless of retry count, because it
     * never returns exactly {@code 0}.
     */
    private static String freshTicksYieldZeroScript(String processorExpr) {
        return "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var real = hal.getProcessor();"
                + "var p = " + processorExpr + ";"
                + "var zeroObserved = 0;"
                + "var attempt = 0;"
                + "while (attempt < " + TICK_ADVANCE_MAX_ATTEMPTS + " && zeroObserved === 0) {"
                + "  var fresh = p.getSystemCpuLoadTicks();"
                + "  var result = p.getSystemCpuLoadBetweenTicks(fresh);"
                + "  if (result === 0) { zeroObserved = 1; }"
                + "  attempt = attempt + 1;"
                + "}"
                + "zeroObserved;";
    }

    /**
     * A JS object literal delegating {@code getSystemCpuLoadTicks} to the real processor (bound as
     * {@code real} by both script builders above) but hardcoding {@code getSystemCpuLoadBetweenTicks}
     * to return {@code 0} regardless of its argument -- the defect {@link
     * #sinceBootBusyFractionScript(String)}'s negative control must catch.
     */
    private static final String HARDCODED_ZERO_PROCESSOR_STUB =
            "{ getSystemCpuLoadTicks: function() { return real.getSystemCpuLoadTicks(); },"
                    + " getSystemCpuLoadBetweenTicks: function(prior) { return 0; } }";

    /**
     * A JS object literal delegating {@code getSystemCpuLoadTicks} to the real processor but
     * returning a fixed positive fraction from {@code getSystemCpuLoadBetweenTicks} regardless of
     * its argument -- the defect {@link #freshTicksYieldZeroScript(String)}'s negative control
     * must catch.
     */
    private static final String ARGUMENT_IGNORING_PROCESSOR_STUB =
            "{ getSystemCpuLoadTicks: function() { return real.getSystemCpuLoadTicks(); },"
                    + " getSystemCpuLoadBetweenTicks: function(prior) { return 0.42; } }";

    /**
     * The 6.12.0 replacement for each of the three D-07 breaks returns a sane value: the
     * non-blocking tick-delta replacement for the removed no-arg CPU load call, and the three
     * moved-member calls at their new location. CR-01 (26.9-VERIFICATION.md re-verification): the
     * tick-delta assertions below now assert on values {@code getSystemCpuLoadBetweenTicks} itself
     * returns, and are proven falsifiable by two in-suite negative controls -- a hardcoded-zero
     * receiver and an argument-ignoring receiver, each rejected by a different predicate, because
     * neither stub reddens both assertions.
     */
    @Test
    public void movedCallReplacementsReturnSaneValues() {
        // Replacement for the removed no-arg getSystemCpuLoad(): obtain prior ticks, wait a plain
        // JS sleep between two non-blocking tick reads (this is NOT a call to the blocking
        // getSystemCpuLoad(long) overload, which sleeps for exactly the duration passed to it and
        // has no 300ms or any other cap -- see the blocking-call trap in
        // docs/irt-1801-oshi-6x-enlighten-migration.md), then measure the delta. The sleep is
        // sized to ABOVE_MEMOIZER_SLEEP_MS, above the OSHI tick memoizer expiration established in
        // Task 1 (MEMOIZER_EXPIRATION_MS): two tick reads inside that window resolve to the same
        // cached sample, which would make the delta 0.0 for a reason unrelated to host load. Value
        // is asserted here (closed interval [0.0, 1.0], an exactly-0.0 idle reading is legitimate
        // and must pass); freshness -- that getSystemCpuLoadTicks() genuinely advanced past a
        // baseline within the attempt budget -- is asserted separately by tickAdvanceScript below.
        // This is the "not sampled this invocation versus a legitimate 0.0 reading" distinction
        // plan 26.9-07 wrote into the migration note.
        String tickDeltaScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "var priorTicks = p.getSystemCpuLoadTicks();"
                + "java.lang.Thread.sleep(" + ABOVE_MEMOIZER_SLEEP_MS + ");"
                + "p.getSystemCpuLoadBetweenTicks(priorTicks);";
        assertMatchesPredicate(tickDeltaScript, PRED_UNIT_INTERVAL);

        // getSystemCpuLoadTicks() advances past a baseline within the attempt budget. This proves
        // only that -- it does NOT call getSystemCpuLoadBetweenTicks() at all, so it proves
        // nothing about that call's own return value; the four assertions below are what pin
        // getSystemCpuLoadBetweenTicks()'s own return value, each with an in-suite negative
        // control. Retries up to TICK_ADVANCE_MAX_ATTEMPTS times (see that constant's javadoc): a
        // single post-window read was measured to occasionally still return a stale sample on
        // this platform, for reasons beyond OSHI's own documented memoizer window, so this loop
        // re-sleeps and re-checks rather than trusting a single read.
        String tickAdvanceScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "var priorTicks = p.getSystemCpuLoadTicks();"
                + "var priorSum = 0;"
                + "for (var i = 0; i < priorTicks.length; i++) { priorSum += priorTicks[i]; }"
                + "var delta = 0;"
                + "var attempt = 0;"
                + "while (attempt < " + TICK_ADVANCE_MAX_ATTEMPTS + " && delta <= 0) {"
                + "  java.lang.Thread.sleep(" + ABOVE_MEMOIZER_SLEEP_MS + ");"
                + "  var laterTicks = p.getSystemCpuLoadTicks();"
                + "  var laterSum = 0;"
                + "  for (var i = 0; i < laterTicks.length; i++) { laterSum += laterTicks[i]; }"
                + "  delta = laterSum - priorSum;"
                + "  attempt = attempt + 1;"
                + "}"
                + "delta;";
        assertMatchesPredicate(tickAdvanceScript, PRED_POSITIVE);

        // CR-01 closure: the tick-delta assertion pinned above (tickDeltaScript) is executed
        // against the real receiver AND proven falsifiable against two stub receivers, each
        // rejected by a different predicate -- neither stub reddens both assertions, so both
        // pairs are required.
        assertMatchesPredicate(sinceBootBusyFractionScript("real"), PRED_POSITIVE_UNIT_INTERVAL);
        assertPredicateRejects(sinceBootBusyFractionScript(HARDCODED_ZERO_PROCESSOR_STUB),
                PRED_POSITIVE_UNIT_INTERVAL);
        assertMatchesPredicate(freshTicksYieldZeroScript("real"), PRED_BOOLEAN_TRUE);
        assertPredicateRejects(freshTicksYieldZeroScript(ARGUMENT_IGNORING_PROCESSOR_STUB),
                PRED_BOOLEAN_TRUE);

        // processor.getProcessorIdentifier().getVendorFreq() -- Hz; -1 is the normal, legitimate
        // "unavailable" value on Graviton, never 0.
        String vendorFreqReplacementScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var p = hal.getProcessor();"
                + "p.getProcessorIdentifier().getVendorFreq();";
        assertMatchesPredicate(vendorFreqReplacementScript, PRED_FREQ_SENTINEL_OR_POSITIVE);

        // memory.getVirtualMemory().getSwapUsed() -- bytes.
        String swapUsedReplacementScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var m = hal.getMemory();"
                + "m.getVirtualMemory().getSwapUsed();";
        assertMatchesPredicate(swapUsedReplacementScript, PRED_NON_NEGATIVE);

        // memory.getVirtualMemory().getSwapTotal() -- bytes.
        String swapTotalReplacementScript = "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var m = hal.getMemory();"
                + "m.getVirtualMemory().getSwapTotal();";
        assertMatchesPredicate(swapTotalReplacementScript, PRED_NON_NEGATIVE);
    }

    // ========== CR-02 closure: execute the migration note's own committed sampling-floor recipe ==========
    //
    // The recipe below is never hand-copied into this suite: it is extracted verbatim from
    // docs/irt-1801-oshi-6x-enlighten-migration.md between two HTML-comment markers and driven
    // through a simulated per-message invocation sequence. A frozen copy of the defective
    // unconditional-store form 26.9-VERIFICATION.md gap 1 found is driven through the identical
    // sequence as the negative control -- this is what proves the recipe test can fail.

    private static final String SAMPLING_FLOOR_MARKER_BEGIN = "<!-- irt1801-sampling-floor-recipe:begin -->";
    private static final String SAMPLING_FLOOR_MARKER_END = "<!-- irt1801-sampling-floor-recipe:end -->";

    /**
     * Base simulated invocation clock, in epoch milliseconds -- a plausible nonzero value rather
     * than 0, so the driver's arithmetic is not accidentally forgiving of an off-by-one against a
     * zero base.
     */
    private static final long RECIPE_SIM_BASE_MS = 1_700_000_000_000L;

    /**
     * Simulated per-invocation clock step, in milliseconds. 150 divides {@link
     * #ABOVE_MEMOIZER_SLEEP_MS} (450) evenly, so one simulated invocation lands exactly on the
     * floor boundary rather than stepping over it; it is also faster than the floor, which is the
     * per-message invocation frequency case the migration note's sampling-floor recipe targets.
     */
    private static final int RECIPE_SIM_STEP_MS = 150;

    /** Number of simulated invocations {@link #samplingFloorDriverScript(String, String)} runs. */
    private static final int RECIPE_SIM_INVOCATIONS = 20;

    /**
     * A frozen literal of the defective unconditional-store form 26.9-VERIFICATION.md gap 1
     * found: both {@code globalMap.put} calls sit outside the guard, so the cached timestamp
     * advances on EVERY invocation rather than only the invocation that took a measurement, and
     * the guard can never fire for a channel invoked faster than the floor. This is a frozen
     * regression fixture and must never be updated to track the note -- doing so would defeat its
     * purpose as the negative control that proves {@link #samplingFloorRecipeGuardFiresOnceTheFloorElapses()}
     * can fail.
     */
    private static final String UNCONDITIONAL_STORE_RECIPE =
            "var now      = harnessClockMs;"
                    + "var lastTime = globalMap.get('oshiTicksTime');"
                    + "var oldTicks = globalMap.get('oshiTicks');"
                    + "var ticks    = processor.getSystemCpuLoadTicks();"
                    + "var load = -1;"
                    + "if (oldTicks && lastTime && (now - lastTime) >= 300) {"
                    + "  load = processor.getSystemCpuLoadBetweenTicks(oldTicks);"
                    + "}"
                    + "globalMap.put('oshiTicks', ticks);"
                    + "globalMap.put('oshiTicksTime', now);";

    // ========== CR-03/gap-1-residual closure: an injectable tick source the driver can drive ==========
    //
    // The simulated clock (harnessClockMs) only ever advances the recipe's FLOOR check -- it
    // never advances the real CentralProcessor's actual ticks, so a recipe with a genuine
    // staleness check driven against the real processor inside this tight, sleep-free loop
    // always sees a stale (identical) read and never reaches the "advanced" branch at all
    // (26.9-VERIFICATION.md gap 1, missing item 3). An injectable stub tick source, whose
    // internal state advances per CALL rather than per elapsed real time, is what lets this
    // driver reach both branches deterministically.

    /**
     * A per-read tick step simulating a source whose ticks genuinely advance on every read --
     * the case {@link #samplingFloorRecipeGuardFiresOnceTheFloorElapses()}'s advancing-source
     * expectations (6 measurements over {@link #RECIPE_SIM_INVOCATIONS} invocations, first at
     * exactly {@link #ABOVE_MEMOIZER_SLEEP_MS}) are pinned against.
     */
    private static final int ADVANCING_TICK_STEP_PER_READ = 40;

    /**
     * A per-read tick step of exactly zero: every read returns a value identical to the last,
     * reproducing deterministically in-suite the platform-staleness condition
     * {@link #TICK_ADVANCE_MAX_ATTEMPTS}'s javadoc measured on the real host (a single post-floor
     * read occasionally still returning the byte-identical prior array), rather than relying on
     * that flaky real-host condition to occur on demand.
     */
    private static final int STALE_TICK_STEP_PER_READ = 0;

    /**
     * Builds a JS object literal simulating a {@code CentralProcessor} tick source: each call to
     * {@code getSystemCpuLoadTicks()} returns a freshly-allocated 8-element {@code long[]}
     * (matching the real return type -- built via {@code java.lang.reflect.Array.newInstance},
     * the same reflective idiom {@link #sinceBootBusyFractionScript(String)} already uses),
     * with every slot advanced by {@code perReadTickStep} since the previous call except the
     * idle slot, which advances by one less so a genuinely advancing source always yields a
     * strictly positive, sub-1.0 utilization fraction. {@code getSystemCpuLoadBetweenTicks(prior)}
     * implements the real 6.x arithmetic confirmed by javap in 26.9-REVIEW.md CR-03
     * ({@code total > 0 ? (total - idleDelta) / total : 0}) against its own last-returned tick
     * array, rather than a convenient constant -- so {@link #STALE_TICK_STEP_PER_READ} reproduces
     * the fabricated {@code 0.0} through the same arithmetic path the real jar takes, not a
     * simulated shortcut. Each call returns a real Java {@code long[]}, so element access from
     * script reaches the recipe's staleness comparison the same way the real processor's does
     * (the {@code java.lang.Long}-wrapping hazard the doc's snippet comment names).
     */
    private static String simulatedTickProcessorStub(int perReadTickStep) {
        int idleStep = perReadTickStep > 0 ? perReadTickStep - 1 : 0;
        return "(function() {"
                + "  var SLOTS = 8;"
                + "  var IDLE_SLOT = 3;"
                + "  var cur = java.lang.reflect.Array.newInstance(java.lang.Long.TYPE, SLOTS);"
                + "  return {"
                + "    getSystemCpuLoadTicks: function() {"
                + "      var next = java.lang.reflect.Array.newInstance(java.lang.Long.TYPE, SLOTS);"
                + "      for (var i = 0; i < SLOTS; i++) {"
                + "        var step = (i === IDLE_SLOT) ? " + idleStep + " : " + perReadTickStep + ";"
                + "        next[i] = cur[i] + step;"
                + "      }"
                + "      cur = next;"
                + "      return cur;"
                + "    },"
                + "    getSystemCpuLoadBetweenTicks: function(prior) {"
                + "      var total = 0;"
                + "      var idleDelta = 0;"
                + "      for (var i = 0; i < SLOTS; i++) {"
                + "        var delta = cur[i] - prior[i];"
                + "        total += delta;"
                + "        if (i === IDLE_SLOT) { idleDelta = delta; }"
                + "      }"
                + "      return total > 0 ? (total - idleDelta) / total : 0;"
                + "    }"
                + "  };"
                + "})()";
    }

    /**
     * A frozen literal of the doc snippet as it stood BEFORE this plan's Task 1 (the shipped
     * form 26.9-VERIFICATION.md gap 1 residual and 26.9-REVIEW.md CR-03 found: guard placement
     * correct, but no staleness check), taken verbatim from
     * {@code git show HEAD~1:docs/irt-1801-oshi-6x-enlighten-migration.md} with only its single
     * live clock read substituted for {@code harnessClockMs}, exactly as
     * {@link #samplingFloorRecipeFromDoc()} substitutes it for the real doc snippet. This is a
     * frozen regression fixture and must never be updated to track the note -- doing so would
     * defeat its purpose as the negative control proving the staleness assertion in
     * {@link #samplingFloorRecipeHoldsTheSentinelOnAStalePostFloorRead()} can fail: without a
     * staleness check, this form trusts the elapsed floor alone and fabricates a reading from
     * whatever {@code getSystemCpuLoadBetweenTicks} returns, including the exact {@code 0.0}
     * CR-03 found on a stale read.
     */
    private static final String STALENESS_BLIND_RECIPE =
            "var now      = harnessClockMs;\n"
                    + "var oldTicks = globalMap.get('oshiTicks');\n"
                    + "var lastTime = globalMap.get('oshiTicksTime');\n"
                    + "// SAMPLE_FLOOR_MS is the 300ms OSHI tick memoizer window\n"
                    + "// (OshiScriptSurfaceSeamTest#MEMOIZER_EXPIRATION_MS) plus a 150ms margin for the measured\n"
                    + "// platform refresh latency described below: sizing the floor at exactly the memoizer window\n"
                    + "// was measured to be necessary but not always sufficient for a single post-window read to\n"
                    + "// return a fresh sample.\n"
                    + "var SAMPLE_FLOOR_MS = 450;\n"
                    + "var load = -1;\n"
                    + "if (!oldTicks || !lastTime) {\n"
                    + "    // First invocation, or no usable prior sample yet: prime the cache and take no reading\n"
                    + "    // this invocation.\n"
                    + "    globalMap.put('oshiTicks', processor.getSystemCpuLoadTicks());\n"
                    + "    globalMap.put('oshiTicksTime', now);\n"
                    + "} else if ((now - lastTime) >= SAMPLE_FLOOR_MS) {\n"
                    + "    // The floor has elapsed since the last sample actually USED: compute the reading against\n"
                    + "    // the cached prior ticks, THEN roll the cache forward. Rolling forward is what ends this\n"
                    + "    // measurement window and starts the next one, and it happens only when a measurement was\n"
                    + "    // taken.\n"
                    + "    load = processor.getSystemCpuLoadBetweenTicks(oldTicks);\n"
                    + "    globalMap.put('oshiTicks', processor.getSystemCpuLoadTicks());\n"
                    + "    globalMap.put('oshiTicksTime', now);\n"
                    + "}\n"
                    + "// else: a too-soon invocation. The cache is left alone and load stays at the not-sampled\n"
                    + "// sentinel.\n";

    /**
     * Resolves the migration note, extracts the exact text between {@link
     * #SAMPLING_FLOOR_MARKER_BEGIN} and {@link #SAMPLING_FLOOR_MARKER_END}, and substitutes its
     * one live clock read for the {@code harnessClockMs} variable {@link
     * #samplingFloorDriverScript(String, String)} binds per invocation. Fails loudly (never skips)
     * when the note cannot be resolved or the marker/fence shape does not match: this test exists
     * to execute the note's own committed text, not a hand-copied approximation of it. Tried in
     * order because the ant server target runs with the module directory as the working
     * directory, but a direct IDE run may not.
     */
    private static String samplingFloorRecipeFromDoc() {
        String[] candidatePaths = {
                "../docs/irt-1801-oshi-6x-enlighten-migration.md",
                "docs/irt-1801-oshi-6x-enlighten-migration.md"
        };
        File docFile = null;
        for (String candidate : candidatePaths) {
            File f = new File(candidate);
            if (f.isFile()) {
                docFile = f;
                break;
            }
        }
        if (docFile == null) {
            fail("Could not resolve the migration note at either " + candidatePaths[0] + " or "
                    + candidatePaths[1] + " -- check the working directory this test ran from.");
            return null; // unreachable: fail() always throws
        }

        String docText;
        try {
            docText = new String(Files.readAllBytes(docFile.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("Could not read the migration note at " + docFile + ": " + e);
            return null; // unreachable: fail() always throws
        }

        int beginIdx = docText.indexOf(SAMPLING_FLOOR_MARKER_BEGIN);
        int endIdx = docText.indexOf(SAMPLING_FLOOR_MARKER_END);
        if (beginIdx < 0 || endIdx < 0 || endIdx <= beginIdx) {
            fail("Could not locate the sampling-floor recipe marker pair in " + docFile
                    + " -- the markers may have been removed or reordered.");
        }

        String between = docText.substring(beginIdx + SAMPLING_FLOOR_MARKER_BEGIN.length(), endIdx);
        int fenceOpen = between.indexOf("```");
        int fenceClose = fenceOpen < 0 ? -1 : between.indexOf("```", fenceOpen + 3);
        if (fenceOpen < 0 || fenceClose < 0) {
            fail("Expected exactly one fenced code block between the sampling-floor recipe "
                    + "markers in " + docFile + ", found none.");
        }
        int bodyStart = between.indexOf('\n', fenceOpen) + 1;
        String body = between.substring(bodyStart, fenceClose);

        String clockCall = "java.lang.System.currentTimeMillis()";
        int clockOccurrences = 0;
        int searchIdx = 0;
        while ((searchIdx = body.indexOf(clockCall, searchIdx)) != -1) {
            clockOccurrences++;
            searchIdx += clockCall.length();
        }
        if (clockOccurrences != 1) {
            fail("Expected the sampling-floor recipe to read the clock exactly once, found "
                    + clockOccurrences + " in " + docFile);
        }

        String substituted = body.replace(clockCall, "harnessClockMs");
        assertTrue("Substituting the harness clock for the live clock read must change the "
                + "recipe text -- a no-op substitution would leave the driver on the real clock "
                + "and make the negative control nondeterministic.", !substituted.equals(body));
        return substituted;
    }

    /**
     * Builds a script simulating {@code invocationCount} per-message channel invocations of
     * {@code perInvocationBody} at a fixed {@code stepMs} interval starting from {@link
     * #RECIPE_SIM_BASE_MS}, binding a {@link HashMap} as {@code globalMap} (shared across
     * invocations, exactly as a channel's globalMap persists between messages), the real
     * processor as {@code real}, and {@code processorExpr} (evaluated once, up front) as {@code
     * processor} -- the receiver the recipe body actually calls. Passing {@code "real"} binds the
     * genuine {@code CentralProcessor}; passing {@link #simulatedTickProcessorStub(int)}'s output
     * binds an injectable stub whose ticks advance per call rather than per elapsed real time,
     * which is what lets this driver reach a recipe's staleness branch under a simulated clock
     * that never advances the real receiver's own ticks (26.9-VERIFICATION.md gap 1, missing item
     * 3). Each invocation runs inside its own immediately-invoked function -- its own script
     * scope, exactly as a real channel invocation gets -- while {@code globalMap} persists across
     * them. Counts invocations whose {@code load} differs from the {@code -1} not-sampled
     * sentinel -- keyed on the sentinel and never on a positive value, because with a simulated
     * clock every real measurement legitimately reads {@code 0.0}, the same two-zeros hazard the
     * migration note documents -- and records the elapsed simulated time and the {@code load}
     * value of the first such invocation. {@code firstMeasurementLoad} starts at {@code -2}, a
     * value that is neither the {@code -1} sentinel nor {@code 0}, so an assertion of exactly
     * {@code 0.0} against it can never pass by initialisation -- only by a genuine first
     * measurement actually recording it.
     */
    private static String samplingFloorDriverScript(String processorExpr, String perInvocationBody,
            String returnExpr, int invocationCount, int stepMs) {
        return "var globalMap = new java.util.HashMap();"
                + "var si = new Packages.oshi.SystemInfo();"
                + "var hal = si.getHardware();"
                + "var real = hal.getProcessor();"
                + "var processor = " + processorExpr + ";"
                + "var measurementCount = 0;"
                + "var firstMeasurementElapsedMs = -1;"
                + "var firstMeasurementLoad = -2;"
                + "for (var invocation = 0; invocation < " + invocationCount + "; invocation++) {"
                + "  var harnessClockMs = " + RECIPE_SIM_BASE_MS + " + (invocation * " + stepMs + ");"
                + "  (function() {"
                + perInvocationBody
                + "    if (load !== -1) {"
                + "      measurementCount = measurementCount + 1;"
                + "      if (firstMeasurementElapsedMs === -1) {"
                + "        firstMeasurementElapsedMs = harnessClockMs - " + RECIPE_SIM_BASE_MS + ";"
                + "        firstMeasurementLoad = load;"
                + "      }"
                + "    }"
                + "  })();"
                + "}"
                + returnExpr + ";";
    }

    /**
     * CR-02's recipe-verification half (26.9-VERIFICATION.md gap 1): the migration note's
     * sampling-floor snippet is extracted VERBATIM from the doc and driven through {@link
     * #RECIPE_SIM_INVOCATIONS} simulated per-message invocations at {@link #RECIPE_SIM_STEP_MS}ms
     * intervals -- faster than the {@link #ABOVE_MEMOIZER_SLEEP_MS}ms floor, the exact case the
     * note is written for. Driven against {@link #simulatedTickProcessorStub(int)}'s advancing
     * source (not the real processor): under this test's sleep-free simulated clock the real
     * ticks never genuinely advance between invocations, so a recipe with a real staleness check
     * needs an injectable source whose ticks advance per call in order to exercise its "advanced"
     * branch at all (26.9-VERIFICATION.md gap 1, missing item 3). The frozen {@link
     * #UNCONDITIONAL_STORE_RECIPE} negative control, driven through the identical sequence, is
     * what proves this test can fail: without it, a driver bug that always reports "no
     * measurements" for any recipe would pass this test vacuously.
     * <p>
     * Exact equality (never a range predicate) is a deliberate, justified exception to this
     * class's range-predicate discipline: the clock driving this test is simulated and the stub
     * arithmetic is deterministic, so these three values are arithmetic rather than
     * machine-dependent, and the third assertion is what proves the first two can fail.
     */
    @Test
    public void samplingFloorRecipeGuardFiresOnceTheFloorElapses() {
        String recipeBody = samplingFloorRecipeFromDoc();
        String advancingStub = simulatedTickProcessorStub(ADVANCING_TICK_STEP_PER_READ);

        long expectedMeasurementCount = ((long) (RECIPE_SIM_INVOCATIONS - 1) * RECIPE_SIM_STEP_MS)
                / ABOVE_MEMOIZER_SLEEP_MS;

        double recipeMeasurementCount = evaluateNumber(
                samplingFloorDriverScript(advancingStub, recipeBody, "measurementCount",
                        RECIPE_SIM_INVOCATIONS, RECIPE_SIM_STEP_MS));
        assertEquals("The doc snippet's measurement count over " + RECIPE_SIM_INVOCATIONS
                + " simulated invocations at a " + RECIPE_SIM_STEP_MS + "ms step must equal "
                + expectedMeasurementCount, (double) expectedMeasurementCount,
                recipeMeasurementCount, 0.0);

        double recipeFirstMeasurementElapsedMs = evaluateNumber(
                samplingFloorDriverScript(advancingStub, recipeBody, "firstMeasurementElapsedMs",
                        RECIPE_SIM_INVOCATIONS, RECIPE_SIM_STEP_MS));
        assertEquals("The doc snippet's first measurement must land at exactly the "
                + ABOVE_MEMOIZER_SLEEP_MS + "ms floor", (double) ABOVE_MEMOIZER_SLEEP_MS,
                recipeFirstMeasurementElapsedMs, 0.0);

        double defectiveMeasurementCount = evaluateNumber(
                samplingFloorDriverScript(advancingStub, UNCONDITIONAL_STORE_RECIPE,
                        "measurementCount", RECIPE_SIM_INVOCATIONS, RECIPE_SIM_STEP_MS));
        assertEquals("The frozen unconditional-store form must produce zero measurements over "
                + "the identical simulated sequence -- this is what proves the recipe test can "
                + "fail.", 0.0, defectiveMeasurementCount, 0.0);
    }

    /**
     * CR-03/26.9-VERIFICATION.md gap-1-residual closure: the migration note's sampling-floor
     * recipe (Task 1 of this plan) must never fabricate a reading on a stale post-floor read, and
     * this assertion must be proven capable of failing rather than passing by construction.
     * <p>
     * Four sub-assertions, in order:
     * <ol>
     * <li>The doc snippet driven against {@link #STALE_TICK_STEP_PER_READ}'s stale stub source
     * yields exactly zero measurements over the identical {@link #RECIPE_SIM_INVOCATIONS}-
     * invocation sequence {@link #samplingFloorRecipeGuardFiresOnceTheFloorElapses()} uses for
     * the advancing case: every post-floor read is byte-identical to the cached prior array, so
     * the recipe must leave {@code load} at the {@code -1} sentinel rather than compute a
     * reading.</li>
     * <li>The frozen {@link #STALENESS_BLIND_RECIPE} -- the exact form Task 1 replaced -- driven
     * against the identical stale stub source yields the same 6 measurements the advancing run
     * produces, with the first recorded {@code load} exactly {@code 0.0}. This is what proves the
     * first assertion can fail: {@code STALENESS_BLIND_RECIPE} trusts the elapsed floor alone and
     * stores whatever {@code getSystemCpuLoadBetweenTicks} returns for a zero tick delta, which is
     * exactly the fabricated zero CR-03 found shipped inside the note's own recipe.</li>
     * <li>The real-receiver pair: a customer channel runs this snippet against a real {@code
     * CentralProcessor}, and Rhino hands the script {@code java.lang.Long}-wrapped elements
     * rather than JS numbers for a {@code long[]}, so a comparison proven only against the JS
     * stub above has not actually been measured against the wrapped-array case the doc's own
     * comment names. Driving the doc snippet against the real processor at an invocation count of
     * 2 and a step of {@link #ABOVE_MEMOIZER_SLEEP_MS} lands invocation 1 past the floor on the
     * simulated clock while the two invocations execute microseconds apart in real time --
     * comfortably inside the real OSHI tick memoizer window {@link
     * #tickMemoizerWindowGovernsSampleFreshness()} pins -- so the real read is expected to be
     * stale. Retried up to {@link #TICK_ADVANCE_MAX_ATTEMPTS} times (see that constant's
     * javadoc), a working comparison must record zero measurements on at least one attempt.</li>
     * <li>The frozen {@link #STALENESS_BLIND_RECIPE} driven through the identical real-receiver
     * path records a measurement on EVERY attempt -- the negative control proving the real-
     * receiver assertion above is discriminating a real staleness condition, not passing for an
     * unrelated reason (a broken driver, an always-stale real processor, or similar).</li>
     * </ol>
     */
    @Test
    public void samplingFloorRecipeHoldsTheSentinelOnAStalePostFloorRead() {
        String recipeBody = samplingFloorRecipeFromDoc();
        String staleStub = simulatedTickProcessorStub(STALE_TICK_STEP_PER_READ);

        // (1) The doc snippet's staleness check must hold the sentinel over a stale stub source.
        double docStaleMeasurementCount = evaluateNumber(
                samplingFloorDriverScript(staleStub, recipeBody, "measurementCount",
                        RECIPE_SIM_INVOCATIONS, RECIPE_SIM_STEP_MS));
        assertEquals("The doc snippet's staleness check must produce zero measurements over a "
                + "stale-source sequence: every post-floor read matches the cached prior array, "
                + "so the recipe must leave load at the -1 sentinel rather than fabricate a "
                + "reading.", 0.0, docStaleMeasurementCount, 0.0);

        // (2) The frozen staleness-blind form must be proven able to fabricate the fabricated
        // zero over the identical stale sequence -- this is what proves (1) can fail.
        double blindStaleMeasurementCount = evaluateNumber(
                samplingFloorDriverScript(staleStub, STALENESS_BLIND_RECIPE, "measurementCount",
                        RECIPE_SIM_INVOCATIONS, RECIPE_SIM_STEP_MS));
        long expectedMeasurementCount = ((long) (RECIPE_SIM_INVOCATIONS - 1) * RECIPE_SIM_STEP_MS)
                / ABOVE_MEMOIZER_SLEEP_MS;
        assertEquals("The frozen staleness-blind form must produce the same " + expectedMeasurementCount
                + " measurements the advancing run produces over a stale source -- it never "
                + "checks whether the sample actually advanced.", (double) expectedMeasurementCount,
                blindStaleMeasurementCount, 0.0);

        double blindStaleFirstLoad = evaluateNumber(
                samplingFloorDriverScript(staleStub, STALENESS_BLIND_RECIPE, "firstMeasurementLoad",
                        RECIPE_SIM_INVOCATIONS, RECIPE_SIM_STEP_MS));
        assertEquals("The frozen staleness-blind form's first recorded load against a stale "
                + "source must be exactly 0.0 -- getSystemCpuLoadBetweenTicks returns exactly "
                + "0.0 on a zero tick delta, and the staleness-blind recipe stores it as though "
                + "it were a real reading. This reproduces CR-03's fabricated zero inside the "
                + "suite.", 0.0, blindStaleFirstLoad, 0.0);

        // (3) The real-receiver assertion: the doc snippet's comparison must also discriminate
        // staleness on the real, wrapped-long[] receiver, not only the JS stub above. Retried up
        // to TICK_ADVANCE_MAX_ATTEMPTS times per that constant's javadoc.
        boolean sawZeroMeasurementsOnRealReceiver = false;
        for (int attempt = 0; attempt < TICK_ADVANCE_MAX_ATTEMPTS && !sawZeroMeasurementsOnRealReceiver;
                attempt++) {
            double realCount = evaluateNumber(samplingFloorDriverScript("real", recipeBody,
                    "measurementCount", 2, ABOVE_MEMOIZER_SLEEP_MS));
            if (realCount == 0.0) {
                sawZeroMeasurementsOnRealReceiver = true;
            }
        }
        assertTrue("The doc snippet's staleness check must record zero measurements against the "
                + "real processor within " + TICK_ADVANCE_MAX_ATTEMPTS + " attempts: two "
                + "invocations spaced past the floor on the simulated clock but executed "
                + "microseconds apart in real time, inside the real OSHI tick memoizer window "
                + "(see TICK_ADVANCE_MAX_ATTEMPTS's javadoc).", sawZeroMeasurementsOnRealReceiver);

        // (4) The negative control on the real-receiver path: proves (3) discriminates a real
        // staleness condition rather than passing for an unrelated reason.
        for (int attempt = 0; attempt < TICK_ADVANCE_MAX_ATTEMPTS; attempt++) {
            double blindRealCount = evaluateNumber(samplingFloorDriverScript("real",
                    STALENESS_BLIND_RECIPE, "measurementCount", 2, ABOVE_MEMOIZER_SLEEP_MS));
            assertEquals("The frozen staleness-blind form must record a measurement on every "
                    + "attempt through the identical real-receiver path -- proving the "
                    + "real-receiver assertion above is discriminating a real staleness "
                    + "condition, not passing for an unrelated reason (attempt " + attempt + ")",
                    1.0, blindRealCount, 0.0);
        }
    }
}
