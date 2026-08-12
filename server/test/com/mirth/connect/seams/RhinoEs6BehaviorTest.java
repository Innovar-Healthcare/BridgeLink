/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.seams;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mirth.connect.model.ConnectorMetaData;
import com.mirth.connect.model.PluginMetaData;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ExtensionController;
import com.mirth.connect.server.util.javascript.MirthContextFactory;

/**
 * Behavioral characterization suite (Phase 23.1 T2/T3, CVE-13) for the bump-sensitive vendored
 * Rhino 1.7.15.1 seams (server/lib/rhino-1.7.15.1.jar), run under the ES6 language version that
 * is the shipped {@code mirth.properties} default ({@code rhino.languageversion = es6}).
 * <p>
 * This is a NEW, standalone suite (D-05) &mdash; it does NOT extend {@link RhinoSeamTest}, the
 * landed Phase-23 seam link-proof baseline, which is not modified. The {@code @BeforeClass}
 * bootstrap below is copied verbatim from {@code RhinoSeamTest} (same mocked
 * {@code ControllerFactory}/{@code ConfigurationController}/{@code ExtensionController}, same
 * Guice static-injection wiring, same scoped {@code conf}-directory classloader) so both suites
 * exercise scripts through the same real {@code MirthContextFactory} seam (D-07), not a raw
 * {@code Context.enter()}.
 * <p>
 * Scope (D-06): only the seams a Rhino version bump actually shifts &mdash; {@code NativeDate}
 * (zero coverage before this suite), number/string coercion, regex, and {@code JSON.stringify}
 * over a {@code NativeJavaObject} (the vendored unwrap-to-{@code NativeArray} seam in
 * {@code NativeJSON.str()}). NOT a broad built-in sweep.
 * <p>
 * Every assertion below asserts a concrete input&rarr;output value (D-11), never a tautology or a
 * bare {@code typeof}/non-null check. Each expected literal was captured from a standalone spike
 * run of the exact script through the real vendored jar + shadowed {@code server/src} classes
 * (the same shadow-precedence classpath ordering {@code server/build.xml}'s testclasspath uses)
 * under both {@code Context.VERSION_ES6} and {@code Context.VERSION_DEFAULT} before being locked
 * here &mdash; see 23.1-02-SUMMARY.md for the recorded spike output. The Date/coercion/regex
 * values are identical under both language versions (they are standard ECMA behavior, not
 * ES6-specific), which is itself the expected, verified result, not a fabricated guess.
 */
public class RhinoEs6BehaviorTest {

    private static MirthContextFactory contextFactory;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        ConfigurationController configurationController = mock(ConfigurationController.class);
        // rhino.languageversion = es6 is the shipped mirth.properties default: characterize the
        // same language version BridgeLink runs scripts under in production (D-07).
        when(configurationController.getRhinoLanguageVersion()).thenReturn(Context.VERSION_ES6);
        when(configurationController.getServerVersion()).thenReturn("26.9.0-es6-behavior-test");
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        ExtensionController extensionController = mock(ExtensionController.class);
        when(extensionController.getConnectorMetaData()).thenReturn(new HashMap<String, ConnectorMetaData>());
        when(extensionController.getPluginMetaData()).thenReturn(new HashMap<String, PluginMetaData>());
        when(controllerFactory.createExtensionController()).thenReturn(extensionController);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        });
        injector.getInstance(ControllerFactory.class);

        // MirthContextFactory's constructor triggers JavaScriptScopeUtil's one-time static
        // initializer, which loads mirth.properties as a classpath resource via the thread
        // context classloader. server/build.xml's testclasspath does not include server/conf
        // (only the production create-setup assembly copies it to server/setup/conf) -- make the
        // real, already-committed server/conf/mirth.properties available for the duration of this
        // one-time class init without touching build.xml.
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        File confDir = new File("conf");
        // The loader is CLOSED in the finally block: restoring the previous context classloader is
        // not enough on its own -- an unclosed URLClassLoader holds a file handle on server/conf for
        // the JVM's lifetime. The one-time class init it exists for has already happened by then.
        URLClassLoader confLoader = null;
        try {
            if (confDir.isDirectory()) {
                URL confUrl = confDir.toURI().toURL();
                confLoader = new URLClassLoader(new URL[] { confUrl }, originalClassLoader);
                Thread.currentThread().setContextClassLoader(confLoader);
            }
            // Real MirthContextFactory -- BridgeLink's own Rhino seam, not raw Context.enter().
            contextFactory = new MirthContextFactory(null, null, false);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            if (confLoader != null) {
                confLoader.close();
            }
        }
    }

    private Object evaluateTyped(String script) {
        Context cx = contextFactory.enterContext();
        try {
            Scriptable scope = cx.initStandardObjects();
            return cx.evaluateString(scope, script, "es6behaviortest", 1, null);
        } finally {
            Context.exit();
        }
    }

    private String evaluate(String script) {
        return Context.toString(evaluateTyped(script));
    }

    // ========== T2: NativeDate (highest priority, zero coverage before this suite) ==========

    @Test
    public void nativeDateEpochAndIsoRoundTrip() {
        // UTC-anchored, TZ-independent values only (RESEARCH Pitfall 4) -- these must not depend
        // on the JVM's default timezone the way local-time formatting (toString/getHours) would.
        assertEquals("0", evaluate("new Date(0).getTime();"));
        assertEquals("1970-01-01T00:00:00.000Z", evaluate("new Date(0).toISOString();"));
        // Round-trip: parsing the ISO string this engine itself produced returns to the same
        // epoch millisecond value.
        assertEquals("0", evaluate("Date.parse('1970-01-01T00:00:00.000Z');"));
    }

    @Test
    public void nativeDateEpochBoundaryStepEitherSide() {
        // Boundary edge (must_haves): one step either side of the epoch is consistent through the
        // NativeDate seam.
        assertEquals("1", evaluate("new Date(1).getTime();"));
        assertEquals("-1", evaluate("new Date(-1).getTime();"));
    }

    // ========== T2: number/string coercion (precision edge) ==========

    @Test
    public void numberStringCoercion() {
        assertEquals("0.10.2", evaluate("'' + 0.1 + 0.2;"));
        assertEquals("0.3333333333333333", evaluate("(1 / 3).toString();"));
        assertEquals("42", evaluate("parseInt('42px', 10);"));
    }

    // ========== T2: regex ==========

    @Test
    public void regexGlobalReplace() {
        assertEquals("a#b#c#", evaluate("'a1b2c3'.replace(/\\d/g, '#');"));
    }

    // ========== T2: JSON.stringify over a NativeJavaObject (the explicit D-06 case) ==========

    @Test
    public void jsonStringifyUnwrapsNativeJavaObject() {
        // Exercises the vendored unwrap-to-NativeArray seam at NativeJSON.str() -- a java.util.List
        // injected into scope must stringify as a JSON array, not "{}"/a stringified toString(), or
        // an error, which is exactly what a bump could clobber.
        Context cx = contextFactory.enterContext();
        try {
            ScriptableObject scope = (ScriptableObject) cx.initStandardObjects();
            List<String> javaList = new ArrayList<String>();
            javaList.add("a");
            javaList.add("b");
            scope.put("javaList", scope, Context.javaToJS(javaList, scope));

            Object result = cx.evaluateString(scope, "JSON.stringify(javaList);", "es6behaviortest", 1, null);

            assertEquals("[\"a\",\"b\"]", Context.toString(result));
        } finally {
            Context.exit();
        }
    }

}
