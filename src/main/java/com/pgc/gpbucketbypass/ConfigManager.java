package com.pgc.gpbucketbypass;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Cached configuration plus safe runtime toggles used by the GUI. */
public final class ConfigManager {
    public enum Scope { CLAIMS, EVERYWHERE }
    private final Main plugin;
    private boolean debug, blockWater, blockLava, blockFill, blockEmpty, blockFlow, blockDispensers, blockCreative, worldEditRegions;
    private boolean permissionExemptions, databaseExemptions, audit, notifyPlayer, notifyStaff, actionBar, blockedSound, consoleBanner, consoleLogo, consoleAnsi, consoleCommands, consoleSummary, consoleStages;
    private long cooldownMs;
    private Scope scope;
    private String databaseFile, blockedMessage, cooldownMessage, guiUpdatedMessage, inspectionHeader, noAuditHistory, noPermissionMessage, playerNotFoundMessage, ruleUpdatedMessage, staffPermission;
    private Set<String> worlds = Set.of();
    public ConfigManager(Main plugin) { this.plugin = plugin; }
    public void load() {
        plugin.saveDefaultConfig(); plugin.reloadConfig(); FileConfiguration c = plugin.getConfig();
        debug = c.getBoolean("debug", false); worldEditRegions = c.getBoolean("worldedit-regions-enabled", true); blockWater = c.getBoolean("block-water", true); blockLava = c.getBoolean("block-lava", true);
        blockFill = c.getBoolean("block-fill", true); blockEmpty = c.getBoolean("block-empty", true); blockFlow = c.getBoolean("block-fluid-flow", true);
        blockDispensers = c.getBoolean("block-dispensers", true); blockCreative = c.getBoolean("block-creative-mode", true);
        permissionExemptions = c.getBoolean("respect-permission-exemptions", true); databaseExemptions = c.getBoolean("respect-database-exemptions", true);
        audit = c.getBoolean("log-blocked-actions", true); notifyPlayer = c.getBoolean("notify-player", true); notifyStaff = c.getBoolean("notify-staff", false); actionBar = c.getBoolean("blocked-actionbar", true); blockedSound = c.getBoolean("blocked-sound", true);
        consoleBanner = c.getBoolean("console.startup-banner", true); consoleLogo = c.getBoolean("console.big-logo", true); consoleAnsi = c.getBoolean("console.ansi-colors", true); consoleCommands = c.getBoolean("console.log-admin-commands", true); consoleSummary = c.getBoolean("console.log-protection-summary", true); consoleStages = c.getBoolean("console.log-load-stages", true);
        cooldownMs = Math.max(0, c.getLong("bucket-cooldown-ms", 0)); databaseFile = c.getString("database-file", "data.db");
        staffPermission = c.getString("staff-notification-permission", "gpbucket.notify");
        blockedMessage = color(c.getString("messages.blocked", "&cLava and water buckets are disabled in this protected area."));
        cooldownMessage = color(c.getString("messages.cooldown", "&ePlease wait before using another liquid bucket."));
        guiUpdatedMessage = color(c.getString("messages.gui-updated", "&aSetting updated."));
        inspectionHeader = color(c.getString("messages.inspection-header", "&b&lGPBucket Protection Inspection")); noAuditHistory = color(c.getString("messages.no-audit-history", "&7No blocked liquid actions are recorded for this player."));
        noPermissionMessage = color(c.getString("messages.no-permission", "&cYou do not have permission to do that."));
        playerNotFoundMessage = color(c.getString("messages.player-not-found", "&cThat player must be online."));
        ruleUpdatedMessage = color(c.getString("messages.rule-updated", "&aRule for &f%player% &ais now &e%rule%&a."));
        try { scope = Scope.valueOf(c.getString("protection-scope", "CLAIMS").toUpperCase(Locale.ROOT)); } catch (Exception ignored) { scope = Scope.CLAIMS; }
        Set<String> names = new HashSet<>(); for (String name : c.getStringList("worlds")) if (name != null && !name.isBlank()) names.add(name); worlds = Set.copyOf(names);
    }
    public boolean isWorldEnabled(String world) { return worlds.contains(world); }
    public boolean shouldBlock(Material liquidBucket, boolean filling) { return (filling ? blockFill : blockEmpty) && ((liquidBucket == Material.WATER_BUCKET && blockWater) || (liquidBucket == Material.LAVA_BUCKET && blockLava)); }
    public boolean shouldBlockLiquid(Material fluid) { return (fluid == Material.WATER && blockWater) || (fluid == Material.LAVA && blockLava); }
    public boolean permissionExemptions() { return permissionExemptions; } public boolean databaseExemptions() { return databaseExemptions; }
    public boolean audit() { return audit; } public boolean blockFlow() { return blockFlow; } public boolean blockDispensers() { return blockDispensers; }
    public boolean blockCreative() { return blockCreative; } public boolean notifyPlayer() { return notifyPlayer; } public boolean notifyStaff() { return notifyStaff; } public boolean actionBar() { return actionBar; } public boolean blockedSound() { return blockedSound; }
    public long cooldownMs() { return cooldownMs; } public Scope scope() { return scope; } public String databaseFile() { return databaseFile; }
    public String blockedMessage() { return blockedMessage; } public String cooldownMessage() { return cooldownMessage; } public String guiUpdatedMessage() { return guiUpdatedMessage; } public String staffPermission() { return staffPermission; }
    public boolean debug() { return debug; }
    public boolean worldEditRegions() { return worldEditRegions; }
    public boolean consoleBanner() { return consoleBanner; } public boolean consoleBigLogo() { return consoleLogo; } public boolean consoleAnsi() { return consoleAnsi; } public boolean consoleAdminCommands() { return consoleCommands; } public boolean consoleSummary() { return consoleSummary; } public boolean consoleLoadStages() { return consoleStages; }
    public int worldCount() { return worlds.size(); }
    public String noPermissionMessage() { return noPermissionMessage; } public String playerNotFoundMessage() { return playerNotFoundMessage; }
    public String inspectionHeader() { return inspectionHeader; } public String noAuditHistory() { return noAuditHistory; }
    public String ruleUpdatedMessage(String player, String rule) { return ruleUpdatedMessage.replace("%player%", player).replace("%rule%", rule); }
    public void toggle(String key) {
        FileConfiguration c = plugin.getConfig();
        if (key.equals("scope")) c.set("protection-scope", scope == Scope.CLAIMS ? "EVERYWHERE" : "CLAIMS");
        else { String path = switch (key) { case "water" -> "block-water"; case "lava" -> "block-lava"; case "fill" -> "block-fill"; case "empty" -> "block-empty"; case "flow" -> "block-fluid-flow"; case "dispenser" -> "block-dispensers"; case "creative" -> "block-creative-mode"; case "audit" -> "log-blocked-actions"; case "notify" -> "notify-player"; default -> null; }; if (path == null) return; c.set(path, !c.getBoolean(path, true)); }
        plugin.saveConfig(); load();
    }
    private static String color(String input) { return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input); }
}
