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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

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
 * Behavioral characterization suite (Phase 23.1 T2/T3, CVE-13) for the bump-sensitive vendored
 * Rhino 1.7.15.1 seams (server/lib/rhino-1.7.15.1.jar), run under the ES6 language version that
 * is the shipped {@code mirth.properties} default ({@code rhino.languageversion = es6}).
 * <p>
 * This is a NEW, standalone suite (D-05): it does NOT extend {@link RhinoSeamTest}, the
 * landed Phase-23 seam link-proof baseline, which is not modified. The {@code @BeforeClass}
 * bootstrap below is copied verbatim from {@code RhinoSeamTest} (same mocked
 * {@code ControllerFactory}/{@code ConfigurationController}/{@code ExtensionController}, same
 * Guice static-injection wiring, same scoped {@code conf}-directory classloader) so both suites
 * exercise scripts through the same real {@code MirthContextFactory} seam (D-07), not a raw
 * {@code Context.enter()}.
 * <p>
 * Scope (D-06): only the seams a Rhino version bump actually shifts: {@code NativeDate},
 * (zero coverage before this suite), number/string coercion, regex, and {@code JSON.stringify}
 * over a {@code NativeJavaObject} (the vendored unwrap-to-{@code NativeArray} seam in
 * {@code NativeJSON.str()}). NOT a broad built-in sweep.
 * <p>
 * Every assertion below asserts a concrete input-to-output value (D-11), never a tautology or a
 * bare {@code typeof}/non-null check. Each expected literal was captured from a standalone spike
 * run of the exact script through the real vendored jar + shadowed {@code server/src} classes
 * (the same shadow-precedence classpath ordering {@code server/build.xml}'s testclasspath uses)
 * under both {@code Context.VERSION_ES6} and {@code Context.VERSION_DEFAULT} before being locked
 * here (see 23.1-02-SUMMARY.md for the recorded spike output). The Date/coercion/regex
 * values are identical under both language versions (they are standard ECMA behavior, not
 * ES6-specific), which is itself the expected, verified result, not a fabricated guess.
 */
public class RhinoEs6BehaviorTest {

    private static MirthContextFactory contextFactory;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        ConfigurationController configurationController = mock(ConfigurationController.class);
        // rhino.languageversion = es6 is the shipped mirth.properties default: characterize the
        // same language version BridgeLink runs scripts under in production (D-07).
        when(configurationController.getRhinoLanguageVersion()).thenReturn(Context.VERSION_ES6);
        when(configurationController.getServerVersion()).thenReturn("26.9.0-es6-behavior-test");
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

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

        // MirthContextFactory's constructor triggers JavaScriptScopeUtil's one-time static
        // initializer, which loads mirth.properties as a classpath resource via the thread
        // context classloader. server/build.xml's testclasspath does not include server/conf
        // (only the production create-setup assembly copies it to server/setup/conf) -- make the
        // real, already-committed server/conf/mirth.properties available for the duration of this
        // one-time class init without touching build.xml.
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        File confDir = new File("conf");
        // The loader is CLOSED in the finally block: restoring the previous context classloader is
        // not enough on its own -- an unclosed URLClassLoader holds a file handle on server/conf for
        // the JVM's lifetime. The one-time class init it exists for has already happened by then.
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
            Object result = cx.evaluateString(scope, script, "es6behaviortest", 1, null);
            return Context.toString(result);
        } finally {
            Context.exit();
        }
    }

    // ========== T2: NativeDate (highest priority, zero coverage before this suite) ==========

    @Test
    public void nativeDateEpochAndIsoRoundTrip() {
        // UTC-anchored, TZ-independent values only (RESEARCH Pitfall 4) -- these must not depend
        // on the JVM's default timezone the way local-time formatting (toString/getHours) would.
        assertEquals("0", evaluate("new Date(0).getTime();"));
        assertEquals("1970-01-01T00:00:00.000Z", evaluate("new Date(0).toISOString();"));
        // Round-trip: parsing the ISO string this engine itself produced returns to the same
        // epoch millisecond value.
        assertEquals("0", evaluate("Date.parse('1970-01-01T00:00:00.000Z');"));
    }

    @Test
    public void nativeDateEpochBoundaryStepEitherSide() {
        // Boundary edge (must_haves): one step either side of the epoch is consistent through the
        // NativeDate seam.
        assertEquals("1", evaluate("new Date(1).getTime();"));
        assertEquals("-1", evaluate("new Date(-1).getTime();"));
    }

    // ========== T2: number/string coercion (precision edge) ==========

    @Test
    public void numberStringCoercion() {
        assertEquals("0.10.2", evaluate("'' + 0.1 + 0.2;"));
        assertEquals("0.3333333333333333", evaluate("(1 / 3).toString();"));
        assertEquals("42", evaluate("parseInt('42px', 10);"));
    }

    // ========== T2: regex ==========

    @Test
    public void regexGlobalReplace() {
        assertEquals("a#b#c#", evaluate("'a1b2c3'.replace(/\\d/g, '#');"));
    }

    // ========== T2: JSON.stringify over a NativeJavaObject (the explicit D-06 case) ==========

    @Test
    public void jsonStringifyUnwrapsNativeJavaObject() {
        // Exercises the vendored unwrap-to-NativeArray seam at NativeJSON.str() -- a java.util.List
        // injected into scope must stringify as a JSON array, not "{}"/a stringified toString(), or
        // an error, which is exactly what a bump could clobber.
        Context cx = contextFactory.enterContext();
        try {
            ScriptableObject scope = (ScriptableObject) cx.initStandardObjects();
            List<String> javaList = new ArrayList<String>();
            javaList.add("a");
            javaList.add("b");
            scope.put("javaList", scope, Context.javaToJS(javaList, scope));

            Object result = cx.evaluateString(scope, "JSON.stringify(javaList);", "es6behaviortest", 1, null);

            assertEquals("[\"a\",\"b\"]", Context.toString(result));
        } finally {
            Context.exit();
        }
    }

    // ========== T3: E4X output-formatting round-trip (D-08/D-09) ==========

    @Test
    public void e4xElementDeleteReserializesFlat() {
        // Sets the production seam config explicitly (D-09; JavaScriptTestUtil.
        // generateGlobalSealedScript() L165-167 sets this on the harness's shared sealed scope,
        // but this suite's evaluate() uses a fresh initStandardObjects() scope per call, not that
        // shared sealed scope, so the config is set inline here).
        //
        // A small custom indented HL7 fragment (not the large shared JavaScriptTestUtil.MSG)
        // maximizes ignoreWhitespace sensitivity: two sibling segment elements (PID, PV1) each on
        // their own indented line. Deleting PV1 -- the canonical blank-line-removal trigger (D-09)
        // -- must re-serialize to an exact flat string with no blank line and no stray indentation
        // left where PV1 was.
        String script = "XML.ignoreWhitespace=true;\n"
                + "XML.prettyPrinting=false;\n"
                + "var msg = new XML('<HL7Message>\\n  <PID><PID.1>1</PID.1></PID>\\n  <PV1><PV1.1>x</PV1.1></PV1>\\n</HL7Message>');\n"
                + "delete msg.PV1[0];\n"
                + "msg.toString();";

        // Captured exact literal from the first green run (23.1-02-SUMMARY.md) -- an exact-string
        // assertion driven by a logical mutation (D-08), NOT a byte-golden file compare. Falsifiable
        // (D-11): a standalone break-the-seam spike setting XML.ignoreWhitespace=false against this
        // same fragment/mutation produced a regressed result with a blank line and stray indentation
        // left where PV1 was deleted (recorded in 23.1-02-SUMMARY.md), proving this assertion would
        // go RED if the XmlProcessor.addTextNodesToRemoveAndTrim/toString(Node) seam regressed.
        assertEquals("<HL7Message><PID><PID.1>1</PID.1></PID></HL7Message>", evaluate(script));
    }

    // ========== Gap closure (23.1-04): evaluate() converts inside the Context (CR-02) ==========

    /**
     * Falsifiability pin for the CR-02 fix (23.1-REVIEW.md): both scripts below return a
     * {@link Scriptable} (an array literal, an object literal), and converting a Scriptable to a
     * String after leaving the Rhino Context throws {@code RuntimeException: No Context associated
     * with current Thread}, because {@code ScriptRuntime.toString(Object)} calls
     * {@code getDefaultValue()} for a Scriptable, which requires the current Context. This method
     * therefore reddens if {@code evaluate()} ever regresses to converting outside the context, the
     * pre-fix shape 23.1-REVIEW.md CR-02 found. The correct shape is
     * {@code RhinoSeamTest.evaluate()} (RhinoSeamTest.java:118-127), which this method's helper was
     * copied from and is now fixed to match.
     */
    @Test
    public void evaluateReturnsScriptableResultsWithinTheContext() {
        assertEquals("1,2,3", evaluate("[1,2,3];"));
        assertEquals("[object Object]", evaluate("({a:1});"));
    }

    // ========== Gap closure (23.1-03): toFixed() rounding/tie-breaking + precision (CVE-2025-66453) ==========

    // NOTE (23.1-04, CR-01): this method is an ES6 toFixed() formatting characterization backstop
    // and a bounded-time forward regression tripwire. It is NOT regression evidence for
    // CVE-2025-66453. 23.1-REVIEW.md on 2026-08-26 replayed these exact scripts against
    // rhino-1.7.13.jar (the vulnerable artifact Phase 23 replaced) and got byte-identical output,
    // with a 340-case differential fuzz over 34 doubles crossed with 10 precisions finding zero
    // differences. The 1.7.15.1 fix changed a resource bound, not any formatted value, so no
    // value-only assertion can evidence it. CVE-13's CVE-2025-66453 protection is carried by
    // version attestation: the Phase 23 re-land of rhino-1.7.15.1 plus the RhinoSeamTest
    // link-proof. Two-jar behavioral-compatibility evidence lands in
    // RhinoEngineVersionDifferentialTest (23.1-05).
    //
    // The @Test timeout below is a forward regression tripwire, not a CVE discriminator: it is
    // chosen generously (30000 ms) so it cannot flake on a loaded CI runner, and it does NOT
    // discriminate rhino-1.7.13.jar from rhino-1.7.15.1.jar (23.1-REVIEW.md measured both
    // returning in about 1 ms). server/build.xml's junit task runs haltonfailure="false" with
    // forkmode="perTest" and no per-test bound, so without this timeout an unbounded toFixed path
    // reintroduced by a future Rhino bump would stall the fork -- and the JDK 17, 21, and 25 legs
    // with it -- producing no failure signal at all; with it, the method reddens instead.
    @Test(timeout = 30000)
    public void numberToFixedRoundingTieBreakingAndPrecision() {
        // Every literal below was captured from a standalone spike run of the exact script through
        // the real vendored jar (server/classes:server/lib/rhino-1.7.15.1.jar shadow-precedence
        // classpath, matching 23.1-02's pattern) under Context.VERSION_ES6, NOT guessed (D-11).

        // (1) IEEE-754 tie-breaking: 1.005 is stored as ~1.00499999999999989 (not exactly 1.005),
        // so the correct ECMA/Rhino output rounds DOWN to "1.00" -- the naive expectation "1.01"
        // (rounding the decimal literal as written) is WRONG. Falsifiable: asserting the real
        // spike-captured "1.00" means a regression that rounded the naive way ("1.01") -- or any
        // other tie-breaking regression -- would redden this assert.
        assertEquals("1.00", evaluate("(1.005).toFixed(2);"));

        // (2) A common precision (not this engine's limit): zero padded to exactly 20 fractional
        // digits. The engine accepts up to 100 fractionDigits and rejects 101 with RangeError; see
        // (3) for the accepted maximum. Asserting the rejected side is out of this plan's scope.
        assertEquals("0.00000000000000000000", evaluate("(0).toFixed(20);"));

        // (3) Large-precision call at fractionDigits=100, the ECMA/vendored-engine maximum
        // (spike-confirmed: 101 throws RangeError). This is a FORMATTING backstop, not CVE
        // evidence (see the class-level NOTE above): 23.1-REVIEW.md measured this same script
        // returning the identical 102-character string in ~1ms on the vulnerable rhino-1.7.13.jar,
        // so no value-only assertion here can evidence the CVE-2025-66453 patch.
        assertEquals("0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000", evaluate("(0).toFixed(100);"));
    }

    // ========== Gap closure (23.1-03): JSON.stringify+JSON.parse round-trip, empty/single-element identity ==========

    @Test
    public void jsonRoundTripEmptyAndSingleElementNativeJavaObject() {
        // Closes the JSON half of the "empty edge" compound must_have: the existing
        // jsonStringifyUnwrapsNativeJavaObject() above is stringify-only over a 2-element list.
        // This test performs a real JSON.stringify()+JSON.parse() round-trip over an EMPTY and a
        // SINGLE-element injected java.util.List, exercising the NativeJavaObject unwrap seam at
        // NativeJSON.str() L273-282 (unwrap() -> Collection.toArray -> new NativeArray(...)) plus
        // the empty-array branch at NativeJSON.ja() and the parse() path, asserting BOTH the exact
        // intermediate JSON text AND the round-tripped structure so a regressed unwrap (rendering
        // an empty list as "{}"/a stringified toString(), or adding/dropping an element) reddens
        // the assert -- not merely a presence/length-only check.
        //
        // Every literal below was captured from a standalone spike run through the real vendored
        // jar (same shadow-precedence classpath as above), NOT guessed (A3/D-11).

        Context cx = contextFactory.enterContext();
        try {
            ScriptableObject scope = (ScriptableObject) cx.initStandardObjects();

            // Empty case: 0 elements in -> 0 elements out, none spuriously added.
            List<String> emptyList = new ArrayList<String>();
            scope.put("emptyList", scope, Context.javaToJS(emptyList, scope));
            Object emptyResult = cx.evaluateString(scope,
                    "var s = JSON.stringify(emptyList); var p = JSON.parse(s); s + '|' + p.length + '|' + Array.isArray(p);",
                    "es6behaviortest", 1, null);
            // Spike-captured: intermediate text "[]", round-tripped length 0, Array.isArray true.
            assertEquals("[]|0|true", Context.toString(emptyResult));

            // Single-element case: the one element survives the round-trip, none dropped/duplicated.
            List<String> singleList = new ArrayList<String>();
            singleList.add("only");
            scope.put("singleList", scope, Context.javaToJS(singleList, scope));
            Object singleResult = cx.evaluateString(scope,
                    "var s = JSON.stringify(singleList); var p = JSON.parse(s); s + '|' + p.length + '|' + p[0];",
                    "es6behaviortest", 1, null);
            // Spike-captured: intermediate text ["only"], round-tripped length 1, element "only".
            assertEquals("[\"only\"]|1|only", Context.toString(singleResult));
        } finally {
            Context.exit();
        }
    }

}
