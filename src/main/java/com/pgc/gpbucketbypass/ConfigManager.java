package com.pgc.gpbucketbypass;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Loads, caches, and reloads GPBucketBypass's config.yml.
 * <p>
 * Values are read once per (re)load into plain Java fields so the hot
 * event-handling path in {@link BucketListener} never has to touch the
 * (comparatively slow) Bukkit {@link FileConfiguration} lookup chain.
 */
public final class ConfigManager {

    private final Main plugin;
    private final Logger logger;

    private boolean debug;
    private boolean disableWaterProtection;
    private boolean disableLavaProtection;
    private boolean allowWaterBucketFill;
    private boolean allowLavaBucketFill;
    private boolean allowWaterBucketEmpty;
    private boolean allowLavaBucketEmpty;
    private Set<String> enabledWorlds = Collections.emptySet();

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Loads (or reloads) config.yml from disk into this manager's cached
     * fields. Safe to call repeatedly, e.g. from /gpbucket reload.
     */
    public void load() {
        // Ensures config.yml exists on disk, copying the bundled default
        // from the jar the first time the plugin runs.
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        FileConfiguration config = plugin.getConfig();

        this.debug = config.getBoolean("debug", false);
        this.disableWaterProtection = config.getBoolean("disable-water-protection", true);
        this.disableLavaProtection = config.getBoolean("disable-lava-protection", true);
        this.allowWaterBucketFill = config.getBoolean("allow-water-bucket-fill", true);
        this.allowLavaBucketFill = config.getBoolean("allow-lava-bucket-fill", true);
        this.allowWaterBucketEmpty = config.getBoolean("allow-water-bucket-empty", true);
        this.allowLavaBucketEmpty = config.getBoolean("allow-lava-bucket-empty", true);

        List<String> worldsList = config.getStringList("worlds");
        Set<String> worlds = new HashSet<>(worldsList.size());
        for (String world : worldsList) {
            if (world != null && !world.isBlank()) {
                worlds.add(world);
            }
        }
        this.enabledWorlds = Collections.unmodifiableSet(worlds);

        if (debug) {
            logger.info("[Debug] Configuration (re)loaded: " +
                    "disable-water-protection=" + disableWaterProtection + ", " +
                    "disable-lava-protection=" + disableLavaProtection + ", " +
                    "allow-water-bucket-fill=" + allowWaterBucketFill + ", " +
                    "allow-lava-bucket-fill=" + allowLavaBucketFill + ", " +
                    "allow-water-bucket-empty=" + allowWaterBucketEmpty + ", " +
                    "allow-lava-bucket-empty=" + allowLavaBucketEmpty + ", " +
                    "worlds=" + enabledWorlds);
        }
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isWorldEnabled(String worldName) {
        return worldName != null && enabledWorlds.contains(worldName);
    }

    /**
     * @return true if water buckets should bypass claim protection when
     * an empty bucket is used to pick water up (fill event).
     */
    public boolean isWaterFillBypassEnabled() {
        return disableWaterProtection && allowWaterBucketFill;
    }

    /**
     * @return true if water buckets should bypass claim protection when
     * a full water bucket is emptied to place water (empty event).
     */
    public boolean isWaterEmptyBypassEnabled() {
        return disableWaterProtection && allowWaterBucketEmpty;
    }

    /**
     * @return true if lava buckets should bypass claim protection when
     * an empty bucket is used to pick lava up (fill event).
     */
    public boolean isLavaFillBypassEnabled() {
        return disableLavaProtection && allowLavaBucketFill;
    }

    /**
     * @return true if lava buckets should bypass claim protection when
     * a full lava bucket is emptied to place lava (empty event).
     */
    public boolean isLavaEmptyBypassEnabled() {
        return disableLavaProtection && allowLavaBucketEmpty;
    }

    public void debugLog(String message) {
        if (debug) {
            logger.info("[Debug] " + message);
        }
    }
}
