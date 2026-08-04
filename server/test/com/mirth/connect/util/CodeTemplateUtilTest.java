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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.mirth.connect.util.CodeTemplateUtil.CodeTemplateDocumentation;

public class CodeTemplateUtilTest {

    @Test
    public void testGetDocumentation1() throws Exception {
        // @formatter:off
        String code = 
            "/**\n"+
            "   testing\n"+
            "\n"+
            "   @param {String} arg1 - arg1 description\n"+
            "   @return {String} return description\n"+
            "*/\n"+
            "function new_function1(arg1) {\n"+
            "   // TODO: Enter code here\n"+
            "}\n";
        // @formatter:on

        CodeTemplateDocumentation info = CodeTemplateUtil.getDocumentation(code);

        assertEquals("testing", info.getDescription());
        assertEquals("new_function1", info.getFunctionDefinition().getName());
        assertEquals("arg1", info.getFunctionDefinition().getParameters().get(0).getName());
        assertEquals("String", info.getFunctionDefinition().getParameters().get(0).getType());
        assertEquals("arg1 description", info.getFunctionDefinition().getParameters().get(0).getDescription());
        assertEquals("String", info.getFunctionDefinition().getReturnType());
        assertEquals("return description", info.getFunctionDefinition().getReturnDescription());
    }

    @Test
    public void testGetDocumentation2() throws Exception {
        // @formatter:off
        String code = 
            "/**\n"+
            "   testing\n"+
            "   test2\n"+
            "\n"+
            "   @param {String} arg1 - arg1 description\n"+
            "   @return {String} return description\n"+
            "*/\n"+
            "function new_function1(arg1) {\n"+
            "   // TODO: Enter code here\n"+
            "}\n";
        // @formatter:on

        CodeTemplateDocumentation info = CodeTemplateUtil.getDocumentation(code);

        assertEquals("testing test2", info.getDescription());
        assertEquals("new_function1", info.getFunctionDefinition().getName());
        assertEquals("arg1", info.getFunctionDefinition().getParameters().get(0).getName());
        assertEquals("String", info.getFunctionDefinition().getParameters().get(0).getType());
        assertEquals("arg1 description", info.getFunctionDefinition().getParameters().get(0).getDescription());
        assertEquals("String", info.getFunctionDefinition().getReturnType());
        assertEquals("return description", info.getFunctionDefinition().getReturnDescription());
    }

    @Test
    public void testGetDocumentation3() throws Exception {
        // @formatter:off
        String code = 
            "/**\n"+
            "\ttesting\n"+
            "\ttest2\n"+
            "\ttest3\n"+
            "\n"+
            "\t@param {String} arg1 - arg1 description\n"+
            "\t@return {String} return description\n"+
            "*/\n"+
            "function new_function1(arg1) {\n"+
            "\t// TODO: Enter code here\n"+
            "}\n";
        // @formatter:on

        CodeTemplateDocumentation info = CodeTemplateUtil.getDocumentation(code);

        assertEquals("testing test2 test3", info.getDescription());
        assertEquals("new_function1", info.getFunctionDefinition().getName());
        assertEquals("arg1", info.getFunctionDefinition().getParameters().get(0).getName());
        assertEquals("String", info.getFunctionDefinition().getParameters().get(0).getType());
        assertEquals("arg1 description", info.getFunctionDefinition().getParameters().get(0).getDescription());
        assertEquals("String", info.getFunctionDefinition().getReturnType());
        assertEquals("return description", info.getFunctionDefinition().getReturnDescription());
    }

    @Test
    public void testGetDocumentation4() throws Exception {
        // @formatter:off
        String code = 
            "/**\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   test test test test test test test test test test test test test test test\n"+
            "   \n"+
            "   @param {String} arg1 - arg1 description\n"+
            "   @return {String} return description\n"+
            "*/\n"+
            "function new_function1(arg1) {\n"+
            "   // TODO: Enter code here\n"+
            "}\n";
        // @formatter:on
        
        String expectedDescription1 = "test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test\n   test test test test test test test test test test test test test test test";
        String expectedDescription2 = "test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test test";

        CodeTemplateDocumentation info = CodeTemplateUtil.getDocumentation(code);

        assertEquals("new_function1", info.getFunctionDefinition().getName());
        assertEquals("arg1", info.getFunctionDefinition().getParameters().get(0).getName());
        assertTrue(info.getDescription().equals(expectedDescription1) || info.getDescription().equals(expectedDescription2));
    }

    // ===== updateCode (IRT-1521 doc-gen) =====

    @Test
    public void testUpdateCodeAddsPlaceholderDescriptionAndParams() throws Exception {
        String code = "function myFunc(arg1) {\n\treturn arg1;\n}";
        String result = CodeTemplateUtil.updateCode(code);

        assertTrue(result.startsWith("/**"));
        assertTrue(result.contains("Modify the description here"));
        assertTrue(result.contains("@param {Any} arg1"));
        assertTrue(result.contains("@return {Any}"));
        assertTrue(result.contains("function myFunc(arg1)"));
    }

    @Test
    public void testUpdateCodeIsIdempotentOnItsOwnOutputStyle() throws Exception {
        // Per IRT-1521: doc-gen round-trips an existing doc's description/param/return text
        // correctly when the input uses the tab-indented style updateCode() itself produces
        // (a known, pre-existing limitation means a hand-typed conventional leading-asterisk JSDoc
        // style does not round-trip the same way - not exercised here).
        String undocumented = "function myFunc(arg1) {\n\treturn arg1;\n}";
        String firstPass = CodeTemplateUtil.updateCode(undocumented);
        String secondPass = CodeTemplateUtil.updateCode(firstPass);
        assertEquals(firstPass, secondPass);
    }

    @Test
    public void testUpdateCodeBlankInputReturnsUnchanged() throws Exception {
        assertNull(CodeTemplateUtil.updateCode(null));
        assertEquals("", CodeTemplateUtil.updateCode(""));
        assertEquals("   ", CodeTemplateUtil.updateCode("   "));
    }
}
