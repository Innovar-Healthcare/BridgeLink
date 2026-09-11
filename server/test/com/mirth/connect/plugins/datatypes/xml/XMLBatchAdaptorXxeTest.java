/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.datatypes.xml;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileWriter;
import java.util.UUID;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.velocity.runtime.RuntimeConstants;
import org.junit.BeforeClass;
import org.junit.Test;

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
 * CVE-2026-82578: XMLBatchAdaptor.getMessageFromReader() historically handed the raw inbound
 * batch stream straight to xpath.evaluate(query, new InputSource(bufferedReader), NODESET), whose
 * internal DocumentBuilder had no hardening at all - a classic XXE. These tests prove (a) an
 * external-entity payload embedded in the batch DOCTYPE is not resolved into the split message,
 * and (b) a batch that only declares an internal DTD subset (no external reference) still parses
 * and splits normally, per the D-04 divergence: this fix must NOT set disallow-doctype-decl.
 */
public class XMLBatchAdaptorXxeTest {

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

    @Test
    public void xxeEntityIsNotResolved() throws Exception {
        File sentinelFile = File.createTempFile("xmlbatchadaptor-xxe-sentinel", ".txt");
        sentinelFile.deleteOnExit();
        String sentinelContent = "XXE-SENTINEL-" + UUID.randomUUID().toString().replace("-", "");
        try (FileWriter writer = new FileWriter(sentinelFile)) {
            writer.write(sentinelContent);
        }

        String batchXml = "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"" + sentinelFile.toURI() + "\">]>"
                + "<root><record>&xxe;</record></root>";

        String message = splitFirstMessage(batchXml, "/root/record");

        assertNotNull("the batch must still split into a message even when the external entity is blocked", message);
        assertFalse("external entity content must not be resolved into the split message", message.contains(sentinelContent));
    }

    @Test
    public void internalSubsetDoctypeStillParsesAndSplits() throws Exception {
        String batchXml = "<!DOCTYPE root [<!ENTITY greeting \"Hello, Clinical System\">]>"
                + "<root><record>First</record><record>Second</record></root>";

        String firstMessage = splitFirstMessage(batchXml, "/root/record");

        assertNotNull("a batch declaring only an internal DTD subset must still parse and split", firstMessage);
        assertTrue("the split message must contain the expected record content", firstMessage.contains("First"));
    }

    private String splitFirstMessage(String batchXml, String xpathQuery) throws Exception {
        SourceConnector sourceConnector = mock(SourceConnector.class);

        XMLBatchProperties batchProperties = new XMLBatchProperties();
        batchProperties.setSplitType(SplitType.XPath_Query);
        batchProperties.setQuery(xpathQuery);

        SerializerProperties serializerProperties = mock(SerializerProperties.class);
        when(serializerProperties.getBatchProperties()).thenReturn(batchProperties);

        XMLBatchAdaptorFactory factory = new XMLBatchAdaptorFactory(sourceConnector, serializerProperties);
        BatchRawMessage batchRawMessage = new BatchRawMessage(new BatchMessageReader(batchXml));
        BatchAdaptor adaptor = factory.createBatchAdaptor(batchRawMessage);

        return adaptor.getMessage();
    }
}
