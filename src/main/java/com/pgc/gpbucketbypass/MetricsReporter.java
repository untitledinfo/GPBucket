package com.pgc.gpbucketbypass;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Feature 99: periodic health/usage summary logged to console for server owners, gated by
 * metrics.enabled. Entirely local — no data leaves the server — so there is nothing to disable
 * for privacy reasons beyond the config toggle itself.
 */
public final class MetricsReporter extends BukkitRunnable {
    private final ConfigManager config;
    private final DatabaseManager database;
    private final RegionManager regions;
    private final ConsoleReporter console;
    private final long startedAt = System.currentTimeMillis();
    public MetricsReporter(ConfigManager config, DatabaseManager database, RegionManager regions, ConsoleReporter console) {
        this.config = config; this.database = database; this.regions = regions; this.console = console;
    }
    @Override public void run() {
        if (!config.metricsEnabled()) return;
        long uptimeMinutes = (System.currentTimeMillis() - startedAt) / 60_000L;
        console.info("Metrics: uptime=" + uptimeMinutes + "m | blocked-total=" + database.auditCount() + " | regions=" + regions.list().size() + " | worlds=" + config.worldCount() + " | panic=" + (config.panicMode() ? "ON" : "off"));
    }
}
