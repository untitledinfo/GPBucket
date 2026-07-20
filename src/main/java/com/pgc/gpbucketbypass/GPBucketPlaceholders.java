package com.pgc.gpbucketbypass;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * Feature 9: exposes %gpbucket_blocked_count%, %gpbucket_rule%, and
 * %gpbucket_protected%. Only ever constructed after confirming
 * PlaceholderAPI is enabled (see Main), so it is safe to reference this
 * class even though PlaceholderAPI is a soft dependency.
 */
public final class GPBucketPlaceholders extends PlaceholderExpansion {
    private final DatabaseManager database;
    private final ConfigManager config;
    private final GriefPreventionHook griefPrevention;
    private final RegionManager regions;
    public GPBucketPlaceholders(DatabaseManager database, ConfigManager config, GriefPreventionHook griefPrevention, RegionManager regions) {
        this.database = database; this.config = config; this.griefPrevention = griefPrevention; this.regions = regions;
    }
    @Override public String getIdentifier() { return "gpbucket"; }
    @Override public String getAuthor() { return "PGC"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }
    @Override public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";
        return switch (params) {
            case "blocked_count" -> String.valueOf(database.blockedCount(player.getUniqueId()));
            case "rule" -> database.rule(player.getUniqueId()).name();
            case "protected" -> ProtectionQuery.isProtected(config, griefPrevention, regions, player.getLocation()) ? "yes" : "no";
            default -> null;
        };
    }
}
