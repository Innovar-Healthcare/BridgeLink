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

package com.mirth.connect.plugins.datatypes.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.velocity.runtime.RuntimeConstants;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.donkey.model.message.BatchRawMessage;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.message.batch.BatchAdaptor;
import com.mirth.connect.donkey.server.message.batch.BatchMessageReader;
import com.mirth.connect.model.datatype.SerializerProperties;
import com.mirth.connect.plugins.datatypes.xml.XMLBatchProperties.SplitType;
import com.mirth.connect.server.controllers.ContextFactoryController;
import com.mirth.connect.server.controllers.ControllerFactory;

/**
 * Ported and adapted from public PR #199
 * (github.com/Innovar-Healthcare/BridgeLink/pull/199, contributor benjodo) under IRT-2262
 * comment 47293 directive (c). Tests only -- no production file is modified by this class.
 * <p>
 * {@link XMLBatchAdaptorXxeTest} proves the external-general-entity XXE vector is blocked but
 * only ever reads the FIRST split message (a single XPath_Query fixture through
 * {@code adaptor.getMessage()} once). This class pins the coverage Calvin's Jira comment 47292
 * flagged as missing: all three XPath-backed {@code SplitType} values (Element_Name, Level,
 * XPath_Query) driven through the full {@link BatchAdaptor#getMessage()} lookahead loop with the
 * complete ordered output asserted, a namespaced-batch split, and the external-DTD-subset
 * (SYSTEM DOCTYPE) vector.
 * <p>
 * D-04 re-targeting: this suite keeps internal DTD subsets and only blocks EXTERNAL resolution
 * (IRT-2262 CONTEXT.md D-04), so {@link #externalDtdSubsetIsNotLoadedAndBatchStillSplits()}
 * asserts the batch STILL splits and the sentinel is simply absent. #199's
 * {@code testExternalDtdIsNotLoaded} instead asserted the batch is REJECTED with a
 * {@code BatchMessageException} -- that rejection-expecting shape encodes #199's separate
 * reject-every-DOCTYPE source change and is deliberately NOT ported (Dan: "nothing from #199's
 * source"). #199's {@code testExternalGeneralEntityIsNotResolved} is also not ported: it
 * duplicates the vector already pinned by {@link XMLBatchAdaptorXxeTest#xxeEntityIsNotResolved()}.
 */
public class XMLBatchAdaptorTest {

    private static final String CANARY = "XML_BATCH_XXE_CANARY_2b8d41";

    private static final String BATCH = "<batch><message>1</message><message>2</message><message>3</message></batch>";

    private static final List<String> EXPECTED_MESSAGES = Arrays.asList(
            "<message>1</message>", "<message>2</message>", "<message>3</message>");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        ContextFactoryController contextFactoryController = mock(ContextFactoryController.class);
        when(controllerFactory.createContextFactoryController()).thenReturn(contextFactoryController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);

