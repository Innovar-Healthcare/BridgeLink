/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 * Copyright (c) 2026 Innovar Healthcare. All rights reserved
 * This project is a fork of Mirth Connect by Nextgen Healthcare.
 * It has been modified and maintained independently by Innovar Healthcare.
 */

package com.mirth.connect.plugins.xsltstep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.lang.reflect.Method;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * Falsifiable negative test for CVE-2026-78224 (CWE-611, XXE in the XSLT Transformer Step,
 * CVSS 8.2, IRT-2262). {@code XsltStep.getTransformationScript()} builds the Rhino script that
 * runs at channel runtime with a bare {@code TransformerFactory} and no external-access
 * restriction on attacker-controllable {@code sourceXml}/{@code template}.
 * <p>
 * This suite drives the ACTUAL {@code getTransformationScript()} output through a real Rhino
 * {@code Context}, following the plain {@code Context.enter()} +
 * {@code cx.initStandardObjects()} precedent already established for simple script-execution
 * assertions in {@code JavaScriptSharedUtilTest} (no Mirth sealed-scope / language-version
 * pinning is needed here: the emitted script only uses {@code Packages.*} Java interop, not
 * E4X, {@code msg}, or any imported Mirth userutil symbol). Reverting the hardening in
 * {@code XsltStep.java} reddens {@link #xxeEntityIsNotResolved()} because the assertion runs
 * the emitted script text itself, not a hand-built factory (D-07).
 * <p>
 * The XXE payload is a synthetic local sentinel file written to a JUnit temp directory -- no
 * PHI, no network dependency.
 */
public class XsltStepXxeTest {

    private static final String SENTINEL_MARKER = "XXE-SENTINEL-9f3a2b1c7e";

    /**
     * RED before the fix / GREEN after the fix: an XSLT payload whose sourceXml declares an
     * external general entity (SYSTEM identifier pointing at a local sentinel file) must NOT
     * have that entity resolved when the generated script runs. On the unhardened code the
     * entity expands and the sentinel content appears in the transform result; on the hardened
     * code either the external access is blocked (script throws) or the entity is not
     * expanded -- either way the sentinel content must never appear.
     */
    @Test
    public void xxeEntityIsNotResolved() throws Exception {
        File sentinelFile = File.createTempFile("xslt-xxe-sentinel", ".txt");
        sentinelFile.deleteOnExit();
        try (Writer writer = new FileWriter(sentinelFile)) {
            writer.write(SENTINEL_MARKER);
        }
        String sentinelUri = sentinelFile.toURI().toString();

        String xxeSourceXml = "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"" + sentinelUri + "\">]><root>&xxe;</root>";
        String identityTextStylesheet = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:output method=\"text\"/>"
                + "<xsl:template match=\"/\"><xsl:value-of select=\"root\"/></xsl:template>"
                + "</xsl:stylesheet>";

        XsltStep step = new XsltStep();
        step.setSourceXml(jsStringLiteral(xxeSourceXml));
        step.setTemplate(jsStringLiteral(identityTextStylesheet));
        step.setResultVariable("resultVar_test");

        String script = invokeTransformationScript(step) + "resultVar.toString();";

        String result;
        try {
            result = evaluate(script);
        } catch (RuntimeException e) {
            // Blocked external access surfacing as a thrown exception is an acceptable form of
            // "the entity was not resolved" -- but it must be THIS specific restriction firing,
            // not any RuntimeException (an emitted-script syntax error would otherwise also
            // pass). Rhino wraps the underlying TransformerException/SAXParseException, so
            // flatten the throwable and its full cause chain and look for the restriction's
            // own signal (Dan's tokens, case-sensitive).
            String flattened = ExceptionUtils.getStackTrace(e);
            assertTrue("Expected the caught exception to be the blocked external-access "
                    + "restriction (message containing 'External Entity' or 'accessExternalDTD'), "
                    + "not an unrelated RuntimeException. Flattened exception was: " + flattened,
                    flattened.contains("External Entity") || flattened.contains("accessExternalDTD"));
            return;
        }

        assertFalse("External entity SYSTEM sentinel content must not be resolved into the "
                + "transform result; entity resolution should be blocked at the transformer "
                + "level. Result was: " + result, result.contains(SENTINEL_MARKER));
    }

    /**
     * Positive control: an ordinary XSLT transform with no external reference still succeeds
     * and produces the expected result, proving the hardening does not break normal transforms.
     */
    @Test
    public void ordinaryTransformStillSucceeds() throws Exception {
        String sourceXml = "<root>hello</root>";
        String identityTextStylesheet = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:output method=\"text\"/>"
                + "<xsl:template match=\"/\"><xsl:value-of select=\"root\"/></xsl:template>"
                + "</xsl:stylesheet>";

        XsltStep step = new XsltStep();
        step.setSourceXml(jsStringLiteral(sourceXml));
        step.setTemplate(jsStringLiteral(identityTextStylesheet));
        step.setResultVariable("resultVar_test");

        String script = invokeTransformationScript(step) + "resultVar.toString();";

        String result;
        try {
            result = evaluate(script);
        } catch (RuntimeException e) {
            fail("An ordinary transform with no external reference must not throw: " + e);
            return;
        }

        assertTrue("Ordinary transform must still produce the expected output", result.contains("hello"));
    }

    /**
     * Cheap source-text supplement (not the whole proof): the generated script must emit the
     * ACCESS_EXTERNAL_DTD / ACCESS_EXTERNAL_STYLESHEET set-attribute calls after the newInstance
     * line, plus FEATURE_SECURE_PROCESSING (G-26.13-3 / WR-03) to bound entity expansion and
     * extension functions on attacker-influenceable stylesheet/source text, consistent with the
     * sibling XMLBatchAdaptor fix.
     */
    @Test
    public void generatedScriptEmitsExternalAccessBlockingAttributes() throws Exception {
        XsltStep step = new XsltStep();
        step.setSourceXml("''");
        step.setTemplate("''");
        step.setResultVariable("resultVar_test");

        String script = invokeTransformationScript(step);

        assertTrue("Generated script must set ACCESS_EXTERNAL_DTD to block external DTD/entity resolution",
                script.contains("ACCESS_EXTERNAL_DTD"));
        assertTrue("Generated script must set ACCESS_EXTERNAL_STYLESHEET to block external stylesheet resolution",
                script.contains("ACCESS_EXTERNAL_STYLESHEET"));
        assertTrue("Generated script must set FEATURE_SECURE_PROCESSING to bound entity expansion/extension functions",
                script.contains("FEATURE_SECURE_PROCESSING"));
        assertTrue("G-26.13-8: default-factory branch must emit an observable logger.warn if it "
                + "rejects FEATURE_SECURE_PROCESSING, instead of silently swallowing the rejection "
                + "(the engine root logger sits at ERROR, so a silent swallow is invisible)",
                script.contains("logger.warn"));
    }

    /**
     * G-26.13-3 (WR-03/WR-04): the useCustomFactory=true branch must emit the same
     * FEATURE_SECURE_PROCESSING hardening as the default-factory branch, plus a script-visible
     * logger.warn if the custom TransformerFactory rejects the hardening attempts -- so a custom
     * factory that rejects them never transforms fully unhardened and silent. This is a
     * source-text assertion (not an execution): the bare Rhino test scope has no real custom
     * factory implementation and no logger global, so the custom-factory script text is asserted
     * rather than run.
     * <p>
     * The secure-processing feature and the external-access attributes must be guarded
     * INDEPENDENTLY (two try/catch blocks, hence two logger.warn calls): a custom factory that
     * rejects FEATURE_SECURE_PROCESSING must not also skip the external-access controls. A single
     * shared guard reddens this test because it emits only one logger.warn.
     */
    @Test
    public void customFactoryScriptEmitsSecureProcessingExternalAccessAndWarn() throws Exception {
        XsltStep step = new XsltStep();
        step.setUseCustomFactory(true);
        step.setCustomFactory("com.example.CustomTransformerFactory");
        step.setSourceXml("''");
        step.setTemplate("''");
        step.setResultVariable("resultVar_test");

        String script = invokeTransformationScript(step);

        assertTrue("Generated script must take the custom-factory branch (custom factory class name present)",
                script.contains("com.example.CustomTransformerFactory"));
        assertTrue("Custom-factory branch must set FEATURE_SECURE_PROCESSING to bound entity expansion/extension functions",
                script.contains("FEATURE_SECURE_PROCESSING"));
        assertTrue("Custom-factory branch must still set ACCESS_EXTERNAL_DTD",
                script.contains("ACCESS_EXTERNAL_DTD"));
        assertTrue("Custom-factory branch must still set ACCESS_EXTERNAL_STYLESHEET",
                script.contains("ACCESS_EXTERNAL_STYLESHEET"));
        assertTrue("Custom-factory catch must emit an observable logger.warn instead of a silent swallow",
                script.contains("logger.warn"));
        assertEquals("Secure-processing and external-access hardening must be guarded independently "
                + "(two try/catch blocks, so a factory rejecting one does not skip the other); "
                + "expected two logger.warn calls in the custom-factory branch",
                2, countOccurrences(script, "logger.warn"));
    }

    /**
     * G-26.13-8: customFactory is interpolated into three emitted-script sites (the newInstance
     * line and the two logger.warn lines). A quote/backslash/newline in the configured class
     * name must not break or inject into the generated Rhino script. This is a source-text
     * assertion (not an execution): the synthetic class name below is never loaded, it is only
     * checked for its escaped form in the emitted script text.
     */
    @Test
    public void customFactoryValueIsEscapedIntoTheGeneratedScript() throws Exception {
        XsltStep step = new XsltStep();
        step.setUseCustomFactory(true);
        step.setCustomFactory("com.acme.Odd'Factory");
        step.setSourceXml("''");
        step.setTemplate("''");
        step.setResultVariable("resultVar_test");

        String script = invokeTransformationScript(step);

        assertTrue("Generated script must contain the escaped customFactory token at every "
                + "interpolation site", script.contains("Odd\\'Factory"));
        assertFalse("Generated script must never contain the bare, unescaped customFactory token "
                + "(it would terminate the JS string early)", script.contains("Odd'Factory"));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        int idx;
        while ((idx = haystack.indexOf(needle, from)) != -1) {
            count++;
            from = idx + needle.length();
        }
        return count;
    }

    private static String invokeTransformationScript(XsltStep step) throws Exception {
        Method method = XsltStep.class.getDeclaredMethod("getTransformationScript");
        method.setAccessible(true);
        return (String) method.invoke(step);
    }

    /**
     * XsltStep.getTransformationScript() concatenates the raw sourceXml/template field values
     * directly into the generated script as JavaScript expressions (not string literals), so a
     * test must supply already-quoted JS string literals -- escape only backslash and single
     * quote since every payload here is built on a single line.
     */
    private static String jsStringLiteral(String raw) {
        return "'" + raw.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private String evaluate(String script) {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Object result = cx.evaluateString(scope, script, "xsltStepXxeTest", 1, null);
            return Context.toString(result);
        } finally {
            Context.exit();
        }
    }
}
