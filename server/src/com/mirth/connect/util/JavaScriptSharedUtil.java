/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.util;

import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.ElementGet;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NodeVisitor;
import org.mozilla.javascript.ast.NumberLiteral;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.StringLiteral;
import org.mozilla.javascript.ast.XmlDotQuery;
import org.mozilla.javascript.ast.XmlElemRef;
import org.mozilla.javascript.ast.XmlMemberGet;
import org.mozilla.javascript.ast.XmlPropRef;

import com.mirth.connect.server.util.ResourceUtil;

public class JavaScriptSharedUtil {

    private final static Pattern RESULT_PATTERN = Pattern.compile("responseMap\\s*\\.\\s*put\\s*\\(\\s*(['\"])(((?!(?<!\\\\)\\1).)*)(?<!\\\\)\\1|\\$r\\s*\\(\\s*(['\"])(((?!(?<!\\\\)\\4).)*)(?<!\\\\)\\4(?=\\s*,)");
    private final static Pattern INVALID_IDENTIFIER_PATTERN = Pattern.compile("[^a-zA-Z0-9_$]");
    private final static Pattern INVALID_PROLOG_PATTERN = Pattern.compile("<<\\s*\\?\\s*xml\\s+version\\s*=\\s*\"(?<version>[^\"]*)\"(\\s+encoding\\s*=\\s*\"(?<encoding>[^\"]*)\")?\\s*\\?\\s*>");
    private final static int FULL_NAME_MATCHER_INDEX = 2;
    private final static int SHORT_NAME_MATCHER_INDEX = 5;
    /*
     * Scripts are wrapped in a dummy function before validation so that top-level "return"
     * statements are legal. The prefix sits on line 1 with no trailing newline, so reported line
     * numbers are unaffected, but a column reported on line 1 is shifted right by the prefix length
     * and must be adjusted back before being handed to a caller.
     */
    private final static String SCRIPT_WRAPPER_PREFIX = "function rhinoWrapper() {";
    private final static String SCRIPT_WRAPPER_SUFFIX = "\n}";
    private final static int SCRIPT_WRAPPER_PREFIX_LENGTH = SCRIPT_WRAPPER_PREFIX.length();
    private static volatile ScriptableObject cachedFormatterScope;
    /*
     * cachedFormatterScope's global/opts objects are shared mutable state across calls; js_beautify
     * execution against them must be serialized so concurrent callers (e.g. concurrent REST
     * requests on the server) don't race on them.
     */
    private static final Object FORMATTER_LOCK = new Object();
    private static int rhinoLanguageVersion = Context.VERSION_DEFAULT;
    private static Logger logger = LogManager.getLogger(JavaScriptSharedUtil.class);

    public static void setRhinoLanguageVersion(int version) {
        try {
            Context.checkLanguageVersion(version);
        } catch (Exception e) {
            logger.error("Error setting Rhino version.", e);
            version = Context.VERSION_DEFAULT;
        }
        rhinoLanguageVersion = version;
    }

    /*
     * Retrieves the Context for the current Thread. The context must be cleaned up with
     * Context.exit() when it is no longer needed.
     */
    public static Context getGlobalContextForValidation() {
        Context context = Context.enter();
        context.setOptimizationLevel(-1);
        context.setLanguageVersion(rhinoLanguageVersion);
        return context;
    }

    public static String validateScript(String script) {
        Context context = JavaScriptSharedUtil.getGlobalContextForValidation();
        try {
            context.compileString(SCRIPT_WRAPPER_PREFIX + script + SCRIPT_WRAPPER_SUFFIX, UUID.randomUUID().toString(), 1, null);
        } catch (EvaluatorException e) {
            return "Error on line " + e.lineNumber() + ": " + e.getMessage() + ".";
        } catch (Exception e) {
            return "Unknown error occurred during validation.";
        } finally {
            Context.exit();
        }
        return null;
    }

    /**
     * Compiles the given script with the real Rhino engine and returns a structured result instead
     * of the single, human-readable string produced by {@link #validateScript(String)}. Rhino's
     * compiler stops at the first syntax error, so at most one error is ever reported. Line and
     * column are 1-based and normalized to the caller's script (the internal wrapper offset is
     * removed); a column of 0 means Rhino could not determine one.
     *
     * @param script the script body to validate (may be null or empty, which is treated as valid)
     * @return a {@link ScriptValidationResult} describing whether the script compiled and, if not,
     *         where it failed
     */
    public static ScriptValidationResult validateScriptStructured(String script) {
        if (StringUtils.isBlank(script)) {
            return ScriptValidationResult.valid();
        }

        Context context = JavaScriptSharedUtil.getGlobalContextForValidation();
        try {
            context.compileString(SCRIPT_WRAPPER_PREFIX + script + SCRIPT_WRAPPER_SUFFIX, UUID.randomUUID().toString(), 1, null);
            return ScriptValidationResult.valid();
        } catch (EvaluatorException e) {
            int line = e.lineNumber();
            int column = e.columnNumber();
            /*
             * Rhino columns are 1-based. Only line 1 carries the wrapper prefix, so only errors on
             * that line need the prefix width subtracted; a non-positive column means "unknown".
             */
            if (line == 1 && column > SCRIPT_WRAPPER_PREFIX_LENGTH) {
                column -= SCRIPT_WRAPPER_PREFIX_LENGTH;
            } else if (column < 0) {
                column = 0;
            }
            String message = StringUtils.defaultIfBlank(e.details(), e.getMessage());
            return ScriptValidationResult.invalid(new ScriptValidationResult.ScriptValidationError(line, column, message));
        } catch (Exception e) {
            return ScriptValidationResult.invalid(new ScriptValidationResult.ScriptValidationError(0, 0, "Unknown error occurred during validation."));
        } finally {
            Context.exit();
        }
    }

