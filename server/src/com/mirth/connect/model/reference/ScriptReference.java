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

package com.mirth.connect.model.reference;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.mirth.connect.model.codetemplates.CodeTemplateContextSet;
import com.mirth.connect.model.codetemplates.CodeTemplateFunctionDefinition;

public class ScriptReference implements Serializable {

    public enum Type {
        CLASS, FUNCTION, VARIABLE, CODE
    }

    private Type type;
    private CodeTemplateContextSet contextSet;
    private String category;
    private String name;
    private String description;
    private String replacementCode;
    private String summary;
    private String definitionString;
    private String template;
    private String className;
    private CodeTemplateFunctionDefinition functionDefinition;
    private List<String> beforeDotTextList;

    public ScriptReference() {}

    private ScriptReference(Type type, CodeTemplateContextSet contextSet, String category, String name, String description, String replacementCode) {
        this.type = type;
        this.contextSet = contextSet;
        this.category = category;
        this.name = name;
        this.description = description;
        this.replacementCode = replacementCode;
    }

    public static ScriptReference code(CodeTemplateContextSet contextSet, String category, String name, String description, String replacementCode) {
        ScriptReference reference = new ScriptReference(Type.CODE, contextSet, category, name, description, replacementCode);
        reference.summary = htmlSummary(name, description, replacementCode);
        return reference;
    }

    public static ScriptReference parameterizedCode(CodeTemplateContextSet contextSet, String category, String name, String description, String template) {
        ScriptReference reference = new ScriptReference(Type.CODE, contextSet, category, name, description, template.replaceAll("\\$\\{([^\\}]+)\\}", "$1"));
        reference.definitionString = name.toLowerCase().replace(' ', '-');
        reference.template = template.replaceAll("\\$(?!\\{)", "\\$\\$");
        reference.summary = htmlSummary(name, description, reference.replacementCode);
        return reference;
    }

    public static ScriptReference variable(CodeTemplateContextSet contextSet, String category, String name, String description, String replacementCode) {
        return new ScriptReference(Type.VARIABLE, contextSet, category, name, description, replacementCode);
    }

    public static ScriptReference function(CodeTemplateContextSet contextSet, String category, String className, String name, String description, String replacementCode, CodeTemplateFunctionDefinition functionDefinition) {
        return function(contextSet, category, className, name, description, replacementCode, functionDefinition, null);
    }

    public static ScriptReference function(CodeTemplateContextSet contextSet, String category, String className, String name, String description, String replacementCode, CodeTemplateFunctionDefinition functionDefinition, List<String> beforeDotTextList) {
        ScriptReference reference = new ScriptReference(Type.FUNCTION, contextSet, category, name, description, replacementCode);
        reference.className = className;
        reference.functionDefinition = functionDefinition;
        reference.beforeDotTextList = beforeDotTextList;
        return reference;
    }

    private static String htmlSummary(String name, String description, String replacementCode) {
        return "<html><body><h4><b>" + StringUtils.trimToEmpty(name) + "</b></h4><hr/>" + StringUtils.trimToEmpty(description) + "<br/><br/><hr/><br/><code>" + StringUtils.trimToEmpty(replacementCode).replaceAll("\r\n|\r|\n", "<br/>").replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;") + "</code></body></html>";
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public CodeTemplateContextSet getContextSet() {
        return contextSet;
    }

    public void setContextSet(CodeTemplateContextSet contextSet) {
        this.contextSet = contextSet;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReplacementCode() {
        return replacementCode;
    }

    public void setReplacementCode(String replacementCode) {
        this.replacementCode = replacementCode;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDefinitionString() {
        return definitionString;
    }

    public void setDefinitionString(String definitionString) {
        this.definitionString = definitionString;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public CodeTemplateFunctionDefinition getFunctionDefinition() {
        return functionDefinition;
    }

    public void setFunctionDefinition(CodeTemplateFunctionDefinition functionDefinition) {
        this.functionDefinition = functionDefinition;
    }

    public List<String> getBeforeDotTextList() {
        return beforeDotTextList;
    }

    public void setBeforeDotTextList(List<String> beforeDotTextList) {
        this.beforeDotTextList = beforeDotTextList;
    }
}
