package com.pgc.gpbucketbypass;

import org.bukkit.Location;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

/** Cached manager for protected WorldEdit cuboids stored in SQLite. */
public final class RegionManager {
    private final DatabaseManager database; private volatile List<ProtectedRegion> regions = List.of();
    public RegionManager(DatabaseManager database) { this.database = database; }
    public void reload() throws SQLException { regions = List.copyOf(database.loadRegions()); }
    public boolean contains(Location location) { return regions.stream().anyMatch(region -> region.contains(location)); }
    public List<ProtectedRegion> list() { return regions; }
    public List<ProtectedRegion> at(Location location) { return regions.stream().filter(region -> region.contains(location)).toList(); }
    public ProtectedRegion find(String name) { return regions.stream().filter(region -> region.name().equalsIgnoreCase(name)).findFirst().orElse(null); }
    public void save(ProtectedRegion region, String by) throws SQLException { database.saveRegion(region, by); reload(); }
    public boolean delete(String name) throws SQLException { boolean deleted = database.deleteRegion(name); reload(); return deleted; }
    public boolean rename(String oldName, String newName) throws SQLException { boolean renamed = database.renameRegion(oldName, newName); reload(); return renamed; }
    // --- Feature 76: priority-ordered overlap resolution ---
    public void setPriority(String name, int priority) throws SQLException { database.setRegionPriority(name, priority); reload(); }
    // --- Feature 77: per-region flag overrides ---
    public void setFlag(String name, String flag, String mode) throws SQLException { database.setRegionFlag(name, flag, mode); reload(); }
    /** Highest-priority region touching this location that has an explicit override for the given flag, or null. */
    public String effectiveFlag(Location location, String flag) {
        return regions.stream()
                .filter(region -> region.contains(location) && region.flag(flag) != null)
                .max(Comparator.comparingInt(ProtectedRegion::priority))
                .map(region -> region.flag(flag))
                .orElse(null);
    }
    // --- Feature 89: export/import all regions (with flags + priority) as a YAML snapshot ---
    public void exportTo(java.io.File file) throws java.io.IOException {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        for (ProtectedRegion r : regions) {
            String base = "regions." + r.name();
            yaml.set(base + ".world", r.world()); yaml.set(base + ".min", List.of(r.minX(), r.minY(), r.minZ())); yaml.set(base + ".max", List.of(r.maxX(), r.maxY(), r.maxZ())); yaml.set(base + ".priority", r.priority());
            for (var entry : r.flags().entrySet()) yaml.set(base + ".flags." + entry.getKey(), entry.getValue());
        }
        yaml.save(file);
    }
    /** Returns the number of regions imported. */
    public int importFrom(java.io.File file, String by) throws java.io.IOException, SQLException {
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("regions"); if (section == null) return 0;
        int count = 0;
        for (String name : section.getKeys(false)) {
            String base = "regions." + name;
            List<?> min = yaml.getList(base + ".min"), max = yaml.getList(base + ".max");
            if (min == null || max == null || min.size() != 3 || max.size() != 3) continue;
            ProtectedRegion region = new ProtectedRegion(name, yaml.getString(base + ".world"),
                    ((Number) min.get(0)).intValue(), ((Number) min.get(1)).intValue(), ((Number) min.get(2)).intValue(),
                    ((Number) max.get(0)).intValue(), ((Number) max.get(1)).intValue(), ((Number) max.get(2)).intValue());
            save(region, by); setPriority(name, yaml.getInt(base + ".priority", 0));
            var flags = yaml.getConfigurationSection(base + ".flags");
            if (flags != null) for (String flag : flags.getKeys(false)) setFlag(name, flag, flags.getString(flag));
            count++;
        }
        reload(); return count;
    }
}
