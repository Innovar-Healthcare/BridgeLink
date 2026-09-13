/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.datatypes.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.donkey.model.message.BatchRawMessage;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.message.batch.BatchMessageException;
import com.mirth.connect.donkey.server.message.batch.BatchMessageReader;
import com.mirth.connect.plugins.datatypes.xml.XMLBatchProperties.SplitType;
import com.mirth.connect.server.controllers.ContextFactoryController;
import com.mirth.connect.server.controllers.ControllerFactory;

/**
 * Regression tests for CVE-2026-82578: the Element Name, Level and XPath Query split modes handed
 * the raw batch to {@code XPath.evaluate(String, InputSource, QName)}, which parses with a default
 * (external entity resolving) parser, so an unauthenticated sender could read server-local files.
 */
public class XMLBatchAdaptorTest {

    private static final String CANARY = "XML_BATCH_XXE_CANARY_2b8d41";
    private static final String BATCH = "<batch><message>1</message><message>2</message><message>3</message></batch>";
    private static final List<String> EXPECTED_MESSAGES = Arrays.asList("<message>1</message>", "<message>2</message>", "<message>3</message>");

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void setup() {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);
        when(controllerFactory.createContextFactoryController()).thenReturn(mock(ContextFactoryController.class));

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);
    }

    // ========== existing split modes keep working ==========

    @Test
    public void testElementNameSplit() throws Exception {
        XMLBatchProperties properties = new XMLBatchProperties();
        properties.setSplitType(SplitType.Element_Name);
        properties.setElementName("message");

        assertEquals(EXPECTED_MESSAGES, readAll(newAdaptor(BATCH, properties)));
    }

    @Test
    public void testLevelSplit() throws Exception {
        XMLBatchProperties properties = new XMLBatchProperties();
        properties.setSplitType(SplitType.Level);
        properties.setLevel(1);

        assertEquals(EXPECTED_MESSAGES, readAll(newAdaptor(BATCH, properties)));
    }

    @Test
    public void testXPathQuerySplit() throws Exception {
        XMLBatchProperties properties = new XMLBatchProperties();
        properties.setSplitType(SplitType.XPath_Query);
        properties.setQuery("/batch/message[position() > 1]");

        assertEquals(EXPECTED_MESSAGES.subList(1, 3), readAll(newAdaptor(BATCH, properties)));
    }

    @Test
    public void testNamespacedElementNameSplit() throws Exception {
        String batch = "<b:batch xmlns:b=\"urn:b\"><b:message>1</b:message><b:message>2</b:message></b:batch>";
        XMLBatchProperties properties = new XMLBatchProperties();
        properties.setSplitType(SplitType.Element_Name);
        properties.setElementName("message");

        List<String> messages = readAll(newAdaptor(batch, properties));

        assertEquals(2, messages.size());
        assertTrue(messages.get(0), messages.get(0).contains(">1</b:message>"));
        assertTrue(messages.get(1), messages.get(1).contains(">2</b:message>"));
    }

    // ========== external entities are not resolved ==========

    @Test
    public void testExternalGeneralEntityIsNotResolved() throws Exception {
        File secret = writeCanaryFile("secret.txt");
        String batch = "<?xml version=\"1.0\"?><!DOCTYPE batch [<!ENTITY x SYSTEM \"" + secret.toURI() + "\">]><batch><message>&x;</message></batch>";

        for (XMLBatchProperties properties : xpathBackedSplitModes()) {
            assertBatchRejectedWithoutLeak(newAdaptor(batch, properties));
        }
    }

    @Test
    public void testExternalDtdIsNotLoaded() throws Exception {
        File secret = writeCanaryFile("secret.txt");
        File dtd = new File(tempFolder.getRoot(), "leak.dtd");
        FileUtils.writeStringToFile(dtd, "<!ENTITY x SYSTEM \"" + secret.toURI() + "\">", StandardCharsets.UTF_8);
        String batch = "<?xml version=\"1.0\"?><!DOCTYPE batch SYSTEM \"" + dtd.toURI() + "\"><batch><message>&x;</message></batch>";

        for (XMLBatchProperties properties : xpathBackedSplitModes()) {
            assertBatchRejectedWithoutLeak(newAdaptor(batch, properties));
        }
    }

    // ========== helpers ==========

    private void assertBatchRejectedWithoutLeak(XMLBatchAdaptor adaptor) {
        try {
            List<String> messages = readAll(adaptor);
            fail("Expected the batch to be rejected, but got: " + messages);
        } catch (BatchMessageException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                assertFalse("external entity was resolved: " + t.getMessage(), String.valueOf(t.getMessage()).contains(CANARY));
            }
        }
    }

    private static List<XMLBatchProperties> xpathBackedSplitModes() {
        XMLBatchProperties elementName = new XMLBatchProperties();
        elementName.setSplitType(SplitType.Element_Name);
        elementName.setElementName("message");

        XMLBatchProperties level = new XMLBatchProperties();
        level.setSplitType(SplitType.Level);
        level.setLevel(1);

        XMLBatchProperties xpath = new XMLBatchProperties();
        xpath.setSplitType(SplitType.XPath_Query);
        xpath.setQuery("/batch/message");

        return Arrays.asList(elementName, level, xpath);
    }

    private static XMLBatchAdaptor newAdaptor(String batch, XMLBatchProperties properties) {
        XMLBatchAdaptorFactory factory = mock(XMLBatchAdaptorFactory.class);
        SourceConnector sourceConnector = mock(SourceConnector.class);
        BatchRawMessage batchRawMessage = new BatchRawMessage(new BatchMessageReader(batch));

        XMLBatchAdaptor adaptor = new XMLBatchAdaptor(factory, sourceConnector, batchRawMessage);
        adaptor.setBatchProperties(properties);
        return adaptor;
    }

    private static List<String> readAll(XMLBatchAdaptor adaptor) throws BatchMessageException {
        List<String> messages = new ArrayList<String>();
        String message;
        while ((message = adaptor.getMessage()) != null) {
            assertNotNull(message);
            messages.add(message.trim());
        }
        return messages;
    }

    private File writeCanaryFile(String name) throws Exception {
        File file = new File(tempFolder.getRoot(), name);
        FileUtils.writeStringToFile(file, CANARY, StandardCharsets.UTF_8);
        return file;
    }
}
