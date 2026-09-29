/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.model.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.client.core.Version;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.MapContent;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.InvalidChannel;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.Xpp3Driver;
import com.thoughtworks.xstream.security.AnyTypePermission;

public class ObjectXMLSerializerTest {

    @BeforeClass
    public static void setup() throws Exception {
        try {
            ObjectXMLSerializer.getInstance().init(Version.getLatest().toString());
        } catch (Exception e) {
            // Ignore if it has already been initialized
        }
    }

    // CVE-01 / D-29.1: < 3.5.0 XML-path old-format channel regression assertion, the XML-path
    // sibling of ObjectJSONSerializerTest.testDeserializeOldChannel. Deserializing a channel
    // rooted below schema 3.5.0 must invoke Channel.migrate3_5_0() and produce a real Channel,
    // not an InvalidChannel: the removal side (a stale DOM child-element cache raises
    // UnknownFieldException on the removed codeTemplateLibraries field and downgrades the
    // result) and the addition side (a stale cache makes the newly-added exportData element
    // invisible and this dereference NPEs) must both succeed. Green here on the shipped
    // xstream 1.4.20 is expected -- 1.4.20's DomReader eagerly rebuilds its child cache on
    // every reassignCurrentElement() call, so this seam cannot be broken until the 1.4.21
    // bump. This test's falsifiability proof is plan 02's responsibility: revert
    // MirthDomReader on the 1.4.21 jars and confirm both this test and
    // ObjectJSONSerializerTest.testDeserializeOldChannel go red, then restore.
    //
    // What would make this green for the wrong reason, and how that is closed: crediting
    // XStreamSeamTest.knownOldFormatChannelXmlDeserializes instead -- its fixture is rooted at
    // schema 3.6.0, so MigratableConverter.migrateElement() never invokes migrate3_5_0() for
    // it and it cannot detect this regression (Pitfall 4). This assertion exists on the
    // ObjectXMLSerializer path specifically because that suite's coverage is JSON-only.
    @Test
    public void testDeserializeOldFormatChannelXmlBelow3_5_0() throws Exception {
        String xml;
        // try-with-resources + an explicit charset: IOUtils.toString(InputStream) is deprecated and
        // decodes with the platform default charset, and the stream was never closed. The null check
        // matters because getResourceAsStream returns null if the fixture is not on the test
        // classpath, which would otherwise surface as a bare NPE inside IOUtils naming nothing.
        try (InputStream in = ObjectXMLSerializerTest.class.getResourceAsStream("legacy-migration-3-4-channel.xml")) {
            assertNotNull("fixture legacy-migration-3-4-channel.xml missing from the test classpath "
                    + "(server/build.xml's test-compile must copy **/*.xml into ${test_classes})", in);
            xml = IOUtils.toString(in, StandardCharsets.UTF_8);
        }

        Channel channel = ObjectXMLSerializer.getInstance().deserialize(xml, Channel.class);

        // Removal side: a stale child-element cache raises UnknownFieldException on the
        // removed codeTemplateLibraries field and downgrades deserialization to InvalidChannel.
        // (There is deliberately no `assertTrue(channel instanceof Channel)` here: `channel` is
        // declared Channel, so that assertion can never fail and asserts nothing. InvalidChannel
        // extends Channel, so this assertFalse is the real type check.)
        assertFalse("channel must not degrade to InvalidChannel", channel instanceof InvalidChannel);
        assertEquals("00000012-0000-0000-0000-000000000012", channel.getId());
        // Addition side: a stale child-element cache makes the newly-added exportData element
        // invisible to xstream, and this dereference NPEs.
        assertNotNull(channel.getExportData());
        assertNotNull(channel.getExportData().getMetadata());
        assertNotNull(channel.getExportData().getMetadata().getPruningSettings());
        assertTrue(channel.getExportData().getMetadata().getPruningSettings().isArchiveEnabled());
    }

