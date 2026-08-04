/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

import com.mirth.connect.util.JavaScriptSharedUtil.ExprPart;

public class JavaScriptSharedUtilTest {

    @Test
    public void testPrettyPrint() {
        String script = "for (var i = 0; i < getArrayOrXmlLength(msg['OBR']); i++) {for (var j = 0; j < getArrayOrXmlLength(msg['OBR'][i]['OBR.3']); j++) {if (typeof(tmp) == 'xml') {if (typeof(tmp['OBR'][i]) == 'undefined') {createSegment('OBR', tmp, i);}if (typeof(tmp['OBR'][i]['OBR.3'][j]) == 'undefined') {createSegment('OBR.3', tmp['OBR'][i], j);}} else {if (typeof(tmp) == 'undefined') {tmp = {};}if (typeof(tmp['OBR']) == 'undefined') {tmp['OBR'] = [];}if (typeof(tmp['OBR'][i]) == 'undefined') {tmp['OBR'][i] = {};}if (typeof(tmp['OBR'][i]['OBR.3']) == 'undefined') {tmp['OBR'][i]['OBR.3'] = [];}if (typeof(tmp['OBR'][i]['OBR.3'][j]) == 'undefined') {tmp['OBR'][i]['OBR.3'][j] = {};}if (typeof(tmp['OBR'][i]['OBR.3'][j]['OBR.3.1']) == 'undefined') {tmp['OBR'][i]['OBR.3'][j]['OBR.3.1'] = {};}}tmp['OBR'][i]['OBR.3'][j]['OBR.3.1']['OBR.3.1.1'] = validate(msg['OBR'][i]['OBR.3'][j]['OBR.3.1']['OBR.3.1.1'].toString(), '', new Array());}}";

        // @formatter:off
        String expected =
            "for (var i = 0; i < getArrayOrXmlLength(msg['OBR']); i++) {\n"+
            "    for (var j = 0; j < getArrayOrXmlLength(msg['OBR'][i]['OBR.3']); j++) {\n"+
            "        if (typeof(tmp) == 'xml') {\n"+
            "            if (typeof(tmp['OBR'][i]) == 'undefined') {\n"+
            "                createSegment('OBR', tmp, i);\n"+
            "            }\n"+
            "            if (typeof(tmp['OBR'][i]['OBR.3'][j]) == 'undefined') {\n"+
            "                createSegment('OBR.3', tmp['OBR'][i], j);\n"+
            "            }\n"+
            "        } else {\n"+
            "            if (typeof(tmp) == 'undefined') {\n"+
            "                tmp = {};\n"+
            "            }\n"+
            "            if (typeof(tmp['OBR']) == 'undefined') {\n"+
            "                tmp['OBR'] = [];\n"+
            "            }\n"+
            "            if (typeof(tmp['OBR'][i]) == 'undefined') {\n"+
            "                tmp['OBR'][i] = {};\n"+
            "            }\n"+
            "            if (typeof(tmp['OBR'][i]['OBR.3']) == 'undefined') {\n"+
            "                tmp['OBR'][i]['OBR.3'] = [];\n"+
            "            }\n"+
            "            if (typeof(tmp['OBR'][i]['OBR.3'][j]) == 'undefined') {\n"+
            "                tmp['OBR'][i]['OBR.3'][j] = {};\n"+
            "            }\n"+
            "            if (typeof(tmp['OBR'][i]['OBR.3'][j]['OBR.3.1']) == 'undefined') {\n"+
            "                tmp['OBR'][i]['OBR.3'][j]['OBR.3.1'] = {};\n"+
            "            }\n"+
            "        }\n"+
            "        tmp['OBR'][i]['OBR.3'][j]['OBR.3.1']['OBR.3.1.1'] = validate(msg['OBR'][i]['OBR.3'][j]['OBR.3.1']['OBR.3.1.1'].toString(), '', new Array());\n"+
            "    }\n"+
            "}\n";
        // @formatter:on

        assertEquals(expected.trim(), JavaScriptSharedUtil.prettyPrint(script).trim());
    }

