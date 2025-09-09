package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MessageTrendsScheduler {
	private static final Logger log = LogManager.getLogger(MessageTrendsScheduler.class);

	private final ScheduledExecutorService flushExec = Executors.newSingleThreadScheduledExecutor(r -> named("MT-Flush", r));
	private final ScheduledExecutorService rollupExec = Executors.newSingleThreadScheduledExecutor(r -> named("MT-Rollup", r));
	private final ScheduledExecutorService purgeExec = Executors.newSingleThreadScheduledExecutor(r -> named("MT-Purge", r));

	private final MinuteFlushRunner flushRunner;
	private final RollupRunner rollupRunner;
	private final PurgeRunner purgeRunner;

	private ScheduledFuture<?> flushTask, rollupTask, purgeTask;

	public MessageTrendsScheduler(MinuteFlushRunner flushRunner, RollupRunner rollupRunner, PurgeRunner purgeRunner) {
		this.flushRunner = flushRunner;
		this.rollupRunner = rollupRunner;
		this.purgeRunner = purgeRunner;
	}

	public synchronized void start() {
		log.debug("Starting MessageTrendsScheduler...");
		scheduleFlush();
		scheduleRollups();
		schedulePurge();
		log.info("MessageTrendsScheduler started. tasks: flush={}, rollup={}, purge={}", flushTask != null, rollupTask != null, purgeTask != null);
	}

	public synchronized void stop() {
		cancel(flushTask);
		cancel(rollupTask);
		cancel(purgeTask);
		safeShutdown(flushExec, true); // best-effort flush
		safeShutdown(rollupExec, false);
		safeShutdown(purgeExec, false);
		log.info("MessageTrendsScheduler stopped.");
	}

	// — scheduling —

	private void scheduleFlush() {
		long initial = flushRunner.initialDelaySecondsToNextMinuteBoundary();
		flushTask = flushExec.scheduleAtFixedRate(flushRunner::runOnce, initial, flushRunner.getFixedRateSeconds(), TimeUnit.SECONDS);
	}

	private void scheduleRollups() {
		long initial = rollupRunner.initialDelaySeconds();
		long period = rollupRunner.fixedRateSeconds();
		log.debug("Scheduling rollups: initialDelay={}s, period={}s", initial, period);
		if (initial < 0 || period <= 0) {
			log.error("Invalid rollup schedule: initialDelay={}, period={}", initial, period);
			return;
		}

		rollupTask = rollupExec.scheduleAtFixedRate(rollupRunner::runOnce, rollupRunner.initialDelaySeconds(), rollupRunner.fixedRateSeconds(), TimeUnit.SECONDS);
	}

	private void schedulePurge() {
		purgeTask = purgeExec.scheduleAtFixedRate(purgeRunner::runOnce, purgeRunner.initialDelaySeconds(), purgeRunner.fixedRateSeconds(), TimeUnit.SECONDS);
	}

	// — utils —
	private static Thread named(String name, Runnable r) {
		Thread t = new Thread(r, name);
		t.setDaemon(true);
		return t;
	}

	private static void cancel(ScheduledFuture<?> f) {
		if (f != null) {
			f.cancel(false);
		}
	}

	private static void safeShutdown(ExecutorService es, boolean finalFlush) {
		es.shutdown();
		try {
			if (!es.awaitTermination(5, TimeUnit.SECONDS)) {
				es.shutdownNow();
			}
		} catch (InterruptedException ie) {
			es.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
