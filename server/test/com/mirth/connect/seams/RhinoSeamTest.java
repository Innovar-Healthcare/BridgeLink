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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;

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
import com.mirth.connect.server.util.javascript.JavaScriptScopeUtil;
import com.mirth.connect.server.util.javascript.MirthContextFactory;

/**
 * Dependency-seam characterization suite (NET-03, D-09/D-10) for the Rhino engine AS SHIPPED:
 * rhino 1.7.15.1 (server/lib/rhino-1.7.15.1.jar), run with the ES6 language version that is the
 * shipped {@code mirth.properties} default ({@code rhino.languageversion = es6}).
 * <p>
 * Scripts are executed through a real {@code com.mirth.connect.server.util.javascript.
 * MirthContextFactory} &mdash; BridgeLink's own Rhino seam &mdash; rather than a raw
 * {@code org.mozilla.javascript.Context.enter()}, per 18-RESEARCH.md Open Question 3: the leanest
 * setup that still exercises the factory's language-version resolution, sealed shared scope
 * construction, and per-thread {@code MirthContext} instruction-observer wiring is used, with a
 * single mocked {@code ConfigurationController} standing in for the rest of the server (Pitfall 9:
 * minimize Mockito; used here only because {@code MirthContextFactory}'s constructor requires
 * {@code ControllerFactory.getFactory()} to be wired).
 * <p>
 * Purpose: protects the JS connector/transformer path for JDK-25 certification (Phase 28) and any
 * future Rhino bump.
 */
public class RhinoSeamTest {

