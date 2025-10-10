/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.server.maintenance;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractScheduledTask {
	private static final Logger logger = LogManager.getLogger(AbstractScheduledTask.class);

	private final String taskName;
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> future;

	private boolean enabled;
	private long intervalSec;
	private long initialDelaySec;

	protected AbstractScheduledTask(String taskName) {
		this.taskName = taskName;
	}

	/** Subclass implements this method with actual job logic. */
	protected abstract void runOnce() throws Exception;

	/** Idempotent refresh: only reschedules when config actually changes. */
	public synchronized void refresh(boolean enabled, long intervalSec, long initialDelaySec) {
		if (enabled && intervalSec <= 0) {
			logWarn("Interval <= 0; disabling task.");
			enabled = false;
		}

		boolean sameConfig = this.enabled == enabled && this.intervalSec == intervalSec && this.initialDelaySec == initialDelaySec && isRunning();

		if (sameConfig) {
			logDebug("No config change; keeping current schedule.");
			return;
		}

		this.enabled = enabled;
		this.intervalSec = intervalSec;
		this.initialDelaySec = Math.max(0, initialDelaySec);

		stopInternal();

		if (!enabled) {
			logInfo("Disabled.");
			return;
		}

		executor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "Lookup-" + taskName);
			t.setDaemon(true);
			return t;
		});

		future = executor.scheduleAtFixedRate(this::safeRunOnce, this.initialDelaySec, this.intervalSec, TimeUnit.SECONDS);
		logInfo(String.format("Scheduled: interval=%ds, initialDelay=%ds", this.intervalSec, this.initialDelaySec));
	}

	public synchronized void stop() {
		stopInternal();
		logInfo("Stopped.");
	}

	public synchronized boolean isRunning() {
		return future != null && !future.isCancelled() && !future.isDone();
	}

	// --- Internal helpers ---
	private void safeRunOnce() {
		try {
			runOnce();
		} catch (Throwable t) {
			logError("Task execution failed", t);
		}
	}

	private void stopInternal() {
		if (future != null) {
			future.cancel(false);
			future = null;
		}

		if (executor != null) {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException ie) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			} finally {
				executor = null;
			}
		}
	}

	// --- Logging shortcuts ---
	protected void logDebug(String msg) {
		logger.debug("[{}] {}", taskName, msg);
	}

	protected void logInfo(String msg) {
		logger.info("[{}] {}", taskName, msg);
	}

	protected void logWarn(String msg) {
		logger.warn("[{}] {}", taskName, msg);
	}

	protected void logError(String msg, Throwable t) {
		logger.error("[{}] {}", taskName, msg, t);
	}
}
