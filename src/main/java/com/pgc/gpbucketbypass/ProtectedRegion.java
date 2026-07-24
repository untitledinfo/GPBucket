package com.pgc.gpbucketbypass;

import org.bukkit.Location;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** An immutable cuboid saved from a WorldEdit selection.
 *  priority (feature 76) resolves overlapping regions: the highest-priority region's flags win.
 *  flags (feature 77) are explicit ALLOW/DENY overrides per liquid/action, e.g. "WATER" -> "ALLOW".
 *  tags (feature 104) is a comma-separated free-text grouping label, e.g. "spawn,event". */
public record ProtectedRegion(String name, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int priority, Map<String, String> flags, String tags) {
    public ProtectedRegion(String name, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this(name, world, minX, minY, minZ, maxX, maxY, maxZ, 0, Map.of(), "");
    }
    public boolean contains(Location location) {
        return location.getWorld() != null && world.equals(location.getWorld().getName())
                && location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockY() >= minY && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }
    /** Feature 77: null means "no override, fall back to global config"; otherwise ALLOW or DENY. */
    public String flag(String key) { return flags.get(key.toUpperCase(Locale.ROOT)); }
    /** Feature 104: true if this region carries the given tag (case-insensitive). */
    public boolean hasTag(String tag) { return tags != null && List.of(tags.toLowerCase(Locale.ROOT).split(",")).contains(tag.toLowerCase(Locale.ROOT)); }
}
