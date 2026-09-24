package com.mirth.connect.donkey.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
 * Verifies the HikariCP SLF4J to Log4j 2.x bridge using an embedded Derby in-memory
 * pool (HikariCP eagerly validates the driver class and, by default, the initial
 * connection, so a real connectable embedded database is required to exercise the
 * pool-initialization logging path).
 *
 * Split out of Slf4jBridgeTest (IRT-1489, Phase 24, D-03): this file is the only part
 * of that suite that loads embedded Derby, so it alone is added to the JDK-17
 * Derby-test build-config exclude (never collected on 17); the remaining
 * Derby-independent Log4j/SLF4J/Quartz coverage stays in Slf4jBridgeTest and runs on
 * every JDK leg. See donkey/build.xml's exclude.derby.tests wiring.
 */
public class HikariCpDerbyBridgeTest {

    private TestAppender testAppender;
    private Logger hikariLogger;
    private Logger quartzLogger;
    private Logger rootLogger;

    @Before
    public void setUp() {
        System.out.println("\n========================================");
        System.out.println("HikariCP/Derby SLF4J Bridge Test Setup");
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

        System.out.println("Test appenders configured");
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
        System.out.println("Test cleanup complete\n");
    }

    @Test
    public void testHikariCpSlf4jBridge() {
        System.out.println("\n=== Testing HikariCP SLF4J -> Log4j 2.x Bridge ===");

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

            // Verify that HikariCP logged through SLF4J -> Log4j 2.x
            assertFalse("HikariCP should have logged messages via SLF4J bridge",
                       capturedLogs.isEmpty());

            boolean hasHikariLogs = capturedLogs.stream()
                .anyMatch(event -> event.getLoggerName().toLowerCase().contains("hikari"));
            assertTrue("Should have HikariCP logs routed through Log4j 2.x", hasHikariLogs);

            System.out.println("HikariCP -> SLF4J -> log4j-slf4j2-impl -> Log4j 2.x verified");

        } catch (Exception e) {
            fail("HikariCP SLF4J bridge test failed: " + e.getMessage());
        } finally {
            if (dataSource != null) {
                dataSource.close();
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

            System.out.println("Both libraries successfully logging through SLF4J -> Log4j 2.x");

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
