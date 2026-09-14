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

import java.io.File;
import java.util.LinkedList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.model.IteratorProperties;
import com.mirth.connect.model.Step;
import com.mirth.connect.util.JavaScriptTestUtil;

/**
 * Ported and adapted from public PR #198
 * (github.com/Innovar-Healthcare/BridgeLink/pull/198, contributor benjodo) under IRT-2262
 * comment 47293 directive (c). Tests only -- no production file is modified by this class.
 * <p>
 * {@link XsltStepXxeTest} drives {@code XsltStep.getTransformationScript()} (a private method,
 * reached by reflection) through a bare {@code Context.initStandardObjects()} Rhino scope. That
 * harness has no logger global and no channelMap, so it can prove the emitted script TEXT but
 * cannot execute the {@code useCustomFactory} branch end to end. This class instead drives the
 * PUBLIC {@code XsltStep.getScript(true)} (and {@code getIterationScript}) through
 * {@link JavaScriptTestUtil#testTransformerStep(Step, ConnectorMessage)}, the same
 * production-like sealed {@code ImporterTopLevel} scope other transformer-step suites use (see
 * {@code DestinationSetFilterStepTest}), which supplies a real log4j logger and a write-through
 * channelMap (backed by {@code ImmutableConnectorMessage(connectorMessage, true)}). This is the
 * first time the {@code useCustomFactory} branch actually executes in this suite.
 * <p>
 * Not ported from #198: {@code testExternalGeneralEntityIsNotResolved} (default-branch external
 * entity via an internal DTD subset) is already pinned by
 * {@link XsltStepXxeTest#xxeEntityIsNotResolved()}, and
 * {@code testGeneratedScriptHardensCustomTransformerFactory} (custom-factory branch source text)
 * is already pinned by
 * {@link XsltStepXxeTest#customFactoryScriptEmitsExternalAccessAndWarnWithoutSecureProcessing()}.
 * Porting either again would duplicate existing coverage.
 */
public class XsltStepTest {

    private static final String CANARY = "XSLT_XXE_CANARY_7f3a9c";
    private static final String RESULT_VARIABLE = "xsltResult";

    /**
     * The JDK's own XSLTC TransformerFactory, addressed by fully qualified class name. It is the
     * only TransformerFactory implementation on the server test classpath (server/lib carries
     * xercesImpl-2.12.2 and xml-apis but no Xalan or Saxon distribution jar), so
     * {@code TransformerFactory.newInstance(FQCN, null)} resolves it through the context class
     * loader; server/build.xml already opens
     * {@code java.xml/com.sun.org.apache.xalan.internal.xsltc.trax} to the test JVM
     * (--add-opens), and it accepts both ACCESS_EXTERNAL_* attributes.
     */
    private static final String CUSTOM_FACTORY_FQCN = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";

    private static final String IDENTITY_TEMPLATE = "<xsl:stylesheet version=\"1.0\" "
            + "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
            + "<xsl:output method=\"xml\" omit-xml-declaration=\"yes\"/>"
            + "<xsl:template match=\"/\"><xsl:copy-of select=\".\"/></xsl:template>"
            + "</xsl:stylesheet>";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void setup() throws Exception {
        JavaScriptTestUtil.setup();
    }

    /**
     * THE directed item (from #198 testExternalEntityIsNotResolvedWithCustomFactory). The
     * useCustomFactory branch is executed at runtime for the first time in this suite: a factory
     * that rejected the ACCESS_EXTERNAL_* attributes would warn on the real logger and transform
     * unhardened, and this test would then go RED because the canary leaks -- the branch's guard
     * cannot pass silently here.
     */
    @Test
    public void customFactoryExternalEntityIsNotResolvedAtRuntime() throws Exception {
        File sentinelFile = temporaryFolder.newFile("xslt-custom-factory-sentinel.txt");
        FileUtils.write(sentinelFile, CANARY, "UTF-8");
        String sentinelUri = sentinelFile.toURI().toString();

        String xxeSourceXml = "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"" + sentinelUri + "\">]><root>&xxe;</root>";

        XsltStep step = newStep(xxeSourceXml, IDENTITY_TEMPLATE);
        step.setUseCustomFactory(true);
        step.setCustomFactory(CUSTOM_FACTORY_FQCN);

        String result;
        try {
            result = transform(step);
        } catch (Exception e) {
            String flattened = ExceptionUtils.getStackTrace(e);
            assertTrue("Expected the caught exception to be the blocked external-access "
                    + "restriction (message containing 'External Entity' or 'accessExternalDTD'), "
                    + "not an unrelated exception. Flattened exception was: " + flattened,
                    flattened.contains("External Entity") || flattened.contains("accessExternalDTD"));
            assertFalse("The canary must never have leaked into the flattened exception", flattened.contains(CANARY));
            return;
        }

        assertFalse("External entity SYSTEM sentinel content must not be resolved into the "
                + "channelMap result when useCustomFactory runs against the JDK's own XSLTC "
                + "factory by FQCN. Result was: " + result, result.contains(CANARY));
    }

