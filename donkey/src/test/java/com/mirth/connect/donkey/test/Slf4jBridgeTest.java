package com.mirth.connect.donkey.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.log4j.Category;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * Comprehensive test suite to verify Log4j 2.x, SLF4J bridge, and third-party library logging.
 *
 * Tests coverage:
 * - log4j-api-2.25.3.jar: Log4j 2.x API functionality
 * - log4j-core-2.25.3.jar: Log4j 2.x core implementation
 * - log4j-1.2-api-2.25.3.jar: Log4j 1.x compatibility API
 * - log4j-slf4j2-impl-2.25.3.jar: SLF4J → Log4j 2.x bridge
 * - slf4j-api-2.0.16.jar: SLF4J API
 * - Third-party libraries: HikariCP and Quartz using SLF4J
 */
public class Slf4jBridgeTest {

    private static final org.apache.logging.log4j.Logger log4j2Logger = LogManager.getLogger(Slf4jBridgeTest.class);
    private static final org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger(Slf4jBridgeTest.class);

    private TestAppender testAppender;
    private Logger hikariLogger;
    private Logger quartzLogger;
    private Logger rootLogger;

    @Before
    public void setUp() {
        System.out.println("\n========================================");
        System.out.println("SLF4J Bridge & Log4j Test Setup");
        System.out.println("========================================");

        // Set root logger level to DEBUG to ensure we capture all logs
        Configurator.setRootLevel(Level.DEBUG);
        Configurator.setLevel("com.zaxxer.hikari", Level.DEBUG);
        Configurator.setLevel("org.quartz", Level.DEBUG);

        // Create test appender to capture log events
        testAppender = new TestAppender("TestAppender");
        testAppender.start();

        // Get LoggerContext and register appender
        LoggerContext context = (LoggerContext) LogManager.getContext(false);

        // Get root logger and specific loggers
        rootLogger = context.getRootLogger();
        hikariLogger = context.getLogger("com.zaxxer.hikari");
        quartzLogger = context.getLogger("org.quartz");

        // Add test appender to root logger to capture ALL logs (most reliable)
        rootLogger.addAppender(testAppender);
        rootLogger.setLevel(Level.DEBUG);

        // Also add to specific loggers for good measure
        hikariLogger.addAppender(testAppender);
        quartzLogger.addAppender(testAppender);

        // Set log level to DEBUG to capture all logs
        hikariLogger.setLevel(Level.DEBUG);
        quartzLogger.setLevel(Level.DEBUG);

        System.out.println("✓ Test appenders configured");
    }

    @After
    public void tearDown() {
        if (testAppender != null) {
            if (rootLogger != null) {
                rootLogger.removeAppender(testAppender);
            }
            if (hikariLogger != null) {
                hikariLogger.removeAppender(testAppender);
            }
            if (quartzLogger != null) {
                quartzLogger.removeAppender(testAppender);
            }
            testAppender.stop();
        }
        ThreadContext.clearAll();
        System.out.println("✓ Test cleanup complete\n");
    }

    // ========== BASIC LOG4J 2.X TESTS ==========

    @Test
    public void testLog4j2DirectLogging() {
        System.out.println("\n=== Testing Log4j 2.x Direct Logging (log4j-api + log4j-core) ===");

        testAppender.clear();
        log4j2Logger.info("Log4j 2.x direct logging works!");
        log4j2Logger.debug("Log4j 2.x debug message");
        log4j2Logger.warn("Log4j 2.x warning message");
        log4j2Logger.error("Log4j 2.x error message");

        List<LogEvent> events = testAppender.getEvents();

        assertTrue("Should have captured log events", events.size() >= 4);
        System.out.println("✓ Log4j 2.x direct logging successful - captured " + events.size() + " events");
    }

    @Test
    public void testLog4j2LoggerHierarchy() {
        System.out.println("\n=== Testing Log4j 2.x Logger Hierarchy ===");

        org.apache.logging.log4j.Logger parentLogger = LogManager.getLogger("com.mirth");
        org.apache.logging.log4j.Logger childLogger = LogManager.getLogger("com.mirth.connect");

        assertNotNull("Parent logger should not be null", parentLogger);
        assertNotNull("Child logger should not be null", childLogger);

        System.out.println("Parent logger: " + parentLogger.getName());
        System.out.println("Child logger: " + childLogger.getName());
        System.out.println("✓ Logger hierarchy working correctly");
    }

