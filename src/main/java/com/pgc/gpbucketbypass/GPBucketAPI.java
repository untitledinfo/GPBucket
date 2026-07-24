package com.pgc.gpbucketbypass;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Feature 85: lightweight public API surface for other plugins.
 * Example: {@code if (GPBucketAPI.isProtected(loc)) { ... }}
 * Set up automatically by Main during onEnable(); available once GPBucketBypass has loaded.
 */
public final class GPBucketAPI {
    private static volatile Main plugin;
    private static volatile ConfigManager config;
    private static volatile DatabaseManager database;
    private static volatile GriefPreventionHook griefPrevention;
    private static volatile RegionManager regions;
    private GPBucketAPI() { }
    static void register(Main plugin, ConfigManager config, DatabaseManager database, GriefPreventionHook griefPrevention, RegionManager regions) {
        GPBucketAPI.plugin = plugin; GPBucketAPI.config = config; GPBucketAPI.database = database; GPBucketAPI.griefPrevention = griefPrevention; GPBucketAPI.regions = regions;
    }
    /** True if the location currently has GPBucket liquid protection active (claim, region, EVERYWHERE scope, or panic mode). */
    public static boolean isProtected(Location location) {
        if (config == null) return false;
        return ProtectionQuery.isProtected(config, griefPrevention, regions, location);
    }
    /** True if the player currently holds an explicit database EXEMPT rule (permission-based exemptions are not reflected here). */
    public static boolean isDatabaseExempt(Player player) {
        return database != null && database.rule(player.getUniqueId()) == DatabaseManager.Rule.EXEMPT;
    }
    /** Total blocked liquid actions logged for this player. */
    public static long blockedCount(Player player) { return database == null ? 0 : database.blockedCount(player.getUniqueId()); }
    /** True while server-wide panic lockdown (feature 80) is active. */
    public static boolean isPanicMode() { return config != null && config.panicMode(); }
    /** The running GPBucketBypass plugin instance, or null if not yet loaded. */
    public static Main plugin() { return plugin; }
}
