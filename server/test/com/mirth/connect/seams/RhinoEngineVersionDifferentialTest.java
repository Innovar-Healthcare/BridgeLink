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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Two-jar behavioral-compatibility differential harness (23.1-05, CVE-13). Compares the
 * deliberately vulnerable {@code rhino-1.7.13.jar} (the exact engine Phase 23 replaced, kept as
 * {@code server/test/fixtures/rhino-1.7.13.jar}) against the vendored, shipped
 * {@code server/lib/rhino-1.7.15.1.jar}, each loaded through its own isolated
 * {@code URLClassLoader} parented at {@code ClassLoader.getPlatformClassLoader()} rather than the
 * application classloader, so neither engine can silently resolve to the other.
 * <p>
 * Framing (no hedging): byte-identical output across the two engines evidences behavioral
 * compatibility, meaning the Phase 23 bump did not change what user JavaScript does. This class is
 * not evidence for protection against CVE-2025-66453, because that fix changed a resource bound,
 * not a formatted value, as 23.1-REVIEW.md CR-01 measured with a 340-case toFixed differential
 * fuzz that found zero output differences between the two jars. CVE-13's CVE-2025-66453
 * protection is carried by version attestation: the Phase 23 re-land of rhino-1.7.15.1 across
 * client/command/manager/server, plus the {@link RhinoSeamTest} link-proof.
 * <p>
 * Production remains single-engine: {@code server/lib/rhino-1.7.15.1.jar} is the only Rhino on
 * any shipped or ant classpath. Engine version is parameterized only inside this one test class's
 * isolated loaders, via {@link EngineUnderTest}.
 * <p>
 * Both {@code URLClassLoader}s are opened in {@link #beforeClass()} and closed in
 * {@link #afterClass()} -- not immediately after construction -- because every {@code @Test} in
 * this class needs its loader alive for the class's lifetime (23.1-REVIEW.md WR-04).
 * <p>
 * Exclusion list (D-06 scope, deliberately not compared, not quietly dropped): the injected
 * {@code java.util.List} form of the JSON seam ({@code RhinoEs6BehaviorTest.
 * jsonStringifyUnwrapsNativeJavaObject}/{@code jsonRoundTripEmptyAndSingleElementNativeJavaObject})
 * is exercised here only in plain-JS array form ({@link #CHARACTERIZATION_CORPUS}), not by
 * injecting a live {@code java.util.List}, because doing so would require the Mirth
 * {@code ControllerFactory}/Guice dependency graph inside this class's isolated loaders. The
 * {@code NativeJavaObject} unwrap branch itself remains covered by {@code RhinoEs6BehaviorTest} on
 * the shipped engine. No corpus entry from that suite was found unable to run on the vulnerable
 * engine; nothing else is excluded.
 * <p>
 * Everything here is Java-17-loadable; the test tree compiles under {@code release=17}.
 */
public class RhinoEngineVersionDifferentialTest {

    /**
     * Verbatim transcriptions of {@code RhinoEs6BehaviorTest}'s characterization scripts, plus the
     * two Scriptable-returning shapes this class's in-context conversion (CR-02 shape) uniquely
     * makes possible. Each entry is {@code { script, expectedOutputOnBothEngines }}. The JSON
     * round-trip pair is a documented plain-JS restatement, not a verbatim transcription -- see the
     * class javadoc exclusion list for why.
     */
    private static final String[][] CHARACTERIZATION_CORPUS = {
            // Five UTC-anchored Date scripts (RESEARCH.md Pitfall 4: no local-time formatting).
            { "new Date(0).getTime();", "0" },
            { "new Date(0).toISOString();", "1970-01-01T00:00:00.000Z" },
            { "Date.parse('1970-01-01T00:00:00.000Z');", "0" },
            { "new Date(1).getTime();", "1" },
            { "new Date(-1).getTime();", "-1" },
            // Three coercion scripts.
            { "'' + 0.1 + 0.2;", "0.10.2" },
            { "(1 / 3).toString();", "0.3333333333333333" },
            { "parseInt('42px', 10);", "42" },
            // Regex global replace.
            { "'a1b2c3'.replace(/\\d/g, '#');", "a#b#c#" },
            // Three toFixed scripts.
            { "(1.005).toFixed(2);", "1.00" },
            { "(0).toFixed(20);", "0.00000000000000000000" },
            { "(0).toFixed(100);", "0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" },
            // JSON round-trip, plain-JS array form (injection-free -- see class javadoc exclusion list).
            { "var s = JSON.stringify([]); var p = JSON.parse(s); s + '|' + p.length + '|' + Array.isArray(p);", "[]|0|true" },
            { "var s = JSON.stringify([1]); var p = JSON.parse(s); s + '|' + p.length + '|' + p[0];", "[1]|1|1" },
            // E4X element-delete reserialization (RhinoEs6BehaviorTest.e4xElementDeleteReserializesFlat).
            { "XML.ignoreWhitespace=true;\n"
                    + "XML.prettyPrinting=false;\n"
                    + "var msg = new XML('<HL7Message>\\n  <PID><PID.1>1</PID.1></PID>\\n  <PV1><PV1.1>x</PV1.1></PV1>\\n</HL7Message>');\n"
                    + "delete msg.PV1[0];\n"
                    + "msg.toString();",
                    "<HL7Message><PID><PID.1>1</PID.1></PID></HL7Message>" },
            // Two Scriptable-returning shapes -- only possible because EngineUnderTest.evaluate
            // converts inside the context (the CR-02 shape carried into this class).
            { "[1,2,3];", "1,2,3" },
            { "({a:1});", "[object Object]" },
    };

    /** Bounded toFixed sweep grid, reproducing 23.1-REVIEW.md CR-01's 340-case differential fuzz. */
    private static final String[] TOFIXED_SWEEP_DOUBLES = { "1.005", "2.675", "0.5", "0", "1e20", "1e21",
            "Number.MIN_VALUE", "Number.MAX_VALUE", "5e-324" };
    private static final int[] TOFIXED_SWEEP_PRECISIONS = { 0, 1, 2, 3, 5, 10, 17, 20, 50, 100 };

    /** Shadowed Rhino classes the corpus above touches (server/src overrides these six of thirteen). */
    private static final String[] SHADOWED_CLASSES_TOUCHED_BY_CORPUS = {
            "org.mozilla.javascript.NativeDate", "org.mozilla.javascript.NativeJSON" };

    private static EngineUnderTest vulnerableEngine;
    private static EngineUnderTest patchedEngine;

    @BeforeClass
    public static void beforeClass() throws Exception {
        File serverClassesDir = new File("classes");
        File patchedJar = new File("lib/rhino-1.7.15.1.jar");
        File vulnerableJar = new File("test/fixtures/rhino-1.7.13.jar");

        requireOnDisk(serverClassesDir, "server/classes (build output of a prior main-source build)");
        requireOnDisk(patchedJar, "server/lib/rhino-1.7.15.1.jar (the vendored, shipped engine)");
        requireOnDisk(vulnerableJar, "server/test/fixtures/rhino-1.7.13.jar (the vulnerable test fixture)");

        vulnerableEngine = new EngineUnderTest("vulnerable-1.7.13", serverClassesDir, vulnerableJar);
        patchedEngine = new EngineUnderTest("patched-1.7.15.1", serverClassesDir, patchedJar);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (vulnerableEngine != null) {
            vulnerableEngine.classLoader.close();
        }
        if (patchedEngine != null) {
            patchedEngine.classLoader.close();
        }
    }

    /**
     * Fails fast and self-describingly (23.1-REVIEW.md IN-02) rather than implying a graceful
     * degraded path that does not exist. This test requires the junit working directory set to
     * {@code server/} (ant's {@code junit dir="${basedir}"}) and a prior main-source build.
     */
    private static void requireOnDisk(File path, String description) {
        if (!path.exists()) {
            throw new IllegalStateException("Expected " + description + " at " + path.getAbsolutePath()
                    + " -- run this test with the working directory set to server/ "
                    + "(ant junit dir=\"${basedir}\"), and ensure server/classes has been built "
                    + "(cd server && ant -f mirth-build.xml -DdisableSigning=true -Dskip.build.tests=true).");
        }
    }

    // ========== Anti-tautology guard: the two loaders must genuinely be different engines ==========

    /**
     * Without this method, an equality-only harness could pass while comparing one engine with
     * itself -- for example if a parent-delegating loader let the application classloader's own
     * rhino-1.7.15.1 answer both class requests, or if the fixture path were accidentally
     * repointed at the vendored jar. This method reddens first in either case, so the equality
     * assertions elsewhere in this class are never misread as evidence they do not have.
     */
    @Test
    public void bothLoadersReportDistinctEngineImplementationVersions() {
        String vulnerableVersion = vulnerableEngine.implementationVersion;
        String patchedVersion = patchedEngine.implementationVersion;

        assertNotEquals("The two isolated loaders must report distinct Rhino implementation "
                + "versions -- identical strings would mean both loaders resolved to the same "
                + "engine, making every equality assertion in this class meaningless",
                vulnerableVersion, patchedVersion);
        assertTrue("Vulnerable-engine implementation version must name 1.7.13, got: " + vulnerableVersion,
                vulnerableVersion.contains("1.7.13"));
        assertTrue("Patched-engine implementation version must name 1.7.15, got: " + patchedVersion,
                patchedVersion.contains("1.7.15"));
    }

    // ========== Tracer: one script, end to end, through two genuinely isolated engines ==========

    @Test
    public void tracerToFixedTieBreakingIsIdenticalOnBothEngines() {
        String script = "(1.005).toFixed(2);";

        String vulnerableResult = vulnerableEngine.evaluate(script);
        String patchedResult = patchedEngine.evaluate(script);

        assertEquals("toFixed tie-breaking output must be identical on both engines for: " + script,
                vulnerableResult, patchedResult);
        assertEquals("1.00", patchedResult);
    }

    // ========== Full characterization corpus + bounded toFixed sweep ==========

    /**
     * Iterates {@link #CHARACTERIZATION_CORPUS}, asserting per-script equality with the script
     * text embedded in the failure message. Also runs a bounded toFixed sweep reproducing
     * 23.1-REVIEW.md CR-01's grid, asserting the executed cell count equals the product of the two
     * dimensions so a silently truncated sweep reddens rather than passing trivially. The 60000 ms
     * timeout is a hang detector (a forward regression tripwire, matching 23.1-04's precedent for
     * the sibling toFixed test), not a performance assertion and not a version discriminator.
     */
    @Test(timeout = 60000)
    public void characterizationCorpusIsIdenticalOnBothEngines() {
        for (String[] entry : CHARACTERIZATION_CORPUS) {
            String script = entry[0];
            String expected = entry[1];

            String vulnerableResult = vulnerableEngine.evaluate(script);
            String patchedResult = patchedEngine.evaluate(script);

            assertEquals("Corpus divergence between engines for script: " + script,
                    vulnerableResult, patchedResult);
            assertEquals("Corpus result does not match the spike-captured expectation for script: " + script,
                    expected, patchedResult);
        }

        int executed = 0;
        for (String d : TOFIXED_SWEEP_DOUBLES) {
            for (int p : TOFIXED_SWEEP_PRECISIONS) {
                String script = "(" + d + ").toFixed(" + p + ");";

                String vulnerableResult = vulnerableEngine.evaluate(script);
                String patchedResult = patchedEngine.evaluate(script);

                assertEquals("toFixed sweep divergence between engines for: " + script,
                        vulnerableResult, patchedResult);
                executed++;
            }
        }
        assertEquals("toFixed sweep must execute the full doubles x precisions grid, not a silently "
                + "truncated subset", TOFIXED_SWEEP_DOUBLES.length * TOFIXED_SWEEP_PRECISIONS.length, executed);
    }

    // ========== Shadow-precedence guard: the corpus must compare code BridgeLink ships ==========

    /**
     * {@code server/src} shadows thirteen Rhino classes; six of those matter to the corpus above
     * ({@code NativeDate}, {@code NativeJSON}, {@code NativeJavaObject}, {@code NativeBoolean}, and
     * the E4X trio {@code XmlProcessor}/{@code XmlNode}/{@code XMLObjectImpl}). This method asserts,
     * for at least {@code NativeDate} and {@code NativeJSON}, that each engine's loader resolves the
     * class from {@code server/classes} rather than from inside a jar. Without this guard, a future
     * classpath reordering would silently turn the corpus into a comparison of two jars' copies of
     * code BridgeLink never runs.
     */
    @Test
    public void shadowedRhinoClassesResolveFromServerClassesOnBothEngines() throws Exception {
        for (String className : SHADOWED_CLASSES_TOUCHED_BY_CORPUS) {
            assertResolvesFromServerClasses(vulnerableEngine, className);
            assertResolvesFromServerClasses(patchedEngine, className);
        }
    }

    private static void assertResolvesFromServerClasses(EngineUnderTest engine, String className) throws Exception {
        Class<?> shadowedClass = Class.forName(className, false, engine.classLoader);
        URL location = shadowedClass.getProtectionDomain().getCodeSource().getLocation();
        String path = location.getPath();

        assertTrue(engine.label + " " + className + " must resolve from server/classes (shadow "
                + "precedence), not from a jar. Resolved location: " + path,
                path.endsWith("classes/") || path.endsWith("classes"));
    }

    // ========== Containment: the vulnerable fixture must reach no production/test classpath ==========

    /**
     * Executable form of the never-on-any-classpath rule {@code server/test/fixtures/README.md}
     * states in prose. The one legitimate reader of {@code rhino-1.7.13.jar} is this class's own
     * {@link EngineUnderTest}, by the explicit relative path resolved in {@link #beforeClass()}.
     * This method proves that reading by any other path -- the running JVM's own classpath, any
     * module's {@code lib}/{@code testlib} directory, or the assembled {@code setup} tree -- does
     * not happen. It is proven able to fail: copying the fixture into {@code server/lib} reddens
     * this method (see the 23.1-05 SUMMARY for the captured one-shot proof).
     */
    @Test
    public void vulnerableFixtureIsNotOnAnyProductionOrTestClasspath() throws Exception {
        assertFalse("The running JVM's own java.class.path must not carry the vulnerable fixture",
                System.getProperty("java.class.path").contains("rhino-1.7.13"));

        String[] libDirsFromServerWorkingDirectory = { "lib", "testlib", "../client/lib", "../command/lib",
                "../manager/lib", "../donkey/lib" };
        for (String dir : libDirsFromServerWorkingDirectory) {
            assertNoFixtureUnderneath(new File(dir));
        }

        // SCAN_DIR in .github/workflows/cve-scan.yml -- if this tree exists (a distribution was
        // assembled), it must not carry the fixture either.
        File assembledSetupTree = new File("setup");
        if (assembledSetupTree.exists()) {
            assertNoFixtureUnderneath(assembledSetupTree);
        }
    }

    private static void assertNoFixtureUnderneath(File root) {
        if (!root.exists()) {
            return;
        }
        List<File> matches = new ArrayList<File>();
        collectFixtureMatches(root, matches);
        assertTrue("Found the vulnerable fixture rhino-1.7.13.jar under " + root.getAbsolutePath()
                + " at: " + matches, matches.isEmpty());
    }

    /** Recursive by design (D-25 lesson): a top-level-only glob misses a nested copy. */
    private static void collectFixtureMatches(File dir, List<File> matches) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectFixtureMatches(child, matches);
            } else if ("rhino-1.7.13.jar".equals(child.getName())) {
                matches.add(child);
            }
        }
    }

    // ========== The engine-version-parameterized evaluator ==========

    /**
     * Holds one isolated {@code URLClassLoader} (server/classes ahead of that engine's rhino jar,
     * parented at {@code ClassLoader.getPlatformClassLoader()}) and drives the full Rhino
     * enter/evaluate/exit cycle against it reflectively, because the loaded {@code Context} class
     * is deliberately not assignment-compatible with the one on this test's own classpath.
     */
    static final class EngineUnderTest {

        private final String label;
        final URLClassLoader classLoader;
        private final Method enterMethod;
        private final Method setLanguageVersionMethod;
        private final Method initStandardObjectsMethod;
        private final Method evaluateStringMethod;
        private final Method toStringMethod;
        private final Method exitMethod;
        private final int versionEs6;
        final String implementationVersion;

        EngineUnderTest(String label, File serverClassesDir, File rhinoJar) throws Exception {
            this.label = label;
            this.classLoader = new URLClassLoader(
                    new URL[] { serverClassesDir.toURI().toURL(), rhinoJar.toURI().toURL() },
                    ClassLoader.getPlatformClassLoader());

            Class<?> contextClass = Class.forName("org.mozilla.javascript.Context", true, classLoader);
            Class<?> scriptableClass = Class.forName("org.mozilla.javascript.Scriptable", true, classLoader);

            this.enterMethod = contextClass.getMethod("enter");
            this.setLanguageVersionMethod = contextClass.getMethod("setLanguageVersion", int.class);
            this.initStandardObjectsMethod = contextClass.getMethod("initStandardObjects");
            this.evaluateStringMethod = contextClass.getMethod("evaluateString", scriptableClass,
                    String.class, String.class, int.class, Object.class);
            this.toStringMethod = contextClass.getMethod("toString", Object.class);
            this.exitMethod = contextClass.getMethod("exit");
            this.versionEs6 = contextClass.getField("VERSION_ES6").getInt(null);

            // getImplementationVersion() is an INSTANCE method (unlike enter/exit/toString, which
            // are static) -- it requires an entered Context, so it is read here inside its own
            // enter/exit pair rather than invoked reflectively with a null target.
            Method getImplementationVersionMethod = contextClass.getMethod("getImplementationVersion");
            Object cx = enterMethod.invoke(null);
            try {
                this.implementationVersion = (String) getImplementationVersionMethod.invoke(cx);
            } finally {
                exitMethod.invoke(null);
            }
        }

        /**
         * Runs the whole enter/evaluate/exit cycle reflectively against this engine's isolated
         * loader. The result is converted to a String via the Context's own {@code toString}
         * WHILE STILL INSIDE the try block, before {@code exit} runs in the finally -- converting
         * outside the context throws for any script returning a Scriptable (23.1-REVIEW.md CR-02).
         */
        String evaluate(String script) {
            try {
                Object cx = enterMethod.invoke(null);
                try {
                    setLanguageVersionMethod.invoke(cx, versionEs6);
                    Object scope = initStandardObjectsMethod.invoke(cx);
                    Object result = evaluateStringMethod.invoke(cx, scope, script, "enginedifferential", 1, null);
                    return (String) toStringMethod.invoke(null, result);
                } finally {
                    exitMethod.invoke(null);
                }
            } catch (InvocationTargetException e) {
                throw new RuntimeException("Script evaluation failed on engine " + label + ": " + script,
                        e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Reflective evaluation setup failed on engine " + label, e);
            }
        }
    }
}