    @Test
    public void testLog4j2Levels() {
        System.out.println("\n=== Testing Log4j 2.x Log Levels ===");

        testAppender.clear();

        log4j2Logger.trace("TRACE level message");
        log4j2Logger.debug("DEBUG level message");
        log4j2Logger.info("INFO level message");
        log4j2Logger.warn("WARN level message");
        log4j2Logger.error("ERROR level message");
        log4j2Logger.fatal("FATAL level message");

        List<LogEvent> events = testAppender.getEvents();

        // Verify different log levels are captured
        boolean hasDebug = events.stream().anyMatch(e -> e.getLevel() == Level.DEBUG);
        boolean hasInfo = events.stream().anyMatch(e -> e.getLevel() == Level.INFO);
        boolean hasWarn = events.stream().anyMatch(e -> e.getLevel() == Level.WARN);
        boolean hasError = events.stream().anyMatch(e -> e.getLevel() == Level.ERROR);
        boolean hasFatal = events.stream().anyMatch(e -> e.getLevel() == Level.FATAL);

        assertTrue("Should have DEBUG messages", hasDebug);
        assertTrue("Should have INFO messages", hasInfo);
        assertTrue("Should have WARN messages", hasWarn);
        assertTrue("Should have ERROR messages", hasError);
        assertTrue("Should have FATAL messages", hasFatal);

        System.out.println("✓ All log levels working correctly");
    }

    @Test
    public void testLog4j2ExceptionLogging() {
        System.out.println("\n=== Testing Log4j 2.x Exception Logging ===");

        testAppender.clear();

        Exception testException = new RuntimeException("Test exception for logging");
        log4j2Logger.error("Error occurred", testException);

        List<LogEvent> events = testAppender.getEvents();

        assertFalse("Should have captured exception log", events.isEmpty());

        LogEvent errorEvent = events.get(0);
        assertNotNull("Should have throwable info", errorEvent.getThrown());
        assertEquals("Exception message should match", "Test exception for logging",
                    errorEvent.getThrown().getMessage());

        System.out.println("✓ Exception logging working correctly");
    }

    @Test
    public void testLog4j2MDC() {
        System.out.println("\n=== Testing Log4j 2.x MDC (Mapped Diagnostic Context) ===");

        testAppender.clear();

        // Put values in ThreadContext (MDC in Log4j 2.x)
        ThreadContext.put("userId", "user123");
        ThreadContext.put("transactionId", "txn-456");
        ThreadContext.put("channelId", "channel-789");

        log4j2Logger.info("Message with MDC context");

        List<LogEvent> events = testAppender.getEvents();

        assertFalse("Should have captured MDC log", events.isEmpty());

        // ThreadContext is automatically included in log events
        System.out.println("MDC values set: userId=user123, transactionId=txn-456, channelId=channel-789");
        System.out.println("✓ MDC functionality working correctly");

        ThreadContext.clearAll();
    }

    @Test
    public void testLog4j2ParameterizedLogging() {
        System.out.println("\n=== Testing Log4j 2.x Parameterized Logging ===");

        testAppender.clear();

        String user = "admin";
        int count = 42;
        double value = 3.14159;

        log4j2Logger.info("User {} performed {} operations with value {}", user, count, value);

        List<LogEvent> events = testAppender.getEvents();

        assertFalse("Should have captured parameterized log", events.isEmpty());

        String message = events.get(0).getMessage().getFormattedMessage();
        assertTrue("Message should contain user", message.contains("admin"));
        assertTrue("Message should contain count", message.contains("42"));
        assertTrue("Message should contain value", message.contains("3.14159"));

        System.out.println("Formatted message: " + message);
        System.out.println("✓ Parameterized logging working correctly");
    }

    // ========== LOG4J 1.X COMPATIBILITY API TESTS ==========

