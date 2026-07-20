package com.pgc.gpbucketbypass;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Cached configuration plus safe runtime toggles used by the GUI. */
public final class ConfigManager {
    public enum Scope { CLAIMS, EVERYWHERE }
    private final Main plugin;
    private boolean debug, blockWater, blockLava, blockFill, blockEmpty, blockFlow, blockDispensers, blockCreative, worldEditRegions;
    private boolean permissionExemptions, databaseExemptions, audit, notifyPlayer, notifyStaff, actionBar, blockedSound, consoleBanner, consoleLogo, consoleAnsi, consoleCommands, consoleSummary, consoleStages;
    // --- HUGE UPDATE fields ---
    private boolean blockFireSpread, blockFlintSteel, blockCauldron, blockPowderSnow, exemptSound, joinTutorialMessage;
    private boolean webhookEnabled, autoBlockEnabled, updateCheckerEnabled;
    private long combatTagMs, messageCooldownMs, regionCreateCooldownMs;
    private int autoBlockThreshold, autoBlockWindowSeconds, autoBlockDurationMinutes, auditRetentionDays;
    private String webhookUrl, updateCheckerRepo;
    private Map<String, Scope> worldScopeOverrides = Map.of();
    private long cooldownMs;
    private Scope scope;
    private String databaseFile, blockedMessage, cooldownMessage, guiUpdatedMessage, inspectionHeader, noAuditHistory, noPermissionMessage, playerNotFoundMessage, ruleUpdatedMessage, staffPermission;
    private String combatTagMessage, autoBlockedMessage;
    private static final int CURRENT_CONFIG_VERSION = 2;
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
        combatTagMessage = color(c.getString("messages.combat-tag", "&cYou cannot use liquid buckets while in combat."));
        autoBlockedMessage = color(c.getString("messages.auto-blocked", "&cYou have been temporarily blocked for repeated violations."));
        try { scope = Scope.valueOf(c.getString("protection-scope", "CLAIMS").toUpperCase(Locale.ROOT)); } catch (Exception ignored) { scope = Scope.CLAIMS; }
        Set<String> names = new HashSet<>(); for (String name : c.getStringList("worlds")) if (name != null && !name.isBlank()) names.add(name); worlds = Set.copyOf(names);

        // --- HUGE UPDATE loading ---
        blockFireSpread = c.getBoolean("block-fire-spread", true);
        blockFlintSteel = c.getBoolean("block-flint-steel", true);
        blockCauldron = c.getBoolean("block-cauldron", true);
        blockPowderSnow = c.getBoolean("block-powder-snow", true);
        exemptSound = c.getBoolean("exempt-sound", false);
        joinTutorialMessage = c.getBoolean("join-tutorial-message", true);
        combatTagMs = Math.max(0, c.getLong("combat-tag-seconds", 0)) * 1000L;
        messageCooldownMs = Math.max(0, c.getLong("message-cooldown-ms", 1500));
        regionCreateCooldownMs = Math.max(0, c.getLong("region-create-cooldown-ms", 5000));
        auditRetentionDays = Math.max(0, c.getInt("audit-retention-days", 90));
        webhookEnabled = c.getBoolean("webhook.enabled", false);
        webhookUrl = c.getString("webhook.url", "");
        autoBlockEnabled = c.getBoolean("auto-block.enabled", true);
        autoBlockThreshold = Math.max(1, c.getInt("auto-block.threshold", 6));
        autoBlockWindowSeconds = Math.max(1, c.getInt("auto-block.window-seconds", 30));
        autoBlockDurationMinutes = Math.max(1, c.getInt("auto-block.duration-minutes", 10));
        updateCheckerEnabled = c.getBoolean("update-checker.enabled", true);
        updateCheckerRepo = c.getString("update-checker.repo", "PGC-mc/GPBucketBypass");
        Map<String, Scope> overrides = new HashMap<>();
        var overrideSection = c.getConfigurationSection("worlds-scope-override");
        if (overrideSection != null) for (String world : overrideSection.getKeys(false)) {
            try { overrides.put(world, Scope.valueOf(String.valueOf(overrideSection.get(world)).toUpperCase(Locale.ROOT))); } catch (Exception ignored) { }
        }
        worldScopeOverrides = Map.copyOf(overrides);

