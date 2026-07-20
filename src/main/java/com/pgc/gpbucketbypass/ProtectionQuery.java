package com.pgc.gpbucketbypass;

import org.bukkit.Location;

/** Shared "is this location currently protected" logic used by every protection listener. */
public final class ProtectionQuery {
    private ProtectionQuery() { }
    public static boolean isProtected(ConfigManager config, GriefPreventionHook griefPrevention, RegionManager regions, Location location) {
        if (location == null || location.getWorld() == null || !config.isWorldEnabled(location.getWorld().getName())) return false;
        ConfigManager.Scope scope = config.effectiveScope(location.getWorld().getName());
        return scope == ConfigManager.Scope.EVERYWHERE || griefPrevention.isClaimed(location) || (config.worldEditRegions() && regions.contains(location));
    }
}
