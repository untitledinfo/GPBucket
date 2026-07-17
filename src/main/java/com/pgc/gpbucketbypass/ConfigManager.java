package com.pgc.gpbucketbypass;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Immutable-at-use cached configuration for the liquid protections. */
public final class ConfigManager {
    public enum Scope { CLAIMS, EVERYWHERE }
    private final Main plugin;
    private boolean debug, blockWater, blockLava, blockFill, blockEmpty, permissionExemptions, databaseExemptions, audit;
    private Scope scope;
    private String databaseFile;
    private Set<String> worlds = Set.of();

    public ConfigManager(Main plugin) { this.plugin = plugin; }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        debug = c.getBoolean("debug", false);
        blockWater = c.getBoolean("block-water", true);
        blockLava = c.getBoolean("block-lava", true);
        blockFill = c.getBoolean("block-fill", true);
        blockEmpty = c.getBoolean("block-empty", true);
        permissionExemptions = c.getBoolean("respect-permission-exemptions", true);
        databaseExemptions = c.getBoolean("respect-database-exemptions", true);
        audit = c.getBoolean("log-blocked-actions", true);
        databaseFile = c.getString("database-file", "data.db");
        try { scope = Scope.valueOf(c.getString("protection-scope", "CLAIMS").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { scope = Scope.CLAIMS; plugin.getLogger().warning("Invalid protection-scope; using CLAIMS."); }
        Set<String> loaded = new HashSet<>();
        for (String name : c.getStringList("worlds")) if (name != null && !name.isBlank()) loaded.add(name);
        worlds = Set.copyOf(loaded);
    }
    public boolean isWorldEnabled(String world) { return worlds.contains(world); }
    public boolean shouldBlock(Material bucket, boolean filling) {
        return (filling ? blockFill : blockEmpty) && ((bucket == Material.WATER_BUCKET && blockWater) || (bucket == Material.LAVA_BUCKET && blockLava));
    }
    public boolean permissionExemptions() { return permissionExemptions; }
    public boolean databaseExemptions() { return databaseExemptions; }
    public boolean audit() { return audit; }
    public boolean debug() { return debug; }
    public Scope scope() { return scope; }
    public String databaseFile() { return databaseFile; }
}