    /*
     * This regular expression uses alternation to capture either the "responseMap.put" syntax, or
     * the "$r('key'," syntax. Kleene closures for whitespace are used in between every method token
     * since it is legal JavaScript. Instead of checking ['"] once at the beginning and end, it
     * checks once and then uses a backreference later on. That way you can capture keys like
     * "Foo's Bar". It also accounts for backslashes before any subsequent backreferences so that
     * "Foo\"s Bar" would still be captured. In the "$r" case, the regular expression also performs
     * a lookahead to ensure that there is a comma after the first argument, indicating that it is
     * the "put" version of the method, not the "get" version.
     */
    public static Collection<String> getResponseVariables(String script) {
        Collection<String> variables = new HashSet<String>();

        if (script != null && script.length() > 0) {
            Matcher matcher = RESULT_PATTERN.matcher(script);
            while (matcher.find()) {
                variables.add(getMapKey(matcher));
            }
        }
        return variables;
    }

    private static String getMapKey(Matcher matcher) {
        /*
         * Since multiple capturing groups are used and the final key could reside on either side of
         * the alternation, we use two specific group indices (2 and 5), one for the full
         * "responseMap" case and one for the short "$r" case. We also replace JavaScript-specific
         * escape sequences like \', \", etc.
         */
        String key = matcher.group(FULL_NAME_MATCHER_INDEX);
        if (key == null) {
            key = matcher.group(SHORT_NAME_MATCHER_INDEX);
        }
        return StringEscapeUtils.unescapeEcmaScript(key);
    }

    /**
     * Removes any invalid characters from the string, making it a valid JavaScript identifier. Note
     * that although the ECMA specifications include a wide range of characters, "valid" in this
     * context means alphanumeric plus "$" and "_".
     */
    public static String convertIdentifier(String identifier) {
        return StringUtils.defaultIfEmpty(INVALID_IDENTIFIER_PATTERN.matcher(identifier).replaceAll(""), "_");
    }

    public static String prettyPrint(String script) {
        ScriptableObject scope = cachedFormatterScope;
        if (scope == null) {
            synchronized (JavaScriptSharedUtil.class) {
                scope = cachedFormatterScope;
                if (scope == null) {
                    scope = cachedFormatterScope = getFormatterScope();
                }
            }
        }

        if (scope != null) {
            Context currentThreadContext = getGlobalContextForValidation();
            try {
                synchronized (FORMATTER_LOCK) {
                    /*
                     * The beautify library wraps everything in a closure and adds the beautify
                     * function to a specified object. We inject the global object so that we can
                     * access the function here.
                     */
                    Scriptable global = (Scriptable) scope.get("global", scope);
                    Scriptable opts = (Scriptable) scope.get("opts", scope);
                    Function function = (Function) global.get("js_beautify", global);
                    Object result = function.call(currentThreadContext, scope, scope, new Object[] {
                            script, opts });
                    String prettyPrinted = (String) (Context.jsToJava(result, String.class));

                    Matcher matcher = INVALID_PROLOG_PATTERN.matcher(prettyPrinted);
                    if (matcher.find()) {
                        StringBuffer buffer = new StringBuffer();

                        do {
                            String version = matcher.group("version");
                            String encoding = matcher.group("encoding");
                            StringBuilder prolog = new StringBuilder("<?xml version=\"").append(version).append('"');
                            if (encoding != null) {
                                prolog.append(" encoding=\"").append(encoding).append('"');
                            }
                            prolog.append("?>");
                            matcher.appendReplacement(buffer, prolog.toString());
                        } while (matcher.find());

                        matcher.appendTail(buffer);
                        prettyPrinted = buffer.toString();
                    }

                    return prettyPrinted;
                }
            } finally {
                Context.exit();
            }
        }
        return script;
    }