    @Test
    public void testPrettyPrintWithE4X() {
        String script = "var _results = Lists.list();\nfor (var i = 0; i < getArrayOrXmlLength(msg['OBX']); i++) {\n\ntFactory = Packages.javax.xml.transform.TransformerFactory.newInstance();\nxsltTemplate = new Packages.java.io.StringReader(<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">    <xsl:template match=\"/\">        <values>            <xsl:for-each select=\"OBX/OBX.5\">                <value><xsl:value-of select=\"OBX.5.1\"/></value>            </xsl:for-each>        </values>    </xsl:template></xsl:stylesheet>);\ntransformer = tFactory.newTransformer(new Packages.javax.xml.transform.stream.StreamSource(xsltTemplate));\nsourceVar = new Packages.java.io.StringReader(msg['OBX'][i]);\nresultVar = new Packages.java.io.StringWriter();\ntransformer.transform(new Packages.javax.xml.transform.stream.StreamSource(sourceVar), new Packages.javax.xml.transform.stream.StreamResult(resultVar));\n_results.add(resultVar.toString());\n\n\n}\nchannelMap.put('results', _results.toArray());";

        // @formatter:off
        String expected = 
            "var _results = Lists.list();\n"+
            "for (var i = 0; i < getArrayOrXmlLength(msg['OBX']); i++) {\n"+
            "\n"+
            "    tFactory = Packages.javax.xml.transform.TransformerFactory.newInstance();\n"+
            "    xsltTemplate = new Packages.java.io.StringReader(<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">    <xsl:template match=\"/\">        <values>            <xsl:for-each select=\"OBX/OBX.5\">                <value><xsl:value-of select=\"OBX.5.1\"/></value>            </xsl:for-each>        </values>    </xsl:template></xsl:stylesheet>);\n"+
            "    transformer = tFactory.newTransformer(new Packages.javax.xml.transform.stream.StreamSource(xsltTemplate));\n"+
            "    sourceVar = new Packages.java.io.StringReader(msg['OBX'][i]);\n"+
            "    resultVar = new Packages.java.io.StringWriter();\n"+
            "    transformer.transform(new Packages.javax.xml.transform.stream.StreamSource(sourceVar), new Packages.javax.xml.transform.stream.StreamResult(resultVar));\n"+
            "    _results.add(resultVar.toString());\n"+
            "\n"+
            "\n"+
            "}\n"+
            "channelMap.put('results', _results.toArray());\n";
        // @formatter:on

        assertEquals(expected.trim(), JavaScriptSharedUtil.prettyPrint(script).trim());
    }

    @Test
    public void testPrettyPrintWithE4XAndProlog() {
        String script = "var _results = Lists.list();\nfor (var i = 0; i < getArrayOrXmlLength(msg['OBX']); i++) {\n\n    tFactory = Packages.javax.xml.transform.TransformerFactory.newInstance();\n    xsltTemplate = new Packages.java.io.StringReader(<?xml version=\"1.0\" encoding=\"UTF-8\"?> <xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">    <xsl:template match=\"/\">        <values>            <xsl:for-each select=\"OBX/OBX.5\">                <value><xsl:value-of select=\"OBX.5.1\"/></value>            </xsl:for-each>        </values>    </xsl:template></xsl:stylesheet>);\n    transformer = tFactory.newTransformer(new Packages.javax.xml.transform.stream.StreamSource(xsltTemplate));\n    sourceVar = new Packages.java.io.StringReader(msg['OBX'][i]);\n    resultVar = new Packages.java.io.StringWriter();\n    transformer.transform(new Packages.javax.xml.transform.stream.StreamSource(sourceVar), new Packages.javax.xml.transform.stream.StreamResult(resultVar));\n    _results.add(resultVar.toString());\n\n\n}\nchannelMap.put('results', _results.toArray());";

        // @formatter:off
        String expected = 
            "var _results = Lists.list();\n"+
            "for (var i = 0; i < getArrayOrXmlLength(msg['OBX']); i++) {\n"+
            "\n"+
            "    tFactory = Packages.javax.xml.transform.TransformerFactory.newInstance();\n"+
            "    xsltTemplate = new Packages.java.io.StringReader(<?xml version=\"1.0\" encoding=\"UTF-8\"?> <xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">    <xsl:template match=\"/\">        <values>            <xsl:for-each select=\"OBX/OBX.5\">                <value><xsl:value-of select=\"OBX.5.1\"/></value>            </xsl:for-each>        </values>    </xsl:template></xsl:stylesheet>);\n"+
            "    transformer = tFactory.newTransformer(new Packages.javax.xml.transform.stream.StreamSource(xsltTemplate));\n"+
            "    sourceVar = new Packages.java.io.StringReader(msg['OBX'][i]);\n"+
            "    resultVar = new Packages.java.io.StringWriter();\n"+
            "    transformer.transform(new Packages.javax.xml.transform.stream.StreamSource(sourceVar), new Packages.javax.xml.transform.stream.StreamResult(resultVar));\n"+
            "    _results.add(resultVar.toString());\n"+
            "\n"+
            "\n"+
            "}\n"+
            "channelMap.put('results', _results.toArray());\n";
        // @formatter:on

        assertEquals(expected.trim(), JavaScriptSharedUtil.prettyPrint(script).trim());
    }

