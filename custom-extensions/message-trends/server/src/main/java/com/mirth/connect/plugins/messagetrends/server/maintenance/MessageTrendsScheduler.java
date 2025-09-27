/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.server.maintenance;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.messagetrends.server.core.MessageTrendsBuffer;

public final class MessageTrendsScheduler {
	private static final Logger logger = LogManager.getLogger(MessageTrendsScheduler.class);

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
		// enable buffer
		MessageTrendsBuffer.getInstance().setEnabled(true);

		// initialize and run schedules
		scheduleFlush();
		scheduleRollups();
		schedulePurge();

		logger.info("MessageTrendsScheduler started. tasks: flush={}, rollup={}, purge={}", flushTask != null, rollupTask != null, purgeTask != null);
	}

	public synchronized void stop() {
		// disable buffer
		MessageTrendsBuffer.getInstance().setEnabled(false);

		// cancel and shutdown schedules
		cancel(flushTask);
		cancel(rollupTask);
		cancel(purgeTask);
		safeShutdown(flushExec); // best-effort flush
		safeShutdown(rollupExec);
		safeShutdown(purgeExec);

		logger.info("MessageTrendsScheduler stopped.");
	}

	// — scheduling —

	private void scheduleFlush() {
		long initial = flushRunner.initialDelaySecondsToNextMinuteBoundary();
		flushTask = flushExec.scheduleAtFixedRate(flushRunner::runOnce, initial, flushRunner.getFixedRateSeconds(), TimeUnit.SECONDS);
	}

	private void scheduleRollups() {
		long initial = rollupRunner.initialDelaySeconds();
		long period = rollupRunner.fixedRateSeconds();
		logger.debug("Scheduling rollups: initialDelay={}s, period={}s", initial, period);
		if (initial < 0 || period <= 0) {
			logger.error("Invalid rollup schedule: initialDelay={}, period={}", initial, period);
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

	private static void safeShutdown(ExecutorService es) {
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
