package com.pgc.gpbucketbypass;

import org.bukkit.scheduler.BukkitRunnable;

/** Feature 12: periodically purges audit rows older than the configured retention window. */
public final class AuditRetentionTask extends BukkitRunnable {
    private final DatabaseManager database;
    private final ConfigManager config;
    private final ConsoleReporter console;
    public AuditRetentionTask(DatabaseManager database, ConfigManager config, ConsoleReporter console) {
        this.database = database; this.config = config; this.console = console;
    }
    @Override public void run() {
        if (config.auditRetentionDays() <= 0) return;
        int purged = database.purgeOlderThan(config.auditRetentionDays());
        if (purged > 0) console.info("Audit retention purged " + purged + " row(s) older than " + config.auditRetentionDays() + " day(s).");
    }
}