    @Test
    public void testInvalidMapContent() throws Exception {
        ConnectorMessage connectorMessage = new ConnectorMessage();
        Map<String, Object> map = new HashMap<String, Object>();
        // Manually allow all types here to deserialize an invalid value
        XStream xstream = new XStream(new Xpp3Driver());
        xstream.addPermission(AnyTypePermission.ANY);
        map.put("key", xstream.fromXML(CACHED_ROW_SET_XML));
        connectorMessage.setChannelMapContent(new MapContent(map, true));

        // Shouldn't cause any errors
        String xml = ObjectXMLSerializer.getInstance().serialize(connectorMessage);
        ObjectXMLSerializer.getInstance().deserialize(xml, ConnectorMessage.class);
    }

    @Test
    public void testDisallowedTypes() throws Exception {
        // Internal server classes
        testDisallowedType("com.mirth.connect.server.Mirth");
        testDisallowedType("com.mirth.connect.server.builders.JavaScriptBuilder");
        testDisallowedType("com.mirth.connect.server.tools.ScriptRunner");

        // Internal Java classes
        testDisallowedType("java.lang.Thread");
        testDisallowedType("java.lang.ProcessBuilder");
        testDisallowedType("java.io.InputStream");
        testDisallowedType("java.nio.channels.Channel");
        testDisallowedType("javax.activation.DataSource");
        testDisallowedType("javax.sql.rowset.BaseRowSet");
        testDisallowedType("javax.mail.internet.MimeMessage");

        // Third-party libraries
        testDisallowedType("org.apache.commons.io.IO");
        testDisallowedType("org.eclipse.jetty.server.HttpConnectionFactory");
        testDisallowedType("software.amazon.awssdk.services.s3.model.CopyObjectRequest");
    }

    private void testDisallowedType(String className) {
        try {
            // Should throw an exception
            ObjectXMLSerializer.getInstance().deserialize("<" + className + "/>", Class.forName(className));
            fail("Deserializing " + className + " should have failed, but didn't.");
        } catch (Exception ignore) {
        }
    }

