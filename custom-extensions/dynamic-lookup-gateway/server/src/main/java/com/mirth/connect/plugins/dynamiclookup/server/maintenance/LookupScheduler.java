package com.mirth.connect.plugins.dynamiclookup.server.maintenance;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LookupScheduler {
	private static final Logger logger = LogManager.getLogger(LookupScheduler.class);

	private final ScheduledExecutorService purgeExec = Executors.newSingleThreadScheduledExecutor(r -> named("Lookup-Purge", r));

	private final PurgeRunner purgeRunner;

	private ScheduledFuture<?> purgeTask;

	public LookupScheduler(PurgeRunner purgeRunner) {
		this.purgeRunner = purgeRunner;
	}

	public synchronized void start() {
		schedulePurge();

		logger.info("LookupScheduler started. tasks: purge={}", purgeTask != null);
	}

	public synchronized void stop() {
		// cancel and shutdown schedules
		cancel(purgeTask);
		safeShutdown(purgeExec);

		logger.info("LookupScheduler stopped.");
	}

	private void schedulePurge() {
		purgeTask = purgeExec.scheduleAtFixedRate(purgeRunner::runOnce, 0, purgeRunner.getFixedRateSeconds(), TimeUnit.SECONDS);
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
