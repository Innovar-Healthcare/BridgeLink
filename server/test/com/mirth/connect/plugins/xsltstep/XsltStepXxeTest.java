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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.lang.reflect.Method;

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
            // "the entity was not resolved" -- the important assertion is that the sentinel
            // content never appears in a successful result.
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
     * line.
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