    private static MirthContextFactory contextFactory;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);

        ConfigurationController configurationController = mock(ConfigurationController.class);
        // rhino.languageversion = es6 is the shipped mirth.properties default: characterize the
        // same language version BridgeLink runs scripts under in production.
        when(configurationController.getRhinoLanguageVersion()).thenReturn(Context.VERSION_ES6);
        when(configurationController.getServerVersion()).thenReturn("26.9.0-seam-test");
        when(controllerFactory.createConfigurationController()).thenReturn(configurationController);

        // JavaScriptScopeUtil.createSealedSharedScope() -> JavaScriptUtil.getCompiledGlobalSealedScript()
        // -> JavaScriptBuilder.generateGlobalSealedScript() reads extensionController's connector/plugin
        // metadata to build importPackage() statements for the sealed shared scope; empty maps
        // characterize the seam with no extensions installed.
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
        // one-time class init without touching build.xml (no server/build.xml changes, D-09/D-10).
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        File confDir = new File("conf");
        if (confDir.isDirectory()) {
            URL confUrl = confDir.toURI().toURL();
            Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[] { confUrl }, originalClassLoader));
        }
        try {
            // Real MirthContextFactory -- BridgeLink's own Rhino seam, not raw Context.enter().
            contextFactory = new MirthContextFactory(null, null, false);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private String evaluate(String script) {
        Context cx = contextFactory.enterContext();
        try {
            Scriptable scope = cx.initStandardObjects();
            Object result = cx.evaluateString(scope, script, "seamtest", 1, null);
            return Context.toString(result);
        } finally {
            Context.exit();
        }
    }

    // ========== Test 1: E4X script executes ==========

    @Test
    public void e4xScriptExecutes() {
        String script = "var msg = <HL7><PID>McDoogal</PID></HL7>; msg.PID.toString();";

        assertEquals("McDoogal", evaluate(script));
    }

    // ========== Test 2: Java interop works ==========

    @Test
    public void javaInteropWorks() {
        String script = "var s = new java.lang.String('interop'); s.toUpperCase();";

        assertEquals("INTEROP", evaluate(script));
    }

    // ========== Test 3: transformer-style script mutates message content ==========

    @Test
    public void transformerStyleScriptMutatesMessage() {
        String script = "var HL7_MSG = 'MSH|^~\\\\&|TestSend|TestFacility|TestRec|TestRecFac|20240101120000||ADT^A01|MSGID001|P|2.3\\r' +\n" +
                "    'PID|1||12345^^^Hospital^MR||McDoogal^Angus||19800101|M\\r';\n" +
                "var segments = HL7_MSG.split('\\r');\n" +
                "var pidSegment = segments[1];\n" +
                "var fields = pidSegment.split('|');\n" +
                "var patientName = fields[5];\n" +
                "'Transformed: ' + patientName;";

        assertEquals("Transformed: McDoogal^Angus", evaluate(script));
    }

    // ========== Test 4: adapter (a) -- NativeJavaObject.init shim registers JavaIterableIterator ==========

    @Test
    public void nativeJavaObjectInitShimRegistersJavaIterableIterator() {
        Context cx = contextFactory.enterContext();
        try {
            // PRECONDITION: the 3 tests above already prove this shim LINKS -- removing it
            // entirely makes ScriptRuntime throw NoSuchMethodError inside the class-level setup's
            // MirthContextFactory construction (bytecode-verified: ScriptRuntime.
            // initSafeStandardObjects is the sole caller of NativeJavaObject.init across all 542
            // classes in rhino-1.7.15.1.jar). What those 3 tests do NOT prove is that the shim's
            // reflection actually REACHED JavaIterableIterator.init -- before D-10's throw, a
            // failed Class.forName/getDeclaredMethod was silently swallowed, leaving
            // initStandardObjects() succeeding while JavaIterableIterator was never registered.
            // JavaIterableIterator.init's final act (via ES6Iterator.init) is
            // scope.associateValue("JavaIterableIterator", prototype) (bytecode-verified), so
            // asserting that value is non-null proves the shim's OBSERVABLE EFFECT, not merely
            // that it linked. This is NOT written as a JS `for...of` over a Java Iterable (D-32):
            // the vendored NativeJavaObject.get(Symbol, Scriptable) has no SymbolKey.ITERATOR
            // handling, so JS-level iteration genuinely does not work here even with a correctly
            // installed shim, and such a test would false-red.
            ScriptableObject scope = (ScriptableObject) cx.initStandardObjects();
            assertNotNull("NativeJavaObject.init shim must register the JavaIterableIterator prototype",
                    ScriptableObject.getTopScopeValue(scope, "JavaIterableIterator"));
        } finally {
            Context.exit();
        }
    }

    // ========== Test 5: adapter (b) -- importPackage'd core symbol resolves via JavaScriptScopeUtil ==========

    @Test
    public void importPackagedCoreSymbolResolvesInJavaScriptScopeUtilScope() {
        // PRECONDITION: JavaScriptBuilder.generateGlobalSealedScript() unconditionally emits
        // importPackage(Packages.com.mirth.connect.userutil) and
        // importPackage(Packages.com.mirth.connect.server.userutil), independent of the empty
        // getConnectorMetaData()/getPluginMetaData() maps this class's class-level setup mocks
        // (confirmed by reading JavaScriptBuilder.java) -- so this scope has a core symbol to
        // resolve without needing to seed one. com.mirth.connect.userutil.Response is picked as a
        // concrete, stable class from that package.
        Scriptable scope = JavaScriptScopeUtil.getDeployScope(contextFactory, null);
        try {
            // PRECONDITION: adapter (b) propagates the sealed scope's
            // associatedValue("importedPackages") onto this parentScope-null child. Without that
            // propagation, "Response" resolves to Scriptable.NOT_FOUND here even though the sealed
            // shared scope itself correctly ran importPackage -- the exact silent behavior change
            // D-11/T-23-21 exist to close, since parentScope is deliberately left null (see
            // JavaScriptScopeUtil.getScope()'s own comment for why) and nothing else makes the
            // sealed scope's imports visible from a fresh child scope. ScriptableObject.getProperty
            // (not a bare scope.get(...)) is used deliberately: it walks the prototype chain the
            // way Rhino's own name-resolution does, invoking the prototype's (the sealed
            // ImporterTopLevel's) overridden get() -- a bare scope.get(name, scope) call does not
            // walk the chain and would false-red regardless of adapter (b).
            Object response = ScriptableObject.getProperty(scope, "Response");
            assertNotEquals("importPackage'd com.mirth.connect.userutil.Response must resolve in a "
                    + "scope obtained through JavaScriptScopeUtil's public getters",
                    Scriptable.NOT_FOUND, response);
        } finally {
            Context.exit();
        }
    }
}