        // Feature 22: back up and stamp the config the first time it is loaded at an older version.
        int configVersion = c.getInt("config-version", 1);
        if (configVersion < CURRENT_CONFIG_VERSION) {
            backupConfig();
            c.set("config-version", CURRENT_CONFIG_VERSION);
            plugin.saveConfig();
        }
    }
    private void backupConfig() {
        try {
            java.io.File source = new java.io.File(plugin.getDataFolder(), "config.yml");
            if (!source.exists()) return;
            java.io.File backup = new java.io.File(plugin.getDataFolder(), "config-backup-" + System.currentTimeMillis() + ".yml");
            java.nio.file.Files.copy(source.toPath(), backup.toPath());
        } catch (Exception ignored) { }
    }
    public boolean isWorldEnabled(String world) { return worlds.contains(world); }
    public boolean shouldBlock(Material liquidBucket, boolean filling) {
        if (!(filling ? blockFill : blockEmpty)) return false;
        if (liquidBucket == Material.WATER_BUCKET) return blockWater;
        if (liquidBucket == Material.LAVA_BUCKET) return blockLava;
        if (liquidBucket == Material.POWDER_SNOW_BUCKET) return blockPowderSnow;
        return false;
    }
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
    // --- HUGE UPDATE getters ---
    public boolean blockFireSpread() { return blockFireSpread; }
    public boolean blockFlintSteel() { return blockFlintSteel; }
    public boolean blockCauldron() { return blockCauldron; }
    public boolean blockPowderSnow() { return blockPowderSnow; }
    public boolean exemptSound() { return exemptSound; }
    public boolean joinTutorialMessage() { return joinTutorialMessage; }
    public long combatTagMs() { return combatTagMs; }
    public long messageCooldownMs() { return messageCooldownMs; }
    public long regionCreateCooldownMs() { return regionCreateCooldownMs; }
    public int auditRetentionDays() { return auditRetentionDays; }
    public boolean webhookEnabled() { return webhookEnabled; }
    public String webhookUrl() { return webhookUrl; }
    public boolean autoBlockEnabled() { return autoBlockEnabled; }
    public int autoBlockThreshold() { return autoBlockThreshold; }
    public int autoBlockWindowSeconds() { return autoBlockWindowSeconds; }
    public int autoBlockDurationMinutes() { return autoBlockDurationMinutes; }
    public boolean updateCheckerEnabled() { return updateCheckerEnabled; }
    public String updateCheckerRepo() { return updateCheckerRepo; }
    public String combatTagMessage() { return combatTagMessage; }
    public String autoBlockedMessage() { return autoBlockedMessage; }
    /** Effective scope for a world: a per-world override if set, otherwise the global scope. */
    public Scope effectiveScope(String world) { return worldScopeOverrides.getOrDefault(world, scope); }
    public String noPermissionMessage() { return noPermissionMessage; } public String playerNotFoundMessage() { return playerNotFoundMessage; }
    public String inspectionHeader() { return inspectionHeader; } public String noAuditHistory() { return noAuditHistory; }
    public String ruleUpdatedMessage(String player, String rule) { return ruleUpdatedMessage.replace("%player%", player).replace("%rule%", rule); }
    public void toggle(String key) {
        FileConfiguration c = plugin.getConfig();
        if (key.equals("scope")) c.set("protection-scope", scope == Scope.CLAIMS ? "EVERYWHERE" : "CLAIMS");
        else { String path = switch (key) { case "water" -> "block-water"; case "lava" -> "block-lava"; case "fill" -> "block-fill"; case "empty" -> "block-empty"; case "flow" -> "block-fluid-flow"; case "dispenser" -> "block-dispensers"; case "creative" -> "block-creative-mode"; case "audit" -> "log-blocked-actions"; case "notify" -> "notify-player";
            case "fire" -> "block-fire-spread"; case "flintsteel" -> "block-flint-steel"; case "cauldron" -> "block-cauldron"; case "powdersnow" -> "block-powder-snow"; case "webhook" -> "webhook.enabled"; case "autoblock" -> "auto-block.enabled"; case "exemptsound" -> "exempt-sound";
            default -> null; }; if (path == null) return; c.set(path, !c.getBoolean(path, true)); }
        plugin.saveConfig(); load();
    }
    private static String color(String input) { return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input); }
}