    @Test
    public void testRemoveNumberLiterals1() {
        String expression = "msg['OBR'][0]['OBR.3'][1]['OBR.3.1'].toString()";
        assertEquals("msg['OBR']['OBR.3']['OBR.3.1'].toString()", JavaScriptSharedUtil.removeNumberLiterals(expression));
    }

    @Test
    public void testRemoveNumberLiterals2() {
        String expression = "msg.OBR[0].*::['OBR.3'][1].ns::['OBR.3.1'][test].toString()";
        assertEquals("msg.OBR.*::['OBR.3'].ns::['OBR.3.1'][test].toString()", JavaScriptSharedUtil.removeNumberLiterals(expression));
    }

    @Test
    public void testGetExpressionParts1() {
        String expression = "msg['OBR'][0]['OBR.3'][1]['OBR.3.1']";

        List<ExprPart> expected = new ArrayList<ExprPart>();
        expected.add(new ExprPart("msg", "msg"));
        expected.add(new ExprPart("['OBR']", "'OBR'"));
        expected.add(new ExprPart("[0]", "0", true));
        expected.add(new ExprPart("['OBR.3']", "'OBR.3'"));
        expected.add(new ExprPart("[1]", "1", true));
        expected.add(new ExprPart("['OBR.3.1']", "'OBR.3.1'"));

        assertArrayEquals(expected.toArray(), JavaScriptSharedUtil.getExpressionParts(expression).toArray());
    }

    @Test
    public void testGetExpressionParts2() {
        String expression = "msg.OBR[0].*::['OBR.3'][1].ns::['OBR.3.1'][test]";

        List<ExprPart> expected = new ArrayList<ExprPart>();
        expected.add(new ExprPart("msg", "msg"));
        expected.add(new ExprPart(".OBR", "OBR"));
        expected.add(new ExprPart("[0]", "0", true));
        expected.add(new ExprPart(".*::['OBR.3']", "'OBR.3'"));
        expected.add(new ExprPart("[1]", "1", true));
        expected.add(new ExprPart(".ns::['OBR.3.1']", "'OBR.3.1'"));
        expected.add(new ExprPart("[test]", "test"));

        assertArrayEquals(expected.toArray(), JavaScriptSharedUtil.getExpressionParts(expression).toArray());
    }

    @Test
    public void testGetExpressionParts3() {
        assertArrayEquals(new Object[0], JavaScriptSharedUtil.getExpressionParts("  ").toArray());
    }

    @Test
    public void testGetExpressionParts4() {
        String expression = "#@$%.^&*(";

        List<ExprPart> expected = new ArrayList<ExprPart>();
        expected.add(new ExprPart("#@$%.^&*(", "#@$%.^&*("));

        assertArrayEquals(expected.toArray(), JavaScriptSharedUtil.getExpressionParts(expression).toArray());
    }

    // ===== Rhino 1.7.15.1 compatibility tests (D-07) =====

    @Test
    public void testContextEnterExit() {
        Context cx = Context.enter();
        try {
            assertNotNull(cx);
            Scriptable scope = cx.initStandardObjects();
            assertNotNull(scope);
        } finally {
            Context.exit();
        }
    }

