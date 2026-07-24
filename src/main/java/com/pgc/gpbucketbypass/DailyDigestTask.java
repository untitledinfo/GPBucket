package com.pgc.gpbucketbypass;

import org.bukkit.scheduler.BukkitRunnable;

/** Feature 111: once every 24h, summarizes the last day of blocked liquid activity. */
public final class DailyDigestTask extends BukkitRunnable {
    private final DatabaseManager database;
    private final ConfigManager config;
    private final ConsoleReporter console;
    private final WebhookNotifier webhookNotifier;
    public DailyDigestTask(DatabaseManager database, ConfigManager config, ConsoleReporter console, WebhookNotifier webhookNotifier) {
        this.database = database; this.config = config; this.console = console; this.webhookNotifier = webhookNotifier;
    }
    @Override public void run() {
        long blockedToday = database.blockedInLastDay();
        console.info("Daily digest: " + blockedToday + " liquid-griefing attempt(s) blocked in the last 24 hours.");
        if (config.webhookEnabled()) webhookNotifier.notifyDigest(blockedToday);
    }
}