    /**
     * Positive control for the custom-factory branch (adapted from #198
     * testBenignXmlStillTransforms). Proves the branch's newInstance(FQCN, null), guarded
     * setAttribute, newTransformer, transform and channelMap.put all execute in the
     * production-like scope, so the negative test above is not passing on a broken script.
     */
    @Test
    public void customFactoryBenignTransformSucceedsAtRuntime() throws Exception {
        String sourceXml = "<d><v>1</v></d>";

        XsltStep step = newStep(sourceXml, IDENTITY_TEMPLATE);
        step.setUseCustomFactory(true);
        step.setCustomFactory(CUSTOM_FACTORY_FQCN);

        String result = transform(step);

        assertEquals("A benign transform through the custom XSLTC factory must produce the "
                + "identity-copied document with no XML declaration", sourceXml, result);
    }

    /**
     * Verbatim port of #198 testBenignXmlStillTransforms (default branch): same input and
     * exact-equality assertion, useCustomFactory left false.
     */
    @Test
    public void benignTransformSucceedsAtRuntime() throws Exception {
        String sourceXml = "<d><v>1</v></d>";

        XsltStep step = newStep(sourceXml, IDENTITY_TEMPLATE);

        String result = transform(step);

        assertEquals("A benign transform through the default TransformerFactory must produce "
                + "the identity-copied document with no XML declaration", sourceXml, result);
    }

    /**
     * From #198 testExternalDtdIsNotLoaded (default branch); the external-DTD-subset vector our
     * suite does not yet exercise. Sharper than #198's SYSTEM-entity DTD: the DTD file declares
     * an INTERNAL-valued entity, so a leak depends only on the external subset being loaded and
     * nothing else.
     */
    @Test
    public void externalDtdIsNotLoadedAtRuntime() throws Exception {
        File dtdFile = temporaryFolder.newFile("xslt-external-subset.dtd");
        FileUtils.write(dtdFile, "<!ENTITY x \"" + CANARY + "\">", "UTF-8");
        String dtdUri = dtdFile.toURI().toString();

        String xxeSourceXml = "<!DOCTYPE d SYSTEM \"" + dtdUri + "\"><d>&x;</d>";

        XsltStep step = newStep(xxeSourceXml, IDENTITY_TEMPLATE);

        String result;
        try {
            result = transform(step);
        } catch (Exception e) {
            String flattened = ExceptionUtils.getStackTrace(e);
            assertTrue("Expected the caught exception to be the blocked external-DTD "
                    + "restriction (message containing 'External DTD' or 'accessExternalDTD'), "
                    + "not an unrelated exception. Flattened exception was: " + flattened,
                    flattened.contains("External DTD") || flattened.contains("accessExternalDTD"));
            assertFalse("The canary must never have leaked into the flattened exception", flattened.contains(CANARY));
            return;
        }

        assertFalse("A SYSTEM DOCTYPE's external subset must not be loaded, so the "
                + "internal-valued entity it declares must never resolve into the channelMap "
                + "result. Result was: " + result, result.contains(CANARY));
    }

    /**
     * Adapted from #198 testGeneratedScriptHardensTransformerFactory; source-text over the
     * PUBLIC API. XsltStepXxeTest reaches only the private getTransformationScript() method by
     * reflection; this pins the two public emitters (getScript / getIterationScript) and the
     * ordering invariant: the hardening must precede the factory's first use
     * (tFactory.newTransformer(...)).
     */
    @Test
    public void generatedScriptHardensFactoryBeforeNewTransformer() throws Exception {
        XsltStep step = newStep("''", IDENTITY_TEMPLATE);

        String getScript = step.getScript(true);
        String getIterationScript = step.getIterationScript(true, new LinkedList<IteratorProperties<Step>>());

        for (String script : new String[] { getScript, getIterationScript }) {
            assertTrue("Generated script must set ACCESS_EXTERNAL_DTD to block external "
                    + "DTD/entity resolution", script.contains("ACCESS_EXTERNAL_DTD"));
            assertTrue("Generated script must set ACCESS_EXTERNAL_STYLESHEET to block external "
                    + "stylesheet resolution", script.contains("ACCESS_EXTERNAL_STYLESHEET"));

            int newTransformerIndex = script.indexOf("tFactory.newTransformer(");
            assertTrue("tFactory.newTransformer( must appear in the generated script",
                    newTransformerIndex >= 0);
            assertTrue("ACCESS_EXTERNAL_DTD must be emitted before tFactory.newTransformer(",
                    script.indexOf("ACCESS_EXTERNAL_DTD") < newTransformerIndex);
            assertTrue("ACCESS_EXTERNAL_STYLESHEET must be emitted before tFactory.newTransformer(",
                    script.indexOf("ACCESS_EXTERNAL_STYLESHEET") < newTransformerIndex);
        }
    }

    private static XsltStep newStep(String sourceXml, String template) {
        XsltStep step = new XsltStep();
        step.setSourceXml(jsLiteral(sourceXml));
        step.setTemplate(jsLiteral(template));
        step.setResultVariable(RESULT_VARIABLE);
        return step;
    }

    private static String jsLiteral(String raw) {
        return "'" + StringEscapeUtils.escapeEcmaScript(raw) + "'";
    }

    private static String transform(XsltStep step) throws Exception {
        ConnectorMessage connectorMessage = new ConnectorMessage();
        JavaScriptTestUtil.testTransformerStep(step, connectorMessage);
        Object result = connectorMessage.getChannelMap().get(RESULT_VARIABLE);
        return result == null ? null : result.toString();
    }
}
