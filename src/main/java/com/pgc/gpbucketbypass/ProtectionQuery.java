package com.pgc.gpbucketbypass;

import org.bukkit.Location;

/** Shared "is this location currently protected" logic used by every protection listener. */
public final class ProtectionQuery {
    private ProtectionQuery() { }
    public static boolean isProtected(ConfigManager config, GriefPreventionHook griefPrevention, RegionManager regions, Location location) {
        if (location == null || location.getWorld() == null || !config.isWorldEnabled(location.getWorld().getName())) return false;
        // Feature 80: panic mode overrides everything and protects every enabled world instantly.
        if (config.panicMode()) return true;
        ConfigManager.Scope scope = config.effectiveScope(location.getWorld().getName());
        boolean baseProtected = scope == ConfigManager.Scope.EVERYWHERE || griefPrevention.isClaimed(location) || (config.worldEditRegions() && regions.contains(location));
        // Feature 82: outside the configured protection schedule window, nothing is protected.
        if (baseProtected && !config.withinSchedule()) return false;
        return baseProtected;
    }
    /** Feature 77: highest-priority region override touching this location for the given flag key, or null if none. */
    public static String regionFlag(RegionManager regions, Location location, String flagKey) {
        return location == null ? null : regions.effectiveFlag(location, flagKey);
    }
}
