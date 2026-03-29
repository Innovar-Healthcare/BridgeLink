/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.client.diff;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses JavaScript functions from a Mirth Connect code template XML.
 *
 * Handles two declaration forms:
 *   1. Standard:       function functionName(...) { ... }
 *   2. Object method:  ObjectName.methodName = function(...) { ... }
 *
 * Brace counting (not regex) is used to extract function bodies so that nested
 * braces, strings, and comments are handled correctly.
 */
public class CodeTemplateFunctionParser {

    /**
     * Pattern alternation:
     *   Group 1 — assignment form:  identifier[.identifier]* = function(
     *   Group 2 — declaration form: function identifier(
     */
    private static final Pattern FUNC_PATTERN = Pattern.compile(
            "([\\w][\\w.]*)\\s*=\\s*function\\s*\\(|\\bfunction\\s+(\\w+)\\s*\\("
    );

    /**
     * Parses all top-level functions from the XML and returns them as an ordered
     * map of {@code functionName → functionCode}.  Returns an empty map if the XML
     * contains no {@code <code>} element or no recognisable function declarations.
     */
    public static Map<String, String> parse(String xml) {
        Map<String, String> result = new LinkedHashMap<>();
        if (xml == null || xml.trim().isEmpty()) return result;

        String code = extractCodeContent(xml);
        if (code == null || code.trim().isEmpty()) return result;

        int searchFrom = 0;
        while (searchFrom < code.length()) {
            Matcher m = FUNC_PATTERN.matcher(code);
            if (!m.find(searchFrom)) break;

            // Resolve function name: assignment group wins over declaration group
            String name = m.group(1) != null ? m.group(1).trim()
                        : m.group(2) != null ? m.group(2).trim() : null;
            if (name == null) {
                searchFrom = m.end();
                continue;
            }

            // Find the opening brace (may be past the parameter list on a later line)
            int braceStart = indexOfOpenBrace(code, m.end());
            if (braceStart < 0) {
                searchFrom = m.end();
                continue;
            }

            // Count braces to find the matching closing brace
            int bodyEnd = findMatchingBrace(code, braceStart);
            if (bodyEnd < 0) {
                searchFrom = m.end();
                continue;
            }

            String funcCode = code.substring(m.start(), bodyEnd + 1).trim();
            result.put(name, funcCode);

            // Advance past the extracted body so nested functions are not re-parsed
            searchFrom = bodyEnd + 1;
        }

        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts the raw text content of the {@code <code>} element, stripping any
     * {@code <![CDATA[ ... ]]>} wrapper.
     */
    private static String extractCodeContent(String xml) {
        int tagStart = xml.indexOf("<code>");
        if (tagStart < 0) return null;

        int contentStart = tagStart + 6; // length of "<code>"
        int tagEnd = xml.indexOf("</code>", contentStart);
        if (tagEnd < 0) return null;

        String content = xml.substring(contentStart, tagEnd).trim();

        if (content.startsWith("<![CDATA[") && content.endsWith("]]>")) {
            content = content.substring(9, content.length() - 3);
        }

        return content;
    }

    private static int indexOfOpenBrace(String code, int from) {
        for (int i = from; i < code.length(); i++) {
            if (code.charAt(i) == '{') return i;
        }
        return -1;
    }

    /**
     * Returns the index of the closing {@code '}' } matching the opening
     * {@code '{'} at {@code openBracePos}.  Correctly skips braces inside
     * line comments ({@code //}), block comments ({@code /* … *\/}), and
     * string literals ({@code "}, {@code '}, or template literal {@code `}).
     *
     * @return index of the matching {@code '}'}, or {@code -1} if not found
     */
    private static int findMatchingBrace(String code, int openBracePos) {
        int depth = 0;
        boolean inLineComment  = false;
        boolean inBlockComment = false;
        boolean inString       = false;
        char    stringChar     = 0;

        for (int i = openBracePos; i < code.length(); i++) {
            char c    = code.charAt(i);
            char next = (i + 1 < code.length()) ? code.charAt(i + 1) : 0;

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }

            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++; // consume '/'
                }
                continue;
            }

            if (inString) {
                if (c == '\\') {
                    i++; // skip escaped character
                } else if (c == stringChar) {
                    inString = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') {
                inString   = true;
                stringChar = c;
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }

        return -1;
    }
}