    // @formatter:off
    private static final String CACHED_ROW_SET_XML = 
    		"<com.mirth.connect.server.userutil.MirthCachedRowSet>\n" + 
    		"  <delegate class=\"com.sun.rowset.CachedRowSetImpl\" serialization=\"custom\">\n" + 
    		"    <javax.sql.rowset.BaseRowSet>\n" + 
    		"      <default>\n" + 
    		"        <concurrency>1008</concurrency>\n" + 
    		"        <escapeProcessing>true</escapeProcessing>\n" + 
    		"        <fetchDir>1000</fetchDir>\n" + 
    		"        <fetchSize>0</fetchSize>\n" + 
    		"        <isolation>2</isolation>\n" + 
    		"        <maxFieldSize>0</maxFieldSize>\n" + 
    		"        <maxRows>0</maxRows>\n" + 
    		"        <queryTimeout>0</queryTimeout>\n" + 
    		"        <readOnly>true</readOnly>\n" + 
    		"        <rowSetType>1004</rowSetType>\n" + 
    		"        <showDeleted>false</showDeleted>\n" + 
    		"        <listeners/>\n" + 
    		"        <params/>\n" + 
    		"      </default>\n" + 
    		"    </javax.sql.rowset.BaseRowSet>\n" + 
    		"    <com.sun.rowset.CachedRowSetImpl>\n" + 
    		"      <default>\n" + 
    		"        <absolutePos>0</absolutePos>\n" + 
    		"        <callWithCon>false</callWithCon>\n" + 
    		"        <currentRow>0</currentRow>\n" + 
    		"        <cursorPos>0</cursorPos>\n" + 
    		"        <dbmslocatorsUpdateCopy>false</dbmslocatorsUpdateCopy>\n" + 
    		"        <endPos>0</endPos>\n" + 
    		"        <iMatchColumn>-1</iMatchColumn>\n" + 
    		"        <lastValueNull>false</lastValueNull>\n" + 
    		"        <maxRowsreached>0</maxRowsreached>\n" + 
    		"        <numDeleted>0</numDeleted>\n" + 
    		"        <numRows>0</numRows>\n" + 
    		"        <onFirstPage>false</onFirstPage>\n" + 
    		"        <onInsertRow>false</onInsertRow>\n" + 
    		"        <onLastPage>false</onLastPage>\n" + 
    		"        <pageSize>0</pageSize>\n" + 
    		"        <pagenotend>true</pagenotend>\n" + 
    		"        <populatecallcount>0</populatecallcount>\n" + 
    		"        <prevEndPos>0</prevEndPos>\n" + 
    		"        <startPos>0</startPos>\n" + 
    		"        <startPrev>0</startPrev>\n" + 
    		"        <tXWriter>true</tXWriter>\n" + 
    		"        <totalRows>0</totalRows>\n" + 
    		"        <updateOnInsert>false</updateOnInsert>\n" + 
    		"        <DEFAULT__SYNC__PROVIDER>com.sun.rowset.providers.RIOptimisticProvider</DEFAULT__SYNC__PROVIDER>\n" + 
    		"        <iMatchColumns>\n" + 
    		"          <int>-1</int>\n" + 
    		"          <int>-1</int>\n" + 
    		"          <int>-1</int>\n" + 
    		"          <int>-1</int>\n" + 
    		"          <int>-1</int>\n" + 
    		"          <int>-1</int>\n" + 
    		"          <int>-1</int>\n" + 
    		"          <int>-1</int>\n" + 
    		"          <int>-1</int>\n" + 
    		"          <int>-1</int>\n" + 
    		"        </iMatchColumns>\n" + 
    		"        <provider class=\"com.sun.rowset.providers.RIOptimisticProvider\" serialization=\"custom\">\n" + 
    		"          <unserializable-parents/>\n" + 
    		"          <com.sun.rowset.providers.RIOptimisticProvider>\n" + 
    		"            <default>\n" + 
    		"              <providerID>com.sun.rowset.providers.RIOptimisticProvider</providerID>\n" + 
    		"              <reader serialization=\"custom\">\n" + 
    		"                <com.sun.rowset.internal.CachedRowSetReader>\n" + 
    		"                  <default>\n" + 
    		"                    <startPosition>0</startPosition>\n" + 
    		"                    <userCon>false</userCon>\n" + 
    		"                    <writerCalls>0</writerCalls>\n" + 
    		"                    <resBundle/>\n" + 
    		"                  </default>\n" + 
    		"                </com.sun.rowset.internal.CachedRowSetReader>\n" + 
    		"              </reader>\n" + 
    		"              <resBundle/>\n" + 
    		"              <vendorName>Oracle Corporation</vendorName>\n" + 
    		"              <versionNumber>1.0</versionNumber>\n" + 
    		"              <writer serialization=\"custom\">\n" + 
    		"                <com.sun.rowset.internal.CachedRowSetWriter>\n" + 
    		"                  <default>\n" + 
    		"                    <callerColumnCount>0</callerColumnCount>\n" + 
    		"                    <iChangedValsInDbAndCRS>0</iChangedValsInDbAndCRS>\n" + 
    		"                    <iChangedValsinDbOnly>0</iChangedValsinDbOnly>\n" + 
    		"                    <reader serialization=\"custom\">\n" + 
    		"                      <com.sun.rowset.internal.CachedRowSetReader>\n" + 
    		"                        <default>\n" + 
    		"                          <startPosition>0</startPosition>\n" + 
    		"                          <userCon>false</userCon>\n" + 
    		"                          <writerCalls>0</writerCalls>\n" + 
    		"                          <resBundle/>\n" + 
    		"                        </default>\n" + 
    		"                      </com.sun.rowset.internal.CachedRowSetReader>\n" + 
    		"                    </reader>\n" + 
    		"                    <resBundle/>\n" + 
    		"                  </default>\n" + 
    		"                </com.sun.rowset.internal.CachedRowSetWriter>\n" + 
    		"              </writer>\n" + 
    		"            </default>\n" + 
    		"          </com.sun.rowset.providers.RIOptimisticProvider>\n" + 
    		"        </provider>\n" + 
    		"        <rowSetReader class=\"com.sun.rowset.internal.CachedRowSetReader\" serialization=\"custom\">\n" + 
    		"          <com.sun.rowset.internal.CachedRowSetReader>\n" + 
    		"            <default>\n" + 
    		"              <startPosition>0</startPosition>\n" + 
    		"              <userCon>false</userCon>\n" + 
    		"              <writerCalls>0</writerCalls>\n" + 
    		"              <resBundle/>\n" + 
    		"            </default>\n" + 
    		"          </com.sun.rowset.internal.CachedRowSetReader>\n" + 
    		"        </rowSetReader>\n" + 
    		"        <rowSetWriter class=\"com.sun.rowset.internal.CachedRowSetWriter\" serialization=\"custom\">\n" + 
    		"          <com.sun.rowset.internal.CachedRowSetWriter>\n" + 
    		"            <default>\n" + 
    		"              <callerColumnCount>0</callerColumnCount>\n" + 
    		"              <iChangedValsInDbAndCRS>0</iChangedValsInDbAndCRS>\n" + 
    		"              <iChangedValsinDbOnly>0</iChangedValsinDbOnly>\n" + 
    		"              <reader serialization=\"custom\">\n" + 
    		"                <com.sun.rowset.internal.CachedRowSetReader>\n" + 
    		"                  <default>\n" + 
    		"                    <startPosition>0</startPosition>\n" + 
    		"                    <userCon>false</userCon>\n" + 
    		"                    <writerCalls>0</writerCalls>\n" + 
    		"                    <resBundle/>\n" + 
    		"                  </default>\n" + 
    		"                </com.sun.rowset.internal.CachedRowSetReader>\n" + 
    		"              </reader>\n" + 
    		"              <resBundle/>\n" + 
    		"            </default>\n" + 
    		"          </com.sun.rowset.internal.CachedRowSetWriter>\n" + 
    		"        </rowSetWriter>\n" + 
    		"        <rowsetWarning>\n" + 
    		"          <stackTrace>\n" + 
    		"            <trace>com.sun.rowset.CachedRowSetImpl.&lt;init&gt;(CachedRowSetImpl.java:384)</trace>\n" + 
    		"            <trace>com.sun.rowset.RowSetFactoryImpl.createCachedRowSet(RowSetFactoryImpl.java:49)</trace>\n" + 
    		"            <trace>com.mirth.connect.server.userutil.MirthCachedRowSet.&lt;init&gt;(MirthCachedRowSet.java:57)</trace>\n" + 
    		"            <trace>com.mirth.connect.model.converters.ObjectXMLSerializerTest.myTest(ObjectXMLSerializerTest.java:48)</trace>\n" + 
    		"            <trace>sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)</trace>\n" + 
    		"            <trace>sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)</trace>\n" + 
    		"            <trace>sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)</trace>\n" + 
    		"            <trace>java.lang.reflect.Method.invoke(Method.java:498)</trace>\n" + 
    		"            <trace>org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:44)</trace>\n" + 
    		"            <trace>org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:15)</trace>\n" + 
    		"            <trace>org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:41)</trace>\n" + 
    		"            <trace>org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:20)</trace>\n" + 
    		"            <trace>org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:76)</trace>\n" + 
    		"            <trace>org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:50)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner$3.run(ParentRunner.java:193)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:52)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner.runChildren(ParentRunner.java:191)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner.access$000(ParentRunner.java:42)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:184)</trace>\n" + 
    		"            <trace>org.junit.internal.runners.statements.RunBefores.evaluate(RunBefores.java:28)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner.run(ParentRunner.java:236)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit4.runner.JUnit4TestReference.run(JUnit4TestReference.java:89)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit.runner.TestExecution.run(TestExecution.java:41)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:542)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:770)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.run(RemoteTestRunner.java:464)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.main(RemoteTestRunner.java:210)</trace>\n" + 
    		"          </stackTrace>\n" + 
    		"          <suppressedExceptions class=\"java.util.Collections$UnmodifiableRandomAccessList\" resolves-to=\"java.util.Collections$UnmodifiableList\">\n" + 
    		"            <c class=\"list\"/>\n" + 
    		"            <list/>\n" + 
    		"          </suppressedExceptions>\n" + 
    		"          <vendorCode>0</vendorCode>\n" + 
    		"        </rowsetWarning>\n" + 
    		"        <rvh/>\n" + 
    		"        <sqlwarn>\n" + 
    		"          <stackTrace>\n" + 
    		"            <trace>com.sun.rowset.CachedRowSetImpl.&lt;init&gt;(CachedRowSetImpl.java:383)</trace>\n" + 
    		"            <trace>com.sun.rowset.RowSetFactoryImpl.createCachedRowSet(RowSetFactoryImpl.java:49)</trace>\n" + 
    		"            <trace>com.mirth.connect.server.userutil.MirthCachedRowSet.&lt;init&gt;(MirthCachedRowSet.java:57)</trace>\n" + 
    		"            <trace>com.mirth.connect.model.converters.ObjectXMLSerializerTest.myTest(ObjectXMLSerializerTest.java:48)</trace>\n" + 
    		"            <trace>sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)</trace>\n" + 
    		"            <trace>sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)</trace>\n" + 
    		"            <trace>sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)</trace>\n" + 
    		"            <trace>java.lang.reflect.Method.invoke(Method.java:498)</trace>\n" + 
    		"            <trace>org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:44)</trace>\n" + 
    		"            <trace>org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:15)</trace>\n" + 
    		"            <trace>org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:41)</trace>\n" + 
    		"            <trace>org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:20)</trace>\n" + 
    		"            <trace>org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:76)</trace>\n" + 
    		"            <trace>org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:50)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner$3.run(ParentRunner.java:193)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:52)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner.runChildren(ParentRunner.java:191)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner.access$000(ParentRunner.java:42)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:184)</trace>\n" + 
    		"            <trace>org.junit.internal.runners.statements.RunBefores.evaluate(RunBefores.java:28)</trace>\n" + 
    		"            <trace>org.junit.runners.ParentRunner.run(ParentRunner.java:236)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit4.runner.JUnit4TestReference.run(JUnit4TestReference.java:89)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit.runner.TestExecution.run(TestExecution.java:41)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:542)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:770)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.run(RemoteTestRunner.java:464)</trace>\n" + 
    		"            <trace>org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.main(RemoteTestRunner.java:210)</trace>\n" + 
    		"          </stackTrace>\n" + 
    		"          <suppressedExceptions class=\"java.util.Collections$UnmodifiableRandomAccessList\" resolves-to=\"java.util.Collections$UnmodifiableList\">\n" + 
    		"            <c class=\"list\"/>\n" + 
    		"            <list/>\n" + 
    		"          </suppressedExceptions>\n" + 
    		"          <vendorCode>0</vendorCode>\n" + 
    		"        </sqlwarn>\n" + 
    		"        <strMatchColumn></strMatchColumn>\n" + 
    		"        <strMatchColumns>\n" + 
    		"          <null/>\n" + 
    		"          <null/>\n" + 
    		"          <null/>\n" + 
    		"          <null/>\n" + 
    		"          <null/>\n" + 
    		"          <null/>\n" + 
    		"          <null/>\n" + 
    		"          <null/>\n" + 
    		"          <null/>\n" + 
    		"          <null/>\n" + 
    		"        </strMatchColumns>\n" + 
    		"        <tWriter class=\"com.sun.rowset.internal.CachedRowSetWriter\" serialization=\"custom\">\n" + 
    		"          <com.sun.rowset.internal.CachedRowSetWriter>\n" + 
    		"            <default>\n" + 
    		"              <callerColumnCount>0</callerColumnCount>\n" + 
    		"              <iChangedValsInDbAndCRS>0</iChangedValsInDbAndCRS>\n" + 
    		"              <iChangedValsinDbOnly>0</iChangedValsinDbOnly>\n" + 
    		"              <reader serialization=\"custom\">\n" + 
    		"                <com.sun.rowset.internal.CachedRowSetReader>\n" + 
    		"                  <default>\n" + 
    		"                    <startPosition>0</startPosition>\n" + 
    		"                    <userCon>false</userCon>\n" + 
    		"                    <writerCalls>0</writerCalls>\n" + 
    		"                    <resBundle/>\n" + 
    		"                  </default>\n" + 
    		"                </com.sun.rowset.internal.CachedRowSetReader>\n" + 
    		"              </reader>\n" + 
    		"              <resBundle/>\n" + 
    		"            </default>\n" + 
    		"          </com.sun.rowset.internal.CachedRowSetWriter>\n" + 
    		"        </tWriter>\n" + 
    		"      </default>\n" + 
    		"    </com.sun.rowset.CachedRowSetImpl>\n" + 
    		"  </delegate>\n" + 
    		"</com.mirth.connect.server.userutil.MirthCachedRowSet>";
    // @formatter:on
}
