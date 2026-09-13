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
     * RED before / GREEN after ACCESS_EXTERNAL_STYLESHEET is set: an {@code xsl:import} of a
     * {@code file:///} stylesheet must be blocked. This is the stylesheet half of
     * CVE-2026-78224 -- external references reached through the STYLESHEET rather than the source
     * -- which was previously only proven by a source-text string match (WR-01, IRT-2262). With
     * FSP removed, ACCESS_EXTERNAL_STYLESHEET is the sole control for this path, so it must be
     * proven by execution.
     * <p>
     * A blocked import throws at {@code newTransformer()} (stylesheet compile) time, carrying the
     * {@code accessExternalStylesheet} restriction in its message; if a given JDK instead ignores
     * the import rather than throwing, the imported sentinel template must never have run. Either
     * way the sentinel content must be absent. The caught-exception assertion requires the
     * restriction's own token (not the mere exception TYPE, which any stylesheet compile error
     * would satisfy and would pass this test vacuously if the fixture ever broke). Removing the
     * attribute reddens this test because the import resolves and the sentinel appears.
     */
    @Test
    public void externalStylesheetImportIsBlocked() throws Exception {
        String importSentinel = "XSLT-IMPORT-SENTINEL-4b8e1d2a6f";
        File importedStylesheet = File.createTempFile("xslt-import-sentinel", ".xsl");
        importedStylesheet.deleteOnExit();
        try (Writer writer = new FileWriter(importedStylesheet)) {
            writer.write("<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                    + "<xsl:template name=\"imported\"><xsl:text>" + importSentinel + "</xsl:text></xsl:template>"
                    + "</xsl:stylesheet>");
        }
        String importUri = importedStylesheet.toURI().toString();

        String sourceXml = "<root>hello</root>";
        String importingStylesheet = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:import href=\"" + importUri + "\"/>"
                + "<xsl:output method=\"text\"/>"
                + "<xsl:template match=\"/\"><xsl:call-template name=\"imported\"/></xsl:template>"
                + "</xsl:stylesheet>";

        XsltStep step = new XsltStep();
        step.setSourceXml(jsStringLiteral(sourceXml));
        step.setTemplate(jsStringLiteral(importingStylesheet));
        step.setResultVariable("resultVar_test");

        String script = invokeTransformationScript(step) + "resultVar.toString();";

        String result;
        try {
            result = evaluate(script);
        } catch (RuntimeException e) {
            // A blocked external stylesheet surfaces as a TransformerConfigurationException at
            // stylesheet-compile time; Rhino wraps it. Flatten the cause chain and require the
            // stylesheet-access restriction's own signal, so an unrelated syntax error cannot
            // pass this branch (Dan's tokens for this half, case-sensitive).
            String flattened = ExceptionUtils.getStackTrace(e);
            assertTrue("Expected the caught exception to be the blocked external-stylesheet "
                    + "restriction (message naming 'accessExternalStylesheet' or 'External "
                    + "Stylesheet'), not an unrelated RuntimeException or an unrelated stylesheet "
                    + "compile error. Flattened exception was: " + flattened,
                    flattened.contains("accessExternalStylesheet")
                            || flattened.contains("External Stylesheet"));
            assertFalse("The imported stylesheet must never have run", flattened.contains(importSentinel));
            return;
        }

        assertFalse("An xsl:import of a file:/// stylesheet must be blocked; the imported "
                + "sentinel template must not run. Result was: " + result, result.contains(importSentinel));
    }

    /**
     * RED before / GREEN after ACCESS_EXTERNAL_STYLESHEET is set: the XPath {@code document()}
     * function pointing at a {@code file:///} URL must not resolve. Together with
     * {@link #externalStylesheetImportIsBlocked()} this completes the execution-level proof of the
     * stylesheet half of CVE-2026-78224 (the {@code document()} function is governed by
     * ACCESS_EXTERNAL_STYLESHEET, not ACCESS_EXTERNAL_DTD).
     * <p>
     * On JDK 17 a blocked {@code document()} of a {@code file:///} URL throws at transform() time
     * carrying the {@code accessExternalStylesheet} restriction (i.e. the step fails to ERROR, the
     * same fail-to-ERROR behavior as the external-DTD half); the test also accepts the sentinel
     * simply being absent, in case another JDK on the matrix degrades to an empty node-set instead
     * of throwing. Either way the sentinel content must never reach the result. Removing the
     * attribute reddens this test because {@code document()} resolves and the sentinel appears.
     */
    @Test
    public void documentFunctionExternalReferenceIsBlocked() throws Exception {
        String docSentinel = "XSLT-DOCUMENT-SENTINEL-7c1f0e9b3d";
        File docFile = File.createTempFile("xslt-document-sentinel", ".xml");
        docFile.deleteOnExit();
        try (Writer writer = new FileWriter(docFile)) {
            writer.write("<data>" + docSentinel + "</data>");
        }
        String docUri = docFile.toURI().toString();

        String sourceXml = "<root>hello</root>";
        String documentStylesheet = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:output method=\"text\"/>"
                + "<xsl:template match=\"/\"><xsl:value-of select=\"document('" + docUri + "')/data\"/></xsl:template>"
                + "</xsl:stylesheet>";

        XsltStep step = new XsltStep();
        step.setSourceXml(jsStringLiteral(sourceXml));
        step.setTemplate(jsStringLiteral(documentStylesheet));
        step.setResultVariable("resultVar_test");

        String script = invokeTransformationScript(step) + "resultVar.toString();";

        String result;
        try {
            result = evaluate(script);
        } catch (RuntimeException e) {
            String flattened = ExceptionUtils.getStackTrace(e);
            assertTrue("If document() throws rather than degrading silently, it must be the "
                    + "blocked external-stylesheet restriction (message naming "
                    + "'accessExternalStylesheet' or 'External Stylesheet'), not an unrelated "
                    + "RuntimeException. Flattened exception was: " + flattened,
                    flattened.contains("accessExternalStylesheet")
                            || flattened.contains("External Stylesheet"));
            assertFalse("The document() target must never have been read", flattened.contains(docSentinel));
            return;
        }

        assertFalse("A document('file:///...') call must not resolve the external file; the "
                + "sentinel content must not reach the transform result. Result was: " + result,
                result.contains(docSentinel));
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
     * Pins the PREMISE of the no-FSP decision (IRT-2262, 2026-09-13), not just its implementation
     * string: a stylesheet that calls a Java extension function must still work with only the two
     * ACCESS_EXTERNAL_* attributes set. FEATURE_SECURE_PROCESSING disables extension functions with
     * no property that restores them, which is why it was removed; this test fails RED if FSP is
     * reintroduced (the transform then throws "Use of the extension function ... is not allowed
     * when the secure processing feature is set to true") and stays GREEN on the current code.
     * <p>
     * This is the one assertion in the suite whose answer plausibly differs across the 17/21/25
     * matrix, so it earns the PR run rather than a local JDK 17 result alone. The extension
     * function used is the JAXP/Xalan {@code java.lang.Math} binding, which needs no external
     * access and no custom classes.
     */
    @Test
    public void extensionFunctionStillWorksWithoutSecureProcessing() throws Exception {
        String sourceXml = "<root>x</root>";
        String extensionStylesheet = "<xsl:stylesheet version=\"1.0\" "
                + "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                + "xmlns:math=\"http://xml.apache.org/xalan/java/java.lang.Math\">"
                + "<xsl:output method=\"text\"/>"
                + "<xsl:template match=\"/\"><xsl:value-of select=\"math:max(2, 7)\"/></xsl:template>"
                + "</xsl:stylesheet>";

        XsltStep step = new XsltStep();
        step.setSourceXml(jsStringLiteral(sourceXml));
        step.setTemplate(jsStringLiteral(extensionStylesheet));
        step.setResultVariable("resultVar_test");

        String script = invokeTransformationScript(step) + "resultVar.toString();";

        String result;
        try {
            result = evaluate(script);
        } catch (RuntimeException e) {
            fail("A stylesheet calling a Java extension function must still run once FEATURE_SECURE_"
                    + "PROCESSING is removed (that is the reason it was removed; IRT-2262 decision "
                    + "2026-09-13). Reintroducing FSP reddens this test. Exception was: " + e);
            return;
        }

        assertTrue("The extension function math:max(2, 7) must evaluate to 7 with only the "
                + "ACCESS_EXTERNAL_* hardening applied. Result was: " + result, result.contains("7"));
    }

    /**
     * Cheap source-text supplement (not the whole proof): the generated script must emit the
     * ACCESS_EXTERNAL_DTD / ACCESS_EXTERNAL_STYLESHEET set-attribute calls after the newInstance
     * line. It must NOT set FEATURE_SECURE_PROCESSING: on JDK 17 FSP disables Java extension
     * functions in legitimate stylesheets with no property to restore them, and adds nothing to
     * this CVE's closure over the two ACCESS_EXTERNAL_* attributes (Dan Svanstedt's decision on
     * IRT-2262, 2026-09-13, matching public PR #198). This assertFalse pins that decision against
     * a regression that reintroduces FSP.
     */
    @Test
    public void generatedScriptEmitsExternalAccessBlockingAttributesWithoutSecureProcessing() throws Exception {
        XsltStep step = new XsltStep();
        step.setSourceXml("''");
        step.setTemplate("''");
        step.setResultVariable("resultVar_test");

        String script = invokeTransformationScript(step);

        assertTrue("Generated script must set ACCESS_EXTERNAL_DTD to block external DTD/entity resolution",
                script.contains("ACCESS_EXTERNAL_DTD"));
        assertTrue("Generated script must set ACCESS_EXTERNAL_STYLESHEET to block external stylesheet resolution",
                script.contains("ACCESS_EXTERNAL_STYLESHEET"));
        assertFalse("Generated script must NOT set FEATURE_SECURE_PROCESSING (it breaks legitimate "
                + "extension-function stylesheets on JDK 17 and adds nothing to the CVE closure; "
                + "IRT-2262 decision 2026-09-13)",
                script.contains("FEATURE_SECURE_PROCESSING"));
    }

    /**
     * The useCustomFactory=true branch must set the two ACCESS_EXTERNAL_* attributes and emit a
     * script-visible logger.warn if the custom TransformerFactory rejects them -- so a custom
     * factory that rejects the hardening never transforms unhardened and silent. This is a
     * source-text assertion (not an execution): the bare Rhino test scope has no real custom
     * factory implementation and no logger global, so the custom-factory script text is asserted
     * rather than run.
     * <p>
     * It must NOT set FEATURE_SECURE_PROCESSING, for the same reason as the default branch
     * (IRT-2262 decision 2026-09-13). With FSP removed the branch has a single guarded
     * setAttribute block, so exactly one logger.warn is expected.
     */
    @Test
    public void customFactoryScriptEmitsExternalAccessAndWarnWithoutSecureProcessing() throws Exception {
        XsltStep step = new XsltStep();
        step.setUseCustomFactory(true);
        step.setCustomFactory("com.example.CustomTransformerFactory");
        step.setSourceXml("''");
        step.setTemplate("''");
        step.setResultVariable("resultVar_test");

        String script = invokeTransformationScript(step);

        assertTrue("Generated script must take the custom-factory branch (custom factory class name present)",
                script.contains("com.example.CustomTransformerFactory"));
        assertTrue("Custom-factory branch must set ACCESS_EXTERNAL_DTD",
                script.contains("ACCESS_EXTERNAL_DTD"));
        assertTrue("Custom-factory branch must set ACCESS_EXTERNAL_STYLESHEET",
                script.contains("ACCESS_EXTERNAL_STYLESHEET"));
        assertFalse("Custom-factory branch must NOT set FEATURE_SECURE_PROCESSING (IRT-2262 "
                + "decision 2026-09-13)", script.contains("FEATURE_SECURE_PROCESSING"));
        assertTrue("Custom-factory catch must emit an observable logger.warn instead of a silent swallow",
                script.contains("logger.warn"));
        assertEquals("With FSP removed the custom-factory branch has a single guarded setAttribute "
                + "block, so exactly one logger.warn is expected",
                1, countOccurrences(script, "logger.warn"));
    }

    /**
     * G-26.13-8: customFactory is interpolated into two emitted-script sites (the newInstance
     * line and the single logger.warn line). A quote/backslash/newline in the configured class
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