    private static ScriptableObject getFormatterScope() {
        Context context = getGlobalContextForValidation();
        InputStream is = null;
        
        try {
            is = JavaScriptSharedUtil.class.getResourceAsStream("beautify-1.6.8.js");
            String script = IOUtils.toString(is);
            ScriptableObject scope = context.initStandardObjects();
            context.evaluateString(scope, "var global = {};", UUID.randomUUID().toString(), 1, null);
            context.evaluateString(scope, script, UUID.randomUUID().toString(), 1, null);
            context.evaluateString(scope, "var opts = { 'e4x': true };", UUID.randomUUID().toString(), 1, null);
            return scope;
        } catch (Exception e) {
            logger.error("Failed to load beautify library.");
            return null;
        } finally {
            ResourceUtil.closeResourceQuietly(is);
            Context.exit();
        }
    }

    public static String removeNumberLiterals(String expression) {
        if (expression == null) {
            return null;
        }

        String suffix = "";
        if (StringUtils.endsWith(expression, ".toString()")) {
            suffix = ".toString()";
            expression = StringUtils.removeEnd(expression, suffix);
        }
        StringBuilder builder = new StringBuilder();
        for (ExprPart part : getExpressionParts(expression, false)) {
            builder.append(part.getValue());
        }
        return builder.append(suffix).toString();
    }

    public static List<ExprPart> getExpressionParts(String expression) {
        return getExpressionParts(expression, true);
    }

    public static List<ExprPart> getExpressionParts(String expression, boolean includeNumberLiterals) {
        try {
            ExpressionVisitor visitor = new ExpressionVisitor(includeNumberLiterals);
            CompilerEnvirons env = new CompilerEnvirons();
            env.setRecordingLocalJsDocComments(true);
            env.setAllowSharpComments(true);
            env.setRecordingComments(true);
            new Parser(env).parse(new StringReader(expression), null, 1).visitAll(visitor);
            if (!visitor.getParts().isEmpty()) {
                return visitor.getParts();
            }
        } catch (Exception e) {
            logger.debug("Error parsing expression: " + expression);
        }

        if (StringUtils.isNotBlank(expression)) {
            return new ArrayList<ExprPart>(Collections.singletonList(new ExprPart(expression, expression)));
        } else {
            return new ArrayList<ExprPart>();
        }
    }

    private static class ExpressionVisitor implements NodeVisitor {

        private boolean includeNumberLiterals;
        private List<ExprPart> parts = new ArrayList<ExprPart>();

        public ExpressionVisitor(boolean includeNumberLiterals) {
            this.includeNumberLiterals = includeNumberLiterals;
        }

        public List<ExprPart> getParts() {
            return parts;
        }

        @Override
        public boolean visit(AstNode node) {
            if (node instanceof AstRoot && node.hasChildren() && node.getFirstChild() instanceof ExpressionStatement) {
                ((ExpressionStatement) node.getFirstChild()).getExpression().visit(this);
            } else if (node instanceof ElementGet || node instanceof PropertyGet || node instanceof XmlMemberGet || node instanceof XmlDotQuery) {
                return true;
            }

            ExprPart part = null;

            if (node instanceof Name) {
                Name name = (Name) node;
                if (parts.isEmpty()) {
                    part = new ExprPart(name.getIdentifier(), name.getIdentifier());
                } else if (name.getParent() instanceof PropertyGet) {
                    part = new ExprPart("." + name.toSource(), name.getIdentifier());
                } else {
                    part = new ExprPart("[" + name.toSource() + "]", name.getIdentifier());
                }
            } else if (!parts.isEmpty()) {
                if (node instanceof StringLiteral) {
                    part = new ExprPart("[" + node.toSource() + "]", node.toSource());
                } else if (node instanceof NumberLiteral) {
                    if (includeNumberLiterals) {
                        part = new ExprPart("[" + node.toSource() + "]", node.toSource(), true);
                    }
                } else if (node instanceof XmlPropRef) {
                    part = new ExprPart("." + node.toSource(), ((XmlPropRef) node).getPropName().toSource());
                } else if (node instanceof XmlElemRef) {
                    part = new ExprPart("." + node.toSource(), ((XmlElemRef) node).getExpression().toSource());
                }
            }

            if (part != null && StringUtils.isNoneBlank(part.getPropertyName(), part.getValue())) {
                parts.add(part);
            }

            return false;
        }
    }

    public static class ExprPart {

        private String value;
        private String propertyName;
        private boolean numberLiteral;

        public ExprPart(String value, String propertyName) {
            this(value, propertyName, false);
        }

        public ExprPart(String value, String propertyName, boolean numberLiteral) {
            this.value = value;
            this.propertyName = propertyName;
            this.numberLiteral = numberLiteral;
        }

        public String getValue() {
            return value;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public boolean isNumberLiteral() {
            return numberLiteral;
        }

        @Override
        public boolean equals(Object obj) {
            return EqualsBuilder.reflectionEquals(this, obj);
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