        Logger logger = LogManager.getLogger(RuntimeConstants.DEFAULT_RUNTIME_LOG_NAME);
        Configurator.setLevel(logger.getName(), Level.OFF);
    }

    /**
     * From #199 testElementNameSplit. Pins the Element_Name split's local-name() query over the
     * full BatchAdaptor.getMessage() lookahead loop -- the complete, ordered three-message
     * output, which XMLBatchAdaptorXxeTest's first-message-only helper does not assert.
     */
    @Test
    public void elementNameSplit() throws Exception {
        XMLBatchProperties properties = new XMLBatchProperties();
        properties.setSplitType(SplitType.Element_Name);
        properties.setElementName("message");

        assertEquals(EXPECTED_MESSAGES, readAll(newAdaptor(BATCH, properties)));
    }

    /**
     * From #199 testLevelSplit. Pins the Level split's loop-bound query over the full
     * getMessage() lookahead loop.
     */
    @Test
    public void levelSplit() throws Exception {
        XMLBatchProperties properties = new XMLBatchProperties();
        properties.setSplitType(SplitType.Level);
        properties.setLevel(1);

        assertEquals(EXPECTED_MESSAGES, readAll(newAdaptor(BATCH, properties)));
    }

    /**
     * From #199 testXPathQuerySplit. Pins multi-message iteration and order through the
     * getMessage() lookahead, which the first-message-only helper in XMLBatchAdaptorXxeTest does
     * not exercise.
     */
    @Test
    public void xpathQuerySplit() throws Exception {
        XMLBatchProperties properties = new XMLBatchProperties();
        properties.setSplitType(SplitType.XPath_Query);
        properties.setQuery("/batch/message[position() > 1]");

        assertEquals(EXPECTED_MESSAGES.subList(1, 3), readAll(newAdaptor(BATCH, properties)));
    }

    /**
     * From #199 testNamespacedElementNameSplit. local-name() is what lets Element_Name match
     * prefixed elements even though XMLBatchProperties' descriptor text says Element Name does
     * not work with namespaces; this test pins the actual behavior. Also asserts the xmlns:b
     * declaration is preserved on each split element -- the namespace-aware serialization parity
     * restored by plan 05 / WR-02; a setNamespaceAware(true) revert would drop it.
     */
    @Test
    public void namespacedElementNameSplit() throws Exception {
        String namespacedBatch = "<b:batch xmlns:b=\"urn:b\"><b:message>1</b:message><b:message>2</b:message></b:batch>";

        XMLBatchProperties properties = new XMLBatchProperties();
        properties.setSplitType(SplitType.Element_Name);
        properties.setElementName("message");

        List<String> messages = readAll(newAdaptor(namespacedBatch, properties));

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains(">1</b:message>"));
        assertTrue(messages.get(1).contains(">2</b:message>"));
        for (String message : messages) {
            assertTrue("split message must preserve the xmlns:b declaration: " + message,
                    message.contains("xmlns:b=\"urn:b\""));
        }
    }

    /**
     * Re-targeted from #199 testExternalDtdIsNotLoaded to D-04 semantics: this suite keeps
     * internal DTD subsets and blocks only external resolution, so a SYSTEM-DOCTYPE batch must
     * STILL SPLIT (not be rejected with a BatchMessageException) and the entity declared only in
     * the external subset must never resolve. The DTD entity carries an INTERNAL literal value
     * (not a SYSTEM reference), so a leak depends only on the external subset being loaded and
     * nothing else -- a different, sharper control than the external-general-entity vector
     * XMLBatchAdaptorXxeTest.xxeEntityIsNotResolved already pins.
     * <p>
     * If the current source instead throws here, this test does not paper over that with a
     * try/catch: a thrown BatchMessageException would contradict this plan's D-04 semantics and
     * the planning-time empirical run, and must be surfaced as a finding rather than silently
     * accepted.
     */
    @Test
    public void externalDtdSubsetIsNotLoadedAndBatchStillSplits() throws Exception {
        File dtdFile = temporaryFolder.newFile("xmlbatch-external-subset.dtd");
        FileUtils.write(dtdFile, "<!ENTITY x \"" + CANARY + "\">", "UTF-8");
        String dtdUri = dtdFile.toURI().toString();

        String batchXml = "<?xml version=\"1.0\"?><!DOCTYPE batch SYSTEM \"" + dtdUri + "\">"
                + "<batch><message>&x;</message></batch>";

        XMLBatchProperties properties = new XMLBatchProperties();
        properties.setSplitType(SplitType.Element_Name);
        properties.setElementName("message");

        List<String> messages = readAll(newAdaptor(batchXml, properties));

        assertEquals("a SYSTEM-DOCTYPE batch must still split under D-04 semantics", 1, messages.size());
        assertFalse("the entity declared only in the unloaded external subset must not resolve into "
                + "the split message: " + messages.get(0), messages.get(0).contains(CANARY));
        assertTrue("the split message must still be the message element (serialized empty since "
                + "the undeclared entity resolves to nothing): " + messages.get(0),
                messages.get(0).startsWith("<message"));
    }

    private static BatchAdaptor newAdaptor(String batchXml, XMLBatchProperties properties) throws Exception {
        SourceConnector sourceConnector = mock(SourceConnector.class);

        SerializerProperties serializerProperties = mock(SerializerProperties.class);
        when(serializerProperties.getBatchProperties()).thenReturn(properties);

        XMLBatchAdaptorFactory factory = new XMLBatchAdaptorFactory(sourceConnector, serializerProperties);
        BatchRawMessage batchRawMessage = new BatchRawMessage(new BatchMessageReader(batchXml));

        return factory.createBatchAdaptor(batchRawMessage);
    }

    private static List<String> readAll(BatchAdaptor adaptor) throws Exception {
        List<String> messages = new ArrayList<String>();
        String message;
        while ((message = adaptor.getMessage()) != null) {
            messages.add(message.trim());
        }
        return messages;
    }
}