    @Test
    public void testJsonParseSerialization() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Object result = cx.evaluateString(scope, "JSON.stringify({a:1})", "test", 1, null);
            assertEquals("{\"a\":1}", Context.toString(result));
        } finally {
            Context.exit();
        }
    }

    @Test
    public void testScriptCompilationCache() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Script script = cx.compileString("1 + 1", "test", 1, null);
            Object r1 = script.exec(cx, scope);
            Object r2 = script.exec(cx, scope);
            assertEquals(Context.toNumber(r1), Context.toNumber(r2), 0.0);
        } finally {
            Context.exit();
        }
    }

    // ===== validateScriptStructured (IRT-1514) =====

    @Test
    public void testValidateScriptStructuredValid() {
        ScriptValidationResult result = JavaScriptSharedUtil.validateScriptStructured("var x = 1;\nlogger.info(x);");
        assertTrue(result.isValid());
        assertNull(result.getError());
    }

    @Test
    public void testValidateScriptStructuredBlankIsValid() {
        assertTrue(JavaScriptSharedUtil.validateScriptStructured(null).isValid());
        assertTrue(JavaScriptSharedUtil.validateScriptStructured("").isValid());
        assertTrue(JavaScriptSharedUtil.validateScriptStructured("   ").isValid());
    }

    @Test
    public void testValidateScriptStructuredSyntaxErrorOnFirstLine() {
        ScriptValidationResult result = JavaScriptSharedUtil.validateScriptStructured("var x = ;");
        assertFalse(result.isValid());
        assertNotNull(result.getError());
        assertEquals(1, result.getError().getLine());
        // Column is normalized to the caller's script; the internal wrapper-prefix offset must
        // already be subtracted out, so it must never be negative.
        assertTrue(result.getError().getColumn() >= 0);
        assertNotNull(result.getError().getMessage());
    }

    @Test
    public void testValidateScriptStructuredSyntaxErrorOnSecondLine() {
        ScriptValidationResult result = JavaScriptSharedUtil.validateScriptStructured("var x = 1;\nvar y = ;");
        assertFalse(result.isValid());
        assertNotNull(result.getError());
        // Only line 1 carries the wrapper-prefix offset; line 2's column should be reported as-is.
        assertEquals(2, result.getError().getLine());
        assertTrue(result.getError().getColumn() >= 0);
    }

    // ===== prettyPrint concurrency (regression test for 9f98a00fb) =====

    @Test
    public void testPrettyPrintConcurrentThreadSafety() throws Exception {
        /*
         * prettyPrint() runs js_beautify against a cached, static Rhino formatter scope
         * (cachedFormatterScope). Before 9f98a00fb, concurrent callers (e.g. concurrent
         * _prettyPrintScript REST requests on separate Jetty threads) could race on that shared
         * mutable state. This drives many concurrent callers with two distinct scripts and asserts
         * every result matches a known-good single-threaded reference - if the shared state were
         * ever corrupted by another thread mid-format, a call would return the wrong script's
         * (or a mangled) result.
         */
        String scriptA = "for(var i=0;i<10;i++){if(i>5){logger.info('a');}}";
        String scriptB = "var x={a:1,b:2};function foo(y){return y+1;}";

        String expectedA = JavaScriptSharedUtil.prettyPrint(scriptA);
        String expectedB = JavaScriptSharedUtil.prettyPrint(scriptB);
        assertNotNull(expectedA);
        assertNotNull(expectedB);

        int threadCount = 16;
        int iterationsPerThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();

        try {
            for (int i = 0; i < threadCount; i++) {
                final boolean useA = i % 2 == 0;
                futures.add(executor.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        String script = useA ? scriptA : scriptB;
                        String expected = useA ? expectedA : expectedB;
                        for (int j = 0; j < iterationsPerThread; j++) {
                            if (!expected.equals(JavaScriptSharedUtil.prettyPrint(script))) {
                                return false;
                            }
                        }
                        return true;
                    }
                }));
            }

            for (Future<Boolean> future : futures) {
                assertTrue("Concurrent prettyPrint call returned a corrupted/mismatched result", future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testJavaInterop() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            // Use a Java String literal (not a JS number) to avoid float coercion (42 → "42.0")
            Object result = cx.evaluateString(scope,
                "java.lang.String.valueOf('hello')", "test", 1, null);
            assertEquals("hello", Context.toString(result));
        } finally {
            Context.exit();
        }
    }
}