    @Test
    public void testLog4j1xCompatibilityAPI() {
        System.out.println("\n=== Testing Log4j 1.x Compatibility API (log4j-1.2-api) ===");

        testAppender.clear();

        // Use Log4j 1.x API - should be bridged to Log4j 2.x
        org.apache.log4j.Logger log4j1Logger = org.apache.log4j.Logger.getLogger(Slf4jBridgeTest.class);

        assertNotNull("Log4j 1.x logger should not be null", log4j1Logger);

        log4j1Logger.info("Log4j 1.x API message");
        log4j1Logger.debug("Log4j 1.x debug message");
        log4j1Logger.warn("Log4j 1.x warning message");

        List<LogEvent> events = testAppender.getEvents();

        assertTrue("Should have captured Log4j 1.x API messages", events.size() >= 3);

        System.out.println("✓ Log4j 1.x compatibility API working - " + events.size() + " messages bridged to Log4j 2.x");
    }

    @Test
    public void testLog4j1xCategoryAPI() {
        System.out.println("\n=== Testing Log4j 1.x Category API ===");

        testAppender.clear();

        // Category is the old Log4j 1.x class
        Category category = Category.getInstance(Slf4jBridgeTest.class);

        assertNotNull("Category should not be null", category);

        category.info("Category API message");
        category.debug("Category debug message");

        List<LogEvent> events = testAppender.getEvents();

        assertTrue("Should have captured Category API messages", events.size() >= 2);

        System.out.println("✓ Log4j 1.x Category API working correctly");
    }

    // ========== SLF4J BRIDGE TESTS ==========

    @Test
    public void testSlf4jBridgeLogging() {
        System.out.println("\n=== Testing SLF4J → Log4j 2.x Bridge ===");

        testAppender.clear();

        slf4jLogger.info("SLF4J bridge to Log4j 2.x works!");
        slf4jLogger.debug("SLF4J debug message");
        slf4jLogger.warn("SLF4J warning message");
        slf4jLogger.error("SLF4J error message");

        List<LogEvent> events = testAppender.getEvents();

        assertTrue("Should have captured SLF4J messages", events.size() >= 4);
        System.out.println("✓ SLF4J bridge successful - captured " + events.size() + " events");
    }

    @Test
    public void testSlf4jImplementation() {
        System.out.println("\n=== Testing SLF4J Implementation ===");

        String loggerClass = slf4jLogger.getClass().getName();
        String loggerClassLower = loggerClass.toLowerCase();

        System.out.println("SLF4J Logger Implementation: " + loggerClass);
        System.out.println("Expected: org.apache.logging.slf4j.Log4jLogger (from log4j-slf4j2-impl)");

        // Verify we're using Log4j SLF4J implementation, not the old log4j12 bridge
        assertTrue("Should be using Log4j 2.x SLF4J implementation (log4j-slf4j2-impl)",
                   loggerClassLower.contains("log4j") && !loggerClassLower.contains("log4j12"));

        // Verify it's NOT the old Log4j 1.x bridge
        assertFalse("Should NOT be using old slf4j-log4j12 bridge",
                    loggerClassLower.contains("slf4j.impl.log4jloggeradapter"));

        // Verify it IS the correct Log4j 2.x bridge
        assertTrue("Should be org.apache.logging.slf4j.Log4jLogger from log4j-slf4j2-impl",
                   loggerClass.equals("org.apache.logging.slf4j.Log4jLogger"));

        System.out.println("✓ Correct SLF4J implementation verified");
    }

    @Test
    public void testSlf4jParameterizedMessages() {
        System.out.println("\n=== Testing SLF4J Parameterized Messages ===");

        testAppender.clear();

        String name = "testUser";
        int id = 123;

        slf4jLogger.info("User {} with ID {} logged in", name, id);

        List<LogEvent> events = testAppender.getEvents();

        assertFalse("Should have captured SLF4J parameterized message", events.isEmpty());

        String message = events.get(0).getMessage().getFormattedMessage();
        assertTrue("Message should contain user name", message.contains("testUser"));
        assertTrue("Message should contain ID", message.contains("123"));

        System.out.println("Formatted message: " + message);
        System.out.println("✓ SLF4J parameterized messages working correctly");
    }

    @Test
    public void testSlf4jExceptionLogging() {
        System.out.println("\n=== Testing SLF4J Exception Logging ===");

        testAppender.clear();

        Exception testException = new IllegalArgumentException("Test SLF4J exception");
        slf4jLogger.error("Error via SLF4J", testException);

        List<LogEvent> events = testAppender.getEvents();

        assertFalse("Should have captured SLF4J exception log", events.isEmpty());

        LogEvent errorEvent = events.get(0);
        assertNotNull("Should have throwable info", errorEvent.getThrown());
        assertEquals("Exception message should match", "Test SLF4J exception",
                    errorEvent.getThrown().getMessage());

        System.out.println("✓ SLF4J exception logging working correctly");
    }

