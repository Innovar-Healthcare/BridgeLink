/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.UUID;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

/**
 * Phase 23.1 T1 divergence proof (D-03/D-04): demonstrates a concrete, falsifiable
 * DEFAULT-vs-ES6 behavioral divergence through the exact same {@link JavaScriptSharedUtil}
 * seam that {@link JavaScriptTestUtil#setup()} now drives with {@code Context.VERSION_ES6}.
 * This is proof that running the behavioral suites under {@code Context.VERSION_DEFAULT} (the
 * pre-fix state) was a genuine blind spot to production behavior -- not a theoretical one.
 * <p>
 * <b>Candidate selection (23.1-RESEARCH.md "T1 -- divergence-proof candidates"):</b> candidate A
 * ({@code let} loop-closure capture) was spiked first, per plan instruction. It DOES diverge
 * (DEFAULT throws, ES6 evaluates), but not with the assumed non-throwing var-hoist value ("333")
 * -- under this vendored Rhino 1.7.15.1, {@code let} itself requires ES6 grammar, so
 * {@code VERSION_DEFAULT} throws {@code EvaluatorException: missing ; after for-loop initializer}
 * before any closures execute. That collapses candidate A onto the exact same throw-vs-evaluate
 * shape as candidate B, so candidate B ({@code for...of}) is used here: it is the plan's own
 * "guaranteed divergence" fallback and requires no assumption about closure semantics, only that
 * {@code for...of} parses under ES6 and does not parse under DEFAULT.
 * <p>
 * <b>Recorded divergence (D-03):</b>
 * <pre>
 * script:  var s = 0; for (var v of [1,2,3]) s += v; s;
 * DEFAULT: throws org.mozilla.javascript.EvaluatorException
 *          ("missing ; after for-loop initializer")
 * ES6:     "6"
 * </pre>
 * <p>
 * This class does NOT call {@link JavaScriptTestUtil#setup()} and is not part of the ~27-test
 * ES6-only behavioral suite (D-01/D-04) -- it is a standalone, focused DEFAULT-vs-ES6 comparison.
 * {@code server/build.xml} uses {@code forkmode="perTest"}, so this class runs in its own JVM;
 * the shared {@link JavaScriptSharedUtil} static therefore starts at its class-load default,
 * {@code Context.VERSION_DEFAULT} (verified: {@code JavaScriptSharedUtil.java:60}), before this
 * test ever touches it. The static is restored to that same starting value in a {@code finally}
 * block so this class leaves no residue for any other test that might share the fork.
 */
public class RhinoEs6DivergenceProofTest {

    private static final String DIVERGENT_SCRIPT = "var s = 0; for (var v of [1,2,3]) s += v; s;";

    @Test
    public void forOfThrowsUnderDefaultOnlyUnderEs6() throws Exception {
        try {
            // --- Run 1: VERSION_DEFAULT ---
            // for...of is not valid grammar under VERSION_DEFAULT in the vendored Rhino
            // 1.7.15.1; it must throw here for the divergence to be real (D-11: falsifiable,
            // not tautological -- if this run ever silently evaluated a value, the assertion
            // below asserting non-equality with ES6's "6" could go green for the wrong reason).
            String defaultOutcome;
            try {
                String evaluated = run(Context.VERSION_DEFAULT, DIVERGENT_SCRIPT);
                fail("Expected for...of to throw an EvaluatorException under VERSION_DEFAULT, but it evaluated to: " + evaluated);
                return; // unreachable, keeps the compiler happy about definite assignment
            } catch (EvaluatorException e) {
                defaultOutcome = "THROW:" + e.getClass().getName();
            }

            // --- Run 2: VERSION_ES6 ---
            String es6Outcome = run(Context.VERSION_ES6, DIVERGENT_SCRIPT);

            // Falsifiable per D-11/D-14: this test fails if DEFAULT and ES6 ever produced the
            // same outcome (both throwing, or both evaluating to the same value), and fails if
            // ES6's numeric result were ever wrong -- there is no assumeTrue/skip channel that
            // could report PASS without both branches actually executing and being compared.
            assertNotEquals("DEFAULT and ES6 must diverge on the same for...of script", defaultOutcome, es6Outcome);
            assertTrue("DEFAULT must throw (not silently evaluate)", defaultOutcome.startsWith("THROW:"));
            assertEquals("ES6 must evaluate the for...of loop to the numeric sum of [1,2,3]", "6", es6Outcome);
        } finally {
            // Restore to this fork's class-load default (JavaScriptSharedUtil.java:60) so no
            // residue leaks into any other test sharing this JVM (D-01/D-04).
            JavaScriptSharedUtil.setRhinoLanguageVersion(Context.VERSION_DEFAULT);
        }
    }

    private String run(int languageVersion, String script) throws Exception {
        JavaScriptSharedUtil.setRhinoLanguageVersion(languageVersion);
        Context context = JavaScriptSharedUtil.getGlobalContextForValidation();
        try {
            Scriptable scope = context.initStandardObjects();
            Script compiledScript = context.compileString(script, UUID.randomUUID().toString(), 1, null);
            Object result = compiledScript.exec(context, scope);
            return Context.toString(result);
        } finally {
            Context.exit();
        }
    }
}
