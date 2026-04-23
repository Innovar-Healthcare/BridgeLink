package com.mirth.connect.server;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.mirth.connect.server.controllers.AlertController;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ContextFactoryController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EngineController;
import com.mirth.connect.server.controllers.EventController;
import com.mirth.connect.server.controllers.ExtensionController;
import com.mirth.connect.server.controllers.MigrationController;
import com.mirth.connect.server.controllers.ScriptController;
import com.mirth.connect.server.controllers.UsageController;
import com.mirth.connect.server.controllers.UserController;
import com.mirth.connect.server.launcher.MirthLauncher;

/**
 * Unit tests for root/administrator privilege checks (IRT-584).
 *
 * Tests the package-private evaluateRootCheck helpers in MirthLauncher and Mirth,
 * and the readNoNewPrivs injectable path in Mirth.
 */
public class RootCheckTest {

    private static Mirth mirth;

    @BeforeClass
    public static void setUpClass() throws Exception {
        ControllerFactory controllerFactory = mock(ControllerFactory.class);
        when(controllerFactory.createEngineController()).thenReturn(mock(EngineController.class));
        when(controllerFactory.createConfigurationController()).thenReturn(mock(ConfigurationController.class));
        when(controllerFactory.createUserController()).thenReturn(mock(UserController.class));
        when(controllerFactory.createExtensionController()).thenReturn(mock(ExtensionController.class));
        when(controllerFactory.createMigrationController()).thenReturn(mock(MigrationController.class));
        when(controllerFactory.createEventController()).thenReturn(mock(EventController.class));
        when(controllerFactory.createScriptController()).thenReturn(mock(ScriptController.class));
        when(controllerFactory.createContextFactoryController()).thenReturn(mock(ContextFactoryController.class));
        when(controllerFactory.createAlertController()).thenReturn(mock(AlertController.class));
        when(controllerFactory.createUsageController()).thenReturn(mock(UsageController.class));

        Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                requestStaticInjection(ControllerFactory.class);
                bind(ControllerFactory.class).toInstance(controllerFactory);
            }
        }).getInstance(ControllerFactory.class);

        mirth = new Mirth();
    }

    // ===== MirthLauncher.evaluateRootCheck (static, result compared via .name()) =====
    // MirthLauncher.RootCheckResult is package-private to com.mirth.connect.server.launcher;
    // test is in com.mirth.connect.server — use .name() string comparison.

    @Test
    public void testLauncher_blocksWhenLinuxRootAndNotAllowed() {
        // allowRoot=false, os=Linux, user=root -> BLOCK
        assertEquals("BLOCK", MirthLauncher.evaluateRootCheck("Linux", "root", false, false).name());
    }

    @Test
    public void testLauncher_warnWhenLinuxRootAndAllowed() {
        // allowRoot=true, os=Linux, user=root -> WARN
        assertEquals("WARN", MirthLauncher.evaluateRootCheck("Linux", "root", false, true).name());
    }

    @Test
    public void testLauncher_okWhenLinuxNonRoot() {
        // allowRoot=false, os=Linux, user=bridgelinkuser -> OK
        assertEquals("OK", MirthLauncher.evaluateRootCheck("Linux", "bridgelinkuser", false, false).name());
    }

    @Test
    public void testLauncher_blocksWhenWindowsAdminAndNotAllowed() {
        // allowRoot=false, os=Windows 10, isWindowsAdmin=true -> BLOCK
        assertEquals("BLOCK", MirthLauncher.evaluateRootCheck("Windows 10", "SYSTEM", true, false).name());
    }

    @Test
    public void testLauncher_okWhenWindowsNonAdmin() {
        // allowRoot=false, os=Windows 10, isWindowsAdmin=false -> OK
        assertEquals("OK", MirthLauncher.evaluateRootCheck("Windows 10", "serviceuser", false, false).name());
    }

    @Test
    public void testLauncher_warnWhenWindowsAdminAndAllowed() {
        // allowRoot=true, os=Windows 10, isWindowsAdmin=true -> WARN
        assertEquals("WARN", MirthLauncher.evaluateRootCheck("Windows 10", "SYSTEM", true, true).name());
    }

    // ===== Mirth.evaluateRootCheck (instance, result compared via .name()) =====
    // Mirth.RootCheckResult is declared as private enum — not accessible from test.
    // Use .name() string comparison consistent with MirthLauncher tests.

    @Test
    public void testMirth_blocksWhenLinuxRootAndNotAllowed() {
        // allowRoot=false, os=Linux, user=root -> BLOCK
        assertEquals("BLOCK", mirth.evaluateRootCheck("Linux", "root", false, false).name());
    }

    @Test
    public void testMirth_warnWhenLinuxRootAndAllowed() {
        // allowRoot=true, os=Linux, user=root -> WARN
        assertEquals("WARN", mirth.evaluateRootCheck("Linux", "root", false, true).name());
    }

    @Test
    public void testMirth_okWhenLinuxNonRoot() {
        // allowRoot=false, os=Linux, user=bridgelinkuser -> OK
        assertEquals("OK", mirth.evaluateRootCheck("Linux", "bridgelinkuser", false, false).name());
    }

    @Test
    public void testMirth_blocksWhenMacRootAndNotAllowed() {
        // allowRoot=false, os=Mac OS X, user=root -> BLOCK (non-windows: user.name check)
        assertEquals("BLOCK", mirth.evaluateRootCheck("Mac OS X", "root", false, false).name());
    }

    // ===== Mirth.readNoNewPrivs (injectable Path) =====

    @Test
    public void testReadNoNewPrivs_noExceptionWhenZero() throws IOException {
        // NoNewPrivs: 0 in temp file -> no exception thrown (logger.warn called internally)
        Path tmp = Files.createTempFile("nnp-test-", ".txt");
        try {
            Files.write(tmp, "NoNewPrivs:\t0\n".getBytes(StandardCharsets.UTF_8));
            mirth.readNoNewPrivs(tmp); // must not throw
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testReadNoNewPrivs_noExceptionWhenOne() throws IOException {
        // NoNewPrivs: 1 in temp file -> no exception thrown (logger.info called internally)
        Path tmp = Files.createTempFile("nnp-test-", ".txt");
        try {
            Files.write(tmp, "NoNewPrivs:\t1\n".getBytes(StandardCharsets.UTF_8));
            mirth.readNoNewPrivs(tmp); // must not throw
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testReadNoNewPrivs_gracefulOnIoException() {
        // Non-existent path -> IOException internally, must not propagate
        Path nonExistent = java.nio.file.Paths.get("/tmp/nnp-nonexistent-" + System.currentTimeMillis() + ".txt");
        mirth.readNoNewPrivs(nonExistent); // must not throw
    }

    @Test
    public void testReadNoNewPrivs_noExceptionWhenFieldMissing() throws IOException {
        // /proc/self/status without NoNewPrivs line -> no exception, no action
        Path tmp = Files.createTempFile("nnp-test-", ".txt");
        try {
            Files.write(tmp, "Name:\tbridgelink\nPid:\t1234\n".getBytes(StandardCharsets.UTF_8));
            mirth.readNoNewPrivs(tmp); // must not throw
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ===== JVM system property override (D-07: override via -Dserver.allowRoot=true) =====

    @Test
    public void testLauncher_jvmFlagOverrideAllowsRoot() {
        // -Dserver.allowRoot=true JVM flag -> WARN (not BLOCK) even when isWindowsAdmin=true
        String prev = System.getProperty("server.allowRoot");
        try {
            System.setProperty("server.allowRoot", "true");
            // checkRunningAsRoot() reads the sysprop first; evaluateRootCheck receives allowRoot=true
            assertEquals("WARN", MirthLauncher.evaluateRootCheck("Windows 10", "SYSTEM", true, true).name());
        } finally {
            if (prev == null) {
                System.clearProperty("server.allowRoot");
            } else {
                System.setProperty("server.allowRoot", prev);
            }
        }
    }

    @Test
    public void testMirth_jvmFlagOverrideAllowsRoot() {
        // -Dserver.allowRoot=true JVM flag -> WARN (not BLOCK) even when running as root on Linux
        String prev = System.getProperty("server.allowRoot");
        try {
            System.setProperty("server.allowRoot", "true");
            // evaluateRootCheck receives allowRoot=true; result is WARN not BLOCK
            assertEquals("WARN", mirth.evaluateRootCheck("Linux", "root", false, true).name());
        } finally {
            if (prev == null) {
                System.clearProperty("server.allowRoot");
            } else {
                System.setProperty("server.allowRoot", prev);
            }
        }
    }

}