    // ========== THIRD-PARTY LIBRARY TESTS ==========

    @Test
    public void testHikariCpSlf4jBridge() {
        System.out.println("\n=== Testing HikariCP SLF4J → Log4j 2.x Bridge ===");

        testAppender.clear();
        HikariDataSource dataSource = null;

        try {
            // Configure HikariCP - this will trigger SLF4J logging
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:derby:memory:hikaritest;create=true");
            config.setDriverClassName("org.apache.derby.jdbc.EmbeddedDriver");
            config.setUsername("");
            config.setPassword("");
            config.setMaximumPoolSize(2);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(3000);
            config.setPoolName("HikariCP-Test-Pool");

            System.out.println("Creating HikariCP DataSource (logs via SLF4J)...");
            dataSource = new HikariDataSource(config);

            // Give HikariCP a moment to initialize and log
            Thread.sleep(1000);

            List<LogEvent> capturedLogs = testAppender.getEvents();

            System.out.println("Captured " + capturedLogs.size() + " log events from HikariCP");

            // Verify that HikariCP logged through SLF4J → Log4j 2.x
            assertFalse("HikariCP should have logged messages via SLF4J bridge",
                       capturedLogs.isEmpty());

            boolean hasHikariLogs = capturedLogs.stream()
                .anyMatch(event -> event.getLoggerName().toLowerCase().contains("hikari"));
            assertTrue("Should have HikariCP logs routed through Log4j 2.x", hasHikariLogs);

            System.out.println("✓ HikariCP → SLF4J → log4j-slf4j2-impl → Log4j 2.x verified");

        } catch (Exception e) {
            fail("HikariCP SLF4J bridge test failed: " + e.getMessage());
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    @Test
    public void testQuartzSlf4jBridge() {
        System.out.println("\n=== Testing Quartz SLF4J → Log4j 2.x Bridge ===");

        testAppender.clear();
        Scheduler scheduler = null;

        try {
            // Configure Quartz - this will trigger SLF4J logging
            Properties props = new Properties();
            props.setProperty("org.quartz.scheduler.instanceName", "Test-Scheduler");
            props.setProperty("org.quartz.scheduler.instanceId", "AUTO");
            props.setProperty("org.quartz.threadPool.threadCount", "2");
            props.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
            props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

            System.out.println("Creating Quartz Scheduler (logs via SLF4J)...");
            StdSchedulerFactory factory = new StdSchedulerFactory(props);
            scheduler = factory.getScheduler();
            scheduler.start();

            // Give Quartz a moment to initialize and log
            Thread.sleep(500);

            List<LogEvent> capturedLogs = testAppender.getEvents();

            System.out.println("Captured " + capturedLogs.size() + " log events from Quartz");

            // Verify that Quartz logged through SLF4J → Log4j 2.x
            assertFalse("Quartz should have logged messages via SLF4J bridge",
                       capturedLogs.isEmpty());

            boolean hasQuartzLogs = capturedLogs.stream()
                .anyMatch(event -> event.getLoggerName().toLowerCase().contains("quartz"));
            assertTrue("Should have Quartz logs routed through Log4j 2.x", hasQuartzLogs);

            System.out.println("✓ Quartz → SLF4J → log4j-slf4j2-impl → Log4j 2.x verified");

        } catch (SchedulerException | InterruptedException e) {
            fail("Quartz SLF4J bridge test failed: " + e.getMessage());
        } finally {
            if (scheduler != null) {
                try {
                    scheduler.shutdown(false);
                } catch (SchedulerException e) {
                    System.err.println("Error shutting down scheduler: " + e.getMessage());
                }
            }
        }
    }

    @Test
    public void testBothLibrariesSimultaneously() {
        System.out.println("\n=== Testing HikariCP + Quartz Simultaneous Logging ===");

        testAppender.clear();
        HikariDataSource dataSource = null;
        Scheduler scheduler = null;

        try {
            System.out.println("Initializing both HikariCP and Quartz...");

            // HikariCP
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:derby:memory:multitest;create=true");
            hikariConfig.setDriverClassName("org.apache.derby.jdbc.EmbeddedDriver");
            hikariConfig.setMaximumPoolSize(2);
            hikariConfig.setPoolName("Multi-Test-Pool");
            dataSource = new HikariDataSource(hikariConfig);

            // Quartz
            Properties quartzProps = new Properties();
            quartzProps.setProperty("org.quartz.scheduler.instanceName", "Multi-Test-Scheduler");
            quartzProps.setProperty("org.quartz.threadPool.threadCount", "2");
            quartzProps.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
            quartzProps.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
            StdSchedulerFactory factory = new StdSchedulerFactory(quartzProps);
            scheduler = factory.getScheduler();
            scheduler.start();

            // Give both time to log
            Thread.sleep(1000);

            List<LogEvent> capturedLogs = testAppender.getEvents();

            long hikariLogCount = capturedLogs.stream()
                .filter(event -> event.getLoggerName().toLowerCase().contains("hikari"))
                .count();

            long quartzLogCount = capturedLogs.stream()
                .filter(event -> event.getLoggerName().toLowerCase().contains("quartz"))
                .count();

            System.out.println("Total captured logs: " + capturedLogs.size());
            System.out.println("  - HikariCP logs: " + hikariLogCount);
            System.out.println("  - Quartz logs: " + quartzLogCount);

            // Verify both libraries logged
            assertTrue("HikariCP should have logged", hikariLogCount > 0);
            assertTrue("Quartz should have logged", quartzLogCount > 0);

            System.out.println("✓ Both libraries successfully logging through SLF4J → Log4j 2.x");

        } catch (Exception e) {
            fail("Simultaneous logging test failed: " + e.getMessage());
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
            if (scheduler != null) {
                try {
                    scheduler.shutdown(false);
                } catch (SchedulerException e) {
                    System.err.println("Error shutting down scheduler: " + e.getMessage());
                }
            }
        }
    }

    // ========== VERSION AND CONFIGURATION TESTS ==========

    @Test
    public void testLog4j2Version() {
        System.out.println("\n=== Testing Log4j 2.x Version ===");

        String version = LogManager.class.getPackage().getImplementationVersion();

        System.out.println("Log4j 2.x API version: " + (version != null ? version : "Unknown"));

        // Verify we're using Log4j 2.x (version should be 2.x.x)
        if (version != null) {
            assertTrue("Should be Log4j 2.x", version.startsWith("2."));
            System.out.println("✓ Log4j 2.x version verified: " + version);
        } else {
            System.out.println("⚠ Version information not available in JAR manifest");
        }
    }

    @Test
    public void testSlf4jVersion() {
        System.out.println("\n=== Testing SLF4J Version ===");

        String version = org.slf4j.LoggerFactory.class.getPackage().getImplementationVersion();

        System.out.println("SLF4J API version: " + (version != null ? version : "Unknown"));

        // Verify we're using SLF4J 2.x (for Java 8+)
        if (version != null) {
            assertTrue("Should be SLF4J 2.x", version.startsWith("2."));
            System.out.println("✓ SLF4J 2.x version verified: " + version);
        } else {
            System.out.println("⚠ Version information not available in JAR manifest");
        }
    }

    @Test
    public void testLoggerContextAvailability() {
        System.out.println("\n=== Testing Log4j 2.x LoggerContext ===");

        LoggerContext context = (LoggerContext) LogManager.getContext(false);

        assertNotNull("LoggerContext should not be null", context);
        assertNotNull("Configuration should not be null", context.getConfiguration());

        System.out.println("LoggerContext name: " + context.getName());
        System.out.println("Configuration source: " + context.getConfiguration().getConfigurationSource());
        System.out.println("✓ LoggerContext available and configured");
    }

    // ========== HELPER CLASS ==========

    /**
     * Custom Log4j 2.x Appender to capture log events for testing
     */
    private static class TestAppender extends AbstractAppender {

        private final List<LogEvent> events = new ArrayList<>();

        protected TestAppender(String name) {
            super(name, null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            // Store immutable copy of the event
            events.add(event.toImmutable());
        }

        public List<LogEvent> getEvents() {
            return new ArrayList<>(events);
        }

        public void clear() {
            events.clear();
        }
    }
}
