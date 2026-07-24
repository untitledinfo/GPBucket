package com.pgc.gpbucketbypass;

import org.bukkit.scheduler.BukkitRunnable;

/** Feature 94: periodically clears timed /gpbucket exempt|block rules (feature 78) once they expire. */
public final class RuleExpiryTask extends BukkitRunnable {
    private final DatabaseManager database;
    private final ConsoleReporter console;
    public RuleExpiryTask(DatabaseManager database, ConsoleReporter console) { this.database = database; this.console = console; }
    @Override public void run() {
        int expired = database.expireTimedRules();
        if (expired > 0) console.info("Cleared " + expired + " expired timed player rule(s).");
    }
}
