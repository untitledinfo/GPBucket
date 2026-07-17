package com.pgc.gpbucketbypass;

import java.util.logging.Logger;

/** Consistent, readable plugin console output with optional ANSI colors. */
public final class ConsoleReporter {
    private static final String RESET = "\u001B[0m", AQUA = "\u001B[36m", GREEN = "\u001B[32m", YELLOW = "\u001B[33m", RED = "\u001B[31m", GRAY = "\u001B[90m";
    private final Logger logger; private final ConfigManager config;
    public ConsoleReporter(Logger logger, ConfigManager config) { this.logger = logger; this.config = config; }
    public void startup(String version) {
        if (!config.consoleBanner()) return;
        line(AQUA, "╔══════════════════════════════════════════════╗");
        line(AQUA, "║  GPBucket Protection Suite  •  v" + version + pad(version) + "║");
        line(AQUA, "╚══════════════════════════════════════════════╝");
    }
    public void summary() {
        if (!config.consoleSummary()) return;
        info("Protection: scope=" + config.scope() + ", water=" + state(config.shouldBlock(org.bukkit.Material.WATER_BUCKET, false)) + ", lava=" + state(config.shouldBlock(org.bukkit.Material.LAVA_BUCKET, false)) + ", flow=" + state(config.blockFlow()) + ", dispensers=" + state(config.blockDispensers()));
        info("Storage: SQLite " + config.databaseFile() + " | worlds=" + config.worldCount() + " | audit=" + state(config.audit()));
    }
    public void info(String text) { line(GREEN, "[GPBucket] " + text); }
    public void warn(String text) { line(YELLOW, "[GPBucket] " + text); }
    public void error(String text) { line(RED, "[GPBucket] " + text); }
    public void command(String actor, String action) { if (config.consoleAdminCommands()) line(GRAY, "[GPBucket] ADMIN " + actor + " → " + action); }
    private String state(boolean enabled) { return enabled ? "ON" : "OFF"; }
    private void line(String color, String text) { logger.info(config.consoleAnsi() ? color + text + RESET : text); }
    private String pad(String version) { return " ".repeat(Math.max(0, 19 - version.length())); }
}
