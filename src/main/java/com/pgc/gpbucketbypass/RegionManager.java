package com.pgc.gpbucketbypass;

import org.bukkit.Location;
import java.sql.SQLException;
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
}
